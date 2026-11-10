package app.gamenative.cooldown

import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.components.AppText
import kotlin.math.ceil
import kotlin.math.floor

/** One slot of the cooldown strip as the app draws it. */
class CooldownCell(
    /** Seconds left, 0 = no cooldown. */
    val remaining: Float,
    /** Share of the cooldown that is left, 0..1 (1 = just started). */
    val fraction: Float,
    /** [CooldownStrip.F_FILLED] and friends. */
    val flags: Int,
) {
    val filled: Boolean get() = flags and CooldownStrip.F_FILLED != 0
    val noResource: Boolean get() = flags and CooldownStrip.F_NO_RESOURCE != 0
    val outOfRange: Boolean get() = flags and CooldownStrip.F_OUT_OF_RANGE != 0

    /** Only the short global cooldown runs (a spell cast just before). */
    val globalOnly: Boolean get() = flags and CooldownStrip.F_GCD != 0

    val cooling: Boolean get() = remaining > 0f

    override fun equals(other: Any?): Boolean =
        other is CooldownCell && other.remaining == remaining && other.fraction == fraction && other.flags == flags

    override fun hashCode(): Int = (remaining.hashCode() * 31 + fraction.hashCode()) * 31 + flags
}

/** Result of one decoding attempt. */
enum class StripStatus(@StringRes val textRes: Int) {
    OK(R.string.game_cd_read),
    NO_SYNC(R.string.game_strip_no_sync),
    BAD_TAIL(R.string.game_strip_bad_tail),
    BAD_CALIBRATION(R.string.game_strip_bad_calibration),
    BAD_CHECKSUM(R.string.game_strip_bad_checksum),
    BAD_VERSION(R.string.game_strip_bad_version),
    ;

    /** The reason in the current app language. */
    val text: String get() = AppText.get(textRes)
}

/**
 * The cooldown strip: a row of coloured cells (4 x 2 pixels) in the top left corner of the game picture that the addon
 * TouchForever draws (TouchForeverCooldown.lua; this object is the other half of the format and is tested against
 * vectors that the Lua file produced). One cell carries three nibbles (one per colour channel, level = nibble * 17).
 *
 * Cell order: 0..3 sync head, 4 info (version, counter, info flags), 5..20 calibration ramp (grey, nibble k = 17k), 21..212
 * data (two cells per slot of [SLOT_COUNT]), 213 checksum, 214..215 sync tail. The calibration ramp is measured first and the
 * nibbles are read as the nearest measured level, so a gamma or colour curve does not matter as long as it is monotonic.
 * Each cell is sampled at its two inner columns (1 and 2 of 4) in both rows: a shift of the strip by one pixel is harmless.
 *
 * Window section (optional, cells [CELL_WIN] to [TOTAL_CELLS] - 1, after the sync tail; only read when [I_WINDOWS_AWARE] is set in
 * the info flags): the rectangles of the open big game windows. Old app and new addon: the old decoder stops after cell 215 and
 * never looks at it. New app and old addon: the flag is missing, the section is not read ([Decoded.windowsKnown] false).
 * Cells: 216..217 sync head, 218 info (section version, rectangle count 0..[WIN_MAX], section flags), 219..230 [WIN_MAX]
 * rectangles of three cells (nibbles: left hi lo, top hi lo, right hi lo, bottom hi lo, kind; 0..255 = 0..1 of the game picture,
 * origin top left), 231 checksum over the 39 nibbles of 218..230, 232 sync tail. A damaged section only makes the windows
 * unknown; the cooldown part is decoded as before.
 *
 * Scroll section (build 3 and up, cells [CELL_SCR] to [TOTAL_CELLS] - 1): the rectangles of the scrollable lists inside the open
 * windows (ScrollBox, ScrollFrame, ScrollingMessageFrame with more content than they show), where the touch view turns a vertical
 * swipe into mouse wheel ticks. Only read when the window section was read and its flag [WF_SCROLL_AWARE] is set (an addon of build
 * 1 or 2 never sets it; builds 3 to 5 clear it while the former addon switch for swipe scrolling is off; from build 6 on it is always set and only the app decides). Cells: 233..234 sync head, 235 info (scroll version,
 * rectangle count 0..[SCROLL_MAX], flags: [WF_TRUNCATED]), 236..253 [SCROLL_MAX] rectangles of three cells (the window layout; kind:
 * 1 ScrollBox, 2 ScrollFrame, 3 ScrollingMessageFrame), 254 checksum over the 57 nibbles of 235..253, 255 sync tail. The rectangles
 * are sorted SMALLEST FIRST: the first one that holds a finger is the innermost list. A damaged section only makes the scroll areas
 * unknown. An old app never looks at these cells.
 *
 * Context section (build 4 and up): what is under the finger (the unit under the mouse pointer, or the target) and the item slot under the
 * pointer, for the context menu of the touch view, plus the spell lists the addon bound to keys. It lives in a SECOND ROW of the strip:
 * the [CELL_H] pixel rows right below the first row, same left edge, same cell width (so the first row is exactly the one of build 3 and
 * is 1024 pixels wide, the second row is [CTX_WIDTH_PX] wide, with the chat section right behind it [SECOND_ROW_WIDTH_PX]). Only read when the window section was read and its flag [WF_CONTEXT_AWARE]
 * is set (an addon of build 1 to 3 never sets it). The cell numbers [CELL_CTX] and up are the numbers of the Lua cell list; on the
 * screen they stand in the second row at cell number minus [CELL_CTX]. Cells: 256..257 sync head, 258 info, 259..260 unit, 261..262 mouse
 * focus (bag, bank or equipment slot under the pointer), 263..264 classes of the action bar 1 slots, 265..312 [CONTEXT_ENTRIES] spell
 * entries of three cells, 313 checksum over the [CONTEXT_NIBBLES] nibbles of 258..312, 314 sync tail. The payload is a plain nibble
 * list, its meaning is in app.gamenative.ui.screen.wow.context.ContextWire (the Lua side: TouchForeverContext.lua). A missing or damaged
 * second row only makes the context unknown. An old app never looks at these pixels.
 *
 * Chat section (build 5 and up): where the main chat window is and whether its edit box is open, for the row of big touch buttons the app puts
 * at the chat ([app.gamenative.ui.screen.wow.chatbar.ChatBarGate]). It stands in the same second row right behind the context section
 * (cell [CELL_CHAT] at x = [CTX_WIDTH_PX]) and has no flag in the window section: it is real when its own sync head, tail, checksum and version
 * match (an older addon draws nothing there, the game picture does not match). Cells: 315..316 sync head, 317 info (version, rectangle count
 * 0..1, chat flags [CF_VISIBLE] / [CF_BOX]), 318..320 one rectangle of three cells (the window layout: the main chat window together with the
 * open edit box; zero while the count is 0), 321 checksum over the 12 nibbles of 317..320, 322 sync tail. A damaged section only makes the
 * chat unknown. An old app never looks at these pixels.
 */
