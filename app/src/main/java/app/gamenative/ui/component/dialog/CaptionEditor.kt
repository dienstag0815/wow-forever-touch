package app.gamenative.ui.component.dialog

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PrefManager
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.screen.xserver.editor.sheet.RecentList
import app.gamenative.wowaction.BindingCaption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Face of the compact emoji button (a smiley, written as escape so the source file stays plain). */
private const val SMILEY = "😀"

/**
 * Small button "Emoji wählen": opens a grid with about 40 game related emojis. Emojis come from the system font (no own
 * graphics); the keyboard's emojis work in the label field as well. [compact] shows just a smiley instead of the words.
 */
@Composable
internal fun EmojiPickerButton(onPick: (String) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) { Text(if (compact) SMILEY else stringResource(R.string.game_cap_choose)) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.game_cap_choose)) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(52.dp),
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(BindingCaption.EMOJI_CHOICES) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clickable {
                                    onPick(emoji)
                                    open = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, fontSize = 26.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.game_close)) } },
        )
    }
}

/**
 * The emojis of [BindingCaption.EMOJI_CHOICES] as a grid of 44 dp cells that wraps (no popup, no own scrolling), so it can
 * sit inside a sheet; a tap calls [onPick].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmojiGrid(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier.fillMaxWidth()) {
        for (emoji in BindingCaption.EMOJI_CHOICES) {
            EmojiCell(emoji, 44.dp, onPick)
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, size: Dp, onPick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clickable { onPick(emoji) },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 22.sp)
    }
}

private const val EMOJI_PREFS = "editor_sheet"
private const val EMOJI_RECENT_KEY = "recent_emojis"
private const val EMOJI_RECENT_COUNT = 6

private fun loadRecentEmojis(context: Context): RecentList = RecentList.parse(
    context.getSharedPreferences(EMOJI_PREFS, Context.MODE_PRIVATE).getString(EMOJI_RECENT_KEY, null),
    EMOJI_RECENT_COUNT,
)

private fun saveRecentEmojis(context: Context, recent: RecentList) {
    context.getSharedPreferences(EMOJI_PREFS, Context.MODE_PRIVATE).edit().putString(EMOJI_RECENT_KEY, recent.serialize()).apply()
}

/**
 * Field "Beschriftung (leer = automatisch)" with the preview of the automatic text and the emoji picker.
 *
 * The typed text is kept here (not read back from the element), so every keystroke shows at once, also on pages whose
 * composables are skipped when only the element changed; it starts again from [label] when [resetKey] changes (another
 * element, a template, undo). With [inlineEmoji] one row of the emojis used last shows while the field is in use, and
 * "Emoji" folds the full grid open (the keyboard goes away so the grid has room).
 *
 * @param label         own label (empty = automatic) at the time [resetKey] changed
 * @param autoPreview   what the field shows without own label (see [BindingCaption.forSlot]), e.g. "Space" or "Karte"
 * @param showEmojiMode also offer the global switch "Emoji statt Symbol" for WoW actions
 * @param inlineEmoji   the emoji choice opens below the field (for sheets) instead of in a popup
 * @param horizontalPadding side padding of the whole block (0 inside a sheet that has its own)
 * @param resetKey      anything that identifies what is edited; a change starts the field from [label] again
 * @param ignoredHint   a note under the field for a text that would not show on the element (null = none)
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun CaptionEditor(
    label: String,
    autoPreview: String,
    onLabelChange: (String) -> Unit,
    showEmojiMode: Boolean = true,
    modifier: Modifier = Modifier,
    inlineEmoji: Boolean = false,
    horizontalPadding: Dp = 16.dp,
    resetKey: Any? = null,
    ignoredHint: (String) -> String? = { null },
) {
    var text by remember(resetKey) { mutableStateOf(label) }
    var emojiMode by remember { mutableStateOf(BindingCaption.emojiMode) }
    var emojiOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var recent by remember { mutableStateOf(loadRecentEmojis(context).values) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val requester = remember { BringIntoViewRequester() }

    fun change(value: String) {
        text = value
        onLabelChange(value)
    }

    fun pickEmoji(emoji: String) {
        change(text + emoji)
        val list = loadRecentEmojis(context)
        list.use(emoji)
        saveRecentEmojis(context, list)
        recent = list.values
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp)
            .bringIntoViewRequester(requester),
    ) {
        NoExtractOutlinedTextField(
            value = text,
            onValueChange = { change(it) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    focused = state.isFocused
                    // The keyboard slides up and shrinks the sheet: bring the whole block back into view afterwards.
                    if (state.isFocused) {
                        scope.launch {
                            delay(350)
                            requester.bringIntoView()
                        }
                    }
                },
            label = { Text(stringResource(R.string.game_cap_label)) },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (text.isBlank()) stringResource(R.string.game_cap_auto, autoPreview) else stringResource(R.string.game_cap_own),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (inlineEmoji) {
                TextButton(
                    onClick = {
                        emojiOpen = !emojiOpen
                        if (emojiOpen) {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (emojiOpen) stringResource(R.string.game_cap_collapse) else "Emoji")
                }
            } else {
                EmojiPickerButton(onPick = { change(text + it) })
            }
        }
        ignoredHint(text)?.let { hint ->
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (inlineEmoji && (focused || emojiOpen)) {
            // One line with the emojis used last (the first choices until there are some).
            val line = (recent + BindingCaption.EMOJI_CHOICES.filter { it !in recent }).take(EMOJI_RECENT_COUNT)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (recent.isEmpty()) R.string.game_cap_suggestion else R.string.game_cap_recent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp),
                )
                for (emoji in line) EmojiCell(emoji, 40.dp) { pickEmoji(it) }
            }
        }
        if (inlineEmoji && emojiOpen) {
            EmojiGrid(onPick = { pickEmoji(it) })
        }
        if (showEmojiMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.game_cap_wow_emoji),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = emojiMode,
                    onCheckedChange = {
                        emojiMode = it
                        BindingCaption.emojiMode = it
                        PrefManager.wowEmojiSymbols = it
                    },
                )
            }
        }
    }
}
