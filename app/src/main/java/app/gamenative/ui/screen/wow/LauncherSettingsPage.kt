package app.gamenative.ui.screen.wow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.backup.BackupDetailBody
import app.gamenative.report.ErrorReport
import app.gamenative.ui.screen.wow.klick.KlickRadSettingsBlock
import app.gamenative.ui.screen.wow.launcher.AppLanguage
import app.gamenative.ui.screen.wow.launcher.AppLanguageSetting
import app.gamenative.ui.screen.wow.launcher.CONTROL_HINT_LINES
import app.gamenative.ui.screen.wow.launcher.CardActionRow
import app.gamenative.ui.screen.wow.launcher.CardChoiceRow
import app.gamenative.ui.screen.wow.launcher.CardOpenRow
import app.gamenative.ui.screen.wow.launcher.CardSwitchRow
import app.gamenative.ui.screen.wow.launcher.CardText
import app.gamenative.ui.screen.wow.launcher.CardValueRow
import app.gamenative.ui.screen.wow.launcher.DetailHint
import app.gamenative.ui.screen.wow.launcher.LauncherArtSettings
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.ui.screen.wow.launcher.LauncherOverviewPage
import app.gamenative.ui.screen.wow.launcher.LauncherSectionTitle
import app.gamenative.ui.screen.wow.launcher.LauncherTexts
import app.gamenative.ui.screen.wow.launcher.OverviewCard
import app.gamenative.ui.screen.wow.launcher.PageDetail
import app.gamenative.ui.screen.wow.launcher.PageTexts
import app.gamenative.ui.screen.wow.launcher.UiText
import app.gamenative.ui.screen.wow.launcher.ValueText
import app.gamenative.ui.screen.wow.launcher.asString
import app.gamenative.ui.screen.wow.launcher.card
import app.gamenative.ui.screen.wow.launcher.controlPlan
import app.gamenative.ui.screen.wow.launcher.settingsPlan
import app.gamenative.ui.screen.wow.voice.VoiceLanguageRow
import app.gamenative.ui.screen.wow.voice.VoiceSettingsBlock
import app.gamenative.update.UpdateManager
import app.gamenative.update.UpdateSettings
import app.gamenative.update.UpdateSettingsBlock
import app.gamenative.update.UpdateState
import app.gamenative.update.updateSourceValue
import app.gamenative.wowicons.WowCooldownSettingsRows
import app.gamenative.wowicons.WowIconsSettingsBlock

/** Aktuelle Werte, die die Einstellungsseite anzeigt (gehalten vom Launcher-Bildschirm). */
internal data class LauncherSettingsValues(
    /** false, solange das Spiel startet: alle Zeilen sind dann gesperrt. */
    val enabled: Boolean,
    val region: GameRegion,
    val resolution: RenderResolution,
    val fpsLimit: FpsLimit,
    val graphicsPreset: GraphicsPreset,
    val inputMode: InputMode,
    /** Der Modus als Text; bei einem gewählten eigenen Layout dessen Name ([TouchLayouts.customLabel]). */
    val inputModeText: String,
    val walkGain: Float,
    val cameraGain: Float,
    val gamePath: String,
    val autoStartOn: Boolean,
    val autoStartSeconds: Int,
    val startCheckOn: Boolean,
    val clientLocal: String?,
    val clientRemote: String?,
    val clientOutdated: Boolean,
)

/** Was die Seite auslöst; Speichern und Zustand erledigt der Launcher-Bildschirm. */
internal data class LauncherSettingsActions(
    val onRegion: (GameRegion) -> Unit,
    val onResolution: (RenderResolution) -> Unit,
    val onFps: (FpsLimit) -> Unit,
    val onGraphics: (GraphicsPreset) -> Unit,
    val onInputMode: (InputMode) -> Unit,
    val onWalkGain: (Float) -> Unit,
    val onCameraGain: (Float) -> Unit,
    val onPickFolder: () -> Unit,
    val onLayoutReset: () -> Unit,
    val onAutoStart: (Boolean) -> Unit,
    val onAutoStartSeconds: (Int) -> Unit,
    val onStartCheck: (Boolean) -> Unit,
    val onOpenAddons: () -> Unit,
)

