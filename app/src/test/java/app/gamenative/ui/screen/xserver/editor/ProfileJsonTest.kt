package app.gamenative.ui.screen.xserver.editor

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileJsonTest {
    private val profile = """
        {
          "id": 7, "name": "Mein Layout", "cursorSpeed": 1.0,
          "elements": [
            {"type": "BUTTON", "shape": "ROUND_RECT", "bindings": ["KEY_SPACE", "NONE", "NONE", "NONE"],
             "scale": 1.5, "x": 0.25, "y": 0.5, "toggleSwitch": true, "text": "Sprung", "iconId": 3,
             "buttonOpacity": 0.7, "wowAction": "JUMP", "futureField": {"a": [1, 2, 3]}},
            {"type": "BUTTON", "shape": "CIRCLE", "bindings": ["OPEN_RADIAL_MENU", "NONE", "NONE", "NONE"],
             "scale": 1.0, "x": 0.9, "y": 0.9, "toggleSwitch": false, "text": "Kreis", "iconId": 0,
             "radialMenuId": "button-1"},
            {"type": "STICK", "shape": "CIRCLE", "bindings": ["GAMEPAD_LEFT_THUMB_UP"],
             "scale": 1.0, "x": 0.1, "y": 0.7, "toggleSwitch": false, "text": "", "iconId": 0}
          ],
          "radialMenus": [
            {"id": "default", "name": "Standard", "slots": []},
            {"id": "button-1", "name": "Emotes", "slots": [{"label": "Tanz", "binding": "NONE"}]}
          ]
        }
    """.trimIndent()

    @Test
    fun `duplicate appends a shifted copy with all other fields equal`() {
        val result = ProfileJson.duplicateElement(profile, 0, 0.05, 0.1)!!

        assertEquals(3, ProfileJson.elementCount(profile))
        assertEquals(4, ProfileJson.elementCount(result.json))
        assertEquals(3, result.newIndex)

        val elements = JSONObject(result.json).getJSONArray("elements")
        val original = elements.getJSONObject(0)
        val copy = elements.getJSONObject(result.newIndex)
        assertEquals(0.30, copy.getDouble("x"), 1e-9)
        assertEquals(0.60, copy.getDouble("y"), 1e-9)

        // unchanged original
        assertEquals(0.25, original.getDouble("x"), 1e-9)
        assertEquals(0.5, original.getDouble("y"), 1e-9)

        // every other field is the same, including fields the code does not know
        for (key in listOf("type", "shape", "scale", "toggleSwitch", "text", "iconId", "buttonOpacity", "wowAction")) {
            assertEquals(key, original.get(key).toString(), copy.get(key).toString())
        }
        assertEquals(original.getJSONArray("bindings").toString(), copy.getJSONArray("bindings").toString())
        assertEquals(original.getJSONObject("futureField").toString(), copy.getJSONObject("futureField").toString())
    }

    @Test
    fun `duplicate keeps the copy inside the view`() {
        val result = ProfileJson.duplicateElement(profile, 1, 0.5, 0.5)!!
        val copy = JSONObject(result.json).getJSONArray("elements").getJSONObject(result.newIndex)

        assertEquals(1.0, copy.getDouble("x"), 1e-9)
        assertEquals(1.0, copy.getDouble("y"), 1e-9)
    }

    @Test
    fun `duplicate of a radial button gets its own menu`() {
        val result = ProfileJson.duplicateElement(profile, 1, 0.05, 0.05)!!
        val root = JSONObject(result.json)
        val copy = root.getJSONArray("elements").getJSONObject(result.newIndex)
        val menus = root.getJSONArray("radialMenus")

        assertNotEquals("button-1", copy.getString("radialMenuId"))
        assertEquals(3, menus.length())
        val newMenu = menus.getJSONObject(2)
        assertEquals(copy.getString("radialMenuId"), newMenu.getString("id"))
        assertEquals("Emotes", newMenu.getString("name"))
        assertEquals(1, newMenu.getJSONArray("slots").length())
        // the original keeps its menu
        assertEquals("button-1", root.getJSONArray("elements").getJSONObject(1).getString("radialMenuId"))
        assertEquals("button-1", menus.getJSONObject(1).getString("id"))
    }

    @Test
    fun `duplicate of a button without own menu does not touch the menus`() {
        val result = ProfileJson.duplicateElement(profile, 0, 0.05, 0.05)!!

        assertEquals(2, JSONObject(result.json).getJSONArray("radialMenus").length())
    }

    @Test
    fun `second duplicate of a radial button gets another free menu id`() {
        val first = ProfileJson.duplicateElement(profile, 1, 0.05, 0.05)!!
        val second = ProfileJson.duplicateElement(first.json, 1, 0.05, 0.05)!!
        val root = JSONObject(second.json)
        val ids = (0 until root.getJSONArray("radialMenus").length())
            .map { root.getJSONArray("radialMenus").getJSONObject(it).getString("id") }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `unknown index gives null`() {
        assertNull(ProfileJson.duplicateElement(profile, 3, 0.1, 0.1))
        assertNull(ProfileJson.duplicateElement(profile, -1, 0.1, 0.1))
    }

    @Test
    fun `position can be read`() {
        val position = ProfileJson.positionOf(profile, 2)!!

        assertEquals(0.1, position.first, 1e-9)
        assertEquals(0.7, position.second, 1e-9)
        assertTrue(ProfileJson.positionOf(profile, 9) == null)
    }
}
