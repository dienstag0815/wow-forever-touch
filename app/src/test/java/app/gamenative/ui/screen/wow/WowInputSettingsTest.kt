package app.gamenative.ui.screen.wow

import org.junit.Rule
import app.gamenative.TestTexts
import app.gamenative.data.TouchGestureConfig
import app.gamenative.ui.screen.wow.klick.TouchOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WowInputSettingsTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    @Test
    fun gainStepsAreAsSpecified() {
        assertEquals(listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f), STICK_GAIN_STEPS)
    }

    @Test
    fun labelsUseDecimalComma() {
        assertEquals("0,75", gainLabel(0.75f))
        assertEquals("1,0", gainLabel(1.0f))
        assertEquals("3,0", gainLabel(3.0f))
    }

    @Test
    fun unknownSavedGainFallsBackToDefault() {
        assertEquals(1.5f, sanitizeGain(1.5f, DEFAULT_WALK_GAIN), 0f)
        assertEquals(DEFAULT_WALK_GAIN, sanitizeGain(7f, DEFAULT_WALK_GAIN), 0f)
        assertEquals(DEFAULT_CAMERA_GAIN, sanitizeGain(0f, DEFAULT_CAMERA_GAIN), 0f)
    }

    @Test
    fun touchGestures_tapLeft_longPressRight_dragCamera_pinchWheel() {
        val g = WowTouch.gestureConfig()
        assertTrue(g.tapEnabled)
        assertEquals(TouchGestureConfig.ACTION_LEFT_CLICK, g.tapAction)
        assertTrue(g.longPressEnabled)
        assertEquals(TouchGestureConfig.ACTION_RIGHT_CLICK, g.longPressAction)
        assertEquals(400, g.longPressDelay)
        assertTrue(g.dragEnabled)
        assertEquals(TouchGestureConfig.PAN_RIGHT_CLICK_DRAG, g.dragAction)
        assertEquals(TouchGestureConfig.MOUSE_DRAG_MOVEMENT_RELATIVE, g.mouseDragMovementMode)
        assertTrue(g.pinchEnabled)
        assertEquals(TouchGestureConfig.ZOOM_SCROLL_WHEEL, g.pinchAction)
        assertFalse(g.showCursorInTouchscreenMode)
    }

    @Test
    fun touchGestures_strayMultiFingerActionsAreOff() {
        val g = WowTouch.gestureConfig()
        assertFalse(g.doubleTapEnabled)
        assertFalse(g.twoFingerDragEnabled)
        assertFalse(g.twoFingerHoldEnabled)
        assertFalse(g.threeFingerDragEnabled)
    }

    @Test
    fun touchGestures_twoFingerTapIsRightClickAndClickWheelIsOnByDefault() {
        val g = WowTouch.gestureConfig()
        assertTrue(g.twoFingerTapEnabled)
        assertEquals(TouchGestureConfig.ACTION_RIGHT_CLICK, g.twoFingerTapAction)
        assertTrue(g.clickWheelEnabled)
        assertEquals(400, g.longPressDelay)
    }

    @Test
    fun touchGestures_optionsSwitchTheTwoExtrasOffIndependently() {
        val noTap = WowTouch.gestureConfig(TouchOptions(twoFingerRightClick = false))
        assertFalse(noTap.twoFingerTapEnabled)
        assertTrue(noTap.clickWheelEnabled)
        val noWheel = WowTouch.gestureConfig(TouchOptions(clickWheel = false))
        assertTrue(noWheel.twoFingerTapEnabled)
        assertFalse(noWheel.clickWheelEnabled)
        // without the wheel the hold is the plain right click again
        assertTrue(noWheel.longPressEnabled)
        assertEquals(TouchGestureConfig.ACTION_RIGHT_CLICK, noWheel.longPressAction)
    }

    @Test
    fun touchLaunch_carriesTheOptionsIntoTheContainerJson() {
        val s = WowTouch.settingsFor(InputMode.TOUCH, wasTouch = false, options = TouchOptions(twoFingerRightClick = false, clickWheel = false))
        val g = TouchGestureConfig.fromJson(s.gestureJson)
        assertFalse(g.twoFingerTapEnabled)
        assertFalse(g.clickWheelEnabled)
    }

    @Test
    fun touchGestures_surviveJsonRoundTrip() {
        val g = WowTouch.gestureConfig()
        assertEquals(g, TouchGestureConfig.fromJson(g.toJson()))
    }

    @Test
    fun touchLaunch_setsTouchscreenMouseStyleAndMarker() {
        val s = WowTouch.settingsFor(InputMode.TOUCH, wasTouch = false)
        assertTrue(s.touchscreenMode)
        assertEquals("1", s.wowTouchExtra)
        assertEquals(WowTouch.STYLE_MOUSE, s.interfaceStyle)
        assertEquals(WowTouch.gestureConfig().toJson(), s.gestureJson)
    }

    @Test
    fun classicAfterTouch_undoesIt() {
        val s = WowTouch.settingsFor(InputMode.CLASSIC, wasTouch = true)
        assertFalse(s.touchscreenMode)
        assertEquals(null, s.wowTouchExtra)
        assertEquals(WowTouch.STYLE_GAMEPAD, s.interfaceStyle)
    }

    @Test
    fun classicNeverTouched_changesNothing() {
        val s = WowTouch.settingsFor(InputMode.CLASSIC, wasTouch = false)
        assertFalse(s.touchscreenMode)
        assertEquals(null, s.interfaceStyle)
        assertEquals(null, s.wowTouchExtra)
    }

    @Test
    fun touchProfileAsset_startsWithTheLeftWalkStick() {
        val file = java.io.File("src/main/assets/inputcontrols/profiles/controls-6.icp")
        val json = org.json.JSONObject(file.readText())
        assertEquals(WowTouch.PROFILE_NAME, json.getString("name"))
        assertEquals(6, json.getInt("id"))
        val elements = json.getJSONArray("elements")
        val stick = elements.getJSONObject(0)
        assertEquals("STICK", stick.getString("type"))
        assertEquals("GAMEPAD_LEFT_THUMB_UP", stick.getJSONArray("bindings").getString(0))
    }

    @Test
    fun customTouchLaunch_isTheSameSetupAsTouch() {
        assertEquals(WowTouch.settingsFor(InputMode.TOUCH, wasTouch = false), WowTouch.settingsFor(InputMode.TOUCH_CUSTOM, wasTouch = false))
        assertEquals(WowTouch.settingsFor(InputMode.TOUCH, wasTouch = true), WowTouch.settingsFor(InputMode.TOUCH_CUSTOM, wasTouch = true))
    }

    @Test
    fun inputModes_profileNames() {
        assertEquals(null, InputMode.CLASSIC.profileName)
        assertFalse(InputMode.CLASSIC.isTouch)
        assertEquals("Touch (WoW)", InputMode.TOUCH.profileName)
        assertEquals("Touch (Mein Layout)", InputMode.TOUCH_CUSTOM.profileName)
        assertTrue(InputMode.TOUCH.isTouch)
        assertTrue(InputMode.TOUCH_CUSTOM.isTouch)
    }

    @Test
    fun customProfileName_isNotAnAssetProfile() {
        // An asset with this name would let app updates reach the player's own layout.
        val dir = java.io.File("src/main/assets/inputcontrols/profiles")
        val names = dir.listFiles()!!.map { org.json.JSONObject(it.readText()).getString("name") }
        assertFalse(names.contains(WowTouch.CUSTOM_PROFILE_NAME))
    }

    @Test
    fun touchGestures_holdIsTheLeftButton_withTheLauncherTimes() {
        val g = WowTouch.gestureConfig()
        assertTrue(g.holdLeftEnabled)
        assertEquals(350, g.holdLeftDelay)
        assertEquals(250, g.tapWindowDelay)
        val custom = WowTouch.gestureConfig(TouchOptions(holdMs = 500, tapWindowMs = 0))
        assertTrue(custom.holdLeftEnabled)
        assertEquals(500, custom.holdLeftDelay)
        assertEquals(0, custom.tapWindowDelay)
        // the hold stays the left button even when the wheel is switched off
        assertTrue(WowTouch.gestureConfig(TouchOptions(clickWheel = false)).holdLeftEnabled)
    }

    @Test
    fun touchOptions_defaultsAndLimits() {
        val o = TouchOptions()
        assertEquals(350, o.holdMs)
        assertEquals(250, o.tapWindowMs)
        assertEquals(200, TouchOptions.MIN_HOLD_MS)
        assertEquals(800, TouchOptions.MAX_HOLD_MS)
        assertEquals(400, TouchOptions.MAX_TAP_WINDOW_MS)
    }
}
