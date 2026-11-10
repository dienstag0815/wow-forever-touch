package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.component.dialog.CustomChatDialog
import app.gamenative.ui.component.dialog.WowSymbolIcon
import app.gamenative.ui.component.dialog.rememberWowKeys
import app.gamenative.ui.component.dialog.DelayTextField
import app.gamenative.ui.screen.wow.chatbar.ChatButton
import app.gamenative.wowaction.ResolvedKey
import app.gamenative.wowaction.WowAction
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowActionGroup
import app.gamenative.wowaction.WowActionResolver
import app.gamenative.wowaction.WowKeySource
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlElement
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.EditorText
import app.gamenative.ui.screen.xserver.editor.text

private enum class KeyCategory(@StringRes val label: Int) {
    WOW(R.string.editor_cat_wow),
    KEYBOARD(R.string.editor_kind_keyboard),
    MOUSE(R.string.editor_mouse),
    GAMEPAD(R.string.editor_gamepad),
    EXTRA(R.string.editor_extra),
}

/** One cell of the picker grid. */
private sealed interface PickerCell {
    data class Header(val text: EditorText) : PickerCell
    data object Chat : PickerCell
    data class Wow(val action: WowAction) : PickerCell
    data class Key(val binding: Binding) : PickerCell

    /** A chat control (voice, send, keyboard, channel, cancel), see [ChatButton]. */
    data class ChatControl(val button: ChatButton) : PickerCell
}

