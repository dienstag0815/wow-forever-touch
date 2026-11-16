package app.gamenative.ui.screen.wow.lab

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.lab.CopyOutcome
import app.gamenative.lab.CopyProgress
import app.gamenative.lab.EasyHeadline
import app.gamenative.lab.EasyMain
import app.gamenative.lab.EasyMark
import app.gamenative.lab.GuideStep
import app.gamenative.lab.LabContainer
import app.gamenative.lab.LabCopyDelete
import app.gamenative.lab.LabCopyView
import app.gamenative.lab.LabGameCopyJob
import app.gamenative.lab.LabOriginalCheck
import app.gamenative.lab.OriginalCheckState
import app.gamenative.lab.PathCheck
import app.gamenative.lab.LabDirs
import app.gamenative.lab.LabDownloader
import app.gamenative.lab.LabFailure
import app.gamenative.lab.LabFlow
import app.gamenative.lab.LabEasyState
import app.gamenative.lab.LabGuard
import app.gamenative.lab.LabIds
import app.gamenative.lab.LabInstalledPackage
import app.gamenative.lab.LabIssue
import app.gamenative.lab.LabIssueCode
import app.gamenative.lab.LabField
import app.gamenative.lab.LabLauncher
import app.gamenative.lab.LabNextNote
import app.gamenative.lab.LabNextStart
import app.gamenative.lab.LabNextStarts
import app.gamenative.lab.LabPackage
import app.gamenative.lab.LabPackageCheck
import app.gamenative.lab.LabPackageErrorCode
import app.gamenative.lab.GuideAction
import app.gamenative.lab.LabFileFacts
import app.gamenative.lab.LabGuide
import app.gamenative.lab.LabGroup
import app.gamenative.lab.LabGuideInputs
import app.gamenative.lab.LabGuideState
import app.gamenative.lab.LabGuideSelf
import app.gamenative.lab.LabGuideSources
import app.gamenative.lab.LabSetupCheck
import app.gamenative.lab.LabSetupCheckResult
import app.gamenative.lab.LabLoginWay
import app.gamenative.lab.LabLoginWayResult
import app.gamenative.lab.LabPackageFlow
import app.gamenative.lab.LabPackageKind
import app.gamenative.lab.LabProtocol
import app.gamenative.lab.LabPackages
import app.gamenative.lab.LabQuickPick
import app.gamenative.lab.LabStabilities
import app.gamenative.lab.LabStability
import app.gamenative.lab.LabStabilityResult
import app.gamenative.lab.LabQuickPickResult
import app.gamenative.lab.LabQuickPicks
import app.gamenative.lab.LabReport
import app.gamenative.lab.LabRestart
import app.gamenative.lab.LabRun
import app.gamenative.lab.LabRunEntry
import app.gamenative.lab.LabSettings
import app.gamenative.lab.LabStart
import app.gamenative.lab.LabStore
import app.gamenative.lab.LabTarget
import app.gamenative.lab.LabTranslator
import app.gamenative.lab.LabStartWay
import app.gamenative.lab.LabStepResult
import app.gamenative.lab.LabStepTest
import app.gamenative.lab.LabWineDebug
import app.gamenative.lab.LabX11Preset
import app.gamenative.ui.screen.wow.SettingActionRow
import app.gamenative.ui.screen.wow.SettingGroupCard
import app.gamenative.ui.screen.wow.SettingRow
import app.gamenative.ui.screen.wow.launcher.CardActionRow
import app.gamenative.ui.screen.wow.launcher.CardChoiceRow
import app.gamenative.ui.screen.wow.launcher.CardSize
import app.gamenative.ui.screen.wow.launcher.CardSwitchRow
import app.gamenative.ui.screen.wow.launcher.CardText
import app.gamenative.ui.screen.wow.launcher.CardValueRow
import app.gamenative.ui.screen.wow.launcher.ExpandRow
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.ui.screen.wow.launcher.LauncherOverviewPage
import app.gamenative.ui.screen.wow.launcher.OverviewCard
import app.gamenative.ui.screen.wow.launcher.SPAN_ALL
import app.gamenative.ui.screen.wow.launcher.cardHeightDp
import app.gamenative.ui.util.SnackbarManager
import com.winlator.contents.ContentsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Die Labor-Seite (versteckt, freigeschaltet auf der Hilfeseite): Battle.net in einer zweiten Wine-Umgebung `bnetlab` versuchen, mit
 * vielen Einstellungen ohne neuen Bau und einem Protokoll je Start. Nichts läuft von selbst; jeder Versuch beginnt mit einem Tipp.
 * [onLaunch] startet den X-Server-Bildschirm mit der Kennung des Labor-Containers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BnetLabPage(onBack: () -> Unit, onLaunch: (String) -> Unit, startNote: String? = null, startPartB: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(LabStore.settings(context)) }
    // Ansicht: „Erweitert“ ist bei jedem Öffnen der Seite zu (mit dem Schalter „immer geöffnet“ offen, dann nur die erste Gruppe); höchstens eine Zeile und eine Gruppe offen.
    var alwaysOpen by remember { mutableStateOf(LabStore.advancedAlwaysOpen(context)) }
    var advancedOpen by rememberSaveable { mutableStateOf(alwaysOpen) }
    var openGroupName by rememberSaveable { mutableStateOf<String?>(if (alwaysOpen) LabGroup.entries.first().name else null) }
    var openStepNumber by rememberSaveable { mutableIntStateOf(0) }
    var whatOpen by rememberSaveable { mutableStateOf(false) }
    // Teil B („Wenn der direkte Start abgelehnt wird“) ist eingeklappt; die Startseite öffnet ihn, wenn „Über Battle.net“ ohne Teil B gewählt wurde.
    var partBOpen by rememberSaveable { mutableStateOf(startPartB) }
    var signHintsOpen by rememberSaveable { mutableStateOf(false) }
    var helpTick by remember { mutableIntStateOf(0) }
    val helpRequester = remember { BringIntoViewRequester() }
    // [startNote]: der eine Satz der Startseite, wenn sie hierher führt (was für den Start über Battle.net fehlt).
    var note by remember { mutableStateOf<String?>(startNote) }
    // „Einstellung setzen“ fand das Übersetzer-Paket nicht: dann zeigt die Schrittzeile 3 den Knopf zu den Paketen.
    var loginNeedsPackage by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    // „Launcher neu starten“ aus der Tastenleiste hat den Lauf beendet: das Angebot „Erneut starten“ (nur auf Tipp, verfällt nach zehn Minuten).
    val restartTarget = remember { LabRestart.pending() }
    var busyId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(-1) }
    var refresh by remember { mutableIntStateOf(0) }
    var installed by remember { mutableStateOf<List<LabInstalledPackage>>(emptyList()) }
    var issues by remember { mutableStateOf<List<LabIssue>>(emptyList()) }
    var usedBytes by remember { mutableStateOf(0L) }
    var bundledWine by remember { mutableStateOf(LabIds.FALLBACK_WINE) }
    var stepResult by remember { mutableStateOf<LabStepResult?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var runs by remember { mutableStateOf(LabStore.runs(context)) }
    val cancel = remember { AtomicBoolean(false) }
    var copyView by remember { mutableStateOf<LabCopyView?>(null) }
    var copyProgress by remember { mutableStateOf<CopyProgress?>(null) }
    var copyRunning by remember { mutableStateOf(LabGameCopyJob.isRunning()) }
    var askCopy by remember { mutableStateOf(false) }
    var askSignInKeys by remember { mutableStateOf(false) }
    var askDeleteCopy by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf(OriginalCheckState()) }
    // Assistent: Dateifragen (nur Existenz), Selbstauskünfte, Rückfrage vor dem Laden eines Pakets, Selbsttest.
    var facts by remember { mutableStateOf<LabFileFacts?>(null) }
    var guideSelf by remember { mutableStateOf(LabStore.guideSelf(context)) }
    var askPackage by remember { mutableStateOf<LabPackage?>(null) }
    var setupResult by remember { mutableStateOf(LabStore.setupCheck(context)) }
    var setupRunning by remember { mutableStateOf(false) }

    val list = remember {
        val text = runCatching { context.assets.open("bnetlab_pakete.json").use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        LabPackages.parse(text)
    }

    LaunchedEffect(refresh) {
        installed = withContext(Dispatchers.IO) { runCatching { LabLauncher.installedPackages(context) }.getOrDefault(emptyList()) }
        usedBytes = withContext(Dispatchers.IO) { runCatching { LabContainer.usedBytes(context) }.getOrDefault(0L) }
        bundledWine = withContext(Dispatchers.IO) { runCatching { LabContainer.readBase(context)?.wineVersion }.getOrNull() } ?: LabIds.FALLBACK_WINE
        stepResult = withContext(Dispatchers.IO) { runCatching { LabStepTest.lastResult(LabDirs.share(context)) }.getOrNull() }
        runs = withContext(Dispatchers.IO) { LabStore.runs(context) }
        // Ein offener Abdruck des Originalordners (App dazwischen beendet) wird hier nachgeholt, dann die Anzeige gelesen.
        original = withContext(Dispatchers.IO) { runCatching { LabOriginalCheck.recover(context); LabOriginalCheck.state(context) }.getOrDefault(OriginalCheckState()) }
        copyView = withContext(Dispatchers.IO) { runCatching { LabGameCopyJob.view(context) }.getOrNull() }
        facts = withContext(Dispatchers.IO) { runCatching { LabGuideSources.fileFacts(context) }.getOrNull() }
        guideSelf = LabStore.guideSelf(context)
    }
    // Solange kopiert wird: Fortschritt abfragen; am Ende die Meldung zeigen. Das Kopieren selbst läuft in `LabGameCopyJob`, nicht hier.
    LaunchedEffect(copyRunning) {
        if (!copyRunning) return@LaunchedEffect
        while (LabGameCopyJob.isRunning()) {
            copyProgress = LabGameCopyJob.progress
            kotlinx.coroutines.delay(700)
        }
        val out = LabGameCopyJob.takeOutcome()
        copyRunning = false
        copyProgress = null
        if (out != null) note = copyOutcomeText(context, out)
        refresh++
    }
    LaunchedEffect(settings, installed, refresh) {
        issues = withContext(Dispatchers.IO) {
            runCatching {
                val bnet = LabLauncher.check(context, settings, LabTarget.BATTLENET).filter { it.code != LabIssueCode.BNET_NOT_FOUND }
                val any = if (settings.anyProgram.isBlank()) emptyList() else LabLauncher.check(context, settings, LabTarget.ANY).filter { it.field == LabField.ANY_PROGRAM }
                bnet + any
            }.getOrDefault(emptyList())
        }
    }

    fun update(changed: LabSettings) {
        // Stufen 4 und 5 (FEX) gelten nur, solange der Übersetzer FEX gewählt ist; sonst fällt die Stufe auf Stufe 1.
        val new = LabStabilities.normalize(changed)
        if (new.stability == LabStability.CAREFUL && new.stability != changed.stability) note = context.getString(R.string.lab_stab_fell_back)
        settings = new
        LabStore.save(context, new)
    }

    fun stepText(r: LabStepResult?): String = when {
        r == null -> context.getString(R.string.lab_step_none)
        r.finished -> context.getString(R.string.lab_step_done)
        r.installerMissing -> context.getString(R.string.lab_step_noinstaller, r.lastCompleted)
        r.lastBegun == 0 -> context.getString(R.string.lab_step_nothing)
        else -> context.getString(R.string.lab_step_reached, r.lastBegun, r.lastCompleted)
    }

    fun mb(bytes: Long): String = "%.1f".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024.0))

    fun issueText(issue: LabIssue): String {
        val res = when (issue.code) {
            LabIssueCode.ENV_NO_EQUALS -> R.string.lab_issue_env_no_equals
            LabIssueCode.ENV_BAD_NAME -> R.string.lab_issue_env_bad_name
            LabIssueCode.ENV_RESERVED -> R.string.lab_issue_env_reserved
            LabIssueCode.ENV_HAS_FIELD -> R.string.lab_issue_env_has_field
            LabIssueCode.ENV_BAD_VALUE -> R.string.lab_issue_env_bad_value
            LabIssueCode.DLL_BAD_ENTRY -> R.string.lab_issue_dll_bad_entry
            LabIssueCode.DEBUG_BAD_ENTRY -> R.string.lab_issue_debug_bad_entry
            LabIssueCode.LINE_UNBALANCED_QUOTE -> R.string.lab_issue_unbalanced_quote
            LabIssueCode.LINE_EMPTY -> R.string.lab_issue_line_empty
            LabIssueCode.LINE_NOT_WINDOWS_PATH -> R.string.lab_issue_not_windows_path
            LabIssueCode.BNET_NOT_FOUND -> R.string.lab_issue_bnet_not_found
            LabIssueCode.SIZE_INVALID -> R.string.lab_issue_size_invalid
            LabIssueCode.WINE_MISSING -> R.string.lab_issue_wine_missing
            LabIssueCode.FEX_MISSING -> R.string.lab_issue_fex_missing
        }
        val text = context.getString(res, issue.text)
        return if (issue.line != null) context.getString(R.string.lab_issue_line, issue.line, text) else text
    }

    fun failureText(reason: ContentsManager.InstallFailedReason?): String = context.getString(
        when (reason) {
            ContentsManager.InstallFailedReason.ERROR_NOSPACE -> R.string.lab_pkg_fail_nospace
            ContentsManager.InstallFailedReason.ERROR_BADTAR -> R.string.lab_pkg_fail_badtar
            ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> R.string.lab_pkg_fail_noprofile
            ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> R.string.lab_pkg_fail_badprofile
            ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> R.string.lab_pkg_fail_missing
            ContentsManager.InstallFailedReason.ERROR_EXIST -> R.string.lab_pkg_fail_exist
            ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> R.string.lab_pkg_fail_untrust
            else -> R.string.lab_pkg_fail_unknown
        },
    )

    fun flowText(flow: LabFlow): String = when (flow) {
        is LabFlow.Installed -> context.getString(R.string.lab_pkg_installed_ok, flow.verName)
        is LabFlow.AlreadyInstalled -> context.getString(R.string.lab_pkg_already, flow.name)
        is LabFlow.InstallerSaved -> {
            val saved = context.getString(R.string.lab_installer_saved, mb(flow.bytes), flow.sha256)
            if (flow.differs) saved + "\n" + context.getString(R.string.lab_installer_differs) else saved
        }
        is LabFlow.Rejected -> when (val c = flow.check) {
            is LabPackageCheck.SizeMismatch -> context.getString(R.string.lab_pkg_size_mismatch, c.expected, c.actual)
            is LabPackageCheck.HashMismatch -> context.getString(R.string.lab_pkg_hash_mismatch)
            else -> context.getString(R.string.lab_pkg_insecure)
        }
        is LabFlow.InstallFailed ->
            if (flow.failure.wrongType) context.getString(R.string.lab_pkg_fail_wrong_type) else context.getString(R.string.lab_pkg_fail, failureText(flow.failure.reason))
        is LabFlow.DownloadFailed -> context.getString(
            R.string.lab_download_failed,
            when (flow.code) {
                LabDownloader.DownloadException.Code.INSECURE -> context.getString(R.string.lab_dl_insecure)
                LabDownloader.DownloadException.Code.HTTP -> context.getString(R.string.lab_dl_http)
                LabDownloader.DownloadException.Code.TOO_MANY_REDIRECTS -> context.getString(R.string.lab_dl_redirects)
                LabDownloader.DownloadException.Code.IO -> context.getString(R.string.lab_dl_io)
                else -> context.getString(R.string.lab_dl_other)
            },
        )
        LabFlow.Cancelled -> context.getString(R.string.lab_download_cancelled)
    }

    fun loadPackage(pkg: LabPackage) {
        if (busyId != null) return
        busyId = pkg.id
        progress = 0
        cancel.set(false)
        note = null
        scope.launch {
            val flow = withContext(Dispatchers.IO) {
                LabPackageFlow.loadAndInstall(context, pkg, { cancel.get() }) { done, total ->
                    val p = if (total > 0) (done * 100 / total).toInt() else -1
                    if (p != progress) progress = p
                }
            }
            busyId = null
            progress = -1
            note = flowText(flow)
            refresh++
        }
    }

    fun start(target: LabTarget) {
        if (busyId != null) return
        scope.launch {
            val result = withContext(Dispatchers.IO) { LabLauncher.prepare(context, target) }
            when (result) {
                is LabStart.Ready -> {
                    LabRestart.clear()
                    note = context.getString(R.string.lab_started)
                    onLaunch(LabIds.CONTAINER_ID)
                }
                is LabStart.Blocked -> note = context.getString(
                    if (result.block == LabGuard.Block.GAME_RUNNING) R.string.lab_blocked_game else R.string.lab_blocked_lab,
                )
                LabStart.OriginalChanged -> {
                    note = context.getString(R.string.lab_blocked_original)
                    // Der rote Block mit „Bestätigen“ steht in der Gruppe „Spielordner und Platz“: dorthin führen.
                    advancedOpen = true
                    openGroupName = LabGroup.GAME_FOLDER.name
                    refresh++
                }
                is LabStart.Invalid -> {
                    note = context.getString(R.string.lab_invalid_head) + "\n" + result.issues.joinToString("\n") { issueText(it) }
                    // Die Felder mit den Fehlern stehen in „Technik“ (Wine, Übersetzer, Fenstergröße) oder in „Entwicklung“ (Zeilen, Umgebung).
                    advancedOpen = true
                    openGroupName = (if (result.issues.firstOrNull()?.field in setOf(LabField.WINE, LabField.TRANSLATOR, LabField.SCREEN)) LabGroup.TECH else LabGroup.DEVELOPMENT).name
                }
                is LabStart.Failed -> note = when (result.failure) {
                    LabFailure.NO_BASE -> context.getString(R.string.lab_fail_no_base)
                    LabFailure.INSTALLER_MISSING -> context.getString(R.string.lab_fail_installer_missing)
                    LabFailure.PREPARE_FAILED -> if (result.detail.isBlank()) context.getString(R.string.lab_fail_prepare_nodetail) else context.getString(R.string.lab_fail_prepare, result.detail)
                    LabFailure.COPY_RUNNING -> context.getString(R.string.lab_fail_copy_running)
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && busyId == null) {
            busyId = "file"
            progress = -1
            note = null
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    val name = runCatching {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
                    }.getOrNull() ?: "?"
                    val hash = LabPackageFlow.hashFile(context, uri)
                    if (hash == null) {
                        context.getString(R.string.lab_pkg_fail, context.getString(R.string.lab_pkg_fail_unknown))
                    } else {
                        val flow = LabPackageFlow.installFromFile(context, uri, name, hash.first, hash.second)
                        context.getString(R.string.lab_pkg_from_file_result, name, mb(hash.first), hash.second) + "\n" + flowText(flow)
                    }
                }
                busyId = null
                note = text
                refresh++
            }
        }
    }

    fun quick(pick: LabQuickPick) {
        when (val r = LabQuickPicks.resolve(pick, settings, installed, list.packages)) {
            is LabQuickPickResult.Applied -> {
                update(r.settings)
                note = context.getString(R.string.lab_quick_applied, quickName(context, pick))
            }
            is LabQuickPickResult.Missing -> {
                val names = r.packageIds.joinToString(", ") { id -> list.packages.firstOrNull { it.id == id }?.name ?: id }
                note = context.getString(R.string.lab_quick_missing, names)
            }
        }
    }

    // „Einstellung setzen“ (Schritt 3, vorher auch die Karte „Anmeldeweg“): lässt den Schalter der Kopie des Spiels, wie er ist (die Kopie wird oft schon vorher eingeschaltet).
    fun setLoginWayForGuide() {
        when (val r = LabLoginWay.applyKeepingCopy(settings, installed, list.packages)) {
            is LabLoginWayResult.Applied -> {
                update(r.settings)
                loginNeedsPackage = false
                note = context.getString(R.string.lab_guide_set_done, r.settings.fexVersion)
            }
            is LabLoginWayResult.FexMissing -> {
                // Nichts ändern und nichts laden: der Nutzer lädt das Paket selbst (Gruppe Technik, Pakete).
                loginNeedsPackage = true
                note = context.getString(
                    R.string.lab_loginway_missing,
                    r.packageIds.joinToString(", ") { id -> list.packages.firstOrNull { it.id == id }?.name ?: id },
                )
            }
        }
    }

    fun saveGuideSelf(new: LabGuideSelf) {
        guideSelf = new
        LabStore.saveGuideSelf(context, new)
    }

    // Der Knopf eines Schritts und „Weiter“: nur App-eigene Aktionen. Ein Paket wird erst nach einer Rückfrage mit der Größe geladen.
    fun runGuideAction(action: GuideAction) {
        when (action) {
            GuideAction.LOAD_FEX, GuideAction.LOAD_INSTALLER -> askPackage = LabGuideText.packageFor(action, list.packages)
            GuideAction.START_INSTALLER -> start(LabTarget.INSTALLER)
            GuideAction.SET_LOGIN_WAY -> setLoginWayForGuide()
            GuideAction.START_LAUNCHER -> start(LabTarget.BATTLENET)
            GuideAction.ENABLE_COPY -> {
                update(settings.copy(useGameCopy = true))
                note = context.getString(R.string.lab_guide_copy_on_done)
            }
            GuideAction.ASK_COPY -> askCopy = true
            GuideAction.NONE -> Unit
        }
    }

    // Selbsttest: liest nur Dateien, Einstellungen und Marken (kein Wine-Start); das Ergebnis geht auch in den nächsten Bericht.
    fun checkSetup() {
        if (setupRunning) return
        setupRunning = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val f = LabGuideSources.fileFacts(context)
                    val copy = LabGuideSources.copyOf(copyView, copyRunning)
                    val r = LabSetupCheck.run(LabGuideSources.setupInputs(context, f, settings, list, installed, copy), System.currentTimeMillis())
                    LabStore.saveSetupCheck(context, r)
                    r
                }.getOrNull()
            }
            if (result != null) setupResult = result
            setupRunning = false
        }
    }

    fun chooseStability(stage: LabStability) {
        when (val r = LabStabilities.select(stage, settings, installed, list.packages)) {
            is LabStabilityResult.Applied -> {
                update(r.settings)
                note = context.getString(R.string.lab_stab_applied, stage.level, stabilityName(context, stage))
            }
            LabStabilityResult.FexMissing -> note = context.getString(R.string.lab_stab_fex_unavailable)
        }
    }

    fun errorsOf(field: LabField): String? =
        issues.filter { it.field == field }.takeIf { it.isNotEmpty() }?.joinToString("\n") { issueText(it) }

    val next = LabNextStarts.describe(settings, installed, bundledWine)
    val wines = installed.filter { it.kind == LabPackageKind.WINE && it.identifier != null }
    val fexes = installed.filter { it.kind == LabPackageKind.FEX }

    // Ansicht „Einfach“ (Entwurf 3.1) und „Erweitert“ mit fünf Gruppen (3.4). Dieselben Aktionen wie vorher, nur anders angeordnet; die Zuordnung steht in `LabItems`.
    val guideInputs: LabGuideInputs? = facts?.let { f ->
        LabGuideSources.guideInputs(f, settings, installed, list.packages, guideSelf, LabGuideSources.copyOf(copyView, copyRunning), runs)
    }
    val guideState: LabGuideState? = guideInputs?.let { LabGuide.evaluate(it) }
    val easy: LabEasyState? = if (guideInputs != null && guideState != null) LabEasyState.of(guideState, guideInputs, restartTarget != null) else null
    val anyBusy = busyId != null || copyRunning
    // Die eine Statuszeile (Laden, Kopieren, letzte Meldung): unter dem Hauptknopf, solange keine Gruppe von „Erweitert“ offen ist, sonst oben in der offenen Gruppe.
    val shownProgress = copyProgress
    val status = when {
        busyId != null && progress >= 0 -> context.getString(R.string.lab_loading, progress)
        busyId != null -> context.getString(R.string.lab_pkg_busy)
        copyRunning && shownProgress != null ->
            context.getString(R.string.lab_copy_progress, shownProgress.filesDone, shownProgress.filesTotal, gb(shownProgress.bytesDone), gb(shownProgress.bytesTotal))
        else -> note.orEmpty()
    }
    val statusInGroup = advancedOpen && openGroupName != null

    fun openGroupAt(group: LabGroup) {
        advancedOpen = true
        openGroupName = group.name
    }

    val openStep = GuideStep.entries.firstOrNull { it.number == openStepNumber }
    val openGroup = LabGroup.entries.firstOrNull { it.name == openGroupName }

    val head: EasyHeadUi? = easy?.let { e ->
        val stepName = e.step?.let { LabGuideText.stepName(context, it) }.orEmpty()
        EasyHeadUi(
            sentence = context.getString(
                when (e.headline) {
                    EasyHeadline.NEXT -> R.string.lab_easy_next
                    EasyHeadline.WAITING -> R.string.lab_easy_wait
                    EasyHeadline.ALL_DONE -> R.string.lab_easy_all_done
                    EasyHeadline.PART_A_DONE -> R.string.lab_easy_part_a_done
                    EasyHeadline.STUCK -> R.string.lab_easy_stuck
                    EasyHeadline.RESTART_PENDING -> R.string.lab_easy_restart_pending
                },
                stepName,
            ),
            sentenceIsError = e.headline == EasyHeadline.STUCK,
            detail = if (e.headline == EasyHeadline.STUCK) context.getString(R.string.lab_guide_restart_hint) else null,
            risk = context.getString(R.string.lab_easy_risk),
            mainText = when (e.main) {
                EasyMain.CONTINUE -> context.getString(R.string.lab_easy_btn_continue, stepName)
                EasyMain.START -> context.getString(R.string.lab_easy_btn_start)
                EasyMain.DIRECT_START -> context.getString(R.string.lab_easy_btn_direct)
                EasyMain.RESTART -> context.getString(R.string.lab_easy_btn_restart)
                EasyMain.RESTART_PENDING -> context.getString(R.string.lab_act_again)
                EasyMain.NONE -> null
            },
            mainEnabled = when (e.main) {
                EasyMain.RESTART_PENDING -> busyId == null
                EasyMain.DIRECT_START -> true
                else -> !anyBusy && e.mainUsable
            },
            showWhat = e.main == EasyMain.CONTINUE,
        )
    }
    // Teil B: der Knopf „Weiter: …“ gilt nur, wenn der nächste offene Schritt der Anleitung in Teil B liegt und eine Aktion hat.
    val partBNext = guideState?.next?.takeIf { it in LabGuide.PART_B }
    val partBUi = EasyPartBUi(
        open = partBOpen,
        continueText = if (partBNext != null && guideState?.continueAction != GuideAction.NONE) {
            context.getString(R.string.lab_easy_btn_continue, LabGuideText.stepName(context, partBNext))
        } else null,
        continueEnabled = !anyBusy,
    )
    val onMain: () -> Unit = {
        when (easy?.main) {
            EasyMain.CONTINUE, EasyMain.START -> runGuideAction(easy.action)
            EasyMain.RESTART -> start(LabTarget.BATTLENET)
            EasyMain.RESTART_PENDING -> restartTarget?.let { start(it) }
            // Zurück auf die Startseite: dort startet „Spielen“ mit der Startart Direkt den unveränderten Direktstart.
            EasyMain.DIRECT_START -> onBack()
            else -> Unit
        }
    }
    val stepEnv = LabStepEnv(anyBusy, busyId == "installer-battlenet-setup", loginNeedsPackage && guideInputs?.fexInstalled != true, copyView, copyRunning, copyProgress)
    val stepCallbacks = LabStepCallbacks(
        onAction = { runGuideAction(it) },
        onSignedIn = { on -> saveGuideSelf(guideSelf.copy(signedInMs = if (on) System.currentTimeMillis() else null)) },
        onNoCopy = { on -> saveGuideSelf(guideSelf.copy(noCopyMs = if (on) System.currentTimeMillis() else null)) },
        onCancelInstaller = { cancel.set(true) },
        onAskCopy = { askCopy = true },
        onCancelCopy = { LabGameCopyJob.cancel() },
        onOpenPackages = { openGroupAt(LabGroup.TECH) },
    )

    val easyCard = OverviewCard(
        "easy",
        CardSize(
            cardHeightDp(
                titled = false,
                rows = if (guideState == null) 1 else 10,
                textLines = if (guideState == null) 2 else 9 + (if (head?.detail != null) 5 else 0),
                extraDp = 40f + (if (openStep != null) 420f else 0f) + (if (openStep == GuideStep.SIGN_IN) 340f else 0f) + (if (whatOpen) 100f else 0f) + (if (partBOpen) 60f else 0f),
            ),
            SPAN_ALL,
            flex = false,
        ),
        null,
    ) {
        if (head != null && guideState != null && guideInputs != null) {
            LabEasyContent(
                head = head,
                status = if (statusInGroup) "" else status,
                whatText = stringResource(R.string.lab_guide_continue_note),
                whatOpen = whatOpen,
                onToggleWhat = { whatOpen = !whatOpen },
                steps = guideState.steps.map { EasyStepUi(it.step, easy?.marks?.get(it.step) ?: EasyMark.OPEN, LabGuideText.stepName(context, it.step)) },
                openStep = openStep,
                onToggleStep = { step -> openStepNumber = if (openStepNumber == step.number) 0 else step.number },
                partB = partBUi,
                onTogglePartB = { partBOpen = !partBOpen },
                onPartBContinue = { runGuideAction(guideState.continueAction) },
                stepBody = { step ->
                    LabStepBody(context, guideState.status(step), guideState, guideInputs, list.packages, guideSelf, stepEnv, stepCallbacks)
                },
                onMain = onMain,
                onHelp = {
                    openGroupAt(LabGroup.TROUBLESHOOTING)
                    helpTick++
                },
            )
        } else {
            CardText(stringResource(R.string.lab_easy_risk), 2, LauncherColors.Body)
        }
    }

    val groupBody: @Composable ColumnScope.(LabGroup) -> Unit = { group ->
        if (status.isNotEmpty() && statusInGroup) CardText(status, 8, LauncherColors.Body)
        when (group) {
            LabGroup.TROUBLESHOOTING -> {
                GroupHeading(stringResource(R.string.lab_adv_last_run))
                LabLastRunBody(context, LabGuide.lastLauncherRun(runs))
                GroupHeading(stringResource(R.string.lab_card_setup))
                LabSetupBody(context, setupResult, setupRunning, busyId != null) { checkSetup() }
                GroupHeading(stringResource(R.string.lab_card_report))
                CardActionRow(stringResource(R.string.lab_report_share), stringResource(R.string.lab_act_share), primary = true) {
                    LabReport.start(context) { note = it }
                }
                GroupHeading(stringResource(R.string.lab_card_runs))
                if (runs.isEmpty()) CardText(stringResource(R.string.lab_runs_none), 1)
                runs.forEach { CardText(runText(context, it), 2, LauncherColors.Body) }
                CardActionRow(stringResource(R.string.lab_runs_clear), stringResource(R.string.lab_act_clear), danger = true, enabled = runs.isNotEmpty()) {
                    LabStore.clearRuns(context)
                    runs = emptyList()
                }
                GroupHeading(stringResource(R.string.lab_adv_tests))
                CardActionRow(stringResource(R.string.lab_step_start), stringResource(R.string.lab_act_start), enabled = busyId == null) { start(LabTarget.STEP_TEST) }
                CardText(stepText(stepResult), 3, LauncherColors.Body)
                CardSwitchRow(stringResource(R.string.lab_stab_translator_log), settings.translatorLog) { update(settings.copy(translatorLog = it)) }
                CardText(stringResource(R.string.lab_stab_translator_log_desc), 2)
                CardActionRow(stringResource(R.string.lab_kill_all), stringResource(R.string.lab_act_end), danger = true) {
                    note = context.getString(if (LabLauncher.killAll()) R.string.lab_stopped else R.string.lab_stop_refused)
                }
            }
            LabGroup.SIGN_IN -> {
                // Anmelde-Knöpfe der Tastenleiste: Einschalten nur nach der Rückfrage, Ausschalten sofort.
                CardSwitchRow(stringResource(R.string.lab_signin_keys), settings.signInKeys) { on -> if (on) askSignInKeys = true else update(settings.copy(signInKeys = false)) }
                CardText(stringResource(R.string.lab_signin_keys_desc), 5)
                ExpandRow(title = stringResource(R.string.lab_easy_signin_hints), expanded = signHintsOpen, onToggle = { signHintsOpen = !signHintsOpen })
                if (signHintsOpen) {
                    CardText(stringResource(R.string.lab_signin_hint), 3)
                    CardText(stringResource(R.string.lab_loginway_hint_repeat), 3)
                    CardText(stringResource(R.string.lab_loginway_hint_vendor), 3)
                    CardText(stringResource(R.string.lab_login_hint_experience), 9)
                    CardText(stringResource(R.string.lab_loginway_hint_gear), 5)
                    CardText(stringResource(R.string.lab_loginway_hint_refused), 3)
                }
            }
            LabGroup.GAME_FOLDER -> {
                val v = copyView
                GroupHeading(stringResource(R.string.lab_card_copy))
                CardText(stringResource(R.string.lab_copy_intro), 3, LauncherColors.Body)
                if (v == null || !v.originalPresent) {
                    CardText(stringResource(R.string.lab_copy_no_original), 2, LauncherColors.ErrText)
                } else {
                    CardText(stringResource(R.string.lab_copy_original, v.originalFiles, gb(v.originalBytes)), 1, LauncherColors.Body)
                    if (v.originalSkipped > 0) CardText(stringResource(R.string.lab_copy_skipped, v.originalSkipped), 2)
                    CardText(stringResource(R.string.lab_copy_space, gb(v.freeBytes), gb(v.needed)), 2, LauncherColors.Body)
                    if (!v.fits) CardText(stringResource(R.string.lab_copy_no_space, gb(v.needed - v.freeBytes)), 2, LauncherColors.ErrText)
                    v.info?.takeIf { it.complete }?.let { CardText(stringResource(R.string.lab_copy_done, it.files, gb(it.bytes), it.seconds), 2, LauncherColors.Body) }
                    if (v.partial) CardText(stringResource(R.string.lab_copy_partial), 3)
                    v.layoutProblem?.let { CardText(stringResource(R.string.lab_copy_layout_problem, layoutDetail(it)), 3, LauncherColors.ErrText) }
                }
                CardSwitchRow(stringResource(R.string.lab_copy_use), settings.useGameCopy) { update(settings.copy(useGameCopy = it)) }
                CardText(stringResource(R.string.lab_copy_use_desc), 3)
                if (settings.useGameCopy && v?.complete != true) CardText(stringResource(R.string.lab_copy_use_missing), 2, LauncherColors.ErrText)
                CardText(stringResource(R.string.lab_copy_hint), 4)
                CardActionRow(
                    stringResource(R.string.lab_copy_delete),
                    stringResource(R.string.lab_act_delete),
                    danger = true,
                    enabled = !copyRunning && (v?.info != null || v?.partial == true),
                ) { askDeleteCopy = true }
                // Nachweis "Originalordner unverändert": rot und sperrend, wenn ein Lauf ihn verändert hat und es nicht bestätigt ist.
                GroupHeading(stringResource(R.string.lab_card_original))
                if (original.needsAck) {
                    Column(modifier = Modifier.fillMaxWidth().background(LauncherColors.ErrFill)) {
                        CardText(stringResource(R.string.lab_orig_changed), 4, LauncherColors.ErrText)
                        original.deviationLines.take(6).forEach { CardText(it, 1, LauncherColors.ErrText) }
                        CardActionRow(stringResource(R.string.lab_orig_ack), stringResource(R.string.lab_orig_ack), danger = true) {
                            LabOriginalCheck.acknowledge(context)
                            original = LabOriginalCheck.state(context)
                            note = context.getString(R.string.lab_orig_acked)
                        }
                    }
                } else if (original.unchanged == true) {
                    CardText(stringResource(R.string.lab_orig_clean, LabProtocol.timestamp(original.checkedMs)), 2, LauncherColors.Body)
                } else {
                    CardText(stringResource(R.string.lab_orig_never), 2, LauncherColors.Body)
                }
                GroupHeading(stringResource(R.string.lab_adv_storage))
                CardValueRow(stringResource(R.string.lab_storage), stringResource(R.string.lab_storage_mb, mb(usedBytes)))
                CardActionRow(stringResource(R.string.lab_delete), stringResource(R.string.lab_act_delete), danger = true) {
                    if (LabRun.isActive() || LabGameCopyJob.isRunning()) note = context.getString(R.string.lab_delete_blocked) else askDelete = true
                }
            }
            LabGroup.TECH -> {
                // Womit der nächste Start läuft: aus den gespeicherten Einstellungen und den installierten Paketen, nicht aus dem letzten Lauf.
                GroupHeading(stringResource(R.string.lab_next_title))
                CardText(nextText(context, next), 3 + 2 + 2 * (next.stabilityLines.joinToString(", ").length / 70 + 1), LauncherColors.Body)
                next.notes.forEach { CardText(nextNoteText(context, it, next), 3, LauncherColors.ErrText) }
                if (LabNextNote.FEX_INSTALLED_NOT_CHOSEN in next.notes) {
                    CardActionRow(stringResource(R.string.lab_next_choose_fex), stringResource(R.string.lab_act_choose)) { quick(LabQuickPick.PROTON11_FEX) }
                }
                // Stabilität: genau eine von sechs Stufen (Vorgabe Stufe 1).
                GroupHeading(stringResource(R.string.lab_card_stability))
                val fexNames = fexes.map { it.verName }
                val fexUsable = LabStabilities.selectable(LabStability.FEX, fexNames)
                CardChoiceRow(
                    title = stringResource(R.string.lab_card_stability),
                    value = stringResource(R.string.lab_stab_row, next.stability.level, stabilityName(context, next.stability)),
                    options = LabStability.entries.toList(),
                    selected = next.stability,
                    optionLabel = { stage ->
                        context.getString(R.string.lab_stab_row, stage.level, stabilityName(context, stage)) +
                            (if (stage == LabStability.FEX && fexUsable) " " + context.getString(R.string.lab_stab_recommended) else "")
                    },
                    enabled = busyId == null,
                ) { chooseStability(it) }
                CardText(stringResource(stabilityDescRes(next.stability)), 3, LauncherColors.Body)
                if (!fexUsable) CardText(stringResource(R.string.lab_stab_fex_unavailable), 2, LauncherColors.ErrText)
                CardText(stringResource(R.string.lab_stab_lines_user), 2)
                // Schnellwahl: eine Zeile statt drei Knöpfen.
                GroupHeading(stringResource(R.string.lab_quick_title))
                CardChoiceRow<LabQuickPick?>(
                    title = stringResource(R.string.lab_quick_title),
                    value = stringResource(R.string.lab_act_choose),
                    options = LabQuickPick.entries.toList(),
                    selected = null,
                    optionLabel = { pick -> if (pick == null) "" else quickName(context, pick) },
                ) { pick -> if (pick != null) quick(pick) }
                CardText(stringResource(R.string.lab_quick_1_note), 3)
                GroupHeading(stringResource(R.string.lab_adv_packages_title))
                CardText(stringResource(R.string.lab_adv_expl_wine), 3)
                CardChoiceRow(
                    title = stringResource(R.string.lab_set_wine),
                    value = settings.wine.ifBlank { stringResource(R.string.lab_set_wine_bundled) },
                    options = listOf("") + wines.mapNotNull { it.identifier },
                    selected = settings.wine,
                    optionLabel = { it.ifBlank { context.getString(R.string.lab_set_wine_bundled) } },
                ) { update(settings.copy(wine = it)) }
                errorsOf(LabField.WINE)?.let { ErrorText(it) }
                CardText(stringResource(R.string.lab_adv_expl_translator), 3)
                CardChoiceRow(
                    title = stringResource(R.string.lab_set_translator),
                    value = translatorLabel(context, settings.translator),
                    options = LabTranslator.entries.toList(),
                    selected = settings.translator,
                    optionLabel = { translatorLabel(context, it) },
                ) { update(settings.copy(translator = it)) }
                if (settings.translator == LabTranslator.FEX) {
                    CardChoiceRow(
                        title = stringResource(R.string.lab_set_fex),
                        value = settings.fexVersion.ifBlank { stringResource(R.string.lab_set_choose_none) },
                        options = fexes.map { it.verName },
                        selected = settings.fexVersion,
                        optionLabel = { it },
                    ) { update(settings.copy(fexVersion = it)) }
                }
                errorsOf(LabField.TRANSLATOR)?.let { ErrorText(it) }
                // Pakete: die Liste zum Laden und die Liste der nachgeladenen.
                GroupHeading(stringResource(R.string.lab_card_packages))
                CardText(stringResource(R.string.lab_adv_expl_packages), 3)
                if (list.packages.isEmpty()) SettingRow(title = stringResource(R.string.lab_pkg_none))
                list.packages.forEach { pkg ->
                    val loading = busyId == pkg.id
                    val have = LabPackages.installedMatch(pkg, installed)
                    val size = if (pkg.bytes > 0) stringResource(R.string.lab_pkg_size, mb(pkg.bytes)) else ""
                    val title = pkg.name + if (have != null) " (" + stringResource(R.string.lab_pkg_installed_tag) + ")" else ""
                    SettingActionRow(
                        title = title,
                        description = listOf(
                            have?.let { stringResource(R.string.lab_pkg_installed_as, "${it.verName}-${it.verCode}") }.orEmpty(),
                            pkg.version,
                            size,
                            stringResource(R.string.lab_pkg_license, pkg.license),
                            pkg.note,
                            if (loading && progress >= 0) stringResource(R.string.lab_loading, progress) else "",
                        ).filter { it.isNotBlank() }.joinToString("\n"),
                        actionText = if (loading) stringResource(R.string.lab_act_cancel) else stringResource(R.string.lab_act_load),
                        enabled = busyId == null || loading,
                    ) { if (loading) cancel.set(true) else loadPackage(pkg) }
                }
                GroupHeading(stringResource(R.string.lab_pkg_installed_list))
                if (installed.isEmpty()) SettingRow(title = stringResource(R.string.lab_pkg_installed_empty))
                installed.forEach { SettingRow(title = "${it.kind.key}: ${it.verName}-${it.verCode}") }
                GroupHeading(stringResource(R.string.lab_set_screen))
                LabTextField(settings.screenSize, stringResource(R.string.lab_set_screen), stringResource(R.string.lab_set_screen_hint), 1, errorsOf(LabField.SCREEN)) {
                    update(settings.copy(screenSize = it))
                }
                CardSwitchRow(stringResource(R.string.lab_set_identity), settings.sameIdentity) { update(settings.copy(sameIdentity = it)) }
                CardText(stringResource(R.string.lab_set_identity_desc), 4)
                CardActionRow(stringResource(R.string.lab_set_reset), stringResource(R.string.lab_act_reset)) { askReset = true }
            }
            LabGroup.DEVELOPMENT -> {
                CardText(stringResource(R.string.lab_adv_dev_intro), 2)
                CardActionRow(stringResource(R.string.lab_any_start), stringResource(R.string.lab_act_start), enabled = busyId == null) { start(LabTarget.ANY) }
                GroupHeading(stringResource(R.string.lab_set_startway))
                CardChoiceRow(
                    title = stringResource(R.string.lab_set_startway),
                    value = startWayLabel(context, settings.startWay),
                    options = LabStartWay.entries.toList(),
                    selected = settings.startWay,
                    optionLabel = { startWayLabel(context, it) },
                ) { update(settings.copy(startWay = it)) }
                CardText(stringResource(R.string.lab_startway_desc), 4)
                LabTextField(settings.battleNetLine, stringResource(R.string.lab_set_bnet_line), stringResource(R.string.lab_set_bnet_line_hint), 2, errorsOf(LabField.START_LINE)) {
                    update(settings.copy(battleNetLine = it))
                }
                LabTextField(settings.anyProgram, stringResource(R.string.lab_set_any_program), stringResource(R.string.lab_set_any_program_hint), 2, errorsOf(LabField.ANY_PROGRAM)) {
                    update(settings.copy(anyProgram = it))
                }
                GroupHeading(stringResource(R.string.lab_set_env))
                LabTextField(settings.extraEnv, stringResource(R.string.lab_set_env), stringResource(R.string.lab_set_env_hint), 5, errorsOf(LabField.ENV)) {
                    update(settings.copy(extraEnv = it))
                }
                LabTextField(settings.dllOverrides, stringResource(R.string.lab_set_dll), stringResource(R.string.lab_set_dll_hint), 4, errorsOf(LabField.DLL)) {
                    update(settings.copy(dllOverrides = it))
                }
                CardText(stringResource(R.string.lab_set_suggestions), 1)
                LabQuickPicks.ENV_SUGGESTIONS.forEach { suggestion ->
                    SettingActionRow(title = suggestion, actionText = stringResource(R.string.lab_act_add)) {
                        val (field, line) = LabQuickPicks.suggestionTarget(suggestion)
                        update(
                            if (field == LabField.DLL) settings.copy(dllOverrides = LabQuickPicks.addLine(settings.dllOverrides, line))
                            else settings.copy(extraEnv = LabQuickPicks.addLine(settings.extraEnv, line)),
                        )
                    }
                }
                CardChoiceRow(
                    title = stringResource(R.string.lab_set_x11),
                    value = x11Label(context, settings.x11),
                    options = LabX11Preset.entries.toList(),
                    selected = settings.x11,
                    optionLabel = { x11Label(context, it) },
                ) { update(settings.copy(x11 = it)) }
                CardChoiceRow(
                    title = stringResource(R.string.lab_set_debug),
                    value = debugLabel(context, settings.wineDebug),
                    options = LabWineDebug.entries.toList(),
                    selected = settings.wineDebug,
                    optionLabel = { debugLabel(context, it) },
                ) { update(settings.copy(wineDebug = it)) }
                if (settings.wineDebug == LabWineDebug.CUSTOM) {
                    LabTextField(settings.wineDebugCustom, stringResource(R.string.lab_set_debug_custom), stringResource(R.string.lab_set_debug_custom_hint), 1, errorsOf(LabField.DEBUG)) {
                        update(settings.copy(wineDebugCustom = it))
                    }
                }
                GroupHeading(stringResource(R.string.lab_adv_packages_dev))
                list.errors.forEach { e ->
                    SettingRow(title = stringResource(R.string.lab_pkg_list_error, e.index, e.id, packageErrorText(context, e.code)))
                }
                CardActionRow(stringResource(R.string.lab_pkg_from_file), stringResource(R.string.lab_act_choose), enabled = busyId == null) { picker.launch(arrayOf("*/*")) }
                CardActionRow(stringResource(R.string.lab_hide), stringResource(R.string.lab_act_hide)) {
                    LabStore.setEntryEnabled(context, false)
                    SnackbarManager.show(context.getString(R.string.lab_hide_done))
                    onBack()
                }
            }
        }
    }

    val advancedCard = OverviewCard(
        "advanced",
        CardSize(
            cardHeightDp(
                titled = false,
                rows = 1 + (if (advancedOpen) LabGroup.entries.size + 1 else 0),
                textLines = 1,
                extraDp = if (advancedOpen && openGroup != null) 600f else 0f,
            ),
            SPAN_ALL,
            flex = false,
        ),
        null,
    ) {
        LabAdvancedContent(
            open = advancedOpen,
            onToggle = { advancedOpen = !advancedOpen },
            openGroup = openGroup,
            onToggleGroup = { group -> openGroupName = if (openGroupName == group.name) null else group.name },
            helpRequester = helpRequester,
            body = groupBody,
            footer = {
                CardSwitchRow(stringResource(R.string.lab_easy_advanced_always), alwaysOpen) { on ->
                    alwaysOpen = on
                    LabStore.setAdvancedAlwaysOpen(context, on)
                }
                CardText(stringResource(R.string.lab_easy_advanced_always_desc), 3)
            },
        )
    }

    // „Hilfe, es klappt nicht“ öffnet die Gruppe Fehlersuche und scrollt dorthin (nur wenn die Seite scrollt).
    LaunchedEffect(helpTick) {
        if (helpTick > 0) {
            kotlinx.coroutines.delay(60)
            runCatching { helpRequester.bringIntoView() }
        }
    }

    LauncherOverviewPage(title = stringResource(R.string.lab_easy_page_title), onBack = onBack, cards = listOf(easyCard, advancedCard))

    if (askSignInKeys) {
        AlertDialog(
            onDismissRequest = { askSignInKeys = false },
            title = { Text(stringResource(R.string.lab_signin_keys_ask_title)) },
            text = { Text(stringResource(R.string.lab_signin_keys_ask)) },
            confirmButton = {
                TextButton(onClick = {
                    askSignInKeys = false
                    update(settings.copy(signInKeys = true))
                }) { Text(stringResource(R.string.lab_act_on)) }
            },
            dismissButton = { TextButton(onClick = { askSignInKeys = false }) { Text(stringResource(R.string.lab_act_cancel)) } },
        )
    }
    if (askCopy) {
        val v = copyView
        AlertDialog(
            onDismissRequest = { askCopy = false },
            title = { Text(stringResource(R.string.lab_copy_ask_title)) },
            text = {
                Text(
                    when {
                        v == null -> ""
                        v.complete -> stringResource(R.string.lab_copy_ask_refresh, v.originalFiles, gb(v.originalBytes), gb(v.freeBytes), gb(v.needed))
                        else -> stringResource(R.string.lab_copy_ask, v.originalFiles, gb(v.originalBytes), gb(v.freeBytes), gb(v.needed))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askCopy = false
                    if (LabGameCopyJob.start(context)) {
                        copyRunning = true
                        note = context.getString(R.string.lab_copy_started)
                    } else {
                        note = context.getString(R.string.lab_copy_busy)
                    }
                }) { Text(stringResource(R.string.lab_copy_confirm)) }
            },
            dismissButton = { TextButton(onClick = { askCopy = false }) { Text(stringResource(R.string.lab_act_cancel)) } },
        )
    }
    askPackage?.let { pkg ->
        // Rückfrage vor dem Laden aus dem Assistenten: Name, Größe und Quelle (Rechnername); der Ablauf selbst ist der der Seite „Pakete“.
        val host = runCatching { java.net.URI(pkg.url).host }.getOrNull().orEmpty()
        AlertDialog(
            onDismissRequest = { askPackage = null },
            title = { Text(stringResource(R.string.lab_guide_ask_pkg_title, pkg.name)) },
            text = {
                Text(
                    stringResource(R.string.lab_guide_ask_pkg, if (pkg.bytes > 0L) mb(pkg.bytes) else "~5", host) +
                        if (LabPackages.isAdvisory(pkg)) "\n" + stringResource(R.string.lab_guide_ask_pkg_unverified) else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askPackage = null
                    loadPackage(pkg)
                }) { Text(stringResource(R.string.lab_act_load)) }
            },
            dismissButton = { TextButton(onClick = { askPackage = null }) { Text(stringResource(R.string.lab_act_cancel)) } },
        )
    }
    if (askDeleteCopy) {
        AlertDialog(
            onDismissRequest = { askDeleteCopy = false },
            title = { Text(stringResource(R.string.lab_copy_delete_ask_title)) },
            text = { Text(stringResource(R.string.lab_copy_delete_ask)) },
            confirmButton = {
                TextButton(onClick = {
                    askDeleteCopy = false
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { LabGameCopyJob.delete(context) }.getOrDefault(LabCopyDelete.Result.REFUSED) }
                        note = context.getString(if (result == LabCopyDelete.Result.REFUSED) R.string.lab_copy_delete_refused else R.string.lab_copy_delete_done)
                        refresh++
                    }
                }) { Text(stringResource(R.string.lab_copy_delete), color = LauncherColors.Err) }
            },
            dismissButton = { TextButton(onClick = { askDeleteCopy = false }) { Text(stringResource(R.string.lab_act_cancel)) } },
        )
    }
    if (askReset) {
        // Neu seit dem Umbau (Entwurf 3.5): „Alle Einstellungen zurücksetzen“ fragt vorher nach, es löscht sonst ohne Rückfrage.
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text(stringResource(R.string.lab_set_reset_ask_title)) },
            text = { Text(stringResource(R.string.lab_set_reset_ask)) },
            confirmButton = {
                TextButton(onClick = {
                    askReset = false
                    settings = LabStore.reset(context)
                    note = context.getString(R.string.lab_set_reset_done)
                }) { Text(stringResource(R.string.lab_act_reset), color = LauncherColors.Err) }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text(stringResource(R.string.lab_act_cancel)) } },
        )
    }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(stringResource(R.string.lab_delete_ask_title)) },
            text = { Text(stringResource(R.string.lab_delete_ask, mb(usedBytes))) },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { LabContainer.deleteAll(context) }.getOrDefault(false) }
                        note = context.getString(if (ok) R.string.lab_delete_done else R.string.lab_delete_failed)
                        refresh++
                    }
                }) { Text(stringResource(R.string.lab_confirm_delete), color = LauncherColors.Err) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text(stringResource(R.string.lab_act_cancel)) } },
        )
    }
}

