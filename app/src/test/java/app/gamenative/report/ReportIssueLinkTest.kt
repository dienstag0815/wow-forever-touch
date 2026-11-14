package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class ReportIssueLinkTest {
    private val repo = "https://github.com/example-owner/example-app"

    private fun params(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    @Test
    fun issueTitleIsAlwaysEnglishWithTheVersion() {
        assertEquals("Problem with WoW Forever 2.3.3-touch9.17", ReportIssueLink.issueTitle("2.3.3-touch9.17"))
    }

    @Test
    fun repoComesFromTheUpdateSourceWhenItIsAGithubReleaseFeed() {
        assertEquals(repo, ReportIssueLink.repoUrl("$repo/releases/latest/download/latest.json", "https://github.com/other/fallback"))
    }

    @Test
    fun repoFallsBackWhenTheFeedIsNotGithub() {
        assertEquals("https://github.com/other/fallback", ReportIssueLink.repoUrl("https://dl.example.org/app/latest.json", "https://github.com/other/fallback/"))
        assertEquals("https://github.com/other/fallback", ReportIssueLink.repoUrl(null, "https://github.com/other/fallback"))
        assertNull(ReportIssueLink.repoUrl("https://dl.example.org/x.json", "http://evil.example/other/fallback"))
        assertNull(ReportIssueLink.repoUrl("https://dl.example.org/x.json", "https://github.com/a/b/c"))
        assertNull(ReportIssueLink.repoUrl(null, null))
    }

    @Test
    fun urlHasTheTemplateTitleAndFields() {
        val url = ReportIssueLink.build(repo, "Problem with App 2.3", "short summary", "WoWForever-Bericht-20261114-1000.zip", "Maker Model", "17 (API 37)", "2.3.3")
        assertTrue(url.startsWith("$repo/issues/new?template=bug_report.yml&title="))
        val p = params(url)
        assertEquals("Problem with App 2.3", p["title"])
        assertEquals("Maker Model", p["device"])
        assertEquals("17 (API 37)", p["android"])
        assertEquals("2.3.3", p["app_version"])
        assertEquals("short summary\n\n" + ReportIssueLink.attachLine("WoWForever-Bericht-20261114-1000.zip"), p["report"])
        assertTrue(url.length <= ReportIssueLink.MAX_URL_CHARS)
    }

    @Test
    fun encodingUsesPercentTwentyAndUtf8() {
        assertEquals("a%20b%26c%3Dd%0A%C3%A4%E2%82%AC", ReportIssueLink.encode("a b&c=d\nä€"))
        val summary = "Zeile 1\nZeile 2 ä ö ü € 😀"
        val url = ReportIssueLink.build(repo, "Größe & Test", summary, "x.zip", "d", "a", "v")
        assertFalse(url.contains(' '))
        assertFalse(url.contains('+'))
        assertFalse(url.contains('\n'))
        assertEquals("Größe & Test", params(url)["title"])
        assertTrue(params(url)["report"]!!.startsWith(summary))
    }

    @Test
    fun longSummaryIsCutSoTheAddressFitsAndTheAttachLineStays() {
        val summary = (1..400).joinToString("\n") { "line $it & more text ä€ with %percent and spaces" }
        val zip = "WoWForever-Bericht-20261114-1000.zip"
        val url = ReportIssueLink.build(repo, "Problem", summary, zip, "Maker Model", "17", "2.3.3")
        assertTrue("Länge ${url.length}", url.length <= ReportIssueLink.MAX_URL_CHARS)
        assertTrue(url.length > ReportIssueLink.MAX_URL_CHARS - 600)
        val report = params(url)["report"]!!
        assertTrue(report.endsWith(ReportIssueLink.attachLine(zip)))
        assertTrue(report.contains("...\n\n"))
        assertTrue(report.startsWith("line 1 & more"))
    }

    @Test
    fun customLimitIsRespectedAndEmojiIsNeverSplit() {
        val summary = "😀".repeat(500)
        val url = ReportIssueLink.build(repo, "T", summary, "x.zip", "d", "a", "v", maxChars = 700)
        assertTrue(url.length <= 700)
        assertFalse(URLDecoder.decode(url, "UTF-8").contains('�'))
        assertFalse(url.contains("%3F"))
    }

    @Test
    fun theFullSummaryIsNotTouchedByTheLimit() {
        // Die Zwischenablage bekommt die volle Kurzfassung: build() ändert den übergebenen Text nicht.
        val summary = "x".repeat(2500)
        ReportIssueLink.build(repo, "T", summary, "x.zip", "d", "a", "v", maxChars = 500)
        assertEquals(2500, summary.length)
    }

    @Test
    fun theLineInTheReportNamesTheTwoStepsAndTheWayOutWithoutAnAttachment() {
        val line = ReportIssueLink.attachLine("x.zip")
        for (part in listOf("attach the file x.zip", "Downloads folder", "short summary is in your clipboard", "paste it", "cannot attach a file", "browser")) {
            assertTrue(part, line.contains(part))
        }
        assertFalse(line.contains("\n"))
    }
}
