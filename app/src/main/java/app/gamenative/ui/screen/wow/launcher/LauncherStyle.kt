package app.gamenative.ui.screen.wow.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/*
 * Alle Stilwerte des Launchers an einer Stelle (Fantasy-MMO-Stil: warmer Stein und Leder, Gold-Filigran, weinrote Hauptschaltfläche).
 * Alles ist im Code gezeichnet; Fremdgrafik gibt es nur als optionale Bilder (launcher_art), Schrift ist Cinzel (LauncherFonts.kt). Außerhalb dieser Datei steht im Paket
 * `launcher/` kein `Color(0x...)` mehr (der Test LauncherStyleTest prüft das).
 */

/** Rohwerte als ARGB-Long, damit der Kontrasttest sie ohne Android rechnen kann. */
internal object LauncherPalette {
    // Grund und Flächen
    const val STONE_DARK = 0xFF14100CL
    const val STONE_LIGHT = 0xFF1E1812L
    const val SURFACE = 0xFF221A13L
    const val SURFACE_RAISED = 0xFF2B2118L
    const val SURFACE_PRESSED = 0xFF32261AL
    const val ERR_FILL = 0xFF2E1512L

    // Metalle
    const val GOLD_BRIGHT = 0xFFE2C995L
    const val GOLD = 0xFFB59A72L
    const val BRONZE = 0xFF85673CL
    const val BRONZE_DEEP = 0xFF574028L

    // Burgund (Hauptschaltfläche, gedämpftes Weinrot)
    const val CRIMSON_DARK = 0xFF5F2226L
    const val CRIMSON = 0xFF842F31L

    // Text
    const val TEXT_HEAD = 0xFFF0E6D2L

    /** Titel auf Bildern (warmes Fast-Weiß) und Unterzeilen auf Bildern (etwas ruhiger). */
    const val TEXT_BRIGHT = 0xFFFFF4DEL
    const val TEXT_BAR_SUB = 0xFFF3EAD8L
    const val BODY = 0xFFE6DCC8L
    const val MUTED = 0xFFB8A98CL
    const val SUBTLE = 0xFFA89A80L
    const val PARCHMENT = 0xFFE9DCB8L
    const val INK = 0xFF1D150BL

    // Zustände
    const val OK = 0xFF48BB78L
    const val WARN = 0xFFED8936L
    const val ERR = 0xFFFC8181L
    const val INFO = 0xFF63B3EDL
    const val ERR_TEXT = 0xFFFFC9C0L
    const val DISABLED_FILL = 0xFF2A241DL
    const val DISABLED_TEXT = 0xFFA09378L

    // Textbalken auf Bildern (Hero, Kacheln): Dunkelbraun der Palette; oben klar, unter dem Text 72 %, unten 80 % deckend.
    const val BAR_CLEAR = 0x000C0906L
    const val BAR_TEXT_ZONE = 0xB80C0906L
    const val BAR_BOTTOM = 0xCC0C0906L

    // Abzeichen "Nur hier" (Bronze) und "Auch im Spiel" (Grün-Gold)
    const val BADGE_HERE_FILL = 0xFF3A2A16L
    const val BADGE_HERE_TEXT = 0xFFE3C08CL
    const val BADGE_GAME_FILL = 0xFF22301AL
    const val BADGE_GAME_BORDER = 0xFF7C9A3CL
    const val BADGE_GAME_TEXT = 0xFFCFE08FL
}

private fun Long.argb(): Color = Color(this)

/** Die Farben als Compose-Werte. Name und Mitglieder der früheren Farbtabelle der Startseite bleiben erhalten. */
internal object LauncherColors {
    // Flächen
    val StoneDark = LauncherPalette.STONE_DARK.argb()
    val StoneLight = LauncherPalette.STONE_LIGHT.argb()
    val Surface = LauncherPalette.SURFACE.argb()
    val SurfaceRaised = LauncherPalette.SURFACE_RAISED.argb()
    val SurfacePressed = LauncherPalette.SURFACE_PRESSED.argb()
    val ErrFill = LauncherPalette.ERR_FILL.argb()

    /** Frühere Namen für Karten (Kachel, Seitenkarte): jetzt Leder statt Marineblau. */
    val CardFill = Surface
    val CardBorder = LauncherPalette.BRONZE.argb()

    // Metalle
    val GoldBright = LauncherPalette.GOLD_BRIGHT.argb()
    val Gold = LauncherPalette.GOLD.argb()
    val Bronze = LauncherPalette.BRONZE.argb()
    val BronzeDeep = LauncherPalette.BRONZE_DEEP.argb()
    val Crimson = LauncherPalette.CRIMSON.argb()
    val CrimsonDark = LauncherPalette.CRIMSON_DARK.argb()

