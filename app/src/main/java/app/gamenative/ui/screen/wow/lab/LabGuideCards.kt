package app.gamenative.ui.screen.wow.lab

import android.content.Context
import androidx.compose.runtime.Composable
import app.gamenative.R
import app.gamenative.lab.CopyProgress
import app.gamenative.lab.GuideAction
import app.gamenative.lab.GuideFact
import app.gamenative.lab.GuideSource
import app.gamenative.lab.GuideState
import app.gamenative.lab.GuideStatus
import app.gamenative.lab.GuideStep
import app.gamenative.lab.LabCopyView
import app.gamenative.lab.LabGuideInputs
import app.gamenative.lab.LabGuideSelf
import app.gamenative.lab.LabGuideState
import app.gamenative.lab.LabPackage
import app.gamenative.lab.LabPackageKind
import app.gamenative.lab.LabQuickPicks
import app.gamenative.lab.LabRunEntry
import app.gamenative.lab.LabSetupCheckResult
import app.gamenative.lab.LabWatchState
import app.gamenative.lab.SetupFinding
import app.gamenative.lab.SetupItem
import app.gamenative.lab.SetupLevel
import app.gamenative.lab.SetupReason
import app.gamenative.ui.screen.wow.launcher.CardActionRow
import app.gamenative.ui.screen.wow.launcher.CardSwitchRow
import app.gamenative.ui.screen.wow.launcher.CardText
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Die Karten des Assistenten „Schritt für Schritt“ und des Selbsttests „Einrichtung prüfen“ für die Labor-Seite. Reine Anzeige: der Zustand kommt
 * fertig aus [LabGuide] und [app.gamenative.lab.LabSetupCheck], die Knöpfe rufen nur die Rückrufe der Seite (alles App-eigene; nichts klickt oder
 * tippt im Launcher). Jede Zustandszeile nennt ihre Quelle.
 */
internal class LabGuideCallbacks(
    val onAction: (GuideAction) -> Unit,
    val onSignedIn: (Boolean) -> Unit,
    val onNoCopy: (Boolean) -> Unit,
    val onRestart: () -> Unit,
)

