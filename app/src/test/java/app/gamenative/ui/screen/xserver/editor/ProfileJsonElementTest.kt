package app.gamenative.ui.screen.xserver.editor

import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlElement
import com.winlator.widget.InputControlsView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/** The duplicate must carry every field a real element writes into the profile, not only the ones the editor knows. */
@RunWith(RobolectricTestRunner::class)
class ProfileJsonElementTest {
    @Test
    fun `duplicate of a real element has the same stored fields and a shifted position`() {
        val view = mock<InputControlsView>()
        whenever(view.snappingSize).thenReturn(10)
        whenever(view.maxWidth).thenReturn(1000)
        whenever(view.maxHeight).thenReturn(500)
        val element = ControlElement(view).apply {
            setType(ControlElement.Type.BUTTON)
            setShape(ControlElement.Shape.ROUND_RECT)
            setX(300)
            setY(200)
            setScale(1.5f)
            setText("Sprung")
            setToggleSwitch(true)
            setBindingComboAt(0, BindingCombo.of(Binding.KEY_SPACE))
            setButtonOpacity(0.7f)
            wowAction = "JUMP"
            chatCommand = "/dance"
        }
        val original = element.toJSONObject()
        val profile = JSONObject().put("id", 1).put("elements", JSONArray().put(original)).toString()

        val result = ProfileJson.duplicateElement(profile, 0, 0.05, 0.1)!!

        val elements = JSONObject(result.json).getJSONArray("elements")
        assertEquals(2, elements.length())
        val copy = elements.getJSONObject(result.newIndex)
        val first = elements.getJSONObject(0)
        assertEquals(0.35, copy.getDouble("x"), 1e-6)
        assertEquals(0.5, copy.getDouble("y"), 1e-6)
        val keys = first.keys().asSequence().toList()
        assertTrue(keys.containsAll(listOf("type", "shape", "bindings", "scale", "x", "y", "text", "wowAction", "chatCommand")))
        for (key in keys) {
            if (key == "x" || key == "y") continue
            assertEquals(key, first.get(key).toString(), copy.get(key).toString())
        }
        assertEquals(keys.toSet(), copy.keys().asSequence().toSet())
    }
}
