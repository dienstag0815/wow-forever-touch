package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.report.ErrorReport
import app.gamenative.ui.screen.wow.ErrorReportSettingRow
import app.gamenative.ui.screen.wow.GAME_ONLY_INTRO
import app.gamenative.ui.screen.wow.GAME_ONLY_TITLE
import app.gamenative.ui.screen.wow.LauncherHomeActions
import app.gamenative.ui.screen.wow.LauncherHomeState
import app.gamenative.ui.screen.wow.ReadinessSummary
import app.gamenative.ui.screen.wow.ReadyFix
import app.gamenative.ui.screen.wow.SettingGroupCard
import app.gamenative.ui.screen.wow.SettingNote
import app.gamenative.ui.screen.wow.SettingRow
import app.gamenative.ui.screen.wow.gameOnlyEntries
import app.gamenative.ui.screen.wow.readiness
import app.gamenative.update.UpdateLogic
import app.gamenative.update.UpdateManager
import app.gamenative.update.UpdateState

/**
 * Seite "Hilfe und Diagnose" als Übersicht: die Bereitschaftsliste mit den Knöpfen zur Lösung (zwei Spalten breit), die letzte
 * Fehlermeldung (nur wenn es eine gibt), der Fehlerbericht, ein Thema "Im Spiel ändern" und der Launcher (Update-Suche, Über diese
 * App). Die langen Texte (ganze Meldung, Erklärung des Fehlerberichts, Liste "Was kann ich im Spiel ändern?") stehen in der
 * Detailansicht der Karte.
 */
@Composable
internal fun HelpPage(s: LauncherHomeState, a: LauncherHomeActions, onBack: () -> Unit) {
    val phase by UpdateManager.checkPhase.collectAsState()
    val updateState by UpdateManager.state.collectAsState()
    val busy = s.busyText != null
    val context = LocalContext.current
    val summary = s.readiness()
    val errorMessage = s.errorMessage
    val plan = remember(errorMessage != null) { helpPlan(errorMessage != null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var reportNote by remember { mutableStateOf<String?>(null) }
    var identityReload by remember { mutableStateOf(0) }
    val identityPanel by produceIdentityPanel(identityReload)
    val identity = identityPanel?.summary
    val updateBusy = updateState is UpdateState.Checking || updateState is UpdateState.Downloading || updateState is UpdateState.Installing

    val cards = buildList<OverviewCard> {
        add(
            plan.card("ready", null) {
                ReadinessCard(
                    summary = summary,
                    onFix = { fix ->
                        when (fix) {
                            ReadyFix.StoragePermission -> a.onGrantStorage()
                            ReadyFix.Install -> a.onInstall()
                            ReadyFix.Login -> a.onLogin()
                            ReadyFix.Update -> a.onUpdate()
                            ReadyFix.None -> Unit
                        }
                    },
                    onRefresh = a.onRefresh,
                    fixesEnabled = !busy,
                )
            },
        )
        if (errorMessage != null) {
            add(
                plan.card("error", stringResource(R.string.launcher_help_last_message), onOpen = { detailId = "error" }) {
                    CardText(errorMessage, 6, LauncherColors.ErrText)
                },
            )
        }
        add(
            plan.card("report", stringResource(R.string.launcher_help_error_report), onOpen = { detailId = "report" }) {
                CardActionRow(stringResource(R.string.launcher_help_create_report), stringResource(R.string.launcher_action_create)) {
                    ErrorReport.start(context) { reportNote = it }
                }
                CardText(reportNote ?: PageTexts.REPORT_SHORT.asString(), 3)
                IdentityShortLine(identity)
            },
        )
        add(
            plan.card("gameonly", stringResource(R.string.launcher_help_in_game), onOpen = { detailId = "gameonly" }) {
                CardText(UiText.join(gameOnlyEntries().map { it.title }).asString(), 3)
            },
        )
        add(
            plan.card("launcher", stringResource(R.string.launcher_help_launcher)) {
                CardActionRow(
                    title = stringResource(R.string.launcher_help_update_check),
                    actionText = stringResource(if (updateState is UpdateState.Checking) R.string.launcher_home_checking else R.string.launcher_action_check),
                    enabled = !updateBusy,
                ) { UpdateManager.checkManually(context) }
                CardActionRow(stringResource(R.string.launcher_help_about_app), stringResource(R.string.launcher_open)) { a.onOpenPage(LauncherPage.About) }
                CardText(UiText.res(R.string.launcher_help_check_line, UpdateLogic.checkLine(phase, BuildConfig.VERSION_NAME)).asString(), 3)
            },
        )
    }

    val close: () -> Unit = { detailId = null }
    val detail: PageDetail? = when {
        detailId == "error" && errorMessage != null -> PageDetail(stringResource(R.string.launcher_help_last_message), close) {
            SettingGroupCard(stringResource(R.string.launcher_help_last_message)) {
                SettingRow(title = stringResource(R.string.launcher_help_error), description = errorMessage)
            }
        }
        detailId == "report" -> PageDetail(stringResource(R.string.launcher_help_error_report), close) {
            SettingGroupCard(stringResource(R.string.launcher_help_error_report)) {
                ErrorReportSettingRow()
            }
            IdentityDetailBody(identityPanel, gameBusy = busy || s.isLaunching, onChanged = { identityReload++ })
        }
        detailId == "gameonly" -> PageDetail(stringResource(R.string.launcher_help_in_game), close) {
            SettingGroupCard(GAME_ONLY_TITLE.asString()) {
                SettingNote(GAME_ONLY_INTRO.asString())
                for (entry in gameOnlyEntries()) {
                    SettingRow(title = entry.title.asString(), description = entry.text.asString())
                }
            }
        }
        else -> null
    }

    LauncherOverviewPage(title = LauncherTexts.pageTitle(LauncherPage.Help).asString(), onBack = onBack, cards = cards, detail = detail)
}

/** Die Bereitschaftsliste: Kopfzeile ("Alles bereit" oder "Offen: ...") mit Neu prüfen und darunter jeder Punkt, offene mit Knopf. */
@Composable
private fun ReadinessCard(summary: ReadinessSummary, onFix: (ReadyFix) -> Unit, onRefresh: () -> Unit, fixesEnabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (READY_HEAD_DP - 1f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusMark(ready = summary.allReady, size = 20)
        Text(
            text = summary.headline.asString().replace(" ✓", ""),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = if (summary.allReady || !summary.checked) LauncherColors.Body else LauncherColors.Warn,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.launcher_files_recheck), tint = LauncherColors.Muted, modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(color = LauncherColors.Divider)
    for (item in summary.items) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = READY_ITEM_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusMark(ready = item.ready, size = 18)
            Text(
                text = item.label.asString(),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = if (item.ready) LauncherColors.Body else LauncherColors.Warn,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            )
            if (!item.ready && item.fix != ReadyFix.None) {
                TextButton(onClick = { onFix(item.fix) }, enabled = fixesEnabled) {
                    Text(item.fix.buttonText?.asString().orEmpty(), fontSize = 14.sp, color = LauncherColors.Gold)
                }
            }
        }
    }
}

@Composable
private fun StatusMark(ready: Boolean, size: Int) {
    Icon(
        imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.Warning,
        contentDescription = null,
        tint = if (ready) LauncherColors.Ok else LauncherColors.Warn,
        modifier = Modifier.size(size.dp),
    )
}
