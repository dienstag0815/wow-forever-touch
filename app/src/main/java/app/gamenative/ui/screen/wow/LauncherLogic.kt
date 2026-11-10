package app.gamenative.ui.screen.wow

import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText

/*
 * Reine Entscheidungslogik des aufgeräumten Launchers (ohne Android, per JUnit getestet):
 *  - Bereitschaftszeile der Hauptansicht (welcher Text, wann klappt die Liste auf),
 *  - Gruppierung der Einstellungsseite (jede Einstellung genau einmal) und Spaltenaufteilung für den aufgeklappten Fold.
 */

/** Was ein offener Punkt der Bereitschaft braucht; die Hauptansicht zeigt dazu einen Knopf. */
internal enum class ReadyFix(@StringRes val buttonRes: Int) {
    None(0),
    StoragePermission(R.string.launcher_ready_fix_allow),
    Install(R.string.launcher_ready_fix_install),
    Login(R.string.launcher_ready_fix_login),
    Update(R.string.launcher_ready_fix_update),
    ;

    /** Beschriftung des Knopfes; null bei [None]. */
    val buttonText: UiText? get() = if (buttonRes == 0) null else UiText.res(buttonRes)
}

/** Die Punkte der Bereitschaft in der Reihenfolge der Liste. [shortName] steht in der Kopfzeile, wenn etwas offen ist. */
internal enum class ReadyKey(@StringRes val shortNameRes: Int) {
    Storage(R.string.launcher_ready_key_storage),
    Program(R.string.launcher_ready_key_program),
    Data(R.string.launcher_ready_key_data),
    InstallInfo(R.string.launcher_ready_key_install),
    Login(R.string.launcher_ready_key_login),
    Client(R.string.launcher_ready_key_client),
    ;

    val shortName: UiText get() = UiText.res(shortNameRes)
}

internal data class ReadyItem(val key: ReadyKey, val label: UiText, val ready: Boolean, val fix: ReadyFix = ReadyFix.None)

internal data class ReadinessInput(
    /** Die Dateien wurden schon einmal geprüft (vorher sähe alles wie "fehlt" aus). */
    val checked: Boolean,
    val programExists: Boolean,
    val dataExists: Boolean,
    val hasForever: Boolean,
    val isLegacyBeta: Boolean,
    val isWrongGame: Boolean,
    val hasStorageAccess: Boolean,
    val hasSavedLogin: Boolean,
    /** Gebaute Client-Version auf dem Gerät und im Netz; null = noch nicht abgefragt. */
    val clientLocal: String? = null,
    val clientRemote: String? = null,
    val clientOutdated: Boolean = false,
)

internal data class ReadinessSummary(
    val checked: Boolean,
    val items: List<ReadyItem>,
    val headline: UiText,
) {
    val open: List<ReadyItem> get() = items.filter { !it.ready }

    val allReady: Boolean get() = checked && open.isEmpty()

    /** Die Liste klappt von selbst nur auf, wenn etwas fehlt; sonst nur per Tipp auf die Zeile. */
    val expandedByDefault: Boolean get() = checked && open.isNotEmpty()
}

/** Kopfzeile: "Alles bereit ✓ · Client 12.0.x aktuell" oder "Offen: Anmeldung". */
internal fun readinessHeadline(checked: Boolean, items: List<ReadyItem>, clientLocal: String?): UiText {
    if (!checked) return UiText.res(R.string.launcher_pill_checking_install)
    val open = items.filter { !it.ready }
    if (open.isEmpty()) {
        return if (clientLocal.isNullOrBlank()) {
            UiText.res(R.string.launcher_ready_all_ready)
        } else {
            UiText.res(R.string.launcher_ready_all_ready_client, clientLocal)
        }
    }
    val names = UiText.join(open.take(3).map { it.key.shortName })
    val more = open.size - 3
    return if (more > 0) UiText.res(R.string.launcher_ready_open_more, names, more) else UiText.res(R.string.launcher_ready_open, names)
}