/**
 * Seite "Einstellungen" als Übersicht aus fünf Karten nach Bedeutung (Spiel, Start und Updates, Steuerung im Spiel, App, Hilfe), die
 * auf dem aufgeklappten Fold ohne Scrollen auf den Schirm passen. Jede Zeile tut direkt etwas (Auswahl, Schalter, Knopf); nur die
 * Zeilen "Öffnen" führen in einen eigenen Bereich (Sprachsteuerung, Symbole und Abklingzeiten), und der Pfeil im Kopf einer Karte
 * heißt: dahinter stehen Beschreibungen und Abzeichen ("Nur hier", "Auch im Spiel"). Mit [onlyGroup] = Gruppe "Steuerung" (Kachel
 * "Steuerung" der Startseite) zeigt sie stattdessen die Steuerungs-Übersicht unter dem Titel [title]; nur dort steht die Karte
 * "Spiel-Einstellungen" ([hiddenGameSettings], zu, bis man die Rückfrage bestätigt). Addons verwaltet die Kachel "Addons".
 */
@Composable
internal fun LauncherSettingsPage(
    values: LauncherSettingsValues,
    actions: LauncherSettingsActions,
    onBack: () -> Unit,
    title: UiText = UiText.res(R.string.launcher_tile_settings),
    onlyGroup: String? = null,
) {
    if (onlyGroup == LauncherTexts.TOUCH_SETTINGS_GROUP) {
        ControlOverview(values, actions, onBack, title)
    } else {
        SettingsOverview(values, actions, onBack, title)
    }
}

private fun groupOf(key: String): SettingsGroup = settingsGroups().first { it.key == key }

@Composable
private fun SettingsOverview(v: LauncherSettingsValues, a: LauncherSettingsActions, onBack: () -> Unit, title: UiText) {
    var detailId by remember { mutableStateOf<String?>(null) }
    val plan = remember { settingsPlan() }
    val cards = listOf(
        plan.card("game", stringResource(R.string.launcher_group_game), onOpen = { detailId = "game" }) { GameCardRows(v, a) },
        plan.card("start", stringResource(R.string.launcher_group_start), onOpen = { detailId = "start" }) { StartCardRows(v, a) { detailId = "start" } },
        plan.card("control", stringResource(R.string.launcher_settings_control_in_game)) { ControlCardRows(v, a) { detailId = it } },
        plan.card("app", stringResource(R.string.launcher_group_app), onOpen = { detailId = "app" }) { AppCardRows(v) { detailId = "backup" } },
        plan.card("help", stringResource(R.string.launcher_group_help)) { HelpCardRows() },
    )
    val scopeHint = SETTINGS_SCOPE_HINT.asString()
    val close: () -> Unit = { detailId = null }
    // Die Karten mit Pfeil im Kopf und die Zeilen "Öffnen" zeigen je eine Gruppe ([settingsGroups]) als Detailansicht.
    val detail: PageDetail? = detailId?.let { key ->
        if (key == "backup") {
            // Backup ist keine Gruppe des Seitenmenüs, sondern eine eigene Detailansicht in der Karte "App".
            PageDetail(stringResource(R.string.backup_title), close) { BackupDetailBody() }
        } else {
            val group = groupOf(key)
            PageDetail(group.title.asString(), close) {
                DetailHint(scopeHint)
                SettingsGroupView(group, v, a)
            }
        }
    }
    LauncherOverviewPage(title = title.asString(), onBack = onBack, cards = cards, detail = detail)
}

