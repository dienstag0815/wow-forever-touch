package app.gamenative.ui.screen.wow.context

import app.gamenative.TestTexts
import app.gamenative.cooldown.CooldownStrip
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowKeys
import app.gamenative.ui.screen.wow.AddonOptionsKey
import app.gamenative.ui.screen.wow.klick.WheelButton
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ContextDecisionTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private val mob = UnitInfo(exists = true, relation = UnitRelation.HOSTILE, source = UnitSource.MOUSEOVER)
    private val onMob = ContextData(ContextData.KEYS or ContextData.SPELLS, mob)
    private val bagItem = ContextData(ContextData.KEYS, UnitInfo(), FocusInfo(kind = FocusKind.BAG, filled = true))

    // touch-down at t = 1000; the strip is read at 1200 (after the settling time) and asked at 1350
    private fun opens(
        data: ContextData?,
        mode: ContextMode = ContextMode.AUTO,
        received: Long = 1_200L,
        down: Long = 1_000L,
        now: Long = 1_350L,
    ) = ContextDecision.opensMenu(mode, data, received, down, now)

    // ---- menu or hold --------------------------------------------------------------------------------------

    @Test
    fun aFreshUnitUnderThePointerOpensTheMenu() {
        assertTrue(opens(onMob))
        assertTrue(opens(bagItem))
    }

    @Test
    fun theSettingOffNeverOpens() {
        assertFalse(opens(onMob, mode = ContextMode.OFF))
        assertFalse(opens(bagItem, mode = ContextMode.OFF))
    }

    @Test
    fun noDataMeansTheOldHold() {
        assertFalse(opens(null))
    }

    @Test
    fun aValueFromBeforeTheTouchOrTooCloseToItIsNotTrusted() {
        // read before the finger came down: it may still show the unit of before
        assertFalse(opens(onMob, received = 900L))
        assertFalse(opens(onMob, received = 1_000L + ContextDecision.SETTLE_MS - 1))
        assertTrue(opens(onMob, received = 1_000L + ContextDecision.SETTLE_MS))
    }

    @Test
    fun anOldValueMeansTheStripStoppedArriving() {
        assertTrue(opens(onMob, received = 1_200L, now = 1_200L + ContextDecision.MAX_AGE_MS))
        assertFalse(opens(onMob, received = 1_200L, now = 1_200L + ContextDecision.MAX_AGE_MS + 1))
    }

    @Test
    fun noUnitOrOnlyTheTargetDoNotOpen() {
        assertFalse(opens(ContextData(0, UnitInfo())))
        // the target as a fallback (the pointer is on the ground): holding there must not open the target's menu
        assertFalse(opens(ContextData(0, mob.copy(source = UnitSource.TARGET))))
        assertFalse(opens(ContextData(0, mob.copy(source = UnitSource.NONE))))
    }

    @Test
    fun anEmptyItemSlotDoesNotOpen() {
        assertFalse(opens(ContextData(0, UnitInfo(), FocusInfo(kind = FocusKind.BAG, filled = false))))
    }

    @Test
    fun anItemMenuOpensEvenWhileAnItemHangsOnTheCursor() {
        assertTrue(opens(ContextData(0, UnitInfo(), FocusInfo(kind = FocusKind.BAG, filled = true, cursorHasItem = true))))
    }

    // ---- a tap on an item looks, it does not pick up --------------------------------------------------------------

    private fun taps(
        data: ContextData?,
        enabled: Boolean = true,
        received: Long = 1_200L,
        down: Long = 1_000L,
        now: Long = 1_350L,
    ) = ContextDecision.tapIsViewOnly(enabled, data, received, down, now)

    @Test
    fun aTapOnAnItemOnlyLooksWhenTheDataIsFresh() {
        assertTrue(taps(bagItem))
        assertFalse(taps(bagItem, enabled = false))
        assertFalse(taps(null))
        assertFalse(taps(bagItem, received = 1_000L + ContextDecision.SETTLE_MS - 1))
        assertFalse(taps(bagItem, received = 1_200L, now = 1_200L + ContextDecision.MAX_AGE_MS + 1))
    }

    @Test
    fun aTapStillClicksOnTheGroundOnUnitsOnEmptySlotsAndWithAnItemOnTheCursor() {
        assertFalse(taps(onMob))
        assertFalse(taps(ContextData(0, UnitInfo(), FocusInfo(kind = FocusKind.BAG, filled = false))))
        // the cursor carries an item: the tap puts it down (a click), that is the point of picking it up
        assertFalse(taps(ContextData(0, UnitInfo(), FocusInfo(kind = FocusKind.BAG, filled = true, cursorHasItem = true))))
        assertTrue(taps(ContextData(0, UnitInfo(), FocusInfo(kind = FocusKind.EQUIPMENT, filled = true))))
        assertTrue(taps(ContextData(0, UnitInfo(), FocusInfo(kind = FocusKind.BANK, filled = true))))
    }

    // ---- another player: the mini menu; myself: the self menu -----------------------------------------------------

    private val player = UnitInfo(exists = true, isPlayer = true, relation = UnitRelation.FRIEND, source = UnitSource.MOUSEOVER)
    private val me = UnitInfo(exists = true, isPlayer = true, isSelf = true, relation = UnitRelation.FRIEND, source = UnitSource.MOUSEOVER)

    @Test
    fun anotherPlayerOpensTheMiniMenuWithMoreForTheGamesOwnMenu() {
        val data = ContextData(ContextData.KEYS or ContextData.SPELLS, player)
        assertTrue(opens(data))                                            // the hold counts as a menu ...
        assertTrue(ContextMenus.usesPlayerMenu(data, ContextMode.AUTO))    // ... the mini menu for players
        assertTrue(ContextMenus.usesPlayerMenu(data, ContextMode.BAR))
        val menu = ContextMenus.build(data, ContextMode.AUTO, { null }) { null }
        assertTrue(menu.isNotEmpty())
        // "More ..." is the game's own player menu: the addon's popup key, as in fold8.14
        val more = menu.first { it.id == "more" }
        assertEquals("Mehr ...", more.label)
        assertEquals(EntryAction.KEY, more.action)
        assertEquals(ContextKeys.POPUP, more.key)
        assertEquals(listOf(ContextKeys.POPUP), ContextPlan.stepsFor(more, cursorHasItem = false).map { it.key })
        // a neutral and a dead player as well (the game's menu has whisper, inspect ... for both)
        assertTrue(ContextMenus.usesPlayerMenu(ContextData(0, player.copy(relation = UnitRelation.NEUTRAL)), ContextMode.AUTO))
        assertTrue(ContextMenus.usesPlayerMenu(ContextData(0, player.copy(dead = true)), ContextMode.AUTO))
    }

    @Test
    fun theMiniMenuIsNotUsedForEnemiesNpcsItemsMyselfOrWhenOff() {
        // a living attackable player: attacks (our segments)
        assertFalse(ContextMenus.usesPlayerMenu(ContextData(0, player.copy(canAttack = true)), ContextMode.AUTO))
        assertFalse(ContextMenus.usesPlayerMenu(ContextData(0, player.copy(relation = UnitRelation.HOSTILE)), ContextMode.AUTO))
        // an NPC
        assertFalse(ContextMenus.usesPlayerMenu(ContextData(0, player.copy(isPlayer = false)), ContextMode.AUTO))
        assertFalse(ContextMenus.usesPlayerMenu(onMob, ContextMode.AUTO))
        // myself, nothing, setting off
        assertFalse(ContextMenus.usesPlayerMenu(ContextData(0, player.copy(isSelf = true)), ContextMode.AUTO))
        assertFalse(ContextMenus.usesPlayerMenu(ContextData(0, UnitInfo()), ContextMode.AUTO))
        assertFalse(ContextMenus.usesPlayerMenu(ContextData(0, player), ContextMode.OFF))
        // an item slot under the pointer goes first
        assertFalse(ContextMenus.usesPlayerMenu(ContextData(0, player, FocusInfo(kind = FocusKind.BAG, filled = true)), ContextMode.AUTO))
    }

    @Test
    fun theOwnFigureUnderTheFingerOpensTheSelfMenu() {
        val data = ContextData(ContextData.KEYS, me)
        assertTrue(opens(data))                                            // myself under the pointer is a menu now
        assertTrue(ContextMenus.usesSelfMenu(data, ContextMode.AUTO))
        assertTrue(ContextMenus.usesSelfMenu(data, ContextMode.BAR))
        assertFalse(ContextMenus.usesSelfMenu(data, ContextMode.OFF))
        assertFalse(opens(data, mode = ContextMode.OFF))
        assertFalse(ContextMenus.usesSelfMenu(ContextData(0, player), ContextMode.AUTO))
        assertFalse(ContextMenus.usesSelfMenu(ContextData(0, UnitInfo()), ContextMode.AUTO))
        // an item slot under the pointer goes first
        assertFalse(ContextMenus.usesSelfMenu(ContextData(0, me, FocusInfo(kind = FocusKind.BAG, filled = true)), ContextMode.AUTO))
        // the fallback to the target (the finger is on the ground, the target is myself) never opens it
        assertFalse(opens(ContextData(ContextData.KEYS, me.copy(source = UnitSource.TARGET))))
    }

    @Test
    fun theKeyForTheGamesMenuIsAnActionKey() {
        assertTrue(ContextKeys.POPUP in ContextKeys.ACTION_KEYS)
        assertEquals("ALT-SHIFT-F2", ContextKeys.POPUP)
        val steps = ContextPlan.stepsFor(entry("popup", EntryAction.KEY, key = ContextKeys.POPUP), cursorHasItem = false)
        assertEquals(listOf(ContextKeys.POPUP), steps.map { it.key })
    }

    // ---- the order of the steps ---------------------------------------------------------------------------------

    private fun entry(id: String, action: EntryAction, clears: Boolean = false, key: String? = null, slot: Int = 0) =
        ContextEntry(id, id, action, key = key, barSlot = slot, clearsCursor = clears)

    @Test
    fun anItemOnTheCursorIsPutBackBeforeTheRightClick() {
        val steps = ContextPlan.stepsFor(entry("use", EntryAction.RIGHT_CLICK, clears = true), cursorHasItem = true)
        assertEquals(2, steps.size)
        assertEquals(PlanKind.KEY, steps[0].kind)
        assertEquals(ContextKeys.CLEAR_CURSOR, steps[0].key)
        assertTrue("the addon needs time for ClearCursor", steps[0].waitAfterMs >= 150L)
        assertEquals(PlanKind.CLICK, steps[1].kind)
        assertEquals(WheelButton.RIGHT, steps[1].button)
        assertFalse(steps[1].shift)
    }

    @Test
    fun withAnEmptyCursorTheRightClickGoesStraightOut() {
        val steps = ContextPlan.stepsFor(entry("use", EntryAction.RIGHT_CLICK, clears = true), cursorHasItem = false)
        assertEquals(1, steps.size)
        assertEquals(PlanKind.CLICK, steps[0].kind)
        assertEquals(WheelButton.RIGHT, steps[0].button)
    }

    @Test
    fun movingNeverClearsTheCursor() {
        val steps = ContextPlan.stepsFor(entry("move", EntryAction.LEFT_CLICK), cursorHasItem = true)
        assertEquals(1, steps.size)
        assertEquals(WheelButton.LEFT, steps[0].button)
    }

    @Test
    fun splittingIsAShiftClickAfterClearing() {
        val steps = ContextPlan.stepsFor(entry("split", EntryAction.SHIFT_LEFT_CLICK, clears = true), cursorHasItem = true)
        assertEquals(listOf(PlanKind.KEY, PlanKind.CLICK), steps.map { it.kind })
        assertTrue(steps[1].shift)
        assertEquals(WheelButton.LEFT, steps[1].button)
    }

    @Test
    fun keysAndBarSlotsAreOneStep() {
        val key = ContextPlan.stepsFor(entry("attack", EntryAction.KEY, key = ContextKeys.ATTACK), cursorHasItem = false)
        assertEquals(listOf(PlanKind.KEY), key.map { it.kind })
        assertEquals(ContextKeys.ATTACK, key[0].key)
        val bar = ContextPlan.stepsFor(entry("bar:4", EntryAction.BAR_SLOT, slot = 4), cursorHasItem = false)
        assertEquals(listOf(PlanKind.BAR_KEY), bar.map { it.kind })
        assertEquals(4, bar[0].barSlot)
        // a key entry without a key does nothing
        assertTrue(ContextPlan.stepsFor(entry("x", EntryAction.KEY), cursorHasItem = false).isEmpty())
    }

    // ---- nothing is thrown away without the question ----------------------------------------------------------------

    @Test
    fun throwingAwayOnlySendsTheAskKeyNeverAClick() {
        val discard = ContextMenus.itemMenu(FocusInfo(FocusKind.BAG, filled = true), keys = true).first { it.id == "discard" }
        for (cursor in listOf(false, true)) {
            val steps = ContextPlan.stepsFor(discard, cursorHasItem = cursor)
            assertTrue("no click in any plan of the discard entry", steps.none { it.kind == PlanKind.CLICK })
            assertEquals(ContextKeys.DISCARD, steps.last().key)
            assertEquals(1, steps.count { it.key == ContextKeys.DISCARD })
        }
        // there is no entry anywhere in the item menu that deletes by itself
        for (entry in ContextMenus.itemMenu(FocusInfo(FocusKind.BAG, filled = true, equippable = true, stacked = true), keys = true)) {
            val steps = ContextPlan.stepsFor(entry, cursorHasItem = true)
            if (entry.id != "discard") assertTrue(entry.id, steps.none { it.key == ContextKeys.DISCARD })
        }
    }

    @Test
    fun theAddonDeletesOnlyAfterTheYesAnswer() {
        val lua = addonFile("TouchForeverContext.lua") ?: return
        val start = lua.indexOf("function C.discardAccept")
        val end = lua.indexOf("function C.discardCancel")
        assertTrue(start in 0 until end)
        var at = lua.indexOf("DeleteCursorItem")
        var count = 0
        while (at >= 0) {
            count++
            assertTrue("DeleteCursorItem outside of discardAccept at $at", at in start until end)
            at = lua.indexOf("DeleteCursorItem", at + 1)
        }
        assertTrue(count >= 1)
        // discardAccept is only reachable from the Yes button of the question
        assertEquals(1, Regex("OnAccept = function\\(\\) C\\.discardAccept\\(\\) end").findAll(lua).count())
        assertEquals("the definition and the Yes button, nothing else", 2, Regex("discardAccept\\(").findAll(lua).count())
        // and the answer is checked against the item that was asked about
        assertTrue(lua.substring(start, end).contains("link == wanted"))
    }

    // ---- the keys ---------------------------------------------------------------------------------------------

    @Test
    fun theKeysAreFreeAndSendable() {
        assertEquals(8 + 12, ContextKeys.ALL.size)
        assertEquals("no key twice", ContextKeys.ALL.size, ContextKeys.ALL.toSet().size)
        val taken = WowActionCatalog.all.mapNotNull { it.defaultKey }.toSet() + setOf(CooldownStrip.RELOAD_KEY)
        for (key in ContextKeys.ALL) {
            assertFalse("$key is used by the catalog", key in taken)
            val combo = WowKeys.toCombo(key)
            assertNotNull(key, combo)
            assertTrue("$key can be sent", AddonOptionsKey.keyCodes(combo).size == 3)
        }
        assertNull(ContextKeys.spellKey(8))
        assertEquals("ALT-CTRL-F5", ContextKeys.spellKey(0))
    }

    @Test
    fun theLuaFileHasTheSameKeys() {
        val lua = addonFile("TouchForeverContext.lua") ?: return
        assertTrue(lua.contains("C.SPELL_KEYS = { " + ContextKeys.SPELL_KEYS.joinToString(", ") { "\"$it\"" } + " }"))
        val pairs = linkedMapOf(
            "attack" to ContextKeys.ATTACK, "popup" to ContextKeys.POPUP,
            "whisper" to ContextKeys.WHISPER, "invite" to ContextKeys.INVITE, "follow" to ContextKeys.FOLLOW,
            "trade" to ContextKeys.TRADE, "inspect" to ContextKeys.INSPECT, "friend" to ContextKeys.FRIEND,
            "clearcursor" to ContextKeys.CLEAR_CURSOR, "link" to ContextKeys.LINK_ITEM, "compare" to ContextKeys.COMPARE, "discard" to ContextKeys.DISCARD,
        )
        for ((action, key) in pairs) assertTrue("$action = $key", lua.contains("$action = \"$key\""))
        assertTrue(lua.contains("C.ACTIONS = { " + pairs.keys.joinToString(", ") { "\"$it\"" } + " }"))
        assertTrue("follow is the secure macro /follow", lua.contains("follow = \"/follow\""))
        assertEquals(ContextKeys.ACTION_KEYS, pairs.values.toList())
        assertTrue(lua.contains("C.NIBBLES = ${CooldownStrip.CONTEXT_NIBBLES}"))
        assertTrue(lua.contains("C.VERSION = ${CooldownStrip.CONTEXT_VERSION}"))
        assertTrue(lua.contains("C.SPELLS = ${CooldownStrip.CONTEXT_SPELLS}"))
        assertTrue(lua.contains("C.ENTRIES = ${CooldownStrip.CONTEXT_ENTRIES}"))
        assertFalse("LF only", lua.contains("\r"))
        val toc = addonFile("TouchForever.toc") ?: return
        assertTrue(toc.lines().any { it.trim() == "TouchForeverContext.lua" })
    }

    private fun addonFile(name: String): String? {
        val dir = File("src/main/assets/wow/addon/TouchForever").takeIf { it.isDirectory }
            ?: File("app/src/main/assets/wow/addon/TouchForever")
        if (!dir.isDirectory) return null // run from an unexpected working directory
        return File(dir, name).readText()
    }
}
