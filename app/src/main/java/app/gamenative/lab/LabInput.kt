package app.gamenative.lab

import com.winlator.xserver.Keyboard
import com.winlator.xserver.XServer
import java.util.EnumMap

/** Woher ein Tastenereignis am X-Server kam. */
enum class LabInputSource(val label: String) {
    /** Alles, was nicht von der Leiste oder dem Textfeld kommt: Android-Tastatur (über `IMEInputReceiver`), Gesten, Eingabegeräte. */
    ANDROID("android (soft keyboard, gestures, devices)"),
    BAR("bar"),
    TEXT("text field"),
    SIGNIN("sign-in key"),
}

/** Welche Rückrufe die Android-Tastatur an `IMEInputReceiver` richtet (nur Zahlen, nie Inhalt). */
enum class LabImeCall(val label: String) {
    COMMIT_TEXT("commitText"),
    DELETE_SURROUNDING("deleteSurroundingText"),
    SEND_KEY_EVENT("sendKeyEvent"),
    SET_COMPOSING("setComposingText"),
    FINISH_COMPOSING("finishComposingText"),
}

/** Das X-Fenster mit Eingabefokus, für das Protokoll: Kennung, Klasse, Name (auf Druckbares und 60 Zeichen gekürzt). */
data class LabFocus(val id: Int, val className: String, val name: String) {
    fun text(): String = "id=0x${Integer.toHexString(id)} class=${clean(className)} name=\"${clean(name)}\""

    private fun clean(s: String): String = s.filter { it.code in 32..126 }.take(60)

    companion object {
        const val NONE = "(no window has the input focus)"
    }
}

/**
 * Zähler für den Abschnitt "Input" des Labor-Protokolls: Zahl der Tastenereignisse am X-Server je Quelle (nie die Zeichen selbst), das
 * Fenster mit Eingabefokus beim ersten und letzten Tastenereignis, Fokuswechsel mit Zeit, ob die Android-Tastatur sichtbar war und
 * was sie an `IMEInputReceiver` gerichtet hat. Rein (Uhr ersetzbar), nicht an Android gebunden.
 */
class LabInputStats(private val startedMs: Long, private val nowMs: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val presses = EnumMap<LabInputSource, Int>(LabInputSource::class.java)
    private val ime = EnumMap<LabImeCall, Int>(LabImeCall::class.java)
    private var imeChars = 0
    private var pressesNoFocus = 0
    private var pressesKeyboardVisible = 0
    private var firstFocus: String? = null
    private var firstAtMs = 0L
    private var lastFocus: String? = null
    private var lastAtMs = 0L
    private var focusSeen = false
    private var currentFocus: String? = null
    private val changes = ArrayList<String>()
    private var changeCount = 0
    private var keyboardVisible: Boolean? = null
    private var keyboardShown = 0
    private var textSends = 0
    private var textChars = 0
    private var textSkipped = 0

    /** Ein Tastendruck am X-Server. [focus] ist null, wenn kein Fenster den Fokus hat. */
    fun onKeyPress(source: LabInputSource, focus: LabFocus?, keyboardVisible: Boolean) = synchronized(lock) {
        presses.merge(source, 1, Int::plus)
        if (focus == null) pressesNoFocus++
        if (keyboardVisible) pressesKeyboardVisible++
        val text = focus?.text() ?: LabFocus.NONE
        if (firstFocus == null) {
            firstFocus = text
            firstAtMs = nowMs()
        }
        lastFocus = text
        lastAtMs = nowMs()
        noteFocusLocked(focus)
        noteKeyboardLocked(keyboardVisible)
    }

    /** Regelmäßige Abfrage (ohne Tasten): hält Fokuswechsel und die Sichtbarkeit der Tastatur fest. */
    fun sample(focus: LabFocus?, keyboardVisible: Boolean) = synchronized(lock) {
        noteFocusLocked(focus)
        noteKeyboardLocked(keyboardVisible)
    }

    fun onImeCall(call: LabImeCall, chars: Int = 0) = synchronized(lock) {
        ime.merge(call, 1, Int::plus)
        if (call == LabImeCall.COMMIT_TEXT) imeChars += chars.coerceAtLeast(0)
    }

    fun onTextSent(sent: Int, skipped: Int) = synchronized(lock) {
        textSends++
        textChars += sent
        textSkipped += skipped
    }

    private fun noteFocusLocked(focus: LabFocus?) {
        val text = focus?.text() ?: LabFocus.NONE
        if (focusSeen && text == currentFocus) return
        focusSeen = true
        currentFocus = text
        changeCount++
        if (changes.size < MAX_CHANGES) changes += "  +${(nowMs() - startedMs) / 1000.0}s  $text"
    }

    private fun noteKeyboardLocked(visible: Boolean) {
        if (visible && keyboardVisible != true) keyboardShown++
        keyboardVisible = visible
    }

    /** Die Zeilen des Abschnitts "Input". */
    fun lines(): List<String> = synchronized(lock) {
        val total = presses.values.sum()
        buildList {
            add("Key presses sent to the X server: $total")
            for (source in LabInputSource.values()) add("  ${source.label}: ${presses[source] ?: 0}")
            add("  without a focused X window (dropped by the X server): $pressesNoFocus")
            add("  while the Android keyboard was visible: $pressesKeyboardVisible")
            add("Android keyboard became visible: $keyboardShown time(s); visible at the last check: ${keyboardVisible ?: "unknown"}")
            add("Calls of the Android keyboard to the app (counts only, never content):")
            for (call in LabImeCall.values()) add("  ${call.label}: ${ime[call] ?: 0}")
            add("  characters delivered by commitText: $imeChars")
            add("Text field: $textSends send(s), $textChars character(s) typed, $textSkipped skipped (no key for them)")
            add("X window with the input focus at the first key press: ${firstFocus?.let { "$it (+${(firstAtMs - startedMs) / 1000.0}s)" } ?: "no key press"}")
            add("X window with the input focus at the last key press: ${lastFocus?.let { "$it (+${(lastAtMs - startedMs) / 1000.0}s)" } ?: "no key press"}")
            add("Focus changes seen (checked about twice a second and at every key press): $changeCount")
            addAll(changes)
            if (changeCount > changes.size) add("  (${changeCount - changes.size} more not listed)")
        }
    }

    companion object {
        const val MAX_CHANGES = 40
    }
}

