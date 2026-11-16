package app.gamenative.lab

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spielsteuerung im Labor (labspiel, Punkt 1): die Werte (Durchstich aus/an), der Umschalter und die Automatik über die Fensterliste. */
class LabGameControlsTest {
    private val gameScreenSource = File("src/main/java/app/gamenative/ui/screen/wow/WoWForeverScreen.kt").readText()
    private val xServerSource = File("src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt").readText()

    private fun plan() = LabCommand.build(
        LabSettings(),
        LabTarget.BATTLENET,
        LabCommandContext(baseEnv = "A=b", battleNetExe = "C:\\Program Files (x86)\\Battle.net\\Battle.net.exe"),
    )

    /** Ein Spiel-Container, wie `applyInputSettings` ihn schreibt (Touch (WoW)): Touch an, Gesten, Extras `wowTouch` und `profileId`. */
    private fun gameJson(touch: Boolean = true, wowTouch: String? = "1", profile: String? = "9", gestures: String = """{"tapEnabled":true,"showCursorInTouchscreenMode":false}""", shooter: Boolean = false) =
        JSONObject().apply {
            put("id", "wow_forever")
            put("touchscreenMode", touch)
            put("shooterMode", shooter)
            if (gestures.isNotEmpty()) put("gestureConfig", gestures)
            put("extraData", JSONObject().apply {
                wowTouch?.let { put("wowTouch", it) }
                profile?.let { put("profileId", it) }
            })
        }

    // --- Durchstich: Schalter aus ---

    @Test
    fun switchOffIsBitIdenticalToWhatTheLabWritesToday() {
        // Die Werte von heute, festgehalten (Stand 2.3.3-touch9.29): `LabContainer.config` und `ControlsVisibilityPref.save(..., false)`.
        val today = LabControlValues(
            touchscreenMode = true, shooterMode = false, gestureConfig = "", shooterConfig = "",
            wowTouch = null, profileId = null, controlsVisible = false,
        )
        assertEquals(today, LabGameControls.OFF)

        val config = LabContainer.config(LabContainer.Base(JSONObject()), plan(), "L:/x")
        assertEquals(true, config.getBoolean("touchscreenMode"))
        assertEquals(false, config.getBoolean("shooterMode"))
        // Das Labor schreibt weder Gesten noch Extras noch Profil: der Container behält seine Vorgaben.
        for (key in listOf("gestureConfig", "shooterConfig", "extraData", "profileId", "wowTouch", "disableMouseInput")) {
            assertFalse("the lab writes $key", config.has(key))
        }
        assertEquals(LabGameControls.OFF.touchscreenMode, config.getBoolean("touchscreenMode"))
        assertEquals(LabGameControls.OFF.shooterMode, config.getBoolean("shooterMode"))
    }

    @Test
    fun theLabStillSavesControlsHiddenAtPrepare() {
        val source = File("src/main/java/app/gamenative/lab/LabContainer.kt").readText()
        assertTrue(source.contains("ControlsVisibilityPref.save(context, LabIds.CONTAINER_ID, false)"))
        assertFalse(LabGameControls.OFF.controlsVisible)
    }

    @Test
    fun theLabNeverWritesTheGameControlsIntoTheLabContainer() {
        // Die Umschaltung ist nur live: kein Aufruf, der den Labor-Container mit Spielwerten speichert.
        val apply = File("src/main/java/app/gamenative/lab/LabGameControlsApply.kt").readText()
        assertFalse(apply.contains("saveData"))
        assertFalse(apply.contains("putExtra"))
        assertFalse(apply.contains("ControlsVisibilityPref.save"))
    }

    // --- Durchstich: Schalter an ---

    @Test
    fun switchOnTakesTheValuesTheGameAreaWroteForItsContainer() {
        val values = LabGameControls.fromGameContainer(gameJson(), storedVisible = null)
        assertNotNull(values)
        values!!
        assertTrue(values.touchscreenMode)
        assertFalse(values.shooterMode)
        assertEquals("""{"tapEnabled":true,"showCursorInTouchscreenMode":false}""", values.gestureConfig)
        assertEquals("1", values.wowTouch)
        assertEquals("9", values.profileId)
        assertEquals(9, values.profileNumber)
        assertTrue("Touch (WoW) shows the on-screen controls", values.controlsVisible)
    }

