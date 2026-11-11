package app.gamenative.ui.component

import android.view.KeyEvent
import timber.log.Timber
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.GyroSettings
import app.gamenative.ui.component.dialog.GyroSettingsDialog
import app.gamenative.ui.screen.wow.WindowHideMode
import app.gamenative.ui.screen.wow.InputMode
import app.gamenative.ui.screen.wow.LayoutNameDialog
import app.gamenative.ui.screen.wow.LayoutPickerDialog
import app.gamenative.ui.screen.wow.LayoutResetDialog
import app.gamenative.ui.screen.wow.QuickMenuAccordion
import app.gamenative.ui.screen.wow.QuickMenuContext
import app.gamenative.ui.screen.wow.QuickMenuRowKind
import app.gamenative.ui.screen.wow.QuickMenuRowText
import app.gamenative.ui.components.asString
import app.gamenative.ui.screen.wow.QuickMenuValues
import app.gamenative.ui.screen.wow.WheelActionDialog
import app.gamenative.ui.screen.wow.WowTouchProfiles
import app.gamenative.ui.screen.wow.klick.ClickWheel
import app.gamenative.ui.screen.wow.quickMenuGroupSummary
import app.gamenative.ui.screen.wow.quickMenuRowView
import app.gamenative.ui.screen.wow.QuickMenuPage
import app.gamenative.ui.screen.wow.QuickMenuSection
import app.gamenative.ui.screen.wow.quickMenuSections
import app.gamenative.ui.screen.wow.WowQuickSettings
import app.gamenative.ui.data.PerformanceHudConfig
import app.gamenative.ui.data.PerformanceHudSize
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.adaptivePanelWidth
import app.gamenative.utils.MathUtils.normalizedProgress
import com.winlator.container.Container
import com.winlator.renderer.GLRenderer
import com.winlator.renderer.VulkanRenderer
import com.winlator.winhandler.ProcessInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object QuickMenuAction {
    const val KEYBOARD = 1
    const val INPUT_CONTROLS = 2
    const val EXIT_GAME = 3
    const val EDIT_CONTROLS = 4
    const val EDIT_PHYSICAL_CONTROLLER = 5
    const val PERFORMANCE_HUD = 6
    const val TOUCHSCREEN_MODE = 7
    const val DISABLE_MOUSE = 8
    const val SHOOTER_MODE = 9
    const val RADIAL_MENU = 10
    const val GYRO = 11
    const val CHECK_ICONS = 12
    const val STICK_WALK = 13
    const val STICK_CAMERA = 14
    const val COOLDOWNS = 15
    const val LOGIN_FULL = 16
    const val LOGIN_PASSWORD = 17
    const val LOGIN_EMAIL = 18
    const val LOGIN_NOTE = 19
    const val TOUCH_HIDE_LOGIN = 20
    const val TOUCH_SHOW_NOW = 21
    const val VOICE_LANGUAGE = 22
    const val TOUCH_HIDE_WINDOWS = 23
    const val REPORT = 24
    const val REPORT_NOTE = 25
    const val ADDON_OPTIONS = 26
    const val TOUCH_STYLE = 27
    const val TOUCH_CONTRAST = 28
    const val CONTEXT_MENU = 29
    const val TAP_VIEW_ITEMS = 30
    const val TWO_FINGER_TAP = 31
    const val HOLD_TIME = 32
    const val TAP_WINDOW = 33
    const val CLICK_WHEEL = 34
    const val WHEEL_CENTER = 35
    const val WHEEL_UP = 36
    const val WHEEL_RIGHT = 37
    const val WHEEL_DOWN = 38
    const val WHEEL_LEFT = 39
    const val SWIPE_SCROLL = 40
    const val SWIPE_SPEED = 41
    const val RESET_LAYOUT = 42
    const val COOLDOWN_GCD = 43
    const val LAYOUT_CHOOSE = 44
    const val LAYOUT_SAVE_AS = 45
}

private object QuickMenuTab {
    const val HUD = 0
    const val LSFG = 1
    const val EFFECTS = 2
    const val CONTROLLER = 3
    const val TOOLS = 4
    const val SPIEL = 5
}

private class GyroQuickMenuState(private val container: Container) {
    var settings by mutableStateOf(GyroSettings.fromContainer(container))
        private set

    val isAvailable: Boolean
        get() = PluviaApp.inputControlsView?.isGyroAvailable == true

    val isTiltAvailable: Boolean
        get() = PluviaApp.inputControlsView?.isGyroTiltAvailable == true

    fun setEnabled(enabled: Boolean) {
        if (enabled && !isAvailable) return
        persistSettings(
            settings.copy(mode = if (enabled) settings.lastTarget else GyroSettings.MODE_DISABLED),
        )
    }

    fun persistSettings(value: GyroSettings) {
        settings = value.normalized()
        settings.saveTo(container)
        applyToCurrentView()
    }

    fun hasGyroControlAssigned(activationMode: Int): Boolean =
        PluviaApp.inputControlsView?.hasGyroControlAssigned(activationMode) == true

    fun applyToCurrentView() {
        PluviaApp.inputControlsView?.setGyroSettings(settings)
    }
}

data class QuickMenuItem(
    val id: Int,
    val icon: ImageVector,
    val labelResId: Int,
    val accentColor: Color = Color.Unspecified,
    val enabled: Boolean = true,
    // Plain text shown instead of the string resource (used by entries that have no translations).
    val label: String? = null,
)

private enum class PerformanceHudPreset(val labelResId: Int) {
    FPS_ONLY(R.string.performance_hud_preset_fps_only),
    ESSENTIAL(R.string.performance_hud_preset_essential),
    BATTERY(R.string.performance_hud_preset_battery),
    FULL(R.string.performance_hud_preset_full),
}

private fun applyPerformanceHudPreset(
    currentConfig: PerformanceHudConfig,
    preset: PerformanceHudPreset,
): PerformanceHudConfig {
    return when (preset) {
        PerformanceHudPreset.FPS_ONLY -> currentConfig.copy(
            showFrameRate = true,
            showCpuUsage = false,
            showGpuUsage = false,
            showRamUsage = false,
            showBatteryLevel = false,
            showPowerDraw = false,
            showBatteryRuntime = false,
            showBatteryTemperature = false,
            showClockTime = false,
            showCpuTemperature = false,
            showGpuTemperature = false,
            showFrameRateGraph = false,
            showCpuUsageGraph = false,
            showGpuUsageGraph = false,
        )

        PerformanceHudPreset.ESSENTIAL -> currentConfig.copy(
            showFrameRate = true,
            showCpuUsage = true,
            showGpuUsage = true,
            showRamUsage = true,
            showBatteryLevel = false,
            showPowerDraw = false,
            showBatteryRuntime = false,
            showBatteryTemperature = false,
            showClockTime = false,
            showCpuTemperature = false,
            showGpuTemperature = false,
            showFrameRateGraph = false,
            showCpuUsageGraph = false,
            showGpuUsageGraph = false,
        )

        PerformanceHudPreset.BATTERY -> currentConfig.copy(
            showFrameRate = true,
            showCpuUsage = true,
            showGpuUsage = true,
            showRamUsage = true,
            showBatteryLevel = true,
            showPowerDraw = false,
            showBatteryRuntime = true,
            showBatteryTemperature = true,
            showClockTime = false,
            showCpuTemperature = false,
            showGpuTemperature = false,
            showFrameRateGraph = true,
            showCpuUsageGraph = false,
            showGpuUsageGraph = false,
        )

        PerformanceHudPreset.FULL -> currentConfig.copy(
            showFrameRate = true,
            showCpuUsage = true,
            showGpuUsage = true,
            showRamUsage = true,
            showBatteryLevel = true,
            showPowerDraw = true,
            showBatteryRuntime = true,
            showBatteryTemperature = true,
            showClockTime = true,
            showCpuTemperature = true,
            showGpuTemperature = true,
            showFrameRateGraph = true,
            showCpuUsageGraph = true,
            showGpuUsageGraph = true,
        )
    }
}

