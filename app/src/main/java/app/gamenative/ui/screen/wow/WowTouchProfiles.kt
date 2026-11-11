package app.gamenative.ui.screen.wow

import app.gamenative.R
import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.PluviaApp
import com.winlator.inputcontrols.InputControlsManager
import timber.log.Timber

/** Finds, creates and resets the on-screen controller profiles of the touch modes. */
internal object WowTouchProfiles {
    /** The bundled "Touch (WoW)" profile; "Layout zurücksetzen" writes it back. */
    const val ASSET_PATH = "inputcontrols/profiles/controls-6.icp"

    /** The bundled "Touch (WoW komplett)" template. */
    const val FULL_ASSET_PATH = "inputcontrols/profiles/controls-7.icp"

    /** The bundled "Touch (Standard)" layout offered at the first start (both sticks with a key ring). */
    const val STANDARD_ASSET_PATH = "inputcontrols/profiles/controls-8.icp"

    /** Start of "Eigenes Layout erstellen": only ESC, keyboard and menu on top. Not a profile asset, so it is never copied to the profile list. */
    const val BASIS_ASSET_PATH = "inputcontrols/vorlagen/basis.icp"

    /** Bundled layout a reset of [mode] writes back: its own template; "Mein Layout" falls back to the standard touch one. */
    fun assetPathFor(mode: InputMode): String = when (mode) {
        InputMode.TOUCH_FULL -> FULL_ASSET_PATH
        InputMode.TOUCH_STANDARD -> STANDARD_ASSET_PATH
        else -> ASSET_PATH
    }

    /** Id of the profile called [name] among (id, name) pairs, or null. Pure, unit tested. */
    fun findProfileId(profiles: List<Pair<Int, String>>, name: String): Int? =
        profiles.firstOrNull { it.second == name }?.first

    /** Which touch profile "Layout zurücksetzen" acts on: the chosen touch mode's, else the standard touch profile. */
    fun resetTarget(mode: InputMode): InputMode = if (mode.isTouch) mode else InputMode.TOUCH

    /** Text of the reset confirmation; the argument is [resetTargetName]. */
    @StringRes
    val RESET_CONFIRM_TEXT: Int = R.string.game_reset_layout_confirm

    /** Name of the profile a reset of [mode] writes over (shown in the confirmation). */
    fun resetTargetName(mode: InputMode): String = resetTarget(mode).profileName.orEmpty()

    /** Like [resetTargetName], but with the player's chosen own layout ("Mein Layout" mode) and without the "Touch (…)" wrapper. */
    fun resetTargetDisplayName(context: Context, mode: InputMode): String {
        val target = resetTarget(mode)
        val name = TouchLayouts.effectiveName(target, TouchLayouts.chosenName(context)).orEmpty()
        return TouchLayouts.displayName(name)
    }

    internal fun manager(context: Context) = PluviaApp.inputControlsManager ?: InputControlsManager(context)

    /**
     * Id of the profile [mode] uses. "Touch (Mein Layout)" is created as a copy of the current "Touch (WoW)" the first
     * time it is needed. Null when the profile is missing and cannot be created (or [mode] has no profile).
     */
    fun resolveProfileId(context: Context, mode: InputMode): Int? {
        val name = mode.profileName ?: return null
        val manager = manager(context)
        val profiles = manager.getProfiles(false)
        // A layout the player saved under its own name wins in "Mein Layout" mode (see TouchLayouts).
        TouchLayouts.pickProfileId(profiles.map { it.id to it.name }, mode, TouchLayouts.chosenName(context))?.let { return it }
        if (mode == InputMode.TOUCH_FULL || mode == InputMode.TOUCH_STANDARD) return createFromAsset(manager, name, assetPathFor(mode))
        if (mode != InputMode.TOUCH_CUSTOM) return null
        val source = profiles.firstOrNull { it.name == WowTouch.PROFILE_NAME }
        if (source == null) {
            Timber.w("Cannot create '%s': '%s' not found", name, WowTouch.PROFILE_NAME)
            return null
        }
        return manager.copyProfileAs(source, name)?.id
    }

    /** Safety net: the update copy of the assets normally creates the template; if it is missing, create it from the asset. */
    private fun createFromAsset(manager: InputControlsManager, name: String, assetPath: String): Int? {
        val created = manager.createProfile(name) ?: return null
        if (!manager.resetProfileToAsset(created, assetPath)) {
            Timber.w("Cannot create '%s' from %s", name, assetPath)
            return null
        }
        return created.id
    }

    /**
     * "Eigenes Layout erstellen" at the first start: creates "Touch (Mein Layout)" with only ESC, keyboard and menu on top, unless
     * the player already has that profile (then nothing is touched). Returns whether the profile exists afterwards.
     */
    fun createOwnLayoutFromBasis(context: Context): Boolean {
        val manager = manager(context)
        val name = InputMode.TOUCH_CUSTOM.profileName ?: return false
        if (findProfileId(manager.getProfiles(false).map { it.id to it.name }, name) != null) return true
        return createFromAsset(manager, name, BASIS_ASSET_PATH) != null
    }

    /** Writes the app's default layout over the chosen touch profile. Returns whether it worked. */
    fun reset(context: Context, mode: InputMode): Boolean {
        val target = resetTarget(mode)
        val id = resolveProfileId(context, target) ?: return false
        val manager = manager(context)
        val profile = manager.getProfile(id) ?: return false
        return manager.resetProfileToAsset(profile, assetPathFor(target))
    }
}
