package app.gamenative.ui.screen.wow

import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.screen.wow.voice.KeySink
import app.gamenative.ui.screen.wow.voice.KeyStroke
import app.gamenative.ui.screen.wow.voice.VoiceKeys
import com.winlator.xserver.XKeycode
import kotlin.random.Random

/**
 * Tastenfolge für die Anmeldung im Spiel ("Anmelden", "Nur Passwort", "Nur E-Mail" im Seitenmenü). Das Eintippen nutzt den
 * Weg der Spracheingabe: Tasten des US-Layouts (mit Umschalttaste), Zeichen ohne Taste (ä, ö, ü, ß, é ...) über die
 * Trägertaste mit umgebogenem Keysym ([VoiceKeys]).
 *
 * Sicherheit: Nichts hier schreibt ein Zeichen des Passworts ins Log, in eine Datei oder in die Zwischenablage; die Zeichen werden
 * einzeln aus dem übergebenen CharArray gelesen, nie zu einer Liste von Tastenschlägen oder einem String zusammengesetzt.
 * Meldungen nennen weder Zeichen noch Stelle noch Länge.
 */
internal object LoginKeys {
    /** Der Tastenschlag für [ch], oder null, wenn das Zeichen nicht übertragbar ist (Steuerzeichen, Emoji, Ersatzzeichen). */
    fun strokeFor(ch: Char): KeyStroke? {
        VoiceKeys.asciiStroke(ch)?.let { return it }
        // Latin-1 und der übrige Grundmultilingual-Bereich über die Trägertaste; 0x80..0x9F sind Steuerzeichen.
        return if (ch.code >= 0xA0 && !ch.isSurrogate()) KeyStroke(VoiceKeys.CARRIER_KEY, false, VoiceKeys.keysymFor(ch)) else null
    }

    /** Ob jedes Zeichen von [text] getippt werden kann (ein leerer Text gilt als tippbar, wird aber vom Aufrufer geprüft). */
    fun typeable(text: CharArray): Boolean {
        for (ch in text) if (strokeFor(ch) == null) return false
        return true
    }
}

/** Welche Felder eine Anmeldeaktion eintippt. */
internal enum class LoginAction(@StringRes val doneRes: Int) {
    FULL(R.string.game_login_done_full),
    EMAIL_ONLY(R.string.game_login_done_email),
    PASSWORD_ONLY(R.string.game_login_done_password),
}

/** Ergebnis der Prüfung vor dem Tippen: nichts wird gesendet, wenn es kein [OK] ist. */
internal enum class LoginCheck(@StringRes val messageRes: Int?) {
    OK(null),
    EMAIL_EMPTY(R.string.game_login_email_empty),
    PASSWORD_EMPTY(R.string.game_login_password_empty),
    EMAIL_UNTYPEABLE(R.string.game_login_email_untypeable),
    PASSWORD_UNTYPEABLE(R.string.game_login_password_untypeable),
}

/** Prüft vor dem Tippen, ob [action] mit diesen Daten vollständig übertragbar ist. Nur die gebrauchten Felder zählen. */
internal fun checkLogin(action: LoginAction, email: CharArray, password: CharArray): LoginCheck {
    val needEmail = action != LoginAction.PASSWORD_ONLY
    val needPassword = action != LoginAction.EMAIL_ONLY
    if (needEmail) {
        if (email.isEmpty()) return LoginCheck.EMAIL_EMPTY
        if (!LoginKeys.typeable(email)) return LoginCheck.EMAIL_UNTYPEABLE
    }
    if (needPassword) {
        if (password.isEmpty()) return LoginCheck.PASSWORD_EMPTY
        if (!LoginKeys.typeable(password)) return LoginCheck.PASSWORD_UNTYPEABLE
    }
    return LoginCheck.OK
}

/**
 * Tippt Anmeldedaten in das aktive Feld des Spiels. Läuft auf einem Arbeitsthread (die Pausen takten die Tastenereignisse);
 * [sleep] und [isCancelled] sind für die Tests ersetzbar. Ein Feld wird vor dem Tippen geleert: Strg+A, Entf (wirkt, wenn das
 * Feld es kann), danach Ende und ein paar Rücktasten über die Länge des Textes hinaus (wirkt immer, auch wenn Strg+A dort
 * nichts auswählt und derselbe Text schon drinsteht).
 *
 * Takt wie ein Mensch (fold8.4): Haltezeit und Abstand je Zeichen schwanken zufällig, nach einigen Zeichen kommt eine kurze
 * Denkpause, zwischen den Feldern und vor Enter längere Pausen. Vorher waren es feste 12 + 8 ms (~50 Zeichen/s); jetzt
 * ~15 Zeichen/s im Mittel, ganze Anmeldung ~5 s. Eine gleichmäßige Maschinenfolge könnte Blizzards Bot-Erkennung auffallen (BLZ51914400 tritt unter Wine gehäuft auf).
 */
