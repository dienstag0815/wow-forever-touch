package app.gamenative.ui.screen.wow

import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.component.QuickMenuAction
import app.gamenative.ui.components.TextRef

/**
 * Gliederung des Seitenmenüs im laufenden Spiel als reine Datenstruktur: welcher Eintrag ([QuickMenuAction]) in welchem Reiter
 * ([QuickMenuPage]) unter welcher Überschrift steht. Die Zeilen selbst (Symbol, Beschriftung) baut QuickMenu.kt; hier steht nur die
 * Zuordnung und die Sichtbarkeit je Lage ([QuickMenuContext]). Die IDs bleiben, wie sie sind; nur Zuordnung und Reihenfolge ändern sich.
 *
 * Reiter mit eigener Oberfläche (Leistungs-HUD, LSFG, Bildschirmeffekte, Task-Manager) stehen nicht hier; ihre Schalter sind keine
 * Zeilen. Der Reiter [QuickMenuPage.HILFE] zeigt oben die Zeilen aus dieser Gliederung und darunter den Task-Manager.
 *
 * Gruppennamen und Beschriftungen entsprechen der Einstellungsseite des Launchers (Steuerung, Sprache, Symbole und Abklingzeiten, Hilfe).
 */
internal enum class QuickMenuPage(val tabId: Int, @StringRes val titleRes: Int) {
    /** Was man im Spiel am häufigsten braucht: Anmelden (nur im Login), Tastatur, Symbole, Sprache. */
    SPIEL(5, R.string.game_qm_page_game),

    /** Alles zur Touch-Steuerung in klappbaren Gruppen: Layout, Sticks, Tippen und Halten, Klick-Rad, Wischen, Touch-Elemente, Eingabemodi. (ID 3 = der frühere Reiter "Controller".) */
    STEUERUNG(3, R.string.game_qm_page_controls),

    /** Fehlerbericht; im Menü teilt sich der Reiter die Seite mit dem Task-Manager. (ID 4 = der frühere Reiter "Task-Manager".) */
    HILFE(4, R.string.game_qm_page_help),
}

/**
 * Eine Überschrift mit ihren Zeilen (in der Reihenfolge von oben nach unten). Leere Abschnitte gibt es nicht.
 *
 * @property groupId Kennung der Gruppe ([QuickMenuGroups]); null = feste Überschrift ohne Namen für die Merkliste
 * @property collapsible die Gruppe lässt sich zu- und aufklappen (Akkordeon: höchstens eine offen, siehe [QuickMenuAccordion]);
 *   false = immer offen mit gewöhnlicher Überschrift
 * @property note kleine Erklärung unter den Zeilen der Gruppe, nur im offenen Zustand
 */
internal data class QuickMenuSection(
    val title: TextRef,
    val ids: List<Int>,
    val groupId: String? = null,
    val collapsible: Boolean = false,
    val note: TextRef? = null,
)

/** Kennungen der Gruppen im Reiter Steuerung. Sie stehen in den Einstellungen (letzte offene Gruppe), also nicht umbenennen. */
internal object QuickMenuGroups {
    const val LAYOUT = "layout"
    const val STICKS = "sticks"
    const val TAP_HOLD = "tippen"
    const val CLICK_WHEEL = "klickrad"
    const val SWIPE = "wischen"
    const val ELEMENTS = "elemente"
    const val MODES = "modi"
    const val MORE = "mehr"
}

/**
 * Akkordeon der Steuerungs-Gruppen als reine Logik: höchstens eine klappbare Gruppe ist offen, die zuletzt offene wird gemerkt.
 * Gespeichert wird die Kennung; die leere Zeichenfolge heißt "bewusst alles zu", fehlender Eintrag heißt "noch nie gewählt".
 */
internal object QuickMenuAccordion {
    /** Beim ersten Öffnen ist diese Gruppe offen (die häufigste Einstellung). */
    const val DEFAULT_OPEN = QuickMenuGroups.STICKS

    /** Gespeicherter Wert für "alles zu". */
    const val NONE = ""

