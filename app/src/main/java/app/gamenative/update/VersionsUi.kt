package app.gamenative.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.backup.BackupStore
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.ui.screen.wow.launcher.ActionButton
import app.gamenative.ui.screen.wow.launcher.ActionKind
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.ui.screen.wow.launcher.PageSecondaryButton
import app.gamenative.ui.screen.wow.launcher.PillTone
import app.gamenative.ui.screen.wow.launcher.StatusChip
import app.gamenative.ui.screen.wow.launcher.StatusLine
import app.gamenative.ui.screen.wow.launcher.asString
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Versionen" in the tester area: a button opens the list of all releases of the tester source (newest first, installed one marked,
 * short changelog text). A newer version is a normal update; an older one opens the dialog of the downgrade
 * ([RollbackLogic]): backup and old APK go to Downloads, then the player uninstalls, installs the file and imports the backup.
 */
@Composable
internal fun TesterVersionsPanel(enabled: Boolean) {
    val context = LocalContext.current
    val versions by UpdateManager.versions.collectAsState()
    val rollback by UpdateManager.rollback.collectAsState()
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<VersionRow?>(null) }
    var browserFailed by remember { mutableStateOf(false) }

    ActionButton(
        text = stringResource(if (open) R.string.launcher_versions_hide else R.string.launcher_versions_show),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        open = !open
        if (open) UpdateManager.loadVersions(context) else UpdateManager.clearVersions()
    }
    if (open) {
        Text(stringResource(R.string.launcher_versions_hint), fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Muted)
        when (val v = versions) {
            VersionsState.Idle, VersionsState.Loading ->
                Text(stringResource(R.string.launcher_versions_loading), fontSize = 14.sp, color = LauncherColors.Muted)
            is VersionsState.Failed ->
                StatusLine(stringResource(R.string.launcher_versions_load_failed, v.reason.asString()), PillTone.Error)
            is VersionsState.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.launcher_browser_hint_page), fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Muted)
                if (browserFailed) Text(stringResource(R.string.launcher_browser_failed), fontSize = 14.sp, color = LauncherColors.ErrText)
                for (row in v.rows) {
                    VersionRowView(row, enabled, onBrowser = {
                        val page = UpdateManager.releasePageTarget(context, row.tag)
                        browserFailed = page == null || !UpdateManager.openInBrowser(context, page)
                    }) {
                        when (row.relation) {
                            VersionRelation.Newer -> UpdateManager.updateToVersion(context, row.tag)
                            VersionRelation.Older -> selected = row
                            VersionRelation.Installed -> Unit
                        }
                    }
                }
            }
        }
    }

    val target = selected
    if (target != null && rollback is RollbackState.Idle) {
        ConfirmRollbackDialog(
            target = target,
            onConfirm = {
                selected = null
                UpdateManager.prepareRollback(context, target.tag)
            },
            onCancel = { selected = null },
        )
    }
    RollbackProgressDialog(rollback)
}

/**
 * The manual way next to "Installieren": opens the APK (public source) or the release page (tester or private source) in the browser,
 * with the hint what happens next. Needs neither storage nor install right of the app; nothing is shown without a usable address.
 */
@Composable
internal fun BrowserDownloadBlock(info: UpdateInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val target = remember(info) { UpdateManager.browserTarget(context, info) } ?: return
    var failed by remember { mutableStateOf(false) }
    val installAllowed = remember(info) { UpdateManager.canInstallPackages(context) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PageSecondaryButton(stringResource(R.string.launcher_browser_button)) {
            failed = !UpdateManager.openInBrowser(context, target)
        }
        Text(
            stringResource(if (target is BrowserDownload.Target.ReleasePage) R.string.launcher_browser_hint_page else R.string.launcher_browser_hint_apk),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = LauncherColors.Muted,
        )
        if (!installAllowed) Text(stringResource(R.string.launcher_browser_no_install_right), fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Muted)
        if (failed) Text(stringResource(R.string.launcher_browser_failed), fontSize = 14.sp, color = LauncherColors.ErrText)
    }
}

