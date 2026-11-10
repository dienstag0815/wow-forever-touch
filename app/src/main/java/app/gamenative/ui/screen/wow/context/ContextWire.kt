package app.gamenative.ui.screen.wow.context

import app.gamenative.cooldown.CooldownStrip
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How the unit under the finger relates to the player (2 bits on the wire). */
enum class UnitRelation(val code: Int) {
    UNKNOWN(0),
    FRIEND(1),
    HOSTILE(2),
    NEUTRAL(3),
    ;

    companion object {
        fun of(code: Int): UnitRelation = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Where the addon took the unit from (2 bits on the wire): the unit under the mouse pointer, or (fallback) the target. */
enum class UnitSource(val code: Int) {
    NONE(0),
    MOUSEOVER(1),
    TARGET(2),
    ;

    companion object {
        fun of(code: Int): UnitSource = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/**
 * Distance classes from CheckInteractDistance (0 = not known, e.g. in combat). Every class includes the nearer ones: a unit in trade
 * range is also in inspect range.
 */
object Reach {
    const val UNKNOWN = 0

    /** Further than 28 yards. */
    const val FAR = 1

    /** Within 28 yards (inspect, follow). */
    const val MID = 2

    /** Within 11 yards (trade). */
    const val NEAR = 3

    /** Within 9.9 yards (duel). */
    const val CLOSE = 4

    const val MAX = 4
}

/** The unit under the finger as the addon reports it (all fields are false / zero when there is none). */
data class UnitInfo(
    val exists: Boolean = false,
    val isPlayer: Boolean = false,
    val dead: Boolean = false,
    val skinnable: Boolean = false,
    val relation: UnitRelation = UnitRelation.UNKNOWN,
    val source: UnitSource = UnitSource.NONE,
    val reach: Int = Reach.UNKNOWN,
    val inGroup: Boolean = false,
    /** UnitReaction(unit, "player"), 0 = unknown, 1..8. */
    val reaction: Int = 0,
    val hasTarget: Boolean = false,
    /** The unit under the finger is the current target. */
    val isTarget: Boolean = false,
    val isSelf: Boolean = false,
    /** UnitCanAttack("player", unit): attackable, also yellow (neutral) mobs. Older addons do not report it (false). */
    val canAttack: Boolean = false,
) {
    /** Gets the attack menu: attackable or hostile by reaction. */
    val hostile: Boolean get() = canAttack || relation == UnitRelation.HOSTILE

    companion object {
        const val F_EXISTS = 1
        const val F_PLAYER = 2
        const val F_DEAD = 4
        const val F_SKINNABLE = 8
        const val G_IN_GROUP = 8
        const val H_HAS_TARGET = 1
        const val H_IS_TARGET = 2
        const val H_IS_SELF = 4
        const val H_CAN_ATTACK = 8
    }
}

/** What lies under the mouse pointer besides units: an item slot of the bags, the bank or the character window. */
enum class FocusKind(val code: Int) {
    NONE(0),
    BAG(1),
    BANK(2),
    EQUIPMENT(3),
    ;

    val isItemSlot: Boolean get() = this != NONE

    companion object {
        fun of(code: Int): FocusKind = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** The item slot under the pointer as the addon reports it ("Mausfokus"). */
data class FocusInfo(
    val kind: FocusKind = FocusKind.NONE,
    /** There is an item in the slot. */
    val filled: Boolean = false,
    val equippable: Boolean = false,
    /** The item can be used (potion, scroll ... an item with a use spell). */
    val usable: Boolean = false,
    /** Stack of more than one. */
    val stacked: Boolean = false,
    /** A vendor window is open (a right click sells). */
    val merchantOpen: Boolean = false,
    /** The cursor carries an item (picked up by an earlier touch). */
    val cursorHasItem: Boolean = false,
    /** Stack size, 0..15 (15 = 15 or more). */
    val count: Int = 0,
) {
    /** A filled item slot: the case the item menu and "tap = look" are about. */
    val isItem: Boolean get() = kind.isItemSlot && filled

    companion object {
        const val F_FILLED = 1
        const val F_EQUIPPABLE = 2
        const val F_USABLE = 4
        const val F_STACKED = 8
        const val M_MERCHANT = 1
        const val M_CURSOR = 2
    }
}

/** Class of an action bar slot spell (2 bits on the wire). */
object BarClass {
    const val NONE = 0
    const val HARMFUL = 1
    const val HELPFUL = 2
    const val BOTH = 3
}

/** One spell of the lists the addon bound to keys: id, flags ([FILLED] ...) and the remaining cooldown in seconds. */
data class SpellEntry(val spellId: Int, val flags: Int, val cooldown: Float) {
    val filled: Boolean get() = flags and FILLED != 0

    /** Not usable now (no mana, wrong form ...). */
    val notUsable: Boolean get() = flags and NOT_USABLE != 0
    val outOfRange: Boolean get() = flags and OUT_OF_RANGE != 0
    val globalOnly: Boolean get() = flags and GCD != 0
    val cooling: Boolean get() = cooldown > 0f

    companion object {
        const val FILLED = 1
        const val NOT_USABLE = 2
        const val OUT_OF_RANGE = 4
        const val GCD = 8
    }
}

/** Everything the context section carries (the payload of [CooldownStrip.CELL_CTX_INFO] to [CooldownStrip.CELL_CTX_CHECK] - 1). */
data class ContextData(
    /** [KEYS], [SPELLS], [TRUNCATED]. */
    val ctxFlags: Int = 0,
    val unit: UnitInfo = UnitInfo(),
    val focus: FocusInfo = FocusInfo(),
    /** [BarClass] of the slots 1..12 of the action bar 1. */
    val barClass: List<Int> = List(BAR_SLOTS) { BarClass.NONE },
    /** [CooldownStrip.CONTEXT_ENTRIES] entries: 0 until [CooldownStrip.CONTEXT_SPELLS] the enemy list, then the friend list. Null = empty. */
    val entries: List<SpellEntry?> = List(CooldownStrip.CONTEXT_ENTRIES) { null },
) {
    /** The addon has set up the key buttons (the keys do something). */
    val keysArmed: Boolean get() = ctxFlags and KEYS != 0

    /** The addon has built the spell lists. */
    val spellsKnown: Boolean get() = ctxFlags and SPELLS != 0

    /** [CooldownStrip.CONTEXT_SPELLS] spells of the enemy list (nulls for the empty places, keeps the key index). */
    val enemySpells: List<SpellEntry?> get() = entries.subList(0, CooldownStrip.CONTEXT_SPELLS)
    val friendSpells: List<SpellEntry?> get() = entries.subList(CooldownStrip.CONTEXT_SPELLS, CooldownStrip.CONTEXT_ENTRIES)

    companion object {
        const val KEYS = 1
        const val SPELLS = 2
        const val TRUNCATED = 4
        const val BAR_SLOTS = 12
    }
}

/**
 * The payload of the context section as a nibble list and back. The Lua side (TouchForeverContext.lua, C.nibbles) writes the same layout:
 *
 *  - [0] version ([CooldownStrip.CONTEXT_VERSION]), [1] reserved, [2] context flags
 *  - [3..5] unit A: flags (1 exists, 2 player, 4 dead, 8 skinnable) | relation + 4 * source | reach + 8 * in group
 *  - [6..8] unit B: reaction (0..8) | 1 has target, 2 is target, 4 is self, 8 can attack | reserved
 *  - [9..14] mouse focus: kind (0 none, 1 bag, 2 bank, 3 equipment) | flags (1 filled, 2 equippable, 4 usable, 8 stacked) |
 *    1 vendor open, 2 cursor carries an item | stack size (0..15) | 0 | 0
 *  - [15..20] classes of the slots 1..12 of the action bar 1: nibble k holds slot 2k (low two bits) and 2k + 1 (high two bits)
 *  - [21 + 9 e ..] spell entry e: spell id as six nibbles (high first, 24 bit), flags, cooldown code as two nibbles (high first)
 */
object ContextWire {
    const val IDX_VERSION = 0
    const val IDX_FLAGS = 2
    const val IDX_UNIT_A = 3
    const val IDX_UNIT_B = 6
    const val IDX_FOCUS = 9
    const val IDX_BAR = 15
    const val IDX_ENTRIES = 21
    const val ENTRY_NIBBLES = 9

    /** Cooldown code 0..255: 0 none, 1..100 tenths of a second up to 10 s, 101..255 whole seconds beyond 10 s (up to 165 s). */
    @JvmStatic
    fun cooldownCode(seconds: Double): Int {
        if (seconds <= 0.0) return 0
        if (seconds <= 10.0) return max(1, floor(seconds * 10 + 0.5).toInt())
        return min(255, 100 + floor(seconds - 10 + 0.5).toInt())
    }

    @JvmStatic
    fun cooldownFromCode(code: Int): Float = when {
        code <= 0 -> 0f
        code <= 100 -> code / 10f
        else -> 10f + (code - 100)
    }

    /** The payload for [data] ([CooldownStrip.CONTEXT_NIBBLES] values 0..15). Used by the tests; the addon does the same in Lua. */
    @JvmStatic
    fun encode(data: ContextData): IntArray {
        val n = IntArray(CooldownStrip.CONTEXT_NIBBLES)
        n[IDX_VERSION] = CooldownStrip.CONTEXT_VERSION
        n[IDX_FLAGS] = data.ctxFlags and 15
        val u = data.unit
        var a = 0
        if (u.exists) a = a or UnitInfo.F_EXISTS
        if (u.isPlayer) a = a or UnitInfo.F_PLAYER
        if (u.dead) a = a or UnitInfo.F_DEAD
        if (u.skinnable) a = a or UnitInfo.F_SKINNABLE
        n[IDX_UNIT_A] = a
        n[IDX_UNIT_A + 1] = (u.relation.code or (u.source.code shl 2)) and 15
        n[IDX_UNIT_A + 2] = (u.reach.coerceIn(0, Reach.MAX) or (if (u.inGroup) UnitInfo.G_IN_GROUP else 0)) and 15
        n[IDX_UNIT_B] = u.reaction.coerceIn(0, 8)
        var b = 0
        if (u.hasTarget) b = b or UnitInfo.H_HAS_TARGET
        if (u.isTarget) b = b or UnitInfo.H_IS_TARGET
        if (u.isSelf) b = b or UnitInfo.H_IS_SELF
        if (u.canAttack) b = b or UnitInfo.H_CAN_ATTACK
        n[IDX_UNIT_B + 1] = b
        val f = data.focus
        n[IDX_FOCUS] = f.kind.code
        var ff = 0
        if (f.filled) ff = ff or FocusInfo.F_FILLED
        if (f.equippable) ff = ff or FocusInfo.F_EQUIPPABLE
        if (f.usable) ff = ff or FocusInfo.F_USABLE
        if (f.stacked) ff = ff or FocusInfo.F_STACKED
        n[IDX_FOCUS + 1] = ff
        n[IDX_FOCUS + 2] = (if (f.merchantOpen) FocusInfo.M_MERCHANT else 0) or (if (f.cursorHasItem) FocusInfo.M_CURSOR else 0)
        n[IDX_FOCUS + 3] = f.count.coerceIn(0, 15)
        for (k in 0 until ContextData.BAR_SLOTS / 2) {
            val lo = data.barClass.getOrElse(2 * k) { 0 } and 3
            val hi = data.barClass.getOrElse(2 * k + 1) { 0 } and 3
            n[IDX_BAR + k] = lo or (hi shl 2)
        }
        for (e in 0 until CooldownStrip.CONTEXT_ENTRIES) {
            val entry = data.entries.getOrNull(e) ?: continue
            val base = IDX_ENTRIES + ENTRY_NIBBLES * e
            val id = entry.spellId and 0xFFFFFF
            for (i in 0 until 6) n[base + i] = (id shr (20 - 4 * i)) and 15
            n[base + 6] = entry.flags and 15
            val cd = cooldownCode(entry.cooldown.toDouble())
            n[base + 7] = (cd shr 4) and 15
            n[base + 8] = cd and 15
        }
        return n
    }

    /** The data in a payload, or null when it is not a payload of this version. */
    @JvmStatic
    fun decode(n: IntArray): ContextData? {
        if (n.size < CooldownStrip.CONTEXT_NIBBLES || n[IDX_VERSION] != CooldownStrip.CONTEXT_VERSION) return null
        val a = n[IDX_UNIT_A]
        val exists = a and UnitInfo.F_EXISTS != 0
        val unit = UnitInfo(
            exists = exists,
            isPlayer = exists && a and UnitInfo.F_PLAYER != 0,
            dead = exists && a and UnitInfo.F_DEAD != 0,
            skinnable = exists && a and UnitInfo.F_SKINNABLE != 0,
            relation = UnitRelation.of(n[IDX_UNIT_A + 1] and 3),
            source = UnitSource.of((n[IDX_UNIT_A + 1] shr 2) and 3),
            reach = (n[IDX_UNIT_A + 2] and 7).coerceAtMost(Reach.MAX),
            inGroup = n[IDX_UNIT_A + 2] and UnitInfo.G_IN_GROUP != 0,
            reaction = n[IDX_UNIT_B].coerceAtMost(8),
            hasTarget = n[IDX_UNIT_B + 1] and UnitInfo.H_HAS_TARGET != 0,
            isTarget = n[IDX_UNIT_B + 1] and UnitInfo.H_IS_TARGET != 0,
            isSelf = n[IDX_UNIT_B + 1] and UnitInfo.H_IS_SELF != 0,
            canAttack = exists && n[IDX_UNIT_B + 1] and UnitInfo.H_CAN_ATTACK != 0,
        )
        val kind = FocusKind.of(n[IDX_FOCUS])
        val ff = n[IDX_FOCUS + 1]
        val focus = FocusInfo(
            kind = kind,
            filled = kind.isItemSlot && ff and FocusInfo.F_FILLED != 0,
            equippable = ff and FocusInfo.F_EQUIPPABLE != 0,
            usable = ff and FocusInfo.F_USABLE != 0,
            stacked = ff and FocusInfo.F_STACKED != 0,
            merchantOpen = n[IDX_FOCUS + 2] and FocusInfo.M_MERCHANT != 0,
            cursorHasItem = n[IDX_FOCUS + 2] and FocusInfo.M_CURSOR != 0,
            count = n[IDX_FOCUS + 3],
        )
        val bar = ArrayList<Int>(ContextData.BAR_SLOTS)
        for (k in 0 until ContextData.BAR_SLOTS / 2) {
            bar.add(n[IDX_BAR + k] and 3)
            bar.add((n[IDX_BAR + k] shr 2) and 3)
        }
        val entries = ArrayList<SpellEntry?>(CooldownStrip.CONTEXT_ENTRIES)
        for (e in 0 until CooldownStrip.CONTEXT_ENTRIES) {
            val base = IDX_ENTRIES + ENTRY_NIBBLES * e
            val flags = n[base + 6] and 15
            if (flags and SpellEntry.FILLED == 0) {
                entries.add(null)
                continue
            }
            var id = 0
            for (i in 0 until 6) id = (id shl 4) or (n[base + i] and 15)
            val cd = n[base + 7] * 16 + n[base + 8]
            entries.add(SpellEntry(id, flags, cooldownFromCode(cd)))
        }
        return ContextData(n[IDX_FLAGS] and 15, unit, focus, bar, entries)
    }
}
