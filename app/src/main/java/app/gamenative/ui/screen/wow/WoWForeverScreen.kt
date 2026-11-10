package app.gamenative.ui.screen.wow

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.gamenative.BuildConfig
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.screen.wow.launcher.AboutPage
import app.gamenative.ui.screen.wow.launcher.FilesPage
import app.gamenative.ui.screen.wow.launcher.HelpPage
import app.gamenative.ui.screen.wow.launcher.LauncherPage
import app.gamenative.ui.screen.wow.launcher.LauncherTexts
import app.gamenative.ui.screen.wow.launcher.NewsPage
import app.gamenative.ui.screen.wow.launcher.launcherBackground
import app.gamenative.ui.screen.wow.WowClientDownloader.BUILD_INFO
import app.gamenative.ui.screen.wow.WowClientDownloader.EXE_NAME
import app.gamenative.ui.screen.wow.WowClientDownloader.FLAVOR_DIR
import app.gamenative.ui.screen.wow.WowClientDownloader.LEGACY_FLAVOR_DIR
import app.gamenative.ui.screen.wow.WowClientDownloader.LEGACY_PRODUCT
import app.gamenative.ui.screen.wow.WowClientDownloader.TARGET_PRODUCT
import app.gamenative.ui.theme.WowBronze
import app.gamenative.ui.theme.WowError
import app.gamenative.ui.theme.WowGold
import app.gamenative.ui.theme.WowMuted
import app.gamenative.ui.theme.WowSubtle
import app.gamenative.utils.StorageUtils
import app.gamenative.wowaction.WowActionSource
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.contents.ContentProfile
import com.winlator.contents.ContentsManager
import com.winlator.core.FileUtils
import com.winlator.core.GPUInformation
import com.winlator.core.TarCompressorUtils
import com.winlator.xenvironment.ImageFs
import com.winlator.xenvironment.ImageFsInstaller
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File

object WoWLauncherState {
    var shouldAutoLaunch = true

    // First-run steps that already ran in this process, so none of them can repeat (see nextFirstRunStep).
    internal var storageRequested = false
    internal var autoInstallAttempted = false
    internal var loginPrompted = false
}

private const val CONTAINER_ID = "wow_forever"