private fun gb(bytes: Long): String = "%.2f".format(java.util.Locale.ROOT, bytes.coerceAtLeast(0L) / (1024.0 * 1024.0 * 1024.0))

private fun copyOutcomeText(context: android.content.Context, out: CopyOutcome): String = when (out) {
    is CopyOutcome.Done -> context.getString(R.string.lab_copy_done, out.info.files, gb(out.info.bytes), out.info.seconds)
    is CopyOutcome.Cancelled -> context.getString(R.string.lab_copy_cancelled)
    is CopyOutcome.Refused -> context.getString(R.string.lab_copy_refused)
    is CopyOutcome.Failed -> if (out.reason == CopyOutcome.Reason.LAYOUT) {
        context.getString(R.string.lab_copy_failed_layout, out.rel.ifEmpty { "-" })
    } else context.getString(
        when (out.reason) {
            CopyOutcome.Reason.CHANGED_DURING_COPY -> R.string.lab_copy_failed_changed
            CopyOutcome.Reason.INCOMPLETE -> R.string.lab_copy_failed_incomplete
            else -> R.string.lab_copy_failed_io
        },
    )
}

/** Kurzer Grund eines gescheiterten Umzugs für die Karte: Art und Eintrag (nur der Name, kein Pfad). */
private fun layoutDetail(f: app.gamenative.lab.LayoutResult.Failed): String =
    f.reason.name.lowercase().replace('_', ' ') + if (f.rel.isEmpty()) "" else ": ${f.rel}"