internal object LabGuideText {
    fun date(ms: Long?): String =
        if (ms == null || ms <= 0L) "–" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))

    fun gb(bytes: Long): String = "%.1f".format(Locale.ROOT, bytes.coerceAtLeast(0L) / (1024.0 * 1024.0 * 1024.0))

    fun stepName(context: Context, step: GuideStep): String = context.getString(
        when (step) {
            GuideStep.PACKAGES -> R.string.lab_guide_step_1
            GuideStep.LAUNCHER -> R.string.lab_guide_step_2
            GuideStep.SETTING -> R.string.lab_guide_step_3
            GuideStep.SIGN_IN -> R.string.lab_guide_step_4
            GuideStep.COPY -> R.string.lab_guide_step_5
            GuideStep.GAME -> R.string.lab_guide_step_6
            GuideStep.PLAY -> R.string.lab_guide_step_7
        },
    )

    private fun whyRes(step: GuideStep): Int = when (step) {
        GuideStep.PACKAGES -> R.string.lab_guide_why_1
        GuideStep.LAUNCHER -> R.string.lab_guide_why_2
        GuideStep.SETTING -> R.string.lab_guide_why_3
        GuideStep.SIGN_IN -> R.string.lab_guide_why_4
        GuideStep.COPY -> R.string.lab_guide_why_5
        GuideStep.GAME -> R.string.lab_guide_why_6
        GuideStep.PLAY -> R.string.lab_guide_why_7
    }

    fun why(context: Context, step: GuideStep): String = context.getString(whyRes(step))

    fun stateName(context: Context, state: GuideState): String = context.getString(
        when (state) {
            GuideState.DONE -> R.string.lab_guide_state_done
            GuideState.OPEN -> R.string.lab_guide_state_open
            GuideState.RUNNING -> R.string.lab_guide_state_running
            GuideState.BLOCKED -> R.string.lab_guide_state_blocked
            GuideState.ERROR -> R.string.lab_guide_state_error
            GuideState.SKIPPED -> R.string.lab_guide_state_skipped
            GuideState.UNKNOWN -> R.string.lab_guide_state_unknown
        },
    )

    fun sourceName(context: Context, source: GuideSource): String = context.getString(
        when (source) {
            GuideSource.INSTALLED_PACKAGES -> R.string.lab_guide_src_installed_packages
            GuideSource.PACKAGE_LIST -> R.string.lab_guide_src_package_list
            GuideSource.FILE_CHECK -> R.string.lab_guide_src_file_check
            GuideSource.SETTINGS -> R.string.lab_guide_src_settings
            GuideSource.SELF_REPORT -> R.string.lab_guide_src_self_report
            GuideSource.LAST_RUN -> R.string.lab_guide_src_last_run
            GuideSource.COPY_MARK -> R.string.lab_guide_src_copy_mark
            GuideSource.APP_STATE -> R.string.lab_guide_src_app_state
            GuideSource.DISK_SPACE -> R.string.lab_guide_src_disk_space
        },
    )

    private fun factRes(fact: GuideFact): Int = when (fact) {
        GuideFact.PKG_FEX_INSTALLED -> R.string.lab_guide_fact_pkg_fex_installed
        GuideFact.PKG_FEX_MISSING -> R.string.lab_guide_fact_pkg_fex_missing
        GuideFact.PKG_FEX_NOT_LISTED -> R.string.lab_guide_fact_pkg_fex_not_listed
        GuideFact.LAUNCHER_FILE_PRESENT -> R.string.lab_guide_fact_launcher_file_present
        GuideFact.LAUNCHER_FILE_MISSING -> R.string.lab_guide_fact_launcher_file_missing
        GuideFact.INSTALLER_FILE_MISSING -> R.string.lab_guide_fact_installer_file_missing
        GuideFact.NO_GAME_CONTAINER -> R.string.lab_guide_fact_no_game_container
        GuideFact.SETTING_APPLIED -> R.string.lab_guide_fact_setting_applied
        GuideFact.SETTING_NOT_APPLIED -> R.string.lab_guide_fact_setting_not_applied
        GuideFact.SETTING_NEEDS_PACKAGE -> R.string.lab_guide_fact_setting_needs_package
        GuideFact.SIGNED_IN_CONFIRMED -> R.string.lab_guide_fact_signed_in_confirmed
        GuideFact.SIGNED_IN_NOT_CONFIRMED -> R.string.lab_guide_fact_signed_in_not_confirmed
        GuideFact.SIGN_IN_NEEDS_LAUNCHER -> R.string.lab_guide_fact_sign_in_needs_launcher
        GuideFact.MAIN_WINDOW_SEEN -> R.string.lab_guide_fact_main_window_seen
        GuideFact.MAIN_WINDOW_NOT_SEEN -> R.string.lab_guide_fact_main_window_not_seen
        GuideFact.NO_RUN_YET -> R.string.lab_guide_fact_no_run_yet
        GuideFact.COPY_SKIPPED -> R.string.lab_guide_fact_copy_skipped
        GuideFact.COPY_RUNNING -> R.string.lab_guide_fact_copy_running
        GuideFact.COPY_LAYOUT_FAILED -> R.string.lab_guide_fact_copy_layout_failed
        GuideFact.COPY_READY -> R.string.lab_guide_fact_copy_ready
        GuideFact.COPY_NOT_MOUNTED -> R.string.lab_guide_fact_copy_not_mounted
        GuideFact.COPY_NO_ORIGINAL -> R.string.lab_guide_fact_copy_no_original
        GuideFact.COPY_NO_SPACE -> R.string.lab_guide_fact_copy_no_space
        GuideFact.COPY_PARTIAL -> R.string.lab_guide_fact_copy_partial
        GuideFact.COPY_NONE -> R.string.lab_guide_fact_copy_none
        GuideFact.GAME_NOT_NEEDED -> R.string.lab_guide_fact_game_not_needed
        GuideFact.GAME_NEEDS_COPY -> R.string.lab_guide_fact_game_needs_copy
        GuideFact.GAME_MARKER_PRESENT -> R.string.lab_guide_fact_game_marker_present
        GuideFact.GAME_MARKER_UNRELIABLE -> R.string.lab_guide_fact_game_marker_unreliable
        GuideFact.GAME_NOT_INSTALLED -> R.string.lab_guide_fact_game_not_installed
        GuideFact.PLAY_NOT_NEEDED -> R.string.lab_guide_fact_play_not_needed
        GuideFact.PLAY_NEEDS_COPY -> R.string.lab_guide_fact_play_needs_copy
        GuideFact.GAME_WINDOW_SEEN -> R.string.lab_guide_fact_game_window_seen
        GuideFact.GAME_WINDOW_NOT_SEEN -> R.string.lab_guide_fact_game_window_not_seen
    }

    fun fact(context: Context, fact: GuideFact, atMs: Long?): String = context.getString(factRes(fact), date(atMs))

    fun watchName(context: Context, text: String): String = context.getString(
        when (text) {
            LabWatchState.STARTING.text -> R.string.lab_guide_watch_starting
            LabWatchState.WINDOW_OK.text -> R.string.lab_guide_watch_window_ok
            LabWatchState.NO_WINDOW.text -> R.string.lab_guide_watch_no_window
            LabWatchState.ERROR_WINDOW.text -> R.string.lab_guide_watch_error_window
            else -> R.string.lab_guide_watch_none
        },
    )

    fun yesNo(context: Context, value: Boolean): String = context.getString(if (value) R.string.lab_guide_yes else R.string.lab_guide_no)

    /** Die beiden Zeilen „Letzter Lauf“ (Zeit, Dauer, Ende-Grund, Abstürze, Zustand der Hänger-Erkennung am Ende, gesehene Fenster). */
    fun lastRunLines(context: Context, run: LabRunEntry): List<String> = listOf(
        context.getString(
            R.string.lab_guide_last_run,
            date(run.startedMs),
            run.durationSec,
            run.endReason.ifBlank { context.getString(R.string.lab_guide_end_unknown) },
        ),
        context.getString(
            R.string.lab_guide_last_run2,
            run.crashes,
            watchName(context, run.watchEnd),
            yesNo(context, run.mainWindowSeen),
            yesNo(context, run.gameWindowSeen),
        ),
    )

    fun itemName(context: Context, item: SetupItem): String = context.getString(
        when (item) {
            SetupItem.GAME_CONTAINER -> R.string.lab_setup_item_game_container
            SetupItem.FEX_PACKAGE -> R.string.lab_setup_item_fex_package
            SetupItem.PACKAGE_LIST -> R.string.lab_setup_item_package_list
            SetupItem.WINE -> R.string.lab_setup_item_wine
            SetupItem.PREFIX -> R.string.lab_setup_item_prefix
            SetupItem.LAUNCHER_FILE -> R.string.lab_setup_item_launcher_file
            SetupItem.FREE_SPACE -> R.string.lab_setup_item_free_space
            SetupItem.COPY -> R.string.lab_setup_item_copy
            SetupItem.COPY_DRIVE -> R.string.lab_setup_item_copy_drive
            SetupItem.ORIGINAL_FOLDER -> R.string.lab_setup_item_original_folder
            SetupItem.ORIGINAL_UNCHANGED -> R.string.lab_setup_item_original_unchanged
            SetupItem.ORIGINAL_NOT_REACHABLE -> R.string.lab_setup_item_original_not_reachable
        },
    )

    fun levelName(context: Context, level: SetupLevel): String = context.getString(
        when (level) {
            SetupLevel.OK -> R.string.lab_setup_level_ok
            SetupLevel.NOTE -> R.string.lab_setup_level_note
            SetupLevel.ERROR -> R.string.lab_setup_level_error
        },
    )

    private fun reasonRes(reason: SetupReason): Int = when (reason) {
        SetupReason.CONTAINER_PRESENT -> R.string.lab_setup_reason_container_present
        SetupReason.CONTAINER_MISSING -> R.string.lab_setup_reason_container_missing
        SetupReason.FEX_INSTALLED -> R.string.lab_setup_reason_fex_installed
        SetupReason.FEX_MISSING -> R.string.lab_setup_reason_fex_missing
        SetupReason.FEX_NOT_LISTED -> R.string.lab_setup_reason_fex_not_listed
        SetupReason.LIST_OK -> R.string.lab_setup_reason_list_ok
        SetupReason.LIST_ERRORS -> R.string.lab_setup_reason_list_errors
        SetupReason.LIST_FEX_ENTRY_INCOMPLETE -> R.string.lab_setup_reason_list_fex_entry_incomplete
        SetupReason.WINE_PRESENT -> R.string.lab_setup_reason_wine_present
        SetupReason.WINE_MISSING -> R.string.lab_setup_reason_wine_missing
        SetupReason.WINE_NOT_CHECKABLE -> R.string.lab_setup_reason_wine_not_checkable
        SetupReason.PREFIX_PRESENT -> R.string.lab_setup_reason_prefix_present
        SetupReason.PREFIX_NOT_CREATED -> R.string.lab_setup_reason_prefix_not_created
        SetupReason.LAUNCHER_PRESENT -> R.string.lab_setup_reason_launcher_present
        SetupReason.LAUNCHER_MISSING -> R.string.lab_setup_reason_launcher_missing
        SetupReason.SPACE_OK -> R.string.lab_setup_reason_space_ok
        SetupReason.SPACE_LOW -> R.string.lab_setup_reason_space_low
        SetupReason.COPY_COMPLETE -> R.string.lab_setup_reason_copy_complete
        SetupReason.COPY_NONE -> R.string.lab_setup_reason_copy_none
        SetupReason.COPY_RUNNING -> R.string.lab_setup_reason_copy_running
        SetupReason.COPY_PARTIAL -> R.string.lab_setup_reason_copy_partial
        SetupReason.COPY_LAYOUT_FAILED -> R.string.lab_setup_reason_copy_layout_failed
        SetupReason.COPY_SWITCH_ON_NOT_READY -> R.string.lab_setup_reason_copy_switch_on_not_ready
        SetupReason.DRIVE_MOUNTED -> R.string.lab_setup_reason_drive_mounted
        SetupReason.DRIVE_OFF -> R.string.lab_setup_reason_drive_off
        SetupReason.DRIVE_NOT_USED -> R.string.lab_setup_reason_drive_not_used
        SetupReason.ORIGINAL_PRESENT -> R.string.lab_setup_reason_original_present
        SetupReason.ORIGINAL_MISSING -> R.string.lab_setup_reason_original_missing
        SetupReason.UNCHANGED -> R.string.lab_setup_reason_unchanged
        SetupReason.CHANGED_UNCONFIRMED -> R.string.lab_setup_reason_changed_unconfirmed
        SetupReason.CHANGED_CONFIRMED -> R.string.lab_setup_reason_changed_confirmed
        SetupReason.NEVER_CHECKED -> R.string.lab_setup_reason_never_checked
        SetupReason.NO_DRIVE_HAS_ORIGINAL -> R.string.lab_setup_reason_no_drive_has_original
        SetupReason.ORIGINAL_REACHABLE -> R.string.lab_setup_reason_original_reachable
        SetupReason.NOT_CHECKABLE -> R.string.lab_setup_reason_not_checkable
    }

    /** Der Text eines Befunds; die Zahl dazu ist je nach Punkt eine Anzahl, GB oder ein Zeitpunkt (nie ein Pfad). */
    fun reason(context: Context, f: SetupFinding): String {
        val number = f.number
        val first = when {
            number == null -> ""
            f.item == SetupItem.FREE_SPACE -> gb(number)
            f.reason == SetupReason.LIST_OK || f.reason == SetupReason.LIST_ERRORS -> number.toString()
            else -> date(number)
        }
        return context.getString(reasonRes(f.reason), first, f.name.orEmpty())
    }

    fun setupLine(context: Context, f: SetupFinding): String =
        context.getString(R.string.lab_setup_line, levelName(context, f.level), itemName(context, f.item), reason(context, f))

    /** Das Paket, das der Knopf eines Schritts lädt: für FEX der bevorzugte Eintrag, sonst irgendein FEX-Eintrag; für den Installer der Installer-Eintrag. */
    fun packageFor(action: GuideAction, packages: List<LabPackage>): LabPackage? = when (action) {
        GuideAction.LOAD_FEX ->
            packages.firstOrNull { it.id == LabQuickPicks.FEX_PACKAGE_ID } ?: packages.firstOrNull { it.kind == LabPackageKind.FEX }
        GuideAction.LOAD_INSTALLER -> packages.firstOrNull { it.kind == LabPackageKind.INSTALLER }
        else -> null
    }

    private fun mb(bytes: Long): String = "%.1f".format(Locale.ROOT, bytes / (1024.0 * 1024.0))

    fun actionTitle(context: Context, action: GuideAction, packages: List<LabPackage>): String = when (action) {
        GuideAction.LOAD_FEX -> context.getString(R.string.lab_guide_btn_load_fex, packageFor(action, packages)?.let { mb(it.bytes) } ?: "?")
        GuideAction.LOAD_INSTALLER ->
            context.getString(R.string.lab_guide_btn_load_installer, packageFor(action, packages)?.takeIf { it.bytes > 0L }?.let { mb(it.bytes) } ?: "5")
        GuideAction.START_INSTALLER -> context.getString(R.string.lab_guide_btn_start_installer)
        GuideAction.SET_LOGIN_WAY -> context.getString(R.string.lab_guide_btn_set)
        GuideAction.START_LAUNCHER -> context.getString(R.string.lab_guide_btn_start_launcher)
        GuideAction.ENABLE_COPY -> context.getString(R.string.lab_guide_btn_enable_copy)
        GuideAction.ASK_COPY -> context.getString(R.string.lab_guide_btn_ask_copy)
        GuideAction.NONE -> ""
    }

    fun actionLabel(context: Context, action: GuideAction): String = context.getString(
        when (action) {
            GuideAction.LOAD_FEX, GuideAction.LOAD_INSTALLER -> R.string.lab_act_load
            GuideAction.SET_LOGIN_WAY -> R.string.lab_act_set
            GuideAction.ENABLE_COPY -> R.string.lab_act_on
            GuideAction.START_INSTALLER, GuideAction.START_LAUNCHER, GuideAction.ASK_COPY, GuideAction.NONE -> R.string.lab_act_start
        },
    )
}

