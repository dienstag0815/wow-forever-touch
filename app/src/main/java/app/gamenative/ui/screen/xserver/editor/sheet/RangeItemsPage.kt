package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.dialog.CaptionEditor
import app.gamenative.ui.component.dialog.wowActionSummary
import app.gamenative.wowaction.BindingCaption
import com.winlator.inputcontrols.ControlElement
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.EditorText
import app.gamenative.ui.screen.xserver.editor.text

private val RowButton = 44.dp

/**
 * Page "Einträge" of a key strip with the free list ("Eigene"): every entry is one key of the strip, assigned like a single
 * button (key or combination, WoW action with the symbol of the addon, chat command) and with its own label. Entries are added,
 * removed and ordered here: drag at the handle (the row under the dragged one is marked and takes its place on release) or
 * step with the arrows. A tap on an entry opens its assignment.
 */
@Composable
internal fun RangeItemsPage(state: ElementSheetState, element: ControlElement, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val count = element.rangeItemCount
    // Height of every row in pixels (measured), for the drop place of a drag.
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val measured = List(count) { heights[it] ?: 0 }
    val dropIndex = if (dragging in 0 until count) RangeItemOrder.targetIndex(dragging, dragOffset, measured) else -1

    val currentMeasured by rememberUpdatedState(measured)
    val currentCount by rememberUpdatedState(count)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SheetHint(stringResource(R.string.editor_entry_intro))
        // How many entries the strip shows at once; the rest scrolls in. Right here, so a change of the list is seen at once.
        val visible = element.bindingCount
        if (count > 1) {
            SheetSlider(
                title = stringResource(R.string.editor_visible_in_strip),
                valueText = stringResource(R.string.editor_visible_of_count, visible, count),
                value = visible.toFloat().coerceIn(1f, count.toFloat()),
                range = 1f..count.toFloat(),
                steps = (count - 2).coerceAtLeast(0),
                onChange = { v -> state.live { element.setBindingCount(v.roundToInt().coerceIn(1, count)) } },
                onFinished = { state.flush() },
            )
        }
        for (index in 0 until count) {
            val item = element.getRangeItem(index) ?: continue
            RangeItemCard(
                state = state,
                element = element,
                index = index,
                item = item,
                count = count,
                isDragged = index == dragging,
                isDropTarget = dropIndex >= 0 && index == dropIndex && index != dragging,
                onHeight = { heights[index] = it + gapPx },
                onDragStart = {
                    dragging = index
                    dragOffset = 0f
                },
                onDrag = { delta -> dragOffset += delta },
                onDragEnd = {
                    val from = dragging
                    val target = if (from in 0 until currentCount) {
                        RangeItemOrder.targetIndex(from, dragOffset, currentMeasured)
                    } else {
                        from
                    }
                    dragging = -1
                    dragOffset = 0f
                    if (from >= 0 && target != from) state.change { element.moveRangeItem(from, target) }
                },
            )
        }
        TextButton(
            onClick = {
                var added = -1
                state.change { added = element.addRangeItem() }
                if (added >= 0) state.open(SheetRoute.picker(PickTarget.RangeItem(added)))
            },
            enabled = count < ControlElement.MAX_RANGE_ITEMS,
            modifier = Modifier.heightIn(min = SheetTarget),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.editor_add_entry))
        }
        SheetHint(
            stringResource(R.string.editor_entries_summary, count, ControlElement.MAX_RANGE_ITEMS, visible) +
                if (visible < count) stringResource(R.string.editor_entries_more) else ".",
        )
    }
}

@Composable
private fun RangeItemCard(
    state: ElementSheetState,
    element: ControlElement,
    index: Int,
    item: com.winlator.inputcontrols.RadialMenu.Slot,
    count: Int,
    isDragged: Boolean,
    isDropTarget: Boolean,
    onHeight: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val number = index + 1
    val startCallback by rememberUpdatedState(onDragStart)
    val dragCallback by rememberUpdatedState(onDrag)
    val endCallback by rememberUpdatedState(onDragEnd)
    val outline = if (isDropTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .alpha(if (isDragged) 0.55f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(2.dp, outline, RoundedCornerShape(16.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.editor_move_entry_n, number),
                modifier = Modifier
                    .size(RowButton)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { startCallback() },
                            onDragEnd = { endCallback() },
                            onDragCancel = { endCallback() },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragCallback(amount)
                            },
                        )
                    }
                    .padding(10.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { state.open(SheetRoute.picker(PickTarget.RangeItem(index))) }
                    .heightIn(min = SheetTarget)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.editor_entry_n, number), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    summaryOf(item).text(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { state.change { element.moveRangeItem(index, RangeItemOrder.step(index, -1, count)) } },
                enabled = index > 0,
                modifier = Modifier.size(RowButton),
            ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.editor_entry_forward_n, number)) }
            IconButton(
                onClick = { state.change { element.moveRangeItem(index, RangeItemOrder.step(index, 1, count)) } },
                enabled = index < count - 1,
                modifier = Modifier.size(RowButton),
            ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.editor_entry_back_n, number)) }
            IconButton(
                onClick = { state.change { element.removeRangeItem(index) } },
                enabled = count > 1,
                modifier = Modifier.size(RowButton),
            ) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.editor_remove_entry_n, number)) }
        }
        // Own label (empty = the strip names the key itself), with a preview of the automatic text.
        val automatic = BindingCaption.forSlot("", item.wowAction, item.chatCommand, item.bindingCombo, 0)
        CaptionEditor(
            label = if (BindingCaption.isOwnLabel(item.label, item.wowAction, item.chatCommand, item.bindingCombo, 0)) item.label else "",
            autoPreview = if (automatic.symbol != null) "${automatic.text} (${stringResource(R.string.editor_symbol)})" else automatic.text,
            onLabelChange = { text -> state.live { item.setLabel(text) } },
            showEmojiMode = false,
            inlineEmoji = true,
            horizontalPadding = 0.dp,
            resetKey = item to state.session.externalRevision,
        )
    }
}

private fun summaryOf(item: com.winlator.inputcontrols.RadialMenu.Slot): EditorText {
    if (!item.isEnabled) return EditorText(R.string.editor_not_assigned)
    return EditorText.raw(wowActionSummary(item.wowAction, item.bindingCombo.toString(), item.chatCommand))
}
