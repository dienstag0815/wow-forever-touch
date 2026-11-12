package app.gamenative.ui.screen.wow

import app.gamenative.TestTexts
import app.gamenative.ui.components.TextRef
import app.gamenative.ui.screen.wow.voice.KeySink
import com.winlator.xserver.XKeycode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LoginTypingTest {
    private class Recorder : KeySink {
        val events = ArrayList<String>()

        override fun press(key: XKeycode, keysym: Int) {
            events.add("+${key.name}:$keysym")
        }

        override fun release(key: XKeycode) {
            events.add("-${key.name}")
        }
    }

    /** Feld leeren: Strg+A, Entf, Ende, Rücktasten (Länge + Zugabe). */
    private fun clearEvents(length: Int): List<String> {
        val out = arrayListOf(
            "+KEY_CTRL_L:0", "+KEY_A:97", "-KEY_A", "-KEY_CTRL_L",
            "+KEY_DEL:0", "-KEY_DEL",
            "+KEY_END:0", "-KEY_END",
        )
        repeat(length + LoginTypist.CLEAR_EXTRA) { out.add("+KEY_BKSP:0"); out.add("-KEY_BKSP") }
        return out
    }

    // --- Zeichen -> Taste ---

    @Test
    fun requiredPasswordCharactersHaveKeys() {
        val chars = "@._-!?#\$%&*+=0123456789abcXYZ ()[]{}/:;,'\"<>|~^`\\"
        for (ch in chars) assertNotNull("Zeichen '$ch' hat keine Taste", LoginKeys.strokeFor(ch))
    }

    @Test
    fun shiftedCharactersUseShiftOnTheUsKey() {
        val at = LoginKeys.strokeFor('@')!!
        assertEquals(XKeycode.KEY_2, at.key)
        assertTrue(at.shift)
        assertEquals('@'.code, at.keysym)
        val hash = LoginKeys.strokeFor('#')!!
        assertEquals(XKeycode.KEY_3, hash.key)
        assertTrue(hash.shift)
        val dollar = LoginKeys.strokeFor('$')!!
        assertEquals(XKeycode.KEY_4, dollar.key)
        assertTrue(dollar.shift)
        val percent = LoginKeys.strokeFor('%')!!
        assertEquals(XKeycode.KEY_5, percent.key)
        val amp = LoginKeys.strokeFor('&')!!
        assertEquals(XKeycode.KEY_7, amp.key)
        val star = LoginKeys.strokeFor('*')!!
        assertEquals(XKeycode.KEY_8, star.key)
        val plus = LoginKeys.strokeFor('+')!!
        assertEquals(XKeycode.KEY_EQUAL, plus.key)
        assertTrue(plus.shift)
        val question = LoginKeys.strokeFor('?')!!
        assertEquals(XKeycode.KEY_SLASH, question.key)
        assertTrue(question.shift)
        val bang = LoginKeys.strokeFor('!')!!
        assertEquals(XKeycode.KEY_1, bang.key)
        assertTrue(bang.shift)
        val underscore = LoginKeys.strokeFor('_')!!
        assertEquals(XKeycode.KEY_MINUS, underscore.key)
        assertTrue(underscore.shift)
    }

    @Test
    fun plainCharactersUseNoShift() {
        for (ch in ".-=") assertFalse("'$ch'", LoginKeys.strokeFor(ch)!!.shift)
        assertEquals(XKeycode.KEY_PERIOD, LoginKeys.strokeFor('.')!!.key)
        assertEquals(XKeycode.KEY_MINUS, LoginKeys.strokeFor('-')!!.key)
        assertEquals(XKeycode.KEY_EQUAL, LoginKeys.strokeFor('=')!!.key)
        assertEquals(XKeycode.KEY_7, LoginKeys.strokeFor('7')!!.key)
    }

    @Test
    fun caseIsKept() {
        val lower = LoginKeys.strokeFor('q')!!
        val upper = LoginKeys.strokeFor('Q')!!
        assertEquals(XKeycode.KEY_Q, lower.key)
        assertEquals(XKeycode.KEY_Q, upper.key)
        assertFalse(lower.shift)
        assertTrue(upper.shift)
        assertEquals('q'.code, lower.keysym)
        assertEquals('Q'.code, upper.keysym)
    }

    @Test
    fun umlautsGoThroughTheCarrierKeyWithTheirKeysym() {
        val ae = LoginKeys.strokeFor('ä')!!
        assertEquals(XKeycode.KEY_SCROLL_LOCK, ae.key)
        assertEquals(0xE4, ae.keysym)
        assertEquals(0xDF, LoginKeys.strokeFor('ß')!!.keysym)
        assertEquals(0xFC, LoginKeys.strokeFor('ü')!!.keysym)
        assertEquals(0x01000000 + 0x20AC, LoginKeys.strokeFor('€')!!.keysym)
    }

    @Test
    fun untypeableCharactersAreReportedNotSkipped() {
        assertNull(LoginKeys.strokeFor('\t'))
        assertNull(LoginKeys.strokeFor('\n'))
        assertNull(LoginKeys.strokeFor('\u0000'))
        assertNull(LoginKeys.strokeFor('\u0085'))
        // Emoji = Ersatzzeichenpaar
        assertNull(LoginKeys.strokeFor("😀"[0]))
        assertNull(LoginKeys.strokeFor("😀"[1]))
        assertFalse(LoginKeys.typeable("abc😀".toCharArray()))
        assertFalse(LoginKeys.typeable("ab\tc".toCharArray()))
        assertTrue(LoginKeys.typeable("Äb€c!".toCharArray()))
    }

    // --- Prüfung vor dem Tippen ---

    @Test
    fun checkAcceptsGoodData() {
        assertEquals(LoginCheck.OK, checkLogin(LoginAction.FULL, "a@example.org".toCharArray(), "Pw!1".toCharArray()))
    }

    @Test
    fun checkRejectsEmptyAndUntypeableFields() {
        val good = "x".toCharArray()
        assertEquals(LoginCheck.EMAIL_EMPTY, checkLogin(LoginAction.FULL, CharArray(0), good))
        assertEquals(LoginCheck.PASSWORD_EMPTY, checkLogin(LoginAction.FULL, good, CharArray(0)))
        assertEquals(LoginCheck.PASSWORD_UNTYPEABLE, checkLogin(LoginAction.FULL, good, "a😀".toCharArray()))
        assertEquals(LoginCheck.EMAIL_UNTYPEABLE, checkLogin(LoginAction.FULL, "a\tb".toCharArray(), good))
    }

    @Test
    fun checkOnlyLooksAtTheFieldsThatAreTyped() {
        val bad = "a😀".toCharArray()
        val good = "x".toCharArray()
        assertEquals(LoginCheck.OK, checkLogin(LoginAction.EMAIL_ONLY, good, bad))
        assertEquals(LoginCheck.OK, checkLogin(LoginAction.PASSWORD_ONLY, bad, good))
        assertEquals(LoginCheck.EMAIL_UNTYPEABLE, checkLogin(LoginAction.EMAIL_ONLY, bad, good))
        assertEquals(LoginCheck.PASSWORD_UNTYPEABLE, checkLogin(LoginAction.PASSWORD_ONLY, good, bad))
    }

    @Test
    fun messagesNameNeitherCharacterNorLength() {
        for (check in LoginCheck.entries) {
            val res = check.messageRes ?: continue
            val message = TestTexts.de(TextRef(res))
            assertFalse(message, message.any { it.isDigit() })
        }
    }

    // --- Ablauf ---

    @Test
    fun signInOrder_clearEmail_email_tab_clearPassword_password_enter() {
        val recorder = Recorder()
        val done = LoginTypist(recorder, sleep = {}).signIn("a@b".toCharArray(), "P1".toCharArray())
        assertTrue(done)
        val expected = ArrayList<String>()
        expected += clearEvents(3)
        expected += listOf(
            "+KEY_A:97", "-KEY_A",
            "+KEY_SHIFT_L:0", "+KEY_2:64", "-KEY_2", "-KEY_SHIFT_L",
            "+KEY_B:98", "-KEY_B",
            "+KEY_TAB:0", "-KEY_TAB",
        )
        expected += clearEvents(2)
        expected += listOf(
            "+KEY_SHIFT_L:0", "+KEY_P:80", "-KEY_P", "-KEY_SHIFT_L",
            "+KEY_1:49", "-KEY_1",
            "+KEY_ENTER:0", "-KEY_ENTER",
        )
        assertEquals(expected, recorder.events)
    }

    @Test
    fun emailOnlyTypesNoTabAndNoEnter() {
        val recorder = Recorder()
        assertTrue(LoginTypist(recorder, sleep = {}).run(LoginAction.EMAIL_ONLY, "ab".toCharArray(), "geheim".toCharArray()))
        assertEquals(clearEvents(2) + listOf("+KEY_A:97", "-KEY_A", "+KEY_B:98", "-KEY_B"), recorder.events)
        assertFalse(recorder.events.any { it.contains("TAB") || it.contains("ENTER") })
    }

    @Test
    fun passwordOnlyTypesNoEnterAndNothingOfTheEmail() {
        val recorder = Recorder()
        assertTrue(LoginTypist(recorder, sleep = {}).run(LoginAction.PASSWORD_ONLY, "mail".toCharArray(), "x".toCharArray()))
        assertEquals(clearEvents(1) + listOf("+KEY_X:120", "-KEY_X"), recorder.events)
    }

    @Test
    fun everyPressIsReleased() {
        val recorder = Recorder()
        LoginTypist(recorder, sleep = {}).signIn("Ä.b@example.org".toCharArray(), "A!b?#\$%&*+=-_1ß".toCharArray())
        val pressed = recorder.events.filter { it.startsWith("+") }.map { it.substring(1).substringBefore(':') }
        val released = recorder.events.filter { it.startsWith("-") }.map { it.substring(1) }
        assertEquals(pressed.sorted(), released.sorted())
        // Zwischen Drücken und Loslassen derselben Taste liegt nichts Fremdes außer Umschalt/Strg.
        var held = 0
        for (e in recorder.events) {
            if (e.startsWith("+")) held++ else held--
            assertTrue(held in 0..2)
        }
        assertEquals(0, held)
    }

    @Test
    fun cancelStopsBeforeTheEnterKey() {
        val recorder = Recorder()
        var cancelled = false
        // Abbruch nach dem ersten Zeichen des Passworts
        val sink = object : KeySink {
            override fun press(key: XKeycode, keysym: Int) {
                recorder.press(key, keysym)
                if (key == XKeycode.KEY_P) cancelled = true
            }

            override fun release(key: XKeycode) = recorder.release(key)
        }
        val result = LoginTypist(sink, sleep = {}, isCancelled = { cancelled }).signIn("a".toCharArray(), "pass".toCharArray())
        assertFalse(result)
        assertFalse(recorder.events.contains("+KEY_ENTER:0"))
    }

    @Test
    fun theTypistPausesBetweenKeysSoTheGameKeepsUp() {
        val pauses = ArrayList<Long>()
        LoginTypist(Recorder(), sleep = { pauses.add(it) }).signIn("a".toCharArray(), "b".toCharArray())
        assertTrue(pauses.any { it in LoginTypist.HOLD_MIN_MS..LoginTypist.HOLD_MAX_MS })
        assertTrue(pauses.count { it in LoginTypist.FIELD_PAUSE_MIN_MS..LoginTypist.FIELD_PAUSE_MAX_MS } >= 2)
        assertTrue(pauses.any { it in LoginTypist.BEFORE_ENTER_MIN_MS..LoginTypist.BEFORE_ENTER_MAX_MS })
    }

    @Test
    fun typingRhythmVariesLikeAHumanAndIsNotMachineFast() {
        val pauses = ArrayList<Long>()
        val text = "spieler.name@example.org".toCharArray()
        LoginTypist(Recorder(), sleep = { pauses.add(it) }, random = Random(42)).typeEmailOnly(text)
        val holds = pauses.filter { it in LoginTypist.HOLD_MIN_MS..LoginTypist.HOLD_MAX_MS }
        // Haltezeiten schwanken (kein Gleichtakt)
        assertTrue(holds.toSet().size > 5)
        // Gesamtdauer: höchstens ~9 Zeichen/s, nicht mehr ~50 wie vorher
        val totalMs = pauses.sum()
        assertTrue("zu schnell: $totalMs ms", totalMs >= text.size * (LoginTypist.HOLD_MIN_MS + LoginTypist.GAP_MIN_MS))
        // Mindestens eine Denkpause bei 24 Zeichen
        assertTrue(pauses.any { it > LoginTypist.GAP_MAX_MS })
    }

    @Test
    fun allPausesStayWithinTheirBounds() {
        val pauses = ArrayList<Long>()
        repeat(20) { seed ->
            LoginTypist(Recorder(), sleep = { pauses.add(it) }, random = Random(seed)).signIn("a@example.org".toCharArray(), "Pw1!".toCharArray())
        }
        assertTrue(pauses.all { it in LoginTypist.CLEAR_HOLD_MIN_MS..LoginTypist.FIELD_PAUSE_MAX_MS })
    }

    @Test
    fun sameSeedGivesSameRhythm() {
        fun run(seed: Int): List<Long> {
            val pauses = ArrayList<Long>()
            LoginTypist(Recorder(), sleep = { pauses.add(it) }, random = Random(seed)).signIn("x@y.z".toCharArray(), "abc".toCharArray())
            return pauses
        }
        assertEquals(run(7), run(7))
    }

    @Test
    fun wipeOverwritesBothFields() {
        val secret = BattleNetSignIn.Secret("mail@example.org".toCharArray(), "geheim".toCharArray())
        secret.wipe()
        assertTrue(secret.email.all { it == '\u0000' })
        assertTrue(secret.password.all { it == '\u0000' })
    }

    // --- Ausfüllen: E-Mail, Tab, Passwort, nie Enter ---

    @Test
    fun fillOrder_clearEmail_email_tab_clearPassword_password_noEnter() {
        val recorder = Recorder()
        assertTrue(LoginTypist(recorder, sleep = {}).fill("a@b".toCharArray(), "P1".toCharArray()))
        val expected = ArrayList<String>()
        expected += clearEvents(3)
        expected += listOf(
            "+KEY_A:97", "-KEY_A",
            "+KEY_SHIFT_L:0", "+KEY_2:64", "-KEY_2", "-KEY_SHIFT_L",
            "+KEY_B:98", "-KEY_B",
            "+KEY_TAB:0", "-KEY_TAB",
        )
        expected += clearEvents(2)
        expected += listOf("+KEY_SHIFT_L:0", "+KEY_P:80", "-KEY_P", "-KEY_SHIFT_L", "+KEY_1:49", "-KEY_1")
        assertEquals(expected, recorder.events)
        assertFalse(recorder.events.any { it.contains("ENTER") })
    }

    @Test
    fun fillIsAnActionOfItsOwnAndRunDispatchesToIt() {
        val recorder = Recorder()
        assertTrue(LoginTypist(recorder, sleep = {}).run(LoginAction.FILL, "a".toCharArray(), "b".toCharArray()))
        assertTrue(recorder.events.contains("+KEY_TAB:0"))
        assertFalse(recorder.events.any { it.contains("ENTER") })
        // FULL bleibt unverändert: dieselben Daten, aber mit Enter am Ende.
        val full = Recorder()
        LoginTypist(full, sleep = {}).run(LoginAction.FULL, "a".toCharArray(), "b".toCharArray())
        assertEquals("+KEY_ENTER:0", full.events[full.events.size - 2])
        assertEquals(LoginCheck.OK, checkLogin(LoginAction.FILL, "a".toCharArray(), "b".toCharArray()))
        assertEquals(LoginCheck.PASSWORD_EMPTY, checkLogin(LoginAction.FILL, "a".toCharArray(), CharArray(0)))
    }

    @Test
    fun fillNeverSendsEnterNoMatterWhereItIsCancelled() {
        // Abbruch nach jeder möglichen Anzahl Tastenereignisse: nie Enter, und der Abbruch stoppt das Tippen vor der nächsten Taste.
        val whole = Recorder()
        LoginTypist(whole, sleep = {}).fill("a@b".toCharArray(), "Pw1".toCharArray())
        for (cut in 0..whole.events.size) {
            val recorder = Recorder()
            var seen = 0
            val sink = object : KeySink {
                override fun press(key: XKeycode, keysym: Int) {
                    recorder.press(key, keysym)
                    seen++
                }

                override fun release(key: XKeycode) {
                    recorder.release(key)
                    seen++
                }
            }
            val done = LoginTypist(sink, sleep = {}, isCancelled = { seen >= cut }).fill("a@b".toCharArray(), "Pw1".toCharArray())
            assertFalse("Enter bei Abbruch nach $cut Ereignissen", recorder.events.any { it.contains("ENTER") })
            // Nach dem Druck der letzten Taste ist alles getippt, ein Abbruch ändert dann nichts mehr.
            if (cut < whole.events.size - 1) assertFalse("Abbruch nach $cut Ereignissen gilt als fertig", done)
            // Nach dem Abbruch kommt höchstens noch das Loslassen der gerade gehaltenen Taste.
            assertTrue("zu viele Ereignisse nach Abbruch bei $cut: ${recorder.events.size}", recorder.events.size <= cut + 3)
        }
    }

    @Test
    fun fillCancelledBeforeTheFirstKeyTypesNothing() {
        val recorder = Recorder()
        assertFalse(LoginTypist(recorder, sleep = {}, isCancelled = { true }).fill("a".toCharArray(), "b".toCharArray()))
        assertTrue(recorder.events.isEmpty())
    }

    @Test
    fun fillWithAnUntypeableCharacterStopsWithoutEnter() {
        val recorder = Recorder()
        assertFalse(LoginTypist(recorder, sleep = {}).fill("a".toCharArray(), "a\t".toCharArray()))
        assertFalse(recorder.events.any { it.contains("ENTER") })
    }

    @Test
    fun fillDoneMessageNamesNoDataAndSaysWhatToDoNext() {
        for (language in listOf("de", "en")) {
            val text = TestTexts.text(TextRef(LoginAction.FILL.doneRes), language)
            assertTrue(text, text.contains("Login"))
            assertFalse(text, text.any { it.isDigit() })
            assertFalse(text, text.contains("@"))
        }
        assertEquals("Ausgefüllt. Jetzt auf Login tippen.", TestTexts.de(TextRef(LoginAction.FILL.doneRes)))
    }
}
