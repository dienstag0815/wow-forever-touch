package app.gamenative.wowaction

import app.gamenative.TestTexts
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.WowActionBindings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WowBindingsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @get:Rule
    val texts = TestTexts.rule("de")

    // Invented sample in the format of bindings-cache.wtf (lines "bind <KEY> <COMMAND>"), no real player data.
    private val sample = """
        bind W MOVEFORWARD
        bind N TOGGLEWORLDMAP
        bind CTRL-N TOGGLEWORLDMAP
        bind K TOGGLETALENTS
        bind ALT-SHIFT-F5 TOGGLESPELLBOOK
        bind 1 ACTIONBUTTON1
        bind SHIFT-1 MULTIACTIONBAR1BUTTON1
        bind BUTTON4 TOGGLEQUESTLOG
        bind NUMPAD5 TOGGLEAUTORUN
    """.trimIndent()

    @Test
    fun `parses bind lines and ignores everything else`() {
        val text = "﻿-- Kommentar\n\nheader 1\nbind m  TOGGLEWORLDMAP\nbind P\nbind  X   SITORSTAND  \nbogus\n"
        val bindings = WowBindings.parse(text)
        assertEquals(listOf("M"), bindings.keysFor("TOGGLEWORLDMAP"))
        assertEquals(listOf("X"), bindings.keysFor("SITORSTAND"))
        assertEquals(2, bindings.size)
    }

    @Test
    fun `a bind line without command frees the key and a later line replaces the command`() {
        val bindings = WowBindings.parse("bind M TOGGLEWORLDMAP\nbind M\nbind N TOGGLETALENTS\nbind N TOGGLESPELLBOOK\n")
        assertTrue(bindings.keysFor("TOGGLEWORLDMAP").isEmpty())
        assertTrue(bindings.keysFor("TOGGLETALENTS").isEmpty())
        assertEquals(listOf("N"), bindings.keysFor("TOGGLESPELLBOOK"))
    }

    @Test
    fun `key names translate to bindings`() {
        assertEquals(BindingCombo.of(Binding.KEY_M), WowKeys.toCombo("M"))
        assertEquals(BindingCombo.of(Binding.KEY_0), WowKeys.toCombo("0"))
        assertEquals(BindingCombo.of(Binding.KEY_F12), WowKeys.toCombo("F12"))
        assertEquals(BindingCombo.of(Binding.KEY_ESC), WowKeys.toCombo("ESCAPE"))
        assertEquals(BindingCombo.of(Binding.KEY_PG_UP), WowKeys.toCombo("PAGEUP"))
        assertEquals(BindingCombo.of(Binding.KEY_KP_5), WowKeys.toCombo("NUMPAD5"))
        assertEquals(BindingCombo.of(Binding.KEY_KP_ADD), WowKeys.toCombo("NUMPADPLUS"))
        assertEquals(BindingCombo.of(Binding.KEY_NUM_LOCK), WowKeys.toCombo("NUMLOCK"))
        assertEquals(BindingCombo.of(Binding.KEY_MINUS), WowKeys.toCombo("-"))
        assertEquals(BindingCombo.of(Binding.KEY_GRAVE), WowKeys.toCombo("`"))
        assertEquals(BindingCombo.of(Binding.MOUSE_RIGHT_BUTTON), WowKeys.toCombo("BUTTON2"))
        assertEquals(BindingCombo.of(Binding.MOUSE_MIDDLE_BUTTON), WowKeys.toCombo("BUTTON3"))
        assertEquals(BindingCombo.of(Binding.MOUSE_SCROLL_UP), WowKeys.toCombo("MOUSEWHEELUP"))
        assertEquals(BindingCombo.of(Binding.KEY_TAB), WowKeys.toCombo("tab"))
    }

    @Test
    fun `modifiers become a combination with the modifier first`() {
        val shiftTab = WowKeys.toCombo("SHIFT-TAB")!!
        assertEquals(listOf(Binding.KEY_SHIFT_L, Binding.KEY_TAB), shiftTab.bindings)
        val three = WowKeys.toCombo("ALT-SHIFT-F5")!!
        assertEquals(listOf(Binding.KEY_ALT_L, Binding.KEY_SHIFT_L, Binding.KEY_F5), three.bindings)
        // The key "-" behind a modifier.
        assertEquals(listOf(Binding.KEY_CTRL_L, Binding.KEY_MINUS), WowKeys.toCombo("CTRL--")!!.bindings)
        assertEquals(2, WowKeys.modifierCount("CTRL-SHIFT-1"))
        assertEquals(0, WowKeys.modifierCount("NUMPADMINUS"))
    }

    @Test
    fun `unsupported keys give null`() {
        assertNull(WowKeys.toCombo(""))
        assertNull(WowKeys.toCombo("BUTTON4"))
        assertNull(WowKeys.toCombo("LSHIFT"))
        assertNull(WowKeys.toCombo("CTRL-"))
        // Four keys do not fit into a combination (maximum 3).
        assertNull(WowKeys.toCombo("ALT-CTRL-SHIFT-1"))
    }

    @Test
    fun `resolver prefers the key from the file and falls back to the standard key`() {
        val bindings = WowBindings.parse(sample)
        val map = WowActionCatalog.find("TOGGLEWORLDMAP")!!
        val resolvedMap = WowActionResolver.resolve(map, bindings)!!
        // N and CTRL-N are bound: the one without modifier wins.
        assertEquals("N", resolvedMap.wowKey)
        assertEquals(BindingCombo.of(Binding.KEY_N), resolvedMap.combo)
        assertEquals(WowKeySource.WOW_FILE, resolvedMap.source)

        val talents = WowActionResolver.resolve(WowActionCatalog.find("TOGGLETALENTS")!!, bindings)!!
        assertEquals("K", talents.wowKey)

        // Bound to a key that cannot be sent (BUTTON4): standard key L is used.
        val quests = WowActionResolver.resolve(WowActionCatalog.find("TOGGLEQUESTLOG")!!, bindings)!!
        assertEquals("L", quests.wowKey)
        assertEquals(WowKeySource.DEFAULT, quests.source)

        // Not in the file: standard key; without a standard key: null.
        val spellbook = WowActionCatalog.find("TOGGLESPELLBOOK")!!
        assertEquals("P", WowActionResolver.resolve(spellbook.copy(defaultKey = "P"), WowBindings.EMPTY)!!.wowKey)
        assertNull(WowActionResolver.resolve(WowActionCatalog.find("INTERACTTARGET")!!, bindings))
        assertEquals("nicht belegt", WowActionResolver.keyText(null))
        assertEquals("1", WowActionResolver.keyText(WowActionResolver.resolve(WowActionCatalog.find("ACTIONBUTTON1")!!, null)))
    }

    @Test
    fun `resolver takes keys for action bar 2 from the file`() {
        val bindings = WowBindings.parse(sample)
        val slot = WowActionResolver.resolve(WowActionCatalog.find("MULTIACTIONBAR1BUTTON1")!!, bindings)!!
        assertEquals("SHIFT-1", slot.wowKey)
        assertEquals(listOf(Binding.KEY_SHIFT_L, Binding.KEY_1), slot.combo.bindings)
        val spell = WowActionResolver.resolve(WowActionCatalog.find("TOGGLESPELLBOOK")!!, bindings)!!
        assertEquals(3, spell.combo.size())
    }

    @Test
    fun `resolveAll without file uses all standard keys`() {
        val all = WowActionResolver.resolveAll(null)
        assertEquals(BindingCombo.of(Binding.KEY_M), all.getValue("TOGGLEWORLDMAP").combo)
        assertTrue("INTERACTTARGET" !in all)
        assertEquals(WowActionCatalog.all.count { it.defaultKey != null }, all.size)
    }

    @Test
    fun `overlay replaces commands of the base file`() {
        val base = WowBindings.parse("bind M TOGGLEWORLDMAP\nbind P TOGGLESPELLBOOK\n")
        val overlay = WowBindings.parse("bind N TOGGLEWORLDMAP\nbind P TOGGLETALENTS\n")
        val merged = base.overlayedWith(overlay)
        assertEquals(listOf("N"), merged.keysFor("TOGGLEWORLDMAP"))
        assertEquals(listOf("P"), merged.keysFor("TOGGLETALENTS"))
        assertTrue(merged.keysFor("TOGGLESPELLBOOK").isEmpty())
    }

    private fun write(path: String, text: String, modified: Long): File {
        val file = File(temp.root, path)
        file.parentFile!!.mkdirs()
        file.writeText(text)
        file.setLastModified(modified)
        return file
    }

    @Test
    fun `loader reads the account file`() {
        write("_forever_/WTF/Account/123#1/bindings-cache.wtf", "bind N TOGGLEWORLDMAP\n", 1_000_000L)
        write("_forever_/WTF/Account/123#1/SavedVariables/bindings-cache.wtf", "bind Q TOGGLEWORLDMAP\n", 2_000_000L)
        val bindings = WowBindingsLoader.load(temp.root)
        assertNotNull(bindings)
        assertEquals(listOf("N"), bindings!!.keysFor("TOGGLEWORLDMAP"))
    }

    @Test
    fun `loader overlays a newer character file but ignores an older one`() {
        write("_forever_/WTF/Account/ACC/bindings-cache.wtf", "bind M TOGGLEWORLDMAP\nbind P TOGGLESPELLBOOK\n", 2_000_000L)
        write("_forever_/WTF/Account/ACC/Realm/Char/bindings-cache.wtf", "bind J TOGGLEWORLDMAP\n", 3_000_000L)
        var bindings = WowBindingsLoader.load(temp.root)!!
        assertEquals(listOf("J"), bindings.keysFor("TOGGLEWORLDMAP"))
        assertEquals(listOf("P"), bindings.keysFor("TOGGLESPELLBOOK"))

        File(temp.root, "_forever_/WTF/Account/ACC/Realm/Char/bindings-cache.wtf").setLastModified(1_000_000L)
        bindings = WowBindingsLoader.load(temp.root)!!
        assertEquals(listOf("M"), bindings.keysFor("TOGGLEWORLDMAP"))
    }

    @Test
    fun `loader picks the account with the newest file and returns null without files`() {
        assertNull(WowBindingsLoader.load(temp.root))
        write("_forever_/WTF/Account/OLD/bindings-cache.wtf", "bind M TOGGLEWORLDMAP\n", 1_000_000L)
        write("_forever_/WTF/Account/NEW/bindings-cache.wtf", "bind N TOGGLEWORLDMAP\n", 2_000_000L)
        assertEquals(listOf("N"), WowBindingsLoader.load(temp.root)!!.keysFor("TOGGLEWORLDMAP"))
    }

    @Test
    fun `refresh publishes the file keys to the profile code`() {
        write("_forever_/WTF/Account/ACC/bindings-cache.wtf", "bind N TOGGLEWORLDMAP\n", 2_000_000L)
        try {
            val resolved = WowActionSource.refresh(temp.root)
            assertEquals("N", resolved.getValue("TOGGLEWORLDMAP").wowKey)
            val stored = BindingCombo.of(Binding.KEY_M)
            assertEquals(BindingCombo.of(Binding.KEY_N), WowActionBindings.resolve("TOGGLEWORLDMAP", stored))
        } finally {
            WowActionBindings.setResolved(null)
        }
    }

    @Test
    fun `refresh with a missing folder falls back to the standard keys`() {
        try {
            val resolved = WowActionSource.refresh(File(temp.root, "gibt-es-nicht"))
            assertEquals("M", resolved.getValue("TOGGLEWORLDMAP").wowKey)
            assertEquals(WowKeySource.DEFAULT, resolved.getValue("TOGGLEWORLDMAP").source)
        } finally {
            WowActionBindings.setResolved(null)
        }
    }
}
