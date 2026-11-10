package com.winlator.inputcontrols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollWheelMathTest {
    private val step = 6f // 0.6 grid units at snapping size 10

    /** Feeds finger movements (screen delta along the axis) through the accumulator like the element does. */
    private fun ticks(deltas: List<Float>, vertical: Boolean = true, reverse: Boolean = false): List<Int> {
        var accum = 0f
        val result = mutableListOf<Int>()
        for (delta in deltas) {
            accum += ScrollWheelMath.signedDelta(delta, vertical, reverse)
            val steps = ScrollWheelMath.wholeSteps(accum, step)
            accum -= steps * step
            result += steps
        }
        return result
    }

    @Test
    fun `step distance follows the grid size and stays within limits`() {
        assertEquals(6f, ScrollWheelMath.stepPixels(0.6f, 10), 0.01f)
        assertEquals(12f, ScrollWheelMath.stepPixels(0.6f, 20), 0.01f)
        assertEquals(1f, ScrollWheelMath.stepPixels(0.01f, 10), 0.01f) // clamped to the minimum step (0.1 grid = 1 px)
        assertEquals(40f, ScrollWheelMath.stepPixels(99f, 10), 0.01f) // clamped to the maximum step
        assertEquals(1f, ScrollWheelMath.stepPixels(0.6f, 0), 0.01f) // never zero
        assertEquals(ScrollWheelMath.DEFAULT_STEP, ScrollWheelMath.clampStep(Float.NaN), 0f)
    }

    @Test
    fun `speed is the default step divided by the step`() {
        assertEquals(1f, ScrollWheelMath.speedOf(ScrollWheelMath.DEFAULT_STEP), 0.0001f)
        assertEquals(2f, ScrollWheelMath.speedOf(0.3f), 0.0001f)
        assertEquals(0.5f, ScrollWheelMath.speedOf(1.2f), 0.0001f)
        // out of range steps count as the nearest allowed one, NaN as the default
        assertEquals(ScrollWheelMath.DEFAULT_STEP / ScrollWheelMath.MIN_STEP, ScrollWheelMath.speedOf(0.001f), 0.0001f)
        assertEquals(1f, ScrollWheelMath.speedOf(Float.NaN), 0.0001f)
    }

    @Test
    fun `step for speed inverts speed of and stays within limits`() {
        for (speed in listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f, 4f, 6f)) {
            val step = ScrollWheelMath.stepForSpeed(speed)
            assertTrue("step $step for $speed", step in ScrollWheelMath.MIN_STEP..ScrollWheelMath.MAX_STEP)
            assertEquals("speed $speed", speed, ScrollWheelMath.speedOf(step), speed * 0.01f)
        }
        assertEquals(ScrollWheelMath.DEFAULT_STEP, ScrollWheelMath.stepForSpeed(1f), 0f)
        assertEquals(ScrollWheelMath.MIN_STEP, ScrollWheelMath.stepForSpeed(100f), 0.0001f)
        assertEquals(ScrollWheelMath.DEFAULT_STEP / ScrollWheelMath.MIN_SPEED, ScrollWheelMath.stepForSpeed(0.001f), 0.0001f)
        assertEquals(ScrollWheelMath.DEFAULT_STEP, ScrollWheelMath.stepForSpeed(Float.NaN), 0f)
        assertEquals(ScrollWheelMath.DEFAULT_STEP, ScrollWheelMath.stepForSpeed(-1f), 0f)
    }

    @Test
    fun `slider position is logarithmic and reaches both ends`() {
        assertEquals(0f, ScrollWheelMath.positionForSpeed(ScrollWheelMath.MIN_SPEED), 0.0001f)
        assertEquals(1f, ScrollWheelMath.positionForSpeed(ScrollWheelMath.MAX_SPEED), 0.0001f)
        assertEquals(ScrollWheelMath.MIN_SPEED, ScrollWheelMath.speedForPosition(0f), 0f)
        assertEquals(ScrollWheelMath.MAX_SPEED, ScrollWheelMath.speedForPosition(1f), 0f)
        // positions outside 0..1 and NaN are tolerated
        assertEquals(ScrollWheelMath.MAX_SPEED, ScrollWheelMath.speedForPosition(5f), 0f)
        assertEquals(ScrollWheelMath.MIN_SPEED, ScrollWheelMath.speedForPosition(-5f), 0f)
        assertEquals(ScrollWheelMath.MIN_SPEED, ScrollWheelMath.speedForPosition(Float.NaN), 0f)
        // equal ratios take equal distances: 0.5x -> 1x -> 2x
        val a = ScrollWheelMath.positionForSpeed(0.5f)
        val b = ScrollWheelMath.positionForSpeed(1f)
        val c = ScrollWheelMath.positionForSpeed(2f)
        assertEquals(b - a, c - b, 0.0001f)
        // the default sits somewhere in the middle, so both slower and faster are reachable
        assertTrue(ScrollWheelMath.positionForSpeed(1f) in 0.2f..0.8f)
    }

    @Test
    fun `dragging the slider changes the stored step monotonically`() {
        var lastStep = Float.MAX_VALUE
        var changes = 0
        for (i in 0..100) {
            val speed = ScrollWheelMath.speedForPosition(i / 100f)
            val step = ScrollWheelMath.stepForSpeed(speed)
            assertTrue("step must not grow with the speed", step <= lastStep + 0.0001f)
            if (step < lastStep - 0.0001f) changes++
            lastStep = step
        }
        // plenty of distinct settings along the slider, not a single dead zone
        assertTrue("distinct steps: $changes", changes >= 30)
    }

    @Test
    fun `a faster setting gives more ticks for the same swipe`() {
        val snapping = 22
        val slow = ScrollWheelMath.stepPixels(ScrollWheelMath.stepForSpeed(0.5f), snapping)
        val normal = ScrollWheelMath.stepPixels(ScrollWheelMath.stepForSpeed(1f), snapping)
        val fast = ScrollWheelMath.stepPixels(ScrollWheelMath.stepForSpeed(4f), snapping)
        val swipe = 300f
        assertTrue(ScrollWheelMath.wholeSteps(swipe, slow) < ScrollWheelMath.wholeSteps(swipe, normal))
        assertTrue(ScrollWheelMath.wholeSteps(swipe, normal) < ScrollWheelMath.wholeSteps(swipe, fast))
        // about 4 times as many as normal
        assertEquals(4.0, ScrollWheelMath.wholeSteps(swipe, fast).toDouble() / ScrollWheelMath.wholeSteps(swipe, normal), 0.5)
    }

    @Test
    fun `swiping up scrolls up and swiping down scrolls down`() {
        assertEquals(listOf(3), ticks(listOf(-20f))) // 20 px up = 3 ticks up (20 / 6)
        assertEquals(listOf(-2), ticks(listOf(14f))) // 14 px down = 2 ticks down
    }

    @Test
    fun `direction can be reversed`() {
        assertEquals(listOf(-3), ticks(listOf(-20f), reverse = true))
        assertEquals(listOf(2), ticks(listOf(14f), reverse = true))
    }

    @Test
    fun `horizontal wheel scrolls up when swiping right`() {
        assertEquals(listOf(3), ticks(listOf(20f), vertical = false))
        assertEquals(listOf(-2), ticks(listOf(-14f), vertical = false))
        assertEquals(listOf(-3), ticks(listOf(20f), vertical = false, reverse = true))
    }

    @Test
    fun `small movements add up to a tick and the remainder is kept`() {
        // 4 px, 4 px -> one tick after the second move (8 px, 2 px left), 4 px more -> 6 px = one more tick
        assertEquals(listOf(0, 1, 1), ticks(listOf(-4f, -4f, -4f)))
    }

    @Test
    fun `turning around cancels the accumulated distance`() {
        // 4 px up, 3 px down: net 1 px up, no tick yet
        assertEquals(listOf(0, 0), ticks(listOf(-4f, 3f)))
        // then 10 px up: 11 px in total = 1 tick (5 px left)
        assertEquals(listOf(0, 0, 1), ticks(listOf(-4f, 3f, -10f)))
    }

    @Test
    fun `a smaller step is more sensitive`() {
        var accum = 0f
        accum += ScrollWheelMath.signedDelta(-20f, true, false)
        assertEquals(3, ScrollWheelMath.wholeSteps(accum, 6f))
        assertEquals(10, ScrollWheelMath.wholeSteps(accum, 2f))
        assertEquals(0, ScrollWheelMath.wholeSteps(accum, 0f))
    }

    @Test
    fun `ticks per event are limited but keep their sign`() {
        assertEquals(ScrollWheelMath.MAX_TICKS_PER_EVENT, ScrollWheelMath.limitTicks(40))
        assertEquals(-ScrollWheelMath.MAX_TICKS_PER_EVENT, ScrollWheelMath.limitTicks(-40))
        assertEquals(2, ScrollWheelMath.limitTicks(2))
        assertEquals(0, ScrollWheelMath.limitTicks(0))
    }

    @Test
    fun `tap needs a short touch without movement`() {
        assertTrue(ScrollWheelMath.isTap(2f, 80, 10f, 200))
        assertFalse(ScrollWheelMath.isTap(12f, 80, 10f, 200))
        assertFalse(ScrollWheelMath.isTap(2f, 250, 10f, 200))
    }

    @Test
    fun `velocity is smoothed and ignores zero time`() {
        assertEquals(0.36f, ScrollWheelMath.blendVelocity(0f, 6f, 10), 0.001f)
        assertEquals(0.4f * 0.6f + 0.6f * 0.6f, ScrollWheelMath.blendVelocity(0.6f, 6f, 10), 0.001f)
        assertEquals(1.5f, ScrollWheelMath.blendVelocity(1.5f, 6f, 0), 0.001f)
    }

    @Test
    fun `coasting needs a fling and is limited`() {
        assertEquals(0, ScrollWheelMath.inertiaTicks(0.1f, step))
        assertEquals(0, ScrollWheelMath.inertiaTicks(0f, step))
        assertTrue(ScrollWheelMath.inertiaTicks(0.5f, step) in 1..ScrollWheelMath.MAX_INERTIA_TICKS)
        assertEquals(ScrollWheelMath.MAX_INERTIA_TICKS, ScrollWheelMath.inertiaTicks(5f, step))
        assertEquals(ScrollWheelMath.MAX_INERTIA_TICKS, ScrollWheelMath.inertiaTicks(-5f, step)) // direction is the caller's business
        assertTrue(ScrollWheelMath.inertiaTicks(0.5f, 6f) <= ScrollWheelMath.inertiaTicks(1.0f, 6f))
    }

    @Test
    fun `coasting slows down`() {
        assertTrue(ScrollWheelMath.inertiaDelayMillis(0) < ScrollWheelMath.inertiaDelayMillis(1))
        assertTrue(ScrollWheelMath.inertiaDelayMillis(5) < ScrollWheelMath.inertiaDelayMillis(11))
        assertEquals(ScrollWheelMath.inertiaDelayMillis(0), ScrollWheelMath.inertiaDelayMillis(-3))
    }
}
