package app.gamenative.lab

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wortliste für Laien (Entwurf 3.6): Die Texte der Ansicht „Einfach“ enthalten keine Fachwörter (Wine, FEX, Stufe, Container, Launcher, Installer, Pakete …);
 * die stehen nur in „Erweitert“. Geprüft wird jeder Schlüssel, den die Einfach-Ansicht anzeigt, in Deutsch und Englisch.
 */
class LabEasyWordsTest {
    private fun load(dir: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings_lab.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent
        }
    }

    /** Schlüssel, die in „Einfach“ stehen können: die neuen `lab_easy_*` und die Texte der Schrittzeilen und ihrer Meldungen. */
    private val easyPrefixes = listOf(
        "lab_easy_", "lab_guide_step_", "lab_guide_why_", "lab_guide_fact_", "lab_guide_src_", "lab_guide_state_", "lab_guide_btn_",
        "lab_signin_ladder_",
    )
    private val easyKeys = setOf(
        "lab_guide_signed_in_switch", "lab_guide_no_copy_switch", "lab_guide_self_hint", "lab_guide_continue_note", "lab_guide_restart_hint",
        "lab_guide_step6_hint", "lab_guide_copy_numbers", "lab_guide_set_done", "lab_guide_copy_on_done", "lab_loginway_missing",
        "lab_loginway_open_packages", "lab_guide_source_line",
    )

    /** Schritt 6 sagt dem Nutzer den Ort, den er im Battle.net-Programm angeben muss: dort bleibt „K:“ stehen (Abweichung vom Entwurf, im Bericht begründet). */
    private val driveAllowed = setOf("lab_guide_why_6", "lab_guide_step6_hint")

    private val forbiddenDe = listOf(
        "wine", "proton", "fex", "wowbox", "box64", "intel", "stufe", "jit", "prefix", "container", "sha-256", "winedebug", "dll", "x11",
        "hodll", "winhandler", "arm64", "guid", "schnellwahl", "stufentest", "fokus", "hänger", "labor", "launcher", "installer", "spielkopie",
        "anmeldeweg", "paket",
    ).map { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) }

    private val forbiddenEn = listOf(
        "wine", "proton", "fex", "wowbox", "box64", "intel", "stage", "prefix", "container", "sha-256", "winedebug", "dll", "x11", "hodll",
        "winhandler", "arm64", "guid", "quick pick", "step test", "focus", "hang", "lab", "launcher", "installer", "game copy", "sign-in path", "package",
    ).map { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE) }

    private fun easyEntries(map: Map<String, String>): Map<String, String> =
        map.filterKeys { key -> key in easyKeys || easyPrefixes.any { key.startsWith(it) } }

    @Test
    fun easyKeysExistInBothLanguages() {
        val de = easyEntries(load("values-de"))
        val en = easyEntries(load("values"))
        assertTrue(de.size > 80)
        assertEquals(de.keys.sorted(), en.keys.sorted())
    }

    @Test
    fun germanEasyTextsHaveNoJargon() {
        for ((key, text) in easyEntries(load("values-de"))) {
            for (word in forbiddenDe) assertTrue("Fachwort „${word.pattern}“ in values-de/$key: $text", !word.containsMatchIn(text))
            if (key !in driveAllowed) assertTrue("Laufwerk in values-de/$key: $text", !text.contains("Laufwerk") && !text.contains("K:"))
        }
    }

    @Test
    fun englishEasyTextsHaveNoJargon() {
        for ((key, text) in easyEntries(load("values"))) {
            for (word in forbiddenEn) assertTrue("jargon \"${word.pattern}\" in values/$key: $text", !word.containsMatchIn(text))
            if (key !in driveAllowed) assertTrue("drive in values/$key: $text", !Regex("\\bdrive\\b").containsMatchIn(text) && !text.contains("K:"))
        }
    }

    @Test
    fun theSevenStepsHaveTheNamesOfTheWordList() {
        val de = load("values-de")
        val names = (1..7).map { de.getValue("lab_guide_step_$it") }
        assertEquals(
            listOf("Übersetzer laden", "Battle.net installieren", "Einstellung setzen", "Anmelden", "Kopie des Spiels (optional)", "Spiel installieren", "Spielen"),
            names,
        )
    }

    @Test
    fun theRiskLineIsTheOneFromTheDesign() {
        assertEquals("Inoffiziell: Anmeldung auf eigene Verantwortung.", load("values-de").getValue("lab_easy_risk"))
    }
}
