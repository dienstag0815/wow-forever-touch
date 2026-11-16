package app.gamenative.ui.screen.wow.launcher

/*
 * Die Kartenlisten der Unterseiten als reine Zählung (ohne Android, per JUnit getestet): welche Karten eine Seite in der Übersicht
 * hat, wie viele Zeilen und Textzeilen jede zeigt und wie viele Spalten sie belegt. Die Seiten bauen ihre Karten mit genau diesen
 * Größen (sizeOf), das Raster (PageGrid.kt) rechnet damit, ob die Seite ohne Scrollen passt. Wer eine Zeile in eine Karte setzt,
 * ändert hier die Zahl mit.
 */

/** Eine Karte der Übersicht: [id] ist der Schlüssel (stabil, auch für den Zustand in der Karte), [size] ihre Größe fürs Raster. */
internal data class CardPlan(val id: String, val size: CardSize)

internal fun List<CardPlan>.sizes(): List<CardSize> = map { it.size }

/** Größe der Karte [id]; ein unbekannter Schlüssel ist ein Programmierfehler. */
internal fun List<CardPlan>.sizeOf(id: String): CardSize =
    firstOrNull { it.id == id }?.size ?: error("Keine Karte mit dem Schlüssel $id")

private fun plan(
    id: String,
    titled: Boolean = true,
    rows: Int = 0,
    lines: Int = 0,
    extraDp: Float = 0f,
    span: Int = 1,
    flex: Boolean = true,
): CardPlan = CardPlan(id, CardSize(cardHeightDp(titled, rows, lines, extraDp), span, flex))

// --- Einstellungen ---

/**
 * Einstellungen in zwei Teilen. "Einfach" (immer sichtbar): die Karte Spiel (Region, Auflösung, Bildrate, Grafik, Spielordner) und
 * die Karte "easystart" (Autostart, Countdown, Updates automatisch, Nach Updates suchen, Update-Quelle). Darunter die Zeile
 * "Erweitert" über die ganze Breite. Nur wenn sie offen ist ([advancedOpen], bei jedem Öffnen der Seite zu), kommen dazu: Start
 * (Startprüfung, Fußzeile mit Version und Client-Build), Steuerung im Spiel (Modus, zwei Zeilen "Öffnen" für Sprachsteuerung sowie
 * Symbole und Abklingzeiten, ein Hinweis auf das Seitenmenü), App (Sprache, zwei Bildschalter), Hilfe (Fehlerbericht mit kurzem
 * Text) und die Karte "gamefold" (Spiel-Einstellungen hinter der Rückfrage). Nur der geschlossene Teil muss ohne Scrollen auf den
 * aufgeklappten Fold passen; der offene darf scrollen.
 */
internal fun settingsPlan(advancedOpen: Boolean = false): List<CardPlan> = buildList {
    add(plan("game", rows = 5))
    add(plan("easystart", rows = 5))
    add(plan("advanced", titled = false, rows = 1, lines = 1, span = SPAN_ALL))
    if (advancedOpen) {
        add(plan("start", rows = 1, lines = 2))
        add(plan("control", rows = 3, lines = 2))
        add(plan("app", rows = 4))
        add(plan("help", rows = 1, lines = 2))
        add(plan("gamefold", lines = 4))
    }
}

// --- Steuerung ---

/** Textzeilen der Hinweis-Karten je Gruppe ("Im Spiel › Seitenmenü › Steuerung › Tippen und Halten" bricht schmal um). */
internal const val CONTROL_HINT_LINES = 3

/** Steuerung: Modus mit Hinweis, eine Hinweis-Karte je Gruppe im Seitenmenü ([groupCount]) und die Karte "Spiel-Einstellungen". */
internal fun controlPlan(groupCount: Int): List<CardPlan> =
    listOf(plan("mode", rows = 1, lines = 2)) +
        (0 until groupCount).map { plan("hint$it", lines = CONTROL_HINT_LINES) } +
        plan("gamefold", lines = 4)

// --- Addons ---

/**
 * Addons: oben eine Hinweisleiste über die ganze Breite (drei Zeilen, ohne Titel), dann je kuratiertem Addon eine Karte
 * (Zustand, Knopf, Schalter Aktiv) und die Karte "Eigene Addons" (Link, Datei, Anzahl oder Abbrechen).
 */
