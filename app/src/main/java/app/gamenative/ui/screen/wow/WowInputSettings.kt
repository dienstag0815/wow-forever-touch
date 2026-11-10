package app.gamenative.ui.screen.wow

import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.components.AppText
import android.content.Context
import app.gamenative.data.TouchGestureConfig
import app.gamenative.ui.screen.wow.klick.TouchOptions
import com.winlator.inputcontrols.ControlElement

/** Gain steps offered for the on-screen sticks. 1.0 reaches full output at full deflection; the old fixed factor was 3.0. */
internal val STICK_GAIN_STEPS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)

internal const val DEFAULT_WALK_GAIN = 1.25f
internal const val DEFAULT_CAMERA_GAIN = 1.0f

/** "0.75" -> "0,75", "1.0" -> "1,0" in German (decimal comma), unchanged in English. */
internal fun gainLabel(gain: Float): String = if (AppText.isGerman()) gain.toString().replace('.', ',') else gain.toString()

/** A saved gain that is not one of the offered steps (hand-edited prefs) falls back to the default. */
internal fun sanitizeGain(saved: Float, default: Float): Float = STICK_GAIN_STEPS.firstOrNull { it == saved } ?: default

/** Sensitivity of the on-screen sticks. Walk = left stick, Camera = right stick / trackpad. Pushed into [ControlElement] at game start. */
internal object StickSensitivity {
    private const val KEY_WALK = "stick_gain_walk"
    private const val KEY_CAMERA = "stick_gain_camera"

    fun walk(context: Context): Float = sanitizeGain(wowPrefs(context).getFloat(KEY_WALK, DEFAULT_WALK_GAIN), DEFAULT_WALK_GAIN)

    fun camera(context: Context): Float = sanitizeGain(wowPrefs(context).getFloat(KEY_CAMERA, DEFAULT_CAMERA_GAIN), DEFAULT_CAMERA_GAIN)

    fun saveWalk(context: Context, gain: Float) {
        wowPrefs(context).edit().putFloat(KEY_WALK, gain).apply()
        apply(context)
    }

    fun saveCamera(context: Context, gain: Float) {
        wowPrefs(context).edit().putFloat(KEY_CAMERA, gain).apply()
        apply(context)
    }

    /** Hands the saved values to the controls; called when the game is prepared and whenever a value changes. */
    fun apply(context: Context) {
        ControlElement.stickSensitivityWalk = walk(context)
        ControlElement.stickSensitivityCamera = camera(context)
    }
}

internal fun wowPrefs(context: Context) = context.getSharedPreferences("wow_forever", Context.MODE_PRIVATE)

/**
 * How the game is controlled. CLASSIC keeps the previous profile; TOUCH is "tap like a mouse" for the unfolded Fold.
 * TOUCH_CUSTOM is the same with the player's own layout copy, which app updates never touch.
 * [profileName] is the on-screen controller profile the mode uses (null = the container's own profile stays).
 */
internal enum class InputMode(@StringRes val labelRes: Int, val profileName: String?) {
    CLASSIC(R.string.game_input_classic, null),
    TOUCH(R.string.game_input_touch, WowTouch.PROFILE_NAME),
    /** Touch with the complete template: walk stick with key ring, action arc, scroll wheel, wheels and top row. */
    TOUCH_FULL(R.string.game_input_touch_full, WowTouch.FULL_PROFILE_NAME),
    TOUCH_CUSTOM(R.string.game_input_touch_custom, WowTouch.CUSTOM_PROFILE_NAME);

    /** The name in the current app language (the profile itself keeps its stored [profileName]). */
    val label: String get() = AppText.get(labelRes)

    /** Touchscreen gestures and the touch profile; everything but CLASSIC. */
    val isTouch: Boolean get() = profileName != null

    companion object {
        private const val KEY_MODE = "input_mode"

        /**
         * Mode of a player who never chose one: a fresh install starts with the complete touch template, an app that was
         * already installed before (an update) keeps the old Standard mode. Pure, unit tested.
         */
        fun defaultFor(freshInstall: Boolean): InputMode = if (freshInstall) TOUCH_FULL else CLASSIC

        /** First install and last update time are equal: the app was never updated, so this is a new installation. */
        fun isFreshInstall(firstInstallTime: Long, lastUpdateTime: Long): Boolean = firstInstallTime == lastUpdateTime

        /** The saved mode; without a saved choice the default of [defaultFor] is decided once and remembered. */
        fun current(context: Context): InputMode {
            val prefs = wowPrefs(context)
            val saved = prefs.getString(KEY_MODE, null)
            if (saved != null) return entries.firstOrNull { it.name == saved } ?: CLASSIC
            val fresh = try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                isFreshInstall(info.firstInstallTime, info.lastUpdateTime)
            } catch (e: Exception) {
                false
            }
            return defaultFor(fresh).also { save(context, it) }
        }

        fun save(context: Context, mode: InputMode) {
            wowPrefs(context).edit().putString(KEY_MODE, mode.name).apply()
        }
    }
}