object CooldownStrip {
    const val CELL_W = 4
    const val CELL_H = 2
    const val VERSION = 1

    /** First slot of each bar of 12: bars 1 to 8 as [app.gamenative.wowicons.ActionSlotMap] numbers them. */
    private val SLOT_FIRST = intArrayOf(1, 25, 37, 49, 61, 145, 157, 169)
    const val SLOT_COUNT = 96

    const val CELL_INFO = 4
    const val CELL_CAL = 5
    const val CELL_DATA = 21
    const val CELL_CHECK = CELL_DATA + 2 * SLOT_COUNT
    const val CELL_TAIL = CELL_CHECK + 1
    /** Cells of the cooldown part (0..215); the window section follows. */
    const val CELL_COUNT = CELL_TAIL + 2

    // Window section.
    /**
     * Version of the window section = build number of the addon. 1: TouchForever 1.6.0 with the window section (fold8.3, fold8.4);
     * 2: plus the combat flag [WF_COMBAT] and the reload key; 3: plus the scroll section; 4: plus the context section; 5: plus the chat section; 6: the scroll section is always reported (no addon switch); 7: minimap flush in its cluster and corner rule (fold8.26). Must equal `S.WIN_VERSION` in TouchForeverCooldown.lua (a unit test
     * compares the two). A decoder reads every version from 1 to 15 (newer ones add flags, never move cells).
     */
    const val ADDON_BUILD = 8

    /** First addon build that has the scroll section ([WF_SCROLL_AWARE]). */
    const val SCROLL_BUILD = 3
    const val WIN_VERSION = ADDON_BUILD

    /** First addon build that has the reload key ([RELOAD_KEY]); a build below it is reloaded by typing `/reload`. */
    const val RELOAD_KEY_BUILD = 2

    /** The key of the reload button of the addon (override binding), in WoW notation. */
    const val RELOAD_KEY = "CTRL-SHIFT-F4"
    const val WIN_MAX = 4
    const val CELL_WIN = CELL_COUNT
    const val CELL_WIN_INFO = CELL_WIN + 2
    const val CELL_WIN_RECT = CELL_WIN_INFO + 1
    const val CELL_WIN_CHECK = CELL_WIN_RECT + 3 * WIN_MAX
    const val CELL_WIN_TAIL = CELL_WIN_CHECK + 1

    /** Pixels up to the end of the window section (what a build 1 or 2 addon draws). */
    const val WIN_WIDTH_PX = (CELL_WIN_TAIL + 1) * CELL_W

    // Scroll section.
    const val SCROLL_MAX = 6
    const val SCROLL_VERSION = 1
    const val CELL_SCR = CELL_WIN_TAIL + 1
    const val CELL_SCR_INFO = CELL_SCR + 2
    const val CELL_SCR_RECT = CELL_SCR_INFO + 1
    const val CELL_SCR_CHECK = CELL_SCR_RECT + 3 * SCROLL_MAX
    const val CELL_SCR_TAIL = CELL_SCR_CHECK + 1

    /** Pixels up to the end of the scroll section (what a build 3 addon draws). */
    const val SCR_WIDTH_PX = (CELL_SCR_TAIL + 1) * CELL_W

    // Context section (build 4).
    const val CONTEXT_VERSION = 1

    /** First addon build that has the context section ([WF_CONTEXT_AWARE]). */
    const val CONTEXT_BUILD = 4

    /** Spells per list (enemy list, friend list). */
    const val CONTEXT_SPELLS = 8

    /** Spell entries of the section: 0 until [CONTEXT_SPELLS] the enemy list, then the friend list. */
    const val CONTEXT_ENTRIES = 2 * CONTEXT_SPELLS
    const val CELL_CTX = CELL_SCR_TAIL + 1
    const val CELL_CTX_INFO = CELL_CTX + 2
    const val CELL_CTX_UNIT = CELL_CTX_INFO + 1
    const val CELL_CTX_FOCUS = CELL_CTX_UNIT + 2
    const val CELL_CTX_BAR = CELL_CTX_FOCUS + 2
    const val CELL_CTX_SPELL = CELL_CTX_BAR + 2
    const val CELL_CTX_CHECK = CELL_CTX_SPELL + 3 * CONTEXT_ENTRIES
    const val CELL_CTX_TAIL = CELL_CTX_CHECK + 1

    /** Nibbles of the context payload (cells [CELL_CTX_INFO] to [CELL_CTX_CHECK] - 1, three nibbles each). */
    const val CONTEXT_NIBBLES = 3 * (CELL_CTX_CHECK - CELL_CTX_INFO)

    // Chat section (build 5), second row right behind the context section.
    const val CHAT_VERSION = 1

    /** First addon build that has the chat section. */
    const val CHAT_BUILD = 5
    const val CELL_CHAT = CELL_CTX_TAIL + 1
    const val CELL_CHAT_INFO = CELL_CHAT + 2
    const val CELL_CHAT_RECT = CELL_CHAT_INFO + 1
    const val CELL_CHAT_CHECK = CELL_CHAT_RECT + 3
    const val CELL_CHAT_TAIL = CELL_CHAT_CHECK + 1

    /** Chat flag: the main chat window is shown and not faded out. */
    const val CF_VISIBLE = 1

