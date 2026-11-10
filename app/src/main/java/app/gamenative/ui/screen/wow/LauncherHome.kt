package app.gamenative.ui.screen.wow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.gamenative.BuildConfig
import app.gamenative.ui.screen.wow.launcher.ErrorBanner
import app.gamenative.ui.screen.wow.launcher.HeroAction
import app.gamenative.ui.screen.wow.launcher.HeroCard
import app.gamenative.ui.screen.wow.launcher.HomeFooter
import app.gamenative.ui.screen.wow.launcher.HomeHeader
import app.gamenative.ui.screen.wow.launcher.HomeInput
import app.gamenative.ui.screen.wow.launcher.HomeTile
import app.gamenative.ui.screen.wow.launcher.HomeUpdateButtonHost
import app.gamenative.ui.screen.wow.launcher.LauncherArt
import app.gamenative.ui.screen.wow.launcher.LauncherPage
import app.gamenative.ui.screen.wow.launcher.LauncherTexts
import app.gamenative.ui.screen.wow.launcher.UpdateButtonSlot
import app.gamenative.ui.screen.wow.launcher.rememberLauncherArt
import app.gamenative.ui.screen.wow.launcher.homeMetricsFor
import app.gamenative.ui.screen.wow.launcher.homeUiState
import app.gamenative.ui.screen.wow.launcher.tileRows
import app.gamenative.update.UpdateBanner
import app.gamenative.update.UpdateManager
import app.gamenative.update.isOffered

/** Was die Hauptansicht anzeigt; alles Zustandsbehaftete bleibt im Launcher-Bildschirm. */
internal data class LauncherHomeState(
    val edition: String,
    val files: GamePath.Status,
    val filesChecked: Boolean,
    val gamePath: String,
    val hasSavedLogin: Boolean,
    val versionStatus: WowClientDownloader.VersionCheckResult?,
    val isLaunching: Boolean,
    val isUpdating: Boolean,
    val isAutoInstall: Boolean,
    /** Text neben dem Fortschrittskreis (Start, Download, Versionsprüfung); null = nichts läuft. */
    val busyText: String?,
    val errorMessage: String?,
    val autoUi: AutoStartUi,
    val canPlay: Boolean,
    /** Beschriftungen der gewählten Einstellungen für Hero und Kachel "Einstellungen". */
    val regionLabel: String,
    val resolutionLabel: String,
    val fpsLabel: String,
    val inputModeLabel: String,
    /** Ein Fenster liegt über der Startseite (Einstellungen, Addons, Seite, Info); das Update-Banner wartet dann. */
    val homeCovered: Boolean = false,
)

internal data class LauncherHomeActions(
    val onPlay: () -> Unit,
    val onCancelCountdown: () -> Unit,
    val onInstall: () -> Unit,
    val onUpdate: () -> Unit,
    val onCancelInstall: () -> Unit,
    val onPickFolder: () -> Unit,
    val onGrantStorage: () -> Unit,
    val onLogin: () -> Unit,
    val onForgetLogin: () -> Unit,
    val onRefresh: () -> Unit,
    /** Öffnet eine Vollbildseite (Kachel, Pille oder Fußzeile); der Launcher-Bildschirm hält den Zustand. */
    val onOpenPage: (LauncherPage) -> Unit,
)

/** Der Launcher-Zustand als Eingabe der reinen Startseiten-Logik (siehe launcher/HomeUiState.kt). */
internal fun LauncherHomeState.toHomeInput(
    appVersion: String = "",
    readinessOpen: Int = 0,
    freeSpaceBytes: Long? = null,
    launcherUpdateOffered: Boolean = false,
    whatsNewPending: Boolean = false,
): HomeInput = HomeInput(
    filesChecked = filesChecked,
    hasStorageAccess = files.hasStorageAccess,
    needsInstall = files.needsInstall,
    isLegacyBeta = files.isLegacyBeta,
    isWrongGame = files.isWrongGame,
    clientLocal = versionStatus?.localVersion,
    clientRemote = versionStatus?.remoteVersion,
    clientOutdated = versionStatus?.isOutdated == true,
    isLaunching = isLaunching,
    isUpdating = isUpdating,
    isAutoInstall = isAutoInstall,
    busyText = busyText,
    errorMessage = errorMessage,
    canPlay = canPlay,
    hasSavedLogin = hasSavedLogin,
    regionLabel = regionLabel,
    resolutionLabel = resolutionLabel,
    fpsLabel = fpsLabel,
    inputModeLabel = inputModeLabel,
    appVersion = appVersion,
    freeSpaceBytes = freeSpaceBytes,
    launcherUpdateOffered = launcherUpdateOffered,
    whatsNewPending = whatsNewPending,
    readinessOpen = readinessOpen,
)