private fun stateColor(state: GuideState) = when (state) {
    GuideState.ERROR, GuideState.BLOCKED -> LauncherColors.ErrText
    GuideState.DONE -> LauncherColors.Body
    else -> LauncherColors.Muted
}

/** Was die Zeilen des Assistenten von der Seite brauchen: laufende Arbeit und der Zustand der Kopie. */
internal class LabStepEnv(
    val busy: Boolean,
    /** Der Installer wird gerade geladen (dann ist sein Knopf „Abbrechen“). */
    val loadingInstaller: Boolean,
    /** „Einstellung setzen“ fand das Übersetzer-Paket nicht: dann steht in der Zeile 3 ein Knopf, der zu den Paketen führt. */
    val loginNeedsPackage: Boolean,
    val copy: LabCopyView?,
    val copyRunning: Boolean,
    val copyProgress: CopyProgress?,
)

internal class LabStepCallbacks(
    val onAction: (GuideAction) -> Unit,
    val onSignedIn: (Boolean) -> Unit,
    val onNoCopy: (Boolean) -> Unit,
    val onCancelInstaller: () -> Unit,
    val onAskCopy: () -> Unit,
    val onCancelCopy: () -> Unit,
    val onOpenPackages: () -> Unit,
)

/**
 * Der Inhalt einer aufgeklappten Schrittzeile: Warum-Satz, Zustand samt Quelle und der eigene Knopf des Schritts. Er steht auch im Zustand „erledigt“
 * da: „Installer neu starten“ und „Einstellung noch einmal setzen“ gibt es nur noch hier (Entwurf 3.3). Reine Anzeige; die Knöpfe rufen die Rückrufe der
 * Seite (alles App-eigene, nichts klickt oder tippt im Launcher).
 */