    /** Chat flag: a chat edit box has the keyboard focus (as [I_CHAT_OPEN]). */
    const val CF_BOX = 2

    /** Cells of the first row (cooldown, window and scroll section: exactly what a build 3 addon draws). */
    const val ROW_CELLS = CELL_SCR_TAIL + 1

    /** All cells of the Lua cell list: the first row, then the context and chat cells of the second row. */
    const val TOTAL_CELLS = CELL_CHAT_TAIL + 1

    /** Width of the first row in pixels (as before the context section). */
    const val WIDTH_PX = ROW_CELLS * CELL_W

    /** Width of the context section in the second row in pixels (what a build 4 addon draws there). */
    const val CTX_WIDTH_PX = (CELL_CTX_TAIL + 1 - CELL_CTX) * CELL_W

    /** Width of the chat section in pixels. */
    const val CHAT_WIDTH_PX = (CELL_CHAT_TAIL + 1 - CELL_CHAT) * CELL_W

    /** Width of the whole second row (context and chat section) in pixels. */
    const val SECOND_ROW_WIDTH_PX = (TOTAL_CELLS - CELL_CTX) * CELL_W

    /** Section flag: more windows were open than the section carries. */
    const val WF_TRUNCATED = 1

    /** Section flag (build 2 and up): the player is in combat. */
    const val WF_COMBAT = 4

    /** Window section flag (build 3 and up): the scroll section follows and is real (clear = unknown, e.g. switched off in the addon). */
    const val WF_SCROLL_AWARE = 8

    /** Window section flag (build 4 and up): the context section follows and is real (clear = unknown). */
    const val WF_CONTEXT_AWARE = 2

    /** Width of the cooldown part alone: the least a decoder needs (an old addon writes no window section). */
    const val BASE_WIDTH_PX = CELL_COUNT * CELL_W

    const val F_FILLED = 1
    const val F_NO_RESOURCE = 2
    const val F_OUT_OF_RANGE = 4
    const val F_GCD = 8

    /** Info flag: the game hid some values (combat restriction); the addon counts known cooldowns down by itself. */
    const val I_SECRET = 1

    /** Info flag: a chat edit box of the game has the keyboard focus (letters typed now go into the chat). */
    const val I_CHAT_OPEN = 2

    /** Info flag: the addon reports [I_CHAT_OPEN]; an older addon never sets it, so a missing [I_CHAT_OPEN] then means "unknown". */
    const val I_CHAT_AWARE = 4

    /** Info flag: the addon writes the window section; an older addon never sets it, so the windows are then unknown. */
    const val I_WINDOWS_AWARE = 8

    /** The chat box according to the info flags: true = open, false = closed, null = the addon does not say. */
    @JvmStatic
    fun chatOpenFrom(infoFlags: Int): Boolean? =
        if (infoFlags and I_CHAT_AWARE == 0) null else infoFlags and I_CHAT_OPEN != 0

    /** The strip may sit a few pixels off the corner; [locate] looks for it in this area. */
    const val SEARCH_X = 8
    const val SEARCH_Y = 4

    /** Pixel columns and rows a decoder needs: [WIDTH_PX] plus the search margin. */
    const val ROW_PIXELS = WIDTH_PX + SEARCH_X

    /** Row length needed for the cooldown part alone. */
    const val BASE_ROW_PIXELS = BASE_WIDTH_PX + SEARCH_X

    /** Pixel rows a decoder needs: the search margin, the first row and the second row (context). */
    const val ROW_COUNT = SEARCH_Y + 2 * CELL_H

    private val HEAD = intArrayOf(0b101, 0b010, 0b001, 0b110)
    private val TAIL = intArrayOf(0b011, 0b100)
    private val WIN_HEAD = intArrayOf(0b100, 0b011)
    private const val WIN_TAIL = 0b111
    private val SCR_HEAD = intArrayOf(0b010, 0b101)
    private const val SCR_TAIL = 0b110
    private val CTX_HEAD = intArrayOf(0b110, 0b001)
    private const val CTX_TAIL = 0b010
    private val CHAT_HEAD = intArrayOf(0b101, 0b110)
    private const val CHAT_TAIL = 0b101

    // ---- slots --------------------------------------------------------------------------------------------

    /** Strip index 0..95 of an action bar [slot] (1..180), -1 for slots the strip does not carry. */
    @JvmStatic
    fun indexOfSlot(slot: Int): Int {
        for (bar in SLOT_FIRST.indices) {
            val first = SLOT_FIRST[bar]
            if (slot in first until first + 12) return bar * 12 + (slot - first)
        }
        return -1
    }

    /** The action bar slot of strip index [index] (0..95). */
    @JvmStatic
    fun slotOfIndex(index: Int): Int = SLOT_FIRST[index / 12] + index % 12

    // ---- value codes (the Lua side has the same formulas) -----------------------------------------------------

    @JvmStatic
    fun remainingFromCode(code: Int): Float = if (code < 3600) code / 10f else 360f + (code - 3600) * 30f

    @JvmStatic
    fun fractionFromCode(code: Int): Float = code / 255f

    @JvmStatic
    fun remainingCode(seconds: Double): Int {
        if (seconds <= 0.0) return 0
        val tenths = maxOf(1, floor(seconds * 10 + 0.5).toInt())
        if (tenths < 3600) return tenths
        return (3600 + floor((seconds - 360) / 30 + 0.5).toInt()).coerceIn(3600, 4095)
    }

    @JvmStatic
    fun fractionCode(remaining: Double, total: Double): Int {
        if (remaining <= 0.0) return 0
        val fraction = if (total > 0.0) minOf(1.0, remaining / total) else 1.0
        return maxOf(1, floor(fraction * 255 + 0.5).toInt())
    }

    @JvmStatic
    fun checksum(nibbles: IntArray, count: Int): Int {
        var c = 0
        for (i in 0 until count) c = (c * 33 + nibbles[i] + 1) % 4093
        return c
    }

    // ---- decoding ---------------------------------------------------------------------------------------------

