package app.gamenative.ui.screen.wow.klick

import org.junit.Rule
import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierLatchTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private fun latch() = ModifierLatch(tapMaxMs = 300, doubleTapMs = 400)

    @Test
    fun timesAreThreeHundredAndFourHundredMilliseconds() {
        assertEquals(300L, ModifierLatch.TAP_MAX_MS)
        assertEquals(400L, ModifierLatch.DOUBLE_TAP_MS)
    }

    @Test
    fun singleTap_keepsTheKeyDownForTheNextClick() {
        val l = latch()
        assertEquals(KeyChange.DOWN, l.press(1000))
        assertEquals(LatchMode.HELD, l.mode)
        assertEquals(KeyChange.NONE, l.release(1100))
        assertEquals(LatchMode.ONCE, l.mode)
        assertTrue(l.keyDown)
    }

    @Test
    fun onceIsUsedUpByTheNextClick() {
        val l = latch()
        l.press(1000); l.release(1100)
        assertEquals(KeyChange.UP, l.consume())
        assertEquals(LatchMode.OFF, l.mode)
        assertFalse(l.keyDown)
        assertEquals(KeyChange.NONE, l.consume()) // a second click finds nothing to release
    }

    @Test
    fun doubleTap_locksUntilTappedAgain() {
        val l = latch()
        l.press(1000); l.release(1100)
        assertEquals(KeyChange.NONE, l.press(1300)) // key is down already
        assertEquals(KeyChange.NONE, l.release(1380))
        assertEquals(LatchMode.LOCKED, l.mode)
        assertTrue(l.keyDown)

        // clicks do not release a locked key
        assertEquals(KeyChange.NONE, l.consume())
        assertEquals(KeyChange.NONE, l.consume())
        assertEquals(LatchMode.LOCKED, l.mode)

        // tapped again: off
        l.press(5000)
        assertEquals(KeyChange.UP, l.release(5100))
        assertEquals(LatchMode.OFF, l.mode)
    }

    @Test
    fun secondTapTooLate_cancelsInsteadOfLocking() {
        val l = latch()
        l.press(1000); l.release(1100)
        l.press(1600) // 500 ms after the first lift > 400 ms
        assertEquals(KeyChange.UP, l.release(1700))
        assertEquals(LatchMode.OFF, l.mode)
    }

    @Test
    fun secondTapExactlyOnTheWindow_stillLocks() {
        val l = latch()
        l.press(1000); l.release(1100)
        l.press(1500) // 400 ms after the lift
        l.release(1550)
        assertEquals(LatchMode.LOCKED, l.mode)
    }

    @Test
    fun holding_pressesOnlyWhileTheFingerStays() {
        val l = latch()
        assertEquals(KeyChange.DOWN, l.press(1000))
        assertEquals(KeyChange.UP, l.release(1300)) // 300 ms is already a hold
        assertEquals(LatchMode.OFF, l.mode)
    }

    @Test
    fun tapJustUnderTheLimit_isATap() {
        val l = latch()
        l.press(1000)
        l.release(1299)
        assertEquals(LatchMode.ONCE, l.mode)
    }

    @Test
    fun holdWhileLocked_stillTurnsOff() {
        val l = latch()
        l.press(0); l.release(50); l.press(100); l.release(150)
        assertEquals(LatchMode.LOCKED, l.mode)
        l.press(2000)
        assertEquals(KeyChange.UP, l.release(3000))
        assertEquals(LatchMode.OFF, l.mode)
    }

    @Test
    fun holdWhileOnce_turnsOffOnLift() {
        val l = latch()
        l.press(0); l.release(50)
        l.press(100)
        assertEquals(KeyChange.UP, l.release(900))
        assertEquals(LatchMode.OFF, l.mode)
    }

    @Test
    fun clickWhileHeld_keepsTheKeyAndDoesNotArmTheNextClick() {
        val l = latch()
        l.press(1000)
        assertEquals(KeyChange.NONE, l.consume()) // the click with the held key
        assertTrue(l.keyDown)
        // the finger lifts quickly, but the key was already used: no latch for the next click
        assertEquals(KeyChange.UP, l.release(1200))
        assertEquals(LatchMode.OFF, l.mode)
    }

    @Test
    fun cancelledTouch_neverArms() {
        val l = latch()
        l.press(1000)
        assertEquals(KeyChange.UP, l.release(1050, wasCancelled = true))
        assertEquals(LatchMode.OFF, l.mode)
    }

    @Test
    fun consumeWhileOnceAndFingerOnTheButtonAgain_endsOff() {
        val l = latch()
        l.press(0); l.release(50)
        l.press(100)
        assertEquals(KeyChange.UP, l.consume()) // a click on another finger used it up
        assertEquals(KeyChange.NONE, l.release(150))
        assertEquals(LatchMode.OFF, l.mode)
    }

    @Test
    fun secondPointerOnTheSameButton_isIgnored() {
        val l = latch()
        assertEquals(KeyChange.DOWN, l.press(0))
        assertEquals(KeyChange.NONE, l.press(10))
        assertEquals(KeyChange.NONE, l.release(100))
        assertEquals(KeyChange.NONE, l.release(120)) // no finger down any more
        assertEquals(LatchMode.ONCE, l.mode)
    }

    @Test
    fun reset_releasesAnythingThatIsDown() {
        val l = latch()
        l.press(0); l.release(50); l.press(100); l.release(150)
        assertEquals(KeyChange.UP, l.reset())
        assertEquals(LatchMode.OFF, l.mode)
        assertEquals(KeyChange.NONE, l.reset())
    }
}

