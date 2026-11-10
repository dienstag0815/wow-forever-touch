package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.screen.wow.STICK_GAIN_STEPS
import app.gamenative.ui.screen.wow.StickSensitivity
import app.gamenative.ui.screen.wow.WindowHideRule
import app.gamenative.ui.screen.wow.WindowVisibility
import app.gamenative.ui.screen.wow.chatbar.ChatAnchor
import app.gamenative.ui.screen.wow.chatbar.ChatShow
import app.gamenative.ui.screen.wow.gainLabel
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.ScrollWheelMath
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.text

private val MOVEMENT_TYPES = listOf("wasd", "arrow_keys", "gamepad_left_stick")
private val MOVEMENT_LABELS = listOf(R.string.editor_wasd, R.string.editor_arrow_keys, R.string.editor_gamepad_left_stick)
private val LOOK_TYPES = listOf("mouse", "gamepad_right_stick")
private val LOOK_LABELS = listOf(R.string.editor_mouse, R.string.editor_gamepad_right_stick)
private val RANGES = listOf(
    ControlElement.Range.FROM_A_TO_Z,
    ControlElement.Range.FROM_0_TO_9,
    ControlElement.Range.FROM_F1_TO_F12,
    ControlElement.Range.CUSTOM,
)
private val RANGE_LABELS = listOf("A – Z", "1 – 0", "F1 – F12")
private val SCROLL_SPEEDS = listOf(0.5f, 1f, 2f, 4f)
private val SCROLL_SPEED_LABELS = listOf(
    R.string.editor_speed_slow,
    R.string.editor_speed_normal,
    R.string.editor_speed_fast,
    R.string.editor_speed_very_fast,
)

/**
 * Page "Verhalten": what the element does beyond its keys (toggle, key strip, scroll wheel, dynamic sticks), its exact position,
 * and, folded away at the end because it is rarely needed, the change of the element type.
 */
@Composable
internal fun AdvancedPage(state: ElementSheetState, element: ControlElement, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val type = element.type
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (type == ControlElement.Type.BUTTON) {
            SheetSection(stringResource(R.string.editor_page_advanced))
            SheetSwitchRow(
                title = stringResource(R.string.editor_toggle),
                subtitle = stringResource(R.string.editor_toggle_hint),
                checked = element.isToggleSwitch,
                onChange = { on -> state.change { element.setToggleSwitch(on) } },
            )
            SheetSwitchRow(
                title = stringResource(R.string.editor_look_through),
                subtitle = stringResource(R.string.editor_look_through_hint),
                checked = element.lookThroughSetting == true,
                onChange = { on -> state.change { element.lookThroughSetting = on } },
            )
        }

        if (type == ControlElement.Type.RANGE_BUTTON) {
            RangeSettings(state, element)
        }
        if (type == ControlElement.Type.SCROLL_WHEEL) {
            ScrollWheelSettings(state, element)
        }
        if (type == ControlElement.Type.SHOOTER_MODE) {
            ShooterSettings(state, element)
        }

        if (type == ControlElement.Type.STICK || type == ControlElement.Type.TRACKPAD) {
            SensitivitySettings(element)
        }

        VisibilitySettings(state, element)

        SheetSection(stringResource(R.string.editor_position))
        PositionControls(state, element)

        SheetGroup(title = stringResource(R.string.editor_change_type), summary = typeName(type).text()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val types = ControlElement.Type.values().toList()
                SheetChips(
                    options = types.map { typeName(it).text() },
                    selectedIndex = types.indexOf(type).coerceAtLeast(0),
                    onSelect = { index -> state.change { changeType(element, types[index]) } },
                )
                SheetHint(stringResource(R.string.editor_change_type_hint))
            }
        }
    }
}

/**
 * Group "Empfindlichkeit" of a stick or trackpad: the global sensitivity step that applies to THIS element (camera for a right-stick
 * or trackpad pad binding, walking otherwise), changed at once for all sticks of that kind. It is a setting of the app, not of the
 * element, so it is no undo step of the editor. Greyed out with a hint when the binding is keys or mouse, where it does not act.
 */
