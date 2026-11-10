package app.gamenative.ui.screen.wow.klick

import android.content.Context
import app.gamenative.ui.screen.wow.wowPrefs

/** The two extras of the touch mode that can be switched off. Both are on by default. */
internal data class TouchOptions(
    /** Tapping with two fingers sends a right click at the first finger. */
    val twoFingerRightClick: Boolean = true,
    /** Tap, touch again and rest (tap + hold) opens the click wheel; off = taps click at once and there is no wheel. */
    val clickWheel: Boolean = true,
    /** Resting a finger this long presses the left mouse button (drag items, spells, UI elements). */
    val holdMs: Int = DEFAULT_HOLD_MS,
    /** A tap's click waits this long for a second touch (tap + hold = wheel); 0 = click at once. */
    val tapWindowMs: Int = DEFAULT_TAP_WINDOW_MS,
    /** Click per wheel direction, as [ClickWheel.ACTIONS] ids. Missing or unknown ids mean the standard assignment. */
    val wheelAssignment: Map<WheelDirection, String> = ClickWheel.DEFAULT_ASSIGNMENT,
) {
    companion object {
        const val DEFAULT_HOLD_MS = 350
        const val MIN_HOLD_MS = 200
        const val MAX_HOLD_MS = 800
        const val HOLD_STEP_MS = 50
        const val DEFAULT_TAP_WINDOW_MS = 250
        const val MAX_TAP_WINDOW_MS = 400
        const val TAP_WINDOW_STEP_MS = 50
    }
}

internal object TouchOptionStore {
    private const val KEY_TWO_FINGER = "touch_two_finger_right_click"
    private const val KEY_WHEEL = "touch_click_wheel"
    private const val KEY_HOLD_MS = "touch_hold_ms"
    private const val KEY_TAP_WINDOW_MS = "touch_tap_window_ms"

    fun load(context: Context): TouchOptions {
        val prefs = wowPrefs(context)
        return TouchOptions(
            twoFingerRightClick = prefs.getBoolean(KEY_TWO_FINGER, true),
            clickWheel = prefs.getBoolean(KEY_WHEEL, true),
            holdMs = prefs.getInt(KEY_HOLD_MS, TouchOptions.DEFAULT_HOLD_MS).coerceIn(TouchOptions.MIN_HOLD_MS, TouchOptions.MAX_HOLD_MS),
            tapWindowMs = prefs.getInt(KEY_TAP_WINDOW_MS, TouchOptions.DEFAULT_TAP_WINDOW_MS).coerceIn(0, TouchOptions.MAX_TAP_WINDOW_MS),
            wheelAssignment = ClickWheel.assignmentFrom { direction -> prefs.getString(wheelKey(direction), null) },
        )
    }

    /** Preference key of one wheel direction, e.g. `touch_wheel_up`. */
    internal fun wheelKey(direction: WheelDirection): String = "touch_wheel_${direction.name.lowercase()}"

    /** Stores the action for one direction. Unknown action ids are ignored. */
    fun saveWheelAction(context: Context, direction: WheelDirection, actionId: String) {
        if (ClickWheel.actionById(actionId) == null) return
        wowPrefs(context).edit().putString(wheelKey(direction), actionId).apply()
    }

    fun saveTwoFingerRightClick(context: Context, on: Boolean) {
        wowPrefs(context).edit().putBoolean(KEY_TWO_FINGER, on).apply()
    }

    fun saveClickWheel(context: Context, on: Boolean) {
        wowPrefs(context).edit().putBoolean(KEY_WHEEL, on).apply()
    }

    fun saveHoldMs(context: Context, ms: Int) {
        wowPrefs(context).edit().putInt(KEY_HOLD_MS, ms.coerceIn(TouchOptions.MIN_HOLD_MS, TouchOptions.MAX_HOLD_MS)).apply()
    }

    fun saveTapWindowMs(context: Context, ms: Int) {
        wowPrefs(context).edit().putInt(KEY_TAP_WINDOW_MS, ms.coerceIn(0, TouchOptions.MAX_TAP_WINDOW_MS)).apply()
    }
}

/** Java-facing access to the stored wheel assignment (TouchpadView reads it whenever the wheel opens). */
object ClickWheelSetting {
    @JvmStatic
    fun fields(context: Context): List<ClickWheelField> =
        ClickWheel.fieldsFor(TouchOptionStore.load(context).wheelAssignment)
}
