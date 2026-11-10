package app.gamenative.ui.screen.wow.klick

import java.lang.ref.WeakReference
import java.util.EnumMap

/** State of one latching modifier button. */
enum class LatchMode {
    /** Key up. */
    OFF,

    /** Tapped once: held down until the next click or key, then released. */
    ONCE,

    /** Tapped twice: held down until the button is tapped again. */
    LOCKED,

    /** Finger rests on the button: held down only as long as the finger stays. */
    HELD,
}

/** What the caller has to do with the physical key after a latch event. */
enum class KeyChange { NONE, DOWN, UP }

/**
 * State machine of one latching modifier button (Shift, Ctrl or Alt on the screen), without Android types.
 *
 * - Short tap: the key goes down and stays down for the next click or key ([consume] releases it).
 * - Second short tap soon after the first: locked, until the button is tapped again.
 * - Finger rests longer than [tapMaxMs]: the key is held only while the finger is on the button.
 *
 * The key is pressed the moment the finger comes down ([press]), so a held modifier acts without delay.
 * All times are milliseconds from any monotonic clock.
 */
class ModifierLatch(
    private val tapMaxMs: Long = TAP_MAX_MS,
    private val doubleTapMs: Long = DOUBLE_TAP_MS,
) {
    var mode: LatchMode = LatchMode.OFF
        private set

    private var fingerDown = false
    private var pressedAt = 0L
    private var pressedFrom = LatchMode.OFF
    private var lastArmedAt = Long.MIN_VALUE / 2
    private var usedWhileHeld = false
    private var cancelled = false

    /** The physical key is down. */
    val keyDown: Boolean get() = mode != LatchMode.OFF

    fun press(nowMs: Long): KeyChange {
        if (fingerDown) return KeyChange.NONE
        val before = keyDown
        fingerDown = true
        pressedAt = nowMs
        pressedFrom = mode
        usedWhileHeld = false
        cancelled = false
        if (mode == LatchMode.OFF) mode = LatchMode.HELD
        return change(before)
    }

    /** @param wasCancelled the touch was torn down (not a deliberate lift): never arms the latch. */
    fun release(nowMs: Long, wasCancelled: Boolean = false): KeyChange {
        if (!fingerDown) return KeyChange.NONE
        val before = keyDown
        fingerDown = false
        val isTap = !wasCancelled && !usedWhileHeld && (nowMs - pressedAt) < tapMaxMs
        mode = when (pressedFrom) {
            LatchMode.OFF, LatchMode.HELD -> if (isTap) {
                lastArmedAt = nowMs
                LatchMode.ONCE
            } else {
                LatchMode.OFF
            }
            LatchMode.ONCE -> if (isTap && pressedAt - lastArmedAt <= doubleTapMs) LatchMode.LOCKED else LatchMode.OFF
            LatchMode.LOCKED -> LatchMode.OFF
        }
        return change(before)
    }

    /**
     * A click or key was sent: a single-use latch is released. A modifier that is held by a finger or locked stays;
     * if a finger is on the button, that is remembered so lifting it does not arm the key for the next click.
     */
    fun consume(): KeyChange {
        val before = keyDown
        // The key did its job while a finger is on the button: lifting that finger must not arm it for the next click.
        if (fingerDown) usedWhileHeld = true
        if (mode == LatchMode.ONCE) mode = LatchMode.OFF
        return change(before)
    }

    fun reset(): KeyChange {
        val before = keyDown
        mode = LatchMode.OFF
        fingerDown = false
        return change(before)
    }

    private fun change(before: Boolean): KeyChange {
        val after = keyDown
        return when {
            after && !before -> KeyChange.DOWN
            !after && before -> KeyChange.UP
            else -> KeyChange.NONE
        }
    }

    companion object {
        /** A press shorter than this is a tap. */
        const val TAP_MAX_MS = 300L

        /** Second tap must come down within this time after the first tap lifted. */
        const val DOUBLE_TAP_MS = 400L
    }
}

/** Where the registry sends the physical key presses. */
fun interface KeySink {
    fun send(key: ModKey, down: Boolean)
}

