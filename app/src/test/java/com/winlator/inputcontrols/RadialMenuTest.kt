package com.winlator.inputcontrols

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialMenuTest {
    @Test
    fun `legacy single binding still loads`() {
        val slot = RadialMenu.Slot.fromJSONObject(
            JSONObject()
                .put("label", "Inventory")
                .put("binding", "KEY_I"),
        )

        assertEquals("Inventory", slot.label)
        assertEquals(BindingCombo.of(Binding.KEY_I), slot.bindingCombo)
    }

    @Test
    fun `simultaneous radial binding round trips`() {
        val expected = BindingCombo.fromBindings(listOf(Binding.KEY_CTRL_L, Binding.KEY_1))
        val slot = RadialMenu.Slot("Action", expected)

        val json = slot.toJSONObject()
        val restored = RadialMenu.Slot.fromJSONObject(json)

        assertEquals(Binding.KEY_1.name, json.getString("binding"))
        assertEquals(expected, restored.bindingCombo)
    }

    @Test
    fun `radial sequence preserves order and delay`() {
        val expected = BindingCombo.fromBindings(
            listOf(Binding.KEY_E, Binding.MOUSE_LEFT_BUTTON),
            BindingCombo.Mode.SEQUENCE,
            240,
        )
        val slot = RadialMenu.Slot("Interact", expected)

        val json = slot.toJSONObject()
        val restored = RadialMenu.Slot.fromJSONObject(json)

        assertEquals(JSONArray(listOf("KEY_E", "MOUSE_LEFT_BUTTON")).toString(), json.getJSONArray("bindings").toString())
        assertEquals("sequence", json.getString("mode"))
        assertEquals(240, json.getInt("sequenceDelayMs"))
        assertEquals(expected, restored.bindingCombo)
    }

    @Test
    fun `legacy radial sequence fields still load`() {
        val slot = RadialMenu.Slot.fromJSONObject(
            JSONObject()
                .put("label", "Interact")
                .put("binding", "MOUSE_LEFT_BUTTON")
                .put("bindings", JSONArray(listOf("KEY_E", "MOUSE_LEFT_BUTTON")))
                .put("bindingMode", "sequence")
                .put("bindingDelayMs", 240),
        )

        assertEquals(BindingCombo.Mode.SEQUENCE, slot.bindingCombo.mode)
        assertEquals(240, slot.bindingCombo.sequenceDelayMs)
    }

    // Direction vectors (dx, dy) in screen coordinates: y points down, so "up" is negative dy.
    private fun select(angleFromTopClockwiseDeg: Double, distance: Double, count: Int, deadZone: Double = 10.0): Int {
        val rad = Math.toRadians(angleFromTopClockwiseDeg)
        return RadialMenu.selectSlotIndex(Math.sin(rad) * distance, -Math.cos(rad) * distance, deadZone, count)
    }

    @Test
    fun `dead zone selects nothing`() {
        assertEquals(-1, RadialMenu.selectSlotIndex(0.0, 0.0, 10.0, 6))
        assertEquals(-1, RadialMenu.selectSlotIndex(0.0, -9.9, 10.0, 6))
        assertEquals(-1, RadialMenu.selectSlotIndex(6.0, 7.0, 10.0, 6))
        assertEquals(0, RadialMenu.selectSlotIndex(0.0, -10.0, 10.0, 6))
    }

    @Test
    fun `no fields selects nothing`() {
        assertEquals(-1, RadialMenu.selectSlotIndex(0.0, -100.0, 10.0, 0))
        assertEquals(-1, RadialMenu.selectSlotIndex(0.0, -100.0, 10.0, -1))
    }

    @Test
    fun `field 0 is at the top and fields run clockwise`() {
        assertEquals(0, RadialMenu.selectSlotIndex(0.0, -50.0, 10.0, 4))
        assertEquals(1, RadialMenu.selectSlotIndex(50.0, 0.0, 10.0, 4))
        assertEquals(2, RadialMenu.selectSlotIndex(0.0, 50.0, 10.0, 4))
        assertEquals(3, RadialMenu.selectSlotIndex(-50.0, 0.0, 10.0, 4))
    }

    @Test
    fun `two fields split top and bottom half`() {
        // Field 0 is centered at the top, so it covers -90..+90 degrees around the top (right half
        // belongs to field 1 from exactly 90 degrees on).
        assertEquals(0, select(0.0, 50.0, 2))
        assertEquals(0, select(45.0, 50.0, 2))
        assertEquals(0, select(-45.0, 50.0, 2))
        assertEquals(1, select(135.0, 50.0, 2))
        assertEquals(1, select(180.0, 50.0, 2))
        assertEquals(1, select(225.0, 50.0, 2))
    }

    @Test
    fun `six fields cover 60 degrees each centered on their direction`() {
        for (index in 0 until 6) {
            val center = index * 60.0
            assertEquals("center of field $index", index, select(center, 50.0, 6))
            assertEquals("just inside field $index (late)", index, select(center + 29.0, 50.0, 6))
            assertEquals("just inside field $index (early)", index, select(center - 29.0, 50.0, 6))
        }
    }

    @Test
    fun `six fields change at the 30 degree boundaries`() {
        assertEquals(0, select(29.9, 50.0, 6))
        assertEquals(1, select(30.1, 50.0, 6))
        assertEquals(1, select(89.9, 50.0, 6))
        assertEquals(2, select(90.1, 50.0, 6))
        assertEquals(5, select(329.9, 50.0, 6))
        assertEquals(0, select(330.1, 50.0, 6))
        assertEquals(0, select(-29.9, 50.0, 6))
        assertEquals(5, select(-30.1, 50.0, 6))
    }

    @Test
    fun `twelve fields cover 30 degrees each`() {
        for (index in 0 until 12) {
            val center = index * 30.0
            assertEquals("center of field $index", index, select(center, 50.0, 12))
            assertEquals("late edge of field $index", index, select(center + 14.0, 50.0, 12))
            assertEquals("early edge of field $index", index, select(center - 14.0, 50.0, 12))
        }
        assertEquals(1, select(15.1, 50.0, 12))
        assertEquals(0, select(14.9, 50.0, 12))
        assertEquals(0, select(-14.9, 50.0, 12))
        assertEquals(11, select(-15.1, 50.0, 12))
    }

    @Test
    fun `selection does not depend on the distance outside the dead zone`() {
        assertEquals(1, select(60.0, 11.0, 6))
        assertEquals(1, select(60.0, 500.0, 6))
    }

    @Test
    fun `number key menu has requested fields with keys 1 to 6`() {
        val menu = RadialMenu.createNumberKeys("button-1", "Radial button", 6)

        assertEquals("button-1", menu.id)
        assertEquals(6, menu.slots.size)
        assertEquals(
            listOf(Binding.KEY_1, Binding.KEY_2, Binding.KEY_3, Binding.KEY_4, Binding.KEY_5, Binding.KEY_6),
            menu.slots.map { it.binding },
        )
        assertEquals(6, menu.enabledSlots.size)
        assertEquals("1", menu.slots[0].displayLabel)
    }

    @Test
    fun `number key menu clamps the field count`() {
        assertEquals(RadialMenu.MIN_SLOTS, RadialMenu.createNumberKeys("a", "a", 0).slots.size)
        assertEquals(RadialMenu.MAX_SLOTS, RadialMenu.createNumberKeys("a", "a", 99).slots.size)
        assertEquals(Binding.KEY_0, RadialMenu.createNumberKeys("a", "a", 12).slots[9].binding)
    }

    @Test
    fun `menu id and slots survive json round trip and copy`() {
        val menu = RadialMenu.createNumberKeys("button-2", "Radial button", 4)
        menu.slots[1].label = "Heal"

        val restored = RadialMenu.fromJSONObject(menu.toJSONObject())
        val copy = menu.copy()

        assertEquals("button-2", restored.id)
        assertEquals("Heal", restored.slots[1].label)
        assertEquals(menu.toJSONObject().toString(), restored.toJSONObject().toString())
        assertEquals(menu.toJSONObject().toString(), copy.toJSONObject().toString())
        assertNotSame(menu, copy)
        assertNotSame(menu.slots[0], copy.slots[0])
    }

    @Test
    fun `number keys continue the sequence beyond six fields`() {
        assertEquals(Binding.KEY_1, RadialMenu.numberKeyForField(0))
        assertEquals(Binding.KEY_6, RadialMenu.numberKeyForField(5))
        assertEquals(Binding.KEY_7, RadialMenu.numberKeyForField(6))
        assertEquals(Binding.KEY_8, RadialMenu.numberKeyForField(7))
        assertEquals(Binding.KEY_9, RadialMenu.numberKeyForField(8))
        assertEquals(Binding.KEY_0, RadialMenu.numberKeyForField(9))
        assertEquals(Binding.KEY_MINUS, RadialMenu.numberKeyForField(10))
        assertEquals(Binding.KEY_EQUAL, RadialMenu.numberKeyForField(11))
        assertEquals(Binding.NONE, RadialMenu.numberKeyForField(12))
        assertEquals(Binding.NONE, RadialMenu.numberKeyForField(-1))
    }

    @Test
    fun `raising six fields to eight prefills fields 7 and 8 with keys 7 and 8`() {
        val existing = RadialMenu.createNumberKeys("button-1", "Radial button", 6).slots.map { it.bindingCombo }
        // Same step as the editor dialog: fields beyond the old count are empty before the prefill.
        val grown = (0 until 8).map { index ->
            RadialMenu.prefillForNewField(index, existing.getOrNull(index) ?: BindingCombo.none())
        }

        assertEquals(existing, grown.take(6))
        assertEquals(BindingCombo.of(Binding.KEY_7), grown[6])
        assertEquals(BindingCombo.of(Binding.KEY_8), grown[7])
        assertEquals(8, grown.count { !it.isEmpty })
    }

    @Test
    fun `prefill keeps keys the user already chose and handles null`() {
        val chosen = BindingCombo.of(Binding.KEY_F5)

        assertEquals(chosen, RadialMenu.prefillForNewField(6, chosen))
        assertEquals(BindingCombo.of(Binding.KEY_7), RadialMenu.prefillForNewField(6, BindingCombo.none()))
        assertEquals(BindingCombo.of(Binding.KEY_7), RadialMenu.prefillForNewField(6, null))
    }

    private fun menuWithEmptyFields(): RadialMenu {
        val menu = RadialMenu.createNumberKeys("button-5", "Radial button", 6)
        menu.addSlot(RadialMenu.Slot("", BindingCombo.none()))
        menu.addSlot(RadialMenu.Slot("", BindingCombo.none()))
        return menu
    }

    @Test
    fun `sector count is the field count even with empty fields`() {
        val menu = menuWithEmptyFields()

        assertEquals(8, menu.slots.size)
        assertEquals(6, menu.enabledSlots.size)
        // 315 degrees clockwise from the top lies in field 7 (index 7) of an 8 field wheel, which is empty.
        assertEquals(7, select(315.0, 50.0, menu.slots.size))
        assertEquals(false, menu.slots[7].isEnabled)
        // With the old "enabled only" count the same direction would have hit field 5.
        assertEquals(5, select(315.0, 50.0, menu.enabledSlots.size))
    }

    @Test
    fun `empty fields survive json round trip and copy`() {
        val menu = menuWithEmptyFields()

        val restored = RadialMenu.fromJSONObject(menu.toJSONObject())
        val copy = menu.copy()

        assertEquals(8, restored.slots.size)
        assertEquals(8, copy.slots.size)
        assertEquals(6, restored.enabledSlots.size)
        assertEquals(true, restored.slots[7].bindingCombo.isEmpty)
    }

    @Test
    fun `menu button binding has a readable editor label`() {
        assertEquals("MENÜ (Einstellungen)", Binding.OPEN_NAVIGATION_MENU.toString())
    }

    @Test
    fun `chat command slot round trips and is enabled without a key`() {
        val slot = RadialMenu.Slot("Tanzen", BindingCombo.none(), "EMOTE_DANCE", "/dance")

        val json = slot.toJSONObject()
        val restored = RadialMenu.Slot.fromJSONObject(json)

        assertEquals("/dance", json.getString("chatCommand"))
        assertEquals("EMOTE_DANCE", json.getString("wowAction"))
        assertEquals("/dance", restored.chatCommand)
        assertEquals("EMOTE_DANCE", restored.wowAction)
        assertEquals("Tanzen", restored.label)
        assertTrue(restored.bindingCombo.isEmpty)
        assertTrue(restored.isEnabled)
    }

    @Test
    fun `free chat command slot without wow action keeps its text`() {
        val slot = RadialMenu.Slot("", BindingCombo.none(), null, "  /s Hallo  ")
        assertEquals("/s Hallo", slot.chatCommand)
        assertEquals("/s Hallo", slot.displayLabel)
        val restored = RadialMenu.Slot.fromJSONObject(slot.toJSONObject())
        assertEquals("/s Hallo", restored.chatCommand)
        assertNull(restored.wowAction)
        assertTrue(restored.isEnabled)
    }

    @Test
    fun `slot without chat command and without key stays disabled`() {
        val slot = RadialMenu.Slot.fromJSONObject(JSONObject().put("label", "x").put("binding", "NONE"))
        assertNull(slot.chatCommand)
        assertFalse(slot.isEnabled)
        slot.chatCommand = "   "
        assertNull(slot.chatCommand)
        assertFalse(slot.isEnabled)
        assertFalse(slot.toJSONObject().has("chatCommand"))
    }

    @Test
    fun `menu copy keeps chat commands`() {
        val menu = RadialMenu()
        menu.id = "button-7"
        menu.addSlot(RadialMenu.Slot("Winken", BindingCombo.none(), "EMOTE_WAVE", "/wave"))
        menu.addSlot(RadialMenu.Slot("Karte", BindingCombo.of(Binding.KEY_M), "TOGGLEWORLDMAP"))

        val copy = menu.copy()

        assertEquals("/wave", copy.slots[0].chatCommand)
        assertNull(copy.slots[1].chatCommand)
        assertEquals(2, copy.enabledSlots.size)
    }
}
