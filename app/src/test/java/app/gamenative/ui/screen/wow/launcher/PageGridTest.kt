package app.gamenative.ui.screen.wow.launcher

import app.gamenative.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * fold8.26: Unterseiten als Übersicht ohne Scrollen auf dem Fold innen (840x760 quer, 760x840 hoch), eine Spalte außen
 * (410x900, 360x640). Die Kartenlisten sind die der Seiten (PagePlans.kt); ein Quelltext-Test prüft, dass die Seiten genau diese
 * Karten bauen.
 */
class PageGridTest {
    private val eps = 0.01f

    private data class Screen(val name: String, val w: Float, val h: Float, val columns: Int)

    private val inner = listOf(Screen("innen quer", 840f, 760f, 3), Screen("innen hoch", 760f, 840f, 2))
    private val outer = listOf(Screen("außen", 410f, 900f, 1), Screen("außen schmal", 360f, 640f, 1))

    /** Alle Kartenlisten, die eine Seite je nach Zustand haben kann (Name der Seite, Liste). */
    private fun allPlans(): List<Pair<String, List<CardPlan>>> = buildList {
        add("Einstellungen" to settingsPlan())
        add("Steuerung" to controlPlan(LauncherTexts.CONTROL_HINT_GROUPS.size))
        add("Addons (katalog)" to addonsPlan(3))
        for (alert in FilesAlert.entries) add("Spieldateien $alert" to filesPlan(alert))
        for (whatsNew in listOf(false, true)) for (offer in listOf(false, true)) for (versions in listOf(0, 1, 2, 3, 12)) {
            add("Neuigkeiten neu=$whatsNew update=$offer versionen=$versions" to newsPlan(whatsNew, offer, versions))
        }
        for (error in listOf(false, true)) add("Hilfe Meldung=$error" to helpPlan(error))
        add("Über" to aboutPlan())
    }

    // --- Spalten ---

    @Test
    fun columnsFollowTheScreen() {
        for (s in inner + outer) assertEquals(s.name, s.columns, pageColumnsFor(s.w, s.h))
        // aufgeklappt quer ab 780 dp drei, schmaler quer nur zwei
        assertEquals(3, pageColumnsFor(1000f, 560f))
        assertEquals(2, pageColumnsFor(700f, 500f))
        assertEquals(1, pageColumnsFor(639f, 900f))
        assertEquals(2, pageColumnsFor(640f, 900f))
        assertEquals(1, pageColumnsFor(Float.NaN, 900f))
    }

    // --- Kein Scrollen innen, jede Seite, beide Maße ---

    @Test
    fun everyPageFitsWithoutScrollingOnTheInnerDisplay() {
        for ((name, plan) in allPlans()) for (s in inner) {
            val g = pageGrid(s.w, s.h, plan.sizes())
            assertEquals("$name bei ${s.name}", s.columns, g.columns)
            assertTrue(
                "$name bei ${s.name} (${s.w} x ${s.h}): Inhalt ${g.contentHeightDp} dp, Platz ${g.availableHeightDp} dp",
                g.fits,
            )
            assertFalse("$name bei ${s.name}", g.scroll)
        }
    }

    @Test
    fun everyPageHasOneColumnOnTheOuterDisplay() {
        for ((name, plan) in allPlans()) for (s in outer) {
            val g = pageGrid(s.w, s.h, plan.sizes())
            assertEquals("$name bei ${s.name}", 1, g.columns)
            // jede Reihe hat genau eine Karte
            assertTrue("$name bei ${s.name}", g.rows.all { it.size == 1 })
            assertEquals(plan.size, g.rows.size)
        }
    }

    @Test
    fun theTightestPagesKeepSomeRoomOnTheInnerDisplay() {
        // Schätzfehler der Zeilenhöhen abfangen: mindestens 15 dp Luft im ungünstigsten Zustand jeder Seite
        for ((name, plan) in allPlans()) for (s in inner) {
            val g = pageGrid(s.w, s.h, plan.sizes())
            assertTrue("$name bei ${s.name}: nur ${g.availableHeightDp - g.contentHeightDp} dp Luft", g.availableHeightDp - g.contentHeightDp >= 15f)
        }
    }

