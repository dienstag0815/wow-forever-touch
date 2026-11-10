package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Der ganze Ablauf mit einer Netz-Attrappe: neueste Version, Download, Entpacken, Verzeichnis, Update, Entfernen. */
class AddonServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val mb = 1024L * 1024
    private val http = FakeAddonHttp()
    private val logs = ArrayList<String>()
    private lateinit var service: AddonService
    private lateinit var addons: File
    private var clock = 1_700_000_000_000L

    private val entry = CatalogEntry(
        "demo", "Demo", "k", "w", "l",
        AddonSource.GitHub("o", "demo", "^Demo-.*\\.zip$"), listOf("Demo", "Demo_B"), 30 * mb, 100 * mb,
    )

    @Before
    fun setUp() {
        addons = File(tmp.root, "game/_forever_/Interface/AddOns").also { it.mkdirs() }
        File(addons, "TouchForever").mkdirs()
        File(addons, "TouchForever/TouchForever.toc").writeText("unser Addon")
        service = AddonService(http, AddonCache(File(tmp.root, "cache"), now = { clock }), File(tmp.root, "tmp"), { clock }, { logs += it })
    }

    private fun zipBytes(version: String, extra: Pair<String, String>? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            val files = mutableListOf(
                "Demo/Demo.toc" to "## Interface: 16001\n## Version: $version",
                "Demo/Demo.lua" to "v=$version",
                "Demo_B/Demo_B.toc" to "## Interface: 16001",
            )
            if (extra != null) files += extra
            for ((name, content) in files) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray()); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Stellt ein GitHub-Release `tag` ins Netz. */
    private fun publish(tag: String, zip: ByteArray = zipBytes(tag), forever: Boolean = true, name: String = "Demo-$tag.zip") {
        val url = "https://github.com/o/demo/releases/download/$tag/$name"
        val metaUrl = "https://github.com/o/demo/releases/download/$tag/release.json"
        http.text(
            "https://api.github.com/repos/o/demo/releases/latest",
            """{"tag_name":"$tag","published_at":"2026-11-03T05:03:38Z","draft":false,"prerelease":false,"assets":[
                {"id":1,"name":"$name","size":${zip.size},"content_type":"application/zip","browser_download_url":"$url"},
                {"id":2,"name":"release.json","size":100,"content_type":"application/json","browser_download_url":"$metaUrl"}]}""",
        )
        http.text(
            metaUrl,
            """{"releases":[{"filename":"$name","nolib":false,"metadata":[{"flavor":"${if (forever) "forever" else "classic"}","interface":16001}]}]}""",
        )
        http.redirect(url, "https://release-assets.githubusercontent.com/blob/$tag")
        http.bytes("https://release-assets.githubusercontent.com/blob/$tag", zip)
    }

    private fun installed(outcome: InstallOutcome): InstalledRecord {
        assertTrue("sollte klappen: $outcome", outcome is InstallOutcome.Installed)
        return (outcome as InstallOutcome.Installed).record
    }

    private fun failed(outcome: InstallOutcome): UiText {
        assertTrue("sollte scheitern: $outcome", outcome is InstallOutcome.Failed)
        return (outcome as InstallOutcome.Failed).reason
    }

    private fun assertTouchForeverUntouched() {
        assertEquals("unser Addon", File(addons, "TouchForever/TouchForever.toc").readText())
    }

    @Test
    fun fullInstallWritesFilesRecordAndLog() {
        publish("v1.0")
        val record = installed(service.install(entry, addons))
        assertEquals("v1.0", record.tag)
        assertEquals(listOf("Demo", "Demo_B"), record.folders)
        assertEquals("v=v1.0", File(addons, "Demo/Demo.lua").readText())
        assertEquals(record, AddonManifest.read(addons)["demo"])
        val expected = AddonFetch.hex(MessageDigest.getInstance("SHA-256").digest(zipBytes("v1.0")))
        assertEquals(expected, record.sha256)
        assertTrue("Prüfsumme wird protokolliert", logs.single().contains(expected))
        assertFalse("Download-Datei aufgeräumt", File(tmp.root, "tmp").listFiles().orEmpty().any { it.name.startsWith("demo") })
        assertTouchForeverUntouched()
    }

    @Test
    fun latestIsCachedForAnHour() {
        publish("v1.0")
        assertTrue(service.latest(entry) is LatestResult.Ok)
        val calls = http.calls.size
        assertTrue(service.latest(entry) is LatestResult.Ok)
        assertEquals("zweiter Aufruf kommt aus dem Zwischenspeicher", calls, http.calls.size)
        // Datei im Zwischenspeicher altern lassen
        File(tmp.root, "cache").listFiles()!!.forEach { it.setLastModified(clock - AddonCache.TTL_MS - 1) }
        assertTrue(service.latest(entry) is LatestResult.Ok)
        assertTrue(http.calls.size > calls)
    }

    @Test
    fun staleCacheIsUsedWhenTheNetworkFails() {
        publish("v1.0")
        assertTrue(service.latest(entry) is LatestResult.Ok)
        File(tmp.root, "cache").listFiles()!!.forEach { it.setLastModified(clock - AddonCache.TTL_MS - 1) }
        http.replies["https://api.github.com/repos/o/demo/releases/latest"] =
            FakeAddonHttp.Reply(403, headers = mapOf("X-RateLimit-Remaining" to "0"))
        val r = service.latest(entry)
        assertTrue("Rückfall auf alten Stand: $r", r is LatestResult.Ok)
        assertEquals("v1.0", (r as LatestResult.Ok).picked.tag)
    }

    @Test
    fun rateLimitWithoutCacheIsReportedNicely() {
        http.text("https://api.github.com/repos/o/demo/releases/latest", "{}", code = 403, headers = mapOf("X-RateLimit-Remaining" to "0"))
        val r = service.latest(entry)
        assertTrue(r is LatestResult.Fail)
        assertEquals(UiText.res(R.string.launcher_addon_net_rate_limited_later), (r as LatestResult.Fail).reason)
    }

    @Test
    fun releaseWithoutForeverIsNotInstalled() {
        publish("v1.0", forever = false)
        assertEquals(UiText.res(R.string.launcher_addon_pick_no_forever, "v1.0"), failed(service.install(entry, addons)))
        assertFalse(File(addons, "Demo").exists())
        assertNull(AddonManifest.read(addons)["demo"])
    }

    @Test
    fun updateReplacesFilesAndBumpsTheRecord() {
        publish("v1.0")
        installed(service.install(entry, addons))
        publish("v1.1", zipBytes("v1.1", "Demo/New.lua" to "n"))
        File(tmp.root, "cache").listFiles()!!.forEach { it.setLastModified(clock - AddonCache.TTL_MS - 1) }
        val record = installed(service.install(entry, addons))
        assertEquals("v1.1", record.tag)
        assertEquals("v=v1.1", File(addons, "Demo/Demo.lua").readText())
        assertTrue(File(addons, "Demo/New.lua").isFile)
        assertEquals("v1.1", AddonManifest.read(addons)["demo"]!!.tag)
        assertTouchForeverUntouched()
    }

    @Test
    fun aSwitchedOffAddonStaysOffAfterAnUpdate() {
        publish("v1.0")
        installed(service.install(entry, addons))
        assertNull(service.setEnabled(entry, addons, false))
        assertEquals(EnableState.DISABLED, AddonToggle.state(addons, entry.folders))
        publish("v1.1")
        File(tmp.root, "cache").listFiles()!!.forEach { it.setLastModified(clock - AddonCache.TTL_MS - 1) }
        installed(service.install(entry, addons))
        assertEquals(EnableState.DISABLED, AddonToggle.state(addons, entry.folders))
        assertNull(service.setEnabled(entry, addons, true))
        assertEquals(EnableState.ENABLED, AddonToggle.state(addons, entry.folders))
    }

    @Test
    fun aFolderNotFromUsIsNeverOverwritten() {
        File(addons, "Demo").mkdirs()
        File(addons, "Demo/Demo.toc").writeText("von Hand")
        publish("v1.0")
        assertEquals(UiText.res(R.string.launcher_addon_folder_foreign, "Demo"), failed(service.install(entry, addons)))
        assertEquals("von Hand", File(addons, "Demo/Demo.toc").readText())
        assertTrue(http.calls.isEmpty())
        assertNotNull(service.remove(entry, addons))
        assertEquals("von Hand", File(addons, "Demo/Demo.toc").readText())
        assertNotNull(service.setEnabled(entry, addons, false))
        assertTrue(File(addons, "Demo/Demo.toc").isFile)
    }

    @Test
    fun nothingIsCreatedBeforeTheGameIsInstalled() {
        val noGame = File(tmp.root, "leer/_forever_/Interface/AddOns")
        publish("v1.0")
        assertEquals(AddonText.gameNotInstalled("_forever_"), failed(service.install(entry, noGame)))
        assertFalse(File(tmp.root, "leer").exists())
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun removeDeletesOnlyItsFoldersAndTheRecord() {
        publish("v1.0")
        installed(service.install(entry, addons))
        File(addons, "Andere").mkdirs()
        assertNull(service.remove(entry, addons))
        assertFalse(File(addons, "Demo").exists())
        assertFalse(File(addons, "Demo_B").exists())
        assertTrue(File(addons, "Andere").isDirectory)
        assertNull(AddonManifest.read(addons)["demo"])
        assertTouchForeverUntouched()
    }

    @Test
    fun oversizeArchiveFailsBeforeDownload() {
        publish("v1.0")
        val small = entry.copy(maxDownloadBytes = 100)
        val reason = failed(service.install(small, addons))
        assertEquals(UiText.res(R.string.launcher_addon_pick_too_big, "Demo-v1.0.zip", 1L, 1L), reason)
        assertFalse(http.calls.any { it.contains("release-assets") })
        assertFalse(File(addons, "Demo").exists())
    }

    @Test
    fun maliciousArchiveChangesNothing() {
        publish("v1.0")
        installed(service.install(entry, addons))
        val evil = zipBytes("v6.6.6", "Demo/../../../TouchForever/TouchForever.toc" to "HACK")
        publish("v6.6.6", evil)
        File(tmp.root, "cache").listFiles()!!.forEach { it.setLastModified(clock - AddonCache.TTL_MS - 1) }
        failed(service.install(entry, addons))
        assertEquals("v=v1.0", File(addons, "Demo/Demo.lua").readText())
        assertEquals("v1.0", AddonManifest.read(addons)["demo"]!!.tag)
        assertTouchForeverUntouched()
    }

    @Test
    fun archiveWithAnUnlistedFolderIsRefused() {
        publish("v1.0", zipBytes("v1.0", "Fremd/Fremd.toc" to "x"))
        assertEquals(UiText.res(R.string.launcher_addon_zip_unexpected_folder, "Fremd"), failed(service.install(entry, addons)))
        assertFalse(File(addons, "Fremd").exists())
        assertFalse(File(addons, "Demo").exists())
    }

    @Test
    fun downloadMustComeFromAGitHubHost() {
        publish("v1.0")
        http.redirect("https://github.com/o/demo/releases/download/v1.0/Demo-v1.0.zip", "https://evil.example/Demo.zip")
        http.bytes("https://evil.example/Demo.zip", zipBytes("v1.0"))
        failed(service.install(entry, addons))
        assertFalse(http.calls.contains("https://evil.example/Demo.zip"))
        assertFalse(File(addons, "Demo").exists())
    }

    // ---- WoWInterface ----------------------------------------------------------------------------------------------------

    private val wowi = entry.copy(id = "wowi", source = AddonSource.WowInterface("123"))

    private fun publishWowi(zip: ByteArray, md5: String) {
        http.text(
            "https://api.mmoui.com/v3/game/WOW/filedetails/123.json",
            """[{"UID":"123","UIName":"Demo","UIVersion":"2.5","UIMD5":"$md5","UIDownload":"https://cdn.wowinterface.com/downloads/getfile.php?id=123"}]""",
        )
        http.bytes("https://cdn.wowinterface.com/downloads/getfile.php?id=123", zip)
    }

    @Test
    fun wowInterfaceInstallChecksTheMd5OfThePage() {
        val zip = zipBytes("2.5")
        val md5 = AddonFetch.hex(MessageDigest.getInstance("MD5").digest(zip))
        publishWowi(zip, md5)
        assertEquals("2.5", installed(service.install(wowi, addons)).tag)
        assertTrue(File(addons, "Demo/Demo.toc").isFile)
    }

    @Test
    fun wowInterfaceWithWrongMd5IsNotInstalled() {
        publishWowi(zipBytes("2.5"), "0".repeat(32))
        assertEquals(UiText.res(R.string.launcher_addon_checksum_mismatch), failed(service.install(wowi, addons)))
        assertFalse(File(addons, "Demo").exists())
    }

    // ---- Liste ---------------------------------------------------------------------------------------------------------------

    @Test
    fun statusesCoverTheWholeCatalogAndNeverListTouchForever() {
        publish("v1.0")
        installed(service.install(entry, addons))
        val other = entry.copy(id = "zwei", folders = listOf("Zwei"))
        val catalog = AddonCatalog(1, "x", listOf(entry, other))
        val list = service.statuses(catalog, addons, mapOf("demo" to "v1.1"))
        assertEquals(listOf("demo", "zwei"), list.map { it.entry.id })
        assertEquals(AddonInstallState.UPDATE_AVAILABLE, list[0].state)
        assertEquals(AddonInstallState.NOT_INSTALLED, list[1].state)
        assertTrue(list.none { it.entry.folders.any { f -> AddonReserved.isReserved(f) } })
    }
}