/** Die Bereitschaftsliste (Zustand der Dateien, Anmeldung, Client) als Zusammenfassung; Startseite und Hilfe nutzen sie. */
internal fun LauncherHomeState.readiness(): ReadinessSummary = readinessSummary(
    ReadinessInput(
        checked = filesChecked,
        programExists = files.exeExists,
        dataExists = files.dataExists,
        hasForever = files.hasForever,
        isLegacyBeta = files.isLegacyBeta,
        isWrongGame = files.isWrongGame,
        hasStorageAccess = files.hasStorageAccess,
        hasSavedLogin = hasSavedLogin,
        clientLocal = versionStatus?.localVersion,
        clientRemote = versionStatus?.remoteVersion,
        clientOutdated = versionStatus?.isOutdated == true,
    ),
)

/**
 * Startseite des Launchers als Kachelbild: Kopf mit Statuspille, ein Streifen für Update oder Fehler, links der Hero
 * (Spielen oder was vorher nötig ist), rechts sechs Kacheln, unten die Fußzeile. Aufgeklappt (ab [TWO_COLUMN_MIN_WIDTH_DP])
 * steht der Hero links, zugeklappt oben. Alles teilt sich die Höhe (homeMetricsFor): die Seite scrollt nur noch unterhalb einer
 * Mindesthöhe. Was zu welchem Zustand angezeigt wird, entscheidet [homeUiState]; die Seiten hinter den
 * Kacheln öffnet der Launcher-Bildschirm über [LauncherHomeActions.onOpenPage].
 */
