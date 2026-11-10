package app.gamenative.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.screen.xserver.editor.sheet.menuAngles
import app.gamenative.ui.touchstyle.RadialArc
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import app.gamenative.wowaction.BindingCaption
import app.gamenative.wowaction.WowActionCatalog
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.RadialMenu
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadialMenuSettingsContent(
    profile: ControlsProfile,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    // Menu of a radial button (ControlElement.radialMenuId); null = the profile's default menu.
    menuId: String? = null,
    // false: only change the menu in memory (the caller saves the profile together with its own changes).
    persist: Boolean = true,
) {
    val menu = remember(profile, menuId) { profile.getRadialMenu(menuId) }
    val initialSlots = remember(menu) { menu.slots.take(RadialMenu.MAX_SLOTS) }
    var slotCount by remember {
        mutableIntStateOf(initialSlots.size.coerceIn(RadialMenu.MIN_SLOTS, RadialMenu.MAX_SLOTS))
    }
    val labels = remember(menu) {
        mutableStateListOf<String>().apply {
            repeat(RadialMenu.MAX_SLOTS) { index ->
                add(initialSlots.getOrNull(index)?.label.orEmpty())
            }
        }
    }
    val bindings = remember(menu) {
        mutableStateListOf<BindingCombo>().apply {
            repeat(RadialMenu.MAX_SLOTS) { index ->
                add(initialSlots.getOrNull(index)?.bindingCombo ?: BindingCombo.none())
            }
        }
    }
    // WoW action behind a field (key follows the player's WoW bindings), null = plain key.
    val wowActions = remember(menu) {
        mutableStateListOf<String?>().apply {
            repeat(RadialMenu.MAX_SLOTS) { index -> add(initialSlots.getOrNull(index)?.wowAction) }
        }
    }
    // Chat text behind a field (typed into the WoW chat, e.g. "/dance"), null = key field.
    val chatCommands = remember(menu) {
        mutableStateListOf<String?>().apply {
            repeat(RadialMenu.MAX_SLOTS) { index -> add(initialSlots.getOrNull(index)?.chatCommand) }
        }
    }
    val wowKeys by rememberWowKeys(true)
    var bindingSlotToEdit by remember { mutableStateOf<Int?>(null) }
    // Own menu of a radial button (ids "button-N") vs. the profile's default menu (gestures and controller).
    val isButtonMenu = menu.id != RadialMenu.DEFAULT_ID

    fun updateSlotCount(nextCount: Int) {
        val next = nextCount.coerceIn(RadialMenu.MIN_SLOTS, RadialMenu.MAX_SLOTS)
        // Newly added fields get the number keys that continue the sequence (7, 8, 9, 0, -, =)
        // instead of staying empty.
        for (index in slotCount until next) {
            if (labels[index].isBlank()) {
                bindings[index] = RadialMenu.prefillForNewField(index, bindings[index])
            }
        }
        slotCount = next
    }

    fun applyPreset(preset: List<Pair<String, Binding>>) {
        slotCount = preset.size.coerceIn(RadialMenu.MIN_SLOTS, RadialMenu.MAX_SLOTS)
        for (index in 0 until RadialMenu.MAX_SLOTS) {
            labels[index] = preset.getOrNull(index)?.first.orEmpty()
            bindings[index] = BindingCombo.of(preset.getOrNull(index)?.second)
            wowActions[index] = null
            chatCommands[index] = null
        }
    }

    // Template with WoW actions: the fields keep the action and the key WoW currently has for it.
    fun applyWowTemplate(commands: List<String>) {
        val actions = commands.mapNotNull { WowActionCatalog.find(it) }
        slotCount = actions.size.coerceIn(RadialMenu.MIN_SLOTS, RadialMenu.MAX_SLOTS)
        for (index in 0 until RadialMenu.MAX_SLOTS) {
            val action = actions.getOrNull(index)
            labels[index] = action?.wheelLabel.orEmpty()
            bindings[index] = action?.let { wowKeys[it.command]?.combo } ?: BindingCombo.none()
            wowActions[index] = action?.command
            chatCommands[index] = action?.chatCommand
        }
    }

    fun saveMenu() {
        val nextMenu = RadialMenu().apply {
            id = menu.id
            name = menu.name
        }
        for (index in 0 until slotCount) {
            nextMenu.addSlot(RadialMenu.Slot(labels[index], bindings[index], wowActions[index], chatCommands[index]))
        }
        profile.putRadialMenu(nextMenu)
        if (persist) profile.save()
        onSave()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isButtonMenu) R.string.radial_button_edit_menu else R.string.radial_menu_settings_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    IconButton(onClick = ::saveMenu) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(
                    if (isButtonMenu) R.string.radial_button_menu_hint else R.string.radial_menu_default_hint,
                ),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(text = stringResource(R.string.radial_menu_slot_count))
            SlotCountSetting(
                slotCount = slotCount,
                onSlotCountChange = ::updateSlotCount,
            )

            SectionHeader(text = stringResource(R.string.quick_presets))
            PresetButtons(
                onGameMenusPreset = { applyWowTemplate(WowActionCatalog.GAME_MENU_COMMANDS) },
                onAttacksPreset = { applyWowTemplate(WowActionCatalog.ATTACK_COMMANDS) },
                onEmotesPreset = { applyWowTemplate(WowActionCatalog.EMOTE_COMMANDS) },
                onCommonPreset = {
                    applyPreset(
                        listOf(
                            "Inventory" to Binding.KEY_I,
                            "Map" to Binding.KEY_M,
                            "Journal" to Binding.KEY_J,
                            "Quick Save" to Binding.KEY_F5,
                            "Quick Load" to Binding.KEY_F9,
                            "Escape" to Binding.KEY_ESC,
                            "Tab" to Binding.KEY_TAB,
                            "Keyboard" to Binding.SHOW_KEYBOARD,
                        ),
                    )
                },
                onWeaponsPreset = {
                    applyPreset((1..8).map { "Weapon $it" to Binding.valueOf("KEY_$it") })
                },
            )

            SectionHeader(text = stringResource(R.string.radial_menu_preview_title))
            Text(
                text = stringResource(R.string.radial_menu_preview_hint),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RadialMenuPreview(
                slotCount = slotCount,
                labels = labels,
                bindings = bindings,
                wowActions = wowActions,
                chatCommands = chatCommands,
            )

            SectionHeader(text = stringResource(R.string.radial_menu_slots))
            for (index in 0 until slotCount) {
                SlotSetting(
                    index = index,
                    label = labels[index],
                    binding = bindings[index],
                    wowActionId = wowActions[index],
                    chatCommand = chatCommands[index],
                    onLabelChange = { labels[index] = it },
                    onEditBinding = { bindingSlotToEdit = index },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    bindingSlotToEdit?.let { slotIndex ->
        ControllerBindingDialog(
            buttonName = stringResource(R.string.radial_menu_slot_binding, slotIndex + 1),
            currentBinding = bindings[slotIndex].primaryBinding.takeIf { it != Binding.NONE },
            currentBindingCombo = bindings[slotIndex],
            onDismiss = { bindingSlotToEdit = null },
            onBindingSelected = {},
            currentWowActionId = wowActions[slotIndex],
            onWowActionSelected = { action, combo ->
                // Name the field after the action unless the player wrote an own label.
                val previous = WowActionCatalog.find(wowActions[slotIndex])
                if (labels[slotIndex].isBlank() || labels[slotIndex] == previous?.wheelLabel) {
                    labels[slotIndex] = action.wheelLabel
                }
                wowActions[slotIndex] = action.command
                chatCommands[slotIndex] = action.chatCommand
                bindings[slotIndex] = if (action.chatCommand != null) BindingCombo.none() else combo ?: BindingCombo.none()
                bindingSlotToEdit = null
            },
            currentChatCommand = chatCommands[slotIndex].takeIf { wowActions[slotIndex] == null },
            currentChatLabel = labels[slotIndex],
            onChatCommandSelected = { label, command ->
                wowActions[slotIndex] = null
                chatCommands[slotIndex] = command
                bindings[slotIndex] = BindingCombo.none()
                labels[slotIndex] = label.ifBlank { command.take(14) }
                bindingSlotToEdit = null
            },
            onBindingComboSelected = { bindingCombo ->
                wowActions[slotIndex] = null
                chatCommands[slotIndex] = null
                bindings[slotIndex] = if (bindingCombo?.bindings?.contains(Binding.OPEN_RADIAL_MENU) == true) {
                    BindingCombo.none()
                } else {
                    bindingCombo ?: BindingCombo.none()
                }
                bindingSlotToEdit = null
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PresetButtons(
    onGameMenusPreset: () -> Unit,
    onAttacksPreset: () -> Unit,
    onEmotesPreset: () -> Unit,
    onCommonPreset: () -> Unit,
    onWeaponsPreset: () -> Unit,
) {
    val buttonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onGameMenusPreset,
            modifier = Modifier.weight(1f),
            colors = buttonColors,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.game_radial_game_menus))
        }
        OutlinedButton(
            onClick = onAttacksPreset,
            modifier = Modifier.weight(1f),
            colors = buttonColors,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.game_radial_attacks))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onEmotesPreset,
            modifier = Modifier.weight(1f),
            colors = buttonColors,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.game_radial_emotes))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCommonPreset,
            modifier = Modifier.weight(1f),
            colors = buttonColors,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.radial_menu_preset_common))
        }
        OutlinedButton(
            onClick = onWeaponsPreset,
            modifier = Modifier.weight(1f),
            colors = buttonColors,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.radial_menu_preset_weapons))
        }
    }
}

