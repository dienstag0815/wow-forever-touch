package app.gamenative.ui.components

import app.gamenative.R
import app.gamenative.TestTexts
import app.gamenative.ui.components.BootingSplashLogic.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootingSplashLogicTest {

    // --- Zuordnung Splash-Text -> Stufe (alle Quelltexte aus dem Code) ---

    @Test
    fun everySourceTextMapsToItsStage() {
        val cases = listOf(
            BootingSplashLogic.TEXT_START to BootStage.Environment, // PluviaMain, XServerScreen
            BootingSplashLogic.TEXT_ENV to BootStage.Environment, // MainState
            "Booting..." to BootStage.Environment, // früherer MainState-Vorgabewert
            "Initializing..." to BootStage.Environment, // früherer BootingSplash-Vorgabewert
            "Preparing wine-9.0 base..." to BootStage.Environment, // BasePrefix.java
            "Migrating Wine prefix..." to BootStage.Environment, // ContainerOverlayMigrator.java
            BootingSplashLogic.TEXT_WINE to BootStage.Wine, // XServerScreen
            BootingSplashLogic.TEXT_GRAPHICS to BootStage.Graphics, // XServerScreen
            "Launching game..." to BootStage.Launch, // XServerScreen
        )
        for ((text, stage) in cases) {
            assertEquals(text, stage, BootingSplashLogic.classify(text).stage)
        }
    }

    @Test
    fun sourceTextsGetTheirDetailLines() {
        assertEquals(TextRef(R.string.game_boot_base, "wine-9.0"), BootingSplashLogic.classify("Preparing wine-9.0 base...").detail)
        assertEquals(TextRef(R.string.game_boot_prefix), BootingSplashLogic.classify("Migrating Wine prefix...").detail)
        assertEquals(TextRef(R.string.game_boot_launch), BootingSplashLogic.classify("Launching game...").detail)
        assertEquals(TextRef(R.string.game_boot_start), BootingSplashLogic.classify(BootingSplashLogic.TEXT_START).detail)
    }

    @Test
    fun detailLinesKeepTheirGermanWording() {
        fun de(text: String) = TestTexts.de(BootingSplashLogic.classify(text).detail)
        assertEquals("Wine-Grundlage wine-9.0 wird aufgebaut (nur beim ersten Start oder nach einem Update) …", de("Preparing wine-9.0 base..."))
        assertEquals("Wine-Präfix wird übernommen …", de("Migrating Wine prefix..."))
        assertEquals("Spiel wird gestartet …", de("Launching game..."))
        assertEquals("WoW wird gestartet …", de(BootingSplashLogic.TEXT_START))
        assertEquals("Umgebung wird vorbereitet …", de(BootingSplashLogic.TEXT_ENV))
        assertEquals("Wine wird eingerichtet …", de(BootingSplashLogic.TEXT_WINE))
        assertEquals("Grafiktreiber werden geladen …", de(BootingSplashLogic.TEXT_GRAPHICS))
    }

    @Test
    fun unknownOrEmptyTextIsOnlyADetailLineAndNeverThrows() {
        val unknown = BootingSplashLogic.classify("Etwas ganz Neues")
        assertNull(unknown.stage)
        assertEquals(TextRef.plain("Etwas ganz Neues"), unknown.detail)
        assertNull(BootingSplashLogic.classify("").stage)
        assertNull(BootingSplashLogic.classify(null).stage)
        assertNull(BootingSplashLogic.classify("   ").stage)
        assertNotNull(BootingSplashLogic.classify("Preparing  base"))
    }

    // --- Reihenfolge bleibt monoton ---

    @Test
    fun stageNeverJumpsBack() {
        val texts = listOf(
            "Booting...", "Preparing wine-9.0 base...", BootingSplashLogic.TEXT_WINE, "Unbekannt",
            "Migrating Wine prefix...", BootingSplashLogic.TEXT_GRAPHICS, BootingSplashLogic.TEXT_WINE,
            "Launching game...", "Preparing wine-9.0 base...", "Booting...",
        )
        var reached: BootStage? = null
        var previous = -1
        for (t in texts) {
            reached = BootingSplashLogic.advance(reached, BootingSplashLogic.classify(t).stage)
            val now = reached?.ordinal ?: -1
            assertTrue("zurückgesprungen bei $t", now >= previous)
            previous = now
        }
        assertEquals(BootStage.Launch, reached)
    }

    @Test
    fun advanceKeepsCurrentForUnknownAndTakesFirstKnown() {
        assertNull(BootingSplashLogic.advance(null, null))
        assertEquals(BootStage.Wine, BootingSplashLogic.advance(null, BootStage.Wine))
        assertEquals(BootStage.Graphics, BootingSplashLogic.advance(BootStage.Graphics, null))
        assertEquals(BootStage.Graphics, BootingSplashLogic.advance(BootStage.Graphics, BootStage.Environment))
    }

    @Test
    fun stepStatesFollowTheReachedStage() {
        assertEquals(StepState.Active, BootingSplashLogic.stepState(BootStage.Environment, null))
        assertEquals(StepState.Pending, BootingSplashLogic.stepState(BootStage.Wine, null))
        assertEquals(StepState.Done, BootingSplashLogic.stepState(BootStage.Environment, BootStage.Graphics))
        assertEquals(StepState.Done, BootingSplashLogic.stepState(BootStage.Wine, BootStage.Graphics))
        assertEquals(StepState.Active, BootingSplashLogic.stepState(BootStage.Graphics, BootStage.Graphics))
        assertEquals(StepState.Pending, BootingSplashLogic.stepState(BootStage.Launch, BootStage.Graphics))
        assertEquals(StepState.Pending, BootingSplashLogic.stepState(BootStage.Login, BootStage.Launch))
    }

    // --- Hinweise: Gamepad nur mit Gamepad ---

    @Test
    fun gamepadHintOnlyWithGamepad() {
        assertEquals(TextRef(R.string.game_boot_back_pad), BootingSplashLogic.backHint(true))
        assertEquals(TextRef(R.string.game_boot_back), BootingSplashLogic.backHint(false))
        assertTrue(TestTexts.de(BootingSplashLogic.backHint(true)).contains("Start + Select + L2 + R2"))
        assertTrue(TestTexts.en(BootingSplashLogic.backHint(true)).contains("Start + Select + L2 + R2"))
        assertFalse(TestTexts.de(BootingSplashLogic.backHint(false)).contains("L2"))
        assertFalse(TestTexts.en(BootingSplashLogic.backHint(false)).contains("L2"))
    }

    @Test
    fun tipsMentionGamepadOnlyWithGamepad() {
        assertTrue(TextRef(R.string.game_boot_tip_pad) in BootingSplashLogic.tips(true))
        assertTrue(TextRef(R.string.game_boot_tip_pad) !in BootingSplashLogic.tips(false))
        assertTrue(BootingSplashLogic.tips(false).none { TestTexts.de(it).contains("L2") || TestTexts.en(it).contains("L2") })
        assertTrue(BootingSplashLogic.tips(false).isNotEmpty())
    }
}
