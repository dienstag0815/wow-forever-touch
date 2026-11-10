package com.winlator.inputcontrols

import com.winlator.widget.InputControlsView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Chat buttons of the editor: chat control, "Zeigen", pinning to the chat window, "Bei offenen Fenstern" (touch, geometry, JSON, default profile). */
@RunWith(RobolectricTestRunner::class)
class ControlElementChatTest {
    private val newKeys = listOf("chatAction", "chatShow", "chatAnchor", "chatAnchorDx", "chatAnchorDy", "windowRule")

    private fun mockView(editMode: Boolean = false, chatRect: FloatArray? = null): InputControlsView {
        val view = mockk<InputControlsView>(relaxed = true)
        every { view.snappingSize } returns 10
        every { view.maxWidth } returns 1000
        every { view.maxHeight } returns 1000
        every { view.isEditMode } returns editMode
        every { view.chatRect } returns chatRect
        return view
    }

    private fun button(view: InputControlsView, x: Int = 600, y: Int = 550) = ControlElement(view).apply {
        setType(ControlElement.Type.BUTTON)
        setShape(ControlElement.Shape.CIRCLE)
        setX(x)
        setY(y)
    }

    private fun readBack(view: InputControlsView, json: JSONObject) = ControlElement(view).apply {
        setType(ControlElement.Type.BUTTON)
        readExtrasFromJson(json)
    }

    // ---- press -----------------------------------------------------------------------------------------------------

    @Test
    fun `a chat control runs once per press and holds no key`() {
        val view = mockView()
        val element = button(view, 100, 100)
        element.chatAction = "voice"

        assertTrue(element.handleTouchDown(1, 100f, 100f))
        verify(exactly = 1) { view.runChatAction("voice") }
        assertTrue(element.handleTouchUp(1))
        verify(exactly = 1) { view.runChatAction(any()) }
        verify(exactly = 0) { view.handleInputEvent(any<BindingCombo>(), any()) }
    }

    @Test
    fun `an unknown chat control is dropped`() {
        val element = button(mockView())
        element.chatAction = "nonsense"
        assertNull(element.chatAction)
        element.chatAction = "send"
        assertEquals("send", element.chatAction)
        element.chatAction = ""
        assertNull(element.chatAction)
    }

    @Test
    fun `only the voice control and the options need the chat state`() {
        val element = button(mockView())
        assertFalse(element.dependsOnChat())
        element.chatAction = "send"
        assertFalse("Enter does not depend on the chat state", element.dependsOnChat())
        element.chatAction = "voice"
        assertTrue("voice appends to an open line", element.dependsOnChat())
        element.chatAction = null
        element.chatShow = "box"
        assertTrue(element.dependsOnChat())
        element.chatShow = null
        element.chatAnchor = "tr"
        assertTrue(element.dependsOnChat())
    }

    // ---- json ------------------------------------------------------------------------------------------------------

    @Test
    fun `a button without chat settings writes none of the new fields`() {
        val json = button(mockView()).toJSONObject()!!
        for (key in newKeys) assertFalse(key, json.has(key))
    }

    @Test
    fun `an old profile reads as before`() {
        val old = JSONObject("""{"type":"BUTTON","shape":"CIRCLE","bindings":["KEY_A","NONE","NONE","NONE"],"scale":1,"x":0.5,"y":0.5,"toggleSwitch":false,"text":"A","iconId":0}""")
        val element = readBack(mockView(), old)
        assertNull(element.chatAction)
        assertNull(element.chatShow)
        assertNull(element.chatAnchor)
        assertFalse(element.isChatAnchorKnown)
        assertNull(element.windowRule)
        assertFalse(element.dependsOnChat())
    }

