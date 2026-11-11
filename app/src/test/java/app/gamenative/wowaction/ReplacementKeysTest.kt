package app.gamenative.wowaction

import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.RadialMenu
import com.winlator.inputcontrols.WowActionBindings
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replacement keys for the action bars 2 to 5 (no key in WoW by default): table, addon side, catalog, fallback of the profile. */
class ReplacementKeysTest {
    @After
    fun tearDown() {
        WowActionBindings.setResolved(null)
    }

    private fun addonFile(name: String): File =
        File("src/main/assets/wow/addon/TouchForever/$name").takeIf { it.isFile }
            ?: File("app/src/main/assets/wow/addon/TouchForever/$name")

    @Test
    fun forty_eight_distinct_keys_that_can_all_be_sent() {
        assertEquals(48, ReplacementKeys.KEYS.size)
        assertEquals(48, ReplacementKeys.KEYS.values.toSet().size)
        for ((command, key) in ReplacementKeys.KEYS) {
            val combo = WowKeys.toCombo(key)
            assertNotNull("$command -> $key", combo)
            assertTrue("$key has at most 3 keys", combo!!.bindings.size in 2..BindingCombo.MAX_BINDINGS)
            assertTrue("$key has a modifier", combo.bindings.first() in listOf(Binding.KEY_ALT_L, Binding.KEY_CTRL_L, Binding.KEY_SHIFT_L))
            assertNotNull("catalog has $command", WowActionCatalog.find(command))
        }
    }

    @Test
    fun noKeyIsALongKnownBindingOrAnythingTouchForeverUsesElsewhere() {
        val replacement = ReplacementKeys.KEYS.values.toSet()
        // every other action of the catalog (WoW standard keys, reload, addon keys, ALT-CTRL-F1..F4)
        val catalogKeys = WowActionCatalog.all.filter { it.command !in ReplacementKeys.KEYS }.mapNotNull { it.defaultKey }.toSet()
        assertTrue("clash with the catalog: ${replacement intersect catalogKeys}", (replacement intersect catalogKeys).isEmpty())
        // the key strings of the addon files and of the context menu (ALT-SHIFT-F1.., ALT-CTRL-F5.., CTRL-SHIFT-F1..)
        val pattern = Regex("\"((?:(?:ALT|CTRL|SHIFT)-)+[A-Z0-9]+)\"")
        val others = HashSet<String>()
        for (name in listOf("TouchForeverUI.lua", "TouchForever.lua", "TouchForeverContext.lua")) {
            pattern.findAll(addonFile(name).readText(Charsets.UTF_8)).forEach { others += it.groupValues[1] }
        }
        val contextKeys = File("src/main/java/app/gamenative/ui/screen/wow/context/ContextKeys.kt").takeIf { it.isFile }
            ?: File("app/src/main/java/app/gamenative/ui/screen/wow/context/ContextKeys.kt")
        pattern.findAll(contextKeys.readText(Charsets.UTF_8)).forEach { others += it.groupValues[1] }
        assertTrue(others.size > 20)
        assertTrue("clash with the addon: ${replacement intersect others}", (replacement intersect others).isEmpty())
        // rules: no ALT-F4, no ALT-CTRL with digits (AltGr), nothing with TAB / SPACE / ENTER under ALT
        assertFalse("ALT-F4" in replacement)
        assertTrue(replacement.none { it.startsWith("ALT-CTRL-") || it.startsWith("CTRL-ALT-") })
        assertTrue(replacement.none { it.startsWith("ALT-") && (it.endsWith("-TAB") || it.endsWith("-SPACE") || it.endsWith("-ENTER")) })
    }

