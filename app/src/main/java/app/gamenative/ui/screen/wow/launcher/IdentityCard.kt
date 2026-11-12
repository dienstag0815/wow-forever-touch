package app.gamenative.ui.screen.wow.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.screen.wow.SettingActionRow
import app.gamenative.ui.screen.wow.SettingGroupCard
import app.gamenative.ui.screen.wow.SettingNote
import app.gamenative.ui.screen.wow.SettingRow
import app.gamenative.ui.screen.wow.SettingSwitchRow
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.MachineIdentity
import app.gamenative.utils.MachineIdentityApply
import app.gamenative.utils.MachineIdentityArchive
import app.gamenative.utils.MachineIdentityKeep
import app.gamenative.utils.MachineIdentityLog
import app.gamenative.utils.MachineIdentityStore
import app.gamenative.utils.PrefixTraits
import app.gamenative.utils.SystemView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Windows-Kennung auf der Seite "Hilfe und Diagnose": eine Zeile auf der Karte "Fehlerbericht" und in deren Detailansicht Kurzkennung,
 * Dauer, Zahl der Wechsel, die letzten Ereignisse und "Verlauf kopieren". Die Kennung selbst zeigt nur "Werte anzeigen" nach dem
 * Antippen (nie in Log, Bericht, Verlauf oder Zwischenablage); dort auch "Neue Kennung erzeugen" und die Liste früherer Kennungen
 * (Logik: IdentityActionLogic.kt, Speicher: MachineIdentityStore).
 */

/** Alles, was die Kennungs-Karte zeigt: Protokoll, Ablage (Kurzkennung und Zeitraum), Sperre bis zur nächsten neuen Kennung. */
internal class IdentityPanel(
    val summary: MachineIdentityLog.Summary,
    val archive: List<MachineIdentityArchive.Info>,
    val lockedUntil: Long?,
    val hasIdentity: Boolean,
    val system: SystemView.Summary? = null,
    val prefix: PrefixTraits.Summary? = null,
    /** Zustand der Kopie im Spielordner und ob der Schalter an ist. */
    val keep: MachineIdentityKeep.Status = MachineIdentityKeep.Status.NONE,
    val keepOn: Boolean = true,
)

/** Liest Protokoll, Ablage und Sperre abseits des Hauptthreads (null, bis sie da sind); ein neuer [reload]-Wert liest erneut. */
@Composable
internal fun produceIdentityPanel(reload: Int): State<IdentityPanel?> {
    val context = LocalContext.current
    return produceState<IdentityPanel?>(initialValue = null, context, reload) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val store = MachineIdentityApply.store(context)
                IdentityPanel(
                    store.summary(),
                    store.archiveInfos(),
                    store.regenerateLockedUntil(),
                    store.peek() != null,
                    runCatching { store.systemSummary() }.getOrNull(),
                    runCatching { store.prefixSummary() }.getOrNull(),
                    runCatching { store.keepStatus() }.getOrDefault(MachineIdentityKeep.Status.NO_ACCESS),
                    store.keepEnabled(),
                )
            }.getOrNull()
        }
    }
}

/** Die Zeile mit Kurzkennung und Dauer ("neu seit ..., vorher ..." nach einem Wechsel, auch nach einer Neuinstallation). */
@Composable
private fun identityLine(summary: MachineIdentityLog.Summary?): String {
    if (summary?.fingerprint == null) return stringResource(R.string.launcher_identity_none)
    val since = MachineIdentityLog.formatDate(summary.since ?: 0L)
    return if (summary.previous != null) {
        stringResource(R.string.launcher_identity_new_since, summary.fingerprint, since, summary.previous)
    } else {
        stringResource(R.string.launcher_identity_line, summary.fingerprint, since)
    }
}

/** Eine Zeile unter dem Kurztext der Karte "Fehlerbericht": Kurzkennung und Datum, bei misslungenem Speichern die Warnung. */
@Composable
internal fun ColumnScope.IdentityShortLine(summary: MachineIdentityLog.Summary?) {
    val fingerprint = summary?.fingerprint
    when {
        summary == null -> Unit
        summary.saveFailed -> CardText(stringResource(R.string.launcher_identity_short_failed), 1, LauncherColors.Warn)
        fingerprint == null -> CardText(stringResource(R.string.launcher_identity_short_none), 1)
        else -> {
            val since = MachineIdentityLog.formatDate(summary.since ?: 0L)
            val id = if (summary.previous != null) R.string.launcher_identity_short_new else R.string.launcher_identity_short
            CardText(stringResource(id, fingerprint, since), 1)
        }
    }
}