@Composable
private fun VersionRowView(row: VersionRow, enabled: Boolean, onBrowser: () -> Unit, onAction: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(row.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = LauncherColors.TextHead, modifier = Modifier.weight(1f))
            if (row.prerelease) StatusChip(stringResource(R.string.launcher_versions_pre), PillTone.Info)
            when (row.relation) {
                VersionRelation.Installed -> StatusChip(stringResource(R.string.launcher_versions_installed), PillTone.Ok)
                VersionRelation.Newer -> ActionButton(stringResource(R.string.launcher_versions_update), ActionKind.Primary, enabled, onClick = onAction)
                VersionRelation.Older -> ActionButton(stringResource(R.string.launcher_versions_older), ActionKind.Secondary, enabled, onClick = onAction)
            }
        }
        if (row.notes.isNotEmpty()) Text(row.notes, fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Muted)
        if (row.relation != VersionRelation.Installed) {
            ActionButton(stringResource(R.string.launcher_browser_button_short), ActionKind.Secondary, enabled, onClick = onBrowser)
        }
    }
}

@Composable
private fun ConfirmRollbackDialog(target: VersionRow, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val gamePath = remember { GamePath.load(context) }
    val place = remember(gamePath) { RollbackLogic.gameFilesPlace(gamePath, context.packageName, context.applicationInfo.dataDir.orEmpty()) }
    // The size only matters when the game is deleted with the app (then it is walked once, off the main thread).
    var gameBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(place) {
        if (place == RollbackLogic.GameFilesPlace.InAppStorage) {
            gameBytes = withContext(Dispatchers.IO) { runCatching { File(gamePath).walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L) }
        }
    }
    val backupName = remember { BackupStore.suggestedFileName(context) }
    val parts = RollbackLogic.dialogParts(target, place, gamePath, gameBytes, backupName)
    RollbackAlert(
        title = stringResource(R.string.launcher_rollback_title, target.name),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (part in parts) {
                    Text(
                        part.text.asString(),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = if (part.warning) LauncherColors.Gold else Color(0xFFE2E8F0),
                        fontWeight = if (part.warning) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        },
        confirm = stringResource(R.string.launcher_rollback_confirm) to onConfirm,
        dismiss = stringResource(R.string.launcher_rollback_cancel) to onCancel,
    )
}

/** The running steps, the result "ready to uninstall" and a failure; nothing in [RollbackState.Idle]. */
@Composable
private fun RollbackProgressDialog(state: RollbackState) {
    val context = LocalContext.current
    var uninstallFailed by remember { mutableStateOf(false) }
    when (state) {
        RollbackState.Idle -> Unit
        is RollbackState.Working -> RollbackAlert(
            title = stringResource(R.string.launcher_rollback_title, state.version),
            body = {
                val text = when (state.step) {
                    RollbackStep.Backup -> stringResource(R.string.launcher_rollback_working_backup)
                    else -> stringResource(R.string.launcher_rollback_working_download, state.version, ((state.fraction ?: 0f) * 100).toInt())
                }
                Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFFE2E8F0))
            },
            confirm = null,
            dismiss = null,
        )
        is RollbackState.Ready -> RollbackAlert(
            title = stringResource(R.string.launcher_rollback_ready_title),
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.launcher_rollback_ready_text, state.version, state.backupFile, state.apkFile),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFE2E8F0),
                    )
                    if (uninstallFailed) StatusLine(stringResource(R.string.launcher_rollback_uninstall_failed), PillTone.Error)
                }
            },
            confirm = stringResource(R.string.launcher_rollback_uninstall) to { uninstallFailed = !UpdateManager.startUninstall(context) },
            dismiss = stringResource(R.string.launcher_rollback_close) to {
                uninstallFailed = false
                UpdateManager.dismissRollback()
            },
        )
        is RollbackState.Failed -> RollbackAlert(
            title = stringResource(R.string.launcher_versions_title),
            body = { Text(state.reason.asString(), fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFFE2E8F0)) },
            confirm = stringResource(R.string.launcher_rollback_close) to { UpdateManager.dismissRollback() },
            dismiss = null,
        )
    }
}

@Composable
private fun RollbackAlert(
    title: String,
    body: @Composable () -> Unit,
    confirm: Pair<String, () -> Unit>?,
    dismiss: Pair<String, () -> Unit>?,
) {
    AlertDialog(
        onDismissRequest = { (dismiss ?: confirm)?.second?.invoke() },
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = LauncherColors.Gold) },
        text = body,
        confirmButton = {
            if (confirm != null) TextButton(onClick = confirm.second) { Text(confirm.first, color = LauncherColors.Gold, fontWeight = FontWeight.Bold) }
        },
        dismissButton = dismiss?.let { d -> @Composable { TextButton(onClick = d.second) { Text(d.first, color = LauncherColors.Muted) } } },
        containerColor = Color(0xFF1B2838),
        shape = RoundedCornerShape(16.dp),
    )
}
