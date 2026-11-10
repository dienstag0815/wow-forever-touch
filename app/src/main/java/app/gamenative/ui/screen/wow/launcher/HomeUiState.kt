package app.gamenative.ui.screen.wow.launcher

import app.gamenative.R
import app.gamenative.ui.screen.wow.TWO_COLUMN_MIN_WIDTH_DP
import app.gamenative.update.CheckPhase
import app.gamenative.update.UpdateInfo
import app.gamenative.update.UpdateState
import kotlin.math.roundToLong

/*
 * Reine Logik der Startseite (ohne Android, per JUnit getestet): aus dem Zustand des Launchers entstehen Statuspille, Hero
 * (Titel, Zeile, Knöpfe), Kacheln mit Zeile 2, Badge und Sperre sowie die Seite "Spieldateien". Die Oberfläche
 * (LauncherParts.kt, FilesPage.kt ...) zeichnet nur noch, was hier herauskommt.
 */

/** Die Vollbildseiten des Launchers. Einstellungen und Addons gab es schon, die übrigen sind neu. */
internal enum class LauncherPage { Settings, Addons, Touch, Files, News, Help, About }

/** Die sechs Kacheln in der Reihenfolge von links nach rechts, oben nach unten; jede öffnet genau ihre [page]. */
internal enum class LauncherTile(val page: LauncherPage) {
    Touch(LauncherPage.Touch),
    Addons(LauncherPage.Addons),
    Settings(LauncherPage.Settings),
    Files(LauncherPage.Files),
    News(LauncherPage.News),
    Help(LauncherPage.Help),
}

/** Aufgeklappter Fold: Hero links, Kacheln rechts. Schmal: Hero oben, Kacheln darunter (gleiche zwei Spalten). */
internal enum class HomeLayout {
    Wide,
    Narrow,
    ;

    val heroOnTop: Boolean get() = this == Narrow
}

/** Die Umschaltung hängt an der Breite ([TWO_COLUMN_MIN_WIDTH_DP], wie die Einstellungsseite), nicht am Gerätenamen. */
internal fun homeLayoutFor(widthDp: Float): HomeLayout = if (widthDp >= TWO_COLUMN_MIN_WIDTH_DP) HomeLayout.Wide else HomeLayout.Narrow

/** Die Kacheln in Zeilen zu je zwei (drei Zeilen), in beiden Layouts gleich. */
internal fun tileRows(): List<List<LauncherTile>> = LauncherTile.entries.chunked(2)

/** Alles, was die Startseite vom Launcher-Zustand wissen muss. Die Vorgaben beschreiben den Zustand "spielbereit". */
internal data class HomeInput(
    val filesChecked: Boolean = true,
    val hasStorageAccess: Boolean = true,
    val needsInstall: Boolean = false,
    val isLegacyBeta: Boolean = false,
    val isWrongGame: Boolean = false,
    val clientLocal: String? = null,
    val clientRemote: String? = null,
    val clientOutdated: Boolean = false,
    val isLaunching: Boolean = false,
    val isUpdating: Boolean = false,
    val isAutoInstall: Boolean = false,
    /** Text neben dem Fortschritt (Start, Download, Versionsprüfung); null = nichts läuft. */
    val busyText: String? = null,
    val errorMessage: String? = null,
    val canPlay: Boolean = true,
    val hasSavedLogin: Boolean = true,
    val regionLabel: String = "Europe",
    val resolutionLabel: String = "1080p",
    val fpsLabel: String = "60",
    val inputModeLabel: String = "Touch (WoW)",
    val appVersion: String = "2.3.3",
    val freeSpaceBytes: Long? = null,
    /** Ein neuerer Launcher steht bereit oder wird geladen/installiert. */
    val launcherUpdateOffered: Boolean = false,
    /** "Neu in ..." wartet noch auf "Verstanden". */
    val whatsNewPending: Boolean = false,
    /** Offene Punkte der Bereitschaftsliste (siehe readinessSummary). */
    val readinessOpen: Int = 0,
) {
    val busy: Boolean get() = busyText != null

    /** Das Spiel ist vorhanden und nutzbar: Touch-Layout und Addons haben dann etwas zu bearbeiten. */
    val gameUsable: Boolean get() = hasStorageAccess && !needsInstall && !isWrongGame
}

