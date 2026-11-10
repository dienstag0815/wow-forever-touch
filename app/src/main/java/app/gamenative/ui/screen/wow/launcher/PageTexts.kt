package app.gamenative.ui.screen.wow.launcher

import app.gamenative.R

/*
 * Kurze Texte und kleine Rechnungen der Übersichten (rein, ohne Android, per JUnit getestet). Die langen Texte bleiben, wo sie
 * waren (LauncherTexts, LauncherSettingScope, AddonText); die Übersicht zeigt nur die Kurzfassung, die Detailansicht den ganzen Text.
 * Alle Texte sind [UiText] (Ressource plus Argumente); die Wörter stehen in strings_launcher.xml.
 */
internal object PageTexts {
    /** Kurzfassung der Zeile "Client-Build" für die Karte (rechts in Gold, eine Zeile). */
    fun clientBuildValue(local: String?, remote: String?, outdated: Boolean): UiText = when {
        local.isNullOrBlank() -> UiText.res(R.string.launcher_client_unchecked)
        outdated -> UiText.raw("$local → ${remote.orEmpty()}")
        else -> UiText.raw(local.orEmpty())
    }

    /** Hinweis auf einer Karte der Seite "Steuerung": wo im Spiel die Gruppe [group] zu finden ist. */
    fun controlHintLine(group: UiText): UiText = UiText.res(R.string.launcher_control_hint_line, group)

    /** Kurzer Hinweis unter dem Steuerungsmodus in den Einstellungen. */
    val CONTROL_SHORT_HINT: UiText = UiText.res(R.string.launcher_control_short_hint)

    /** Kurzer Hinweis unter dem Modus auf der Seite "Steuerung". */
    val CONTROL_MODE_HINT: UiText = UiText.res(R.string.launcher_control_mode_hint)

    /** Titel der Karte, die auf der Seite "Steuerung" den zugeklappten Bereich "Spiel-Einstellungen hier ändern ..." öffnet. */
    val GAME_FOLD_CARD_TITLE: UiText = UiText.res(R.string.launcher_game_fold_card_title)

    /** Kurzfassung des Fehlerberichts (der lange Text steht in der Detailansicht). */
    val REPORT_SHORT: UiText = UiText.res(R.string.launcher_report_short)

    /** Zeile unter dem Spielordner auf der Seite "Spieldateien". */
    val FOLDER_HINT: UiText = UiText.res(R.string.launcher_folder_hint)

    /** Kleine Zählzeile einer Version: "11.11.2026 · 7 Punkte" (ohne Datum nur die Zahl). */
    fun versionLine(date: String, items: Int): UiText {
        val count = UiText.plural(R.plurals.launcher_points, items)
        return if (date.isBlank()) count else UiText.res(R.string.launcher_join_dot, date, count)
    }

    /** Titel der Karte mit der neuesten Version: "2.3.3-fold8.25 · 11.11.2026" (ohne Datum nur die Version). */
    fun latestTitle(version: String, date: String): String = if (date.isBlank()) version else "$version · $date"

    /** Zeile "+ 3 weitere" unter den gezeigten Punkten einer Spalte; null, wenn alle gezeigt sind. */
    fun moreLine(total: Int, shown: Int): UiText? = if (total > shown) UiText.res(R.string.launcher_more_items, total - shown) else null

    /** Zeile der Karte "Alle Änderungen". */
    fun allVersionsLine(count: Int): UiText = UiText.plural(R.plurals.launcher_versions_newest_first, count)

    /** Was die Hinweisleiste der Addon-Seite zeigt: [text] und ob es ein Fehler ist (rot). */
    data class AddonsNote(val text: UiText, val error: Boolean)

    /**
     * Die Hinweisleiste oben auf der Addon-Seite zeigt immer genau eine Meldung, in dieser Rangfolge: Fortschritt oder Ergebnis der
     * Katalog-Aktion, laufende oder letzte Aktion bei den eigenen Addons, ungültiger Katalog, Spiel läuft, Spiel fehlt, sonst
     * die Einleitung. Alles steht in der Detailansicht der Leiste vollständig.
     */
    fun addonsNote(
        progress: UiText?,
        message: UiText?,
        messageError: Boolean,
        customBusy: UiText?,
        customMessage: UiText?,
        customError: Boolean,
        catalogProblem: UiText?,
        gameBusy: Boolean,
        noGame: Boolean,
        busyHint: UiText,
        noGameHint: UiText,
        intro: UiText,
    ): AddonsNote = when {
        progress != null -> AddonsNote(progress, false)
        message != null -> AddonsNote(message, messageError)
        customBusy != null -> AddonsNote(customBusy, false)
        customMessage != null -> AddonsNote(customMessage, customError)
        catalogProblem != null -> AddonsNote(UiText.res(R.string.launcher_catalog_invalid, catalogProblem), true)
        gameBusy -> AddonsNote(busyHint, false)
        noGame -> AddonsNote(noGameHint, false)
        else -> AddonsNote(intro, false)
    }
}
