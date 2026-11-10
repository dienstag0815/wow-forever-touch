package app.gamenative.ui.touchstyle

import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.RadialMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which fields of a radial menu or key ring disappear in the game: the empty ones (no assignment, or an empty action bar slot). */
class RadialSlotVisibilityTest {
    @Test
    fun aFieldWithoutAnyAssignmentIsEmpty() {
        assertTrue(RadialSlotVisibility.isEmpty(false, null) { null })
        assertTrue(RadialSlotVisibility.isEmpty(false, 3) { true })        // nothing assigned: empty whatever the slot holds
        assertTrue(RadialSlotVisibility.isEmpty(null as RadialMenu.Slot?))
        assertTrue(RadialSlotVisibility.isEmpty(RadialMenu.Slot()))
        assertTrue(RadialSlotVisibility.isEmpty(RadialMenu.Slot("", Binding.NONE)))
    }

    @Test
    fun aFieldWithAKeyOrAChatCommandIsNotEmpty() {
        assertFalse(RadialSlotVisibility.isEmpty(RadialMenu.Slot("", Binding.KEY_M)))
        assertFalse(RadialSlotVisibility.isEmpty(RadialMenu.Slot().apply { setChatCommand("/dance") }))
        assertFalse(RadialSlotVisibility.isEmpty(true, null) { null })
    }

    @Test
    fun anActionBarFieldIsEmptyOnlyWhenTheSlotIsKnownToBeEmpty() {
        assertTrue("empty slot", RadialSlotVisibility.isEmpty(true, 3) { false })
        assertFalse("filled slot", RadialSlotVisibility.isEmpty(true, 3) { true })
        assertFalse("unknown: never hidden on suspicion", RadialSlotVisibility.isEmpty(true, 3) { null })
        // the question is asked for the slot of the field
        var asked: Int? = null
        RadialSlotVisibility.isEmpty(true, 7) { asked = it; true }
        assertEquals(7, asked)
        // no action bar slot behind the field: the state of the bar does not matter
        assertFalse(RadialSlotVisibility.isEmpty(true, null) { false })
    }
}
