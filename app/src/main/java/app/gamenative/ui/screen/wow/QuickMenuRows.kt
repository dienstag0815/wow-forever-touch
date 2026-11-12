package app.gamenative.ui.screen.wow

import android.content.Context
import app.gamenative.R
import app.gamenative.ui.component.QuickMenuAction
import app.gamenative.ui.components.TextRef
import app.gamenative.ui.screen.wow.context.ContextMode
import app.gamenative.ui.screen.wow.klick.ClickWheel
import app.gamenative.ui.screen.wow.klick.TouchOptions
import app.gamenative.ui.screen.wow.klick.WheelDirection
import app.gamenative.ui.screen.wow.klick.wheelRowRes
import app.gamenative.ui.touchstyle.TouchStyle
import app.gamenative.ui.touchstyle.TouchStyleMath
import kotlin.math.abs

/**
 * Zeilen des Seitenmenüs als reine Daten: welche Art Zeile eine ID ist, was links (Titel) und rechts (Wert) steht, und die
 * Kurzinfo einer zugeklappten Gruppe. QuickMenu.kt zeichnet nur noch, was hier steht. Ohne Android-Klassen, getestet.
 *
 * Regel für Titel: eine Zeile, kurz. Das Seitenmenü hat rund 287 dp Inhalt (Außendisplay rund 193 dp); der Wert steht rechts,
 * der Titel gibt nach (Auslassungspunkte). Darum höchstens [QUICK_MENU_TITLE_MAX] Zeichen.
 */
internal const val QUICK_MENU_TITLE_MAX = 24

/** Die Art einer Zeile. */
internal enum class QuickMenuRowKind {
    /** Zeile mit rundem Symbol links (nur noch im Reiter Hilfe). */
    ICON,

    /** Titel links, Schalter rechts. */
    TOGGLE,

    /** Titel links, rechts "- Wert +". */
    STEP,

    /** Titel links, Wert oder Aktionswort rechts; Antippen führt aus, schaltet reihum oder öffnet eine Auswahl. */
    VALUE,

    /** Titel, darunter nebeneinander die Auswahlknöpfe. */
    CHOICE,

    /** Nur Text, keine Eingabe. */
    NOTE,
}

/** Art der Zeile [id]; null für die Zeilen außerhalb des Menüs (Ende-Symbol, HUD-Schalter). */
internal fun quickMenuRowKind(id: Int): QuickMenuRowKind? = when (id) {
    QuickMenuAction.REPORT, QuickMenuAction.REPORT_NOTE -> QuickMenuRowKind.ICON
    QuickMenuAction.INPUT_CONTROLS, QuickMenuAction.TOUCHSCREEN_MODE, QuickMenuAction.TWO_FINGER_TAP,
    QuickMenuAction.TAP_VIEW_ITEMS, QuickMenuAction.CLICK_WHEEL, QuickMenuAction.SWIPE_SCROLL,
    QuickMenuAction.TOUCH_HIDE_LOGIN, QuickMenuAction.SHOOTER_MODE, QuickMenuAction.GYRO, QuickMenuAction.DISABLE_MOUSE,
    QuickMenuAction.COOLDOWNS, QuickMenuAction.COOLDOWN_GCD,
    -> QuickMenuRowKind.TOGGLE
    QuickMenuAction.STICK_WALK, QuickMenuAction.STICK_CAMERA, QuickMenuAction.HOLD_TIME, QuickMenuAction.TAP_WINDOW,
    QuickMenuAction.SWIPE_SPEED, QuickMenuAction.TOUCH_CONTRAST,
    -> QuickMenuRowKind.STEP
    QuickMenuAction.CONTEXT_MENU, QuickMenuAction.TOUCH_HIDE_WINDOWS, QuickMenuAction.TOUCH_STYLE -> QuickMenuRowKind.CHOICE
    QuickMenuAction.LOGIN_NOTE -> QuickMenuRowKind.NOTE
    QuickMenuAction.EDIT_CONTROLS, QuickMenuAction.EDIT_PHYSICAL_CONTROLLER, QuickMenuAction.RESET_LAYOUT,
    QuickMenuAction.LAYOUT_CHOOSE, QuickMenuAction.LAYOUT_SAVE_AS,
    QuickMenuAction.RADIAL_MENU, QuickMenuAction.TOUCH_SHOW_NOW, QuickMenuAction.WHEEL_CENTER, QuickMenuAction.WHEEL_UP,
    QuickMenuAction.WHEEL_RIGHT, QuickMenuAction.WHEEL_DOWN, QuickMenuAction.WHEEL_LEFT, QuickMenuAction.KEYBOARD,
    QuickMenuAction.LOGIN_FULL, QuickMenuAction.LOGIN_FILL, QuickMenuAction.LOGIN_PASSWORD, QuickMenuAction.LOGIN_EMAIL, QuickMenuAction.CHECK_ICONS,
    QuickMenuAction.ADDON_OPTIONS, QuickMenuAction.VOICE_LANGUAGE,
    -> QuickMenuRowKind.VALUE
    else -> null
}

