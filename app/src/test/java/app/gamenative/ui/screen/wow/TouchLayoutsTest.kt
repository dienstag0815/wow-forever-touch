package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchLayoutsTest {
    private val profiles = listOf(
        2 to "Profile 2",
        6 to "Touch (WoW)",
        7 to "Touch (WoW komplett)",
        8 to "Touch (Standard)",
        12 to "Touch (Mein Layout)",
        20 to "Touch (Urlaub)",
        21 to "Touch (arena)",
        30 to "Spiel - Controls",
    )

    @Test
    fun onlyTouchNamedProfilesAreLayouts() {
        assertTrue(TouchLayouts.isLayoutName("Touch (Urlaub)"))
        assertFalse(TouchLayouts.isLayoutName("Profile 2"))
        assertFalse(TouchLayouts.isLayoutName("Touch ()"))
        assertFalse(TouchLayouts.isLayoutName(null))
        assertEquals("Urlaub", TouchLayouts.displayName("Touch (Urlaub)"))
        assertEquals("Profile 2", TouchLayouts.displayName("Profile 2"))
    }

    @Test
    fun listPutsTemplatesFirstInFixedOrderThenTheRestByName() {
        val entries = TouchLayouts.list(profiles)
        assertEquals(
            listOf("Standard", "WoW komplett", "WoW", "Mein Layout", "arena", "Urlaub"),
            entries.map { it.display },
        )
        assertEquals(listOf(true, true, true, false, false, false), entries.map { it.template })
        assertEquals(8, entries.first().id)
    }

    @Test
    fun listIgnoresDuplicateNames() {
        val entries = TouchLayouts.list(profiles + (40 to "Touch (Urlaub)"))
        assertEquals(1, entries.count { it.display == "Urlaub" })
    }

    @Test
    fun nameInputIsCleanedAndCapped() {
        assertEquals("Mein Test", TouchLayouts.clean("  Mein   Test  "))
        assertEquals(TouchLayouts.NAME_MAX, TouchLayouts.clean("x".repeat(60)).length)
        assertEquals("Touch (Urlaub)", TouchLayouts.profileNameFor(" Urlaub "))
        assertNull(TouchLayouts.profileNameFor("   "))
    }

    @Test
    fun nameProblemFlagsEmptyAndTakenNamesIgnoringCase() {
        val names = profiles.map { it.second }
        assertEquals(TouchLayouts.NameProblem.EMPTY, TouchLayouts.nameProblem("  ", names))
        assertEquals(TouchLayouts.NameProblem.TAKEN, TouchLayouts.nameProblem("urlaub", names))
        assertEquals(TouchLayouts.NameProblem.TAKEN, TouchLayouts.nameProblem("Standard", names))
        assertEquals(TouchLayouts.NameProblem.NONE, TouchLayouts.nameProblem("Dungeon", names))
    }

    @Test
    fun templatesAreTheThreeBundledLayoutsOnly() {
        assertTrue(TouchLayouts.isTemplate("Touch (Standard)"))
        assertTrue(TouchLayouts.isTemplate("Touch (WoW)"))
        assertTrue(TouchLayouts.isTemplate("Touch (WoW komplett)"))
        assertFalse(TouchLayouts.isTemplate("Touch (Mein Layout)"))
        assertFalse(TouchLayouts.isTemplate("Touch (Urlaub)"))
        assertFalse(TouchLayouts.isTemplate(null))
    }

    @Test
    fun modeFollowsTheChosenLayout() {
        assertEquals(InputMode.TOUCH_STANDARD, TouchLayouts.modeFor("Touch (Standard)"))
        assertEquals(InputMode.TOUCH_FULL, TouchLayouts.modeFor("Touch (WoW komplett)"))
        assertEquals(InputMode.TOUCH, TouchLayouts.modeFor("Touch (WoW)"))
        assertEquals(InputMode.TOUCH_CUSTOM, TouchLayouts.modeFor("Touch (Mein Layout)"))
        assertEquals(InputMode.TOUCH_CUSTOM, TouchLayouts.modeFor("Touch (Urlaub)"))
    }

    @Test
    fun ownLayoutWinsOnlyInMeinLayoutMode() {
        val pairs = profiles
        assertEquals(20, TouchLayouts.pickProfileId(pairs, InputMode.TOUCH_CUSTOM, "Touch (Urlaub)"))
        // Andere Modi bleiben bei ihrer Vorlage, auch wenn noch ein eigenes Layout gemerkt ist.
        assertEquals(8, TouchLayouts.pickProfileId(pairs, InputMode.TOUCH_STANDARD, "Touch (Urlaub)"))
        // Ohne Auswahl: "Mein Layout".
        assertEquals(12, TouchLayouts.pickProfileId(pairs, InputMode.TOUCH_CUSTOM, null))
        // Das gewaehlte Layout gibt es nicht mehr (geloescht, Wiederherstellung): zurueck auf "Mein Layout".
        assertEquals(12, TouchLayouts.pickProfileId(pairs, InputMode.TOUCH_CUSTOM, "Touch (Weg)"))
        // Ein Name ausserhalb der Touch-Layouts wird nicht gewaehlt.
        assertEquals(12, TouchLayouts.pickProfileId(pairs, InputMode.TOUCH_CUSTOM, "Spiel - Controls"))
        assertNull(TouchLayouts.pickProfileId(pairs, InputMode.CLASSIC, "Touch (Urlaub)"))
    }

    @Test
    fun effectiveNameIsTheModeNameUnlessAnOwnLayoutIsChosen() {
        assertEquals("Touch (Urlaub)", TouchLayouts.effectiveName(InputMode.TOUCH_CUSTOM, "Touch (Urlaub)"))
        assertEquals("Touch (Mein Layout)", TouchLayouts.effectiveName(InputMode.TOUCH_CUSTOM, null))
        assertEquals("Touch (Standard)", TouchLayouts.effectiveName(InputMode.TOUCH_STANDARD, "Touch (Urlaub)"))
        assertNull(TouchLayouts.effectiveName(InputMode.CLASSIC, "Touch (Urlaub)"))
    }

    @Test
    fun editedTemplateAsksOtherLayoutsAndUnchangedTemplatesDoNot() {
        assertTrue(TouchLayouts.needsTemplatePrompt("Touch (Standard)", changed = true))
        assertFalse(TouchLayouts.needsTemplatePrompt("Touch (Standard)", changed = false))
        assertFalse(TouchLayouts.needsTemplatePrompt("Touch (Urlaub)", changed = true))
        assertFalse(TouchLayouts.needsTemplatePrompt("Touch (Mein Layout)", changed = true))
        assertFalse(TouchLayouts.needsTemplatePrompt(null, changed = true))
    }

    @Test
    fun launcherShowsNameOfChosenOwnLayout() {
        assertEquals("Touch · Raid", TouchLayouts.customLabel(InputMode.TOUCH_CUSTOM, "Touch (Raid)"))
        // no chosen layout, "Mein Layout" itself, templates and Standard keep the usual text of the mode
        assertNull(TouchLayouts.customLabel(InputMode.TOUCH_CUSTOM, null))
        assertNull(TouchLayouts.customLabel(InputMode.TOUCH_CUSTOM, "Touch (Mein Layout)"))
        assertNull(TouchLayouts.customLabel(InputMode.TOUCH_CUSTOM, "kein Layoutname"))
        assertNull(TouchLayouts.customLabel(InputMode.TOUCH_STANDARD, "Touch (Raid)"))
        assertNull(TouchLayouts.customLabel(InputMode.CLASSIC, "Touch (Raid)"))
    }
}