    @Test
    fun theKeysAreTheOnesTheGameAreaWritesAndTheScreenReads() {
        assertTrue(gameScreenSource.contains("container.putExtra(WowTouch.EXTRA_KEY, input.wowTouchExtra)"))
        assertTrue(File("src/main/java/app/gamenative/ui/screen/wow/WowInputSettings.kt").readText().contains("const val EXTRA_KEY = \"${LabGameControls.EXTRA_WOW_TOUCH}\""))
        assertTrue(gameScreenSource.contains("container.putExtra(\"${LabGameControls.EXTRA_PROFILE_ID}\""))
        assertTrue(gameScreenSource.contains("container.setTouchscreenMode(plan.touchscreenMode)"))
        assertTrue(gameScreenSource.contains("container.setGestureConfig(it)"))
        assertTrue(xServerSource.contains("container.getExtra(\"${LabGameControls.EXTRA_WOW_TOUCH}\") == \"1\""))
        assertTrue(xServerSource.contains("container.getExtra(\"${LabGameControls.EXTRA_PROFILE_ID}\""))
        // Die gespeicherten Namen im Container (`Container.saveData`).
        val container = File("src/main/java/com/winlator/container/Container.java").readText()
        for (key in listOf("touchscreenMode", "shooterMode", "gestureConfig", "shooterConfig", "extraData")) {
            assertTrue("Container.saveData does not write $key", container.contains("data.put(\"$key\""))
        }
    }

    @Test
    fun withoutAGameContainerThereAreNoValues() {
        assertNull(LabGameControls.fromGameContainer(null, null))
    }

    @Test
    fun aMissingValueTakesTheDefaultOfTheContainerClass() {
        val values = LabGameControls.fromGameContainer(JSONObject().put("id", "wow_forever"), null)!!
        assertFalse(values.touchscreenMode)
        assertTrue("Container.java: shooterMode = true", values.shooterMode)
        assertEquals("", values.gestureConfig)
        assertNull(values.wowTouch)
        assertNull(values.profileId)
        assertEquals(0, values.profileNumber)
        assertTrue("without touch the on-screen controls are shown (XServerScreen)", values.controlsVisible)
    }

    @Test
    fun theControlsAreShownByTheRuleOfTheGameScreen() {
        // Touch ohne `wowTouch`: versteckt (XServerScreen: `container.isTouchscreenMode -> false`).
        assertFalse(LabGameControls.fromGameContainer(gameJson(touch = true, wowTouch = null), null)!!.controlsVisible)
        // Touch (WoW): sichtbar, auch bei eingeschaltetem Touch-Schalter.
        assertTrue(LabGameControls.fromGameContainer(gameJson(touch = true, wowTouch = "1"), null)!!.controlsVisible)
        // Standard (kein Touch): sichtbar.
        assertTrue(LabGameControls.fromGameContainer(gameJson(touch = false, wowTouch = null), null)!!.controlsVisible)
        // Der gespeicherte Sichtbarkeitswert des Spiel-Containers gewinnt, wenn er „aus“ sagt.
        assertFalse(LabGameControls.fromGameContainer(gameJson(), storedVisible = false)!!.controlsVisible)
        assertTrue(LabGameControls.fromGameContainer(gameJson(), storedVisible = true)!!.controlsVisible)
    }

    @Test
    fun anEmptyExtraIsTreatedAsNotSet() {
        val values = LabGameControls.fromGameContainer(gameJson(wowTouch = "", profile = ""), null)!!
        assertNull(values.wowTouch)
        assertNull(values.profileId)
    }

    @Test
    fun theValuesOffAndOnDiffer() {
        val on = LabGameControls.fromGameContainer(gameJson(), null)!!
        assertTrue(on != LabGameControls.OFF)
        assertTrue(LabGameControls.describe(on).contains("wowTouch=1"))
        assertTrue(LabGameControls.describe(LabGameControls.OFF).contains("controls=hidden"))
    }

    // --- LoginGuard bleibt im Labor wirksam, ob die Spielsteuerung an oder aus ist ---

