package com.winlator.inputcontrols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickRingTest {
    private val up = -90f

    /** Angle of the segment centres for [count] segments with segment 0 at [start]. */
    private fun centres(count: Int, start: Float) = (0 until count).map { StickRing.segmentCenterAngle(it, count, start) }

    @Test
    fun `segment index for 4 6 and 8 segments hits each segment at its centre`() {
        for (count in listOf(4, 6, 8)) {
            centres(count, up).forEachIndexed { index, angle ->
                assertEquals("count $count", index, StickRing.segmentIndex(angle, count, up))
            }
        }
    }

    @Test
    fun `eight segments go clockwise from the top`() {
        // up, up-right, right, down-right, down, down-left, left, up-left
        assertEquals(0, StickRing.segmentIndex(StickRing.angleOf(0f, -50f), 8, up))
        assertEquals(1, StickRing.segmentIndex(StickRing.angleOf(40f, -40f), 8, up))
        assertEquals(2, StickRing.segmentIndex(StickRing.angleOf(50f, 0f), 8, up))
        assertEquals(4, StickRing.segmentIndex(StickRing.angleOf(0f, 50f), 8, up))
        assertEquals(6, StickRing.segmentIndex(StickRing.angleOf(-50f, 0f), 8, up))
        assertEquals(7, StickRing.segmentIndex(StickRing.angleOf(-40f, -40f), 8, up))
    }

    @Test
    fun `segment borders belong to exactly one segment`() {
        // 4 segments, segment 0 centred up: the border to segment 1 is at -45 degrees
        assertEquals(0, StickRing.segmentIndex(-46f, 4, up))
        assertEquals(1, StickRing.segmentIndex(-44f, 4, up))
        // wrap around: segment 0 spans -135..-45, segment 3 (left) ends at -135
        assertEquals(3, StickRing.segmentIndex(-136f, 4, up))
        assertEquals(0, StickRing.segmentIndex(-134f, 4, up))
    }

    @Test
    fun `start angle moves the first segment`() {
        // 6 segments, first one centred on the right (0 degrees)
        assertEquals(0, StickRing.segmentIndex(0f, 6, 0f))
        assertEquals(1, StickRing.segmentIndex(60f, 6, 0f))
        assertEquals(5, StickRing.segmentIndex(-60f, 6, 0f))
        assertEquals(3, StickRing.segmentIndex(180f, 6, 0f))
        // 4 segments with segment 0 at 45 degrees (down-right)
        assertEquals(0, StickRing.segmentIndex(45f, 4, 45f))
        assertEquals(1, StickRing.segmentIndex(135f, 4, 45f))
        assertEquals(3, StickRing.segmentIndex(-45f, 4, 45f))
    }

    @Test
    fun `every angle maps to a valid segment`() {
        for (count in listOf(4, 6, 8)) {
            for (start in listOf(-90f, 0f, 22.5f, 180f, 370f)) {
                var angle = -720f
                while (angle <= 720f) {
                    val index = StickRing.segmentIndex(angle, count, start)
                    assertTrue("count $count start $start angle $angle -> $index", index in 0 until count)
                    angle += 3.7f
                }
            }
        }
    }

    @Test
    fun `without segments there is no index`() {
        assertEquals(-1, StickRing.segmentIndex(10f, 0, up))
    }

    @Test
    fun `segment count is rounded to the allowed values`() {
        assertEquals(0, StickRing.normalizeSegments(0))
        assertEquals(0, StickRing.normalizeSegments(-3))
        assertEquals(4, StickRing.normalizeSegments(1))
        assertEquals(4, StickRing.normalizeSegments(4))
        assertEquals(6, StickRing.normalizeSegments(5))
        assertEquals(6, StickRing.normalizeSegments(6))
        assertEquals(8, StickRing.normalizeSegments(7))
        assertEquals(8, StickRing.normalizeSegments(12))
    }

    @Test
    fun `zones are stick then gap counted to the stick then ring then nothing`() {
        // stick radius 60, gap 7.5, width 25 -> ring from 67.5 to 92.5
        assertEquals(StickRing.ZONE_STICK, StickRing.zoneAt(0f, 0f, 60f, 7.5f, 25f))
        assertEquals(StickRing.ZONE_STICK, StickRing.zoneAt(0f, -60f, 60f, 7.5f, 25f))
        assertEquals(StickRing.ZONE_STICK, StickRing.zoneAt(0f, -67f, 60f, 7.5f, 25f))
        assertEquals(StickRing.ZONE_RING, StickRing.zoneAt(0f, -68f, 60f, 7.5f, 25f))
        assertEquals(StickRing.ZONE_RING, StickRing.zoneAt(80f, 0f, 60f, 7.5f, 25f))
        assertEquals(StickRing.ZONE_RING, StickRing.zoneAt(0f, 92f, 60f, 7.5f, 25f))
        assertEquals(StickRing.ZONE_NONE, StickRing.zoneAt(0f, 93f, 60f, 7.5f, 25f))
        // diagonal: 3-4-5 triangle scaled to 75 is inside the ring
        assertEquals(StickRing.ZONE_RING, StickRing.zoneAt(45f, 60f, 60f, 7.5f, 25f))
    }

    @Test
    fun `outer band lies behind the ring after a gap that still counts to the ring`() {
        // stick 60, gap 7.5, ring width 25 -> ring 67.5..92.5; outer gap 5, outer width 25 -> outer band 97.5..122.5
        assertEquals(StickRing.ZONE_RING, StickRing.zoneAt(0f, -92f, 60f, 7.5f, 25f, 5f, 25f))
        assertEquals(StickRing.ZONE_RING, StickRing.zoneAt(0f, -96f, 60f, 7.5f, 25f, 5f, 25f))
        assertEquals(StickRing.ZONE_OUTER, StickRing.zoneAt(0f, -98f, 60f, 7.5f, 25f, 5f, 25f))
        assertEquals(StickRing.ZONE_OUTER, StickRing.zoneAt(110f, 0f, 60f, 7.5f, 25f, 5f, 25f))
        assertEquals(StickRing.ZONE_OUTER, StickRing.zoneAt(0f, 122f, 60f, 7.5f, 25f, 5f, 25f))
        assertEquals(StickRing.ZONE_NONE, StickRing.zoneAt(0f, 123f, 60f, 7.5f, 25f, 5f, 25f))
        // stick and gap to the stick are unchanged
        assertEquals(StickRing.ZONE_STICK, StickRing.zoneAt(0f, -67f, 60f, 7.5f, 25f, 5f, 25f))
    }

    @Test
    fun `without an outer band the long form behaves like the short one`() {
        for (distance in listOf(0f, 60f, 67f, 68f, 92f, 93f, 100f, 130f)) {
            assertEquals(
                "distance $distance",
                StickRing.zoneAt(distance, 0f, 60f, 7.5f, 25f),
                StickRing.zoneAt(distance, 0f, 60f, 7.5f, 25f, 5f, 0f),
            )
        }
    }

    @Test
    fun `sector hit test for rings and arcs`() {
        // sector from radius 30 to 50 centred on the top, 90 degrees wide
        assertTrue(StickRing.inSector(0f, -40f, 30f, 50f, -90f, 90f))
        assertTrue(StickRing.inSector(20f, -30f, 30f, 50f, -90f, 90f)) // 34 degrees off to the side, radius 36
        assertFalse(StickRing.inSector(0f, -20f, 30f, 50f, -90f, 90f)) // too close to the centre
        assertFalse(StickRing.inSector(0f, -60f, 30f, 50f, -90f, 90f)) // too far out
        assertFalse(StickRing.inSector(40f, 0f, 30f, 50f, -90f, 90f)) // on the right, 90 degrees off
        // wrap around at 180 degrees
        assertTrue(StickRing.inSector(-40f, 5f, 30f, 50f, 180f, 60f))
        assertTrue(StickRing.inSector(-40f, -5f, 30f, 50f, 180f, 60f))
        assertFalse(StickRing.inSector(-40f, 40f, 30f, 50f, 180f, 60f))
    }

    @Test
    fun `angle helpers`() {
        assertEquals(0f, StickRing.angleOf(1f, 0f), 0.001f)
        assertEquals(90f, StickRing.angleOf(0f, 1f), 0.001f)
        assertEquals(-90f, StickRing.angleOf(0f, -1f), 0.001f)
        assertEquals(270f, StickRing.normalizeAngle(-90f), 0.001f)
        assertEquals(10f, StickRing.normalizeAngle(730f), 0.001f)
        assertEquals(-20f, StickRing.angleDifference(350f, 10f), 0.001f)
        assertEquals(20f, StickRing.angleDifference(10f, 350f), 0.001f)
    }

    @Test
    fun `width and gap are clamped`() {
        assertEquals(StickRing.MIN_WIDTH, StickRing.clampWidth(0f), 0f)
        assertEquals(StickRing.MAX_WIDTH, StickRing.clampWidth(99f), 0f)
        assertEquals(StickRing.MIN_GAP, StickRing.clampGap(-1f), 0f)
        assertEquals(StickRing.MAX_GAP, StickRing.clampGap(99f), 0f)
    }
}
