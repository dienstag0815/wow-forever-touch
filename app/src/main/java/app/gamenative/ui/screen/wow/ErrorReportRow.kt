package app.gamenative.ui.screen.wow

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.gamenative.report.ErrorReport

/**
 * Zeile "Fehlerbericht erstellen" der Einstellungsseite (Gruppe Hilfe). Der Bericht entsteht im Hintergrund, die Statusanzeige
 * ist die Snackbar; danach öffnet sich das Teilen-Menü. E-Mail und Passwort sind darin geschwärzt, gesendet wird nichts von selbst.
 */
@Composable
internal fun ErrorReportSettingRow() {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    SettingActionRow(
        title = stringResource(R.string.game_report_row_title),
        description = note ?: stringResource(R.string.game_report_row_desc),
        actionText = stringResource(R.string.game_report_row_action),
        onClick = { ErrorReport.start(context) { note = it } },
    )
}
