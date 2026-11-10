package app.gamenative.ui.screen.xserver.editor.sheet

import app.gamenative.ui.screen.wow.STICK_GAIN_STEPS
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlElement
import kotlin.math.abs
import kotlin.math.roundToInt
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.EditorText

/** Which global stick sensitivity acts on one element: walking (left stick), the camera (right stick, pad trackpad) or none. */
internal enum class StickGainScope { WALK, CAMERA, NONE }

/**
 * The sensitivity that applies to a stick or trackpad with the binding [combos] (its four slots), the same decision as
 * [ControlElement] makes while the finger moves: the first slot with a gamepad axis (and no sequence) decides. A stick uses the
 * camera value for a right-stick axis and the walk value otherwise; a trackpad always the camera value. A trackpad slot with a mouse
 * move works as a mouse and ignores the sensitivity. WASD, arrow keys, mouse and every other element: [StickGainScope.NONE].
 */
internal fun stickGainScope(type: ControlElement.Type, combos: List<BindingCombo>): StickGainScope {
    if (type != ControlElement.Type.STICK && type != ControlElement.Type.TRACKPAD) return StickGainScope.NONE
    for (combo in combos) {
        if (combo.isSequence) continue
        if (type == ControlElement.Type.TRACKPAD && combo.bindings.any { it.isMouseMove }) continue
        val axis = combo.bindings.firstOrNull { it.isGamepadAxis } ?: continue
        return if (type == ControlElement.Type.TRACKPAD || ControlElement.isRightThumb(axis)) StickGainScope.CAMERA else StickGainScope.WALK
    }
    return StickGainScope.NONE
}

/** Scope whose value the greyed-out slider shows when nothing applies: the one the element would use with a pad binding. */
internal fun StickGainScope.orDefaultFor(type: ControlElement.Type): StickGainScope = when {
    this != StickGainScope.NONE -> this
    type == ControlElement.Type.TRACKPAD -> StickGainScope.CAMERA
    else -> StickGainScope.WALK
}

/** Index of the step of [STICK_GAIN_STEPS] that is closest to [gain]. */
internal fun gainStepIndex(gain: Float): Int =
    STICK_GAIN_STEPS.indices.minByOrNull { abs(STICK_GAIN_STEPS[it] - gain) } ?: 0

/** The step for a slider position (rounded, clamped to the offered steps). */
internal fun gainForSliderValue(value: Float): Float =
    STICK_GAIN_STEPS[value.roundToInt().coerceIn(0, STICK_GAIN_STEPS.size - 1)]

internal fun stickGainTitle(scope: StickGainScope): EditorText = when (scope) {
    StickGainScope.WALK -> EditorText(R.string.editor_gain_title_walk)
    StickGainScope.CAMERA -> EditorText(R.string.editor_gain_title_camera)
    StickGainScope.NONE -> EditorText(R.string.editor_sensitivity)
}

internal fun stickGainHint(scope: StickGainScope): EditorText = when (scope) {
    StickGainScope.WALK -> EditorText(R.string.editor_gain_hint_walk)
    StickGainScope.CAMERA -> EditorText(R.string.editor_gain_hint_camera)
    StickGainScope.NONE -> EditorText(R.string.editor_gain_hint_none)
}