    /** Result arrays of [decode]; reuse one instance per decoder thread. */
    class Decoded {
        var counter = 0
        var infoFlags = 0
        val remainingCode = IntArray(SLOT_COUNT)
        val fractionCode = IntArray(SLOT_COUNT)
        val flags = IntArray(SLOT_COUNT)

        /** The window section was present and intact (false: older addon, or the section is damaged). */
        var windowsKnown = false

        /** Number of rectangles in [windows] (0..[WIN_MAX]). */
        var windowCount = 0

        /** Section flags ([WF_TRUNCATED], [WF_COMBAT]). */
        var windowFlags = 0

        /**
         * Build number of the addon that wrote this strip: 0 = no window section at all (addon older than fold8.3), -1 = the
         * addon has one but it could not be read this time, otherwise the section version ([ADDON_BUILD] is the bundled one).
         */
        var addonBuild = 0

        /** Rectangles, four codes (left, top, right, bottom; 0..255 of the game picture) per window; [windowKinds] has the kinds. */
        val windows = IntArray(4 * WIN_MAX)
        val windowKinds = IntArray(WIN_MAX)

        /** The scroll section was present and intact (false: older addon, switched off, or the section is damaged). */
        var scrollKnown = false

        /** Number of areas in [scrollAreas] (0..[SCROLL_MAX]). */
        var scrollCount = 0

        /** Scroll section flags ([WF_TRUNCATED]). */
        var scrollFlags = 0

        /** Scroll areas, four codes (left, top, right, bottom; 0..255 of the game picture) each, smallest first; [scrollKinds] has the kinds. */
        val scrollAreas = IntArray(4 * SCROLL_MAX)
        val scrollKinds = IntArray(SCROLL_MAX)

        /** The context section was present and intact (false: older addon, or the section is damaged). */
        var contextKnown = false

        /** The payload of the context section as a nibble list ([CONTEXT_NIBBLES] values 0..15); meaning: ContextWire. */
        val contextNibbles = IntArray(CONTEXT_NIBBLES)

        /** The chat section was present and intact (false: older addon, or the section is damaged). */
        var chatKnown = false

        /** Chat flags ([CF_VISIBLE], [CF_BOX]); 0 while [chatKnown] is false. */
        var chatFlags = 0

        /** The chat rectangle (left, top, right, bottom; 0..255 of the game picture) is in [chatRect]. */
        var chatRectKnown = false
        val chatRect = IntArray(4)

        fun sameAs(other: Decoded): Boolean =
            infoFlags == other.infoFlags && remainingCode.contentEquals(other.remainingCode) &&
                fractionCode.contentEquals(other.fractionCode) && flags.contentEquals(other.flags)

        fun copyFrom(other: Decoded) {
            counter = other.counter
            infoFlags = other.infoFlags
            other.remainingCode.copyInto(remainingCode)
            other.fractionCode.copyInto(fractionCode)
            other.flags.copyInto(flags)
            windowsKnown = other.windowsKnown
            windowCount = other.windowCount
            windowFlags = other.windowFlags
            addonBuild = other.addonBuild
            other.windows.copyInto(windows)
            other.windowKinds.copyInto(windowKinds)
            scrollKnown = other.scrollKnown
            scrollCount = other.scrollCount
            scrollFlags = other.scrollFlags
            other.scrollAreas.copyInto(scrollAreas)
            other.scrollKinds.copyInto(scrollKinds)
            contextKnown = other.contextKnown
            other.contextNibbles.copyInto(contextNibbles)
            chatKnown = other.chatKnown
            chatFlags = other.chatFlags
            chatRectKnown = other.chatRectKnown
            other.chatRect.copyInto(chatRect)
        }

        fun cell(index: Int): CooldownCell {
            val code = remainingCode[index]
            return CooldownCell(remainingFromCode(code), fractionFromCode(fractionCode[index]), flags[index])
        }
    }

    /** Copies row [y] (its first [ROW_PIXELS] pixels) of a frame whose rows are [strideInts] ints apart into [into]. */
    @JvmStatic
    fun readRow(ints: java.nio.IntBuffer, strideInts: Int, y: Int, into: IntArray) {
        ints.position(y * strideInts)
        ints.get(into, 0, ROW_PIXELS)
    }

    /**
     * Finds the strip in [rows] (the first [ROW_COUNT] rows of the frame, each at least [ROW_PIXELS] pixels as ARGB ints):
     * the offset ox + oy * 256 of its top left corner, or -1. Only the sync cells are compared, so this is cheap.
     */
    @JvmStatic
    fun locate(rows: Array<IntArray>): Int {
        for (oy in 0 until SEARCH_Y) {
            for (ox in 0 until SEARCH_X) {
                if (syncMatches(rows[oy], rows[oy + 1], ox)) return ox + oy * 256
            }
        }
        return -1
    }

    /** Both rows must show the sync cells on their own, so a strip is not "found" half a row too high. */
    private fun syncMatches(top: IntArray, bottom: IntArray, ox: Int): Boolean = syncRow(top, ox) && syncRow(bottom, ox)

    private fun syncRow(row: IntArray, ox: Int): Boolean {
        for (i in HEAD.indices) if (!highLow(row, row, ox, i, HEAD[i], 128)) return false
        for (i in TAIL.indices) if (!highLow(row, row, ox, CELL_TAIL + i, TAIL[i], 128)) return false
        return true
    }

    private fun highLow(top: IntArray, bottom: IntArray, ox: Int, cell: Int, pattern: Int, threshold: Int): Boolean {
        val r = sample(top, bottom, ox, cell, 16)
        val g = sample(top, bottom, ox, cell, 8)
        val b = sample(top, bottom, ox, cell, 0)
        return (r >= threshold) == (pattern and 4 != 0) && (g >= threshold) == (pattern and 2 != 0) && (b >= threshold) == (pattern and 1 != 0)
    }

    /** The nibble [shift] (16, 8 or 0) of a cell as the nearest measured calibration level of [cal]. */
    private fun nibbleAt(top: IntArray, bottom: IntArray, ox: Int, cal: IntArray, cell: Int, shift: Int): Int {
        val v = sample(top, bottom, ox, cell, shift)
        var n = 0
        // Nearest measured level: above the midpoint of two neighbours, the higher one.
        while (n < 15 && v * 2 >= cal[n] + cal[n + 1]) n++
        return n
    }