@Composable
internal fun LabStepBody(
    context: Context,
    s: GuideStatus,
    guide: LabGuideState,
    inputs: LabGuideInputs,
    packages: List<LabPackage>,
    self: LabGuideSelf,
    env: LabStepEnv,
    cb: LabStepCallbacks,
) {
    val isNext = guide.next == s.step
    CardText(LabGuideText.why(context, s.step), 4)
    CardText(
        context.getString(R.string.lab_guide_state_line, LabGuideText.stateName(context, s.state), LabGuideText.fact(context, s.fact, s.atMs)),
        4,
        stateColor(s.state),
    )
    CardText(context.getString(R.string.lab_guide_source_line, LabGuideText.sourceName(context, s.source)), 1)
    s.evidence?.let { e ->
        CardText(LabGuideText.fact(context, e.fact, e.atMs), 4)
        CardText(context.getString(R.string.lab_guide_source_line, LabGuideText.sourceName(context, e.fact.source)), 1)
    }
    when (s.step) {
        GuideStep.PACKAGES -> if (s.action == GuideAction.LOAD_FEX) StepButton(context, s.action, packages, isNext, env.busy, cb)
        GuideStep.LAUNCHER -> {
            // Laden und Starten stehen beide immer da (heute zwei Knöpfe in der Karte „Versuch“): das Laden lässt sich abbrechen, solange es läuft.
            CardActionRow(
                LabGuideText.actionTitle(context, GuideAction.LOAD_INSTALLER, packages),
                context.getString(if (env.loadingInstaller) R.string.lab_act_cancel else R.string.lab_act_load),
                primary = isNext && s.action == GuideAction.LOAD_INSTALLER,
                enabled = !env.busy || env.loadingInstaller,
            ) { if (env.loadingInstaller) cb.onCancelInstaller() else cb.onAction(GuideAction.LOAD_INSTALLER) }
            StepButton(context, GuideAction.START_INSTALLER, packages, isNext && s.action == GuideAction.START_INSTALLER, env.busy, cb)
        }
        GuideStep.SETTING -> {
            StepButton(context, GuideAction.SET_LOGIN_WAY, packages, isNext && s.action == GuideAction.SET_LOGIN_WAY, env.busy, cb)
            if (env.loginNeedsPackage) {
                CardActionRow(context.getString(R.string.lab_loginway_open_packages), context.getString(R.string.lab_act_open)) { cb.onOpenPackages() }
            }
        }
        GuideStep.SIGN_IN -> {
            StepButton(context, GuideAction.START_LAUNCHER, packages, isNext, env.busy, cb)
            CardSwitchRow(context.getString(R.string.lab_guide_signed_in_switch), self.signedInMs != null, enabled = !env.busy) { cb.onSignedIn(it) }
            CardText(context.getString(R.string.lab_guide_self_hint), 2)
            CardText(context.getString(R.string.lab_easy_signin_more), 3)
            CardText(context.getString(R.string.lab_signin_ladder_title), 2, LauncherColors.Body)
            CardText(context.getString(R.string.lab_signin_ladder_1), 2)
            CardText(context.getString(R.string.lab_signin_ladder_2), 2)
            CardText(context.getString(R.string.lab_signin_ladder_3), 3)
            CardText(context.getString(R.string.lab_signin_ladder_4), 4)
            CardText(context.getString(R.string.lab_signin_ladder_5), 2)
            CardText(context.getString(R.string.lab_signin_ladder_note), 2)
        }
        GuideStep.COPY -> {
            val v = env.copy
            if (v != null && v.originalPresent && !v.complete) {
                CardText(
                    context.getString(R.string.lab_guide_copy_numbers, LabGuideText.gb(inputs.copy.neededBytes), LabGuideText.gb(inputs.copy.freeBytes)),
                    2,
                    LauncherColors.Body,
                )
            }
            if (env.copyRunning) {
                env.copyProgress?.let { p ->
                    CardText(
                        context.getString(R.string.lab_copy_progress, p.filesDone, p.filesTotal, LabGuideText.gb(p.bytesDone), LabGuideText.gb(p.bytesTotal)),
                        2,
                        LauncherColors.Body,
                    )
                }
                CardActionRow(context.getString(R.string.lab_act_cancel), context.getString(R.string.lab_act_cancel), danger = true) { cb.onCancelCopy() }
            } else {
                CardActionRow(
                    context.getString(
                        when {
                            v?.partial == true -> R.string.lab_copy_continue
                            v?.complete == true -> R.string.lab_copy_refresh
                            else -> R.string.lab_copy_create
                        },
                    ),
                    context.getString(R.string.lab_act_start),
                    primary = isNext && s.action == GuideAction.ASK_COPY,
                    enabled = !env.busy && v != null && v.originalPresent && v.fits,
                ) { cb.onAskCopy() }
            }
            CardSwitchRow(context.getString(R.string.lab_guide_no_copy_switch), self.noCopyMs != null, enabled = !env.busy) { cb.onNoCopy(it) }
            CardText(context.getString(R.string.lab_guide_self_hint), 2)
        }
        GuideStep.GAME -> {
            CardText(context.getString(R.string.lab_guide_step6_hint), 6)
            if (s.action == GuideAction.START_LAUNCHER) StepButton(context, s.action, packages, isNext, env.busy, cb)
        }
        GuideStep.PLAY -> StepButton(context, GuideAction.START_LAUNCHER, packages, isNext, env.busy, cb)
    }
}

