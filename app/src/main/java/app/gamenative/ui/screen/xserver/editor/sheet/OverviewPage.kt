package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.dialog.CaptionEditor
import app.gamenative.ui.component.dialog.wowActionSummary
import app.gamenative.ui.screen.wow.chatbar.ChatButton
import app.gamenative.ui.screen.xserver.editor.PreviewIcon
import app.gamenative.ui.screen.xserver.editor.PreviewKind
import app.gamenative.ui.screen.xserver.editor.ResizeMath
import app.gamenative.wowaction.StandardNames
import app.gamenative.wowaction.WowActionCatalog
import com.winlator.inputcontrols.ControlElement
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.EditorText
import app.gamenative.ui.screen.xserver.editor.text

/** First page: the label, the sub pages with their current values, and the two settings used most (size, opacity). */
@Composable
internal fun OverviewPage(state: ElementSheetState, element: ControlElement, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val reload = state.session.externalRevision
    val view = state.view
    var label by remember(element, reload) { mutableStateOf(StandardNames.displayLabel(element.text)) }
    val pages = SheetPages.available(element.type, element.isRadialMenuButton, element.isCustomRange)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // First line: what kind of element this is (button, key strip, stick, ...), changeable right here.
        ElementKindChooser(state, element)

        if (element.type == ControlElement.Type.BUTTON) {
            CaptionEditor(
                label = label,
                autoPreview = WowActionCatalog.displayLabel(null, element.wowAction, element.getBindingComboAt(0)).ifBlank { "–" },
                onLabelChange = {
                    label = it
                    state.live { element.setText(it) }
                },
                inlineEmoji = true,
                horizontalPadding = 0.dp,
                resetKey = element to reload,
            )
        }

        for (page in pages) {
            SheetNavRow(
                title = stringResource(page.title),
                value = pageSummary(page, element, state)?.text(),
                onClick = { state.open(SheetRoute(page)) },
            )
        }

        SheetSlider(
            title = stringResource(R.string.editor_size),
            valueText = "${(element.scale * 100).roundToInt()} %",
            value = element.scale,
            range = ResizeMath.MIN_SCALE..ResizeMath.MAX_SCALE,
            onChange = { v -> state.live { element.setScale(ResizeMath.tidy(v)) } },
            onFinished = { state.flush() },
            resetLabel = "100 %",
            onReset = { state.change { element.setScale(1f) } },
        )

        val inherited = element.buttonOpacity < 0f
        SheetSlider(
            title = stringResource(R.string.editor_opacity),
            valueText = if (inherited) {
                stringResource(R.string.editor_opacity_default, "${(view.overlayOpacity * 100).roundToInt()} %")
            } else {
                "${(element.buttonOpacity * 100).roundToInt()} %"
            },
            value = element.getEffectiveButtonOpacity(view.overlayOpacity),
            range = 0.1f..1f,
            onChange = { v -> state.live { element.setButtonOpacity(v) } },
            onFinished = { state.flush() },
            resetLabel = stringResource(R.string.editor_default),
            onReset = { state.change { element.setButtonOpacity(ControlElement.INHERIT_BUTTON_OPACITY) } },
        )
    }
}

/** One line under a page's name in the overview. */
internal fun pageSummary(page: SheetPage, element: ControlElement, state: ElementSheetState): EditorText? = when (page) {
    SheetPage.BINDING -> bindingSummary(element)
    SheetPage.RADIAL_MENU -> {
        val menu = state.view.profile?.getRadialMenu(element.radialMenuId)
        menu?.let { EditorText(R.string.editor_summary_fields, listOf(it.slots.size)) }
    }
    SheetPage.RING ->
        if (element.ringSegments > 0) EditorText(R.string.editor_summary_keys, listOf(element.ringSegments))
        else EditorText(R.string.editor_off)
    SheetPage.RANGE_ITEMS -> EditorText(R.string.editor_summary_entries, listOf(element.rangeItemCount))
    SheetPage.APPEARANCE -> EditorText(R.string.editor_summary_appearance, listOf(shapeName(element.shape)))
    SheetPage.ADVANCED -> EditorText(R.string.editor_summary_position, listOf(typeName(element.type), element.x, element.y))
    else -> null
}

