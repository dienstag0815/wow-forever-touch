package app.gamenative.backup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.screen.wow.SettingGroupCard
import app.gamenative.ui.screen.wow.SettingRow
import app.gamenative.ui.screen.wow.launcher.DetailHint
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.update.RollbackFiles
import app.gamenative.update.RollbackLogic
import app.gamenative.update.UpdateSettings
import app.gamenative.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Was die Backup-Ansicht gerade als Rückfrage oder Meldung zeigt. */
private sealed interface BackupDialog {
    /** Die gelesene Datei, vor dem Übernehmen: [conflicts] sind Layouts, die es mit anderem Inhalt schon gibt. */
    data class Preview(val backup: Backup, val conflicts: List<String>) : BackupDialog

    data class Done(val result: ImportResult) : BackupDialog

    /** Eine einfache Meldung mit fertigem Text. */
    data class Info(val text: String) : BackupDialog
}

/**
 * Inhalt der Detailansicht "Backup" in den Einstellungen (Karte "App"): Export und Import mit Rückfragen. Datei wählt der Benutzer
 * über das Auswahlfenster des Systems (kein Speicherrecht nötig). Nach einem Import baut sie die Activity neu auf, weil die
 * Oberfläche die Einstellungen beim Aufbau liest.
 */
@Composable
internal fun ColumnScope.BackupDetailBody() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<BackupDialog?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val manifest = withContext(Dispatchers.IO) { BackupStore.exportTo(context, uri) }
            busy = false
            dialog = BackupDialog.Info(
                if (manifest == null) {
                    context.getString(R.string.backup_export_failed)
                } else {
                    context.getString(R.string.backup_export_done, manifest.profileCount, manifest.settingCount)
                },
            )
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                when (val r = BackupStore.read(context, uri)) {
                    is UnpackResult.Ok -> r to BackupStore.conflicts(context, r.backup)
                    is UnpackResult.Failed -> r to emptyList()
                }
            }
            busy = false
            dialog = when (val r = read.first) {
                is UnpackResult.Ok -> BackupDialog.Preview(r.backup, read.second)
                is UnpackResult.Failed -> BackupDialog.Info(context.getString(errorText(r.error)))
            }
        }
    }

    DetailHint(stringResource(R.string.backup_hint))
    SettingGroupCard(stringResource(R.string.backup_title)) {
        SettingRow(
            title = stringResource(R.string.backup_export),
            description = stringResource(R.string.backup_export_desc),
            enabled = !busy,
            onClick = { exportPicker.launch(BackupStore.suggestedFileName(context)) },
        )
        SettingRow(
            title = stringResource(R.string.backup_import),
            description = stringResource(R.string.backup_import_desc),
            enabled = !busy,
            onClick = { importPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        )
    }

    BackupDialogs(dialog, onDialog = { dialog = it }, onBusy = { busy = it })
}

/** Die Rückfragen und Meldungen des Backups (Info, Vorschau, Ergebnis) samt dem Übernehmen; für die Ansicht "Backup" und den Vorschlag nach einer Neuinstallation. */
@Composable
private fun BackupDialogs(dialog: BackupDialog?, onDialog: (BackupDialog?) -> Unit, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun apply(preview: BackupDialog.Preview, policy: ConflictPolicy) {
        onDialog(null)
        onBusy(true)
        scope.launch {
            // Auf dem Hauptfaden: die Zwischenspeicher der Oberfläche (Bilder, Abklingzeiten) werden dabei nachgezogen.
            val result = BackupStore.apply(context, preview.backup, policy)
            onBusy(false)
            onDialog(BackupDialog.Done(result))
        }
    }

    when (dialog) {
        null -> Unit
        is BackupDialog.Info -> BackupAlert(
            title = stringResource(R.string.backup_title),
            text = dialog.text,
            confirm = stringResource(R.string.backup_ok) to { onDialog(null) },
        )
        is BackupDialog.Preview -> PreviewDialog(
            preview = dialog,
            onApply = { policy -> apply(dialog, policy) },
            onCancel = { onDialog(null) },
        )
        is BackupDialog.Done -> BackupAlert(
            title = stringResource(R.string.backup_done_title),
            text = doneText(context, dialog.result),
            confirm = stringResource(R.string.backup_ok) to {
                onDialog(null)
                findActivity(context)?.recreate()
            },
        )
    }
}

/**
 * Nach einer Neuinstallation (erster Start, Rückstufen oder Neuaufbau): liegt in Downloads ein Backup dieser App, schlägt der Launcher
 * einmal den Import des neuesten vor. "Importieren" liest die Datei und zeigt die übliche Vorschau; "Nein, danke" und jede Antwort
 * beenden den Vorschlag. Ohne Speicherzugriff bleibt er offen, bis die Datei lesbar ist.
 */