@Composable
private fun GameCardRows(v: LauncherSettingsValues, a: LauncherSettingsActions) {
    CardChoiceRow(
        title = stringResource(R.string.launcher_settings_region),
        value = v.region.label,
        options = GameRegion.entries,
        selected = v.region,
        optionLabel = { it.label },
        enabled = v.enabled,
        onSelect = a.onRegion,
    )
    CardChoiceRow(
        title = stringResource(R.string.launcher_settings_resolution),
        value = v.resolution.label,
        options = RenderResolution.entries,
        selected = v.resolution,
        optionLabel = { it.label },
        enabled = v.enabled,
        onSelect = a.onResolution,
    )
    CardChoiceRow(
        title = stringResource(R.string.launcher_settings_fps),
        value = "${v.fpsLimit.label} FPS",
        options = FpsLimit.entries,
        selected = v.fpsLimit,
        optionLabel = { "${it.label} FPS" },
        enabled = v.enabled,
        onSelect = a.onFps,
    )
    CardChoiceRow(
        title = stringResource(R.string.launcher_settings_graphics),
        value = v.graphicsPreset.label,
        options = GraphicsPreset.entries,
        selected = v.graphicsPreset,
        optionLabel = { it.label },
        enabled = v.enabled,
        onSelect = a.onGraphics,
    )
    CardActionRow(stringResource(R.string.launcher_files_game_folder), stringResource(R.string.launcher_action_change), enabled = v.enabled) { a.onPickFolder() }
}

@Composable
private fun StartCardRows(v: LauncherSettingsValues, a: LauncherSettingsActions, onSource: () -> Unit) {
    val context = LocalContext.current
    val autoInstall by remember { UpdateManager.autoInstallState(context) }.collectAsState()
    val state by UpdateManager.state.collectAsState()
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing
    val nextCountdown = { a.onAutoStartSeconds(AutoStartLogic.nextSeconds(v.autoStartSeconds)) }
    CardSwitchRow(stringResource(R.string.launcher_settings_autostart), v.autoStartOn, v.enabled) { a.onAutoStart(it) }
    CardValueRow(
        title = stringResource(R.string.launcher_settings_countdown),
        value = if (v.autoStartOn) "${v.autoStartSeconds} s" else stringResource(R.string.launcher_settings_off),
        enabled = v.enabled && v.autoStartOn,
        onClick = if (v.autoStartOn) nextCountdown else null,
    )
    CardSwitchRow(stringResource(R.string.launcher_settings_startcheck), v.startCheckOn, v.enabled) { a.onStartCheck(it) }
    CardSwitchRow(stringResource(R.string.launcher_update_auto_title), autoInstall, v.enabled) { UpdateManager.setAutoInstall(context, it) }
    CardActionRow(
        title = stringResource(R.string.launcher_home_check_updates),
        actionText = stringResource(if (state is UpdateState.Checking) R.string.launcher_home_checking else R.string.launcher_action_check),
        enabled = v.enabled && !busy,
        primary = true,
    ) { UpdateManager.checkManually(context) }
    // Die Update-Quelle (Offiziell, eigene Quelle oder Tester) steht in der Übersicht; Tippen öffnet den Block in der Detailansicht.
    CardValueRow(
        title = stringResource(R.string.launcher_source_title),
        value = updateSourceValue(UpdateSettings.sourceForm(context)),
        enabled = v.enabled,
        onClick = onSource,
    )
    // Version und Client-Build nur leise als Fußzeile; ausführlich stehen sie in der Detailansicht.
    val client = PageTexts.clientBuildValue(v.clientLocal, v.clientRemote, v.clientOutdated).asString()
    CardText(stringResource(R.string.launcher_settings_footer, BuildConfig.VERSION_NAME, client), 2)
}

/** Steuerung im Spiel: Modus direkt, Sprachsteuerung sowie Symbole und Abklingzeiten als Zeilen "Öffnen" ([onDetail] mit dem Gruppenschlüssel). */
@Composable
private fun ControlCardRows(v: LauncherSettingsValues, a: LauncherSettingsActions, onDetail: (String) -> Unit) {
    CardChoiceRow(
        title = stringResource(R.string.launcher_settings_control_mode),
        value = v.inputModeText,
        options = InputMode.entries,
        selected = v.inputMode,
        optionLabel = { it.label },
        enabled = v.enabled,
        onSelect = a.onInputMode,
    )
    val open = stringResource(R.string.launcher_open)
    CardOpenRow(stringResource(R.string.launcher_group_voice), open) { onDetail("voice") }
    CardOpenRow(stringResource(R.string.launcher_group_icons), open) { onDetail("icons") }
    CardText(PageTexts.CONTROL_SHORT_HINT.asString(), 2)
}

