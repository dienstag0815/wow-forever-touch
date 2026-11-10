package app.gamenative.cooldown

import app.gamenative.R
import app.gamenative.ui.components.AppText

/** What the app does about a running addon that is older than the one it brought. */
enum class ReloadAction {
    NONE,

    /** Press the reload key of the addon (build 2 and up has it): a hardware key press runs the secure /reload macro. */
    RELOAD_KEY,

    /** Type `/reload` into the chat (an addon without the key). */
    RELOAD_CHAT,

    /** The running addon is old AND the files on the disk are not the bundled ones: a reload would not help, only a hint. */
    DISK_OUTDATED,
}

/**
 * Decides, from the strip, whether the running addon is older than the bundled one and what to do about it. Pure (the time
 * comes from the caller), one instance per game session, only the Present thread uses it.
 *
 * Rules (all must hold, otherwise [ReloadAction.NONE] and the question stays open):
 *  - the build is known (0 = an addon before the window section, 1..15 = section version; -1 = unreadable, no decision);
 *  - it is lower than the bundled one for [settleMs] without interruption (the strip exists = the player is in the world; the
 *    wait covers a loading screen and a half-drawn strip);
 *  - not in combat, chat edit box not open (a key press would go into the chat);
 *  - at most once per session ([finished]): a reload that did not help is not repeated.
 * If the files on the disk are not the bundled ones ([diskCurrent] false) the answer is [ReloadAction.DISK_OUTDATED] once, because a
 * reload would load the old files again.
 */
class AddonReloadRule(
    private val expectedBuild: Int = CooldownStrip.ADDON_BUILD,
    private val keyBuild: Int = CooldownStrip.RELOAD_KEY_BUILD,
    private val settleMs: Long = SETTLE_MS,
) {
    private var outdatedSince = NONE

    /** Nothing more to decide this session (the addon is current, or the one action was taken). */
    var finished = false
        private set

    /** Build of the last readable strip, -1 before the first one. */
    var lastBuild = -1
        private set

    /** The action taken this session, [ReloadAction.NONE] while none was. */
    var taken = ReloadAction.NONE
        private set

    /**
     * One read of the strip. [build] is [CooldownStrip.Decoded.addonBuild]; [combat] and [chatOpen] as the addon reports them
     * (see [inCombat]); [diskCurrent] from [app.gamenative.wowicons.AddonSetup.diskCurrent], null = unknown (counts as current).
     */
    fun onStrip(build: Int, combat: Boolean, chatOpen: Boolean?, diskCurrent: Boolean?, nowMs: Long): ReloadAction {
        if (finished) return ReloadAction.NONE
        if (build < 0) {
            outdatedSince = NONE
            return ReloadAction.NONE
        }
        lastBuild = build
        if (build >= expectedBuild) {
            finished = true
            outdatedSince = NONE
            return ReloadAction.NONE
        }
        if (outdatedSince == NONE) outdatedSince = nowMs
        if (nowMs - outdatedSince < settleMs) return ReloadAction.NONE
        if (diskCurrent == false) return take(ReloadAction.DISK_OUTDATED)
        if (combat || chatOpen == true) return ReloadAction.NONE
        return take(if (build >= keyBuild) ReloadAction.RELOAD_KEY else ReloadAction.RELOAD_CHAT)
    }

    private fun take(action: ReloadAction): ReloadAction {
        finished = true
        taken = action
        return action
    }

    /** One line for the status text of the service. */
    fun statusText(): String = when {
        lastBuild < 0 -> AppText.get(R.string.game_addon_unknown)
        lastBuild >= expectedBuild -> AppText.get(R.string.game_addon_current, lastBuild)
        taken == ReloadAction.DISK_OUTDATED -> AppText.get(R.string.game_addon_old_disk, lastBuild, expectedBuild)
        taken != ReloadAction.NONE -> AppText.get(R.string.game_addon_old_reload, lastBuild, expectedBuild)
        else -> AppText.get(R.string.game_addon_old, lastBuild, expectedBuild)
    }

    companion object {
        private const val NONE = Long.MIN_VALUE

        /** So long the strip must show an older build before anything happens. */
        const val SETTLE_MS = 4_000L

        /**
         * Whether the player is in combat according to the strip: the section flag of build 2 and up; an older addon has none,
         * there the hidden cooldown values ([CooldownStrip.I_SECRET]) are the best sign.
         */
        @JvmStatic
        fun inCombat(build: Int, windowFlags: Int, infoFlags: Int): Boolean =
            if (build >= 2) windowFlags and CooldownStrip.WF_COMBAT != 0 else infoFlags and CooldownStrip.I_SECRET != 0
    }
}