// --- Pille ---

internal enum class PillTone { Ok, Warn, Error, Info, Muted }

/** [text] ist die lange Fassung (aufgeklappt), [shortText] ohne Versionsangabe (zugeklappt, wenig Platz). */
internal data class PillUi(val tone: PillTone, val text: UiText, val shortText: UiText = text)

private fun updateTitle(remote: String?): UiText =
    if (remote.isNullOrBlank()) UiText.res(R.string.launcher_pill_update) else UiText.res(R.string.launcher_pill_update_version, remote)

internal fun pillUi(i: HomeInput): PillUi = when {
    i.errorMessage != null -> PillUi(PillTone.Error, UiText.res(R.string.launcher_pill_problem))
    i.busy && i.isUpdating -> PillUi(PillTone.Info, UiText.res(R.string.launcher_pill_installing))
    i.busy && i.isLaunching -> PillUi(PillTone.Info, UiText.res(R.string.launcher_pill_starting))
    i.busy -> PillUi(PillTone.Info, UiText.res(R.string.launcher_pill_checking_version))
    !i.filesChecked -> PillUi(PillTone.Muted, UiText.res(R.string.launcher_pill_checking_install))
    !i.hasStorageAccess -> PillUi(PillTone.Warn, UiText.res(R.string.launcher_pill_access))
    i.needsInstall ->
        if (i.isLegacyBeta) PillUi(PillTone.Warn, UiText.res(R.string.launcher_pill_upgrade))
        else PillUi(PillTone.Muted, UiText.res(R.string.launcher_pill_not_installed))
    i.clientOutdated -> PillUi(PillTone.Warn, updateTitle(i.clientRemote), UiText.res(R.string.launcher_pill_update_short))
    i.isWrongGame -> PillUi(PillTone.Error, UiText.res(R.string.launcher_pill_wrong_game))
    else -> {
        val base = UiText.res(if (i.launcherUpdateOffered) R.string.launcher_pill_ready_app_update else R.string.launcher_pill_ready)
        val withClient =
            if (i.clientLocal.isNullOrBlank() || i.launcherUpdateOffered) base else UiText.res(R.string.launcher_join_dot, base, i.clientLocal)
        PillUi(PillTone.Ok, withClient, base)
    }
}

// --- Hero ---

internal enum class HeroKind { NeedAccess, Install, Upgrade, Update, Play, WrongGame, Installing, Starting, Checking }

internal enum class HeroAction { GrantStorage, Install, Update, Play, CancelInstall, PickFolder }

internal data class HeroButton(val text: UiText, val action: HeroAction, val enabled: Boolean = true, val danger: Boolean = false)

internal data class HeroUi(
    val kind: HeroKind,
    /** Große Überschrift im Hero. */
    val title: UiText,
    /** Eine erklärende Zeile darunter. */
    val line: UiText,
    /** Der große Bronzeknopf; null, solange etwas läuft. */
    val primary: HeroButton?,
    /** Der zweite Knopf als Textlink (Dateien suchen, trotzdem starten, abbrechen). */
    val secondary: HeroButton?,
    /** Fortschrittskreis neben der Zeile (Start, Download, Versionsprüfung). */
    val showSpinner: Boolean,
    /** Countdown/Autostart-Zeile unter dem Knopf (nur beim Spielen). */
    val showCountdown: Boolean,
    /** Anmelde-Chip; wie bisher nur, solange nichts läuft. */
    val showLogin: Boolean,
)

/** " · 112,0 GB frei" (bzw. " · 112.0 GB free"); leer, wenn der freie Platz nicht bekannt ist. */
internal fun freeSpaceSuffix(bytes: Long?): UiText =
    if (bytes == null) UiText.raw("") else UiText.res(R.string.launcher_free_suffix, gbText(bytes))

/** "112,0 GB": eine Nachkommastelle, das Komma folgt der App-Sprache (Deutsch Komma, Englisch Punkt). */
internal fun gbText(bytes: Long): UiText {
    val tenths = (bytes.coerceAtLeast(0L) / 1e8).roundToLong()
    return UiText.res(R.string.launcher_gb, tenths / 10.0)
}