    @Test
    fun theLuaTableIsTheSameEntryByEntry() {
        val lua = addonFile("TouchForeverBindings.lua").readText(Charsets.UTF_8)
        // evaluate the three loops of the file by hand: the rows and the four formulas
        fun row(name: String): List<String> =
            Regex("local $name = \\{([^}]*)\\}").find(lua)!!.groupValues[1].split(",").map { it.trim().trim('"') }
        val numbers = row("NUMBER_ROW")
        val navigation = row("NAVIGATION_ROW")
        assertEquals(12, numbers.size)
        assertEquals(12, navigation.size)
        val formulas = Regex("B\\.KEYS\\[\"MULTIACTIONBAR(\\d)BUTTON\" \\.\\. place\\] = \"([A-Z-]+)\" \\.\\. (NUMBER_ROW|NAVIGATION_ROW)\\[place\\]")
            .findAll(lua).associate { it.groupValues[1].toInt() to (it.groupValues[2] to it.groupValues[3]) }
        assertEquals(setOf(1, 2, 3, 4), formulas.keys)
        val fromLua = LinkedHashMap<String, String>()
        for (bar in 1..4) {
            val (prefix, rowName) = formulas.getValue(bar)
            val keys = if (rowName == "NUMBER_ROW") numbers else navigation
            for (place in 1..12) fromLua["MULTIACTIONBAR${bar}BUTTON$place"] = prefix + keys[place - 1]
        }
        assertEquals(ReplacementKeys.KEYS, fromLua)
        // loaded through the toc, after the files it does not depend on
        val toc = addonFile("TouchForever.toc").readText(Charsets.UTF_8).lines().map { it.trim() }
        assertTrue("TouchForeverBindings.lua" in toc)
        // only set while the player has no key of their own, never in combat
        assertTrue(lua.contains("if not GetBindingKey(command) then"))
        assertTrue(lua.contains("SetOverrideBinding(owner, false, key, command)"))
        assertTrue(lua.contains("InCombatLockdown"))
    }

    @Test
    fun theCatalogTakesThemAsStandardKeysAndTheKeyOfThePlayerGoesFirst() {
        for ((command, key) in ReplacementKeys.KEYS) assertEquals(key, WowActionCatalog.find(command)!!.defaultKey)
        // bars 6 to 8 stay without a key
        assertNull(WowActionCatalog.find("MULTIACTIONBAR5BUTTON1")!!.defaultKey)
        val action = WowActionCatalog.find("MULTIACTIONBAR1BUTTON1")!!
        val standard = WowActionResolver.resolve(action, null)!!
        assertEquals(WowKeySource.DEFAULT, standard.source)
        assertEquals(WowKeys.toCombo("ALT-SHIFT-1"), standard.combo)
        // the player has bound the action in WoW: that key wins
        val own = WowActionResolver.resolve(action, WowBindings.parse("bind SHIFT-F9 MULTIACTIONBAR1BUTTON1"))!!
        assertEquals(WowKeySource.WOW_FILE, own.source)
        assertEquals(WowKeys.toCombo("SHIFT-F9"), own.combo)
        // action bar 1 keeps the number row
        assertEquals("1", WowActionCatalog.find("ACTIONBUTTON1")!!.defaultKey)
    }

    @Test
    fun aFieldWithNoneAndAWowActionGetsTheReplacementKeyEvenBeforeTheKeysAreResolved() {
        val field = RadialMenu.Slot("", BindingCombo.none(), "MULTIACTIONBAR1BUTTON9")
        assertEquals(WowKeys.toCombo("ALT-SHIFT-9"), field.bindingCombo)
        assertTrue(field.isEnabled)
        // the saved JSON carries the whole combination (Alt + Shift + 9), not an empty key
        val saved = field.toJSONObject().toString()
        assertTrue(saved, saved.contains("KEY_ALT_L") && saved.contains("KEY_SHIFT_L") && saved.contains("KEY_9"))
        // a key resolved from the player's WoW wins
        WowActionBindings.setResolved(mapOf("MULTIACTIONBAR1BUTTON9" to WowKeys.toCombo("CTRL-F9")!!))
        assertEquals(WowKeys.toCombo("CTRL-F9"), field.bindingCombo)
        // a stored key stays when nothing is resolved
        WowActionBindings.setResolved(null)
        val stored = RadialMenu.Slot("", BindingCombo.of(Binding.KEY_Q), "MULTIACTIONBAR1BUTTON9")
        assertEquals(BindingCombo.of(Binding.KEY_Q), stored.bindingCombo)
        // an action without replacement key stays without
        val bar6 = RadialMenu.Slot("", BindingCombo.none(), "MULTIACTIONBAR5BUTTON1")
        assertTrue(bar6.bindingCombo.isEmpty)
        assertFalse(bar6.isEnabled)
    }

    @Test
    fun theLeftRingOfTheBackupGetsKeysOnAllEightFields() {
        // the left ring of the standard layout of the device: MULTIACTIONBAR1BUTTON9, 11, 12, 1, 2, 3, 4, 5, all with binding NONE
        for (place in listOf(9, 11, 12, 1, 2, 3, 4, 5)) {
            val field = RadialMenu.Slot("", BindingCombo.none(), "MULTIACTIONBAR1BUTTON$place")
            assertTrue("place $place", field.isEnabled)
            assertEquals(ReplacementKeys.comboFor("MULTIACTIONBAR1BUTTON$place"), field.bindingCombo)
        }
    }
}
