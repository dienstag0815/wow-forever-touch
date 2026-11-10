package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddonFetchTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val http = FakeAddonHttp()
    private val gh = AddonHosts.GITHUB
    private val now = 1_000_000_000_000L

    private fun failure(block: () -> Unit): AddonNetworkException {
        try {
            block()
        } catch (e: AddonNetworkException) {
            return e
        }
        fail("hätte scheitern müssen")
        throw IllegalStateException()
    }

    private fun sha(data: ByteArray) = AddonFetch.hex(MessageDigest.getInstance("SHA-256").digest(data))

    // ---- Text ------------------------------------------------------------------------------------------------------

    @Test
    fun textIsFetched() {
        http.text("https://api.github.com/a", "hallo")
        assertEquals("hallo", AddonFetch.getText(http, "https://api.github.com/a", gh))
    }

    @Test
    fun hostNotOnTheListIsNeverContacted() {
        http.text("https://evil.example/a", "x")
        val e = failure { AddonFetch.getText(http, "https://evil.example/a", gh) }
        assertEquals(UiText.res(R.string.launcher_addon_net_host_not_allowed, "evil.example"), e.text)
        assertTrue(http.calls.isEmpty())
        failure { AddonFetch.getText(http, "http://api.github.com/a", gh) }
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun wowInterfaceHostIsRefusedForAGitHubEntry() {
        http.text("https://cdn.wowinterface.com/a", "x")
        failure { AddonFetch.getText(http, "https://cdn.wowinterface.com/a", AddonHosts.GITHUB) }
        assertEquals("x", AddonFetch.getText(http, "https://cdn.wowinterface.com/a", AddonHosts.WOWINTERFACE))
    }

    @Test
    fun textSizeIsLimited() {
        http.text("https://api.github.com/big", "x".repeat(5000))
        failure { AddonFetch.getText(http, "https://api.github.com/big", gh, maxBytes = 1000) }
        http.bytes("https://api.github.com/nolength", ByteArray(5000), declareLength = false)
        failure { AddonFetch.getText(http, "https://api.github.com/nolength", gh, maxBytes = 1000) }
    }

    // ---- Weiterleitungen --------------------------------------------------------------------------------------------

    @Test
    fun redirectsAreFollowedAcrossAllowedHosts() {
        http.redirect("https://github.com/o/r/releases/download/v1/a.zip", "https://release-assets.githubusercontent.com/x?sig=1")
        http.text("https://release-assets.githubusercontent.com/x?sig=1", "daten")
        assertEquals("daten", AddonFetch.getText(http, "https://github.com/o/r/releases/download/v1/a.zip", gh))
    }

    @Test
    fun relativeRedirectsAreResolved() {
        http.redirect("https://api.github.com/a/b", "/c/d", code = 301)
        http.text("https://api.github.com/c/d", "ok")
        assertEquals("ok", AddonFetch.getText(http, "https://api.github.com/a/b", gh))
    }

    @Test
    fun redirectToForeignHostOrHttpIsRefused() {
        http.redirect("https://github.com/a", "https://evil.example/payload")
        http.text("https://evil.example/payload", "böse")
        failure { AddonFetch.getText(http, "https://github.com/a", gh) }
        assertEquals(listOf("https://github.com/a"), http.calls)

        http.calls.clear()
        http.redirect("https://github.com/b", "http://objects.githubusercontent.com/x")
        failure { AddonFetch.getText(http, "https://github.com/b", gh) }
        assertEquals(listOf("https://github.com/b"), http.calls)

        http.calls.clear()
        http.redirect("https://github.com/c", "https://github.com@evil.example/x")
        failure { AddonFetch.getText(http, "https://github.com/c", gh) }
    }

    @Test
    fun redirectLoopsAndMissingTargetsStop() {
        http.redirect("https://github.com/loop", "https://github.com/loop")
        val e = failure { AddonFetch.getText(http, "https://github.com/loop", gh) }
        assertEquals(UiText.res(R.string.launcher_addon_net_too_many_redirects), e.text)
        assertEquals(AddonFetch.MAX_REDIRECTS + 1, http.calls.size)
        http.replies["https://github.com/nolocation"] = FakeAddonHttp.Reply(302)
        failure { AddonFetch.getText(http, "https://github.com/nolocation", gh) }
    }

    // ---- Fehlercodes und GitHub-Limit ---------------------------------------------------------------------------------

    @Test
    fun httpErrorsGetClearMessages() {
        assertEquals(UiText.res(R.string.launcher_addon_net_not_found), failure { AddonFetch.getText(http, "https://github.com/missing", gh) }.text)
        http.text("https://github.com/boom", "x", code = 500)
        assertEquals(UiText.res(R.string.launcher_addon_net_server_error, 500), failure { AddonFetch.getText(http, "https://github.com/boom", gh) }.text)
        http.text("https://github.com/forbidden", "x", code = 403)
        val plain = failure { AddonFetch.getText(http, "https://github.com/forbidden", gh) }
        assertFalse(plain.rateLimited)
    }

    @Test
    fun gitHubRateLimitIsExplainedWithTheWaitTime() {
        val reset = (now / 1000) + 25 * 60
        http.text(
            "https://api.github.com/limited", "{}", code = 403,
            headers = mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to reset.toString()),
        )
        val e = failure { AddonFetch.getText(http, "https://api.github.com/limited", gh, now = { now }) }
        assertTrue(e.rateLimited)
        assertEquals(UiText.plural(R.plurals.launcher_addon_net_rate_limited_minutes, 25), e.text)
        http.text("https://api.github.com/limited2", "{}", code = 429)
        assertTrue(failure { AddonFetch.getText(http, "https://api.github.com/limited2", gh, now = { now }) }.rateLimited)
    }

    // ---- Download ---------------------------------------------------------------------------------------------------

    @Test
    fun downloadWritesTheFileAndComputesSha256() {
        val data = ByteArray(300_000) { (it % 251).toByte() }
        http.bytes("https://github.com/o/r/a.zip", data)
        val dest = File(tmp.root, "x/a.zip")
        val progress = ArrayList<Long>()
        val result = AddonFetch.download(http, "https://github.com/o/r/a.zip", gh, dest, 1_000_000, { done, _ -> progress += done })
        assertEquals(data.size.toLong(), result.bytes)
        assertEquals(sha(data), result.sha256)
        assertTrue(dest.readBytes().contentEquals(data))
        assertFalse(File(dest.path + ".part").exists())
        assertEquals(data.size.toLong(), progress.last())
    }

    @Test
    fun downloadFollowsGitHubsRedirectToTheAssetHost() {
        val data = "zipdaten".toByteArray()
        http.redirect("https://github.com/o/r/a.zip", "https://release-assets.githubusercontent.com/blob?sp=r&sig=a%2Bb")
        http.bytes("https://release-assets.githubusercontent.com/blob?sp=r&sig=a%2Bb", data)
        val dest = File(tmp.root, "a.zip")
        assertEquals(sha(data), AddonFetch.download(http, "https://github.com/o/r/a.zip", gh, dest, 1000).sha256)
    }

    @Test
    fun downloadLargerThanTheLimitLeavesNothing() {
        val dest = File(tmp.root, "big.zip")
        http.bytes("https://github.com/big", ByteArray(5000))
        failure { AddonFetch.download(http, "https://github.com/big", gh, dest, 1000) }
        assertFalse(dest.exists())
        assertFalse(File(dest.path + ".part").exists())
        // Ohne Content-Length wird erst beim Lesen abgebrochen.
        http.bytes("https://github.com/big2", ByteArray(5000), declareLength = false)
        failure { AddonFetch.download(http, "https://github.com/big2", gh, dest, 1000) }
        assertFalse(dest.exists())
        assertFalse(File(dest.path + ".part").exists())
    }

    @Test
    fun aFailedDownloadKeepsAnOlderFile() {
        val dest = File(tmp.root, "keep.zip")
        dest.writeText("alt")
        failure { AddonFetch.download(http, "https://github.com/missing", gh, dest, 1000) }
        assertEquals("alt", dest.readText())
    }

    @Test
    fun foreignHostDownloadIsRefusedBeforeAnyRequest() {
        http.bytes("https://evil.example/a.zip", ByteArray(10))
        failure { AddonFetch.download(http, "https://evil.example/a.zip", gh, File(tmp.root, "a.zip"), 1000) }
        assertTrue(http.calls.isEmpty())
    }

    // ---- Zwischenspeicher -----------------------------------------------------------------------------------------------

    @Test
    fun cacheKeepsTextForOneHour() {
        var clock = now
        val cache = AddonCache(File(tmp.root, "cache"), now = { clock })
        assertNull(cache.fresh("k"))
        cache.put("gh-o-r/latest", "{\"a\":1}")
        val file = File(tmp.root, "cache").listFiles()!!.single()
        file.setLastModified(now)
        assertEquals("{\"a\":1}", cache.fresh("gh-o-r/latest"))
        clock = now + AddonCache.TTL_MS - 1
        assertEquals("{\"a\":1}", cache.fresh("gh-o-r/latest"))
        clock = now + AddonCache.TTL_MS
        assertNull(cache.fresh("gh-o-r/latest"))
        assertEquals("veraltet bleibt für den Rückfall lesbar", "{\"a\":1}", cache.stale("gh-o-r/latest"))
        assertNull(cache.stale("andere"))
    }

    @Test
    fun cacheKeysCannotEscapeTheFolder() {
        val cache = AddonCache(File(tmp.root, "cache"))
        cache.put("../../evil", "x")
        assertTrue(File(tmp.root, "cache").listFiles()!!.single().canonicalPath.startsWith(File(tmp.root, "cache").canonicalPath))
        assertFalse(File(tmp.root.parentFile, "evil.json").exists())
    }
}
