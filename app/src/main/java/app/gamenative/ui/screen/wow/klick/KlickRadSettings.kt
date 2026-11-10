package app.gamenative.ui.screen.wow.klick

import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import app.gamenative.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.gamenative.ui.screen.wow.SettingNote
import app.gamenative.ui.screen.wow.SettingSwitchRow
import app.gamenative.ui.screen.wow.SettingValueRow
import app.gamenative.ui.screen.wow.SwipeScrollMath
import app.gamenative.ui.screen.wow.SwipeScrollSetting
import app.gamenative.ui.screen.wow.context.ContextMode
import app.gamenative.ui.screen.wow.context.ContextSetting

/** Next value of a stepper that wraps around: [min]..[max] in [step]s. */
internal fun nextStep(current: Int, min: Int, max: Int, step: Int): Int {
    val next = current + step
    return if (next > max) min else next.coerceAtLeast(min)
}

/** Short name of a wheel direction for the settings buttons (lower case, used inside a sentence). */
@StringRes
internal fun wheelLabelRes(direction: WheelDirection): Int = when (direction) {
    WheelDirection.CENTER -> R.string.game_wheel_dir_center
    WheelDirection.UP -> R.string.game_wheel_dir_up
    WheelDirection.RIGHT -> R.string.game_wheel_dir_right
    WheelDirection.DOWN -> R.string.game_wheel_dir_down
    WheelDirection.LEFT -> R.string.game_wheel_dir_left
}

/** Name of a wheel direction as the title of a menu row (capitalised). */
@StringRes
internal fun wheelRowRes(direction: WheelDirection): Int = when (direction) {
    WheelDirection.CENTER -> R.string.game_wheel_row_center
    WheelDirection.UP -> R.string.game_wheel_row_up
    WheelDirection.RIGHT -> R.string.game_wheel_row_right
    WheelDirection.DOWN -> R.string.game_wheel_row_down
    WheelDirection.LEFT -> R.string.game_wheel_row_left
}

/**
 * Zeilen der Touch-Einstellungen auf der Einstellungsseite: 2-Finger-Tipp = Rechtsklick, das Klick-Rad (Tippen + Halten)
 * mit Klick je Richtung, Haltezeit der linken Maustaste und Doppeltipp-Fenster. Wirkt ab dem nächsten Start.
 */
@Composable
internal fun KlickRadSettingsBlock(enabled: Boolean) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(TouchOptionStore.load(context)) }
    Column {
        SettingSwitchRow(
            title = stringResource(R.string.game_klick_two_finger),
            description = stringResource(R.string.game_klick_two_finger_desc),
            checked = options.twoFingerRightClick,
            enabled = enabled,
        ) { next ->
            TouchOptionStore.saveTwoFingerRightClick(context, next)
            options = options.copy(twoFingerRightClick = next)
        }
        SettingSwitchRow(
            title = stringResource(R.string.game_klick_wheel),
            description = stringResource(R.string.game_klick_wheel_desc),
            checked = options.clickWheel,
            enabled = enabled,
        ) { next ->
            TouchOptionStore.saveClickWheel(context, next)
            options = options.copy(clickWheel = next)
        }
        // Antippen wechselt reihum durch die Werte (Stepper, der am Ende wieder von vorn beginnt).
        SettingValueRow(
            title = stringResource(R.string.game_klick_hold),
            description = stringResource(R.string.game_klick_hold_desc),
            value = "${options.holdMs} ms",
            enabled = enabled,
        ) {
            val next = nextStep(options.holdMs, TouchOptions.MIN_HOLD_MS, TouchOptions.MAX_HOLD_MS, TouchOptions.HOLD_STEP_MS)
            TouchOptionStore.saveHoldMs(context, next)
            options = options.copy(holdMs = next)
        }
        SettingValueRow(
            title = stringResource(R.string.game_klick_tapwindow),
            description = stringResource(R.string.game_klick_tapwindow_desc),
            value = if (options.tapWindowMs == 0) stringResource(R.string.game_off_lc) else "${options.tapWindowMs} ms",
            enabled = enabled && options.clickWheel,
        ) {
            val next = nextStep(options.tapWindowMs, 0, TouchOptions.MAX_TAP_WINDOW_MS, TouchOptions.TAP_WINDOW_STEP_MS)
            TouchOptionStore.saveTapWindowMs(context, next)
            options = options.copy(tapWindowMs = next)
        }
        // Klick-Rad-Belegung je Richtung: Antippen wechselt durch die Aktionen (Linksklick, Rechtsklick, Shift, Strg, Alt, ...).
        for (direction in ClickWheel.DIRECTIONS) {
            val action = ClickWheel.actionFor(direction, options.wheelAssignment[direction])
            SettingValueRow(
                title = stringResource(R.string.game_wheel_title, stringResource(wheelLabelRes(direction))),
                value = action.label,
                enabled = enabled && options.clickWheel,
            ) {
                val next = ClickWheel.nextActionId(action.id)
                TouchOptionStore.saveWheelAction(context, direction, next)
                options = options.copy(wheelAssignment = options.wheelAssignment + (direction to next))
            }
        }
        // Wischen in Listen (Ruf, Optionen, Questlog ...): wirkt sofort, braucht das Addon TouchForever ab Stand 3.
        var swipeScroll by remember { mutableStateOf(SwipeScrollSetting.isEnabled(context)) }
        var swipeSpeed by remember { mutableStateOf(SwipeScrollSetting.speed(context)) }
        SettingSwitchRow(
            title = stringResource(R.string.game_klick_swipe),
            description = stringResource(R.string.game_klick_swipe_desc),
            checked = swipeScroll,
            enabled = enabled,
        ) { next ->
            SwipeScrollSetting.setEnabled(context, next)
            swipeScroll = next
        }
        SettingValueRow(
            title = stringResource(R.string.game_klick_swipe_speed),
            description = stringResource(R.string.game_klick_swipe_speed_desc),
            value = SwipeScrollMath.speedLabel(swipeSpeed),
            enabled = enabled && swipeScroll,
        ) {
            val next = SwipeScrollMath.nextSpeed(swipeSpeed)
            SwipeScrollSetting.setSpeed(context, next)
            swipeSpeed = next
        }
        // Kontextmenü beim Gedrückthalten auf Spielern, NPCs, Gegnern und Gegenständen: wirkt sofort, braucht das Addon ab Stand 4.
        var contextMode by remember { mutableStateOf(ContextSetting.mode(context)) }
        var tapView by remember { mutableStateOf(ContextSetting.tapViewOnly(context)) }
        SettingValueRow(
            title = stringResource(R.string.game_klick_context),
            description = stringResource(R.string.game_klick_context_desc),
            value = contextMode.label,
            enabled = enabled,
        ) {
            val next = contextMode.next()
            ContextSetting.setMode(context, next)
            contextMode = next
        }
        SettingSwitchRow(
            title = stringResource(R.string.game_klick_tapview),
            description = stringResource(R.string.game_klick_tapview_desc),
            checked = tapView,
            enabled = enabled && contextMode != ContextMode.OFF,
        ) { next ->
            ContextSetting.setTapViewOnly(context, next)
            tapView = next
        }
        SettingNote(stringResource(R.string.game_klick_note))
    }
}
