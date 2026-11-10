package app.gamenative.ui.screen.wow.launcher

import app.gamenative.ui.screen.wow.TWO_COLUMN_MIN_WIDTH_DP

/*
 * Raster der Unterseiten als reine Rechnung (ohne Android, per JUnit getestet). Jede Unterseite ist eine Übersicht aus Karten.
 * Aufgeklappt (Innendisplay) sollen alle Karten ohne Scrollen auf den Schirm passen: quer drei Spalten, hoch zwei. Zugeklappt
 * (Außendisplay) steht eine Spalte, die scrollen darf. Jede Karte kennt ihre Höhe aus denselben Konstanten, mit denen PageCards.kt
 * die Zeilen setzt (Zeilenhöhe, Textzeilenhöhe, Titelzeile, Innenabstand); PagePlans.kt zählt je Seite, was in der Übersicht steht.
 */

// --- Maße der Seite (dp) ---

/** Rand links und rechts neben dem Raster. */
internal const val PAGE_SIDE_DP = 16f

/** Abstand zwischen den Karten, waagerecht wie senkrecht. */
internal const val PAGE_GAP_DP = 12f

/** Kopfzeile mit Zurück-Pfeil (Knopf 48 dp plus 4 dp Abstand oben) und darunter der Trenner. */
internal const val PAGE_HEADER_DP = 52f
internal const val PAGE_DIVIDER_DP = 14f

/** Abstand zwischen Trenner und erster Kartenreihe sowie unter der letzten. */
internal const val PAGE_BODY_TOP_DP = 6f
internal const val PAGE_BODY_BOTTOM_DP = 12f

/** Alles, was von der Seitenhöhe nicht dem Raster gehört. */
internal const val PAGE_CHROME_DP = PAGE_HEADER_DP + PAGE_DIVIDER_DP + PAGE_BODY_TOP_DP + PAGE_BODY_BOTTOM_DP

/** Zugeklappt (eine Spalte) bleibt die Spalte so schmal wie vorher. */
internal const val PAGE_MAX_WIDTH_ONE_COLUMN_DP = 720f

/** Ab dieser Breite stehen drei Spalten (nur im Querformat); schmaler, aber noch aufgeklappt, sind es zwei. */
internal const val THREE_COLUMN_MIN_WIDTH_DP = 780f

/** So viele Spalten hat das Raster höchstens; eine Karte mit [CardSize.span] = [SPAN_ALL] füllt eine ganze Zeile. */
internal const val SPAN_ALL = 3

// --- Maße einer Karte (dp) ---

internal const val CARD_PAD_H_DP = 14f
internal const val CARD_PAD_V_DP = 12f

/** Titelzeile der Karte (Cinzel 16 sp, Zeilenhöhe 22 sp) samt Luft darunter. */
internal const val CARD_TITLE_DP = 28f

/** Eine Zeile mit Wert oder Schalter; Titel dürfen darin zweizeilig sein (16 sp, Zeilenhöhe 20 sp). */
internal const val CARD_ROW_DP = 48f

/** Eine Textzeile (14 sp, Zeilenhöhe 20 sp). */
internal const val CARD_TEXT_LINE_DP = 20f

/** Höhe eines Knopfes auf der Karte (PagePrimaryButton, 56 dp) samt 8 dp Abstand davor. */
internal const val CARD_BUTTON_DP = 64f

/** Höhe einer Karte aus Titelzeile, [rows] Zeilen, [textLines] Textzeilen und [extraDp] für Besonderes. */
internal fun cardHeightDp(titled: Boolean = true, rows: Int = 0, textLines: Int = 0, extraDp: Float = 0f): Float =
    2f * CARD_PAD_V_DP + (if (titled) CARD_TITLE_DP else 0f) + rows * CARD_ROW_DP + textLines * CARD_TEXT_LINE_DP + extraDp

