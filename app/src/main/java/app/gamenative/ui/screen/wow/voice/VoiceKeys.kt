package app.gamenative.ui.screen.wow.voice

import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.components.AppText
import com.winlator.xserver.XKeycode
import java.text.Normalizer

/** How characters outside plain ASCII (umlauts, ß) reach the game. */
enum class UmlautMode(@StringRes val labelRes: Int) {
    /** Real characters: the key carrying them is given the character's X keysym (Latin-1 code point). */
    DIRECT(R.string.game_umlaut_direct),

    /** ä -> ae, ö -> oe, ü -> ue, ß -> ss: always works, even if the game's keyboard layout does not know umlauts. */
    ASCII(R.string.game_umlaut_ascii),
    ;

    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)
}

/** One key tap: [key] pressed (with Shift when [shift]) while the X server is told that it produces [keysym] (0 = unchanged). */
internal data class KeyStroke(val key: XKeycode, val shift: Boolean, val keysym: Int)

/**
 * Turns text into key taps for the X server, the way the soft keyboard path of the app does it for ASCII (a key
 * code of the US layout plus Shift, the keysym is the character). Characters without a key on the US layout use
 * [CARRIER_KEY]: it is a key that no profile binds, and the injection remaps its keysym to the character before
 * the press ([com.winlator.xserver.InputDeviceManager.onKeyPress] sends a MappingNotify when the keysym differs).
 * Latin-1 characters have the keysym of their code point (ä = 0xE4 = adiaeresis, ß = 0xDF = ssharp, ü = 0xFC),
 * everything else in the BMP uses the Unicode keysym 0x01000000 + code point.
 */
internal object VoiceKeys {
    /** Maximum length of a WoW chat line. */
    const val MAX_CHAT_LENGTH = 255

    val CARRIER_KEY: XKeycode = XKeycode.KEY_SCROLL_LOCK

    private const val SHIFTED = ")!@#\$%^&*("
    private val SHIFTED_PAIRS = mapOf(
        '_' to XKeycode.KEY_MINUS, '+' to XKeycode.KEY_EQUAL,
        '{' to XKeycode.KEY_BRACKET_LEFT, '}' to XKeycode.KEY_BRACKET_RIGHT,
        '|' to XKeycode.KEY_BACKSLASH, ':' to XKeycode.KEY_SEMICOLON,
        '"' to XKeycode.KEY_APOSTROPHE, '<' to XKeycode.KEY_COMMA,
        '>' to XKeycode.KEY_PERIOD, '?' to XKeycode.KEY_SLASH, '~' to XKeycode.KEY_GRAVE,
    )
    private val PLAIN_KEYS = mapOf(
        ' ' to XKeycode.KEY_SPACE, '-' to XKeycode.KEY_MINUS, '=' to XKeycode.KEY_EQUAL,
        '[' to XKeycode.KEY_BRACKET_LEFT, ']' to XKeycode.KEY_BRACKET_RIGHT,
        '\\' to XKeycode.KEY_BACKSLASH, ';' to XKeycode.KEY_SEMICOLON,
        '\'' to XKeycode.KEY_APOSTROPHE, ',' to XKeycode.KEY_COMMA,
        '.' to XKeycode.KEY_PERIOD, '/' to XKeycode.KEY_SLASH, '`' to XKeycode.KEY_GRAVE,
    )
    private val DIGIT_KEYS = arrayOf(
        XKeycode.KEY_0, XKeycode.KEY_1, XKeycode.KEY_2, XKeycode.KEY_3, XKeycode.KEY_4,
        XKeycode.KEY_5, XKeycode.KEY_6, XKeycode.KEY_7, XKeycode.KEY_8, XKeycode.KEY_9,
    )

    /** The X keysym for a character: Latin-1 = code point, other BMP characters = Unicode keysym. */
    fun keysymFor(ch: Char): Int = if (ch.code < 0x100) ch.code else 0x01000000 + ch.code