internal class LoginTypist(
    private val sink: KeySink,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val isCancelled: () -> Boolean = { false },
    private val random: Random = Random.Default,
) {
    private var untilBreak = nextBreak()

    /** "Anmelden": E-Mail, Tab, Passwort, Enter. Gibt false zurück, wenn abgebrochen wurde. */
    fun signIn(email: CharArray, password: CharArray): Boolean {
        clearField(email.size)
        if (!typeText(email)) return false
        sleep(between(FIELD_PAUSE_MIN_MS, FIELD_PAUSE_MAX_MS))
        tap(XKeycode.KEY_TAB)
        sleep(between(FIELD_PAUSE_MIN_MS, FIELD_PAUSE_MAX_MS))
        clearField(password.size)
        if (!typeText(password)) return false
        sleep(between(BEFORE_ENTER_MIN_MS, BEFORE_ENTER_MAX_MS))
        if (isCancelled()) return false
        tap(XKeycode.KEY_ENTER)
        return true
    }

    /** "Nur E-Mail eintippen": in das gerade aktive Feld, ohne Tab und Enter. */
    fun typeEmailOnly(email: CharArray): Boolean {
        clearField(email.size)
        return typeText(email)
    }

    /** "Nur Passwort eintippen": in das gerade aktive Feld, ohne Enter. */
    fun typePasswordOnly(password: CharArray): Boolean {
        clearField(password.size)
        return typeText(password)
    }

    fun run(action: LoginAction, email: CharArray, password: CharArray): Boolean = when (action) {
        LoginAction.FULL -> signIn(email, password)
        LoginAction.EMAIL_ONLY -> typeEmailOnly(email)
        LoginAction.PASSWORD_ONLY -> typePasswordOnly(password)
    }

    private fun clearField(length: Int) {
        // Strg+A, Entf
        sink.press(XKeycode.KEY_CTRL_L, 0)
        tapKey(XKeycode.KEY_A, 'a'.code)
        sink.release(XKeycode.KEY_CTRL_L)
        tap(XKeycode.KEY_DEL)
        // Ende, dann Rücktasten über die Länge hinaus
        tap(XKeycode.KEY_END)
        repeat(length + CLEAR_EXTRA) { tap(XKeycode.KEY_BKSP) }
        sleep(between(GAP_MIN_MS, GAP_MAX_MS))
    }

    private fun typeText(text: CharArray): Boolean {
        for (ch in text) {
            if (isCancelled()) return false
            val stroke = LoginKeys.strokeFor(ch) ?: return false
            if (stroke.shift) sink.press(XKeycode.KEY_SHIFT_L, 0)
            sink.press(stroke.key, stroke.keysym)
            sleep(between(HOLD_MIN_MS, HOLD_MAX_MS))
            sink.release(stroke.key)
            if (stroke.shift) sink.release(XKeycode.KEY_SHIFT_L)
            sleep(between(GAP_MIN_MS, GAP_MAX_MS))
            if (--untilBreak <= 0) {
                sleep(between(BREAK_MIN_MS, BREAK_MAX_MS))
                untilBreak = nextBreak()
            }
        }
        return true
    }

    private fun tap(key: XKeycode) = tapKey(key, 0)

    /** Steuertasten beim Leeren (Strg+A, Entf, Ende, Rücktasten): zügig wie eine gehaltene Rücktaste, aber nicht im Gleichtakt. */
    private fun tapKey(key: XKeycode, keysym: Int) {
        sink.press(key, keysym)
        sleep(between(CLEAR_HOLD_MIN_MS, CLEAR_HOLD_MAX_MS))
        sink.release(key)
        sleep(between(CLEAR_GAP_MIN_MS, CLEAR_GAP_MAX_MS))
    }

    private fun between(min: Long, max: Long): Long = random.nextLong(min, max + 1)

    private fun nextBreak(): Int = random.nextInt(BREAK_EVERY_MIN, BREAK_EVERY_MAX + 1)

    companion object {
        // Ziel: ganze Anmeldung (E-Mail ~25 + Passwort ~14 Zeichen, inkl. Leeren) in etwa 5 s, wie zügiges Tippen von Hand.
        // Zeichen: Haltezeit und Abstand je Taste (zusammen ~40–90 ms, ~11–25 Zeichen/s, im Mittel ~15)
        const val HOLD_MIN_MS = 20L
        const val HOLD_MAX_MS = 45L
        const val GAP_MIN_MS = 20L
        const val GAP_MAX_MS = 45L

        // Kurze Denkpause nach 6–12 Zeichen
        const val BREAK_EVERY_MIN = 6
        const val BREAK_EVERY_MAX = 12
        const val BREAK_MIN_MS = 60L
        const val BREAK_MAX_MS = 160L

        // Zwischen den Feldern (vor und nach Tab) und vor Enter
        const val FIELD_PAUSE_MIN_MS = 150L
        const val FIELD_PAUSE_MAX_MS = 300L
        const val BEFORE_ENTER_MIN_MS = 150L
        const val BEFORE_ENTER_MAX_MS = 300L

        // Leeren des Feldes (wie eine gehaltene Rücktaste)
        const val CLEAR_HOLD_MIN_MS = 8L
        const val CLEAR_HOLD_MAX_MS = 15L
        const val CLEAR_GAP_MIN_MS = 8L
        const val CLEAR_GAP_MAX_MS = 15L

        /** Rücktasten über die Länge des neuen Textes hinaus (Reste eines früheren Eintrags). */
        const val CLEAR_EXTRA = 8
    }
}
