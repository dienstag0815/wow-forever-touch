package app.gamenative.lab

import app.gamenative.ui.screen.wow.LoginKeys
import app.gamenative.ui.screen.wow.LoginTypist
import app.gamenative.ui.screen.wow.voice.KeySink
import com.winlator.xserver.XKeycode
import kotlin.random.Random

/** Die Tasten der Leiste im Labor. */
enum class LabBarKey { ESC, TAB, SHIFT_TAB, ENTER, BACKSPACE, LEFT, RIGHT, UP, DOWN }

/** Eine Taste des X-Servers, mit oder ohne Umschalttaste. */
internal data class LabChord(val key: XKeycode, val shift: Boolean)

/** Belegung Leistentaste -> X-Taste. Rein. */
internal object LabKeyMap {
    fun chord(key: LabBarKey): LabChord = when (key) {
        LabBarKey.ESC -> LabChord(XKeycode.KEY_ESC, false)
        LabBarKey.TAB -> LabChord(XKeycode.KEY_TAB, false)
        LabBarKey.SHIFT_TAB -> LabChord(XKeycode.KEY_TAB, true)
        LabBarKey.ENTER -> LabChord(XKeycode.KEY_ENTER, false)
        LabBarKey.BACKSPACE -> LabChord(XKeycode.KEY_BKSP, false)
        LabBarKey.LEFT -> LabChord(XKeycode.KEY_LEFT, false)
        LabBarKey.RIGHT -> LabChord(XKeycode.KEY_RIGHT, false)
        LabBarKey.UP -> LabChord(XKeycode.KEY_UP, false)
        LabBarKey.DOWN -> LabChord(XKeycode.KEY_DOWN, false)
    }
}

/**
 * Sendet Tasten der Leiste und eingegebenen Text über einen [KeySink] (im Labor der X-Server, derselbe Weg wie `LoginFill` im Spiel:
 * `XServer.injectKeyPress/Release`). Läuft auf einem Arbeitsthread; [sleep] und [isCancelled] sind für Tests ersetzbar.
 *
 * Text geht Zeichen für Zeichen aus dem übergebenen [CharArray] (nie zu einem String oder einer Liste zusammengesetzt) und wird nirgends
 * protokolliert; gemeldet wird nur die Zahl der gesendeten und der übersprungenen Zeichen. Zeichen ohne Taste (Steuerzeichen, Emoji)
 * werden übersprungen.
 */
internal class LabKeySender(
    private val sink: KeySink,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val isCancelled: () -> Boolean = { false },
    private val random: Random = Random.Default,
) {
    /** Ergebnis von [typeText]: wie viele Zeichen getippt und wie viele übersprungen wurden; [cancelled] bei Abbruch. */
    data class TextResult(val sent: Int, val skipped: Int, val cancelled: Boolean)

    /** Ein Tastendruck der Leiste (Umschalt+Tab = Umschalttaste gedrückt, Tab, Umschalttaste los). */
    fun tap(key: LabBarKey) {
        val chord = LabKeyMap.chord(key)
        if (chord.shift) sink.press(XKeycode.KEY_SHIFT_L, 0)
        sink.press(chord.key, 0)
        sleep(between(LoginTypist.HOLD_MIN_MS, LoginTypist.HOLD_MAX_MS))
        sink.release(chord.key)
        if (chord.shift) sink.release(XKeycode.KEY_SHIFT_L)
    }

    fun typeText(text: CharArray): TextResult {
        var sent = 0
        var skipped = 0
        for (ch in text) {
            if (isCancelled()) return TextResult(sent, skipped, cancelled = true)
            val stroke = LoginKeys.strokeFor(ch)
            if (stroke == null) {
                skipped++
                continue
            }
            if (stroke.shift) sink.press(XKeycode.KEY_SHIFT_L, 0)
            sink.press(stroke.key, stroke.keysym)
            sleep(between(LoginTypist.HOLD_MIN_MS, LoginTypist.HOLD_MAX_MS))
            sink.release(stroke.key)
            if (stroke.shift) sink.release(XKeycode.KEY_SHIFT_L)
            sleep(between(LoginTypist.GAP_MIN_MS, LoginTypist.GAP_MAX_MS))
            sent++
        }
        return TextResult(sent, skipped, cancelled = false)
    }

    private fun between(min: Long, max: Long): Long = random.nextLong(min, max + 1)
}

/** Zweimal tippen zum Beenden: der erste Tipp scharf stellt, der zweite innerhalb von [WINDOW_MS] löst aus. Rein. */
object LabConfirmTap {
    const val WINDOW_MS = 3_000L

    data class Step(val armedAtMs: Long?, val fire: Boolean)

    fun tap(armedAtMs: Long?, nowMs: Long): Step =
        if (armedAtMs != null && nowMs - armedAtMs in 0..WINDOW_MS) Step(null, true) else Step(nowMs, false)

    /** Ob die Scharfstellung noch gilt (für die Beschriftung). */
    fun isArmed(armedAtMs: Long?, nowMs: Long): Boolean = armedAtMs != null && nowMs - armedAtMs in 0..WINDOW_MS
}
