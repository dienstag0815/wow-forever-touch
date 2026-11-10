package app.gamenative.ui.screen.wow

import org.junit.Rule
import app.gamenative.TestTexts
import app.gamenative.cooldown.CooldownStrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine Teile des Ausblendens über Spielfenstern: Fenster-Stand, Abbildung, Überlappungsregel, Hysterese. Ohne Android. */
class WindowHideTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private fun decoded(known: Boolean, vararg rects: List<Int>): CooldownStrip.Decoded {
        val d = CooldownStrip.Decoded()
        d.windowsKnown = known
        d.windowCount = rects.size
        for ((i, r) in rects.withIndex()) for (e in 0 until 4) d.windows[4 * i + e] = r[e]
        return d
    }

    private fun snapshot(vararg rects: List<Int>, pw: Int = 2000, ph: Int = 1000, sw: Int = 2000, sh: Int = 1000): WindowSnapshot =
        WindowSnapshot(rects.size, rects.flatMap { it }.toIntArray(), pw, ph, sw, sh)

    // ---- Stand aus dem Streifen ------------------------------------------------------------------------------

    @Test
    fun snapshotFromAStripWithWindows() {
        val s = WindowSnapshot.from(decoded(true, listOf(10, 20, 30, 40), listOf(50, 60, 70, 80)), 1920, 1080, 2000, 1100)
        assertNotNull(s)
        assertEquals(2, s!!.count)
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 70, 80), s.codes.toList())
        assertEquals(1920, s.pictureWidth)
        assertEquals(1100, s.screenHeight)
    }

    @Test
    fun noSnapshotWhenUnknownOrEmpty() {
        assertNull("alter Addon: unbekannt", WindowSnapshot.from(decoded(false, listOf(1, 2, 3, 4)), 100, 100, 100, 100))
        assertNull("keines offen", WindowSnapshot.from(decoded(true), 100, 100, 100, 100))
    }

    @Test
    fun snapshotsCompareByContent() {
        val a = snapshot(listOf(1, 2, 3, 4))
        assertTrue(a.sameAs(snapshot(listOf(1, 2, 3, 4))))
        assertFalse(a.sameAs(snapshot(listOf(1, 2, 3, 5))))
        assertFalse(a.sameAs(snapshot(listOf(1, 2, 3, 4), listOf(5, 6, 7, 8))))
        assertFalse(a.sameAs(snapshot(listOf(1, 2, 3, 4), sw = 2001)))
    }

    // ---- Fenster-Stand halten ----------------------------------------------------------------------------------

    @Test
    fun feedReportsChangesOnly() {
        val feed = WindowFeed()
        assertFalse("nichts offen, nichts bekannt", feed.update(true, null, 0))
        assertTrue("Fenster geht auf", feed.update(true, snapshot(listOf(1, 2, 3, 4)), 100))
        assertFalse("gleicher Stand", feed.update(true, snapshot(listOf(1, 2, 3, 4)), 133))
        assertTrue("Fenster bewegt", feed.update(true, snapshot(listOf(1, 2, 3, 9)), 166))
        assertTrue("Fenster zu", feed.update(true, null, 200))
        assertNull(feed.current)
    }

    @Test
    fun feedForgetsExactlyAtTheLimit() {
        val feed = WindowFeed(forgetAfterMs = 400)
        feed.update(true, snapshot(listOf(1, 2, 3, 4)), 0)
        assertFalse(feed.update(false, null, 1_000))   // erste Lücke: Frist beginnt hier
        assertFalse("399 ms später noch da", feed.update(false, null, 1_399))
        assertNotNull(feed.current)
        assertTrue("400 ms später weg", feed.update(false, null, 1_400))
        assertNull(feed.current)
    }

    @Test
    fun aReadStripInBetweenRestartsTheGap() {
        val feed = WindowFeed(forgetAfterMs = 400)
        feed.update(true, snapshot(listOf(1, 2, 3, 4)), 0)
        feed.update(false, null, 100)
        feed.update(true, snapshot(listOf(1, 2, 3, 4)), 300)
        assertFalse("Lücke beginnt neu bei 350", feed.update(false, null, 350))
        assertFalse(feed.update(false, null, 700))
        assertNotNull(feed.current)
        assertTrue(feed.update(false, null, 750))
    }

    @Test
    fun feedClearForgetsAtOnce() {
        val feed = WindowFeed()
        assertFalse("war leer", feed.clear())
        feed.update(true, snapshot(listOf(1, 2, 3, 4)), 0)
        assertTrue(feed.clear())
        assertNull(feed.current)
    }

    // ---- Abbildung auf die Ansicht -------------------------------------------------------------------------------

    private fun assertRect(expected: List<Float>, rects: WindowViewRects, index: Int = 0) {
        for (e in 0 until 4) assertEquals("Wert $e", expected[e], rects.rects[4 * index + e], 0.01f)
    }

    @Test
    fun mappingWithoutLetterbox() {
        // Bild = Bildschirm 2000 x 1000, Ansicht 1000 x 500: Faktor 0,5. Fenster von 20 % bis 40 % der Breite, 10 % bis 90 % der Höhe.
        val s = snapshot(listOf(51, 26, 102, 230))
        val v = WindowGeometry.toView(s, 1000, 500, false)
        assertEquals(1, v.count)
        assertRect(listOf(51 / 255f * 1000, 26 / 255f * 500, 102 / 255f * 1000, 230 / 255f * 500), v)
    }

    @Test
    fun mappingWithLetterboxAddsTheOffset() {
        // Bildschirm 2000 x 1000 in eine Ansicht 2000 x 1500: Faktor 1, oben und unten je 250 Rand.
        val s = snapshot(listOf(0, 0, 255, 255))
        val v = WindowGeometry.toView(s, 2000, 1500, false)
        assertRect(listOf(0f, 250f, 2000f, 1250f), v)
        // Pillarbox: Ansicht 3000 x 1000, links und rechts je 500 Rand
        val w = WindowGeometry.toView(s, 3000, 1000, false)
        assertRect(listOf(500f, 0f, 2500f, 1000f), w)
    }

    @Test
    fun mappingInFullscreenHasNoOffset() {
        val s = snapshot(listOf(0, 0, 255, 255))
        val v = WindowGeometry.toView(s, 2000, 1500, true)
        assertRect(listOf(0f, 0f, 2000f, 1000f), v)
    }

    @Test
    fun mappingUsesThePictureSizeInsideTheScreen() {
        // Das Spielbild ist kleiner als der X-Bildschirm (1000 x 500 von 2000 x 1000): ganze Breite des Bilds = halbe des Bildschirms.
        val s = snapshot(listOf(0, 0, 255, 255), pw = 1000, ph = 500, sw = 2000, sh = 1000)
        val v = WindowGeometry.toView(s, 2000, 1000, false)
        assertRect(listOf(0f, 0f, 1000f, 500f), v)
    }

    @Test
    fun mappingWithoutASizeGivesNothing() {
        assertEquals(0, WindowGeometry.toView(snapshot(listOf(0, 0, 255, 255)), 0, 500, false).count)
        assertEquals(0, WindowGeometry.toView(snapshot(listOf(0, 0, 255, 255), sw = 0), 500, 500, false).count)
    }

    // ---- Überlappung -----------------------------------------------------------------------------------------------

    private fun view(vararg rects: List<Float>) = WindowViewRects(rects.size, rects.flatMap { it }.toFloatArray())

    private fun share(l: Float, t: Float, r: Float, b: Float, vararg windows: List<Float>) =
        WindowGeometry.coveredShare(l, t, r, b, view(*windows))

    @Test
    fun noOverlapAndFullOverlap() {
        assertEquals(0f, share(0f, 0f, 10f, 10f), 0f)
        assertEquals(0f, share(0f, 0f, 10f, 10f, listOf(20f, 20f, 30f, 30f)), 0f)
        assertEquals(1f, share(0f, 0f, 10f, 10f, listOf(-5f, -5f, 15f, 15f)), 0.0001f)
        assertEquals(1f, share(0f, 0f, 10f, 10f, listOf(0f, 0f, 10f, 10f)), 0.0001f)
    }

    @Test
    fun edgesThatOnlyTouchDoNotCount() {
        assertEquals(0f, share(0f, 0f, 10f, 10f, listOf(10f, 0f, 20f, 10f)), 0f)
        assertEquals(0f, share(0f, 0f, 10f, 10f, listOf(0f, 10f, 10f, 20f)), 0f)
    }

    @Test
    fun partialOverlapIsTheShareOfTheElementArea() {
        assertEquals(0.5f, share(0f, 0f, 10f, 10f, listOf(5f, -3f, 30f, 30f)), 0.0001f)
        assertEquals(0.25f, share(0f, 0f, 10f, 10f, listOf(5f, 5f, 30f, 30f)), 0.0001f)
        assertEquals(0.3f, share(0f, 0f, 10f, 10f, listOf(7f, -1f, 20f, 11f)), 0.0001f)
    }

    @Test
    fun overlappingWindowsAreNotCountedTwice() {
        // Zwei Fenster, die sich selbst überlappen: die Fläche zählt einmal
        val s = share(0f, 0f, 10f, 10f, listOf(0f, 0f, 6f, 10f), listOf(4f, 0f, 10f, 10f))
        assertEquals(1f, s, 0.0001f)
        val t = share(0f, 0f, 10f, 10f, listOf(0f, 0f, 6f, 5f), listOf(3f, 2f, 9f, 8f))
        // A: 6x5 = 30; B: 6x6 = 36; Überlappung 3x3 = 9 -> 57 von 100
        assertEquals(0.57f, t, 0.0001f)
    }

    @Test
    fun threeWindowsAtTheCorners() {
        val s = share(
            0f, 0f, 10f, 10f,
            listOf(0f, 0f, 5f, 5f), listOf(5f, 5f, 10f, 10f), listOf(2f, 2f, 8f, 8f),
        )
        // 25 + 25 + (36 - overlap with the first two: 3x3 + 3x3 = 18) = 68
        assertEquals(0.68f, s, 0.0001f)
    }

    @Test
    fun emptyElementHasNoShare() {
        assertEquals(0f, share(5f, 5f, 5f, 9f, listOf(0f, 0f, 10f, 10f)), 0f)
    }

    // ---- Regel -----------------------------------------------------------------------------------------------------

    @Test
    fun thresholdIsMoreThanTenPercent() {
        assertFalse("genau 10 %", WindowHideRule.covers(WindowHideMode.COVERED, 0.10f, 1))
        assertTrue(WindowHideRule.covers(WindowHideMode.COVERED, 0.1001f, 1))
        assertFalse(WindowHideRule.covers(WindowHideMode.COVERED, 0.09f, 1))
        assertTrue(WindowHideRule.covers(WindowHideMode.COVERED, 1f, 1))
        assertFalse("kein Fenster", WindowHideRule.covers(WindowHideMode.COVERED, 1f, 0))
        assertEquals(0.10f, WindowHideRule.COVER_SHARE, 0f)
    }

    @Test
    fun allModeHidesWhenAnyWindowIsOpen() {
        assertTrue(WindowHideRule.covers(WindowHideMode.ALL, 0f, 1))
        assertTrue(WindowHideRule.covers(WindowHideMode.ALL, 0f, 4))
        assertFalse(WindowHideRule.covers(WindowHideMode.ALL, 1f, 0))
    }

    @Test
    fun offModeNeverHides() {
        assertFalse(WindowHideRule.covers(WindowHideMode.OFF, 1f, 4))
    }

    @Test
    fun modesCycleAndAreStoredByKey() {
        assertEquals(WindowHideMode.ALL, WindowHideMode.COVERED.next())
        assertEquals(WindowHideMode.OFF, WindowHideMode.ALL.next())
        assertEquals(WindowHideMode.COVERED, WindowHideMode.OFF.next())
        for (m in WindowHideMode.entries) assertEquals(m, WindowHideMode.fromKey(m.key))
        assertEquals("unbekannt: Standard", WindowHideMode.COVERED, WindowHideMode.fromKey("xyz"))
        assertEquals(WindowHideMode.COVERED, WindowHideMode.fromKey(null))
        assertEquals(WindowHideMode.COVERED, WindowHideMode.DEFAULT)
        assertEquals(listOf("Überdeckte", "Alle außer ESC/Tastatur", "Aus"), WindowHideMode.entries.map { it.label })
    }

    // ---- Hysterese ------------------------------------------------------------------------------------------------

    @Test
    fun hidesAtOnceAndShowsOnlyAfterTheDelay() {
        val tracker = WindowHideTracker(showDelayMs = 150)
        val key = Any()
        assertFalse("nie überdeckt", tracker.hidden(key, covered = false, held = false, nowMs = 0))
        assertTrue("sofort weg", tracker.hidden(key, covered = true, held = false, nowMs = 1_000))
        assertTrue(tracker.hidden(key, covered = true, held = false, nowMs = 1_500))
        // frei: erst 150 ms später wieder da
        assertTrue("frei, aber noch gesperrt", tracker.hidden(key, covered = false, held = false, nowMs = 2_000))
        assertTrue(tracker.hidden(key, covered = false, held = false, nowMs = 2_149))
        assertFalse("150 ms frei: wieder da", tracker.hidden(key, covered = false, held = false, nowMs = 2_150))
        assertFalse(tracker.hidden(key, covered = false, held = false, nowMs = 2_151))
    }

    @Test
    fun coveringAgainRestartsTheWait() {
        val tracker = WindowHideTracker(showDelayMs = 150)
        val key = Any()
        tracker.hidden(key, true, false, 0)
        assertTrue(tracker.hidden(key, false, false, 100))          // Wartezeit beginnt bei 100
        assertTrue(tracker.hidden(key, true, false, 200))           // wieder überdeckt
        assertTrue(tracker.hidden(key, false, false, 250))          // beginnt neu bei 250
        assertTrue("sonst wäre es bei 250 ab 100 schon da", tracker.hidden(key, false, false, 399))
        assertFalse(tracker.hidden(key, false, false, 400))
    }

    @Test
    fun aLongPauseBetweenEvaluationsDoesNotSkipTheWait() {
        // Die Ansicht zeichnet 2 s nicht; das erste Bild nach der Freigabe zählt als Beginn der Wartezeit.
        val tracker = WindowHideTracker(showDelayMs = 150)
        val key = Any()
        tracker.hidden(key, true, false, 0)
        assertTrue("erste Auswertung nach 2 s: noch gesperrt", tracker.hidden(key, false, false, 2_000))
        assertFalse(tracker.hidden(key, false, false, 2_150))
    }

    @Test
    fun aRunningTouchKeepsTheElementVisible() {
        val tracker = WindowHideTracker(showDelayMs = 150)
        val key = Any()
        assertFalse("überdeckt, aber Finger liegt drauf", tracker.hidden(key, covered = true, held = true, nowMs = 0))
        assertFalse(tracker.hidden(key, covered = true, held = true, nowMs = 5_000))
        // losgelassen: jetzt weg
        assertTrue(tracker.hidden(key, covered = true, held = false, nowMs = 5_010))
    }

    @Test
    fun anElementHiddenBeforeTheTouchIsNotRevivedByHolding() {
        // Verdeckt und ausgeblendet; ein Finger kann gar nicht erst drauf (Berührung ging an die Maus). Hält ein anderer Finger es
        // (held), bleibt es sichtbar, danach wieder weg.
        val tracker = WindowHideTracker(showDelayMs = 150)
        val key = Any()
        assertTrue(tracker.hidden(key, true, false, 0))
        assertFalse(tracker.hidden(key, true, true, 10))
        assertTrue(tracker.hidden(key, true, false, 20))
    }

    @Test
    fun heldElementThatGetsFreeStillWaitsForTheDelayAfterRelease() {
        val tracker = WindowHideTracker(showDelayMs = 150)
        val key = Any()
        tracker.hidden(key, true, false, 0)                          // weg
        assertFalse("Finger drauf, frei", tracker.hidden(key, false, true, 100))
        assertTrue("losgelassen, Wartezeit läuft ab 100", tracker.hidden(key, false, false, 200))
        assertFalse(tracker.hidden(key, false, false, 250))
    }

    @Test
    fun elementsAreIndependent() {
        val tracker = WindowHideTracker(showDelayMs = 150)
        val a = Any()
        val b = Any()
        assertTrue(tracker.hidden(a, true, false, 0))
        assertFalse(tracker.hidden(b, false, false, 0))
        assertTrue(tracker.hidden(a, false, false, 10))
        assertFalse(tracker.hidden(b, false, false, 10))
    }

    @Test
    fun nextChangeTellsWhenToDrawAgain() {
        val tracker = WindowHideTracker(showDelayMs = 150)
        val key = Any()
        assertEquals(-1L, tracker.nextChangeIn(0))
        tracker.hidden(key, true, false, 0)
        assertEquals("überdeckt: nichts wartet", -1L, tracker.nextChangeIn(0))
        tracker.hidden(key, false, false, 100)
        assertEquals(150L, tracker.nextChangeIn(100))
        assertEquals(50L, tracker.nextChangeIn(200))
        assertEquals("fällig", 0L, tracker.nextChangeIn(400))
        tracker.hidden(key, false, false, 250)
        assertEquals("wieder sichtbar: nichts wartet", -1L, tracker.nextChangeIn(250))
    }

    @Test
    fun endPassForgetsElementsThatWereNotAsked() {
        val tracker = WindowHideTracker(showDelayMs = 150)
        val gone = Any()
        val stays = Any()
        tracker.beginPass()
        tracker.hidden(gone, true, false, 0)
        tracker.hidden(stays, true, false, 0)
        tracker.endPass()
        tracker.hidden(gone, false, false, 10)                       // wartet auf Wiedereinblenden
        tracker.beginPass()
        tracker.hidden(stays, true, false, 20)                       // das andere Element gibt es nicht mehr
        tracker.endPass()
        assertEquals("der entfernte Eintrag erzeugt keine Zeichenaufträge", -1L, tracker.nextChangeIn(1_000))
        assertTrue(tracker.hidden(stays, true, false, 30))
    }

    @Test
    fun resetForgetsEverything() {
        val tracker = WindowHideTracker(showDelayMs = 150)
        val key = Any()
        tracker.hidden(key, true, false, 0)
        tracker.reset()
        assertFalse(tracker.hidden(key, false, false, 10))
        assertEquals(-1L, tracker.nextChangeIn(10))
    }

    @Test
    fun theDefaultDelayIsAbout150Ms() {
        assertEquals(150L, WindowHideTracker.SHOW_DELAY_MS)
        assertSame(WindowHideMode.COVERED, WindowHideMode.DEFAULT)
    }
}
