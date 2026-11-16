package app.gamenative.lab

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voreinstellung "Anmeldeweg" (Auftrag labweg1, Punkt 1): Ausgangszustände → Ergebnis, nichts anderes ändert sich. */
class LabLoginWayTest {
    private fun pkg(id: String, version: String) =
        LabPackage(id, LabPackageKind.FEX, id, version, "https://example.invalid/$id", "00", 1L, "MIT", "tzst", "")

    private val packages = listOf(pkg(LabQuickPicks.FEX_PACKAGE_ID, "2508"), pkg(LabQuickPicks.FEX_SECOND_PACKAGE_ID, "2605"))
    private val fexInstalled = LabInstalledPackage(LabPackageKind.FEX, "lab-2508", 1, "lab-2508")
    private val otherFexInstalled = LabInstalledPackage(LabPackageKind.FEX, "lab-2605", 1, "lab-2605")
    private val protonInstalled = LabInstalledPackage(LabPackageKind.WINE, "proton-10.0-34-arm64ec", 1, "proton-10.0-34-arm64ec-1")

    /** Alle Felder auf Werte, die nicht die Vorgabe sind: so fällt jede unbeabsichtigte Änderung auf. */
    private val everythingSet = LabSettings(
        wine = "proton-10.0-34-arm64ec-1",
        translator = LabTranslator.BUNDLED,
        fexVersion = "",
        battleNetLine = "--eigene-schalter",
        anyProgram = "C:\\x.exe -a",
        extraEnv = "A=1\nB=2",
        dllOverrides = "locationapi=d",
        wineDebug = LabWineDebug.ERRORS,
        wineDebugCustom = "err+all,+relay",
        startWay = LabStartWay.WINHANDLER,
        screenSize = "1280x720",
        sameIdentity = false,
        x11 = LabX11Preset.entries.last(),
        stability = LabStability.SAFEST,
        translatorLog = true,
        useGameCopy = true,
    )

    private fun applied(current: LabSettings, installed: List<LabInstalledPackage>): LabSettings {
        val r = LabLoginWay.apply(current, installed, packages)
        assertTrue("expected Applied but was $r", r is LabLoginWayResult.Applied)
        return (r as LabLoginWayResult.Applied).settings
    }

    /** Die Schlüssel des gespeicherten JSON, deren Wert sich zwischen zwei Einstellungen unterscheidet. */
    private fun differingKeys(a: LabSettings, b: LabSettings): Set<String> {
        val x = JSONObject(a.toJson())
        val y = JSONObject(b.toJson())
        return x.keys().asSequence().filter { x.get(it) != y.get(it) }.toSet()
    }

    @Test
    fun fexMissingChangesNothingAndNamesThePackage() {
        val r = LabLoginWay.apply(everythingSet, emptyList(), packages)
        assertEquals(LabLoginWayResult.FexMissing(listOf(LabQuickPicks.FEX_PACKAGE_ID)), r)
        // Ein Proton-Paket allein ersetzt FEX nicht.
        assertEquals(LabLoginWayResult.FexMissing(listOf(LabQuickPicks.FEX_PACKAGE_ID)), LabLoginWay.apply(LabSettings(), listOf(protonInstalled), packages))
    }

    @Test
    fun fexPresentSetsTheFiveFields() {
        val r = applied(LabSettings(), listOf(fexInstalled))
        assertEquals("", r.wine)
        assertEquals(LabTranslator.FEX, r.translator)
        assertEquals("lab-2508", r.fexVersion)
        assertEquals(LabStability.FEX, r.stability)
        assertFalse(r.useGameCopy)
        assertTrue(LabLoginWay.isApplied(r))
    }

    @Test
    fun copyOnAndOtherStageBecomeCopyOffAndStageFour() {
        val r = applied(everythingSet, listOf(fexInstalled))
        assertFalse(r.useGameCopy)
        assertEquals(LabStability.FEX, r.stability)
        assertEquals("", r.wine)
        // Wirksame Stufe und Übersetzer gehören zusammen (die Seite normalisiert nach jeder Änderung, das Ergebnis ist schon so).
        assertEquals(r, LabStabilities.normalize(r))
        assertEquals(LabStability.FEX, LabStabilities.effective(r))
    }

