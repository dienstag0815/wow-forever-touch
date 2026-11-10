package app.gamenative.cooldown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownMathTest {
    @Test
    fun sweepGrowsClockwiseFromTwelveOClock() {
        // Android angles: 0 = 3 o'clock, 90 = 6, 180 = 9, 270 = 12 (-90).
        assertEquals(-90f, CooldownMath.sweepStart(1f), 0.001f)
        assertEquals(360f, CooldownMath.sweepAngle(1f), 0.001f)
        // Half left: the lit half is the right one (12 -> 6 o'clock), the dark half goes from 6 o'clock over 9 to 12.
        assertEquals(90f, CooldownMath.sweepStart(0.5f), 0.001f)
        assertEquals(180f, CooldownMath.sweepAngle(0.5f), 0.001f)
        // A quarter left: dark from 9 o'clock to 12 o'clock.
        assertEquals(180f, CooldownMath.sweepStart(0.25f), 0.001f)
        assertEquals(90f, CooldownMath.sweepAngle(0.25f), 0.001f)
        // The dark part always ends at 12 o'clock (270 degrees = -90): start + sweep.
        for (fraction in listOf(0.1f, 0.33f, 0.75f, 1f)) {
            val end = (CooldownMath.sweepStart(fraction) + CooldownMath.sweepAngle(fraction) + 360f) % 360f
            assertEquals("fraction $fraction", 270f, end, 0.01f)
        }
        // Out of range values are clamped.
        assertEquals(0f, CooldownMath.sweepAngle(-1f), 0f)
        assertEquals(360f, CooldownMath.sweepAngle(3f), 0f)
    }

    @Test
    fun remainingTimeText() {
        assertEquals("", CooldownMath.format(0f))
        assertEquals("0.0", CooldownMath.format(0.04f))
        assertEquals("0.9", CooldownMath.format(0.96f))
        assertEquals("2.9", CooldownMath.format(2.99f))
        assertEquals("3", CooldownMath.format(3f))
        assertEquals("4", CooldownMath.format(3.01f))
        assertEquals("10", CooldownMath.format(9.2f))
        assertEquals("30", CooldownMath.format(30f))
        assertEquals("60", CooldownMath.format(59.4f))
        assertEquals("1m", CooldownMath.format(59.5f))
        assertEquals("1m", CooldownMath.format(60f))
        assertEquals("2m", CooldownMath.format(61f))
        assertEquals("15m", CooldownMath.format(900f))
        assertEquals("59m", CooldownMath.format(3540f))
        assertEquals("1h", CooldownMath.format(3541f))
        assertEquals("2h", CooldownMath.format(7200f))
    }

    @Test
    fun cellFlagsAndEquality() {
        val cell = CooldownCell(12.5f, 0.4f, CooldownStrip.F_FILLED or CooldownStrip.F_GCD)
        assertTrue(cell.filled && cell.cooling && cell.globalOnly)
        assertFalse(cell.outOfRange || cell.noResource)
        assertEquals(cell, CooldownCell(12.5f, 0.4f, CooldownStrip.F_FILLED or CooldownStrip.F_GCD))
        assertFalse(CooldownCell(0f, 0f, CooldownStrip.F_FILLED).cooling)
    }
}