internal fun loginChipText(hasSavedLogin: Boolean): UiText = if (hasSavedLogin) LauncherTexts.LOGIN_SAVED else LauncherTexts.LOGIN_SETUP

/** Wie der große Knopf früher (MainAction): Reihenfolge Zugriff, Installation, Spiel-Update, Spielen. */
internal fun heroUi(i: HomeInput): HeroUi {
    val busyText = i.busyText
    if (busyText != null) {
        val line = UiText.raw(busyText)
        return when {
            i.isUpdating -> HeroUi(
                HeroKind.Installing, UiText.res(R.string.launcher_pill_installing), line,
                primary = null,
                secondary = if (i.isAutoInstall) HeroButton(LauncherTexts.CANCEL_INSTALL_BUTTON, HeroAction.CancelInstall) else null,
                showSpinner = true, showCountdown = false, showLogin = false,
            )
            i.isLaunching -> HeroUi(
                HeroKind.Starting, UiText.res(R.string.launcher_pill_starting), line,
                primary = null, secondary = null, showSpinner = true, showCountdown = false, showLogin = false,
            )
            else -> HeroUi(
                HeroKind.Checking, UiText.res(R.string.launcher_hero_checking), line,
                primary = null, secondary = null, showSpinner = true, showCountdown = false, showLogin = false,
            )
        }
    }
    val find = HeroButton(LauncherTexts.FIND_FILES_BUTTON, HeroAction.PickFolder)
    return when {
        !i.hasStorageAccess -> HeroUi(
            HeroKind.NeedAccess, UiText.res(R.string.launcher_pill_access),
            UiText.res(R.string.launcher_hero_access_line),
            primary = HeroButton(LauncherTexts.GRANT_BUTTON, HeroAction.GrantStorage),
            secondary = null, showSpinner = false, showCountdown = false, showLogin = true,
        )
        i.needsInstall && i.isLegacyBeta -> HeroUi(
            HeroKind.Upgrade, UiText.res(R.string.launcher_hero_upgrade_title),
            UiText.res(R.string.launcher_hero_upgrade_line),
            primary = HeroButton(LauncherTexts.UPGRADE_BUTTON, HeroAction.Install),
            secondary = find, showSpinner = false, showCountdown = false, showLogin = true,
        )
        i.needsInstall -> HeroUi(
            HeroKind.Install, UiText.res(R.string.launcher_hero_install_title),
            UiText.res(R.string.launcher_hero_install_line, freeSpaceSuffix(i.freeSpaceBytes)),
            primary = HeroButton(LauncherTexts.INSTALL_BUTTON, HeroAction.Install),
            secondary = find, showSpinner = false, showCountdown = false, showLogin = true,
        )
        i.clientOutdated -> HeroUi(
            HeroKind.Update, UiText.res(R.string.launcher_pill_update),
            UiText.res(R.string.launcher_hero_outdated_line, i.clientRemote.orEmpty(), i.clientLocal.orEmpty()),
            primary = HeroButton(UiText.res(R.string.launcher_hero_update_to, i.clientRemote.orEmpty()), HeroAction.Update, enabled = i.canPlay),
            secondary = HeroButton(LauncherTexts.PLAY_OUTDATED_BUTTON, HeroAction.Play, enabled = i.canPlay, danger = true),
            showSpinner = false, showCountdown = false, showLogin = true,
        )
        else -> {
            val wrong = i.isWrongGame
            val line = listOfNotNull("WoW Forever", i.clientLocal?.takeIf { it.isNotBlank() }, i.regionLabel, i.resolutionLabel)
                .joinToString(" · ")
            HeroUi(
                if (wrong) HeroKind.WrongGame else HeroKind.Play,
                if (wrong) UiText.res(R.string.launcher_pill_wrong_game) else LauncherTexts.PLAY_TITLE,
                if (wrong) UiText.res(R.string.launcher_hero_wrong_game_line) else UiText.raw(line),
                primary = HeroButton(if (wrong) LauncherTexts.WRONG_GAME_BUTTON else LauncherTexts.PLAY_BUTTON, HeroAction.Play, enabled = i.canPlay),
                secondary = null, showSpinner = false, showCountdown = true, showLogin = true,
            )
        }
    }
}