    @Test
    fun theLoginGuardStillBlocksTypingCredentialsInTheLab() {
        val guard = app.gamenative.ui.screen.wow.LoginGuard
        // Der Schalter ändert weder die Container-Kennung noch den Lauf des Labors: beides entscheidet die Sperre.
        assertFalse(guard.mayType(LabIds.CONTAINER_ID, labActive = false))
        assertFalse(guard.mayType(LabIds.CONTAINER_ID, labActive = true))
        assertFalse(guard.mayType(LabIds.WOW_CONTAINER_ID, labActive = true))
        assertFalse(guard.mayWriteFile(labActive = true))
        assertTrue("the game itself may still type", guard.mayType(LabIds.WOW_CONTAINER_ID, labActive = false))
        // Der neue Code spricht weder die Sperre noch das Tippen von Zugangsdaten an.
        for (name in listOf("LabGameControls.kt", "LabGameControlsApply.kt", "LabGameSetup.kt")) {
            val text = File("src/main/java/app/gamenative/lab/$name").readText()
            for (word in listOf("LoginGuard", "LoginFill", "BattleNetSignIn", "login.txt", "injectKeyPress")) {
                assertFalse("$name mentions $word", text.contains(word))
            }
        }
    }

    // --- Umschalter und Automatik ---

    private fun win(cls: String, focused: Boolean = true) =
        LabWinEntry(id = 1, className = cls, title = "t", x = 0, y = 0, width = 100, height = 100, topmost = true, focused = focused, closable = false)

    private fun snap(vararg entries: LabWinEntry) = LabWinSnapshot(entries.toList(), LabFocusKind.WINDOW, entries.firstOrNull()?.id)

    private val launcher = win("Chrome_WidgetWin_0")
    private val game = win("wow-arm64.exe")

    @Test
    fun theRunStartsOffAndManual() {
        assertEquals("Game controls: off (manual)", LabGameControlsLogic.headerLine(LabGcState()))
    }

    @Test
    fun theHeaderOfTheProtocolNamesGameControls() {
        val head = LabProtocol.header(LabProtocolInfo(0L, "1.0", "d", "a", LabSettings(), plan(), 1L))
        assertTrue(head.contains("\nGame controls: off (manual)\n"))
        assertFalse("no setup line when the setup did not run", head.contains("Game setup on copy"))
        val withSetup = LabProtocol.header(LabProtocolInfo(0L, "1.0", "d", "a", LabSettings(), plan(), 1L, gameSetupLine = "Game setup on copy: Config.wtf, addon"))
        assertTrue(withSetup.contains("\nGame setup on copy: Config.wtf, addon\nGame controls: off (manual)\n"))
        // Beide Zeilen stehen im Kopf, vor `== Settings ==`.
        assertTrue(withSetup.indexOf("Game controls:") < withSetup.indexOf("== Settings =="))
    }

    @Test
    fun aTapSwitchesOnAndOffManually() {
        val on = LabGameControlsLogic.onTap(LabGcState())
        assertTrue(on.state.on)
        assertEquals(LabGcChange(true, LabGcSource.MANUAL, "switch"), on.change)
        assertEquals("Game controls: on (manual)", LabGameControlsLogic.headerLine(on.state))
        val off = LabGameControlsLogic.onTap(on.state)
        assertFalse(off.state.on)
        assertFalse(off.change!!.on)
    }

    @Test
    fun theAutomaticSwitchesOnWhenTheGameWindowHasTheFocus() {
        var step = LabGameControlsLogic.onSnapshot(LabGcState(), snap(launcher), 1_000)
        assertFalse(step.state.on)
        assertNull(step.change)
        step = LabGameControlsLogic.onSnapshot(step.state, snap(game, launcher.copy(focused = false)), 2_000)
        assertTrue(step.state.on)
        assertEquals(LabGcSource.AUTO, step.state.source)
        assertEquals(true, step.change?.on)
        assertEquals("Game controls: on (auto)", LabGameControlsLogic.headerLine(step.state))
    }

    @Test
    fun aGameWindowWithoutFocusDoesNotSwitchOn() {
        val step = LabGameControlsLogic.onSnapshot(LabGcState(), snap(launcher, game.copy(focused = false)), 1_000)
        assertFalse(step.state.on)
        assertNull(step.change)
    }

    @Test
    fun theClassIsComparedWithoutCase() {
        val step = LabGameControlsLogic.onSnapshot(LabGcState(), snap(win("WOW-ARM64.EXE")), 1_000)
        assertTrue(step.state.on)
    }

