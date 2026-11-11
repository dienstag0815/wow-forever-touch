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

    /** Texte, in denen die Fehlernummer am Ende steht (der Platzhalter %1$d): der Rest (unbekannte Codes) und der Serverfehler. */
    fun showsCode(@StringRes res: Int): Boolean = res == R.string.game_voice_error || res == R.string.game_voice_server

    /** Der Dienst war nicht erreichbar: Serverfehler (4) oder Verbindung zum Dienst weg (11). */
    fun isServerProblem(code: Int): Boolean =
        code == SpeechRecognizer.ERROR_SERVER || code == SpeechRecognizer.ERROR_SERVER_DISCONNECTED

    /** Sprache nicht nutzbar: Sprachpaket fehlt (13) oder wird nicht unterstützt (12). */
    fun isLanguageProblem(code: Int): Boolean =
        code == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || code == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED

    /** Was nach einem Fehler geschieht, ohne dass der Spieler etwas tun muss. */
    enum class Recovery {
        /** Fehlertext zeigen, Schluss. */
        MESSAGE,

        /** Gleich nochmal, diesmal ohne PREFER_OFFLINE: der Dienst darf die Sprache online erkennen. */
        RETRY_ONLINE,

        /** Serverfehler: ein neuer Erkenner, immer online (war der erste Versuch offline bevorzugt, der zweite online; war er schon online, nochmal online). */
        RETRY_SERVER,

        /** Download des Sprachpakets anstoßen (ab Android 13) und "wird geladen" zeigen. */
        DOWNLOAD_MODEL,
    }

    /**
     * Aus Fehler, Versuch und Android-Version der nächste Schritt. Beim ersten Versuch (offline bevorzugt) fehlt das Sprachpaket
     * oft nur lokal: ein Wiederholen online löst es meist. Schlägt auch das fehl, wird das Paket geladen (nur bei fehlendem Paket,
     * nicht bei nicht unterstützter Sprache) oder der Spieler bekommt den Weg zu den Android-Einstellungen genannt.
     *
     * Serverfehler (4, 11) bekommen höchstens [MAX_SERVER_RETRIES] Neuversuche je Tipp aufs Mikrofon, jeweils nach einer Pause
     * ([serverRetryDelayMs]): [serverRetries] sagt, wie viele in diesem Tipp schon liefen (sind es genug, bleibt es bei der Meldung,
     * keine Schleife). Der Neuversuch gleich nach dem Fehler kam zu früh: der Erkennungsdienst war noch nicht wieder gebunden (11).
     */
    fun recovery(code: Int, preferredOffline: Boolean, serverRetries: Int = 0, sdk: Int = Build.VERSION.SDK_INT): Recovery = when {
        isServerProblem(code) -> if (serverRetries >= MAX_SERVER_RETRIES) Recovery.MESSAGE else Recovery.RETRY_SERVER
        !isLanguageProblem(code) -> Recovery.MESSAGE
        preferredOffline -> Recovery.RETRY_ONLINE
        code == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE && sdk >= Build.VERSION_CODES.TIRAMISU -> Recovery.DOWNLOAD_MODEL
        else -> Recovery.MESSAGE
    }

    /** So viele automatische Neuversuche nach einem Serverfehler (4, 11) gibt es je Tipp aufs Mikrofon. */
    const val MAX_SERVER_RETRIES = 2

    /** Pause vor dem ersten und vor dem zweiten Neuversuch (ms): der Dienst braucht einen Moment, bis er wieder gebunden ist. */
    private val SERVER_RETRY_DELAYS_MS = longArrayOf(500L, 1000L)

    /** Pause vor dem Neuversuch Nummer [serverRetries] + 1 (0 = der erste); ab [MAX_SERVER_RETRIES] gibt es keinen mehr (dann 0). */
    fun serverRetryDelayMs(serverRetries: Int): Long = SERVER_RETRY_DELAYS_MS.getOrElse(serverRetries) { 0L }

    /**
     * Die Zählung der Neuversuche nach Serverfehlern in einem Tipp aufs Mikrofon, ohne Android (getestet). Der Aufrufer plant den
     * Neuversuch nach der Pause ein ([schedule]) und ruft zu dessen Zeitpunkt [fire]: nur wenn der Spieler in der Zwischenzeit nicht
     * abgebrochen hat ([cancel]) oder neu getippt hat ([reset]), startet der Neuversuch wirklich.
     */
    class ServerRetries {
        /** Wie viele Neuversuche dieses Tipps schon eingeplant waren. */
        var count = 0
            private set

        /** Ein Neuversuch ist eingeplant und noch nicht gefeuert oder abgebrochen. */
        var waiting = false
            private set

        /** Neuer Tipp aufs Mikrofon: Zählung von vorn, ein eingeplanter Neuversuch des vorigen Tipps verfällt. */
        fun reset() {
            count = 0
            waiting = false
        }

        /** Der Spieler bricht ab: ein eingeplanter Neuversuch verfällt (die Zählung bleibt, bis der nächste Tipp sie zurücksetzt). */
        fun cancel() {
            waiting = false
        }

        /** Pause vor dem Neuversuch (ms), oder null, wenn der Fehler [code] keinen mehr bekommt (dann die Meldung zeigen). */
        fun schedule(code: Int, preferredOffline: Boolean): Long? {
            if (recovery(code, preferredOffline, count) != Recovery.RETRY_SERVER) return null
            val delay = serverRetryDelayMs(count)
            count++
            waiting = true
            return delay
        }

        /** Die Pause ist um: wahr, wenn der Neuversuch jetzt starten darf (nicht abgebrochen, nicht überholt). Einmal je Einplanung. */
        fun fire(): Boolean {
            val go = waiting
            waiting = false
            return go
        }
    }

    /** Text nach angestoßenem Download. */
    @StringRes
    fun downloadingRes(): Int = R.string.game_voice_language_downloading

    // Seit API 33 in SpeechRecognizer; hier als Zahl, damit es auch mit älteren Konstanten-Tabellen übersetzt.
    private const val ERROR_CANNOT_CHECK_SUPPORT = 14
    private const val ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15
}