/**
 * Detailansicht der Kennung. [gameBusy] = das Spiel startet oder läuft (dann sind "Neue Kennung erzeugen" und "Wieder verwenden" aus);
 * [onChanged] lässt die Karte neu lesen, nachdem die Kennung gewechselt wurde.
 */
@Composable
internal fun ColumnScope.IdentityDetailBody(panel: IdentityPanel?, gameBusy: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summary = panel?.summary
    var values by remember { mutableStateOf<MachineIdentity?>(null) }
    var dialog by remember { mutableStateOf<IdentityDialog>(IdentityDialog.None) }
    val hasIdentity = panel?.hasIdentity == true
    val newBlock = regenerateBlock(hasIdentity, gameBusy, panel?.lockedUntil)
    val reuseBlock = restoreBlock(hasIdentity, gameBusy)

    fun execute(message: (MachineIdentityStore.Outcome) -> String, action: (MachineIdentityStore) -> MachineIdentityStore.Outcome) {
        dialog = IdentityDialog.None
        values = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { action(MachineIdentityApply.store(context)) }.getOrDefault(MachineIdentityStore.Outcome.SaveFailed)
            }
            SnackbarManager.show(message(outcome))
            onChanged()
        }
    }

    SettingGroupCard(stringResource(R.string.launcher_help_identity)) {
        SettingNote(stringResource(R.string.launcher_identity_hint))
        if (summary?.saveFailed == true) {
            SettingRow(title = stringResource(R.string.launcher_identity_save_failed))
        }
        SettingRow(
            title = identityLine(summary),
            description = summary?.takeIf { it.fingerprint != null }?.let { stringResource(R.string.launcher_identity_changes, it.changes) },
        )
        if (summary?.fingerprint != null) {
            val update = summary.update
            when {
                update == null -> SettingNote(stringResource(R.string.launcher_identity_update_none))
                update.unchanged -> SettingRow(
                    title = stringResource(R.string.launcher_identity_update_same, update.from, update.to),
                    description = MachineIdentityLog.formatTime(update.at) + " UTC",
                )
                else -> SettingRow(
                    title = stringResource(R.string.launcher_identity_update_changed, update.from, update.to),
                    description = MachineIdentityLog.formatTime(update.at) + " UTC",
                )
            }
        }
        SettingRow(
            title = stringResource(R.string.launcher_identity_copy),
            description = stringResource(R.string.launcher_identity_copy_hint),
            enabled = summary?.fingerprint != null,
            onClick = { copyHistory(context, summary) },
        )
        IdentityValuesRows(
            shown = values,
            hasIdentity = hasIdentity,
            onToggle = {
                values = if (values != null) null else runCatching { MachineIdentityApply.store(context).peek() }.getOrNull()
            },
        )
        if (panel != null) {
            val keepOn = stringResource(R.string.launcher_identity_keep_on_done)
            val keepOff = stringResource(R.string.launcher_identity_keep_off_done)
            val keepFailed = stringResource(R.string.launcher_identity_keep_failed)
            SettingSwitchRow(
                title = stringResource(R.string.launcher_identity_keep_title),
                checked = panel.keepOn,
                description = stringResource(R.string.launcher_identity_keep_hint),
                onChange = { on ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { MachineIdentityApply.store(context).setKeepEnabled(on) }.getOrDefault(false) }
                        SnackbarManager.show(if (!ok) keepFailed else if (on) keepOn else keepOff)
                        onChanged()
                    }
                },
            )
            SettingNote(
                stringResource(
                    when (panel.keep) {
                        MachineIdentityKeep.Status.OFF -> R.string.launcher_identity_keep_status_off
                        MachineIdentityKeep.Status.NO_ACCESS -> R.string.launcher_identity_keep_status_no_access
                        MachineIdentityKeep.Status.NONE -> R.string.launcher_identity_keep_status_none
                        MachineIdentityKeep.Status.MATCHES -> R.string.launcher_identity_keep_status_matches
                        MachineIdentityKeep.Status.OTHER_IDENTITY -> R.string.launcher_identity_keep_status_other_identity
                        MachineIdentityKeep.Status.OTHER_MODEL -> R.string.launcher_identity_keep_status_other_model
                        MachineIdentityKeep.Status.DAMAGED -> R.string.launcher_identity_keep_status_damaged
                    },
                ),
            )
        }
        val newDescription = when (newBlock) {
            IdentityBlock.GameRunning -> stringResource(R.string.launcher_identity_game_running)
            IdentityBlock.Locked -> stringResource(R.string.launcher_identity_locked, MachineIdentityLog.formatTime(panel?.lockedUntil ?: 0L))
            else -> stringResource(R.string.launcher_identity_new_hint)
        }
        SettingActionRow(
            title = stringResource(R.string.launcher_identity_new_title),
            description = newDescription,
            actionText = stringResource(R.string.launcher_identity_new_button),
            enabled = newBlock == IdentityBlock.None,
            danger = true,
            onClick = { dialog = openRegenerate(newBlock) },
        )
    }
    if (panel != null) {
        SettingGroupCard(stringResource(R.string.launcher_identity_earlier)) {
            if (panel.archive.isEmpty()) {
                SettingNote(stringResource(R.string.launcher_identity_earlier_none))
            }
            panel.archive.forEachIndexed { index, info ->
                SettingActionRow(
                    title = stringResource(R.string.launcher_identity_earlier_row, info.fingerprint),
                    description = stringResource(
                        R.string.launcher_identity_earlier_period,
                        MachineIdentityLog.formatDate(info.from),
                        MachineIdentityLog.formatDate(info.until),
                    ),
                    actionText = stringResource(R.string.launcher_identity_reuse),
                    enabled = reuseBlock == IdentityBlock.None,
                    onClick = { dialog = openReuse(reuseBlock, panel.archive, index) },
                )
            }
            if (panel.archive.isNotEmpty() && reuseBlock == IdentityBlock.GameRunning) {
                SettingNote(stringResource(R.string.launcher_identity_game_running))
            }
        }
    }
    SystemViewGroup(panel?.system)
    PrefixTraitsGroup(panel?.prefix)
    if (summary != null && summary.recent.isNotEmpty()) {
        SettingGroupCard(stringResource(R.string.launcher_identity_recent)) {
            for (entry in summary.recent.asReversed()) {
                val detail = if (entry.detail.isEmpty()) "" else " · " + entry.detail
                SettingRow(
                    title = entry.event.id + detail,
                    description = "${MachineIdentityLog.formatTime(entry.time)} UTC · ${entry.fingerprint} · v${entry.versionCode}",
                )
            }
        }
    }

    val failedNew = stringResource(R.string.launcher_identity_new_failed)
    val failedReuse = stringResource(R.string.launcher_identity_reuse_failed)
    val running = stringResource(R.string.launcher_identity_game_running)
    when (val d = dialog) {
        IdentityDialog.None -> Unit
        IdentityDialog.Regenerate -> {
            val done = stringResource(R.string.launcher_identity_new_done, "%s")
            val locked = stringResource(R.string.launcher_identity_locked, "%s")
            IdentityConfirmDialog(
                title = stringResource(R.string.launcher_identity_dialog_new_title),
                text = stringResource(R.string.launcher_identity_dialog_new_text),
                confirm = stringResource(R.string.launcher_identity_dialog_new_confirm),
                cancel = stringResource(R.string.launcher_identity_dialog_cancel),
                onCancel = { dialog = IdentityDialog.None },
                onConfirm = {
                    execute(
                        message = { outcome ->
                            when (outcome) {
                                is MachineIdentityStore.Outcome.Done -> done.format(outcome.fingerprint)
                                MachineIdentityStore.Outcome.GameRunning -> running
                                is MachineIdentityStore.Outcome.Locked -> locked.format(MachineIdentityLog.formatTime(outcome.until))
                                else -> failedNew
                            }
                        },
                        action = { it.regenerate(gameBusy) },
                    )
                },
            )
        }
        is IdentityDialog.Reuse -> {
            val done = stringResource(R.string.launcher_identity_reuse_done, "%s")
            IdentityConfirmDialog(
                title = stringResource(R.string.launcher_identity_dialog_reuse_title, d.fingerprint),
                text = stringResource(R.string.launcher_identity_dialog_reuse_text),
                confirm = stringResource(R.string.launcher_identity_reuse),
                cancel = stringResource(R.string.launcher_identity_dialog_cancel),
                onCancel = { dialog = IdentityDialog.None },
                onConfirm = {
                    execute(
                        message = { outcome ->
                            when (outcome) {
                                is MachineIdentityStore.Outcome.Done -> done.format(outcome.fingerprint)
                                MachineIdentityStore.Outcome.GameRunning -> running
                                else -> failedReuse
                            }
                        },
                        action = { it.restore(d.index, gameBusy) },
                    )
                },
            )
        }
    }
}

