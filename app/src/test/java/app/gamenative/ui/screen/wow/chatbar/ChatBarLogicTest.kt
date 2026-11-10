package app.gamenative.ui.screen.wow.chatbar

import app.gamenative.cooldown.CooldownStrip
import com.winlator.xserver.XKeycode
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBarLogicTest {
    private val owner = Any()

    @After
    fun resetTheGate() {
        ChatBarGate.setDemand(false)
        ChatBarGate.detach(owner)
    }

    private fun decoded(
        known: Boolean = true,
        flags: Int = CooldownStrip.CF_VISIBLE,
        rect: IntArray? = intArrayOf(0, 128, 128, 255),
    ): CooldownStrip.Decoded {
        val d = CooldownStrip.Decoded()
        d.chatKnown = known
        d.chatFlags = flags
        if (rect != null) {
            d.chatRectKnown = true
            rect.copyInto(d.chatRect)
        }
        return d
    }

    private fun state(
        flags: Int = CooldownStrip.CF_VISIBLE,
        rect: IntArray? = intArrayOf(0, 128, 128, 255),
    ): ChatBarState = ChatBarState.from(decoded(true, flags, rect), 1000, 500, 1000, 500)!!

    private val open = CooldownStrip.CF_VISIBLE or CooldownStrip.CF_BOX

    // ---- what a button does -------------------------------------------------------------------------------------

    @Test
    fun sendChannelAndCancelAreKeysOfTheGame() {
        assertEquals(ChatPlan.Key(XKeycode.KEY_ENTER), ChatBarLogic.plan(ChatButton.ENTER, false))
        assertEquals(ChatPlan.Key(XKeycode.KEY_ENTER), ChatBarLogic.plan(ChatButton.ENTER, true))
        assertEquals(ChatPlan.Key(XKeycode.KEY_TAB), ChatBarLogic.plan(ChatButton.CHANNEL, true))
        assertEquals(ChatPlan.Key(XKeycode.KEY_ESC), ChatBarLogic.plan(ChatButton.CANCEL, true))
        assertEquals(XKeycode.KEY_ENTER, ChatBarLogic.keyFor(ChatButton.ENTER))
        assertEquals(XKeycode.KEY_TAB, ChatBarLogic.keyFor(ChatButton.CHANNEL))
        assertEquals(XKeycode.KEY_ESC, ChatBarLogic.keyFor(ChatButton.CANCEL))
    }

    @Test
    fun voiceAppendsToTheOpenLineAndIsOrdinaryOtherwise() {
        assertEquals(ChatPlan.Voice(true), ChatBarLogic.plan(ChatButton.MIC, true))
        assertEquals(ChatPlan.Voice(false), ChatBarLogic.plan(ChatButton.MIC, false))
        assertNull(ChatBarLogic.keyFor(ChatButton.MIC))
    }

    @Test
    fun keyboardIsAnActionOfTheApp() {
        assertEquals(ChatPlan.Keyboard, ChatBarLogic.plan(ChatButton.KEYBOARD, false))
        assertNull(ChatBarLogic.keyFor(ChatButton.KEYBOARD))
    }

    @Test
    fun everyButtonHasAnIdThatFindsItAgain() {
        assertEquals(ChatButton.entries.size, ChatButton.entries.map { it.id }.toSet().size)
        for (button in ChatButton.entries) assertEquals(button, ChatButton.fromId(button.id))
        assertNull(ChatButton.fromId(null))
        assertNull(ChatButton.fromId(""))
        assertNull(ChatButton.fromId("whatever"))
    }

    // ---- when a button shows --------------------------------------------------------------------------------------

    @Test
    fun alwaysAndUnknownShowWithoutAnyChatData() {
        assertTrue(ChatBarLogic.isVisible(null, state(), false))
        assertTrue(ChatBarLogic.isVisible("nonsense", state(), false))
        // no report of the addon (old build, strip not read): never hide a button for lack of data
        assertTrue(ChatBarLogic.isVisible("chat", null, false))
        assertTrue(ChatBarLogic.isVisible("box", null, false))
    }

    @Test
    fun chatShowsWhileTheWindowIsVisibleOrTheLineIsOpen() {
        assertTrue(ChatBarLogic.isVisible("chat", state(CooldownStrip.CF_VISIBLE), false))
        assertFalse(ChatBarLogic.isVisible("chat", state(0), false))
        // typing counts even if the window itself is faded out
        assertTrue(ChatBarLogic.isVisible("chat", state(CooldownStrip.CF_BOX), false))
    }

    @Test
    fun boxShowsOnlyWhileTheLineIsOpen() {
        assertFalse(ChatBarLogic.isVisible("box", state(CooldownStrip.CF_VISIBLE), false))
        assertTrue(ChatBarLogic.isVisible("box", state(open), false))
        assertFalse(ChatBarLogic.isVisible("box", state(0), false))
    }

    @Test
    fun theEditorShowsEveryButton() {
        assertTrue(ChatBarLogic.isVisible("chat", state(0), true))
        assertTrue(ChatBarLogic.isVisible("box", state(CooldownStrip.CF_VISIBLE), true))
    }

    @Test
    fun showOptionsStoreOnlyKnownValues() {
        assertNull(ChatShow.normalize(null))
        assertNull(ChatShow.normalize("always"))
        assertNull(ChatShow.normalize("x"))
        assertEquals("chat", ChatShow.normalize("chat"))
        assertEquals("box", ChatShow.normalize("box"))
        assertEquals("tr", ChatAnchor.normalize("tr"))
        assertNull(ChatAnchor.normalize("middle"))
        assertNull(ChatAnchor.normalize(null))
    }

    // ---- pinning to the chat window ---------------------------------------------------------------------------------

    private val rect = floatArrayOf(100f, 600f, 500f, 900f)

    @Test
    fun cornersOfTheChatRectangle() {
        assertArrayEquals(floatArrayOf(100f, 600f), ChatBarLogic.cornerPoint("tl", rect)!!, 0f)
        assertArrayEquals(floatArrayOf(500f, 600f), ChatBarLogic.cornerPoint("tr", rect)!!, 0f)
        assertArrayEquals(floatArrayOf(100f, 900f), ChatBarLogic.cornerPoint("bl", rect)!!, 0f)
        assertArrayEquals(floatArrayOf(500f, 900f), ChatBarLogic.cornerPoint("br", rect)!!, 0f)
        assertNull(ChatBarLogic.cornerPoint("mm", rect))
        assertNull(ChatBarLogic.cornerPoint("tr", null))
        assertNull(ChatBarLogic.cornerPoint("tr", floatArrayOf(1f, 2f)))
    }

    @Test
    fun theButtonFollowsTheMovedChatWindow() {
        // offset 10 % of the width to the right and 5 % of the height up from the top right corner of a 1000 x 1000 view
        val before = ChatBarLogic.anchoredPosition("tr", rect, 0.1f, -0.05f, 1000, 1000)!!
        assertArrayEquals(floatArrayOf(600f, 550f), before, 0.01f)
        // the chat moved up and grew: same distance to the same corner
        val moved = floatArrayOf(200f, 300f, 700f, 600f)
        val after = ChatBarLogic.anchoredPosition("tr", moved, 0.1f, -0.05f, 1000, 1000)!!
        assertArrayEquals(floatArrayOf(800f, 250f), after, 0.01f)
    }

    @Test
    fun withoutARectangleOrCornerTheStoredPlaceStays() {
        assertNull(ChatBarLogic.anchoredPosition("tr", null, 0.1f, 0f, 1000, 1000))
        assertNull(ChatBarLogic.anchoredPosition(null, rect, 0.1f, 0f, 1000, 1000))
        assertNull(ChatBarLogic.anchoredPosition("tr", rect, 0.1f, 0f, 0, 1000))
    }

    @Test
    fun theButtonStaysInsideTheView() {
        val moved = floatArrayOf(900f, 900f, 990f, 990f)
        val p = ChatBarLogic.anchoredPosition("br", moved, 0.2f, 0.2f, 1000, 1000)!!
        assertArrayEquals(floatArrayOf(1000f, 1000f), p, 0.01f)
        val q = ChatBarLogic.anchoredPosition("tl", floatArrayOf(10f, 10f, 100f, 100f), -0.2f, -0.2f, 1000, 1000)!!
        assertArrayEquals(floatArrayOf(0f, 0f), q, 0.01f)
    }

    @Test
    fun theOffsetIsMeasuredAndPutBackToTheSamePlace() {
        val offset = ChatBarLogic.anchorOffset("bl", rect, 160f, 950f, 1000, 1000)!!
        assertEquals(0.06f, offset[0], 0.0001f)
        assertEquals(0.05f, offset[1], 0.0001f)
        val back = ChatBarLogic.anchoredPosition("bl", rect, offset[0], offset[1], 1000, 1000)!!
        assertArrayEquals(floatArrayOf(160f, 950f), back, 0.01f)
    }

    // ---- the state from the strip -----------------------------------------------------------------------------------

    @Test
    fun noStateWithoutAChatSectionOrWithoutAMeasure() {
        assertNull(ChatBarState.from(decoded(known = false), 1000, 500, 1000, 500))
        val noRect = state(rect = null)
        assertNull(noRect.area)
        assertNull(ChatBarLogic.viewRect(noRect, 1000, 500, true))
        assertNull(ChatBarLogic.viewRect(null, 1000, 500, true))
    }

    @Test
    fun theFlagsBecomeTheState() {
        assertTrue(state(CooldownStrip.CF_VISIBLE).windowVisible)
        assertFalse(state(CooldownStrip.CF_VISIBLE).boxOpen)
        assertTrue(state(open).boxOpen)
        assertFalse(state(0).windowVisible)
    }

    @Test
    fun theRectangleIsMappedToTheView() {
        val r = ChatBarLogic.viewRect(state(), 1000, 500, true)
        assertNotNull(r)
        assertEquals(0f, r!![0], 0.5f)
        assertEquals(500f * 128f / 255f, r[1], 1.5f)
        assertEquals(1000f * 128f / 255f, r[2], 1.5f)
        assertEquals(500f, r[3], 1.5f)
    }

    @Test
    fun sameAsComparesFlagsAndRectangle() {
        assertTrue(state().sameAs(state()))
        assertFalse(state().sameAs(state(open)))
        assertFalse(state().sameAs(state(rect = intArrayOf(0, 100, 128, 255))))
        assertFalse(state().sameAs(state(rect = null)))
        assertTrue(state(rect = null).sameAs(state(rect = null)))
    }

    // ---- the gate -----------------------------------------------------------------------------------------------------

    @Test
    fun nothingIsReadWithoutAViewAndWithoutDemand() {
        assertFalse(ChatBarGate.needsSignal())
        ChatBarGate.setDemand(true)
        assertFalse("no view attached", ChatBarGate.needsSignal())
        ChatBarGate.attach(owner, Runnable { })
        assertTrue(ChatBarGate.needsSignal())
        ChatBarGate.setDemand(false)
        assertFalse("no button needs the chat", ChatBarGate.needsSignal())
        ChatBarGate.onStrip(decoded(), 1000, 500, 1000, 500)
        assertNull(ChatBarGate.state())
    }

    @Test
    fun theGateTellsTheViewOnlyAboutChanges() {
        var calls = 0
        ChatBarGate.attach(owner, Runnable { calls++ })
        ChatBarGate.setDemand(true)

        ChatBarGate.onStrip(decoded(), 1000, 500, 1000, 500)
        assertEquals(1, calls)
        assertNotNull(ChatBarGate.state())
        ChatBarGate.onStrip(decoded(), 1000, 500, 1000, 500)
        assertEquals("same state again", 1, calls)
        ChatBarGate.onStrip(decoded(flags = open), 1000, 500, 1000, 500)
        assertEquals(2, calls)
        assertTrue(ChatBarGate.state()!!.boxOpen)

        // an older addon without a chat section: the chat is unknown again
        ChatBarGate.onStrip(decoded(known = false), 1000, 500, 1000, 500)
        assertEquals(3, calls)
        assertNull(ChatBarGate.state())

        ChatBarGate.onStrip(decoded(), 1000, 500, 1000, 500)
        ChatBarGate.clear()
        assertNull(ChatBarGate.state())
        ChatBarGate.clear()
        assertEquals("clearing twice tells once", 5, calls)
    }

    @Test
    fun anOldViewCannotDetachTheNewOne() {
        val other = Any()
        ChatBarGate.attach(other, Runnable { })
        ChatBarGate.setDemand(true)
        ChatBarGate.attach(owner, Runnable { })
        ChatBarGate.detach(other)
        assertTrue("the new view stays attached", ChatBarGate.needsSignal())
        ChatBarGate.detach(owner)
        assertFalse(ChatBarGate.needsSignal())
    }
}
