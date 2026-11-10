package com.winlator.inputcontrols

import androidx.test.core.app.ApplicationProvider
import com.winlator.widget.InputControlsView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RadialButtonMenuTest {
    private fun radialButton(view: InputControlsView, menuId: String?) = ControlElement(view).apply {
        setType(ControlElement.Type.BUTTON)
        setX(100)
        setY(100)
        setBindingComboAt(0, BindingCombo.of(Binding.OPEN_RADIAL_MENU))
        radialMenuId = menuId
    }

    private fun mockView(): InputControlsView {
        val view = mockk<InputControlsView>(relaxed = true)
        every { view.maxWidth } returns 1000
        every { view.maxHeight } returns 1000
        every { view.snappingSize } returns 10
        return view
    }

    @Test
    fun `radial menu id is written to the element json`() {
        val element = radialButton(mockView(), "button-1")

        val json = element.toJSONObject()

        assertNotNull(json)
        assertEquals("button-1", json!!.getString("radialMenuId"))
        assertEquals("OPEN_RADIAL_MENU", json.getJSONArray("bindings").getString(0))
    }

    @Test
    fun `element without own menu writes no radial menu id`() {
        val json = radialButton(mockView(), null).toJSONObject()

        assertNotNull(json)
        assertFalse(json!!.has("radialMenuId"))
    }

    @Test
    fun `empty radial menu id is treated as default menu`() {
        val element = radialButton(mockView(), "")

        assertNull(element.radialMenuId)
    }

    @Test
    fun `changing the element type forgets the own menu`() {
        val element = radialButton(mockView(), "button-1")

        element.setType(ControlElement.Type.STICK)

        assertNull(element.radialMenuId)
    }

    @Test
    fun `touch down passes the own menu id to the view`() {
        val view = mockView()
        val element = radialButton(view, "button-3")

        val handled = element.handleTouchDown(1, 100f, 100f)

        assertTrue(handled)
        verify(exactly = 1) { view.handleRadialMenuTouchDown(1, 100f, 100f, "button-3", null) }
    }

    @Test
    fun `profile keeps default menu and button menus apart`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = ControlsProfile(context, 4242)
        val defaultMenu = profile.defaultRadialMenu

        val own = profile.createButtonRadialMenu(6)
        val second = profile.createButtonRadialMenu(4)

        assertEquals("button-1", own.id)
        assertEquals("button-2", second.id)
        assertSame(own, profile.getRadialMenu("button-1"))
        assertSame(second, profile.getRadialMenu("button-2"))
        assertSame(defaultMenu, profile.getRadialMenu(null))
        assertSame(defaultMenu, profile.getRadialMenu("unknown"))
        assertNull(profile.findRadialMenu("unknown"))
        assertEquals(3, profile.radialMenus.size)
        assertSame(defaultMenu, profile.radialMenus[0])
    }

    @Test
    fun `replacing the default menu keeps button menus`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = ControlsProfile(context, 4243)
        val own = profile.createButtonRadialMenu(6)
        val newDefault = RadialMenu.createNumberKeys(RadialMenu.DEFAULT_ID, "Radial Menu", 3)

        profile.defaultRadialMenu = newDefault

        assertSame(newDefault, profile.defaultRadialMenu)
        assertSame(own, profile.findRadialMenu(own.id))
        assertEquals(2, profile.radialMenus.size)
    }

    @Test
    fun `put replaces by id and remove never drops the default menu`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = ControlsProfile(context, 4244)
        val own = profile.createButtonRadialMenu(6)
        val edited = RadialMenu.createNumberKeys(own.id, "Radial button", 8)

        profile.putRadialMenu(edited)

        assertSame(edited, profile.findRadialMenu(own.id))
        assertEquals(2, profile.radialMenus.size)
        assertTrue(profile.removeRadialMenu(own.id))
        assertFalse(profile.removeRadialMenu(RadialMenu.DEFAULT_ID))
        assertEquals(1, profile.radialMenus.size)
    }

    @Test
    fun `edited button menu with count labels and empty fields survives save and reload`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val profile = ControlsProfile(context, 4245)
        profile.name = "Radial persistence"
        val own = profile.createButtonRadialMenu(6)

        // Same shape the editor dialog saves: 8 fields, field 2 relabelled, fields 7 and 8 without keys.
        val edited = RadialMenu().apply {
            id = own.id
            name = own.name
        }
        for (index in 0 until 6) {
            edited.addSlot(RadialMenu.Slot(if (index == 1) "Heal" else "", RadialMenu.numberKeyForField(index)))
        }
        edited.addSlot(RadialMenu.Slot("", BindingCombo.none()))
        edited.addSlot(RadialMenu.Slot("", BindingCombo.none()))
        profile.putRadialMenu(edited)
        profile.save()

        val reloaded = ControlsProfile(context, 4245)
        reloaded.loadRadialMenus()
        val menu = reloaded.findRadialMenu(own.id)

        assertNotNull(menu)
        assertEquals(8, menu!!.slots.size)
        assertEquals("Heal", menu.slots[1].label)
        assertEquals(Binding.KEY_6, menu.slots[5].binding)
        assertTrue(menu.slots[6].bindingCombo.isEmpty)
        assertTrue(menu.slots[7].bindingCombo.isEmpty)
        assertEquals(2, reloaded.radialMenus.size)
    }

    // ---- "opens to" (radialDirection) ---------------------------------------------------------------------------

    @Test
    fun `without a direction the json has no direction field and the menu opens automatically`() {
        val element = radialButton(mockView(), "button-1")

        assertNull(element.radialDirection)
        assertFalse(element.toJSONObject()!!.has("radialDirection"))
    }

    @Test
    fun `a fixed direction is written, read back and copied`() {
        val view = mockView()
        for (direction in listOf("up", "down", "left", "right")) {
            val element = radialButton(view, "button-1")
            element.setRadialDirection(direction)

            val json = element.toJSONObject()!!
            assertEquals(direction, json.getString("radialDirection"))

            val read = radialButton(view, "button-1")
            read.readExtrasFromJson(json)
            assertEquals(direction, read.radialDirection)

            val copy = radialButton(view, "button-1")
            copy.copyExtrasFrom(element)
            assertEquals(direction, copy.radialDirection)
        }
    }

    @Test
    fun `old profiles and unknown values mean automatic`() {
        val element = radialButton(mockView(), "button-1")
        element.setRadialDirection("down")

        element.readExtrasFromJson(org.json.JSONObject())
        assertNull(element.radialDirection)

        element.setRadialDirection("diagonal")
        assertNull(element.radialDirection)
        element.setRadialDirection("")
        assertNull(element.radialDirection)
        element.setRadialDirection("auto")
        assertNull(element.radialDirection)
    }

    @Test
    fun `touch down passes the fixed direction to the view`() {
        val view = mockView()
        val element = radialButton(view, "button-3")
        element.setRadialDirection("left")

        assertTrue(element.handleTouchDown(1, 100f, 100f))

        verify(exactly = 1) { view.handleRadialMenuTouchDown(1, 100f, 100f, "button-3", "left") }
    }
}
