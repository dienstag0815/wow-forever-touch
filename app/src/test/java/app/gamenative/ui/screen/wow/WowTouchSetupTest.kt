package app.gamenative.ui.screen.wow

import app.gamenative.data.TouchGestureConfig
import app.gamenative.ui.screen.wow.klick.TouchOptions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The touch setup of a start: the player's Quick menu switch and gesture-dialog values survive a restart and an app update,
 * the launcher's defaults only fill first setup, a changed Steuerung and "Zurücksetzen".
 */
class WowTouchSetupTest {
    private val defaults = WowTouch.gestureConfig().toJson()

    private fun plan(
        mode: InputMode = InputMode.TOUCH,
        defaults: String = this.defaults,
        hadTouchSetup: Boolean,
        touchscreen: Boolean,
        gestures: String,
        state: TouchSetupState,
    ) = TouchSetupPolicy.plan(mode, defaults, hadTouchSetup, touchscreen, gestures, state)

    /** What the container looks like after a start in [mode]; returns (touchscreen, gestures, state, hadTouchSetup). */
    private data class Container(val touchscreen: Boolean, val gestures: String, val state: TouchSetupState, val touchExtra: Boolean)

    private fun start(c: Container, mode: InputMode = InputMode.TOUCH, defaults: String = this.defaults): Container {
        val p = plan(mode, defaults, c.touchExtra, c.touchscreen, c.gestures, c.state)
        return Container(p.touchscreenMode, p.gestureConfig ?: c.gestures, p.state, mode.isTouch)
    }

    private val fresh = Container(touchscreen = false, gestures = "", state = TouchSetupState(), touchExtra = false)

    private fun withGesture(json: String, key: String, value: Any): String = JSONObject(json).put(key, value).toString()

    @Test
    fun firstStart_writesDefaults() {
        val c = start(fresh)
        assertTrue(c.touchscreen)
        assertEquals(defaults, c.gestures)
        assertEquals(defaults, c.state.gestureBase)
        assertEquals("TOUCH", c.state.mode)
    }

    @Test
    fun restart_keepsGestureChangedInDialog() {
        var c = start(fresh)
        c = c.copy(gestures = withGesture(c.gestures, "longPressDelay", 650))
        c = start(c)
        assertEquals(650, JSONObject(c.gestures).getInt("longPressDelay"))
        // fields the player did not touch still follow the defaults
        assertEquals(TouchGestureConfig.ACTION_LEFT_CLICK, JSONObject(c.gestures).getString("tapAction"))
    }

    @Test
    fun restart_keepsTouchscreenSwitchTurnedOff() {
        var c = start(fresh)
        c = c.copy(touchscreen = false)
        c = start(c)
        assertFalse(c.touchscreen)
        c = start(c)
        assertFalse(c.touchscreen)
    }

    @Test
    fun restart_keepsTouchscreenSwitchTurnedOnInStandard() {
        var c = start(fresh, InputMode.CLASSIC)
        assertFalse(c.touchscreen)
        c = c.copy(touchscreen = true)
        c = start(c, InputMode.CLASSIC)
        assertTrue(c.touchscreen)
    }

    @Test
    fun update_newDefaultsReachUntouchedFieldsOnly() {
        var c = start(fresh)
        c = c.copy(gestures = withGesture(c.gestures, "tapAction", TouchGestureConfig.ACTION_RIGHT_CLICK))
        // the new app version ships another long-press delay and another tap action
        val newDefaults = withGesture(withGesture(defaults, "longPressDelay", 500), "tapAction", TouchGestureConfig.ACTION_MIDDLE_CLICK)
        c = start(c, defaults = newDefaults)
        val json = JSONObject(c.gestures)
        assertEquals(TouchGestureConfig.ACTION_RIGHT_CLICK, json.getString("tapAction"))
        assertEquals(500, json.getInt("longPressDelay"))
        assertEquals(newDefaults, c.state.gestureBase)
    }

    @Test
    fun update_fromBuildWithoutTrackingKeepsChanges() {
        // fold7.1: the container holds the old defaults plus one change, nothing is remembered yet
        val old = withGesture(defaults, "pinchEnabled", false)
        val c = start(Container(true, old, TouchSetupState(), touchExtra = true))
        assertTrue(c.touchscreen)
        assertFalse(JSONObject(c.gestures).getBoolean("pinchEnabled"))
        assertEquals(defaults, c.state.gestureBase)
    }

    @Test
    fun update_fromBuildWithoutTrackingKeepsSwitchTurnedOff() {
        val c = start(Container(false, defaults, TouchSetupState(), touchExtra = true))
        assertFalse(c.touchscreen)
    }