@Composable
private fun SlotCountSetting(
    slotCount: Int,
    onSlotCountChange: (Int) -> Unit,
) {
    fun changeSlotCount(delta: Int) {
        onSlotCountChange((slotCount + delta).coerceIn(RadialMenu.MIN_SLOTS, RadialMenu.MAX_SLOTS))
    }

    val dpadSlotCountModifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> {
                changeSlotCount(-1)
                true
            }
            Key.DirectionRight -> {
                changeSlotCount(1)
                true
            }
            else -> false
        }
    }

    SettingsMenuLink(
        modifier = dpadSlotCountModifier,
        colors = settingsTileColors(),
        title = { Text(stringResource(R.string.radial_menu_slot_count)) },
        subtitle = { Text(stringResource(R.string.radial_menu_slot_count_subtitle, slotCount)) },
        onClick = {},
    )
    Row(
        modifier = dpadSlotCountModifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = { changeSlotCount(-1) },
            enabled = slotCount > RadialMenu.MIN_SLOTS,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(R.string.radial_menu_decrease_slots),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = slotCount.toFloat(),
            onValueChange = { onSlotCountChange(it.roundToInt()) },
            valueRange = RadialMenu.MIN_SLOTS.toFloat()..RadialMenu.MAX_SLOTS.toFloat(),
            steps = RadialMenu.MAX_SLOTS - RadialMenu.MIN_SLOTS - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                activeTickColor = MaterialTheme.colorScheme.primary,
                inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = slotCount.toString(),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(
            onClick = { changeSlotCount(1) },
            enabled = slotCount < RadialMenu.MAX_SLOTS,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.radial_menu_increase_slots),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SlotSetting(
    index: Int,
    label: String,
    binding: BindingCombo,
    wowActionId: String?,
    chatCommand: String?,
    onLabelChange: (String) -> Unit,
    onEditBinding: () -> Unit,
) {
    val keyText = if (binding.isEmpty) stringResource(R.string.not_set) else binding.toString()
    val bindingText = wowActionSummary(wowActionId, keyText, chatCommand)
    SettingsMenuLink(
        colors = if (index % 2 == 0) settingsTileColorsAlt() else settingsTileColors(),
        title = {
            Text(
                stringResource(
                    if (index == 0) R.string.radial_menu_slot_title_top else R.string.radial_menu_slot_title,
                    index + 1,
                ),
            )
        },
        subtitle = { Text(stringResource(R.string.radial_menu_slot_binding_summary, bindingText)) },
        action = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoExtractOutlinedTextField(
                    value = label,
                    onValueChange = onLabelChange,
                    modifier = Modifier.width(128.dp),
                    placeholder = { Text(stringResource(R.string.radial_menu_slot_label_hint)) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true,
                )
                // Emoji as label (system font); empty label = the wheel names the field itself.
                EmojiPickerButton(onPick = { onLabelChange(label + it) }, compact = true)
            }
        },
        onClick = onEditBinding,
    )
}

