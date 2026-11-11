package app.gamenative.ui.screen.wow.launcher

import app.gamenative.BuildConfig
import app.gamenative.R

/**
 * Alle Namen und Texte des neuen Launchers an einer Stelle. Das sind Arbeitstitel: Wortmarke, Kachelnamen, Seitentitel und
 * Hinweise lassen sich hier ersetzen, ohne eine Oberfläche anzufassen. Texte, die vom Zustand abhängen (Pille, Hero, Kachelzeilen),
 * entstehen in HomeUiState.kt und nutzen diese Werte. Die Wörter selbst stehen in `strings_launcher.xml` (values = Englisch,
 * values-de = Deutsch); hier sind es [UiText] (Ressource plus Argumente), die die Oberfläche mit `asString()` auflöst.
 */
internal object LauncherTexts {
    // --- Kopf und Fuß ---
    const val WORDMARK = "WOW FOREVER"
    val UNOFFICIAL_TAG: UiText = UiText.res(R.string.launcher_unofficial_tag)
    val UNOFFICIAL_NOTICE: UiText = UiText.res(R.string.launcher_unofficial_notice)
    val NO_GAME_FILES_NOTICE: UiText = UiText.res(R.string.launcher_no_game_files_notice)
    const val LICENSE_NAME = "GPL-3.0"
    val FOOTER_LICENSES: UiText = UiText.res(R.string.launcher_footer_licenses)
    val FOOTER_SOURCE: UiText = UiText.res(R.string.launcher_footer_source)

    /** Quellcode (GPL-3.0); die Adresse kommt als Build-Feld aus `app/build.gradle.kts` (Property `sourceUrl`). */
    val SOURCE_URL: String = BuildConfig.SOURCE_URL

    /** Herkunft für die Seite "Über" (Arbeitstitel, später ggf. ergänzen). */
    val ORIGIN_LINES: UiText = UiText.res(R.string.launcher_origin_lines)

    // --- Hero ---
    val PLAY_TITLE: UiText = UiText.res(R.string.launcher_play)
    val PLAY_BUTTON: UiText = UiText.res(R.string.launcher_play)
    val WRONG_GAME_BUTTON: UiText = UiText.res(R.string.launcher_wrong_game_button)
    val GRANT_BUTTON: UiText = UiText.res(R.string.launcher_grant_button)
    val INSTALL_BUTTON: UiText = UiText.res(R.string.launcher_install_button)
    val UPGRADE_BUTTON: UiText = UiText.res(R.string.launcher_upgrade_button)
    val FIND_FILES_BUTTON: UiText = UiText.res(R.string.launcher_find_files_button)
    val PLAY_OUTDATED_BUTTON: UiText = UiText.res(R.string.launcher_play_outdated_button)
    val CANCEL_INSTALL_BUTTON: UiText = UiText.res(R.string.launcher_cancel_install_button)

    // --- Kacheln ---
    fun tileTitle(tile: LauncherTile): UiText = when (tile) {
        LauncherTile.Touch -> UiText.res(R.string.launcher_tile_controls)
        LauncherTile.Addons -> UiText.res(R.string.launcher_tile_addons)
        LauncherTile.Settings -> UiText.res(R.string.launcher_tile_settings)
        LauncherTile.Files -> UiText.res(R.string.launcher_tile_files)
        LauncherTile.News -> UiText.res(R.string.launcher_tile_news)
        LauncherTile.Help -> UiText.res(R.string.launcher_tile_help)
    }

    /** Titel der Vollbildseite (Kopfzeile neben "Zurück"). */
    fun pageTitle(page: LauncherPage): UiText = when (page) {
        LauncherPage.Settings -> UiText.res(R.string.launcher_tile_settings)
        LauncherPage.Addons -> UiText.res(R.string.launcher_tile_addons)
        LauncherPage.Touch -> UiText.res(R.string.launcher_tile_controls)
        LauncherPage.Files -> UiText.res(R.string.launcher_tile_files)
        LauncherPage.News -> UiText.res(R.string.launcher_tile_news)
        LauncherPage.Help -> UiText.res(R.string.launcher_tile_help)
        LauncherPage.About -> UiText.res(R.string.launcher_page_about)
    }

    /** Schlüssel der Gruppe auf der Einstellungsseite, die die Touch-Kachel zeigt (siehe settingsGroups()); kein Anzeigetext. */
    const val TOUCH_SETTINGS_GROUP = "controls"

    /** Zweite Zeile der Kachel "Steuerung": der Modus, dahinter der Hinweis, dass die Feinheiten im Spiel stehen. */
    fun controlTileSubtitle(inputModeLabel: String): UiText = UiText.res(R.string.launcher_tile_controls_sub, inputModeLabel)

    // --- Gruppe Steuerung: Hinweiskarte und der zugeklappte Bereich ---
    val CONTROL_HINT_TITLE: UiText = UiText.res(R.string.launcher_control_hint_title)
    val CONTROL_HINT_WHERE: UiText = UiText.res(R.string.launcher_control_hint_where)

    /** Die Gruppen, die es im Seitenmenü unter Steuerung gibt (Liste auf der Hinweiskarte). */
    val CONTROL_HINT_GROUPS: List<UiText> = listOf(
        UiText.res(R.string.launcher_control_group_sticks),
        UiText.res(R.string.launcher_control_group_tap_hold),
        UiText.res(R.string.launcher_control_group_click_wheel),
        UiText.res(R.string.launcher_control_group_swipe),
        UiText.res(R.string.launcher_control_group_touch_elements),
    )

    val GAME_FOLD_ROW_TITLE: UiText = UiText.res(R.string.launcher_game_fold_row_title)
    val GAME_FOLD_GROUP_TITLE: UiText = UiText.res(R.string.launcher_game_fold_group_title)
    val GAME_FOLD_ROW_HINT: UiText = UiText.res(R.string.launcher_game_fold_row_hint)
    val GAME_FOLD_ASK_TITLE: UiText = UiText.res(R.string.launcher_game_fold_ask_title)
    val GAME_FOLD_ASK_TEXT: UiText = UiText.res(R.string.launcher_game_fold_ask_text)
    val GAME_FOLD_CANCEL: UiText = UiText.res(R.string.launcher_cancel)
    val GAME_FOLD_OPEN: UiText = UiText.res(R.string.launcher_open)
}
