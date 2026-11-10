package app.gamenative.ui.component.dialog

import app.gamenative.R
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.ui.component.NoExtractOutlinedTextField
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import app.gamenative.wowaction.WowAction
import app.gamenative.wowaction.WowActionCatalog

/** Index of the "WoW-Aktion" tab in [ControllerBindingDialog] (0 to 3 are the key categories). */
private const val WOW_ACTION_CATEGORY = 4

/**
 * Dialog for selecting controller button bindings.
 *
 * Provides a searchable, categorized list of available bindings (keyboard, mouse, gamepad)
 * with search functionality for quick filtering.
 *
 * @param buttonName Display name of the button being configured
 * @param currentBinding Currently assigned binding (if any)
 * @param onDismiss Callback when dialog is dismissed without selection
 * @param onBindingSelected Callback when a binding is selected (null for NONE)
 * @param onWowActionSelected If set, the dialog offers the extra tab "WoW-Aktion": the player picks a WoW action by name and
 *        gets the key currently set in WoW (null combo = the action has no key). Closing is up to the caller.
 * @param currentWowActionId Action id already stored for the button/field (highlighted in the tab)
 * @param onChatCommandSelected If set, the WoW tab offers "Eigener Chat-Befehl ..." (label and free chat text such as
 *        "/follow" or "/s Hallo"); the callback gets (label, command). Closing is up to the caller.
 * @param currentChatCommand Chat text already stored for the button/field (prefills the free command dialog)
 * @param currentChatLabel Label already stored for the button/field (prefills the free command dialog)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerBindingDialog(
    buttonName: String,
    currentBinding: Binding?,
    currentBindingCombo: BindingCombo? = currentBinding?.let { BindingCombo.of(it) },
    onDismiss: () -> Unit,
    onBindingSelected: (Binding?) -> Unit,
    onBindingComboSelected: ((BindingCombo?) -> Unit)? = null,
    onWowActionSelected: ((WowAction, BindingCombo?) -> Unit)? = null,
    currentWowActionId: String? = null,
    onChatCommandSelected: ((String, String) -> Unit)? = null,
    currentChatCommand: String? = null,
    currentChatLabel: String? = null,
) {
    val wowKeys by rememberWowKeys(onWowActionSelected != null)
    var showCustomChat by remember { mutableStateOf(false) }
    if (showCustomChat && onChatCommandSelected != null) {
        CustomChatDialog(
            initialLabel = currentChatLabel.orEmpty(),
            initialCommand = currentChatCommand.orEmpty(),
            onConfirm = { label, command ->
                showCustomChat = false
                onChatCommandSelected(label, command)
            },
            onDismiss = { showCustomChat = false },
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    // 0 = Keyboard, 1 = Mouse, 2 = Gamepad, 3 = Extra, 4 = WoW action, null = All
    var selectedCategory by remember {
        mutableStateOf<Int?>(if (onWowActionSelected != null && currentWowActionId != null) WOW_ACTION_CATEGORY else 0)
    }
    val wowTabActive = onWowActionSelected != null && selectedCategory == WOW_ACTION_CATEGORY
    var isSearchExpanded by remember { mutableStateOf(false) }
    val selectedBindings = remember(currentBindingCombo, currentBinding) {
        mutableStateListOf<Binding>().apply {
            addAll((currentBindingCombo ?: BindingCombo.of(currentBinding)).bindings)
        }
    }
    // false: a new key replaces the chosen one; true: keys are added up to a combination (e.g. Shift + 1).
    var comboMode by remember(currentBindingCombo, currentBinding) {
        mutableStateOf((currentBindingCombo ?: BindingCombo.of(currentBinding)).bindings.size > 1)
    }
    var selectedMode by remember(currentBindingCombo, currentBinding) {
        mutableStateOf((currentBindingCombo ?: BindingCombo.of(currentBinding)).mode)
    }
    var selectedSequenceDelayMs by remember(currentBindingCombo, currentBinding) {
        mutableIntStateOf((currentBindingCombo ?: BindingCombo.of(currentBinding)).sequenceDelayMs)
    }

    fun submitSelection(combo: BindingCombo?) {
        if (onBindingComboSelected != null) {
            onBindingComboSelected(combo)
        } else {
            onBindingSelected(combo?.primaryBinding)
        }
    }

    // Get bindings by category
    val keyboardBindings = remember { Binding.keyboardBindingValues().toList() }
    val mouseBindings = remember { Binding.mouseBindingValues().toList() }
    val gamepadBindings = remember { Binding.gamepadBindingValues().toList() }
    val extraBindings = remember { Binding.extraBindingValues().toList() }

    // Data class to hold binding with its category for cross-category search
    data class BindingWithCategory(val binding: Binding, val categoryIndex: Int)

    // All bindings for cross-category search
    val allBindings = remember {
        keyboardBindings.map { BindingWithCategory(it, 0) } +
        mouseBindings.map { BindingWithCategory(it, 1) } +
        gamepadBindings.map { BindingWithCategory(it, 2) } +
        extraBindings.map { BindingWithCategory(it, 3) }
    }

    // Filter bindings based on search and selected category
    val filteredBindingsWithCategory = remember(searchQuery, selectedCategory) {
        if (searchQuery.isBlank()) {
            // No search - show bindings from selected category
            val currentBindings = when (selectedCategory) {
                0 -> keyboardBindings
                1 -> mouseBindings
                2 -> gamepadBindings
                3 -> extraBindings
                else -> emptyList()
            }
            currentBindings.map { BindingWithCategory(it, selectedCategory ?: 0) }
        } else {
            // Search mode - filter all or specific category
            val searchResults = allBindings.filter {
                it.binding.toString().contains(searchQuery, ignoreCase = true)
            }

            // If a category is selected, filter to only that category
            if (selectedCategory != null) {
                searchResults.filter { it.categoryIndex == selectedCategory }
            } else {
                searchResults
            }
        }
    }

    val filteredBindings = filteredBindingsWithCategory.map { it.binding }

    // Count matches per category for search mode
    val categoryMatchCounts = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            null
        } else {
            mapOf(
                0 to allBindings.count { it.categoryIndex == 0 && it.binding.toString().contains(searchQuery, ignoreCase = true) },
                1 to allBindings.count { it.categoryIndex == 1 && it.binding.toString().contains(searchQuery, ignoreCase = true) },
                2 to allBindings.count { it.categoryIndex == 2 && it.binding.toString().contains(searchQuery, ignoreCase = true) },
                3 to allBindings.count { it.categoryIndex == 3 && it.binding.toString().contains(searchQuery, ignoreCase = true) }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,  // Allow custom width beyond platform default
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.98f)  // Nearly full width for better space utilization
                .fillMaxHeight(0.92f),  // Taller to maximize vertical space
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with title, current binding, and close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title and current binding
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(app.gamenative.R.string.bind_button, buttonName),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val combo = BindingCombo.fromBindings(selectedBindings, selectedMode, selectedSequenceDelayMs)
                        val comboLabel = if (combo.isSequence) {
                            stringResource(
                                app.gamenative.R.string.binding_sequence_current_format,
                                combo.toString(),
                                combo.sequenceDelayMs,
                            )
                        } else {
                            combo.toString()
                        }
                        Text(
                            text = stringResource(app.gamenative.R.string.current_binding, comboLabel),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                val combo = BindingCombo.fromBindings(selectedBindings, selectedMode, selectedSequenceDelayMs)
                                submitSelection(if (combo.isEmpty) null else combo)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Check, stringResource(app.gamenative.R.string.save))
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, stringResource(app.gamenative.R.string.close))
                        }
                    }
                }

                // Two-column layout: Left = Controls, Right = Bindings list
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left column: Search and Category selection
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Chosen keys: "x" removes just that one key, a new key replaces them unless "combination" is on.
                        if (selectedBindings.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectedBindings.toList().forEach { chosen ->
                                    Surface(
                                        modifier = Modifier.clickable { selectedBindings.remove(chosen) },
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(chosen.toString(), style = MaterialTheme.typography.bodyMedium)
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(app.gamenative.R.string.clear_binding),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (!wowTabActive) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Kombination (mehrere Tasten)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(checked = comboMode, onCheckedChange = { comboMode = it })
                            }
                        }
                        // Collapsible search - starts as icon button, expands to full search field
                        if (!isSearchExpanded) {
                            // Compact search button
                            OutlinedButton(
                                onClick = { isSearchExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(app.gamenative.R.string.search_placeholder),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            // Expanded search field
                            NoExtractOutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    // When search starts, switch to "all categories" mode
                                    if (it.isNotBlank() && selectedCategory != null && selectedCategory != WOW_ACTION_CATEGORY) {
                                        selectedCategory = null
                                    }
                                },
                                placeholder = { Text(stringResource(app.gamenative.R.string.search_placeholder), style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            if (selectedCategory != WOW_ACTION_CATEGORY) selectedCategory = 0 // Return to Keyboard category
                                            isSearchExpanded = false // Collapse search
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(app.gamenative.R.string.clear_search),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp), // Even more compact when expanded
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                        }

                        // Category buttons (vertical stack)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(app.gamenative.R.string.category),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            if (selectedBindings.size > 1) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = selectedMode == BindingCombo.Mode.SIMULTANEOUS,
                                        onClick = { selectedMode = BindingCombo.Mode.SIMULTANEOUS },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        label = { Text(stringResource(app.gamenative.R.string.binding_mode_simultaneous)) },
                                    )
                                    SegmentedButton(
                                        selected = selectedMode == BindingCombo.Mode.SEQUENCE,
                                        onClick = { selectedMode = BindingCombo.Mode.SEQUENCE },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        label = { Text(stringResource(app.gamenative.R.string.binding_mode_sequence)) },
                                    )
                                }
                                if (selectedMode == BindingCombo.Mode.SEQUENCE) {
                                    DelayTextField(
                                        label = stringResource(app.gamenative.R.string.binding_sequence_delay_ms),
                                        value = selectedSequenceDelayMs,
                                        valueRange = BindingCombo.MIN_SEQUENCE_DELAY_MS..BindingCombo.MAX_SEQUENCE_DELAY_MS,
                                        onValueChange = { selectedSequenceDelayMs = it },
                                    )
                                }
                            }

                            // Helper function to render category button
                            @Composable
                            fun CategoryButton(
                                categoryIndex: Int,
                                label: String,
                                matchCount: Int? = null
                            ) {
                                val isSelected = selectedCategory == categoryIndex
                                val isSearching = searchQuery.isNotBlank()

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSearching) {
                                                // In search mode: clicking filters to this category
                                                selectedCategory = if (selectedCategory == categoryIndex) null else categoryIndex
                                            } else {
                                                // Normal mode: switch to this category
                                                selectedCategory = categoryIndex
                                            }
                                        },
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp), // Slightly reduced padding for smaller screens
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (matchCount != null && isSearching) {
                                            Text(
                                                text = "($matchCount)",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // WoW action tab (only where the caller can store an action)
                            if (onWowActionSelected != null) {
                                CategoryButton(
                                    categoryIndex = WOW_ACTION_CATEGORY,
                                    label = stringResource(R.string.game_wow_action),
                                    matchCount = if (searchQuery.isBlank()) null else WowActionCatalog.search(searchQuery).size
                                )
                            }

                            // Keyboard button
                            CategoryButton(
                                categoryIndex = 0,
                                label = stringResource(app.gamenative.R.string.keyboard),
                                matchCount = categoryMatchCounts?.get(0)
                            )

                            // Mouse button
                            CategoryButton(
                                categoryIndex = 1,
                                label = stringResource(app.gamenative.R.string.mouse),
                                matchCount = categoryMatchCounts?.get(1)
                            )

                            // Gamepad button
                            CategoryButton(
                                categoryIndex = 2,
                                label = stringResource(app.gamenative.R.string.gamepad),
                                matchCount = categoryMatchCounts?.get(2)
                            )

                            // Extra button
                            CategoryButton(
                                categoryIndex = 3,
                                label = stringResource(app.gamenative.R.string.extra),
                                matchCount = categoryMatchCounts?.get(3)
                            )

                            // Clear Binding button - more compact for smaller screens
                            // Only empties the choice; the dialog stays open and the list is ready for a new key.
                            // The empty choice is applied with the check mark in the header.
                            if (selectedBindings.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            Log.d("ControllerBindingDialog", "Clearing chosen binding for $buttonName")
                                            selectedBindings.clear()
                                            comboMode = false
                                        },
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp), // Reduced padding for smaller screens
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp) // Slightly smaller icon
                                        )
                                        Text(
                                            text = stringResource(app.gamenative.R.string.clear_binding),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right column: Bindings list
                    Column(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (wowTabActive && onWowActionSelected != null) {
                            WowActionList(
                                query = searchQuery,
                                keys = wowKeys,
                                selectedId = currentWowActionId,
                                onPick = { action -> onWowActionSelected(action, wowKeys[action.command]?.combo) },
                                onCustomChat = if (onChatCommandSelected != null) ({ showCustomChat = true }) else null,
                                customChatSelected = currentWowActionId == null && !currentChatCommand.isNullOrBlank(),
                            )
                        } else if (filteredBindings.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(app.gamenative.R.string.no_bindings_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            filteredBindings.forEach { binding ->
                                val isNone = binding == Binding.NONE
                                BindingOption(
                                    binding = binding,
                                    isSelected = if (isNone) selectedBindings.isEmpty() else binding in selectedBindings,
                                    enabled = isNone || binding in selectedBindings || !comboMode || selectedBindings.size < BindingCombo.MAX_BINDINGS,
                                    onClick = {
                                        if (isNone) {
                                            selectedBindings.clear()
                                        } else if (binding in selectedBindings) {
                                            selectedBindings.remove(binding)
                                        } else if (!comboMode) {
                                            // Replace: the new key takes the place of the old one, no need to clear first.
                                            selectedBindings.clear()
                                            selectedBindings.add(binding)
                                        } else if (selectedBindings.size < BindingCombo.MAX_BINDINGS) {
                                            selectedBindings.add(binding)
                                        }
                                        Log.d("ControllerBindingDialog", "Binding toggled for $buttonName: ${binding.name}")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BindingOption(
    binding: Binding,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = binding.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else if (!enabled)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