private fun quickName(context: android.content.Context, pick: LabQuickPick): String = context.getString(
    when (pick) {
        LabQuickPick.PROTON11_BUNDLED -> R.string.lab_quick_1
        LabQuickPick.PROTON11_FEX -> R.string.lab_quick_2
        LabQuickPick.PROTON10_FEX -> R.string.lab_quick_3
    },
)

private fun translatorLabel(context: android.content.Context, t: LabTranslator): String = context.getString(
    when (t) {
        LabTranslator.BUNDLED -> R.string.lab_tr_bundled
        LabTranslator.FEX -> R.string.lab_tr_fex
        LabTranslator.NONE -> R.string.lab_tr_none
    },
)

private fun startWayLabel(context: android.content.Context, w: LabStartWay): String = context.getString(
    when (w) {
        LabStartWay.DIRECT -> R.string.lab_startway_direct
        LabStartWay.WINHANDLER -> R.string.lab_startway_winhandler
    },
)

private fun x11Label(context: android.content.Context, p: LabX11Preset): String = context.getString(
    when (p) {
        LabX11Preset.DEFAULT -> R.string.lab_x11_default
        LabX11Preset.TAKE_FOCUS_ON -> R.string.lab_x11_take_focus_on
        LabX11Preset.TAKE_FOCUS_OFF -> R.string.lab_x11_take_focus_off
        LabX11Preset.NO_XIM -> R.string.lab_x11_no_xim
        LabX11Preset.UNMANAGED -> R.string.lab_x11_unmanaged
    },
)