private fun matchesPerformanceHudPreset(
    currentConfig: PerformanceHudConfig,
    preset: PerformanceHudPreset,
): Boolean {
    val presetConfig = applyPerformanceHudPreset(currentConfig, preset)
    return currentConfig.showFrameRate == presetConfig.showFrameRate &&
        currentConfig.showCpuUsage == presetConfig.showCpuUsage &&
        currentConfig.showGpuUsage == presetConfig.showGpuUsage &&
        currentConfig.showRamUsage == presetConfig.showRamUsage &&
        currentConfig.showBatteryLevel == presetConfig.showBatteryLevel &&
        currentConfig.showPowerDraw == presetConfig.showPowerDraw &&
        currentConfig.showBatteryRuntime == presetConfig.showBatteryRuntime &&
        currentConfig.showBatteryTemperature == presetConfig.showBatteryTemperature &&
        currentConfig.showClockTime == presetConfig.showClockTime &&
        currentConfig.showCpuTemperature == presetConfig.showCpuTemperature &&
        currentConfig.showGpuTemperature == presetConfig.showGpuTemperature &&
        currentConfig.showFrameRateGraph == presetConfig.showFrameRateGraph &&
        currentConfig.showCpuUsageGraph == presetConfig.showCpuUsageGraph &&
        currentConfig.showGpuUsageGraph == presetConfig.showGpuUsageGraph
}

// fpsLimiterSteps / fpsLimiterCurrentIndex / fpsLimiterProgress /
// nextFpsLimiterValue / previousFpsLimiterValue live in FpsLimiterUtils.kt

/** Performance HUD + FPS limiter state/callbacks as one QuickMenu parameter instead of eight —
 * XServerScreen's call site sits at the dex verifier's 255-register limit, and every argument
 * of that call costs a register there (a runtime VerifyError from exactly that was reproduced
 * on device). */
class PerformanceQuickMenuState(
    val hudEnabled: Boolean = false,
    val hudConfig: PerformanceHudConfig = PerformanceHudConfig(),
    val fpsLimiterEnabled: Boolean = true,
    val fpsLimiterTarget: Int = 60,
    val fpsLimiterMax: Int = 60,
    val onHudConfigChanged: (PerformanceHudConfig) -> Unit = {},
    val onFpsLimiterEnabledChanged: (Boolean) -> Unit = {},
    val onFpsLimiterChanged: (Int) -> Unit = {},
)

/** LSFG hot-reload state/callbacks as one QuickMenu parameter instead of seven — same
 * register-limit reason as [PerformanceQuickMenuState]. Tab only visible when [isAvailable]. */
class LsfgQuickMenuState(
    val isAvailable: Boolean = false,
    val multiplier: Int = 2,
    val flowScale: Float = 0.80f,
    val performanceMode: Boolean = true,
    val onMultiplierChanged: (Int) -> Unit = {},
    val onFlowScaleChanged: (Float) -> Unit = {},
    val onPerformanceModeChanged: (Boolean) -> Unit = {},
)

