package app.gamenative.ui.component.dialog

import androidx.compose.ui.res.stringResource
import app.gamenative.ui.components.AppText
import app.gamenative.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.wowaction.ResolvedKey
import app.gamenative.wowaction.WowAction
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowActionGroup
import app.gamenative.wowaction.WowActionResolver
import app.gamenative.wowaction.WowActionSource
import app.gamenative.wowaction.WowKeySource
import app.gamenative.wowaction.WowSymbols
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Current keys of the WoW actions: first the standard keys, then (read off the main thread) the player's own bindings from
 * the game folder's WTF files, if they exist.
 */
@Composable
internal fun rememberWowKeys(enabled: Boolean = true): State<Map<String, ResolvedKey>> {
    val context = LocalContext.current
    return produceState(initialValue = WowActionResolver.resolveAll(null), enabled) {
        if (enabled) {
            value = withContext(Dispatchers.IO) {
                try {
                    WowActionSource.resolveFor(File(GamePath.load(context)))
                } catch (e: Exception) {
                    WowActionResolver.resolveAll(null)
                }
            }
        }
    }
}

/** "WoW: Karte – M" for a button or field that stores a WoW action, else just the key text. */
internal fun wowActionSummary(actionId: String?, keyText: String, chatCommand: String? = null): String {
    val action = WowActionCatalog.find(actionId)
    if (chatCommand != null) {
        // Chat command: the text typed into the WoW chat instead of a key.
        return if (action != null) "WoW: ${action.name} – $chatCommand" else "Chat: $chatCommand"
    }
    if (action == null) return keyText
    return "WoW: ${action.name} – $keyText"
}

/** Own vector symbol of a WoW action (see WowSymbols), drawn in [color]. */
@Composable
internal fun WowSymbolIcon(symbol: String, color: Color, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val argb = color.toArgb()
    Canvas(modifier = modifier.size(size)) {
        drawIntoCanvas { canvas ->
            WowSymbols.draw(canvas.nativeCanvas, symbol, this.size.width / 2f, this.size.height / 2f, this.size.minDimension / 2f * 0.9f, argb)
        }
    }
}

/**
 * List of the WoW actions grouped as in the editor (menus, action bar, target/combat, movement, other, emotes),
 * filtered by [query]. Each row shows the symbol, the name and the key currently set in WoW (emotes: the chat command).
 * With [onCustomChat] the list starts with the row "Eigener Chat-Befehl ...".
 */
@Composable
internal fun WowActionList(
    query: String,
    keys: Map<String, ResolvedKey>,
    selectedId: String?,
    onPick: (WowAction) -> Unit,
    onCustomChat: (() -> Unit)? = null,
    customChatSelected: Boolean = false,
) {
    val matches = WowActionCatalog.search(query)
    if (onCustomChat != null) {
        CustomChatRow(customChatSelected, onCustomChat)
    }
    if (matches.isEmpty()) {
        Text(
            text = stringResource(R.string.game_picker_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    val hasFile = keys.values.any { it.source == WowKeySource.WOW_FILE }
    Text(
        text = if (hasFile) {
            stringResource(R.string.game_picker_keys_file)
        } else {
            stringResource(R.string.game_picker_keys_default)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    for (group in WowActionGroup.values()) {
        val inGroup = matches.filter { it.group == group }
        if (inGroup.isEmpty()) continue
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        for (action in inGroup) {
            WowActionRow(action, keys[action.command], action.command == selectedId, onPick)
        }
    }
}

/**
 * Dialog for a free chat command: any slash command or chat text (e.g. "/follow", "/s Hallo") plus an own label.
 * [onConfirm] gets (label, command); the command is never empty.
 */
@Composable
internal fun CustomChatDialog(
    initialLabel: String,
    initialCommand: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var command by remember { mutableStateOf(initialCommand) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_picker_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NoExtractOutlinedTextField(
                    value = command,
                    onValueChange = { command = it.take(255) },
                    label = { Text(stringResource(R.string.game_picker_command)) },
                    placeholder = { Text("/follow") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NoExtractOutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(14) },
                    label = { Text(stringResource(R.string.game_picker_label)) },
                    placeholder = { Text(stringResource(R.string.game_picker_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.game_picker_custom_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = command.isNotBlank(),
                onClick = { onConfirm(label.trim(), command.trim()) },
            ) { Text(stringResource(R.string.game_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_cancel)) } },
    )
}

/** Row "Eigener Chat-Befehl ...": opens the dialog for a free slash command or chat text. */
@Composable
private fun CustomChatRow(isSelected: Boolean, onClick: () -> Unit) {
    val container = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = container,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WowSymbolIcon(WowActionCatalog.CUSTOM_CHAT_SYMBOL, content)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.game_picker_custom_row), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = content)
                Text(
                    text = stringResource(R.string.game_picker_custom_example),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun WowActionRow(
    action: WowAction,
    key: ResolvedKey?,
    isSelected: Boolean,
    onPick: (WowAction) -> Unit,
) {
    val container = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(action) },
        color = container,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WowSymbolIcon(action.symbol, content)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = action.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = content)
                if (AppText.isGerman()) {
                    Text(text = action.nameEn, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = action.chatCommand ?: WowActionResolver.keyText(key),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (key == null) content.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                )
                if (action.chatCommand == null && key != null && key.source == WowKeySource.DEFAULT && !action.certain) {
                    Text(text = stringResource(R.string.game_picker_unchecked), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.6f))
                }
            }
        }
    }
}