@Composable
private fun SensitivitySettings(element: ControlElement) {
    val context = LocalContext.current
    val type = element.type
    val combos = (0 until SheetPages.slotCount(type)).map { element.getBindingComboAt(it) }
    val actual = stickGainScope(type, combos)
    val shown = actual.orDefaultFor(type)
    // The value is kept here: the editor's redraw counter does not tick for an app setting.
    var gain by remember(shown) {
        mutableStateOf(if (shown == StickGainScope.CAMERA) StickSensitivity.camera(context) else StickSensitivity.walk(context))
    }
    SheetSection(stringResource(R.string.editor_sensitivity))
    SheetSlider(
        title = stickGainTitle(actual).text(),
        valueText = gainLabel(gain) + "x",
        value = gainStepIndex(gain).toFloat(),
        range = 0f..(STICK_GAIN_STEPS.size - 1).toFloat(),
        steps = (STICK_GAIN_STEPS.size - 2).coerceAtLeast(0),
        enabled = actual != StickGainScope.NONE,
        onChange = { v ->
            val next = gainForSliderValue(v)
            if (next != gain) {
                gain = next
                if (shown == StickGainScope.CAMERA) StickSensitivity.saveCamera(context, next) else StickSensitivity.saveWalk(context, next)
            }
        },
        onFinished = {},
    )
    SheetHint(stickGainHint(actual).text())
}

/**
 * Section "Sichtbarkeit": what happens to the element while big game windows are open (every element) and, for buttons, when it shows
 * with the game's chat and whether it follows the chat window.
 */
@Composable
private fun VisibilitySettings(state: ElementSheetState, element: ControlElement) {
    val rules = WindowVisibility.entries
    SheetSection(stringResource(R.string.editor_visibility))
    SheetChips(
        options = rules.map { it.label },
        selectedIndex = rules.indexOf(WindowVisibility.fromKey(element.windowRule)).coerceAtLeast(0),
        onSelect = { index -> state.change { element.windowRule = rules[index].key } },
    )
    SheetHint(stringResource(R.string.editor_visibility_hint))
    val keepers = state.view.profile?.elements?.count {
        WindowHideRule.alwaysKept(it.type, it.getBindingAt(0)) && it.windowRule != WindowVisibility.HIDE.key
    } ?: 0
    val essential = WindowHideRule.alwaysKept(element.type, element.getBindingAt(0))
    if (WindowHideRule.lockedOut(element.windowRule, essential, keepers)) {
        SheetHint(stringResource(R.string.editor_lockout_hint))
    }

    if (element.type != ControlElement.Type.BUTTON) return

    val shows = ChatShow.entries
    SheetSection(stringResource(R.string.editor_chat))
    SheetChips(
        options = shows.map { it.label },
        selectedIndex = shows.indexOf(ChatShow.fromKey(element.chatShow)).coerceAtLeast(0),
        onSelect = { index -> state.change { element.chatShow = shows[index].key } },
    )
    SheetHint(stringResource(R.string.editor_chat_show_hint))
    SheetSwitchRow(
        title = stringResource(R.string.editor_chat_anchor),
        subtitle = stringResource(R.string.editor_chat_anchor_hint),
        checked = element.chatAnchor != null,
        onChange = { on -> state.change { element.chatAnchor = if (on) ChatAnchor.TOP_RIGHT.key else null } },
    )
    if (element.chatAnchor != null) {
        val corners = ChatAnchor.entries
        SheetChips(
            options = corners.map { it.label },
            selectedIndex = corners.indexOf(ChatAnchor.fromKey(element.chatAnchor)).coerceAtLeast(0),
            onSelect = { index -> state.change { element.chatAnchor = corners[index].key } },
        )
        SheetHint(
            when {
                state.view.chatRect == null -> stringResource(R.string.editor_anchor_not_reported)
                element.isChatAnchorKnown -> stringResource(R.string.editor_anchor_measured)
                else -> stringResource(R.string.editor_anchor_pending)
            },
        )
    }
}

/** Exact position of the element's middle: number fields (applied while typing) and arrows that move by one grid step. */
@Composable
private fun PositionControls(state: ElementSheetState, element: ControlElement) {
    val view = state.view
    val step = view.snappingSize
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PositionRow(
            title = stringResource(R.string.editor_horizontal_x),
            value = element.x.toInt(),
            max = view.maxWidth,
            step = step,
            onTyped = { v -> state.live { element.setX(v) } },
            onStep = { v -> state.change { element.setX(v) } },
        )
        PositionRow(
            title = stringResource(R.string.editor_vertical_y),
            value = element.y.toInt(),
            max = view.maxHeight,
            step = step,
            onTyped = { v -> state.live { element.setY(v) } },
            onStep = { v -> state.change { element.setY(v) } },
        )
        SheetHint(stringResource(R.string.editor_position_hint, step))
    }
}

