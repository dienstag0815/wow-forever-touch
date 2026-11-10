package app.gamenative.ui.screen.wow

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gamenative.ui.screen.wow.klick.ClickWheel
import app.gamenative.ui.screen.wow.klick.WheelDirection
import app.gamenative.ui.screen.wow.klick.wheelLabelRes

/**
 * Auswahlliste für eine Richtung des Klick-Rads im Seitenmenü: dieselben Aktionen ([ClickWheel.ACTIONS]) und Namen wie in den
 * Einstellungen des Launchers, nur als Liste statt Durchschalten. Antippen wählt und schließt.
 */
@Composable
internal fun WheelActionDialog(
    direction: WheelDirection,
    currentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_wheel_title, stringResource(wheelLabelRes(direction)))) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (action in ClickWheel.ACTIONS) {
                    val selected = action.id == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onPick(action.id) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (if (selected) "✓  " else "     ") + action.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_cancel)) } },
    )
}

/** Rückfrage vor dem Zurücksetzen des Touch-Layouts auf die Vorlage der App. */
@Composable
internal fun LayoutResetDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_reset_layout_title)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.game_reset)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_cancel)) } },
    )
}