    // Text
    val TextHead = LauncherPalette.TEXT_HEAD.argb()
    val Body = LauncherPalette.BODY.argb()
    val Muted = LauncherPalette.MUTED.argb()
    val Subtle = LauncherPalette.SUBTLE.argb()
    val TextBright = LauncherPalette.TEXT_BRIGHT.argb()
    val TextBarSub = LauncherPalette.TEXT_BAR_SUB.argb()
    val Parchment = LauncherPalette.PARCHMENT.argb()
    val Ink = LauncherPalette.INK.argb()

    // Zustände
    val Ok = LauncherPalette.OK.argb()
    val Warn = LauncherPalette.WARN.argb()
    val Err = LauncherPalette.ERR.argb()
    val Info = LauncherPalette.INFO.argb()
    val ErrText = LauncherPalette.ERR_TEXT.argb()
    val FocusRing = TextHead
    val WarnBorder = Color(0xFF7A5628)
    val BadBorder = Color(0xFF8A4545)
    val DisabledFill = LauncherPalette.DISABLED_FILL.argb()
    val DisabledText = LauncherPalette.DISABLED_TEXT.argb()

    // Linien und Schatten
    val Divider = Color(0xFF3A2D1F)
    val Shadow = Color(0xCC000000)

    /** Weicher Schatten unter Titeln und Knopftext (früher voller Shadow mit 80 %). */
    val TextShadowSoft = Color(0x80000000)
    val VignetteEdge = Color(0x8C000000)

    /** Leichter Schleier über Fotos (Screenshot, mitgeliefertes Bild), damit Schrift und Rahmen lesbar bleiben. */
    val ImageScrim = Color(0x38000000)

    /** Gedämpfte Goldlinie der Kacheln und des Hero (55 % Deckkraft). */
    val GoldMuted = LauncherPalette.GOLD.argb().copy(alpha = 0.55f)

    /** Textbalken am unteren Rand von Hero und Kacheln (siehe LauncherBar.kt). */
    val BarClear = LauncherPalette.BAR_CLEAR.argb()
    val BarTextZone = LauncherPalette.BAR_TEXT_ZONE.argb()
    val BarBottom = LauncherPalette.BAR_BOTTOM.argb()

    /** Leichte Abdunkelung (20 %) über den Kartenbildern der Kacheln, damit sie ruhiger wirken. */
    val TileDim = Color(0x33000000)
    val GrainLight = Color(0x14FFE2B0)
    val GrainDark = Color(0x1F000000)

    // Abzeichen
    val BadgeHereFill = LauncherPalette.BADGE_HERE_FILL.argb()
    val BadgeHereBorder = Bronze
    val BadgeHereText = LauncherPalette.BADGE_HERE_TEXT.argb()
    val BadgeGameFill = LauncherPalette.BADGE_GAME_FILL.argb()
    val BadgeGameBorder = LauncherPalette.BADGE_GAME_BORDER.argb()
    val BadgeGameText = LauncherPalette.BADGE_GAME_TEXT.argb()
}

/** Maße und Laufweiten. */
internal object LauncherStyle {
    /** Rundung der Rahmen: eher Stein als Kunststoff. */
    val FrameRadius = 6.dp
    const val OUTER_LINE_DP = 1.5f
    const val INNER_LINE_DP = 1f
    const val INNER_INSET_DP = 4f
    const val EDGE_LINE_DP = 1.5f

    /** Laufweite der Überschriften in Versalien (0,12 em). */
    const val TITLE_TRACKING_EM = 0.12f

    /** Rundung von Hero und Kacheln der Startseite und ihre dünne Goldlinie. */
    val CardRadius = 14.dp
    const val CARD_LINE_DP = 1f

    /** Höhe des Auslaufs oben im Textbalken (von klar zu 72 % deckend); der Text beginnt darunter. */
    const val BAR_FADE_DP = 16f

    /** Sättigung der Kartenbilder auf den Kacheln (1 = unverändert): 20 % weniger. */
    const val TILE_SATURATION = 0.8f

    /** Seitengröße der Körnungskachel in Pixeln (fester Zufallssamen, einmal gezeichnet, dann gekachelt). */
    const val GRAIN_TILE_PX = 128
    const val GRAIN_SEED = 0x57A11L
}

/** Farben der gezeichneten Hero-Landschaft: Dämmerung in Gold und Violett. */
internal object LauncherArtColors {
    val SkyTop = Color(0xFF241A45)
    val SkyUpper = Color(0xFF58396F)
    val SkyMid = Color(0xFFA9527A)
    val SkyLow = Color(0xFFE39A4A)
    val SkyHorizon = Color(0xFFF4CC78)
    val SunCore = Color(0xF2FFE3A8)
    val SunMid = Color(0x73FFB458)
    val SunEdge = Color(0x00FFB458)
    val Star = Color(0xCCFFF1D6)
    val Cloud = Color(0x29F7D9B0)
    val FarTop = Color(0xFF6E4A78)
    val FarBottom = Color(0xFF3F2F5E)
    val Snow = Color(0x8CFFF1DC)
    val SnowSoft = Color(0x80FFF1DC)
    val Tower = Color(0xFF2B1F42)
    val MidTop = Color(0xFF402D58)
    val MidBottom = Color(0xFF2A2040)
    val NearTop = Color(0xFF281E38)
    val NearBottom = Color(0xFF15101F)
    val Forest = Color(0xFF0E0A14)
    val HazeClear = Color(0x00F2C986)
    val HazeFull = Color(0x47F2C986)
    val Window = Color(0xFFFFD98A)
}