    @Test
    fun aLargerSystemFontCanTurnOnScrollingInsteadOfClipping() {
        val plan = settingsPlan().sizes()
        assertFalse(pageGrid(840f, 760f, plan, fontScale = 1f).scroll)
        assertTrue(pageGrid(840f, 760f, plan, fontScale = 2f).scroll)
        // kleiner als 1 zählt wie 1
        assertEquals(pageGrid(840f, 760f, plan).contentHeightDp, pageGrid(840f, 760f, plan, fontScale = 0.8f).contentHeightDp, eps)
    }

    // --- Rechnung des Rasters ---

    @Test
    fun rowsFillInOrderAndTheTallestCardSetsTheRowHeight() {
        val cards = listOf(CardSize(100f), CardSize(200f), CardSize(150f), CardSize(120f))
        val g = pageGrid(840f, 760f, cards)
        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), g.rows)
        assertEquals(listOf(200f, 120f), g.rowHeightsDp)
        assertEquals(200f + 120f + PAGE_GAP_DP, g.contentHeightDp, eps)
        assertEquals(760f - PAGE_CHROME_DP, g.availableHeightDp, eps)
    }

    @Test
    fun aWideCardTakesItsColumnsAndStartsANewRowWhenItDoesNotFit() {
        val cards = listOf(CardSize(80f), CardSize(90f, span = 2), CardSize(70f, span = 2), CardSize(60f))
        val g = pageGrid(840f, 760f, cards)
        // 1 + 2 = 3 Spalten voll; die nächste Breite beginnt eine neue Reihe, die kleine Karte passt noch dazu? 2 + 1 = 3: ja
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), g.rows)
        // SPAN_ALL belegt auch zwei Spalten ganz (hoch) und eine (außen)
        val full = listOf(CardSize(50f, span = SPAN_ALL), CardSize(50f))
        assertEquals(listOf(listOf(0), listOf(1)), pageGrid(840f, 760f, full).rows)
        assertEquals(listOf(listOf(0), listOf(1)), pageGrid(760f, 840f, full).rows)
        assertEquals(listOf(listOf(0), listOf(1)), pageGrid(410f, 900f, full).rows)
        // eine Karte über die Spaltenzahl hinaus wird auf die Spaltenzahl gekürzt
        assertEquals(listOf(listOf(0)), pageGrid(410f, 900f, listOf(CardSize(50f, span = 3))).rows)
    }

    @Test
    fun theGridOverflowsWhenTheCardsAreTooTall() {
        val cards = List(9) { CardSize(300f) }
        val g = pageGrid(840f, 760f, cards)
        assertFalse(g.fits)
        assertTrue(g.scroll)
        assertEquals(3 * 300f + 2 * PAGE_GAP_DP, g.contentHeightDp, eps)
        // ohne Karten: nichts zu zeigen, passt
        val empty = pageGrid(840f, 760f, emptyList())
        assertTrue(empty.fits)
        assertEquals(0f, empty.contentHeightDp, eps)
    }

    @Test
    fun cardHeightsComeFromTheSameConstantsAsTheRows() {
        val expected = 2f * CARD_PAD_V_DP + CARD_TITLE_DP + 3 * CARD_ROW_DP + 2 * CARD_TEXT_LINE_DP + 10f
        assertEquals(expected, cardHeightDp(rows = 3, textLines = 2, extraDp = 10f), eps)
        assertEquals(2f * CARD_PAD_V_DP + 3 * CARD_TEXT_LINE_DP, cardHeightDp(titled = false, textLines = 3), eps)
        // Zeile für den Daumen, Textzeile passend zu 14 sp, Titelzeile für Cinzel 16 sp
        assertTrue(CARD_ROW_DP >= 44f)
        assertTrue(CARD_TEXT_LINE_DP >= 14f * 1.3f)
        assertTrue(CARD_TITLE_DP >= 16f * 1.3f)
        // ein Knopf (56 dp) samt Abstand
        assertEquals(56f + 8f, CARD_BUTTON_DP, eps)
        // Kopfzeile: Knopf 48 dp + 4 dp oben
        assertEquals(52f, PAGE_HEADER_DP, eps)
        assertEquals(PAGE_HEADER_DP + PAGE_DIVIDER_DP + PAGE_BODY_TOP_DP + PAGE_BODY_BOTTOM_DP, PAGE_CHROME_DP, eps)
    }

    // --- Pläne ---

    @Test
    fun planIdsAreUniquePerPageAndUnknownIdsFailLoudly() {
        for ((name, plan) in allPlans()) {
            assertEquals("$name: doppelter Schlüssel", plan.size, plan.map { it.id }.toSet().size)
            for (card in plan) {
                assertTrue("$name/${card.id}: Höhe", card.size.heightDp > 0f)
                assertTrue("$name/${card.id}: Spannweite", card.size.span in 1..SPAN_ALL)
            }
        }
        try {
            settingsPlan().sizeOf("gibtsnicht")
            fail("unbekannter Schlüssel hätte scheitern müssen")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("gibtsnicht"))
        }
    }

    @Test
    fun theSettingsOverviewKeepsEveryGroupReachable() {
        // Einstellungen: fünf Karten nach Bedeutung; die zwei höchsten (Spiel, Start und Updates) stehen nebeneinander,
        // die niedrigste (Hilfe) steht hochkant allein in der letzten Reihe
        assertEquals(listOf("game", "start", "control", "app", "help"), settingsPlan().map { it.id })
        // Steuerung: Modus, je Gruppe im Seitenmenü eine Karte, dazu die Karte für die Spiel-Einstellungen
        val control = controlPlan(LauncherTexts.CONTROL_HINT_GROUPS.size).map { it.id }
        assertEquals("mode", control.first())
        assertEquals("gamefold", control.last())
        assertEquals(LauncherTexts.CONTROL_HINT_GROUPS.size + 2, control.size)
    }

    @Test
    fun theSettingsPlanFitsWithoutScrollingAndKeepsEachRowSimilarlyHigh() {
        val sizes = settingsPlan().sizes()
        val wide = pageGrid(840f, 760f, sizes)
        assertEquals(3, wide.columns)
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), wide.rows)
        assertTrue(wide.fits)
        val tall = pageGrid(760f, 840f, sizes)
        assertEquals(2, tall.columns)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), tall.rows)
        assertTrue(tall.fits)
        // außen eine Spalte, scrollen ist dort erlaubt
        assertEquals(1, pageGrid(410f, 900f, sizes).columns)
        assertEquals(1, pageGrid(360f, 640f, sizes).columns)
        // Karten einer Reihe sind höchstens 110 dp unterschiedlich hoch (kein großer Leerraum); quer: App (4 Zeilen, 244) neben Hilfe (140) = 104
        for (g in listOf(wide, tall)) for (row in g.rows.filter { it.size > 1 }) {
            val diff = row.maxOf { sizes[it].heightDp } - row.minOf { sizes[it].heightDp }
            assertTrue("Reihe $row: $diff dp Unterschied", diff <= 110f)
        }
    }

    @Test
    fun newsAndFilesPlansFollowTheirState() {
        assertEquals(listOf("latest", "prev0", "prev1", "all"), newsPlan(false, false, 5).map { it.id })
        assertEquals(listOf("latest", "prev0", "all"), newsPlan(false, false, 2).map { it.id })
        assertEquals(listOf("latest", "all"), newsPlan(false, false, 1).map { it.id })
        assertEquals(listOf("offer", "whatsnew", "empty"), newsPlan(true, true, 0).map { it.id })
        assertEquals(listOf("offer", "whatsnew", "latest", "prev0", "prev1", "all"), newsPlan(true, true, 9).map { it.id })
        assertEquals(listOf("state", "folder", "login"), filesPlan(FilesAlert.None).map { it.id })
        for (alert in listOf(FilesAlert.Grant, FilesAlert.Install, FilesAlert.Update)) {
            assertEquals(listOf("alert", "state", "folder", "login"), filesPlan(alert).map { it.id })
            assertEquals(SPAN_ALL, filesPlan(alert).sizeOf("alert").span)
        }
        assertEquals(listOf("ready", "report", "gameonly", "launcher"), helpPlan(false).map { it.id })
        assertEquals(listOf("ready", "error", "report", "gameonly", "launcher"), helpPlan(true).map { it.id })
    }

    @Test
    fun theAddonsPlanHasOneCardPerCatalogEntry() {
        assertEquals(listOf("note", "addon0", "addon1", "addon2", "custom"), addonsPlan(3).map { it.id })
        assertEquals(listOf("note", "custom"), addonsPlan(0).map { it.id })
        // der echte Katalog: so viele Einträge wie Karten, und die Seite passt aufgeklappt
        val catalog = listOf("src/main/assets/wow/addons/katalog.json", "app/src/main/assets/wow/addons/katalog.json").map { File(it) }.firstOrNull { it.isFile }
        assumeTrue("Katalog nicht gefunden", catalog != null)
        val entries = Regex(""""id"\s*:""").findAll(catalog!!.readText()).count()
        assertTrue(entries in 1..6)
        for (s in inner) assertTrue("Addons mit $entries Einträgen bei ${s.name}", pageGrid(s.w, s.h, addonsPlan(entries).sizes()).fits)
    }

    // --- Texte ---

    @Test
    fun smallTextHelpers() {
        assertEquals(UiText.res(R.string.launcher_client_unchecked), PageTexts.clientBuildValue(null, null, false))
        assertEquals(UiText.res(R.string.launcher_client_unchecked), PageTexts.clientBuildValue("  ", "1", true))
        assertEquals(UiText.raw("1.60.1"), PageTexts.clientBuildValue("1.60.1", "1.60.1", false))
        assertEquals(UiText.raw("1.60.1 → 1.61.0"), PageTexts.clientBuildValue("1.60.1", "1.61.0", true))
        assertEquals(UiText.res(R.string.launcher_control_hint_line, UiText.res(R.string.launcher_control_group_sticks)), PageTexts.controlHintLine(UiText.res(R.string.launcher_control_group_sticks)))
        assertEquals(UiText.res(R.string.launcher_join_dot, "11.11.2026", UiText.plural(R.plurals.launcher_points, 7)), PageTexts.versionLine("11.11.2026", 7))
        assertEquals(UiText.plural(R.plurals.launcher_points, 1), PageTexts.versionLine("", 1))
        assertEquals("2.3.3-fold8.26 · 11.11.2026", PageTexts.latestTitle("2.3.3-fold8.26", "11.11.2026"))
        assertEquals("2.3.3", PageTexts.latestTitle("2.3.3", ""))
        assertEquals(UiText.res(R.string.launcher_more_items, 3), PageTexts.moreLine(5, 2))
        assertNull(PageTexts.moreLine(2, 2))
        assertNull(PageTexts.moreLine(1, 2))
        assertEquals(UiText.plural(R.plurals.launcher_versions_newest_first, 1), PageTexts.allVersionsLine(1))
        assertEquals(UiText.plural(R.plurals.launcher_versions_newest_first, 4), PageTexts.allVersionsLine(4))
    }

    @Test
    fun theAddonsNoteShowsOneMessageInAFixedOrder() {
        fun note(
            progress: UiText? = null,
            message: UiText? = null,
            customBusy: UiText? = null,
            customMessage: UiText? = null,
            problem: UiText? = null,
            gameBusy: Boolean = false,
            noGame: Boolean = false,
        ) = PageTexts.addonsNote(
            progress = progress,
            message = message,
            messageError = true,
            customBusy = customBusy,
            customMessage = customMessage,
            customError = false,
            catalogProblem = problem,
            gameBusy = gameBusy,
            noGame = noGame,
            busyHint = UiText.raw("BUSY"),
            noGameHint = UiText.raw("NOGAME"),
            intro = UiText.raw("INTRO"),
        )
        fun raw(s: String) = UiText.raw(s)
        assertEquals(raw("INTRO"), note().text)
        assertEquals(raw("NOGAME"), note(noGame = true).text)
        assertEquals(raw("BUSY"), note(noGame = true, gameBusy = true).text)
        assertEquals(UiText.res(R.string.launcher_catalog_invalid, raw("kaputt")), note(problem = raw("kaputt"), gameBusy = true).text)
        assertTrue(note(problem = raw("kaputt")).error)
        assertEquals(raw("custom fertig"), note(problem = raw("kaputt"), customMessage = raw("custom fertig")).text)
        assertFalse(note(customMessage = raw("custom fertig")).error)
        assertEquals(raw("läuft"), note(customMessage = raw("x"), customBusy = raw("läuft")).text)
        assertEquals(raw("Fehler"), note(customBusy = raw("läuft"), message = raw("Fehler")).text)
        assertTrue(note(customBusy = raw("läuft"), message = raw("Fehler")).error)
        assertEquals(raw("30 %"), note(message = raw("Fehler"), progress = raw("30 %")).text)
        assertFalse(note(message = raw("Fehler"), progress = raw("30 %")).error)
    }

    // --- Neuigkeiten: kleine Karten bleiben klein, die neueste Version nutzt den Platz ---

    @Test
    fun onlyTheLatestVersionCardGrowsWithTheFreeSpace() {
        val plan = newsPlan(true, true, 9)
        assertEquals(listOf("latest"), plan.filter { it.size.flex }.map { it.id })
        val grid = pageGrid(900f, 700f, plan.sizes())
        assertEquals(grid.rows.size, grid.rowFlex.size)
        for ((r, row) in grid.rows.withIndex()) {
            assertEquals("Reihe $r", row.any { plan[it].id == "latest" }, grid.rowFlex[r])
        }
        // Ohne flexible Karte wächst keine Reihe mit.
        assertEquals(listOf(false), pageGrid(900f, 700f, listOf(CardSize(100f, flex = false))).rowFlex)
        assertEquals(listOf(true), pageGrid(900f, 700f, listOf(CardSize(100f))).rowFlex)
    }

    @Test
    fun theLatestVersionUsesTheHeightItGets() {
        // Hoch genug für zwei Punkte zu drei Zeilen: bei zwei Punkten bleiben, die Zeilen wachsen bis sechs.
        assertEquals(NewsColumnLayout(2, 3), newsColumnLayout(200f))
        assertEquals(NewsColumnLayout(2, 6), newsColumnLayout(300f))
        // Noch mehr Platz: weitere Punkte statt längerer Texte.
        assertEquals(NewsColumnLayout(3, 4), newsColumnLayout(340f))
        // Zu wenig Platz für zwei Punkte: einer, und mindestens eine Zeile.
        assertEquals(NewsColumnLayout(1, 2), newsColumnLayout(100f))
        assertEquals(NewsColumnLayout(1, 1), newsColumnLayout(60f))
        // Größere Systemschrift: höhere Zeilen, also weniger davon.
        assertEquals(NewsColumnLayout(1, 5), newsColumnLayout(200f, fontScale = 1.3f))
        // Keine brauchbare Höhe: Mindestgröße wie früher.
        assertEquals(NewsColumnLayout(NEWS_ITEMS_SHOWN, NEWS_ITEM_LINES), newsColumnLayout(Float.NaN))
        assertEquals(NewsColumnLayout(NEWS_ITEMS_SHOWN, NEWS_ITEM_LINES), newsColumnLayout(Float.POSITIVE_INFINITY))
        // Nie mehr als die Obergrenzen.
        val huge = newsColumnLayout(5000f)
        assertTrue(huge.items <= NEWS_ITEMS_MAX && huge.lines <= NEWS_MAX_LINES)
    }

    // --- Quelltext: die Seiten bauen genau die Karten ihres Plans, Schrift nicht kleiner als vereinbart ---

    private fun wowDir(): File? =
        listOf("src/main/java/app/gamenative/ui/screen/wow", "app/src/main/java/app/gamenative/ui/screen/wow").map { File(it) }.firstOrNull { it.isDirectory }

    /** Schlüssel der Karten, die eine Seite baut: `plan.card("id"`; ein angehängtes `$index` zählt als Familie (hint, addon, prev). */
    private fun cardIdsIn(file: File): Set<String> =
        Regex("""plan\.card\(\s*"([^"]+)"""").findAll(file.readText()).map { it.groupValues[1].replace("\$index", "") }.toSet()

    private fun familyOf(id: String): String = id.trimEnd { it.isDigit() }

    private fun assertPageBuildsItsPlan(file: String, vararg plans: List<CardPlan>, inLauncherDir: Boolean = true) {
        val dir = wowDir()
        assumeTrue("Quellordner nicht gefunden", dir != null)
        val f = if (inLauncherDir) File(File(dir!!, "launcher"), file) else File(dir!!, file)
        assertTrue("$file fehlt", f.isFile)
        val built = cardIdsIn(f)
        val planned = plans.flatMap { it.map { card -> familyOf(card.id) } }.toSet()
        assertEquals("$file: Karten der Seite und des Plans", planned, built)
    }

    @Test
    fun everyPageBuildsExactlyTheCardsOfItsPlan() {
        assertPageBuildsItsPlan(
            "LauncherSettingsPage.kt",
            settingsPlan(),
            controlPlan(LauncherTexts.CONTROL_HINT_GROUPS.size),
            inLauncherDir = false,
        )
        assertPageBuildsItsPlan("AddonsPage.kt", addonsPlan(3), inLauncherDir = false)
        assertPageBuildsItsPlan("FilesPage.kt", filesPlan(FilesAlert.Install), filesPlan(FilesAlert.None))
        assertPageBuildsItsPlan("NewsPage.kt", newsPlan(true, true, 5), newsPlan(false, false, 0))
        assertPageBuildsItsPlan("HelpPage.kt", helpPlan(true))
        assertPageBuildsItsPlan("AboutPage.kt", aboutPlan())
    }

    @Test
    fun theNewPageFilesUseNoSmallFontAndNoColorLiterals() {
        val dir = wowDir()
        assumeTrue("Quellordner nicht gefunden", dir != null)
        val launcher = File(dir!!, "launcher")
        val names = listOf("PageCards.kt", "PageScaffold.kt", "FilesPage.kt", "NewsPage.kt", "HelpPage.kt", "PageTexts.kt", "PagePlans.kt", "PageGrid.kt")
        val fontSize = Regex("""fontSize\s*=\s*(\d+(?:\.\d+)?)\.sp""")
        for (n in names) {
            val f = File(launcher, n)
            assertTrue("$n fehlt", f.isFile)
            val text = f.readText()
            val small = fontSize.findAll(text).filter { it.groupValues[1].toFloat() < 14f }.map { it.value }.toList()
            assertTrue("$n: Schrift unter 14 sp: $small", small.isEmpty())
            assertFalse("$n: Farbliteral", Regex("""Color\(\s*0x""").containsMatchIn(text))
        }
        // Titel der Karten in Cinzel mindestens 16 sp
        val scaffold = File(launcher, "PageScaffold.kt").readText()
        assertTrue(scaffold.contains("fontFamily = LauncherFonts.Cinzel"))
        assertNotNull(Regex("""fontSize = 16\.sp,\s*lineHeight = 22\.sp,\s*fontWeight = FontWeight\.Bold,\s*fontFamily = LauncherFonts\.Cinzel""").find(scaffold))
    }

    @Test
    fun backClosesTheDetailBeforeThePage() {
        val dir = wowDir()
        assumeTrue("Quellordner nicht gefunden", dir != null)
        val scaffold = File(File(dir!!, "launcher"), "PageScaffold.kt").readText()
        // Die Seite hört nur zu, solange keine Detailansicht offen ist; die Detailansicht fängt Zurück selbst ab und schließt sich.
        assertTrue(scaffold.contains("BackHandler(enabled = detail == null)"))
        assertTrue(scaffold.contains("BackHandler(onBack = detail.onClose)"))
        assertTrue(scaffold.contains("if (detail != null) DetailSheet(detail)"))
    }
}
