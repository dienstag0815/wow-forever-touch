package app.gamenative.ui.screen.wow

import android.content.Context
import app.gamenative.wowicons.AddonLayoutSignal

/** What the player picks in the dialog before the very first game start. */
internal enum class LayoutChoice { STANDARD, OWN }

/** What a [LayoutChoice] sets up. Pure data, unit tested. */
internal data class LayoutChoicePlan(
    val mode: InputMode,
    /** Create "Touch (Mein Layout)" from the basis template (ESC, keyboard, menu on top) before the start. */
    val createOwnProfile: Boolean,
    /** The new container starts with shooter mode on, which hides sticks; off for the chosen layout so its sticks show up. */
    val shooterModeOff: Boolean,
    /** Open the controls editor once the game is up (the editor only exists inside the game). */
    val openEditor: Boolean,
    /** Signal "Standardlayout aktiv" for the addon ([AddonLayoutSignal]). */
    val addonSignal: Boolean,
)

/**
 * Asks once, before the very first game start, whether to use the standard layout or build one's own. The state lives in three
 * flags of the "wow_forever" preferences; [shouldAsk] and [planFor] are pure.
 */
internal object FirstLayoutChoice {
    private const val KEY_DECIDED = "layout_choice"
    private const val KEY_SHOOTER_OFF = "layout_shooter_off_pending"
    private const val KEY_OPEN_EDITOR = "layout_open_editor_pending"

    /** Marker for "decided" without the dialog: the player picked a mode in the settings, or a backup was imported. */
    private const val BY_SETTINGS = "settings"
    private const val BY_IMPORT = "import"

    /** Ask only when nothing was decided yet and no game was ever started with this app. Pure, unit tested. */
    fun shouldAsk(decided: Boolean, launchedBefore: Boolean): Boolean = !decided && !launchedBefore

    fun planFor(choice: LayoutChoice): LayoutChoicePlan = when (choice) {
        LayoutChoice.STANDARD -> LayoutChoicePlan(InputMode.TOUCH_STANDARD, createOwnProfile = false, shooterModeOff = true, openEditor = false, addonSignal = true)
        LayoutChoice.OWN -> LayoutChoicePlan(InputMode.TOUCH_CUSTOM, createOwnProfile = true, shooterModeOff = true, openEditor = true, addonSignal = false)
    }

    /** The container's shooter mode for this start: a pending "off" from the dialog wins once, else the container keeps its value. */
    fun shooterModeFor(pendingOff: Boolean, current: Boolean): Boolean = if (pendingOff) false else current

    fun shouldAsk(context: Context): Boolean = shouldAsk(wowPrefs(context).contains(KEY_DECIDED), WowTouch.hasLaunched(context))

    /** Saves the choice (mode, flags) so the dialog never comes back; the caller starts the game afterwards. */
    fun choose(context: Context, choice: LayoutChoice) {
        val plan = planFor(choice)
        if (plan.createOwnProfile) WowTouchProfiles.createOwnLayoutFromBasis(context)
        InputMode.save(context, plan.mode)
        AddonLayoutSignal.set(context, plan.addonSignal)
        wowPrefs(context).edit()
            .putString(KEY_DECIDED, choice.name)
            .putBoolean(KEY_SHOOTER_OFF, plan.shooterModeOff)
            .putBoolean(KEY_OPEN_EDITOR, plan.openEditor)
            .apply()
    }

    /** The player chose a mode in the settings: no dialog needed any more. Keeps an existing choice. */
    fun markDecidedBySettings(context: Context) = markDecided(context, BY_SETTINGS)

    /** A backup import brought the player's own layout and mode: no dialog. Keeps an existing choice. */
    fun markDecidedByImport(context: Context) = markDecided(context, BY_IMPORT)

    private fun markDecided(context: Context, how: String) {
        val prefs = wowPrefs(context)
        if (!prefs.contains(KEY_DECIDED)) prefs.edit().putString(KEY_DECIDED, how).apply()
    }

    /** True once after the dialog: the next container setup switches shooter mode off. Reads and clears the flag. */
    fun takeShooterOff(context: Context): Boolean = take(context, KEY_SHOOTER_OFF)

    /** True once after "Eigenes Layout erstellen": the game screen opens the editor. Reads and clears the flag. */
    fun takeOpenEditor(context: Context): Boolean = take(context, KEY_OPEN_EDITOR)

    private fun take(context: Context, key: String): Boolean {
        val prefs = wowPrefs(context)
        if (!prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, false).apply()
        return true
    }
}