/** App: Sprache der App und die zwei Bildschalter des Launchers. */
@Composable
private fun AppCardRows(v: LauncherSettingsValues, onBackup: () -> Unit) {
    val context = LocalContext.current
    val language = AppLanguageSetting.currentTag()
    val screenshots by LauncherArtSettings.screenshotsFlow(context).collectAsState()
    val wowArt by LauncherArtSettings.wowArtFlow(context).collectAsState()
    CardChoiceRow(
        title = stringResource(R.string.launcher_language_row),
        value = AppLanguage.nativeName(language),
        options = AppLanguage.CHOICES,
        selected = language,
        optionLabel = { AppLanguage.nativeName(it) },
        onSelect = { AppLanguageSetting.set(context, it) },
    )
    CardSwitchRow(stringResource(R.string.launcher_settings_own_screenshots_short), screenshots, v.enabled) { LauncherArtSettings.setScreenshots(context, it) }
    CardSwitchRow(stringResource(R.string.launcher_settings_wow_art_short), wowArt, v.enabled) { LauncherArtSettings.setWowArt(context, it) }
    CardOpenRow(stringResource(R.string.backup_row), stringResource(R.string.launcher_open)) { onBackup() }
}

/** Hilfe: der Fehlerbericht wird direkt erstellt; die Statusmeldung ersetzt den Text darunter ([ErrorReport.start] meldet sie zurück). */
@Composable
private fun HelpCardRows() {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    CardActionRow(
        title = stringResource(R.string.game_report_row_title),
        actionText = stringResource(R.string.game_report_row_action),
        primary = true,
    ) { ErrorReport.start(context) { note = it } }
    CardText(note ?: stringResource(R.string.launcher_settings_report_hint), 2)
}

/**
 * Kachel "Steuerung": der Modus (Detail: die Gruppe Steuerung samt Hinweiskarte), je eine Hinweiskarte zu den Gruppen im Seitenmenü
 * und die Karte "Spiel-Einstellungen", die erst nach Rückfrage ([GameSettingsFold]) die Einstellungen zeigt, die es auch im Spiel gibt.
 */
@Composable
private fun ControlOverview(v: LauncherSettingsValues, a: LauncherSettingsActions, onBack: () -> Unit, title: UiText) {
    var detailId by remember { mutableStateOf<String?>(null) }
    var fold by remember { mutableStateOf(GameSettingsFold()) }
    val groups = LauncherTexts.CONTROL_HINT_GROUPS
    val plan = remember(groups.size) { controlPlan(groups.size) }
    val cards = buildList<OverviewCard> {
        add(
            plan.card("mode", stringResource(R.string.launcher_settings_control_mode), onOpen = { detailId = "mode" }) {
                CardChoiceRow(
                    title = stringResource(R.string.launcher_settings_mode),
                    value = v.inputModeText,
                    options = InputMode.entries,
                    selected = v.inputMode,
                    optionLabel = { it.label },
                    enabled = v.enabled,
                    onSelect = a.onInputMode,
                )
                CardText(PageTexts.CONTROL_MODE_HINT.asString(), 2)
            },
        )
        groups.forEachIndexed { index, name ->
            add(
                plan.card("hint$index", name.asString(), onOpen = { detailId = "mode" }) {
                    CardText(PageTexts.controlHintLine(name).asString(), CONTROL_HINT_LINES, LauncherColors.Body)
                },
            )
        }
        add(
            plan.card("gamefold", PageTexts.GAME_FOLD_CARD_TITLE.asString(), onOpen = { fold = fold.onRowTap() }) {
                CardText(LauncherTexts.GAME_FOLD_ROW_TITLE.asString(), 2, LauncherColors.Body)
                CardText(LauncherTexts.GAME_FOLD_ROW_HINT.asString(), 2)
            },
        )
    }
    val detail: PageDetail? = when {
        fold.open -> PageDetail(LauncherTexts.GAME_FOLD_GROUP_TITLE.asString(), { fold = GameSettingsFold() }) {
            SettingGroupCard(LauncherTexts.GAME_FOLD_GROUP_TITLE.asString()) {
                for (setting in hiddenGameSettings()) SettingItem(setting, v, a)
            }
        }
        detailId == "mode" -> PageDetail(title.asString(), { detailId = null }) {
            SettingsGroupView(groupOf(LauncherTexts.TOUCH_SETTINGS_GROUP), v, a)
        }
        else -> null
    }
    LauncherOverviewPage(title = title.asString(), onBack = onBack, cards = cards, detail = detail)
    if (fold.asking) GameFoldAskDialog(onCancel = { fold = fold.onCancel() }, onConfirm = { fold = fold.onConfirm() })
}