// --- Kacheln ---

internal enum class TileTone { Normal, Warn, Bad }

internal data class TileUi(
    val tile: LauncherTile,
    val title: UiText,
    /** Zeile 2: der Zustand in einem Satz. */
    val subtitle: UiText,
    val badge: UiText? = null,
    /** Gesperrte Kacheln bleiben sichtbar, lassen sich aber nicht öffnen. */
    val locked: Boolean = false,
    val tone: TileTone = TileTone.Normal,
)

/**
 * Sperre: läuft etwas (Start, Installation, Prüfung), bleiben nur Neuigkeiten und Hilfe offen; ohne nutzbares Spiel sind
 * Touch-Layout und Addons gesperrt.
 */
internal fun isTileLocked(tile: LauncherTile, i: HomeInput): Boolean = when {
    tile == LauncherTile.News || tile == LauncherTile.Help -> false
    i.busy -> true
    tile == LauncherTile.Touch || tile == LauncherTile.Addons -> !i.gameUsable
    else -> false
}

internal fun tilesUi(i: HomeInput): List<TileUi> = LauncherTile.entries.map { tile ->
    val locked = isTileLocked(tile, i)
    val title = LauncherTexts.tileTitle(tile)
    when (tile) {
        LauncherTile.Touch -> TileUi(
            tile, title,
            if (!i.gameUsable) UiText.res(R.string.launcher_tile_install_first) else LauncherTexts.controlTileSubtitle(i.inputModeLabel),
            locked = locked,
        )
        LauncherTile.Addons -> TileUi(
            tile, title,
            UiText.res(if (!i.gameUsable) R.string.launcher_tile_install_first else R.string.launcher_tile_manage_addons),
            locked = locked,
        )
        LauncherTile.Settings -> TileUi(
            tile, title, UiText.res(R.string.launcher_tile_settings_sub, i.resolutionLabel, i.fpsLabel, i.regionLabel), locked = locked,
        )
        LauncherTile.Files -> when {
            !i.hasStorageAccess -> TileUi(tile, title, UiText.res(R.string.launcher_tile_access_missing), locked = locked, tone = TileTone.Warn)
            i.isWrongGame -> TileUi(tile, title, UiText.res(R.string.launcher_tile_wrong_game), locked = locked, tone = TileTone.Bad)
            i.needsInstall -> TileUi(
                tile, title,
                UiText.res(if (i.isLegacyBeta) R.string.launcher_pill_upgrade else R.string.launcher_pill_not_installed),
                locked = locked, tone = TileTone.Warn,
            )
            i.clientOutdated -> TileUi(
                tile, title, updateTitle(i.clientRemote),
                badge = UiText.res(R.string.launcher_tile_update_badge), locked = locked, tone = TileTone.Warn,
            )
            else -> TileUi(tile, title, UiText.res(R.string.launcher_tile_installed, freeSpaceSuffix(i.freeSpaceBytes)), locked = locked)
        }
        LauncherTile.News -> TileUi(
            tile, title,
            if (i.launcherUpdateOffered) UiText.res(R.string.launcher_tile_app_update) else UiText.res(R.string.launcher_tile_new_in, i.appVersion),
            badge = if (i.whatsNewPending || i.launcherUpdateOffered) UiText.res(R.string.launcher_tile_new_badge) else null,
            locked = locked,
        )
        LauncherTile.Help -> when {
            i.errorMessage != null -> TileUi(
                tile, title, UiText.res(R.string.launcher_tile_problem_found), badge = UiText.raw("1"), locked = locked, tone = TileTone.Bad,
            )
            !i.filesChecked -> TileUi(tile, title, UiText.res(R.string.launcher_hero_checking), locked = locked)
            i.readinessOpen > 0 -> TileUi(
                tile, title, UiText.plural(R.plurals.launcher_points_open, i.readinessOpen),
                badge = UiText.raw(i.readinessOpen.toString()), locked = locked, tone = TileTone.Warn,
            )
            else -> TileUi(tile, title, UiText.res(R.string.launcher_tile_all_ready), locked = locked)
        }
    }
}

