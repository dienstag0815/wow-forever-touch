package app.gamenative.wowaction

import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.WowActionBindings

/**
 * Protection against a double tap on the reload button ("Oberfläche neu laden"): a press of the reload key within [LOCK_MS]
 * of the last accepted one is dropped, together with its release. Sits at the one place all key output of the touch controls
 * passes ([com.winlator.widget.InputControlsView.handleInputEvent]), so it holds for a button, a ring segment, a wheel field and
 * the automatic reload alike. The key is that of the catalog action [ACTION_ID] ([RELOAD_KEY], or what the player resolved).
 */
object ReloadGuard {
    /** Id of the catalog action. */
    const val ACTION_ID = "TF_RELOAD"

    /** The key the addon binds to its secure reload button (an override binding, see TouchForever.lua). */
    const val RELOAD_KEY = "CTRL-SHIFT-F4"

    /** After an accepted press the key is locked for this long. */
    const val LOCK_MS = 2_000L

    private var lastAcceptedMs = Long.MIN_VALUE / 2
    private var swallowedDown = false

    /** The combination of the reload key now (resolved keys first, else the standard key). */
    @JvmStatic
    fun reloadCombo(): BindingCombo? = WowActionBindings.resolve(ACTION_ID, WowKeys.toCombo(RELOAD_KEY))

    /**
     * Whether the key event goes through: false = drop it. Other key combinations always pass. [nowMs] is a monotonic clock.
     */
    @JvmStatic
    @Synchronized
    fun allow(combo: BindingCombo, isDown: Boolean, nowMs: Long): Boolean = allow(combo, isDown, nowMs, reloadCombo())

    @JvmStatic
    @Synchronized
    fun allow(combo: BindingCombo, isDown: Boolean, nowMs: Long, reload: BindingCombo?): Boolean {
        if (reload == null || combo.isSequence || combo.bindings != reload.bindings) return true
        if (isDown) {
            if (nowMs - lastAcceptedMs < LOCK_MS) {
                swallowedDown = true
                return false
            }
            lastAcceptedMs = nowMs
            swallowedDown = false
            return true
        }
        if (swallowedDown) {
            swallowedDown = false
            return false
        }
        return true
    }

    /** Test helper: forget the last press. */
    @JvmStatic
    @Synchronized
    fun reset() {
        lastAcceptedMs = Long.MIN_VALUE / 2
        swallowedDown = false
    }
}