private fun debugLabel(context: android.content.Context, d: LabWineDebug): String = context.getString(
    when (d) {
        LabWineDebug.OFF -> R.string.lab_dbg_off
        LabWineDebug.ERRORS -> R.string.lab_dbg_errors
        LabWineDebug.DIAGNOSE -> R.string.lab_dbg_diagnose
        LabWineDebug.VERBOSE -> R.string.lab_dbg_verbose
        LabWineDebug.CUSTOM -> R.string.lab_dbg_custom
    },
)

private fun packageErrorText(context: android.content.Context, code: LabPackageErrorCode): String = context.getString(
    when (code) {
        LabPackageErrorCode.FILE_BROKEN -> R.string.lab_pkg_err_file_broken
        LabPackageErrorCode.NOT_AN_OBJECT -> R.string.lab_pkg_err_not_object
        LabPackageErrorCode.ID_MISSING -> R.string.lab_pkg_err_id_missing
        LabPackageErrorCode.ID_DUPLICATE -> R.string.lab_pkg_err_id_duplicate
        LabPackageErrorCode.KIND_UNKNOWN -> R.string.lab_pkg_err_kind_unknown
        LabPackageErrorCode.NAME_MISSING -> R.string.lab_pkg_err_name_missing
        LabPackageErrorCode.URL_NOT_HTTPS -> R.string.lab_pkg_err_url_not_https
        LabPackageErrorCode.SHA256_INVALID -> R.string.lab_pkg_err_sha_invalid
        LabPackageErrorCode.SHA256_MISSING -> R.string.lab_pkg_err_sha_missing
        LabPackageErrorCode.BYTES_INVALID -> R.string.lab_pkg_err_bytes_invalid
        LabPackageErrorCode.FORMAT_INVALID -> R.string.lab_pkg_err_format_invalid
    },
)


