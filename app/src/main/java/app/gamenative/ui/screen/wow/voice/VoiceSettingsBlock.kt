package app.gamenative.ui.screen.wow.voice

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.screen.wow.SettingActionRow
import app.gamenative.ui.screen.wow.SettingNote
import app.gamenative.ui.screen.wow.SettingValueRow
import app.gamenative.ui.theme.WowGold
import app.gamenative.ui.theme.WowSubtle
import app.gamenative.ui.util.SnackbarManager

/** Zeile "Erkennungssprache": gibt es auch im Spiel (Seitenmenü > Spiel), steht im Launcher nur im zugeklappten Spiel-Bereich. */
@Composable
internal fun VoiceLanguageRow(enabled: Boolean) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(VoiceSettings.language(context)) }
    SettingValueRow(
        title = stringResource(R.string.game_qm_row_voice_language),
        description = stringResource(R.string.game_voice_set_desc),
        value = language.label,
        enabled = enabled,
    ) {
        val next = VoiceLanguage.entries[(language.ordinal + 1) % VoiceLanguage.entries.size]
        VoiceSettings.saveLanguage(context, next)
        language = next
    }
}

/**
 * Zeilen der Spracheingabe auf der Einstellungsseite: Umgang mit Umlauten und ein Feld für eigene Wörter (Gilden- und
 * Charakternamen, eins pro Zeile oder mit Komma getrennt), dazu das Erkennungsprotokoll. Die Erkennungssprache steht in
 * [VoiceLanguageRow].
 */
@Composable
internal fun VoiceSettingsBlock(enabled: Boolean) {
    val context = LocalContext.current
    var umlauts by remember { mutableStateOf(VoiceSettings.umlautMode(context)) }
    var words by remember { mutableStateOf(VoiceSettings.customWordsText(context)) }
    var logOpen by remember { mutableStateOf(false) }
    var logEntries by remember { mutableStateOf(emptyList<VoiceLogEntry>()) }

    Column {
        SettingValueRow(
            title = stringResource(R.string.game_voice_set_umlauts),
            description = stringResource(R.string.game_voice_set_umlauts_desc),
            value = stringResource(umlauts.labelRes),
            enabled = enabled,
        ) {
            val next = UmlautMode.entries[(umlauts.ordinal + 1) % UmlautMode.entries.size]
            VoiceSettings.saveUmlautMode(context, next)
            umlauts = next
        }
        NoExtractOutlinedTextField(
            value = words,
            onValueChange = {
                words = it
                VoiceSettings.saveCustomWordsText(context, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            enabled = enabled,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            label = { Text(stringResource(R.string.game_voice_set_words), fontSize = 12.sp, color = WowSubtle) },
            singleLine = false,
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WowGold,
                unfocusedBorderColor = Color(0xFF2A3C54),
                cursorColor = WowGold,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
        )
        // Erkennungsprotokoll: die letzten 30 Paare "gehört -> korrigiert", nur auf diesem Gerät, um falsch gehörte Wörter zu melden.
        SettingActionRow(
            title = stringResource(R.string.game_voice_set_log),
            description = stringResource(R.string.game_voice_set_log_desc),
            actionText = stringResource(if (logOpen) R.string.game_voice_set_hide else R.string.game_voice_set_show),
        ) {
            logEntries = VoiceLog.load(context)
            logOpen = !logOpen
        }
        if (logOpen) {
            if (logEntries.isEmpty()) {
                SettingNote(stringResource(R.string.game_voice_set_empty))
            } else {
                for (entry in logEntries) {
                    Text(
                        VoiceLog.line(entry),
                        fontSize = 12.sp,
                        color = WowSubtle,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                SettingActionRow(title = stringResource(R.string.game_voice_set_copy), actionText = stringResource(R.string.game_voice_set_copy_action)) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.game_voice_set_clip), VoiceLog.format(logEntries)))
                    SnackbarManager.show(context.getString(R.string.game_voice_set_copied))
                }
                SettingActionRow(title = stringResource(R.string.game_voice_set_clear), actionText = stringResource(R.string.game_voice_set_clear_action)) {
                    VoiceLog.clear(context)
                    logEntries = emptyList()
                }
            }
        }
    }
}
