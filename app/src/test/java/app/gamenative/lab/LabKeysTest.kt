package app.gamenative.lab

import app.gamenative.ui.screen.wow.voice.KeySink
import com.winlator.xserver.XKeycode
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tastenleiste und Text senden im Labor: Belegung, Tastenfolge, Zweimal-Tippen, und dass der neue Weg nie Zugangsdaten anfasst. */
class LabKeysTest {
    private class Recorder : KeySink {
        val events = ArrayList<String>()

        override fun press(key: XKeycode, keysym: Int) {
            events.add("+${key.name}:$keysym")
        }

        override fun release(key: XKeycode) {
            events.add("-${key.name}")
        }
    }

    private fun sender(r: Recorder, cancelled: () -> Boolean = { false }, slept: MutableList<Long>? = null) =
        LabKeySender(r, sleep = { slept?.add(it) }, isCancelled = cancelled, random = Random(1))

    // --- Belegung ---

    @Test
    fun everyBarKeyHasItsXKey() {
        assertEquals(LabChord(XKeycode.KEY_ESC, false), LabKeyMap.chord(LabBarKey.ESC))
        assertEquals(LabChord(XKeycode.KEY_TAB, false), LabKeyMap.chord(LabBarKey.TAB))
        assertEquals(LabChord(XKeycode.KEY_TAB, true), LabKeyMap.chord(LabBarKey.SHIFT_TAB))
        assertEquals(LabChord(XKeycode.KEY_ENTER, false), LabKeyMap.chord(LabBarKey.ENTER))
        assertEquals(LabChord(XKeycode.KEY_BKSP, false), LabKeyMap.chord(LabBarKey.BACKSPACE))
        assertEquals(LabChord(XKeycode.KEY_LEFT, false), LabKeyMap.chord(LabBarKey.LEFT))
        assertEquals(LabChord(XKeycode.KEY_RIGHT, false), LabKeyMap.chord(LabBarKey.RIGHT))
        assertEquals(LabChord(XKeycode.KEY_UP, false), LabKeyMap.chord(LabBarKey.UP))
        assertEquals(LabChord(XKeycode.KEY_DOWN, false), LabKeyMap.chord(LabBarKey.DOWN))
        assertEquals(LabBarKey.values().size, LabBarKey.values().map { LabKeyMap.chord(it) }.toSet().size)
    }

    @Test
    fun aPlainBarKeyIsPressedAndReleasedWithoutKeysym() {
        val r = Recorder()
        sender(r).tap(LabBarKey.ESC)
        assertEquals(listOf("+KEY_ESC:0", "-KEY_ESC"), r.events)
    }

    @Test
    fun shiftTabHoldsShiftAroundTab() {
        val r = Recorder()
        sender(r).tap(LabBarKey.SHIFT_TAB)
        assertEquals(listOf("+KEY_SHIFT_L:0", "+KEY_TAB:0", "-KEY_TAB", "-KEY_SHIFT_L"), r.events)
    }

    // --- Text ---

    @Test
    fun textTypesLettersDigitsAndShiftedSymbolsLikeTheGame() {
        val r = Recorder()
        val result = sender(r).typeText("aB1@._-".toCharArray())
        assertEquals(LabKeySender.TextResult(7, 0, false), result)
        assertEquals(
            listOf(
                "+KEY_A:97", "-KEY_A",
                "+KEY_SHIFT_L:0", "+KEY_B:66", "-KEY_B", "-KEY_SHIFT_L",
                "+KEY_1:49", "-KEY_1",
                "+KEY_SHIFT_L:0", "+KEY_2:64", "-KEY_2", "-KEY_SHIFT_L",
                "+KEY_PERIOD:46", "-KEY_PERIOD",
                "+KEY_SHIFT_L:0", "+KEY_MINUS:95", "-KEY_MINUS", "-KEY_SHIFT_L",
                "+KEY_MINUS:45", "-KEY_MINUS",
            ),
            r.events,
        )
    }

    @Test
    fun charactersWithoutAKeyAreSkippedAndCounted() {
        val r = Recorder()
        val result = sender(r).typeText("a\nb\u0007".toCharArray())
        assertEquals(LabKeySender.TextResult(2, 2, false), result)
        assertEquals(listOf("+KEY_A:97", "-KEY_A", "+KEY_B:98", "-KEY_B"), r.events)
    }

    @Test
    fun umlautsGoThroughTheCarrierKey() {
        val r = Recorder()
        val result = sender(r).typeText("ä".toCharArray())
        assertEquals(1, result.sent)
        assertEquals(listOf("+KEY_SCROLL_LOCK:228", "-KEY_SCROLL_LOCK"), r.events)
    }

