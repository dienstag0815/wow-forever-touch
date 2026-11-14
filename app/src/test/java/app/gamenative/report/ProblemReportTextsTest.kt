package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Die Texte von "Problem melden" gibt es auf Englisch und Deutsch (die Schlüsselgleichheit insgesamt prüft StringsGameTest). */
class ProblemReportTextsTest {
    private fun load(dir: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings_game.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
    }

    private val keys = listOf(
        "game_pm_row_title", "game_pm_row_desc", "game_pm_action", "game_pm_dialog_title", "game_pm_dialog_body", "game_pm_dialog_ok",
        "game_pm_saved","game_pm_no_repo", "game_pm_not_saved",
        "game_pm_dialog_apps", "game_pm_q_intro", "game_pm_q1", "game_pm_q2", "game_pm_q2_worked", "game_pm_q2_failed", "game_pm_q3",
        "game_pm_q4", "game_pm_q4_pc", "game_pm_q4_browser", "game_pm_a_yes", "game_pm_a_no", "game_pm_a_unknown",
        "game_pm_steps", "game_pm_card_title", "game_pm_card_saved", "game_pm_card_cache", "game_pm_card_open", "game_pm_card_browser",
        "game_pm_card_share", "game_pm_card_copy", "game_pm_card_close", "game_pm_card_copied",
        "game_pm_ready_step1", "game_pm_ready_step1_share", "game_pm_ready_step2", "game_pm_ready_step3", "game_pm_ready_hint",
        "game_pm_show_downloads", "game_pm_show_downloads_failed", "game_pm_save_as", "game_pm_saved_as", "game_pm_save_failed",
    )

    @Test
    fun everyTextExistsInEnglishAndGerman() {
        for (dir in listOf("values", "values-de")) {
            val texts = load(dir)
            for (key in keys) assertTrue("$dir/$key fehlt oder ist leer", !texts[key].isNullOrBlank())
        }
    }

    @Test
    fun theDialogSaysWhatTheZipHoldsThatSecretsAreBlackedOutNothingIsSentAndTheReportIsPublic() {
        val en = load("values").getValue("game_pm_dialog_body").lowercase()
        for (word in listOf("zip", "logs", "blacked out", "nothing is opened", "nothing is sent automatically", "public")) assertTrue("en: $word", en.contains(word))
        val de = load("values-de").getValue("game_pm_dialog_body").lowercase()
        for (word in listOf("zip", "logs", "geschwärzt", "öffnet sich nichts", "nichts automatisch gesendet", "öffentlich")) assertTrue("de: $word", de.contains(word))
    }

    @Test
    fun theDialogSaysThatOtherLauncherAppsAreChecked() {
        val en = load("values").getValue("game_pm_dialog_apps").lowercase()
        for (word in listOf("other launcher apps", "installed", "yes or no only")) assertTrue("en: $word", en.contains(word))
        val de = load("values-de").getValue("game_pm_dialog_apps").lowercase()
        for (word in listOf("andere start-apps", "installiert", "nur ja oder nein")) assertTrue("de: $word", de.contains(word))
    }

    @Test
    fun theFourQuestionsAreInBothLanguagesAndEveryAnswerIsAChoice() {
        for (dir in listOf("values", "values-de")) {
            val texts = load(dir)
            for (q in listOf("game_pm_q1", "game_pm_q2", "game_pm_q3", "game_pm_q4")) assertTrue("$dir/$q", texts.getValue(q).trim().endsWith("?"))
        }
        val de = load("values-de")
        assertTrue(de.getValue("game_pm_q1").contains("schon einmal geklappt"))
        assertTrue(de.getValue("game_pm_q2").contains("andere App"))
        assertTrue(de.getValue("game_pm_q3").contains("Authenticator"))
        assertTrue(de.getValue("game_pm_q4").contains("woanders angemeldet"))
        assertTrue(de.getValue("game_pm_q4_pc").contains("Battle.net"))
        assertTrue(de.getValue("game_pm_q4_browser").contains("Browser"))
    }

    @Test
    fun theIssueTemplateHasTheFieldsTheLinkFills() {
        val template = File("../.github/ISSUE_TEMPLATE/bug_report.yml").readText().replace("\r\n", "\n")
        for (id in listOf(ReportIssueLink.FIELD_DEVICE, ReportIssueLink.FIELD_ANDROID, ReportIssueLink.FIELD_VERSION, ReportIssueLink.FIELD_REPORT)) {
            assertTrue("Feld $id fehlt in der Vorlage", template.contains("id: $id\n"))
        }
        for (option in listOf("Installation", "Game download", "Start", "Login", "In the game")) assertTrue(option, template.contains("- $option"))
        assertTrue(template.contains("Help > Report a problem"))
    }

    @Test
    fun theCardTextsHaveTheSamePlaceholdersInBothLanguages() {
        val en = load("values")
        val de = load("values-de")
        val placeholder = Regex("%\\d\\$[sd]")
        for (key in keys.filter { it.startsWith("game_pm_card_") || it.startsWith("game_pm_ready_") || it == "game_pm_steps" || it == "game_pm_saved" }) {
            assertEquals(key, placeholder.findAll(en.getValue(key)).map { it.value }.toList(), placeholder.findAll(de.getValue(key)).map { it.value }.toList())
        }
        assertEquals(listOf("%1\$s"), placeholder.findAll(en.getValue("game_pm_card_saved")).map { it.value }.toList())
        assertEquals(listOf("%1\$s"), placeholder.findAll(de.getValue("game_pm_card_cache")).map { it.value }.toList())
        assertTrue(placeholder.findAll(en.getValue("game_pm_steps")).none())
        assertEquals(listOf("%1\$s"), placeholder.findAll(en.getValue("game_pm_ready_step1")).map { it.value }.toList())
        assertEquals(listOf("%1\$s"), placeholder.findAll(de.getValue("game_pm_saved")).map { it.value }.toList())
    }

