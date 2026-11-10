package app.gamenative.cooldown

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.gamenative.ui.screen.wow.context.BarClass
import app.gamenative.ui.screen.wow.context.ContextData
import app.gamenative.ui.screen.wow.context.ContextWire
import app.gamenative.ui.screen.wow.context.FocusKind
import app.gamenative.ui.screen.wow.context.UnitRelation
import app.gamenative.ui.screen.wow.context.UnitSource

class CooldownStripTest {
    /** What the Lua scenarios of the vector file must decode to (checked with the typed reading, ContextWire). */
    private val contextChecks: Map<String, (ContextData) -> Unit> = mapOf<String, (ContextData) -> Unit>(
        "context: nothing" to { d: ContextData ->
            assertTrue(d.keysArmed && d.spellsKnown)
            assertFalse(d.unit.exists)
            assertEquals(FocusKind.NONE, d.focus.kind)
            assertEquals(101, d.enemySpells[0]!!.spellId)
            assertEquals(102, d.enemySpells[1]!!.spellId)
            assertEquals(201, d.friendSpells[0]!!.spellId)
            assertEquals(202, d.friendSpells[1]!!.spellId)
            assertEquals(listOf(BarClass.HARMFUL, BarClass.HELPFUL, BarClass.HARMFUL), d.barClass.take(3))
        },
        "context: hostile mob" to { d: ContextData ->
            assertTrue(d.unit.exists && !d.unit.isPlayer && !d.unit.dead)
            assertEquals(UnitRelation.HOSTILE, d.unit.relation)
            assertEquals(UnitSource.MOUSEOVER, d.unit.source)
            assertEquals(4, d.unit.reach)
            assertTrue(d.unit.isTarget)
            assertEquals(25f, d.enemySpells[0]!!.cooldown, 0.001f)
            assertTrue(d.enemySpells[1]!!.outOfRange && d.enemySpells[1]!!.globalOnly)
            assertTrue(d.friendSpells[0]!!.notUsable)
        },
        "context: dead skinnable mob" to { d: ContextData ->
            assertTrue(d.unit.dead && d.unit.skinnable)
        },
        "context: friendly player" to { d: ContextData ->
            assertTrue(d.unit.isPlayer)
            assertEquals(UnitRelation.FRIEND, d.unit.relation)
            assertEquals(2, d.unit.reach)
            assertFalse(d.unit.inGroup)
        },
        "context: bag sword" to { d: ContextData ->
            assertEquals(FocusKind.BAG, d.focus.kind)
            assertTrue(d.focus.filled && d.focus.equippable && !d.focus.usable && !d.focus.stacked)
        },
        "context: bag potion at the vendor" to { d: ContextData ->
            assertTrue(d.focus.filled && d.focus.usable && d.focus.stacked && d.focus.merchantOpen)
            assertEquals(5, d.focus.count)
        },
        "context: equipment slot" to { d: ContextData ->
            assertEquals(FocusKind.EQUIPMENT, d.focus.kind)
            assertFalse(d.focus.equippable)
        },
        "context: eleven harmful spells" to { d: ContextData ->
            assertTrue(d.ctxFlags and ContextData.TRUNCATED != 0)
            assertEquals(507, d.enemySpells[7]!!.spellId)
            assertTrue(d.friendSpells.all { it == null })
        },
    )

    private val scratch = IntArray(3 + 6 * CooldownStrip.SLOT_COUNT)

    private fun slots(vararg pairs: Pair<Int, CooldownStrip.SlotInput>): Array<CooldownStrip.SlotInput?> {
        val all = arrayOfNulls<CooldownStrip.SlotInput>(CooldownStrip.SLOT_COUNT)
        for (i in all.indices) all[i] = CooldownStrip.SlotInput(0.0, 0.0, CooldownStrip.F_FILLED)
        for ((index, input) in pairs) all[index] = input
        return all
    }

    /** A frame area of ROW_COUNT rows with the strip row [row] drawn at ([ox], [oy]); everything else dark grey. */
    private fun rowsWith(row: IntArray, ox: Int, oy: Int, context: IntArray? = null, chat: IntArray? = null): Array<IntArray> {
        val rows = Array(CooldownStrip.ROW_COUNT) { IntArray(CooldownStrip.ROW_PIXELS) { 0xFF202020.toInt() } }
        for (h in 0 until CooldownStrip.CELL_H) row.copyInto(rows[oy + h], ox)
        // the second row (context section) stands right below the first
        if (context != null) for (h in 0 until CooldownStrip.CELL_H) context.copyInto(rows[oy + CooldownStrip.CELL_H + h], ox)
        // the chat section stands in the same second row right behind the context section
        if (chat != null) for (h in 0 until CooldownStrip.CELL_H) chat.copyInto(rows[oy + CooldownStrip.CELL_H + h], ox + CooldownStrip.CTX_WIDTH_PX)
        return rows
    }

    private fun decodeAt(rows: Array<IntArray>, found: Int): Pair<StripStatus, CooldownStrip.Decoded> {
        val out = CooldownStrip.Decoded()
        val ox = found and 255
        val oy = found shr 8
        return CooldownStrip.decode(
            rows[oy], rows[oy + 1], ox, out, scratch, rows[oy + CooldownStrip.CELL_H], rows[oy + CooldownStrip.CELL_H + 1],
        ) to out
    }

    private fun channel(argb: Int, shift: Int) = (argb shr shift) and 0xFF

    private fun map(row: IntArray, f: (Int, Int) -> Int): IntArray = IntArray(row.size) { i ->
        val p = row[i]
        -0x1000000 or (f(i, channel(p, 16)) shl 16) or (f(i, channel(p, 8)) shl 8) or f(i, channel(p, 0))
    }

    // ---- slots and codes -----------------------------------------------------------------------------------

    @Test
    fun slotIndexMatchesTheBarsOfActionSlotMap() {
        assertEquals(0, CooldownStrip.indexOfSlot(1))
        assertEquals(11, CooldownStrip.indexOfSlot(12))
        assertEquals(12, CooldownStrip.indexOfSlot(25))
        assertEquals(59, CooldownStrip.indexOfSlot(72))
        assertEquals(60, CooldownStrip.indexOfSlot(145))
        assertEquals(95, CooldownStrip.indexOfSlot(180))
        for (slot in listOf(0, 13, 24, 73, 120, 144, 181)) assertEquals("slot $slot", -1, CooldownStrip.indexOfSlot(slot))
        for (index in 0 until CooldownStrip.SLOT_COUNT) {
            assertEquals(index, CooldownStrip.indexOfSlot(CooldownStrip.slotOfIndex(index)))
        }
        // The bars of the catalog: ACTIONBUTTON1..12, MULTIACTIONBAR1..7BUTTON1..12 all have a cell.
        for (command in listOf("ACTIONBUTTON7", "MULTIACTIONBAR1BUTTON1", "MULTIACTIONBAR3BUTTON12", "MULTIACTIONBAR7BUTTON12")) {
            val slot = app.gamenative.wowicons.ActionSlotMap.slotFor(command)!!
            assertTrue(command, CooldownStrip.indexOfSlot(slot) >= 0)
        }
    }

    @Test
    fun remainingCodeRoundTripsWithTheStatedResolution() {
        assertEquals(0, CooldownStrip.remainingCode(0.0))
        assertEquals(1, CooldownStrip.remainingCode(0.04))
        assertEquals(125, CooldownStrip.remainingCode(12.5))
        assertEquals(12.5f, CooldownStrip.remainingFromCode(125), 0f)
        assertEquals(3599, CooldownStrip.remainingCode(359.94))
        assertEquals(3600, CooldownStrip.remainingCode(360.0))
        assertEquals(3618, CooldownStrip.remainingCode(900.0))
        assertEquals(900f, CooldownStrip.remainingFromCode(3618), 0f)
        assertEquals(4095, CooldownStrip.remainingCode(1e7))
        for (seconds in listOf(0.1, 1.55, 29.9, 150.0, 359.9)) {
            assertEquals(seconds, CooldownStrip.remainingFromCode(CooldownStrip.remainingCode(seconds)).toDouble(), 0.051)
        }
        for (seconds in listOf(400.0, 900.0, 1800.0, 7200.0, 15000.0)) {
            assertEquals(seconds, CooldownStrip.remainingFromCode(CooldownStrip.remainingCode(seconds)).toDouble(), 15.01)
        }
    }

    @Test
    fun fractionCode() {
        assertEquals(0, CooldownStrip.fractionCode(0.0, 30.0))
        assertEquals(128, CooldownStrip.fractionCode(15.0, 30.0))
        assertEquals(1, CooldownStrip.fractionCode(0.001, 30.0))
        assertEquals(255, CooldownStrip.fractionCode(40.0, 30.0))
        assertEquals(255, CooldownStrip.fractionCode(5.0, 0.0))
        assertEquals(0.5f, CooldownStrip.fractionFromCode(128), 0.01f)
    }

    // ---- round trip --------------------------------------------------------------------------------------------