internal fun addonsPlan(entryCount: Int): List<CardPlan> =
    listOf(plan("note", titled = false, lines = 3, span = SPAN_ALL)) +
        (0 until entryCount).map { plan("addon$it", rows = 3) } +
        plan("custom", rows = 3)

// --- Spieldateien ---

/** Welche Hinweiskarte oben steht; es ist höchstens eine (Zugriff fehlt, Installation nötig oder Spiel-Update nötig). */
internal enum class FilesAlert { None, Grant, Install, Update }

/** Hinweiskarte: Titel, drei Textzeilen über die ganze Breite und ein Knopf. */
internal fun filesPlan(alert: FilesAlert): List<CardPlan> =
    (if (alert == FilesAlert.None) emptyList<CardPlan>() else listOf(plan("alert", lines = 3, extraDp = CARD_BUTTON_DP, span = SPAN_ALL))) +
        listOf(
            plan("state", rows = 2, lines = 2),
            plan("folder", rows = 1, lines = 4),
            plan("login", rows = 4, lines = 3),
        )

// --- Neuigkeiten ---

/**
 * Aufbau der großen Karte mit der neuesten Version (drei Spalten nebeneinander): Kopf der Spalte, zwei Punkte zu je drei Zeilen und
 * die Zeile "+ n weitere", dazwischen je [NEWS_ITEM_GAP_DP] Abstand (vier Teile, drei Abstände).
 */
internal const val NEWS_COLUMN_HEAD_DP = 24f
internal const val NEWS_ITEM_LINES = 3
internal const val NEWS_ITEMS_SHOWN = 2
internal const val NEWS_ITEM_GAP_DP = 4f
internal const val NEWS_LATEST_EXTRA_DP =
    NEWS_COLUMN_HEAD_DP + NEWS_ITEMS_SHOWN * NEWS_ITEM_LINES * CARD_TEXT_LINE_DP + (NEWS_ITEMS_SHOWN + 1) * NEWS_ITEM_GAP_DP + CARD_TEXT_LINE_DP

/** Grenzen für die Punkte der neuesten Version, wenn die Karte mehr Höhe hat als die Mindestgröße: Zeilen je Punkt, Punkte je Spalte. */
internal const val NEWS_MIN_LINES = 3
internal const val NEWS_MAX_LINES = 6
internal const val NEWS_ITEMS_MAX = 6

/** Wie viele Punkte je Spalte der neuesten Version stehen und wie viele Zeilen jeder Punkt hat. */
internal data class NewsColumnLayout(val items: Int, val lines: Int)

/**
 * Aufbau einer Spalte der neuesten Version für eine Kartenhöhe von [availableDp] (unter dem Titel der Karte): Kopf, [NewsColumnLayout.items]
 * Punkte zu je [NewsColumnLayout.lines] Zeilen, die Zeile "+ n weitere" und dazwischen je [NEWS_ITEM_GAP_DP] Abstand. Es bleibt
 * bei zwei Punkten, solange die Zeilen je Punkt dabei höchstens [NEWS_MAX_LINES] werden; reicht der Platz dafür nicht, steht ein
 * Punkt, und ist noch mehr Platz da, kommen weitere Punkte dazu (höchstens [NEWS_ITEMS_MAX]). [fontScale] vergrößert die Zeilenhöhe.
 * Ohne brauchbare Höhe gilt die Mindestgröße (zwei Punkte zu drei Zeilen).
 */
internal fun newsColumnLayout(availableDp: Float, fontScale: Float = 1f): NewsColumnLayout {
    val fallback = NewsColumnLayout(NEWS_ITEMS_SHOWN, NEWS_ITEM_LINES)
    if (availableDp.isNaN() || availableDp.isInfinite() || availableDp <= 0f) return fallback
    val scale = if (fontScale.isNaN() || fontScale < 1f) 1f else fontScale
    val line = CARD_TEXT_LINE_DP * scale
    fun lines(items: Int): Int {
        val free = availableDp - NEWS_FIT_MARGIN_DP - NEWS_COLUMN_HEAD_DP - line - (items + 1) * NEWS_ITEM_GAP_DP
        return kotlin.math.floor(free / (items * line)).toInt()
    }
    var items = NEWS_ITEMS_SHOWN
    if (lines(items) < NEWS_MIN_LINES) items = 1
    while (lines(items) > NEWS_MAX_LINES && items < NEWS_ITEMS_MAX) items++
    return NewsColumnLayout(items, lines(items).coerceIn(1, NEWS_MAX_LINES))
}

