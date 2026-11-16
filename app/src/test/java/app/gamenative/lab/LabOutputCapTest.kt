package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Obergrenze der Wine-Ausgabe: Anfang und Ende bleiben, die Mitte entfällt und wird vermerkt (Punkt 2 von labstart2). */
class LabOutputCapTest {
    @Test
    fun shortOutputIsKeptWhole() {
        val cap = LabOutputCap(headBytes = 100, tailBytes = 50)
        assertEquals("one", cap.add("one"))
        assertEquals("two", cap.add("two"))
        assertEquals("", cap.tailText())
        assertEquals(0, cap.omittedLines)
    }

    @Test
    fun longOutputKeepsTheStartAndTheEndAndNotesTheCut() {
        val cap = LabOutputCap(headBytes = 30, tailBytes = 30)
        val written = mutableListOf<String>()
        for (i in 1..100) cap.add("line%03d".format(i))?.let { written += it }
        // Anfang: 8 Bytes je Zeile (7 Zeichen + Umbruch), 30 Bytes = 3 Zeilen.
        assertEquals(listOf("line001", "line002", "line003"), written)
        val tail = cap.tailText()
        assertTrue(tail, tail.startsWith("(middle of the Wine output cut: "))
        val kept = tail.lines().filter { it.startsWith("line") }
        assertEquals(listOf("line098", "line099", "line100"), kept)
        assertEquals(94, cap.omittedLines)
        assertEquals(94L * 8, cap.omittedBytes)
        assertEquals(100, cap.totalLines)
    }

    @Test
    fun withoutOverflowThereIsNoNote() {
        val cap = LabOutputCap(headBytes = 8, tailBytes = 100)
        assertEquals("line001", cap.add("line001"))
        assertNull(cap.add("line002"))
        assertNull(cap.add("line003"))
        val tail = cap.tailText()
        assertEquals("line002\nline003\n", tail)
        assertEquals(0, cap.omittedLines)
    }

    @Test
    fun aHugeSingleLineIsShortened() {
        val cap = LabOutputCap()
        val out = cap.add("x".repeat(100_000))!!
        assertTrue(out.length < LabOutputCap.MAX_LINE + 50)
        assertTrue(out.endsWith("...(line cut)"))
    }

    @Test
    fun memoryStaysBoundedForHugeOutput() {
        val cap = LabOutputCap(headBytes = 1_000, tailBytes = 1_000)
        repeat(200_000) { cap.add("some wine message number $it") }
        assertTrue(cap.tailText().length < 1_300)
        assertEquals(200_000, cap.totalLines)
    }

    @Test
    fun theDefaultsKeepTheOldTotalOfThreeMegabytes() {
        assertEquals(3_000_000L, LabOutputCap.DEFAULT_HEAD_BYTES + LabOutputCap.DEFAULT_TAIL_BYTES)
    }
}