/** Die zwei Zeilen "Wine" und "Übersetzer" der Karte "Der nächste Start läuft mit". */
private fun nextText(context: android.content.Context, next: LabNextStart): String {
    val wine = context.getString(if (next.wineBundled) R.string.lab_next_wine_bundled else R.string.lab_next_wine_pkg, next.wine)
    val hodll = next.hodll ?: "-"
    val translator = when {
        next.translator == LabTranslator.FEX && next.fexVersion != null ->
            context.getString(R.string.lab_next_tr_fex, next.fexVersion, next.emulator, hodll)
        next.translator == LabTranslator.FEX -> context.getString(R.string.lab_next_tr_fex_none)
        next.translator == LabTranslator.NONE -> context.getString(R.string.lab_next_tr_none)
        else -> context.getString(R.string.lab_next_tr_bundled, next.emulator, hodll)
    }
    val stability = context.getString(R.string.lab_next_stability, next.stability.level, stabilityName(context, next.stability)) +
        if (next.stabilityLines.isEmpty()) "" else "\n" + context.getString(R.string.lab_next_stability_lines, next.stabilityLines.joinToString(", "))
    val log = if (next.translatorLog) "\n" + context.getString(R.string.lab_next_translator_log) else ""
    return wine + "\n" + translator + "\n" + stability + log
}

