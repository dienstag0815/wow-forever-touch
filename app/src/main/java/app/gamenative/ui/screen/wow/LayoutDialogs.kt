package app.gamenative.ui.screen.wow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.gamenative.R

/**
 * Auswahlliste der Touch-Layouts im Seitenmenü: Häkchen beim aktiven, Antippen wechselt sofort. Darunter "Als neues Layout
 * speichern …". Die Namen sind die Anzeigenamen ([TouchLayouts.Entry.display]); Vorlagen der App sind als solche markiert.
 */
@Composable
internal fun LayoutPickerDialog(
    entries: List<TouchLayouts.Entry>,
    activeId: Int?,
    onPick: (TouchLayouts.Entry) -> Unit,
    onSaveAs: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_layout_pick_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (entries.isEmpty()) {
                    Text(stringResource(R.string.game_layout_none), style = MaterialTheme.typography.bodyMedium)
                }
                for (entry in entries) {
                    val selected = entry.id == activeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onPick(entry) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val suffix = if (entry.template) "  ·  " + stringResource(R.string.game_layout_template) else ""
                        Text(
                            text = (if (selected) "✓  " else "     ") + entry.display + suffix,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSaveAs) { Text(stringResource(R.string.game_layout_save_as_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_cancel)) } },
    )
}

/**
 * Name eines neuen Layouts eingeben. Speichern ist erst möglich, wenn der Name nicht leer und noch frei ist (Groß-/Kleinschreibung
 * zählt nicht); die Eingabe ist auf [TouchLayouts.NAME_MAX] Zeichen begrenzt. [existingProfileNames] sind die Profilnamen ("Touch (…)").
 */
@Composable
internal fun LayoutNameDialog(
    existingProfileNames: Collection<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.game_layout_name_title),
) {
    var input by remember { mutableStateOf("") }
    val problem = TouchLayouts.nameProblem(input, existingProfileNames)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(TouchLayouts.NAME_MAX) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.game_layout_name_hint)) },
                    isError = problem == TouchLayouts.NameProblem.TAKEN,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                if (problem == TouchLayouts.NameProblem.TAKEN) {
                    Text(
                        text = stringResource(R.string.game_layout_name_taken),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = problem == TouchLayouts.NameProblem.NONE, onClick = { onSave(input) }) {
                Text(stringResource(R.string.game_layout_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_cancel)) } },
    )
}

/**
 * Der Editor wird mit Änderungen an einer Vorlage der App verlassen. Nichts wird still überschrieben: der Spieler speichert die
 * Änderungen als eigenes Layout (die Vorlage bleibt, wie sie war), ändert die Vorlage selbst oder verwirft die Änderungen.
 */
@Composable
internal fun TemplateEditedDialog(
    templateDisplayName: String,
    onSaveOwn: () -> Unit,
    onChangeTemplate: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.game_layout_template_edited_title)) },
        text = {
            Column {
                Text(stringResource(R.string.game_layout_template_edited_text, templateDisplayName))
                TextButton(onClick = onSaveOwn) { Text(stringResource(R.string.game_layout_save_own)) }
                TextButton(onClick = onChangeTemplate) { Text(stringResource(R.string.game_layout_change_template)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.game_layout_discard)) }
            }
        },
        confirmButton = {},
    )
}
