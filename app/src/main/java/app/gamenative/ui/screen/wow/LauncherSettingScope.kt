package app.gamenative.ui.screen.wow

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText

/*
 * Wo sich eine Einstellung ändern lässt: nur im Launcher oder auch im Spiel (und wo dort). Reine Zuordnung ohne Oberfläche, damit
 * sie per JUnit prüfbar ist; die Abzeichen auf der Einstellungsseite und die Liste auf der Hilfeseite lesen nur von hier.
 *
 * Grundlage ist eine Code-Analyse der Einstellungen von Launcher und Spiel (Stand fold8.20). Wichtig dafür: Region, Auflösung,
 * Bildrate und Grafik-Voreinstellung schreibt der Start bei jedem Spielstart neu in die Config.wtf (ensureGameConfig); was man im
 * WoW-Menü ändert, gilt deshalb nur bis zum nächsten Start.
 */

internal enum class ScopeKind {
    /** Nur im Launcher einstellbar. */
    OnlyHere,

    /** Im Spiel gibt es einen Teil davon; die Notiz sagt, welchen. */
    Partly,

    /** Auch im Spiel änderbar. */
    AlsoInGame,

    /** Reine Anzeige (Version, Build), nichts einzustellen: kein Abzeichen. */
    InfoOnly,
}

internal data class SettingScope(
    val kind: ScopeKind,
    /** Ort im Spiel, z. B. "Seitenmenü > Steuerung"; bei [ScopeKind.AlsoInGame] und [ScopeKind.Partly] Pflicht. */
    val place: UiText? = null,
    /** Gilt erst beim nächsten Spielstart. */
    val nextStart: Boolean = false,
    /** Eine kurze Zeile mit der Ausnahme oder dem Hinweis dazu. */
    val note: UiText? = null,
) {
    /** Text des Abzeichens; null bei reiner Anzeige. */
    val badgeText: UiText?
        get() = when (kind) {
            ScopeKind.OnlyHere -> if (nextStart) BADGE_HERE_NEXT else BADGE_HERE
            ScopeKind.AlsoInGame -> UiText.res(R.string.launcher_badge_game, place ?: UiText.raw(""))
            ScopeKind.Partly -> UiText.res(R.string.launcher_badge_partly, place ?: UiText.raw(""))
            ScopeKind.InfoOnly -> null
        }

    /** Ob die Einstellung auch im Spiel erreichbar ist (ganz oder teilweise): bestimmt die Farbe des Abzeichens. */
    val inGame: Boolean get() = kind == ScopeKind.AlsoInGame || kind == ScopeKind.Partly
}

internal val BADGE_HERE: UiText = UiText.res(R.string.launcher_badge_here)
internal val BADGE_HERE_NEXT: UiText = UiText.res(R.string.launcher_badge_here_next)

/** Erklärzeile oben auf der Einstellungsseite. */
internal val SETTINGS_SCOPE_HINT: UiText = UiText.res(R.string.launcher_scope_hint)

private val SIDE_CONTROL: UiText = UiText.res(R.string.launcher_place_controls)
private val SIDE_GAME: UiText = UiText.res(R.string.launcher_place_game)
private val SIDE_HELP: UiText = UiText.res(R.string.launcher_place_help)

private val onlyHereNext = SettingScope(ScopeKind.OnlyHere, nextStart = true)
private val onlyHere = SettingScope(ScopeKind.OnlyHere)

/** Die Zuordnung jeder Einstellung der Seite; das `when` ist vollständig, eine neue Einstellung ohne Eintrag kompiliert nicht. */
internal fun settingScope(setting: LauncherSetting): SettingScope = when (setting) {
    LauncherSetting.Region -> onlyHereNext
    LauncherSetting.Resolution -> onlyHereNext
    LauncherSetting.Fps -> onlyHereNext.copy(note = UiText.res(R.string.launcher_note_fps))
    LauncherSetting.Graphics -> onlyHereNext.copy(note = UiText.res(R.string.launcher_note_graphics))
    LauncherSetting.GameFolder -> onlyHereNext
    LauncherSetting.InputMode -> onlyHereNext.copy(note = UiText.res(R.string.launcher_note_input))
    LauncherSetting.ControlHint -> SettingScope(ScopeKind.InfoOnly)
    LauncherSetting.StickWalk -> SettingScope(ScopeKind.AlsoInGame, SIDE_CONTROL)
    LauncherSetting.StickCamera -> SettingScope(ScopeKind.AlsoInGame, SIDE_CONTROL)
    LauncherSetting.LoginTouch -> SettingScope(ScopeKind.AlsoInGame, SIDE_CONTROL)
    LauncherSetting.WindowHide -> SettingScope(ScopeKind.AlsoInGame, SIDE_CONTROL)
    LauncherSetting.TouchLook -> SettingScope(ScopeKind.AlsoInGame, SIDE_CONTROL)
    LauncherSetting.ClickWheel -> SettingScope(
        ScopeKind.AlsoInGame,
        SIDE_CONTROL,
        note = UiText.res(R.string.launcher_note_click_wheel),
    )
    LauncherSetting.LayoutReset -> SettingScope(ScopeKind.AlsoInGame, SIDE_CONTROL, note = UiText.res(R.string.launcher_note_layout_reset))
    LauncherSetting.Voice -> onlyHere.copy(note = UiText.res(R.string.launcher_note_voice))
    LauncherSetting.VoiceLanguage -> SettingScope(ScopeKind.AlsoInGame, SIDE_GAME)
    LauncherSetting.Icons -> onlyHere.copy(note = UiText.res(R.string.launcher_note_icons))
    LauncherSetting.Cooldowns -> SettingScope(ScopeKind.AlsoInGame, SIDE_GAME, note = UiText.res(R.string.launcher_note_cooldowns))
    LauncherSetting.AutoStart -> onlyHere
    LauncherSetting.StartCheck -> onlyHere
    LauncherSetting.Screenshots -> onlyHere
    LauncherSetting.WowArt -> onlyHere
    LauncherSetting.Updates -> onlyHere
    LauncherSetting.AppVersion -> SettingScope(ScopeKind.InfoOnly)
    LauncherSetting.ClientBuild -> SettingScope(ScopeKind.InfoOnly)
    LauncherSetting.ErrorReport -> SettingScope(ScopeKind.AlsoInGame, SIDE_HELP)
}

/** Ein Abschnitt der Hilfe-Liste "Was kann ich im Spiel ändern?": Ort und was es dort nur gibt. */
internal data class GameOnlyEntry(val title: UiText, val text: UiText)

/** Das, was es nur im Spiel gibt (nicht im Launcher). */
internal fun gameOnlyEntries(): List<GameOnlyEntry> = listOf(
    GameOnlyEntry(UiText.res(R.string.launcher_game_only_side_title), UiText.res(R.string.launcher_game_only_side_text)),
    GameOnlyEntry(UiText.res(R.string.launcher_game_only_editor_title), UiText.res(R.string.launcher_game_only_editor_text)),
    GameOnlyEntry(UiText.res(R.string.launcher_game_only_tf_title), UiText.res(R.string.launcher_game_only_tf_text)),
    GameOnlyEntry(UiText.res(R.string.launcher_game_only_wow_title), UiText.res(R.string.launcher_game_only_wow_text)),
)

/** Überschrift des Abschnitts auf der Hilfeseite. */
internal val GAME_ONLY_TITLE: UiText = UiText.res(R.string.launcher_game_only_title)
internal val GAME_ONLY_INTRO: UiText = UiText.res(R.string.launcher_game_only_intro)
