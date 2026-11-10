package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.dialog.CaptionEditor
import app.gamenative.ui.component.dialog.RingCountChips
import app.gamenative.ui.component.dialog.wowActionSummary
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.wowaction.ActionBarTemplates
import app.gamenative.wowaction.BindingCaption
import app.gamenative.wowaction.WowActionSource
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.StickRing
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.resolve

/**
 * Page "Ringtasten" of a stick: how many keys sit around it (Aus / 4 / 6 / 8), where the ring starts and how wide it is,
 * and for each key its assignment, its label (with emojis) and an optional game picture area.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RingPage(state: ElementSheetState, element: ControlElement, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    confirm?.let { (name, action) ->
        OverwriteDialog(
            templateName = name,
            onConfirm = {
                confirm = null
                action()
            },
            onDismiss = { confirm = null },
        )
    }
    val selected = state.selectedField(element).takeIf { it in 0 until element.ringSegments } ?: -1
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHint(
            stringResource(R.string.editor_ring_intro),
        )
        SheetSection(stringResource(R.string.editor_count))
        RingCountChips(element = element, onChanged = { state.change { } })

        if (element.ringSegments > 0) {
            SheetSection(stringResource(R.string.editor_preview))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FieldPreview(
                    cells = (0 until element.ringSegments).map { fieldCellOf(element.getRingItem(it), it + 1) },
                    angles = ringAngles(element.ringSegments, element.ringStartAngle),
                    selected = selected,
                    onSelect = { index ->
                        state.selectField(element, index)
                        state.open(SheetRoute.picker(PickTarget.RingSegment(index)))
                    },
                )
            }
            SheetHint(stringResource(R.string.editor_ring_tap_hint))

            if (selected >= 0) {
                SheetSection(stringResource(R.string.editor_ring_key_n, selected + 1))
                TextButton(onClick = { state.selectField(element, -1) }, modifier = Modifier.heightIn(min = SheetTarget)) {
                    Text(stringResource(R.string.editor_show_all_ring_keys))
                }
            }
            for (index in 0 until element.ringSegments) {
                if (selected >= 0 && index != selected) continue
                RingSegmentCard(state = state, element = element, index = index)
            }

            SheetGroup(title = stringResource(R.string.editor_templates), summary = stringResource(R.string.editor_fill_ring_keys)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetSection(stringResource(R.string.editor_from_template))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (template in ActionBarTemplates.RING_TEMPLATES) {
                            AssistChip(
                                onClick = {
                                    val doApply = {
                                        val keys = runCatching {
                                            WowActionSource.combos(WowActionSource.resolveFor(File(GamePath.load(context))))
                                        }.getOrNull() ?: emptyMap()
                                        val count = element.ringSegments
                                        val slots = ActionBarTemplates.ringSlotsFor(template, count, keys)
                                        state.change { slots.forEachIndexed { index, slot -> element.setRingItem(index, slot) } }
                                        val empty = ActionBarTemplates.ringEmpty(template, count)
                                        SnackbarManager.show(
                                            if (empty > 0) {
                                                context.getString(R.string.editor_template_applied_partial, template.name, count - empty, empty)
                                            } else {
                                                context.getString(R.string.editor_template_applied, template.name, count)
                                            },
                                        )
                                    }
                                    val current = (0 until element.ringSegments).map { element.getRingItem(it) }
                                    if (ActionBarTemplates.isCustomized(current)) confirm = template.name to doApply else doApply()
                                },
                                label = { Text(template.name) },
                                modifier = Modifier.heightIn(min = SheetTarget),
                            )
                        }
                    }
                    SheetHint(
                        stringResource(R.string.editor_ring_template_prefix) +
                            ActionBarTemplates.RING_TEMPLATES.joinToString(" ") { "${it.name}: ${it.hint}." } +
                            stringResource(R.string.editor_ring_template_suffix),
                    )

                    SheetSection(stringResource(R.string.editor_action_bar_template))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (bar in 1..ActionBarTemplates.BAR_COUNT) {
                            AssistChip(
                                onClick = {
                                    val doApply = {
                                        val keys = runCatching {
                                            WowActionSource.combos(WowActionSource.resolveFor(File(GamePath.load(context))))
                                        }.getOrNull() ?: emptyMap()
                                        val slots = ActionBarTemplates.ringSlots(bar, element.ringSegments, keys, actionBarData(context))
                                        state.change { slots.forEachIndexed { index, slot -> element.setRingItem(index, slot) } }
                                        SnackbarManager.show(context.getString(R.string.editor_template_applied, ActionBarTemplates.menuName(bar), slots.size))
                                    }
                                    val current = (0 until element.ringSegments).map { element.getRingItem(it) }
                                    if (ActionBarTemplates.isCustomized(current)) confirm = ActionBarTemplates.menuName(bar) to doApply else doApply()
                                },
                                label = { Text(stringResource(R.string.editor_bar_n, bar)) },
                                modifier = Modifier.heightIn(min = SheetTarget),
                            )
                        }
                    }
                    SheetHint(stringResource(R.string.editor_ring_bar_hint))
                }
            }

            SheetGroup(title = stringResource(R.string.editor_ring_shape), summary = stringResource(R.string.editor_ring_shape_summary)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetSlider(
                        title = stringResource(R.string.editor_start_angle),
                        valueText = "${element.ringStartAngle.roundToInt()}°",
                        value = element.ringStartAngle,
                        range = -180f..180f,
                        onChange = { v -> state.live { element.setRingStartAngle((v / 5f).roundToInt() * 5f) } },
                        onFinished = { state.flush() },
                    )
                    SheetHint(stringResource(R.string.editor_start_angle_hint))
                    SheetSlider(
                        title = stringResource(R.string.editor_ring_width),
                        valueText = stringResource(R.string.editor_grid_value, String.format(Locale.US, "%.1f", element.ringWidth)),
                        value = element.ringWidth,
                        range = StickRing.MIN_WIDTH..StickRing.MAX_WIDTH,
                        onChange = { v -> state.live { element.setRingWidth((v * 10f).roundToInt() / 10f) } },
                        onFinished = { state.flush() },
                    )
                    SheetSlider(
                        title = stringResource(R.string.editor_gap_to_stick),
                        valueText = stringResource(R.string.editor_grid_value, String.format(Locale.US, "%.2f", element.ringGap)),
                        value = element.ringGap,
                        range = StickRing.MIN_GAP..StickRing.MAX_GAP,
                        onChange = { v -> state.live { element.setRingGap((v * 20f).roundToInt() / 20f) } },
                        onFinished = { state.flush() },
                    )
                    SheetHint(stringResource(R.string.editor_gap_hint))
                }
            }
        }
    }
}

@Composable
private fun RingSegmentCard(state: ElementSheetState, element: ControlElement, index: Int) {
    // The element is plain Java: read the tick so the card is not skipped when only the element changed.
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val item = element.getRingItem(index)
    val number = index + 1
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SheetNavRow(
            title = stringResource(R.string.editor_ring_key_n, number),
            value = wowActionSummary(item.wowAction, item.bindingCombo.toString(), item.chatCommand),
            onClick = { state.open(SheetRoute.picker(PickTarget.RingSegment(index))) },
        )
        // Own label (empty = the ring names the key itself), with a preview of the automatic text.
        val automatic = BindingCaption.forSlot("", item.wowAction, item.chatCommand, item.bindingCombo, number)
        CaptionEditor(
            label = if (BindingCaption.isOwnLabel(item.label, item.wowAction, item.chatCommand, item.bindingCombo, number)) item.label else "",
            autoPreview = if (automatic.symbol != null) "${automatic.text} (${stringResource(R.string.editor_symbol)})" else automatic.text,
            onLabelChange = { text -> state.live { item.setLabel(text) } },
            showEmojiMode = index == 0,
            inlineEmoji = true,
            horizontalPadding = 0.dp,
            resetKey = item to state.session.externalRevision,
            ignoredHint = { text ->
                ignoredLabelHint(text, BindingCaption.isOwnLabel(text, item.wowAction, item.chatCommand, item.bindingCombo, number))?.resolve(context)
            },
        )
        RingOuterControls(state = state, element = element, index = index)
    }
}

/**
 * Second, outer ring of one segment: "hinzufügen" creates an empty outer field with the same angle as the segment (concentric,
 * the geometry follows from the ring width) and opens its key picker; an existing one is assigned like an inner key.
 */
