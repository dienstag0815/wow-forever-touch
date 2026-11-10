package com.winlator.inputcontrols

import com.winlator.widget.InputControlsView
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Profiles saved before the picture-area feature ("Symbol aus Spielbild") was removed carry a "mirror" object on buttons,
 * ring segments and wheel fields. They must still load; the key is ignored and is gone after the next save.
 */
@RunWith(RobolectricTestRunner::class)
class OldMirrorKeyProfileTest {
    private val oldKey = """{"x":-0.1234,"y":0.9012,"w":0.054,"h":0.054}"""

    private fun mockView(): InputControlsView {
        val view = mockk<InputControlsView>(relaxed = true)
        every { view.snappingSize } returns 10
        every { view.maxWidth } returns 1000
        every { view.maxHeight } returns 1000
        return view
    }

    @Test
    fun `button with an old mirror key loads and saves without it`() {
        val element = ControlElement(mockView()).apply {
            setType(ControlElement.Type.BUTTON)
            setX(100)
            setY(100)
            readExtrasFromJson(JSONObject("""{"cornerRadius":0.5,"mirror":$oldKey}"""))
        }
        assertEquals(0.5f, element.cornerRadius, 1e-6f)
        assertFalse(element.toJSONObject()!!.has("mirror"))
    }

    @Test
    fun `ring segment with an old mirror key loads and saves without it`() {
        val view = mockView()
        val source = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            setX(200)
            setY(200)
            setRingSegments(4)
            getRingItem(2).label = "Heilung"
        }
        val json = source.toJSONObject()!!
        json.getJSONObject("ring").getJSONArray("items").getJSONObject(2).put("mirror", JSONObject(oldKey))

        val loaded = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            readExtrasFromJson(JSONObject(json.toString()))
        }
        assertEquals("Heilung", loaded.getRingItem(2).label)
        val items = loaded.toJSONObject()!!.getJSONObject("ring").getJSONArray("items")
        for (i in 0 until items.length()) assertFalse(items.getJSONObject(i).has("mirror"))
    }

    @Test
    fun `radial menu slot with an old mirror key loads and saves without it`() {
        val slot = RadialMenu.Slot.fromJSONObject(JSONObject("""{"label":"Karte","binding":"KEY_M","mirror":$oldKey}"""))
        assertEquals("Karte", slot.label)
        val written = slot.toJSONObject()
        assertFalse(written.has("mirror"))
        assertTrue(written.has("label"))
    }
}
