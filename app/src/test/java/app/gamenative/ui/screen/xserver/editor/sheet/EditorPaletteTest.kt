package app.gamenative.ui.screen.xserver.editor.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPaletteTest {
    private val aa = 4.5
    private val nonText = 3.0

    private fun assertText(foreground: Int, background: Int, name: String) {
        val ratio = EditorPalette.contrast(foreground, background)
        assertTrue("$name: $ratio is below 4.5", ratio >= aa)
    }

    @Test
    fun `contrast follows the WCAG definition`() {
        assertEquals(21.0, EditorPalette.contrast(0xFFFFFF, 0x000000), 0.01)
        assertEquals(1.0, EditorPalette.contrast(0x123456, 0x123456), 0.001)
    }

    @Test
    fun `text on every surface of the sheet reaches AA`() {
        val surfaces = listOf(EditorPalette.SURFACE, EditorPalette.SURFACE_VARIANT, EditorPalette.SURFACE_HIGH)
        for (surface in surfaces) {
            assertText(EditorPalette.ON_SURFACE, surface, "text")
            assertText(EditorPalette.ON_VARIANT, surface, "secondary text")
            assertText(EditorPalette.PRIMARY, surface, "accent text")
        }
    }

    @Test
    fun `buttons and selected chips reach AA`() {
        assertText(EditorPalette.ON_PRIMARY, EditorPalette.PRIMARY, "main button")
        assertText(EditorPalette.ON_PRIMARY_CONTAINER, EditorPalette.PRIMARY_CONTAINER, "selected chip")
        assertText(0xFFFFFF, EditorPalette.DONE, "Fertig")
        assertText(EditorPalette.ERROR, EditorPalette.SURFACE, "error text")
    }

    @Test
    fun `borders stay visible against the surfaces`() {
        for (surface in listOf(EditorPalette.SURFACE, EditorPalette.SURFACE_VARIANT)) {
            val ratio = EditorPalette.contrast(EditorPalette.OUTLINE, surface)
            assertTrue("border $ratio is below 3", ratio >= nonText)
        }
    }

    @Test
    fun `text stays readable when the sheet lies over a bright game picture`() {
        // 92 % slate over white is the worst case for the picture behind the sheet.
        val worst = EditorPalette.over(EditorPalette.SURFACE, EditorPalette.SURFACE_ALPHA, 0xFFFFFF)
        assertText(EditorPalette.ON_SURFACE, worst, "text over white")
        assertText(EditorPalette.ON_VARIANT, worst, "secondary text over white")
        assertText(EditorPalette.PRIMARY, worst, "accent over white")
    }
}