/** Reine Kontrastrechnung (WCAG 2.x) auf den Rohwerten der Palette. */
internal object LauncherContrast {
    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Legt [fg] (Deckkraft in den obersten 8 Bit) über den deckenden Hintergrund [bg]; das Ergebnis ist deckend. */
    fun over(fg: Long, bg: Long): Long {
        val a = ((fg shr 24) and 0xFF) / 255.0
        fun mix(shift: Int): Long {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            return Math.round(f * a + b * (1.0 - a))
        }
        return 0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Eine Paarung "Text auf Fläche", wie sie in der Oberfläche vorkommt; Mindestverhältnis 4,5. */
    data class ContrastPair(val name: String, val text: Long, val surface: Long)

    val pairs: List<ContrastPair> = with(LauncherPalette) {
        val surfaces = listOf(
            "Stein dunkel" to STONE_DARK, "Stein hell" to STONE_LIGHT, "Fläche" to SURFACE,
            "Fläche erhöht" to SURFACE_RAISED, "Fläche gedrückt" to SURFACE_PRESSED,
        )
        val texts = listOf(
            "Text hell" to TEXT_HEAD, "Titel hell" to TEXT_BRIGHT, "Unterzeile" to TEXT_BAR_SUB, "Text" to BODY, "Gedämpft" to MUTED, "Leise" to SUBTLE, "Gold" to GOLD,
            "Hellgold" to GOLD_BRIGHT, "Pergament" to PARCHMENT, "Ok" to OK, "Warnung" to WARN, "Fehler" to ERR, "Info" to INFO,
        )
        buildList {
            for ((sn, s) in surfaces) for ((tn, t) in texts) add(ContrastPair("$tn auf $sn", t, s))
            add(ContrastPair("Fehlertext auf Fehlerfläche", ERR_TEXT, ERR_FILL))
            add(ContrastPair("Fehler auf Fehlerfläche", ERR, ERR_FILL))
            add(ContrastPair("Gold auf Fehlerfläche", GOLD, ERR_FILL))
            add(ContrastPair("Hellgold auf Karmesin", GOLD_BRIGHT, CRIMSON))
            add(ContrastPair("Hellgold auf Karmesin dunkel", GOLD_BRIGHT, CRIMSON_DARK))
            add(ContrastPair("Tinte auf Gold", INK, GOLD))
            add(ContrastPair("Tinte auf Hellgold", INK, GOLD_BRIGHT))
            add(ContrastPair("Gesperrt", DISABLED_TEXT, DISABLED_FILL))
            add(ContrastPair("Abzeichen Nur hier", BADGE_HERE_TEXT, BADGE_HERE_FILL))
            add(ContrastPair("Abzeichen Auch im Spiel", BADGE_GAME_TEXT, BADGE_GAME_FILL))
        }
    }

    /** Bildhintergründe, auf denen der Textbalken schlimmstenfalls liegt: weiß, helles Gold (Horizont der gezeichneten Kunst), grau, schwarz. */
    val barBackdrops: List<Pair<String, Long>> = listOf(
        "Weiß" to 0xFFFFFFFFL, "Hellgold" to 0xFFF4CC78L, "Mittelgrau" to 0xFF808080L, "Schwarz" to 0xFF000000L,
    )

    /**
     * Texte auf dem Balken, gerechnet an der schwächsten Stelle: der Balken unter dem Text ist mindestens [LauncherPalette.BAR_TEXT_ZONE]
     * (72 %) deckend, unten [LauncherPalette.BAR_BOTTOM] (80 %). Mindestverhältnis 4,5 auf jedem Hintergrund.
     */
    val barPairs: List<ContrastPair> = with(LauncherPalette) {
        val texts = listOf("Titel hell" to TEXT_BRIGHT, "Unterzeile" to TEXT_BAR_SUB, "Hellgold" to GOLD_BRIGHT)
        val zones = listOf("Textzone" to BAR_TEXT_ZONE, "Balkenfuß" to BAR_BOTTOM)
        buildList {
            for ((bn, backdrop) in barBackdrops) for ((zn, zone) in zones) for ((tn, t) in texts) {
                add(ContrastPair("$tn auf Balken ($zn) über $bn", t, over(zone, backdrop)))
            }
        }
    }
}