    @Test
    fun encodeDecodeRoundTrip() {
        val row = CooldownStrip.encode(
            slots(
                0 to CooldownStrip.SlotInput(12.5, 30.0, CooldownStrip.F_FILLED),
                17 to CooldownStrip.SlotInput(1.0, 1.5, CooldownStrip.F_FILLED or CooldownStrip.F_GCD),
                60 to CooldownStrip.SlotInput(800.0, 900.0, CooldownStrip.F_FILLED or CooldownStrip.F_OUT_OF_RANGE or CooldownStrip.F_NO_RESOURCE),
                95 to CooldownStrip.SlotInput(0.0, 0.0, 0),
            ),
            counter = 11,
            infoFlags = CooldownStrip.I_SECRET,
        )
        assertEquals(CooldownStrip.WIDTH_PX, row.size)
        val rows = rowsWith(row, 0, 0)
        val found = CooldownStrip.locate(rows)
        assertEquals(0, found)
        val (status, out) = decodeAt(rows, found)
        assertEquals(StripStatus.OK, status)
        assertEquals(11, out.counter)
        assertEquals(CooldownStrip.I_SECRET, out.infoFlags)
        val first = out.cell(0)
        assertEquals(12.5f, first.remaining, 0f)
        assertEquals(12.5f / 30f, first.fraction, 0.005f)
        assertTrue(first.filled && first.cooling && !first.globalOnly && !first.outOfRange)
        val gcd = out.cell(17)
        assertTrue(gcd.globalOnly && gcd.cooling)
        val far = out.cell(60)
        assertTrue(far.outOfRange && far.noResource)
        assertEquals(800f, far.remaining, 15f)
        val idle = out.cell(1)
        assertTrue(idle.filled && !idle.cooling)
        assertFalse(out.cell(95).filled)
    }

    @Test
    fun theStripIsFoundWhenItSitsAFewPixelsOff() {
        val row = CooldownStrip.encode(slots(5 to CooldownStrip.SlotInput(3.3, 10.0, CooldownStrip.F_FILLED)), 2, 0)
        for ((ox, oy) in listOf(0 to 0, 3 to 1, 7 to 3, 1 to 2)) {
            val rows = rowsWith(row, ox, oy)
            val found = CooldownStrip.locate(rows)
            // The sampling tolerates one pixel, so the search may stop one column before the true corner.
            assertEquals("row of offset $ox/$oy", oy, found shr 8)
            assertTrue("column of offset $ox/$oy", Math.abs((found and 255) - ox) <= 1)
            val (status, out) = decodeAt(rows, found)
            assertEquals(StripStatus.OK, status)
            assertEquals(3.3f, out.cell(5).remaining, 0.001f)
        }
    }

    @Test
    fun aShiftOfOnePixelAgainstTheCellGridStillDecodes() {
        // Decoding at an offset one pixel off the true position reads the same cells (inner columns of 4).
        val row = CooldownStrip.encode(slots(4 to CooldownStrip.SlotInput(7.0, 14.0, CooldownStrip.F_FILLED)), 3, 0)
        val rows = rowsWith(row, 2, 0)
        for (guess in listOf(1, 2, 3)) {
            val out = CooldownStrip.Decoded()
            val status = CooldownStrip.decode(rows[0], rows[1], guess, out, scratch)
            assertEquals("guess $guess", StripStatus.OK, status)
            assertEquals(7.0f, out.cell(4).remaining, 0.001f)
        }
    }

    @Test
    fun emptyAreaIsNotAStrip() {
        val rows = Array(CooldownStrip.ROW_COUNT) { IntArray(CooldownStrip.ROW_PIXELS) { 0xFF404040.toInt() } }
        assertEquals(-1, CooldownStrip.locate(rows))
        val out = CooldownStrip.Decoded()
        assertTrue(CooldownStrip.decode(rows[0], rows[1], 0, out, scratch) != StripStatus.OK)
    }

    // ---- robustness ------------------------------------------------------------------------------------------

