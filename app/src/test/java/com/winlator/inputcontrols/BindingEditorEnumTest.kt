package com.winlator.inputcontrols

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The binding "Tasten bearbeiten" (OPEN_CONTROLS_EDITOR) was appended to the enum; old profiles must load unchanged. */
class BindingEditorEnumTest {
    @Test
    fun `new binding is the last constant so no stored position moves`() {
        val values = Binding.values()
        assertEquals(Binding.OPEN_CONTROLS_EDITOR, values.last())
        // Everything the gamepad code counts by position stays in front of it.
        assertTrue(Binding.GAMEPAD_DPAD_LEFT.ordinal < Binding.OPEN_CONTROLS_EDITOR.ordinal)
        assertEquals(Binding.GAMEPAD_DPAD_LEFT.ordinal + 1, Binding.OPEN_CONTROLS_EDITOR.ordinal)
    }

    @Test
    fun `new binding is an extra with a german name and no gamepad or key side effect`() {
        val binding = Binding.OPEN_CONTROLS_EDITOR
        assertTrue(binding.isExtra)
        assertFalse(binding.isGamepad)
        assertFalse(binding.isMouse)
        assertFalse(binding.isKeyboard)
        assertEquals("TASTEN BEARBEITEN", binding.toString())
        assertTrue(Binding.extraBindingValues().contains(binding))
        assertEquals(binding, Binding.fromString("OPEN_CONTROLS_EDITOR"))
    }

    @Test
    fun `old profiles with all stored names load to the same bindings`() {
        for (binding in Binding.values()) {
            assertEquals(binding, Binding.fromString(binding.name))
        }
        // A name from a newer build still falls back to NONE instead of crashing.
        assertEquals(Binding.NONE, Binding.fromString("SOMETHING_FROM_THE_FUTURE"))
        val combo = BindingCombo.fromJsonValue(JSONObject("""{"bindings":["KEY_SHIFT_L","KEY_1"]}"""))
        assertEquals(listOf(Binding.KEY_SHIFT_L, Binding.KEY_1), combo.bindings)
    }

    @Test
    fun `editor binding survives a combo json round trip`() {
        val combo = BindingCombo.of(Binding.OPEN_CONTROLS_EDITOR)
        assertEquals(combo, BindingCombo.fromJsonValue(combo.toJsonValue()))
    }
}
