package app.gamenative.ui.screen.wow

import org.junit.Rule
import app.gamenative.ui.components.TextRef
import app.gamenative.TestTexts
import app.gamenative.ui.component.QuickMenuAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class QuickMenuLayoutTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    /** Alle Lagen: 7 Wahrheitswerte, 128 Kombinationen. */
    private val allContexts: List<QuickMenuContext> = buildList {
        for (bits in 0 until 128) {
            fun on(i: Int) = bits and (1 shl i) != 0
            add(
                QuickMenuContext(
                    inLogin = on(0),
                    hasSavedLogin = on(1),
                    hasLoginNote = on(2),
                    hasReportNote = on(3),
                    hasPhysicalController = on(4),
                    hasContainer = on(5),
                    hasTouchLayouts = on(6),
                ),
            )
        }
    }

    private val everything = QuickMenuContext(
        inLogin = true, hasSavedLogin = true, hasLoginNote = true, hasReportNote = true,
        hasPhysicalController = true, hasContainer = true,
    )

    /** Alle IDs aus [QuickMenuAction] (per Reflexion: eine neue ID ohne Zuordnung lässt den Test rot werden). */
    private fun allActionIds(): Set<Int> =
        QuickMenuAction::class.java.declaredFields
            // `\$stable` (Compose-Compiler, Wert 0) und andere synthetische Felder ausnehmen
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && !it.isSynthetic && !it.name.startsWith("\$") }
            .map { it.getInt(null) }
            .toSet()

    private fun idsOf(page: QuickMenuPage, ctx: QuickMenuContext): List<Int> =
        quickMenuSections(page, ctx).flatMap { it.ids }

    @Test
    fun everyEntryIsAssignedExactlyOnceInTheFullContext() {
        val assigned = QuickMenuPage.entries.flatMap { idsOf(it, everything) }
        assertEquals("keine ID doppelt", assigned.size, assigned.toSet().size)
        assertEquals(
            "jede ID genau einmal, ausser den Zeilen ausserhalb der Gliederung",
            allActionIds() - QUICK_MENU_OUTSIDE_LAYOUT,
            assigned.toSet(),
        )
    }

    @Test
    fun noEntryAppearsTwiceInAnyContext() {
        for (ctx in allContexts) {
            val assigned = QuickMenuPage.entries.flatMap { idsOf(it, ctx) }
            assertEquals("doppelte ID in $ctx", assigned.size, assigned.toSet().size)
        }
    }

    @Test
    fun noSectionIsEmptyAndEveryTitleIsSet() {
        for (ctx in allContexts) {
            for (page in QuickMenuPage.entries) {
                for (section in quickMenuSections(page, ctx)) {
                    assertTrue("leerer Abschnitt ${section.title} in $ctx", section.ids.isNotEmpty())
                    assertTrue(TestTexts.de(section.title).isNotBlank())
                }
            }
        }
    }

    @Test
    fun loginEntriesOnlyAppearInTheLoginContext() {
        for (ctx in allContexts.filter { !it.inLogin }) {
            val all = QuickMenuPage.entries.flatMap { idsOf(it, ctx) }
            assertTrue("Anmeldezeile in der Spielwelt: $ctx", all.none { it in QUICK_MENU_LOGIN_IDS })
        }
        val login = QuickMenuPage.entries.flatMap { idsOf(it, everything) }
        assertTrue(login.containsAll(QUICK_MENU_LOGIN_IDS))
    }

    @Test
    fun loginEntriesSitInTheirOwnSectionAtTheTopOfTheGameTab() {
        val sections = quickMenuSections(QuickMenuPage.SPIEL, everything)
        assertEquals("Anmeldung", TestTexts.de(sections.first().title))
        assertEquals(
            listOf(QuickMenuAction.LOGIN_FULL, QuickMenuAction.LOGIN_PASSWORD, QuickMenuAction.LOGIN_EMAIL, QuickMenuAction.LOGIN_NOTE),
            sections.first().ids,
        )
        // In der Spielwelt entfaellt der ganze Abschnitt, nicht nur seine Zeilen.
        val world = quickMenuSections(QuickMenuPage.SPIEL, everything.copy(inLogin = false))
        assertFalse(world.any { TestTexts.de(it.title) == "Anmeldung" })
    }

    @Test
    fun partialLoginIsOnlyOfferedWhenSaved() {
        val noSaved = idsOf(QuickMenuPage.SPIEL, everything.copy(hasSavedLogin = false, hasLoginNote = false))
        assertTrue(QuickMenuAction.LOGIN_FULL in noSaved)
        assertFalse(QuickMenuAction.LOGIN_PASSWORD in noSaved)
        assertFalse(QuickMenuAction.LOGIN_EMAIL in noSaved)
        assertFalse(QuickMenuAction.LOGIN_NOTE in noSaved)
    }

    @Test
    fun conditionalEntriesFollowTheirCondition() {
        val without = QuickMenuContext(hasPhysicalController = false, hasContainer = false, hasReportNote = false)
        val steuerungOff = idsOf(QuickMenuPage.STEUERUNG, without)
        assertFalse(QuickMenuAction.EDIT_PHYSICAL_CONTROLLER in steuerungOff)
        assertFalse(QuickMenuAction.GYRO in steuerungOff)
        assertFalse(QuickMenuAction.REPORT_NOTE in idsOf(QuickMenuPage.HILFE, without))

        val steuerungOn = idsOf(QuickMenuPage.STEUERUNG, everything)
        assertTrue(QuickMenuAction.EDIT_PHYSICAL_CONTROLLER in steuerungOn)
        assertTrue(QuickMenuAction.GYRO in steuerungOn)
        assertTrue(QuickMenuAction.REPORT_NOTE in idsOf(QuickMenuPage.HILFE, everything))
    }

    @Test
    fun noteRowsFollowTheirActionRow() {
        val spiel = idsOf(QuickMenuPage.SPIEL, everything)
        assertEquals(QuickMenuAction.LOGIN_NOTE, spiel[spiel.indexOf(QuickMenuAction.LOGIN_EMAIL) + 1])
        val hilfe = idsOf(QuickMenuPage.HILFE, everything)
        assertEquals(
            listOf(QuickMenuAction.REPORT, QuickMenuAction.REPORT_NOTE, QuickMenuAction.CHECK_ICONS, QuickMenuAction.ADDON_OPTIONS),
            hilfe,
        )
    }

    private fun steuerung(ctx: QuickMenuContext = everything) = quickMenuSections(QuickMenuPage.STEUERUNG, ctx)

    @Test
    fun controlTabHasTheEightGroupsInOrder() {
        assertEquals(
            listOf("Layout", "Sticks", "Tippen und Halten", "Klick-Rad", "Wischen", "Touch-Elemente", "Weitere Eingabemodi", "Mehr"),
            steuerung().map { TestTexts.de(it.title) },
        )
        assertEquals(
            listOf(
                QuickMenuGroups.LAYOUT, QuickMenuGroups.STICKS, QuickMenuGroups.TAP_HOLD, QuickMenuGroups.CLICK_WHEEL,
                QuickMenuGroups.SWIPE, QuickMenuGroups.ELEMENTS, QuickMenuGroups.MODES, QuickMenuGroups.MORE,
            ),
            steuerung().map { it.groupId },
        )
    }

    @Test
    fun layoutIsAlwaysOpenAndTheRestCollapsesInEveryContext() {
        for (ctx in allContexts) {
            val sections = steuerung(ctx)
            assertFalse("Layout klappt nicht", sections.first().collapsible)
            assertTrue(sections.drop(1).all { it.collapsible })
            assertEquals(sections.size, sections.mapNotNull { it.groupId }.toSet().size)
        }
    }

    @Test
    fun groupsHoldTheRowsOfTheConcept() {
        val byTitle = steuerung().associate { TestTexts.de(it.title) to it.ids }
        assertEquals(
            listOf(QuickMenuAction.LAYOUT_CHOOSE, QuickMenuAction.EDIT_CONTROLS, QuickMenuAction.LAYOUT_SAVE_AS, QuickMenuAction.INPUT_CONTROLS),
            byTitle["Layout"],
        )
        assertEquals(listOf(QuickMenuAction.RESET_LAYOUT, QuickMenuAction.EDIT_PHYSICAL_CONTROLLER), byTitle["Mehr"])
        assertEquals(listOf(QuickMenuAction.STICK_WALK, QuickMenuAction.STICK_CAMERA), byTitle["Sticks"])
        assertEquals(
            listOf(
                QuickMenuAction.TOUCHSCREEN_MODE, QuickMenuAction.TWO_FINGER_TAP, QuickMenuAction.HOLD_TIME,
                QuickMenuAction.TAP_WINDOW, QuickMenuAction.CONTEXT_MENU, QuickMenuAction.TAP_VIEW_ITEMS,
            ),
            byTitle["Tippen und Halten"],
        )
        assertEquals(
            listOf(
                QuickMenuAction.CLICK_WHEEL, QuickMenuAction.WHEEL_CENTER, QuickMenuAction.WHEEL_UP,
                QuickMenuAction.WHEEL_RIGHT, QuickMenuAction.WHEEL_DOWN, QuickMenuAction.WHEEL_LEFT,
            ),
            byTitle["Klick-Rad"],
        )
        assertEquals(listOf(QuickMenuAction.SWIPE_SCROLL, QuickMenuAction.SWIPE_SPEED), byTitle["Wischen"])
        assertEquals(
            listOf(
                QuickMenuAction.TOUCH_HIDE_LOGIN, QuickMenuAction.TOUCH_SHOW_NOW, QuickMenuAction.TOUCH_HIDE_WINDOWS,
                QuickMenuAction.TOUCH_STYLE, QuickMenuAction.TOUCH_CONTRAST,
            ),
            byTitle["Touch-Elemente"],
        )
        assertEquals(
            listOf(QuickMenuAction.SHOOTER_MODE, QuickMenuAction.GYRO, QuickMenuAction.DISABLE_MOUSE, QuickMenuAction.RADIAL_MENU),
            byTitle["Weitere Eingabemodi"],
        )
    }

    @Test
    fun sticksGroupIsCalledSticksAndPointsToTheEditor() {
        // Der Touch-Editor verweist auf "Seitenmenü > Steuerung > Sticks": der Name darf sich nicht ändern.
        val sticks = steuerung().single { it.groupId == QuickMenuGroups.STICKS }
        assertEquals("Sticks", TestTexts.de(sticks.title))
        assertTrue(TestTexts.de(sticks.note!!).contains("Editor"))
    }

    @Test
    fun cooldownGlobalRowSitsWithTheCooldownsOnTheGameTab() {
        for (ctx in allContexts) {
            val section = quickMenuSections(QuickMenuPage.SPIEL, ctx).single { TestTexts.de(it.title) == "Symbole und Abklingzeiten" }
            assertEquals(listOf(QuickMenuAction.COOLDOWNS, QuickMenuAction.COOLDOWN_GCD), section.ids)
        }
    }

    @Test
    fun newRowsAreAssignedOnlyToTheirTab() {
        val newIds = (QuickMenuAction.TWO_FINGER_TAP..QuickMenuAction.COOLDOWN_GCD).toList()
        assertEquals(13, newIds.size)
        for (ctx in allContexts) {
            for (id in newIds) {
                val pages = QuickMenuPage.entries.filter { id in idsOf(it, ctx) }
                val expected = if (id == QuickMenuAction.COOLDOWN_GCD) QuickMenuPage.SPIEL else QuickMenuPage.STEUERUNG
                assertEquals("ID $id in $ctx", listOf(expected), pages)
            }
        }
    }

    @Test
    fun accordionKeepsAtMostOneGroupOpenAndRemembersIt() {
        val sections = steuerung()
        // noch nie gewählt: Sticks
        assertEquals(QuickMenuGroups.STICKS, QuickMenuAccordion.resolve(null, sections))
        // gespeicherte Gruppe
        assertEquals(QuickMenuGroups.SWIPE, QuickMenuAccordion.resolve(QuickMenuGroups.SWIPE, sections))
        // bewusst alles zu
        assertEquals(null, QuickMenuAccordion.resolve(QuickMenuAccordion.NONE, sections))
        // unbekannte oder nicht klappbare Gruppe: zu
        assertEquals(null, QuickMenuAccordion.resolve("gibtsnicht", sections))
        assertEquals(null, QuickMenuAccordion.resolve(QuickMenuGroups.LAYOUT, sections))
        // ohne die Gruppe im Reiter (andere Liste) fällt auch der Standard weg
        assertEquals(null, QuickMenuAccordion.resolve(null, sections.filter { it.groupId != QuickMenuGroups.STICKS }))
    }

    @Test
    fun accordionToggleClosesTheOpenOneAndSwitchesToAnother() {
        assertEquals(null, QuickMenuAccordion.toggle(QuickMenuGroups.STICKS, QuickMenuGroups.STICKS))
        assertEquals(QuickMenuGroups.SWIPE, QuickMenuAccordion.toggle(QuickMenuGroups.STICKS, QuickMenuGroups.SWIPE))
        assertEquals(QuickMenuGroups.MODES, QuickMenuAccordion.toggle(null, QuickMenuGroups.MODES))
    }

    @Test
    fun accordionStateSurvivesTheRoundTripThroughThePreference() {
        val sections = steuerung()
        var open: String? = QuickMenuAccordion.resolve(null, sections)
        // Wischen auf, dann Wischen zu: gespeichert wird "" (alles zu), nicht "nie gewählt" (das wäre wieder Sticks).
        open = QuickMenuAccordion.toggle(open, QuickMenuGroups.SWIPE)
        assertEquals(QuickMenuGroups.SWIPE, QuickMenuAccordion.resolve(QuickMenuAccordion.toSaved(open), sections))
        open = QuickMenuAccordion.toggle(open, QuickMenuGroups.SWIPE)
        assertEquals("", QuickMenuAccordion.toSaved(open))
        assertEquals(null, QuickMenuAccordion.resolve(QuickMenuAccordion.toSaved(open), sections))
    }

    @Test
    fun onlyOneGroupIsOpenForAnySavedValue() {
        val sections = steuerung()
        for (saved in listOf(null, "") + sections.mapNotNull { it.groupId }) {
            val open = QuickMenuAccordion.resolve(saved, sections)
            assertTrue(sections.count { it.collapsible && it.groupId == open } <= 1)
        }
    }

    @Test
    fun voiceAndIconsKeepTheirGroupNames() {
        // The names match the launcher's setting groups (Voice, Icons and cooldowns, Controls) in both languages.
        val titles = quickMenuSections(QuickMenuPage.SPIEL, everything).map { TestTexts.de(it.title) }
        assertTrue("Sprache" in titles)
        assertTrue("Symbole und Abklingzeiten" in titles)
        assertEquals("Steuerung", TestTexts.de(TextRef(QuickMenuPage.STEUERUNG.titleRes)))
        val english = quickMenuSections(QuickMenuPage.SPIEL, everything).map { TestTexts.en(it.title) }
        assertTrue("Voice" in english)
        assertTrue("Icons and cooldowns" in english)
        assertEquals("Controls", TestTexts.en(TextRef(QuickMenuPage.STEUERUNG.titleRes)))
    }

    @Test
    fun addonSettingsAndIconCheckSitInTheMoreSectionOfTheHelpTabInEveryContext() {
        // Selten gebraucht: nicht mehr im Reiter Spiel, sondern in "Mehr" unter Hilfe, unabhaengig von Login, Controller oder Container.
        assertEquals(26, QuickMenuAction.ADDON_OPTIONS)
        for (ctx in allContexts) {
            val section = quickMenuSections(QuickMenuPage.HILFE, ctx).single { TestTexts.de(it.title) == "Mehr" }
            assertEquals(listOf(QuickMenuAction.CHECK_ICONS, QuickMenuAction.ADDON_OPTIONS), section.ids)
            assertFalse("Mehr in Hilfe klappt nicht", section.collapsible)
            for (page in QuickMenuPage.entries.filter { it != QuickMenuPage.HILFE }) {
                assertFalse(QuickMenuAction.ADDON_OPTIONS in idsOf(page, ctx))
                assertFalse(QuickMenuAction.CHECK_ICONS in idsOf(page, ctx))
            }
        }
        assertFalse(QuickMenuAction.ADDON_OPTIONS in QUICK_MENU_OUTSIDE_LAYOUT)
        assertFalse(QuickMenuAction.ADDON_OPTIONS in QUICK_MENU_LOGIN_IDS)
    }

    @Test
    fun layoutRowsLeadTheControlTabInTouchModesOnly() {
        for (ctx in allContexts) {
            val layout = steuerung(ctx).first()
            assertEquals("Layout", TestTexts.de(layout.title))
            if (ctx.hasTouchLayouts) {
                // Das aktive Layout mit Namen ganz oben, "Neues Layout speichern" und Bearbeiten darunter.
                assertEquals(QuickMenuAction.LAYOUT_CHOOSE, layout.ids.first())
                assertTrue(QuickMenuAction.LAYOUT_SAVE_AS in layout.ids)
                assertTrue(QuickMenuAction.EDIT_CONTROLS in layout.ids)
            } else {
                assertEquals(listOf(QuickMenuAction.EDIT_CONTROLS, QuickMenuAction.INPUT_CONTROLS), layout.ids)
            }
            for (page in QuickMenuPage.entries.filter { it != QuickMenuPage.STEUERUNG }) {
                assertFalse(QuickMenuAction.LAYOUT_CHOOSE in idsOf(page, ctx))
                assertFalse(QuickMenuAction.LAYOUT_SAVE_AS in idsOf(page, ctx))
            }
        }
        assertEquals(44, QuickMenuAction.LAYOUT_CHOOSE)
        assertEquals(45, QuickMenuAction.LAYOUT_SAVE_AS)
    }

    @Test
    fun noTabShowsMoreThanSevenRowsByDefault() {
        // Sichtbar = Zeilen der immer offenen Abschnitte plus die der offenen Gruppe (Voreinstellung Sticks); Kopfzeilen zaehlen nicht.
        // Im Login (4 Anmeldezeilen) darf der Reiter Spiel um die Anmeldung laenger sein, die gibt es nur dort.
        for (ctx in allContexts.filter { !it.inLogin }) {
            for (page in QuickMenuPage.entries) {
                val sections = quickMenuSections(page, ctx)
                val open = QuickMenuAccordion.resolve(null, sections)
                val visible = sections.filter { !it.collapsible || it.groupId == open }.sumOf { it.ids.size }
                assertTrue("$page zeigt $visible Zeilen in $ctx", visible <= 7)
            }
        }
    }

    @Test
    fun tabIdsStayStableAndUnique() {
        // 3 und 4 gehoeren den frueheren Reitern Controller und Task-Manager (gespeicherter letzter Reiter), 5 ist neu.
        assertEquals(3, QuickMenuPage.STEUERUNG.tabId)
        assertEquals(4, QuickMenuPage.HILFE.tabId)
        assertEquals(5, QuickMenuPage.SPIEL.tabId)
        assertEquals(QuickMenuPage.entries.size, QuickMenuPage.entries.map { it.tabId }.toSet().size)
    }
}