@Composable
private fun SettingsGroupView(group: SettingsGroup, values: LauncherSettingsValues, actions: LauncherSettingsActions) {
    SettingGroupCard(group.title.asString()) {
        for (setting in group.settings) SettingItem(setting, values, actions)
    }
}

/**
 * Eine Einstellung als Zeile (oder Zeilenblock). Das `when` deckt alle [LauncherSetting] ab; die Gruppen ([settingsGroups]) und der
 * zugeklappte Spiel-Bereich ([hiddenGameSettings]) bestimmen nur die Stelle.
 */
@Composable
private fun SettingItem(setting: LauncherSetting, v: LauncherSettingsValues, a: LauncherSettingsActions) {
    val scope = settingScope(setting)
    // Blöcke aus mehreren Zeilen (Klick-Rad, Sprache, Symbole ...) können das Abzeichen nicht selbst tragen: es steht als Streifen davor.
    if (setting.isBlock) SettingScopeStrip(scope)
    when (setting) {
        LauncherSetting.Region -> SettingChoiceRow(
            title = stringResource(R.string.launcher_settings_region),
            value = v.region.label,
            options = GameRegion.entries,
            selected = v.region,
            optionLabel = { it.label },
            description = stringResource(R.string.launcher_settings_region_desc),
            enabled = v.enabled,
            scope = scope,
            onSelect = a.onRegion,
        )
        LauncherSetting.Resolution -> SettingChoiceRow(
            title = stringResource(R.string.launcher_settings_resolution),
            value = v.resolution.label,
            options = RenderResolution.entries,
            selected = v.resolution,
            optionLabel = { it.label },
            description = stringResource(R.string.launcher_settings_resolution_desc),
            enabled = v.enabled,
            scope = scope,
            onSelect = a.onResolution,
        )
        LauncherSetting.Fps -> SettingChoiceRow(
            title = stringResource(R.string.launcher_settings_fps),
            value = "${v.fpsLimit.label} FPS",
            options = FpsLimit.entries,
            selected = v.fpsLimit,
            optionLabel = { "${it.label} FPS" },
            description = stringResource(R.string.launcher_settings_fps_desc),
            enabled = v.enabled,
            scope = scope,
            onSelect = a.onFps,
        )
        LauncherSetting.Graphics -> SettingChoiceRow(
            title = stringResource(R.string.launcher_settings_graphics),
            value = v.graphicsPreset.label,
            options = GraphicsPreset.entries,
            selected = v.graphicsPreset,
            optionLabel = { it.label },
            description = stringResource(R.string.launcher_settings_graphics_desc),
            enabled = v.enabled,
            scope = scope,
            onSelect = a.onGraphics,
        )
        LauncherSetting.GameFolder -> SettingActionRow(
            title = stringResource(R.string.launcher_files_game_folder),
            description = v.gamePath,
            actionText = stringResource(R.string.launcher_action_change),
            enabled = v.enabled,
            scope = scope,
            onClick = a.onPickFolder,
        )
        LauncherSetting.InputMode -> SettingChoiceRow(
            title = stringResource(R.string.launcher_settings_control_mode),
            value = v.inputModeText,
            options = InputMode.entries,
            selected = v.inputMode,
            optionLabel = { it.label },
            description = PageTexts.CONTROL_MODE_HINT.asString(),
            enabled = v.enabled,
            scope = scope,
            onSelect = a.onInputMode,
        )
        LauncherSetting.ControlHint -> ControlHintCard()
        LauncherSetting.StickWalk -> SettingChoiceRow(
            title = stringResource(R.string.launcher_settings_stick_walk),
            value = gainLabel(v.walkGain),
            options = STICK_GAIN_STEPS,
            selected = v.walkGain,
            optionLabel = { gainLabel(it) },
            description = stringResource(R.string.launcher_settings_stick_walk_desc),
            enabled = v.enabled,
            scope = scope,
            onSelect = a.onWalkGain,
        )
        LauncherSetting.StickCamera -> SettingChoiceRow(
            title = stringResource(R.string.launcher_settings_stick_camera),
            value = gainLabel(v.cameraGain),
            options = STICK_GAIN_STEPS,
            selected = v.cameraGain,
            optionLabel = { gainLabel(it) },
            description = stringResource(R.string.launcher_settings_stick_camera_desc),
            enabled = v.enabled,
            scope = scope,
            onSelect = a.onCameraGain,
        )
        LauncherSetting.LoginTouch -> LoginTouchSettingRow(enabled = v.enabled)
        LauncherSetting.WindowHide -> WindowHideSettingRow(enabled = v.enabled)
        LauncherSetting.TouchLook -> TouchLookSettingsBlock(enabled = v.enabled)
        LauncherSetting.ClickWheel -> KlickRadSettingsBlock(enabled = v.enabled)
        LauncherSetting.LayoutReset -> SettingActionRow(
            title = stringResource(R.string.launcher_settings_layout_reset),
            description = stringResource(R.string.launcher_settings_layout_reset_desc),
            actionText = stringResource(R.string.launcher_banner_reset),
            danger = true,
            enabled = v.enabled,
            scope = scope,
            onClick = a.onLayoutReset,
        )
        LauncherSetting.Voice -> VoiceSettingsBlock(enabled = v.enabled)
        LauncherSetting.VoiceLanguage -> VoiceLanguageRow(enabled = v.enabled)
        LauncherSetting.Cooldowns -> WowCooldownSettingsRows(enabled = v.enabled)
        LauncherSetting.Icons -> WowIconsSettingsBlock(enabled = v.enabled)
        LauncherSetting.AutoStart -> {
            SettingSwitchRow(
                title = stringResource(R.string.launcher_settings_autostart),
                description = stringResource(R.string.launcher_settings_autostart_desc),
                checked = v.autoStartOn,
                enabled = v.enabled,
                scope = scope,
                onChange = a.onAutoStart,
            )
            if (v.autoStartOn) {
                SettingValueRow(
                    title = stringResource(R.string.launcher_settings_countdown),
                    description = stringResource(R.string.launcher_settings_countdown_desc),
                    value = "${v.autoStartSeconds} s",
                    enabled = v.enabled,
                ) { a.onAutoStartSeconds(AutoStartLogic.nextSeconds(v.autoStartSeconds)) }
            }
        }
        LauncherSetting.StartCheck -> SettingSwitchRow(
            title = stringResource(R.string.launcher_settings_startcheck),
            description = stringResource(R.string.launcher_settings_startcheck_desc),
            checked = v.startCheckOn,
            enabled = v.enabled,
            scope = scope,
            onChange = a.onStartCheck,
        )
        LauncherSetting.Screenshots -> ScreenshotsSettingRow(enabled = v.enabled, scope = scope)
        LauncherSetting.WowArt -> WowArtSettingRow(enabled = v.enabled, scope = scope)
        LauncherSetting.Updates -> UpdateSettingsBlock(enabled = v.enabled)
        LauncherSetting.AppVersion -> SettingRow(title = stringResource(R.string.launcher_about_app_version)) {
            ValueText(BuildConfig.VERSION_NAME)
        }
        LauncherSetting.ClientBuild -> SettingRow(
            title = stringResource(R.string.launcher_settings_client_build),
            description = clientBuildText(v.clientLocal, v.clientRemote, v.clientOutdated).asString(),
        )
        LauncherSetting.ErrorReport -> ErrorReportSettingRow()
    }
}

