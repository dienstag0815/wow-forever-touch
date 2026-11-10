package app.gamenative.ui.screen.xserver.editor

import org.json.JSONObject

/**
 * Operations on the text of a controls profile file (controls-N.icp) without Android classes, so they stay valid for every
 * field the element model has now or gets later (the copy is a copy of the stored JSON, not of selected fields).
 */
object ProfileJson {
    data class Duplicate(val json: String, val newIndex: Int)

    fun elementCount(profileJson: String): Int =
        JSONObject(profileJson).optJSONArray("elements")?.length() ?: 0

    /**
     * Appends a copy of element [index] shifted by ([dxFraction], [dyFraction]) (fractions of the view size, as stored in
     * the profile) and returns the new profile text with the index of the copy, or null if [index] does not exist.
     * A radial button gets its own copy of its menu so editing one button does not change the other.
     */
    fun duplicateElement(profileJson: String, index: Int, dxFraction: Double, dyFraction: Double): Duplicate? {
        val root = JSONObject(profileJson)
        val elements = root.optJSONArray("elements") ?: return null
        if (index < 0 || index >= elements.length()) return null

        val copy = JSONObject(elements.getJSONObject(index).toString())
        copy.put("x", clamp01(copy.optDouble("x", 0.5) + dxFraction))
        copy.put("y", clamp01(copy.optDouble("y", 0.5) + dyFraction))
        cloneRadialMenu(root, copy)
        elements.put(copy)
        return Duplicate(root.toString(), elements.length() - 1)
    }

    /** Position of element [index] as stored (fractions of the view size), or null. */
    fun positionOf(profileJson: String, index: Int): Pair<Double, Double>? {
        val elements = JSONObject(profileJson).optJSONArray("elements") ?: return null
        if (index < 0 || index >= elements.length()) return null
        val element = elements.getJSONObject(index)
        return element.optDouble("x", 0.0) to element.optDouble("y", 0.0)
    }

    private fun cloneRadialMenu(root: JSONObject, element: JSONObject) {
        val menuId = element.optString("radialMenuId", "")
        if (menuId.isEmpty()) return
        val menus = root.optJSONArray("radialMenus") ?: return
        var source: JSONObject? = null
        val ids = HashSet<String>()
        for (i in 0 until menus.length()) {
            val menu = menus.optJSONObject(i) ?: continue
            ids.add(menu.optString("id", ""))
            // Index 0 is the default menu and is shared by design; only menus of radial buttons are copied.
            if (i > 0 && menu.optString("id", "") == menuId) source = menu
        }
        if (source == null) return
        var number = 1
        while (ids.contains("button-$number")) number++
        val newId = "button-$number"
        val menuCopy = JSONObject(source.toString())
        menuCopy.put("id", newId)
        menus.put(menuCopy)
        element.put("radialMenuId", newId)
    }

    private fun clamp01(value: Double): Double = if (value < 0.0) 0.0 else if (value > 1.0) 1.0 else value
}
