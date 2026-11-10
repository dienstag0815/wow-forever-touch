package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.ui.screen.wow.LauncherHomeActions
import app.gamenative.ui.screen.wow.LauncherHomeState
import app.gamenative.ui.screen.wow.SettingActionRow
import app.gamenative.ui.screen.wow.SettingGroupCard
import app.gamenative.ui.screen.wow.SettingRow
import app.gamenative.ui.screen.wow.WowClientDownloader.FLAVOR_DIR
import app.gamenative.ui.screen.wow.toHomeInput

/**
 * Seite "Spieldateien" als Übersicht: oben, wenn etwas fehlt, eine Hinweiskarte mit dem Knopf (Dateizugriff erlauben, installieren
 * oder aktualisieren), darunter der Stand mit "Status neu prüfen", der Spielordner und die Battle.net-Anmeldung. Die Karten zeigen
 * Zustand und Knöpfe; Pfad, freier Platz und die ausführlichen Erklärungen stehen in der Detailansicht (Antippen der Karte).
 */
@Composable
internal fun FilesPage(s: LauncherHomeState, a: LauncherHomeActions, onBack: () -> Unit) {
    val free = remember(s.gamePath, s.isUpdating, s.filesChecked) { GamePath.freeSpaceBytes(s.gamePath) }
    val ui = filesPageUi(s.toHomeInput(freeSpaceBytes = free))
    val enabled = ui.controlsEnabled
    val alert = when {
        ui.showGrantStorage -> FilesAlert.Grant
        ui.showInstall -> FilesAlert.Install
        ui.showUpdate -> FilesAlert.Update
        else -> FilesAlert.None
    }
    val plan = remember(alert) { filesPlan(alert) }
    var detailId by remember { mutableStateOf<String?>(null) }

    val cards = buildList<OverviewCard> {
        when (alert) {
            FilesAlert.Grant -> add(
                plan.card("alert", stringResource(R.string.launcher_files_access_needed)) {
                    CardText(stringResource(R.string.launcher_files_access_needed_body), 3, LauncherColors.Body)
                    Gap(8)
                    Box(modifier = Modifier.widthIn(max = ALERT_BUTTON_MAX_DP.dp)) {
                        PagePrimaryButton(LauncherTexts.GRANT_BUTTON.asString(), enabled, a.onGrantStorage)
                    }
                },
            )
            FilesAlert.Install -> add(
                plan.card(
                    "alert",
                    stringResource(if (ui.installIsUpgrade) R.string.launcher_files_beta_here else R.string.launcher_files_install_wow),
                ) {
                    CardText(installBody(ui.installIsUpgrade), 3, LauncherColors.Body)
                    Gap(8)
                    Box(modifier = Modifier.widthIn(max = ALERT_BUTTON_MAX_DP.dp)) {
                        PagePrimaryButton(
                            text = (if (ui.installIsUpgrade) LauncherTexts.UPGRADE_BUTTON else LauncherTexts.INSTALL_BUTTON).asString(),
                            enabled = enabled,
                            onClick = a.onInstall,
                        )
                    }
                },
            )
            FilesAlert.Update -> add(
                plan.card("alert", stringResource(R.string.launcher_files_update_needed)) {
                    CardText(stringResource(R.string.launcher_files_update_body), 3, LauncherColors.ErrText)
                    Gap(8)
                    Box(modifier = Modifier.widthIn(max = ALERT_BUTTON_MAX_DP.dp)) {
                        PagePrimaryButton(ui.updateText.asString(), enabled && s.canPlay, a.onUpdate)
                    }
                },
            )
            FilesAlert.None -> Unit
        }
        add(
            plan.card("state", stringResource(R.string.launcher_files_current_status), onOpen = { detailId = "state" }) {
                CardValueRow(title = ui.status.asString())
                CardActionRow(
                    stringResource(R.string.launcher_files_recheck), stringResource(R.string.launcher_action_verify),
                    enabled = enabled, onClick = a.onRefresh,
                )
                CardText("${s.gamePath}${freeSpaceSuffix(free).asString()}", 2)
            },
        )
        add(
            plan.card("folder", stringResource(R.string.launcher_files_game_folder), onOpen = { detailId = "folder" }) {
                CardActionRow(
                    stringResource(R.string.launcher_files_choose_folder), stringResource(R.string.launcher_action_choose),
                    enabled = enabled, onClick = a.onPickFolder,
                )
                CardText(PageTexts.FOLDER_HINT.asString(), 4)
            },
        )
        add(
            plan.card("login", stringResource(R.string.launcher_files_login), onOpen = { detailId = "login" }) {
                CardValueRow(title = stringResource(if (s.hasSavedLogin) R.string.launcher_ready_login_ok else R.string.launcher_files_no_login))
                CardActionRow(
                    title = stringResource(if (s.hasSavedLogin) R.string.launcher_files_change_login else R.string.launcher_files_setup_login),
                    actionText = stringResource(R.string.launcher_open),
                    enabled = enabled,
                    onClick = a.onLogin,
                )
                if (s.hasSavedLogin) {
                    CardActionRow(
                        stringResource(R.string.launcher_files_forget_login), stringResource(R.string.launcher_action_forget),
                        enabled = enabled, danger = true, onClick = a.onForgetLogin,
                    )
                }
            },
        )
    }

    val close: () -> Unit = { detailId = null }
    val detail: PageDetail? = when (detailId) {
        "state" -> PageDetail(stringResource(R.string.launcher_files_current_status), close) {
            SettingGroupCard(stringResource(R.string.launcher_files_current_status)) {
                SettingRow(title = ui.status.asString(), description = "${s.gamePath}${freeSpaceSuffix(free).asString()}")
                SettingActionRow(
                    title = stringResource(R.string.launcher_files_recheck),
                    description = stringResource(R.string.launcher_files_recheck_desc),
                    actionText = stringResource(R.string.launcher_action_verify),
                    enabled = enabled,
                    onClick = a.onRefresh,
                )
            }
        }
        "folder" -> PageDetail(stringResource(R.string.launcher_files_game_folder), close) {
            SettingGroupCard(stringResource(R.string.launcher_files_game_folder)) {
                SettingActionRow(
                    title = stringResource(R.string.launcher_files_choose_or_find),
                    description = PageTexts.FOLDER_HINT.asString(),
                    actionText = stringResource(R.string.launcher_action_choose),
                    enabled = enabled,
                    onClick = a.onPickFolder,
                )
            }
        }
        "login" -> PageDetail(stringResource(R.string.launcher_files_login_title), close) {
            SettingGroupCard(stringResource(R.string.launcher_files_login_title)) {
                SettingRow(
                    title = stringResource(if (s.hasSavedLogin) R.string.launcher_login_saved else R.string.launcher_files_no_login),
                    description = stringResource(R.string.launcher_files_login_desc),
                )
                SettingActionRow(
                    title = stringResource(if (s.hasSavedLogin) R.string.launcher_files_change_login else R.string.launcher_files_setup_login),
                    actionText = stringResource(R.string.launcher_open),
                    enabled = enabled,
                    onClick = a.onLogin,
                )
                if (s.hasSavedLogin) {
                    SettingActionRow(
                        title = stringResource(R.string.launcher_files_forget_login),
                        description = stringResource(R.string.launcher_files_forget_desc),
                        actionText = stringResource(R.string.launcher_action_forget),
                        danger = true,
                        enabled = enabled,
                        onClick = a.onForgetLogin,
                    )
                }
            }
        }
        else -> null
    }

    LauncherOverviewPage(title = LauncherTexts.pageTitle(LauncherPage.Files).asString(), onBack = onBack, cards = cards, detail = detail)
}

/** Breite, die der Knopf der Hinweiskarte höchstens bekommt (die Karte läuft über die ganze Seite). */
private const val ALERT_BUTTON_MAX_DP = 380

@Composable
private fun installBody(isUpgrade: Boolean): String =
    if (isUpgrade) stringResource(R.string.launcher_files_install_upgrade_body, FLAVOR_DIR) else stringResource(R.string.launcher_files_install_body)