    /** Mean of one channel (shift 16, 8 or 0) over the four inner pixels of a cell. */
    private fun sample(top: IntArray, bottom: IntArray, ox: Int, cell: Int, shift: Int): Int {
        val x = ox + cell * CELL_W + 1
        return (((top[x] shr shift) and 0xFF) + ((top[x + 1] shr shift) and 0xFF) +
            ((bottom[x] shr shift) and 0xFF) + ((bottom[x + 1] shr shift) and 0xFF) + 2) / 4
    }

    /**
     * Decodes the strip whose top left corner is at ([ox], 0) of the two rows [top] and [bottom] into [out]. [scratch] must
     * hold at least 3 + 6 * [SLOT_COUNT] ints. Nothing is written to [out] unless the status is [StripStatus.OK].
     * [contextTop] and [contextBottom] are the two pixel rows right below (the second row, context section); null = not available,
     * the context is then unknown and everything else is read as ever.
     */
    @JvmStatic
    @JvmOverloads
    fun decode(
        top: IntArray,
        bottom: IntArray,
        ox: Int,
        out: Decoded,
        scratch: IntArray,
        contextTop: IntArray? = null,
        contextBottom: IntArray? = null,
    ): StripStatus {
        if (ox < 0 || top.size < ox + BASE_WIDTH_PX || bottom.size < ox + BASE_WIDTH_PX || scratch.size < 3 + 6 * SLOT_COUNT) return StripStatus.NO_SYNC
        // Calibration: measured grey level of each nibble.
        val cal = IntArray(16)
        for (k in 0 until 16) {
            val cell = CELL_CAL + k
            cal[k] = (sample(top, bottom, ox, cell, 16) + sample(top, bottom, ox, cell, 8) + sample(top, bottom, ox, cell, 0)) / 3
        }
        for (k in 0 until 15) if (cal[k + 1] - cal[k] < 2) return StripStatus.BAD_CALIBRATION
        val mid = (cal[0] + cal[15]) / 2
        for (i in HEAD.indices) if (!highLow(top, bottom, ox, i, HEAD[i], mid)) return StripStatus.NO_SYNC
        for (i in TAIL.indices) if (!highLow(top, bottom, ox, CELL_TAIL + i, TAIL[i], mid)) return StripStatus.BAD_TAIL

        fun nibble(cell: Int, shift: Int): Int = nibbleAt(top, bottom, ox, cal, cell, shift)

        var count = 0
        fun put(cell: Int) {
            scratch[count++] = nibble(cell, 16)
            scratch[count++] = nibble(cell, 8)
            scratch[count++] = nibble(cell, 0)
        }
        put(CELL_INFO)
        for (i in 0 until SLOT_COUNT) {
            put(CELL_DATA + 2 * i)
            put(CELL_DATA + 2 * i + 1)
        }
        val check = nibble(CELL_CHECK, 16) * 256 + nibble(CELL_CHECK, 8) * 16 + nibble(CELL_CHECK, 0)
        if (check != checksum(scratch, count)) return StripStatus.BAD_CHECKSUM
        if (scratch[0] != VERSION) return StripStatus.BAD_VERSION

        out.counter = scratch[1]
        out.infoFlags = scratch[2]
        for (i in 0 until SLOT_COUNT) {
            val base = 3 + i * 6
            out.remainingCode[i] = scratch[base] * 256 + scratch[base + 1] * 16 + scratch[base + 2]
            out.fractionCode[i] = scratch[base + 3] * 16 + scratch[base + 4]
            out.flags[i] = scratch[base + 5]
        }
        decodeWindows(top, bottom, ox, mid, ::nibble, out)
        decodeScroll(top, bottom, ox, mid, ::nibble, out)
        decodeContext(contextTop, contextBottom, ox, mid, cal, out)
        decodeChat(contextTop, contextBottom, ox, mid, cal, out)
        return StripStatus.OK
    }

    /**
     * Reads the window section into [out] when the addon says it writes one and the cells are intact; otherwise the windows are
     * unknown (never an error: the cooldown part is decoded either way).
     */
    private fun decodeWindows(top: IntArray, bottom: IntArray, ox: Int, mid: Int, nibble: (Int, Int) -> Int, out: Decoded) {
        out.windowsKnown = false
        out.windowCount = 0
        out.windowFlags = 0
        out.addonBuild = 0
        out.windows.fill(0)
        out.windowKinds.fill(0)
        if (out.infoFlags and I_WINDOWS_AWARE == 0) return
        out.addonBuild = -1
        if (top.size < ox + WIN_WIDTH_PX || bottom.size < ox + WIN_WIDTH_PX) return
        for (i in WIN_HEAD.indices) if (!highLow(top, bottom, ox, CELL_WIN + i, WIN_HEAD[i], mid)) return
        if (!highLow(top, bottom, ox, CELL_WIN_TAIL, WIN_TAIL, mid)) return
        val cells = 1 + 3 * WIN_MAX
        val n = IntArray(3 * cells)
        for (c in 0 until cells) {
            n[3 * c] = nibble(CELL_WIN_INFO + c, 16)
            n[3 * c + 1] = nibble(CELL_WIN_INFO + c, 8)
            n[3 * c + 2] = nibble(CELL_WIN_INFO + c, 0)
        }
        val check = nibble(CELL_WIN_CHECK, 16) * 256 + nibble(CELL_WIN_CHECK, 8) * 16 + nibble(CELL_WIN_CHECK, 0)
        if (check != checksum(n, n.size)) return
        if (n[0] !in 1..15 || n[1] > WIN_MAX) return
        out.addonBuild = n[0]
        out.windowCount = n[1]
        out.windowFlags = n[2]
        for (i in 0 until WIN_MAX) {
            val b = 3 + 9 * i
            for (e in 0 until 4) out.windows[4 * i + e] = n[b + 2 * e] * 16 + n[b + 2 * e + 1]
            out.windowKinds[i] = n[b + 8]
        }
        out.windowsKnown = true
    }