    @Test
    fun `all chat settings survive a round trip`() {
        val view = mockView()
        val element = button(view)
        element.chatAction = "voice"
        element.chatShow = "box"
        element.chatAnchor = "br"
        element.setChatAnchorOffset(0.06f, -0.04f)
        element.windowRule = "keep"

        val json = element.toJSONObject()!!
        assertEquals("voice", json.getString("chatAction"))
        assertEquals("box", json.getString("chatShow"))
        assertEquals("br", json.getString("chatAnchor"))
        assertEquals(0.06, json.getDouble("chatAnchorDx"), 1e-6)
        assertEquals(-0.04, json.getDouble("chatAnchorDy"), 1e-6)
        assertEquals("keep", json.getString("windowRule"))

        val back = readBack(view, json)
        assertEquals("voice", back.chatAction)
        assertEquals("box", back.chatShow)
        assertEquals("br", back.chatAnchor)
        assertTrue(back.isChatAnchorKnown)
        assertEquals(0.06f, back.chatAnchorDx, 1e-6f)
        assertEquals(-0.04f, back.chatAnchorDy, 1e-6f)
        assertEquals("keep", back.windowRule)
    }

    @Test
    fun `a pinned corner without a measured offset writes only the corner`() {
        val element = button(mockView())
        element.chatAnchor = "tl"
        val json = element.toJSONObject()!!
        assertEquals("tl", json.getString("chatAnchor"))
        assertFalse(json.has("chatAnchorDx"))
        assertFalse(json.has("chatAnchorDy"))
        assertFalse(readBack(mockView(), json).isChatAnchorKnown)
    }

    @Test
    fun `damaged values are dropped on reading`() {
        val json = JSONObject(
            """{"chatAction":"x","chatShow":"y","chatAnchor":"z","chatAnchorDx":1,"chatAnchorDy":1,"windowRule":"w"}""",
        )
        val element = readBack(mockView(), json)
        assertNull(element.chatAction)
        assertNull(element.chatShow)
        assertNull(element.chatAnchor)
        assertFalse(element.isChatAnchorKnown)
        assertNull(element.windowRule)
    }

    @Test
    fun `the window option is written for every type`() {
        val view = mockView()
        val stick = ControlElement(view).apply {
            setType(ControlElement.Type.STICK)
            windowRule = "hide"
        }
        assertEquals("hide", stick.toJSONObject()!!.getString("windowRule"))
        // the chat fields belong to buttons only
        stick.chatShow = "box"
        assertFalse(stick.toJSONObject()!!.has("chatShow"))
    }

    @Test
    fun `a copy takes the chat settings along`() {
        val view = mockView()
        val element = button(view)
        element.chatAction = "send"
        element.chatShow = "chat"
        element.chatAnchor = "tr"
        element.setChatAnchorOffset(0.1f, 0.2f)
        element.windowRule = "keep"
        val copy = ControlElement(view).apply {
            setType(ControlElement.Type.BUTTON)
            copyExtrasFrom(element)
        }
        assertEquals("send", copy.chatAction)
        assertEquals("chat", copy.chatShow)
        assertEquals("tr", copy.chatAnchor)
        assertTrue(copy.isChatAnchorKnown)
        assertEquals("keep", copy.windowRule)
    }

    // ---- pinned to the chat window ---------------------------------------------------------------------------------

    @Test
    fun `a pinned button follows the chat window`() {
        val view = mockView()
        val element = button(view, 0, 0)
        element.chatAnchor = "tr"
        element.setChatAnchorOffset(0.1f, -0.05f)

        element.applyChatAnchor(floatArrayOf(100f, 600f, 500f, 900f))
        assertEquals(600, element.x.toInt())
        assertEquals(550, element.y.toInt())
        // the chat moved and grew
        element.applyChatAnchor(floatArrayOf(200f, 300f, 700f, 600f))
        assertEquals(800, element.x.toInt())
        assertEquals(250, element.y.toInt())
    }

    @Test
    fun `without a rectangle the button stays where it was saved`() {
        val view = mockView()
        val element = button(view, 321, 123)
        element.chatAnchor = "tr"
        element.setChatAnchorOffset(0.1f, -0.05f)
        element.applyChatAnchor(null)
        assertEquals(321, element.x.toInt())
        assertEquals(123, element.y.toInt())
    }

    @Test
    fun `without a measured offset the button does not move`() {
        val element = button(mockView(), 321, 123)
        element.chatAnchor = "tr"
        element.applyChatAnchor(floatArrayOf(100f, 600f, 500f, 900f))
        assertEquals(321, element.x.toInt())
        assertEquals(123, element.y.toInt())
    }