@Composable
private fun PositionRow(title: String, value: Int, max: Int, step: Int, onTyped: (Int) -> Unit, onStep: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        NoExtractOutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed
                ElementPosition.parse(typed)?.let { onTyped(ElementPosition.clamp(it, max)) }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(title) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedButton(
            onClick = { onStep(ElementPosition.nudge(value, -1, step, max)) },
            modifier = Modifier.heightIn(min = SheetTarget),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) { Text("−") }
        OutlinedButton(
            onClick = { onStep(ElementPosition.nudge(value, 1, step, max)) },
            modifier = Modifier.heightIn(min = SheetTarget),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) { Text("+") }
    }
}

/** Switches the element to [newType] with the follow-up settings the old element dialog applied. */
internal fun changeType(element: ControlElement, newType: ControlElement.Type) {
    if (element.type == newType) return
    val shape = when (newType) {
        ControlElement.Type.STICK, ControlElement.Type.SHOOTER_MODE -> ControlElement.Shape.CIRCLE
        ControlElement.Type.TRACKPAD, ControlElement.Type.SCROLL_WHEEL, ControlElement.Type.RANGE_BUTTON -> ControlElement.Shape.ROUND_RECT
        else -> element.shape
    }
    element.setShape(shape)
    element.setTypeWithoutReset(newType)
    if (newType == ControlElement.Type.SCROLL_WHEEL) {
        // Tap without swiping = middle click; slot 2 (held while scrolling) starts empty.
        element.setBindingComboAt(0, BindingCombo.of(Binding.MOUSE_MIDDLE_BUTTON))
        element.setBindingComboAt(1, BindingCombo.none())
    }
    if (newType != ControlElement.Type.BUTTON) {
        element.wowAction = null
        element.chatCommand = null
        element.chatAction = null
        element.chatShow = null
        element.chatAnchor = null
    }
    if (newType == ControlElement.Type.RANGE_BUTTON) element.setRange(element.range)
}

@Composable
private fun RangeSettings(state: ElementSheetState, element: ControlElement) {
    SheetSection(stringResource(R.string.editor_kind_key_strip))
    SheetChips(
        options = RANGE_LABELS + stringResource(R.string.editor_range_custom),
        selectedIndex = RANGES.indexOf(element.range).coerceAtLeast(0),
        onSelect = { index ->
            val chosen = RANGES[index]
            val wasCustom = element.isCustomRange
            state.change {
                if (chosen == ControlElement.Range.CUSTOM) {
                    // The free list starts with its own keys (Enter, Space, ...), visibly not the template's; each entry is changeable.
                    element.convertToCustomRange()
                } else {
                    element.setRange(chosen)
                    // Clamp visible segments to the new range's max
                    val newMax = chosen.max.toInt()
                    if (element.bindingCount > newMax) element.setBindingCount(newMax)
                }
            }
            // "Eigene" is chosen to edit the entries: go straight to them.
            if (chosen == ControlElement.Range.CUSTOM && !wasCustom) state.open(SheetRoute.RANGE_ITEMS)
        },
    )
    if (element.isCustomRange) {
        SheetNavRow(
            title = stringResource(R.string.editor_edit_entries),
            value = stringResource(R.string.editor_summary_entries, element.rangeItemCount),
            onClick = { state.open(SheetRoute.RANGE_ITEMS) },
        )
    }
    SheetSection(stringResource(R.string.editor_direction))
    SheetChips(
        options = listOf(stringResource(R.string.editor_horizontal), stringResource(R.string.editor_vertical)),
        selectedIndex = element.orientation.toInt().coerceIn(0, 1),
        onSelect = { index -> state.change { element.setOrientation(index.toByte()) } },
    )
    val maxSegments = element.rangeCount
    if (maxSegments > 1) {
        SheetSlider(
            title = stringResource(R.string.editor_visible_keys),
            valueText = "${element.bindingCount}",
            value = element.bindingCount.toFloat().coerceIn(1f, maxSegments.toFloat()),
            range = 1f..maxSegments.toFloat(),
            steps = (maxSegments - 2).coerceAtLeast(0),
            onChange = { v -> state.live { element.setBindingCount(v.roundToInt().coerceIn(1, maxSegments)) } },
            onFinished = { state.flush() },
        )
    }
    SheetSwitchRow(
        title = stringResource(R.string.editor_lock_scroll),
        subtitle = stringResource(R.string.editor_lock_scroll_hint),
        checked = element.isScrollLocked,
        onChange = { on -> state.change { element.isScrollLocked = on } },
    )
    if (element.isCustomRange) {
        SheetHint(stringResource(R.string.editor_custom_range_hint))
    } else {
        SheetHint(stringResource(R.string.editor_range_hint))
    }
}