    /**
     * Reads the scroll section into [out] when the window section was read, says it is real ([WF_SCROLL_AWARE]) and the cells are
     * intact; otherwise the scroll areas are unknown (never an error).
     */
    private fun decodeScroll(top: IntArray, bottom: IntArray, ox: Int, mid: Int, nibble: (Int, Int) -> Int, out: Decoded) {
        out.scrollKnown = false
        out.scrollCount = 0
        out.scrollFlags = 0
        out.scrollAreas.fill(0)
        out.scrollKinds.fill(0)
        if (!out.windowsKnown || out.windowFlags and WF_SCROLL_AWARE == 0 || out.addonBuild < SCROLL_BUILD) return
        if (top.size < ox + SCR_WIDTH_PX || bottom.size < ox + SCR_WIDTH_PX) return
        for (i in SCR_HEAD.indices) if (!highLow(top, bottom, ox, CELL_SCR + i, SCR_HEAD[i], mid)) return
        if (!highLow(top, bottom, ox, CELL_SCR_TAIL, SCR_TAIL, mid)) return
        val cells = 1 + 3 * SCROLL_MAX
        val n = IntArray(3 * cells)
        for (c in 0 until cells) {
            n[3 * c] = nibble(CELL_SCR_INFO + c, 16)
            n[3 * c + 1] = nibble(CELL_SCR_INFO + c, 8)
            n[3 * c + 2] = nibble(CELL_SCR_INFO + c, 0)
        }
        val check = nibble(CELL_SCR_CHECK, 16) * 256 + nibble(CELL_SCR_CHECK, 8) * 16 + nibble(CELL_SCR_CHECK, 0)
        if (check != checksum(n, n.size)) return
        if (n[0] != SCROLL_VERSION || n[1] > SCROLL_MAX) return
        out.scrollCount = n[1]
        out.scrollFlags = n[2]
        for (i in 0 until SCROLL_MAX) {
            val b = 3 + 9 * i
            for (e in 0 until 4) out.scrollAreas[4 * i + e] = n[b + 2 * e] * 16 + n[b + 2 * e + 1]
            out.scrollKinds[i] = n[b + 8]
        }
        out.scrollKnown = true
    }

    /**
     * Reads the context section into [out] when the window section was read, says it is real ([WF_CONTEXT_AWARE]) and the cells are
     * intact; otherwise the context is unknown (never an error). Independent of the scroll section (it may be switched off).
     */
    private fun decodeContext(top: IntArray?, bottom: IntArray?, ox: Int, mid: Int, cal: IntArray, out: Decoded) {
        out.contextKnown = false
        out.contextNibbles.fill(0)
        if (top == null || bottom == null) return
        if (!out.windowsKnown || out.windowFlags and WF_CONTEXT_AWARE == 0 || out.addonBuild < CONTEXT_BUILD) return
        if (top.size < ox + CTX_WIDTH_PX || bottom.size < ox + CTX_WIDTH_PX) return
        // The cells stand in the second row from its left edge: cell number minus CELL_CTX.
        fun nibble(cell: Int, shift: Int): Int = nibbleAt(top, bottom, ox, cal, cell - CELL_CTX, shift)
        for (i in CTX_HEAD.indices) if (!highLow(top, bottom, ox, i, CTX_HEAD[i], mid)) return
        if (!highLow(top, bottom, ox, CELL_CTX_TAIL - CELL_CTX, CTX_TAIL, mid)) return
        val cells = CELL_CTX_CHECK - CELL_CTX_INFO
        val n = IntArray(3 * cells)
        for (c in 0 until cells) {
            n[3 * c] = nibble(CELL_CTX_INFO + c, 16)
            n[3 * c + 1] = nibble(CELL_CTX_INFO + c, 8)
            n[3 * c + 2] = nibble(CELL_CTX_INFO + c, 0)
        }
        val check = nibble(CELL_CTX_CHECK, 16) * 256 + nibble(CELL_CTX_CHECK, 8) * 16 + nibble(CELL_CTX_CHECK, 0)
        if (check != checksum(n, n.size)) return
        if (n[0] != CONTEXT_VERSION) return
        n.copyInto(out.contextNibbles)
        out.contextKnown = true
    }

    /**
     * Reads the chat section (second row, right behind the context section) into [out] when its head, tail, checksum and version match;
     * otherwise the chat is unknown (never an error). Independent of the window section.
     */
    private fun decodeChat(top: IntArray?, bottom: IntArray?, ox: Int, mid: Int, cal: IntArray, out: Decoded) {
        out.chatKnown = false
        out.chatFlags = 0
        out.chatRectKnown = false
        out.chatRect.fill(0)
        if (top == null || bottom == null) return
        if (top.size < ox + SECOND_ROW_WIDTH_PX || bottom.size < ox + SECOND_ROW_WIDTH_PX) return
        fun nibble(cell: Int, shift: Int): Int = nibbleAt(top, bottom, ox, cal, cell - CELL_CTX, shift)
        for (i in CHAT_HEAD.indices) if (!highLow(top, bottom, ox, CELL_CHAT - CELL_CTX + i, CHAT_HEAD[i], mid)) return
        if (!highLow(top, bottom, ox, CELL_CHAT_TAIL - CELL_CTX, CHAT_TAIL, mid)) return
        val cells = CELL_CHAT_CHECK - CELL_CHAT_INFO
        val n = IntArray(3 * cells)
        for (c in 0 until cells) {
            n[3 * c] = nibble(CELL_CHAT_INFO + c, 16)
            n[3 * c + 1] = nibble(CELL_CHAT_INFO + c, 8)
            n[3 * c + 2] = nibble(CELL_CHAT_INFO + c, 0)
        }
        val check = nibble(CELL_CHAT_CHECK, 16) * 256 + nibble(CELL_CHAT_CHECK, 8) * 16 + nibble(CELL_CHAT_CHECK, 0)
        if (check != checksum(n, n.size)) return
        if (n[0] != CHAT_VERSION || n[1] > 1) return
        out.chatKnown = true
        out.chatFlags = n[2]
        if (n[1] == 1) {
            for (e in 0 until 4) out.chatRect[e] = n[3 + 2 * e] * 16 + n[3 + 2 * e + 1]
            out.chatRectKnown = true
        }
    }

