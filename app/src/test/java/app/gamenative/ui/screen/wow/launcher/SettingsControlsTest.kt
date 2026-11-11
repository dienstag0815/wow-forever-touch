package app.gamenative.ui.screen.wow.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Einstellungsseiten: Aktionen sind Knöpfe, Werte sind Text, Zustände haben Farbe; Kontrast und Touch-Ziel stimmen. */
class SettingsControlsTest {
    private val kinds = ActionKind.entries

    @Test
    fun touchTargetIsAtLeast48dp() {
        assertTrue(ActionStyle.MIN_HEIGHT_DP >= 48)
    }

    @Test
    fun buttonTextReaches4Point5OnItsFillInEveryState() {
        for (kind in kinds) for (enabled in listOf(true, false)) for (pressed in listOf(false, true)) {
            val c = ActionStyle.colors(kind, enabled, pressed)
            val r = LauncherContrast.ratio(c.text, c.fill)
            assertTrue("$kind enabled=$enabled pressed=$pressed: Kontrast ${"%.2f".format(r)}", r >= 4.5)
        }
    }

    @Test
    fun outlinedButtonsHaveAVisibleFrameAgainstTheirSurface() {
        // Nicht-Text-Kontrast (WCAG 1.4.11): Rahmen gegen die Fläche, auf der der Knopf liegt, mindestens 3.
        val surfaces = listOf(LauncherPalette.SURFACE, LauncherPalette.SURFACE_INSET, LauncherPalette.SURFACE_RAISED)
        for (kind in listOf(ActionKind.Secondary, ActionKind.Danger)) {
            val c = ActionStyle.colors(kind)
            for (s in surfaces) {
                val r = LauncherContrast.ratio(c.border, s)
                assertTrue("$kind Rahmen auf ${s.toString(16)}: ${"%.2f".format(r)}", r >= 3.0)
            }
        }
        val primary = ActionStyle.colors(ActionKind.Primary)
        for (s in surfaces) assertTrue(LauncherContrast.ratio(primary.fill, s) >= 3.0)
    }

    @Test
    fun theThreeKindsLookDifferent() {
        val p = ActionStyle.colors(ActionKind.Primary)
        val s = ActionStyle.colors(ActionKind.Secondary)
        val d = ActionStyle.colors(ActionKind.Danger)
        assertNotEquals(p.fill, s.fill)
        assertNotEquals(s.border, d.border)
        assertNotEquals(s.fill, d.fill)
        // Hauptaktion = Goldfläche; Neben- und gefährliche Aktion sind dunkel mit Rahmen.
        assertEquals(LauncherPalette.GOLD, p.fill)
        assertTrue(LauncherContrast.luminance(s.fill) < 0.05)
        assertTrue(LauncherContrast.luminance(d.fill) < 0.05)
        // gedrückt ändert die Fläche, gesperrt sieht grau aus
        assertNotEquals(p.fill, ActionStyle.colors(ActionKind.Primary, pressed = true).fill)
        assertEquals(LauncherPalette.DISABLED_FILL, ActionStyle.colors(ActionKind.Primary, enabled = false).fill)
    }

    @Test
    fun actionKindPrefersDangerOverPrimary() {
        assertEquals(ActionKind.Danger, actionKindOf(primary = true, danger = true))
        assertEquals(ActionKind.Danger, actionKindOf(primary = false, danger = true))
        assertEquals(ActionKind.Primary, actionKindOf(primary = true, danger = false))
        assertEquals(ActionKind.Secondary, actionKindOf(primary = false, danger = false))
    }

    @Test
    fun everyToneIsReadableAndDistinct() {
        for (tone in PillTone.entries) {
            val c = ToneStyle.colors(tone)
            val r = LauncherContrast.ratio(c.text, c.fill)
            assertTrue("$tone: ${"%.2f".format(r)}", r >= 4.5)
        }
        assertEquals(PillTone.entries.size, PillTone.entries.map { ToneStyle.colors(it).fill }.distinct().size)
        // grün = Ok, rot = Fehler: die Schrift unterscheidet sich im Farbton, nicht nur in der Helligkeit
        assertNotEquals(ToneStyle.colors(PillTone.Ok).text, ToneStyle.colors(PillTone.Error).text)
    }

    // --- Aufbau der Quelltexte: Aktion = Knopf-Baustein, kein reiner Text-Klick ---

    private fun sourceFile(relative: String): String? =
        listOf("src/main/java/app/gamenative/$relative", "app/src/main/java/app/gamenative/$relative")
            .map { File(it) }.firstOrNull { it.isFile }?.readText()?.replace("\r\n", "\n")

