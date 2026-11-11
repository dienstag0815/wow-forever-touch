package app.gamenative.ui.screen.wow.voice

import android.os.Build
import android.speech.SpeechRecognizer
import app.gamenative.R
import app.gamenative.ui.screen.wow.voice.VoiceErrors.Recovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

class VoiceErrorsTest {
    @Test
    fun languageUnavailableGetsPlainText() {
        assertEquals(13, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertEquals(R.string.game_voice_language_missing, VoiceErrors.messageRes(13))
        assertEquals(R.string.game_voice_language_unsupported, VoiceErrors.messageRes(12))
    }

    @Test
    fun knownCodesMapToText() {
        assertEquals(R.string.game_voice_nothing, VoiceErrors.messageRes(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(R.string.game_voice_nothing, VoiceErrors.messageRes(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(R.string.game_voice_mic_missing, VoiceErrors.messageRes(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(R.string.game_voice_no_connection, VoiceErrors.messageRes(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(R.string.game_voice_no_connection, VoiceErrors.messageRes(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertEquals(R.string.game_voice_busy, VoiceErrors.messageRes(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(R.string.game_voice_busy, VoiceErrors.messageRes(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS))
        assertEquals(R.string.game_voice_audio, VoiceErrors.messageRes(SpeechRecognizer.ERROR_AUDIO))
        assertEquals(R.string.game_voice_server, VoiceErrors.messageRes(SpeechRecognizer.ERROR_SERVER))
        assertEquals(R.string.game_voice_server, VoiceErrors.messageRes(SpeechRecognizer.ERROR_SERVER_DISCONNECTED))
        assertEquals(R.string.game_voice_client, VoiceErrors.messageRes(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(R.string.game_voice_client, VoiceErrors.messageRes(14))
        assertEquals(R.string.game_voice_client, VoiceErrors.messageRes(15))
    }

    @Test
    fun unknownCodeKeepsNumberText() {
        assertEquals(R.string.game_voice_error, VoiceErrors.messageRes(99))
    }

    @Test
    fun onlyLanguageCodesAreLanguageProblems() {
        assertTrue(VoiceErrors.isLanguageProblem(12))
        assertTrue(VoiceErrors.isLanguageProblem(13))
        assertFalse(VoiceErrors.isLanguageProblem(SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(VoiceErrors.isLanguageProblem(SpeechRecognizer.ERROR_NETWORK))
    }

    @Test
    fun missingLanguageRetriesOnlineFirst() {
        assertEquals(Recovery.RETRY_ONLINE, VoiceErrors.recovery(13, preferredOffline = true, sdk = 37))
        assertEquals(Recovery.RETRY_ONLINE, VoiceErrors.recovery(12, preferredOffline = true, sdk = 37))
    }

    @Test
    fun missingLanguageAfterOnlineTryStartsDownload() {
        assertEquals(Recovery.DOWNLOAD_MODEL, VoiceErrors.recovery(13, preferredOffline = false, sdk = Build.VERSION_CODES.TIRAMISU))
        assertEquals(Recovery.DOWNLOAD_MODEL, VoiceErrors.recovery(13, preferredOffline = false, sdk = 37))
    }

    @Test
    fun beforeAndroid13OnlyTheMessage() {
        assertEquals(Recovery.MESSAGE, VoiceErrors.recovery(13, preferredOffline = false, sdk = Build.VERSION_CODES.S))
    }

    @Test
    fun unsupportedLanguageNeverDownloads() {
        assertEquals(Recovery.MESSAGE, VoiceErrors.recovery(12, preferredOffline = false, sdk = 37))
    }

    @Test
    fun serverErrorsGetAutomaticRetries() {
        assertEquals(4, SpeechRecognizer.ERROR_SERVER)
        assertEquals(11, SpeechRecognizer.ERROR_SERVER_DISCONNECTED)
        for (code in listOf(SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
            assertTrue(VoiceErrors.isServerProblem(code))
            // first try was offline-preferred, or already online: either way one more
            assertEquals(Recovery.RETRY_SERVER, VoiceErrors.recovery(code, preferredOffline = true, sdk = 37))
            assertEquals(Recovery.RETRY_SERVER, VoiceErrors.recovery(code, preferredOffline = false, sdk = 37))
            assertEquals(Recovery.RETRY_SERVER, VoiceErrors.recovery(code, preferredOffline = false, serverRetries = 0, sdk = 37))
            assertEquals(Recovery.RETRY_SERVER, VoiceErrors.recovery(code, preferredOffline = false, serverRetries = 1, sdk = 37))
        }
    }

    @Test
    fun atMostTwoRetriesThenTheMessage() {
        assertEquals(2, VoiceErrors.MAX_SERVER_RETRIES)
        for (code in listOf(SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED)) {
            assertEquals(Recovery.MESSAGE, VoiceErrors.recovery(code, preferredOffline = false, serverRetries = 2, sdk = 37))
            assertEquals(Recovery.MESSAGE, VoiceErrors.recovery(code, preferredOffline = true, serverRetries = 2, sdk = 37))
        }
        // a loop is impossible: two answers yield a retry, the third yields the message
        var done = 0
        while (VoiceErrors.recovery(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, preferredOffline = true, serverRetries = done, sdk = 37) == Recovery.RETRY_SERVER) {
            done++
            assertTrue(done < 5)
        }
        assertEquals(2, done)
    }

    @Test
    fun theRetriesWaitAndTheSecondWaitsLonger() {
        val first = VoiceErrors.serverRetryDelayMs(0)
        val second = VoiceErrors.serverRetryDelayMs(1)
        assertTrue("first pause $first", first in 400L..600L)
        assertTrue("second pause $second", second > first && second <= 1500L)
        assertEquals(0L, VoiceErrors.serverRetryDelayMs(2))
    }

    @Test
    fun countingPerTapWithPauses() {
        val retries = VoiceErrors.ServerRetries()
        val code = SpeechRecognizer.ERROR_SERVER_DISCONNECTED
        assertFalse(retries.waiting)
        assertEquals(VoiceErrors.serverRetryDelayMs(0), retries.schedule(code, preferredOffline = true))
        assertTrue(retries.waiting)
        assertTrue(retries.fire())
        assertFalse(retries.waiting)
        assertEquals(VoiceErrors.serverRetryDelayMs(1), retries.schedule(code, preferredOffline = false))
        assertTrue(retries.fire())
        assertEquals(2, retries.count)
        // the third error of the same tap: no retry, the message follows (with the number)
        assertEquals(null, retries.schedule(code, preferredOffline = false))
        assertFalse(retries.waiting)
        assertEquals(2, retries.count)
        // a new tap on the microphone counts anew
        retries.reset()
        assertEquals(0, retries.count)
        assertEquals(VoiceErrors.serverRetryDelayMs(0), retries.schedule(SpeechRecognizer.ERROR_SERVER, preferredOffline = true))
    }

    @Test
    fun cancelOrNewTapDropsTheWaitingRetry() {
        val retries = VoiceErrors.ServerRetries()
        retries.schedule(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, preferredOffline = true)
        retries.cancel()                      // the player cancels during the pause
        assertFalse(retries.fire())           // the retry that comes due does nothing
        retries.schedule(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, preferredOffline = true)
        retries.reset()                       // the player taps the microphone again: the old retry is gone
        assertFalse(retries.fire())
        // fires only once per scheduling
        retries.schedule(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, preferredOffline = true)
        assertTrue(retries.fire())
        assertFalse(retries.fire())
    }

    @Test
    fun otherErrorsAreNeverScheduled() {
        val retries = VoiceErrors.ServerRetries()
        for (code in listOf(SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_AUDIO, 13, 99)) {
            assertEquals(null, retries.schedule(code, preferredOffline = false))
        }
        assertEquals(0, retries.count)
        assertFalse(retries.waiting)
    }

    @Test
    fun otherServerFreeCodesAreNotRetried() {
        for (code in listOf(SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_RECOGNIZER_BUSY, 99)) {
            assertFalse(VoiceErrors.isServerProblem(code))
            assertEquals(Recovery.MESSAGE, VoiceErrors.recovery(code, preferredOffline = true, sdk = 37))
        }
    }

    @Test
    fun theServerMessageShowsTheErrorNumberLikeTheUnknownOne() {
        assertTrue(VoiceErrors.showsCode(VoiceErrors.messageRes(SpeechRecognizer.ERROR_SERVER)))
        assertTrue(VoiceErrors.showsCode(VoiceErrors.messageRes(SpeechRecognizer.ERROR_SERVER_DISCONNECTED)))
        assertTrue(VoiceErrors.showsCode(VoiceErrors.messageRes(99)))
        assertFalse(VoiceErrors.showsCode(VoiceErrors.messageRes(SpeechRecognizer.ERROR_NETWORK)))
        assertFalse(VoiceErrors.showsCode(VoiceErrors.messageRes(13)))
    }

    @Test
    fun languageRetriesStayTheyWereWhileServerErrorsRetryFirst() {
        assertEquals(Recovery.RETRY_ONLINE, VoiceErrors.recovery(13, preferredOffline = true, serverRetries = 0, sdk = 37))
        assertEquals(Recovery.DOWNLOAD_MODEL, VoiceErrors.recovery(13, preferredOffline = false, serverRetries = 2, sdk = 37))
    }

    @Test
    fun otherErrorsJustShowTheMessage() {
        assertEquals(Recovery.MESSAGE, VoiceErrors.recovery(SpeechRecognizer.ERROR_NETWORK, preferredOffline = true, sdk = 37))
        assertEquals(Recovery.MESSAGE, VoiceErrors.recovery(SpeechRecognizer.ERROR_NO_MATCH, preferredOffline = false, sdk = 37))
    }

    @Test
    fun bothLanguagesEndTheServerMessageWithTheNumber() {
        val dirs = listOf("src/main/res/values", "src/main/res/values-de").map { File(it).takeIf { d -> d.isDirectory } ?: File("app/$it") }
        if (dirs.any { !it.isDirectory }) return // run from an unexpected working directory
        for (dir in dirs) {
            val xml = File(dir, "strings_game.xml").readText()
            val text = Regex("<string name=\"game_voice_server\">([^<]*)</string>").find(xml)!!.groupValues[1]
            assertTrue("$dir: $text", text.endsWith(" (%1\$d)"))
        }
    }
}