    // ---- encoding (used by the tests to build strips; the addon does the same in Lua) ------------------------

    /** One slot's input for [encode]. */
    class SlotInput(val remaining: Double, val total: Double, val flags: Int)

    /** One open window for [encode]: the codes 0..255 of left, top, right, bottom (shares of the game picture) and the kind nibble. */
    class WindowInput(val left: Int, val top: Int, val right: Int, val bottom: Int, val kind: Int = 0)

    /**
     * The first row of the strip as ARGB pixels ([WIDTH_PX], to be drawn in [CELL_H] identical pixel rows). [slots] has [SLOT_COUNT] entries, null = empty slot.
     * [contextNibbles] non-null: [WF_CONTEXT_AWARE] is set in the window flags (the second row, [encodeContext], carries the data).
     * [windows] non-null: the window section lists them (at most [WIN_MAX]) and [I_WINDOWS_AWARE] is set; null: the section is
     * written empty and the flag is left as given, which is how an addon without windows looks to the app.
     * [scrollAreas] non-null: the scroll section lists them (at most [SCROLL_MAX], in the order given = smallest first) and
     * [WF_SCROLL_AWARE] is set in the window flags; null: the section is written empty and the flag is not set (an addon of build 2,
     * or, before build 6, swipe scrolling switched off in the addon).
     */
    @JvmStatic
    @JvmOverloads
    fun encode(
        slots: Array<SlotInput?>,
        counter: Int,
        infoFlags: Int,
        windows: List<WindowInput>? = null,
        windowFlags: Int = 0,
        addonBuild: Int = ADDON_BUILD,
        scrollAreas: List<WindowInput>? = null,
        scrollFlags: Int = 0,
        contextNibbles: IntArray? = null,
    ): IntArray {
        val flagsOut = if (windows != null) infoFlags or I_WINDOWS_AWARE else infoFlags
        val cells = IntArray(ROW_CELLS)
        fun rgb(r: Int, g: Int, b: Int) = -0x1000000 or (r shl 16) or (g shl 8) or b
        fun nibbles(a: Int, b: Int, c: Int) = rgb(a * 17, b * 17, c * 17)
        fun pattern(p: Int) = rgb(if (p and 4 != 0) 255 else 0, if (p and 2 != 0) 255 else 0, if (p and 1 != 0) 255 else 0)
        for (i in HEAD.indices) cells[i] = pattern(HEAD[i])
        val sum = IntArray(3 + 6 * SLOT_COUNT)
        var n = 0
        sum[n++] = VERSION
        sum[n++] = counter and 15
        sum[n++] = flagsOut and 15
        cells[CELL_INFO] = nibbles(VERSION, counter and 15, flagsOut and 15)
        for (k in 0 until 16) cells[CELL_CAL + k] = nibbles(k, k, k)
        for (i in 0 until SLOT_COUNT) {
            val s = slots[i]
            val r = if (s == null) 0 else remainingCode(s.remaining)
            val f = if (s == null) 0 else fractionCode(s.remaining, s.total)
            val flags = (s?.flags ?: 0) and 15
            val values = intArrayOf((r shr 8) and 15, (r shr 4) and 15, r and 15, (f shr 4) and 15, f and 15, flags)
            for (v in values) sum[n++] = v
            cells[CELL_DATA + 2 * i] = nibbles(values[0], values[1], values[2])
            cells[CELL_DATA + 2 * i + 1] = nibbles(values[3], values[4], values[5])
        }
        val c = checksum(sum, n)
        cells[CELL_CHECK] = nibbles(c shr 8, (c shr 4) and 15, c and 15)
        for (i in TAIL.indices) cells[CELL_TAIL + i] = pattern(TAIL[i])
        // window section
        for (i in WIN_HEAD.indices) cells[CELL_WIN + i] = pattern(WIN_HEAD[i])
        val list = windows ?: emptyList()
        val count = minOf(list.size, WIN_MAX)
        val wn = IntArray(3 + 9 * WIN_MAX)
        wn[0] = addonBuild and 15
        wn[1] = count
        var wflags = windowFlags
        if (scrollAreas != null) wflags = wflags or WF_SCROLL_AWARE
        if (contextNibbles != null) wflags = wflags or WF_CONTEXT_AWARE
        wn[2] = wflags and 15
        for (i in 0 until count) {
            val w = list[i]
            val b = 3 + 9 * i
            val codes = intArrayOf(w.left, w.top, w.right, w.bottom)
            for (e in 0 until 4) {
                wn[b + 2 * e] = (codes[e] shr 4) and 15
                wn[b + 2 * e + 1] = codes[e] and 15
            }
            wn[b + 8] = w.kind and 15
        }
        for (c in 0..3 * WIN_MAX) cells[CELL_WIN_INFO + c] = nibbles(wn[3 * c], wn[3 * c + 1], wn[3 * c + 2])
        val wc = checksum(wn, wn.size)
        cells[CELL_WIN_CHECK] = nibbles(wc shr 8, (wc shr 4) and 15, wc and 15)
        cells[CELL_WIN_TAIL] = pattern(WIN_TAIL)
        // scroll section
        for (i in SCR_HEAD.indices) cells[CELL_SCR + i] = pattern(SCR_HEAD[i])
        val areas = scrollAreas ?: emptyList()
        val areaCount = minOf(areas.size, SCROLL_MAX)
        val sn = IntArray(3 + 9 * SCROLL_MAX)
        sn[0] = SCROLL_VERSION
        sn[1] = areaCount
        sn[2] = scrollFlags and 15
        for (i in 0 until areaCount) {
            val w = areas[i]
            val b = 3 + 9 * i
            val codes = intArrayOf(w.left, w.top, w.right, w.bottom)
            for (e in 0 until 4) {
                sn[b + 2 * e] = (codes[e] shr 4) and 15
                sn[b + 2 * e + 1] = codes[e] and 15
            }
            sn[b + 8] = w.kind and 15
        }
        for (c in 0..3 * SCROLL_MAX) cells[CELL_SCR_INFO + c] = nibbles(sn[3 * c], sn[3 * c + 1], sn[3 * c + 2])
        val sc = checksum(sn, sn.size)
        cells[CELL_SCR_CHECK] = nibbles(sc shr 8, (sc shr 4) and 15, sc and 15)
        cells[CELL_SCR_TAIL] = pattern(SCR_TAIL)
        val row = IntArray(WIDTH_PX)
        for (cell in 0 until ROW_CELLS) for (px in 0 until CELL_W) row[cell * CELL_W + px] = cells[cell]
        return row
    }