class StickyModifiersTest {
    private val events = ArrayList<String>()

    private fun attachFresh() {
        StickyModifiers.attach(Any()) { key, down -> events += "${key.name}:${if (down) "down" else "up"}" }
        events.clear()
    }

    @Test
    fun tapShiftThenClick_sendsShiftDownAndUpAroundTheClick() {
        attachFresh()
        StickyModifiers.press(ModKey.SHIFT, 1000)
        StickyModifiers.release(ModKey.SHIFT, 1100, false)
        assertEquals(LatchMode.ONCE, StickyModifiers.mode(ModKey.SHIFT))
        assertEquals(setOf(ModKey.SHIFT), StickyModifiers.held())
        assertEquals(listOf("SHIFT:down"), events)

        StickyModifiers.consume()
        assertEquals(listOf("SHIFT:down", "SHIFT:up"), events)
        assertTrue(StickyModifiers.held().isEmpty())
    }

    @Test
    fun modifiersAreIndependent() {
        attachFresh()
        StickyModifiers.press(ModKey.SHIFT, 0); StickyModifiers.release(ModKey.SHIFT, 50, false)
        StickyModifiers.press(ModKey.CTRL, 100); StickyModifiers.release(ModKey.CTRL, 150, false)
        assertEquals(setOf(ModKey.SHIFT, ModKey.CTRL), StickyModifiers.held())
        StickyModifiers.consume()
        assertTrue(StickyModifiers.held().isEmpty())
        assertEquals(listOf("SHIFT:down", "CTRL:down", "SHIFT:up", "CTRL:up"), events)
    }

    @Test
    fun lockedShift_survivesClicks_andReleaseAllEndsIt() {
        attachFresh()
        StickyModifiers.press(ModKey.SHIFT, 0); StickyModifiers.release(ModKey.SHIFT, 50, false)
        StickyModifiers.press(ModKey.SHIFT, 100); StickyModifiers.release(ModKey.SHIFT, 150, false)
        StickyModifiers.consume()
        StickyModifiers.consume()
        assertEquals(LatchMode.LOCKED, StickyModifiers.mode(ModKey.SHIFT))
        assertEquals(listOf("SHIFT:down"), events)
        StickyModifiers.releaseAll()
        assertEquals(listOf("SHIFT:down", "SHIFT:up"), events)
        assertTrue(StickyModifiers.held().isEmpty())
    }

    @Test
    fun newOwner_startsWithAllKeysUp() {
        attachFresh()
        StickyModifiers.press(ModKey.ALT, 0); StickyModifiers.release(ModKey.ALT, 50, false)
        assertTrue(StickyModifiers.isDown(ModKey.ALT))
        attachFresh()
        assertFalse(StickyModifiers.isDown(ModKey.ALT))
    }

    @Test
    fun sameOwner_keepsItsState() {
        val owner = Any()
        StickyModifiers.attach(owner) { _, _ -> }
        StickyModifiers.press(ModKey.CTRL, 0); StickyModifiers.release(ModKey.CTRL, 50, false)
        StickyModifiers.attach(owner) { _, _ -> }
        assertTrue(StickyModifiers.isDown(ModKey.CTRL))
        StickyModifiers.releaseAll()
    }

    @Test
    fun whichButtonsLatch() {
        assertEquals(ModKey.SHIFT, StickyModifiers.modifierOfButton("KEY_SHIFT_L", "NONE"))
        assertEquals(ModKey.SHIFT, StickyModifiers.modifierOfButton("KEY_SHIFT_R", "NONE"))
        assertEquals(ModKey.CTRL, StickyModifiers.modifierOfButton("KEY_CTRL_L", "NONE"))
        assertEquals(ModKey.CTRL, StickyModifiers.modifierOfButton("NONE", "KEY_CTRL_R"))
        assertEquals(ModKey.ALT, StickyModifiers.modifierOfButton("KEY_ALT_L", "NONE"))
        assertNull(StickyModifiers.modifierOfButton("KEY_SPACE", "NONE"))
        assertNull(StickyModifiers.modifierOfButton("NONE", "NONE"))
        assertNull(StickyModifiers.modifierOfButton("KEY_SHIFT_L", "KEY_A")) // a button with a second key behaves as before
        assertNull(StickyModifiers.modifierOfButton("KEY_SHIFT_L", "KEY_CTRL_L"))
        assertNull(StickyModifiers.modifierOfButton(null, "NONE"))
        assertNull(StickyModifiers.modifierOfBinding("GYRO_MODIFIER"))
    }

    @Test
    fun whatUsesUpASingleUseModifier() {
        assertTrue(StickyModifiers.consumedBy("left_click"))
        assertTrue(StickyModifiers.consumedBy("right_click"))
        assertTrue(StickyModifiers.consumedBy("middle_click"))
        assertTrue(StickyModifiers.consumedBy("key_SPACE"))
        assertTrue(StickyModifiers.consumedBy("key_ESC"))
        assertFalse(StickyModifiers.consumedBy("key_SHIFT_L"))
        assertFalse(StickyModifiers.consumedBy("key_CTRL_R"))
        assertFalse(StickyModifiers.consumedBy("key_ALT_L"))
        assertFalse(StickyModifiers.consumedBy("show_keyboard"))
        assertFalse(StickyModifiers.consumedBy("open_radial_menu"))
        assertFalse(StickyModifiers.consumedBy(null))
    }
}
