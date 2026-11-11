package app.gamenative.ui.screen.wow

import org.junit.Rule
import app.gamenative.ui.components.TextRef
import app.gamenative.ui.components.AppText
import app.gamenative.TestTexts
import app.gamenative.ui.component.QuickMenuAction
import app.gamenative.ui.screen.wow.context.ContextMode
import app.gamenative.ui.screen.wow.klick.TouchOptions
import app.gamenative.ui.screen.wow.klick.WheelDirection
import app.gamenative.ui.touchstyle.TouchStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zeilen, Kurzinfos und Stufen des Seitenmenüs (reine Daten, siehe QuickMenuRows.kt). */
class QuickMenuRowsTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private val everything = QuickMenuContext(
        inLogin = true, hasSavedLogin = true, hasLoginNote = true, hasReportNote = true,
        hasPhysicalController = true, hasContainer = true,
    )

    private val defaults = QuickMenuValues()

    private val variants: List<QuickMenuValues> = listOf(
        defaults,
        defaults.copy(hasLogin = true, loginNote = "Anmeldedaten gespeichert", addonNote = "Addon: Taste unbekannt", layoutNote = "x"),
        defaults.copy(clickWheel = false, swipeScroll = false, twoFingerRightClick = false, cooldowns = true),
        defaults.copy(contextMode = ContextMode.BAR, windowHide = WindowHideMode.OFF, style = TouchStyle.G, tapWindowMs = 0),
    )

    private fun allIds(): List<Int> = QuickMenuPage.entries.flatMap { page -> quickMenuSections(page, everything).flatMap { it.ids } }

    @Test
    fun everyRowOfTheLayoutHasAKindAndAView() {
        for (id in allIds()) {
            val kind = quickMenuRowKind(id)
            assertNotNull("Art fehlt für ID $id", kind)
            for (v in variants) {
                val view = quickMenuRowView(id, v)
                assertNotNull("Zeile fehlt für ID $id", view)
                assertEquals("Art stimmt nicht für ID $id", kind, view!!.kind)
            }
        }
    }

    @Test
    fun rowsOutsideTheLayoutHaveNoKind() {
        for (id in QUICK_MENU_OUTSIDE_LAYOUT) {
            assertNull(quickMenuRowKind(id))
            assertNull(quickMenuRowView(id, defaults))
        }
    }

    @Test
    fun titlesAreShortSingleLinesWithoutValue() {
        for (id in allIds()) {
            for (v in variants) {
                val view = quickMenuRowView(id, v)!!
                if (view.kind == QuickMenuRowKind.ICON || view.kind == QuickMenuRowKind.NOTE) continue
                for (language in listOf("de", "en")) {
                    val title = TestTexts.text(view.title, language)
                    assertTrue("Titel leer bei ID $id ($language)", title.isNotBlank())
                    assertTrue("Titel zu lang bei ID $id ($language): $title", title.length <= QUICK_MENU_TITLE_MAX)
                    assertFalse("Zeilenumbruch im Titel bei ID $id", title.contains('\n'))
                    assertFalse("Titel mit Wert ('Titel: Wert') bei ID $id: $title", title.contains(':'))
                }
            }
        }
    }

    @Test
    fun stepAndValueRowsShowAValueOnTheRight() {
        for (id in allIds()) {
            val view = quickMenuRowView(id, defaults.copy(voiceLabel = "English"))!!
            if (view.kind == QuickMenuRowKind.STEP || view.kind == QuickMenuRowKind.VALUE) {
                assertTrue("Wert fehlt bei ID $id", TestTexts.de(view.value).isNotBlank())
            }
        }
    }

    @Test
    fun choiceRowsHaveThreeChipsAndASelectedOne() {
        for (id in listOf(QuickMenuAction.CONTEXT_MENU, QuickMenuAction.TOUCH_HIDE_WINDOWS, QuickMenuAction.TOUCH_STYLE)) {
            for (v in variants) {
                val view = quickMenuRowView(id, v)!!
                assertEquals(3, view.chips.size)
                assertTrue(view.selected in view.chips.indices)
            }
        }
        assertEquals(ContextMode.entries.size, CONTEXT_CHIPS.size)
        assertEquals(WindowHideMode.entries.size, WINDOW_HIDE_CHIPS.size)
        assertEquals(TouchStyle.entries.size, STYLE_CHIPS.size)
    }

    @Test
    fun choiceDetailShowsTheFullNameOnlyWhereTheChipShortensIt() {
        val auto = quickMenuRowView(QuickMenuAction.CONTEXT_MENU, defaults)!!
        assertEquals(1, auto.selected)
        assertEquals("Automatisch", TestTexts.de(auto.detail!!))
        val off = quickMenuRowView(QuickMenuAction.CONTEXT_MENU, defaults.copy(contextMode = ContextMode.OFF))!!
        assertNull(off.detail)
        val covered = quickMenuRowView(QuickMenuAction.TOUCH_HIDE_WINDOWS, defaults)!!
        assertEquals(TextRef(WindowHideMode.COVERED.labelRes), covered.detail)
    }

    @Test
    fun valuesAreShownLikeTheLauncherShowsThem() {
        assertEquals("1,25", TestTexts.de(quickMenuRowView(QuickMenuAction.STICK_WALK, defaults)!!.value))
        assertEquals("1,0", TestTexts.de(quickMenuRowView(QuickMenuAction.STICK_CAMERA, defaults)!!.value))
        assertEquals("350 ms", TestTexts.de(quickMenuRowView(QuickMenuAction.HOLD_TIME, defaults)!!.value))
        assertEquals("250 ms", TestTexts.de(quickMenuRowView(QuickMenuAction.TAP_WINDOW, defaults)!!.value))
        assertEquals("aus", TestTexts.de(quickMenuRowView(QuickMenuAction.TAP_WINDOW, defaults.copy(tapWindowMs = 0))!!.value))
        assertEquals("1x", TestTexts.de(quickMenuRowView(QuickMenuAction.SWIPE_SPEED, defaults)!!.value))
        assertEquals("100 %", TestTexts.de(quickMenuRowView(QuickMenuAction.TOUCH_CONTRAST, defaults)!!.value))
    }

    @Test
    fun englishDecimalsUsePoints() {
        AppText.testGerman = false
        try {
            assertEquals("1.25", TestTexts.en(quickMenuRowView(QuickMenuAction.STICK_WALK, defaults)!!.value))
            assertEquals("Walk 1.25 · Camera 1.0", TestTexts.en(quickMenuGroupSummary(QuickMenuGroups.STICKS, defaults)))
        } finally {
            AppText.testGerman = true
        }
    }

    @Test
    fun wheelRowsListTheActionOfTheirDirection() {
        val rows = listOf(
            QuickMenuAction.WHEEL_CENTER to ("Mitte" to "Linksklick"),
            QuickMenuAction.WHEEL_UP to ("Oben" to "Shift-Klick"),
            QuickMenuAction.WHEEL_RIGHT to ("Rechts" to "Strg-Klick"),
            QuickMenuAction.WHEEL_DOWN to ("Unten" to "Rechtsklick"),
            QuickMenuAction.WHEEL_LEFT to ("Links" to "Alt-Klick"),
        )
        for ((id, expected) in rows) {
            val view = quickMenuRowView(id, defaults)!!
            assertEquals(expected.first, TestTexts.de(view.title))
            assertEquals(expected.second, TestTexts.de(view.value))
            assertTrue(view.enabled)
            assertFalse("aus, wenn das Rad aus ist", quickMenuRowView(id, defaults.copy(clickWheel = false))!!.enabled)
        }
        assertEquals(WheelDirection.UP, wheelDirectionOf(QuickMenuAction.WHEEL_UP))
        assertNull(wheelDirectionOf(QuickMenuAction.HOLD_TIME))
    }

    @Test
    fun dependentRowsAreDimmedWhenTheirSwitchIsOff() {
        assertFalse(quickMenuRowView(QuickMenuAction.SWIPE_SPEED, defaults.copy(swipeScroll = false))!!.enabled)
        assertTrue(quickMenuRowView(QuickMenuAction.SWIPE_SPEED, defaults)!!.enabled)
        assertFalse(quickMenuRowView(QuickMenuAction.COOLDOWN_GCD, defaults.copy(cooldowns = false))!!.enabled)
        assertTrue(quickMenuRowView(QuickMenuAction.COOLDOWN_GCD, defaults.copy(cooldowns = true))!!.enabled)
    }

    @Test
    fun loginRowFollowsTheSavedLogin() {
        assertEquals("Anmelden", TestTexts.de(quickMenuRowView(QuickMenuAction.LOGIN_FULL, defaults.copy(hasLogin = true))!!.title))
        assertEquals("Anmeldedaten speichern", TestTexts.de(quickMenuRowView(QuickMenuAction.LOGIN_FULL, defaults)!!.title))
        val note = quickMenuRowView(QuickMenuAction.LOGIN_NOTE, defaults.copy(loginNote = "Tippe ein ..."))!!
        assertEquals(QuickMenuRowKind.NOTE, note.kind)
        assertEquals("Tippe ein ...", TestTexts.de(note.value))
    }

    @Test
    fun notesTravelWithTheirRow() {
        assertEquals("Addon: nicht bereit", quickMenuRowView(QuickMenuAction.ADDON_OPTIONS, defaults.copy(addonNote = "Addon: nicht bereit"))!!.note)
        assertNull(quickMenuRowView(QuickMenuAction.ADDON_OPTIONS, defaults)!!.note)
        assertEquals("fertig", quickMenuRowView(QuickMenuAction.RESET_LAYOUT, defaults.copy(layoutNote = "fertig"))!!.note)
    }

    // ---- Kurzinfo der zugeklappten Gruppen --------------------------------------------------------------------------------

    @Test
    fun summariesOfTheDefaults() {
        assertEquals("Laufen 1,25 · Kamera 1,0", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.STICKS, defaults)))
        assertEquals("Halten 350 ms · 2 Finger an", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.TAP_HOLD, defaults)))
        assertEquals("an · Mitte Linksklick", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.CLICK_WHEEL, defaults)))
        assertEquals("an · 1x", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.SWIPE, defaults)))
        assertEquals("G · Kontrast 100 %", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.ELEMENTS, defaults)))
        assertEquals("alle aus", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.MODES, defaults)))
    }

    @Test
    fun summariesFollowTheValues() {
        val v = defaults.copy(
            walkGain = 2.0f, cameraGain = 0.75f, holdMs = 500, twoFingerRightClick = false,
            clickWheel = false, swipeScroll = false, style = TouchStyle.G, contrast = 1.2f,
            shooterMode = true, gyro = true,
        )
        assertEquals("Laufen 2,0 · Kamera 0,75", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.STICKS, v)))
        assertEquals("Halten 500 ms · 2 Finger aus", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.TAP_HOLD, v)))
        assertEquals("aus", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.CLICK_WHEEL, v)))
        assertEquals("aus", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.SWIPE, v)))
        assertEquals("G · Kontrast 120 %", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.ELEMENTS, v)))
        assertEquals("Shooter · Gyro", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.MODES, v)))
        assertEquals("Shooter · Gyro · Maus aus", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.MODES, v.copy(mouseOff = true))))
        assertEquals("an · 2x", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.SWIPE, v.copy(swipeScroll = true, swipeSpeed = 2f))))
    }

    @Test
    fun layoutAndUnknownGroupsHaveNoSummary() {
        assertEquals("", TestTexts.de(quickMenuGroupSummary(QuickMenuGroups.LAYOUT, defaults)))
        assertEquals("", TestTexts.de(quickMenuGroupSummary(null, defaults)))
        assertEquals("", TestTexts.de(quickMenuGroupSummary("gibtsnicht", defaults)))
    }

    @Test
    fun everyCollapsibleGroupHasASummaryThatFitsOneLine() {
        val sections = quickMenuSections(QuickMenuPage.STEUERUNG, everything)
        for (section in sections.filter { it.collapsible }) {
            for (v in variants) {
                for (language in listOf("de", "en")) {
                    val text = TestTexts.text(quickMenuGroupSummary(section.groupId, v), language)
                    assertTrue("Kurzinfo fehlt: ${section.title}", text.isNotBlank())
                    assertTrue("Kurzinfo zu lang: $text", text.length <= 36)
                }
            }
        }
    }

    // ---- Stufen -----------------------------------------------------------------------------------------------------------

    @Test
    fun gainStepsStopAtTheEnds() {
        assertEquals(1.5f, QuickMenuSteps.gain(1.25f, 1))
        assertEquals(1.0f, QuickMenuSteps.gain(1.25f, -1))
        assertEquals(STICK_GAIN_STEPS.first(), QuickMenuSteps.gain(STICK_GAIN_STEPS.first(), -1))
        assertEquals(STICK_GAIN_STEPS.last(), QuickMenuSteps.gain(STICK_GAIN_STEPS.last(), 1))
        // ein Wert zwischen den Stufen (handbearbeitet) geht von der nächsten aus
        assertEquals(1.25f, QuickMenuSteps.gain(1.2f, 0))
    }

    @Test
    fun holdTimeMovesIn50StepsWithin200To800() {
        assertEquals(400, QuickMenuSteps.holdMs(350, 1))
        assertEquals(300, QuickMenuSteps.holdMs(350, -1))
        assertEquals(TouchOptions.MIN_HOLD_MS, QuickMenuSteps.holdMs(TouchOptions.MIN_HOLD_MS, -1))
        assertEquals(TouchOptions.MAX_HOLD_MS, QuickMenuSteps.holdMs(TouchOptions.MAX_HOLD_MS, 1))
    }

    @Test
    fun tapWindowMovesIn50StepsWithin0To400() {
        assertEquals(300, QuickMenuSteps.tapWindowMs(250, 1))
        assertEquals(0, QuickMenuSteps.tapWindowMs(50, -1))
        assertEquals(0, QuickMenuSteps.tapWindowMs(0, -1))
        assertEquals(TouchOptions.MAX_TAP_WINDOW_MS, QuickMenuSteps.tapWindowMs(TouchOptions.MAX_TAP_WINDOW_MS, 1))
    }

    @Test
    fun swipeSpeedAndContrastStopAtTheEnds() {
        assertEquals(1.5f, QuickMenuSteps.swipeSpeed(1f, 1))
        assertEquals(0.75f, QuickMenuSteps.swipeSpeed(1f, -1))
        assertEquals(0.5f, QuickMenuSteps.swipeSpeed(0.5f, -1))
        assertEquals(4f, QuickMenuSteps.swipeSpeed(4f, 1))
        assertEquals(1.1f, QuickMenuSteps.contrast(1.0f, 1))
        assertEquals(0.7f, QuickMenuSteps.contrast(0.7f, -1))
        assertEquals(1.5f, QuickMenuSteps.contrast(1.5f, 1))
    }
}