/**
 * Eine Karte fürs Raster: ihre Höhe in der Übersicht und wie viele Spalten sie belegt (1 bis [SPAN_ALL]). [flex] false: die Karte
 * bleibt bei ihrer Inhaltshöhe, auch wenn die Seite Platz übrig hat (sie wächst nicht mit); den Platz bekommen die übrigen Karten.
 */
internal data class CardSize(val heightDp: Float, val span: Int = 1, val flex: Boolean = true)

/**
 * Ergebnis von [pageGrid]: [columns] Spalten, [rows] die Karten je Reihe (Indizes in der übergebenen Liste), [rowHeightsDp] die
 * Höhe jeder Reihe, [contentHeightDp] alle Reihen samt Abständen, [availableHeightDp] der Platz dafür. [fits]: passt ohne Scrollen.
 */
internal data class PageGridResult(
    val columns: Int,
    val rows: List<List<Int>>,
    val rowHeightsDp: List<Float>,
    val contentHeightDp: Float,
    val availableHeightDp: Float,
    val fits: Boolean,
    /** Je Reihe: wächst sie mit, wenn Platz übrig ist (mindestens eine Karte darin ist flexibel). */
    val rowFlex: List<Boolean> = emptyList(),
) {
    /** Die Seite scrollt: das Raster ist höher als der Platz. */
    val scroll: Boolean get() = !fits
}

/**
 * Spaltenzahl für einen Platz von [widthDp] x [heightDp]: schmaler als [TWO_COLUMN_MIN_WIDTH_DP] (Außendisplay) eine Spalte,
 * aufgeklappt quer ab [THREE_COLUMN_MIN_WIDTH_DP] drei, sonst zwei.
 */
internal fun pageColumnsFor(widthDp: Float, heightDp: Float): Int = when {
    widthDp.isNaN() || widthDp < TWO_COLUMN_MIN_WIDTH_DP.toFloat() -> 1
    widthDp >= THREE_COLUMN_MIN_WIDTH_DP && widthDp > heightDp -> 3
    else -> 2
}

/**
 * Das Raster einer Seite: Karten der Reihe nach in Reihen setzen (eine Karte, die nicht mehr in die Reihe passt, beginnt die
 * nächste), Reihenhöhe = höchste Karte, dazu die Abstände. [widthDp] x [heightDp] ist der Platz der ganzen Seite innerhalb der
 * Systemleisten; die Kopfzeile ([PAGE_CHROME_DP]) geht davon ab. [fontScale] über 1 vergrößert die Höhen (Systemschrift größer).
 */
internal fun pageGrid(widthDp: Float, heightDp: Float, cards: List<CardSize>, fontScale: Float = 1f): PageGridResult {
    val columns = pageColumnsFor(widthDp, heightDp)
    val scale = if (fontScale.isNaN() || fontScale < 1f) 1f else fontScale
    val rows = ArrayList<List<Int>>()
    val rowHeights = ArrayList<Float>()
    val rowFlex = ArrayList<Boolean>()
    var current = ArrayList<Int>()
    var used = 0
    var currentHeight = 0f
    var currentFlex = false
    for ((index, card) in cards.withIndex()) {
        val span = card.span.coerceIn(1, columns)
        if (current.isNotEmpty() && used + span > columns) {
            rows.add(current)
            rowHeights.add(currentHeight)
            rowFlex.add(currentFlex)
            current = ArrayList()
            used = 0
            currentHeight = 0f
            currentFlex = false
        }
        current.add(index)
        used += span
        currentHeight = maxOf(currentHeight, card.heightDp * scale)
        currentFlex = currentFlex || card.flex
    }
    if (current.isNotEmpty()) {
        rows.add(current)
        rowHeights.add(currentHeight)
        rowFlex.add(currentFlex)
    }
    val content = rowHeights.sum() + PAGE_GAP_DP * maxOf(rows.size - 1, 0)
    val available = heightDp - PAGE_CHROME_DP
    return PageGridResult(
        columns = columns,
        rows = rows,
        rowHeightsDp = rowHeights,
        contentHeightDp = content,
        availableHeightDp = available,
        fits = content <= available + 0.01f,
        rowFlex = rowFlex,
    )
}
