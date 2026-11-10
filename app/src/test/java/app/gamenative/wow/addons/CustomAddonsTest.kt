package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomAddonsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val http = FakeAddonHttp()
    private lateinit var service: CustomAddonService
    private lateinit var game: File
    private lateinit var addons: File
    private var now = 1_000_000_000_000L
    private val none = emptySet<String>()

    @Before
    fun setUp() {
        game = tmp.newFolder("game")
        addons = File(game, "_forever_/Interface/AddOns").also { it.mkdirs() }
        service = CustomAddonService(http, File(tmp.root, "dl"), clock = { now })
    }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, content) in entries) {
                z.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) z.write(content.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun toc(title: String = "Demo", version: String = "1.0", iface: String = "16001") =
        "## Interface: $iface\n## Title: $title\n## Version: $version\n"

    private fun demoZip(version: String = "1.0") = zipBytes("Demo/Demo.toc" to toc(version = version), "Demo/Demo.lua" to "print('hi')")

    private fun releaseJson(tag: String, assets: List<Triple<String, Long, String>>, draft: Boolean = false): String {
        val list = assets.joinToString(",") { (name, size, url) ->
            """{"name":"$name","size":$size,"content_type":"application/zip","browser_download_url":"$url"}"""
        }
        return """{"tag_name":"$tag","draft":$draft,"prerelease":false,"published_at":"2026-11-01T00:00:00Z","assets":[$list]}"""
    }

    private fun ok(r: PlanResult): DownloadPlan {
        assertTrue("Erwartet Ok, war $r", r is PlanResult.Ok)
        return (r as PlanResult.Ok).plan
    }

    private fun prepared(r: PrepareResult): Prepared {
        assertTrue("Erwartet Ok, war $r", r is PrepareResult.Ok)
        return (r as PrepareResult.Ok).prepared
    }

    // ---- Link -> Download-Adresse ------------------------------------------------------------------------------------

    @Test
    fun directZipPlansTheLinkItself() {
        val plan = ok(service.plan(resolveAddonLink("https://example.org/Demo.zip")))
        assertEquals("https://example.org/Demo.zip", plan.url)
        assertEquals(CustomSourceKind.LINK, plan.kind)
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun browserSitesGiveTheFriendlyMessageAndTheirPage() {
        val r = service.plan(resolveAddonLink("https://www.curseforge.com/wow/addons/demo"))
        assertTrue(r is PlanResult.Browser)
        r as PlanResult.Browser
        assertEquals("https://www.curseforge.com/wow/addons/demo", r.pageUrl)
        assertEquals(AddonLinkTexts.needsBrowser("CurseForge"), r.message)
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun unsupportedLinkIsAFailWithoutNetwork() {
        val r = service.plan(resolveAddonLink("https://example.org/page"))
        assertTrue(r is PlanResult.Fail)
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun gitHubLatestReleasePicksTheZipAsset() {
        val asset = "https://github.com/o/r/releases/download/2.1.14/R-2.1.14.zip"
        http.text("https://api.github.com/repos/o/r/releases/latest", releaseJson("2.1.14", listOf(Triple("R-2.1.14.zip", 1000L, asset))))
        val plan = ok(service.plan(AddonLink.GitHubRepo("o", "r", null)))
        assertEquals(asset, plan.url)
        assertEquals("2.1.14", plan.tag)
        assertEquals("o/r", plan.repo)
        assertEquals(CustomSourceKind.GITHUB, plan.kind)
        assertTrue(plan.hosts.contains("codeload.github.com"))
        assertTrue(plan.hosts.contains("release-assets.githubusercontent.com"))
    }

    @Test
    fun gitHubReleaseWithSeveralZipsPrefersTheForeverFile() {
        val base = "https://github.com/o/r/releases/download/1/"
        http.text(
            "https://api.github.com/repos/o/r/releases/latest",
            releaseJson(
                "1",
                listOf(
                    Triple("R-1-cata.zip", 100L, base + "R-1-cata.zip"),
                    Triple("R-1.zip", 100L, base + "R-1.zip"),
                    Triple("R-1-classic.zip", 100L, base + "R-1-classic.zip"),
                    Triple("release.json", 10L, base + "release.json"),
                ),
            ),
        )
        http.text(
            base + "release.json",
            """{"releases":[{"filename":"R-1-classic.zip","nolib":false,"metadata":[{"flavor":"forever","interface":16001}]},
               {"filename":"R-1.zip","nolib":false,"metadata":[{"flavor":"mainline","interface":120105}]}]}""",
        )
        val plan = ok(service.plan(AddonLink.GitHubRepo("o", "r", null)))
        assertEquals(base + "R-1-classic.zip", plan.url)
    }

    @Test
    fun gitHubReleaseWithSeveralZipsWithoutMetaPrefersTheNameWithoutGameVersion() {
        val base = "https://github.com/o/r/releases/download/1/"
        val zips = listOf("R-1-cata.zip", "R-1.zip", "R-1-classic.zip").map { ReleaseAsset(it, base + it, 100L, "application/zip") }
        assertEquals("R-1.zip", CustomAssetPicker.choose(zips, null)?.name)
        assertEquals("R-1-cata.zip", CustomAssetPicker.choose(zips.filter { it.name != "R-1.zip" }, null)?.name)
        assertNull(CustomAssetPicker.choose(emptyList(), null))
    }

    @Test
    fun gitHubTagLinkAsksForThatRelease() {
        val asset = "https://github.com/o/r/releases/download/v9/R.zip"
        http.text("https://api.github.com/repos/o/r/releases/tags/v9", releaseJson("v9", listOf(Triple("R.zip", 10L, asset))))
        assertEquals(asset, ok(service.plan(AddonLink.GitHubRepo("o", "r", "v9"))).url)
        val missing = service.plan(AddonLink.GitHubRepo("o", "r", "v10"))
        assertTrue(missing is PlanResult.Fail)
        assertEquals(UiText.res(R.string.launcher_addon_plan_release_missing, "v10", "o/r"), (missing as PlanResult.Fail).reason)
    }

    @Test
    fun gitHubReleaseWithoutZipFallsBackToTheSourceArchiveOfTheTag() {
        http.text("https://api.github.com/repos/o/r/releases/latest", releaseJson("v3", emptyList()))
        val plan = ok(service.plan(AddonLink.GitHubRepo("o", "r", null)))
        assertEquals("https://api.github.com/repos/o/r/zipball/v3", plan.url)
        assertEquals("v3", plan.tag)
    }

    @Test
    fun gitHubWithoutReleaseUsesTheDefaultBranch() {
        val sha = "7fa425bad3f08cbd500cd044abe31b3d269ec422"
        http.text("https://api.github.com/repos/o/r", """{"name":"r","default_branch":"main"}""")
        http.text("https://api.github.com/repos/o/r/commits/main", sha)
        val plan = ok(service.plan(AddonLink.GitHubRepo("o", "r", null)))
        assertEquals("https://api.github.com/repos/o/r/zipball/main", plan.url)
        assertEquals("main@7fa425b", plan.tag)
        assertEquals("o/r", plan.repo)
    }

    @Test
    fun gitHubBranchWithoutShaStillWorks() {
        http.text("https://api.github.com/repos/o/r", """{"default_branch":"master"}""")
        val plan = ok(service.plan(AddonLink.GitHubRepo("o", "r", null)))
        assertEquals("master", plan.tag)
    }

    @Test
    fun gitHubUnknownRepoGivesAFriendlyReason() {
        val r = service.plan(AddonLink.GitHubRepo("o", "gibtsnicht", null))
        assertTrue(r is PlanResult.Fail)
        assertEquals(UiText.res(R.string.launcher_addon_plan_repo_missing, "o/gibtsnicht"), (r as PlanResult.Fail).reason)
    }

    @Test
    fun gitHubRateLimitMessagePassesThrough() {
        http.text(
            "https://api.github.com/repos/o/r/releases/latest",
            "{}",
            code = 403,
            headers = mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "${now / 1000 + 600}"),
        )
        val r = service.plan(AddonLink.GitHubRepo("o", "r", null))
        assertTrue(r is PlanResult.Fail)
        assertEquals(UiText.plural(R.plurals.launcher_addon_net_rate_limited_minutes, 10), (r as PlanResult.Fail).reason)
    }

    @Test
    fun wowInterfaceUsesTheApiDownloadLink() {
        http.text(
            "https://api.mmoui.com/v3/game/WOW/filedetails/24910.json",
            """[{"UID":"24910","UIVersion":"5.22.1","UIMD5":"5168dc09918ef0fbd19dd81815fac020","UIDownload":"https:\/\/cdn.wowinterface.com\/downloads\/getfile.php?id=24910&d=1791652888&minion","UIName":"WeakAuras 2"}]""",
        )
        val plan = ok(service.plan(AddonLink.WowInterface("24910")))
        assertEquals("https://cdn.wowinterface.com/downloads/getfile.php?id=24910&d=1791652888&minion", plan.url)
        assertEquals("5.22.1", plan.tag)
        assertEquals("5168dc09918ef0fbd19dd81815fac020", plan.md5)
        assertEquals(CustomSourceKind.WOWINTERFACE, plan.kind)
        assertEquals("https://www.wowinterface.com/downloads/info24910", plan.source)
    }

    @Test
    fun wowInterfaceFallsBackToTheKnownDirectLink() {
        val plan = ok(service.plan(AddonLink.WowInterface("24910")))
        assertEquals("https://cdn.wowinterface.com/downloads/getfile.php?id=24910", plan.url)
        assertNull(plan.md5)
    }

    // ---- Laden und Prüfen --------------------------------------------------------------------------------------------

    @Test
    fun prepareDownloadsAndScans() {
        http.bytes("https://example.org/Demo.zip", demoZip())
        val plan = ok(service.plan(resolveAddonLink("https://example.org/Demo.zip")))
        val p = prepared(service.prepare(plan))
        assertEquals(listOf("Demo"), p.scan.addons.map { it.folder })
        assertTrue(p.zip.isFile)
        assertEquals(64, p.sha256.length)
        assertTrue(http.calls.single().startsWith("https://example.org/"))
        service.discard(p)
        assertFalse(p.zip.exists())
    }

    @Test
    fun prepareFollowsRedirectsToOtherPublicHostsButNotToLocalOnes() {
        http.redirect("https://example.org/Demo.zip", "https://cdn.example.net/x/Demo.zip")
        http.bytes("https://cdn.example.net/x/Demo.zip", demoZip())
        val plan = ok(service.plan(resolveAddonLink("https://example.org/Demo.zip")))
        prepared(service.prepare(plan))

        http.redirect("https://example.org/Evil.zip", "https://192.168.0.1/Evil.zip")
        http.bytes("https://192.168.0.1/Evil.zip", demoZip())
        val bad = service.prepare(ok(service.plan(resolveAddonLink("https://example.org/Evil.zip"))))
        assertTrue(bad is PrepareResult.Fail)
        assertFalse(http.calls.contains("https://192.168.0.1/Evil.zip"))
    }

    @Test
    fun prepareRefusesDowngradeToHttp() {
        http.redirect("https://example.org/Demo.zip", "http://example.org/Demo.zip")
        http.bytes("http://example.org/Demo.zip", demoZip())
        val r = service.prepare(ok(service.plan(resolveAddonLink("https://example.org/Demo.zip"))))
        assertTrue(r is PrepareResult.Fail)
        assertFalse(http.calls.contains("http://example.org/Demo.zip"))
    }

    @Test
    fun prepareRefusesFilesOverFiftyMegabytes() {
        val big = ByteArray(1024)
        http.replies["https://example.org/Big.zip"] = FakeAddonHttp.Reply(
            200, big, mapOf("Content-Length" to (CustomAddonService.MAX_DOWNLOAD_BYTES + 1).toString()), declareLength = false,
        )
        val r = service.prepare(ok(service.plan(resolveAddonLink("https://example.org/Big.zip"))))
        assertTrue(r is PrepareResult.Fail)
        assertEquals(UiText.res(R.string.launcher_addon_net_file_too_big_mb, 50L), (r as PrepareResult.Fail).reason)
        assertEquals(50L * 1024 * 1024, CustomAddonService.MAX_DOWNLOAD_BYTES)
        assertTrue(File(tmp.root, "dl").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun prepareCanBeCancelled() {
        http.bytes("https://example.org/Demo.zip", demoZip())
        val r = service.prepare(ok(service.plan(resolveAddonLink("https://example.org/Demo.zip"))), cancelled = { true })
        assertTrue(r is PrepareResult.Fail)
        assertEquals(UiText.res(R.string.launcher_addon_cancelled), (r as PrepareResult.Fail).reason)
        assertTrue(File(tmp.root, "dl").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun prepareStopsAfterTheTimeLimit() {
        // mehrere Blöcke à 64 KB: der erste Fortschritt schiebt die Uhr vor, beim zweiten Block ist das Zeitlimit überschritten
        val random = java.util.Random(1)
        val blob = String(CharArray(300_000) { 'a' + random.nextInt(26) })
        http.bytes("https://example.org/Demo.zip", zipBytes("Demo/Demo.toc" to toc(), "Demo/blob.txt" to blob))
        val r = service.prepare(
            ok(service.plan(resolveAddonLink("https://example.org/Demo.zip"))),
            onProgress = { _, _ -> now += CustomAddonService.TIME_LIMIT_MS + 1 },
        )
        assertTrue(r is PrepareResult.Fail)
        assertEquals(UiText.plural(R.plurals.launcher_addon_time_limit, 5), (r as PrepareResult.Fail).reason)
        assertTrue(File(tmp.root, "dl").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun prepareRefusesAWebPageInsteadOfAZip() {
        http.text("https://example.org/Demo.zip", "<html><body>Bitte anmelden</body></html>")
        val r = service.prepare(ok(service.plan(resolveAddonLink("https://example.org/Demo.zip"))))
        assertTrue(r is PrepareResult.Fail)
        assertEquals(UiText.res(R.string.launcher_addon_not_zip), (r as PrepareResult.Fail).reason)
    }

    @Test
    fun prepareChecksTheMd5FromWowInterface() {
        http.text(
            "https://api.mmoui.com/v3/game/WOW/filedetails/5.json",
            """[{"UIVersion":"1.0","UIMD5":"00000000000000000000000000000000","UIDownload":"https://cdn.wowinterface.com/downloads/getfile.php?id=5"}]""",
        )
        http.bytes("https://cdn.wowinterface.com/downloads/getfile.php?id=5", demoZip())
        val r = service.prepare(ok(service.plan(AddonLink.WowInterface("5"))))
        assertTrue(r is PrepareResult.Fail)
        assertEquals(UiText.res(R.string.launcher_addon_checksum_mismatch), (r as PrepareResult.Fail).reason)
    }

    @Test
    fun prepareStreamReadsAFileAndRefusesOversize() {
        val p = prepared(service.prepareStream(ByteArrayInputStream(demoZip()), "Demo.zip"))
        assertEquals(CustomSourceKind.FILE, p.plan.kind)
        assertEquals("Demo.zip", p.plan.source)
        assertEquals(listOf("Demo"), p.scan.addons.map { it.folder })

        val tooBig = object : java.io.InputStream() {
            var left = CustomAddonService.MAX_DOWNLOAD_BYTES + 10
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                left -= n
                return n
            }
        }
        val r = service.prepareStream(tooBig, "Gross.zip")
        assertTrue(r is PrepareResult.Fail)
        assertEquals(UiText.res(R.string.launcher_addon_net_file_too_big_mb, 50L), (r as PrepareResult.Fail).reason)
    }

    @Test
    fun prepareStreamRefusesZipSlip() {
        val r = service.prepareStream(ByteArrayInputStream(zipBytes("Demo/Demo.toc" to toc(), "Demo/../../x.lua" to "x")), "Evil.zip")
        assertTrue(r is PrepareResult.Fail)
        assertEquals(UiText.res(R.string.launcher_addon_zip_unsafe_path), (r as PrepareResult.Fail).reason)
        assertTrue(File(tmp.root, "dl").listFiles().orEmpty().isEmpty())
    }

    // ---- Einbauen, Liste, An/Aus, Entfernen --------------------------------------------------------------------------

    private fun installFrom(bytes: ByteArray, source: String = "Demo.zip", replace: Boolean = false, protectedFolders: Set<String> = none) =
        service.install(prepared(service.prepareStream(ByteArrayInputStream(bytes), source)), addons, protectedFolders, replace)

    @Test
    fun installRecordsTheAddonAndListsIt() {
        val o = installFrom(demoZip())
        assertTrue(o is CustomInstallOutcome.Installed)
        val r = (o as CustomInstallOutcome.Installed).record
        assertEquals("demo", r.id)
        assertEquals("Demo", r.name)
        assertEquals("1.0", r.version)
        assertEquals(CustomSourceKind.FILE, r.kind)
        assertEquals(listOf("Demo"), r.folders)
        assertEquals(listOf(16001), r.interfaces)
        assertTrue(File(addons, "Demo/Demo.toc").isFile)

        val list = service.list(addons)
        assertEquals(1, list.size)
        assertEquals(EnableState.ENABLED, list.single().enable)
        assertEquals(UiText.res(R.string.launcher_addon_custom_source_file, "Demo.zip"), CustomAddonLabels.sourceLine(r))
        assertTrue(File(tmp.root, "dl").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun installNeedsAGameFolder() {
        val lonely = File(tmp.root, "x/Interface/AddOns")
        val p = prepared(service.prepareStream(ByteArrayInputStream(demoZip()), "Demo.zip"))
        val o = service.install(p, lonely, none, false)
        assertTrue(o is CustomInstallOutcome.Failed)
        assertEquals(AddonText.gameNotInstalled("x"), (o as CustomInstallOutcome.Failed).reason)
    }

    @Test
    fun toggleUsesTheSameMechanismAsCuratedAddons() {
        val r = (installFrom(demoZip()) as CustomInstallOutcome.Installed).record
        assertNull(service.setEnabled(r, addons, false))
        assertTrue(File(addons, "Demo/Demo.toc.aus").isFile)
        assertEquals(EnableState.DISABLED, service.list(addons).single().enable)
        assertNull(service.setEnabled(r, addons, true))
        assertTrue(File(addons, "Demo/Demo.toc").isFile)
        assertEquals(EnableState.ENABLED, service.list(addons).single().enable)
    }

    @Test
    fun replacingKeepsTheSwitchAndMakesABackup() {
        val r = (installFrom(demoZip("1.0")) as CustomInstallOutcome.Installed).record
        service.setEnabled(r, addons, false)
        val needsReplace = installFrom(demoZip("2.0"))
        assertTrue(needsReplace is CustomInstallOutcome.Failed)
        assertEquals(UiText.res(R.string.launcher_addon_folder_exists, "Demo"), (needsReplace as CustomInstallOutcome.Failed).reason)
        val o = installFrom(demoZip("2.0"), replace = true) as CustomInstallOutcome.Installed
        assertEquals("2.0", o.record.version)
        assertEquals(EnableState.DISABLED, service.list(addons).single().enable)
        assertTrue(File(addons, "Demo_bak").isDirectory)
        assertEquals(1, service.list(addons).size)
    }

    @Test
    fun installRefusesCuratedFolders() {
        File(addons, "Questie").mkdirs()
        val o = installFrom(zipBytes("Questie/Questie.toc" to toc("Questie")), replace = true, protectedFolders = setOf("questie"))
        assertTrue(o is CustomInstallOutcome.Failed)
        assertEquals(UiText.res(R.string.launcher_addon_assess_protected, "Questie"), (o as CustomInstallOutcome.Failed).reason)
        assertTrue(service.list(addons).isEmpty())
    }

    @Test
    fun packageWithSeveralFoldersIsOneEntry() {
        val o = installFrom(zipBytes("WA/WA.toc" to toc("WA"), "WAOptions/WAOptions.toc" to toc("WA Options"))) as CustomInstallOutcome.Installed
        assertEquals("WA (+1)", o.record.name)
        assertEquals(listOf("WA", "WAOptions"), o.record.folders)
        val r = o.record
        assertNull(service.setEnabled(r, addons, false))
        assertTrue(File(addons, "WAOptions/WAOptions.toc.aus").isFile)
    }

    @Test
    fun newerPackageTakesOverFoldersFromAnotherEntry() {
        installFrom(zipBytes("A/A.toc" to toc("A"), "B/B.toc" to toc("B")))
        installFrom(zipBytes("B/B.toc" to toc("B2")), replace = true)
        val list = service.list(addons).map { it.record }
        assertEquals(2, list.size)
        assertEquals(listOf("A"), list.single { it.id == "a" }.folders)
        assertEquals(listOf("B"), list.single { it.id == "b" }.folders)
    }

    @Test
    fun removeDeletesFoldersBackupAndEntry() {
        val r = (installFrom(demoZip("1.0")) as CustomInstallOutcome.Installed).record
        installFrom(demoZip("2.0"), replace = true)
        assertNull(service.remove(r, addons, none))
        assertFalse(File(addons, "Demo").exists())
        assertFalse(File(addons, "Demo_bak").exists())
        assertTrue(service.list(addons).isEmpty())
    }

    @Test
    fun removeCannotTouchCuratedFolders() {
        val r = CustomAddonRecord("q", "Q", null, null, CustomSourceKind.FILE, "Q.zip", null, emptyList(), listOf("Questie"), "", 0L, 0L)
        File(addons, "Questie").mkdirs()
        val problem = service.remove(r, addons, setOf("questie"))
        assertTrue(problem != null)
        assertTrue(File(addons, "Questie").isDirectory)
    }

    // ---- Liste speichern ---------------------------------------------------------------------------------------------

    @Test
    fun storeRoundTrip() {
        assertTrue(CustomAddonStore.read(addons).isEmpty())
        val a = CustomAddonRecord(
            "demo", "Demo", "1.0", "v1.0", CustomSourceKind.GITHUB, "https://github.com/o/r", "o/r", listOf(16001, 120105),
            listOf("Demo", "DemoOptions"), "abc", 123L, 456L,
        )
        val b = a.copy(id = "zwei", name = "Zwei", version = null, tag = null, kind = CustomSourceKind.FILE, repo = null, interfaces = emptyList(), folders = listOf("Zwei"))
        CustomAddonStore.write(addons, listOf(a, b))
        assertEquals(listOf(a, b), CustomAddonStore.read(addons))
    }

    @Test
    fun storeSurvivesGarbage() {
        File(addons, CustomAddonStore.FILE).writeText("kein json")
        assertTrue(CustomAddonStore.read(addons).isEmpty())
        File(addons, CustomAddonStore.FILE).writeText("""{"version":1,"addons":[{"id":"x"},{"id":"y","folders":["Y"]}]}""")
        assertEquals(listOf("y"), CustomAddonStore.read(addons).map { it.id })
    }

    // ---- Update ------------------------------------------------------------------------------------------------------

    @Test
    fun versionComparison() {
        assertTrue(CustomVersion.isNewer(null, "1.0"))
        assertTrue(CustomVersion.isNewer("2.1.14", "2.1.15"))
        assertTrue(CustomVersion.isNewer("v1.9", "v1.10"))
        assertTrue(CustomVersion.isNewer("1.0", "1.0.1"))
        assertFalse(CustomVersion.isNewer("2.1.14", "2.1.14"))
        assertFalse(CustomVersion.isNewer("v2.1.14", "2.1.14"))
        assertFalse(CustomVersion.isNewer("2.1.15", "2.1.14"))
        assertFalse(CustomVersion.isNewer("1.0", "1"))
        // Zweig@Commit: jeder Unterschied zählt
        assertTrue(CustomVersion.isNewer("main@7fa425b", "main@8aa0001"))
        assertFalse(CustomVersion.isNewer("main@7fa425b", "main@7fa425b"))
        assertTrue(CustomVersion.isNewer("main@7fa425b", "v1.0"))
        assertTrue(CustomVersion.isNewer("v1.0", "main@7fa425b"))
    }

    private fun githubRecord(tag: String?) = CustomAddonRecord(
        "demo", "Demo", tag, tag, CustomSourceKind.GITHUB, "https://github.com/o/r", "o/r", emptyList(), listOf("Demo"), "", 0L, 0L,
    )

    @Test
    fun updateCheckFindsNewerReleaseTag() {
        val asset = "https://github.com/o/r/releases/download/2.0/R.zip"
        http.text("https://api.github.com/repos/o/r/releases/latest", releaseJson("2.0", listOf(Triple("R.zip", 100L, asset))))
        val newer = service.checkUpdate(githubRecord("1.5"))
        assertTrue(newer is UpdateCheck.Newer)
        newer as UpdateCheck.Newer
        assertEquals("2.0", newer.latestTag)
        assertEquals(asset, newer.plan.url)
        assertEquals(UpdateCheck.Current, service.checkUpdate(githubRecord("2.0")))
        assertEquals(UpdateCheck.Current, service.checkUpdate(githubRecord("v2.0")))
    }

    @Test
    fun updateCheckOnlyForGitHubSources() {
        val file = githubRecord("1").copy(kind = CustomSourceKind.FILE, repo = null)
        assertEquals(UpdateCheck.NotApplicable, service.checkUpdate(file))
        val wowi = githubRecord("1").copy(kind = CustomSourceKind.WOWINTERFACE, repo = null)
        assertEquals(UpdateCheck.NotApplicable, service.checkUpdate(wowi))
    }

    @Test
    fun updateCheckReportsNetworkProblems() {
        val r = service.checkUpdate(githubRecord("1"))
        // unbekanntes Repo in der Attrappe: 404 -> kein Release -> kein Repo
        assertTrue(r is UpdateCheck.Fail)
    }

    @Test
    fun updateInstallsOverTheOldVersionEndToEnd() {
        val asset1 = "https://github.com/o/r/releases/download/1.0/R.zip"
        val asset2 = "https://github.com/o/r/releases/download/2.0/R.zip"
        http.text("https://api.github.com/repos/o/r/releases/latest", releaseJson("1.0", listOf(Triple("R.zip", 100L, asset1))))
        http.bytes(asset1, demoZip("1.0"))
        http.bytes(asset2, demoZip("2.0"))

        val plan = ok(service.plan(resolveAddonLink("https://github.com/o/r")))
        val first = service.install(prepared(service.prepare(plan)), addons, none, false) as CustomInstallOutcome.Installed
        assertEquals("1.0", first.record.tag)
        assertEquals("o/r", first.record.repo)
        assertEquals(UiText.res(R.string.launcher_addon_source_github, "o/r"), CustomAddonLabels.sourceLine(first.record))

        assertEquals(UpdateCheck.Current, service.checkUpdate(first.record))
        http.text("https://api.github.com/repos/o/r/releases/latest", releaseJson("2.0", listOf(Triple("R.zip", 100L, asset2))))
        val check = service.checkUpdate(first.record) as UpdateCheck.Newer
        val second = service.install(prepared(service.prepare(check.plan)), addons, none, true) as CustomInstallOutcome.Installed
        assertEquals("2.0", second.record.tag)
        assertEquals("2.0", second.record.version)
        assertEquals(1, service.list(addons).size)
        assertTrue(File(addons, "Demo_bak").isDirectory)
        assertEquals(UpdateCheck.Current, service.checkUpdate(second.record))
    }

    @Test
    fun cleanTempRemovesLeftovers() {
        File(tmp.root, "dl").mkdirs()
        File(tmp.root, "dl/custom-1.zip").writeText("x")
        service.cleanTemp()
        assertTrue(File(tmp.root, "dl").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun userAgentIsSent() {
        var seen: Map<String, String>? = null
        val spy = AddonHttp { url, headers ->
            seen = headers
            http.get(url, headers)
        }
        http.text("https://api.github.com/repos/o/r/releases/latest", releaseJson("1", emptyList()))
        CustomAddonService(spy, File(tmp.root, "dl2")).plan(AddonLink.GitHubRepo("o", "r", null))
        assertEquals("WowForeverFold", seen?.get("User-Agent"))
        if (seen == null) fail("kein Aufruf")
    }
}