    @Test
    fun launcherSwitchWinsWhenFlipped_otherwiseDialogValueStays() {
        var c = start(fresh)
        // the player turns the click wheel off in the gesture dialog: stays while the launcher switch is untouched
        c = c.copy(gestures = withGesture(c.gestures, "clickWheelEnabled", false))
        c = start(c)
        assertFalse(JSONObject(c.gestures).getBoolean("clickWheelEnabled"))
        // the launcher switch is flipped off and on again: the launcher's value is written
        val wheelOff = WowTouch.gestureConfig(TouchOptions(clickWheel = false)).toJson()
        c = start(c, defaults = wheelOff)
        assertFalse(JSONObject(c.gestures).getBoolean("clickWheelEnabled"))
        c = start(c, defaults = defaults)
        assertTrue(JSONObject(c.gestures).getBoolean("clickWheelEnabled"))
    }

    @Test
    fun standardToTouch_setsTouchscreenOnButKeepsGestureChanges() {
        var c = start(fresh, InputMode.CLASSIC)
        c = c.copy(gestures = "{\"longPressDelay\":650}")
        c = start(c, InputMode.TOUCH_CUSTOM)
        assertTrue(c.touchscreen)
        assertEquals(650, JSONObject(c.gestures).getInt("longPressDelay"))
    }

    @Test
    fun touchToTouch_keepsTouchscreenSwitchAndGestures() {
        var c = start(fresh)
        c = c.copy(touchscreen = false, gestures = withGesture(c.gestures, "longPressDelay", 650))
        for (next in listOf(InputMode.TOUCH_CUSTOM, InputMode.TOUCH_STANDARD, InputMode.TOUCH_FULL, InputMode.TOUCH)) {
            c = start(c, next)
            assertFalse("switch to $next", c.touchscreen)
            assertEquals(650, JSONObject(c.gestures).getInt("longPressDelay"))
            assertEquals(next.name, c.state.mode)
        }
    }

    @Test
    fun touchToTouch_reset_stillWritesDefaults() {
        var c = start(fresh)
        c = c.copy(touchscreen = false, state = c.state.copy(forceDefaults = true))
        c = start(c, InputMode.TOUCH_CUSTOM)
        assertTrue(c.touchscreen)
        assertEquals(defaults, c.gestures)
    }

    @Test
    fun standardAndBack_roundTripKeepsPlayersGestures() {
        var c = start(fresh)
        c = c.copy(gestures = withGesture(c.gestures, "longPressDelay", 650))
        val toStandard = start(c, InputMode.CLASSIC)
        assertFalse(toStandard.touchscreen)
        assertEquals("", toStandard.gestures)
        val back = start(toStandard, InputMode.TOUCH)
        assertTrue(back.touchscreen)
        assertEquals(650, JSONObject(back.gestures).getInt("longPressDelay"))
        assertNull(back.state.gestureStash)
    }

    @Test
    fun standardStart_leavesGestureJsonAlone() {
        val p = plan(InputMode.CLASSIC, hadTouchSetup = false, touchscreen = true, gestures = "{\"tapEnabled\":false}", state = TouchSetupState(mode = "CLASSIC"))
        assertNull(p.gestureConfig)
        assertTrue(p.touchscreenMode)
    }

    @Test
    fun reset_writesDefaultsOverEverything() {
        var c = start(fresh)
        c = c.copy(touchscreen = false, gestures = withGesture(c.gestures, "longPressDelay", 650))
        c = c.copy(state = c.state.copy(forceDefaults = true))
        c = start(c)
        assertTrue(c.touchscreen)
        assertEquals(defaults, c.gestures)
        assertFalse(c.state.forceDefaults)
        // and the next start keeps whatever the player does afterwards
        c = c.copy(gestures = withGesture(c.gestures, "longPressDelay", 700))
        assertEquals(700, JSONObject(start(c).gestures).getInt("longPressDelay"))
    }

    @Test
    fun recreatedContainer_isSetUpAgain() {
        val c = start(fresh)
        val lost = c.copy(touchscreen = false, gestures = "", touchExtra = false)
        val again = start(lost)
        assertTrue(again.touchscreen)
        assertEquals(defaults, again.gestures)
    }

    @Test
    fun corruptGestureJson_fallsBackToDefaults() {
        assertEquals(defaults, TouchSetupPolicy.mergeGestures("{nope", defaults, defaults, TouchSetupPolicy.LAUNCHER_KEYS))
    }

    @Test
    fun unknownKeysOfThePlayersConfigStay() {
        val merged = TouchSetupPolicy.mergeGestures(withGesture(defaults, "futureKey", "x"), defaults, defaults, TouchSetupPolicy.LAUNCHER_KEYS)
        assertEquals("x", JSONObject(merged).getString("futureKey"))
    }

    @Test
    fun holdTimeAndTapWindow_followTheLauncherWhenChanged_otherwiseDialogValueStays() {
        var c = start(fresh)
        c = c.copy(gestures = withGesture(c.gestures, "holdLeftDelay", 600))
        c = start(c) // launcher untouched: the player's 600 stays
        assertEquals(600, JSONObject(c.gestures).getInt("holdLeftDelay"))
        val changed = WowTouch.gestureConfig(TouchOptions(holdMs = 450, tapWindowMs = 100)).toJson()
        c = start(c, defaults = changed) // launcher changed: its values are written
        assertEquals(450, JSONObject(c.gestures).getInt("holdLeftDelay"))
        assertEquals(100, JSONObject(c.gestures).getInt("tapWindowDelay"))
    }

