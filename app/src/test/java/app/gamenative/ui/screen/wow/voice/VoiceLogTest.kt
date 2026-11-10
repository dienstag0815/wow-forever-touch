package app.gamenative.ui.screen.wow.voice

import org.junit.Rule
import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLogTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private fun entry(n: Int) = VoiceLogEntry("heard $n", "corrected $n")

    @Test
    fun newestEntryComesFirstAndTheListIsLimited() {
        var entries = emptyList<VoiceLogEntry>()
        for (n in 1..(VoiceLog.MAX_ENTRIES + 5)) entries = VoiceLog.add(entries, entry(n))
        assertEquals(VoiceLog.MAX_ENTRIES, entries.size)
        assertEquals("heard ${VoiceLog.MAX_ENTRIES + 5}", entries.first().heard)
        assertEquals("heard 6", entries.last().heard)
    }

    @Test
    fun encodeAndDecodeKeepAllFields() {
        val entries = listOf(
            VoiceLogEntry("dead Miles iron Ford", "Deadmines Ironforge", VoiceLog.OUTCOME_SENT, "dead Mines iron Forge | dead Miles"),
            VoiceLogEntry("Hallo", "Hallo"),
        )
        val back = VoiceLog.decode(VoiceLog.encode(entries))
        assertEquals(2, back.size)
        assertEquals("dead Miles iron Ford", back[0].heard)
        assertEquals("Deadmines Ironforge", back[0].corrected)
        assertEquals("gesendet", back[0].outcome)
        assertEquals("dead Mines iron Forge | dead Miles", back[0].alternatives)
        assertEquals("", back[1].outcome)
        assertEquals("", back[1].alternatives)
    }

    @Test
    fun separatorsInsideTextCannotBreakTheFormat() {
        val tricky = VoiceLogEntry("a\tb\nc\r\nd", "x\ty", "", "p\nq")
        val back = VoiceLog.decode(VoiceLog.encode(listOf(tricky, entry(1))))
        assertEquals(2, back.size)
        assertEquals("a b c  d", back[0].heard)
        assertEquals("x y", back[0].corrected)
        assertEquals("p q", back[0].alternatives)
        assertEquals("heard 1", back[1].heard)
    }

    @Test
    fun decodeToleratesEmptyAndShortLines() {
        assertTrue(VoiceLog.decode("").isEmpty())
        assertTrue(VoiceLog.decode("\n\n").isEmpty())
        val back = VoiceLog.decode("nur erkannt\n")
        assertEquals(1, back.size)
        assertEquals("nur erkannt", back[0].heard)
        assertEquals("", back[0].corrected)
    }

    @Test
    fun outcomeIsSetOnTheNewestEntryOnly() {
        val entries = VoiceLog.add(VoiceLog.add(emptyList(), entry(1)), entry(2))
        val updated = VoiceLog.withOutcome(entries, VoiceLog.OUTCOME_AGAIN)
        assertEquals("nochmal", updated[0].outcome)
        assertEquals("", updated[1].outcome)
        assertEquals("heard 2", updated[0].heard)
        assertTrue(VoiceLog.withOutcome(emptyList(), "x").isEmpty())
    }

    @Test
    fun lineShowsHeardAndCorrected() {
        assertEquals(
            "dead Miles → Deadmines  [verworfen]",
            VoiceLog.line(VoiceLogEntry("dead Miles", "Deadmines", VoiceLog.OUTCOME_DISCARDED)),
        )
        assertEquals("Hallo → (unverändert)", VoiceLog.line(VoiceLogEntry("Hallo", "Hallo")))
        assertEquals(
            "a → b\n    Alternativen: c | d",
            VoiceLog.line(VoiceLogEntry("a", "b", "", "c | d")),
        )
    }

    @Test
    fun formatNumbersTheEntriesForTheClipboard() {
        val text = VoiceLog.format(listOf(VoiceLogEntry("a", "b"), VoiceLogEntry("c", "c")))
        val lines = text.lines()
        assertTrue(lines[0].startsWith("Sprachprotokoll"))
        assertEquals("1. a → b", lines[1])
        assertEquals("2. c → (unverändert)", lines[2])
        assertEquals(3, lines.size)
    }
}