/**
 * The latching modifiers of the whole game: one [ModifierLatch] per key, shared by the on-screen buttons, the
 * touch surface and the click wheel. Only used on the main thread.
 */
object StickyModifiers {
    private val latches = EnumMap<ModKey, ModifierLatch>(ModKey::class.java)
    private var sink: KeySink? = null
    private var owner: Any? = null
    private var repaintTarget: WeakReference<android.view.View>? = null

    init {
        for (key in ModKey.values()) latches[key] = ModifierLatch()
    }

    /** Binds the registry to a key output. A new [newOwner] (another game session) starts with all keys up. */
    @JvmStatic
    @Synchronized
    fun attach(newOwner: Any, newSink: KeySink) {
        if (owner === newOwner) return
        releaseAll()
        owner = newOwner
        sink = newSink
    }

    /** The view that is redrawn when a latch changes. */
    @JvmStatic
    @Synchronized
    fun setRepaintTarget(view: android.view.View?) {
        repaintTarget = if (view != null) WeakReference(view) else null
    }

    @JvmStatic
    @Synchronized
    fun press(key: ModKey, nowMs: Long) {
        apply(key, latch(key).press(nowMs))
    }

    @JvmStatic
    @Synchronized
    fun release(key: ModKey, nowMs: Long, wasCancelled: Boolean) {
        apply(key, latch(key).release(nowMs, wasCancelled))
    }

    /** A click or key was sent: releases every single-use modifier. */
    @JvmStatic
    @Synchronized
    fun consume() {
        for (key in ModKey.values()) apply(key, latch(key).consume())
    }

    /** Keys up (view detached, session over). */
    @JvmStatic
    @Synchronized
    fun releaseAll() {
        for (key in ModKey.values()) apply(key, latch(key).reset())
    }

    @JvmStatic
    @Synchronized
    fun mode(key: ModKey): LatchMode = latch(key).mode

    @JvmStatic
    @Synchronized
    fun isDown(key: ModKey): Boolean = latch(key).keyDown

    /** All modifiers that are down right now (latched, locked or held). */
    @JvmStatic
    @Synchronized
    fun held(): Set<ModKey> = ModKey.values().filter { latch(it).keyDown }.toSet()

    private fun latch(key: ModKey): ModifierLatch = latches.getValue(key)

    private fun apply(key: ModKey, change: KeyChange) {
        if (change == KeyChange.NONE) return
        sink?.send(key, change == KeyChange.DOWN)
        repaintTarget?.get()?.postInvalidate()
    }

    /**
     * Which modifier a screen button latches: the button has exactly one key binding and that is a modifier
     * (binding names as in the Binding enum, "NONE" for an empty slot). Anything else (combos, other keys,
     * both slots filled) returns null and the button behaves as before.
     */
    @JvmStatic
    fun modifierOfButton(first: String?, second: String?): ModKey? {
        val a = first ?: return null
        val b = second ?: return null
        val aMod = modifierOfBinding(a)
        val bMod = modifierOfBinding(b)
        return when {
            aMod != null && b == "NONE" -> aMod
            bMod != null && a == "NONE" -> bMod
            else -> null
        }
    }

    /**
     * Does ending this single touch-gesture action ("left_click", "key_SPACE", ...) use up a single-use modifier?
     * Clicks and normal keys do; the modifier keys themselves, the on-screen keyboard and the radial menu do not.
     */
    @JvmStatic
    fun consumedBy(action: String?): Boolean {
        if (action == null) return false
        return when {
            action == "left_click" || action == "right_click" || action == "middle_click" -> true
            action.startsWith("key_") -> when (action) {
                "key_SHIFT_L", "key_SHIFT_R", "key_CTRL_L", "key_CTRL_R", "key_ALT_L", "key_ALT_R" -> false
                else -> true
            }
            else -> false
        }
    }

    @JvmStatic
    fun modifierOfBinding(name: String): ModKey? = when (name) {
        "KEY_SHIFT_L", "KEY_SHIFT_R" -> ModKey.SHIFT
        "KEY_CTRL_L", "KEY_CTRL_R" -> ModKey.CTRL
        "KEY_ALT_L", "KEY_ALT_R" -> ModKey.ALT
        else -> null
    }
}
