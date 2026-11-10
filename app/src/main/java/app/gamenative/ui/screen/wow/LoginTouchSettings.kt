package app.gamenative.ui.screen.wow

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import androidx.compose.runtime.Composable
import app.gamenative.ui.components.AppText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Zeile der Einstellungsseite (Gruppe Steuerung): Touch-Steuerung im Login ausblenden. Derselbe Schalter wie im Seitenmenü. */
@Composable
internal fun LoginTouchSettingRow(enabled: Boolean) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(LoginTouchGate.hideEnabled(context)) }
    SettingSwitchRow(
        title = stringResource(R.string.game_logintouch_title),
        description = stringResource(R.string.game_logintouch_desc),
        checked = on,
        enabled = enabled,
        onChange = { next ->
            LoginTouchGate.setHideEnabled(context, next)
            on = next
        },
    )
}

/**
 * Zeile der Einstellungsseite (Gruppe Steuerung): Touch-Elemente über offenen Spielfenstern (Taschen, Karte, Menüs) ausblenden.
 * Dieselbe Einstellung wie im Seitenmenü; wirkt sofort. Im Editor wird nie ausgeblendet.
 */
@Composable
internal fun WindowHideSettingRow(enabled: Boolean) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(WindowGate.savedMode(context)) }
    SettingChoiceRow(
        title = stringResource(R.string.game_windowhide_title),
        value = stringResource(mode.labelRes),
        options = WindowHideMode.entries,
        selected = mode,
        optionLabel = { AppText.get(it.labelRes) },
        description = stringResource(R.string.game_windowhide_desc, stringResource(mode.descriptionRes)),
        enabled = enabled,
        onSelect = { next ->
            WindowGate.setMode(context, next)
            mode = next
        },
    )
}
