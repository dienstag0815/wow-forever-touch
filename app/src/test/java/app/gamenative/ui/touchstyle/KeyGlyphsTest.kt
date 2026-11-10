package app.gamenative.ui.touchstyle

import org.junit.Rule
import app.gamenative.TestTexts
import app.gamenative.wowaction.BindingCaption
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which key gets a symbol (KeyGlyphs) and how big a label may be (TextFit). Pure logic, no Android classes. */
class KeyGlyphsTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private fun combo(vararg bindings: Binding, mode: BindingCombo.Mode = BindingCombo.Mode.SIMULTANEOUS) =
        BindingCombo.fromBindings(bindings.toList(), mode)

    private fun auto(combo: BindingCombo, segment: Int = 0) =
        BindingCaption.forSlot("", null, null, combo, segment, emoji = true)

    // ---- Assignment ------------------------------------------------------------------------------------------------

    @Test
    fun theBigWordKeysGetSymbols() {
        assertEquals(Glyph.SPACE, KeyGlyphs.glyphFor(Binding.KEY_SPACE))
        assertEquals(Glyph.TAB, KeyGlyphs.glyphFor(Binding.KEY_TAB))
        assertEquals(Glyph.SHIFT, KeyGlyphs.glyphFor(Binding.KEY_SHIFT_L))
        assertEquals(Glyph.SHIFT, KeyGlyphs.glyphFor(Binding.KEY_SHIFT_R))
        assertEquals(Glyph.ENTER, KeyGlyphs.glyphFor(Binding.KEY_ENTER))
        assertEquals(Glyph.BACKSPACE, KeyGlyphs.glyphFor(Binding.KEY_BKSP))
        assertEquals(Glyph.ARROW_UP, KeyGlyphs.glyphFor(Binding.KEY_UP))
        assertEquals(Glyph.ARROW_DOWN, KeyGlyphs.glyphFor(Binding.KEY_DOWN))
        assertEquals(Glyph.ARROW_LEFT, KeyGlyphs.glyphFor(Binding.KEY_LEFT))
        assertEquals(Glyph.ARROW_RIGHT, KeyGlyphs.glyphFor(Binding.KEY_RIGHT))
    }

    @Test
    fun mouseButtonsAndWheelGetSymbols() {
        assertEquals(Glyph.MOUSE_LEFT, KeyGlyphs.glyphFor(Binding.MOUSE_LEFT_BUTTON))
        assertEquals(Glyph.MOUSE_RIGHT, KeyGlyphs.glyphFor(Binding.MOUSE_RIGHT_BUTTON))
        assertEquals(Glyph.MOUSE_MIDDLE, KeyGlyphs.glyphFor(Binding.MOUSE_MIDDLE_BUTTON))
        assertEquals(Glyph.WHEEL_UP, KeyGlyphs.glyphFor(Binding.MOUSE_SCROLL_UP))
        assertEquals(Glyph.WHEEL_DOWN, KeyGlyphs.glyphFor(Binding.MOUSE_SCROLL_DOWN))
    }

    @Test
    fun ctrlAltLettersDigitsAndGamepadKeepTheirText() {
        for (binding in listOf(
            Binding.KEY_CTRL_L, Binding.KEY_CTRL_R, Binding.KEY_ALT_L, Binding.KEY_ESC, Binding.KEY_A, Binding.KEY_1,
            Binding.KEY_F5, Binding.MOUSE_MOVE_LEFT, Binding.GAMEPAD_BUTTON_A, Binding.NONE, Binding.OPEN_NAVIGATION_MENU,
        )) {
            assertNull(binding.name, KeyGlyphs.glyphFor(binding))
            assertNull(binding.name, KeyGlyphs.labelFor(BindingCombo.of(binding)))
        }
        assertNull(KeyGlyphs.glyphFor(null))
    }

    @Test
    fun everyGlyphHasAPositiveAspect() {
        for (glyph in Glyph.entries) assertTrue(glyph.name, glyph.aspect > 0.3f && glyph.aspect < 3f)
    }

    // ---- Labels ----------------------------------------------------------------------------------------------------

    @Test
    fun spaceIsOneSymbolAndKeepsTheOldTextAsFallback() {
        val label = KeyGlyphs.labelFor(BindingCombo.of(Binding.KEY_SPACE))!!
        assertEquals(listOf(LabelPart(Glyph.SPACE, "")), label.parts)
        assertEquals("Space", label.fallbackText)
        assertEquals("Space", KeyGlyphs.fallbackText(BindingCombo.of(Binding.KEY_SPACE)))
        assertFalse(label.hasText)
        assertEquals(1, label.glyphCount)
    }

    @Test
    fun rightMouseButtonIsASymbol() {
        val label = KeyGlyphs.labelFor(BindingCombo.of(Binding.MOUSE_RIGHT_BUTTON))!!
        assertEquals(listOf(LabelPart(Glyph.MOUSE_RIGHT, "")), label.parts)
        assertEquals("RMB", label.fallbackText)
    }

    @Test
    fun shiftPlusKeyIsSymbolPlusText() {
        val label = KeyGlyphs.labelFor(combo(Binding.KEY_SHIFT_L, Binding.KEY_1))!!
        assertEquals(listOf(LabelPart(Glyph.SHIFT, ""), LabelPart(null, "1")), label.parts)
        assertEquals("⇧1", label.fallbackText)
        assertTrue(label.hasText)
    }

    @Test
    fun otherCombinationsAreJoinedWithPlus() {
        val label = KeyGlyphs.labelFor(combo(Binding.KEY_CTRL_L, Binding.KEY_SPACE))!!
        assertEquals(listOf(LabelPart(null, "Strg+"), LabelPart(Glyph.SPACE, "")), label.parts)
        val tabAlt = KeyGlyphs.labelFor(combo(Binding.KEY_ALT_L, Binding.KEY_TAB))!!
        assertEquals(listOf(LabelPart(null, "Alt+"), LabelPart(Glyph.TAB, "")), tabAlt.parts)
    }

    @Test
    fun sequencesUseTheArrowAndNoTightShift() {
        val label = KeyGlyphs.labelFor(combo(Binding.KEY_SPACE, Binding.KEY_1, mode = BindingCombo.Mode.SEQUENCE))!!
        assertEquals(listOf(LabelPart(Glyph.SPACE, ""), LabelPart(null, "→1")), label.parts)
        val shiftThen = KeyGlyphs.labelFor(combo(Binding.KEY_SHIFT_L, Binding.KEY_1, mode = BindingCombo.Mode.SEQUENCE))!!
        assertEquals(listOf(LabelPart(Glyph.SHIFT, ""), LabelPart(null, "→1")), shiftThen.parts)
    }

    @Test
    fun twoSymbolsInOneCombination() {
        val label = KeyGlyphs.labelFor(combo(Binding.KEY_SHIFT_L, Binding.KEY_TAB))!!
        assertEquals(listOf(LabelPart(Glyph.SHIFT, ""), LabelPart(Glyph.TAB, "")), label.parts)
        assertEquals(2, label.glyphCount)
    }

    @Test
    fun noKeyNoLabelAndTheResultIsCached() {
        assertNull(KeyGlyphs.labelFor(null))
        assertNull(KeyGlyphs.labelFor(BindingCombo.none()))
        val space = BindingCombo.of(Binding.KEY_SPACE)
        assertSame(KeyGlyphs.labelFor(space), KeyGlyphs.labelFor(BindingCombo.of(Binding.KEY_SPACE)))
        val letter = BindingCombo.of(Binding.KEY_Q)
        assertNull(KeyGlyphs.labelFor(letter))
        assertNull(KeyGlyphs.labelFor(letter))
    }

    // ---- Captions: own label, WoW action and chat command stay --------------------------------------------------------

    @Test
    fun automaticCaptionOfASymbolKeyGetsTheLabel() {
        val space = BindingCombo.of(Binding.KEY_SPACE)
        assertNotNull(KeyGlyphs.forCaption(auto(space), null, null, space))
        assertNotNull(KeyGlyphs.forCaption(auto(space, 3), null, "  ", space))
        val rmb = BindingCombo.of(Binding.MOUSE_RIGHT_BUTTON)
        assertNotNull(KeyGlyphs.forCaption(auto(rmb, 2), null, null, rmb))
        val tab = BindingCombo.of(Binding.KEY_TAB)
        assertNotNull(KeyGlyphs.forCaption(auto(tab), null, null, tab))
    }

    @Test
    fun ownLabelStaysUnchanged() {
        val space = BindingCombo.of(Binding.KEY_SPACE)
        val own = BindingCaption.forSlot("Jump", null, null, space, 1, emoji = true)
        assertNull(KeyGlyphs.forCaption(own, null, null, space))
        val emoji = BindingCaption.forSlot("🎒", null, null, space, 1, emoji = true)
        assertNull(KeyGlyphs.forCaption(emoji, null, null, space))
    }

    @Test
    fun leftOverRingNumberIsNotAnOwnLabel() {
        // "3" on a ring segment whose key is Space is a leftover of the creation: the symbol shows.
        val space = BindingCombo.of(Binding.KEY_SPACE)
        val caption = BindingCaption.forSlot("3", null, null, space, 3, emoji = true)
        assertNotNull(KeyGlyphs.forCaption(caption, null, null, space))
    }

    @Test
    fun wowActionAndChatCommandStayUnchanged() {
        val space = BindingCombo.of(Binding.KEY_SPACE)
        val action = BindingCaption.forSlot("", "TOGGLEWORLDMAP", null, space, 1, emoji = false)
        assertNull(KeyGlyphs.forCaption(action, "TOGGLEWORLDMAP", null, space))
        val chat = BindingCaption.forSlot("", null, "/dance", space, 1, emoji = true)
        assertNull(KeyGlyphs.forCaption(chat, null, "/dance", space))
    }

    // ---- Font size ---------------------------------------------------------------------------------------------------

    @Test
    fun textIsAtMostFortyFivePercentOfTheDiameterHigh() {
        assertEquals(45f, TextFit.maxSize(100f, false), 0.001f)
        assertEquals(90f, TextFit.maxSize(200f, false), 0.001f)
        assertEquals(0f, TextFit.maxSize(-5f, false), 0f)
    }

    @Test
    fun ringFieldsAreSmallerThanTheMiddle() {
        assertTrue(TextFit.maxSize(100f, true) < TextFit.maxSize(100f, false))
        assertEquals(45f * TextFit.RING_FACTOR, TextFit.maxSize(100f, true), 0.001f)
    }

    @Test
    fun shortTextGetsTheMaximumLongTextShrinksToTheWidth() {
        // widthPerUnit: width of the text at font size 1 ("A" about 0.7, "Backspace" about 5.5)
        assertEquals(45f, TextFit.size(100f, 100f, 0.7f, false), 0.001f)
        assertEquals(100f / 5.5f, TextFit.size(100f, 100f, 5.5f, false), 0.001f)
        assertTrue(TextFit.size(100f, 100f, 5.5f, false) < TextFit.size(100f, 100f, 0.7f, false))
    }

    @Test
    fun smallAndBigElementsScaleTheSize() {
        val small = TextFit.size(40f, 40f, 1f, false)
        val big = TextFit.size(160f, 160f, 1f, false)
        assertEquals(18f, small, 0.001f)
        assertEquals(72f, big, 0.001f)
        // a wide pill: the height (diameter = short side) limits, not the width
        assertEquals(18f, TextFit.size(40f, 300f, 1f, false), 0.001f)
    }

    @Test
    fun callersCapAndEmptyRoomAreRespected() {
        assertEquals(20f, TextFit.size(200f, 200f, 1f, false, 20f), 0.001f)
        assertEquals(0f, TextFit.size(100f, 0f, 1f, false), 0f)
        assertEquals(45f, TextFit.size(100f, 50f, 0f, false), 0.001f)
    }

    @Test
    fun belowTheMinimumTextIsCutInsteadOfShrunk() {
        val diameter = 100f
        val minimum = TextFit.minSize(diameter, false)
        assertEquals(22.5f, minimum, 0.001f)
        assertTrue(minimum < TextFit.maxSize(diameter, false))
        assertTrue(TextFit.needsCut(10f, diameter, false))
        assertTrue(!TextFit.needsCut(30f, diameter, false))
        assertTrue(!TextFit.needsCut(22.5f, diameter, false))
    }

    @Test
    fun theMinimumNeverExceedsTheMaximumOrTheCap() {
        // tiny element: max 4.5 px, the pixel floor of 10 must not lift the minimum above it
        assertEquals(4.5f, TextFit.minSize(10f, false), 0.001f)
        assertEquals(9f, TextFit.minSize(200f, false, 9f), 0.001f)
        // medium element: floor of 10 px applies
        assertEquals(10f, TextFit.minSize(30f, false), 0.001f)
    }

    @Test
    fun cutShortensUntilItFitsAndKeepsAtLeastOneCharacter() {
        val perChar = 10f
        assertEquals("Backspace", TextFit.cut("Backspace", 100f) { it.codePointCount(0, it.length) * perChar })
        assertEquals("Backs", TextFit.cut("Backspace", 50f) { it.codePointCount(0, it.length) * perChar })
        assertEquals("B", TextFit.cut("Backspace", 1f) { it.codePointCount(0, it.length) * perChar })
        // an emoji (surrogate pair) is not split
        assertEquals("a🎒", TextFit.cut("a🎒b", 25f) { it.codePointCount(0, it.length) * perChar })
    }
}
