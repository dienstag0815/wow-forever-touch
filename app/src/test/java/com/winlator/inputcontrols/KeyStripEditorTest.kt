package com.winlator.inputcontrols

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import app.gamenative.TestTexts
import app.gamenative.ui.screen.xserver.editor.ElementPalette
import app.gamenative.ui.screen.xserver.editor.PaletteKind
import app.gamenative.ui.screen.xserver.editor.PreviewKind
import app.gamenative.ui.screen.xserver.editor.previewKindOf
import app.gamenative.ui.screen.xserver.editor.sheet.changeType
import com.winlator.widget.InputControlsView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Key strip in the editor: entries and the strip stay in step (growing, JSON round trip), the kind of element can be switched
 * without losing position and size, the gallery offers the key strip, and an unassigned button has no "NONE" text.
 */
@RunWith(RobolectricTestRunner::class)
class KeyStripEditorTest {
    private fun mockView(): InputControlsView {
        val view = mockk<InputControlsView>(relaxed = true)
        every { view.snappingSize } returns 10
        every { view.maxWidth } returns 1000
        every { view.maxHeight } returns 1000
        return view
    }

    private fun strip(view: InputControlsView, visible: Int): ControlElement = ControlElement(view).apply {
        setType(ControlElement.Type.RANGE_BUTTON)
        setX(500)
        setY(500)
        setRange(ControlElement.Range.FROM_A_TO_Z)
        convertToCustomRange()
        setBindingCount(visible)
    }

    private fun key(name: String) = BindingCombo.of(Binding.valueOf(name))

    // ---- entries and strip stay in step -----------------------------------------------------------------------------

    @Test
    fun `a narrow strip grows to four keys when an entry is added`() {
        val element = strip(mockView(), visible = 2)
        assertEquals(2, element.bindingCount)
        element.addRangeItem()
        assertEquals(4, element.bindingCount)
        // 4 keys of 40 each; the box follows at once
        assertEquals(160, element.boundingBox.width())
    }

    @Test
    fun `a strip with two entries grows by one key per added entry up to four`() {
        val element = strip(mockView(), visible = 4)
        while (element.rangeItemCount > 2) element.removeRangeItem(0)
        assertEquals(2, element.bindingCount)
        element.addRangeItem()
        assertEquals(3, element.bindingCount)
        element.addRangeItem()
        assertEquals(4, element.bindingCount)
        element.addRangeItem()
        assertEquals(5, element.bindingCount)
    }

    @Test
    fun `a strip set to more than four keys is left alone when an entry is added`() {
        val element = strip(mockView(), visible = 6)
        element.addRangeItem()
        assertEquals(6, element.bindingCount)
    }

    @Test
    fun `entry changes survive saving and loading with the same entries and width`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        val profile = ControlsProfile(context, 4931)
        val element = strip(view, visible = 2)
        // three edits like on the entries page: add + assign, label, move, remove
        val added = element.addRangeItem()
        element.getRangeItem(added).setBindingCombo(key("KEY_T"))
        element.getRangeItem(1).setLabel("zwei")
        element.moveRangeItem(added, 0)
        element.removeRangeItem(5)
        profile.addElement(element)
        profile.save()