@Composable
private fun ScrollWheelSettings(state: ElementSheetState, element: ControlElement) {
    SheetSection(stringResource(R.string.editor_kind_scroll_wheel))
    SheetChips(
        options = listOf(stringResource(R.string.editor_scroll_vertical), stringResource(R.string.editor_scroll_horizontal)),
        selectedIndex = element.orientation.toInt().coerceIn(0, 1),
        onSelect = { index -> state.change { element.setOrientation(index.toByte()) } },
    )
    // Speed instead of the tick distance: right = faster (the distance behind it is the other way round), logarithmic so the
    // slow and the fast end both have room, with presets and a reset button.
    val speed = ScrollWheelMath.speedOf(element.scrollStep)
    SheetSlider(
        title = stringResource(R.string.editor_speed),
        valueText = String.format(Locale.US, "%.2fx", speed),
        value = ScrollWheelMath.positionForSpeed(speed),
        range = 0f..1f,
        onChange = { p -> state.live { element.setScrollStep(ScrollWheelMath.stepForSpeed(ScrollWheelMath.speedForPosition(p))) } },
        onFinished = { state.flush() },
        resetLabel = stringResource(R.string.editor_default),
        onReset = { state.change { element.setScrollStep(ScrollWheelMath.DEFAULT_STEP) } },
    )
    SheetChips(
        options = SCROLL_SPEED_LABELS.map { stringResource(it) },
        selectedIndex = SCROLL_SPEEDS.indexOfFirst { kotlin.math.abs(it - speed) < 0.03f },
        onSelect = { index -> state.change { element.setScrollStep(ScrollWheelMath.stepForSpeed(SCROLL_SPEEDS[index])) } },
    )
    SheetHint(stringResource(R.string.editor_scroll_step_hint))
    SheetSwitchRow(
        title = stringResource(R.string.editor_reverse_direction),
        subtitle = stringResource(R.string.editor_reverse_hint),
        checked = element.isScrollReverse,
        onChange = { on -> state.change { element.setScrollReverse(on) } },
    )
    SheetSwitchRow(
        title = stringResource(R.string.editor_inertia),
        subtitle = stringResource(R.string.editor_inertia_hint),
        checked = element.isScrollInertia,
        onChange = { on -> state.change { element.setScrollInertia(on) } },
    )
}

@Composable
private fun ShooterSettings(state: ElementSheetState, element: ControlElement) {
    SheetSection(stringResource(R.string.editor_movement))
    SheetChips(
        options = MOVEMENT_LABELS.map { stringResource(it) },
        selectedIndex = MOVEMENT_TYPES.indexOf(element.shooterMovementType).coerceAtLeast(0),
        onSelect = { index -> state.change { element.shooterMovementType = MOVEMENT_TYPES[index] } },
    )
    SheetSection(stringResource(R.string.editor_look))
    SheetChips(
        options = LOOK_LABELS.map { stringResource(it) },
        selectedIndex = LOOK_TYPES.indexOf(element.shooterLookType).coerceAtLeast(0),
        onSelect = { index -> state.change { element.shooterLookType = LOOK_TYPES[index] } },
    )
    SheetSlider(
        title = stringResource(R.string.editor_look_sensitivity),
        valueText = String.format(Locale.US, "%.1fx", element.shooterLookSensitivity),
        value = element.shooterLookSensitivity,
        range = 0.1f..10.0f,
        onChange = { v -> state.live { element.shooterLookSensitivity = v } },
        onFinished = { state.flush() },
    )
    SheetSlider(
        title = stringResource(R.string.editor_stick_size),
        valueText = String.format(Locale.US, "%.1fx", element.shooterJoystickSize),
        value = element.shooterJoystickSize,
        range = 0.5f..3.0f,
        onChange = { v -> state.live { element.shooterJoystickSize = v } },
        onFinished = { state.flush() },
    )
}