@Composable
private fun StepButton(context: Context, action: GuideAction, packages: List<LabPackage>, primary: Boolean, busy: Boolean, cb: LabStepCallbacks) {
    CardActionRow(
        LabGuideText.actionTitle(context, action, packages),
        LabGuideText.actionLabel(context, action),
        primary = primary,
        enabled = !busy,
    ) { cb.onAction(action) }
}

/** Die beiden Zeilen „Letzter Lauf“ (Gruppe Fehlersuche). */
@Composable
internal fun LabLastRunBody(context: Context, lastRun: LabRunEntry?) {
    if (lastRun == null) {
        CardText(context.getString(R.string.lab_guide_no_run), 1, LauncherColors.Body)
    } else {
        LabGuideText.lastRunLines(context, lastRun).forEach { CardText(it, 3, LauncherColors.Body) }
    }
}

/** „Einrichtung prüfen“ (Gruppe Fehlersuche): ein Knopf, darunter die Liste ok/Hinweis/Fehler der letzten Prüfung. */
@Composable
internal fun LabSetupBody(context: Context, result: LabSetupCheckResult?, running: Boolean, busy: Boolean, onCheck: () -> Unit) {
    CardActionRow(
        context.getString(R.string.lab_setup_row),
        context.getString(R.string.lab_act_check),
        primary = true,
        enabled = !busy && !running,
        onClick = onCheck,
    )
    CardText(context.getString(R.string.lab_setup_hint), 3)
    if (result == null) {
        CardText(context.getString(R.string.lab_setup_none), 1, LauncherColors.Body)
    } else {
        CardText(
            context.getString(
                R.string.lab_setup_done,
                LabGuideText.date(result.atMs),
                result.count(SetupLevel.OK),
                result.count(SetupLevel.NOTE),
                result.count(SetupLevel.ERROR),
            ),
            2,
            LauncherColors.Body,
        )
        result.findings.forEach { f ->
            CardText(
                LabGuideText.setupLine(context, f),
                2,
                when (f.level) {
                    SetupLevel.OK -> LauncherColors.Body
                    SetupLevel.NOTE -> LauncherColors.Gold
                    SetupLevel.ERROR -> LauncherColors.ErrText
                },
            )
        }
    }
}