    private fun letterKey(lower: Char): XKeycode = XKeycode.valueOf("KEY_" + lower.uppercaseChar())

    /** The tap for a printable ASCII character, or null if the character has no key on the US layout. */
    fun asciiStroke(ch: Char): KeyStroke? {
        return when {
            ch in 'a'..'z' -> KeyStroke(letterKey(ch), false, ch.code)
            ch in 'A'..'Z' -> KeyStroke(letterKey(ch.lowercaseChar()), true, ch.code)
            ch in '0'..'9' -> KeyStroke(DIGIT_KEYS[ch - '0'], false, ch.code)
            ch == ' ' -> KeyStroke(XKeycode.KEY_SPACE, false, 0)
            PLAIN_KEYS.containsKey(ch) -> KeyStroke(PLAIN_KEYS.getValue(ch), false, ch.code)
            SHIFTED.indexOf(ch) >= 0 -> {
                // ")!@#$%^&*(" are the shifted digits 0..9 of the US layout.
                val digit = SHIFTED.indexOf(ch)
                KeyStroke(DIGIT_KEYS[digit], true, ch.code)
            }
            SHIFTED_PAIRS.containsKey(ch) -> KeyStroke(SHIFTED_PAIRS.getValue(ch), true, ch.code)
            else -> null
        }
    }

    /** Replaces everything outside ASCII: ä -> ae, ß -> ss, é -> e; characters without a base letter are dropped. */
    fun toAscii(text: String): String {
        val sb = StringBuilder(text.length + 4)
        for (ch in text) {
            when (ch) {
                'ä' -> sb.append("ae")
                'ö' -> sb.append("oe")
                'ü' -> sb.append("ue")
                'Ä' -> sb.append("Ae")
                'Ö' -> sb.append("Oe")
                'Ü' -> sb.append("Ue")
                'ß', 'ẞ' -> sb.append("ss")
                else -> if (ch.code < 0x80) sb.append(ch) else sb.append(stripAccent(ch))
            }
        }
        return sb.toString()
    }

    private fun stripAccent(ch: Char): String {
        val decomposed = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (c in decomposed) if (c.code < 0x80) sb.append(c)
        return sb.toString()
    }

    /**
     * Cleans the text for the chat line: control characters become spaces, blanks are collapsed, the line is cut to
     * [MAX_CHAT_LENGTH] at a word boundary. A leading slash would run a chat command, so it is dropped, unless
     * [keepSlash] is set (chat command buttons such as the emotes: "/dance" must stay "/dance").
     */
    fun sanitize(text: String, keepSlash: Boolean = false): String {
        val flat = buildString(text.length) {
            for (ch in text) append(if (ch.isISOControl()) ' ' else ch)
        }.replace(Regex("\\s+"), " ").trim()
        val cleaned = if (keepSlash) flat else flat.trimStart('/').trim()
        if (cleaned.length <= MAX_CHAT_LENGTH) return cleaned
        val cut = cleaned.substring(0, MAX_CHAT_LENGTH)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > MAX_CHAT_LENGTH / 2) cut.substring(0, lastSpace) else cut).trim()
    }

    /** The key taps for [text]; characters that cannot be typed are skipped. */
    fun strokesFor(text: String, mode: UmlautMode, keepSlash: Boolean = false): List<KeyStroke> {
        val prepared = sanitize(if (mode == UmlautMode.ASCII) toAscii(text) else text, keepSlash)
        val out = ArrayList<KeyStroke>(prepared.length)
        for (ch in prepared) {
            val ascii = asciiStroke(ch)
            when {
                ascii != null -> out.add(ascii)
                ch.code >= 0xA0 && !ch.isSurrogate() -> out.add(KeyStroke(CARRIER_KEY, false, keysymFor(ch)))
                else -> Unit
            }
        }
        return out
    }
}

