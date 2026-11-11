package app.gamenative.ui.screen.wow.context

import org.junit.Rule
import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMenusTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private val names = mapOf(
        100 to "Feuerball", 101 to "Frostblitz", 102 to "Pyroblast", 103 to "Eisspeer",
        200 to "Heilen", 201 to "Segen",
    )

    private fun spell(id: Int, out: Boolean = false, noMana: Boolean = false, cd: Float = 0f) =
        SpellEntry(id, SpellEntry.FILLED or (if (out) SpellEntry.OUT_OF_RANGE else 0) or (if (noMana) SpellEntry.NOT_USABLE else 0), cd)

    private fun entries(enemy: List<SpellEntry?> = emptyList(), friend: List<SpellEntry?> = emptyList()): List<SpellEntry?> =
        List(8) { enemy.getOrNull(it) } + List(8) { friend.getOrNull(it) }

    private fun data(
        unit: UnitInfo = UnitInfo(),
        focus: FocusInfo = FocusInfo(),
        enemy: List<SpellEntry?> = listOf(spell(100), spell(101, out = true), spell(102)),
        friend: List<SpellEntry?> = listOf(spell(200), spell(201)),
        bar: List<Int> = List(12) { BarClass.NONE },
        flags: Int = ContextData.KEYS or ContextData.SPELLS,
    ) = ContextData(flags, unit, focus, bar, entries(enemy, friend))

    // the unit is already the target: the numbers of the spell lists belong to it
    private val mob = UnitInfo(
        exists = true, relation = UnitRelation.HOSTILE, canAttack = true, source = UnitSource.MOUSEOVER, reach = Reach.CLOSE,
        hasTarget = true, isTarget = true,
    )
    private val friendlyPlayer = UnitInfo(exists = true, isPlayer = true, relation = UnitRelation.FRIEND, source = UnitSource.MOUSEOVER, reach = Reach.MID)

    private fun bars(vararg slots: Pair<Int, BarSlotState>): (Int) -> BarSlotState? {
        val map = slots.toMap()
        return { map[it] }
    }

    private fun build(d: ContextData, mode: ContextMode = ContextMode.AUTO, bar: (Int) -> BarSlotState? = { null }) =
        ContextMenus.build(d, mode, bar) { names[it] }

    private fun ids(list: List<ContextEntry>) = list.map { it.id }

    // ---- units --------------------------------------------------------------------------------------------

    @Test
    fun noUnitGivesNoMenuAndTheSettingOffNeither() {
        assertTrue(build(data(UnitInfo())).isEmpty())
        assertTrue(build(data(mob), mode = ContextMode.OFF).isEmpty())
    }

    @Test
    fun aLivingEnemyOffersAttacksAutoAttackAndTarget() {
        val menu = build(data(mob))
        // Frostblitz is out of range: after the two in range
        assertEquals(listOf("spell:0", "spell:2", "spell:1", "attack", "target"), ids(menu))
        assertEquals(listOf("Feuerball", "Pyroblast", "Frostblitz"), menu.take(3).map { it.label })
        assertEquals(listOf(ContextKeys.spellKey(0), ContextKeys.spellKey(2), ContextKeys.spellKey(1)), menu.take(3).map { it.key })
        assertTrue(menu.take(3).all { it.action == EntryAction.KEY })
        assertEquals(ContextKeys.ATTACK, menu.first { it.id == "attack" }.key)
        assertEquals(EntryAction.LEFT_CLICK, menu.first { it.id == "target" }.action)
        // the spell key is the index in the list, not the place in the menu
        assertEquals(ContextKeys.SPELL_KEYS[1], menu.first { it.id == "spell:1" }.key)
    }

    @Test
    fun theSortingByRangeKeepsTheBookOrderWithinTheGroups() {
        val enemy = listOf(spell(100, out = true), spell(101), spell(102, out = true), spell(103))
        val menu = build(data(mob, enemy = enemy))
        assertEquals(listOf("spell:1", "spell:3", "spell:0", "spell:2"), ids(menu).take(4))
    }

    @Test
    fun spellsOnCooldownOrWithoutManaAreDimmedButStillThere() {
        val menu = build(data(mob, enemy = listOf(spell(100, cd = 12f), spell(101, noMana = true), spell(102))))
        val byId = menu.associateBy { it.id }
        assertTrue(byId.getValue("spell:0").dimmed)
        assertEquals(12f, byId.getValue("spell:0").cooldown, 0f)
        assertTrue(byId.getValue("spell:1").dimmed)
        assertFalse(byId.getValue("spell:2").dimmed)
        assertTrue(byId.values.all { it.enabled })
        assertEquals("12", ContextMenus.cooldownText(byId.getValue("spell:0")))
    }

    @Test
    fun unnamedSpellsAreShownByTheirNumber() {
        val menu = ContextMenus.build(data(mob, enemy = listOf(spell(999))), ContextMode.AUTO, { null }) { null }
        assertEquals("Zauber 999", menu.first().label)
    }

    @Test
    fun withoutArmedKeysKeyEntriesAreDisabledButClicksStay() {
        val menu = build(data(mob, flags = ContextData.SPELLS))
        assertTrue(menu.filter { it.action == EntryAction.KEY }.none { it.enabled })
        assertTrue(menu.first { it.id == "target" }.enabled)
    }

    @Test
    fun aDeadEnemyOffersLootAndSkinningOnlyWhenSkinnable() {
        val dead = mob.copy(dead = true)
        assertEquals(listOf("loot", "target"), ids(build(data(dead))))
        val skin = build(data(dead.copy(skinnable = true)))
        assertEquals(listOf("loot", "skin", "target"), ids(skin))
        assertEquals(EntryAction.RIGHT_CLICK, skin[0].action)
        assertEquals(EntryAction.RIGHT_CLICK, skin[1].action)
        assertEquals("Kürschnern", skin[1].label)
        // a dead friendly or neutral NPC is lootable as well, a dead player is not
        assertEquals(listOf("loot", "target"), ids(build(data(UnitInfo(exists = true, dead = true, relation = UnitRelation.NEUTRAL, source = UnitSource.MOUSEOVER)))))
        assertEquals("a dead player gets the mini menu for players, no loot", "more", build(data(friendlyPlayer.copy(dead = true))).last().id)
    }

    @Test
    fun myOwnFigureGetsTheSelfMenuWithTheFiveWindows() {
        val menu = build(data(friendlyPlayer.copy(isSelf = true)))
        assertEquals(listOf("bags", "character", "spellbook", "talents", "map"), ids(menu))
        assertEquals(listOf("Taschen", "Charakter", "Zauberbuch", "Talente", "Karte"), menu.map { it.label })
        assertEquals(listOf("OPENALLBAGS", "TOGGLECHARACTER0", "TOGGLESPELLBOOK", "TOGGLETALENTS", "TOGGLEWORLDMAP"), menu.map { it.wowAction })
        // the keys are the game's own window bindings (no addon key, no protected call): every entry works without the addon keys
        for (entry in menu) {
            assertEquals(EntryAction.WOW_ACTION, entry.action)
            assertTrue(entry.enabled)
            assertNull(entry.key)
            assertNotNull("${entry.wowAction} is a known action", app.gamenative.wowaction.WowActionCatalog.find(entry.wowAction))
            val steps = ContextPlan.stepsFor(entry, cursorHasItem = false)
            assertEquals(listOf(PlanKind.WOW_KEY), steps.map { it.kind })
            assertEquals(entry.wowAction, steps.single().wowAction)
        }
        assertTrue(menu.all { it.ring == 0 })
        assertNull("one ring: the layout splits by itself", ContextMenus.ringCounts(menu))
        assertEquals(menu, build(data(friendlyPlayer.copy(isSelf = true), flags = 0)))            // without the addon keys as well
        assertTrue(build(data(friendlyPlayer.copy(isSelf = true)), mode = ContextMode.OFF).isEmpty())
        // an item under the pointer goes first
        assertEquals("use", build(data(friendlyPlayer.copy(isSelf = true), focus = FocusInfo(FocusKind.BAG, filled = true))).first().id)
    }

    @Test
    fun aFriendlyPlayerGetsTheMiniMenuWithMoreAsTheGamesOwnMenu() {
        val menu = build(data(friendlyPlayer.copy(reach = Reach.CLOSE, isTarget = true)))
        assertTrue(ContextMenus.usesPlayerMenu(data(friendlyPlayer), ContextMode.AUTO))
        // inside: whisper by voice, whisper by keyboard, invite, follow, trade, wave
        assertEquals(
            listOf("whisper_voice", "whisper_keys", "invite", "follow", "trade", "wave"),
            ids(menu.filter { it.ring == 0 }),
        )
        // outside: add friend, inspect, the helpful spells in range (the first two of the friend list here), "More ..." last
        assertEquals(
            listOf("friend", "inspect", "spell:0", "spell:1", "more"),
            ids(menu.filter { it.ring == 1 }),
        )
        assertEquals(listOf(6, 5), ContextMenus.ringCounts(menu))
        assertEquals(listOf("Flüstern (Sprache)", "Flüstern (Tastatur)", "Einladen", "Folgen", "Handeln", "Winken"), menu.take(6).map { it.label })
        assertEquals("Mehr ...", menu.last().label)
        // every entry has a symbol: drawn glyphs for the fixed ones, the game's icon for the spells
        assertTrue(menu.filter { !it.id.startsWith("spell:") }.all { it.glyph != null })
        // the old direct entries of the game's own menu are not ours: duel and so on
        assertTrue(ids(menu).none { it in setOf("duel", "target", "attack", "talk") })
    }

    @Test
    fun theMiniMenuActionsUseTheAddonKeysAndTheAppsVoiceKeyboardAndChat() {
        val menu = build(data(friendlyPlayer.copy(isTarget = true)))
        fun by(id: String) = menu.first { it.id == id }
        assertEquals(ContextKeys.INVITE, by("invite").key)
        assertEquals(ContextKeys.FOLLOW, by("follow").key)
        assertEquals(ContextKeys.TRADE, by("trade").key)
        assertEquals(ContextKeys.INSPECT, by("inspect").key)
        assertEquals(ContextKeys.FRIEND, by("friend").key)
        assertEquals(ContextKeys.POPUP, by("more").key)
        for (id in listOf("invite", "follow", "trade", "inspect", "friend", "more")) {
            assertEquals(listOf(PlanKind.KEY), ContextPlan.stepsFor(by(id), cursorHasItem = false).map { it.kind })
        }
        // whisper: the addon key opens "/w Name ", then the app listens or opens the keyboard
        val voice = ContextPlan.stepsFor(by("whisper_voice"), cursorHasItem = false)
        assertEquals(listOf(PlanKind.KEY, PlanKind.VOICE_WHISPER), voice.map { it.kind })
        assertEquals(ContextKeys.WHISPER, voice[0].key)
        assertTrue("the game gets time to open the box", voice[0].waitAfterMs >= 200L)
        val keyboard = ContextPlan.stepsFor(by("whisper_keys"), cursorHasItem = false)
        assertEquals(listOf(PlanKind.KEY, PlanKind.KEYBOARD), keyboard.map { it.kind })
        assertEquals(ContextKeys.WHISPER, keyboard[0].key)
        // wave is a chat command, no key
        val wave = ContextPlan.stepsFor(by("wave"), cursorHasItem = false)
        assertEquals(listOf(PlanKind.CHAT), wave.map { it.kind })
        assertEquals("/wave", wave.single().text)
        assertTrue("a chat command works without the addon keys", by("wave").enabled)
    }

    @Test
    fun aPlayerInMyGroupHasLeaveGroupInPlaceOfInvite() {
        val inGroup = build(data(friendlyPlayer.copy(inGroup = true, isTarget = true)))
        // same place, same ring, same count; only the entry differs
        assertEquals(
            listOf("whisper_voice", "whisper_keys", "leave_group", "follow", "trade", "wave"),
            ids(inGroup.filter { it.ring == 0 }),
        )
        assertEquals(listOf(6, 5), ContextMenus.ringCounts(inGroup))
        val leave = inGroup.first { it.id == "leave_group" }
        assertEquals("Gruppe verlassen", leave.label)
        assertEquals(ContextGlyph.LEAVE, leave.glyph)
        assertEquals(EntryAction.KEY, leave.action)
        assertEquals(ContextKeys.INVITE, leave.key)          // the invite key has the second meaning, the addon decides at the target
        assertTrue(leave.enabled && !leave.dimmed)
        assertEquals(listOf(PlanKind.KEY), ContextPlan.stepsFor(leave, cursorHasItem = false).map { it.kind })
        assertEquals(ContextKeys.INVITE, ContextPlan.stepsFor(leave, cursorHasItem = false).single().key)
        assertNull("no question of its own: the entry is the deliberate choice", ContextPlan.stepsFor(leave, cursorHasItem = false).single().text)
        // not in my group (or the addon reports none): the old invite, never "Leave group"
        val outside = build(data(friendlyPlayer.copy(inGroup = false, isTarget = true)))
        assertTrue(outside.none { it.id == "leave_group" })
        assertEquals("Einladen", outside.first { it.id == "invite" }.label)
        assertFalse(outside.first { it.id == "invite" }.dimmed)
        assertEquals(ContextGlyph.INVITE, outside.first { it.id == "invite" }.glyph)
        // without the addon keys the entry does nothing
        val unarmed = build(data(friendlyPlayer.copy(inGroup = true), flags = 0))
        assertFalse(unarmed.first { it.id == "leave_group" }.enabled)
        // a hostile or dead player is handled as before (no leave entry in the attack menu)
        assertTrue(build(data(friendlyPlayer.copy(inGroup = true, relation = UnitRelation.HOSTILE))).none { it.id == "leave_group" })
        // the own figure: the self menu stays as it was (it does not select the target, the key would act on whatever is targeted)
        assertTrue(build(data(friendlyPlayer.copy(isSelf = true, inGroup = true))).none { it.id == "leave_group" })
    }

    @Test
    fun theMiniMenuDimsWhatIsTooFarAndDisablesAddonKeysWithoutTheAddon() {
        val far = build(data(friendlyPlayer.copy(reach = Reach.FAR, isTarget = true)))
        assertTrue(far.first { it.id == "follow" }.dimmed)
        assertTrue(far.first { it.id == "trade" }.dimmed)
        assertTrue(far.first { it.id == "inspect" }.dimmed)
        assertFalse(far.first { it.id == "invite" }.dimmed)
        val mid = build(data(friendlyPlayer.copy(reach = Reach.MID, isTarget = true)))
        assertFalse(mid.first { it.id == "follow" }.dimmed)
        assertTrue(mid.first { it.id == "trade" }.dimmed)
        // distance unknown (in combat): nothing is dimmed on suspicion
        val unknown = build(data(friendlyPlayer.copy(reach = Reach.UNKNOWN, isTarget = true)))
        assertTrue(unknown.none { it.id in setOf("follow", "trade", "inspect") && it.dimmed })
        // already in the group: no invite any more, "Leave group" stands in its place (see the tests below)
        assertTrue(build(data(friendlyPlayer.copy(inGroup = true))).none { it.id == "invite" })
        // the addon keys are not armed: the key entries do nothing, the wave still does
        val unarmed = build(data(friendlyPlayer, flags = 0))
        assertTrue(unarmed.filter { it.action == EntryAction.KEY || it.action == EntryAction.WHISPER_VOICE || it.action == EntryAction.WHISPER_KEYBOARD }.none { it.enabled })
        assertTrue(unarmed.first { it.id == "wave" }.enabled)
    }

    @Test
    fun theMiniMenuHasAtMostFourHelpfulSpellsAndOnlyThoseTheAddonListed() {
        val many = build(data(friendlyPlayer.copy(isTarget = true), friend = List(8) { spell(300 + it) }))
        assertEquals(4, many.count { it.id.startsWith("spell:") })
        assertEquals("more", many.last().id)
        assertEquals(listOf(6, 2 + 4 + 1), ContextMenus.ringCounts(many))
        // not yet the target: the numbers of the friend list are not his: no spells yet
        assertTrue(build(data(friendlyPlayer)).none { it.id.startsWith("spell:") })
        // the ring layout takes the counts: inner 6, outer 7
        val layout = ContextMenuLayout.compute(
            many.size, 600f, 400f, 1280f, 800f, ContextMenuLayout.Geometry.forDensity(1f), emptyList(), ContextMenuLayout.ARC_DEG,
            ContextMenus.ringCounts(many),
        )
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1), layout.segments.map { it.ring })
    }

    @Test
    fun ringCountsNeedEntriesOrderedByRing() {
        fun e(ring: Int) = ContextEntry("x", "x", EntryAction.KEY, ring = ring)
        assertNull(ContextMenus.ringCounts(emptyList()))
        assertNull(ContextMenus.ringCounts(listOf(e(0), e(0))))
        assertEquals(listOf(2, 1), ContextMenus.ringCounts(listOf(e(0), e(0), e(1))))
        assertNull("not ordered", ContextMenus.ringCounts(listOf(e(1), e(0))))
        assertNull("an empty inner ring", ContextMenus.ringCounts(listOf(e(1), e(1))))
    }

    @Test
    fun onlySpellsTheAddonListedAreOffered() {
        // the addon leaves out what cannot be cast on another unit (Stoneform, tracking, self buffs): an empty place is not shown
        val menu = build(data(mob, enemy = listOf(spell(100), null, spell(102))))
        assertEquals(listOf("spell:0", "spell:2", "attack", "target"), ids(menu))
        // the spell keys keep their place in the list
        assertEquals(ContextKeys.SPELL_KEYS[2], menu.first { it.id == "spell:2" }.key)
        // unfilled places (flags 0) are not shown either
        assertEquals(listOf("spell:0", "attack", "target"), ids(build(data(mob, enemy = listOf(spell(100), SpellEntry(101, 0, 0f))))))
    }

    @Test
    fun theSpellsWaitUntilTheUnitIsTheTarget() {
        // the click that chooses the target is on its way: the numbers still belong to the old target, so no spells yet
        val notYet = mob.copy(isTarget = false)
        assertEquals(listOf("attack", "target"), ids(build(data(notYet))))
        assertEquals(listOf("spell:0", "spell:2", "spell:1", "attack", "target"), ids(build(data(mob))))
    }

    @Test
    fun theSpellsOfTheMenuHaveNoDrawnSymbolButTheFixedEntriesHave() {
        val menu = build(data(mob))
        assertTrue(menu.filter { it.id.startsWith("spell:") }.all { it.glyph == null && it.spellId > 0 })
        assertEquals(ContextGlyph.ATTACK, menu.first { it.id == "attack" }.glyph)
        assertEquals(ContextGlyph.TARGET, menu.first { it.id == "target" }.glyph)
        val dead = build(data(mob.copy(dead = true, skinnable = true)))
        assertEquals(listOf(ContextGlyph.LOOT, ContextGlyph.SKIN, ContextGlyph.TARGET), dead.map { it.glyph })
        assertEquals(ContextGlyph.TALK, build(data(UnitInfo(exists = true, relation = UnitRelation.FRIEND, source = UnitSource.MOUSEOVER))).first().glyph)
    }

    @Test
    fun aFriendlyOrNeutralNpcOffersTalkingAndTarget() {
        val npc = UnitInfo(exists = true, relation = UnitRelation.FRIEND, source = UnitSource.MOUSEOVER)
        assertEquals(listOf("talk", "target"), ids(build(data(npc))))
        assertEquals(EntryAction.RIGHT_CLICK, build(data(npc)).first().action)
        assertEquals(listOf("talk", "target"), ids(build(data(npc.copy(relation = UnitRelation.NEUTRAL)))))
        assertEquals(listOf("talk", "target"), ids(build(data(npc.copy(relation = UnitRelation.UNKNOWN)))))
    }

    @Test
    fun anAttackableYellowMobGetsTheAttackMenuNotTalking() {
        // neutral by reaction but attackable (UnitCanAttack): attacks; only what cannot be attacked is talked to
        val yellow = UnitInfo(
            exists = true, relation = UnitRelation.NEUTRAL, canAttack = true, source = UnitSource.MOUSEOVER, hasTarget = true, isTarget = true,
        )
        val menu = build(data(yellow))
        assertTrue("attack" in ids(menu))
        assertFalse("talk" in ids(menu))
        assertTrue(menu.any { it.id.startsWith("spell:") })
        // the same unit not attackable (a vendor): talk
        assertEquals(listOf("talk", "target"), ids(build(data(yellow.copy(canAttack = false)))))
        // an older addon reports no flag: the reaction decides, as before
        assertEquals(listOf("talk", "target"), ids(build(data(UnitInfo(exists = true, relation = UnitRelation.NEUTRAL, source = UnitSource.MOUSEOVER)))))
        assertTrue("attack" in ids(build(data(UnitInfo(exists = true, relation = UnitRelation.HOSTILE, source = UnitSource.MOUSEOVER, isTarget = true)))))
    }

    @Test
    fun aHostilePlayerIsAnEnemy() {
        val menu = build(data(mob.copy(isPlayer = true)))
        assertTrue("attack" in ids(menu))
        assertFalse("whisper" in ids(menu))
    }

    // ---- the action bar mode -------------------------------------------------------------------------------

    @Test
    fun theBarModeOffersTheSlotsOfBarOneWithTheirClassAndRange() {
        val bar = bars(
            1 to BarSlotState(true, "Feuerball"),
            2 to BarSlotState(true, "Heilen"),
            3 to BarSlotState(true, "Makro", outOfRange = true),
            4 to BarSlotState(true, "Trank", notUsable = true, cooldown = 30f),
            5 to BarSlotState(false),
        )
        val classes = List(12) { slot ->
            when (slot) {
                0 -> BarClass.HARMFUL
                1 -> BarClass.HELPFUL
                else -> BarClass.NONE
            }
        }
        val enemy = build(data(mob, bar = classes), mode = ContextMode.BAR, bar = bar)
        // slot 2 is helpful: not against an enemy; slot 3 (unknown class) is out of range: after slot 1 and 4
        assertEquals(listOf("bar:1", "bar:4", "bar:3", "attack", "target"), ids(enemy))
        assertTrue(enemy.take(3).all { it.action == EntryAction.BAR_SLOT })
        assertEquals(listOf(1, 4, 3), enemy.take(3).map { it.barSlot })
        assertTrue(enemy.first { it.id == "bar:4" }.dimmed)
        assertEquals(30f, enemy.first { it.id == "bar:4" }.cooldown, 0f)
        // a friendly (not attackable) NPC gets no spells of the bar
        val npc = UnitInfo(exists = true, relation = UnitRelation.FRIEND, source = UnitSource.MOUSEOVER)
        assertEquals(listOf("talk", "target"), ids(build(data(npc, bar = classes), mode = ContextMode.BAR, bar = bar)))
        // the lists of the automatic mode are not used in the bar mode
        assertFalse(ids(enemy).any { it.startsWith("spell:") })
    }

    @Test
    fun theBarSlotNameIsTheLabelAndAFallbackExists() {
        val menu = build(data(mob), mode = ContextMode.BAR, bar = bars(7 to BarSlotState(true, null)))
        assertEquals("Leiste 7", menu.first().label)
    }

    @Test
    fun theAutomaticModeIgnoresTheBar() {
        val menu = build(data(mob), mode = ContextMode.AUTO, bar = bars(1 to BarSlotState(true, "X")))
        assertFalse(ids(menu).any { it.startsWith("bar:") })
    }

    // ---- items ---------------------------------------------------------------------------------------------

    private fun item(
        kind: FocusKind = FocusKind.BAG,
        equippable: Boolean = false,
        usable: Boolean = false,
        stacked: Boolean = false,
        vendor: Boolean = false,
        cursor: Boolean = false,
    ) = FocusInfo(kind, filled = true, equippable = equippable, usable = usable, stacked = stacked, merchantOpen = vendor, cursorHasItem = cursor, count = if (stacked) 5 else 1)

    @Test
    fun anItemInTheBagsOffersUseMoveLinkCompareAndDiscard() {
        val sword = build(data(focus = item(equippable = true)))
        assertEquals(listOf("use", "move", "link", "compare", "discard"), ids(sword))
        assertEquals("Anlegen", sword[0].label)
        assertEquals(EntryAction.RIGHT_CLICK, sword[0].action)
        assertEquals("Benutzen", build(data(focus = item(usable = true)))[0].label)
        assertEquals("Verkaufen", build(data(focus = item(equippable = true, vendor = true)))[0].label)
        // an item that the addon could not classify as worn or used still gets "Benutzen": the right click does the right thing
        val plain = build(data(focus = item()))
        assertEquals(listOf("use", "move", "link", "compare", "discard"), ids(plain))
        assertEquals("Benutzen", plain[0].label)
        assertEquals(EntryAction.RIGHT_CLICK, plain[0].action)
        assertTrue(plain[0].clearsCursor)
    }

    @Test
    fun everyFilledSlotOffersTheRightClickAsFirstEntry() {
        for (kind in FocusKind.entries.filter { it.isItemSlot }) {
            for (equippable in listOf(false, true)) for (usable in listOf(false, true)) for (vendor in listOf(false, true)) for (cursor in listOf(false, true)) {
                val menu = build(data(focus = item(kind = kind, equippable = equippable, usable = usable, vendor = vendor, cursor = cursor)))
                val first = menu.first()
                assertEquals("$kind $equippable $usable $vendor $cursor", "use", first.id)
                assertEquals(EntryAction.RIGHT_CLICK, first.action)
                assertTrue(first.clearsCursor)
            }
        }
    }

    @Test
    fun theRightClickEntryIsNamedByWhatItDoes() {
        assertEquals("Anlegen" to ContextGlyph.EQUIP, build(data(focus = item(equippable = true))).first().let { it.label to it.glyph })
        assertEquals("Benutzen" to ContextGlyph.USE, build(data(focus = item(usable = true))).first().let { it.label to it.glyph })
        assertEquals("Benutzen" to ContextGlyph.USE, build(data(focus = item())).first().let { it.label to it.glyph })
        assertEquals("Verkaufen" to ContextGlyph.SELL, build(data(focus = item(vendor = true))).first().let { it.label to it.glyph })
        assertEquals("Ausziehen" to ContextGlyph.UNEQUIP, build(data(focus = item(kind = FocusKind.EQUIPMENT))).first().let { it.label to it.glyph })
        // wearing beats using; the vendor beats both (a right click at a vendor sells), except for worn gear
        assertEquals("Anlegen", build(data(focus = item(equippable = true, usable = true))).first().label)
        assertEquals("Verkaufen", build(data(focus = item(equippable = true, usable = true, vendor = true))).first().label)
        assertEquals("Ausziehen", build(data(focus = item(kind = FocusKind.EQUIPMENT, vendor = true))).first().label)
    }

    @Test
    fun everyItemEntryHasADrawnSymbol() {
        val menu = build(data(focus = item(equippable = true, stacked = true)))
        assertTrue(menu.all { it.glyph != null })
        assertEquals(
            listOf(ContextGlyph.EQUIP, ContextGlyph.MOVE, ContextGlyph.SPLIT, ContextGlyph.LINK, ContextGlyph.COMPARE, ContextGlyph.DISCARD),
            menu.map { it.glyph },
        )
    }

    @Test
    fun stackedItemsCanBeSplit() {
        val menu = build(data(focus = item(stacked = true, usable = true)))
        assertTrue("split" in ids(menu))
        assertEquals(EntryAction.SHIFT_LEFT_CLICK, menu.first { it.id == "split" }.action)
        assertFalse("split" in ids(build(data(focus = item(usable = true)))))
    }

    @Test
    fun anEquippedItemIsTakenOffAndABankItemMovedAcross() {
        assertEquals("Ausziehen", build(data(focus = item(kind = FocusKind.EQUIPMENT)))[0].label)
        assertEquals("Umlagern", build(data(focus = item(kind = FocusKind.BANK)))[0].label)
    }

    @Test
    fun anItemOnTheCursorIsPutDownByMove() {
        val menu = build(data(focus = item(equippable = true, cursor = true)))
        assertEquals("Hier ablegen", menu.first { it.id == "move" }.label)
        assertEquals(EntryAction.LEFT_CLICK, menu.first { it.id == "move" }.action)
        assertEquals("Verschieben", build(data(focus = item(equippable = true))).first { it.id == "move" }.label)
    }

    @Test
    fun theItemEntriesThatClickAnItemPutTheCursorItemBackFirst() {
        val menu = build(data(focus = item(equippable = true, stacked = true)))
        val byId = menu.associateBy { it.id }
        assertTrue(byId.getValue("use").clearsCursor)
        assertTrue(byId.getValue("split").clearsCursor)
        assertTrue(byId.getValue("discard").clearsCursor)
        // moving picks up or puts down: it must not clear
        assertFalse(byId.getValue("move").clearsCursor)
    }

    @Test
    fun theItemMenuComesBeforeAnyUnit() {
        // a unit under the pointer is ignored while an item slot lies there (windows have no mouseover unit; the focus wins in any case)
        val menu = build(data(unit = mob, focus = item(usable = true)))
        assertTrue("discard" in ids(menu))
        assertFalse("attack" in ids(menu))
    }

    @Test
    fun anEmptySlotHasNoItemMenuAndFallsBackToTheUnit() {
        val empty = FocusInfo(kind = FocusKind.BAG, filled = false)
        assertTrue(build(data(focus = empty)).isEmpty())
        assertNotNull(build(data(unit = mob, focus = empty)).firstOrNull { it.id == "attack" })
    }

    @Test
    fun discardOnlyAsksOnTheKeyAndNeverClicks() {
        val discard = build(data(focus = item(equippable = true))).first { it.id == "discard" }
        assertEquals(EntryAction.KEY, discard.action)
        assertEquals(ContextKeys.DISCARD, discard.key)
        assertEquals("Wegwerfen", discard.label)
    }

    @Test
    fun itemKeysNeedTheArmedAddon() {
        val menu = build(data(focus = item(equippable = true), flags = 0))
        assertFalse(menu.first { it.id == "link" }.enabled)
        assertFalse(menu.first { it.id == "discard" }.enabled)
        assertTrue(menu.first { it.id == "use" }.enabled)
    }

    // ---- quests --------------------------------------------------------------------------------------------

    private fun quest(shareable: Boolean = false, tracked: Boolean = false) =
        FocusInfo(FocusKind.QUEST, filled = true, shareable = shareable, tracked = tracked)

    @Test
    fun aQuestOffersLinkShareTrackAndTheGamesOwnMenu() {
        val menu = build(data(focus = quest(shareable = true)))
        assertEquals(listOf("quest_link", "quest_share", "quest_track", "quest_more"), ids(menu))
        assertEquals(listOf("Im Chat verlinken", "Mit Gruppe teilen", "Verfolgen", "Mehr ..."), menu.map { it.label })
        assertEquals(ContextGlyph.LINK, menu[0].glyph)
        assertEquals(listOf(EntryAction.KEY, EntryAction.KEY, EntryAction.KEY, EntryAction.RIGHT_CLICK), menu.map { it.action })
        assertEquals(listOf(ContextKeys.LINK_ITEM, ContextKeys.COMPARE, ContextKeys.CLEAR_CURSOR, null), menu.map { it.key })
        assertTrue(menu.all { it.enabled })
        assertTrue("no cancel entry of our own", menu.none { it.id.contains("cancel") || it.id.contains("abandon") })
        assertTrue("every entry has a drawn symbol", menu.all { it.glyph != null })
    }

    @Test
    fun aQuestThatCannotBeSharedIsDimmedNotHidden() {
        val menu = build(data(focus = quest(shareable = false)))
        val share = menu.first { it.id == "quest_share" }
        assertFalse(share.enabled)
        assertTrue(menu.first { it.id == "quest_link" }.enabled)
        assertTrue(menu.first { it.id == "quest_track" }.enabled)
    }

    @Test
    fun trackingFlipsItsLabel() {
        assertEquals("Verfolgen", build(data(focus = quest(tracked = false))).first { it.id == "quest_track" }.label)
        assertEquals("Nicht verfolgen", build(data(focus = quest(tracked = true))).first { it.id == "quest_track" }.label)
    }

    @Test
    fun questKeysNeedTheArmedAddonButMoreDoesNot() {
        val menu = build(data(focus = quest(shareable = true), flags = 0))
        assertFalse(menu.first { it.id == "quest_link" }.enabled)
        assertFalse(menu.first { it.id == "quest_share" }.enabled)
        assertFalse(menu.first { it.id == "quest_track" }.enabled)
        assertTrue(menu.first { it.id == "quest_more" }.enabled)
    }

    @Test
    fun theQuestMenuComesBeforeAnyUnitAndTheOffSettingStaysOff() {
        val menu = build(data(unit = mob, focus = quest()))
        assertEquals("quest_link", menu.first().id)
        assertTrue("attack" !in ids(menu))
        assertTrue(build(data(unit = friendlyPlayer, focus = quest())).none { it.id == "whisper_voice" })
        assertTrue(build(data(focus = quest()), mode = ContextMode.OFF).isEmpty())
        // an item under the pointer still wins (the addon never reports both, the order is part of the contract)
        assertTrue("discard" in ids(build(data(focus = item(equippable = true)))))
        // a quest that is not present is nothing
        assertTrue(build(data(focus = FocusInfo(FocusKind.QUEST, filled = false))).isEmpty())
    }
}