/** Richtung des Klick-Rads einer der fünf Zeilen, sonst null. */
internal fun wheelDirectionOf(id: Int): WheelDirection? = when (id) {
    QuickMenuAction.WHEEL_CENTER -> WheelDirection.CENTER
    QuickMenuAction.WHEEL_UP -> WheelDirection.UP
    QuickMenuAction.WHEEL_RIGHT -> WheelDirection.RIGHT
    QuickMenuAction.WHEEL_DOWN -> WheelDirection.DOWN
    QuickMenuAction.WHEEL_LEFT -> WheelDirection.LEFT
    else -> null
}

/** Alle Werte, die Zeilen und Kurzinfos zeigen. Die Vorgaben sind die Standardwerte der App. */
internal data class QuickMenuValues(
    val walkGain: Float = DEFAULT_WALK_GAIN,
    val cameraGain: Float = DEFAULT_CAMERA_GAIN,
    val twoFingerRightClick: Boolean = true,
    val holdMs: Int = TouchOptions.DEFAULT_HOLD_MS,
    val tapWindowMs: Int = TouchOptions.DEFAULT_TAP_WINDOW_MS,
    val contextMode: ContextMode = ContextMode.AUTO,
    val clickWheel: Boolean = true,
    val wheelAssignment: Map<WheelDirection, String> = ClickWheel.DEFAULT_ASSIGNMENT,
    val swipeScroll: Boolean = true,
    val swipeSpeed: Float = SwipeScrollMath.DEFAULT_SPEED,
    val windowHide: WindowHideMode = WindowHideMode.DEFAULT,
    val style: TouchStyle = TouchStyle.DEFAULT,
    val contrast: Float = TouchStyleMath.CONTRAST_DEFAULT,
    val cooldowns: Boolean = false,
    val shooterMode: Boolean = false,
    val gyro: Boolean = false,
    val mouseOff: Boolean = false,
    val hasLogin: Boolean = false,
    val loginNote: String? = null,
    val addonNote: String? = null,
    val layoutNote: String? = null,
    val layoutName: String = "",
    val layoutSwitchNote: String? = null,
    val voiceLabel: String = "",
)

/**
 * Eine Zeile zum Zeichnen; die Texte sind noch nicht in eine Sprache übersetzt ([TextRef]), das macht [resolve] beim Zeichnen.
 *
 * @property value rechts neben dem Titel (STEP, VALUE) oder der Text einer NOTE-Zeile
 * @property chips Beschriftungen der Auswahlknöpfe (CHOICE), [selected] ist der gewählte
 * @property detail kleine Zeile unter den Knöpfen: voller Name des Gewählten, wenn der Knopf ihn abkürzt
 * @property enabled false = gedämpft und nicht bedienbar
 * @property note kleine Zeile unter der Zeile (zum Beispiel warum etwas nicht ging); schon fertiger Text
 */
internal data class QuickMenuRowView(
    val kind: QuickMenuRowKind,
    val title: TextRef,
    val value: TextRef = EMPTY_TEXT,
    val chips: List<TextRef> = emptyList(),
    val selected: Int = -1,
    val detail: TextRef? = null,
    val enabled: Boolean = true,
    val note: String? = null,
) {
    /** Dieselbe Zeile mit fertigen Texten in der Sprache von [context]. */
    fun resolve(context: Context): QuickMenuRowText = QuickMenuRowText(
        kind = kind,
        title = title.resolve(context),
        value = value.resolve(context),
        chips = chips.map { it.resolve(context) },
        selected = selected,
        detail = detail?.resolve(context),
        enabled = enabled,
        note = note,
    )
}

/** Eine Zeile mit fertigen Texten, so wie QuickMenu.kt sie zeichnet. */
internal data class QuickMenuRowText(
    val kind: QuickMenuRowKind,
    val title: String,
    val value: String,
    val chips: List<String>,
    val selected: Int,
    val detail: String?,
    val enabled: Boolean,
    val note: String?,
)