/** Alles zusammen, was die Startseite zeichnet. */
internal data class HomeUi(
    val pill: PillUi,
    val hero: HeroUi,
    /** Meldung für das rote Banner (ohne "Fehler:"), sonst null. */
    val errorBanner: String?,
    val tiles: List<TileUi>,
)

internal fun homeUiState(i: HomeInput): HomeUi = HomeUi(pillUi(i), heroUi(i), i.errorMessage, tilesUi(i))

// --- Seite "Spieldateien" ---

internal data class FilesPageUi(
    /** Aktueller Stand in einem Satz. */
    val status: UiText,
    val showGrantStorage: Boolean,
    val showInstall: Boolean,
    val installIsUpgrade: Boolean,
    val showUpdate: Boolean,
    val updateText: UiText,
    /** Ordner wählen, Neu prüfen und Anmeldung sind immer da; gesperrt, solange etwas läuft. */
    val controlsEnabled: Boolean,
)

internal fun filesPageUi(i: HomeInput): FilesPageUi {
    val status = when {
        !i.hasStorageAccess -> UiText.res(R.string.launcher_files_access_missing)
        i.isWrongGame -> UiText.res(R.string.launcher_files_other_game)
        i.needsInstall ->
            if (i.isLegacyBeta) UiText.res(R.string.launcher_files_beta_upgrade) else UiText.res(R.string.launcher_pill_not_installed)
        i.clientOutdated -> UiText.res(R.string.launcher_files_update_available, i.clientRemote.orEmpty())
        else -> {
            val client = i.clientLocal?.takeIf { it.isNotBlank() }
            if (client != null) UiText.res(R.string.launcher_files_installed_client, client) else UiText.res(R.string.launcher_files_installed)
        }
    }
    return FilesPageUi(
        status = status,
        showGrantStorage = !i.hasStorageAccess,
        showInstall = i.hasStorageAccess && i.needsInstall,
        installIsUpgrade = i.isLegacyBeta,
        showUpdate = i.hasStorageAccess && !i.needsInstall && i.clientOutdated,
        updateText = UiText.res(R.string.launcher_hero_update_to, i.clientRemote.orEmpty()),
        controlsEnabled = !i.busy,
    )
}

// --- Knopf "Nach Updates suchen" ---

/** Wie lange ein Ergebnis ("Aktuell", "Keine Verbindung") am Knopf stehen bleibt, bevor er wieder "Nach Updates suchen" zeigt. */
internal const val HOME_UPDATE_RESULT_MS = 5_000L

internal enum class HomeUpdateKind { Search, Checking, UpToDate, Offered, Working, Failed }

/** Was ein Tipp auslöst: Check = UpdateManager.checkManually (wie in den Einstellungen), Install = UpdateManager.install (wie die Update-Karte). */
internal enum class HomeUpdateAction { None, Check, Install }

/**
 * Der Knopf auf der Startseite. [label] steht neben dem Symbol (breite Startseite), [description] ist der Text für die
 * Bedienungshilfe und das Einzige, was zugeklappt bleibt (nur Symbol). [autoReset]: das Ergebnis ist flüchtig und wird nach
 * [HOME_UPDATE_RESULT_MS] zurückgesetzt (UpdateManager.dismiss).
 */
internal data class HomeUpdateButtonUi(
    val kind: HomeUpdateKind,
    val label: UiText,
    val description: UiText,
    val action: HomeUpdateAction,
    val enabled: Boolean,
    val spinner: Boolean,
    val autoReset: Boolean,
)

/**
 * Fehlertexte des Prüfwegs, die auf fehlendes Netz hindeuten: "Kein Netz." und die Meldungen von OkHttp/Java, die hinter "Suche
 * fehlgeschlagen" stehen (das Argument des Textes ist dann die rohe Meldung).
 */
internal fun isConnectionProblem(message: UiText): Boolean = when (message) {
    is UiText.Res -> when (message.id) {
        R.string.launcher_update_no_network -> true
        R.string.launcher_update_search_failed -> {
            val raw = (message.args.firstOrNull() as? String).orEmpty().lowercase()
            listOf("unable to resolve", "timed out", "timeout", "failed to connect", "unreachable").any { it in raw }
        }
        else -> false
    }
    else -> false
}

