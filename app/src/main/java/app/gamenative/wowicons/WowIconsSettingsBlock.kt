package app.gamenative.wowicons

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.components.AppText
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.gamenative.cooldown.CooldownService
import app.gamenative.ui.screen.wow.SettingActionRow
import app.gamenative.ui.screen.wow.SettingNote
import app.gamenative.ui.screen.wow.SettingSwitchRow
import java.util.Locale

/**
 * Zeilen "Abklingzeiten anzeigen" und "Auch kurze globale Abklingzeit": gibt es auch im Spiel (Seitenmenü > Spiel), steht im Launcher
 * nur im zugeklappten Spiel-Bereich der Seite Steuerung. Wirkt sofort.
 */
@Composable
internal fun WowCooldownSettingsRows(enabled: Boolean) {
    val context = LocalContext.current
    var cooldowns by remember {
        CooldownService.init(context)
        mutableStateOf(CooldownService.isEnabled)
    }
    var globalCooldown by remember { mutableStateOf(CooldownService.showGlobal) }

    Column {
        SettingSwitchRow(
            title = stringResource(R.string.game_icons_cooldowns),
            description = stringResource(R.string.game_icons_cooldowns_desc),
            checked = cooldowns,
            enabled = enabled,
        ) { next ->
            cooldowns = next
            CooldownService.setEnabled(context, next)
        }
        if (cooldowns) {
            SettingSwitchRow(
                title = stringResource(R.string.game_icons_gcd),
                checked = globalCooldown,
                enabled = enabled,
            ) { next ->
                globalCooldown = next
                CooldownService.setShowGlobal(context, next)
            }
            SettingNote(
                stringResource(R.string.game_icons_strip_note),
            )
        }
    }
}

/**
 * Zeilen "Echte Symbole aus WoW" und "Gespeicherte Symbole" auf der Einstellungsseite: echte Zauber- und Gegenstandssymbole auf
 * Knöpfen, die einen Platz der Aktionsleiste speichern. Zeigt, was auf dem Gerät gespeichert ist, und lässt es leeren.
 */
@Composable
internal fun WowIconsSettingsBlock(enabled: Boolean) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(WowIcons.isEnabled(context)) }
    var stats by remember { mutableStateOf(WowIcons.cacheStats(context)) }

    Column {
        SettingSwitchRow(
            title = stringResource(R.string.game_icons_real),
            description = stringResource(R.string.game_icons_real_desc),
            checked = on,
            enabled = enabled,
        ) { next ->
            on = next
            WowIcons.setEnabled(context, next)
        }
        if (on) {
            SettingActionRow(
                title = stringResource(R.string.game_icons_saved),
                description = stringResource(R.string.game_icons_saved_stats, stats.first, formatSize(stats.second)),
                actionText = stringResource(R.string.game_icons_delete),
                enabled = enabled && stats.first > 0,
            ) {
                WowIcons.clearCache(context)
                stats = WowIcons.cacheStats(context)
            }
        }
        SettingNote(
            if (on) {
                stringResource(R.string.game_icons_on_note)
            } else {
                stringResource(R.string.game_icons_off_note)
            },
        )
    }
}

internal fun formatSize(bytes: Long): String =
    if (bytes < 1024 * 1024) String.format(sizeLocale(), "%.0f KB", bytes / 1024.0)
    else String.format(sizeLocale(), "%.1f MB", bytes / 1048576.0)

/** Decimal comma in a German app, decimal point otherwise. */
private fun sizeLocale(): Locale = if (AppText.isGerman()) Locale.GERMANY else Locale.US