/** Where the key events go; implemented on top of the X server, replaced by a recorder in the tests. */
internal interface KeySink {
    fun press(key: XKeycode, keysym: Int)

    fun release(key: XKeycode)
}

/**
 * What the app knows about the chat edit box of the game. The app cannot look into the game, but the addon reports
 * whether a chat edit box has the keyboard focus (info flag of the cooldown strip, see CooldownService.chatBoxOpen).
 */
internal interface ChatGate {
    /** Called once before a line is typed (starts the reading of the strip if it is not running). */
    fun prepare() = Unit

    /** true = a chat edit box is open, false = none is, null = not known (no reading, or an addon that does not report it). */
    fun isChatOpen(): Boolean?

    companion object {
        /** No information: the sender then works blind, as before. */
        val BLIND: ChatGate = object : ChatGate {
            override fun isChatOpen(): Boolean? = null
        }
    }
}

/** How [VoiceTextSender.send] ended. */
internal enum class SendResult {
    /** The line was typed and the closing Enter went out. */
    SENT,

    /** Nothing to type. */
    EMPTY,

    /** The sender was stopped (the screen is closing); the line was not completed. */
    CANCELLED,

    /** The chat box did not open (or closed while typing): the letters were NOT sent to the game, or typing stopped. */
    CHAT_NOT_OPEN,
}

/**
 * Types a chat line into WoW: Enter (opens the chat box), the text, Enter (sends). With keepSlash a leading "/" is
 * typed (chat commands such as "/dance"), otherwise it is dropped (spoken text must never run a command).
 *
 * Why the [gate] matters: Enter only toggles. If the box is already open it closes it, and if the game swallowed or delayed the
 * first Enter, every typed letter would be a key binding of the game (windows opening and closing). With a gate that knows
 * the box ([ChatGate.isChatOpen] != null) the sender
 *  - presses Enter only while the box is closed, and waits until the addon reports it open before the first letter (a second
 *    Enter only when the reading says "still closed"); if it never opens, nothing is typed and the result is [SendResult.CHAT_NOT_OPEN];
 *  - clears the box (Ctrl+A, Backspace) when it was already open with leftovers;
 *  - looks again every few letters and before the closing Enter, and stops if the box has closed.
 * Without that knowledge (old addon, strip not readable) it falls back to the blind sequence with generous pauses.
 * Runs on a worker thread; [sleep] is replaceable for tests.
 */