/** Zwei Zeilen je Lauf: Uhrzeit, Ziel und Dauer; dann Stufe, Übersetzer und Zahl der Abstürze. */
private fun runText(context: android.content.Context, e: LabRunEntry): String {
    val time = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(e.startedMs))
    val target = context.getString(
        when (e.target) {
            "battlenet" -> R.string.lab_runs_target_battlenet
            "installer" -> R.string.lab_runs_target_installer
            "step_test" -> R.string.lab_runs_target_step_test
            else -> R.string.lab_runs_target_any
        },
    )
    val cut = if (e.outputCut) " " + context.getString(R.string.lab_runs_cut) else ""
    return context.getString(R.string.lab_runs_line1, time, target, e.durationSec) + "\n" +
        context.getString(R.string.lab_runs_line2, e.stage, LabStability.entries.firstOrNull { it.level == e.stage }?.let { stabilityName(context, it) } ?: e.stageName, e.translator, e.crashes) + cut
}

private fun stabilityName(context: android.content.Context, stage: LabStability): String = context.getString(
    when (stage) {
        LabStability.OFF -> R.string.lab_stab_name_0
        LabStability.CAREFUL -> R.string.lab_stab_name_1
        LabStability.CAREFUL_NO_JIT -> R.string.lab_stab_name_2
        LabStability.SAFEST -> R.string.lab_stab_name_3
        LabStability.FEX -> R.string.lab_stab_name_4
        LabStability.FEX_CAREFUL -> R.string.lab_stab_name_5
    },
)

