package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.component.dialog.ControlAppearance
import app.gamenative.ui.component.dialog.ControlAppearancePreview
import app.gamenative.ui.component.dialog.closestStrokeWidthIndex
import app.gamenative.ui.component.dialog.controlStrokeWidthScales
import app.gamenative.ui.component.dialog.parseRgbHex
import app.gamenative.ui.component.dialog.rgbToHex
import com.winlator.inputcontrols.ControlElement
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.wowaction.StandardNames
import app.gamenative.ui.screen.xserver.editor.EditorText
import app.gamenative.ui.screen.xserver.editor.text

private val COLOR_PRESETS = listOf(
    0xffffff, 0xf44336, 0xff9800, 0xffeb3b,
    0x4caf50, 0x03a9f4, 0x3f51b5, 0x9c27b0,
)

/**
 * Page "Aussehen": form, width/height/rotation, colors as circles, line width, the game picture symbol, and copying the look
 * of another element. Everything shows on the element in the game picture while it is changed.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun AppearancePage(state: ElementSheetState, element: ControlElement, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val view = state.view
    val type = element.type
    val isButton = type == ControlElement.Type.BUTTON
    var copyOpen by remember(element) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ControlAppearancePreview(
            type = type,
            text = element.text?.ifEmpty { null }?.let { StandardNames.displayLabel(it) } ?: element.getBindingAt(0).toString().take(7).ifEmpty { "A" },
            shape = element.shape,
            orientation = element.orientation.toInt(),
            segmentCount = element.bindingCount,
            color = element.buttonColor,
            activeColor = element.buttonActiveColor,
            opacity = element.getEffectiveButtonOpacity(view.overlayOpacity),
            strokeScale = element.buttonStrokeScale,
        )

        if (isButton) {
            SheetSection(stringResource(R.string.editor_form))
            val shapes = ControlElement.Shape.values().toList()
            SheetChips(
                options = shapes.map { shapeName(it).text() },
                selectedIndex = shapes.indexOf(element.shape).coerceAtLeast(0),
                onSelect = { state.change { element.setShape(shapes[it]) } },
            )
        }

        if (isButton || type == ControlElement.Type.SCROLL_WHEEL) {
            SheetSection(stringResource(R.string.editor_size_and_shape))
            SheetSlider(
                title = stringResource(R.string.editor_width),
                valueText = String.format(Locale.US, "%.2fx", element.widthScale),
                value = element.widthScale,
                range = 0.3f..3.0f,
                onChange = { v -> state.live { element.setWidthScale((v * 20f).roundToInt() / 20f) } },
                onFinished = { state.flush() },
            )
            SheetSlider(
                title = stringResource(R.string.editor_height),
                valueText = String.format(Locale.US, "%.2fx", element.heightScale),
                value = element.heightScale,
                range = 0.3f..3.0f,
                onChange = { v -> state.live { element.setHeightScale((v * 20f).roundToInt() / 20f) } },
                onFinished = { state.flush() },
            )
            if (isButton) {
                SheetSection(stringResource(R.string.editor_rotation))
                SheetChips(
                    options = listOf("0°", "90°", "180°", "270°"),
                    selectedIndex = (element.rotation / 90).coerceIn(0, 3),
                    onSelect = { state.change { element.setRotation(it * 90) } },
                )
                SheetHint(stringResource(R.string.editor_rotation_hint))
                if (element.shape == ControlElement.Shape.ROUND_RECT) {
                    SheetSlider(
                        title = stringResource(R.string.editor_corner_radius),
                        valueText = "${(element.cornerRadius * 100).roundToInt()} %",
                        value = element.cornerRadius,
                        range = 0f..1f,
                        onChange = { v -> state.live { element.setCornerRadius((v * 20f).roundToInt() / 20f) } },
                        onFinished = { state.flush() },
                    )
                }
                if (element.shape == ControlElement.Shape.ARC) {
                    SheetSlider(
                        title = stringResource(R.string.editor_arc_angle),
                        valueText = "${element.arcSweep}°",
                        value = element.arcSweep.toFloat(),
                        range = ControlElement.MIN_ARC_SWEEP.toFloat()..ControlElement.MAX_ARC_SWEEP.toFloat(),
                        onChange = { v -> state.live { element.setArcSweep((v / 5f).roundToInt() * 5) } },
                        onFinished = { state.flush() },
                    )
                }
            }
            AssistChip(
                onClick = {
                    state.change {
                        element.setWidthScale(1.0f)
                        element.setHeightScale(1.0f)
                        element.setRotation(0)
                        element.setCornerRadius(ControlElement.DEFAULT_CORNER_RADIUS)
                        element.setArcSweep(ControlElement.DEFAULT_ARC_SWEEP)
                    }
                },
                label = { Text(stringResource(R.string.editor_reset_shape)) },
                modifier = Modifier.heightIn(min = SheetTarget),
            )
        }

        SheetSection(stringResource(R.string.editor_color))
        ColorCircles(
            colors = COLOR_PRESETS,
            selected = element.buttonColor,
            onPick = { rgb -> state.change { element.setButtonColor(rgb) } },
        )
        HexField(
            label = stringResource(R.string.editor_custom_color),
            current = element.buttonColor,
            onColor = { rgb -> state.change { element.setButtonColor(rgb) } },
        )

        SheetSection(stringResource(R.string.editor_color_on_touch))
        ColorCircles(
            colors = COLOR_PRESETS,
            selected = if (element.hasCustomButtonActiveColor()) element.buttonActiveColor else null,
            onPick = { rgb -> state.change { element.setButtonActiveColor(rgb, true) } },
        )
        HexField(
            label = stringResource(R.string.editor_custom_color),
            current = element.buttonActiveColor,
            onColor = { rgb -> state.change { element.setButtonActiveColor(rgb, true) } },
        )
        AssistChip(
            onClick = {
                state.change {
                    element.setButtonActiveColor(ControlElement.DEFAULT_BUTTON_ACTIVE_COLOR, false)
                }
            },
            label = {
                Text(
                    if (element.hasCustomButtonActiveColor()) stringResource(R.string.editor_automatic)
                    else stringResource(R.string.editor_automatic_chosen),
                )
            },
            modifier = Modifier.heightIn(min = SheetTarget),
        )

        SheetSection(stringResource(R.string.editor_line_width))
        SheetChips(
            options = listOf(
                stringResource(R.string.editor_stroke_thin),
                stringResource(R.string.editor_default),
                stringResource(R.string.editor_stroke_thick),
                stringResource(R.string.editor_stroke_extra_thick),
            ),
            selectedIndex = closestStrokeWidthIndex(element.buttonStrokeScale),
            onSelect = { state.change { element.setButtonStrokeScale(controlStrokeWidthScales[it]) } },
        )

        SheetSection(stringResource(R.string.editor_copy_look))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { copyOpen = !copyOpen },
                label = {
                    Text(
                        if (copyOpen) stringResource(R.string.editor_close_list)
                        else stringResource(R.string.editor_from_other_element),
                    )
                },
                modifier = Modifier.heightIn(min = SheetTarget),
            )
            AssistChip(
                onClick = {
                    state.change {
                        val look = ControlAppearance.capture(element)
                        view.profile?.elements?.forEach { look.applyTo(it) }
                    }
                },
                label = { Text(stringResource(R.string.editor_apply_to_all)) },
                modifier = Modifier.heightIn(min = SheetTarget),
            )
            AssistChip(
                onClick = {
                    state.change {
                        ControlAppearance(
                            scale = 1.0f,
                            color = ControlElement.DEFAULT_BUTTON_COLOR,
                            activeColor = ControlElement.DEFAULT_BUTTON_ACTIVE_COLOR,
                            activeColorCustom = false,
                            opacity = ControlElement.INHERIT_BUTTON_OPACITY,
                            strokeScale = ControlElement.DEFAULT_BUTTON_STROKE_SCALE,
                            lookThrough = ControlElement.DEFAULT_LOOK_THROUGH,
                            legacyShooterLookThrough = true,
                        ).applyTo(element)
                    }
                },
                label = { Text(stringResource(R.string.editor_reset)) },
                modifier = Modifier.heightIn(min = SheetTarget),
            )
        }
        SheetHint(stringResource(R.string.editor_copy_hint))
        if (copyOpen) {
            val others = view.profile?.elements?.filter { it !== element }.orEmpty()
            if (others.isEmpty()) {
                SheetHint(stringResource(R.string.editor_no_other_elements))
            }
            for (other in others) {
                SheetNavRow(
                    title = "${typeName(other.type).text()} – ${labelOf(other).text()}",
                    value = String.format(Locale.US, "%.2fx · %s", other.scale, rgbToHex(other.buttonColor)),
                    onClick = {
                        copyOpen = false
                        state.change { ControlAppearance.capture(other).applyTo(element) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun labelOf(element: ControlElement): EditorText {
    if (!element.text.isNullOrEmpty()) return EditorText.raw(StandardNames.displayLabel(element.text))
    val combo = element.getBindingComboAt(0)
    return if (combo.isEmpty) EditorText(R.string.editor_no_binding) else EditorText.raw(combo.toString().take(15))
}

/** Text field for a color as #RRGGBB; a valid entry is applied at once. */
@Composable
private fun HexField(label: String, current: Int, onColor: (Int) -> Unit) {
    var text by remember(current) { mutableStateOf(rgbToHex(current)) }
    NoExtractOutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value.uppercase(Locale.US)
            parseRgbHex(value)?.let { if (it != (current and 0x00ffffff)) onColor(it) }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
    )
}