/**
 * Page "Taste wählen": search field, categories as chips and tiles for keys and WoW actions (the ones used last on top).
 * A tap is applied to the element at once. A plain key goes back to the previous page (in combination mode it stays, so more
 * keys can be added); "Entfernen" empties the binding and the page stays open for a new key.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeyPickerPage(
    state: ElementSheetState,
    element: ControlElement,
    route: SheetRoute,
    modifier: Modifier = Modifier,
) {
    val target = route.target ?: return
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val spec = state.pickSpecFor(element, target)
    if (spec == null) {
        // The target is gone (undo, ring made smaller): back to where we came from.
        LaunchedEffect(route) { state.back() }
        return
    }

    val context = LocalContext.current
    val wowKeys by rememberWowKeys(spec.allowWow)
    val recent = remember { ElementSheetState.loadRecent(context) }
    val selection = remember(route, state.session.externalRevision) { BindingSelection(spec.currentCombo) }
    var selectionTick by remember(route) { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val selectionRedraw = selectionTick
    var query by remember(route) { mutableStateOf("") }
    var category by remember(route) {
        mutableStateOf(
            if (spec.allowWow && (spec.currentWowActionId != null || spec.currentChatAction != null)) KeyCategory.WOW else KeyCategory.KEYBOARD,
        )
    }
    var showCustomChat by remember(route) { mutableStateOf(false) }

    val categories = remember(spec.allowWow) { KeyCategory.values().filter { spec.allowWow || it != KeyCategory.WOW } }
    val cells = remember(query, category, spec.allowWow, spec.allowChatAction, recent.values) {
        buildCells(query, category, spec.allowWow, spec.allowChatAction, recent.values)
    }

    fun applySelection() {
        spec.onKey(selection.toCombo())
        selectionTick++
    }

    if (showCustomChat) {
        CustomChatDialog(
            initialLabel = spec.currentChatLabel.orEmpty(),
            initialCommand = spec.currentChatCommand.orEmpty(),
            onConfirm = { label, command ->
                showCustomChat = false
                spec.onChat(label, command)
                state.back()
            },
            onDismiss = { showCustomChat = false },
        )
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // What is assigned now, and the button that empties it (the page stays open).
        val currentText = currentText(spec, selection)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_current), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    currentText.text(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = {
                    selection.clear()
                    spec.onKey(null)
                    selectionTick++
                },
                enabled = currentText.res != R.string.editor_not_assigned,
                modifier = Modifier.heightIn(min = SheetTarget),
            ) { Text(stringResource(R.string.editor_remove)) }
        }

        // Keys already chosen as a combination: a tap on one takes just that key away.
        if (selection.keys.size > 1 || (selection.comboMode && selection.keys.isNotEmpty())) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (chosen in selection.keys) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                selection.remove(chosen)
                                applySelection()
                            }
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(chosen.toString(), style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.editor_remove_key), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        NoExtractOutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    if (spec.allowWow) stringResource(R.string.editor_search_key_or_wow)
                    else stringResource(R.string.editor_search_key),
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.editor_clear_search)) }
                }
            },
        )

        if (query.isBlank()) {
            SheetChips(
                options = categories.map { stringResource(it.label) },
                selectedIndex = categories.indexOf(category).coerceAtLeast(0),
                onSelect = { category = categories[it] },
            )
        }

        if (cells.none { it !is PickerCell.Header }) {
            Text(
                stringResource(R.string.editor_nothing_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Options above the tiles scroll away with them, so the grid keeps room on a small sheet.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (category != KeyCategory.WOW || query.isNotBlank()) {
                        SheetSwitchRow(
                            title = stringResource(R.string.editor_combo_mode),
                            subtitle = null,
                            checked = selection.comboMode,
                            onChange = {
                                selection.setComboMode(it)
                                selectionTick++
                            },
                        )
                        if (selection.keys.size > 1) {
                            SheetChips(
                                options = listOf(
                                    stringResource(R.string.editor_simultaneous),
                                    stringResource(R.string.editor_one_after_another),
                                ),
                                selectedIndex = if (selection.mode == BindingCombo.Mode.SEQUENCE) 1 else 0,
                                onSelect = {
                                    selection.setMode(if (it == 1) BindingCombo.Mode.SEQUENCE else BindingCombo.Mode.SIMULTANEOUS)
                                    applySelection()
                                },
                            )
                            if (selection.mode == BindingCombo.Mode.SEQUENCE) {
                                DelayTextField(
                                    label = stringResource(R.string.editor_delay_ms),
                                    value = selection.sequenceDelayMs,
                                    valueRange = BindingCombo.MIN_SEQUENCE_DELAY_MS..BindingCombo.MAX_SEQUENCE_DELAY_MS,
                                    onValueChange = {
                                        selection.setDelay(it)
                                        applySelection()
                                    },
                                )
                            }
                        }
                    }

                    if (spec.allowWow && (category == KeyCategory.WOW || query.isNotBlank())) {
                        Text(
                            text = if (wowKeys.values.any { it.source == WowKeySource.WOW_FILE }) {
                                stringResource(R.string.editor_keys_from_wow)
                            } else {
                                stringResource(R.string.editor_no_wow_bindings)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(
                items = cells,
                span = { cell ->
                    if (cell is PickerCell.Header || cell is PickerCell.Chat) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                },
            ) { cell ->
                when (cell) {
                    is PickerCell.Header -> Text(
                        cell.text.text(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    is PickerCell.Chat -> ChatTile(selected = spec.currentWowActionId == null && !spec.currentChatCommand.isNullOrBlank()) {
                        showCustomChat = true
                    }
                    is PickerCell.ChatControl -> ChatControlTile(
                        button = cell.button,
                        selected = cell.button.id == spec.currentChatAction,
                    ) {
                        spec.onChatAction(cell.button)
                        state.back()
                    }
                    is PickerCell.Wow -> WowTile(
                        action = cell.action,
                        key = wowKeys[cell.action.command],
                        selected = cell.action.command == spec.currentWowActionId,
                    ) {
                        recent.use(cell.action.command)
                        ElementSheetState.saveRecent(context, recent)
                        spec.onWow(cell.action, wowKeys[cell.action.command]?.combo)
                        state.back()
                    }
                    is PickerCell.Key -> KeyTile(
                        binding = cell.binding,
                        selected = cell.binding in selection.keys,
                        enabled = selection.canTap(cell.binding),
                    ) {
                        if (selection.tap(cell.binding)) {
                            applySelection()
                            // One key: done, back to the list. A combination stays open for the next key.
                            if (!selection.comboMode && !selection.isEmpty) state.back()
                        }
                    }
                }
            }
        }
    }
}

private fun currentText(spec: PickSpec, selection: BindingSelection): EditorText {
    // Untouched choice: say what the element really stores (a WoW action or chat text, not just its key).
    if (selection.keys == spec.currentCombo.bindings.filter { it != Binding.NONE }) {
        val action = WowActionCatalog.find(spec.currentWowActionId)
        val chat = spec.currentChatCommand
        ChatButton.fromId(spec.currentChatAction)?.let { return EditorText(R.string.editor_chat_action, listOf(it.label)) }
        if (action != null) {
            val tail = action.chatCommand ?: selection.toCombo()?.toString()
            return if (tail != null) {
                EditorText(R.string.editor_wow_action_tail, listOf(action.name, tail))
            } else {
                EditorText(R.string.editor_wow_action, listOf(action.name))
            }
        }
        if (chat != null) return EditorText(R.string.editor_chat_action, listOf(chat))
    }
    val combo = selection.toCombo() ?: return EditorText(R.string.editor_not_assigned)
    return if (combo.isSequence) {
        EditorText(R.string.editor_sequence_ms, listOf(combo, combo.sequenceDelayMs))
    } else {
        EditorText.raw(combo.toString())
    }
}

private fun buildCells(
    query: String,
    category: KeyCategory,
    allowWow: Boolean,
    allowChatAction: Boolean,
    recentIds: List<String>,
): List<PickerCell> {
    val q = query.trim()
    val cells = ArrayList<PickerCell>()
    if (q.isEmpty()) {
        when (category) {
            KeyCategory.WOW -> {
                cells.add(PickerCell.Chat)
                if (allowChatAction) {
                    cells.add(PickerCell.Header(EditorText(R.string.editor_chat_buttons)))
                    ChatButton.entries.forEach { cells.add(PickerCell.ChatControl(it)) }
                }
                val all = WowActionCatalog.all
                val (recent, rest) = withRecentsFirst(all, recentIds) { it.command }
                if (recent.isNotEmpty()) {
                    cells.add(PickerCell.Header(EditorText(R.string.editor_recently_used)))
                    recent.forEach { cells.add(PickerCell.Wow(it)) }
                }
                for (group in WowActionGroup.values()) {
                    val inGroup = rest.filter { it.group == group }
                    if (inGroup.isEmpty()) continue
                    cells.add(PickerCell.Header(EditorText.raw(group.title)))
                    inGroup.forEach { cells.add(PickerCell.Wow(it)) }
                }
            }
            else -> bindingsOf(category).forEach { cells.add(PickerCell.Key(it)) }
        }
        return cells
    }
    // A search looks through everything at once.
    if (allowChatAction) {
        val controls = ChatButton.entries.filter { it.label.contains(q, ignoreCase = true) || "chat".contains(q, ignoreCase = true) }
        if (controls.isNotEmpty()) {
            cells.add(PickerCell.Header(EditorText(R.string.editor_chat_buttons)))
            controls.forEach { cells.add(PickerCell.ChatControl(it)) }
        }
    }
    if (allowWow) {
        val actions = WowActionCatalog.search(q)
        if (actions.isNotEmpty()) {
            cells.add(PickerCell.Header(EditorText(R.string.editor_wow_actions)))
            val (recent, rest) = withRecentsFirst(actions, recentIds) { it.command }
            (recent + rest).forEach { cells.add(PickerCell.Wow(it)) }
        }
    }
    for (cat in KeyCategory.values()) {
        if (cat == KeyCategory.WOW) continue
        val hits = bindingsOf(cat).filter { it.toString().contains(q, ignoreCase = true) }
        if (hits.isNotEmpty()) {
            cells.add(PickerCell.Header(EditorText(cat.label)))
            hits.forEach { cells.add(PickerCell.Key(it)) }
        }
    }
    return cells
}

private fun bindingsOf(category: KeyCategory): List<Binding> = when (category) {
    KeyCategory.KEYBOARD -> KEYBOARD
    KeyCategory.MOUSE -> MOUSE
    KeyCategory.GAMEPAD -> GAMEPAD
    KeyCategory.EXTRA -> EXTRA
    KeyCategory.WOW -> emptyList()
}

private val KEYBOARD: List<Binding> by lazy { Binding.keyboardBindingValues().filter { it != Binding.NONE } }
private val MOUSE: List<Binding> by lazy { Binding.mouseBindingValues().filter { it != Binding.NONE } }
private val GAMEPAD: List<Binding> by lazy { Binding.gamepadBindingValues().filter { it != Binding.NONE } }
private val EXTRA: List<Binding> by lazy { Binding.extraBindingValues().filter { it != Binding.NONE } }

@Composable
private fun KeyTile(binding: Binding, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = SheetTarget)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = binding.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) content else content.copy(alpha = 0.38f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WowTile(action: WowAction, key: ResolvedKey?, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(onClick = onClick)
            .heightIn(min = 84.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        WowSymbolIcon(action.symbol, content, size = 28.dp)
        Text(
            text = action.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = action.chatCommand ?: WowActionResolver.keyText(key),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (key == null && action.chatCommand == null) content.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A chat control of the chat buttons: name and a short line what it does. */
@Composable
private fun ChatControlTile(button: ChatButton, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(onClick = onClick)
            .heightIn(min = 84.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = button.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = button.hint,
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatTile(selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WowSymbolIcon(WowActionCatalog.CUSTOM_CHAT_SYMBOL, content)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.editor_custom_chat_command), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = content)
            Text(
                stringResource(R.string.editor_chat_command_hint),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.7f),
            )
        }
    }
}
