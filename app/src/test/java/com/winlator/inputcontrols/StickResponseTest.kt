package com.winlator.inputcontrols

import com.winlator.inputcontrols.StickResponse.stickResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickResponseTest {
    private val dead = 0.15f

    @Test
    fun deadZoneGivesZero() {
        assertEquals(0f, stickResponse(0f, 1f, dead, 1.5f), 0f)
        assertEquals(0f, stickResponse(0.15f, 3f, dead, 1.5f), 0f)
        assertEquals(0f, stickResponse(-0.1f, 3f, dead, 1.5f), 0f)
    }

    @Test
    fun fullDeflectionIsFullAtSensitivityOne() {
        assertEquals(1f, stickResponse(1f, 1f, dead, 1.5f), 1e-6f)
        assertEquals(-1f, stickResponse(-1f, 1f, dead, 1.5f), 1e-6f)
    }

    @Test
    fun curveIsMonotonic() {
        var last = -1f
        var raw = 0f
        while (raw <= 1f) {
            val out = stickResponse(raw, 1.25f, dead, 1.5f)
            assertTrue("raw=$raw out=$out last=$last", out >= last)
            last = out
            raw += 0.01f
        }
    }

    @Test
    fun outputIsClamped() {
        assertEquals(1f, stickResponse(1f, 3f, dead, 1.5f), 0f)
        assertEquals(-1f, stickResponse(-0.9f, 3f, dead, 1.5f), 0f)
        assertEquals(1f, stickResponse(5f, 1f, dead, 1.5f), 0f)
    }

    @Test
    fun curveGivesFinerControlAtSmallDeflection() {
        val linear = stickResponse(0.4f, 1f, dead, 1f)
        val curved = stickResponse(0.4f, 1f, dead, 1.5f)
        assertTrue(curved < linear)
    }

    @Test
    fun higherSensitivityNeverReducesOutput() {
        val levels = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
        for (raw in floatArrayOf(0.2f, 0.4f, 0.6f, 0.8f)) {
            var last = 0f
            for (s in levels) {
                val out = stickResponse(raw, s, dead, 1.5f)
                assertTrue(out >= last)
                last = out
            }
        }
    }

    @Test
    fun symmetricAndNaNSafe() {
        assertEquals(-stickResponse(0.5f, 1.25f, dead, 1.5f), stickResponse(-0.5f, 1.25f, dead, 1.5f), 0f)
        assertEquals(0f, stickResponse(Float.NaN, 1f, dead, 1.5f), 0f)
    }
}