/** Name eines Teils der Systemsicht in der App-Sprache. */
@Composable
private fun partLabel(id: String): String = stringResource(
    when (id) {
        "hostname" -> R.string.launcher_system_part_hostname
        "schnittstellen" -> R.string.launcher_system_part_interfaces
        "netz" -> R.string.launcher_system_part_network
        "prozessor" -> R.string.launcher_system_part_cpu
        "spielordner" -> R.string.launcher_system_part_folder
        "nutzer" -> R.string.launcher_system_part_user
        "speicher" -> R.string.launcher_system_part_space
        "android" -> R.string.launcher_system_part_android
        else -> R.string.launcher_system_part_other
    },
)

/**
 * Abschnitt "Systemsicht": Fingerabdruck, seit wann unverändert, was sich zuletzt geändert hat, aufklappbar die groben Merkmale je Teil.
 * Gezeigt wird der Stand des letzten Spielstarts; Hostname, MAC- und IP-Adressen kommen nie vor, nur Fingerabdruck und Art/Anzahl/ja-nein.
 * Modell und Android-Version stehen nur hier (live gelesen), nicht in Protokoll, Verlaufsdatei oder Fehlerbericht.
 */
@Composable
private fun SystemViewGroup(system: SystemView.Summary?) {
    var open by remember { mutableStateOf(false) }
    SettingGroupCard(stringResource(R.string.launcher_system_title)) {
        SettingNote(stringResource(R.string.launcher_system_hint))
        if (system?.total == null) {
            SettingNote(stringResource(R.string.launcher_system_none))
            return@SettingGroupCard
        }
        val since = system.since?.let { MachineIdentityLog.formatTime(it) + " UTC" } ?: "?"
        SettingRow(
            title = stringResource(R.string.launcher_system_line, system.total, since),
            description = stringResource(R.string.launcher_system_changes, system.changes),
        )
        if (system.changedParts.isNotEmpty()) {
            val labels = system.changedParts.map { partLabel(it) }
            val names = labels.joinToString(", ")
            SettingRow(
                title = stringResource(R.string.launcher_system_last_change, names),
                description = system.changedAt?.let { MachineIdentityLog.formatTime(it) + " UTC" },
            )
        }
        SettingRow(
            title = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            description = stringResource(R.string.launcher_system_device_note, Build.MODEL ?: "?"),
        )
        if (system.parts.isNotEmpty()) {
            ExpandRow(
                title = stringResource(R.string.launcher_system_features),
                expanded = open,
                onToggle = { open = !open },
            )
            if (open) {
                InsetPanel {
                    for (p in system.parts) {
                        Text(text = partLabel(p.part.id) + " · " + p.fingerprint, fontSize = 14.sp, color = LauncherColors.Body, fontWeight = FontWeight.Bold)
                        Text(text = p.features.joinToString("\n"), fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.Muted)
                    }
                }
            }
        }
    }
}