        val loadedProfile = ControlsProfile(context, 4931)
        loadedProfile.loadElements(view)
        val loaded = loadedProfile.elements.single()
        assertEquals(element.toJSONObject().toString(), loaded.toJSONObject().toString())
        assertEquals(element.rangeItemCount, loaded.rangeItemCount)
        assertEquals(element.bindingCount, loaded.bindingCount)
        assertEquals(element.boundingBox.width(), loaded.boundingBox.width())
        assertEquals(key("KEY_T"), loaded.getRangeItem(0).bindingCombo)
        assertEquals("zwei", loaded.getRangeItem(2).label)
    }

    // ---- kind of element --------------------------------------------------------------------------------------------

    @Test
    fun `switching the kind keeps position and size`() {
        val element = ControlElement(mockView()).apply {
            setType(ControlElement.Type.BUTTON)
            setX(320)
            setY(240)
            setScale(1.5f)
        }
        for (type in listOf(
            ControlElement.Type.RANGE_BUTTON,
            ControlElement.Type.STICK,
            ControlElement.Type.SCROLL_WHEEL,
            ControlElement.Type.BUTTON,
        )) {
            changeType(element, type)
            assertEquals(type, element.type)
            assertEquals(320, element.x.toInt())
            assertEquals(240, element.y.toInt())
            assertEquals(1.5f, element.scale, 0.0001f)
        }
    }

    @Test
    fun `a key strip has its own name and picture, also in the gallery`() {
        val element = strip(mockView(), visible = 4)
        assertEquals(PreviewKind.KEY_STRIP, previewKindOf(element))
        // label and title are string resource ids (the screen resolves them with stringResource), so the test resolves them too
        assertEquals("Tastenleiste", TestTexts.text(previewKindOf(element).label, emptyList(), "de"))
        assertTrue(PaletteKind.KEY_STRIP in PaletteKind.available())
        assertEquals("Tastenleiste", TestTexts.text(PaletteKind.KEY_STRIP.title, emptyList(), "de"))
        // a pill-shaped button is still the "Längliche Taste"
        val wide = ControlElement(mockView()).apply {
            setType(ControlElement.Type.BUTTON)
            setShape(ControlElement.Shape.PILL)
        }
        assertEquals(PreviewKind.WIDE_BUTTON, previewKindOf(wide))
    }

    @Test
    fun `the gallery builds a key strip with the A to Z template`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = mockView()
        val created = ElementPalette.create(PaletteKind.KEY_STRIP, view, ControlsProfile(context, 4932), context)
        assertEquals(ControlElement.Type.RANGE_BUTTON, created.type)
        assertEquals(ControlElement.Range.FROM_A_TO_Z, created.range)
        assertEquals(26, created.rangeCount)
        assertTrue(created.bindingCount >= 1)
    }

    // ---- unassigned button ------------------------------------------------------------------------------------------

    private fun displayText(element: ControlElement): String {
        val method = ControlElement::class.java.getDeclaredMethod("getDisplayText")
        method.isAccessible = true
        return method.invoke(element) as String
    }

    @Test
    fun `a new button has no assignment and no NONE label`() {
        val element = ControlElement(mockView()).apply { setType(ControlElement.Type.BUTTON) }
        assertTrue(element.isUnassignedButton)
        assertEquals("", displayText(element))
    }

    @Test
    fun `any assignment makes a button assigned`() {
        val view = mockView()
        fun button() = ControlElement(view).apply { setType(ControlElement.Type.BUTTON) }

        assertFalse(button().apply { setBindingComboAt(0, key("KEY_A")) }.isUnassignedButton)
        assertFalse(button().apply { setBindingComboAt(1, key("KEY_B")) }.isUnassignedButton)
        assertFalse(button().apply { setText("Hallo") }.isUnassignedButton)
        assertFalse(button().apply { setWowAction("TOGGLEWORLDMAP") }.isUnassignedButton)
        assertFalse(button().apply { setChatCommand("/dance") }.isUnassignedButton)
        assertFalse(button().apply { setIconId(3) }.isUnassignedButton)
        assertFalse(button().apply { setRadialMenuId("menu") }.isUnassignedButton)
        // other kinds are never "unassigned buttons"
        assertFalse(ControlElement(view).apply { setType(ControlElement.Type.D_PAD) }.isUnassignedButton)
        // a key still shows its name
        assertEquals("A", displayText(button().apply { setBindingComboAt(0, key("KEY_A")) }))
    }

    @Test
    fun `an unassigned button is not drawn and not tappable in the game`() {
        // the view is not in edit mode (relaxed mock: isEditMode is false)
        val element = ControlElement(mockView()).apply {
            setType(ControlElement.Type.BUTTON)
            setX(500)
            setY(500)
        }
        val canvas = mockk<Canvas>(relaxed = true)
        element.draw(canvas)
        verify(exactly = 0) { canvas.drawCircle(any(), any(), any(), any()) }
        verify(exactly = 0) { canvas.drawOval(any<RectF>(), any()) }
        verify(exactly = 0) { canvas.drawRoundRect(any<RectF>(), any(), any(), any()) }
        assertFalse(element.handleTouchDown(1, 500f, 500f))
    }
}