@Composable
internal fun BackupResumeHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offer by remember { mutableStateOf<java.io.File?>(null) }
    var dialog by remember { mutableStateOf<BackupDialog?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!UpdateSettings.backupSuggestPending(context)) return@LaunchedEffect
        val found = withContext(Dispatchers.IO) {
            if (!StorageUtils.hasStoragePermission(context, RollbackFiles.downloadsDir().absolutePath)) null else RollbackFiles.newestBackup()
        }
        when {
            RollbackLogic.shouldSuggestImport(true, found?.name) -> offer = found
            StorageUtils.hasStoragePermission(context, RollbackFiles.downloadsDir().absolutePath) ->
                UpdateSettings.setBackupSuggestPending(context, false)
        }
    }

    offer?.let { file ->
        val decline = {
            UpdateSettings.setBackupSuggestPending(context, false)
            offer = null
        }
        BackupAlert(
            title = stringResource(R.string.backup_resume_title),
            text = stringResource(R.string.backup_resume_text, file.name),
            confirm = stringResource(R.string.backup_resume_yes) to {
                UpdateSettings.setBackupSuggestPending(context, false)
                offer = null
                busy = true
                scope.launch {
                    val read = withContext(Dispatchers.IO) {
                        when (val r = BackupStore.read(context, android.net.Uri.fromFile(file))) {
                            is UnpackResult.Ok -> r to BackupStore.conflicts(context, r.backup)
                            is UnpackResult.Failed -> r to emptyList()
                        }
                    }
                    busy = false
                    dialog = when (val r = read.first) {
                        is UnpackResult.Ok -> BackupDialog.Preview(r.backup, read.second)
                        is UnpackResult.Failed -> BackupDialog.Info(context.getString(errorText(r.error)))
                    }
                }
            },
            dismiss = stringResource(R.string.backup_resume_no) to decline,
        )
    }
    BackupDialogs(dialog, onDialog = { dialog = it }, onBusy = { busy = it })
}

@Composable
private fun PreviewDialog(preview: BackupDialog.Preview, onApply: (ConflictPolicy) -> Unit, onCancel: () -> Unit) {
    val backup = preview.backup
    val manifest = backup.manifest
    val empty = backup.profiles.isEmpty() && backup.settings.isEmpty()
    val emptyText = stringResource(R.string.backup_import_empty)
    val summaryText = stringResource(
        R.string.backup_import_summary,
        backup.profiles.size,
        backup.settings.size,
        manifest.createdAt.ifBlank { "?" },
        manifest.appVersionName.ifBlank { "?" },
    )
    val conflictText = stringResource(R.string.backup_import_conflict, preview.conflicts.joinToString(", "))
    val skippedText = stringResource(R.string.backup_import_skipped, backup.skipped)
    val text = buildString {
        append(if (empty) emptyText else summaryText)
        if (!empty && preview.conflicts.isNotEmpty()) append("\n\n").append(conflictText)
        if (backup.skipped > 0) append("\n\n").append(skippedText)
    }
    val cancel = stringResource(R.string.backup_cancel)
    when {
        empty -> BackupAlert(stringResource(R.string.backup_import), text, confirm = stringResource(R.string.backup_ok) to onCancel)
        preview.conflicts.isEmpty() -> BackupAlert(
            title = stringResource(R.string.backup_import_title),
            text = text,
            confirm = stringResource(R.string.backup_import_apply) to { onApply(ConflictPolicy.REPLACE) },
            dismiss = cancel to onCancel,
        )
        else -> AlertDialog(
            onDismissRequest = onCancel,
            title = { DialogTitle(stringResource(R.string.backup_import_title)) },
            text = { DialogText(text) },
            confirmButton = {
                Column {
                    TextButton(onClick = { onApply(ConflictPolicy.REPLACE) }) { GoldLabel(stringResource(R.string.backup_import_replace)) }
                    TextButton(onClick = { onApply(ConflictPolicy.KEEP_BOTH) }) { GoldLabel(stringResource(R.string.backup_import_keep_both)) }
                }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(cancel, color = LauncherColors.Muted) } },
            containerColor = Color(0xFF1B2838),
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun BackupAlert(title: String, text: String, confirm: Pair<String, () -> Unit>, dismiss: Pair<String, () -> Unit>? = null) {
    val dismissContent: (@Composable () -> Unit)? = dismiss?.let { d ->
        @Composable { TextButton(onClick = d.second) { Text(d.first, color = LauncherColors.Muted) } }
    }
    AlertDialog(
        onDismissRequest = (dismiss ?: confirm).second,
        title = { DialogTitle(title) },
        text = { DialogText(text) },
        confirmButton = { TextButton(onClick = confirm.second) { GoldLabel(confirm.first) } },
        dismissButton = dismissContent,
        containerColor = Color(0xFF1B2838),
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun DialogTitle(text: String) =
    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = LauncherColors.Gold)

@Composable
private fun DialogText(text: String) =
    Text(text = text, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFFE2E8F0))

@Composable
private fun GoldLabel(text: String) = Text(text, color = LauncherColors.Gold, fontWeight = FontWeight.Bold)

private fun errorText(error: BackupError): Int = when (error) {
    BackupError.NOT_A_BACKUP -> R.string.backup_err_not_backup
    BackupError.NO_MANIFEST -> R.string.backup_err_not_backup
    BackupError.WRONG_FORMAT -> R.string.backup_err_not_backup
    BackupError.TOO_OLD -> R.string.backup_err_too_old
    BackupError.TOO_NEW -> R.string.backup_err_too_new
    BackupError.TOO_BIG -> R.string.backup_err_too_big
    BackupError.BROKEN -> R.string.backup_err_broken
}

/** Meldung nach dem Import: Zähler, Fehlschläge und das Layout, das jetzt aktiv ist. */
private fun doneText(context: Context, r: ImportResult): String = buildString {
    append(context.getString(R.string.backup_done_text, r.created, r.replaced, r.renamed, r.identical, r.settings))
    if (r.failed > 0) append("\n").append(context.getString(R.string.backup_done_failed, r.failed))
    if (r.activeProfile.isNotEmpty()) append("\n").append(context.getString(R.string.backup_done_active, r.activeProfile))
}

private tailrec fun findActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> findActivity(context.baseContext)
    else -> null
}