/** Name eines Teils der Prefix-Merkmale in der App-Sprache. */
@Composable
private fun prefixPartLabel(id: String): String = stringResource(
    when (id) {
        "hwprofil" -> R.string.launcher_prefix_part_hwprofil
        "sqm" -> R.string.launcher_prefix_part_sqm
        "produktschluessel" -> R.string.launcher_prefix_part_productkey
        "benutzer" -> R.string.launcher_prefix_part_user
        "netzwerkkarten" -> R.string.launcher_prefix_part_netcards
        "updatezeit" -> R.string.launcher_prefix_part_update
        else -> R.string.launcher_system_part_other
    },
)

/**
 * Abschnitt "Prefix-Merkmale" neben der Systemsicht: Werte, die Wine selbst im Prefix erzeugt (nicht unsere Kennung), gemessen vor dem Anwenden
 * der Kennung. Nur Fingerabdruck und grobe Merkmale; die Werte selbst kommen nie vor, und nichts davon wird geändert.
 */
@Composable
private fun PrefixTraitsGroup(prefix: PrefixTraits.Summary?) {
    var open by remember { mutableStateOf(false) }
    SettingGroupCard(stringResource(R.string.launcher_prefix_title)) {
        SettingNote(stringResource(R.string.launcher_prefix_hint))
        if (prefix?.total == null) {
            SettingNote(stringResource(R.string.launcher_prefix_none))
            return@SettingGroupCard
        }
        val since = prefix.since?.let { MachineIdentityLog.formatTime(it) + " UTC" } ?: "?"
        SettingRow(
            title = stringResource(R.string.launcher_prefix_line, prefix.total, since),
            description = stringResource(R.string.launcher_prefix_changes, prefix.changes),
        )
        if (prefix.changedParts.isNotEmpty()) {
            val names = prefix.changedParts.map { prefixPartLabel(it) }.joinToString(", ")
            SettingRow(
                title = stringResource(R.string.launcher_prefix_last_change, names),
                description = prefix.changedAt?.let { MachineIdentityLog.formatTime(it) + " UTC" },
            )
        }
        if (prefix.parts.isNotEmpty()) {
            ExpandRow(
                title = stringResource(R.string.launcher_prefix_features),
                expanded = open,
                onToggle = { open = !open },
            )
            if (open) {
                InsetPanel {
                    for (p in prefix.parts) {
                        Text(text = prefixPartLabel(p.part.id) + " · " + p.fingerprint, fontSize = 14.sp, color = LauncherColors.Body, fontWeight = FontWeight.Bold)
                        Text(text = p.features.joinToString("\n"), fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.Muted)
                    }
                }
            }
        }
    }
}

