package app.gamenative.ui.screen.wow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.gamenative.ui.theme.WowMuted
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.gamenative.report.ErrorReport
import app.gamenative.report.LoggedInElsewhere
import app.gamenative.report.OtherAppBefore
import app.gamenative.report.ProblemAnswers
import app.gamenative.report.WorkedBefore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import android.text.format.DateUtils
import app.gamenative.report.ReportCard
import app.gamenative.report.ReportCardActions
import app.gamenative.report.ReportCardStore
import app.gamenative.report.ReportDialog
import app.gamenative.report.ReportDialogState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gamenative.ui.screen.wow.launcher.ActionButton
import app.gamenative.ui.screen.wow.launcher.ActionKind
import app.gamenative.ui.screen.wow.launcher.LauncherColors

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
        primary = true,
        onClick = { ErrorReport.start(context) { note = it } },
    )
}

/**
 * Zeile "Problem melden" neben dem Fehlerbericht: erst ein Hinweis ([ProblemReportDialog]), dann baut die App den Bericht, legt die Zip
 * in Downloads und kopiert die Kurzfassung; das Ergebnis zeigt der Dialog [ReportReadyDialog]. Gesendet wird nichts von selbst.
 */
@Composable
internal fun ProblemReportSettingRow() {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    val appVisible = rememberAppVisible()
    SettingActionRow(
        title = stringResource(R.string.game_pm_row_title),
        description = note ?: stringResource(R.string.game_pm_row_desc),
        actionText = stringResource(R.string.game_pm_action),
        onClick = { asking = true },
    )
    if (asking) {
        ProblemReportDialog(onConfirm = { answers -> asking = false; ErrorReport.startProblem(context, answers, appVisible) { note = it } }, onDismiss = { asking = false })
    }
}