@Composable
private fun RingOuterControls(state: ElementSheetState, element: ControlElement, index: Int) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val context = LocalContext.current
    val number = index + 1
    val outer = element.getRingOuterItem(index)
    if (outer == null) {
        TextButton(
            onClick = {
                state.change { element.addRingOuter(index) }
                state.open(SheetRoute.picker(PickTarget.RingOuter(index)))
            },
            modifier = Modifier.heightIn(min = SheetTarget),
        ) {
            Text(stringResource(R.string.editor_add_outer_field))
        }
        return
    }
    SheetNavRow(
        title = stringResource(R.string.editor_outer_field_n, number),
        value = wowActionSummary(outer.wowAction, outer.bindingCombo.toString(), outer.chatCommand),
        onClick = { state.open(SheetRoute.picker(PickTarget.RingOuter(index))) },
    )
    val automatic = BindingCaption.forSlot("", outer.wowAction, outer.chatCommand, outer.bindingCombo, number)
    CaptionEditor(
        label = if (BindingCaption.isOwnLabel(outer.label, outer.wowAction, outer.chatCommand, outer.bindingCombo, number)) outer.label else "",
        autoPreview = if (automatic.symbol != null) "${automatic.text} (${stringResource(R.string.editor_symbol)})" else automatic.text,
        onLabelChange = { text -> state.live { outer.setLabel(text) } },
        showEmojiMode = false,
        inlineEmoji = true,
        horizontalPadding = 0.dp,
        resetKey = outer to state.session.externalRevision,
        ignoredHint = { text ->
            ignoredLabelHint(text, BindingCaption.isOwnLabel(text, outer.wowAction, outer.chatCommand, outer.bindingCombo, number))?.resolve(context)
        },
    )
    TextButton(
        onClick = { state.change { element.removeRingOuter(index) } },
        modifier = Modifier.heightIn(min = SheetTarget),
    ) {
        Text(stringResource(R.string.editor_remove_outer_field))
    }
}