    /**
     * The second row of the strip (context section) as ARGB pixels ([CTX_WIDTH_PX], to be drawn in [CELL_H] identical pixel rows right
     * below the first row, at the same left edge). [contextNibbles] has [CONTEXT_NIBBLES] values; null = an empty payload.
     */
    @JvmStatic
    fun encodeContext(contextNibbles: IntArray?): IntArray {
        fun rgb(r: Int, g: Int, b: Int) = -0x1000000 or (r shl 16) or (g shl 8) or b
        fun nibbles(a: Int, b: Int, c: Int) = rgb(a * 17, b * 17, c * 17)
        fun pattern(p: Int) = rgb(if (p and 4 != 0) 255 else 0, if (p and 2 != 0) 255 else 0, if (p and 1 != 0) 255 else 0)
        val cells = IntArray(CELL_CTX_TAIL + 1 - CELL_CTX)
        for (i in CTX_HEAD.indices) cells[i] = pattern(CTX_HEAD[i])
        val cn = IntArray(CONTEXT_NIBBLES)
        if (contextNibbles != null) for (i in 0 until minOf(contextNibbles.size, CONTEXT_NIBBLES)) cn[i] = contextNibbles[i] and 15
        for (c in 0 until (CELL_CTX_CHECK - CELL_CTX_INFO)) {
            cells[CELL_CTX_INFO - CELL_CTX + c] = nibbles(cn[3 * c], cn[3 * c + 1], cn[3 * c + 2])
        }
        val cc = checksum(cn, cn.size)
        cells[CELL_CTX_CHECK - CELL_CTX] = nibbles(cc shr 8, (cc shr 4) and 15, cc and 15)
        cells[CELL_CTX_TAIL - CELL_CTX] = pattern(CTX_TAIL)
        val row = IntArray(CTX_WIDTH_PX)
        for (cell in cells.indices) for (px in 0 until CELL_W) row[cell * CELL_W + px] = cells[cell]
        return row
    }

    /**
     * The chat section as ARGB pixels ([CHAT_WIDTH_PX], to be drawn in the second row at x = [CTX_WIDTH_PX], right behind the context section).
     * [rect] = left, top, right, bottom codes 0..255, null = no rectangle (count 0). [flags]: [CF_VISIBLE] and [CF_BOX].
     */
    @JvmStatic
    @JvmOverloads
    fun encodeChat(flags: Int, rect: IntArray?, version: Int = CHAT_VERSION): IntArray {
        fun rgb(r: Int, g: Int, b: Int) = -0x1000000 or (r shl 16) or (g shl 8) or b
        fun nibbles(a: Int, b: Int, c: Int) = rgb(a * 17, b * 17, c * 17)
        fun pattern(p: Int) = rgb(if (p and 4 != 0) 255 else 0, if (p and 2 != 0) 255 else 0, if (p and 1 != 0) 255 else 0)
        val cells = IntArray(CELL_CHAT_TAIL + 1 - CELL_CHAT)
        for (i in CHAT_HEAD.indices) cells[i] = pattern(CHAT_HEAD[i])
        val n = IntArray(3 * (CELL_CHAT_CHECK - CELL_CHAT_INFO))
        n[0] = version and 15
        n[1] = if (rect != null) 1 else 0
        n[2] = flags and 15
        if (rect != null) {
            for (e in 0 until 4) {
                n[3 + 2 * e] = (rect[e] shr 4) and 15
                n[3 + 2 * e + 1] = rect[e] and 15
            }
        }
        for (c in 0 until (CELL_CHAT_CHECK - CELL_CHAT_INFO)) cells[CELL_CHAT_INFO - CELL_CHAT + c] = nibbles(n[3 * c], n[3 * c + 1], n[3 * c + 2])
        val cc = checksum(n, n.size)
        cells[CELL_CHAT_CHECK - CELL_CHAT] = nibbles(cc shr 8, (cc shr 4) and 15, cc and 15)
        cells[CELL_CHAT_TAIL - CELL_CHAT] = pattern(CHAT_TAIL)
        val row = IntArray(CHAT_WIDTH_PX)
        for (cell in cells.indices) for (px in 0 until CELL_W) row[cell * CELL_W + px] = cells[cell]
        return row
    }
}

/** Arithmetic of the cooldown picture: sector angles and the remaining time as text. Pure, unit tested. */
object CooldownMath {
    /** Angle (degrees, clockwise from 3 o'clock as Android counts) where the dark sector starts: the lit part grows clockwise from 12 o'clock. */
    @JvmStatic
    fun sweepStart(fraction: Float): Float = -90f + 360f * (1f - fraction.coerceIn(0f, 1f))

    /** Size of the dark sector in degrees. */
    @JvmStatic
    fun sweepAngle(fraction: Float): Float = 360f * fraction.coerceIn(0f, 1f)

    /**
     * Remaining time as short text: tenths below 3 s ("2.4"), whole seconds up to a minute (rounded up), then minutes ("5m")
     * and hours ("2h"), each rounded up. Empty for nothing left.
     */
    @JvmStatic
    fun format(seconds: Float): String {
        if (seconds <= 0f) return ""
        if (seconds < 3f) {
            val tenths = floor(seconds * 10).toInt()
            return "${tenths / 10}.${tenths % 10}"
        }
        if (seconds < 59.5f) return ceil(seconds).toInt().toString()
        if (seconds <= 3540f) return "${ceil(seconds / 60f).toInt()}m"
        return "${ceil(seconds / 3600f).toInt()}h"
    }
}