/** Steht die App im Vordergrund? (Wird erst beim Fertigwerden des Berichts gelesen, nicht beim Zusammensetzen der Seite.) */
@Composable
internal fun rememberAppVisible(): () -> Boolean {
    val owner = LocalLifecycleOwner.current
    return { owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
}

/**
 * Der Dialog vor dem Erstellen: ein Satz, was die Zip enthält, was geschwärzt ist, dass nichts von selbst gesendet wird, die Meldung öffentlich
 * ist und dass geprüft wird, ob andere Start-Apps installiert sind; darunter vier freiwillige Fragen (Auswahl per Tipp, erneut tippen nimmt
 * die Auswahl zurück). [onConfirm] bekommt die Antworten (nicht beantwortete bleiben leer).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProblemReportDialog(onConfirm: (ProblemAnswers) -> Unit, onDismiss: () -> Unit) {
    var worked by remember { mutableStateOf<WorkedBefore?>(null) }
    var otherApp by remember { mutableStateOf<OtherAppBefore?>(null) }
    var authenticator by remember { mutableStateOf<Boolean?>(null) }
    var elsewhere by remember { mutableStateOf(emptySet<LoggedInElsewhere>()) }
    val textColor = LauncherColors.Body

    @Composable
    fun <T> Question(question: String, options: List<Pair<T, String>>, isSelected: (T) -> Boolean, onPick: (T) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(question, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) ->
                    FilterChip(
                        selected = isSelected(value),
                        onClick = { onPick(value) },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }

    val yes = stringResource(R.string.game_pm_a_yes)
    val no = stringResource(R.string.game_pm_a_no)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_pm_dialog_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = LauncherColors.Gold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.game_pm_dialog_body), fontSize = 14.sp, lineHeight = 20.sp, color = textColor)
                Text(stringResource(R.string.game_pm_dialog_apps), fontSize = 14.sp, lineHeight = 20.sp, color = textColor)
                Text(stringResource(R.string.game_pm_q_intro), fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.Muted, modifier = Modifier.padding(top = 4.dp))
                Question(
                    stringResource(R.string.game_pm_q1),
                    listOf(WorkedBefore.YES to yes, WorkedBefore.NO to no, WorkedBefore.UNKNOWN to stringResource(R.string.game_pm_a_unknown)),
                    isSelected = { worked == it },
                    onPick = { worked = if (worked == it) null else it },
                )
                Question(
                    stringResource(R.string.game_pm_q2),
                    listOf(
                        OtherAppBefore.NO to no,
                        OtherAppBefore.YES_WORKED to stringResource(R.string.game_pm_q2_worked),
                        OtherAppBefore.YES_FAILED to stringResource(R.string.game_pm_q2_failed),
                    ),
                    isSelected = { otherApp == it },
                    onPick = { otherApp = if (otherApp == it) null else it },
                )
                Question(
                    stringResource(R.string.game_pm_q3),
                    listOf(true to yes, false to no),
                    isSelected = { authenticator == it },
                    onPick = { authenticator = if (authenticator == it) null else it },
                )
                Question(
                    stringResource(R.string.game_pm_q4),
                    listOf(
                        LoggedInElsewhere.PC_APP to stringResource(R.string.game_pm_q4_pc),
                        LoggedInElsewhere.BROWSER to stringResource(R.string.game_pm_q4_browser),
                        LoggedInElsewhere.NONE to no,
                    ),
                    isSelected = { it in elsewhere },
                    onPick = { pick ->
                        elsewhere = when {
                            pick in elsewhere -> elsewhere - pick
                            pick == LoggedInElsewhere.NONE -> setOf(pick)
                            else -> elsewhere - LoggedInElsewhere.NONE + pick
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(ProblemAnswers(worked, otherApp, authenticator, elsewhere)) },
                colors = ButtonDefaults.buttonColors(containerColor = LauncherColors.Crimson, contentColor = LauncherColors.TextHead),
                shape = RoundedCornerShape(10.dp),
            ) { Text(stringResource(R.string.game_pm_dialog_ok), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.launcher_cancel), color = LauncherColors.Gold, fontWeight = FontWeight.Bold) } },
        containerColor = LauncherColors.SurfaceRaised,
        shape = RoundedCornerShape(16.dp),
    )
}

/** "Speichern unter ...": öffnet den Dateidialog von Android mit dem Namen der Zip. Abbruch tut nichts; [card] null = nichts zu speichern. */
@Composable
private fun rememberSaveZipAs(card: ReportCard?, onNote: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && card != null) ReportCardActions.saveZipAs(context, card, uri, onNote)
    }
    return { card?.let { launcher.launch(it.zipName) } }
}

/**
 * Die Karte "Bericht bereit" ([ReportCard]): Dateiname, wo die Zip liegt, Uhrzeit und die zwei Handgriffe, darunter vier Knöpfe
 * (Im Browser öffnen, In der App öffnen, Zip teilen, Kurzfassung kopieren) und "Schließen". Sie bleibt stehen, auch wenn die App von
 * der Meldeseite verdeckt wurde und nach einem Neustart, bis ein neuer Bericht sie ersetzt oder der Spieler sie schließt. Ohne Karte
 * zeigt die Funktion nichts. [onNote] bekommt Statuszeilen (zum Beispiel "Kurzfassung kopiert").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportReadyCard(onNote: (String) -> Unit) {
    val context = LocalContext.current
    val card by ReportCardStore.flow(context).collectAsState()
    val saveAs = rememberSaveZipAs(card, onNote)
    val current = card ?: return
    val time = DateUtils.formatDateTime(context, current.createdMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
    val where = stringResource(if (current.inDownloads) R.string.game_pm_card_saved else R.string.game_pm_card_cache, time)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.game_pm_card_title), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = LauncherColors.Gold)
        Text(current.zipName, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = LauncherColors.Body)
        Text(where, fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.Muted)
        Text(stringResource(R.string.game_pm_steps), fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Body)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(stringResource(R.string.game_pm_card_browser), ActionKind.Primary, enabled = current.issueUrl != null) { ReportCardActions.openInBrowser(context, current) }
            ActionButton(stringResource(R.string.game_pm_card_open), enabled = current.issueUrl != null) { ReportCardActions.openIssue(context, current) }
            ActionButton(stringResource(R.string.game_pm_card_share)) { ReportCardActions.shareZip(context, current, onNote) }
            ActionButton(stringResource(R.string.game_pm_show_downloads)) { ReportCardActions.showDownloads(context, onNote) }
            ActionButton(stringResource(R.string.game_pm_save_as)) { saveAs() }
            ActionButton(stringResource(R.string.game_pm_card_copy)) { ReportCardActions.copySummary(context, current, onNote) }
        }
        TextButton(onClick = { ReportCardStore.dismiss(context) }) { Text(stringResource(R.string.game_pm_card_close), color = WowMuted) }
    }
}

/**
 * Der Dialog "Bericht bereit" ([ReportReadyDialog]) an der Stelle, an der die Hilfeseite steht (Übersicht und Detailseite). Er erscheint,
 * sobald der Bericht fertig ist ([ReportDialogState]) und kommt nach "Schließen" nicht wieder. Ohne fällige Meldung zeigt die Funktion nichts.
 */