/**
 * Small wheel preview in the layout of the game: an arc of 270 degrees open at the bottom, field 1 at its left end, the other fields
 * clockwise (with more than eight fields the preview keeps the plain circle, a second ring does not fit this size). Fields without
 * keys show a dash.
 */
@Composable
private fun RadialMenuPreview(
    slotCount: Int,
    labels: List<String>,
    bindings: List<BindingCombo>,
    wowActions: List<String?>,
    chatCommands: List<String?>,
) {
    val radius = 92f
    val arcAngles = if (slotCount <= RadialArc.FIRST_RING_CAP) menuAngles(slotCount) else null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        for (index in 0 until slotCount) {
            val angle = Math.toRadians(arcAngles?.getOrNull(index)?.toDouble() ?: (-90.0 + index * 360.0 / slotCount))
            val binding = bindings[index]
            val wowAction = WowActionCatalog.find(wowActions[index])
            val chat = chatCommands[index]
            val text = labels[index].ifBlank {
                wowAction?.let { BindingCaption.emojiForAction(it.command) ?: it.wheelLabel }
                    ?: BindingCaption.textForSlot("", null, chat, binding).ifEmpty { "–" }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = (cos(angle) * radius).toFloat().dp,
                        y = (sin(angle) * radius).toFloat().dp,
                    )
                    .width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (wowAction != null) {
                    WowSymbolIcon(wowAction.symbol, MaterialTheme.colorScheme.primary, size = 22.dp)
                } else if (chat != null) {
                    WowSymbolIcon(WowActionCatalog.CUSTOM_CHAT_SYMBOL, MaterialTheme.colorScheme.primary, size = 22.dp)
                } else {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = if (binding.isEmpty && chat == null && labels[index].isBlank()) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
