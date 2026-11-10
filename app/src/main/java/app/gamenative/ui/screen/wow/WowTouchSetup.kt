package app.gamenative.ui.screen.wow

import android.content.Context
import app.gamenative.data.TouchGestureConfig
import app.gamenative.ui.screen.wow.klick.TouchOptionStore
import app.gamenative.ui.screen.wow.klick.TouchOptions
import org.json.JSONObject

/**
 * What the launcher remembers about the touch setup it wrote into the container at the last start. With it a start can tell
 * the launcher's own defaults from the player's changes (Quick menu -> Touchscreen-Modus switch, gear wheel gesture dialog)
 * and leave the player's changes alone.
 */
internal data class TouchSetupState(
    /** [InputMode.name] of the last start; null = unknown (installed before this was tracked). */
    val mode: String? = null,
    /** Gesture JSON the launcher itself wrote last (its defaults, not the player's edits); null = unknown. */
    val gestureBase: String? = null,
    /** The player's touch gestures while Standard is selected (the container's copy is cleared then); restored on return to Touch. */
    val gestureStash: String? = null,
    /** "Zurücksetzen": the next start writes the WoW defaults for the touchscreen switch and all gestures. */
    val forceDefaults: Boolean = false,
)

/** What a start writes into the container. [gestureConfig] null = leave the container's gesture JSON as it is. */
internal data class TouchSetupPlan(
    val touchscreenMode: Boolean,
    val gestureConfig: String?,
    val state: TouchSetupState,
)

/**
 * Pure decisions for the touch setup of a start (no Android classes, unit tested).
 *
 * Rule: the launcher's defaults are written on first setup, when the player picks another Steuerung, and after "Zurücksetzen".
 * Otherwise the player's values win: the touchscreen switch stays as the container has it, and a gesture field is only
 * set to the launcher's value when the player never changed it.
 */
internal object TouchSetupPolicy {
    /**
     * Gesture fields that mirror a launcher setting (click wheel, two-finger tap, hold time, double-tap window). They follow
     * the launcher when it was changed since the last start; otherwise the player's value from the gesture dialog stays.
     */
    val LAUNCHER_KEYS = setOf("clickWheelEnabled", "twoFingerTapEnabled", "holdLeftDelay", "tapWindowDelay")

    /**
     * @param mode the chosen Steuerung
     * @param defaults the launcher's gesture JSON for this start ([InputLaunchSettings.gestureJson])
     * @param hadTouchSetup the container still carries the touch setup of an earlier start (extra "wowTouch" or [wasApplied])
     * @param containerTouchscreen touchscreen switch as the container has it now (includes Quick menu changes)
     * @param containerGestures gesture JSON as the container has it now (includes gear wheel changes)
     */
    fun plan(
        mode: InputMode,
        defaults: String,
        hadTouchSetup: Boolean,
        containerTouchscreen: Boolean,
        containerGestures: String,
        state: TouchSetupState,
    ): TouchSetupPlan {
        // A touch mode with no gestures anywhere is a container that was created anew: set it up from scratch.
        val freshContainer = mode.isTouch && containerGestures.isBlank() && state.gestureStash.isNullOrBlank()
        val modeChanged = state.forceDefaults || freshContainer ||
            (state.mode?.let { it != mode.name } ?: (hadTouchSetup != mode.isTouch))
        val touchscreen = if (modeChanged) mode.isTouch else containerTouchscreen
        val next = state.copy(mode = mode.name, forceDefaults = false)
        return when {
            mode.isTouch -> {
                val source = containerGestures.ifBlank { state.gestureStash.orEmpty() }
                val gestures = if (state.forceDefaults) defaults else mergeGestures(source, state.gestureBase, defaults, LAUNCHER_KEYS)
                TouchSetupPlan(touchscreen, gestures, next.copy(gestureBase = defaults, gestureStash = null))
            }
            hadTouchSetup -> {
                // Back to Standard: the touch gestures leave the container, the player's version is kept for the way back.
                val stash = containerGestures.ifBlank { state.gestureStash.orEmpty() }.ifBlank { null }
                TouchSetupPlan(touchscreen, "", next.copy(gestureStash = if (state.forceDefaults) null else stash))
            }
            else -> TouchSetupPlan(touchscreen, null, next)
        }
    }

    /**
     * Three-way merge of gesture JSON: [current] is what the container holds, [base] what the launcher wrote last, [defaults]
     * what it writes now. A key the player changed (current differs from base) keeps the player's value; every other key
     * takes [defaults]. Keys only [current] has stay as they are. Without a [base] only the launcher-switch keys are set
     * from [defaults] and every other value that differs from [defaults] counts as the player's.
     */
    fun mergeGestures(current: String, base: String?, defaults: String, launcherKeys: Set<String>): String {
        val def = parse(defaults) ?: return defaults
        val cur = parse(current) ?: return defaults
        val baseObj = base?.let(::parse)
        val out = JSONObject(cur.toString())
        for (key in def.keys()) {
            val defaultValue = def.get(key)
            val baseValue = baseObj?.opt(key)
            val keep = if (key in launcherKeys) {
                // Flipped in the launcher since the last start (or unknown): the launcher's switch wins.
                baseValue != null && same(baseValue, defaultValue) && cur.has(key)
            } else {
                cur.has(key) && !same(cur.get(key), baseValue ?: defaultValue)
            }
            if (!keep) out.put(key, defaultValue)
        }
        return out.toString()
    }