internal fun readinessSummary(input: ReadinessInput): ReadinessSummary {
    val storageOk = input.hasStorageAccess
    val installFix = if (storageOk) ReadyFix.Install else ReadyFix.None
    val items = buildList {
        add(
            ReadyItem(
                ReadyKey.Storage,
                UiText.res(if (storageOk) R.string.launcher_ready_storage_ok else R.string.launcher_ready_storage_missing),
                storageOk,
                if (storageOk) ReadyFix.None else ReadyFix.StoragePermission,
            ),
        )
        add(
            ReadyItem(
                ReadyKey.Program,
                UiText.res(if (input.programExists) R.string.launcher_ready_program_ok else R.string.launcher_ready_program_missing),
                input.programExists,
                if (input.programExists) ReadyFix.None else installFix,
            ),
        )
        add(
            ReadyItem(
                ReadyKey.Data,
                UiText.res(if (input.dataExists) R.string.launcher_ready_data_ok else R.string.launcher_ready_data_missing),
                input.dataExists,
                if (input.dataExists) ReadyFix.None else installFix,
            ),
        )
        add(
            when {
                input.isWrongGame -> ReadyItem(ReadyKey.InstallInfo, UiText.res(R.string.launcher_ready_wrong_game), false)
                input.isLegacyBeta -> ReadyItem(ReadyKey.InstallInfo, UiText.res(R.string.launcher_ready_beta), false, installFix)
                input.hasForever -> ReadyItem(ReadyKey.InstallInfo, UiText.res(R.string.launcher_ready_install_ok), true)
                else -> ReadyItem(ReadyKey.InstallInfo, UiText.res(R.string.launcher_ready_install_missing), false, installFix)
            },
        )
        add(
            ReadyItem(
                ReadyKey.Login,
                UiText.res(if (input.hasSavedLogin) R.string.launcher_ready_login_ok else R.string.launcher_ready_login_missing),
                input.hasSavedLogin,
                if (input.hasSavedLogin) ReadyFix.None else ReadyFix.Login,
            ),
        )
        if (input.clientLocal != null) {
            val remote = input.clientRemote.orEmpty()
            add(
                if (input.clientOutdated) {
                    ReadyItem(ReadyKey.Client, UiText.res(R.string.launcher_ready_client_outdated, input.clientLocal, remote), false, ReadyFix.Update)
                } else {
                    ReadyItem(ReadyKey.Client, UiText.res(R.string.launcher_ready_client_ok, input.clientLocal), true)
                },
            )
        }
    }
    // Ein Knopf je Lösung genügt: "Installieren" steht nur am ersten Punkt, der es braucht.
    val seen = mutableSetOf<ReadyFix>()
    val deduped = items.map { item ->
        if (item.fix != ReadyFix.None && !seen.add(item.fix)) item.copy(fix = ReadyFix.None) else item
    }
    return ReadinessSummary(input.checked, deduped, readinessHeadline(input.checked, deduped, input.clientLocal))
}

// --- Einstellungsseite ---

/**
 * Jede Einstellung der Seite mit ungefährer Zeilenzahl ([rows], für die Spaltenaufteilung). [nextStart]: wird erst beim nächsten
 * Spielstart gelesen. Jede kommt in genau einer [SettingsGroup] vor oder, wenn sie es auch im Spiel gibt, in [hiddenGameSettings].
 */
internal enum class LauncherSetting(val rows: Int, val nextStart: Boolean = false) {
    Region(1, nextStart = true),
    Resolution(1, nextStart = true),
    Fps(1, nextStart = true),
    Graphics(1, nextStart = true),
    GameFolder(1),
    InputMode(1, nextStart = true),

    /** Hinweiskarte der Gruppe Steuerung: der Rest der Steuerung steht im Spiel (reine Anzeige). */
    ControlHint(4),
    Voice(3),
    Icons(2),
    AutoStart(2),
    StartCheck(1),
    Screenshots(1),
    WowArt(1),
    Updates(2),
    AppVersion(1),
    ClientBuild(1),
    ErrorReport(1),

    // Nur im Spiel-Bereich "Spiel-Einstellungen hier ändern" (zugeklappt, siehe hiddenGameSettings()).
    StickWalk(1),
    StickCamera(1),
    LoginTouch(2),
    WindowHide(1),
    TouchLook(2),
    ClickWheel(10, nextStart = true),
    LayoutReset(1),
    VoiceLanguage(1),
    Cooldowns(3),
}

/** Eine Gruppe der Einstellungsseite: [key] ist der feste Schlüssel (nicht der Anzeigetext), [titleRes] die Überschrift. */
internal data class SettingsGroup(val key: String, @StringRes val titleRes: Int, val settings: List<LauncherSetting>) {
    val title: UiText get() = UiText.res(titleRes)

