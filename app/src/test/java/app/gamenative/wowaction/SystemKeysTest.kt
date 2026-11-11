package app.gamenative.wowaction

import app.gamenative.cooldown.CooldownStrip
import app.gamenative.ui.screen.wow.context.ContextKeys
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keine Taste, die die App ans Spiel schickt, darf unter Windows/Wine das Fenster schließen oder wechseln. Alt+F4 beendet das Spiel auch mit
 * Umschalt oder Strg dazu ("Einladen" lag auf Alt+Umschalt+F4 und beendete das Spiel). Geprüft werden alle Tabellen der App (Kontext-Tasten,
 * Menü-/Fensteraktionen des Katalogs, Ersatztasten, Zaubertasten, Neuladen), alle Tastenstrings im Quelltext der App und alle Strings der
 * Addon-Dateien. Die Lua-Tabellen zur Laufzeit prüft tools/cooldown_strip_check.py mit derselben Regel.
 */
class SystemKeysTest {
    /** Grund, warum [key] (WoW-Schreibweise, z. B. ALT-CTRL-F4) nie gesendet werden darf; null = in Ordnung. */
    private fun problem(key: String): String? {
        val parts = key.uppercase().split("-")
        val name = parts.last().ifEmpty { "-" }
        val mods = parts.dropLast(1).toSet()
        return when {
            "ALT" in mods && name in setOf("F4", "TAB", "ESC", "ESCAPE", "SPACE", "ENTER", "RETURN") -> "Alt+$name schließt oder wechselt das Fenster"
            mods == setOf("CTRL") && name in setOf("ESC", "ESCAPE") -> "Strg+Esc öffnet das Startmenü"
            mods.containsAll(setOf("CTRL", "SHIFT")) && name in setOf("ESC", "ESCAPE") -> "Strg+Umschalt+Esc öffnet den Task-Manager"
            mods.containsAll(setOf("ALT", "CTRL")) && name in setOf("DELETE", "DEL") -> "Strg+Alt+Entf"
            else -> null
        }
    }

    private fun assertSafe(keys: Collection<String>, what: String) {
        val bad = keys.mapNotNull { key -> problem(key)?.let { "$key ($it)" } }
        assertTrue("$what enthält Tasten, die das Fenster schließen oder wechseln: $bad", bad.isEmpty())
    }

    private fun file(path: String): File = File(path).takeIf { it.exists() } ?: File("app/$path")

    private val literal = Regex("[\"']((?:(?:ALT|CTRL|SHIFT)-)+[A-Z0-9]+)[\"']")

    @Test
    fun theRuleItselfRecognisesTheDangerousKeys() {
        for (key in listOf("ALT-F4", "ALT-SHIFT-F4", "ALT-CTRL-F4", "CTRL-ALT-SHIFT-F4", "ALT-TAB", "SHIFT-ALT-TAB", "ALT-ESCAPE", "ALT-SPACE", "ALT-ENTER", "CTRL-ESCAPE", "CTRL-SHIFT-ESCAPE", "ALT-CTRL-DELETE")) {
            assertTrue(key, problem(key) != null)
        }
        for (key in listOf("CTRL-SHIFT-F4", "ALT-CTRL-INSERT", "ALT-CTRL-HOME", "ALT-SHIFT-F5", "CTRL-F4", "ALT-Z", "TAB", "ESCAPE", "ENTER", "SPACE", "ALT-SHIFT-DELETE")) {
            assertNull(key, problem(key))
        }
    }

    @Test
    fun noTableOfTheAppHasAKeyThatClosesOrSwitchesTheWindow() {
        assertSafe(ContextKeys.ALL, "ContextKeys.ALL")
        assertSafe(ContextKeys.SPELL_KEYS, "ContextKeys.SPELL_KEYS")
        assertSafe(ContextKeys.ACTION_KEYS, "ContextKeys.ACTION_KEYS")
        assertSafe(ReplacementKeys.KEYS.values, "ReplacementKeys.KEYS")
        assertSafe(listOf(ReloadGuard.RELOAD_KEY, CooldownStrip.RELOAD_KEY), "Neuladen-Taste")
        val catalog = WowActionCatalog.all.mapNotNull { it.defaultKey }
        assertTrue(catalog.size > 100)
        assertSafe(catalog, "WowActionCatalog (Menü-, Fenster- und Zaubertasten)")
        // what the combinations become when they are sent: Alt held together with F4 (any further key) must not occur
        val altF4 = com.winlator.inputcontrols.Binding.KEY_F4
        val sendable = (catalog + ReplacementKeys.KEYS.values + ContextKeys.ALL).mapNotNull { WowKeys.toCombo(it) }
        assertTrue(sendable.none { combo ->
            val b = combo.bindings
            altF4 in b && (com.winlator.inputcontrols.Binding.KEY_ALT_L in b || com.winlator.inputcontrols.Binding.KEY_ALT_R in b)
        })
    }

    @Test
    fun noKeyStringInTheSourceOfTheAppOrTheAddonCloses() {
        var count = 0
        for (dir in listOf("src/main/java/app/gamenative", "src/main/assets/wow/addon/TouchForever")) {
            val root = file(dir)
            assertTrue(root.path, root.isDirectory)
            root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "lua", "xml") }.forEach { f ->
                // the only exception: the list of former keys that are taken away again (UI.OLD_EXTRA_KEYS in TouchForeverUI.lua)
                val text = f.readLines(Charsets.UTF_8).filterNot { "OLD_EXTRA_KEYS" in it }.joinToString("\n")
                val found = literal.findAll(text).map { it.groupValues[1] }.toList()
                count += found.size
                assertSafe(found, f.name)
            }
        }
        assertTrue("zu wenige Tastenstrings gefunden: $count", count > 60)
    }

    @Test
    fun theInviteAndRaidKeysAreTheFreeReplacementsAndOnlyUsedOnce() {
        assertEquals("ALT-CTRL-INSERT", ContextKeys.INVITE)
        assertEquals("ALT-CTRL-HOME", WowActionCatalog.find("TOGGLERAIDTAB")!!.defaultKey)
        val all = WowActionCatalog.all.mapNotNull { it.defaultKey } + ContextKeys.ALL + ReplacementKeys.KEYS.values
        for (key in listOf("ALT-CTRL-INSERT", "ALT-CTRL-HOME")) {
            assertEquals(key, 1, all.count { it == key })
            val combo = WowKeys.toCombo(key)!!
            assertEquals(key, 3, combo.bindings.size)
        }
    }
}