@Composable
fun WoWForeverScreen(
    onLaunch: (appId: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gpu = remember { GpuProfile.detect(context) }

    var gamePath by remember { mutableStateOf(GamePath.load(context)) }
    var files by remember { mutableStateOf(GamePath.Status()) }
    var isLaunching by remember { mutableStateOf(false) }
    var launchJob by remember { mutableStateOf<Job?>(null) }
    var hasSavedLogin by remember { mutableStateOf(BattleNetSignIn.load(context) != null) }
    var region by remember(gamePath, files) { mutableStateOf(GameRegion.current(context, gamePath)) }
    var resolution by remember { mutableStateOf(RenderResolution.current(context, gpu.screenSize)) }
    var fpsLimit by remember { mutableStateOf(FpsLimit.current(context)) }
    var walkGain by remember { mutableStateOf(StickSensitivity.walk(context)) }
    var cameraGain by remember { mutableStateOf(StickSensitivity.camera(context)) }
    var inputMode by remember { mutableStateOf(InputMode.current(context)) }
    var showLayoutResetDialog by remember { mutableStateOf(false) }
    // Die Einstellungen liegen auf einer eigenen Seite über der Hauptansicht (LauncherSettingsPage.kt).
    var showSettings by remember { mutableStateOf(false) }
    // Die Addon-Verwaltung liegt noch eine Seite darüber (AddonsPage.kt), Zurück führt in die Einstellungen.
    var showAddons by remember { mutableStateOf(false) }
    // Die übrigen Seiten hinter den Kacheln der Startseite (Touch-Layout, Spieldateien, Neuigkeiten, Hilfe, Über), eine zur Zeit.
    var launcherPage by remember { mutableStateOf<LauncherPage?>(null) }
    // "Über" liegt noch eine Ebene darüber (Fußzeile oder Hilfe), Zurück führt dorthin zurück.
    var showAbout by remember { mutableStateOf(false) }
    var graphicsPreset by remember { mutableStateOf(GraphicsPreset.current(context)) }
    var showLoginDialog by remember { mutableStateOf(false) }
    val readyStatus = stringResource(R.string.game_status_ready)
    var statusText by remember { mutableStateOf(readyStatus) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStoragePermissionDialog by remember { mutableStateOf(false) }
    var versionStatus by remember { mutableStateOf<WowClientDownloader.VersionCheckResult?>(null) }
    var isCheckingVersion by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf("") }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var installJob by remember { mutableStateOf<Job?>(null) }
    var isAutoInstall by remember { mutableStateOf(false) }
    // The default Status() reads as "needs install", so the first-run flow waits until checkFiles() has really looked.
    var filesChecked by remember { mutableStateOf(false) }
    var loginOpenedByFlow by remember { mutableStateOf(false) }
    var showFoldHint by remember { mutableStateOf(false) }
    var showStartCheck by remember { mutableStateOf(false) }
    var startCheckOn by remember { mutableStateOf(StartCheck.enabled(context)) }
    var autoStartOn by remember { mutableStateOf(AutoStartSettings.enabled(context)) }
    var autoStartSeconds by remember { mutableIntStateOf(AutoStartSettings.seconds(context)) }
    // Automatic start: the launcher is always shown first, the game starts after this visible countdown (see runAutoStart).
    var autoUi by remember { mutableStateOf<AutoStartUi>(AutoStartUi.Off) }
    // Bumped to re-run the first-run effect when its inputs changed without any of its other keys changing.
    var flowTick by remember { mutableIntStateOf(0) }

    // Self-update (package app.gamenative.update): cached hint at once, silent feed check at most every 6 h.
    LaunchedEffect(Unit) { app.gamenative.update.UpdateManager.onLauncherShown(context) }

    suspend fun fetchVersionStatus() {
        isCheckingVersion = true
        versionStatus = withContext(Dispatchers.IO) { WowClientDownloader.checkVersion(File(gamePath)) }
        isCheckingVersion = false
    }

    fun checkVersionStatus() {
        if (!File(gamePath, BUILD_INFO).exists()) return
        scope.launch { fetchVersionStatus() }
    }

    fun checkFiles(): Boolean {
        files = GamePath.status(context, gamePath)
        filesChecked = true
        if (files.isWrongGame) {
            errorMessage = context.getString(R.string.game_wrong_product, files.products.joinToString(), TARGET_PRODUCT)
        }
        return files.isReady
    }

    fun denyStorageAccess() {
        files = files.copy(hasStorageAccess = false)
        showStoragePermissionDialog = true
    }

    fun resetLaunch() {
        launchJob?.cancel()
        launchJob = null
        isLaunching = false
        statusText = readyStatus
    }

    fun startLaunch(ignoreFold: Boolean, ignoreStartCheck: Boolean = false) {
        if (isLaunching || files.isWrongGame) return
        if (!files.hasStorageAccess) {
            showStoragePermissionDialog = true
            return
        }
        if (!ignoreFold && isFoldedNow(context)) {
            // The game is sized for the unfolded inner display; wait for the player to unfold, or to start anyway.
            showFoldHint = true
            PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
            return
        }
        showFoldHint = false
        if (!ignoreStartCheck && startCheckOn) {
            // Netz-/Login-Vorpruefung (StartCheckUi.kt); "Weiter" kommt mit ignoreStartCheck = true zurueck.
            showStartCheck = true
            return
        }
        showStartCheck = false
        StartCheck.onLaunch(context, File(gamePath))
        // Fold state at start: sets the picture size and what the hint says when folding/unfolding during play.
        val foldedAtStart = isFoldedNow(context)
        FoldSession.startedFolded = foldedAtStart
        isLaunching = true
        errorMessage = null
        statusText = context.getString(R.string.game_status_preparing)

        launchJob = scope.launch {
            try {
                val screenSize = fitScreenSizeToWindow(context, resolution.size, foldedAtStart)
                val containerId = withContext(Dispatchers.IO) {
                    prepareLaunch(context, File(gamePath), gpu, screenSize, fpsLimit.fps, inputMode) { msg ->
                        scope.launch(Dispatchers.Main) { statusText = msg }
                    }
                }
                statusText = context.getString(R.string.game_status_starting)
                onLaunch(containerId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: StorageAccessDeniedException) {
                isLaunching = false
                statusText = readyStatus
                denyStorageAccess()
            } catch (e: Exception) {
                Timber.e(e, "Error launching WoW Forever")
                errorMessage = e.message ?: context.getString(R.string.game_launch_failed)
                statusText = context.getString(R.string.game_launch_failed)
                isLaunching = false
            }
        }
    }

    fun launchGame() = startLaunch(ignoreFold = false)

    LifecycleResumeEffect(gamePath) {
        // Storage access may have just been granted, which can reveal an install that was invisible before.
        if (!isUpdating && !isLaunching) gamePath = GamePath.load(context)
        checkFiles()
        isLaunching = false
        statusText = readyStatus
        BattleNetSignIn.removeLoginFile(File(gamePath))
        if (versionStatus == null && !WoWLauncherState.shouldAutoLaunch) {
            checkVersionStatus()
        }
        onPauseOrDispose {
            launchJob?.cancel()
            launchJob = null
        }
    }

    DisposableEffect(Unit) {
        val reset: (Any) -> Unit = {
            resetLaunch()
            BattleNetSignIn.removeLoginFile(File(gamePath))
        }
        PluviaApp.events.on<AndroidEvent.ForceCloseApp, Unit>(reset)
        PluviaApp.events.on<AndroidEvent.GuestProgramTerminated, Unit>(reset)
        onDispose {
            PluviaApp.events.off<AndroidEvent.ForceCloseApp, Unit>(reset)
            PluviaApp.events.off<AndroidEvent.GuestProgramTerminated, Unit>(reset)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = StorageUtils.getPathFromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
        val root = GamePath.findGameRoot(File(path)) ?: GamePath.installDirFor(File(path)).also { it.mkdirs() }
        errorMessage = null
        gamePath = root.absolutePath
        GamePath.save(context, gamePath)
        versionStatus = null
        val ok = checkFiles()
        if (!files.hasStorageAccess) {
            showStoragePermissionDialog = true
        } else if (ok) {
            checkVersionStatus()
        }
    }

    val canPlay = !isLaunching && !isUpdating && !files.needsInstall && !files.isWrongGame

    val view = LocalView.current
    DisposableEffect(isUpdating) {
        view.keepScreenOn = isUpdating
        onDispose { view.keepScreenOn = false }
    }


    fun downloadGameFiles(
        failure: String,
        work: (File, (String) -> Unit) -> Unit,
        auto: Boolean = false,
        onSuccess: () -> Unit,
    ) {
        if (isUpdating || isLaunching) return
        if (!files.hasStorageAccess) {
            showStoragePermissionDialog = true
            return
        }
        isUpdating = true
        isAutoInstall = auto
        updateStatusText = context.getString(R.string.game_status_connecting)
        errorMessage = null
        installJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // The downloader is blocking code; every status report is a checkpoint where a cancel can take effect.
                    val job = coroutineContext.job
                    work(File(gamePath)) { msg ->
                        job.ensureActive()
                        scope.launch(Dispatchers.Main) { updateStatusText = msg }
                    }
                }
                isUpdating = false
                isAutoInstall = false
                onSuccess()
            } catch (e: CancellationException) {
                isUpdating = false
                isAutoInstall = false
                throw e
            } catch (e: Exception) {
                Timber.e(e, failure)
                if (e.isPermissionDenied()) denyStorageAccess()
                errorMessage = "$failure: ${e.message}"
                isUpdating = false
                isAutoInstall = false
            }
        }
    }

    fun startInstall(auto: Boolean) {
        showUpgradeDialog = false
        downloadGameFiles(context.getString(R.string.game_install_failed), WowClientDownloader::install, auto) {
            versionStatus = null
            if (checkFiles()) {
                checkVersionStatus()
                if (hasSavedLogin) launchGame()
            }
        }
    }

    fun performInstall() = startInstall(auto = false)

    fun cancelAutoInstall() {
        // A cancelled automatic install is not restarted on the next launch; the button stays available.
        GamePath.setAutoInstallDeclined(context, true)
        installJob?.cancel()
        installJob = null
    }

    fun performUpdate() {
        val target = versionStatus ?: return
        downloadGameFiles(context.getString(R.string.game_update_failed), { root, onStatus -> WowClientDownloader.updateGame(root, target, onStatus) }) {
            checkFiles()
            versionStatus = target.copy(isOutdated = false, localVersion = target.remoteVersion)
            launchGame()
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Refusing is fine: the game runs without a microphone, and the question is not asked again.
        Timber.i("Microphone permission granted=$granted")
        flowTick++
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Timber.i("Notification permission granted=$granted")
        flowTick++
    }

    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Timber.i("Battery optimization exempt=${BatteryExemption.exempt(context)}")
        flowTick++
    }

    // First run without a click: storage access, then the install, then the login. Each step runs at most once per
    // process (see nextFirstRunStep), so returning from Settings or a failed install can't loop.
    LaunchedEffect(files, filesChecked, hasSavedLogin, isUpdating, isLaunching, flowTick) {
        if (!filesChecked) return@LaunchedEffect
        val step = nextFirstRunStep(
            FirstRunInput(
                busy = isUpdating || isLaunching,
                hasStorageAccess = files.hasStorageAccess,
                micGranted = MicPermission.granted(context),
                notificationsOk = NotificationPermission.granted(context),
                batteryExempt = BatteryExemption.exempt(context),
                isWrongGame = files.isWrongGame,
                isLegacyBeta = files.isLegacyBeta,
                needsInstall = files.needsInstall,
                hasSavedLogin = hasSavedLogin,
                locationChosen = GamePath.hasChosenLocation(context),
                unmetered = isUnmeteredNetwork(context),
                autoLaunchPending = WoWLauncherState.shouldAutoLaunch,
                storageRequested = WoWLauncherState.storageRequested,
                micRequested = MicPermission.wasAsked(context),
                notificationsRequested = NotificationPermission.wasAsked(context),
                batteryRequested = BatteryExemption.wasAsked(context),
                installAttempted = WoWLauncherState.autoInstallAttempted || GamePath.autoInstallDeclined(context),
                loginPrompted = WoWLauncherState.loginPrompted,
            ),
        )
        Timber.i("WoWForeverScreen first run: step=$step")
        when (step) {
            FirstRunStep.None -> Unit
            FirstRunStep.RequestStorage -> {
                WoWLauncherState.storageRequested = true
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                if (!StorageUtils.requestManageExternalStoragePermission(context)) showStoragePermissionDialog = true
            }
            FirstRunStep.RequestMicrophone -> {
                MicPermission.markAsked(context)
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
            FirstRunStep.RequestNotifications -> {
                NotificationPermission.markAsked(context)
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            FirstRunStep.RequestBatteryExemption -> {
                BatteryExemption.markAsked(context)
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                // Some devices have no such settings screen; refusing or failing must never block anything.
                runCatching { batteryLauncher.launch(BatteryExemption.request(context)) }
                    .onFailure { Timber.w(it, "Battery optimization request unavailable"); flowTick++ }
            }
            FirstRunStep.AutoInstall -> {
                WoWLauncherState.autoInstallAttempted = true
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                startInstall(auto = true)
            }
            FirstRunStep.PromptLogin -> {
                WoWLauncherState.loginPrompted = true
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                loginOpenedByFlow = true
                showLoginDialog = true
            }
        }
    }

    // Visible countdown instead of a hidden auto-start. Re-evaluated every second (AutoStartLogic.step): a launcher update
    // that shows up, a tap on "Abbrechen"/"Jetzt spielen", an outdated game or a pending permission prompt stop it.
    suspend fun runAutoStart() {
        val total = autoStartSeconds
        var left = total
        var holdMs = 0
        fun input() = AutoStartInput(
            enabled = autoStartOn,
            launcherUpdateOffered = app.gamenative.update.UpdateManager.state.value.let {
                it is app.gamenative.update.UpdateState.Available || it is app.gamenative.update.UpdateState.Downloading ||
                    it is app.gamenative.update.UpdateState.Installing
            },
            gameOutdated = versionStatus?.isOutdated == true,
            permissionsPending = firstRunPermissionsPending(context),
            busy = isLaunching || isUpdating,
            userStopped = autoUi is AutoStartUi.Stopped,
            checkRunning = app.gamenative.update.UpdateManager.checkPhase.value == app.gamenative.update.CheckPhase.Running,
        )
        try {
            while (true) {
                when (val step = AutoStartLogic.step(left, input(), holdMs >= AutoStartLogic.CHECK_HOLD_MAX_MS)) {
                    is AutoStartStep.Wait -> {
                        autoUi = AutoStartUi.Counting(step.secondsLeft, total, waitingForCheck = false)
                        delay(1000)
                        left--
                    }
                    AutoStartStep.WaitForCheck -> {
                        autoUi = AutoStartUi.Counting(0, total, waitingForCheck = true)
                        delay(250)
                        holdMs += 250
                    }
                    AutoStartStep.Start -> {
                        autoUi = AutoStartUi.Off
                        Timber.i("WoWForeverScreen: countdown over, starting the game")
                        launchGame()
                        return
                    }
                    is AutoStartStep.Stop -> {
                        Timber.i("WoWForeverScreen: auto-start stopped: ${step.reason}")
                        if (autoUi !is AutoStartUi.Stopped) autoUi = AutoStartUi.Stopped(step.reason)
                        // Permission prompts of the first-run flow were held back while the auto-start was pending.
                        flowTick++
                        return
                    }
                }
            }
        } finally {
            // The effect was cancelled mid-countdown (e.g. the game folder changed): never leave a dead countdown on screen.
            if (autoUi is AutoStartUi.Counting) autoUi = AutoStartUi.Stopped(AutoStartBlock.UserStopped)
        }
    }

    LaunchedEffect(gamePath) {
        val ready = checkFiles()
        // Every setup path reads or writes shared storage; the first-run effect below opens the access screen for it.
        Timber.i("WoWForeverScreen LaunchedEffect: gamePath=$gamePath, ready=$ready, hasSavedLogin=$hasSavedLogin, shouldAutoLaunch=${WoWLauncherState.shouldAutoLaunch}")
        when {
            !ready || !hasSavedLogin -> {
                WoWLauncherState.shouldAutoLaunch = false
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                if (files.isLegacyBeta) showUpgradeDialog = true
                if (ready) {
                    checkVersionStatus()
                }
            }
            !WoWLauncherState.shouldAutoLaunch -> {
                checkVersionStatus()
            }
            else -> {
                fetchVersionStatus()
                Timber.i("WoWForeverScreen LaunchedEffect: isOutdated=${versionStatus?.isOutdated}")
                WoWLauncherState.shouldAutoLaunch = false
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                runAutoStart()
            }
        }
    }

    // Start oder Download laufen: der Fortschritt gehört in die Hauptansicht, die Einstellungsseite schließt sich.
    LaunchedEffect(isUpdating, isLaunching) {
        if (isUpdating || isLaunching) {
            showSettings = false
            showAddons = false
            launcherPage = null
            showAbout = false
        }
    }

    val busyText = when {
        isUpdating -> updateStatusText
        isLaunching -> statusText
        isCheckingVersion && WoWLauncherState.shouldAutoLaunch -> context.getString(R.string.game_status_checking_version)
        else -> null
    }

    val homeState = LauncherHomeState(
        edition = gpu.edition,
        files = files,
        filesChecked = filesChecked,
        gamePath = gamePath,
        hasSavedLogin = hasSavedLogin,
        versionStatus = versionStatus,
        isLaunching = isLaunching,
        isUpdating = isUpdating,
        isAutoInstall = isAutoInstall,
        busyText = busyText,
        errorMessage = errorMessage,
        autoUi = autoUi,
        canPlay = canPlay,
        regionLabel = region.label,
        resolutionLabel = resolution.label,
        fpsLabel = fpsLimit.label,
        inputModeLabel = inputMode.label,
        homeCovered = launcherPage != null || showSettings || showAddons || showAbout,
    )
    val homeActions = LauncherHomeActions(
        onPlay = {
            // Ein Tipp auf "Spielen" ersetzt den Countdown durch den normalen Start.
            if (autoUi is AutoStartUi.Counting) autoUi = AutoStartUi.Stopped(AutoStartBlock.PlayedNow)
            launchGame()
        },
        onCancelCountdown = { autoUi = AutoStartUi.Stopped(AutoStartBlock.UserStopped) },
        onInstall = ::performInstall,
        onUpdate = ::performUpdate,
        onCancelInstall = ::cancelAutoInstall,
        onPickFolder = { folderPicker.launch(null) },
        onGrantStorage = { StorageUtils.requestManageExternalStoragePermission(context) },
        onLogin = { showLoginDialog = true },
        onForgetLogin = {
            BattleNetSignIn.forget(context)
            BattleNetSignIn.removeLoginFile(File(gamePath))
            hasSavedLogin = false
        },
        onRefresh = {
            checkFiles()
            checkVersionStatus()
        },
        onOpenPage = { page ->
            // Wer eine Seite öffnet, will nicht, dass das Spiel im Hintergrund losstartet.
            if (autoUi is AutoStartUi.Counting) autoUi = AutoStartUi.Stopped(AutoStartBlock.UserStopped)
            when (page) {
                LauncherPage.Settings -> showSettings = true
                LauncherPage.Addons -> showAddons = true
                LauncherPage.About -> showAbout = true
                else -> launcherPage = page
            }
        },
    )
    // Einstellungsseite und die Kachel "Touch-Layout" (nur die Gruppe Steuerung) teilen Werte und Aktionen.
    val settingsValues = LauncherSettingsValues(
        enabled = !isLaunching,
        region = region,
        resolution = resolution,
        fpsLimit = fpsLimit,
        graphicsPreset = graphicsPreset,
        inputMode = inputMode,
        walkGain = walkGain,
        cameraGain = cameraGain,
        gamePath = gamePath,
        autoStartOn = autoStartOn,
        autoStartSeconds = autoStartSeconds,
        startCheckOn = startCheckOn,
        clientLocal = versionStatus?.localVersion,
        clientRemote = versionStatus?.remoteVersion,
        clientOutdated = versionStatus?.isOutdated == true,
    )
    val settingsActions = LauncherSettingsActions(
        onRegion = { GameRegion.save(context, it); region = it },
        onResolution = { RenderResolution.save(context, it); resolution = it },
        onFps = { FpsLimit.save(context, it); fpsLimit = it },
        onGraphics = { GraphicsPreset.save(context, it); graphicsPreset = it },
        onInputMode = { InputMode.save(context, it); inputMode = it },
        onWalkGain = { StickSensitivity.saveWalk(context, it); walkGain = it },
        onCameraGain = { StickSensitivity.saveCamera(context, it); cameraGain = it },
        onPickFolder = { folderPicker.launch(null) },
        onLayoutReset = { showLayoutResetDialog = true },
        onAutoStart = { autoStartOn = it; AutoStartSettings.setEnabled(context, it) },
        onAutoStartSeconds = { autoStartSeconds = it; AutoStartSettings.setSeconds(context, it) },
        onStartCheck = { startCheckOn = it; StartCheck.setEnabled(context, it) },
        onOpenAddons = { showAddons = true },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .launcherBackground(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            LauncherHome(s = homeState, a = homeActions)
        }

        if (showSettings) {
            LauncherSettingsPage(values = settingsValues, actions = settingsActions, onBack = { showSettings = false })
        }

        if (showAddons) {
            AddonsPage(
                addonsDir = File(gamePath, "$FLAVOR_DIR/Interface/AddOns"),
                gameBusy = isLaunching || isUpdating,
                onBack = { showAddons = false },
            )
        }

        // Seiten hinter den Kacheln; "Über" liegt darüber, damit Zurück aus der Hilfe wieder dorthin führt.
        when (launcherPage) {
            LauncherPage.Touch -> LauncherSettingsPage(
                values = settingsValues,
                actions = settingsActions,
                onBack = { launcherPage = null },
                title = LauncherTexts.pageTitle(LauncherPage.Touch),
                onlyGroup = LauncherTexts.TOUCH_SETTINGS_GROUP,
            )
            LauncherPage.Files -> FilesPage(homeState, homeActions, onBack = { launcherPage = null })
            LauncherPage.News -> NewsPage(canInstall = !isLaunching && !isUpdating, onBack = { launcherPage = null })
            LauncherPage.Help -> HelpPage(homeState, homeActions, onBack = { launcherPage = null })
            else -> Unit
        }
        if (showAbout) {
            AboutPage(onBack = { showAbout = false })
        }
    }

    if (showLoginDialog) {
        BattleNetCredentialDialog(
            onDismiss = {
                showLoginDialog = false
                loginOpenedByFlow = false
            },
            onConfirm = { login ->
                showLoginDialog = false
                BattleNetSignIn.save(context, login)
                hasSavedLogin = true
                // Only the login prompt opened by the first-run flow continues into the game; a manual "Update Login" doesn't.
                if (loginOpenedByFlow) {
                    loginOpenedByFlow = false
                    if (shouldLaunchAfterLogin(!files.needsInstall, files.isWrongGame, versionStatus?.isOutdated == true)) launchGame()
                }
            },
        )
    }

    if (showLayoutResetDialog) {
        AlertDialog(
            onDismissRequest = { showLayoutResetDialog = false },
            title = { Text(text = stringResource(R.string.game_reset_layout_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
            text = {
                Column {
                    Text(
                        text = stringResource(WowTouchProfiles.RESET_CONFIRM_TEXT, WowTouchProfiles.resetTargetName(inputMode)),
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 18.sp,
                    )
                    // Touchscreen switch and gestures keep the player's changes across starts; this hands them back to the WoW defaults.
                    TextButton(
                        onClick = {
                            showLayoutResetDialog = false
                            TouchSetupStore.requestReset(context)
                            app.gamenative.ui.util.SnackbarManager.show(context.getString(R.string.game_reset_touch_scheduled))
                        },
                    ) {
                        Text(stringResource(R.string.game_reset_touch_only), color = WowGold, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLayoutResetDialog = false
                        scope.launch {
                            val done = withContext(Dispatchers.IO) {
                                try {
                                    WowTouchProfiles.reset(context, inputMode)
                                } catch (e: Exception) {
                                    Timber.w(e, "Layout reset failed")
                                    false
                                }
                            }
                            app.gamenative.ui.util.SnackbarManager.show(
                                if (done) context.getString(R.string.game_layout_reset_done) else context.getString(R.string.game_layout_reset_failed),
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.game_reset), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLayoutResetDialog = false }) {
                    Text(stringResource(R.string.game_cancel), color = WowMuted)
                }
            },
            containerColor = Color(0xFF1B2838),
            shape = RoundedCornerShape(16.dp),
        )
    }

    if (showStartCheck) {
        StartCheckDialog(
            region = region,
            onContinue = { showStartCheck = false; startLaunch(ignoreFold = false, ignoreStartCheck = true) },
            onCancel = { showStartCheck = false },
            onDisable = {
                startCheckOn = false
                StartCheck.setEnabled(context, false)
                showStartCheck = false
                startLaunch(ignoreFold = false, ignoreStartCheck = true)
            },
        )
    }

    if (showFoldHint) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        // Unfolding while the hint is up continues into the game.
        LaunchedEffect(configuration) {
            if (!isFoldedNow(context)) startLaunch(ignoreFold = true)
        }
        AlertDialog(
            onDismissRequest = { showFoldHint = false },
            title = { Text(text = stringResource(R.string.game_fold_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
            text = {
                Text(
                    text = stringResource(R.string.game_fold_text),
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { startLaunch(ignoreFold = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.game_fold_start_folded), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFoldHint = false }) {
                    Text(stringResource(R.string.game_cancel), color = WowMuted)
                }
            },
            containerColor = Color(0xFF1B2838),
            shape = RoundedCornerShape(16.dp),
        )
    }

    if (showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = { showUpgradeDialog = false },
            title = { Text(text = stringResource(R.string.game_upgrade_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
            text = {
                Text(
                    text = stringResource(R.string.game_upgrade_text, FLAVOR_DIR),
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = ::performInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.game_upgrade_now), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showUpgradeDialog = false
                        folderPicker.launch(null)
                    }) {
                        Text(stringResource(R.string.game_choose_folder), color = WowMuted)
                    }
                    TextButton(onClick = { showUpgradeDialog = false }) {
                        Text(stringResource(R.string.game_later), color = WowMuted)
                    }
                }
            },
            containerColor = Color(0xFF1B2838),
            shape = RoundedCornerShape(16.dp),
        )
    }

    if (showStoragePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showStoragePermissionDialog = false },
            title = { Text(text = stringResource(R.string.game_storage_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
            text = {
                Text(
                    text = stringResource(R.string.game_storage_text),
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStoragePermissionDialog = false
                        StorageUtils.requestManageExternalStoragePermission(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.game_open_settings), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStoragePermissionDialog = false }) {
                    Text(stringResource(R.string.game_cancel), color = WowMuted)
                }
            },
            containerColor = Color(0xFF1B2838),
            shape = RoundedCornerShape(16.dp),
        )
    }
}

private fun prefs(context: Context) = context.getSharedPreferences("wow_forever", Context.MODE_PRIVATE)

/**
 * The Battle.net region WoW logs into. Without the Battle.net app to pick one, WoW stops at a region picker before
 * login, so the launcher writes it to Config.wtf as the portal.
 */
enum class GameRegion(val label: String) {
    US("Americas"), EU("Europe"), KR("Korea"), TW("Taiwan");

    companion object {
        private const val KEY_REGION = "region"

        /** The region picked in the launcher, or null to keep the install's current portal. */
        fun saved(context: Context): GameRegion? = prefs(context).getString(KEY_REGION, null)?.let(::of)

        fun save(context: Context, region: GameRegion) {
            prefs(context).edit().putString(KEY_REGION, region.name).apply()
        }

        fun current(context: Context, gamePath: String): GameRegion = saved(context) ?: of(portalOf(File(gamePath))) ?: US

        private fun of(portal: String) = entries.firstOrNull { it.name.equals(portal, ignoreCase = true) }
    }
}

/** The 16:9 render budget, before it's reshaped to the window's aspect ratio. Defaults to the GPU profile's size. */
enum class RenderResolution(val size: String, val label: String) {
    HD("1280x720", "720p"), HD_PLUS("1600x900", "900p"), FULL_HD("1920x1080", "1080p");

    companion object {
        private const val KEY_RESOLUTION = "resolution"

        /** The player's choice; until there is one, 900p on a large inner display of a foldable, else the GPU default. */
        fun current(context: Context, default: String): RenderResolution =
            prefs(context).getString(KEY_RESOLUTION, null)?.let { saved -> entries.firstOrNull { it.name == saved } }
                ?: windowSize(context, maximum = true).let { (width, height) -> defaultResolution(default, width, height) }

        fun save(context: Context, resolution: RenderResolution) {
            prefs(context).edit().putString(KEY_RESOLUTION, resolution.name).apply()
        }
    }
}

/** The FPS cap written to Config.wtf on every start: thin foldables run hot, and a lower cap saves battery. */
enum class FpsLimit(val fps: Int, val label: String) {
    FPS_60(60, "60"), FPS_45(45, "45"), FPS_30(30, "30");

    companion object {
        private const val KEY_FPS = "fps_limit"

        fun current(context: Context): FpsLimit =
            prefs(context).getString(KEY_FPS, null)?.let { saved -> entries.firstOrNull { it.name == saved } } ?: FPS_60

        fun save(context: Context, limit: FpsLimit) {
            prefs(context).edit().putString(KEY_FPS, limit.name).apply()
        }
    }
}

/** WoW's voice chat needs the microphone. The launcher asks once; a refusal is respected and never repeated. */
internal object MicPermission {
    private const val KEY_ASKED = "microphone_asked"

    fun granted(context: Context) =
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun wasAsked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }
}

/** Notifications show the ongoing "WoW Forever läuft" entry. Needs a runtime permission from Android 13; asked once. */
internal object NotificationPermission {
    private const val KEY_ASKED = "notifications_asked"

    fun granted(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun wasAsked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }
}

/** Exemption from battery optimization keeps Android from throttling the connection in long sessions; asked once. */
internal object BatteryExemption {
    private const val KEY_ASKED = "battery_exemption_asked"

    fun exempt(context: Context) =
        context.getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun wasAsked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun request(context: Context): android.content.Intent =
        android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:${context.packageName}"),
        )
}

object GamePath {
    private const val KEY_GAME_PATH = "game_path"
    private const val KEY_AUTO_INSTALL_DECLINED = "auto_install_declined"

    private val defaultPath = File(Environment.getExternalStorageDirectory(), "WoW Forever")

    data class Status(
        val exeExists: Boolean = false,
        val dataExists: Boolean = false,
        val buildInfoExists: Boolean = false,
        val products: Set<String> = emptySet(),
        val hasStorageAccess: Boolean = true,
    ) {
        val hasForever get() = TARGET_PRODUCT in products
        val isLegacyBeta get() = !hasForever && LEGACY_PRODUCT in products
        val isWrongGame get() = buildInfoExists && products.isNotEmpty() && !hasForever && !isLegacyBeta
        val needsInstall get() = !isWrongGame && !(hasForever && dataExists)
        val isReady get() = exeExists && dataExists && hasForever && hasStorageAccess
    }

    fun status(context: Context, path: String): Status {
        val root = File(path)
        return Status(
            exeExists = File(root, "$FLAVOR_DIR/$EXE_NAME").exists(),
            dataExists = File(root, "Data").listFiles()?.isNotEmpty() == true,
            buildInfoExists = File(root, BUILD_INFO).isFile,
            products = productsOf(root),
            hasStorageAccess = StorageUtils.hasStoragePermission(context, path),
        )
    }

    /**
     * The folder the user picked, as long as it still exists. Otherwise the first default location (internal storage, then
     * each SD card) that holds an install, preferring the full release over the beta.
     */
    fun load(context: Context): String {
        val saved = prefs(context).getString(KEY_GAME_PATH, null)?.let(::File)
        if (saved != null && saved.isDirectory) return saved.absolutePath
        val defaults = listOf(defaultPath) + context.getExternalFilesDirs(null).drop(1).filterNotNull()
            .map { File(it.path.substringBefore("/Android/"), defaultPath.name) }
        val usable = defaults.firstOrNull { TARGET_PRODUCT in productsOf(it) } ?: defaults.firstOrNull { LEGACY_PRODUCT in productsOf(it) }
        return (usable ?: saved ?: defaultPath).absolutePath
    }

    /** Installs into [picked] if it's empty, or into a new "WoW Forever" folder inside it (e.g. an SD card's root). */
    fun installDirFor(picked: File): File =
        if (picked.listFiles().isNullOrEmpty()) picked else File(picked, defaultPath.name)

    fun save(context: Context, path: String) {
        prefs(context).edit().putString(KEY_GAME_PATH, path).apply()
    }

    /** True once the player picked a folder; the default location is then no longer a given. */
    internal fun hasChosenLocation(context: Context) = prefs(context).contains(KEY_GAME_PATH)

    internal fun autoInstallDeclined(context: Context) = prefs(context).getBoolean(KEY_AUTO_INSTALL_DECLINED, false)

    internal fun setAutoInstallDeclined(context: Context, declined: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_INSTALL_DECLINED, declined).apply()
    }

    fun isReady(context: Context, path: String = load(context)): Boolean =
        path.isNotEmpty() && status(context, path).isReady

    fun findGameRoot(picked: File): File? =
        sequenceOf(picked, picked.parentFile, File(picked, "WoW Forever"), File(picked, "World of Warcraft"))
            .filterNotNull()
            .firstOrNull { File(it, BUILD_INFO).isFile }

    fun freeSpaceBytes(path: String): Long =
        generateSequence(File(path)) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace ?: 0L

    private fun productsOf(root: File) = WowClientDownloader.installedProducts(File(root, BUILD_INFO))
}

private class StorageAccessDeniedException(cause: Throwable) : Exception(cause)

private fun Throwable.isPermissionDenied() =
    message?.let { "EPERM" in it || "Operation not permitted" in it } == true

private const val DEFAULT_DRIVER = "Turnip-WoW-scheduler-test"
private const val ADRENO_8XX_DRIVER = "Turnip-V32-RP6sched"
private const val PROTON_VERSION = "proton-11.0-90624-arm64ec"
private const val DXVK_VERSION = "2.4.1-wow-aarch64-test"

private data class GpuProfile(val driver: String, val tuDebug: String, val screenSize: String, val label: String, val edition: String) {
    companion object {
        fun detect(context: Context) = if (isAdreno8xxDevice(context)) {
            GpuProfile(ADRENO_8XX_DRIVER, "noconform,sysmem", "1280x720", "Adreno 8xx", "Snapdragon 8 Elite / Adreno 8xx")
        } else {
            GpuProfile(DEFAULT_DRIVER, "noconform", "1920x1080", "Adreno 740", "Snapdragon 8 Series / Adreno 740")
        }

        private fun isAdreno8xxDevice(context: Context): Boolean {
            if (GPUInformation.isAdreno8Elite(context)) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val soc = Build.SOC_MODEL
                if (listOf("SM8735", "SM8750", "SM8845", "SM8850").any { soc.contains(it, ignoreCase = true) }) return true
            }
            return Build.HARDWARE.contains("sun", ignoreCase = true)
        }
    }
}

/**
 * Reshapes the GPU's 16:9 render budget to the current window's aspect ratio so the game fills
 * non-16:9 screens (e.g. a foldable's near-square inner display) instead of being letterboxed.
 * The pixel count stays the same as [baseSize], so GPU cost matches the 16:9 default.
 */
private fun fitScreenSizeToWindow(context: Context, baseSize: String, foldedAtStart: Boolean): String {
    // Unfolded (or an ordinary phone): the full display, on a foldable the inner screen the game is meant for.
    // Started folded shut on purpose: the small window, so the picture fills it; unfolding later only scales it.
    val (curWidth, curHeight) = windowSize(context, maximum = false)
    val (maxWidth, maxHeight) = windowSize(context, maximum = true)
    val (width, height) = fitBasis(foldedAtStart, curWidth, curHeight, maxWidth, maxHeight)
    return fitScreenSize(baseSize, width, height)
}

internal fun fitScreenSize(baseSize: String, windowWidth: Int, windowHeight: Int): String {
    if (windowWidth <= 0 || windowHeight <= 0) return baseSize
    val (baseWidth, baseHeight) = baseSize.split("x").map { it.toInt() }
    val aspect = maxOf(windowWidth, windowHeight).toFloat() / minOf(windowWidth, windowHeight)
    val height = sqrt(baseWidth * baseHeight / aspect)
    val width = height * aspect
    return "${(width / 2).roundToInt() * 2}x${(height / 2).roundToInt() * 2}"
}

private fun prepareLaunch(
    context: Context,
    gameRoot: File,
    gpu: GpuProfile,
    screenSize: String,
    maxFps: Int,
    inputMode: InputMode,
    onStatus: (String) -> Unit,
): String {
    installBundledComponents(context, onStatus)
    StickSensitivity.apply(context)
    val input = WowTouch.settingsFor(inputMode, WowTouch.wasApplied(context), app.gamenative.ui.screen.wow.klick.TouchOptionStore.load(context))

    check(File(gameRoot, BUILD_INFO).exists()) { "Missing $BUILD_INFO in $gameRoot. Copy it from your WoW install." }
    try {
        ensureGameConfig(gameRoot, screenSize, GameRegion.saved(context), maxFps, input.interfaceStyle, graphicsCvars(GraphicsPreset.current(context)))
    } catch (e: Exception) {
        if (e.isPermissionDenied()) throw StorageAccessDeniedException(e)
        throw e
    }

    // Buttons and wheel fields that store a WoW action take the keys the player has set in WoW (never fails the start).
    WowActionSource.refresh(gameRoot)

    // The addon TouchForever goes into the game folder FIRST (every start, whatever the icon setting says), before the container
    // and the game are started, so WoW loads the current files at its start and nobody has to type /reload. Never fails the start;
    // the result tells the app later whether a reload would help when the running addon is older (AddonSetup.diskCurrent).
    app.gamenative.wowicons.AddonSetup.run(context, gameRoot)

    // Real spell/item icons on buttons: reads what the addon wrote and loads the icons in the background
    // (setting "Echte Symbole aus WoW"; never fails the start).
    app.gamenative.wowicons.WowIcons.start(context, gameRoot)

    val arm64Exe = File(gameRoot, "$FLAVOR_DIR/$EXE_NAME")
    if (!arm64Exe.exists()) {
        try {
            WowClientDownloader.download(gameRoot, onStatus)
        } catch (e: Exception) {
            if (!arm64Exe.exists()) throw IllegalStateException("Couldn't download $EXE_NAME: ${e.message}", e)
            Timber.w(e, "Client update check failed, launching the existing $EXE_NAME")
        }
    }

    onStatus(context.getString(R.string.game_status_container, gpu.label))
    val containerManager = ContainerManager(context)
    val config = containerConfig(gameRoot, gpu, screenSize)
    val container = containerManager.getContainerById(CONTAINER_ID)
    if (container == null) {
        onStatus(context.getString(R.string.game_status_wine))
        containerManager.createContainer(CONTAINER_ID, config)
    } else {
        container.loadData(config)
        container.saveData()
    }
    applyInputSettings(context, containerManager.getContainerById(CONTAINER_ID), input, inputMode)
    WowTouch.markApplied(context, inputMode.isTouch)
    check(containerManager.hasContainer(CONTAINER_ID)) { "Container creation failed. Check system storage and logs." }

    // Keep the plaintext credentials on disk for as short a time as possible: write login.txt only when the exe starts next.
    BattleNetSignIn.load(context)?.let { login ->
        BattleNetSignIn.writeLoginFile(gameRoot, login)
        BattleNetSignIn.startLoginWatcher(gameRoot)
    }
    return CONTAINER_ID
}

private fun containerConfig(gameRoot: File, gpu: GpuProfile, screenSize: String): JSONObject {
    val samsungEnv = if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) " FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1" else ""
    val envVars = "WRAPPER_MAX_IMAGE_COUNT=0 ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact,deck_emu MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=0 MESA_VK_WSI_PRESENT_MODE=mailbox TU_DEBUG=${gpu.tuDebug} VKD3D_SHADER_MODEL=6_0 PULSE_LATENCY_MSEC=144$samsungEnv"
    return JSONObject().apply {
        put("id", CONTAINER_ID)
        put("name", "WoW Forever")
        put("screenSize", screenSize)
        put("envVars", envVars)
        put("graphicsDriver", "Wrapper")
        put("graphicsDriverVersion", gpu.driver)
        put("graphicsDriverConfig", "version=${gpu.driver},adrenotoolsTurnip=1,resourceType=buffer,bcnEmulation=auto,quality=high")
        put("displayRenderer", "vulkan")
        put("dxwrapper", "dxvk-$DXVK_VERSION-1")
        put("dxwrapperConfig", "version=$DXVK_VERSION-1")
        put("wineVersion", "$PROTON_VERSION-1")
        put("containerVariant", "bionic")
        put("fexcoreVersion", "2609-0")
        put("drives", "D:/storage/emulated/0/DownloadE:/data/data/app.wowforever/storageG:${gameRoot.path}")
        put("executablePath", "G:\\$FLAVOR_DIR\\$EXE_NAME")
        put("execArgs", "-d3d11")
        put("showFPS", true)
        // Keep running in the background: a suspended client drops its Battle.net connection, e.g. while the
        // player fetches an authenticator code from another app.
        put("suspendPolicy", Container.SUSPEND_POLICY_NEVER)
        put("startupSelection", Container.STARTUP_SELECTION_AGGRESSIVE.toInt())
        put("wow64Mode", true)
        put("wincomponents", "direct3d=0,directsound=0,directinput8=0,directinput=0,directmusic=0,directshow=0,directplay=0,vcrun2010=0,wmdecoder=0,opengl=0")
    }
}

private val CONFIG_DEFAULTS = linkedMapOf(
    "agentUID" to "\"$TARGET_PRODUCT\"",
    "gxApi" to "\"D3D11\"",
    "textLocale" to "\"enUS\"",
    "audioLocale" to "\"enUS\"",
    "gxMaximize" to "\"1\"",
    "graphicsQuality" to "\"0\"",
    "ResampleQuality" to "\"0\"",
    "RenderScale" to "\"1\"",
    "farclip" to "\"1200\"",
    "horizonClip" to "\"1200\"",
    "RAIDfarclip" to "\"1200\"",
    "RAIDhorizonClip" to "\"1200\"",
    "shadowMode" to "\"0\"",
    "graphicsShadowQuality" to "\"0\"",
    "raidGraphicsShadowQuality" to "\"0\"",
    "worldBaseMip" to "\"0\"",
    "RAIDworldBaseMip" to "\"0\"",
    "graphicsTextureResolution" to "\"2\"",
    "raidGraphicsTextureResolution" to "\"2\"",
    "componentTextureLevel" to "\"0\"",
    "RAIDcomponentTextureLevel" to "\"0\"",
    "entityShadowFadeScale" to "\"0\"",
    "refraction" to "\"0\"",
    "groundEffectDensity" to "\"16\"",
    "GamePadEnable" to "\"1\"",
    "InputDeviceInterfaceStyle" to "\"1\"",
    // Answers the first-run Screen Narrator prompt, which otherwise blocks login.txt auto-login.
    "showScreenNarrationDialog" to "\"0\"",
    "accessibilityScreenNarrationEnabled" to "\"0\"",
)

internal fun ensureGameConfig(
    root: File,
    screenSize: String,
    region: GameRegion?,
    maxFps: Int = DEFAULT_MAX_FPS,
    interfaceStyle: String? = null,
    graphics: Map<String, String> = emptyMap(),
) {
    val flavorDir = File(root, FLAVOR_DIR)
    flavorDir.mkdirs()
    val flavorInfo = File(flavorDir, ".flavor.info")
    if (!flavorInfo.exists()) {
        flavorInfo.writeText("Product Flavor!STRING:0\n$TARGET_PRODUCT\n")
    }
    // Handheld defaults are only written on first boot so in-game changes stick, with four exceptions that are
    // forced on every launch (plus the graphics preset's cvars, none for "Eigene"). The FPS cap (maxFPS, maxFPSBk) is the launcher's setting, like the resolution. gxApi: DXVK only works with D3D11, and a config copied from a Mac would otherwise
    // select Metal. Resolutions match the container, which the launcher sizes: WoW's "auto" picks a 720p size
    // because it sees a low-end Intel GPU via Wine, and saves that size on exit as if the player chose it.
    // The portal is the region picked in the launcher, or kept as is (filled from the install's region when
    // missing); without the Battle.net app to supply it, WoW otherwise stops at a region picker before login.
    val configWtf = File(root, CONFIG_WTF)
    val betaConfigWtf = File(root, "$LEGACY_FLAVOR_DIR/WTF/Config.wtf")
    if (!configWtf.exists() && betaConfigWtf.isFile) {
        // Carry the player's settings over from the beta, minus the lines that pointed it at the beta servers.
        configWtf.parentFile?.mkdirs()
        val betaOnly = listOf("SET portal ", "SET agentUID ", "SET CACHE-")
        val carried = betaConfigWtf.readLines().filterNot { line -> betaOnly.any { line.startsWith(it, ignoreCase = true) } }
        configWtf.writeText((carried + "SET agentUID ${CONFIG_DEFAULTS.getValue("agentUID")}").joinToString("\n") + "\n")
    }
    if (!configWtf.exists()) {
        configWtf.parentFile?.mkdirs()
        configWtf.writeText(CONFIG_DEFAULTS.entries.joinToString("\n") { "SET ${it.key} ${it.value}" } + "\n")
    }

    val forced = mapOf("gxApi" to CONFIG_DEFAULTS.getValue("gxApi"), "portal" to "\"${region?.name ?: portalOf(root)}\"") +
        RESOLUTION_KEYS.associateWith { "\"$screenSize\"" } +
        mapOf("maxFPS" to "\"$maxFps\"", "maxFPSBk" to "\"$BACKGROUND_FPS\"", "useMaxFPS" to "\"1\"", "useMaxFPSBk" to "\"1\"") +
        (interfaceStyle?.let { mapOf("InputDeviceInterfaceStyle" to "\"$it\"") } ?: emptyMap()) +
        graphics.mapValues { "\"${it.value}\"" }
    val forcedLines = forced.map { (key, value) -> "SET $key $value" }
    val original = configWtf.readLines()
    if (!original.containsAll(forcedLines)) {
        val kept = original.filterNot { line -> forced.keys.any { line.startsWith("SET $it ", ignoreCase = true) } }
        configWtf.writeText((kept + forcedLines).joinToString("\n") + "\n")
    }
}

internal const val DEFAULT_MAX_FPS = 60
private const val BACKGROUND_FPS = 8

private val RESOLUTION_KEYS = listOf("gxFullscreenResolution", "gxWindowedResolution")

private const val CONFIG_WTF = "$FLAVOR_DIR/WTF/Config.wtf"

// The portal Config.wtf already points at, else the install's region from .build.info.
private fun portalOf(root: File) =
    File(root, CONFIG_WTF).takeIf { it.isFile }?.readLines()?.firstOrNull { it.startsWith("SET portal ", ignoreCase = true) }
        ?.substringAfter(" ")?.substringAfter(" ")?.trim('"')?.takeIf { it.isNotBlank() }
        ?: WowClientDownloader.readBuildInfo(File(root, BUILD_INFO))?.get("Branch")?.takeIf { it.isNotBlank() }?.uppercase() ?: "US"

private fun installBundledComponents(context: Context, onStatus: (String) -> Unit) {
    installDriver(context, DEFAULT_DRIVER, "turnip-wow-scheduler-test.zip", onStatus)
    installDriver(context, ADRENO_8XX_DRIVER, "turnip-V32-RP6sched.zip", onStatus)

    val protonDir = File(ImageFs.getSharedProtonDir(context), PROTON_VERSION)
    if (!File(protonDir, "profile.json").exists() || !File(protonDir, "bin/wine").exists()) {
        onStatus(context.getString(R.string.game_status_proton))
        check(extractBundledWcp(context, "$PROTON_VERSION.wcp", protonDir)) { "Failed to extract Proton or graphics components." }
        File(protonDir, "bin").listFiles()?.forEach {
            it.setExecutable(true, false)
            it.setReadable(true, false)
        }
    }

    val dxvkDir = File(ContentsManager.getContentTypeDir(context, ContentProfile.ContentType.CONTENT_TYPE_DXVK), DXVK_VERSION)
    if (!File(dxvkDir, "profile.json").exists()) {
        onStatus(context.getString(R.string.game_status_dxvk))
        if (!extractBundledWcp(context, "dxvk-$DXVK_VERSION.wcp", dxvkDir)) Timber.e("Failed to extract DXVK")
    }

    ImageFsInstaller.ensureBionicLib(context, ImageFs.find(context).rootDir)
    ContentsManager(context).syncContents()
}

private fun installDriver(context: Context, driverName: String, assetZip: String, onStatus: (String) -> Unit) {
    val driverDir = File(context.filesDir, "contents/adrenotools/$driverName")
    if (File(driverDir, "meta.json").exists()) return
    onStatus(context.getString(R.string.game_status_driver, driverName))
    driverDir.mkdirs()
    if (!FileUtils.extractZipFromAssets(context, "bundled_components/$assetZip", driverDir)) {
        Timber.e("Error extracting driver $driverName from $assetZip")
    }
}

private fun extractBundledWcp(context: Context, assetName: String, destination: File): Boolean {
    destination.mkdirs()
    return TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "bundled_components/$assetName", destination)
}

/** Writes the input mode into the game container: touchscreen flag, gesture config, the stick-only profile and the "wowTouch" marker. */
private fun applyInputSettings(context: Context, container: Container?, input: InputLaunchSettings, inputMode: InputMode) {
    if (container == null) return
    val hadTouchSetup = container.getExtra(WowTouch.EXTRA_KEY) == "1"
    // The player's own values (Quick menu "Touchscreen-Modus" switch, gesture dialog) win over our defaults; the defaults
    // are written on first setup, when another Steuerung is chosen and after "Zurücksetzen" (see TouchSetupPolicy).
    val plan = TouchSetupPolicy.plan(
        mode = inputMode,
        defaults = input.gestureJson,
        hadTouchSetup = hadTouchSetup,
        containerTouchscreen = container.isTouchscreenMode(),
        containerGestures = container.getGestureConfig(),
        state = TouchSetupStore.load(context),
    )
    container.setTouchscreenMode(plan.touchscreenMode)
    plan.gestureConfig?.let { container.setGestureConfig(it) }
    if (input.touchscreenMode) {
        // "Touch (Mein Layout)" is created from the current "Touch (WoW)" the first time; "Touch (WoW komplett)" is the
        // bundled template (controls-7.icp), created from the asset if the update copy has not added it yet.
        val profileId = WowTouchProfiles.resolveProfileId(context, inputMode)
        if (profileId != null) container.putExtra("profileId", profileId.toString())
        else Timber.w("Touch profile '%s' not found, keeping the container's profile", inputMode.profileName)
    } else if (hadTouchSetup) {
        // Back to Standard after Touch: drop the touch-only profile (the gestures went to the stash).
        container.putExtra("profileId", null)
    }
    container.putExtra(WowTouch.EXTRA_KEY, input.wowTouchExtra)
    container.saveData()
    TouchSetupStore.save(context, plan.state)
}
