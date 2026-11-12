package app.gamenative.ui.screen.wow

import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.ui.screen.wow.voice.KeySink
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tippt die gespeicherten Anmeldedaten in das Spiel; gemeinsamer Weg für die Zeilen im Seitenmenü und den Knopf "Anmeldedaten
 * ausfüllen" der Stufe "Ausfüllen". Erst wird geprüft, ob alles übertragbar ist (sonst geschieht nichts und die Meldung sagt warum),
 * dann tippt ein Arbeitsthread. Das Passwort existiert nur als CharArray und wird danach überschrieben; weder Zeichen noch Länge
 * gehen ins Log oder in eine Meldung.
 *
 * Abbruch ([cancel] beim Pausieren der App, Spiel beendet, Welt erreicht): das Tippen hört vor der nächsten Taste auf.
 */
internal object LoginFill {
    enum class Start {
        /** Es sind keine Daten gespeichert. */
        NO_SECRET,

        /** Nichts getippt; der Grund steht in der Meldung. */
        REFUSED,

        /** Ein Arbeitsthread tippt. */
        TYPING,
    }

    /** Gesetzt, wenn das Tippen sofort enden soll; jeder neue Start löscht es. */
    @Volatile private var cancelled = false

    private val busy = AtomicBoolean(false)

    // Ein Thread, damit nie zwei Anmeldeaktionen gleichzeitig Tasten senden.
    val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wow-login-typing").apply { isDaemon = true }
    }

    /** Die App geht in den Hintergrund oder das Spiel endet: laufendes Tippen stoppt. */
    fun cancel() {
        cancelled = true
    }

    /** Ob gerade getippt wird. */
    fun isBusy(): Boolean = busy.get()

    /**
     * Startet [action]. [doneRes] ist die Meldung nach dem Tippen (mit Uhrzeit, wenn [withClock]); [settleMs] die Wartezeit vor der
     * ersten Taste (das Seitenmenü hält das Spiel an, danach braucht es einen Moment). Meldungen gehen über [note], auch vom
     * Arbeitsthread.
     */
    fun start(
        context: Context,
        action: LoginAction,
        @StringRes doneRes: Int,
        withClock: Boolean,
        settleMs: Long,
        note: (String) -> Unit,
    ): Start {
        val secret = BattleNetSignIn.loadSecret(context) ?: return Start.NO_SECRET
        val check = checkLogin(action, secret.email, secret.password)
        if (check != LoginCheck.OK) {
            secret.wipe()
            check.messageRes?.let { note(context.getString(it)) }
            return Start.REFUSED
        }
        if (LoginTouchGate.inWorld()) {
            secret.wipe()
            note(context.getString(R.string.game_note_inworld))
            return Start.REFUSED
        }
        val server = PluviaApp.xServerView?.getxServer()
        if (server == null) {
            secret.wipe()
            note(context.getString(R.string.game_note_notready))
            return Start.REFUSED
        }
        if (!busy.compareAndSet(false, true)) {
            secret.wipe()
            note(context.getString(R.string.game_note_typing))
            return Start.REFUSED
        }
        cancelled = false
        note(context.getString(R.string.game_note_typing))
        try {
            executor.execute {
                var done = false
                try {
                    Thread.sleep(settleMs)
                    val stop = { cancelled || LoginTouchGate.inWorld() || PluviaApp.xServerView?.getxServer() !== server }
                    done = !stop() && LoginTypist(sinkOf(server), isCancelled = stop).run(action, secret.email, secret.password)
                } catch (e: Exception) {
                    Timber.w("Anmeldung eintippen fehlgeschlagen: ${e.javaClass.simpleName}")
                } finally {
                    secret.wipe()
                    busy.set(false)
                }
                note(
                    if (done) {
                        val text = context.getString(doneRes)
                        if (withClock) "$text (${clock()})" else text
                    } else {
                        context.getString(R.string.game_note_typing_cancelled)
                    },
                )
            }
        } catch (e: Exception) {
            secret.wipe()
            busy.set(false)
            note(context.getString(R.string.game_note_typing_failed))
            return Start.REFUSED
        }
        return Start.TYPING
    }

    private fun sinkOf(server: XServer) = object : KeySink {
        override fun press(key: XKeycode, keysym: Int) = server.injectKeyPress(key, keysym)

        override fun release(key: XKeycode) = server.injectKeyRelease(key)
    }

    private fun clock(): String = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
}
