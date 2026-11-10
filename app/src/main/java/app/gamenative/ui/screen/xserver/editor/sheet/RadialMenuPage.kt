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
import androidx.compose.runtime.LaunchedEffect
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
import app.gamenative.ui.component.dialog.wowActionSummary
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.ui.touchstyle.RadialOpen
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.wowaction.ActionBarTemplates
import app.gamenative.wowaction.StandardNames
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowActionSource
import app.gamenative.wowicons.BridgeData
import app.gamenative.wowicons.BridgeLocator
import app.gamenative.wowicons.WowIcons
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.RadialMenu
import java.io.File
import androidx.compose.ui.res.stringResource
import app.gamenative.R

/**
 * Page "Kreismenü" of a radial button: number of fields, templates with WoW actions, and for each field its assignment,
 * label (with emojis) and an optional game picture area. Every change is stored in the profile's menu of this button at once.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RadialMenuPage(state: ElementSheetState, element: ControlElement, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val context = LocalContext.current
    val profile = state.view.profile ?: return
    val hasMenu = element.radialMenuId?.let { profile.findRadialMenu(it) } != null
    if (!hasMenu) {
        // A button that just got the radial menu key gets its own menu (number keys 1 to 6) the first time the page opens.
        LaunchedEffect(element) { state.change { state.ensureRadialMenu(element) } }
        return
    }
    val menu = profile.getRadialMenu(element.radialMenuId)
    val menuId = menu.id

    fun applyTemplate(name: String, commands: List<String>) {
        val keys = runCatching {
            WowActionSource.combos(WowActionSource.resolveFor(File(GamePath.load(context))))
        }.getOrNull() ?: emptyMap()
        state.change { profile.putRadialMenu(WowActionCatalog.buildMenu(menuId, name, commands, keys)) }
    }

    // A template replaces every field; ask first if the player has set fields by hand (undo is available as well).
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    fun ask(name: String, action: () -> Unit) {
        if (ActionBarTemplates.isCustomized(profile.getRadialMenu(element.radialMenuId).slots)) confirm = name to action else action()
    }
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
    val selected = state.selectedField(element).takeIf { it in menu.slots.indices } ?: -1

    fun applyActionBar(bar: Int) {
        val keys = runCatching {
            WowActionSource.combos(WowActionSource.resolveFor(File(GamePath.load(context))))
        }.getOrNull() ?: emptyMap()
        val applied = ActionBarTemplates.buildMenu(menuId, bar, keys, actionBarData(context))
        state.change { profile.putRadialMenu(applied.menu) }
        SnackbarManager.show(applied.message)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHint(stringResource(R.string.editor_radial_hint))

        SheetSlider(
            title = stringResource(R.string.editor_fields),
            valueText = "${menu.slots.size}",
            value = menu.slots.size.toFloat(),
            range = RadialMenu.MIN_SLOTS.toFloat()..RadialMenu.MAX_SLOTS.toFloat(),
            steps = RadialMenu.MAX_SLOTS - RadialMenu.MIN_SLOTS - 1,
            onChange = { v -> state.live { resizeMenu(profile, profile.getRadialMenu(element.radialMenuId), Math.round(v)) } },
            onFinished = { state.flush() },
        )

        SheetSection(stringResource(R.string.editor_opens_towards))
        val opens = RadialOpen.values().toList()
        SheetChips(
            options = opens.map { it.label },
            selectedIndex = RadialOpen.fromId(element.radialDirection).ordinal,
            onSelect = { state.change { element.setRadialDirection(opens[it].id) } },
        )
        SheetHint(stringResource(R.string.editor_radial_direction_hint))

        SheetSection(stringResource(R.string.editor_preview))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FieldPreview(
                cells = menu.slots.mapIndexed { index, slot -> fieldCellOf(slot, index + 1) },
                angles = menuAngles(menu.slots.size),
                radiusFractions = menuRadiusFractions(menu.slots.size),
                selected = selected,
                onSelect = { index ->
                    state.selectField(element, index)
                    state.open(SheetRoute.picker(PickTarget.RadialSlot(index)))
                },
            )
        }
        SheetHint(stringResource(R.string.editor_field_tap_hint))

        SheetSection(
            if (selected >= 0) stringResource(R.string.editor_field_n, selected + 1) else stringResource(R.string.editor_fields),
        )
        if (selected >= 0) {
            TextButton(onClick = { state.selectField(element, -1) }, modifier = Modifier.heightIn(min = SheetTarget)) {
                Text(stringResource(R.string.editor_show_all_fields))
            }
        }
        menu.slots.forEachIndexed { index, slot ->
            if (selected >= 0 && index != selected) return@forEachIndexed
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val key = if (slot.bindingCombo.isEmpty) stringResource(R.string.editor_not_assigned) else slot.bindingCombo.toString()
                SheetNavRow(
                    title = stringResource(R.string.editor_field_n, index + 1),
                    value = wowActionSummary(slot.wowAction, key, slot.chatCommand),
                    onClick = { state.open(SheetRoute.picker(PickTarget.RadialSlot(index))) },
                )
                CaptionEditor(
                    label = StandardNames.slotLabel(slot.label, slot.wowAction),
                    autoPreview = WowActionCatalog.displayLabel(null, slot.wowAction, slot.bindingCombo).ifBlank { "–" },
                    onLabelChange = { text -> state.live { slot.setLabel(text) } },
                    showEmojiMode = false,
                    inlineEmoji = true,
                    horizontalPadding = 0.dp,
                    resetKey = slot to state.session.externalRevision,
                )
            }
        }

        SheetGroup(title = stringResource(R.string.editor_templates), summary = stringResource(R.string.editor_fill_fields)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetSection(stringResource(R.string.editor_templates))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { ask(StandardNames.displayLabel("Spielmenüs")) { applyTemplate("Spielmenüs", WowActionCatalog.GAME_MENU_COMMANDS) } },
                        label = { Text(StandardNames.displayLabel("Spielmenüs")) },
                        modifier = Modifier.heightIn(min = SheetTarget),
                    )
                    AssistChip(
                        onClick = { ask(StandardNames.displayLabel("WoW-Menü komplett")) { applyTemplate("WoW-Menü komplett", WowActionCatalog.WOW_MENU_COMMANDS) } },
                        label = { Text(StandardNames.displayLabel("WoW-Menü komplett")) },
                        modifier = Modifier.heightIn(min = SheetTarget),
                    )
                    AssistChip(
                        onClick = { ask(StandardNames.displayLabel("WoW-Menü komplett 2")) { applyTemplate("WoW-Menü komplett 2", WowActionCatalog.WOW_MENU_2_COMMANDS) } },
                        label = { Text(StandardNames.displayLabel("WoW-Menü komplett 2")) },
                        modifier = Modifier.heightIn(min = SheetTarget),
                    )
                    AssistChip(
                        onClick = { ask(StandardNames.displayLabel("WoW-Menü Rest")) { applyTemplate("WoW-Menü Rest", WowActionCatalog.WOW_MENU_REST_COMMANDS) } },
                        label = { Text(StandardNames.displayLabel("WoW-Menü Rest")) },
                        modifier = Modifier.heightIn(min = SheetTarget),
                    )
                    AssistChip(
                        onClick = { ask(StandardNames.displayLabel("Angriffe")) { applyTemplate("Angriffe", WowActionCatalog.ATTACK_COMMANDS) } },
                        label = { Text(StandardNames.displayLabel("Angriffe")) },
                        modifier = Modifier.heightIn(min = SheetTarget),
                    )
                    AssistChip(
                        onClick = { ask(StandardNames.displayLabel("WoW Emotes")) { applyTemplate("WoW Emotes", WowActionCatalog.EMOTE_COMMANDS) } },
                        label = { Text(StandardNames.displayLabel("WoW Emotes")) },
                        modifier = Modifier.heightIn(min = SheetTarget),
                    )
                }
                SheetHint(stringResource(R.string.editor_template_help))

                // Templates of the addon TouchForever (action bars, chat, minimap and quests on/off).
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { ask(StandardNames.displayLabel("Oberfläche")) { applyTemplate("Oberfläche", WowActionCatalog.INTERFACE_COMMANDS) } },
                        label = { Text(StandardNames.displayLabel("Oberfläche")) },
                        modifier = Modifier.heightIn(min = SheetTarget),
                    )
                    AssistChip(
                        onClick = {
                            val keys = runCatching {
                                WowActionSource.combos(WowActionSource.resolveFor(File(GamePath.load(context))))
                            }.getOrNull() ?: emptyMap()
                            state.change { profile.putRadialMenu(WowActionCatalog.buildChatMenu(menuId, "Chat", keys)) }
                        },
                        label = { Text("Chat") },
                        modifier = Modifier.heightIn(min = SheetTarget),
                    )
                }
                SheetHint(stringResource(R.string.editor_interface_hint))


                SheetSection(stringResource(R.string.editor_action_bar))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (bar in 1..ActionBarTemplates.BAR_COUNT) {
                        AssistChip(
                            onClick = { ask(ActionBarTemplates.menuName(bar)) { applyActionBar(bar) } },
                            label = { Text(stringResource(R.string.editor_bar_n, bar)) },
                            modifier = Modifier.heightIn(min = SheetTarget),
                        )
                    }
                }
                SheetHint(stringResource(R.string.editor_radial_bar_hint))
            }
        }
    }
}

/** Gives [menu] exactly [count] fields: fields that exist stay, new ones start with the number key that continues the sequence. */
internal fun resizeMenu(profile: ControlsProfile, menu: RadialMenu, count: Int) {
    val target = count.coerceIn(RadialMenu.MIN_SLOTS, RadialMenu.MAX_SLOTS)
    if (target == menu.slots.size) return
    val next = RadialMenu().apply {
        id = menu.id
        name = menu.name
    }
    for (index in 0 until target) {
        val slot = menu.slots.getOrNull(index)
            ?: RadialMenu.Slot("", RadialMenu.prefillForNewField(index, BindingCombo.none()))
        next.addSlot(slot)
    }
    profile.putRadialMenu(next)
}

/** Addon data for the templates: what the running game has, else the file in the game folder; null if there is none. */
internal fun actionBarData(context: android.content.Context): BridgeData? =
    WowIcons.bridgeData() ?: runCatching {
        BridgeLocator.find(File(GamePath.load(context)))?.let { BridgeLocator.read(it) }
    }.getOrNull()
