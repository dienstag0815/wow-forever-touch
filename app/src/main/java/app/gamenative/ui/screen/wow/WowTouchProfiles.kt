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

    /** Bundled layout a reset of [mode] writes back: its own template; "Mein Layout" falls back to the standard touch one. */
    fun assetPathFor(mode: InputMode): String = if (mode == InputMode.TOUCH_FULL) FULL_ASSET_PATH else ASSET_PATH

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

    private fun manager(context: Context) = PluviaApp.inputControlsManager ?: InputControlsManager(context)

    /**
     * Id of the profile [mode] uses. "Touch (Mein Layout)" is created as a copy of the current "Touch (WoW)" the first
     * time it is needed. Null when the profile is missing and cannot be created (or [mode] has no profile).
     */
    fun resolveProfileId(context: Context, mode: InputMode): Int? {
        val name = mode.profileName ?: return null
        val manager = manager(context)
        val profiles = manager.getProfiles(false)
        findProfileId(profiles.map { it.id to it.name }, name)?.let { return it }
        if (mode == InputMode.TOUCH_FULL) return createFromAsset(manager, name, FULL_ASSET_PATH)
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

    /** Writes the app's default layout over the chosen touch profile. Returns whether it worked. */
    fun reset(context: Context, mode: InputMode): Boolean {
        val target = resetTarget(mode)
        val id = resolveProfileId(context, target) ?: return false
        val manager = manager(context)
        val profile = manager.getProfile(id) ?: return false
        return manager.resetProfileToAsset(profile, assetPathFor(target))
    }
}