@Composable
internal fun LauncherHome(s: LauncherHomeState, a: LauncherHomeActions, artOverride: LauncherArt? = null) {
    // Bilder: eigene Screenshots, WoW-Artwork, mitgelieferte Bilder, sonst gezeichnet (siehe launcher/LauncherImages.kt).
    // Nach dem Spiel (isLaunching wird wieder false) lädt die Auswahl neu, damit neue Screenshots erscheinen.
    val loadedArt = rememberLauncherArt(s.gamePath, s.isLaunching)
    val art: LauncherArt = artOverride ?: loadedArt
    val updateState by UpdateManager.state.collectAsState()
    val whatsNew by UpdateManager.whatsNew.collectAsState()
    val uriHandler = LocalUriHandler.current
    val readiness = s.readiness()
    val freeBytes = remember(s.gamePath, s.isUpdating, s.filesChecked) { GamePath.freeSpaceBytes(s.gamePath) }
    val input = s.toHomeInput(
        appVersion = BuildConfig.VERSION_NAME,
        readinessOpen = if (readiness.checked) readiness.open.size else 0,
        freeSpaceBytes = freeBytes,
        launcherUpdateOffered = updateState.isOffered(),
        whatsNewPending = whatsNew != null,
    )
    val ui = homeUiState(input)
    val onHero: (HeroAction) -> Unit = { action ->
        when (action) {
            HeroAction.GrantStorage -> a.onGrantStorage()
            HeroAction.Install -> a.onInstall()
            HeroAction.Update -> a.onUpdate()
            HeroAction.Play -> a.onPlay()
            HeroAction.CancelInstall -> a.onCancelInstall()
            HeroAction.PickFolder -> a.onPickFolder()
        }
    }
    val openSource: () -> Unit = { runCatching { uriHandler.openUri(LauncherTexts.SOURCE_URL) } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Breite und Höhe hier lesen: in der Column ist der Empfänger von BoxWithConstraints nicht mehr implizit (Falle aus fold8.20).
        val m = homeMetricsFor(maxWidth.value, maxHeight.value)
        val compact = m.layout.heroOnTop
        val sectionGap = m.sectionGapDp.dp
        val tileGap = m.tileGapDp.dp
        val tileHeight = m.tileHeightDp.dp
        val byTile = ui.tiles.associateBy { it.tile }
        // Der Update-Knopf braucht keine eigene Zeile: rechts im Kopf (aufgeklappt) oder rechts in der Fußzeile (zugeklappt).
        val updateButton: @Composable () -> Unit = {
            HomeUpdateButtonHost(canInstall = !s.isLaunching && !s.isUpdating, showText = m.updateButtonText)
        }
        val header: @Composable () -> Unit = {
            HomeHeader(s.edition, ui.pill, compact, art, m.density) { a.onOpenPage(LauncherPage.Help) }
        }
        val footer: @Composable () -> Unit = {
            HomeFooter(
                appVersion = BuildConfig.VERSION_NAME,
                compact = compact,
                onLicenses = { a.onOpenPage(LauncherPage.About) },
                onSource = { openSource() },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (m.scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(sectionGap),
        ) {
            if (m.updateSlot == UpdateButtonSlot.Header) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) { header() }
                    updateButton()
                }
            } else {
                header()
            }
            UpdateBanner(
                canInstall = !s.isLaunching && !s.isUpdating,
                autoStartCounting = s.autoUi is AutoStartUi.Counting,
                homeCovered = s.homeCovered,
                showCheckResults = !m.updateButtonText,
            ) { a.onOpenPage(LauncherPage.News) }
            ui.errorBanner?.let { ErrorBanner(it) { a.onOpenPage(LauncherPage.Help) } }

            val hero: @Composable (Modifier) -> Unit = { mod ->
                HeroCard(
                    hero = ui.hero,
                    hasSavedLogin = s.hasSavedLogin,
                    autoUi = s.autoUi,
                    compact = compact,
                    art = art,
                    onAction = onHero,
                    onLogin = a.onLogin,
                    onCancelCountdown = a.onCancelCountdown,
                    modifier = mod,
                    density = m.density,
                )
            }

            // Der Mittelteil nimmt die Höhe, die Kopf, Streifen und Fußzeile übrig lassen (Gewicht 1). Nur unterhalb der Mindesthöhe
            // hat er eine feste Höhe und die Seite scrollt.
            val middle = if (m.scroll) {
                Modifier.fillMaxWidth().height(m.middleMinDp.dp)
            } else {
                Modifier.fillMaxWidth().weight(1f)
            }

            if (compact) {
                Column(modifier = middle, verticalArrangement = Arrangement.spacedBy(tileGap)) {
                    hero(Modifier.fillMaxWidth().weight(1f))
                    for (row in tileRows()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(tileHeight),
                            horizontalArrangement = Arrangement.spacedBy(tileGap),
                        ) {
                            for (tile in row) {
                                val t = byTile.getValue(tile)
                                HomeTile(
                                    t, art, compact = true, onClick = { a.onOpenPage(tile.page) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(), short = m.tileShort,
                                )
                            }
                        }
                    }
                }
            } else {
                Row(modifier = middle, horizontalArrangement = Arrangement.spacedBy(tileGap)) {
                    hero(Modifier.weight(1.45f).fillMaxHeight())
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(tileGap),
                    ) {
                        for (row in tileRows()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(tileGap),
                            ) {
                                for (tile in row) {
                                    val t = byTile.getValue(tile)
                                    HomeTile(
                                        t, art, compact = false, onClick = { a.onOpenPage(tile.page) },
                                        modifier = Modifier.weight(1f).fillMaxHeight(), short = m.tileShort,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (m.updateSlot == UpdateButtonSlot.Footer) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) { footer() }
                    updateButton()
                }
            } else {
                footer()
            }
        }
    }
}