    /**
     * A value set during the game (Seitenmenü > Steuerung): [gestures] with the four launcher fields set to [options].
     * Other fields and unknown keys stay. Blank or unreadable JSON comes back as it is (a container without touch gestures
     * keeps none; the preference alone carries the value to the next start).
     */
    fun applyOptions(gestures: String, options: TouchOptions): String {
        val obj = parse(gestures) ?: return gestures
        return obj.apply {
            put("clickWheelEnabled", options.clickWheel)
            put("twoFingerTapEnabled", options.twoFingerRightClick)
            put("holdLeftDelay", options.holdMs)
            put("tapWindowDelay", options.tapWindowMs)
        }.toString()
    }

    /**
     * The launcher's remembered base after a value was set during the game: the base takes the new value as well, so the next
     * start sees "launcher unchanged since the last start" and the container's value (the one just set) stays. Without it,
     * setting a value back to the launcher's old one would be mistaken for a player's change. Null stays null.
     */
    fun rebaseGestures(base: String?, options: TouchOptions): String? = base?.let { applyOptions(it, options) }

    /** The four launcher fields of [config] as options; [current] supplies the rest (wheel assignment). Values are clamped to the launcher's ranges. */
    fun optionsFrom(config: TouchGestureConfig, current: TouchOptions): TouchOptions = current.copy(
        twoFingerRightClick = config.twoFingerTapEnabled,
        clickWheel = config.clickWheelEnabled,
        holdMs = config.holdLeftDelay.coerceIn(TouchOptions.MIN_HOLD_MS, TouchOptions.MAX_HOLD_MS),
        tapWindowMs = config.tapWindowDelay.coerceIn(0, TouchOptions.MAX_TAP_WINDOW_MS),
    )

    private fun same(a: Any?, b: Any?): Boolean = a != null && b != null && a.toString() == b.toString()

    private fun parse(json: String): JSONObject? = try {
        if (json.isBlank()) null else JSONObject(json)
    } catch (_: Exception) {
        null
    }
}

/** Persists [TouchSetupState] in the launcher's preferences. */
internal object TouchSetupStore {
    private const val KEY_MODE = "touch_setup_mode"
    private const val KEY_BASE = "touch_setup_gesture_base"
    private const val KEY_STASH = "touch_setup_gesture_stash"
    private const val KEY_FORCE = "touch_setup_force_defaults"

    fun load(context: Context): TouchSetupState {
        val prefs = wowPrefs(context)
        return TouchSetupState(
            mode = prefs.getString(KEY_MODE, null),
            gestureBase = prefs.getString(KEY_BASE, null),
            gestureStash = prefs.getString(KEY_STASH, null),
            forceDefaults = prefs.getBoolean(KEY_FORCE, false),
        )
    }

    fun save(context: Context, state: TouchSetupState) {
        wowPrefs(context).edit()
            .putString(KEY_MODE, state.mode)
            .putString(KEY_BASE, state.gestureBase)
            .putString(KEY_STASH, state.gestureStash)
            .putBoolean(KEY_FORCE, state.forceDefaults)
            .apply()
    }

    /** "Zurücksetzen" in the launcher: the next start writes the WoW defaults (touchscreen switch and all gestures). */
    fun requestReset(context: Context) {
        save(context, load(context).copy(forceDefaults = true, gestureStash = null))
    }

    /**
     * A touch option was set during the game (Seitenmenü > Steuerung). The preference is the truth: the caller has stored [options]
     * there; this updates what the next start compares with (the launcher's base and the kept player copy) and returns the
     * gesture JSON the container should hold now, or null when the container carries no touch gestures (Standard).
     */
    fun applyInGame(context: Context, containerGestures: String, options: TouchOptions): String? {
        val state = load(context)
        save(
            context,
            state.copy(
                gestureBase = TouchSetupPolicy.rebaseGestures(state.gestureBase, options),
                gestureStash = state.gestureStash?.let { TouchSetupPolicy.applyOptions(it, options) },
            ),
        )
        return if (containerGestures.isBlank()) null else TouchSetupPolicy.applyOptions(containerGestures, options)
    }

    /**
     * The gesture dialog (gear wheel) saved [config]: its four launcher fields become the preferences too, so Seitenmenü and
     * launcher show what the game really uses. Fields outside the launcher's ranges are clamped in the preference only.
     */
    fun adoptGestures(context: Context, config: TouchGestureConfig) {
        val options = TouchSetupPolicy.optionsFrom(config, TouchOptionStore.load(context))
        TouchOptionStore.saveTwoFingerRightClick(context, options.twoFingerRightClick)
        TouchOptionStore.saveClickWheel(context, options.clickWheel)
        TouchOptionStore.saveHoldMs(context, options.holdMs)
        TouchOptionStore.saveTapWindowMs(context, options.tapWindowMs)
        val state = load(context)
        save(context, state.copy(gestureBase = TouchSetupPolicy.rebaseGestures(state.gestureBase, options)))
    }

    /**
     * "Auf WoW-Standard" in the gesture dialog during a game: returns the defaults to write into the container now and
     * remembers them as the launcher's base, so later starts treat them as untouched.
     */
    fun resetGestures(context: Context): app.gamenative.data.TouchGestureConfig {
        val defaults = WowTouch.gestureConfig(app.gamenative.ui.screen.wow.klick.TouchOptionStore.load(context))
        save(context, load(context).copy(gestureBase = defaults.toJson(), gestureStash = null))
        return defaults
    }
}