@Composable
internal fun ReportReadyDialogHost(onNote: (String) -> Unit) {
    val context = LocalContext.current
    val card by ReportCardStore.flow(context).collectAsState()
    val due by ReportDialog.state.due.collectAsState()
    val current = card ?: return
    if (!ReportDialogState.isDue(due, current)) return
    ReportReadyDialog(current, onNote, onClose = { ReportDialog.state.dismiss() })
}

/**
 * Die nummerierten Handgriffe (Zip in Downloads, Meldeseite öffnen, Kurzfassung einfügen und Zip anhängen) und die Knöpfe "Im Browser
 * öffnen" (Hauptknopf), "In der App öffnen", "Zip teilen" und "Schließen". Er bleibt offen, wenn der Spieler die Meldeseite öffnet,
 * damit er beim Zurückwechseln die Schritte noch sieht. Ließ sich die Zip nicht in Downloads ablegen, heißt Schritt 1 "Zip teilen".
 */
@Composable
internal fun ReportReadyDialog(card: ReportCard, onNote: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val textColor = LauncherColors.Body
    val saveAs = rememberSaveZipAs(card, onNote)
    val first = if (card.inDownloads) stringResource(R.string.game_pm_ready_step1, card.zipName) else stringResource(R.string.game_pm_ready_step1_share)
    val steps = listOf(first, stringResource(R.string.game_pm_ready_step2), stringResource(R.string.game_pm_ready_step3))
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.game_pm_card_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = LauncherColors.Gold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                steps.forEachIndexed { index, step -> Text("${index + 1}. $step", fontSize = 14.sp, lineHeight = 20.sp, color = textColor) }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(stringResource(R.string.game_pm_card_browser), ActionKind.Primary, enabled = card.issueUrl != null, modifier = Modifier.fillMaxWidth()) { ReportCardActions.openInBrowser(context, card) }
                    ActionButton(stringResource(R.string.game_pm_card_open), enabled = card.issueUrl != null, modifier = Modifier.fillMaxWidth()) { ReportCardActions.openIssue(context, card) }
                    ActionButton(stringResource(R.string.game_pm_card_share), modifier = Modifier.fillMaxWidth()) { ReportCardActions.shareZip(context, card, onNote) }
                    ActionButton(stringResource(R.string.game_pm_show_downloads), modifier = Modifier.fillMaxWidth()) { ReportCardActions.showDownloads(context, onNote) }
                    ActionButton(stringResource(R.string.game_pm_save_as), modifier = Modifier.fillMaxWidth()) { saveAs() }
                }
                Text(stringResource(R.string.game_pm_ready_hint), fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.Muted)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.game_pm_card_close), color = LauncherColors.Gold, fontWeight = FontWeight.Bold) } },
        containerColor = LauncherColors.SurfaceRaised,
        shape = RoundedCornerShape(16.dp),
    )
}
