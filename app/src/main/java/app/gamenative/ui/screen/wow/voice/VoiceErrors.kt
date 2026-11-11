package app.gamenative.ui.screen.wow.voice

import android.os.Build
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import app.gamenative.R

/**
 * Fehlercodes der Android-Spracherkennung in Klartext und der Weg zurück, wenn die Sprache fehlt. Ohne diese Abbildung stand
 * "Spracherkennung Fehler 13" im Spiel: 13 = ERROR_LANGUAGE_UNAVAILABLE (das Sprachpaket des Erkenners fehlt, z. B. nach einer
 * Neuinstallation), 12 = ERROR_LANGUAGE_NOT_SUPPORTED.
 */
internal object VoiceErrors {
    /** Fehlertext für [code]; der Rest (unbekannte Codes) zeigt die Nummer, aber mit Erklärung. */
    @StringRes
    fun messageRes(code: Int): Int = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.game_voice_nothing
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.game_voice_mic_missing
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.game_voice_no_connection
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> R.string.game_voice_server
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> R.string.game_voice_busy
        SpeechRecognizer.ERROR_AUDIO -> R.string.game_voice_audio
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> R.string.game_voice_language_unsupported
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.game_voice_language_missing
        SpeechRecognizer.ERROR_CLIENT, ERROR_CANNOT_CHECK_SUPPORT, ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> R.string.game_voice_client
        else -> R.string.game_voice_error
    }

    /** Sprache nicht nutzbar: Sprachpaket fehlt (13) oder wird nicht unterstützt (12). */
    fun isLanguageProblem(code: Int): Boolean =
        code == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || code == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED

    /** Was nach einem Fehler geschieht, ohne dass der Spieler etwas tun muss. */
    enum class Recovery {
        /** Fehlertext zeigen, Schluss. */
        MESSAGE,

        /** Gleich nochmal, diesmal ohne PREFER_OFFLINE: der Dienst darf die Sprache online erkennen. */
        RETRY_ONLINE,

        /** Download des Sprachpakets anstoßen (ab Android 13) und "wird geladen" zeigen. */
        DOWNLOAD_MODEL,
    }

    /**
     * Aus Fehler, Versuch und Android-Version der nächste Schritt. Beim ersten Versuch (offline bevorzugt) fehlt das Sprachpaket
     * oft nur lokal: ein Wiederholen online löst es meist. Schlägt auch das fehl, wird das Paket geladen (nur bei fehlendem Paket,
     * nicht bei nicht unterstützter Sprache) oder der Spieler bekommt den Weg zu den Android-Einstellungen genannt.
     */
    fun recovery(code: Int, preferredOffline: Boolean, sdk: Int = Build.VERSION.SDK_INT): Recovery = when {
        !isLanguageProblem(code) -> Recovery.MESSAGE
        preferredOffline -> Recovery.RETRY_ONLINE
        code == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE && sdk >= Build.VERSION_CODES.TIRAMISU -> Recovery.DOWNLOAD_MODEL
        else -> Recovery.MESSAGE
    }

    /** Text nach angestoßenem Download. */
    @StringRes
    fun downloadingRes(): Int = R.string.game_voice_language_downloading

    // Seit API 33 in SpeechRecognizer; hier als Zahl, damit es auch mit älteren Konstanten-Tabellen übersetzt.
    private const val ERROR_CANNOT_CHECK_SUPPORT = 14
    private const val ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15
}
