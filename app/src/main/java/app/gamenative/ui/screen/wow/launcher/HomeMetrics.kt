package app.gamenative.ui.screen.wow.launcher

/*
 * Maße der Startseite als reine Rechnung (ohne Android, per JUnit getestet). Die Startseite soll ohne Scrollen auf den Schirm
 * passen: aufgeklappt (Innendisplay) und zugeklappt (Außendisplay). Dafür teilen sich Kopf, Hero, Kacheln und Fußzeile die Höhe;
 * die Rechnung sagt, wie dicht (HomeDensity), wie hoch die Kachelreihen sind und ab welcher Höhe Scrollen nötig wird.
 * LauncherHome.kt liest das Ergebnis und setzt Hero und Kacheln mit Gewichten in den Rest.
 */

/** Wie eng die Startseite gesetzt wird: Schriftgrößen, Abstände und Knopfhöhen im Hero hängen daran. */
internal enum class HomeDensity { Roomy, Normal, Tight }

/** Geschätzte Höhe des Kopfes (Wappen, Wortmarke, Statuspille) in dp. */
internal const val HOME_HEADER_DP = 52f

/** Geschätzte Höhe der einzeiligen Fußzeile in dp. */
internal const val HOME_FOOTER_DP = 40f

/** Unterhalb dieser Höhe (dp) scrollt der aufgeklappte Fold, weil Hero und Kachelreihen sonst gequetscht würden. */
internal const val HOME_MIN_HEIGHT_WIDE_DP = 520f

/** Dasselbe für das Außendisplay (Hero oben, drei Kachelreihen darunter braucht mehr Höhe). */
internal const val HOME_MIN_HEIGHT_NARROW_DP = 600f

/** Kleinste Kachelhöhe: darunter wäre selbst ein Titel mit einer Zeile zu eng. */
internal const val HOME_TILE_MIN_DP = 72f
internal const val HOME_TILE_MAX_DP = 112f

/** Kacheln unter dieser Höhe zeigen nur Titel und eine Zeile und ein kleineres Motiv. */
internal const val HOME_TILE_SHORT_DP = 92f

/** Höhe des Update-Knopfes in dp; er steht in Kopf oder Fußzeile und darf deren Höhe nicht über [HOME_HEADER_DP]/[HOME_FOOTER_DP] heben. */
internal const val HOME_UPDATE_BUTTON_DP = 40f

/** Ab dieser Breite (dp) zeigt der Knopf "Nach Updates suchen" mit Text; schmaler nur das Symbol (der Streifen trägt dann das Ergebnis). */
internal const val HOME_UPDATE_TEXT_MIN_WIDTH_DP = 820f

/** Wo der Update-Knopf sitzt: aufgeklappt rechts im Kopf (neben der Statuspille), zugeklappt rechts in der Fußzeile. */
internal enum class UpdateButtonSlot { Header, Footer }

internal fun updateButtonSlotFor(layout: HomeLayout): UpdateButtonSlot =
    if (layout == HomeLayout.Narrow) UpdateButtonSlot.Footer else UpdateButtonSlot.Header

/** Text am Knopf nur im Kopf (aufgeklappt) und ab [HOME_UPDATE_TEXT_MIN_WIDTH_DP]; sonst Symbol mit Beschreibung für die Bedienungshilfe. */
internal fun updateButtonShowsText(layout: HomeLayout, widthDp: Float): Boolean =
    layout == HomeLayout.Wide && widthDp >= HOME_UPDATE_TEXT_MIN_WIDTH_DP

internal data class HomeMetrics(
    val layout: HomeLayout,
    val density: HomeDensity,
    /** true: die Seite ist höher als der Schirm und scrollt (nur unterhalb der Mindesthöhe). */
    val scroll: Boolean,
    /** Abstand zwischen Kopf, Streifen, Mittelteil und Fußzeile. */
    val sectionGapDp: Float,
    /** Abstand zwischen Kacheln und zwischen Hero und Kacheln. */
    val tileGapDp: Float,
    /** Höhe einer Kachelreihe; aufgeklappt der Schätzwert (die Reihen teilen sich die Höhe über Gewichte). */
    val tileHeightDp: Float,
    /** Höhe des Hero (zugeklappt) beziehungsweise des Rasters (aufgeklappt) ohne Streifen für Update oder Fehler. */
    val heroHeightDp: Float,
    /** Kacheln zeigen Titel und eine Zeile, Motiv klein. */
    val tileShort: Boolean,
    /** Höhe des Mittelteils (Hero und Kacheln) im scrollenden Fall; sonst nur zur Information. */
    val middleMinDp: Float,
    /** Der Update-Knopf sitzt im Kopf oder in der Fußzeile; er braucht keine eigene Zeile (Höhen bleiben wie ohne ihn). */
    val updateSlot: UpdateButtonSlot = updateButtonSlotFor(layout),
    /** Der Knopf zeigt Text neben dem Symbol (und der Streifen lässt "Aktuell"/Suchfehler weg, weil der Knopf sie trägt). */
    val updateButtonText: Boolean = false,
)

/** Mindesthöhe des Hero in dp je Dichte: was Titel, Zeile, Anmelde-Chip, Knopf und Countdown brauchen. */
internal fun heroMinHeightFor(density: HomeDensity): Float = when (density) {
    HomeDensity.Roomy -> 300f
    HomeDensity.Normal -> 260f
    HomeDensity.Tight -> 230f
}

internal fun homeDensityFor(heightDp: Float): HomeDensity = when {
    heightDp >= 760f -> HomeDensity.Roomy
    heightDp >= 640f -> HomeDensity.Normal
    else -> HomeDensity.Tight
}