    @Test
    fun theAutomaticSwitchesOffOnlyAfterTheWindowWasGoneForAWhile() {
        val on = LabGameControlsLogic.onSnapshot(LabGcState(), snap(game), 1_000).state
        // Das Fenster öffnet sich beim Start kurz mehrfach neu: ein Schnappschuss ohne Fenster schaltet nicht aus.
        var step = LabGameControlsLogic.onSnapshot(on, snap(launcher), 2_000)
        assertTrue(step.state.on)
        step = LabGameControlsLogic.onSnapshot(step.state, snap(game), 2_500)
        assertTrue(step.state.on)
        assertNull(step.state.absentSinceMs)
        step = LabGameControlsLogic.onSnapshot(step.state, snap(launcher), 3_000)
        step = LabGameControlsLogic.onSnapshot(step.state, snap(launcher), 3_000 + LabGameControlsLogic.GONE_AFTER_MS - 1)
        assertTrue(step.state.on)
        step = LabGameControlsLogic.onSnapshot(step.state, snap(launcher), 3_000 + LabGameControlsLogic.GONE_AFTER_MS)
        assertFalse(step.state.on)
        assertEquals(LabGcChange(false, LabGcSource.AUTO, "window wow-arm64.exe is gone"), step.change)
    }

    @Test
    fun theAutomaticNeverSwitchesOffWhatTheUserSwitchedOn() {
        val on = LabGameControlsLogic.onTap(LabGcState()).state
        var step = LabGameControlsLogic.onSnapshot(on, snap(launcher), 1_000)
        step = LabGameControlsLogic.onSnapshot(step.state, snap(launcher), 1_000 + 10 * LabGameControlsLogic.GONE_AFTER_MS)
        assertTrue(step.state.on)
        assertNull(step.change)
    }

    @Test
    fun switchingOffByHandKeepsTheAutomaticOffUntilTheWindowWasGone() {
        val auto = LabGameControlsLogic.onSnapshot(LabGcState(), snap(game), 1_000).state
        val off = LabGameControlsLogic.onTap(auto).state
        assertFalse(off.on)
        // Das Spielfenster ist weiter da und hat den Fokus: die Automatik schaltet nicht wieder ein.
        var step = LabGameControlsLogic.onSnapshot(off, snap(game), 2_000)
        assertFalse(step.state.on)
        assertNull(step.change)
        // Das Fenster verschwindet lange genug, dann darf die Automatik wieder.
        step = LabGameControlsLogic.onSnapshot(step.state, snap(launcher), 3_000)
        step = LabGameControlsLogic.onSnapshot(step.state, snap(launcher), 3_000 + LabGameControlsLogic.GONE_AFTER_MS)
        assertFalse(step.state.autoSuppressed)
        step = LabGameControlsLogic.onSnapshot(step.state, snap(game), 20_000)
        assertTrue(step.state.on)
    }

    @Test
    fun switchingOffWithoutAGameWindowDoesNotBlockTheAutomatic() {
        val on = LabGameControlsLogic.onTap(LabGcState()).state
        val off = LabGameControlsLogic.onTap(on).state
        assertFalse(off.autoSuppressed)
        assertTrue(LabGameControlsLogic.onSnapshot(off, snap(game), 1_000).state.on)
    }

    @Test
    fun whenTheValuesAreMissingTheStateGoesBackToOffAndDoesNotRetryAtOnce() {
        val tried = LabGameControlsLogic.onSnapshot(LabGcState(), snap(game), 1_000).state
        val failed = LabGameControlsLogic.applyFailed(tried)
        assertFalse(failed.on)
        assertTrue(failed.autoSuppressed)
        assertFalse(LabGameControlsLogic.onSnapshot(failed, snap(game), 2_000).state.on)
    }

    @Test
    fun theLogLineHasTheTimeSinceStart() {
        val line = LabGameControlsLogic.changeLine(LabGcChange(true, LabGcSource.AUTO, "window wow-arm64.exe has focus"), 12_345, applied = true)
        assertEquals("-- Game controls: on (auto, window wow-arm64.exe has focus) after 12.3 s", line)
        val failed = LabGameControlsLogic.changeLine(LabGcChange(true, LabGcSource.MANUAL, "switch"), 500, applied = false)
        assertTrue(failed.contains("after 0.5 s"))
        assertTrue(failed.contains("NOT applied"))
    }

    @Test
    fun theGameClassIsTheOneOfTheWindowList() {
        // Protokoll 153707: `class=wow-arm64.exe`.
        assertEquals("wow-arm64.exe", LabGameControlsLogic.GAME_CLASS)
        assertTrue(LabGameControlsLogic.isGameWindow(game))
        assertFalse(LabGameControlsLogic.isGameWindow(launcher))
    }
}