/**
 * Verbindung zum Labor-Protokoll: hält die Zähler des laufenden Versuchs, hängt sich an die Tastatur des X-Servers
 * ([Keyboard.OnKeyboardListener]: jedes eingespeiste Tastenereignis, gleich von wo) und merkt sich die Quelle je Thread. Ereignisse ohne
 * Markierung gelten als [LabInputSource.ANDROID]. Ohne laufenden Versuch und ohne [attach] geschieht nichts; das Spiel wird nie berührt.
 */
object LabInput {
    private val tag = ThreadLocal<LabInputSource?>()

    @Volatile private var stats: LabInputStats? = null
    @Volatile private var attached: Pair<XServer, Keyboard.OnKeyboardListener>? = null
    @Volatile private var keyboardVisible: () -> Boolean = { false }

    /** Neuer Versuch (aus `LabRun.begin`). */
    fun begin(startedMs: Long) {
        stats = LabInputStats(startedMs)
    }

    /** Abschnitt "Input" für das Protokoll; leer ohne Versuch. */
    fun lines(): List<String> = stats?.lines().orEmpty()

    /** Ende des Versuchs (aus `LabRun`, nachdem [lines] geschrieben wurde): Hörer ab, Zähler weg, damit das Spiel danach nichts mehr zählt. */
    fun end() {
        detach()
        stats = null
    }

    /** Führt [block] aus und markiert alle Tastenereignisse dieses Threads darin mit [source]. */
    fun <T> asSource(source: LabInputSource, block: () -> T): T {
        val before = tag.get()
        tag.set(source)
        try {
            return block()
        } finally {
            tag.set(before)
        }
    }

    /** Rückruf der Android-Tastatur (aus `IMEInputReceiver`); zählt nur, wenn ein Labor-Versuch läuft. */
    fun imeCall(call: LabImeCall, chars: Int = 0) {
        stats?.onImeCall(call, chars)
    }

    fun textSent(sent: Int, skipped: Int) {
        stats?.onTextSent(sent, skipped)
    }

    /** Hängt sich an [server] (einmal je Server); [visible] sagt, ob die Android-Tastatur gerade sichtbar ist. */
    fun attach(server: XServer, visible: () -> Boolean) {
        keyboardVisible = visible
        if (attached?.first === server) return
        detach()
        val listener = object : Keyboard.OnKeyboardListener {
            override fun onKeyPress(keycode: Byte, keysym: Int) {
                val s = stats ?: return
                s.onKeyPress(tag.get() ?: LabInputSource.ANDROID, focusOf(server), safeVisible())
            }

            override fun onKeyRelease(keycode: Byte) = Unit
        }
        server.keyboard.addOnKeyboardListener(listener)
        attached = server to listener
    }

    fun detach() {
        attached?.let { (server, listener) -> server.keyboard.removeOnKeyboardListener(listener) }
        attached = null
    }

    /** Regelmäßige Abfrage des Fokus (vom Thread der Leiste). */
    fun sample() {
        val s = stats ?: return
        val server = attached?.first ?: return
        s.sample(focusOf(server), safeVisible())
    }

    private fun safeVisible(): Boolean = runCatching { keyboardVisible() }.getOrDefault(false)

    private fun focusOf(server: XServer): LabFocus? = runCatching {
        server.windowManager.focusedWindow?.let { LabFocus(it.id, it.className.orEmpty(), LabWindows.titleOf(it)) }
    }.getOrNull()
}