    val weight: Int get() = settings.sumOf { it.rows }
}

/**
 * Die Gruppen der Einstellungsseite in der Reihenfolge der Karten (nach Bedeutung). "voice" und "icons" sind die Blöcke hinter den
 * Zeilen "Öffnen" der Karte Steuerung im Spiel; "controls" ist der feste Schlüssel der Kachel "Steuerung" der Startseite.
 * Die Addons verwaltet die Kachel "Addons" der Startseite, nicht diese Seite.
 */
internal fun settingsGroups(): List<SettingsGroup> = listOf(
    SettingsGroup(
        "game", R.string.launcher_group_game,
        listOf(
            LauncherSetting.Region, LauncherSetting.Resolution, LauncherSetting.Fps, LauncherSetting.Graphics,
            LauncherSetting.GameFolder,
        ),
    ),
    SettingsGroup(
        "start", R.string.launcher_group_start,
        listOf(
            LauncherSetting.AutoStart, LauncherSetting.StartCheck, LauncherSetting.Updates,
            LauncherSetting.AppVersion, LauncherSetting.ClientBuild,
        ),
    ),
    SettingsGroup("controls", R.string.launcher_group_controls, listOf(LauncherSetting.InputMode, LauncherSetting.ControlHint)),
    SettingsGroup("voice", R.string.launcher_group_voice, listOf(LauncherSetting.Voice)),
    SettingsGroup("icons", R.string.launcher_group_icons, listOf(LauncherSetting.Icons)),
    SettingsGroup("app", R.string.launcher_group_app, listOf(LauncherSetting.Screenshots, LauncherSetting.WowArt)),
    SettingsGroup("help", R.string.launcher_group_help, listOf(LauncherSetting.ErrorReport)),
)

/**
 * Was aus der normalen Ansicht verschwunden ist, weil es das auch im Spiel gibt (Seitenmenü > Steuerung bzw. > Spiel). Es steht nur
 * ganz unten auf der Seite "Steuerung" hinter der Zeile "Spiel-Einstellungen hier ändern ..." (zu, bis man bestätigt), jede mit
 * Abzeichen "Auch im Spiel". Reihenfolge von oben nach unten.
 */
internal fun hiddenGameSettings(): List<LauncherSetting> = listOf(
    LauncherSetting.StickWalk, LauncherSetting.StickCamera, LauncherSetting.LoginTouch, LauncherSetting.WindowHide,
    LauncherSetting.TouchLook, LauncherSetting.ClickWheel, LauncherSetting.LayoutReset, LauncherSetting.VoiceLanguage,
    LauncherSetting.Cooldowns,
)

/**
 * Zustand des zugeklappten Bereichs "Spiel-Einstellungen hier ändern": Antippen der Zeile fragt erst nach, erst "Öffnen" klappt
 * ihn auf, ein weiterer Tipp klappt ihn zu. Wird nie gespeichert: bei jedem Öffnen der Seite ist er wieder zu.
 */
internal data class GameSettingsFold(val asking: Boolean = false, val open: Boolean = false) {
    fun onRowTap(): GameSettingsFold = if (open) GameSettingsFold() else copy(asking = true)

    fun onCancel(): GameSettingsFold = copy(asking = false)

    fun onConfirm(): GameSettingsFold = GameSettingsFold(asking = false, open = true)
}

/** Zeile "Client-Build" der Einstellungsseite: Version auf dem Gerät und ob eine neuere bereitsteht. */
internal fun clientBuildText(local: String?, remote: String?, outdated: Boolean): UiText = when {
    local == null -> UiText.res(R.string.launcher_client_not_checked)
    outdated -> UiText.res(R.string.launcher_client_update_available, local, remote.orEmpty())
    else -> UiText.res(R.string.launcher_client_up_to_date, local)
}

/** Nächster Wert der Stick-Empfindlichkeit für die Zeile im Seitenmenü (reihum; ein unbekannter Wert beginnt vorn). */
internal fun nextGainStep(current: Float): Float {
    val i = STICK_GAIN_STEPS.indexOf(current)
    return STICK_GAIN_STEPS[(i + 1) % STICK_GAIN_STEPS.size]
}

/** Ab dieser Breite (dp) zeigt die Einstellungsseite zwei Spalten: der aufgeklappte Fold, nicht das schmale Außendisplay. */
internal const val TWO_COLUMN_MIN_WIDTH_DP = 640
