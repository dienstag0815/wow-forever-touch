package app.gamenative.ui.screen.wow.voice

import android.os.Build
import android.speech.SpeechRecognizer
import app.gamenative.R
import app.gamenative.ui.screen.wow.voice.VoiceErrors.Recovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun otherErrorsJustShowTheMessage() {
        assertEquals(Recovery.MESSAGE, VoiceErrors.recovery(SpeechRecognizer.ERROR_NETWORK, preferredOffline = true, sdk = 37))
        assertEquals(Recovery.MESSAGE, VoiceErrors.recovery(SpeechRecognizer.ERROR_NO_MATCH, preferredOffline = false, sdk = 37))
    }
}
