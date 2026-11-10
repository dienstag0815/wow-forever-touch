package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateLogicTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sha = "a".repeat(64)
    private val apkUrl = "https://downloads.example.org/wowforever/WoW-Forever-Fold-2.3.3-fold7.1.apk"
    private val feedUrl = "https://downloads.example.org/wowforever/latest.json"
    private val abcSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private fun feed(
        version: String = "\"version\":\"2.3.3-fold7.1\"",
        file: String = "\"datei\":\"WoW-Forever-Fold-2.3.3-fold7.1.apk\"",
        url: String = "\"url\":\"$apkUrl\"",
        hash: String = "\"sha256\":\"$sha\"",
        bytes: String = "\"bytes\":155788181",
        time: String = "\"zeit\":\"2026-11-08T12:00:00Z\"",
        notes: String = "\"text\":\"Neu: Selbst-Update\"",
        code: String = "\"versionCode\":244",
    ) = listOf(version, file, url, hash, bytes, time, notes, code).filter { it.isNotEmpty() }.joinToString(",", "{", "}")

    private fun info(code: Int = 244, name: String = "2.3.3-fold7.1") =
        UpdateInfo(code, name, "a.apk", "", sha, 10, "")

    private fun fileOnly(ref: String) = UpdateInfo(0, "1", ref, "", sha, 1, "")

    // ---- Feed-Parser ------------------------------------------------------------------------------------------

    @Test
    fun validFeedIsParsed() {
        val result = UpdateLogic.parseFeed(feed())
        assertTrue(result is FeedResult.Ok)
        val info = (result as FeedResult.Ok).info
        assertEquals(244, info.versionCode)
        assertEquals("2.3.3-fold7.1", info.versionName)
        assertEquals("WoW-Forever-Fold-2.3.3-fold7.1.apk", info.file)
        assertEquals(apkUrl, info.url)
        assertEquals(sha, info.sha256)
        assertEquals(155788181L, info.size)
        assertEquals("Neu: Selbst-Update", info.notes)
    }

    @Test
    fun versionCodeTimeAndNotesAreOptional() {
        val result = UpdateLogic.parseFeed(feed(code = "", time = "", notes = ""))
        val info = (result as FeedResult.Ok).info
        assertEquals(0, info.versionCode)
        assertEquals("", info.notes)
    }

    @Test
    fun fileOrUrlAloneIsEnough() {
        assertTrue(UpdateLogic.parseFeed(feed(url = "")) is FeedResult.Ok)
        assertTrue(UpdateLogic.parseFeed(feed(file = "")) is FeedResult.Ok)
        assertTrue(UpdateLogic.parseFeed(feed(file = "", url = "")) is FeedResult.Invalid)
    }

    @Test
    fun shaIsLowercased() {
        val info = (UpdateLogic.parseFeed(feed(hash = "\"sha256\":\"${"AB".repeat(32)}\"")) as FeedResult.Ok).info
        assertEquals("ab".repeat(32), info.sha256)
    }

    @Test
    fun missingFieldsAreRejected() {
        assertTrue(UpdateLogic.parseFeed(feed(version = "")) is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed(feed(hash = "")) is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed(feed(bytes = "")) is FeedResult.Invalid)
    }

    @Test
    fun badValuesAreRejected() {
        assertTrue(UpdateLogic.parseFeed(feed(version = "\"version\":\"  \"")) is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed(feed(hash = "\"sha256\":\"1234\"")) is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed(feed(hash = "\"sha256\":\"${"z".repeat(64)}\"")) is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed(feed(bytes = "\"bytes\":0")) is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed(feed(bytes = "\"bytes\":-5")) is FeedResult.Invalid)
    }

    @Test
    fun brokenInputIsRejectedWithoutThrowing() {
        assertTrue(UpdateLogic.parseFeed(null) is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed("") is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed("   ") is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed("<html>404</html>") is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed("{\"version\":\"2.3.3-fold8\",") is FeedResult.Invalid)
        assertTrue(UpdateLogic.parseFeed("[1,2,3]") is FeedResult.Invalid)
    }

    // ---- Versionsvergleich ------------------------------------------------------------------------------------

    @Test
    fun higherVersionCodeIsNewer() {
        assertTrue(UpdateLogic.isNewer(info(code = 244), 243, "2.3.3-fold7"))
        assertTrue(UpdateLogic.isNewer(244, 243))
    }

    @Test
    fun equalOrLowerVersionCodeIsNotNewer() {
        assertFalse(UpdateLogic.isNewer(info(code = 243), 243, "2.3.3-fold7"))
        assertFalse(UpdateLogic.isNewer(info(code = 242), 243, "2.3.3-fold7"))
        assertFalse(UpdateLogic.isNewer(243, 243))
    }

    @Test
    fun versionCodeWinsOverNames() {
        // The code says "not newer" although the name looks newer: the code decides.
        assertFalse(UpdateLogic.isNewer(info(code = 243, name = "2.3.3-fold9"), 243, "2.3.3-fold7"))
    }

    @Test
    fun withoutVersionCodeNamesDecide() {
        assertTrue(UpdateLogic.isNewer(info(code = 0, name = "2.3.3-fold7.1"), 243, "2.3.3-fold7"))
        assertTrue(UpdateLogic.isNewer(info(code = 0, name = "2.3.3-fold8"), 243, "2.3.3-fold7.1"))
        assertFalse(UpdateLogic.isNewer(info(code = 0, name = "2.3.3-fold7"), 243, "2.3.3-fold7"))
        assertFalse(UpdateLogic.isNewer(info(code = 0, name = "2.3.3-fold6.2"), 243, "2.3.3-fold7"))
    }

    @Test
    fun versionNamesCompareNumerically() {
        assertTrue(UpdateLogic.compareVersionNames("2.3.3-fold7.1", "2.3.3-fold7") > 0)
        assertTrue(UpdateLogic.compareVersionNames("2.3.3-fold7", "2.3.3-fold6.2") > 0)
        assertTrue(UpdateLogic.compareVersionNames("2.3.3-fold10", "2.3.3-fold9") > 0)
        assertTrue(UpdateLogic.compareVersionNames("2.3.3-fold7.10", "2.3.3-fold7.9") > 0)
        assertTrue(UpdateLogic.compareVersionNames("2.3.4-fold1", "2.3.3-fold9") > 0)
        assertTrue(UpdateLogic.compareVersionNames("2.4.0", "2.3.9-fold9") > 0)
        assertTrue(UpdateLogic.compareVersionNames("2.3.3-fold6.2", "2.3.3-fold7") < 0)
        assertTrue(UpdateLogic.compareVersionNames("2.3.3", "2.3.3-fold7") < 0)
        assertEquals(0, UpdateLogic.compareVersionNames("2.3.3-fold7", "v2.3.3-fold7.0"))
        assertEquals(0, UpdateLogic.compareVersionNames("2.3.3-fold7", "2.3.3-fold7"))
    }

    @Test
    fun touchNamesFollowFoldNames() {
        // Schema ab Launch: 2.3.3-touch<n>.<m>; der Zahlenteil läuft weiter (touch9.0 nach fold8.30).
        assertTrue(UpdateLogic.compareVersionNames("2.3.3-touch9.0", "2.3.3-fold8.30") > 0)
        assertTrue(UpdateLogic.compareVersionNames("2.3.3-touch9.1", "2.3.3-touch9.0") > 0)
        assertTrue(UpdateLogic.compareVersionNames("2.3.3-touch10", "2.3.3-touch9.9") > 0)
        assertTrue(UpdateLogic.isNewer(info(code = 0, name = "2.3.3-touch9.0"), 283, "2.3.3-fold8.30"))
        assertFalse(UpdateLogic.isNewer(info(code = 0, name = "2.3.3-fold8.30"), 283, "2.3.3-touch9.0"))
    }

    // ---- Prüfintervall ----------------------------------------------------------------------------------------

    private val hour = 60L * 60 * 1000

    @Test
    fun neverCheckedMeansDue() {
        assertTrue(UpdateLogic.shouldCheck(lastCheckMs = 0, nowMs = 10 * hour))
    }

    @Test
    fun checkWithinSixHoursIsSkipped() {
        val last = 100 * hour
        assertFalse(UpdateLogic.shouldCheck(last, last + 1))
        assertFalse(UpdateLogic.shouldCheck(last, last + 6 * hour - 1))
    }

    @Test
    fun checkAfterSixHoursIsDue() {
        val last = 100 * hour
        assertTrue(UpdateLogic.shouldCheck(last, last + 6 * hour))
        assertTrue(UpdateLogic.shouldCheck(last, last + 30 * hour))
    }

    @Test
    fun forcedCheckIgnoresInterval() {
        val last = 100 * hour
        assertTrue(UpdateLogic.shouldCheck(last, last + 1, force = true))
    }

    @Test
    fun clockGoingBackwardsMeansDue() {
        assertTrue(UpdateLogic.shouldCheck(lastCheckMs = 100 * hour, nowMs = 50 * hour))
    }

    // ---- URL-Auflösung ----------------------------------------------------------------------------------------

    @Test
    fun feedUrlAppendsFileName() {
        assertEquals(feedUrl, UpdateLogic.feedUrl("https://downloads.example.org/wowforever/"))
        assertEquals(feedUrl, UpdateLogic.feedUrl("  https://downloads.example.org/wowforever  "))
    }

    @Test
    fun feedUrlKeepsExplicitJson() {
        assertEquals(feedUrl, UpdateLogic.feedUrl(feedUrl))
        assertEquals("https://x.test/a/feed.json", UpdateLogic.feedUrl("https://x.test/a/feed.json"))
    }

    @Test
    fun feedUrlRejectsNonHttp() {
        assertNull(UpdateLogic.feedUrl(""))
        assertNull(UpdateLogic.feedUrl("   "))
        assertNull(UpdateLogic.feedUrl("ftp://x.test/a/"))
        assertNull(UpdateLogic.feedUrl("file:///sdcard/latest.json"))
        assertNull(UpdateLogic.feedUrl("nur text"))
    }

    @Test
    fun relativeFileResolvesNextToFeed() {
        assertEquals(apkUrl, UpdateLogic.resolveApkUrl(feedUrl, fileOnly("WoW-Forever-Fold-2.3.3-fold7.1.apk")))
        assertEquals("https://downloads.example.org/wowforever/apk/a.apk", UpdateLogic.resolveApkUrl(feedUrl, fileOnly("apk/a.apk")))
        assertEquals("https://downloads.example.org/other/a.apk", UpdateLogic.resolveApkUrl(feedUrl, fileOnly("../other/a.apk")))
        assertEquals("https://downloads.example.org/a.apk", UpdateLogic.resolveApkUrl(feedUrl, fileOnly("/a.apk")))
    }

    @Test
    fun absoluteUrlWinsOverFile() {
        val i = UpdateInfo(244, "x", "andere.apk", apkUrl, sha, 1, "")
        assertEquals(apkUrl, UpdateLogic.resolveApkUrl(feedUrl, i))
    }

    @Test
    fun absoluteUrlAloneWorksEvenForOtherFeedHost() {
        val i = UpdateInfo(244, "x", "", apkUrl, sha, 1, "")
        assertEquals(apkUrl, UpdateLogic.resolveApkUrl("http://192.168.1.5:8080/latest.json", i))
    }

    @Test
    fun unusableUrlFallsBackToFileOrFails() {
        val withFile = UpdateInfo(244, "x", "a.apk", "ftp://x.test/a.apk", sha, 1, "")
        assertEquals("https://downloads.example.org/wowforever/a.apk", UpdateLogic.resolveApkUrl(feedUrl, withFile))
        val noFile = UpdateInfo(244, "x", "", "file:///sdcard/a.apk", sha, 1, "")
        assertNull(UpdateLogic.resolveApkUrl(feedUrl, noFile))
    }

    // ---- GitHub-Feed (Release-Assets, Weiterleitung auf den Datei-Host) -----------------------------------------

    private val gitHubFeed = "https://github.com/owner/repo/releases/latest/download/latest.json"
    private val gitHubApk = "https://github.com/owner/repo/releases/download/v2.3.3-fold7.1/WoW-Forever-Fold-2.3.3-fold7.1.apk"

    @Test
    fun gitHubFeedUrlIsUsedAsIs() {
        assertEquals(gitHubFeed, UpdateLogic.feedUrl(gitHubFeed))
        assertEquals(gitHubFeed, UpdateLogic.feedUrl("  $gitHubFeed  "))
        assertEquals(gitHubFeed, UpdateLogic.feedUrl("https://github.com/owner/repo/releases/latest/download"))
    }

    @Test
    fun gitHubAssetUrlOfTheFeedIsTakenAsIs() {
        val i = UpdateInfo(283, "2.3.3-fold7.1", "WoW-Forever-Fold-2.3.3-fold7.1.apk", gitHubApk, sha, 1, "")
        assertEquals(gitHubApk, UpdateLogic.resolveApkUrl(gitHubFeed, i))
    }

    @Test
    fun relativeFileNextToGitHubFeedPointsToTheLatestRelease() {
        assertEquals(
            "https://github.com/owner/repo/releases/latest/download/a.apk",
            UpdateLogic.resolveApkUrl(gitHubFeed, fileOnly("a.apk")),
        )
    }

    @Test
    fun noHostListRestrictsTheDownload() {
        // GitHub answers asset URLs with a 302 to objects.githubusercontent.com / release-assets.githubusercontent.com; OkHttp
        // follows it. The host is not checked on purpose: SHA-256 of the feed and Android's signature check protect the install.
        for (host in listOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")) {
            val i = UpdateInfo(283, "x", "", "https://$host/o/r/a.apk", sha, 1, "")
            assertEquals("https://$host/o/r/a.apk", UpdateLogic.resolveApkUrl(gitHubFeed, i))
        }
    }

    @Test
    fun nonHttpOrBrokenReferencesAreRefused() {
        assertNull(UpdateLogic.resolveApkUrl(feedUrl, fileOnly("file:///sdcard/a.apk")))
        assertNull(UpdateLogic.resolveApkUrl(feedUrl, fileOnly("has space.apk")))
        assertNull(UpdateLogic.resolveApkUrl(feedUrl, fileOnly("  ")))
        assertNull(UpdateLogic.resolveApkUrl("kaputt", fileOnly("a.apk")))
    }

    // ---- SHA-256 / Größe --------------------------------------------------------------------------------------

    @Test
    fun sha256OfKnownInput() {
        assertEquals(abcSha, UpdateLogic.sha256Hex(ByteArrayInputStream("abc".toByteArray())))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            UpdateLogic.sha256Hex(ByteArrayInputStream(ByteArray(0))),
        )
    }

    @Test
    fun verifyFileAcceptsMatchingFile() {
        val f = tmp.newFile("ok.apk").apply { writeText("abc") }
        assertNull(UpdateLogic.verifyFile(f, 3, abcSha))
        assertNull(UpdateLogic.verifyFile(f, 3, abcSha.uppercase()))
    }

    @Test
    fun verifyFileRejectsWrongSize() {
        val f = tmp.newFile("size.apk").apply { writeText("abc") }
        val reason = UpdateLogic.verifyFile(f, 4, abcSha)
        assertNotNull(reason)
        assertEquals(UiText.res(R.string.launcher_update_verify_size, 3L, 4L), reason)
    }

    @Test
    fun verifyFileRejectsWrongHash() {
        val f = tmp.newFile("hash.apk").apply { writeText("abd") }
        val reason = UpdateLogic.verifyFile(f, 3, abcSha)
        assertNotNull(reason)
        assertEquals(UiText.res(R.string.launcher_update_verify_hash), reason)
    }

    @Test
    fun verifyFileRejectsMissingFile() {
        assertNotNull(UpdateLogic.verifyFile(File(tmp.root, "fehlt.apk"), 1, sha))
    }

    // ---- Update-Karte (Launcher oben) -------------------------------------------------------------------------

    @Test
    fun feedTextReachesTheCardWithLineBreaks() {
        val json = feed(notes = "\"text\":\"Neu: Launcher zuerst\\n- Update-Karte oben\\n\\n\\n\\nFix: Abklingzeit\"")
        val info = (UpdateLogic.parseFeed(json) as FeedResult.Ok).info
        assertEquals("Neu: Launcher zuerst\n- Update-Karte oben\n\nFix: Abklingzeit", UpdateLogic.cardNotes(info.notes))
    }

    @Test
    fun cardNotesAreEmptyWithoutFeedText() {
        val info = (UpdateLogic.parseFeed(feed(notes = "")) as FeedResult.Ok).info
        assertEquals("", UpdateLogic.cardNotes(info.notes))
        assertEquals("", UpdateLogic.cardNotes("  \n  \n"))
    }

    @Test
    fun longCardNotesAreCutOnAWordBoundary() {
        val text = "wort ".repeat(200)
        val cut = UpdateLogic.cardNotes(text, maxChars = 50)
        assertTrue(cut.endsWith("…"))
        assertTrue(cut.length <= 51)
        assertFalse(cut.dropLast(1).endsWith(" "))
        assertTrue(cut.dropLast(1).endsWith("wort"))
    }

    @Test
    fun versionTransitionAndSizeForTheCard() {
        assertEquals("2.3.3-fold7.4 → 2.3.3-fold7.7", UpdateLogic.versionTransition("2.3.3-fold7.4", " 2.3.3-fold7.7 "))
        assertEquals("149 MB", UpdateLogic.megabytes(155_788_181L))
    }

    @Test
    fun checkLineNamesRunningCurrentAndFailedCheck() {
        assertEquals(UiText.res(R.string.launcher_update_check_running), UpdateLogic.checkLine(CheckPhase.Running, "2.3.3-fold7.6"))
        assertEquals(UiText.res(R.string.launcher_update_check_done, "2.3.3-fold7.6"), UpdateLogic.checkLine(CheckPhase.Done, "2.3.3-fold7.6"))
        assertEquals(UiText.res(R.string.launcher_update_check_failed, "2.3.3-fold7.6"), UpdateLogic.checkLine(CheckPhase.Failed, "2.3.3-fold7.6"))
        assertEquals(UiText.res(R.string.launcher_update_check_unknown, "2.3.3-fold7.6"), UpdateLogic.checkLine(CheckPhase.Unknown, "2.3.3-fold7.6"))
    }

    @Test
    fun confirmTextExplainsTheOneTimePromptOnlyForForeignInstallers() {
        val own = "app.wowforever"
        assertEquals(UiText.res(R.string.launcher_update_confirm_own), UpdateLogic.confirmText(own, own))
        assertEquals(UiText.res(R.string.launcher_update_confirm_foreign), UpdateLogic.confirmText(null, own))
        assertEquals(UiText.res(R.string.launcher_update_confirm_foreign), UpdateLogic.confirmText("com.google.android.packageinstaller", own))
    }

    @Test
    fun installFractionSplitsCopyPhaseAndAndroidPhase() {
        val eps = 0.0001f
        assertEquals(0f, UpdateLogic.installFraction(0L, 100L, null), eps)
        assertEquals(0.25f, UpdateLogic.installFraction(50L, 100L, null), eps)
        assertEquals(0.5f, UpdateLogic.installFraction(100L, 100L, null), eps)
        assertEquals(0.5f, UpdateLogic.installFraction(200L, 100L, null), eps)
        assertEquals(0f, UpdateLogic.installFraction(-5L, 100L, null), eps)
        assertEquals(0f, UpdateLogic.installFraction(0L, 0L, null), eps)
        assertEquals(0.5f, UpdateLogic.installFraction(100L, 100L, 0f), eps)
        assertEquals(0.75f, UpdateLogic.installFraction(100L, 100L, 0.5f), eps)
        assertEquals(1f, UpdateLogic.installFraction(100L, 100L, 1f), eps)
        assertEquals(1f, UpdateLogic.installFraction(100L, 100L, 3f), eps)
    }
}