/** What a launch writes into the container and Config.wtf for one [InputMode]. Pure data, unit tested. */
internal data class InputLaunchSettings(
    /** Container flag: absolute touch gestures instead of the touchpad. */
    val touchscreenMode: Boolean,
    /** Container gesture JSON; empty keeps whatever the container has. */
    val gestureJson: String,
    /** Container extra "wowTouch" (the game screen keeps the stick visible in touchscreen mode); null removes it. */
    val wowTouchExtra: String?,
    /** Value for Config.wtf InputDeviceInterfaceStyle, null = leave the player's setting alone. */
    val interfaceStyle: String?,
)

internal object WowTouch {
    private const val KEY_APPLIED = "touch_applied"

    /** The last launch set up the touch mode, so a switch back to Standard has to undo it. */
    fun wasApplied(context: Context): Boolean = wowPrefs(context).getBoolean(KEY_APPLIED, false)

    fun markApplied(context: Context, applied: Boolean) {
        wowPrefs(context).edit().putBoolean(KEY_APPLIED, applied).apply()
    }

    const val PROFILE_NAME = "Touch (WoW)"
    /** The bundled complete template (controls-7.icp); "Layout zurücksetzen" writes it back. */
    const val FULL_PROFILE_NAME = "Touch (WoW komplett)"
    /** The player's own copy of [PROFILE_NAME]; not in the assets, so an update can never overwrite it. */
    const val CUSTOM_PROFILE_NAME = "Touch (Mein Layout)"
    const val EXTRA_KEY = "wowTouch"
    const val LONG_PRESS_MS = 400

    /** WoW: "0" = mouse and keyboard interface, "1" = gamepad interface (the launcher's handheld default). */
    const val STYLE_MOUSE = "0"
    const val STYLE_GAMEPAD = "1"

    /**
     * Gestures of the touch mode: tap = left click where the finger is (held back for the double-tap window while the
     * wheel is on), resting the finger = left mouse button held with the pointer following the finger (move talents, spells,
     * bag items, UI elements), tap + touch again and rest = click wheel (centre = right click, up = Shift click, right =
     * Ctrl click, left = Alt click, down = only point), one-finger drag = camera (right button held, relative movement),
     * pinch = mouse wheel, two-finger tap = right click at the first finger. Everything else is off so a stray second
     * finger cannot press arrow keys or middle-click. Three-finger tap (keyboard) and hold (Esc) stay.
     */
    fun gestureConfig(options: TouchOptions = TouchOptions()): TouchGestureConfig = TouchGestureConfig(
        tapEnabled = true,
        tapAction = TouchGestureConfig.ACTION_LEFT_CLICK,
        dragEnabled = true,
        dragAction = TouchGestureConfig.PAN_RIGHT_CLICK_DRAG,
        mouseDragMovementMode = TouchGestureConfig.MOUSE_DRAG_MOVEMENT_RELATIVE,
        holdMouseButtonWhileTouchingEnabled = false,
        longPressEnabled = true,
        longPressAction = TouchGestureConfig.ACTION_RIGHT_CLICK,
        longPressMouseBehavior = TouchGestureConfig.MOUSE_BEHAVIOR_CLICK,
        longPressDelay = LONG_PRESS_MS,
        clickWheelEnabled = options.clickWheel,
        holdLeftEnabled = true,
        holdLeftDelay = options.holdMs,
        tapWindowDelay = options.tapWindowMs,
        doubleTapEnabled = false,
        twoFingerDragEnabled = false,
        pinchEnabled = true,
        pinchAction = TouchGestureConfig.ZOOM_SCROLL_WHEEL,
        twoFingerTapEnabled = options.twoFingerRightClick,
        twoFingerTapAction = TouchGestureConfig.ACTION_RIGHT_CLICK,
        twoFingerHoldEnabled = false,
        threeFingerTapEnabled = true,
        threeFingerTapAction = TouchGestureConfig.ACTION_SHOW_KEYBOARD,
        threeFingerDragEnabled = false,
        threeFingerHoldEnabled = true,
        threeFingerHoldAction = TouchGestureConfig.ACTION_KEY_ESC,
        showCursorInTouchscreenMode = false,
    )

    /**
     * @param wasTouch the container still carries the touch setup of an earlier launch: switching back to Standard
     *                 then undoes it (touchscreen mode off, Config.wtf back to the gamepad interface).
     * @param options the player's switches for the two-finger tap and the click wheel (both on by default)
     */
    fun settingsFor(mode: InputMode, wasTouch: Boolean, options: TouchOptions = TouchOptions()): InputLaunchSettings = when {
        mode.isTouch -> InputLaunchSettings(
            touchscreenMode = true,
            gestureJson = gestureConfig(options).toJson(),
            wowTouchExtra = "1",
            interfaceStyle = STYLE_MOUSE,
        )
        wasTouch -> InputLaunchSettings(
            touchscreenMode = false,
            gestureJson = "",
            wowTouchExtra = null,
            interfaceStyle = STYLE_GAMEPAD,
        )
        else -> InputLaunchSettings(
            touchscreenMode = false,
            gestureJson = "",
            wowTouchExtra = null,
            interfaceStyle = null,
        )
    }
}