/** Leerer Wert einer Zeile. */
internal val EMPTY_TEXT = TextRef.plain("")

/** Kurze Knopfbeschriftungen für die Auswahl "Kontextmenü beim Halten" (gleiche Reihenfolge wie [ContextMode]). */
internal val CONTEXT_CHIPS = listOf(
    TextRef(R.string.game_off),
    TextRef(R.string.game_qm_row_chip_context_auto),
    TextRef(R.string.game_qm_row_chip_context_bar),
)

/** Kurze Knopfbeschriftungen für "Über Spielfenstern ausblenden" (gleiche Reihenfolge wie [WindowHideMode]). */
internal val WINDOW_HIDE_CHIPS = listOf(
    TextRef(R.string.game_qm_row_chip_hide_covered),
    TextRef(R.string.game_qm_row_chip_hide_all),
    TextRef(R.string.game_off),
)

/** Kurze Knopfbeschriftungen für "Aussehen" (gleiche Reihenfolge wie [TouchStyle]). */
internal val STYLE_CHIPS = listOf(TextRef(R.string.game_qm_row_style_current), TextRef.plain("F"), TextRef.plain("G"))

/** Das Doppeltipp-Fenster als Wert: 0 heißt, der Klick geht sofort durch. */
internal fun tapWindowLabel(ms: Int): TextRef = if (ms <= 0) TextRef(R.string.game_off_lc) else TextRef(R.string.game_qm_ms, ms)

private fun choiceView(title: TextRef, chips: List<TextRef>, selected: Int, full: TextRef): QuickMenuRowView =
    QuickMenuRowView(
        kind = QuickMenuRowKind.CHOICE,
        title = title,
        chips = chips,
        selected = selected,
        detail = full.takeIf { selected in chips.indices && it != chips[selected] },
    )

private fun valueView(title: TextRef, value: TextRef, note: String? = null) =
    QuickMenuRowView(QuickMenuRowKind.VALUE, title, value, note = note)

/** Name des aktiven Layouts; ist er unbekannt, steht "Öffnen" da (die Zeile öffnet die Auswahl). */
private fun layoutNameText(name: String): TextRef = if (name.isBlank()) TextRef(R.string.game_qm_row_open) else TextRef.plain(name)

private fun stepView(title: TextRef, value: TextRef) = QuickMenuRowView(QuickMenuRowKind.STEP, title, value)

private fun toggleView(title: TextRef, enabled: Boolean = true) =
    QuickMenuRowView(QuickMenuRowKind.TOGGLE, title, enabled = enabled)

