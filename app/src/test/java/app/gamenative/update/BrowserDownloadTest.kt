package app.gamenative.update

import app.gamenative.update.BrowserDownload.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadTest {
    private val gitea = RepoRef(SourceKind.Gitea, "https", "git.example.org", "owner", "repo")
    private val github = RepoRef(SourceKind.GitHub, "https", "github.com", "owner", "repo")
    private val fakeCredential = "tok-1234567890abcdef"

    private fun info(url: String, tag: String = "v2.3.2") =
        UpdateInfo(290, "2.3.2", "app.apk", url, "", 10L, "", tag)

    @Test
    fun officialSourceOpensTheApk() {
        val apk = "https://github.com/owner/repo/releases/download/v2.3.2/app.apk"
        assertEquals(Target.Apk(apk), BrowserDownload.choose(info(apk), null, tester = false, token = null, officialApkUrl = apk))
    }

    @Test
    fun officialSourceWithoutAnHttpsAddressOpensNothing() {
        assertNull(BrowserDownload.choose(info(""), null, false, null, null))
        assertNull(BrowserDownload.choose(info(""), null, false, null, "http://example.org/app.apk"))
    }

    @Test
    fun publicCustomSourceOpensTheApk() {
        val apk = "https://git.example.org/owner/repo/releases/download/v2.3.2/app.apk"
        assertEquals(Target.Apk(apk), BrowserDownload.choose(info(apk), gitea, tester = false, token = null, officialApkUrl = null))
        assertEquals(Target.Apk(apk), BrowserDownload.choose(info(apk), gitea, tester = false, token = "  ", officialApkUrl = null))
    }

    @Test
    fun privateSourceOpensTheReleasePage() {
        val apk = "https://git.example.org/owner/repo/releases/download/v2.3.2/app.apk"
        assertEquals(
            Target.ReleasePage("https://git.example.org/owner/repo/releases/tag/v2.3.2"),
            BrowserDownload.choose(info(apk), gitea, tester = false, token = fakeCredential, officialApkUrl = null),
        )
    }

    @Test
    fun testerSourceOpensTheReleasePageEvenWithoutToken() {
        assertEquals(
            Target.ReleasePage("https://github.com/owner/repo/releases/tag/v2.3.2"),
            BrowserDownload.choose(info("https://github.com/x.apk"), github, tester = true, token = null, officialApkUrl = null),
        )
    }

    @Test
    fun aGithubApiAddressWithTokenIsNeverOpened() {
        val api = "https://api.github.com/repos/owner/repo/releases/assets/42"
        val target = BrowserDownload.choose(info(api), github, tester = false, token = fakeCredential, officialApkUrl = null)
        assertTrue(target is Target.ReleasePage)
        assertFalse(target!!.url.contains("api.github.com"))
    }

    @Test
    fun theTokenIsNeverPartOfAnAddress() {
        for (tester in listOf(false, true)) {
            for (ref in listOf(gitea, github)) {
                val target = BrowserDownload.choose(info("https://git.example.org/a.apk"), ref, tester, fakeCredential, null)!!
                assertFalse(target.url.contains(fakeCredential))
                assertFalse(target.url.contains("token", ignoreCase = true))
            }
        }
        // An APK address that carries the token is refused.
        assertNull(BrowserDownload.choose(info("https://git.example.org/a.apk?access_token=$fakeCredential"), gitea, false, null, null))
    }

    @Test
    fun aTokenInTheQueryOfAPublicAddressIsRefused() {
        assertNull(BrowserDownload.choose(info("https://git.example.org/a.apk?token=abc"), gitea, false, null, null))
    }

    @Test
    fun onlyHttpsAddressesAreOpened() {
        assertNull(BrowserDownload.choose(info("http://git.example.org/a.apk"), gitea, false, null, null))
        assertNull(BrowserDownload.choose(info("https://user:pw@git.example.org/a.apk"), gitea, false, null, null))
        assertNull(BrowserDownload.choose(info("javascript:alert(1)"), gitea, false, null, null))
    }

    @Test
    fun theTagIsEncodedAndAMissingTagGivesTheReleaseList() {
        assertEquals(
            "https://git.example.org/owner/repo/releases/tag/v2.3.3%20touch%2F9",
            BrowserDownload.choose(info("https://git.example.org/a.apk", tag = "v2.3.3 touch/9"), gitea, true, null, null)!!.url,
        )
        assertEquals(
            "https://git.example.org/owner/repo/releases",
            BrowserDownload.choose(info("https://git.example.org/a.apk", tag = ""), gitea, true, null, null)!!.url,
        )
    }
}