private fun stabilityDescRes(stage: LabStability): Int = when (stage) {
    LabStability.OFF -> R.string.lab_stab_desc_0
    LabStability.CAREFUL -> R.string.lab_stab_desc_1
    LabStability.CAREFUL_NO_JIT -> R.string.lab_stab_desc_2
    LabStability.SAFEST -> R.string.lab_stab_desc_3
    LabStability.FEX -> R.string.lab_stab_desc_4
    LabStability.FEX_CAREFUL -> R.string.lab_stab_desc_5
}

private fun nextNoteText(context: android.content.Context, note: LabNextNote, next: LabNextStart): String = when (note) {
    LabNextNote.FEX_INSTALLED_NOT_CHOSEN -> context.getString(R.string.lab_next_note_fex_installed, next.installedFex.orEmpty())
    LabNextNote.FEX_CHOSEN_NONE_INSTALLED -> context.getString(R.string.lab_next_note_fex_none)
    LabNextNote.FEX_CHOSEN_NO_VERSION -> context.getString(R.string.lab_next_note_fex_noversion)
    LabNextNote.FEX_VERSION_NOT_INSTALLED -> context.getString(R.string.lab_next_note_fex_missing, next.missingName.orEmpty())
    LabNextNote.WINE_NOT_INSTALLED -> context.getString(R.string.lab_next_note_wine_missing, next.missingName.orEmpty())
    LabNextNote.NO_TRANSLATOR -> context.getString(R.string.lab_next_note_none)
}

@Composable
private fun ErrorText(text: String, error: Boolean = true) {
    Text(
        text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = if (error) LauncherColors.ErrText else LauncherColors.Body,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** Ein Eingabefeld der Einstellungen; die Eingabe wird bei jeder Änderung gespeichert, [error] steht darunter. */
@Composable
private fun LabTextField(value: String, label: String, hint: String, lines: Int, error: String?, onChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            supportingText = { Text(hint, fontSize = 12.sp) },
            isError = error != null,
            singleLine = lines == 1,
            minLines = lines.coerceAtMost(3),
            maxLines = lines,
            textStyle = LocalTextStyle.current.copy(color = LauncherColors.Body),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LauncherColors.Body,
                unfocusedTextColor = LauncherColors.Body,
                focusedBorderColor = LauncherColors.Gold,
                unfocusedBorderColor = LauncherColors.Bronze,
                focusedLabelColor = LauncherColors.Gold,
                unfocusedLabelColor = LauncherColors.Muted,
                cursorColor = LauncherColors.Gold,
                focusedSupportingTextColor = LauncherColors.Muted,
                unfocusedSupportingTextColor = LauncherColors.Muted,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error, fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.ErrText, modifier = Modifier.padding(top = 2.dp))
    }
}