    @Test
    fun nothingElseChangesFieldByField() {
        for (start in listOf(LabSettings(), everythingSet)) {
            val r = applied(start, listOf(fexInstalled))
            assertEquals(start.copy(wine = "", translator = LabTranslator.FEX, fexVersion = "lab-2508", stability = LabStability.FEX, useGameCopy = false), r)
            val changed = differingKeys(start, r)
            assertTrue("unexpected fields changed: $changed", changed.all { it in LabLoginWay.CHANGED_FIELDS })
        }
        // Die nicht berührten Felder des "alles gesetzt"-Zustands bleiben wörtlich.
        val r = applied(everythingSet, listOf(fexInstalled))
        assertEquals("--eigene-schalter", r.battleNetLine)
        assertEquals("C:\\x.exe -a", r.anyProgram)
        assertEquals("A=1\nB=2", r.extraEnv)
        assertEquals("locationapi=d", r.dllOverrides)
        assertEquals(LabWineDebug.ERRORS, r.wineDebug)
        assertEquals("err+all,+relay", r.wineDebugCustom)
        assertEquals(LabStartWay.WINHANDLER, r.startWay)
        assertEquals("1280x720", r.screenSize)
        assertFalse(r.sameIdentity)
        assertTrue(r.translatorLog)
    }

    @Test
    fun alreadyChosenStaysTheSame() {
        val once = applied(LabSettings(), listOf(fexInstalled))
        val twice = applied(once, listOf(fexInstalled))
        assertEquals(once, twice)
        assertTrue(differingKeys(once, twice).isEmpty())
    }

    @Test
    fun otherFexVersionInstalledIsUsedWhenOnlyThatOneIsThere() {
        val r = applied(LabSettings(), listOf(otherFexInstalled))
        assertEquals("lab-2605", r.fexVersion)
        assertTrue(LabLoginWay.isApplied(r))
        // Beide da: bevorzugt der Eintrag fex-2508.
        assertEquals("lab-2508", applied(LabSettings(), listOf(otherFexInstalled, fexInstalled)).fexVersion)
    }

    @Test
    fun isAppliedRecognisesEachDeviation() {
        val ok = applied(LabSettings(), listOf(fexInstalled))
        assertTrue(LabLoginWay.isApplied(ok))
        assertFalse(LabLoginWay.isApplied(LabSettings()))
        assertFalse(LabLoginWay.isApplied(ok.copy(useGameCopy = true)))
        assertFalse(LabLoginWay.isApplied(ok.copy(wine = "proton-10.0-34-arm64ec-1")))
        assertFalse(LabLoginWay.isApplied(ok.copy(translator = LabTranslator.BUNDLED)))
        assertFalse(LabLoginWay.isApplied(ok.copy(fexVersion = "")))
        assertFalse(LabLoginWay.isApplied(ok.copy(stability = LabStability.FEX_CAREFUL)))
        // Eine gespeicherte Stufe 1 bei Übersetzer FEX zählt als Stufe 4 (wirksame Stufe), die Seite normalisiert ohnehin.
        assertTrue(LabLoginWay.isApplied(ok.copy(stability = LabStability.CAREFUL)))
        // Andere Felder ändern die Voreinstellung nicht.
        assertTrue(LabLoginWay.isApplied(ok.copy(extraEnv = "A=1", translatorLog = true, screenSize = "1280x720")))
    }

    @Test
    fun headerLineSaysAppliedOrNot() {
        assertEquals("Sign-in setup: not applied", LabLoginWay.headerLine(LabSettings()))
        assertEquals("Sign-in setup: applied", LabLoginWay.headerLine(applied(LabSettings(), listOf(fexInstalled))))
    }

    private fun header(settings: LabSettings, fex: Set<String> = emptySet()): String {
        val plan = LabCommand.build(
            settings,
            LabTarget.BATTLENET,
            LabCommandContext(baseEnv = "MESA_GLTHREAD=true", battleNetExe = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe", installedFex = fex),
        )
        return LabProtocol.header(LabProtocolInfo(0L, "1.0", "d", "a", settings, plan, 1L))
    }

    @Test
    fun theHeaderOfTheProtocolNamesTheSignInSetup() {
        val plain = header(LabSettings())
        assertTrue(plain.contains("\nGame controls: off (manual)\nSign-in setup: not applied\n"))
        val set = applied(LabSettings(), listOf(fexInstalled))
        val text = header(set, fex = setOf("lab-2508"))
        assertTrue(text.contains("\nSign-in setup: applied\n"))
        assertTrue(text.contains("\nGame copy: off"))
        assertTrue(text.contains("\nStability: 4 FEX\n"))
        assertTrue(text.indexOf("Sign-in setup:") < text.indexOf("== Settings =="))
        // Mit eingeschalteter Kopie gilt die Voreinstellung nicht mehr.
        assertTrue(header(set.copy(useGameCopy = true), fex = setOf("lab-2508")).contains("\nSign-in setup: not applied\n"))
    }

    @Test
    fun gameControlsStartOffInEveryRun() {
        // "Spiel"-Steuerung ist keine gespeicherte Einstellung: jeder Lauf beginnt aus, das Protokoll sagt es im Kopf.
        assertFalse(LabGcState().on)
        assertEquals("Game controls: off (manual)", LabGameControlsLogic.headerLine(LabGcState()))
    }
}