    @Test
    fun theStepsNameTheSummaryInTheClipboardTheZipInDownloadsAndTheBrowserWayOut() {
        val en = load("values").getValue("game_pm_steps").lowercase()
        for (word in listOf("short summary", "clipboard", "text field is empty", "attach the zip", "downloads", "cannot attach a file", "open in browser")) assertTrue("en: $word", en.contains(word))
        val de = load("values-de").getValue("game_pm_steps")
        for (word in listOf("Kurzfassung", "Zwischenablage", "Textfeld leer", "hänge die Zip", "Downloads", "keine Datei anhängen", "Im Browser öffnen")) assertTrue("de: $word", de.contains(word))
    }

    @Test
    fun theFourButtonsHaveTheNamesOfTheTaskInBothLanguages() {
        val en = load("values")
        val de = load("values-de")
        assertEquals(listOf("Open in browser", "Open in app", "Share zip", "Copy summary"), listOf("browser", "open", "share", "copy").map { en.getValue("game_pm_card_$it") })
        assertEquals(listOf("Im Browser öffnen", "In der App öffnen", "Zip teilen", "Kurzfassung kopieren"), listOf("browser", "open", "share", "copy").map { de.getValue("game_pm_card_$it") })
    }

    @Test
    fun theFirstDialogOnlyCreatesAndTheTextsNoLongerPromiseAnAutomaticOpening() {
        val en = load("values")
        val de = load("values-de")
        assertEquals("Create report", en.getValue("game_pm_dialog_ok"))
        assertEquals("Bericht erstellen", de.getValue("game_pm_dialog_ok"))
        for ((texts, words) in listOf(en to listOf("opens a new report", "in your browser"), de to listOf("öffnet eine neue Meldung", "im Browser eine neue"))) {
            for (key in listOf("game_pm_dialog_body", "game_pm_row_desc", "game_pm_saved")) {
                for (word in words) assertTrue("$key: $word", !texts.getValue(key).contains(word))
            }
        }
        assertTrue(!en.containsKey("game_pm_opened") && !de.containsKey("game_pm_opened"))
    }

    @Test
    fun theResultDialogNumbersTheThreeStepsAndHasAHintAboutTheBrowser() {
        val en = load("values")
        val de = load("values-de")
        assertEquals("The zip is in Downloads: %1\$s", en.getValue("game_pm_ready_step1"))
        assertEquals("Die Zip liegt in Downloads: %1\$s", de.getValue("game_pm_ready_step1"))
        assertEquals("Open the report page", en.getValue("game_pm_ready_step2"))
        assertEquals("Meldeseite öffnen", de.getValue("game_pm_ready_step2"))
        assertTrue(en.getValue("game_pm_ready_step3").contains("short summary") && en.getValue("game_pm_ready_step3").contains("attach the zip"))
        assertTrue(de.getValue("game_pm_ready_step3").contains("Kurzfassung") && de.getValue("game_pm_ready_step3").contains("Zip anhängen"))
        assertTrue(en.getValue("game_pm_ready_step1_share").startsWith("Share zip"))
        assertTrue(de.getValue("game_pm_ready_step1_share").startsWith("Zip teilen"))
        assertTrue(en.getValue("game_pm_ready_hint").contains("logged in") && en.getValue("game_pm_ready_hint").contains("filled in"))
        assertTrue(de.getValue("game_pm_ready_hint").contains("angemeldet") && de.getValue("game_pm_ready_hint").contains("vorausgefüllt"))
    }

    @Test
    fun theResultDialogShowsTheStepsAndTheButtonsAndTheHelpPageShowsTheCardOnTheOverviewAndTheDetailPage() {
        val dialog = File("src/main/java/app/gamenative/ui/screen/wow/ErrorReportRow.kt").readText().replace("\r\n", "\n")
        for (key in listOf("game_pm_card_title", "game_pm_ready_step1", "game_pm_ready_step1_share", "game_pm_ready_step2", "game_pm_ready_step3", "game_pm_ready_hint")) assertTrue(key, dialog.contains("R.string.$key"))
        // Die Karte und der Ergebnis-Dialog haben dieselbe Reihenfolge: Browser (hervorgehoben), in der App, Zip teilen.
        val order = listOf("game_pm_card_browser", "game_pm_card_open", "game_pm_card_share")
        for (part in listOf(dialog.substringAfter("fun ReportReadyCard"), dialog.substringAfter("fun ReportReadyDialog("))) {
            val at = order.map { part.indexOf("R.string.$it") }
            assertTrue(at.toString(), at.all { it >= 0 } && at == at.sorted())
        }
        assertTrue(dialog.contains("R.string.game_pm_card_browser), ActionKind.Primary"))
        assertTrue(!dialog.substringBefore("fun ReportReadyCard").contains("R.string.game_pm_steps"))
        val help = File("src/main/java/app/gamenative/ui/screen/wow/launcher/HelpPage.kt").readText()
        assertEquals(2, Regex("ReportReadyCard \\{").findAll(help).count())
        assertEquals(1, Regex("ReportReadyDialogHost \\{").findAll(help).count())
    }
}