/** Die Zeile [id] mit Titel und Wert; null für IDs ohne Zeile im Menü. Die Schalterstellung kommt aus dem Menü, nicht von hier. */
internal fun quickMenuRowView(id: Int, v: QuickMenuValues): QuickMenuRowView? {
    wheelDirectionOf(id)?.let { direction ->
        val action = ClickWheel.actionFor(direction, v.wheelAssignment[direction])
        return QuickMenuRowView(
            QuickMenuRowKind.VALUE,
            TextRef(wheelRowRes(direction)),
            TextRef(action.labelRes),
            enabled = v.clickWheel,
        )
    }
    return when (id) {
        QuickMenuAction.INPUT_CONTROLS -> toggleView(TextRef(R.string.game_qm_row_touch_controls))
        QuickMenuAction.LAYOUT_CHOOSE -> valueView(TextRef(R.string.game_qm_row_layout), layoutNameText(v.layoutName), note = v.layoutSwitchNote)
        QuickMenuAction.LAYOUT_SAVE_AS -> valueView(TextRef(R.string.game_qm_row_layout_save_as), TextRef(R.string.game_qm_row_save))
        QuickMenuAction.EDIT_CONTROLS -> valueView(TextRef(R.string.game_qm_row_edit_controls), TextRef(R.string.game_qm_row_open))
        QuickMenuAction.EDIT_PHYSICAL_CONTROLLER -> valueView(TextRef(R.string.game_qm_row_physical), TextRef(R.string.game_qm_row_open))
        QuickMenuAction.RESET_LAYOUT -> valueView(TextRef(R.string.game_qm_row_layout_default), TextRef(R.string.game_qm_row_reset), note = v.layoutNote)
        QuickMenuAction.STICK_WALK -> stepView(TextRef(R.string.game_qm_row_walk), TextRef.plain(gainLabel(v.walkGain)))
        QuickMenuAction.STICK_CAMERA -> stepView(TextRef(R.string.game_qm_row_camera), TextRef.plain(gainLabel(v.cameraGain)))
        QuickMenuAction.TOUCHSCREEN_MODE -> toggleView(TextRef(R.string.game_qm_row_touchscreen))
        QuickMenuAction.TWO_FINGER_TAP -> toggleView(TextRef(R.string.game_qm_row_two_finger))
        QuickMenuAction.HOLD_TIME -> stepView(TextRef(R.string.game_qm_row_hold_time), TextRef(R.string.game_qm_ms, v.holdMs))
        QuickMenuAction.TAP_WINDOW -> stepView(TextRef(R.string.game_qm_row_tap_window), tapWindowLabel(v.tapWindowMs))
        QuickMenuAction.CONTEXT_MENU ->
            choiceView(TextRef(R.string.game_qm_row_context), CONTEXT_CHIPS, v.contextMode.ordinal, TextRef(v.contextMode.labelRes))
        QuickMenuAction.TAP_VIEW_ITEMS -> toggleView(TextRef(R.string.game_qm_row_bags_view))
        QuickMenuAction.CLICK_WHEEL -> toggleView(TextRef(R.string.game_qm_row_wheel))
        QuickMenuAction.SWIPE_SCROLL -> toggleView(TextRef(R.string.game_qm_row_scroll_lists))
        QuickMenuAction.SWIPE_SPEED -> QuickMenuRowView(
            QuickMenuRowKind.STEP, TextRef(R.string.game_qm_row_speed), TextRef.plain(SwipeScrollMath.speedLabel(v.swipeSpeed)), enabled = v.swipeScroll,
        )
        QuickMenuAction.TOUCH_HIDE_LOGIN -> toggleView(TextRef(R.string.game_qm_row_hide_login))
        QuickMenuAction.TOUCH_SHOW_NOW -> valueView(TextRef(R.string.game_qm_row_show_now), TextRef(R.string.game_qm_row_show))
        QuickMenuAction.TOUCH_HIDE_WINDOWS ->
            choiceView(TextRef(R.string.game_qm_row_hide_windows), WINDOW_HIDE_CHIPS, v.windowHide.ordinal, TextRef(v.windowHide.labelRes))
        QuickMenuAction.TOUCH_STYLE -> choiceView(TextRef(R.string.game_qm_row_appearance), STYLE_CHIPS, v.style.ordinal, TextRef(v.style.labelRes))
        QuickMenuAction.TOUCH_CONTRAST -> stepView(TextRef(R.string.game_qm_row_contrast), TextRef.plain(TouchStyleMath.contrastLabel(v.contrast)))
        QuickMenuAction.SHOOTER_MODE -> toggleView(TextRef(R.string.game_qm_row_shooter))
        QuickMenuAction.GYRO -> toggleView(TextRef(R.string.game_qm_row_gyro))
        QuickMenuAction.DISABLE_MOUSE -> toggleView(TextRef(R.string.game_qm_row_mouse_off))
        QuickMenuAction.RADIAL_MENU -> valueView(TextRef(R.string.game_qm_row_radial), TextRef(R.string.game_qm_row_open))
        QuickMenuAction.LOGIN_FULL ->
            if (v.hasLogin) valueView(TextRef(R.string.game_qm_row_login), TextRef(R.string.game_qm_row_type_in)) else valueView(TextRef(R.string.game_qm_row_login_save), TextRef(R.string.game_qm_row_save))
        QuickMenuAction.LOGIN_FILL -> valueView(TextRef(R.string.game_qm_row_fill), TextRef(R.string.game_qm_row_type_in))
        QuickMenuAction.LOGIN_PASSWORD -> valueView(TextRef(R.string.game_qm_row_password_only), TextRef(R.string.game_qm_row_type_in))
        QuickMenuAction.LOGIN_EMAIL -> valueView(TextRef(R.string.game_qm_row_email_only), TextRef(R.string.game_qm_row_type_in))
        QuickMenuAction.LOGIN_NOTE ->
            QuickMenuRowView(QuickMenuRowKind.NOTE, EMPTY_TEXT, TextRef.plain(v.loginNote.orEmpty()), enabled = false)
        QuickMenuAction.KEYBOARD -> valueView(TextRef(R.string.game_qm_row_keyboard), TextRef(R.string.game_qm_row_show))
        QuickMenuAction.COOLDOWNS -> toggleView(TextRef(R.string.game_qm_row_cooldowns))
        QuickMenuAction.COOLDOWN_GCD -> toggleView(TextRef(R.string.game_qm_row_gcd), enabled = v.cooldowns)
        QuickMenuAction.CHECK_ICONS -> valueView(TextRef(R.string.game_qm_row_check_icons), TextRef(R.string.game_qm_row_check))
        QuickMenuAction.ADDON_OPTIONS -> valueView(TextRef(R.string.game_qm_row_addon_settings), TextRef(R.string.game_qm_row_open), note = v.addonNote)
        QuickMenuAction.VOICE_LANGUAGE -> valueView(TextRef(R.string.game_qm_row_voice_language), TextRef.plain(v.voiceLabel))
        QuickMenuAction.REPORT, QuickMenuAction.REPORT_NOTE -> QuickMenuRowView(QuickMenuRowKind.ICON, EMPTY_TEXT)
        else -> null
    }
}