    /** Rumpf einer Funktion: ab der Zeile mit `fun name(` bis zur nächsten Deklaration auf oberster Ebene. */
    private fun body(src: String, name: String): String {
        val start = Regex("""\n(internal |private )?fun\s+(<T>\s+)?$name\(""").find(src)?.range?.first ?: error("$name nicht gefunden")
        val rest = src.substring(start + 1)
        val end = Regex("""\n(/\*\*|@Composable|internal fun|private fun|fun )""").find(rest, 10)?.range?.first ?: rest.length
        return rest.substring(0, end)
    }

    @Test
    fun actionRowsUseTheButtonBuildingBlock() {
        val rows = sourceFile("ui/screen/wow/LauncherSettingsRows.kt")
        val cards = sourceFile("ui/screen/wow/launcher/PageCards.kt")
        assumeTrue("Quellordner nicht gefunden", rows != null && cards != null)
        val settingAction = body(rows!!, "SettingActionRow")
        assertTrue("SettingActionRow nimmt ActionButton", "ActionButton(" in settingAction)
        // Der Text steht im Knopf; die Zeile selbst ist mit Knopf nicht tippbar (nur ohne Aktionstext ist sie die Aktion).
        assertFalse("SettingActionRow zeigt keinen Text als Aktion", "Text(" in settingAction)
        for (name in listOf("CardActionRow", "CardOpenRow")) {
            val b = body(cards!!, name)
            assertTrue("$name nimmt ActionButton", "ActionButton(" in b)
            assertFalse("$name zeigt keinen Text als Aktion", "Text(" in b)
            assertTrue("$name: die Zeile selbst ist nicht tippbar", "CardRowShell(enabled, onClick = null)" in b)
        }
    }

    @Test
    fun tappableValuesAreChipsAndPlainValuesAreMutedText() {
        val rows = sourceFile("ui/screen/wow/LauncherSettingsRows.kt")
        val cards = sourceFile("ui/screen/wow/launcher/PageCards.kt")
        assumeTrue("Quellordner nicht gefunden", rows != null && cards != null)
        val valueRow = body(rows!!, "SettingValueRow")
        assertTrue("ValueChip(" in valueRow && "ValueText(" in valueRow)
        assertTrue("ValueChip(" in body(rows, "SettingChoiceRow"))
        assertTrue("ValueChip(" in body(cards!!, "CardChoiceRow"))
        val cardValue = body(cards, "CardValueRow")
        assertTrue("ValueChip(" in cardValue && "ValueText(" in cardValue)
        // Version und Client-Build: kein goldener Text mehr in den Einstellungszeilen
        assertFalse("Gold" in valueRow)
        val page = sourceFile("ui/screen/wow/LauncherSettingsPage.kt")
        assumeTrue(page != null)
        assertTrue("ValueText(BuildConfig.VERSION_NAME)" in page!!)
    }

    @Test
    fun expandableAreasUseChevronRowsNotShowHideText() {
        val update = sourceFile("update/UpdateCard.kt")
        val source = sourceFile("update/UpdateSourceUi.kt")
        assumeTrue("Quellordner nicht gefunden", update != null && source != null)
        assertTrue("ExpandRow(" in update!!)
        assertTrue("InsetPanel" in update)
        for ((name, text) in listOf("UpdateCard" to update, "UpdateSourceUi" to source!!)) {
            assertFalse("$name: Anzeigen/Ausblenden als Text statt Pfeil", "launcher_action_hide" in text)
        }
    }

    @Test
    fun updateSourceHasThreeRadioCardsAndFieldsOnlyForTheirMode() {
        val source = sourceFile("update/UpdateSourceUi.kt")
        assumeTrue("Quellordner nicht gefunden", source != null)
        assertEquals(3, Regex("""RadioCard\(""").findAll(source!!).count())
        val ownStart = source.indexOf("SourceShort.Kind.Own -> InsetPanel")
        val testerStart = source.indexOf("SourceShort.Kind.Tester -> if")
        assertTrue(ownStart > testerStart && testerStart > 0)
        val testerPart = source.substring(testerStart, ownStart)
        // Tester: weder Token-Feld noch Repo-Adresse noch Pre-Release-Schalter noch "Token entfernen"
        assertFalse("launcher_source_token_label" in testerPart)
        assertFalse("launcher_source_url_label" in testerPart)
        assertFalse("PanelSwitch(" in testerPart)
        assertFalse("launcher_source_token_remove" in testerPart)
        // Aktiver Tester-Modus: Statuskarte mit "Verbindung prüfen" und "Tester-Modus beenden"
        assertTrue("launcher_tester_status" in testerPart && "launcher_tester_end" in testerPart && "launcher_source_test" in testerPart)
        // Server-Art, Adresse, Token und Pre-Releases stehen nur im Zweig "Eigener Server"
        val ownPart = source.substring(ownStart)
        for (key in listOf("launcher_source_url_label", "launcher_source_token_label", "PanelSwitch(", "SegmentChoice(")) {
            assertTrue("$key im Zweig Eigener Server", key in ownPart)
        }
    }
}