@Composable
fun QuickMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onItemSelected: (Int) -> Boolean,
    renderer: VulkanRenderer? = null,
    glRenderer: GLRenderer? = null,
    container: Container? = null,
    wineProcesses: List<ProcessInfo> = emptyList(),
    isWineProcessesLoading: Boolean = false,
    onToolsVisibilityChanged: (Boolean) -> Unit = {},
    onEndWineProcess: (ProcessInfo) -> Unit = {},
    performance: PerformanceQuickMenuState = PerformanceQuickMenuState(),
    hasPhysicalController: Boolean = false,
    isTouchscreenModeActive: Boolean = false,
    onTouchGestureSettingsClick: () -> Unit = {},
    isShooterModeActive: Boolean = false,
    onShooterModeSettingsClick: () -> Unit = {},
    activeToggleIds: Set<Int> = emptySet(),
    lsfg: LsfgQuickMenuState = LsfgQuickMenuState(),
    onAnimationComplete: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isPerformanceHudEnabled = performance.hudEnabled
    val performanceHudConfig = performance.hudConfig
    val fpsLimiterEnabled = performance.fpsLimiterEnabled
    val fpsLimiterTarget = performance.fpsLimiterTarget
    val fpsLimiterMax = performance.fpsLimiterMax
    val onPerformanceHudConfigChanged = performance.onHudConfigChanged
    val onFpsLimiterEnabledChanged = performance.onFpsLimiterEnabledChanged
    val onFpsLimiterChanged = performance.onFpsLimiterChanged
    val isLsfgAvailable = lsfg.isAvailable
    val lsfgMultiplier = lsfg.multiplier
    val lsfgFlowScale = lsfg.flowScale
    val lsfgPerformanceMode = lsfg.performanceMode
    val onLsfgMultiplierChanged = lsfg.onMultiplierChanged
    val onLsfgFlowScaleChanged = lsfg.onFlowScaleChanged
    val onLsfgPerformanceModeChanged = lsfg.onPerformanceModeChanged
    val exitGameItem = QuickMenuItem(
        id = QuickMenuAction.EXIT_GAME,
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        labelResId = R.string.exit_game,
        accentColor = PluviaTheme.colors.accentDanger,
    )

    val checkContext = androidx.compose.ui.platform.LocalContext.current

    // Stick-Empfindlichkeit und Abklingzeiten wirken sofort; Werte und Klicks stecken in einer eigenen Klasse (eine Variable hier).
    val wowQuick = remember { WowQuickSettings(checkContext) }
    wowQuick.container = container

    // Kept here rather than plumbed through XServerScreen: that composable sits at the
    // dex verifier's register limit and extra locals can produce invalid bytecode.
    val gyroMenu = remember(container?.id) { container?.let(::GyroQuickMenuState) }

    // Zeilen mit rundem Symbol (nur Reiter Hilfe) und Zeilen mit eigener Verfügbarkeit (Gyro); alle anderen Zeilen entstehen aus
    // QuickMenuRows.kt (Titel und Wert) und werden bei Bedarf mit einem Standardsymbol als Klickziel versehen.
    // Welche Zeile wo und unter welcher Überschrift steht, regelt QuickMenuLayout.kt (reine Daten, getestet).
    val purple = PluviaTheme.colors.accentPurple
    val itemsById: Map<Int, QuickMenuItem> = listOf(
        QuickMenuItem(
            QuickMenuAction.GYRO, Icons.Default.ScreenRotation, R.string.gyro_aiming, purple,
            enabled = gyroMenu?.isAvailable == true,
        ),
        // Hilfe: Fehlerbericht als Zip erstellen und über das Teilen-Menü weitergeben (nichts wird von selbst gesendet).
        QuickMenuItem(QuickMenuAction.REPORT, Icons.Default.BugReport, R.string.input_controls, purple, label = "Fehlerbericht erstellen"),
        QuickMenuItem(
            QuickMenuAction.REPORT_NOTE, Icons.Default.BugReport, R.string.input_controls,
            label = wowQuick.reportNote.orEmpty(), enabled = false,
        ),
    ).associateBy { it.id }

    // Im Login zeigt das Menü die Anmeldezeilen, in der Spielwelt nicht. Beim Öffnen einmal geprüft (das Spiel steht dann still),
    // damit die Zeilen beim Schließen nicht noch springen.
    var inLogin by remember { mutableStateOf(true) }
    var touchLayoutsAvailable by remember { mutableStateOf(true) }
    LaunchedEffect(isVisible) {
        if (isVisible) {
            touchLayoutsAvailable = InputMode.current(checkContext).isTouch
            inLogin = wowQuick.inLogin()
            // Andere Orte schreiben dieselben Werte (Touch-Editor: Sticks, Zahnrad: Gesten): bei jedem Öffnen frisch lesen.
            wowQuick.reload()
        }
    }
    val layoutContext = QuickMenuContext(
        inLogin = inLogin,
        hasSavedLogin = wowQuick.hasLogin,
        hasLoginNote = wowQuick.loginNote != null,
        hasReportNote = wowQuick.reportNote != null,
        hasPhysicalController = hasPhysicalController,
        hasContainer = container != null,
        hasTouchLayouts = touchLayoutsAvailable,
    )
    val spielSections = quickMenuSections(QuickMenuPage.SPIEL, layoutContext)
    val steuerungSections = quickMenuSections(QuickMenuPage.STEUERUNG, layoutContext)
    val hilfeSections = quickMenuSections(QuickMenuPage.HILFE, layoutContext)

    val gyroEnabled = gyroMenu?.settings?.mode?.let { it != GyroSettings.MODE_DISABLED } == true
    val isRowActive: (Int) -> Boolean = { id ->
        when (id) {
            QuickMenuAction.GYRO -> gyroEnabled
            else -> id in activeToggleIds || wowQuick.isOn(id)
        }
    }
    var showGyroSettingsDialog by rememberSaveable(container?.id) { mutableStateOf(false) }
    val onRowClick: (QuickMenuItem) -> Unit = { item ->
        if (item.id == QuickMenuAction.GYRO && gyroMenu != null) {
            gyroMenu.setEnabled(!gyroEnabled)
        } else if (item.id == QuickMenuAction.CHECK_ICONS) {
            // Check of the icon data and the cooldown strip; the dialog opens over the running game.
            app.gamenative.cooldown.CooldownCheckDialog.show(checkContext)
            onDismiss()
        } else if (wowQuick.handle(item.id)) {
            // Stick-Empfindlichkeit / Abklingzeiten / Touch-Schalter: wirkt sofort, das Menü bleibt offen.
            // Anmeldung eintippen / Touch-Steuerung zeigen / Fehlerbericht: das Menü schließt, das Spiel läuft weiter.
            if (wowQuick.closeRequested) onDismiss()
        } else if (onItemSelected(item.id)) {
            onDismiss()
        }
    }
    // Zweites Symbol rechts neben der Zeile: Einstellungen des Modus, solange er an ist.
    val rowExtraFor: (Int) -> QuickMenuRowExtra? = { id ->
        when {
            id == QuickMenuAction.TOUCHSCREEN_MODE && isTouchscreenModeActive ->
                QuickMenuRowExtra(R.string.gesture_settings_title, onTouchGestureSettingsClick)
            id == QuickMenuAction.SHOOTER_MODE && isShooterModeActive ->
                QuickMenuRowExtra(R.string.shooter_mode_settings_title, onShooterModeSettingsClick)
            id == QuickMenuAction.GYRO && gyroEnabled ->
                QuickMenuRowExtra(R.string.gyro_settings_title) { showGyroSettingsDialog = true }
            else -> null
        }
    }

    var lsfgPresentMode by remember(container?.id) {
        mutableStateOf(container?.let { app.gamenative.utils.LsfgQuickMenuHelper.presentMode(it) } ?: "mailbox")
    }
    var lsfgBackend by remember(container?.id) {
        mutableStateOf(container?.let { app.gamenative.utils.LsfgVkManager.backend(it) } ?: "native")
    }

    var selectedTab by rememberSaveable {
        mutableIntStateOf(
            when {
                PrefManager.quickMenuLastTab == QuickMenuTab.LSFG && !isLsfgAvailable -> QuickMenuTab.HUD
                else -> PrefManager.quickMenuLastTab
            }
        )
    }
    val selectedTabTitle = when (selectedTab) {
        QuickMenuTab.HUD -> stringResource(R.string.performance_hud)
        QuickMenuTab.LSFG -> stringResource(R.string.lsfg_tab_title)
        QuickMenuTab.EFFECTS -> stringResource(R.string.screen_effects)
        QuickMenuTab.TOOLS -> stringResource(QuickMenuPage.HILFE.titleRes)
        QuickMenuTab.SPIEL -> stringResource(QuickMenuPage.SPIEL.titleRes)
        else -> stringResource(QuickMenuPage.STEUERUNG.titleRes)
    }

    val hudScrollState = rememberScrollState()
    val effectsScrollState = rememberScrollState()
    val lsfgScrollState = rememberScrollState()
    val effectsTabFocusRequester = remember { FocusRequester() }
    val controllerScrollState = rememberScrollState()
    val spielScrollState = rememberScrollState()
    val spielTabFocusRequester = remember { FocusRequester() }
    val spielItemFocusRequester = remember { FocusRequester() }
    val lsfgTabFocusRequester = remember { FocusRequester() }
    val hudTabFocusRequester = remember { FocusRequester() }
    val controllerTabFocusRequester = remember { FocusRequester() }
    val toolsTabFocusRequester = remember { FocusRequester() }
    val hudItemFocusRequester = remember { FocusRequester() }
    val effectsItemFocusRequester = remember { FocusRequester() }
    val controllerItemFocusRequester = remember { FocusRequester() }
    val toolsItemFocusRequester = remember { FocusRequester() }
    val lsfgItemFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = isVisible

    LaunchedEffect(visibleState.currentState, visibleState.isIdle) {
        if (visibleState.isIdle) {
            onAnimationComplete(visibleState.currentState)
        }
    }

    BackHandler(enabled = isVisible) {
        onDismiss()
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(200),
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(150),
            ),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            val panelWidth = adaptivePanelWidth(400.dp)

            Surface(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 24.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.quick_menu_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        QuickMenuCloseButton(onClick = onDismiss)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .fillMaxHeight()
                                .focusGroup(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val tabScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(tabScrollState),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Reihenfolge nach Häufigkeit: Spiel, Steuerung, dann Anzeige und Leistung, zuletzt Hilfe.
                                QuickMenuTabButton(
                                    icon = Icons.Default.SportsEsports,
                                    contentDescription = stringResource(QuickMenuPage.SPIEL.titleRes),
                                    selected = selectedTab == QuickMenuTab.SPIEL,
                                    accentColor = PluviaTheme.colors.accentPurple,
                                    onSelected = {
                                        selectedTab = QuickMenuTab.SPIEL
                                        PrefManager.quickMenuLastTab = selectedTab
                                    },
                                    modifier = Modifier.width(56.dp),
                                    focusRequester = spielTabFocusRequester,
                                )
                                QuickMenuTabButton(
                                    icon = Icons.Default.Gamepad,
                                    contentDescription = stringResource(QuickMenuPage.STEUERUNG.titleRes),
                                    selected = selectedTab == QuickMenuTab.CONTROLLER,
                                    accentColor = PluviaTheme.colors.accentPurple,
                                    onSelected = {
                                        selectedTab = QuickMenuTab.CONTROLLER
                                        PrefManager.quickMenuLastTab = selectedTab
                                    },
                                    modifier = Modifier.width(56.dp),
                                    focusRequester = controllerTabFocusRequester,
                                )
                                QuickMenuTabButton(
                                    icon = Icons.Default.QueryStats,
                                    contentDescription = stringResource(R.string.performance_hud),
                                    selected = selectedTab == QuickMenuTab.HUD,
                                    accentColor = PluviaTheme.colors.accentPurple,
                                    onSelected = {
                                        selectedTab = QuickMenuTab.HUD
                                        PrefManager.quickMenuLastTab = selectedTab
                                    },
                                    modifier = Modifier.width(56.dp),
                                    focusRequester = hudTabFocusRequester,
                                )
                                if (isLsfgAvailable) {
                                    QuickMenuTabButton(
                                        icon = Icons.Default.Speed,
                                        contentDescription = stringResource(R.string.lsfg_tab_title),
                                        selected = selectedTab == QuickMenuTab.LSFG,
                                        accentColor = PluviaTheme.colors.accentPurple,
                                        onSelected = {
                                            selectedTab = QuickMenuTab.LSFG
                                            PrefManager.quickMenuLastTab = selectedTab
                                        },
                                        modifier = Modifier.width(56.dp),
                                        focusRequester = lsfgTabFocusRequester,
                                    )
                                }
                                if (renderer != null || glRenderer != null) {
                                    QuickMenuTabButton(
                                        icon = Icons.Default.AutoFixHigh,
                                        contentDescription = stringResource(R.string.screen_effects),
                                        selected = selectedTab == QuickMenuTab.EFFECTS,
                                        accentColor = PluviaTheme.colors.accentPurple,
                                        onSelected = {
                                            selectedTab = QuickMenuTab.EFFECTS
                                            PrefManager.quickMenuLastTab = selectedTab
                                        },
                                        modifier = Modifier.width(56.dp),
                                        focusRequester = effectsTabFocusRequester,
                                    )
                                }
                                QuickMenuTabButton(
                                    icon = Icons.Default.BarChart,
                                    contentDescription = stringResource(QuickMenuPage.HILFE.titleRes),
                                    selected = selectedTab == QuickMenuTab.TOOLS,
                                    accentColor = PluviaTheme.colors.accentPurple,
                                    onSelected = { selectedTab = QuickMenuTab.TOOLS },
                                    modifier = Modifier.width(56.dp),
                                    focusRequester = toolsTabFocusRequester,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 12.dp)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                            )

                            QuickMenuRailActionButton(
                                item = exitGameItem,
                                onClick = {
                                    if (onItemSelected(QuickMenuAction.EXIT_GAME)) {
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.width(56.dp),
                                focusRequester = exitFocusRequester,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                        ) {
                            Text(
                                text = selectedTabTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            )

                            Box(
                                modifier = Modifier.weight(1f),
                            ) {
                                when (selectedTab) {
                                    QuickMenuTab.HUD -> {
                                        PerformanceHudQuickMenuTab(
                                            isPerformanceHudEnabled = isPerformanceHudEnabled,
                                            performanceHudConfig = performanceHudConfig,
                                            fpsLimiterEnabled = fpsLimiterEnabled,
                                            fpsLimiterTarget = fpsLimiterTarget,
                                            fpsLimiterMax = fpsLimiterMax,
                                            lsfgMultiplier = if (isLsfgAvailable) lsfgMultiplier else 0,
                                            onTogglePerformanceHud = {
                                                onItemSelected(QuickMenuAction.PERFORMANCE_HUD)
                                            },
                                            onPerformanceHudConfigChanged = onPerformanceHudConfigChanged,
                                            onFpsLimiterEnabledChanged = onFpsLimiterEnabledChanged,
                                            onFpsLimiterChanged = onFpsLimiterChanged,
                                            scrollState = hudScrollState,
                                            focusRequester = hudItemFocusRequester,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }

                                    QuickMenuTab.LSFG -> {
                                        LsfgQuickMenuTab(
                                            multiplier = lsfgMultiplier,
                                            flowScale = lsfgFlowScale,
                                            performanceMode = lsfgPerformanceMode,
                                            onMultiplierChanged = onLsfgMultiplierChanged,
                                            onFlowScaleChanged = onLsfgFlowScaleChanged,
                                            onPerformanceModeChanged = onLsfgPerformanceModeChanged,
                                            backend = lsfgBackend,
                                            onBackendChanged = { backend ->
                                                lsfgBackend = backend
                                                container?.let {
                                                    app.gamenative.utils.LsfgQuickMenuHelper.applyBackend(it, backend)
                                                }
                                            },
                                            presentMode = lsfgPresentMode,
                                            onPresentModeChanged = { mode ->
                                                lsfgPresentMode = mode
                                                container?.let {
                                                    app.gamenative.utils.LsfgQuickMenuHelper.applyPresentMode(it, mode)
                                                }
                                            },
                                            scrollState = lsfgScrollState,
                                            focusRequester = lsfgItemFocusRequester,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }

                                    QuickMenuTab.EFFECTS -> {
                                        if (renderer != null) {
                                            ScreenEffectsTabContent(
                                                renderer = renderer,
                                                container = container,
                                                modifier = Modifier.fillMaxSize(),
                                                firstItemFocusRequester = effectsItemFocusRequester,
                                                scrollState = effectsScrollState,
                                            )
                                        } else if (glRenderer != null) {
                                            GLScreenEffectsTabContent(
                                                renderer = glRenderer,
                                                container = container,
                                                modifier = Modifier.fillMaxSize(),
                                                firstItemFocusRequester = effectsItemFocusRequester,
                                                scrollState = effectsScrollState,
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp, vertical = 16.dp),
                                                contentAlignment = Alignment.TopStart,
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.main_loading),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }

                                    QuickMenuTab.TOOLS -> {
                                        ToolsQuickMenuTab(
                                            helpSections = hilfeSections,
                                            itemsById = itemsById,
                                            isActive = isRowActive,
                                            onItemClick = onRowClick,
                                            processes = wineProcesses,
                                            isLoadingProcesses = isWineProcessesLoading,
                                            onEndProcess = onEndWineProcess,
                                            firstItemFocusRequester = toolsItemFocusRequester,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }

                                    QuickMenuTab.SPIEL -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(spielScrollState)
                                                .focusGroup(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            QuickMenuRows(
                                                sections = spielSections,
                                                itemsById = itemsById,
                                                wowQuick = wowQuick,
                                                values = wowQuick.values(
                                                    isRowActive(QuickMenuAction.SHOOTER_MODE),
                                                    isRowActive(QuickMenuAction.GYRO),
                                                    isRowActive(QuickMenuAction.DISABLE_MOUSE),
                                                ),
                                                isActive = isRowActive,
                                                onItemClick = onRowClick,
                                                extraFor = rowExtraFor,
                                                firstItemFocusRequester = spielItemFocusRequester,
                                            )
                                        }
                                    }

                                    QuickMenuTab.CONTROLLER -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(controllerScrollState)
                                                .focusGroup(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            QuickMenuRows(
                                                sections = steuerungSections,
                                                itemsById = itemsById,
                                                wowQuick = wowQuick,
                                                values = wowQuick.values(
                                                    isRowActive(QuickMenuAction.SHOOTER_MODE),
                                                    isRowActive(QuickMenuAction.GYRO),
                                                    isRowActive(QuickMenuAction.DISABLE_MOUSE),
                                                ),
                                                isActive = isRowActive,
                                                onItemClick = onRowClick,
                                                extraFor = rowExtraFor,
                                                firstItemFocusRequester = controllerItemFocusRequester,
                                            )
                                        }
                                    }

                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (wowQuick.showCredentialDialog) {
        // Keine Anmeldedaten gespeichert: derselbe Dialog wie im Launcher, die Daten landen verschlüsselt an derselben Stelle.
        app.gamenative.ui.screen.wow.BattleNetCredentialDialog(
            onDismiss = { wowQuick.showCredentialDialog = false },
            onConfirm = { login -> wowQuick.onCredentialsSaved(login) },
        )
    }

    QuickMenuWowDialogs(wowQuick)
    QuickMenuLayoutDialogs(wowQuick)

    if (showGyroSettingsDialog && gyroMenu != null) {
        GyroSettingsDialog(
            gyroSettings = gyroMenu.settings,
            tiltAvailable = gyroMenu.isTiltAvailable,
            hasGyroControlAssigned = gyroMenu::hasGyroControlAssigned,
            onDismiss = { showGyroSettingsDialog = false },
            onSave = { settings ->
                gyroMenu.persistSettings(settings)
                showGyroSettingsDialog = false
            },
            onAssignControl = { settings ->
                gyroMenu.persistSettings(settings)
                showGyroSettingsDialog = false
                if (onItemSelected(QuickMenuAction.EDIT_CONTROLS)) onDismiss()
            },
        )
    }

    LaunchedEffect(isVisible, selectedTab) {
        onToolsVisibilityChanged(isVisible && selectedTab == QuickMenuTab.TOOLS)
    }

    LaunchedEffect(isVisible, gyroMenu) {
        gyroMenu?.applyToCurrentView()
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            repeat(3) { attempt ->
                try {
                    when (selectedTab) {
                        QuickMenuTab.HUD -> hudItemFocusRequester.requestFocus()
                        QuickMenuTab.LSFG -> lsfgItemFocusRequester.requestFocus()
                        QuickMenuTab.EFFECTS -> effectsItemFocusRequester.requestFocus()
                        QuickMenuTab.TOOLS -> toolsItemFocusRequester.requestFocus()
                        QuickMenuTab.SPIEL -> spielItemFocusRequester.requestFocus()
                        else -> controllerItemFocusRequester.requestFocus()
                    }
                    Timber.i("QuickMenu: requestFocus succeeded for tab=%d on attempt=%d", selectedTab, attempt)
                    return@LaunchedEffect
                } catch (t: Exception) {
                    Timber.w(t, "QuickMenu: requestFocus threw for tab=%d on attempt=%d", selectedTab, attempt)
                    delay(80)
                }
            }
            Timber.w("QuickMenu: requestFocus never succeeded for tab=%d after 3 attempts", selectedTab)
        }
    }
}

/** Zweites Symbol rechts neben einer Zeile (Einstellungen des Modus), mit Beschriftung für die Bedienungshilfe. */
private class QuickMenuRowExtra(val descResId: Int, val onClick: () -> Unit) {
    val icon: ImageVector get() = Icons.Default.Settings
}

/**
 * Die Zeilen mehrerer Abschnitte untereinander, je Abschnitt mit Überschrift. Die erste Zeile bekommt [firstItemFocusRequester]
 * (der Fokus landet beim Öffnen des Reiters dort). Gehört in eine Spalte mit Abstand zwischen den Zeilen.
 */
@Composable
private fun QuickMenuSectionRows(
    sections: List<QuickMenuSection>,
    itemsById: Map<Int, QuickMenuItem>,
    isActive: (Int) -> Boolean,
    onItemClick: (QuickMenuItem) -> Unit,
    extraFor: (Int) -> QuickMenuRowExtra? = { null },
    firstItemFocusRequester: FocusRequester? = null,
) {
    var first = true
    sections.forEachIndexed { sectionIndex, section ->
        if (sectionIndex > 0) Spacer(modifier = Modifier.height(8.dp))
        QuickMenuSectionHeader(title = section.title.asString())
        section.ids.forEach { id ->
            val item = itemsById[id] ?: return@forEach
            val extra = extraFor(id)
            QuickMenuItemRow(
                item = item,
                isActive = isActive(id),
                onClick = { onItemClick(item) },
                focusRequester = if (first) firstItemFocusRequester else null,
                secondaryIcon = extra?.icon,
                secondaryContentDescriptionResId = extra?.descResId,
                onSecondaryClick = extra?.onClick,
            )
            first = false
        }
    }
}

@Composable
private fun ToolsQuickMenuTab(
    helpSections: List<QuickMenuSection>,
    itemsById: Map<Int, QuickMenuItem>,
    isActive: (Int) -> Boolean,
    onItemClick: (QuickMenuItem) -> Unit,
    processes: List<ProcessInfo>,
    isLoadingProcesses: Boolean,
    onEndProcess: (ProcessInfo) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val accentColor = PluviaTheme.colors.accentPurple

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Oben die Hilfe (Fehlerbericht), darunter der Task-Manager mit den laufenden Prozessen.
        QuickMenuSectionRows(
            sections = helpSections,
            itemsById = itemsById,
            isActive = isActive,
            onItemClick = onItemClick,
            firstItemFocusRequester = firstItemFocusRequester,
        )
        if (helpSections.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))

        QuickMenuSectionHeader(
            title = stringResource(R.string.task_manager),
            subtitle = if (isLoadingProcesses) {
                stringResource(R.string.main_loading)
            } else {
                stringResource(R.string.tools_wine_processes_running_hint, processes.size)
            },
        )

        if (!isLoadingProcesses && processes.isEmpty()) {
            Text(
                text = stringResource(R.string.tools_wine_processes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            processes.forEachIndexed { index, process ->
                QuickMenuDetailRow(
                    title = process.name + if (process.wow64Process) " *32" else "",
                    subtitle = process.formattedMemoryUsage,
                    accentColor = accentColor,
                    onActivate = {
                        onEndProcess(process)
                    },
                    focusRequester = if (index == 0 && helpSections.isEmpty()) firstItemFocusRequester else null,
                )
            }
        }
    }
}

@Composable
private fun PerformanceHudQuickMenuTab(
    isPerformanceHudEnabled: Boolean,
    performanceHudConfig: PerformanceHudConfig,
    fpsLimiterEnabled: Boolean,
    fpsLimiterTarget: Int,
    fpsLimiterMax: Int,
    lsfgMultiplier: Int,
    onTogglePerformanceHud: () -> Unit,
    onPerformanceHudConfigChanged: (PerformanceHudConfig) -> Unit,
    onFpsLimiterEnabledChanged: (Boolean) -> Unit,
    onFpsLimiterChanged: (Int) -> Unit,
    scrollState: ScrollState,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val accentColor = PluviaTheme.colors.accentPurple

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── FPS Limiter (topmost) ────────────────────────────────────────
        val lsfgActive = lsfgMultiplier >= 2
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_fps_limiter),
            subtitle = if (lsfgActive) {
                stringResource(R.string.performance_hud_fps_limiter_lsfg_base)
            } else null,
            enabled = fpsLimiterEnabled,
            onToggle = { onFpsLimiterEnabledChanged(!fpsLimiterEnabled) },
            accentColor = accentColor,
            focusRequester = focusRequester,
        )

        AnimatedVisibility(
            visible = fpsLimiterEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                QuickMenuAdjustmentRow(
                    title = stringResource(R.string.performance_hud_fps_limiter_target),
                    valueText = stringResource(
                        R.string.performance_hud_fps_limiter_value,
                        fpsLimiterTarget,
                    ),
                    progress = fpsLimiterProgress(fpsLimiterTarget, fpsLimiterMax),
                    onDecrease = {
                        onFpsLimiterChanged(previousFpsLimiterValue(fpsLimiterTarget, fpsLimiterMax))
                    },
                    onIncrease = {
                        onFpsLimiterChanged(nextFpsLimiterValue(fpsLimiterTarget, fpsLimiterMax))
                    },
                    accentColor = accentColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Performance HUD ──────────────────────────────────────────────
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud),
            subtitle = stringResource(R.string.performance_hud_description),
            enabled = isPerformanceHudEnabled,
            onToggle = onTogglePerformanceHud,
            accentColor = accentColor,
        )

        Spacer(modifier = Modifier.height(8.dp))

        QuickMenuSectionHeader(
            title = stringResource(R.string.performance_hud_presets),
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PerformanceHudPreset.values().forEach { preset ->
                QuickMenuChoiceChip(
                    text = stringResource(preset.labelResId),
                    selected = matchesPerformanceHudPreset(performanceHudConfig, preset),
                    accentColor = accentColor,
                    onClick = {
                        onPerformanceHudConfigChanged(applyPerformanceHudPreset(performanceHudConfig, preset))
                        if (!isPerformanceHudEnabled) {
                            onTogglePerformanceHud()
                        }
                    },
                    modifier = Modifier.width(56.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        QuickMenuSectionHeader(
            title = stringResource(R.string.performance_hud_appearance),
        )

        Text(
            text = stringResource(R.string.performance_hud_size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                PerformanceHudSize.SMALL to R.string.performance_hud_size_small,
                PerformanceHudSize.MEDIUM to R.string.performance_hud_size_medium,
                PerformanceHudSize.LARGE to R.string.performance_hud_size_large,
            ).forEach { (size, labelResId) ->
                QuickMenuChoiceChip(
                    text = stringResource(labelResId),
                    selected = performanceHudConfig.size == size,
                    accentColor = accentColor,
                    onClick = {
                        onPerformanceHudConfigChanged(performanceHudConfig.copy(size = size))
                    },
                    modifier = Modifier.width(56.dp),
                )
            }
        }

        QuickMenuAdjustmentRow(
            title = stringResource(R.string.performance_hud_background_opacity),
            valueText = stringResource(
                R.string.performance_hud_percentage_value,
                (performanceHudConfig.backgroundOpacity * 100f).roundToInt(),
            ),
            progress = normalizedProgress(performanceHudConfig.backgroundOpacity, 0f, 1f),
            onDecrease = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(
                        backgroundOpacity = (performanceHudConfig.backgroundOpacity - 0.05f).coerceIn(0f, 1f),
                    ),
                )
            },
            onIncrease = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(
                        backgroundOpacity = (performanceHudConfig.backgroundOpacity + 0.05f).coerceIn(0f, 1f),
                    ),
                )
            },
            accentColor = accentColor,
        )

        QuickMenuAdjustmentRow(
            title = stringResource(R.string.performance_hud_color_intensity),
            valueText = stringResource(
                R.string.performance_hud_percentage_value,
                (performanceHudConfig.colorIntensity * 100f).roundToInt(),
            ),
            progress = normalizedProgress(performanceHudConfig.colorIntensity, 0f, 1f),
            onDecrease = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(
                        colorIntensity = (performanceHudConfig.colorIntensity - 0.05f).coerceIn(0f, 1f),
                    ),
                )
            },
            onIncrease = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(
                        colorIntensity = (performanceHudConfig.colorIntensity + 0.05f).coerceIn(0f, 1f),
                    ),
                )
            },
            accentColor = accentColor,
        )

        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_text_outline),
            enabled = performanceHudConfig.showTextOutline,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showTextOutline = !performanceHudConfig.showTextOutline),
                )
            },
            accentColor = accentColor,
        )

        Spacer(modifier = Modifier.height(8.dp))

        QuickMenuSectionHeader(
            title = stringResource(R.string.performance_hud_metrics),
        )

        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_frame_rate),
            enabled = performanceHudConfig.showFrameRate,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showFrameRate = !performanceHudConfig.showFrameRate),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_frame_rate_graph),
            enabled = performanceHudConfig.showFrameRateGraph,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showFrameRateGraph = !performanceHudConfig.showFrameRateGraph),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_cpu_usage),
            enabled = performanceHudConfig.showCpuUsage,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showCpuUsage = !performanceHudConfig.showCpuUsage),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_cpu_usage_graph),
            enabled = performanceHudConfig.showCpuUsageGraph,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showCpuUsageGraph = !performanceHudConfig.showCpuUsageGraph),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_gpu_usage),
            enabled = performanceHudConfig.showGpuUsage,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showGpuUsage = !performanceHudConfig.showGpuUsage),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_gpu_usage_graph),
            enabled = performanceHudConfig.showGpuUsageGraph,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showGpuUsageGraph = !performanceHudConfig.showGpuUsageGraph),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_ram_usage),
            enabled = performanceHudConfig.showRamUsage,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showRamUsage = !performanceHudConfig.showRamUsage),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_battery_level),
            enabled = performanceHudConfig.showBatteryLevel,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showBatteryLevel = !performanceHudConfig.showBatteryLevel),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_power_draw),
            enabled = performanceHudConfig.showPowerDraw,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showPowerDraw = !performanceHudConfig.showPowerDraw),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_runtime_left),
            enabled = performanceHudConfig.showBatteryRuntime,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showBatteryRuntime = !performanceHudConfig.showBatteryRuntime),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_battery_temperature),
            enabled = performanceHudConfig.showBatteryTemperature,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showBatteryTemperature = !performanceHudConfig.showBatteryTemperature),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_clock_time),
            enabled = performanceHudConfig.showClockTime,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showClockTime = !performanceHudConfig.showClockTime),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_cpu_temperature),
            enabled = performanceHudConfig.showCpuTemperature,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showCpuTemperature = !performanceHudConfig.showCpuTemperature),
                )
            },
            accentColor = accentColor,
        )
        QuickMenuToggleRow(
            title = stringResource(R.string.performance_hud_gpu_temperature),
            enabled = performanceHudConfig.showGpuTemperature,
            onToggle = {
                onPerformanceHudConfigChanged(
                    performanceHudConfig.copy(showGpuTemperature = !performanceHudConfig.showGpuTemperature),
                )
            },
            accentColor = accentColor,
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun LsfgQuickMenuTab(
    multiplier: Int,
    flowScale: Float,
    performanceMode: Boolean,
    onMultiplierChanged: (Int) -> Unit,
    onFlowScaleChanged: (Float) -> Unit,
    onPerformanceModeChanged: (Boolean) -> Unit,
    backend: String,
    onBackendChanged: (String) -> Unit,
    presentMode: String,
    onPresentModeChanged: (String) -> Unit,
    scrollState: ScrollState,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val accentColor = PluviaTheme.colors.accentPurple
    val isEnabled = multiplier >= 2

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Multiplier (Off / 2x / 3x / 4x) ───────────────────────────────
        QuickMenuSectionHeader(
            title = stringResource(R.string.lsfg_multiplier),
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0, 2, 3, 4).forEach { value ->
                QuickMenuChoiceChip(
                    text = if (value == 0) "Off" else "${value}x",
                    selected = multiplier == value || (value == 0 && multiplier < 2),
                    accentColor = accentColor,
                    onClick = { onMultiplierChanged(value) },
                    modifier = Modifier.width(56.dp),
                    focusRequester = if (value == 0) focusRequester else null,
                )
            }
        }

        AnimatedVisibility(
            visible = isEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.height(4.dp))

                // ── Flow Scale ────────────────────────────────────────────
                QuickMenuAdjustmentRow(
                    title = stringResource(R.string.lsfg_flow_scale),
                    subtitle = stringResource(R.string.lsfg_flow_scale_desc),
                    valueText = String.format(java.util.Locale.US, "%.2f", flowScale),
                    progress = (flowScale - 0.25f) / 0.75f, // 0.25..1.0 → 0..1
                    onDecrease = {
                        val next = (flowScale - 0.05f).coerceIn(0.25f, 1.0f)
                        onFlowScaleChanged(String.format(java.util.Locale.US, "%.2f", next).toFloat())
                    },
                    onIncrease = {
                        val next = (flowScale + 0.05f).coerceIn(0.25f, 1.0f)
                        onFlowScaleChanged(String.format(java.util.Locale.US, "%.2f", next).toFloat())
                    },
                    accentColor = accentColor,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ── Performance Mode ──────────────────────────────────────
                QuickMenuToggleRow(
                    title = stringResource(R.string.lsfg_performance_mode),
                    subtitle = stringResource(R.string.lsfg_performance_mode_desc),
                    enabled = performanceMode,
                    onToggle = { onPerformanceModeChanged(!performanceMode) },
                    accentColor = accentColor,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ── Present Mode (Mailbox / FIFO) ─────────────────────────
                QuickMenuSectionHeader(
                    title = stringResource(R.string.lsfg_present_mode),
                    subtitle = stringResource(R.string.lsfg_present_mode_desc),
                )
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("mailbox" to "Mailbox", "fifo" to "FIFO").forEach { (value, label) ->
                        QuickMenuChoiceChip(
                            text = label,
                            selected = presentMode == value,
                            accentColor = accentColor,
                            onClick = { onPresentModeChanged(value) },
                            modifier = Modifier.width(96.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Backend (Legacy layer / Native renderer) ──────────────
                QuickMenuSectionHeader(
                    title = stringResource(R.string.lsfg_backend),
                    subtitle = stringResource(R.string.lsfg_backend_desc),
                )
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "native" to stringResource(R.string.lsfg_backend_native),
                        "legacy" to stringResource(R.string.lsfg_backend_legacy),
                    ).forEach { (value, label) ->
                        QuickMenuChoiceChip(
                            text = label,
                            selected = backend == value,
                            accentColor = accentColor,
                            onClick = { onBackendChanged(value) },
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Die Zeilen der Reiter Spiel und Steuerung: je Abschnitt eine Überschrift (in der Steuerung klappbar, Akkordeon), darunter
 * kompakte Zeilen mit Titel links und Wert rechts. Die Zeilen selbst (Art, Titel, Wert) kommen aus QuickMenuRows.kt, die Gliederung
 * aus QuickMenuLayout.kt. Die erste Zeile bekommt [firstItemFocusRequester] (der Fokus landet beim Öffnen des Reiters dort).
 * Gehört in eine Spalte mit Abstand zwischen den Zeilen.
 */
@Composable
private fun QuickMenuRows(
    sections: List<QuickMenuSection>,
    itemsById: Map<Int, QuickMenuItem>,
    wowQuick: WowQuickSettings,
    values: QuickMenuValues,
    isActive: (Int) -> Boolean,
    onItemClick: (QuickMenuItem) -> Unit,
    extraFor: (Int) -> QuickMenuRowExtra?,
    firstItemFocusRequester: FocusRequester?,
) {
    val accent = PluviaTheme.colors.accentPurple
    val open = QuickMenuAccordion.resolve(wowQuick.openGroupSaved, sections)
    var first = true
    sections.forEachIndexed { sectionIndex, section ->
        if (sectionIndex > 0) Spacer(modifier = Modifier.height(4.dp))
        val groupId = section.groupId
        val isOpen = !section.collapsible || groupId == open
        if (section.collapsible && groupId != null) {
            QuickMenuGroupHeader(
                title = section.title.asString(),
                summary = if (isOpen) null else quickMenuGroupSummary(groupId, values).asString(),
                open = isOpen,
                accentColor = accent,
                onClick = { wowQuick.toggleGroup(sections, groupId) },
                focusRequester = if (first) firstItemFocusRequester else null,
            )
            first = false
        } else {
            QuickMenuSectionHeader(title = section.title.asString())
        }
        if (isOpen) {
            section.ids.forEach { id ->
                val view = quickMenuRowView(id, values)?.resolve(androidx.compose.ui.platform.LocalContext.current)
                if (view != null) {
                    val item = itemsById[id] ?: QuickMenuItem(id, Icons.Default.Settings, R.string.input_controls)
                    QuickMenuOneRow(
                        id = id,
                        view = view,
                        item = item,
                        on = isActive(id),
                        wowQuick = wowQuick,
                        onItemClick = onItemClick,
                        extra = extraFor(id),
                        accentColor = accent,
                        focusRequester = if (first) firstItemFocusRequester else null,
                    )
                    first = false
                }
            }
            val note = section.note?.asString()
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Eine Zeile je nach Art (QuickMenuRowKind), danach ihre Anmerkung (zum Beispiel warum etwas nicht ging). */
@Composable
private fun QuickMenuOneRow(
    id: Int,
    view: QuickMenuRowText,
    item: QuickMenuItem,
    on: Boolean,
    wowQuick: WowQuickSettings,
    onItemClick: (QuickMenuItem) -> Unit,
    extra: QuickMenuRowExtra?,
    accentColor: Color,
    focusRequester: FocusRequester?,
) {
    when (view.kind) {
        QuickMenuRowKind.ICON -> QuickMenuItemRow(
            item = item,
            isActive = on,
            onClick = { onItemClick(item) },
            focusRequester = focusRequester,
        )
        QuickMenuRowKind.STEP -> QuickMenuStepLine(
            view = view,
            accentColor = accentColor,
            onStep = { delta -> wowQuick.adjust(id, delta) },
            focusRequester = focusRequester,
        )
        QuickMenuRowKind.CHOICE -> QuickMenuChoiceLine(
            view = view,
            accentColor = accentColor,
            onChoose = { index -> wowQuick.choose(id, index) },
            focusRequester = focusRequester,
        )
        QuickMenuRowKind.NOTE -> {
            if (view.value.isNotBlank()) {
                Text(
                    text = view.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
        QuickMenuRowKind.TOGGLE, QuickMenuRowKind.VALUE -> QuickMenuLine(
            view = view,
            on = on,
            enabled = item.enabled && view.enabled,
            accentColor = accentColor,
            onClick = { onItemClick(item) },
            extra = extra,
            focusRequester = focusRequester,
        )
    }
    val note = view.note
    if (note != null && view.kind != QuickMenuRowKind.NOTE) {
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

/** Dialoge des Seitenmenüs, die über dem Spiel stehen: Auswahlliste des Klick-Rads und die Rückfrage zum Layout. */
@Composable
private fun QuickMenuWowDialogs(wowQuick: WowQuickSettings) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = wowQuick.wheelPicker
    if (picker != null) {
        WheelActionDialog(
            direction = picker,
            currentId = ClickWheel.actionFor(picker, wowQuick.touchOptions.wheelAssignment[picker]).id,
            onPick = { actionId -> wowQuick.pickWheelAction(picker, actionId) },
            onDismiss = { wowQuick.wheelPicker = null },
        )
    }
    if (wowQuick.showResetConfirm) {
        LayoutResetDialog(
            text = stringResource(WowTouchProfiles.RESET_CONFIRM_TEXT, WowTouchProfiles.resetTargetDisplayName(context, InputMode.current(context))),
            onConfirm = { wowQuick.confirmResetLayout() },
            onDismiss = { wowQuick.showResetConfirm = false },
        )
    }
}

/** Dialoge der Zeilen "Layout" (Auswahlliste) und "Als neues Layout speichern". */
@Composable
private fun QuickMenuLayoutDialogs(wowQuick: WowQuickSettings) {
    if (wowQuick.showLayoutPicker) {
        LayoutPickerDialog(
            entries = wowQuick.layoutEntries(),
            activeId = wowQuick.activeLayoutId(),
            onPick = { wowQuick.chooseLayout(it) },
            onSaveAs = {
                wowQuick.showLayoutPicker = false
                wowQuick.showLayoutNameDialog = true
            },
            onDismiss = { wowQuick.showLayoutPicker = false },
        )
    }
    if (wowQuick.showLayoutNameDialog) {
        LayoutNameDialog(
            existingProfileNames = wowQuick.layoutEntries().map { it.profileName },
            onSave = { wowQuick.saveLayoutAs(it) },
            onDismiss = { wowQuick.showLayoutNameDialog = false },
        )
    }
}

/** Kopf einer klappbaren Gruppe: Name, im zugeklappten Zustand die Kurzinfo der Werte, rechts der Pfeil. Auch per Controller bedienbar. */
@Composable
private fun QuickMenuGroupHeader(
    title: String,
    summary: String?,
    open: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val fallbackFocusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(shape)
            .background(
                if (isFocused) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            )
            .focusRing(interactionSource, shape, width = 2.dp)
            .focusRequester(focusRequester ?: fallbackFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || !isFocused) {
                    return@onPreviewKeyEvent false
                }
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .selectable(
                selected = open,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Kompakte Zeile: Titel links (eine Zeile), rechts der Schalter (TOGGLE) oder der Wert (VALUE). Mit [extra] folgt rechts ein
 * Zahnrad für die Einstellungen des Modus; vom Controller erreicht man es mit der Taste nach rechts.
 */
@Composable
private fun QuickMenuLine(
    view: QuickMenuRowText,
    on: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    extra: QuickMenuRowExtra?,
    focusRequester: FocusRequester?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val fallbackFocusRequester = remember { FocusRequester() }
    val rowFocusRequester = focusRequester ?: fallbackFocusRequester
    val extraFocusRequester = remember { FocusRequester() }
    val extraInteractionSource = remember { MutableInteractionSource() }
    val isExtraFocused by extraInteractionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(shape)
                .background(
                    if (isFocused && enabled) accentColor.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
                )
                .then(if (enabled) Modifier.focusRing(interactionSource, shape, width = 2.dp) else Modifier)
                .focusRequester(rowFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || !isFocused) {
                        return@onPreviewKeyEvent false
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (extra != null) {
                                extraFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        -> {
                            if (enabled) onClick()
                            true
                        }
                        else -> false
                    }
                }
                .selectable(
                    selected = isFocused,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .focusable(enabled = enabled, interactionSource = interactionSource)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = view.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (view.kind == QuickMenuRowKind.TOGGLE) {
                QuickMenuSwitch(enabled = on, accentColor = accentColor)
            } else {
                Text(
                    text = view.value,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (extra != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .focusRing(extraInteractionSource, CircleShape, width = 2.dp)
                    .clip(CircleShape)
                    .background(
                        if (isExtraFocused) accentColor.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .focusRequester(extraFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || !isExtraFocused) {
                            return@onPreviewKeyEvent false
                        }
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                rowFocusRequester.requestFocus()
                                true
                            }
                            KeyEvent.KEYCODE_BUTTON_A,
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            -> {
                                extra.onClick()
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable(
                        interactionSource = extraInteractionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = extra.onClick,
                    )
                    .focusable(interactionSource = extraInteractionSource),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = extra.icon,
                    contentDescription = stringResource(extra.descResId),
                    tint = if (isExtraFocused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Zeile mit "- Wert +": Titel links, rechts die beiden Knöpfe um den Wert. Per Controller: A sperrt die Zeile, dann ändern links und
 * rechts den Wert, B oder A gibt sie wieder frei (damit links und rechts sonst zwischen den Reitern springen).
 */
@Composable
private fun QuickMenuStepLine(
    view: QuickMenuRowText,
    accentColor: Color,
    onStep: (Int) -> Unit,
    focusRequester: FocusRequester?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var locked by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val enabled = view.enabled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(
                if (isFocused && enabled) accentColor.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
            )
            .then(
                if (isFocused && !locked && enabled) Modifier.border(2.dp, accentColor.copy(alpha = 0.7f), shape) else Modifier,
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (!it.isFocused) locked = false }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || !isFocused || !enabled) {
                    return@onPreviewKeyEvent false
                }
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    -> {
                        locked = !locked
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_B -> {
                        if (locked) {
                            locked = false
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (locked) {
                            onStep(-1)
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (locked) {
                            onStep(1)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .selectable(
                selected = isFocused,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = view.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            QuickMenuStepButton(text = "−", locked = locked, accentColor = accentColor, enabled = enabled) { onStep(-1) }
            Text(
                text = view.value,
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused || locked) accentColor else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 48.dp),
            )
            QuickMenuStepButton(text = "+", locked = locked, accentColor = accentColor, enabled = enabled) { onStep(1) }
        }
    }
}

/** Minus- oder Plusknopf der Wertzeile; nur zum Tippen (der Controller bedient die ganze Zeile, daher nicht fokussierbar). */
@Composable
private fun QuickMenuStepButton(
    text: String,
    locked: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .background(
                if (locked) accentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .border(
                width = if (locked) 2.dp else 1.dp,
                color = if (locked) accentColor.copy(alpha = 0.9f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = shape,
            )
            .focusProperties { canFocus = false }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (locked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Zeile mit Auswahlknöpfen: Titel, darunter die Knöpfe nebeneinander; darunter der volle Name des Gewählten, wenn der Knopf ihn kürzt. */
@Composable
private fun QuickMenuChoiceLine(
    view: QuickMenuRowText,
    accentColor: Color,
    onChoose: (Int) -> Unit,
    focusRequester: FocusRequester?,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = view.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            view.chips.forEachIndexed { index, chip ->
                QuickMenuChoiceChip(
                    text = chip,
                    selected = index == view.selected,
                    accentColor = accentColor,
                    onClick = { onChoose(index) },
                    modifier = Modifier.weight(1f),
                    focusRequester = if (index == 0) focusRequester else null,
                    compact = true,
                )
            }
        }
        val detail = view.detail
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickMenuSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickMenuCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(
                if (isFocused) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .focusRing(interactionSource, shape, width = 2.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.quick_menu_back),
            tint = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun QuickMenuTabButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    accentColor: Color,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(shape)
            .background(
                when {
                    selected -> accentColor.copy(alpha = 0.18f)
                    isFocused -> accentColor.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .focusRing(interactionSource, shape, width = 2.dp)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                if (it.isFocused && !selected) {
                    onSelected()
                }
            }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelected,
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                selected || isFocused -> accentColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun QuickMenuRailActionButton(
    item: QuickMenuItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val accentColor = if (item.accentColor != Color.Unspecified) {
        item.accentColor
    } else {
        MaterialTheme.colorScheme.error
    }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .size(56.dp)
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = accentColor.copy(alpha = 0.7f),
                        shape = shape,
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        shape = shape,
                    )
                }
            )
            .clip(shape)
            .background(
                if (isFocused) {
                    accentColor.copy(alpha = 0.18f)
                } else {
                    accentColor.copy(alpha = 0.08f)
                },
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = stringResource(item.labelResId),
            tint = if (isFocused) accentColor else accentColor.copy(alpha = 0.9f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun QuickMenuChoiceChip(
    text: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .height(if (compact) 36.dp else 44.dp)
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = accentColor.copy(alpha = 0.7f),
                        shape = shape,
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = if (selected) accentColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        shape = shape,
                    )
                }
            )
            .clip(shape)
            .background(
                when {
                    selected -> accentColor.copy(alpha = 0.18f)
                    isFocused -> accentColor.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                },
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = if (compact) 4.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected || isFocused) accentColor else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
    }
}

@Composable
internal fun QuickMenuAdjustmentRow(
    title: String,
    valueText: String,
    progress: Float,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    subtitle: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    var isAdjustmentLocked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                if (isFocused) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f),
                            accentColor.copy(alpha = 0.08f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                        ),
                    )
                },
            )
            .then(
                if (isFocused && !isAdjustmentLocked) {
                    Modifier.border(
                        width = 2.dp,
                        color = accentColor.copy(alpha = 0.7f),
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                if (!it.isFocused) {
                    if (isAdjustmentLocked) {
                        Timber.i("QuickMenu: row '%s' lost focus while locked — force-unlocking", title)
                    }
                    isAdjustmentLocked = false
                }
            }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && isFocused) {
                    when {
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A -> {
                            isAdjustmentLocked = !isAdjustmentLocked
                            Timber.i("QuickMenu: row '%s' lock now %b", title, isAdjustmentLocked)
                            true
                        }

                        isAdjustmentLocked &&
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B -> {
                            isAdjustmentLocked = false
                            true
                        }

                        isAdjustmentLocked && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onDecrease()
                            true
                        }

                        isAdjustmentLocked && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onIncrease()
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
            .selectable(
                selected = isFocused,
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isAdjustmentLocked) {
                    Text(
                        text = "●",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                    )
                }
            }
        }

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickMenuAdjustmentButton(
                text = "-",
                rowIsFocused = isFocused,
                isAdjustmentLocked = isAdjustmentLocked,
                accentColor = accentColor,
                onClick = onDecrease,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDecrease,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onIncrease,
                            ),
                    )
                }
            }

            QuickMenuAdjustmentButton(
                text = "+",
                rowIsFocused = isFocused,
                isAdjustmentLocked = isAdjustmentLocked,
                accentColor = accentColor,
                onClick = onIncrease,
            )
        }
    }
}

@Composable
private fun QuickMenuAdjustmentButton(
    text: String,
    rowIsFocused: Boolean,
    isAdjustmentLocked: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isAdjustmentLocked) {
                    accentColor.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (rowIsFocused) 0.32f else 0.45f)
                },
            )
            .border(
                width = if (isAdjustmentLocked) 2.dp else 1.dp,
                color = if (isAdjustmentLocked) {
                    accentColor.copy(alpha = 0.9f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isAdjustmentLocked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun QuickMenuToggleRow(
    title: String,
    enabled: Boolean,
    selectable: Boolean = true,
    onToggle: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isFocused) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f),
                            accentColor.copy(alpha = 0.08f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                        ),
                    )
                },
            )
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = accentColor.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(14.dp),
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .selectable(
                selected = isFocused,
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (selectable) onToggle() },
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (!selectable) Modifier.alpha(0.5f) else Modifier)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier = if (!selectable) Modifier.alpha(0.5f) else Modifier
        ) {
            QuickMenuSwitch(
                enabled = enabled,
                accentColor = accentColor,
            )
        }
    }
}

@Composable
private fun QuickMenuSwitch(
    enabled: Boolean,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (enabled) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            )
            .border(
                width = 1.dp,
                color = if (enabled) accentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                .background(Color.White, CircleShape),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickMenuDetailRow(
    title: String,
    subtitle: String,
    accentColor: Color,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                if (isFocused) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.14f),
                            accentColor.copy(alpha = 0.06f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                        ),
                    )
                },
            )
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = accentColor.copy(alpha = 0.8f),
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && isFocused) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            onActivate()
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
            .selectable(
                selected = isFocused,
                interactionSource = interactionSource,
                indication = null,
                onClick = onActivate,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) accentColor.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickMenuItemRow(
    item: QuickMenuItem,
    isActive: Boolean = false,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    secondaryIcon: ImageVector? = null,
    secondaryContentDescriptionResId: Int? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val fallbackFocusRequester = remember { FocusRequester() }
    val rowFocusRequester = focusRequester ?: fallbackFocusRequester
    val secondaryFocusRequester = remember { FocusRequester() }
    val secondaryInteractionSource = remember { MutableInteractionSource() }
    val isSecondaryFocused by secondaryInteractionSource.collectIsFocusedAsState()
    val isEnabled = item.enabled
    val hasSecondaryAction = secondaryIcon != null && onSecondaryClick != null


    val accentColor = if (item.accentColor != Color.Unspecified) {
        item.accentColor
    } else {
        MaterialTheme.colorScheme.primary
    }

    val disabledAlpha = 0.4f
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .then(
                    if (isFocused && isEnabled) {
                        Modifier.background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.15f),
                                    accentColor.copy(alpha = 0.05f),
                                ),
                            ),
                        )
                    } else Modifier
                )
                .then(
                    if (isEnabled) Modifier.focusRing(interactionSource, shape, width = 2.dp)
                    else Modifier
                )
                .focusRequester(rowFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || !isFocused) {
                        return@onPreviewKeyEvent false
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (hasSecondaryAction) {
                                secondaryFocusRequester.requestFocus()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                }
                .selectable(
                    selected = isFocused,
                    enabled = isEnabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .focusable(enabled = isEnabled, interactionSource = interactionSource)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .then(
                        if (isActive) Modifier.border(BorderStroke(2.dp, accentColor), CircleShape)
                        else Modifier
                    )
                    .clip(CircleShape)
                    .background(
                        when {
                            !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            isFocused || isActive -> accentColor.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = when {
                        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                        isFocused || isActive -> accentColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
            }

            Text(
                text = item.label ?: stringResource(item.labelResId),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                    isFocused -> accentColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (secondaryIcon != null && onSecondaryClick != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .focusRing(secondaryInteractionSource, CircleShape, width = 2.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSecondaryFocused) accentColor.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .focusRequester(secondaryFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || !isSecondaryFocused) {
                            return@onPreviewKeyEvent false
                        }
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                rowFocusRequester.requestFocus()
                                true
                            }
                            KeyEvent.KEYCODE_BUTTON_A,
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            -> {
                                onSecondaryClick()
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable(
                        interactionSource = secondaryInteractionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onSecondaryClick,
                    )
                    .focusable(interactionSource = secondaryInteractionSource),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = secondaryIcon,
                    contentDescription = stringResource(
                        secondaryContentDescriptionResId ?: R.string.gesture_settings_title,
                    ),
                    tint = if (isSecondaryFocused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun Preview_QuickMenu() {
    PluviaTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            QuickMenu(
                isVisible = true,
                onDismiss = {},
                onItemSelected = { false },
                hasPhysicalController = false,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun Preview_QuickMenu_WithController() {
    PluviaTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            QuickMenu(
                isVisible = true,
                onDismiss = {},
                onItemSelected = { false },
                hasPhysicalController = true,
            )
        }
    }
}