    @Test
    fun update_fromClickWheelBuildGetsTheHoldGesture() {
        // fold7.2 wrote no hold/window keys: the update adds them, everything the player changed stays
        val old = JSONObject(defaults).apply {
            remove("holdLeftEnabled")
            remove("holdLeftDelay")
            remove("tapWindowDelay")
            put("pinchEnabled", false)
        }.toString()
        val c = start(Container(true, old, TouchSetupState(), touchExtra = true))
        val g = JSONObject(c.gestures)
        assertTrue(g.getBoolean("holdLeftEnabled"))
        assertEquals(350, g.getInt("holdLeftDelay"))
        assertEquals(250, g.getInt("tapWindowDelay"))
        assertFalse(g.getBoolean("pinchEnabled"))
    }

    // ---- Seitenmenü > Steuerung: Werte im Spiel setzen (Einstellung = Wahrheit) -------------------------------------------

    /** What Seitenmenü > Steuerung does when a value is set: container gestures and the launcher's base follow the preference. */
    private fun inGame(c: Container, options: TouchOptions): Container = Container(
        c.touchscreen,
        TouchSetupPolicy.applyOptions(c.gestures, options),
        c.state.copy(gestureBase = TouchSetupPolicy.rebaseGestures(c.state.gestureBase, options)),
        c.touchExtra,
    )

    private fun defaultsFor(options: TouchOptions) = WowTouch.gestureConfig(options).toJson()

    @Test
    fun inGameValue_survivesTheNextStart() {
        var c = start(fresh)
        val set = TouchOptions(holdMs = 550, tapWindowMs = 100, clickWheel = false, twoFingerRightClick = false)
        c = inGame(c, set)
        // The launcher's defaults now come from the preference the game just wrote.
        c = start(c, defaults = defaultsFor(set))
        val g = JSONObject(c.gestures)
        assertEquals(550, g.getInt("holdLeftDelay"))
        assertEquals(100, g.getInt("tapWindowDelay"))
        assertFalse(g.getBoolean("clickWheelEnabled"))
        assertFalse(g.getBoolean("twoFingerTapEnabled"))
    }

    @Test
    fun launcherChangeAfterAnInGameValue_stillWins_evenBackToTheOldValue() {
        var c = start(fresh)
        c = inGame(c, TouchOptions(holdMs = 550))
        c = start(c, defaults = defaultsFor(TouchOptions(holdMs = 550)))
        // The launcher goes back to 350: without the moved base this would look like "launcher unchanged" and keep 550.
        c = start(c, defaults = defaultsFor(TouchOptions(holdMs = 350)))
        assertEquals(350, JSONObject(c.gestures).getInt("holdLeftDelay"))
    }

    @Test
    fun inGameValue_leavesOtherGestureFieldsAndUnknownKeysAlone() {
        var c = start(fresh)
        c = c.copy(gestures = withGesture(withGesture(c.gestures, "pinchEnabled", false), "futureKey", 7))
        c = inGame(c, TouchOptions(holdMs = 600))
        val g = JSONObject(c.gestures)
        assertFalse(g.getBoolean("pinchEnabled"))
        assertEquals(7, g.getInt("futureKey"))
        assertEquals(600, g.getInt("holdLeftDelay"))
        c = start(c, defaults = defaultsFor(TouchOptions(holdMs = 600)))
        assertFalse(JSONObject(c.gestures).getBoolean("pinchEnabled"))
    }

    @Test
    fun applyOptions_keepsBlankAndBrokenJsonAsItIs() {
        assertEquals("", TouchSetupPolicy.applyOptions("", TouchOptions(holdMs = 600)))
        assertEquals("kaputt", TouchSetupPolicy.applyOptions("kaputt", TouchOptions(holdMs = 600)))
        assertNull(TouchSetupPolicy.rebaseGestures(null, TouchOptions(holdMs = 600)))
    }

    @Test
    fun optionsFromGestures_clampToTheLauncherRanges() {
        val config = TouchGestureConfig(holdLeftDelay = 1500, tapWindowDelay = 900, clickWheelEnabled = false, twoFingerTapEnabled = false)
        val o = TouchSetupPolicy.optionsFrom(config, TouchOptions())
        assertEquals(TouchOptions.MAX_HOLD_MS, o.holdMs)
        assertEquals(TouchOptions.MAX_TAP_WINDOW_MS, o.tapWindowMs)
        assertFalse(o.clickWheel)
        assertFalse(o.twoFingerRightClick)
        assertEquals(TouchOptions().wheelAssignment, o.wheelAssignment)
    }
}
