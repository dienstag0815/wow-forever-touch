package app.gamenative.ui.screen.wow.voice

import app.gamenative.cooldown.CooldownStrip
import com.winlator.xserver.XKeycode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat line must never reach the game as key bindings: letters are only typed while the addon reports the chat box open
 * (see [ChatGate]). The fake game below opens the box on an Enter press when it is closed, closes it on one when it is open.
 */
class VoiceSenderGateTest {
    private class Recorder : KeySink {
        val events = ArrayList<String>()

        override fun press(key: XKeycode, keysym: Int) {
            events.add("+${key.name}:$keysym")
        }

        override fun release(key: XKeycode) {
            events.add("-${key.name}")
        }

        val enterPresses: Int get() = events.count { it == "+KEY_ENTER:0" }
        val letterPresses: Int get() = events.count { it.startsWith("+KEY_") && it != "+KEY_ENTER:0" && !it.startsWith("+KEY_SHIFT") }
    }

    /** A game whose chat box toggles on Enter; [swallowFirst] Enter presses are lost (the game was not ready). */
    private class FakeGame(
        private val sink: Recorder,
        var open: Boolean = false,
        private val swallowFirst: Int = 0,
        private val aware: Boolean = true,
    ) : ChatGate {
        private var seenEnters = 0
        private var letters = 0
        var closeAfterLetters = Int.MAX_VALUE

        override fun isChatOpen(): Boolean? {
            apply()
            return if (aware) open else null
        }

        private fun apply() {
            val enters = sink.enterPresses
            while (seenEnters < enters) {
                seenEnters++
                if (seenEnters > swallowFirst) open = !open
            }
            letters = sink.letterPresses
            if (letters >= closeAfterLetters) open = false
        }
    }

    private fun sender(sink: Recorder, gate: ChatGate, pauses: MutableList<Long> = ArrayList()) =
        VoiceTextSender(sink, sleep = { pauses.add(it) }, gate = gate)

    @Test
    fun closedBoxIsOpenedThenTextIsTypedThenSent() {
        val rec = Recorder()
        val game = FakeGame(rec)
        val result = sender(rec, game).send("Hi", UmlautMode.DIRECT)
        assertEquals(SendResult.SENT, result)
        assertEquals(2, rec.enterPresses)
        assertEquals("+KEY_ENTER:0", rec.events.first())
        assertEquals(2, rec.letterPresses)
        game.isChatOpen() // the fake game applies the closing Enter when asked
        assertFalse(game.open) // the closing Enter sent the line and closed the box
    }

    @Test
    fun noLetterIsTypedBeforeTheBoxIsReportedOpen() {
        val rec = Recorder()
        // The game swallows the first Enter; the sender sees "closed" for the whole wait and presses a second Enter.
        val game = FakeGame(rec, swallowFirst = 1)
        val result = sender(rec, game).send("Hi", UmlautMode.DIRECT)
        assertEquals(SendResult.SENT, result)
        val firstLetter = rec.events.indexOfFirst { it.startsWith("+KEY_H") }
        val enterBeforeLetter = rec.events.subList(0, firstLetter).count { it == "+KEY_ENTER:0" }
        assertEquals(2, enterBeforeLetter)
    }

    @Test
    fun boxThatNeverOpensSendsNoLettersAtAll() {
        val rec = Recorder()
        val game = FakeGame(rec, swallowFirst = 99)
        val result = sender(rec, game).send("Hallo Welt", UmlautMode.DIRECT)
        assertEquals(SendResult.CHAT_NOT_OPEN, result)
        assertEquals(0, rec.letterPresses)
        assertEquals(VoiceTextSender.OPEN_ATTEMPTS, rec.enterPresses)
    }

    @Test
    fun boxAlreadyOpenIsEmptiedAndNotClosedByEnter() {
        val rec = Recorder()
        val game = FakeGame(rec, open = true)
        val result = sender(rec, game).send("Hi", UmlautMode.DIRECT)
        assertEquals(SendResult.SENT, result)
        // Ctrl+A, Backspace first; the only Enter is the closing one.
        assertEquals(listOf("+KEY_CTRL_L:0", "+KEY_A:97", "-KEY_A", "-KEY_CTRL_L", "+KEY_BKSP:0", "-KEY_BKSP"), rec.events.take(6))
        assertEquals(1, rec.enterPresses)
        assertEquals("+KEY_ENTER:0", rec.events[rec.events.size - 2])
    }

