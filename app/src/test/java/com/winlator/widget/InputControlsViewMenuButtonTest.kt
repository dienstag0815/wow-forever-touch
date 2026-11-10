package com.winlator.widget

import androidx.test.core.app.ApplicationProvider
import com.winlator.inputcontrols.Binding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InputControlsViewMenuButtonTest {
    @Test
    fun `menu binding on an on-screen button opens the quick menu once per press`() {
        val view = InputControlsView(ApplicationProvider.getApplicationContext())
        var opened = 0
        view.setOpenNavigationMenuCallback(Runnable { opened++ })

        view.handleInputEvent(Binding.OPEN_NAVIGATION_MENU, true, 0f)
        // Held down: no second trigger.
        view.handleInputEvent(Binding.OPEN_NAVIGATION_MENU, true, 0f)
        assertEquals(1, opened)

        view.handleInputEvent(Binding.OPEN_NAVIGATION_MENU, false, 0f)
        view.handleInputEvent(Binding.OPEN_NAVIGATION_MENU, true, 0f)
        assertEquals(2, opened)
    }

    @Test
    fun `menu binding without a callback does nothing`() {
        val view = InputControlsView(ApplicationProvider.getApplicationContext())

        view.handleInputEvent(Binding.OPEN_NAVIGATION_MENU, true, 0f)
        view.handleInputEvent(Binding.OPEN_NAVIGATION_MENU, false, 0f)
    }

    @Test
    fun `editor binding on an on-screen button starts the editor once per press and not while editing`() {
        val view = InputControlsView(ApplicationProvider.getApplicationContext())
        var opened = 0
        view.setOpenControlsEditorCallback(Runnable { opened++ })

        view.handleInputEvent(Binding.OPEN_CONTROLS_EDITOR, true, 0f)
        view.handleInputEvent(Binding.OPEN_CONTROLS_EDITOR, true, 0f)
        assertEquals(1, opened)
        view.handleInputEvent(Binding.OPEN_CONTROLS_EDITOR, false, 0f)

        // In the edit mode the button does not fire again.
        view.setEditMode(true)
        view.handleInputEvent(Binding.OPEN_CONTROLS_EDITOR, true, 0f)
        view.handleInputEvent(Binding.OPEN_CONTROLS_EDITOR, false, 0f)
        assertEquals(1, opened)

        view.setEditMode(false)
        view.handleInputEvent(Binding.OPEN_CONTROLS_EDITOR, true, 0f)
        assertEquals(2, opened)
    }

    @Test
    fun `editor binding without a callback does nothing`() {
        val view = InputControlsView(ApplicationProvider.getApplicationContext())

        view.handleInputEvent(Binding.OPEN_CONTROLS_EDITOR, true, 0f)
        view.handleInputEvent(Binding.OPEN_CONTROLS_EDITOR, false, 0f)
    }
}
