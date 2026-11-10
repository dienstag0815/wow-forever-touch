package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonReleaseTest {

    private val mb = 1024L * 1024

    private fun entry(maxMb: Long = 30, forever: Boolean = true, pattern: String = "^Demo-.*\\.zip$"): Pair<CatalogEntry, AddonSource.GitHub> {
        val source = AddonSource.GitHub("o", "demo", pattern, forever)
        return CatalogEntry("demo", "Demo", "k", "w", "l", source, listOf("Demo"), maxMb * mb, 100 * mb) to source
    }

    private fun asset(name: String, size: Long = 1000, type: String = "application/zip", host: String = "github.com") =
        """{"id":1,"name":"$name","size":$size,"content_type":"$type","browser_download_url":"https://$host/o/demo/releases/download/v1/$name"}"""

    private fun releaseJson(
        tag: String = "v1.2",
        assets: List<String> = listOf(asset("Demo-v1.2.zip"), asset("release.json", 300, "application/json")),
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = """{"tag_name":"$tag","published_at":"2026-11-03T05:03:38Z","draft":$draft,"prerelease":$prerelease,"assets":[${assets.joinToString(",")}]}"""

    private fun meta(file: String = "Demo-v1.2.zip", flavor: String = "forever", iface: Int = 16001, nolib: Boolean = false) =
        """{"releases":[{"name":"Demo","version":"v1.2","filename":"$file","nolib":$nolib,"metadata":[{"flavor":"mainline","interface":120100},{"flavor":"$flavor","interface":$iface}]}]}"""

    private fun pick(json: String = releaseJson(), meta: String? = meta(), e: Pair<CatalogEntry, AddonSource.GitHub> = entry()): PickResult {
        val release = GitHubReleaseParser.parse(json)
        assertNotNull(release)
        return AddonAssetPicker.pick(e.first, e.second, release!!, meta)
    }

    private fun fail(r: PickResult): UiText {
        assertTrue("sollte abgelehnt werden: $r", r is PickResult.Fail)
        return (r as PickResult.Fail).reason
    }

    // ---- GitHub-Antwort lesen ---------------------------------------------------------------------------------

    @Test
    fun releaseJsonIsParsed() {
        val r = GitHubReleaseParser.parse(releaseJson())!!
        assertEquals("v1.2", r.tag)
        assertEquals("2026-11-03T05:03:38Z", r.publishedAt)
        assertEquals(2, r.assets.size)
        assertEquals("release.json", r.metaAsset?.name)
        assertEquals(1000L, r.assets[0].size)
    }

    @Test
    fun brokenReleaseJsonGivesNull() {
        assertNull(GitHubReleaseParser.parse(null))
        assertNull(GitHubReleaseParser.parse(""))
        assertNull(GitHubReleaseParser.parse("<html>"))
        assertNull(GitHubReleaseParser.parse("""{"message":"Not Found"}"""))
        assertNull(GitHubReleaseParser.parse("""{"tag_name":""}"""))
        val noAssets = GitHubReleaseParser.parse("""{"tag_name":"v1"}""")!!
        assertTrue(noAssets.assets.isEmpty())
    }

    // ---- Asset-Auswahl ----------------------------------------------------------------------------------------

    @Test
    fun theForeverArchiveIsPicked() {
        val ok = pick() as PickResult.Ok
        assertEquals("Demo-v1.2.zip", ok.picked.asset.name)
        assertEquals("v1.2", ok.picked.tag)
    }

    @Test
    fun withoutForeverInReleaseJsonNothingIsPicked() {
        assertEquals(UiText.res(R.string.launcher_addon_pick_no_forever, "v1.2"), fail(pick(meta = meta(flavor = "mainline"))))
        assertEquals(UiText.res(R.string.launcher_addon_pick_no_forever, "v1.2"), fail(pick(meta = meta(flavor = "classic"))))
        // Forever, aber mit einer fremden Interface-Nummer: kein Treffer.
        fail(pick(meta = meta(iface = 120100)))
        // release.json fehlt oder ist unlesbar: Forever nicht bestätigt, also nichts.
        assertEquals(UiText.res(R.string.launcher_addon_pick_no_release_json, "v1.2"), fail(pick(meta = null)))
        fail(pick(meta = "kaputt"))
        fail(pick(meta = """{"releases":"nein"}"""))
    }

    @Test
    fun foreverCheckCanBeSwitchedOffPerEntry() {
        val e = entry(forever = false)
        val ok = pick(meta = null, e = e)
        assertTrue(ok is PickResult.Ok)
        // Mit zwei passenden Archiven bleibt es trotzdem uneindeutig.
        val two = releaseJson(assets = listOf(asset("Demo-v1.2.zip"), asset("Demo-v1.2-classic.zip")))
        assertEquals(UiText.res(R.string.launcher_addon_pick_ambiguous, "v1.2"), fail(pick(json = two, meta = null, e = e)))
    }

    @Test
    fun metadataDecidesBetweenSeveralArchives() {
        val json = releaseJson(assets = listOf(asset("Demo-v1.2-classic.zip"), asset("Demo-v1.2.zip"), asset("release.json", 3, "application/json")))
        val ok = pick(json = json, meta = meta(file = "Demo-v1.2.zip")) as PickResult.Ok
        assertEquals("Demo-v1.2.zip", ok.picked.asset.name)
    }

    @Test
    fun nolibAndNonZipAndForeignHostsAreSkipped() {
        val json = releaseJson(
            assets = listOf(
                asset("Demo-v1.2-nolib.zip"),
                asset("Demo-v1.2.tar.gz", type = "application/gzip"),
                asset("Demo-v1.2.zip", host = "evil.example"),
                asset("Demo-v1.2.zip.sha256", type = "text/plain"),
            ),
        )
        fail(pick(json = json, meta = meta()))
        // Ein Archiv, das release.json als nolib kennzeichnet, zählt nicht.
        fail(pick(meta = meta(nolib = true)))
    }

    @Test
    fun octetStreamArchivesAreAccepted() {
        val json = releaseJson(assets = listOf(asset("Demo-v1.2.zip", type = "application/octet-stream"), asset("release.json", 3, "application/json")))
        assertTrue(pick(json = json) is PickResult.Ok)
    }

    @Test
    fun namePatternMustMatchTheWholeName() {
        val json = releaseJson(assets = listOf(asset("OtherDemo-v1.2.zip"), asset("release.json", 3, "application/json")))
        fail(pick(json = json, meta = meta(file = "OtherDemo-v1.2.zip")))
    }

    @Test
    fun draftsAndPrereleasesAreRefused() {
        assertEquals(UiText.res(R.string.launcher_addon_pick_prerelease), fail(pick(json = releaseJson(prerelease = true))))
        fail(pick(json = releaseJson(draft = true)))
    }

    @Test
    fun sizeLimitIsEnforced() {
        val big = releaseJson(assets = listOf(asset("Demo-v1.2.zip", size = 31 * mb), asset("release.json", 3, "application/json")))
        assertEquals(UiText.res(R.string.launcher_addon_pick_too_big, "Demo-v1.2.zip", 31L, 30L), fail(pick(json = big)))
        val exact = releaseJson(assets = listOf(asset("Demo-v1.2.zip", size = 30 * mb), asset("release.json", 3, "application/json")))
        assertTrue(pick(json = exact) is PickResult.Ok)
        val raised = pick(json = big, e = entry(maxMb = 40))
        assertTrue(raised is PickResult.Ok)
        val unknown = releaseJson(assets = listOf(asset("Demo-v1.2.zip", size = 0), asset("release.json", 3, "application/json")))
        fail(pick(json = unknown))
    }

    @Test
    fun foreverFilesReadsOnlyForeverEntries() {
        assertEquals(setOf("Demo-v1.2.zip"), AddonAssetPicker.foreverFiles(meta()))
        assertEquals(emptySet<String>(), AddonAssetPicker.foreverFiles(meta(flavor = "classic")))
        assertNull(AddonAssetPicker.foreverFiles(null))
        assertNull(AddonAssetPicker.foreverFiles("{"))
    }

    @Test
    fun theRealQuestieReleaseJsonShapeWorks() {
        // Form der release.json von Questie (08.11.2026): zwei classic-Einträge, forever 16001, zusätzliche Felder.
        val text = """{"releases":[{"filename":"Questie-v12.6.4+v1.0.12.zip","nolib":false,"metadata":[
            {"flavor":"classic","interface":11508},{"flavor":"classic","interface":11509},{"flavor":"forever","interface":16001}]}],
            "questie":{"repository":"https://example.invalid","version":"12.6.4"}}"""
        assertEquals(setOf("Questie-v12.6.4+v1.0.12.zip"), AddonAssetPicker.foreverFiles(text))
    }

    // ---- WoWInterface ---------------------------------------------------------------------------------------------

    @Test
    fun wowInterfaceDetailsAreParsed() {
        val md5 = "d41d8cd98f00b204e9800998ecf8427e"
        val json = """[{"UID":"123","UIName":"Demo","UIVersion":"1.4","UIMD5":"$md5","UIDownload":"https://cdn.wowinterface.com/downloads/getfile.php?id=123"}]"""
        val f = WowInterfaceParser.parseDetails(json)!!
        assertEquals("1.4", f.version)
        assertEquals(md5, f.md5)
        assertEquals("Demo", f.title)
    }

    @Test
    fun wowInterfaceDownloadMustStayOnItsHosts() {
        fun details(url: String) = """[{"UID":"1","UIVersion":"1","UIDownload":"$url"}]"""
        assertNull(WowInterfaceParser.parseDetails(details("https://evil.example/a.zip")))
        assertNull(WowInterfaceParser.parseDetails(details("http://cdn.wowinterface.com/a.zip")))
        assertNull(WowInterfaceParser.parseDetails(details("https://github.com/a.zip")))
        assertNull(WowInterfaceParser.parseDetails("[]"))
        assertNull(WowInterfaceParser.parseDetails("kaputt"))
        assertNull(WowInterfaceParser.parseDetails(null))
        val noMd5 = WowInterfaceParser.parseDetails("""{"UIVersion":"1","UIMD5":"zzz","UIDownload":"https://cdn.wowinterface.com/a.zip"}""")!!
        assertFalse(noMd5.md5 != null)
    }
}