internal fun bindingSummary(element: ControlElement): EditorText {
    if (element.type == ControlElement.Type.BUTTON) {
        chatActionSummary(element)?.let { return it }
        val combo = element.getBindingComboAt(0)
        if (element.wowAction == null && element.chatCommand == null && combo.isEmpty) return EditorText(R.string.editor_not_assigned)
        return EditorText.raw(wowActionSummary(element.wowAction, combo.toString(), element.chatCommand))
    }
    val count = SheetPages.slotCount(element.type)
    val set = (0 until count).count { !element.getBindingComboAt(it).isEmpty }
    return EditorText(R.string.editor_slots_assigned, listOf(set, count))
}

/** "Chat: Sprache" for a button that runs a chat control, else null. */
internal fun chatActionSummary(element: ControlElement): EditorText? =
    ChatButton.fromId(element.chatAction)?.let { EditorText(R.string.editor_chat_action, listOf(it.label)) }

internal fun shapeName(shape: ControlElement.Shape): EditorText = EditorText(
    when (shape) {
        ControlElement.Shape.CIRCLE -> R.string.editor_shape_circle
        ControlElement.Shape.RECT -> R.string.editor_shape_rect
        ControlElement.Shape.ROUND_RECT -> R.string.editor_shape_round_rect
        ControlElement.Shape.SQUARE -> R.string.editor_shape_square
        ControlElement.Shape.PILL -> R.string.editor_shape_pill
        ControlElement.Shape.ARC -> R.string.editor_shape_arc
    },
)

internal fun typeName(type: ControlElement.Type): EditorText = EditorText(
    when (type) {
        ControlElement.Type.BUTTON -> R.string.editor_kind_button
        ControlElement.Type.D_PAD -> R.string.editor_kind_d_pad
        ControlElement.Type.RANGE_BUTTON -> R.string.editor_kind_key_strip
        ControlElement.Type.STICK -> R.string.editor_kind_stick
        ControlElement.Type.TRACKPAD -> R.string.editor_kind_trackpad
        ControlElement.Type.SHOOTER_MODE -> R.string.editor_type_shooter
        ControlElement.Type.SCROLL_WHEEL -> R.string.editor_kind_scroll_wheel
    },
)

/** Picture of an element type in the "Knopfart" chips. */
internal fun typePreview(type: ControlElement.Type): PreviewKind = when (type) {
    ControlElement.Type.BUTTON -> PreviewKind.BUTTON
    ControlElement.Type.D_PAD -> PreviewKind.D_PAD
    ControlElement.Type.RANGE_BUTTON -> PreviewKind.KEY_STRIP
    ControlElement.Type.STICK -> PreviewKind.STICK
    ControlElement.Type.TRACKPAD -> PreviewKind.TRACKPAD
    ControlElement.Type.SHOOTER_MODE -> PreviewKind.SHOOTER
    ControlElement.Type.SCROLL_WHEEL -> PreviewKind.SCROLL_WHEEL
}

/**
 * "Knopfart": one chip per element type with its picture and name, the current one marked. A tap switches the element
 * (position and size stay, see [changeType]); the sheet follows to the pages of the new type.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ElementKindChooser(state: ElementSheetState, element: ControlElement, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val types = ControlElement.Type.values().toList()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetSection(stringResource(R.string.editor_button_type_section, typeName(element.type).text()))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (type in types) {
                FilterChip(
                    selected = type == element.type,
                    onClick = { state.change { changeType(element, type) } },
                    label = { Text(typeName(type).text()) },
                    leadingIcon = {
                        PreviewIcon(
                            kind = typePreview(type),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    modifier = Modifier.heightIn(min = SheetTarget),
                )
            }
        }
    }
}
