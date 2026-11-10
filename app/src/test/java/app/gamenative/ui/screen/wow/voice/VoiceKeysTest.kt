package app.gamenative.ui.screen.wow.voice

import com.winlator.xserver.XKeycode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceKeysTest {
    @Test
    fun asciiLettersAndDigits() {
        assertEquals(KeyStroke(XKeycode.KEY_A, false, 97), VoiceKeys.asciiStroke('a'))
        assertEquals(KeyStroke(XKeycode.KEY_Z, true, 90), VoiceKeys.asciiStroke('Z'))
        assertEquals(KeyStroke(XKeycode.KEY_0, false, 48), VoiceKeys.asciiStroke('0'))
        assertEquals(KeyStroke(XKeycode.KEY_9, false, 57), VoiceKeys.asciiStroke('9'))
    }

    @Test
    fun asciiSymbolsUseTheUsLayout() {
        assertEquals(KeyStroke(XKeycode.KEY_SPACE, false, 0), VoiceKeys.asciiStroke(' '))
        assertEquals(KeyStroke(XKeycode.KEY_1, true, 33), VoiceKeys.asciiStroke('!'))
        assertEquals(KeyStroke(XKeycode.KEY_9, true, 40), VoiceKeys.asciiStroke('('))
        assertEquals(KeyStroke(XKeycode.KEY_0, true, 41), VoiceKeys.asciiStroke(')'))
        assertEquals(KeyStroke(XKeycode.KEY_SLASH, true, 63), VoiceKeys.asciiStroke('?'))
        assertEquals(KeyStroke(XKeycode.KEY_APOSTROPHE, false, 39), VoiceKeys.asciiStroke('\''))
        assertEquals(KeyStroke(XKeycode.KEY_COMMA, false, 44), VoiceKeys.asciiStroke(','))
        assertEquals(KeyStroke(XKeycode.KEY_MINUS, true, 95), VoiceKeys.asciiStroke('_'))
        assertEquals(KeyStroke(XKeycode.KEY_SEMICOLON, true, 58), VoiceKeys.asciiStroke(':'))
        assertNull(VoiceKeys.asciiStroke('ä'))
        assertNull(VoiceKeys.asciiStroke('€'))
    }

    @Test
    fun everyPrintableAsciiCharacterHasAKey() {
        for (code in 32..126) {
            val ch = code.toChar()
            assertTrue("no key for '$ch'", VoiceKeys.asciiStroke(ch) != null)
        }
    }

    @Test
    fun keysymsOfGermanCharactersAreTheLatin1CodePoints() {
        assertEquals(0xE4, VoiceKeys.keysymFor('ä'))
        assertEquals(0xF6, VoiceKeys.keysymFor('ö'))
        assertEquals(0xFC, VoiceKeys.keysymFor('ü'))
        assertEquals(0xC4, VoiceKeys.keysymFor('Ä'))
        assertEquals(0xD6, VoiceKeys.keysymFor('Ö'))
        assertEquals(0xDC, VoiceKeys.keysymFor('Ü'))
        assertEquals(0xDF, VoiceKeys.keysymFor('ß'))
        assertEquals(0x010020AC, VoiceKeys.keysymFor('€'))
    }

    @Test
    fun directModeTypesUmlautsOnTheCarrierKey() {
        val strokes = VoiceKeys.strokesFor("Hä ß", UmlautMode.DIRECT)
        assertEquals(
            listOf(
                KeyStroke(XKeycode.KEY_H, true, 72),
                KeyStroke(VoiceKeys.CARRIER_KEY, false, 0xE4),
                KeyStroke(XKeycode.KEY_SPACE, false, 0),
                KeyStroke(VoiceKeys.CARRIER_KEY, false, 0xDF),
            ),
            strokes,
        )
    }

    @Test
    fun asciiModeTransliterates() {
        assertEquals("Gruesse aus Koeln, Aepfel und Strasse", VoiceKeys.toAscii("Grüße aus Köln, Äpfel und Straße"))
        assertEquals("Groesse Aepfel e", VoiceKeys.toAscii("Größe Äpfel é"))
        val strokes = VoiceKeys.strokesFor("Grüße", UmlautMode.ASCII)
        assertEquals(7, strokes.size)
        assertTrue(strokes.none { it.key == VoiceKeys.CARRIER_KEY })
    }

    @Test
    fun sanitizeCleansTheLine() {
        assertEquals("dance", VoiceKeys.sanitize("/dance"))
        assertEquals("a b c", VoiceKeys.sanitize("  a\tb \n c  "))
        assertEquals("", VoiceKeys.sanitize("   "))
    }

    @Test
    fun sanitizeCutsAtWordBoundaryToChatLimit() {
        val text = (1..80).joinToString(" ") { "wort$it" }
        val cut = VoiceKeys.sanitize(text)
        assertTrue(cut.length <= VoiceKeys.MAX_CHAT_LENGTH)
        assertTrue(text.startsWith(cut))
        assertTrue(!cut.endsWith(" "))
        // The cut falls between words, so the last word is complete.
        assertTrue(text.startsWith("$cut "))
    }

    private class Recorder : KeySink {
        val events = ArrayList<String>()

        override fun press(key: XKeycode, keysym: Int) {
            events.add("+${key.name}:$keysym")
        }

        override fun release(key: XKeycode) {
            events.add("-${key.name}")
        }
    }

    @Test
    fun senderTypesEnterTextEnter() {
        val recorder = Recorder()
        val pauses = ArrayList<Long>()
        VoiceTextSender(recorder, sleep = { pauses.add(it) }).send("Hi", UmlautMode.DIRECT)
        assertEquals(
            listOf(
                "+KEY_ENTER:0", "-KEY_ENTER",
                "+KEY_SHIFT_L:0", "+KEY_H:72", "-KEY_H", "-KEY_SHIFT_L",
                "+KEY_I:105", "-KEY_I",
                "+KEY_ENTER:0", "-KEY_ENTER",
            ),
            recorder.events,
        )
        // The game gets time to open the chat box before the first character.
        assertTrue(pauses.contains(VoiceTextSender.OPEN_CHAT_MS))
    }

    @Test
    fun senderSendsUmlautWithItsKeysym() {
        val recorder = Recorder()
        VoiceTextSender(recorder, sleep = {}).send("ä", UmlautMode.DIRECT)
        assertEquals(
            listOf(
                "+KEY_ENTER:0", "-KEY_ENTER",
                "+${VoiceKeys.CARRIER_KEY.name}:${0xE4}", "-${VoiceKeys.CARRIER_KEY.name}",
                "+KEY_ENTER:0", "-KEY_ENTER",
            ),
            recorder.events,
        )
    }

    @Test
    fun senderDoesNothingForEmptyText() {
        val recorder = Recorder()
        VoiceTextSender(recorder, sleep = {}).send("   ", UmlautMode.DIRECT)
        assertTrue(recorder.events.isEmpty())
    }

    @Test
    fun cancelledSenderNeverSendsTheLine() {
        val recorder = Recorder()
        VoiceTextSender(recorder, sleep = {}, isCancelled = { true }).send("Hallo", UmlautMode.DIRECT)
        // Only the opening Enter went out; no characters and no closing Enter.
        assertEquals(listOf("+KEY_ENTER:0", "-KEY_ENTER"), recorder.events)
    }

    @Test
    fun sanitizeKeepsTheLeadingSlashForChatCommands() {
        assertEquals("/dance", VoiceKeys.sanitize("/dance", keepSlash = true))
        assertEquals("/s Hallo Welt", VoiceKeys.sanitize("  /s   Hallo\tWelt ", keepSlash = true))
        // Spoken text still never runs a command.
        assertEquals("dance", VoiceKeys.sanitize("/dance"))
    }

    @Test
    fun strokesForDanceCommandStartWithTheSlashKey() {
        assertEquals(
            listOf(
                KeyStroke(XKeycode.KEY_SLASH, false, 47),
                KeyStroke(XKeycode.KEY_D, false, 100),
                KeyStroke(XKeycode.KEY_A, false, 97),
                KeyStroke(XKeycode.KEY_N, false, 110),
                KeyStroke(XKeycode.KEY_C, false, 99),
                KeyStroke(XKeycode.KEY_E, false, 101),
            ),
            VoiceKeys.strokesFor("/dance", UmlautMode.DIRECT, keepSlash = true),
        )
        // Without keepSlash the slash is dropped (voice path).
        assertEquals(5, VoiceKeys.strokesFor("/dance", UmlautMode.DIRECT).size)
    }

    @Test
    fun senderTypesTheDanceCommandWithEnterSlashLettersEnter() {
        val recorder = Recorder()
        VoiceTextSender(recorder, sleep = {}).send("/dance", UmlautMode.DIRECT, keepSlash = true)
        assertEquals(
            listOf(
                "+KEY_ENTER:0", "-KEY_ENTER",
                "+KEY_SLASH:47", "-KEY_SLASH",
                "+KEY_D:100", "-KEY_D",
                "+KEY_A:97", "-KEY_A",
                "+KEY_N:110", "-KEY_N",
                "+KEY_C:99", "-KEY_C",
                "+KEY_E:101", "-KEY_E",
                "+KEY_ENTER:0", "-KEY_ENTER",
            ),
            recorder.events,
        )
    }

    @Test
    fun senderWithoutKeepSlashStillDropsTheSlash() {
        val recorder = Recorder()
        VoiceTextSender(recorder, sleep = {}).send("/a", UmlautMode.DIRECT)
        assertEquals(
            listOf("+KEY_ENTER:0", "-KEY_ENTER", "+KEY_A:97", "-KEY_A", "+KEY_ENTER:0", "-KEY_ENTER"),
            recorder.events,
        )
    }

    @Test
    fun senderTypesFreeCommandWithSpaceAndArguments() {
        val recorder = Recorder()
        VoiceTextSender(recorder, sleep = {}).send("/s Hi", UmlautMode.DIRECT, keepSlash = true)
        val typed = recorder.events.filter { it.startsWith("+") }.map { it.substringBefore(':') }
        assertEquals(
            listOf("+KEY_ENTER", "+KEY_SLASH", "+KEY_S", "+KEY_SPACE", "+KEY_SHIFT_L", "+KEY_H", "+KEY_I", "+KEY_ENTER"),
            typed,
        )
    }
}