internal class VoiceTextSender(
    private val sink: KeySink,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val isCancelled: () -> Boolean = { false },
    private val gate: ChatGate = ChatGate.BLIND,
) {
    /**
     * [appendToOpenBox]: whisper from the long-press mini menu. The addon has opened the chat box with "/w Name " already; the text is
     * appended to it and sent. The box is never emptied and never opened here: if it is not known to be open, nothing is typed
     * ([SendResult.CHAT_NOT_OPEN]), because a line typed into a box we opened ourselves would go out as plain /say.
     */
    fun send(text: String, mode: UmlautMode, keepSlash: Boolean = false, appendToOpenBox: Boolean = false): SendResult {
        val strokes = VoiceKeys.strokesFor(text, mode, keepSlash)
        if (strokes.isEmpty()) return SendResult.EMPTY
        gate.prepare()
        val known = waitForReading()
        if (appendToOpenBox) {
            if (known != true) return SendResult.CHAT_NOT_OPEN
            sleep(SETTLE_MS)
        } else when {
            known == null -> {
                // Blind: the old sequence, with a longer Enter and a longer pause for a slow frame rate.
                tap(XKeycode.KEY_ENTER, 0, ENTER_HOLD_MS)
                sleep(OPEN_CHAT_MS)
            }
            known == true -> {
                // Already open (leftover of an earlier try, or opened by hand): Enter would close it. Empty it and type.
                clearBox()
                sleep(SETTLE_MS)
            }
            else -> if (!openChatChecked()) return if (isCancelled()) SendResult.CANCELLED else SendResult.CHAT_NOT_OPEN
        }
        val watching = known != null
        for ((index, stroke) in strokes.withIndex()) {
            if (isCancelled()) return SendResult.CANCELLED
            if (watching && index % CHECK_EVERY == 0 && gate.isChatOpen() == false) return SendResult.CHAT_NOT_OPEN
            if (stroke.shift) sink.press(XKeycode.KEY_SHIFT_L, 0)
            sink.press(stroke.key, stroke.keysym)
            sleep(KEY_HOLD_MS)
            sink.release(stroke.key)
            if (stroke.shift) sink.release(XKeycode.KEY_SHIFT_L)
            sleep(KEY_GAP_MS)
        }
        sleep(BEFORE_SEND_MS)
        if (isCancelled()) return SendResult.CANCELLED
        // The box closed meanwhile: Enter would open it again and leave it open for the next line.
        if (watching && gate.isChatOpen() == false) return SendResult.CHAT_NOT_OPEN
        tap(XKeycode.KEY_ENTER, 0, ENTER_HOLD_MS)
        return SendResult.SENT
    }

    /** The state of the chat box; waits a moment for the first reading after [ChatGate.prepare]. null = not known. */
    private fun waitForReading(): Boolean? {
        var waited = 0L
        var state = gate.isChatOpen()
        while (state == null && waited < WARM_UP_MS) {
            sleep(POLL_MS)
            waited += POLL_MS
            state = gate.isChatOpen()
        }
        return state
    }

    /** Presses Enter until the addon reports the box open (at most [OPEN_ATTEMPTS] times). false = it did not open. */
    private fun openChatChecked(): Boolean {
        repeat(OPEN_ATTEMPTS) {
            if (isCancelled()) return false
            tap(XKeycode.KEY_ENTER, 0, ENTER_HOLD_MS)
            var waited = 0L
            while (waited < OPEN_WAIT_MS) {
                sleep(POLL_MS)
                waited += POLL_MS
                if (gate.isChatOpen() == true) {
                    sleep(SETTLE_MS)
                    return true
                }
            }
            // No answer at all: a second Enter could close a box that did open. Only retry when the reading says "closed".
            if (gate.isChatOpen() != false) return false
        }
        return false
    }

    /** Ctrl+A, Backspace: empties an edit box that already holds text. */
    private fun clearBox() {
        sink.press(XKeycode.KEY_CTRL_L, 0)
        sink.press(XKeycode.KEY_A, 'a'.code)
        sleep(KEY_HOLD_MS)
        sink.release(XKeycode.KEY_A)
        sink.release(XKeycode.KEY_CTRL_L)
        sleep(KEY_GAP_MS)
        tap(XKeycode.KEY_BKSP, 0, KEY_HOLD_MS)
    }

    private fun tap(key: XKeycode, keysym: Int, holdMs: Long) {
        sink.press(key, keysym)
        sleep(holdMs)
        sink.release(key)
    }

    companion object {
        /** Pause after a blind opening Enter, before the first character. */
        const val OPEN_CHAT_MS = 250L
        const val KEY_HOLD_MS = 12L
        const val KEY_GAP_MS = 8L
        const val BEFORE_SEND_MS = 60L

        /** Enter is held longer than a letter: a slow game frame must not miss a press and release inside one frame. */
        const val ENTER_HOLD_MS = 40L

        /** Polling step, and how long to wait for the box to open / for the first reading. */
        const val POLL_MS = 25L
        const val OPEN_WAIT_MS = 700L
        const val WARM_UP_MS = 300L

        /** Pause after the box reports open (focus settled) or was emptied. */
        const val SETTLE_MS = 40L

        /** Enter presses at most (the second only if the reading still says closed). */
        const val OPEN_ATTEMPTS = 2

        /** The box is looked at again before every n-th character. */
        const val CHECK_EVERY = 6
    }
}