/** Schalter "Eigene Screenshots im Launcher zeigen" (Standard an); der Zustand liegt in [LauncherArtSettings], die Startseite folgt sofort. */
@Composable
private fun ScreenshotsSettingRow(enabled: Boolean, scope: SettingScope) {
    val context = LocalContext.current
    val on by LauncherArtSettings.screenshotsFlow(context).collectAsState()
    SettingSwitchRow(
        title = stringResource(R.string.launcher_settings_screenshots),
        description = stringResource(R.string.launcher_settings_screenshots_desc),
        checked = on,
        enabled = enabled,
        scope = scope,
        onChange = { LauncherArtSettings.setScreenshots(context, it) },
    )
}

/** Schalter "Echte WoW-Bilder im Launcher" (Standard an): Aus = geladene Bilder werden nicht benutzt und nichts wird geladen. */
@Composable
private fun WowArtSettingRow(enabled: Boolean, scope: SettingScope) {
    val context = LocalContext.current
    val on by LauncherArtSettings.wowArtFlow(context).collectAsState()
    SettingSwitchRow(
        title = stringResource(R.string.launcher_settings_wow_art),
        description = stringResource(R.string.launcher_settings_wow_art_desc),
        checked = on,
        enabled = enabled,
        scope = scope,
        onChange = { LauncherArtSettings.setWowArt(context, it) },
    )
}