    @Test
    fun cancellingStopsBeforeTheNextKeyAndReports() {
        val r = Recorder()
        var n = 0
        val result = sender(r, cancelled = { ++n > 2 }).typeText("abcd".toCharArray())
        assertTrue(result.cancelled)
        assertEquals(2, result.sent)
        assertEquals(4, r.events.size)
    }

    @Test
    fun textTimingIsHumanLikeAndInRange() {
        val slept = ArrayList<Long>()
        sender(Recorder(), slept = slept).typeText("abc".toCharArray())
        assertEquals(6, slept.size)
        assertTrue(slept.all { it in 20L..45L })
    }

    @Test
    fun emptyTextSendsNothing() {
        val r = Recorder()
        assertEquals(LabKeySender.TextResult(0, 0, false), sender(r).typeText(CharArray(0)))
        assertTrue(r.events.isEmpty())
    }

    // --- Beenden: zweimal tippen ---

    @Test
    fun exitNeedsASecondTapWithinTheWindow() {
        val first = LabConfirmTap.tap(null, 10_000)
        assertFalse(first.fire)
        assertEquals(10_000L, first.armedAtMs)
        val second = LabConfirmTap.tap(first.armedAtMs, 10_000 + LabConfirmTap.WINDOW_MS)
        assertTrue(second.fire)
        assertNull(second.armedAtMs)
    }

    @Test
    fun aLateSecondTapArmsAgainInsteadOfExiting() {
        val late = LabConfirmTap.tap(10_000, 10_000 + LabConfirmTap.WINDOW_MS + 1)
        assertFalse(late.fire)
        assertEquals(10_000 + LabConfirmTap.WINDOW_MS + 1, late.armedAtMs)
        assertFalse(LabConfirmTap.isArmed(10_000, 10_000 + LabConfirmTap.WINDOW_MS + 1))
        assertTrue(LabConfirmTap.isArmed(10_000, 10_500))
        assertFalse(LabConfirmTap.isArmed(null, 10_500))
    }

    // --- Zugangsdaten und Protokoll ---

    private fun source(path: String) = File("src/main/java/app/gamenative/$path").readText()

    @Test
    fun theNewPathNeverReachesStoredLoginDetails() {
        val forbidden = listOf("BattleNetSignIn", "loadSecret", "LoginFill.start", "LoginFill.executor", "LoginTypist(", "LoginJournal", "PrefManager", "SecretStore")
        for (path in listOf("lab/LabKeys.kt", "lab/LabInput.kt", "ui/screen/wow/lab/LabKeyBar.kt")) {
            val text = source(path)
            for (word in forbidden) assertFalse("$path mentions $word", text.contains(word))
        }
    }

    @Test
    fun theNewPathLogsOnlyTheCountOfSentCharacters() {
        val allowed = "Timber.i(\"Text gesendet: %d Zeichen\", result.sent)"
        // Anmelde-Knöpfe: ebenfalls nur die Zahl der Zeichen bzw. der Name der Ausnahme, nie ein Wert.
        val allowedSignIn = setOf(
            "Timber.i(\"Anmelde-Knopf gesendet: %d Zeichen\", result.sent)",
            "Timber.w(\"Anmelde-Knopf senden fehlgeschlagen: %s\", e.javaClass.simpleName)",
        )
        for (path in listOf("lab/LabKeys.kt", "lab/LabInput.kt", "ui/screen/wow/lab/LabKeyBar.kt")) {
            val calls = source(path).lines().filter { Regex("""\b(Timber|Log)\.\w+\(""").containsMatchIn(it) }.map { it.trim() }
            for (call in calls) {
                assertTrue("$path logs: $call", call == allowed || call in allowedSignIn || call.startsWith("Timber.w(\"Text senden fehlgeschlagen: %s\", e.javaClass.simpleName)"))
            }
        }
    }

    @Test
    fun theGuardStillBlocksStoredLoginDetailsInTheLab() {
        assertFalse(app.gamenative.ui.screen.wow.LoginGuard.mayType(LabIds.CONTAINER_ID, labActive = false))
        assertFalse(app.gamenative.ui.screen.wow.LoginGuard.mayType("wow_forever", labActive = true))
        assertTrue(app.gamenative.ui.screen.wow.LoginGuard.mayType("wow_forever", labActive = false))
    }

    @Test
    fun theBarOnlyDrawsInTheLabContainer() {
        val text = source("ui/screen/wow/lab/LabKeyBar.kt")
        assertTrue(text.contains("if (containerId != LabIds.CONTAINER_ID) return"))
    }
}