    @Test
    fun `a free button ignores the chat window`() {
        val element = button(mockView(), 321, 123)
        element.applyChatAnchor(floatArrayOf(100f, 600f, 500f, 900f))
        assertEquals(321, element.x.toInt())
        assertEquals(123, element.y.toInt())
    }

    @Test
    fun `the editor measures the offset when it saves`() {
        val view = mockView(editMode = true, chatRect = floatArrayOf(100f, 600f, 500f, 900f))
        val element = button(view, 600, 550)
        element.chatAnchor = "tr"
        assertFalse(element.isChatAnchorKnown)

        val json = element.toJSONObject()!!
        assertEquals(0.1, json.getDouble("chatAnchorDx"), 1e-4)
        assertEquals(-0.05, json.getDouble("chatAnchorDy"), 1e-4)
        assertTrue(element.isChatAnchorKnown)

        // dragged elsewhere in the editor: measured again against the same chat window
        element.setX(700)
        element.setY(500)
        val moved = element.toJSONObject()!!
        assertEquals(0.2, moved.getDouble("chatAnchorDx"), 1e-4)
        assertEquals(-0.10, moved.getDouble("chatAnchorDy"), 1e-4)
    }

    @Test
    fun `the editor keeps the old offset when the chat window is not reported`() {
        val view = mockView(editMode = true, chatRect = null)
        val element = button(view, 600, 550)
        element.chatAnchor = "tr"
        element.setChatAnchorOffset(0.3f, 0.3f)
        val json = element.toJSONObject()!!
        assertEquals(0.3, json.getDouble("chatAnchorDx"), 1e-4)
        assertEquals(0.3, json.getDouble("chatAnchorDy"), 1e-4)
    }

    @Test
    fun `while playing a save does not re-measure the offset`() {
        val view = mockView(editMode = false, chatRect = floatArrayOf(100f, 600f, 500f, 900f))
        val element = button(view, 10, 10)
        element.chatAnchor = "tr"
        element.setChatAnchorOffset(0.1f, -0.05f)
        val json = element.toJSONObject()!!
        assertEquals(0.1, json.getDouble("chatAnchorDx"), 1e-4)
        assertEquals(-0.05, json.getDouble("chatAnchorDy"), 1e-4)
    }

    @Test
    fun `a new corner forgets the offset`() {
        val element = button(mockView())
        element.chatAnchor = "tr"
        element.setChatAnchorOffset(0.1f, 0.1f)
        element.chatAnchor = "tr"
        assertTrue("same corner keeps it", element.isChatAnchorKnown)
        element.chatAnchor = "bl"
        assertFalse(element.isChatAnchorKnown)
        element.chatAnchor = null
        assertNull(element.chatAnchor)
    }

    // ---- default profile ---------------------------------------------------------------------------------------------

    @Test
    fun `the touch profile has the voice and the send button pinned to the chat`() {
        val json = JSONObject(File("src/main/assets/inputcontrols/profiles/controls-6.icp").readText())
        val elements = json.getJSONArray("elements")
        val chat = (0 until elements.length()).map { elements.getJSONObject(it) }.filter { it.has("chatAction") }
        assertEquals(listOf("voice", "send"), chat.map { it.getString("chatAction") })

        val view = mockView()
        for (e in chat) {
            assertEquals("BUTTON", e.getString("type"))
            assertEquals("chat", e.getString("chatShow"))
            assertEquals("tr", e.getString("chatAnchor"))
            assertEquals("keep", e.getString("windowRule"))
            // the button is circle-sized like the other small buttons and holds no key
            assertEquals("CIRCLE", e.getString("shape"))
            assertEquals(1.0, e.getDouble("scale"), 0.0)
            val bindings = e.getJSONArray("bindings")
            for (i in 0 until bindings.length()) assertEquals("NONE", bindings.getString(i))
            val element = readBack(view, e)
            assertTrue(element.isChatAnchorKnown)
            assertEquals(e.getString("chatAction"), element.chatAction)
            assertTrue(element.dependsOnChat())
        }
    }
}