    /** Die offene Gruppe aus dem gespeicherten Wert [saved]; eine Gruppe, die es in [sections] nicht (mehr) gibt, zählt als zu. */
    fun resolve(saved: String?, sections: List<QuickMenuSection>): String? {
        val ids = sections.filter { it.collapsible }.mapNotNull { it.groupId }
        return when {
            saved == null -> DEFAULT_OPEN.takeIf { it in ids }
            saved in ids -> saved
            else -> null
        }
    }

    /** Antippen des Kopfes von [tapped]: die offene Gruppe klappt zu, eine andere öffnet sich und schließt die bisher offene. */
    fun toggle(open: String?, tapped: String): String? = if (open == tapped) null else tapped

    /** Was in die Einstellungen geschrieben wird. */
    fun toSaved(open: String?): String = open ?: NONE
}

/**
 * Lage, von der die Sichtbarkeit einzelner Zeilen abhängt.
 *
 * @property inLogin das Spiel steht im Login oder in der Charakterauswahl, nicht in der Spielwelt ([LoginTouchGate.inWorld] ist falsch)
 * @property hasSavedLogin Anmeldedaten sind gespeichert (dann auch "Nur Passwort" und "Nur E-Mail")
 * @property hasLoginNote die Zeile zur letzten Anmeldeaktion existiert
 * @property hasReportNote die Zeile zum Fehlerbericht existiert
 * @property hasPhysicalController ein physischer Controller ist verbunden
 * @property hasContainer es gibt einen Container (Voraussetzung für Gyro-Zielen)
 * @property hasTouchLayouts die Steuerung ist ein Touch-Modus (nur dann gibt es wählbare Layouts, siehe [TouchLayouts])
 */
internal data class QuickMenuContext(
    val inLogin: Boolean = true,
    val hasSavedLogin: Boolean = false,
    val hasLoginNote: Boolean = false,
    val hasReportNote: Boolean = false,
    val hasPhysicalController: Boolean = false,
    val hasContainer: Boolean = true,
    val hasTouchLayouts: Boolean = true,
)

/** Hinweis unter den Sticks: die Werte gelten für alle Sticks, die Belegung steht am Stick selbst (Touch-Editor). */
internal val STICK_NOTE = TextRef(R.string.game_qm_stick_note)

