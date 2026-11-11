package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateSourceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sha = "b".repeat(64)
    private val gitea = RepoRef(SourceKind.Gitea, "https", "git.example.org", "owner", "repo")
    private val github = RepoRef(SourceKind.GitHub, "https", "github.com", "owner", "repo")

    private fun ok(kind: SourceKind, text: String): RepoRef = (UpdateSourceLogic.parseRepo(kind, text) as RepoParse.Ok).ref

    private fun invalid(kind: SourceKind, text: String): UiText = (UpdateSourceLogic.parseRepo(kind, text) as RepoParse.Invalid).reason

    // ---- address ------------------------------------------------------------------------------------------------

    @Test
    fun addressIsParsedFromHostOwnerRepo() {
        assertEquals(gitea, ok(SourceKind.Gitea, "https://git.example.org/owner/repo"))
        assertEquals(gitea, ok(SourceKind.Gitea, "  HTTPS://Git.Example.org/owner/repo/  "))
        assertEquals(gitea, ok(SourceKind.Gitea, "https://git.example.org/owner/repo.git"))
        assertEquals("git.example.org:3000", ok(SourceKind.Gitea, "https://git.example.org:3000/owner/repo").authority)
        assertEquals("https://git.example.org/owner/repo", ok(SourceKind.Gitea, "https://git.example.org/owner/repo.git").displayUrl)
    }

    @Test
    fun addressRefusesEverythingButHttpsHostOwnerRepo() {
        val notHttps = UiText.res(R.string.launcher_source_err_not_https)
        val malformed = UiText.res(R.string.launcher_source_err_malformed)
        assertEquals(notHttps, invalid(SourceKind.Gitea, "http://git.example.org/owner/repo"))
        assertEquals(notHttps, invalid(SourceKind.Gitea, "ftp://git.example.org/owner/repo"))
        assertEquals(malformed, invalid(SourceKind.Gitea, ""))
        assertEquals(malformed, invalid(SourceKind.Gitea, "git.example.org/owner/repo"))
        assertEquals(malformed, invalid(SourceKind.Gitea, "https://git.example.org/owner"))
        assertEquals(malformed, invalid(SourceKind.Gitea, "https://git.example.org/owner/repo/releases"))
        assertEquals(malformed, invalid(SourceKind.Gitea, "https://user:pw" + "@" + "git.example.org/owner/repo"))
        assertEquals(malformed, invalid(SourceKind.Gitea, "https://git.example.org/owner/repo?x=1"))
        assertEquals(malformed, invalid(SourceKind.Gitea, "https://git.example.org/owner/repo#a"))
        assertEquals(malformed, invalid(SourceKind.Gitea, "https://git.example.org/../repo"))
        assertEquals(malformed, invalid(SourceKind.Gitea, "https://git.example.org/ow ner/repo"))
    }

    @Test
    fun releasesApiPathsPerServerKind() {
        assertEquals("https://git.example.org/api/v1/repos/owner/repo/releases?limit=20", gitea.releasesUrl())
        assertEquals("https://api.github.com/repos/owner/repo/releases?per_page=20", github.releasesUrl())
        val enterprise = github.copy(authority = "ghe.example.org")
        assertEquals("https://ghe.example.org/api/v3/repos/owner/repo/releases?per_page=20", enterprise.releasesUrl())
    }

    // ---- token rules ---------------------------------------------------------------------------------------------

    @Test
    fun tokenGoesOnlyToTheRepositoryHosts() {
        assertTrue(gitea.isTokenTarget("https://git.example.org/owner/repo/releases/download/v1/a.apk"))
        assertTrue(github.isTokenTarget("https://api.github.com/repos/owner/repo/releases/assets/7"))
        assertTrue(github.isTokenTarget("https://github.com/owner/repo/releases/download/v1/a.apk"))
        assertFalse(gitea.isTokenTarget("https://other.example.org/a.apk"))
        assertFalse(gitea.isTokenTarget("https://git.example.org.evil.net/a.apk"))
        assertFalse(gitea.isTokenTarget("https://evil.net/git.example.org/a.apk"))
        assertFalse(gitea.isTokenTarget("http://git.example.org/a.apk"))
        assertFalse(gitea.isTokenTarget("https://git.example.org:8443/a.apk"))
        assertFalse(gitea.isTokenTarget("not a url"))
    }

    @Test
    fun requestHeadersCarryTheTokenOnlyWhereAllowed() {
        val own = UpdateSourceLogic.requestHeaders(gitea, " tok123 ", gitea.releasesUrl(), wantFile = false)
        assertEquals("token tok123", own["Authorization"])
        assertEquals("application/json", own["Accept"])
        val foreign = UpdateSourceLogic.requestHeaders(gitea, "tok123", "https://cdn.example.net/a.apk", wantFile = true)
        assertNull(foreign["Authorization"])
        assertNull(UpdateSourceLogic.requestHeaders(gitea, null, gitea.releasesUrl(), false)["Authorization"])
        assertNull(UpdateSourceLogic.requestHeaders(gitea, "  ", gitea.releasesUrl(), false)["Authorization"])
    }

    @Test
    fun githubApiAssetIsLoadedAsFile() {
        val url = "https://api.github.com/repos/owner/repo/releases/assets/7"
        assertEquals("application/octet-stream", UpdateSourceLogic.requestHeaders(github, "t", url, wantFile = true)["Accept"])
        assertEquals("application/json", UpdateSourceLogic.requestHeaders(github, "t", url, wantFile = false)["Accept"])
        assertEquals(
            "application/json",
            UpdateSourceLogic.requestHeaders(gitea, "t", "https://git.example.org/api/v1/x/releases/assets/7", wantFile = true)["Accept"],
        )
    }

    @Test
    fun tokenIsMaskedAndRedacted() {
        assertEquals("", UpdateSourceLogic.maskToken(null))
        assertEquals("", UpdateSourceLogic.maskToken(""))
        assertEquals("•••••", UpdateSourceLogic.maskToken("abcde"))
        assertEquals("•".repeat(12), UpdateSourceLogic.maskToken("x".repeat(80)))
        assertFalse(UpdateSourceLogic.maskToken("secret-token").contains("secret"))
        assertEquals("GET https://h/?t=*** failed", UpdateSourceLogic.redact("GET https://h/?t=secret failed", " secret "))
        assertEquals("nothing", UpdateSourceLogic.redact("nothing", null))
        assertEquals("nothing", UpdateSourceLogic.redact("nothing", ""))
    }

    // ---- releases ------------------------------------------------------------------------------------------------

    private fun asset(name: String, id: Int = 1, size: Long = 1000, digest: String = "", host: String = "git.example.org") =
        """{"id":$id,"name":"$name","size":$size,"browser_download_url":"https://$host/owner/repo/releases/download/v1/$name",
           "url":"https://api.$host/repos/owner/repo/releases/assets/$id","digest":"$digest"}"""

    private fun release(tag: String, assets: List<String>, draft: Boolean = false, pre: Boolean = false, body: String = "Notes $tag") =
        """{"tag_name":"$tag","name":"R $tag","draft":$draft,"prerelease":$pre,"body":"$body","assets":[${assets.joinToString(",")}]}"""

    private fun releases(vararg entries: String) = UpdateSourceLogic.parseReleases("[${entries.joinToString(",")}]")!!

    @Test
    fun releaseListIsParsedAndGarbageIsRefused() {
        val list = releases(release("v2", listOf(asset("a.apk", 1), asset("latest.json", 2))))
        assertEquals(1, list.size)
        assertEquals("v2", list[0].tag)
        assertEquals(listOf("a.apk", "latest.json"), list[0].assets.map { it.name })
        assertNotNull(UpdateSourceLogic.feedAsset(list[0]))
        assertNull(UpdateSourceLogic.parseReleases(null))
        assertNull(UpdateSourceLogic.parseReleases("<html>login</html>"))
        assertNull(UpdateSourceLogic.parseReleases("""{"message":"Not Found"}"""))
        assertEquals(emptyList<ReleaseEntry>(), UpdateSourceLogic.parseReleases("[]"))
    }

    @Test
    fun newestUsableReleaseIsPicked() {
        val list = releases(
            release("v5", listOf(asset("a.apk")), draft = true),
            release("v4-rc", listOf(asset("a.apk")), pre = true),
            release("v3", listOf(asset("notes.txt"))),
            release("v2", listOf(asset("a.apk"))),
            release("v1", listOf(asset("a.apk"))),
        )
        assertEquals("v2", UpdateSourceLogic.pickRelease(list, includePrereleases = false)?.tag)
        assertEquals("v4-rc", UpdateSourceLogic.pickRelease(list, includePrereleases = true)?.tag)
        assertNull(UpdateSourceLogic.pickRelease(releases(release("v1", listOf(asset("x.zip")))), true))
        assertNull(UpdateSourceLogic.pickRelease(emptyList(), true))
    }

    private fun feedJson(name: String = "2.4.0", file: String = "b.apk", code: Int = 300, bytes: Long = 5000) =
        """{"version":"$name","datei":"$file","url":"https://elsewhere.example.net/$file","sha256":"$sha","bytes":$bytes,"text":"Feed notes","versionCode":$code}"""

    @Test
    fun feedDecidesTheInfoButTheDownloadAddressComesFromTheReleaseAsset() {
        val rel = releases(release("v2.4.0", listOf(asset("a.apk", 1), asset("b.apk", 2), asset("latest.json", 3))))[0]
        val found = UpdateSourceLogic.infoFor(gitea, rel, feedJson(), hasToken = false) as SourceResult.Found
        assertEquals("v2.4.0", found.tag)
        assertEquals(300, found.info.versionCode)
        assertEquals("2.4.0", found.info.versionName)
        assertEquals(sha, found.info.sha256)
        assertEquals(5000L, found.info.size)
        assertEquals("Feed notes", found.info.notes)
        assertEquals("b.apk", found.info.file)
        assertEquals("https://git.example.org/owner/repo/releases/download/v1/b.apk", found.info.url)
    }

    @Test
    fun feedNamingNoAssetFallsBackToTheFirstApk() {
        val rel = releases(release("v1", listOf(asset("a.apk", 1))))[0]
        val found = UpdateSourceLogic.infoFor(gitea, rel, feedJson(file = "missing.apk"), false) as SourceResult.Found
        assertEquals("a.apk", found.info.file)
    }

    @Test
    fun brokenFeedIsReportedNotIgnored() {
        val rel = releases(release("v1", listOf(asset("a.apk", 1))))[0]
        val bad = UpdateSourceLogic.infoFor(gitea, rel, "{\"version\":\"1\"}", false)
        assertTrue(bad is SourceResult.Failed)
        assertTrue(UpdateSourceLogic.infoFor(gitea, rel, "no json", false) is SourceResult.Failed)
    }

    @Test
    fun withoutFeedTheInfoComesFromTagAssetAndDigest() {
        val rel = releases(release("v2.4.0-fold8", listOf(asset("WoW.apk", 1, 7777, digest = "sha256:" + "C".repeat(64)))))[0]
        val found = UpdateSourceLogic.infoFor(gitea, rel, null, false) as SourceResult.Found
        assertEquals(0, found.info.versionCode)
        assertEquals("2.4.0-fold8", found.info.versionName)
        assertEquals(7777L, found.info.size)
        assertEquals("c".repeat(64), found.info.sha256)
        assertEquals("Notes v2.4.0-fold8", found.info.notes)
        val noDigest = releases(release("v1", listOf(asset("WoW.apk", 1, 10))))[0]
        assertEquals("", (UpdateSourceLogic.infoFor(gitea, noDigest, null, false) as SourceResult.Found).info.sha256)
        val noSize = releases(release("v1", listOf(asset("WoW.apk", 1, 0))))[0]
        assertTrue(UpdateSourceLogic.infoFor(gitea, noSize, null, false) is SourceResult.Failed)
    }

    @Test
    fun githubWithTokenLoadsThroughTheApiAddress() {
        val rel = releases(release("v1", listOf(asset("a.apk", 9, host = "github.com"))))[0]
        val plain = UpdateSourceLogic.infoFor(github, rel, null, hasToken = false) as SourceResult.Found
        assertEquals("https://github.com/owner/repo/releases/download/v1/a.apk", plain.info.url)
        val withToken = UpdateSourceLogic.infoFor(github, rel, null, hasToken = true) as SourceResult.Found
        assertEquals("https://api.github.com/repos/owner/repo/releases/assets/9", withToken.info.url)
        // Gitea always uses the download address, token or not.
        val fRel = releases(release("v1", listOf(asset("a.apk", 9))))[0]
        assertTrue((UpdateSourceLogic.infoFor(gitea, fRel, null, true) as SourceResult.Found).info.url.contains("/releases/download/"))
    }

    @Test
    fun nonHttpsAssetAddressIsRefused() {
        val json = """[{"tag_name":"v1","assets":[{"name":"a.apk","size":10,"browser_download_url":"http://git.example.org/a.apk"}]}]"""
        val rel = UpdateSourceLogic.parseReleases(json)!![0]
        assertEquals(
            UiText.res(R.string.launcher_update_apk_address_unusable),
            (UpdateSourceLogic.infoFor(gitea, rel, null, false) as SourceResult.Failed).reason,
        )
    }

    // ---- versions, cache, checksum ------------------------------------------------------------------------------------

    @Test
    fun onlyAHigherVersionCountsWithoutAndWithVersionCode() {
        val byName = UpdateInfo(0, "2.4.0", "a.apk", "", "", 1, "")
        assertTrue(UpdateLogic.isNewer(byName, 244, "2.3.3-fold7.1"))
        assertFalse(UpdateLogic.isNewer(byName, 244, "2.4.0"))
        assertFalse(UpdateLogic.isNewer(byName, 244, "2.5.0"))
        val byCode = UpdateInfo(300, "9.9", "a.apk", "", "", 1, "")
        assertTrue(UpdateLogic.isNewer(byCode, 244, "2.3.3"))
        assertFalse(UpdateLogic.isNewer(byCode, 300, "1.0"))
    }

    @Test
    fun cachedFeedOfACustomSourceSurvivesWithoutChecksum() {
        val info = UpdateInfo(0, "2.4.0", "a.apk", "https://git.example.org/a.apk", "", 123, "Notes\nline")
        val json = UpdateSourceLogic.toFeedJson(info)
        assertTrue(UpdateLogic.parseFeed(json) is FeedResult.Invalid)
        assertEquals(info, (UpdateLogic.parseFeed(json, requireSha = false) as FeedResult.Ok).info)
        val withSha = info.copy(sha256 = sha, versionCode = 7)
        assertEquals(withSha, (UpdateLogic.parseFeed(UpdateSourceLogic.toFeedJson(withSha)) as FeedResult.Ok).info)
        val bad = UpdateSourceLogic.toFeedJson(info.copy(sha256 = "zz"))
        assertTrue(UpdateLogic.parseFeed(bad, requireSha = false) is FeedResult.Invalid)
    }

    @Test
    fun blankChecksumChecksOnlyTheSize() {
        val file = File(tmp.root, "x.apk").apply { writeText("abc") }
        assertNull(UpdateLogic.verifyFile(file, 3, ""))
        assertNotNull(UpdateLogic.verifyFile(file, 4, ""))
        assertNotNull(UpdateLogic.verifyFile(file, 3, "a".repeat(64)))
    }

    @Test
    fun sourceSettingsNeverReachTheBackup() {
        for (key in listOf("source_custom", "source_kind", "source_url", "source_prerelease", "source_token")) {
            assertNull(key, app.gamenative.backup.BackupAllowlist.find(app.gamenative.backup.BackupAllowlist.FILE_UPDATE, key))
        }
        val updateKeys = app.gamenative.backup.BackupAllowlist.ALL.filter { it.file == app.gamenative.backup.BackupAllowlist.FILE_UPDATE }.map { it.key }
        assertEquals(listOf("auto_install"), updateKeys)
    }

    // ---- signature ---------------------------------------------------------------------------------------------------

    @Test
    fun signatureMismatchGetsItsOwnText() {
        assertTrue(UpdateSourceLogic.isSignatureMismatch("INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package app.x signatures do not match newer version"))
        assertTrue(UpdateSourceLogic.isSignatureMismatch("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES"))
        assertFalse(UpdateSourceLogic.isSignatureMismatch("INSTALL_FAILED_OLDER_SDK"))
        assertFalse(UpdateSourceLogic.isSignatureMismatch(""))
        assertEquals(
            UiText.res(R.string.launcher_update_signature_mismatch),
            UpdateLogic.rejectedText("INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match"),
        )
        assertEquals(UiText.res(R.string.launcher_update_rejected, "ABI"), UpdateLogic.rejectedText("ABI"))
    }

    @Test
    fun httpErrorsHaveClearTexts() {
        assertEquals(UiText.res(R.string.launcher_source_err_401), UpdateSourceLogic.httpFailure(401))
        assertEquals(UiText.res(R.string.launcher_source_err_403), UpdateSourceLogic.httpFailure(403))
        assertEquals(UiText.res(R.string.launcher_source_err_404), UpdateSourceLogic.httpFailure(404))
        assertEquals(UiText.res(R.string.launcher_update_http_error, 500), UpdateSourceLogic.httpFailure(500))
    }

    // ---- fetcher (no network: answers come from an interceptor) -------------------------------------------------------

    private class Fake(val answers: Map<String, Pair<Int, String>>) {
        val seen = mutableListOf<Request>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                seen += request
                val key = request.url.toString()
                val (code, body) = answers[key] ?: (404 to "")
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            },
        ).build()
    }

    private val listUrl = gitea.releasesUrl()
    private val source = CustomSource(gitea, includePrereleases = false)

    @Test
    fun fetcherReadsReleaseAndFeedAndSendsTheTokenToTheRepositoryOnly() {
        val feedAssetUrl = "https://git.example.org/owner/repo/releases/download/v1/latest.json"
        val list = "[" + release("v1", listOf(asset("a.apk", 1), asset("latest.json", 2))) + "]"
        val fake = Fake(mapOf(listUrl to (200 to list), feedAssetUrl to (200 to feedJson(file = "a.apk"))))
        val result = ReleaseFetcher(fake.client, "UA/1").fetch(source, "tok").let { it as SourceResult.Found }
        assertEquals("2.4.0", result.info.versionName)
        assertEquals(2, fake.seen.size)
        assertTrue(fake.seen.all { it.header("Authorization") == "token tok" && it.header("User-Agent") == "UA/1" })
    }

    @Test
    fun fetcherWithoutTokenSendsNoAuthorization() {
        val list = "[" + release("v1", listOf(asset("a.apk", 1))) + "]"
        val fake = Fake(mapOf(listUrl to (200 to list)))
        assertTrue(ReleaseFetcher(fake.client, "UA").fetch(source, null) is SourceResult.Found)
        assertNull(fake.seen.single().header("Authorization"))
    }

    @Test
    fun fetcherMapsHttpErrorsAndBadAnswers() {
        fun failure(code: Int, body: String = ""): UiText =
            (ReleaseFetcher(Fake(mapOf(listUrl to (code to body))).client, "UA").fetch(source, "t") as SourceResult.Failed).reason
        assertEquals(UpdateSourceLogic.httpFailure(401), failure(401))
        assertEquals(UpdateSourceLogic.httpFailure(404), failure(404))
        assertEquals(UiText.res(R.string.launcher_source_err_bad_json), failure(200, "<html>"))
        assertEquals(UiText.res(R.string.launcher_source_err_no_release), failure(200, "[]"))
        assertEquals(UiText.res(R.string.launcher_source_err_no_release), failure(200, "[" + release("v1", listOf(asset("a.zip"))) + "]"))
    }

    @Test
    fun preReleasesComeOnlyWhenAskedFor() {
        val list = "[" + release("v2-rc", listOf(asset("a.apk")), pre = true) + "," + release("v1", listOf(asset("a.apk"))) + "]"
        val fake = Fake(mapOf(listUrl to (200 to list)))
        val stable = ReleaseFetcher(fake.client, "UA").fetch(source, null) as SourceResult.Found
        assertEquals("v1", stable.tag)
        val withPre = ReleaseFetcher(fake.client, "UA").fetch(source.copy(includePrereleases = true), null) as SourceResult.Found
        assertEquals("v2-rc", withPre.tag)
    }

    @Test
    fun aNetworkErrorTextNeverEchoesTheToken() {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { throw java.io.IOException("failed for https://h/?t=secret99") }).build()
        val reason = (ReleaseFetcher(client, "UA").fetch(source, "secret99") as SourceResult.Failed).reason
        assertFalse(reason.toString().contains("secret99"))
    }
}