/** Sicherheitsabstand der Höhenrechnung in [newsColumnLayout], damit die letzte Zeile nicht am Kartenrand abgeschnitten wird. */
internal const val NEWS_FIT_MARGIN_DP = 4f

/** So viele Vorgänger der neuesten Version stehen kurz daneben. */
internal const val NEWS_PREVIOUS_SHOWN = 2

/**
 * Neuigkeiten: wenn ein Update bereitsteht die Karte "Update" (zwei Spalten, Text und Knopf), wenn "Neu in ..." offen ist die Karte
 * "Neu dabei", dann die neueste Version groß über die ganze Breite, bis zu zwei Vorgänger kurz und "Alle Änderungen".
 * [versions]: Zahl der Versionen in der Änderungsliste; 0 = keine Liste, dann steht nur eine Zeile dazu.
 */
internal fun newsPlan(whatsNew: Boolean, offer: Boolean, versions: Int): List<CardPlan> = buildList {
    if (offer) add(plan("offer", lines = 3, extraDp = CARD_BUTTON_DP, span = 2, flex = false))
    if (whatsNew) add(plan("whatsnew", rows = 1, flex = false))
    if (versions <= 0) {
        add(plan("empty", titled = false, lines = 1, span = SPAN_ALL))
    } else {
        add(plan("latest", extraDp = NEWS_LATEST_EXTRA_DP, span = SPAN_ALL))
        for (i in 0 until minOf(NEWS_PREVIOUS_SHOWN, versions - 1)) add(plan("prev$i", lines = 3, flex = false))
        add(plan("all", lines = 1, flex = false))
    }
}

// --- Hilfe ---

/** Bereitschaft: Kopfzeile mit Trennlinie (49 dp, der Knopf "Neu prüfen" ist 48 dp hoch) und bis zu sechs Punkte zu je 40 dp. */
internal const val READY_HEAD_DP = 49f
internal const val READY_ITEM_DP = 40f
internal const val READY_ITEMS_MAX = 6

/**
 * Hilfe: Bereitschaft über zwei Spalten, die letzte Meldung (nur wenn es eine gibt), Fehlerbericht, "Im Spiel ändern" und Launcher. Die
 * Karte Fehlerbericht hat unter dem Kurztext eine Zeile zur Windows-Kennung (Kurzkennung und Datum, bei einem misslungenen Speichern die
 * Warnung); mehr als diese eine Zeile verträgt die Seite hochkant mit Meldung nicht (15 dp Luft, PageGridTest). Wechsel, Verlauf und
 * "Verlauf kopieren" stehen in der Detailansicht der Karte.
 */
/** [labEntry]: der freigeschaltete Eintrag "Labor" steht als dritte Zeile in der Karte Launcher; die Prüfzeile darunter hat dann eine Textzeile statt drei (sonst passt die Seite innen nicht mehr ohne Scrollen). */
internal fun helpPlan(hasError: Boolean, labEntry: Boolean = false): List<CardPlan> = buildList {
    add(plan("ready", titled = false, extraDp = READY_HEAD_DP + READY_ITEMS_MAX * READY_ITEM_DP, span = 2))
    if (hasError) add(plan("error", lines = 6))
    add(plan("report", rows = 1, lines = 4))
    add(plan("gameonly", lines = 3))
    add(plan("launcher", rows = if (labEntry) 3 else 2, lines = if (labEntry) 1 else 3))
}

// --- Über ---

/** Über: Name mit Version und Hinweisen über zwei Spalten, Lizenz, Schrift, Bilder und Lizenzhinweise. */
internal fun aboutPlan(): List<CardPlan> = listOf(
    plan("app", rows = 1, lines = 4, span = 2),
    plan("license", rows = 1, lines = 2),
    plan("font", lines = 2),
    plan("images", lines = 3),
    plan("notices", lines = 2),
)