private fun offeredButton(info: UpdateInfo, canInstall: Boolean): HomeUpdateButtonUi {
    val v = info.versionName.trim()
    return HomeUpdateButtonUi(
        kind = HomeUpdateKind.Offered,
        label = UiText.res(R.string.launcher_home_install_update, v),
        description = UiText.res(if (canInstall) R.string.launcher_home_update_available_desc else R.string.launcher_home_update_blocked_desc, v),
        action = HomeUpdateAction.Install,
        enabled = canInstall,
        spinner = false,
        autoReset = false,
    )
}

/**
 * Zustand des Knopfes aus dem Zustand der Update-Verwaltung (reine Funktion, siehe UpdateManager): ruhig "Nach Updates suchen";
 * läuft die Suche (auch die automatische beim Start, [phase]) ist er gesperrt; danach "Aktuell (Version ...)", "Update ... installieren"
 * oder der Fehler mit erneutem Versuch. Liegt schon ein Angebot vor, zeigt er sofort das. [canInstall] ist false, solange ein Spiel
 * startet oder Dateien geladen werden (wie beim Streifen).
 */
internal fun homeUpdateButtonState(state: UpdateState, phase: CheckPhase, appVersion: String, canInstall: Boolean): HomeUpdateButtonUi {
    val search = HomeUpdateButtonUi(
        HomeUpdateKind.Search, UiText.res(R.string.launcher_home_check_updates), UiText.res(R.string.launcher_home_check_updates),
        HomeUpdateAction.Check, enabled = true, spinner = false, autoReset = false,
    )
    val checking = HomeUpdateButtonUi(
        HomeUpdateKind.Checking, UiText.res(R.string.launcher_home_checking), UiText.res(R.string.launcher_home_checking_desc),
        HomeUpdateAction.None, enabled = false, spinner = true, autoReset = false,
    )
    return when (state) {
        is UpdateState.Idle, is UpdateState.Installed -> if (phase == CheckPhase.Running) checking else search
        is UpdateState.Checking -> checking
        is UpdateState.UpToDate -> HomeUpdateButtonUi(
            HomeUpdateKind.UpToDate, UiText.res(R.string.launcher_home_up_to_date, appVersion),
            UiText.res(R.string.launcher_home_up_to_date_desc, appVersion),
            HomeUpdateAction.Check, enabled = true, spinner = false, autoReset = true,
        )
        is UpdateState.Available -> offeredButton(state.info, canInstall)
        is UpdateState.Downloading -> {
            val percent = (state.fraction * 100f).toInt().coerceIn(0, 100)
            HomeUpdateButtonUi(
                HomeUpdateKind.Working, UiText.res(R.string.launcher_home_downloading, percent),
                UiText.res(R.string.launcher_home_downloading_desc, percent), HomeUpdateAction.None,
                enabled = false, spinner = true, autoReset = false,
            )
        }
        is UpdateState.Installing -> HomeUpdateButtonUi(
            HomeUpdateKind.Working, UiText.res(R.string.launcher_home_installing), UiText.res(R.string.launcher_home_installing_desc),
            HomeUpdateAction.None, enabled = false, spinner = true, autoReset = false,
        )
        is UpdateState.Error -> if (state.info != null) {
            HomeUpdateButtonUi(
                HomeUpdateKind.Failed, UiText.res(R.string.launcher_home_try_again), UiText.res(R.string.launcher_home_failed_desc),
                HomeUpdateAction.Install, enabled = canInstall, spinner = false, autoReset = false,
            )
        } else {
            val reason = UiText.res(if (isConnectionProblem(state.message)) R.string.launcher_home_no_connection else R.string.launcher_home_search_failed)
            HomeUpdateButtonUi(
                HomeUpdateKind.Failed, UiText.res(R.string.launcher_home_reason_retry, reason), UiText.res(R.string.launcher_home_reason_tap, reason),
                HomeUpdateAction.Check, enabled = true, spinner = false, autoReset = true,
            )
        }
    }
}
