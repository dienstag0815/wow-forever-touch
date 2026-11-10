package app.gamenative.wowaction

import org.junit.Rule
import app.gamenative.TestTexts
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BindingCaptionTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private fun auto(binding: Binding, segment: Int = 1) =
        BindingCaption.forSlot("", null, null, BindingCombo.of(binding), segment, emoji = true).text

    @Test
    fun `keys get short readable names`() {
        assertEquals("Space", auto(Binding.KEY_SPACE))
        assertEquals("⇧", auto(Binding.KEY_SHIFT_L))
        assertEquals("⇧", auto(Binding.KEY_SHIFT_R))
        assertEquals("Strg", auto(Binding.KEY_CTRL_L))
        assertEquals("Alt", auto(Binding.KEY_ALT_L))
        assertEquals("Tab", auto(Binding.KEY_TAB))
        assertEquals("↵", auto(Binding.KEY_ENTER))
        assertEquals("Esc", auto(Binding.KEY_ESC))
        assertEquals("←", auto(Binding.KEY_LEFT))
        assertEquals("↑", auto(Binding.KEY_UP))
        assertEquals("→", auto(Binding.KEY_RIGHT))
        assertEquals("↓", auto(Binding.KEY_DOWN))
        assertEquals("1", auto(Binding.KEY_1))
        assertEquals("B", auto(Binding.KEY_B))
        assertEquals("F5", auto(Binding.KEY_F5))
        assertEquals("NP5", auto(Binding.KEY_KP_5))
        assertEquals("LMB", auto(Binding.MOUSE_LEFT_BUTTON))
        assertEquals("RMB", auto(Binding.MOUSE_RIGHT_BUTTON))
        assertEquals("MMB", auto(Binding.MOUSE_MIDDLE_BUTTON))
        assertEquals("⇞", auto(Binding.MOUSE_SCROLL_UP))
        assertEquals("⇟", auto(Binding.MOUSE_SCROLL_DOWN))
        assertEquals("LB", auto(Binding.GAMEPAD_BUTTON_L1))
        assertEquals("A", auto(Binding.GAMEPAD_BUTTON_A))
        assertEquals("D↑", auto(Binding.GAMEPAD_DPAD_UP))
        assertEquals("Menü", auto(Binding.OPEN_NAVIGATION_MENU))
        assertEquals("Bearb.", auto(Binding.OPEN_CONTROLS_EDITOR))
        assertEquals("", auto(Binding.NONE))
    }

    @Test
    fun `combinations`() {
        val shift1 = BindingCombo.fromBindings(listOf(Binding.KEY_SHIFT_L, Binding.KEY_1))
        assertEquals("⇧1", BindingCaption.forCombo(shift1))
        val ctrlC = BindingCombo.fromBindings(listOf(Binding.KEY_CTRL_L, Binding.KEY_C))
        assertEquals("Strg+C", BindingCaption.forCombo(ctrlC))
        val sequence = BindingCombo.fromBindings(listOf(Binding.KEY_1, Binding.KEY_2), BindingCombo.Mode.SEQUENCE)
        assertEquals("1→2", BindingCaption.forCombo(sequence))
        assertEquals("", BindingCaption.forCombo(BindingCombo.none()))
        assertEquals("", BindingCaption.forCombo(null))
    }

    @Test
    fun `wow action shows emoji or symbol depending on the mode`() {
        val combo = BindingCombo.of(Binding.KEY_B)
        val emoji = BindingCaption.forSlot("", "OPENALLBAGS", null, combo, 4, emoji = true)
        assertEquals("🎒", emoji.text)
        assertNull(emoji.symbol)

        val symbol = BindingCaption.forSlot("", "OPENALLBAGS", null, combo, 4, emoji = false)
        assertEquals("bag", symbol.symbol)
        assertEquals("Taschen", symbol.text)
    }

    @Test
    fun `emoji mode of the object is the default`() {
        val before = BindingCaption.emojiMode
        try {
            BindingCaption.emojiMode = true
            assertEquals("🗺️", BindingCaption.forSlot("", "TOGGLEWORLDMAP", null, BindingCombo.of(Binding.KEY_M)).text)
            BindingCaption.emojiMode = false
            assertEquals("map", BindingCaption.forSlot("", "TOGGLEWORLDMAP", null, BindingCombo.of(Binding.KEY_M)).symbol)
            assertNull(BindingCaption.emojiForAction("TOGGLEWORLDMAP"))
            BindingCaption.emojiMode = true
            assertEquals("🗺️", BindingCaption.emojiForAction("TOGGLEWORLDMAP"))
        } finally {
            BindingCaption.emojiMode = before
        }
    }

    @Test
    fun `wow action without emoji falls back to the symbol`() {
        // Action bar slots show their number (no emoji), also in emoji mode.
        val caption = BindingCaption.forSlot("", "ACTIONBUTTON3", null, BindingCombo.of(Binding.KEY_3), 3, emoji = true)
        assertEquals("slot", caption.symbol)
        assertEquals("3", caption.text)
    }

    @Test
    fun `every emoji of the catalog is a real emoji and only slots lack one`() {
        for (action in WowActionCatalog.all) {
            val emoji = action.emoji
            if (action.group == WowActionGroup.ACTION_BAR) {
                assertNull(action.command, emoji)
            } else {
                assertNotNull(action.command, emoji)
                assertTrue(action.command, BindingCaption.containsEmoji(emoji))
            }
        }
        assertTrue(BindingCaption.EMOJI_CHOICES.size in 38..45)
        assertTrue(BindingCaption.EMOJI_CHOICES.all { BindingCaption.containsEmoji(it) })
        assertEquals(BindingCaption.EMOJI_CHOICES.size, BindingCaption.EMOJI_CHOICES.toSet().size)
    }

    @Test
    fun `chat command is shortened without the slash`() {
        assertEquals("dance", BindingCaption.forSlot("", null, "/dance", BindingCombo.none(), 2).text)
        assertEquals("s Hallo", BindingCaption.forSlot("", null, "/s Hallo", BindingCombo.none()).text)
        assertEquals("follow", BindingCaption.forChatCommand("/follow"))
        // Long commands are cut (8 characters).
        assertEquals("a1234567", BindingCaption.forChatCommand("/a123456789"))
    }

    @Test
    fun `chat command of an emote action shows the emote emoji`() {
        val caption = BindingCaption.forSlot("", "EMOTE_DANCE", "/dance", BindingCombo.none(), 5, emoji = true)
        assertEquals("💃", caption.text)
    }

    @Test
    fun `own label beats the automatic text`() {
        val combo = BindingCombo.of(Binding.KEY_SPACE)
        val caption = BindingCaption.forSlot("Sprung", "OPENALLBAGS", "/dance", combo, 1, emoji = true)
        assertEquals("Sprung", caption.text)
        assertFalse(caption.automatic)
        assertNull(caption.symbol)
        // An emoji typed by the player is a label like any other.
        assertEquals("🔥", BindingCaption.forSlot("🔥", null, null, combo, 1).text)
    }

    @Test
    fun `old profile with label 2 and key tab shows tab`() {
        val caption = BindingCaption.forSlot("2", null, null, BindingCombo.of(Binding.KEY_TAB), 2)
        assertEquals("Tab", caption.text)
        assertTrue(caption.automatic)
    }

    @Test
    fun `label 2 with key 2 stays 2 and a different number is an own label`() {
        assertEquals("2", BindingCaption.forSlot("2", null, null, BindingCombo.of(Binding.KEY_2), 2).text)
        assertTrue(BindingCaption.isOwnLabel("2", null, null, BindingCombo.of(Binding.KEY_2), 2))
        // "3" on segment 2 was typed by the player (not the creation default of that segment).
        assertEquals("3", BindingCaption.forSlot("3", null, null, BindingCombo.of(Binding.KEY_TAB), 2).text)
        // Without ring (wheel field) a label "2" is always own.
        assertTrue(BindingCaption.isOwnLabel("2", null, null, BindingCombo.of(Binding.KEY_TAB), 0))
        // Old default number on a segment that holds a WoW action: the symbol/emoji wins.
        assertEquals(
            "🎒",
            BindingCaption.forSlot("4", "OPENALLBAGS", null, BindingCombo.of(Binding.KEY_B), 4, emoji = true).text,
        )
    }

    @Test
    fun `empty and blank labels are not own`() {
        assertFalse(BindingCaption.isOwnLabel("", null, null, BindingCombo.none(), 1))
        assertFalse(BindingCaption.isOwnLabel("   ", null, null, BindingCombo.none(), 1))
        assertFalse(BindingCaption.isOwnLabel(null, null, null, BindingCombo.none(), 1))
    }

    @Test
    fun `cutting never splits an emoji`() {
        val text = "ab🔥🔥🔥"
        assertEquals("ab🔥", BindingCaption.fit(text, 3))
        assertEquals(text, BindingCaption.fit(text, 8))
    }

    @Test
    fun `emoji detection ignores arrows and letters`() {
        assertFalse(BindingCaption.containsEmoji("⇧"))
        assertFalse(BindingCaption.containsEmoji("←"))
        assertFalse(BindingCaption.containsEmoji("Space"))
        assertTrue(BindingCaption.containsEmoji("🎒"))
        assertTrue(BindingCaption.containsEmoji("⚔️"))
        assertTrue(BindingCaption.containsEmoji("⬆️"))
    }

    @Test
    fun `every binding has a short name or is none`() {
        for (binding in Binding.values()) {
            val name = BindingCaption.forBinding(binding)
            if (binding == Binding.NONE) assertEquals("", name) else assertTrue(binding.name, name.isNotEmpty())
        }
    }
}
