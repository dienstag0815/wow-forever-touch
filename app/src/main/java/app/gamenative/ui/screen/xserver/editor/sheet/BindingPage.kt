package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.dialog.wowActionSummary
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.StickCenter
import kotlin.math.roundToInt
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.EditorText
import app.gamenative.ui.screen.xserver.editor.text

/** Page "Belegung": the binding slots of the element (a tap opens the key picker) and, for pads and sticks, quick presets. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BindingPage(state: ElementSheetState, element: ControlElement, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (element.type == ControlElement.Type.D_PAD || element.type == ControlElement.Type.STICK) {
            SheetSection(stringResource(R.string.editor_quick_pick))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (preset in DirectionPreset.values()) {
                    AssistChip(
                        onClick = { state.change { preset.applyTo(element) } },
                        label = { Text(stringResource(preset.label)) },
                        modifier = Modifier.heightIn(min = SheetTarget),
                    )
                }
            }
        }

        SheetSection(stringResource(R.string.editor_slots_section))
        for (index in 0 until SheetPages.slotCount(element.type)) {
            val combo = element.getBindingComboAt(index)
            val first = index == 0 && element.type == ControlElement.Type.BUTTON
            val value: EditorText = when {
                first && element.chatAction != null -> chatActionSummary(element) ?: EditorText(R.string.editor_not_assigned)
                first -> if (element.wowAction == null && element.chatCommand == null && combo.isEmpty) {
                    EditorText(R.string.editor_not_assigned)
                } else {
                    EditorText.raw(wowActionSummary(element.wowAction, combo.toString(), element.chatCommand))
                }
                combo.isEmpty -> EditorText(R.string.editor_not_assigned)
                else -> EditorText.raw(combo.toString())
            }
            SheetNavRow(
                title = SheetPages.slotLabel(element.type, index).text(),
                value = value.text(),
                onClick = { state.open(SheetRoute.picker(PickTarget.Slot(index))) },
            )
        }

        if (element.type == ControlElement.Type.STICK) {
            SheetSection(stringResource(R.string.editor_center))
            val center = element.getCenterItem()
            SheetNavRow(
                title = stringResource(R.string.editor_press_center),
                value = if (center.wowAction == null && center.chatCommand == null && center.bindingCombo.isEmpty) {
                    stringResource(R.string.editor_none)
                } else {
                    wowActionSummary(center.wowAction, center.bindingCombo.toString(), center.chatCommand)
                },
                onClick = { state.open(SheetRoute.picker(PickTarget.Center)) },
            )
            SheetHint(stringResource(R.string.editor_center_hint))
            if (element.hasCenterAction()) {
                SheetSection(stringResource(R.string.editor_center_sensitivity))
                SheetSlider(
                    title = stringResource(R.string.editor_tap_limit),
                    valueText = "${element.centerTapDp.roundToInt()} dp",
                    value = element.centerTapDp,
                    range = StickCenter.MIN_TAP_DP..StickCenter.MAX_TAP_DP,
                    onChange = { v -> state.live { element.setCenterTapDp(v.roundToInt().toFloat()) } },
                    onFinished = { state.flush() },
                    resetLabel = stringResource(R.string.editor_default),
                    onReset = { state.change { element.setCenterTapDp(StickCenter.DEFAULT_TAP_DP) } },
                )
                SheetSlider(
                    title = stringResource(R.string.editor_tap_time),
                    valueText = "${element.centerTapMillis} ms",
                    value = element.centerTapMillis.toFloat(),
                    range = StickCenter.MIN_TAP_MILLIS.toFloat()..StickCenter.MAX_TAP_MILLIS.toFloat(),
                    onChange = { v -> state.live { element.setCenterTapMillis((v / 10f).roundToInt() * 10L) } },
                    onFinished = { state.flush() },
                    resetLabel = stringResource(R.string.editor_default),
                    onReset = { state.change { element.setCenterTapMillis(StickCenter.DEFAULT_TAP_MILLIS) } },
                )
                SheetHint(stringResource(R.string.editor_center_tap_hint))
            }
        }

        when (element.type) {
            ControlElement.Type.BUTTON -> SheetHint(stringResource(R.string.editor_hint_primary_secondary))
            ControlElement.Type.SCROLL_WHEEL -> SheetHint(stringResource(R.string.editor_hint_scroll_slots))
            else -> {}
        }
    }
}

/** Direction presets for pads and sticks (up, right, down, left). */
internal enum class DirectionPreset(@StringRes val label: Int, private val keys: List<Binding>) {
    WASD(R.string.editor_wasd, listOf(Binding.KEY_W, Binding.KEY_D, Binding.KEY_S, Binding.KEY_A)),
    ARROWS(R.string.editor_arrow_keys, listOf(Binding.KEY_UP, Binding.KEY_RIGHT, Binding.KEY_DOWN, Binding.KEY_LEFT)),
    MOUSE(
        R.string.editor_mouse,
        listOf(Binding.MOUSE_MOVE_UP, Binding.MOUSE_MOVE_RIGHT, Binding.MOUSE_MOVE_DOWN, Binding.MOUSE_MOVE_LEFT),
    ),
    DPAD(
        R.string.editor_kind_d_pad,
        listOf(Binding.GAMEPAD_DPAD_UP, Binding.GAMEPAD_DPAD_RIGHT, Binding.GAMEPAD_DPAD_DOWN, Binding.GAMEPAD_DPAD_LEFT),
    ),
    LEFT_STICK(
        R.string.editor_left_stick,
        listOf(
            Binding.GAMEPAD_LEFT_THUMB_UP,
            Binding.GAMEPAD_LEFT_THUMB_RIGHT,
            Binding.GAMEPAD_LEFT_THUMB_DOWN,
            Binding.GAMEPAD_LEFT_THUMB_LEFT,
        ),
    ),
    RIGHT_STICK(
        R.string.editor_right_stick,
        listOf(
            Binding.GAMEPAD_RIGHT_THUMB_UP,
            Binding.GAMEPAD_RIGHT_THUMB_RIGHT,
            Binding.GAMEPAD_RIGHT_THUMB_DOWN,
            Binding.GAMEPAD_RIGHT_THUMB_LEFT,
        ),
    );

    fun applyTo(element: ControlElement) {
        keys.forEachIndexed { index, binding -> element.setBindingAt(index, binding) }
    }
}