/** Rein: die Abschnitte eines Reiters in dieser Lage. Abschnitte ohne Zeile entfallen. */
internal fun quickMenuSections(page: QuickMenuPage, ctx: QuickMenuContext): List<QuickMenuSection> {
    val sections = when (page) {
        QuickMenuPage.SPIEL -> listOf(
            // Anmeldung: nur im Login. In der Spielwelt wären Buchstaben und Tab Tastenbelegungen des Spiels.
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_login),
                buildList {
                    if (ctx.inLogin) {
                        add(QuickMenuAction.LOGIN_FULL)
                        if (ctx.hasSavedLogin) {
                            add(QuickMenuAction.LOGIN_PASSWORD)
                            add(QuickMenuAction.LOGIN_EMAIL)
                        }
                        if (ctx.hasLoginNote) add(QuickMenuAction.LOGIN_NOTE)
                    }
                },
            ),
            QuickMenuSection(TextRef(R.string.game_qm_sec_input), listOf(QuickMenuAction.KEYBOARD)),
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_icons),
                listOf(QuickMenuAction.COOLDOWNS, QuickMenuAction.COOLDOWN_GCD),
            ),
            QuickMenuSection(TextRef(R.string.game_qm_sec_voice), listOf(QuickMenuAction.VOICE_LANGUAGE)),
        )
        QuickMenuPage.STEUERUNG -> listOf(
            // Layout: immer offen, oben (aktives Layout wählen, bearbeiten, als neues speichern). Der Rest ist ein Akkordeon.
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_layout),
                buildList {
                    if (ctx.hasTouchLayouts) add(QuickMenuAction.LAYOUT_CHOOSE)
                    add(QuickMenuAction.EDIT_CONTROLS)
                    if (ctx.hasTouchLayouts) add(QuickMenuAction.LAYOUT_SAVE_AS)
                    add(QuickMenuAction.INPUT_CONTROLS)
                },
                groupId = QuickMenuGroups.LAYOUT,
            ),
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_sticks),
                listOf(QuickMenuAction.STICK_WALK, QuickMenuAction.STICK_CAMERA),
                groupId = QuickMenuGroups.STICKS,
                collapsible = true,
                note = STICK_NOTE,
            ),
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_taphold),
                listOf(
                    QuickMenuAction.TOUCHSCREEN_MODE,
                    QuickMenuAction.TWO_FINGER_TAP,
                    QuickMenuAction.HOLD_TIME,
                    QuickMenuAction.TAP_WINDOW,
                    QuickMenuAction.CONTEXT_MENU,
                    QuickMenuAction.TAP_VIEW_ITEMS,
                ),
                groupId = QuickMenuGroups.TAP_HOLD,
                collapsible = true,
            ),
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_wheel),
                listOf(
                    QuickMenuAction.CLICK_WHEEL,
                    QuickMenuAction.WHEEL_CENTER,
                    QuickMenuAction.WHEEL_UP,
                    QuickMenuAction.WHEEL_RIGHT,
                    QuickMenuAction.WHEEL_DOWN,
                    QuickMenuAction.WHEEL_LEFT,
                ),
                groupId = QuickMenuGroups.CLICK_WHEEL,
                collapsible = true,
            ),
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_swipe),
                listOf(QuickMenuAction.SWIPE_SCROLL, QuickMenuAction.SWIPE_SPEED),
                groupId = QuickMenuGroups.SWIPE,
                collapsible = true,
            ),
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_elements),
                listOf(
                    QuickMenuAction.TOUCH_HIDE_LOGIN,
                    QuickMenuAction.TOUCH_SHOW_NOW,
                    QuickMenuAction.TOUCH_HIDE_WINDOWS,
                    QuickMenuAction.TOUCH_STYLE,
                    QuickMenuAction.TOUCH_CONTRAST,
                ),
                groupId = QuickMenuGroups.ELEMENTS,
                collapsible = true,
            ),
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_modes),
                buildList {
                    add(QuickMenuAction.SHOOTER_MODE)
                    if (ctx.hasContainer) add(QuickMenuAction.GYRO)
                    add(QuickMenuAction.DISABLE_MOUSE)
                    add(QuickMenuAction.RADIAL_MENU)
                },
                groupId = QuickMenuGroups.MODES,
                collapsible = true,
            ),
            // Selten gebraucht: Layout auf Standard zurücksetzen, Controller-Belegung.
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_more),
                buildList {
                    add(QuickMenuAction.RESET_LAYOUT)
                    if (ctx.hasPhysicalController) add(QuickMenuAction.EDIT_PHYSICAL_CONTROLLER)
                },
                groupId = QuickMenuGroups.MORE,
                collapsible = true,
            ),
        )
        QuickMenuPage.HILFE -> listOf(
            QuickMenuSection(
                TextRef(R.string.game_qm_sec_help),
                buildList {
                    add(QuickMenuAction.REPORT)
                    if (ctx.hasReportNote) add(QuickMenuAction.REPORT_NOTE)
                },
            ),
            // Selten gebraucht, darum hier statt im Reiter Spiel.
            QuickMenuSection(TextRef(R.string.game_qm_sec_more), listOf(QuickMenuAction.CHECK_ICONS, QuickMenuAction.ADDON_OPTIONS)),
        )
    }
    return sections.filter { it.ids.isNotEmpty() }
}

/** Zeilen, die nicht in dieser Gliederung stehen: das Ende-Symbol der Leiste und der Schalter im Leistungs-HUD-Reiter. */
internal val QUICK_MENU_OUTSIDE_LAYOUT: Set<Int> = setOf(QuickMenuAction.EXIT_GAME, QuickMenuAction.PERFORMANCE_HUD)

/** Die Anmelde-Zeilen: nur im Login-Kontext sichtbar. */
internal val QUICK_MENU_LOGIN_IDS: Set<Int> = setOf(
    QuickMenuAction.LOGIN_FULL,
    QuickMenuAction.LOGIN_PASSWORD,
    QuickMenuAction.LOGIN_EMAIL,
    QuickMenuAction.LOGIN_NOTE,
)