/**
 * Kurzinfo einer zugeklappten Gruppe, zum Beispiel "Laufen 1,25 · Kamera 1,0". Für die immer offene Gruppe Layout und unbekannte
 * Kennungen leer.
 */
internal fun quickMenuGroupSummary(groupId: String?, v: QuickMenuValues): TextRef = when (groupId) {
    QuickMenuGroups.STICKS ->
        TextRef(R.string.game_qm_sum_sticks, gainLabel(v.walkGain), gainLabel(v.cameraGain))
    QuickMenuGroups.TAP_HOLD ->
        TextRef(R.string.game_qm_sum_taphold, v.holdMs, TextRef(if (v.twoFingerRightClick) R.string.game_on_lc else R.string.game_off_lc))
    QuickMenuGroups.CLICK_WHEEL ->
        if (v.clickWheel) {
            TextRef(
                R.string.game_qm_sum_wheel_on,
                TextRef(ClickWheel.actionFor(WheelDirection.CENTER, v.wheelAssignment[WheelDirection.CENTER]).labelRes),
            )
        } else {
            TextRef(R.string.game_off_lc)
        }
    QuickMenuGroups.SWIPE ->
        if (v.swipeScroll) TextRef(R.string.game_qm_sum_swipe_on, SwipeScrollMath.speedLabel(v.swipeSpeed)) else TextRef(R.string.game_off_lc)
    QuickMenuGroups.ELEMENTS ->
        TextRef(
            R.string.game_qm_sum_elements,
            STYLE_CHIPS[v.style.ordinal.coerceIn(STYLE_CHIPS.indices)],
            TouchStyleMath.contrastLabel(v.contrast),
        )
    QuickMenuGroups.MORE -> TextRef(R.string.game_qm_sum_more)
    QuickMenuGroups.MODES -> buildList {
        if (v.shooterMode) add(TextRef(R.string.game_qm_sum_shooter))
        if (v.gyro) add(TextRef(R.string.game_qm_sum_gyro))
        if (v.mouseOff) add(TextRef(R.string.game_qm_sum_mouse_off))
    }.reduceOrNull { first, second -> TextRef(R.string.game_qm_join, first, second) } ?: TextRef(R.string.game_qm_sum_all_off)
    else -> EMPTY_TEXT
}

/**
 * Schrittweise Änderung der "- Wert +"-Zeilen. Anders als das Reihum-Schalten im Launcher bleibt der Wert an den Enden stehen
 * (das Minus am kleinsten Wert macht nichts), so wie man es von einem Regler erwartet.
 */
internal object QuickMenuSteps {
    /** Der Wert in [steps], der [current] am nächsten liegt, um [delta] Stufen verschoben und am Rand begrenzt. */
    fun stepList(steps: List<Float>, current: Float, delta: Int): Float {
        if (steps.isEmpty()) return current
        val nearest = steps.indices.minByOrNull { abs(steps[it] - current) } ?: 0
        return steps[(nearest + delta).coerceIn(0, steps.lastIndex)]
    }

    fun gain(current: Float, delta: Int): Float = stepList(STICK_GAIN_STEPS, current, delta)

    fun contrast(current: Float, delta: Int): Float = stepList(TouchStyleMath.CONTRAST_STEPS, current, delta)

    fun swipeSpeed(current: Float, delta: Int): Float = stepList(SwipeScrollMath.SPEEDS.toList(), current, delta)

    fun holdMs(current: Int, delta: Int): Int =
        (current + delta * TouchOptions.HOLD_STEP_MS).coerceIn(TouchOptions.MIN_HOLD_MS, TouchOptions.MAX_HOLD_MS)

    fun tapWindowMs(current: Int, delta: Int): Int =
        (current + delta * TouchOptions.TAP_WINDOW_STEP_MS).coerceIn(0, TouchOptions.MAX_TAP_WINDOW_MS)
}
