package com.winlator.inputcontrols

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WowActionBindingsTest {
    @After
    fun clear() {
        WowActionBindings.setResolved(null)
    }

    @Test
    fun `resolve returns the stored key without an entry`() {
        val stored = BindingCombo.of(Binding.KEY_M)
        assertEquals(stored, WowActionBindings.resolve("TOGGLEWORLDMAP", stored))
        assertEquals(stored, WowActionBindings.resolve(null, stored))
        assertEquals(stored, WowActionBindings.resolve("", stored))
    }

    @Test
    fun `resolve returns the current key and ignores empty entries`() {
        WowActionBindings.setResolved(
            mapOf("TOGGLEWORLDMAP" to BindingCombo.of(Binding.KEY_N), "JUMP" to BindingCombo.none()),
        )
        val stored = BindingCombo.of(Binding.KEY_M)
        assertEquals(BindingCombo.of(Binding.KEY_N), WowActionBindings.resolve("TOGGLEWORLDMAP", stored))
        assertEquals(stored, WowActionBindings.resolve("JUMP", stored))
        assertEquals(stored, WowActionBindings.resolve("OTHER", stored))
    }

    @Test
    fun `slot with wow action follows the resolved key and keeps the stored one as fallback`() {
        val slot = RadialMenu.Slot("Karte", BindingCombo.of(Binding.KEY_M), "TOGGLEWORLDMAP")
        assertEquals(BindingCombo.of(Binding.KEY_M), slot.bindingCombo)
        WowActionBindings.setResolved(mapOf("TOGGLEWORLDMAP" to BindingCombo.of(Binding.KEY_N)))
        assertEquals(BindingCombo.of(Binding.KEY_N), slot.bindingCombo)
        assertEquals(Binding.KEY_N, slot.binding)
        assertTrue(slot.isEnabled)

        val json = slot.toJSONObject()
        assertEquals("TOGGLEWORLDMAP", json.getString("wowAction"))
        assertEquals("KEY_N", json.getString("binding"))
    }

    @Test
    fun `slot without wow action ignores the resolved keys`() {
        WowActionBindings.setResolved(mapOf("TOGGLEWORLDMAP" to BindingCombo.of(Binding.KEY_N)))
        val slot = RadialMenu.Slot("Map", Binding.KEY_M)
        assertNull(slot.wowAction)
        assertEquals(BindingCombo.of(Binding.KEY_M), slot.bindingCombo)
        assertFalse(slot.toJSONObject().has("wowAction"))
    }

    @Test
    fun `wow action survives json round trip and menu copy`() {
        val menu = RadialMenu()
        menu.id = "button-3"
        menu.addSlot(RadialMenu.Slot("Talente", BindingCombo.of(Binding.KEY_N), "TOGGLETALENTS"))
        menu.addSlot(RadialMenu.Slot("Tab", Binding.KEY_TAB))

        val restored = RadialMenu.fromJSONObject(JSONObject(menu.toJSONObject()!!.toString()))
        assertEquals("TOGGLETALENTS", restored.slots[0].wowAction)
        assertEquals(BindingCombo.of(Binding.KEY_N), restored.slots[0].bindingCombo)
        assertNull(restored.slots[1].wowAction)

        val copy = menu.copy()
        assertEquals("TOGGLETALENTS", copy.slots[0].wowAction)
    }

    @Test
    fun `old profile json without wow action still loads`() {
        val slot = RadialMenu.Slot.fromJSONObject(JSONObject().put("label", "Inventory").put("binding", "KEY_I"))
        assertNull(slot.wowAction)
        assertEquals(BindingCombo.of(Binding.KEY_I), slot.bindingCombo)
    }

    @Test
    fun `button takes the current key of its wow action`() {
        val element = ControlElement(null)
        element.setWowAction("TOGGLEWORLDMAP")
        element.setBindingComboAt(0, BindingCombo.of(Binding.KEY_M))

        element.applyWowActionBinding()
        assertEquals(BindingCombo.of(Binding.KEY_M), element.getBindingComboAt(0))

        WowActionBindings.setResolved(mapOf("TOGGLEWORLDMAP" to BindingCombo.of(Binding.KEY_N)))
        element.applyWowActionBinding()
        assertEquals(BindingCombo.of(Binding.KEY_N), element.getBindingComboAt(0))
    }

    @Test
    fun `button without wow action keeps its key and a type reset clears the action`() {
        WowActionBindings.setResolved(mapOf("TOGGLEWORLDMAP" to BindingCombo.of(Binding.KEY_N)))
        val element = ControlElement(null)
        element.setBindingComboAt(0, BindingCombo.of(Binding.KEY_M))
        element.applyWowActionBinding()
        assertEquals(BindingCombo.of(Binding.KEY_M), element.getBindingComboAt(0))

        element.wowAction = "TOGGLEWORLDMAP"
        assertEquals("TOGGLEWORLDMAP", element.wowAction)
        element.wowAction = ""
        assertNull(element.wowAction)
        element.wowAction = "JUMP"
        element.setType(ControlElement.Type.BUTTON)
        assertNull(element.wowAction)
    }

    @Test
    fun `a field whose action is unbound in wow stays disabled when it has no stored key`() {
        val slot = RadialMenu.Slot("Plätze", BindingCombo.none(), "MULTIACTIONBAR5BUTTON1")
        assertFalse(slot.isEnabled)
        WowActionBindings.setResolved(
            mapOf("MULTIACTIONBAR5BUTTON1" to BindingCombo.fromBindings(listOf(Binding.KEY_SHIFT_L, Binding.KEY_1))),
        )
        assertTrue(slot.isEnabled)
    }
}
