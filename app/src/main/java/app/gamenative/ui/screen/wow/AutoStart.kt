package app.gamenative.ui.screen.wow

import app.gamenative.R
import app.gamenative.ui.components.AppText
import android.content.Context

/** Why the game does not start by itself. */
enum class AutoStartBlock {
    /** "Spiel automatisch starten" is off. */
    Disabled,

    /** A newer launcher is offered (or already downloading/installing): the update card comes first. */
    UpdateAvailable,

    /** The game files are outdated: the player decides between update and "launch anyway". */
    GameOutdated,

    /** A first-run permission prompt (microphone, notifications, battery) is still due before the game starts. */
    PermissionsPending,

    /** The game is already launching or its files are being updated. */
    Busy,

    /** The player tapped "Abbrechen". */
    UserStopped,

    /** The player tapped "Jetzt spielen": the countdown is replaced by the normal launch. */
    PlayedNow,
}

data class AutoStartInput(
    val enabled: Boolean,
    val launcherUpdateOffered: Boolean,
    val gameOutdated: Boolean,
    val permissionsPending: Boolean,
    val busy: Boolean,
    val userStopped: Boolean = false,
    /** The launcher's feed check is still running. */
    val checkRunning: Boolean = false,
)

sealed interface AutoStartStep {
    /** Show [secondsLeft] and wait one second. */
    data class Wait(val secondsLeft: Int) : AutoStartStep

    /** The countdown is over but the update check has not answered yet: hold for a moment. */
    data object WaitForCheck : AutoStartStep

    data object Start : AutoStartStep
    data class Stop(val reason: AutoStartBlock) : AutoStartStep
}

/** What the launcher shows for the automatic start. */
sealed interface AutoStartUi {
    data object Off : AutoStartUi

    /** [secondsLeft] = 0 with [waitingForCheck]: the countdown is over, the update check is still answering. */
    data class Counting(val secondsLeft: Int, val total: Int, val waitingForCheck: Boolean) : AutoStartUi
    data class Stopped(val reason: AutoStartBlock) : AutoStartUi
}

/**
 * Decision logic of the automatic start (pure, unit-tested). The launcher is always shown first; the game only starts
 * after a visible countdown, and only when nothing needs the player's attention: no launcher update, no outdated
 * game, no pending permission prompt. No network is not a reason to wait (the update check then reports "failed").
 */
object AutoStartLogic {
    const val DEFAULT_SECONDS = 5
    val CHOICES = listOf(3, 5, 10)

    /** After the countdown the answer of the update check is awaited at most this long. */
    const val CHECK_HOLD_MAX_MS = 5_000

    fun blockReason(i: AutoStartInput): AutoStartBlock? = when {
        i.userStopped -> AutoStartBlock.UserStopped
        !i.enabled -> AutoStartBlock.Disabled
        i.launcherUpdateOffered -> AutoStartBlock.UpdateAvailable
        i.gameOutdated -> AutoStartBlock.GameOutdated
        i.permissionsPending -> AutoStartBlock.PermissionsPending
        i.busy -> AutoStartBlock.Busy
        else -> null
    }

    /**
     * Next step of the countdown, evaluated again every second so a late update answer or a tap stops it.
     * [checkHoldExpired]: the update check did not answer within [CHECK_HOLD_MAX_MS]; then the game starts anyway.
     */
    fun step(secondsLeft: Int, input: AutoStartInput, checkHoldExpired: Boolean = false): AutoStartStep {
        blockReason(input)?.let { return AutoStartStep.Stop(it) }
        if (secondsLeft > 0) return AutoStartStep.Wait(secondsLeft)
        if (input.checkRunning && !checkHoldExpired) return AutoStartStep.WaitForCheck
        return AutoStartStep.Start
    }

    fun normalizeSeconds(seconds: Int): Int = if (seconds in CHOICES) seconds else DEFAULT_SECONDS

    /** 3 -> 5 -> 10 -> 3 for the countdown button. */
    fun nextSeconds(seconds: Int): Int {
        val i = CHOICES.indexOf(normalizeSeconds(seconds))
        return CHOICES[(i + 1) % CHOICES.size]
    }

    fun countdownText(secondsLeft: Int, waitingForCheck: Boolean): String =
        if (waitingForCheck) AppText.get(R.string.game_auto_soon) else AppText.get(R.string.game_auto_in, secondsLeft)

    /** One line under the update card for the reasons the player should know about; null = nothing to say. */
    fun blockText(reason: AutoStartBlock): String? = when (reason) {
        AutoStartBlock.UpdateAvailable -> AppText.get(R.string.game_auto_block_update)
        AutoStartBlock.GameOutdated -> AppText.get(R.string.game_auto_block_game)
        AutoStartBlock.UserStopped -> AppText.get(R.string.game_auto_stopped)
        else -> null
    }
}

/** Persistent switch and countdown length of the automatic start. */
object AutoStartSettings {
    private const val PREFS = "wow_forever_autostart"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SECONDS = "seconds"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Default on. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun seconds(context: Context): Int = AutoStartLogic.normalizeSeconds(prefs(context).getInt(KEY_SECONDS, AutoStartLogic.DEFAULT_SECONDS))

    fun setSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_SECONDS, AutoStartLogic.normalizeSeconds(seconds)).apply()
    }
}
