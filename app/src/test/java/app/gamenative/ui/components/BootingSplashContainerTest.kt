package app.gamenative.ui.components

import app.gamenative.R
import app.gamenative.TestTexts
import app.gamenative.lab.LabIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootingSplashContainerTest {
    private val game = LabIds.WOW_CONTAINER_ID
    private val lab = LabIds.CONTAINER_ID

    @Test
    fun gameShowsAllStepsWithItsOwnLabels() {
        assertEquals(BootStage.values().toList(), BootingSplashContainer.steps(game))
        assertEquals(BootStage.values().toList(), BootingSplashContainer.steps(null))
        for (step in BootStage.values()) assertEquals(step.labelRes, BootingSplashContainer.stepLabel(step, game))
    }

    @Test
    fun labShowsStepsUpToGraphicsAndOneProgramStep() {
        assertEquals(
            listOf(BootStage.Environment, BootStage.Wine, BootStage.Graphics, BootStage.Launch),
            BootingSplashContainer.steps(lab),
        )
        assertEquals(R.string.game_boot_stage_program, BootingSplashContainer.stepLabel(BootStage.Launch, lab))
        assertEquals(R.string.game_boot_stage_env, BootingSplashContainer.stepLabel(BootStage.Environment, lab))
        assertEquals("Programm wird gestartet", TestTexts.de(TextRef(R.string.game_boot_stage_program)))
        assertEquals("Starting program", TestTexts.en(TextRef(R.string.game_boot_stage_program)))
    }

    @Test
    fun titleIsTheGameTitleExceptInTheLab() {
        assertEquals(SplashTitle("WORLD OF WARCRAFT", "Forever · Touch"), BootingSplashContainer.title(game))
        assertEquals(SplashTitle("Battle.net Lab", null), BootingSplashContainer.title(lab))
        assertNull(BootingSplashContainer.title(lab).sub)
    }

    @Test
    fun detailLineIsReplacedOnlyInTheLab() {
        val start = BootingSplashLogic.classify(BootingSplashLogic.TEXT_START)
        val launch = BootingSplashLogic.classify("Launching game...")
        val wine = BootingSplashLogic.classify(BootingSplashLogic.TEXT_WINE)
        assertEquals(start.detail, BootingSplashContainer.detail(game, start))
        assertEquals(launch.detail, BootingSplashContainer.detail(game, launch))
        assertEquals(TextRef(R.string.game_boot_program), BootingSplashContainer.detail(lab, start))
        assertEquals(TextRef(R.string.game_boot_program), BootingSplashContainer.detail(lab, launch))
        assertEquals(wine.detail, BootingSplashContainer.detail(lab, wine))
        assertEquals("Programm wird gestartet …", TestTexts.de(TextRef(R.string.game_boot_program)))
    }

    @Test
    fun gameSplashStaysWhileRequestedWhateverTheText() {
        for (text in listOf(BootingSplashLogic.TEXT_START, BootingSplashLogic.TEXT_WINE, BootingSplashLogic.TEXT_GRAPHICS, "Launching game...", null, "")) {
            assertTrue(text.toString(), BootingSplashContainer.isVisible(game, true, text))
            assertFalse(text.toString(), BootingSplashContainer.isVisible(game, false, text))
            assertTrue(text.toString(), BootingSplashContainer.isVisible(null, true, text))
        }
    }

    @Test
    fun labSplashDisappearsWhenTheProgramIsStarted() {
        assertTrue(BootingSplashContainer.isVisible(lab, true, BootingSplashLogic.TEXT_START))
        assertTrue(BootingSplashContainer.isVisible(lab, true, BootingSplashLogic.TEXT_WINE))
        assertTrue(BootingSplashContainer.isVisible(lab, true, BootingSplashLogic.TEXT_GRAPHICS))
        assertTrue(BootingSplashContainer.isVisible(lab, true, "Preparing wine-9.0 base..."))
        assertFalse(BootingSplashContainer.isVisible(lab, true, "Launching game..."))
        assertFalse(BootingSplashContainer.isVisible(lab, false, BootingSplashLogic.TEXT_WINE))
    }
}
