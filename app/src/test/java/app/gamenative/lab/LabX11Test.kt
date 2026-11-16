package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** X11-Schalter des Labors: die Vorgabe schreibt nichts, jede Wahl setzt genau ihre Werte, Reste früherer Wahlen werden entfernt. */
class LabX11Test {
    @Test
    fun theDefaultSetsNothingAndRemovesAllLabValues() {
        val plan = LabX11Options.plan(LabX11Preset.DEFAULT)
        assertEquals(LabX11Options.NAMES, plan.map { it.name })
        assertTrue(plan.all { it.value == null })
    }

    @Test
    fun eachChoiceWritesExactlyItsValueAndClearsTheOthers() {
        val cases = mapOf(
            LabX11Preset.TAKE_FOCUS_ON to ("UseTakeFocus" to "Y"),
            LabX11Preset.TAKE_FOCUS_OFF to ("UseTakeFocus" to "N"),
            LabX11Preset.NO_XIM to ("UseXIM" to "N"),
            LabX11Preset.UNMANAGED to ("Managed" to "N"),
        )
        for ((preset, expected) in cases) {
            val set = LabX11Options.plan(preset).filter { it.value != null }
            assertEquals(preset.key, listOf(LabX11Options.Op(expected.first, expected.second)), set)
            assertEquals(LabX11Options.NAMES.size, LabX11Options.plan(preset).size)
        }
    }

    @Test
    fun everyValueNameOfAPresetIsInTheClearedList() {
        for (p in LabX11Preset.entries) for ((name, _) in p.values) assertTrue("$name of ${p.key}", name in LabX11Options.NAMES)
    }

    @Test
    fun keysAreUniqueAndUnknownKeyIsTheDefault() {
        assertEquals(LabX11Preset.entries.size, LabX11Preset.entries.map { it.key }.toSet().size)
        assertEquals(LabX11Preset.DEFAULT, LabX11Preset.fromKey("nonsense"))
        assertEquals(LabX11Preset.DEFAULT, LabX11Preset.fromKey(null))
        for (p in LabX11Preset.entries) assertEquals(p, LabX11Preset.fromKey(p.key))
    }

    @Test
    fun settingsKeepTheChoiceThroughJsonAndOldFilesGetTheDefault() {
        val s = LabSettings(x11 = LabX11Preset.NO_XIM)
        assertEquals(LabX11Preset.NO_XIM, LabSettings.fromJson(s.toJson()).x11)
        assertEquals(LabX11Preset.DEFAULT, LabSettings.fromJson("{\"translator\":\"bundled\"}").x11)
        assertEquals(LabX11Preset.DEFAULT, LabSettings().x11)
    }

    @Test
    fun protocolNamesTheChoiceAndEveryValue() {
        val lines = LabX11Options.protocolLines(LabX11Preset.UNMANAGED, "Result: written")
        assertEquals("X11 driver options: unmanaged", lines.first())
        assertTrue(lines.contains("  Managed = N"))
        assertTrue(lines.contains("  UseXIM = (not set)"))
        assertEquals("Result: written", lines.last())
        assertNull(LabX11Options.plan(LabX11Preset.UNMANAGED).first { it.name == "UseXIM" }.value)
    }

    @Test
    fun theHookIsOnlyForTheLabContainer() {
        val text = java.io.File("src/main/java/app/gamenative/lab/LabX11.kt").readText()
        assertTrue(text.contains("container.id != LabIds.CONTAINER_ID) return"))
    }
}