/**
 * Die Maße der Startseite für einen Platz von [widthDp] x [heightDp] (gemessen innerhalb der Ränder des Launcher-Bildschirms).
 * Ohne Streifen für Update oder Fehler; kommt einer dazu, schrumpft der Hero (zugeklappt) beziehungsweise schrumpfen die Kacheln.
 */
internal fun homeMetricsFor(widthDp: Float, heightDp: Float): HomeMetrics {
    val layout = homeLayoutFor(widthDp)
    val density = homeDensityFor(heightDp)
    val gap = when (density) {
        HomeDensity.Roomy -> 12f
        HomeDensity.Normal -> 11f
        HomeDensity.Tight -> 10f
    }
    val minHeight = if (layout == HomeLayout.Narrow) HOME_MIN_HEIGHT_NARROW_DP else HOME_MIN_HEIGHT_WIDE_DP
    val scroll = heightDp < minHeight
    val chrome = HOME_HEADER_DP + HOME_FOOTER_DP
    val heroMin = heroMinHeightFor(density)

    if (layout == HomeLayout.Narrow) {
        // Abschnitte: Kopf, Hero, drei Kachelreihen, Fußzeile = fünf Abstände.
        val tile = if (scroll) HOME_TILE_MIN_DP else (heightDp * 0.115f).coerceIn(HOME_TILE_MIN_DP, HOME_TILE_MAX_DP)
        val hero = if (scroll) heroMin else heightDp - chrome - 5f * gap - 3f * tile
        return HomeMetrics(
            layout = layout,
            density = density,
            scroll = scroll,
            sectionGapDp = gap,
            tileGapDp = gap,
            tileHeightDp = tile,
            heroHeightDp = hero,
            tileShort = tile < HOME_TILE_SHORT_DP,
            middleMinDp = hero + 3f * tile + 3f * gap,
            updateButtonText = updateButtonShowsText(layout, widthDp),
        )
    }

    // Aufgeklappt: Kopf, Raster, Fußzeile = zwei Abstände; im Raster drei Reihen mit zwei Abständen.
    val grid = if (scroll) maxOf(3f * HOME_TILE_MIN_DP + 2f * gap, 300f) else heightDp - chrome - 2f * gap
    val tile = (grid - 2f * gap) / 3f
    return HomeMetrics(
        layout = layout,
        density = density,
        scroll = scroll,
        sectionGapDp = gap,
        tileGapDp = gap,
        tileHeightDp = tile,
        heroHeightDp = grid,
        tileShort = tile < HOME_TILE_SHORT_DP,
        middleMinDp = grid,
        updateButtonText = updateButtonShowsText(layout, widthDp),
    )
}

// --- Schriftgrößen der Startseite (Titel und Zeilen auf den Textbalken) ---

/** Kleinste Schriftgröße auf der Startseite (Fußzeile, Abzeichen, Statuspille) in sp. */
internal const val HOME_MIN_SP = 13f

/** Titel des Hero in sp (Cinzel in Versalien): 40 bis 48 aufgeklappt, 34 bis 42 zugeklappt (schmaler Platz). */
internal fun heroTitleSp(compact: Boolean, density: HomeDensity): Float = when (density) {
    HomeDensity.Roomy -> if (compact) 42f else 48f
    HomeDensity.Normal -> if (compact) 38f else 44f
    HomeDensity.Tight -> if (compact) 34f else 40f
}

/** Kleinste Größe, auf die ein langer Hero-Titel (zum Beispiel "Installiert") schrumpft, damit kein Wort bricht. */
internal const val HERO_TITLE_MIN_SP = 26f

/** Zeile unter dem Hero-Titel in sp: 16 (bei viel Platz), sonst 15. */
internal fun heroLineSp(compact: Boolean, density: HomeDensity): Float = if (compact || density == HomeDensity.Tight) 15f else 16f

/** Kleinste Größe eines Kacheltitels in sp (Cinzel in Versalien), auf die ein langer Titel schrumpft, damit kein Wort bricht. */
internal const val TILE_TITLE_MIN_SP = 15f

/** Höchstgröße des Kacheltitels in sp (Cinzel Bold in Versalien, vor dem Einpassen): 23 aufgeklappt, 20 zugeklappt, 17 auf kurzen Kacheln. */
internal fun tileTitleSp(compact: Boolean, short: Boolean): Float = if (short) 17f else if (compact) 20f else 23f

/** Zeile unter dem Kacheltitel in sp: 15, auf kurzen Kacheln 13. */
internal fun tileLineSp(short: Boolean): Float = if (short) 13f else 15f

/** Geschätzte Breite eines Versalienbuchstabens der Cinzel Bold in Vielfachen der Schriftgröße, großzügig und mit Laufweite. */
internal const val CAPS_EM_PER_CHAR = 0.88f

/**
 * Größe in sp für einen Titel in Versalien, der in [widthDp] passen soll: das längste Wort (Wörter brechen nicht) muss in eine Zeile.
 * Ergebnis zwischen [minSp] und [maxSp]; ein kurzer Titel behält [maxSp]. Ungültige Breite ergibt [maxSp].
 */
internal fun fitTitleSp(text: String, widthDp: Float, maxSp: Float, minSp: Float): Float {
    val longest = text.split(' ').maxOfOrNull { it.length } ?: 0
    if (longest <= 0 || widthDp <= 0f || widthDp.isNaN()) return maxSp
    val fit = widthDp / (longest * CAPS_EM_PER_CHAR)
    return fit.coerceAtMost(maxSp).coerceAtLeast(minOf(minSp, maxSp))
}