/** Hinweiskarte der Gruppe Steuerung: die Feinheiten der Steuerung stehen im Spiel, mit Weg und Liste der dortigen Gruppen. */
@Composable
private fun ControlHintCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LauncherSectionTitle(text = LauncherTexts.CONTROL_HINT_TITLE.asString())
        Text(
            text = LauncherTexts.CONTROL_HINT_WHERE.asString(),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = LauncherColors.Body,
        )
        for (name in LauncherTexts.CONTROL_HINT_GROUPS) {
            Text(
                text = "•  ${name.asString()}",
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = LauncherColors.Muted,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Rückfrage vor dem Öffnen der Einstellungen, die es auch im Spiel gibt ([GameSettingsFold.asking]). */
@Composable
private fun GameFoldAskDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = LauncherTexts.GAME_FOLD_ASK_TITLE.asString(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = LauncherColors.Gold) },
        text = { Text(text = LauncherTexts.GAME_FOLD_ASK_TEXT.asString(), fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFFE2E8F0)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(LauncherTexts.GAME_FOLD_OPEN.asString(), color = LauncherColors.Gold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(LauncherTexts.GAME_FOLD_CANCEL.asString(), color = LauncherColors.Muted)
            }
        },
        containerColor = Color(0xFF1B2838),
        shape = RoundedCornerShape(16.dp),
    )
}

/** Einstellungen, die aus einem eigenen Block mit mehreren Zeilen bestehen (oder fremde Zeilen benutzen): Abzeichen als Streifen davor. */
private val LauncherSetting.isBlock: Boolean
    get() = when (this) {
        LauncherSetting.LoginTouch, LauncherSetting.WindowHide, LauncherSetting.TouchLook, LauncherSetting.ClickWheel,
        LauncherSetting.Voice, LauncherSetting.Icons, LauncherSetting.Updates, LauncherSetting.ErrorReport,
        LauncherSetting.VoiceLanguage, LauncherSetting.Cooldowns,
        -> true
        else -> false
    }