    @Test
    fun colourNoiseWithinTheStepDoesNotMatter() {
        val row = CooldownStrip.encode(slots(9 to CooldownStrip.SlotInput(42.0, 60.0, CooldownStrip.F_FILLED)), 7, 0)
        var seed = 12345L
        fun noise(): Int {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33) % 9).toInt() - 4 // -4..+4
        }
        // Calibration and data cells with +-4 per channel: the sample and the measured level of the ramp each err by at most 4,
        // together below half a step (8.5), so every nibble must come out right.
        val noisy = map(row) { i, v ->
            val cell = i / CooldownStrip.CELL_W
            if (cell < 4 || cell >= CooldownStrip.CELL_TAIL) v else (v + noise()).coerceIn(0, 255)
        }
        val rows = rowsWith(noisy, 0, 0)
        val (status, out) = decodeAt(rows, CooldownStrip.locate(rows))
        assertEquals(StripStatus.OK, status)
        assertEquals(42.0f, out.cell(9).remaining, 0.001f)
    }

    @Test
    fun monotonicColourCurvesAreCalibratedAway() {
        val row = CooldownStrip.encode(slots(30 to CooldownStrip.SlotInput(96.5, 120.0, CooldownStrip.F_FILLED or CooldownStrip.F_OUT_OF_RANGE)), 14, 0)
        // Brightening (gamma 0.5) and mild darkening (gamma 1.5): all 16 levels stay distinguishable and the ramp tells how.
        for (gamma in listOf(0.5, 0.8, 1.3, 1.5)) {
            val curved = map(row) { _, v -> (255.0 * (v / 255.0).pow(gamma)).roundToInt() }
            val rows = rowsWith(curved, 0, 0)
            val found = CooldownStrip.locate(rows)
            assertTrue("gamma $gamma found", found >= 0)
            val (status, out) = decodeAt(rows, found)
            assertEquals("gamma $gamma", StripStatus.OK, status)
            assertEquals(96.5f, out.cell(30).remaining, 0.001f)
            assertTrue(out.cell(30).outOfRange)
        }
    }

    @Test
    fun aDamagedStripIsRejectedNotMisread() {
        val row = CooldownStrip.encode(slots(2 to CooldownStrip.SlotInput(5.0, 10.0, CooldownStrip.F_FILLED)), 1, 0)
        // A data cell painted over (as by another window or a different scale): checksum fails.
        val cell = CooldownStrip.CELL_DATA + 7
        val broken = row.copyOf()
        for (px in 0 until CooldownStrip.CELL_W) broken[cell * CooldownStrip.CELL_W + px] = 0xFFFFFFFF.toInt()
        val rows = rowsWith(broken, 0, 0)
        val (status, _) = decodeAt(rows, CooldownStrip.locate(rows))
        assertEquals(StripStatus.BAD_CHECKSUM, status)

        // The tail is missing (strip cut off or scaled): not even located.
        val cut = row.copyOf()
        for (px in 0 until CooldownStrip.CELL_W * 2) cut[CooldownStrip.CELL_TAIL * CooldownStrip.CELL_W + px] = 0xFF000000.toInt()
        val cutRows = rowsWith(cut, 0, 0)
        assertEquals(-1, CooldownStrip.locate(cutRows))
        val out = CooldownStrip.Decoded()
        assertEquals(StripStatus.BAD_TAIL, CooldownStrip.decode(cutRows[0], cutRows[1], 0, out, scratch))

        // A flat ramp (e.g. a one colour area that happens to carry the sync colours) is no calibration.
        val flat = row.copyOf()
        for (k in 0 until 16) for (px in 0 until CooldownStrip.CELL_W) flat[(CooldownStrip.CELL_CAL + k) * CooldownStrip.CELL_W + px] = 0xFF808080.toInt()
        val flatRows = rowsWith(flat, 0, 0)
        assertEquals(StripStatus.BAD_CALIBRATION, CooldownStrip.decode(flatRows[0], flatRows[1], 0, out, scratch))
    }

    @Test
    fun decodedDataIsNotTouchedByAFailedRead() {
        val row = CooldownStrip.encode(slots(1 to CooldownStrip.SlotInput(5.0, 10.0, CooldownStrip.F_FILLED)), 1, 0)
        val rows = rowsWith(row, 0, 0)
        val (_, good) = decodeAt(rows, 0)
        val broken = row.copyOf()
        broken[(CooldownStrip.CELL_DATA + 1) * CooldownStrip.CELL_W + 1] = 0xFFFFFFFF.toInt()
        broken[(CooldownStrip.CELL_DATA + 1) * CooldownStrip.CELL_W + 2] = 0xFFFFFFFF.toInt()
        val brokenRows = rowsWith(broken, 0, 0)
        val keep = CooldownStrip.Decoded().also { it.copyFrom(good) }
        val status = CooldownStrip.decode(brokenRows[0], brokenRows[1], 0, keep, scratch)
        assertEquals(StripStatus.BAD_CHECKSUM, status)
        assertTrue(keep.sameAs(good))
    }

    // ---- the window section --------------------------------------------------------------------------------

    private fun win(left: Int, top: Int, right: Int, bottom: Int, kind: Int = 0) = CooldownStrip.WindowInput(left, top, right, bottom, kind)

    private fun decodeRow(row: IntArray, context: IntArray? = null, chat: IntArray? = null): Pair<StripStatus, CooldownStrip.Decoded> {
        val rows = rowsWith(row, 0, 0, context, chat)
        val found = CooldownStrip.locate(rows)
        assertEquals(0, found)
        return decodeAt(rows, found)
    }

    @Test
    fun theWindowsOfTheSectionRoundTrip() {
        val list = listOf(win(13, 23, 238, 238, 2), win(175, 129, 241, 245, 1), win(0, 0, 255, 255, 15), win(1, 2, 3, 4, 5))
        val row = CooldownStrip.encode(
            slots(3 to CooldownStrip.SlotInput(9.0, 18.0, CooldownStrip.F_FILLED)), 6, CooldownStrip.I_SECRET, list, CooldownStrip.WF_TRUNCATED,
        )
        assertEquals(CooldownStrip.ROW_CELLS * CooldownStrip.CELL_W, row.size) // encode liefert nur Zeile 1 (Kontext in Zeile 2)
        val (status, out) = decodeRow(row)
        assertEquals(StripStatus.OK, status)
        assertTrue(out.windowsKnown)
        assertEquals(4, out.windowCount)
        assertEquals(CooldownStrip.WF_TRUNCATED, out.windowFlags)
        for ((i, w) in list.withIndex()) {
            assertEquals(listOf(w.left, w.top, w.right, w.bottom), (0 until 4).map { out.windows[4 * i + it] })
            assertEquals(w.kind, out.windowKinds[i])
        }
        // the flag is set by the encoder, the other info flags and the cooldown part are as always
        assertEquals(CooldownStrip.I_SECRET or CooldownStrip.I_WINDOWS_AWARE, out.infoFlags)
        assertEquals(9.0f, out.cell(3).remaining, 0.001f)
        assertEquals(6, out.counter)
    }

    @Test
    fun fewerWindowsLeaveTheRestZero() {
        val (_, out) = decodeRow(CooldownStrip.encode(slots(), 1, 0, listOf(win(10, 20, 30, 40, 3))))
        assertTrue(out.windowsKnown)
        assertEquals(1, out.windowCount)
        assertEquals(listOf(10, 20, 30, 40), (0 until 4).map { out.windows[it] })
        assertEquals(List(12) { 0 }, (4 until 16).map { out.windows[it] })
    }

    @Test
    fun noWindowOpenIsKnownAndEmpty() {
        val (status, out) = decodeRow(CooldownStrip.encode(slots(), 1, 0, emptyList()))
        assertEquals(StripStatus.OK, status)
        assertTrue(out.windowsKnown)
        assertEquals(0, out.windowCount)
    }

    @Test
    fun moreThanFourWindowsAreCutAtTheEncoder() {
        val many = List(6) { win(it, it, 100 + it, 100 + it, it) }
        val (_, out) = decodeRow(CooldownStrip.encode(slots(), 1, 0, many))
        assertEquals(CooldownStrip.WIN_MAX, out.windowCount)
    }

    @Test
    fun anAddonWithoutTheFlagHasUnknownWindowsEvenIfCellsFollow() {
        // Windows null: the section cells are written (empty) but the flag is not set: an older addon never sets it.
        val (status, out) = decodeRow(CooldownStrip.encode(slots(1 to CooldownStrip.SlotInput(4.0, 8.0, CooldownStrip.F_FILLED)), 2, 0))
        assertEquals(StripStatus.OK, status)
        assertFalse(out.windowsKnown)
        assertEquals(0, out.windowCount)
        assertEquals(0, out.infoFlags and CooldownStrip.I_WINDOWS_AWARE)
        assertEquals(4.0f, out.cell(1).remaining, 0.001f)
    }

    @Test
    fun theStripOfAnOlderAddonWithoutSectionDecodesAsBefore() {
        // An addon of fold8.2 draws only the 216 cells; whatever the game shows right of them is no section.
        val full = CooldownStrip.encode(slots(5 to CooldownStrip.SlotInput(30.0, 60.0, CooldownStrip.F_FILLED)), 9, CooldownStrip.I_CHAT_AWARE)
        val old = full.copyOf(CooldownStrip.BASE_WIDTH_PX)
        val rows = rowsWith(old, 3, 1)
        val found = CooldownStrip.locate(rows)
        assertEquals(1, found shr 8)
        val (status, out) = decodeAt(rows, found)
        assertEquals(StripStatus.OK, status)
        assertFalse(out.windowsKnown)
        assertEquals(30.0f, out.cell(5).remaining, 0.001f)
        assertEquals(CooldownStrip.I_CHAT_AWARE, out.infoFlags)
        assertEquals(false, CooldownStrip.chatOpenFrom(out.infoFlags))
    }

    @Test
    fun aRowThatEndsBehindTheCooldownPartStillDecodesTheCooldowns() {
        // The flag is set but the buffer is cut right after cell 215 (very narrow picture): windows unknown, cooldowns fine.
        val full = CooldownStrip.encode(slots(2 to CooldownStrip.SlotInput(3.0, 6.0, CooldownStrip.F_FILLED)), 4, 0, listOf(win(1, 2, 3, 4)))
        val top = full.copyOf(CooldownStrip.BASE_WIDTH_PX)
        val out = CooldownStrip.Decoded()
        assertEquals(StripStatus.OK, CooldownStrip.decode(top, top, 0, out, scratch))
        assertFalse(out.windowsKnown)
        assertEquals(3.0f, out.cell(2).remaining, 0.001f)
    }

    @Test
    fun theBasePartDoesNotDependOnTheWindows() {
        // New addon and old app: the old app reads cells 0..215 only; they must not change with the window content.
        val input = slots(7 to CooldownStrip.SlotInput(2.0, 4.0, CooldownStrip.F_FILLED))
        val a = CooldownStrip.encode(input, 3, 0, listOf(win(1, 2, 3, 4, 5)))
        val b = CooldownStrip.encode(input, 3, 0, listOf(win(200, 100, 250, 220, 9), win(9, 9, 99, 99, 1)))
        val baseEnd = CooldownStrip.BASE_WIDTH_PX
        assertEquals(CooldownStrip.CELL_COUNT * CooldownStrip.CELL_W, baseEnd)
        assertTrue((0 until baseEnd).all { a[it] == b[it] })
        // the windows differ
        assertTrue((baseEnd until a.size).any { a[it] != b[it] })
        // the info flag is the only difference to a section-less strip in the cooldown part
        val plain = CooldownStrip.encode(input, 3, CooldownStrip.I_WINDOWS_AWARE)
        assertTrue((0 until baseEnd).all { a[it] == plain[it] })
    }

    @Test
    fun aDamagedWindowSectionMakesOnlyTheWindowsUnknown() {
        val good = CooldownStrip.encode(slots(1 to CooldownStrip.SlotInput(5.0, 10.0, CooldownStrip.F_FILLED)), 1, 0, listOf(win(10, 20, 30, 40, 1)))
        fun painted(cell: Int, argb: Int): IntArray {
            val row = good.copyOf()
            for (px in 0 until CooldownStrip.CELL_W) row[cell * CooldownStrip.CELL_W + px] = argb
            return row
        }
        val white = 0xFFFFFFFF.toInt()
        val damaged = mapOf(
            "rectangle cell" to painted(CooldownStrip.CELL_WIN_RECT + 1, white),
            "info cell" to painted(CooldownStrip.CELL_WIN_INFO, white),
            "checksum cell" to painted(CooldownStrip.CELL_WIN_CHECK, white),
            "sync head" to painted(CooldownStrip.CELL_WIN, white),
            "sync tail" to painted(CooldownStrip.CELL_WIN_TAIL, 0xFF000000.toInt()),
        )
        for ((name, row) in damaged) {
            val (status, out) = decodeRow(row)
            assertEquals(name, StripStatus.OK, status)
            assertFalse(name, out.windowsKnown)
            assertEquals(name, 0, out.windowCount)
            assertEquals(name, 5.0f, out.cell(1).remaining, 0.001f)
        }
    }

    @Test
    fun aWindowSectionOfAnotherVersionIsIgnored() {
        // Version nibble 0 with a valid checksum: a section this app does not know (1 to 15 are builds of the addon).
        val forged = CooldownStrip.encode(slots(), 1, 0, listOf(win(10, 20, 30, 40)))
        val n = IntArray(3 + 9 * CooldownStrip.WIN_MAX)
        n[0] = 0
        n[1] = 1
        fun rgb(a: Int, b: Int, c: Int) = -0x1000000 or ((a * 17) shl 16) or ((b * 17) shl 8) or (c * 17)
        for (c in 0..3 * CooldownStrip.WIN_MAX) {
            val argb = rgb(n[3 * c], n[3 * c + 1], n[3 * c + 2])
            for (px in 0 until CooldownStrip.CELL_W) forged[(CooldownStrip.CELL_WIN_INFO + c) * CooldownStrip.CELL_W + px] = argb
        }
        val sum = CooldownStrip.checksum(n, n.size)
        val check = rgb(sum shr 8, (sum shr 4) and 15, sum and 15)
        for (px in 0 until CooldownStrip.CELL_W) forged[CooldownStrip.CELL_WIN_CHECK * CooldownStrip.CELL_W + px] = check
        val (status, out) = decodeRow(forged)
        assertEquals(StripStatus.OK, status)
        assertFalse(out.windowsKnown)
    }

    @Test
    fun theSectionVersionIsTheBuildOfTheAddon() {
        val (_, current) = decodeRow(CooldownStrip.encode(slots(), 1, 0, listOf(win(10, 20, 30, 40))))
        assertEquals(CooldownStrip.ADDON_BUILD, current.addonBuild)
        // fold8.3 / fold8.4: the same section with version 1, still read (windows known), but an older build
        val (_, first) = decodeRow(CooldownStrip.encode(slots(), 1, 0, listOf(win(10, 20, 30, 40)), addonBuild = 1))
        assertTrue(first.windowsKnown)
        assertEquals(1, first.addonBuild)
        assertEquals(1, first.windowCount)
        // an addon without the window section (before fold8.3): build 0
        val (_, none) = decodeRow(CooldownStrip.encode(slots(), 1, 0))
        assertFalse(none.windowsKnown)
        assertEquals(0, none.addonBuild)
        // the flag says there is a section but it is damaged: unknown (-1), not "old"
        val damaged = CooldownStrip.encode(slots(), 1, 0, listOf(win(10, 20, 30, 40)))
        damaged[CooldownStrip.CELL_WIN_CHECK * CooldownStrip.CELL_W + 1] = 0xFFFFFFFF.toInt()
        damaged[CooldownStrip.CELL_WIN_CHECK * CooldownStrip.CELL_W + 2] = 0xFFFFFFFF.toInt()
        val (status, bad) = decodeRow(damaged)
        assertEquals(StripStatus.OK, status)
        assertFalse(bad.windowsKnown)
        assertEquals(-1, bad.addonBuild)
        // a failed read does not change the build of the last good one
        assertTrue(CooldownStrip.RELOAD_KEY_BUILD <= CooldownStrip.ADDON_BUILD)
    }

    @Test
    fun theCombatFlagComesFromTheSectionFlags() {
        val (_, calm) = decodeRow(CooldownStrip.encode(slots(), 1, 0, emptyList()))
        assertEquals(0, calm.windowFlags and CooldownStrip.WF_COMBAT)
        val (_, fight) = decodeRow(CooldownStrip.encode(slots(), 1, 0, emptyList(), windowFlags = CooldownStrip.WF_COMBAT))
        assertEquals(CooldownStrip.WF_COMBAT, fight.windowFlags and CooldownStrip.WF_COMBAT)
        assertEquals(0, fight.windowFlags and CooldownStrip.WF_TRUNCATED)
    }

    @Test
    fun theWindowSectionSurvivesColourCurves() {
        val row = CooldownStrip.encode(slots(), 5, 0, listOf(win(13, 23, 238, 238, 2), win(175, 129, 241, 245, 1)))
        for (gamma in listOf(0.6, 1.0, 1.4)) {
            val curved = map(row) { _, v -> (255.0 * (v / 255.0).pow(gamma)).roundToInt() }
            val (status, out) = decodeRow(curved)
            assertEquals("gamma $gamma", StripStatus.OK, status)
            assertTrue("gamma $gamma", out.windowsKnown)
            assertEquals("gamma $gamma", listOf(175, 129, 241, 245), (0 until 4).map { out.windows[4 + it] })
        }
    }

    @Test
    fun aFailedReadLeavesTheDecodedWindowsAlone() {
        val row = CooldownStrip.encode(slots(), 1, 0, listOf(win(10, 20, 30, 40, 1)))
        val (_, good) = decodeRow(row)
        val broken = row.copyOf()
        broken[(CooldownStrip.CELL_DATA + 1) * CooldownStrip.CELL_W + 1] = 0xFFFFFFFF.toInt()
        broken[(CooldownStrip.CELL_DATA + 1) * CooldownStrip.CELL_W + 2] = 0xFFFFFFFF.toInt()
        val keep = CooldownStrip.Decoded().also { it.copyFrom(good) }
        val rows = rowsWith(broken, 0, 0)
        assertEquals(StripStatus.BAD_CHECKSUM, CooldownStrip.decode(rows[0], rows[1], 0, keep, scratch))
        assertTrue(keep.windowsKnown)
        assertEquals(1, keep.windowCount)
        assertEquals(listOf(10, 20, 30, 40), (0 until 4).map { keep.windows[it] })
    }

    // ---- the scroll section (build 3) ------------------------------------------------------------------------

    private fun encodeScroll(areas: List<CooldownStrip.WindowInput>?, scrollFlags: Int = 0, windows: List<CooldownStrip.WindowInput> = listOf(win(10, 20, 30, 40))) =
        CooldownStrip.encode(
            slots(1 to CooldownStrip.SlotInput(5.0, 10.0, CooldownStrip.F_FILLED)), 3, 0, windows,
            scrollAreas = areas, scrollFlags = scrollFlags,
        )

    @Test
    fun theLayoutOfTheScrollSection() {
        assertEquals(233, CooldownStrip.CELL_SCR)
        assertEquals(235, CooldownStrip.CELL_SCR_INFO)
        assertEquals(236, CooldownStrip.CELL_SCR_RECT)
        assertEquals(254, CooldownStrip.CELL_SCR_CHECK)
        assertEquals(255, CooldownStrip.CELL_SCR_TAIL)
        assertEquals(1024, CooldownStrip.SCR_WIDTH_PX)
        // the first row is exactly the one of build 3; the context cells are no part of it
        assertEquals(256, CooldownStrip.ROW_CELLS)
        assertEquals(1024, CooldownStrip.WIDTH_PX)
        assertEquals(323, CooldownStrip.TOTAL_CELLS)
        assertEquals(236, CooldownStrip.CTX_WIDTH_PX)
        assertEquals(268, CooldownStrip.SECOND_ROW_WIDTH_PX)
        assertEquals(1032, CooldownStrip.ROW_PIXELS)
        assertEquals(CooldownStrip.SEARCH_Y + 2 * CooldownStrip.CELL_H, CooldownStrip.ROW_COUNT)
        assertEquals((CooldownStrip.CELL_WIN_TAIL + 1) * CooldownStrip.CELL_W, CooldownStrip.WIN_WIDTH_PX)
        assertTrue(CooldownStrip.SCROLL_BUILD <= CooldownStrip.ADDON_BUILD)
        // the smallest game picture the launcher offers (1280 x 720) still holds the strip and its search margin
        assertTrue(CooldownStrip.ROW_PIXELS <= 1280)
    }

    @Test
    fun theScrollAreasRoundTripInTheGivenOrder() {
        val areas = listOf(win(63, 122, 125, 172, 2), win(40, 76, 185, 202, 1), win(0, 0, 255, 255, 3))
        val (status, out) = decodeRow(encodeScroll(areas, scrollFlags = CooldownStrip.WF_TRUNCATED))
        assertEquals(StripStatus.OK, status)
        assertTrue(out.windowsKnown)
        assertTrue(out.windowFlags and CooldownStrip.WF_SCROLL_AWARE != 0)
        assertTrue(out.scrollKnown)
        assertEquals(3, out.scrollCount)
        assertEquals(CooldownStrip.WF_TRUNCATED, out.scrollFlags)
        for ((i, a) in areas.withIndex()) {
            assertEquals(listOf(a.left, a.top, a.right, a.bottom), (0 until 4).map { out.scrollAreas[4 * i + it] })
            assertEquals(a.kind, out.scrollKinds[i])
        }
        // the window part is unaffected
        assertEquals(1, out.windowCount)
        assertEquals(listOf(10, 20, 30, 40), (0 until 4).map { out.windows[it] })
        assertEquals(5.0f, out.cell(1).remaining, 0.001f)
    }

    @Test
    fun sixAreasFitAndMoreAreCutAtTheEncoder() {
        val six = List(6) { win(10 * it, 5, 10 * it + 50, 200, 1) }
        val (_, out6) = decodeRow(encodeScroll(six))
        assertEquals(6, out6.scrollCount)
        assertEquals(listOf(50, 5, 100, 200), (0 until 4).map { out6.scrollAreas[20 + it] })
        val (_, out8) = decodeRow(encodeScroll(six + win(1, 1, 2, 2) + win(3, 3, 4, 4)))
        assertEquals(CooldownStrip.SCROLL_MAX, out8.scrollCount)
    }

    @Test
    fun noScrollListOpenIsKnownAndEmpty() {
        val (_, out) = decodeRow(encodeScroll(emptyList()))
        assertTrue(out.scrollKnown)
        assertEquals(0, out.scrollCount)
        assertTrue(out.windowsKnown)
    }

    @Test
    fun withoutTheFlagTheScrollAreasAreUnknown() {
        // Build 3 with swipe scrolling switched off (or an addon of build 2): the section cells are empty and the flag is clear.
        val (_, out) = decodeRow(encodeScroll(null))
        assertTrue(out.windowsKnown)
        assertFalse(out.scrollKnown)
        assertEquals(0, out.scrollCount)
        assertEquals(0, out.windowFlags and CooldownStrip.WF_SCROLL_AWARE)
    }

    @Test
    fun anAddonOfBuild2StopsAfterTheWindowSection() {
        // A build 2 addon draws 233 cells; whatever the game shows right of them (here: noise) is no scroll section.
        val full = encodeScroll(listOf(win(1, 2, 3, 4)))
        val old = full.copyOf(CooldownStrip.WIN_WIDTH_PX)
        val noise = IntArray(CooldownStrip.WIDTH_PX) { if (it < old.size) old[it] else 0xFF802040.toInt() }
        for (row in listOf(old, noise)) {
            val (status, out) = decodeRow(row)
            assertEquals(StripStatus.OK, status)
            assertTrue(out.windowsKnown)
            assertEquals(1, out.windowCount)
            assertFalse(out.scrollKnown)
            assertEquals(0, out.scrollCount)
        }
        // the old addon with the section version 2 and no scroll flag: flag clear, so the noise is not read even though it is there
        val (_, b2) = decodeRow(CooldownStrip.encode(slots(), 1, 0, listOf(win(1, 2, 3, 4)), addonBuild = 2))
        assertFalse(b2.scrollKnown)
        assertEquals(2, b2.addonBuild)
    }

    @Test
    fun aForgedScrollFlagOnAnOldBuildIsIgnored() {
        // The flag is only valid from build 3 on (the cells right of an old addon's window section are game picture).
        val row = CooldownStrip.encode(
            slots(), 1, 0, listOf(win(1, 2, 3, 4)), windowFlags = CooldownStrip.WF_SCROLL_AWARE, addonBuild = 2,
            scrollAreas = listOf(win(5, 6, 7, 8)),
        )
        val (_, out) = decodeRow(row)
        assertTrue(out.windowsKnown)
        assertFalse(out.scrollKnown)
    }

    @Test
    fun aDamagedScrollSectionMakesOnlyTheScrollAreasUnknown() {
        val good = encodeScroll(listOf(win(10, 20, 30, 40, 1)))
        fun painted(cell: Int, argb: Int): IntArray {
            val row = good.copyOf()
            for (px in 0 until CooldownStrip.CELL_W) row[cell * CooldownStrip.CELL_W + px] = argb
            return row
        }
        val white = 0xFFFFFFFF.toInt()
        val damaged = mapOf(
            "rectangle cell" to painted(CooldownStrip.CELL_SCR_RECT + 1, white),
            "info cell" to painted(CooldownStrip.CELL_SCR_INFO, white),
            "checksum cell" to painted(CooldownStrip.CELL_SCR_CHECK, white),
            "sync head" to painted(CooldownStrip.CELL_SCR, white),
            "sync tail" to painted(CooldownStrip.CELL_SCR_TAIL, 0xFF000000.toInt()),
        )
        for ((name, row) in damaged) {
            val (status, out) = decodeRow(row)
            assertEquals(name, StripStatus.OK, status)
            assertFalse(name, out.scrollKnown)
            assertEquals(name, 0, out.scrollCount)
            // windows and cooldowns are still read
            assertTrue(name, out.windowsKnown)
            assertEquals(name, 1, out.windowCount)
            assertEquals(name, 5.0f, out.cell(1).remaining, 0.001f)
        }
    }

    @Test
    fun aDamagedWindowSectionTakesTheScrollAreasWithIt() {
        val row = encodeScroll(listOf(win(10, 20, 30, 40, 1)))
        row[CooldownStrip.CELL_WIN_CHECK * CooldownStrip.CELL_W + 1] = 0xFFFFFFFF.toInt()
        row[CooldownStrip.CELL_WIN_CHECK * CooldownStrip.CELL_W + 2] = 0xFFFFFFFF.toInt()
        val (status, out) = decodeRow(row)
        assertEquals(StripStatus.OK, status)
        assertFalse(out.windowsKnown)
        assertFalse(out.scrollKnown)
    }

    @Test
    fun theBasePartAndTheWindowSectionDoNotDependOnTheScrollAreas() {
        // New addon and old app: the old app reads cells 0..215 (windows: 0..232) only; the scroll areas must not change them.
        val a = encodeScroll(listOf(win(1, 2, 3, 4, 1)))
        val b = encodeScroll(listOf(win(200, 100, 250, 220, 3), win(9, 9, 99, 99, 2)))
        // the window flag WF_SCROLL_AWARE is set in both, so the window section is identical too
        assertTrue((0 until CooldownStrip.WIN_WIDTH_PX).all { a[it] == b[it] })
        assertTrue((CooldownStrip.WIN_WIDTH_PX until a.size).any { a[it] != b[it] })
    }

    @Test
    fun theScrollSectionSurvivesColourCurves() {
        val row = encodeScroll(listOf(win(13, 23, 238, 238, 2), win(175, 129, 241, 245, 1)))
        for (gamma in listOf(0.6, 1.0, 1.4)) {
            val curved = map(row) { _, v -> (255.0 * (v / 255.0).pow(gamma)).roundToInt() }
            val (status, out) = decodeRow(curved)
            assertEquals("gamma $gamma", StripStatus.OK, status)
            assertTrue("gamma $gamma", out.scrollKnown)
            assertEquals("gamma $gamma", listOf(175, 129, 241, 245), (0 until 4).map { out.scrollAreas[4 + it] })
        }
    }

    @Test
    fun theScrollAreasAreCopiedWithTheDecodedState() {
        val (_, out) = decodeRow(encodeScroll(listOf(win(1, 2, 3, 4, 3))))
        val copy = CooldownStrip.Decoded().also { it.copyFrom(out) }
        assertTrue(copy.scrollKnown)
        assertEquals(1, copy.scrollCount)
        assertEquals(listOf(1, 2, 3, 4), (0 until 4).map { copy.scrollAreas[it] })
        assertEquals(3, copy.scrollKinds[0])
    }

    // ---- the context section (build 4) -----------------------------------------------------------------------

    private fun sampleContext(): ContextData = ContextData(
        ctxFlags = ContextData.KEYS or ContextData.SPELLS,
        unit = app.gamenative.ui.screen.wow.context.UnitInfo(
            exists = true, isPlayer = true, relation = UnitRelation.FRIEND, source = UnitSource.MOUSEOVER, reach = 3, inGroup = true,
            reaction = 5, hasTarget = true, isTarget = true,
        ),
        focus = app.gamenative.ui.screen.wow.context.FocusInfo(kind = FocusKind.BANK, filled = true, usable = true, stacked = true, count = 9),
        barClass = List(12) { it % 4 },
        entries = List(CooldownStrip.CONTEXT_ENTRIES) { i ->
            if (i % 3 == 0) app.gamenative.ui.screen.wow.context.SpellEntry(1_000_000 + i, 1 or (i % 2) * 4, i * 5f) else null
        },
    )

    /** The first row (cooldowns, windows, scroll areas, the context flag) and the second row (context section). */
    private class Strip(val row: IntArray, val context: IntArray)

    private fun encodeContext(
        nibbles: IntArray? = ContextWire.encode(sampleContext()),
        addonBuild: Int = CooldownStrip.ADDON_BUILD,
        scroll: List<CooldownStrip.WindowInput>? = listOf(win(1, 2, 3, 4)),
    ) = Strip(
        CooldownStrip.encode(
            slots(1 to CooldownStrip.SlotInput(5.0, 10.0, CooldownStrip.F_FILLED)), 3, 0, listOf(win(10, 20, 30, 40)),
            addonBuild = addonBuild, scrollAreas = scroll, contextNibbles = nibbles,
        ),
        CooldownStrip.encodeContext(nibbles),
    )

    private fun decodeStrip(strip: Strip) = decodeRow(strip.row, strip.context)

    @Test
    fun theLayoutOfTheContextSection() {
        assertEquals(256, CooldownStrip.CELL_CTX)
        assertEquals(258, CooldownStrip.CELL_CTX_INFO)
        assertEquals(259, CooldownStrip.CELL_CTX_UNIT)
        assertEquals(261, CooldownStrip.CELL_CTX_FOCUS)
        assertEquals(263, CooldownStrip.CELL_CTX_BAR)
        assertEquals(265, CooldownStrip.CELL_CTX_SPELL)
        assertEquals(313, CooldownStrip.CELL_CTX_CHECK)
        assertEquals(314, CooldownStrip.CELL_CTX_TAIL)
        assertEquals(165, CooldownStrip.CONTEXT_NIBBLES)
        assertEquals(CooldownStrip.CONTEXT_NIBBLES, ContextWire.IDX_ENTRIES + ContextWire.ENTRY_NIBBLES * CooldownStrip.CONTEXT_ENTRIES)
        assertEquals(2, CooldownStrip.WF_CONTEXT_AWARE)
        assertEquals(4, CooldownStrip.CONTEXT_BUILD)
        assertTrue(CooldownStrip.CONTEXT_BUILD <= CooldownStrip.ADDON_BUILD)
        // the flag does not clash with the other window flags
        assertEquals(0, CooldownStrip.WF_CONTEXT_AWARE and (CooldownStrip.WF_TRUNCATED or CooldownStrip.WF_COMBAT or CooldownStrip.WF_SCROLL_AWARE))
        // the second row is no wider than the first, and the first is as wide as before: a squarish window (about 1010 px wide at the
        // smallest resolution) must not lose the strip because of the context section
        assertEquals(1024, CooldownStrip.WIDTH_PX)
        assertTrue(CooldownStrip.CTX_WIDTH_PX <= CooldownStrip.WIDTH_PX)
        assertTrue(CooldownStrip.SECOND_ROW_WIDTH_PX <= CooldownStrip.WIDTH_PX)
        assertEquals(CooldownStrip.SCR_WIDTH_PX, CooldownStrip.WIDTH_PX)
        assertEquals(2, CooldownStrip.CELL_H)
    }

    @Test
    fun theContextRoundTrips() {
        val (status, out) = decodeStrip(encodeContext())
        assertEquals(StripStatus.OK, status)
        assertTrue(out.contextKnown)
        assertTrue(out.windowFlags and CooldownStrip.WF_CONTEXT_AWARE != 0)
        assertEquals(sampleContext(), ContextWire.decode(out.contextNibbles))
        // the other sections are not disturbed
        assertTrue(out.windowsKnown && out.scrollKnown)
        assertEquals(1, out.scrollCount)
        assertEquals(5.0f, out.cell(1).remaining, 0.001f)
    }

    @Test
    fun theContextIsIndependentOfTheScrollSection() {
        val (_, out) = decodeStrip(encodeContext(scroll = null))
        assertTrue(out.contextKnown)
        assertFalse(out.scrollKnown)
    }

    @Test
    fun anAddonWithoutContextHasUnknownContext() {
        val (_, none) = decodeStrip(encodeContext(nibbles = null))
        assertFalse(none.contextKnown)
        assertEquals(0, none.windowFlags and CooldownStrip.WF_CONTEXT_AWARE)
        // build 3: the first row is the whole strip; whatever stands below it is game picture (here: noise)
        val full = encodeContext()
        val noise = IntArray(CooldownStrip.CTX_WIDTH_PX) { 0xFF802040.toInt() }
        for (context in listOf(null, noise)) {
            val (status, out) = decodeRow(full.row, context)
            assertEquals(StripStatus.OK, status)
            assertTrue(out.windowsKnown)
            assertTrue(out.scrollKnown)
            assertFalse(out.contextKnown)
        }
        // a second row that is too short makes the context unknown, the rest is read
        val (shortStatus, short) = decodeRow(full.row, full.context.copyOf(CooldownStrip.CTX_WIDTH_PX - 8))
        assertEquals(StripStatus.OK, shortStatus)
        assertFalse(short.contextKnown)
        assertTrue(short.scrollKnown)
        // a forged flag on an old build is ignored
        val (_, forged) = decodeStrip(encodeContext(addonBuild = 3))
        assertFalse(forged.contextKnown)
        assertEquals(3, forged.addonBuild)
        // the old reader (cooldown part only, no window section) still works with the new strip
        val base = full.row.copyOf(CooldownStrip.BASE_WIDTH_PX)
        val rows = rowsWith(base, 0, 0)
        val (status, out) = decodeAt(rows, CooldownStrip.locate(rows))
        assertEquals(StripStatus.OK, status)
        assertEquals(5.0f, out.cell(1).remaining, 0.001f)
    }

    @Test
    fun aDamagedContextSectionMakesOnlyTheContextUnknown() {
        val good = encodeContext()
        // the second row with one cell (numbered as in the Lua cell list) painted over
        fun painted(cell: Int, argb: Int): Strip {
            val row = good.context.copyOf()
            for (px in 0 until CooldownStrip.CELL_W) row[(cell - CooldownStrip.CELL_CTX) * CooldownStrip.CELL_W + px] = argb
            return Strip(good.row, row)
        }
        val white = 0xFFFFFFFF.toInt()
        val damaged = mapOf(
            "unit cell" to painted(CooldownStrip.CELL_CTX_UNIT, white),
            "focus cell" to painted(CooldownStrip.CELL_CTX_FOCUS, white),
            "spell cell" to painted(CooldownStrip.CELL_CTX_SPELL + 5, white),
            "info cell" to painted(CooldownStrip.CELL_CTX_INFO, white),
            "checksum cell" to painted(CooldownStrip.CELL_CTX_CHECK, white),
            "sync head" to painted(CooldownStrip.CELL_CTX, white),
            "sync tail" to painted(CooldownStrip.CELL_CTX_TAIL, 0xFF000000.toInt()),
        )
        for ((name, strip) in damaged) {
            val (status, out) = decodeStrip(strip)
            assertEquals(name, StripStatus.OK, status)
            assertFalse(name, out.contextKnown)
            assertTrue(name, out.contextNibbles.all { it == 0 })
            // windows, scroll areas and cooldowns are still read
            assertTrue(name, out.windowsKnown)
            assertTrue(name, out.scrollKnown)
            assertEquals(name, 5.0f, out.cell(1).remaining, 0.001f)
        }
    }

    @Test
    fun theContextSurvivesColourCurvesAndIsCopied() {
        val strip = encodeContext()
        for (gamma in listOf(0.6, 1.0, 1.4)) {
            val curved = Strip(
                map(strip.row) { _, v -> (255.0 * (v / 255.0).pow(gamma)).roundToInt() },
                map(strip.context) { _, v -> (255.0 * (v / 255.0).pow(gamma)).roundToInt() },
            )
            val (status, out) = decodeStrip(curved)
            assertEquals("gamma $gamma", StripStatus.OK, status)
            assertTrue("gamma $gamma", out.contextKnown)
            assertEquals("gamma $gamma", sampleContext(), ContextWire.decode(out.contextNibbles))
        }
        val (_, out) = decodeStrip(strip)
        val copy = CooldownStrip.Decoded().also { it.copyFrom(out) }
        assertTrue(copy.contextKnown)
        assertEquals(out.contextNibbles.toList(), copy.contextNibbles.toList())
    }

    // ---- the way the service reads the game buffer ---------------------------------------------------------

    @Test
    fun readsTheStripOutOfABgraBufferWithPadding() {
        val strideInts = 1300
        val height = 20
        val bytes = ByteBuffer.allocateDirect(strideInts * height * 4)
        for (i in 0 until strideInts * height) bytes.put(i * 4, 0x10).put(i * 4 + 1, 0x20).put(i * 4 + 2, 0x30).put(i * 4 + 3, 0)
        val row = CooldownStrip.encode(slots(8 to CooldownStrip.SlotInput(20.0, 40.0, CooldownStrip.F_FILLED)), 5, 0)
        val ox = 2
        val oy = 1
        for (h in 0 until CooldownStrip.CELL_H) {
            for (x in row.indices) {
                val argb = row[x]
                val o = ((oy + h) * strideInts + ox + x) * 4
                bytes.put(o, (argb and 0xFF).toByte())
                bytes.put(o + 1, ((argb shr 8) and 0xFF).toByte())
                bytes.put(o + 2, ((argb shr 16) and 0xFF).toByte())
                bytes.put(o + 3, 0) // depth-24 game picture: undefined alpha
            }
        }
        val ints = FrameAccess.intView(bytes.order(ByteOrder.LITTLE_ENDIAN))
        val rows = Array(CooldownStrip.ROW_COUNT) { IntArray(CooldownStrip.ROW_PIXELS) }
        for (y in 0 until CooldownStrip.ROW_COUNT) CooldownStrip.readRow(ints, strideInts, y, rows[y])
        val found = CooldownStrip.locate(rows)
        assertEquals(oy, found shr 8)
        assertTrue(Math.abs((found and 255) - ox) <= 1)
        val (status, out) = decodeAt(rows, found)
        assertEquals(StripStatus.OK, status)
        assertEquals(20.0f, out.cell(8).remaining, 0.001f)
        assertEquals(5, out.counter)
    }

    // ---- the vectors the Lua file of the addon produced -----------------------------------------------------

    @Test
    fun decodesWhatTheLuaAddonDraws() {
        val stream = javaClass.getResourceAsStream("/cooldown/cooldown_strip_vectors.txt")
        assertNotNull("vector file (tools/cooldown_strip_check.py --write-vectors)", stream)
        val lines = stream!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.size >= 4)
        for (line in lines) {
            val parts = line.split('|')
            assertEquals(line.take(40), 9, parts.size)
            val name = parts[0]
            val counter = parts[1].toInt()
            val infoFlags = parts[2].toInt()
            val expected = HashMap<Int, Triple<Int, Int, Int>>()
            for (entry in parts[3].split(';').filter { it.isNotEmpty() }) {
                val f = entry.split(':').map { it.toInt() }
                expected[f[0]] = Triple(f[1], f[2], f[3])
            }
            val hex = parts[4]
            assertEquals(CooldownStrip.TOTAL_CELLS * 6, hex.length)
            // the first ROW_CELLS cells make the first row, the rest the second row (context section) right below it
            val row = IntArray(CooldownStrip.WIDTH_PX)
            val contextRow = IntArray(CooldownStrip.SECOND_ROW_WIDTH_PX)
            for (cell in 0 until CooldownStrip.TOTAL_CELLS) {
                val argb = -0x1000000 or hex.substring(cell * 6, cell * 6 + 6).toInt(16)
                val target = if (cell < CooldownStrip.ROW_CELLS) row else contextRow
                val index = if (cell < CooldownStrip.ROW_CELLS) cell else cell - CooldownStrip.ROW_CELLS
                for (px in 0 until CooldownStrip.CELL_W) target[index * CooldownStrip.CELL_W + px] = argb
            }
            val rows = rowsWith(row, 0, 0, contextRow)
            val found = CooldownStrip.locate(rows)
            assertEquals(name, 0, found)
            val (status, out) = decodeAt(rows, found)
            assertEquals(name, StripStatus.OK, status)
            assertEquals(name, counter, out.counter)
            assertEquals(name, infoFlags, out.infoFlags)
            for (i in 0 until CooldownStrip.SLOT_COUNT) {
                val want = expected[i] ?: Triple(0, 0, 0)
                assertEquals("$name slot index $i", want, Triple(out.remainingCode[i], out.fractionCode[i], out.flags[i]))
            }
            // windows: "-" = the addon wrote no window section; else "flags;left:top:right:bottom:kind;..."
            val windows = parts[5]
            if (windows == "-") {
                assertFalse("$name windows", out.windowsKnown)
                assertEquals(0, out.addonBuild)
                assertEquals(0, out.infoFlags and CooldownStrip.I_WINDOWS_AWARE)
            } else {
                val fields = windows.split(';')
                assertTrue("$name windows known", out.windowsKnown)
                assertEquals("$name addon build", CooldownStrip.ADDON_BUILD, out.addonBuild)
                assertTrue(out.infoFlags and CooldownStrip.I_WINDOWS_AWARE != 0)
                assertEquals("$name window flags", fields[0].toInt(), out.windowFlags)
                assertEquals("$name window count", fields.size - 1, out.windowCount)
                for ((i, entry) in fields.drop(1).withIndex()) {
                    val f = entry.split(':').map { it.toInt() }
                    assertEquals("$name window $i", f.take(4), (0 until 4).map { out.windows[4 * i + it] })
                    assertEquals("$name window $i kind", f[4], out.windowKinds[i])
                }
            }
            // scroll areas: "-" = no scroll section was announced (the window flag WF_SCROLL_AWARE is clear); else "flags;l:t:r:b:kind;..."
            val scroll = parts[6]
            if (scroll == "-") {
                assertFalse("$name scroll", out.scrollKnown)
                assertEquals("$name scroll count", 0, out.scrollCount)
                assertEquals("$name scroll flag", 0, out.windowFlags and CooldownStrip.WF_SCROLL_AWARE)
            } else {
                val fields = scroll.split(';')
                assertTrue("$name scroll known", out.scrollKnown)
                assertTrue("$name scroll flag", out.windowFlags and CooldownStrip.WF_SCROLL_AWARE != 0)
                assertEquals("$name scroll flags", fields[0].toInt(), out.scrollFlags)
                assertEquals("$name scroll count", fields.size - 1, out.scrollCount)
                for ((i, entry) in fields.drop(1).withIndex()) {
                    val f = entry.split(':').map { it.toInt() }
                    assertEquals("$name scroll area $i", f.take(4), (0 until 4).map { out.scrollAreas[4 * i + it] })
                    assertEquals("$name scroll area $i kind", f[4], out.scrollKinds[i])
                }
            }
            // context: "-" = no context section announced (the window flag WF_CONTEXT_AWARE is clear); else the 165 payload nibbles as hex
            val context = parts[7]
            if (context == "-") {
                assertFalse("$name context", out.contextKnown)
                assertEquals("$name context flag", 0, if (out.windowsKnown) out.windowFlags and CooldownStrip.WF_CONTEXT_AWARE else 0)
            } else {
                assertEquals(CooldownStrip.CONTEXT_NIBBLES, context.length)
                assertTrue("$name context known", out.contextKnown)
                assertTrue("$name context flag", out.windowFlags and CooldownStrip.WF_CONTEXT_AWARE != 0)
                assertEquals("$name context nibbles", context.map { it.digitToInt(16) }, out.contextNibbles.toList())
                // the typed reading of the nibbles the Lua file wrote
                assertNotNull("$name context decodes", ContextWire.decode(out.contextNibbles))
                contextChecks[name]?.invoke(ContextWire.decode(out.contextNibbles)!!)
            }
            // chat (build 5): "flags;left:top:right:bottom" or "flags;-" (no rectangle)
            val chatFields = parts[8].split(';')
            assertTrue("$name chat known", out.chatKnown)
            assertEquals("$name chat flags", chatFields[0].toInt(), out.chatFlags)
            if (chatFields[1] == "-") {
                assertFalse("$name chat rectangle", out.chatRectKnown)
            } else {
                assertTrue("$name chat rectangle", out.chatRectKnown)
                assertEquals("$name chat rectangle", chatFields[1].split(':').map { it.toInt() }, out.chatRect.toList())
            }
        }
        // the vectors cover the cases the window section must handle (and, below, the context section)
        val names = lines.map { it.substringBefore('|') }
        for (needed in listOf(
            "windows none", "bags open", "map and bags", "five windows", "windows unavailable",
            "character and bags", "professions and bags", "maximised map", "combat", "combat over",
            "scroll: reputation list", "scroll: settings two lists", "scroll: quest log", "scroll: nested lists",
            "scroll: nothing to scroll here", "scroll: message frame", "scroll: eight lists", "scroll: old switch value false",
            "context: nothing", "context: hostile mob", "context: dead skinnable mob", "context: friendly player", "context: bag sword",
            "context: bag potion at the vendor", "context: equipment slot", "context: eleven harmful spells", "context: file missing",
            "chat: no window", "chat: window visible, box closed", "chat: box open below the window", "chat: box open above the window",
            "chat: faded out", "chat: place unknown", "chat: window hidden",
        )) {
            assertTrue("vector $needed", needed in names)
        }
    }


    // ---- the chat section (build 5) ---------------------------------------------------------------------------

    private fun chatStrip() = CooldownStrip.encode(
        slots(1 to CooldownStrip.SlotInput(5.0, 10.0, CooldownStrip.F_FILLED)), 3, 0, listOf(win(10, 20, 30, 40)),
        scrollAreas = listOf(win(1, 2, 3, 4)), contextNibbles = ContextWire.encode(sampleContext()),
    )

    /** The whole second row: context section, then the chat section right behind it. */
    private fun secondRow(chat: IntArray?): IntArray {
        val second = IntArray(CooldownStrip.SECOND_ROW_WIDTH_PX) { 0xFF202020.toInt() }
        CooldownStrip.encodeContext(ContextWire.encode(sampleContext())).copyInto(second, 0)
        chat?.copyInto(second, CooldownStrip.CTX_WIDTH_PX)
        return second
    }

    @Test
    fun theLayoutOfTheChatSection() {
        assertEquals(315, CooldownStrip.CELL_CHAT)
        assertEquals(317, CooldownStrip.CELL_CHAT_INFO)
        assertEquals(318, CooldownStrip.CELL_CHAT_RECT)
        assertEquals(321, CooldownStrip.CELL_CHAT_CHECK)
        assertEquals(322, CooldownStrip.CELL_CHAT_TAIL)
        assertEquals(1, CooldownStrip.CHAT_VERSION)
        assertEquals(5, CooldownStrip.CHAT_BUILD)
        assertTrue(CooldownStrip.CHAT_BUILD <= CooldownStrip.ADDON_BUILD)
        assertEquals(1, CooldownStrip.CF_VISIBLE)
        assertEquals(2, CooldownStrip.CF_BOX)
        // the chat section stands right behind the context section, in the same second row
        assertEquals(CooldownStrip.CELL_CTX_TAIL + 1, CooldownStrip.CELL_CHAT)
        assertEquals(8 * CooldownStrip.CELL_W, CooldownStrip.CHAT_WIDTH_PX)
        assertEquals(CooldownStrip.CTX_WIDTH_PX + CooldownStrip.CHAT_WIDTH_PX, CooldownStrip.SECOND_ROW_WIDTH_PX)
        assertEquals(CooldownStrip.CHAT_WIDTH_PX, CooldownStrip.encodeChat(0, null).size)
    }

    @Test
    fun theChatSectionRoundTrips() {
        val cases = listOf(
            CooldownStrip.CF_VISIBLE to intArrayOf(5, 100, 120, 200),
            (CooldownStrip.CF_VISIBLE or CooldownStrip.CF_BOX) to intArrayOf(0, 0, 255, 255),
            CooldownStrip.CF_BOX to intArrayOf(17, 34, 187, 238),
            0 to null,
            CooldownStrip.CF_VISIBLE to null,
        )
        for ((flags, rect) in cases) {
            val (status, out) = decodeRow(chatStrip(), CooldownStrip.encodeContext(ContextWire.encode(sampleContext())), CooldownStrip.encodeChat(flags, rect))
            assertEquals(StripStatus.OK, status)
            assertTrue(out.chatKnown)
            assertEquals(flags, out.chatFlags)
            assertEquals(rect != null, out.chatRectKnown)
            assertEquals((rect ?: intArrayOf(0, 0, 0, 0)).toList(), out.chatRect.toList())
            // the other sections are not disturbed
            assertTrue(out.windowsKnown && out.scrollKnown && out.contextKnown)
            assertEquals(5.0f, out.cell(1).remaining, 0.001f)
            assertEquals(sampleContext(), ContextWire.decode(out.contextNibbles))
        }
    }

    @Test
    fun theChatSectionIsIndependentOfTheOtherSections() {
        // no window list, no context: the chat is still read (it announces itself by head, tail and checksum)
        val row = CooldownStrip.encode(slots(), 1, 0)
        val (status, out) = decodeRow(row, null, CooldownStrip.encodeChat(CooldownStrip.CF_VISIBLE, intArrayOf(1, 2, 3, 4)))
        assertEquals(StripStatus.OK, status)
        assertFalse(out.windowsKnown)
        assertFalse(out.contextKnown)
        assertTrue(out.chatKnown)
        assertEquals(listOf(1, 2, 3, 4), out.chatRect.toList())
    }

    @Test
    fun anAddonWithoutTheChatSectionHasAnUnknownChat() {
        // build 4: the context section is there, behind it stands the game picture (here: grey, noise)
        val (_, grey) = decodeRow(chatStrip(), CooldownStrip.encodeContext(ContextWire.encode(sampleContext())), null)
        assertFalse(grey.chatKnown)
        assertEquals(0, grey.chatFlags)
        assertFalse(grey.chatRectKnown)
        assertTrue(grey.contextKnown)
        val noise = IntArray(CooldownStrip.CHAT_WIDTH_PX) { 0xFF802040.toInt() }
        val (_, noisy) = decodeRow(chatStrip(), CooldownStrip.encodeContext(ContextWire.encode(sampleContext())), noise)
        assertFalse(noisy.chatKnown)
        // no second row at all (build 3 and older)
        val (status, none) = decodeRow(chatStrip(), null, null)
        assertEquals(StripStatus.OK, status)
        assertFalse(none.chatKnown)
        // a section of another version is not read
        val (_, forged) = decodeRow(chatStrip(), null, CooldownStrip.encodeChat(CooldownStrip.CF_VISIBLE, intArrayOf(1, 2, 3, 4), version = 2))
        assertFalse(forged.chatKnown)
        // the old reader (first row only) still works
        val base = chatStrip().copyOf(CooldownStrip.BASE_WIDTH_PX)
        val rows = rowsWith(base, 0, 0)
        val (baseStatus, baseOut) = decodeAt(rows, CooldownStrip.locate(rows))
        assertEquals(StripStatus.OK, baseStatus)
        assertFalse(baseOut.chatKnown)
    }

    @Test
    fun aDamagedChatSectionMakesOnlyTheChatUnknown() {
        val good = secondRow(CooldownStrip.encodeChat(CooldownStrip.CF_VISIBLE or CooldownStrip.CF_BOX, intArrayOf(10, 20, 200, 220)))
        fun painted(cell: Int, argb: Int): IntArray {
            val second = good.copyOf()
            for (px in 0 until CooldownStrip.CELL_W) second[(cell - CooldownStrip.CELL_CTX) * CooldownStrip.CELL_W + px] = argb
            return second
        }
        val white = 0xFFFFFFFF.toInt()
        val damaged = mapOf(
            "sync head" to painted(CooldownStrip.CELL_CHAT, 0xFF000000.toInt()),
            "info cell" to painted(CooldownStrip.CELL_CHAT_INFO, white),
            "rectangle cell" to painted(CooldownStrip.CELL_CHAT_RECT + 1, white),
            "checksum cell" to painted(CooldownStrip.CELL_CHAT_CHECK, white),
            "sync tail" to painted(CooldownStrip.CELL_CHAT_TAIL, 0xFF000000.toInt()),
        )
        val (_, ok) = decodeRow(chatStrip(), good, null)
        assertTrue(ok.chatKnown && ok.contextKnown)
        for ((name, second) in damaged) {
            val (status, out) = decodeRow(chatStrip(), second, null)
            assertEquals(name, StripStatus.OK, status)
            assertFalse(name, out.chatKnown)
            assertEquals(name, 0, out.chatFlags)
            assertFalse(name, out.chatRectKnown)
            assertTrue("$name: context still read", out.contextKnown)
            assertTrue("$name: windows still read", out.windowsKnown)
            assertEquals(5.0f, out.cell(1).remaining, 0.001f)
        }
    }

    @Test
    fun decodedCopiesTheChat() {
        val (_, out) = decodeRow(chatStrip(), null, CooldownStrip.encodeChat(CooldownStrip.CF_BOX, intArrayOf(9, 8, 7, 6)))
        val copy = CooldownStrip.Decoded()
        copy.copyFrom(out)
        assertTrue(copy.chatKnown && copy.chatRectKnown)
        assertEquals(CooldownStrip.CF_BOX, copy.chatFlags)
        assertEquals(listOf(9, 8, 7, 6), copy.chatRect.toList())
    }

    @Test
    fun theLuaFileAgreesWithTheConstantsHere() {
        val dir = File("src/main/assets/wow/addon/TouchForever").takeIf { it.isDirectory }
            ?: File("app/src/main/assets/wow/addon/TouchForever")
        if (!dir.isDirectory) return // run from an unexpected working directory
        val lua = File(dir, "TouchForeverCooldown.lua").readText()
        assertFalse("LF only", lua.contains("\r"))
        assertTrue(lua.contains("S.SLOT_FIRST = { 1, 25, 37, 49, 61, 145, 157, 169 }"))
        assertTrue(lua.contains("S.CELL_W, S.CELL_H = ${CooldownStrip.CELL_W}, ${CooldownStrip.CELL_H}"))
        assertTrue(lua.contains("S.CELL_INFO, S.CELL_CAL, S.CELL_DATA = ${CooldownStrip.CELL_INFO}, ${CooldownStrip.CELL_CAL}, ${CooldownStrip.CELL_DATA}"))
        assertTrue(lua.contains("S.VERSION = ${CooldownStrip.VERSION}"))
        assertTrue(lua.contains("S.I_SECRET = ${CooldownStrip.I_SECRET}"))
        assertTrue(lua.contains("S.I_CHAT_OPEN = ${CooldownStrip.I_CHAT_OPEN}"))
        assertTrue(lua.contains("S.I_CHAT_AWARE = ${CooldownStrip.I_CHAT_AWARE}"))
        assertTrue(lua.contains("S.I_WINDOWS_AWARE = ${CooldownStrip.I_WINDOWS_AWARE}"))
        assertTrue(lua.contains("S.WIN_VERSION = ${CooldownStrip.ADDON_BUILD}"))
        assertTrue(
            lua.contains(
                "S.WF_TRUNCATED, S.WF_COMBAT, S.WF_SCROLL_AWARE = ${CooldownStrip.WF_TRUNCATED}, ${CooldownStrip.WF_COMBAT}, ${CooldownStrip.WF_SCROLL_AWARE}",
            ),
        )
        assertTrue(lua.contains("S.WF_CONTEXT_AWARE = ${CooldownStrip.WF_CONTEXT_AWARE}"))
        assertTrue(lua.contains("S.CONTEXT_VERSION = ${CooldownStrip.CONTEXT_VERSION}"))
        assertTrue(lua.contains("S.CONTEXT_ENTRIES = ${CooldownStrip.CONTEXT_ENTRIES}"))
        assertTrue(lua.contains("local CTX_HEAD = { { 255, 255, 0 }, { 0, 0, 255 } }"))
        assertTrue(lua.contains("local CTX_TAIL = { 0, 255, 0 }"))
        assertTrue(lua.contains("S.CHAT_VERSION = ${CooldownStrip.CHAT_VERSION}"))
        assertTrue(lua.contains("S.CHAT_BUILD = ${CooldownStrip.CHAT_BUILD}"))
        assertTrue(lua.contains("S.CF_VISIBLE, S.CF_BOX = ${CooldownStrip.CF_VISIBLE}, ${CooldownStrip.CF_BOX}"))
        assertTrue(lua.contains("local CHAT_HEAD = { { 255, 0, 255 }, { 255, 255, 0 } }"))
        assertTrue(lua.contains("local CHAT_TAIL = { 255, 0, 255 }"))
        assertTrue(lua.contains("S.CELL_CHAT = S.CELL_CTX_TAIL + 1"))
        assertTrue(lua.contains("S.CELL_CTX = S.CELL_SCR_TAIL + 1"))
        assertTrue(lua.contains("S.ROW_CELLS = S.CELL_SCR_TAIL + 1"))
        assertTrue(lua.contains("frame:SetSize(S.WIDTH, 2 * S.CELL_H)"))
        assertTrue(lua.contains("-S.CELL_H)"))
        assertTrue(lua.contains("S.WIN_MAX = ${CooldownStrip.WIN_MAX}"))
        assertTrue(lua.contains("S.SCROLL_MAX = ${CooldownStrip.SCROLL_MAX}"))
        assertTrue(lua.contains("S.SCROLL_VERSION = ${CooldownStrip.SCROLL_VERSION}"))
        assertTrue(lua.contains("local SCR_HEAD = { { 0, 255, 0 }, { 255, 0, 255 } }"))
        assertTrue(lua.contains("local SCR_TAIL = { 255, 255, 0 }"))
        assertTrue(lua.contains("S.CELL_SCR = S.CELL_WIN_TAIL + 1"))
        assertTrue(lua.contains("local WIN_HEAD = { { 255, 0, 0 }, { 0, 255, 255 } }"))
        assertTrue(lua.contains("local WIN_TAIL = { 255, 255, 255 }"))
        assertTrue(lua.contains("c * 33 + nibbles[i] + 1) % 4093"))
        val toc = File(dir, "TouchForever.toc").readText()
        assertTrue(toc.lines().any { it.trim() == "TouchForeverCooldown.lua" })
    }
}