/** "Werte anzeigen": erst nach dem Antippen stehen die Werte da; der Satz dazu sagt, dass sie zufällig erzeugt sind und für keinen echten PC stehen. */
@Composable
private fun IdentityValuesRows(shown: MachineIdentity?, hasIdentity: Boolean, onToggle: () -> Unit) {
    SettingRow(
        title = stringResource(if (shown != null) R.string.launcher_identity_hide else R.string.launcher_identity_show),
        description = stringResource(R.string.launcher_identity_show_hint),
        enabled = hasIdentity,
        onClick = onToggle,
    )
    if (shown == null) {
        if (!hasIdentity) SettingNote(stringResource(R.string.launcher_identity_values_none))
        return
    }
    SettingNote(stringResource(R.string.launcher_identity_values_note))
    SettingRow(title = shown.machineGuid, description = stringResource(R.string.launcher_identity_v_guid))
    SettingRow(title = shown.computerName, description = stringResource(R.string.launcher_identity_v_name))
    SettingRow(title = shown.volumeSerial, description = stringResource(R.string.launcher_identity_v_serial))
    SettingRow(title = shown.productId, description = stringResource(R.string.launcher_identity_v_product))
    SettingRow(title = MachineIdentityLog.formatDate(shown.installDate), description = stringResource(R.string.launcher_identity_v_install))
}

/** Rückfrage mit "Abbrechen" als hervorgehobenem Standardknopf; Zurück und Tippen daneben brechen ab. */
@Composable
private fun IdentityConfirmDialog(title: String, text: String, confirm: String, cancel: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = LauncherColors.Gold) },
        text = { Text(text = text, fontSize = 14.sp, lineHeight = 20.sp, color = LauncherColors.Body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = LauncherColors.Err) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(cancel, color = LauncherColors.Gold, fontWeight = FontWeight.Bold) }
        },
        containerColor = LauncherColors.SurfaceRaised,
        shape = RoundedCornerShape(16.dp),
    )
}

private fun copyHistory(context: Context, summary: MachineIdentityLog.Summary?) {
    val text = MachineIdentityLog.historyText(summary?.trace.orEmpty())
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WoW Forever ID history", text))
        SnackbarManager.show(context.getString(R.string.launcher_identity_copied))
    }
}