    @Test
    fun boxClosingWhileTypingStopsTheLineWithoutClosingEnter() {
        val rec = Recorder()
        val game = FakeGame(rec)
        game.closeAfterLetters = 7
        val result = sender(rec, game).send("abcdefghijklmnopqrstuvwxyz", UmlautMode.DIRECT)
        assertEquals(SendResult.CHAT_NOT_OPEN, result)
        // Stopped at the next look (every CHECK_EVERY letters): far fewer than all 26 letters, and only the opening Enter.
        assertTrue(rec.letterPresses < 26)
        assertTrue(rec.letterPresses <= 7 + VoiceTextSender.CHECK_EVERY)
        assertEquals(1, rec.enterPresses)
    }

    @Test
    fun boxClosedBeforeTheClosingEnterIsNotOpenedAgain() {
        val rec = Recorder()
        val game = FakeGame(rec)
        game.closeAfterLetters = 2 // closes after the last letter of "Hi" (the look happens only every few letters)
        val result = sender(rec, game).send("Hi", UmlautMode.DIRECT)
        assertEquals(SendResult.CHAT_NOT_OPEN, result)
        assertEquals(1, rec.enterPresses)
    }

    @Test
    fun noAnswerFromTheGateMeansNoSecondEnter() {
        val rec = Recorder()
        // The strip is lost right after the first Enter: unknown must not trigger a second (toggling) Enter.
        var calls = 0
        val gate = object : ChatGate {
            override fun isChatOpen(): Boolean? {
                calls++
                return if (rec.enterPresses == 0) false else null
            }
        }
        val result = sender(rec, gate).send("Hi", UmlautMode.DIRECT)
        assertEquals(SendResult.CHAT_NOT_OPEN, result)
        assertEquals(1, rec.enterPresses)
        assertEquals(0, rec.letterPresses)
        assertTrue(calls > 1)
    }

    @Test
    fun unknownStateFallsBackToTheBlindSequence() {
        val rec = Recorder()
        val game = FakeGame(rec, aware = false)
        val pauses = ArrayList<Long>()
        val result = sender(rec, game, pauses).send("Hi", UmlautMode.DIRECT)
        assertEquals(SendResult.SENT, result)
        assertEquals(2, rec.enterPresses)
        assertEquals(2, rec.letterPresses)
        assertTrue(pauses.contains(VoiceTextSender.OPEN_CHAT_MS))
    }

    @Test
    fun cancelledGatedSenderStopsWithoutLetters() {
        val rec = Recorder()
        val game = FakeGame(rec)
        val result = VoiceTextSender(rec, sleep = {}, isCancelled = { true }, gate = game).send("Hi", UmlautMode.DIRECT)
        assertEquals(SendResult.CANCELLED, result)
        assertEquals(0, rec.letterPresses)
    }

    @Test
    fun emptyTextTypesNothing() {
        val rec = Recorder()
        val result = sender(rec, FakeGame(rec)).send("  ", UmlautMode.DIRECT)
        assertEquals(SendResult.EMPTY, result)
        assertTrue(rec.events.isEmpty())
    }

    @Test
    fun chatFlagsOfTheStrip() {
        // An addon that does not know the flag says nothing, even though the open bit is clear.
        assertNull(CooldownStrip.chatOpenFrom(0))
        assertNull(CooldownStrip.chatOpenFrom(CooldownStrip.I_SECRET))
        assertEquals(false, CooldownStrip.chatOpenFrom(CooldownStrip.I_CHAT_AWARE))
        assertEquals(true, CooldownStrip.chatOpenFrom(CooldownStrip.I_CHAT_AWARE or CooldownStrip.I_CHAT_OPEN))
        assertEquals(true, CooldownStrip.chatOpenFrom(CooldownStrip.I_CHAT_AWARE or CooldownStrip.I_CHAT_OPEN or CooldownStrip.I_SECRET))
    }

    @Test
    fun defaultVoiceLanguageIsEnglish() {
        assertEquals(VoiceLanguage.EN, VoiceSettings.DEFAULT_LANGUAGE)
    }
}
