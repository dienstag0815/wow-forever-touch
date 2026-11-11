package app.gamenative.ui.touchstyle

import org.junit.Rule
import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine Rechnung der Touch-Stile F und G: Stile, Farben, Deckkraft je Zustand, Kontrast, Ruhe-Timer, Joystick-Profil. */
class TouchStyleModelTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private val g = TouchStyle.G
    private val f = TouchStyle.F

    // ---- Stil ------------------------------------------------------------------------------------------------------

    @Test
    fun unknownOrEmptyStyleIdMeansTheWowDefault() {
        assertEquals(TouchStyle.G, TouchStyle.fromId(null))
        assertEquals(TouchStyle.G, TouchStyle.fromId(""))
        assertEquals(TouchStyle.G, TouchStyle.fromId("zzz"))
        for (style in TouchStyle.entries) assertEquals(style, TouchStyle.fromId(style.id))
    }

    @Test
    fun `standardthema ist WoW (G), aktuell bleibt waehlbar`() {
        assertEquals(TouchStyle.G, TouchStyle.DEFAULT)
        assertEquals(TouchStyle.CURRENT, TouchStyle.fromId("aktuell"))
        assertEquals(TouchStyle.entries.size, TouchStyle.entries.map { it.id }.toSet().size)
    }

    @Test
    fun nextCyclesThroughAllStylesAndBack() {
        assertEquals(TouchStyle.F, TouchStyle.CURRENT.next())
        assertEquals(TouchStyle.G, TouchStyle.F.next())
        assertEquals(TouchStyle.CURRENT, TouchStyle.G.next())
    }

    // ---- Kontrast --------------------------------------------------------------------------------------------------

    @Test
    fun contrastIsClampedToSeventyToOneFiftyPercent() {
        assertEquals(0.7f, TouchStyleMath.clampContrast(0.1f), 0f)
        assertEquals(1.5f, TouchStyleMath.clampContrast(9f), 0f)
        assertEquals(1.2f, TouchStyleMath.clampContrast(1.2f), 0f)
        assertEquals(1.0f, TouchStyleMath.clampContrast(Float.NaN), 0f)
        assertEquals("70 %", TouchStyleMath.contrastLabel(0.7f))
        assertEquals("100 %", TouchStyleMath.contrastLabel(1.0f))
        assertEquals("150 %", TouchStyleMath.contrastLabel(7f))
    }

    @Test
    fun contrastStepsCoverTheRangeInTenPercent() {
        val steps = TouchStyleMath.CONTRAST_STEPS
        assertEquals(0.7f, steps.first(), 0f)
        assertEquals(1.5f, steps.last(), 0f)
        assertTrue(steps.contains(1.0f))
        for (i in 1 until steps.size) assertEquals(0.1f, steps[i] - steps[i - 1], 0.001f)
    }

    @Test
    fun nextContrastGoesStepByStepAndWraps() {
        assertEquals(1.1f, TouchStyleMath.nextContrast(1.0f), 0.0001f)
        assertEquals(0.7f, TouchStyleMath.nextContrast(1.5f), 0.0001f)
        // zwischen den Stufen: die nächsthöhere
        assertEquals(1.1f, TouchStyleMath.nextContrast(1.05f), 0.0001f)
        // ausserhalb des Bereichs wird zuerst begrenzt
        assertEquals(0.8f, TouchStyleMath.nextContrast(0.1f), 0.0001f)
        assertEquals(0.7f, TouchStyleMath.nextContrast(99f), 0.0001f)
    }

    @Test
    fun scaleAlphaMultipliesAndStaysInByteRange() {
        assertEquals(72, TouchStyleMath.scaleAlpha(72, 1.0f))
        assertEquals(108, TouchStyleMath.scaleAlpha(72, 1.5f))
        assertEquals(50, TouchStyleMath.scaleAlpha(72, 0.7f))
        assertEquals(255, TouchStyleMath.scaleAlpha(204, 1.5f))
        assertEquals(0, TouchStyleMath.scaleAlpha(0, 1.5f))
        // Faktor ausserhalb des Bereichs wird begrenzt
        assertEquals(TouchStyleMath.scaleAlpha(100, 1.5f), TouchStyleMath.scaleAlpha(100, 50f))
        assertEquals(TouchStyleMath.scaleAlpha(100, 0.7f), TouchStyleMath.scaleAlpha(100, -3f))
    }

    // ---- G: Ruhe, aktiv, gedrückt ----------------------------------------------------------------------------------

    @Test
    fun styleGFillIsTwentyEightFiftyThreeAndEightyPercent() {
        val rest = TouchStyleMath.baseFillAlpha(g, TouchRole.SLOT, pressed = false, level = 0f)
        val active = TouchStyleMath.baseFillAlpha(g, TouchRole.SLOT, pressed = false, level = 1f)
        val pressed = TouchStyleMath.baseFillAlpha(g, TouchRole.SLOT, pressed = true, level = 0f)
        assertEquals(72, rest)
        assertEquals(136, active)
        assertEquals(204, pressed)
        assertEquals(0.28f, rest / 255f, 0.01f)
        assertEquals(0.53f, active / 255f, 0.01f)
        assertEquals(0.80f, pressed / 255f, 0.01f)
        // gedrückt ist unabhängig von der Ruhe-Stufe
        assertEquals(204, TouchStyleMath.baseFillAlpha(g, TouchRole.SLOT, pressed = true, level = 1f))
    }

    @Test
    fun styleGEdgeIsSixtyPercentAtRestAndGrowsWithTheLevel() {
        assertEquals(153, TouchStyleMath.baseEdgeAlpha(g, TouchRole.SLOT, 0f))
        assertEquals(225, TouchStyleMath.baseEdgeAlpha(g, TouchRole.SLOT, 1f))
        assertEquals(0.6f, 153 / 255f, 0.001f)
        val mid = TouchStyleMath.baseEdgeAlpha(g, TouchRole.SLOT, 0.5f)
        assertTrue(mid in 154..224)
        // Grundkreis des Sticks hat eigene, leisere Werte
        assertEquals(120, TouchStyleMath.baseEdgeAlpha(g, TouchRole.BASE, 0f))
        assertEquals(170, TouchStyleMath.baseEdgeAlpha(g, TouchRole.BASE, 1f))
    }

    @Test
    fun styleGPanelAndThumbKeepTheirFillWhilePressed() {
        for (role in listOf(TouchRole.PANEL, TouchRole.THUMB)) {
            assertEquals(
                TouchStyleMath.baseFillAlpha(g, role, pressed = false, level = 0.3f),
                TouchStyleMath.baseFillAlpha(g, role, pressed = true, level = 0.3f),
            )
        }
        assertEquals(36, TouchStyleMath.baseFillAlpha(g, TouchRole.BASE, pressed = false, level = 0f))
        assertEquals(36, TouchStyleMath.baseFillAlpha(g, TouchRole.BASE, pressed = true, level = 1f))
    }

    @Test
    fun styleGColoursAreStoneBronzeLightEdgeAndText() {
        assertEquals(0x1C1A17, TouchStyleMath.fillRgb(g, TouchRole.SLOT, pressed = false))
        assertEquals(0x1C1A17, TouchStyleMath.fillRgb(g, TouchRole.SEGMENT, pressed = true))
        assertEquals(0x8C6B3E, TouchStyleMath.G_BRONZE)
        assertEquals(0xC9A15A, TouchStyleMath.G_LIGHT)
        assertEquals(0xE8DCC0, TouchStyleMath.G_TEXT)
        assertEquals(TouchStyleMath.G_WARM, TouchStyleMath.pressedRingRgb(g))
        assertEquals(0xFFE8DCC0.toInt(), TouchStyleMath.textColor(g, pressed = false))
        assertEquals(0xFFE8DCC0.toInt(), TouchStyleMath.textColor(g, pressed = true))
    }

    @Test
    fun pressedSymbolInStyleGIsTwentyFivePercentSmaller() {
        assertEquals(0.75f, TouchStyleMath.symbolScale(g, true), 0f)
        assertEquals(1f, TouchStyleMath.symbolScale(g, false), 0f)
        assertEquals(1f, TouchStyleMath.symbolScale(f, true), 0f)
        assertEquals(0.94f * 0.75f, TouchStyleMath.textScale(g, true), 0.0001f)
        assertEquals(0.94f, TouchStyleMath.textScale(f, true), 0.0001f)
    }

    @Test
    fun iconOpacityGrowsFromRestToActiveAndIsFullWhenPressed() {
        assertEquals(209, TouchStyleMath.iconAlpha(g, pressed = false, level = 0f)) // 82 %
        assertEquals(255, TouchStyleMath.iconAlpha(g, pressed = false, level = 1f))
        assertEquals(255, TouchStyleMath.iconAlpha(g, pressed = true, level = 0f))
        assertEquals(255, TouchStyleMath.iconAlpha(f, pressed = false, level = 0f))
    }

    // ---- F: feste Werte --------------------------------------------------------------------------------------------

    @Test
    fun styleFHasFixedValuesIndependentOfTheRestLevel() {
        for (role in TouchRole.entries) {
            for (pressed in listOf(false, true)) {
                assertEquals(
                    TouchStyleMath.baseFillAlpha(f, role, pressed, 0f),
                    TouchStyleMath.baseFillAlpha(f, role, pressed, 1f),
                )
            }
            assertEquals(TouchStyleMath.baseEdgeAlpha(f, role, 0f), TouchStyleMath.baseEdgeAlpha(f, role, 1f))
        }
        assertEquals(150, TouchStyleMath.baseFillAlpha(f, TouchRole.SLOT, false, 0f))
        assertEquals(165, TouchStyleMath.baseFillAlpha(f, TouchRole.SLOT, true, 0f))
        assertEquals(0xFFD41E, TouchStyleMath.pressedRingRgb(f))
        assertEquals(240, TouchStyleMath.pressedRingAlpha(f, 1.0f))
    }

    @Test
    fun styleFPressedTextIsEightyPercentOfTheGold() {
        val rest = TouchStyleMath.textColor(f, pressed = false)
        val pressed = TouchStyleMath.textColor(f, pressed = true)
        assertEquals(0xFFE8C646.toInt(), rest)
        assertEquals(255, pressed ushr 24)
        assertEquals(186, (pressed shr 16) and 0xFF) // 232 * 0,8 = 185,6
    }

    // ---- Kontrast wirkt auf Füllung, Kante und Schatten --------------------------------------------------------------

    @Test
    fun contrastScalesFillEdgeAndShadow() {
        val fill = TouchStyleMath.fillAlpha(g, TouchRole.SLOT, false, 0f, 1.5f)
        val edge = TouchStyleMath.edgeAlpha(g, TouchRole.SLOT, 0f, 0.7f)
        assertEquals(108, fill)
        assertEquals(107, edge) // 153 * 0.7 = 107,1
        val shadow = TouchStyleMath.shadow(g, TouchRole.SLOT)
        assertNotNull(shadow)
        assertEquals(173, TouchStyleMath.scaleAlpha(shadow!!.alpha, 1.5f)) // 115 * 1,5 = 172,5, gerundet
        // 100 % verändert nichts
        assertEquals(72, TouchStyleMath.fillAlpha(g, TouchRole.SLOT, false, 0f, 1.0f))
    }

    @Test
    fun shadowsAreOutsideOnlyForGAndAbsentForTheStickBase() {
        assertTrue(TouchStyleMath.shadowOutsideOnly(g))
        assertFalse(TouchStyleMath.shadowOutsideOnly(f))
        assertNull(TouchStyleMath.shadow(g, TouchRole.BASE))
        assertNull(TouchStyleMath.shadow(f, TouchRole.BASE))
        assertNotNull(TouchStyleMath.shadow(g, TouchRole.PANEL))
        assertNull(TouchStyleMath.shadow(TouchStyle.CURRENT, TouchRole.SLOT))
    }

    @Test
    fun chamferIsNinePercentForSlotsInStyleG() {
        assertEquals(0.09f, TouchStyleMath.chamferFraction(g, TouchRole.SLOT), 0f)
        assertEquals(0.10f, TouchStyleMath.chamferFraction(g, TouchRole.TEXT), 0f)
        assertEquals(0.10f, TouchStyleMath.chamferFraction(g, TouchRole.PANEL), 0f)
        assertTrue(TouchStyleMath.chamferFraction(f, TouchRole.SLOT) > TouchStyleMath.chamferFraction(g, TouchRole.SLOT))
    }

    @Test
    fun edgeWidthIsOneAndAHalfDpInStyleG() {
        for (role in TouchRole.entries) assertEquals(1.5f, TouchStyleMath.edgeWidthDp(g, role), 0f)
        for (role in TouchRole.entries) assertTrue(TouchStyleMath.edgeWidthDp(f, role) in 0.5f..1.5f)
    }

    @Test
    fun argbAndWithAlphaPackTheChannels() {
        assertEquals(0x80102030.toInt(), TouchStyleMath.argb(0x80, 0x10, 0x20, 0x30))
        assertEquals(0x80102030.toInt(), TouchStyleMath.withAlpha(0x102030, 0x80))
        assertEquals(0xFF000000.toInt(), TouchStyleMath.withAlpha(0, 999))
        assertEquals(0x00FFFFFF, TouchStyleMath.withAlpha(0xFFFFFF, -5))
    }

    // ---- Ruhe-Timer ------------------------------------------------------------------------------------------------

    @Test
    fun timerStartsActiveAndRestsAfterFourSecondsWithoutTouch() {
        val t = TouchRestTimer()
        t.reset(1000)
        assertEquals(1f, t.update(1000, false), 0f)
        assertEquals(1f, t.update(4900, false), 0f) // 3,9 s nach dem Start: noch aktiv
        // ab 5000 ms ist Ruhe; der Übergang dauert 400 ms und beginnt erst dann
        assertEquals(1f, t.update(5000, false), 0.001f)
        assertEquals(0.75f, t.update(5100, false), 0.001f)
        assertEquals(0.25f, t.update(5300, false), 0.001f)
        assertEquals(0f, t.update(5400, false), 0.001f)
        assertEquals(0f, t.update(9000, false), 0f)
    }

    @Test
    fun aLongPauseBetweenFramesDoesNotSkipTheFade() {
        val t = TouchRestTimer()
        t.reset(0)
        t.update(100, false)
        // das nächste Bild kommt erst nach 4,2 s (Ruhe begann bei 4000): erst 200 ms Übergang
        assertEquals(0.5f, t.update(4200, false), 0.001f)
    }

    @Test
    fun touchBringsBackTheActiveLookWithinOneHundredMilliseconds() {
        val t = TouchRestTimer()
        t.reset(0)
        t.update(6000, false)
        t.update(6100, false)
        assertEquals(0f, t.level, 0f)
        t.touch(7000)
        assertEquals(0.5f, t.update(7050, false), 0.001f)
        assertEquals(1f, t.update(7100, false), 0.001f)
        // und bleibt vier Sekunden aktiv
        assertEquals(1f, t.update(10900, false), 0f)
    }

    @Test
    fun aHeldFingerKeepsTheElementsActive() {
        val t = TouchRestTimer()
        t.reset(0)
        for (now in listOf(1000L, 5000L, 20000L, 60000L)) assertEquals(1f, t.update(now, true), 0f)
        // nach dem Loslassen zählen die 4 s ab dem letzten Bild mit Finger
        assertEquals(1f, t.update(63000, false), 0f)
        assertEquals(0.75f, t.update(64100, false), 0.001f)
    }

    @Test
    fun redrawIsAskedForOnlyWhenSomethingWillChange() {
        val t = TouchRestTimer()
        t.reset(0)
        t.update(1000, false)
        // aktiv: die Ruhe beginnt in 3 s
        assertEquals(3000L, t.nextRedrawIn(1000, false))
        // Finger liegt: nichts nötig (das Loslassen löst das Zeichnen aus)
        assertEquals(-1L, t.nextRedrawIn(1000, true))
        // im Übergang: sofort wieder
        t.update(4100, false)
        assertEquals(0L, t.nextRedrawIn(4100, false))
        // in Ruhe: nichts
        t.update(4500, false)
        assertEquals(-1L, t.nextRedrawIn(4500, false))
        // Berührung in Ruhe: sofort zeichnen, bis die Stufe oben ist
        t.touch(5000)
        assertEquals(0L, t.nextRedrawIn(5000, false))
    }

    // ---- Zeichenzeit -----------------------------------------------------------------------------------------------

    @Test
    fun frameTimeStatsReportAfterEachFullWindow() {
        val stats = FrameTimeStats(window = 3)
        assertNull(stats.add(1_000_000))
        assertNull(stats.add(2_000_000))
        val s = stats.add(6_000_000)
        assertNotNull(s)
        assertEquals(3, s!!.frames)
        assertEquals(3.0, s.meanMs, 0.0001)
        assertEquals(6.0, s.maxMs, 0.0001)
        // das nächste Fenster beginnt neu
        assertNull(stats.add(10_000_000))
        assertNull(stats.add(10_000_000))
        assertEquals(10.0, stats.add(10_000_000)!!.meanMs, 0.0001)
    }

    // ---- Joystick als weiche Fläche -----------------------------------------------------------------------------------

    @Test
    fun softStickUnderlayIsThirtyToThirtyFivePercentAndFadesToNothing() {
        val peak = TouchStyleMath.softUnderlayAlpha(0f)
        assertTrue("Unterlage 30 bis 35 %", peak in 0.30f..0.35f)
        assertEquals(peak, TouchStyleMath.softUnderlayAlpha(0.15f), 0.0001f)
        assertEquals(0f, TouchStyleMath.softUnderlayAlpha(1f), 0.0001f)
        var last = peak
        for (i in 1..20) {
            val a = TouchStyleMath.softUnderlayAlpha(i / 20f)
            assertTrue(a <= last + 1e-6f)
            last = a
        }
    }

    @Test
    fun softStickGlowFadesFromTheCentreToZero() {
        assertEquals(TouchStyleMath.GLOW_PEAK, TouchStyleMath.softGlowAlpha(0f), 0.0001f)
        assertEquals(0f, TouchStyleMath.softGlowAlpha(1f), 0.0001f)
        assertTrue(TouchStyleMath.softGlowAlpha(0.5f) in 0.01f..TouchStyleMath.GLOW_PEAK)
    }

    @Test
    fun restingPlaceholderIsTwentyPercent() {
        assertEquals(0.2f, TouchStyleMath.PLACEHOLDER_ALPHA / 255f, 0.001f)
    }

    @Test
    fun smoothstepIsClampedAndSymmetric() {
        assertEquals(0f, TouchStyleMath.smoothstep(0.2f, 0.8f, 0f), 0f)
        assertEquals(1f, TouchStyleMath.smoothstep(0.2f, 0.8f, 1f), 0f)
        assertEquals(0.5f, TouchStyleMath.smoothstep(0.2f, 0.8f, 0.5f), 0.0001f)
    }
}
