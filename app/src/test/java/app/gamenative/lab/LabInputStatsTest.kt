package app.gamenative.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Abschnitt "Input" des Labor-Protokolls: Zähler je Quelle, Fokusfenster, Fokuswechsel, Tastatur; nie Zeichen. */
class LabInputStatsTest {
    private var clock = 1_000_000L
    private fun stats() = LabInputStats(startedMs = 1_000_000L, nowMs = { clock })
    private val bnet = LabFocus(0x2000009, "battle.net.exe", "Battle.net")
    private val desktop = LabFocus(0x80000a, "explorer.exe", "")

    private fun LabInputStats.line(prefix: String): String = lines().first { it.trimStart().startsWith(prefix) }

    @Test
    fun countsPressesPerSource() {
        val s = stats()
        repeat(3) { s.onKeyPress(LabInputSource.ANDROID, bnet, keyboardVisible = true) }
        repeat(2) { s.onKeyPress(LabInputSource.BAR, bnet, keyboardVisible = false) }
        s.onKeyPress(LabInputSource.TEXT, bnet, keyboardVisible = false)
        assertEquals("Key presses sent to the X server: 6", s.lines().first())
        assertTrue(s.line("android").endsWith(": 3"))
        assertTrue(s.line("bar").endsWith(": 2"))
        assertTrue(s.line("text field").endsWith(": 1"))
        assertTrue(s.line("while the Android keyboard was visible").endsWith(": 3"))
    }

    @Test
    fun keysWithoutAFocusedWindowAreCountedAndNamed() {
        val s = stats()
        s.onKeyPress(LabInputSource.ANDROID, null, keyboardVisible = false)
        assertTrue(s.line("without a focused X window").endsWith(": 1"))
        assertTrue(s.line("X window with the input focus at the first key press").contains(LabFocus.NONE))
    }

    @Test
    fun firstAndLastFocusWindowAreKept() {
        val s = stats()
        clock += 2_000
        s.onKeyPress(LabInputSource.BAR, desktop, false)
        clock += 3_000
        s.onKeyPress(LabInputSource.BAR, bnet, false)
        val first = s.line("X window with the input focus at the first key press")
        val last = s.line("X window with the input focus at the last key press")
        assertTrue(first, first.contains("class=explorer.exe") && first.contains("+2.0s"))
        assertTrue(last, last.contains("class=battle.net.exe") && last.contains("+5.0s"))
    }

    @Test
    fun noKeyPressSaysSo() {
        val s = stats()
        assertTrue(s.line("X window with the input focus at the first key press").endsWith("no key press"))
        assertTrue(s.line("Key presses").endsWith(": 0") || s.lines().first().endsWith(": 0"))
    }

    @Test
    fun focusChangesAreListedOnceEachWithTime() {
        val s = stats()
        s.sample(desktop, false)
        clock += 1_500
        s.sample(desktop, false)
        s.sample(bnet, false)
        clock += 500
        s.sample(null, false)
        assertTrue(s.line("Focus changes seen").endsWith(": 3"))
        val listed = s.lines().filter { it.startsWith("  +") }
        assertEquals(3, listed.size)
        assertTrue(listed[0].contains("+0.0s") && listed[0].contains("explorer.exe"))
        assertTrue(listed[1].contains("+1.5s") && listed[1].contains("battle.net.exe"))
        assertTrue(listed[2].contains("+2.0s") && listed[2].contains(LabFocus.NONE))
    }

    @Test
    fun theListOfFocusChangesIsCapped() {
        val s = stats()
        for (i in 0 until LabInputStats.MAX_CHANGES + 10) s.sample(LabFocus(i, "c$i", ""), false)
        assertEquals(LabInputStats.MAX_CHANGES, s.lines().count { it.startsWith("  +") })
        assertTrue(s.lines().any { it.contains("10 more not listed") })
    }

    @Test
    fun keyboardShownIsCountedPerAppearance() {
        val s = stats()
        s.sample(bnet, false)
        s.sample(bnet, true)
        s.sample(bnet, true)
        s.sample(bnet, false)
        s.sample(bnet, true)
        assertTrue(s.line("Android keyboard became visible").startsWith("Android keyboard became visible: 2 time(s)"))
    }

    @Test
    fun imeCallsAreCountedWithoutContent() {
        val s = stats()
        s.onImeCall(LabImeCall.COMMIT_TEXT, 5)
        s.onImeCall(LabImeCall.COMMIT_TEXT, 1)
        s.onImeCall(LabImeCall.SET_COMPOSING)
        s.onImeCall(LabImeCall.DELETE_SURROUNDING)
        assertTrue(s.line("commitText").endsWith(": 2"))
        assertTrue(s.line("setComposingText").endsWith(": 1"))
        assertTrue(s.line("finishComposingText").endsWith(": 0"))
        assertTrue(s.line("characters delivered by commitText").endsWith(": 6"))
    }

    @Test
    fun textSendsAreCountedByNumbersOnly() {
        val s = stats()
        s.onTextSent(12, 1)
        s.onTextSent(3, 0)
        assertEquals("Text field: 2 send(s), 15 character(s) typed, 1 skipped (no key for them)", s.line("Text field"))
    }

    @Test
    fun theWindowNameIsCleanedAndShortened() {
        val dirty = LabFocus(1, "cls\u0000\n", "Näme" + "x".repeat(200))
        val text = dirty.text()
        assertFalse(text.contains('\u0000') || text.contains('\n') || text.contains('ä'))
        assertTrue(text.length < 140)
    }

    @Test
    fun protocolBlockHasTheInputHeading() {
        val block = LabProtocol.inputBlock(stats().lines())
        assertTrue(block.contains("\n== Input ==\n"))
        assertTrue(block.contains("Key presses sent to the X server: 0"))
        assertTrue(LabProtocol.inputBlock(emptyList()).contains("(no input counters)"))
    }

    @Test
    fun theSectionMentionsNoTypedCharacters() {
        val s = stats()
        // Gesendet wird "Geheim1@": nichts davon darf im Abschnitt landen.
        s.onKeyPress(LabInputSource.TEXT, bnet, false)
        s.onImeCall(LabImeCall.COMMIT_TEXT, 8)
        s.onTextSent(8, 0)
        val all = s.lines().joinToString("\n")
        assertFalse(all.contains("Geheim"))
        assertFalse(all.contains("@"))
    }
}
