package app.gamenative.ui.screen.wow

import app.gamenative.TestTexts
import app.gamenative.ui.components.TextRef
import app.gamenative.wowaction.WowActionCatalog
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.ProfileUpdatePolicy
import com.winlator.inputcontrols.StickRing
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** The bundled "Touch (WoW komplett)" template (controls-7.icp): loads, fits the Fold 8 screen, nothing overlaps. */
class WowTouchFullProfileTest {
    /** [InputMode.label] reads the German texts here, like the rest of the German test texts. */
    @get:Rule
    val texts = TestTexts.rule("de")

    /** Bestätigungstext des Zurücksetzens (Ressource plus Profilname), wie ihn der Dialog zeigt. */
    private fun resetConfirmText(mode: InputMode) = TextRef(WowTouchProfiles.RESET_CONFIRM_TEXT, WowTouchProfiles.resetTargetName(mode))

    private val assetDir = File("src/main/assets/inputcontrols/profiles")
    private val file = File("src/main/assets/" + WowTouchProfiles.FULL_ASSET_PATH)
    private val json = JSONObject(file.readText())
    private val elements = json.getJSONArray("elements")

    private fun element(i: Int) = elements.getJSONObject(i)
    private fun all() = (0 until elements.length()).map { element(it) }
    private fun ofType(type: String) = all().filter { it.getString("type") == type }
    private fun bindingNames(e: JSONObject): List<String> {
        val out = ArrayList<String>()
        val bindings = e.getJSONArray("bindings")
        for (i in 0 until bindings.length()) {
            when (val b = bindings.get(i)) {
                is String -> out.add(b)
                is JSONArray -> for (j in 0 until b.length()) out.add(b.getString(j))
            }
        }
        return out
    }

    // --- geometry, mirrors ControlElement.computeBoundingBox for scale-only elements -------------------------------

    private sealed class Shape {
        data class Rect(val l: Float, val t: Float, val r: Float, val b: Float) : Shape()
        data class Circle(val cx: Float, val cy: Float, val radius: Float) : Shape()
    }

    private fun shapeOf(e: JSONObject, width: Int, height: Int): Shape {
        val snap = width / 100
        val maxWidth = width
        val maxHeight = (Math.round(height / snap.toFloat()) * snap)
        val x = (e.getDouble("x") * maxWidth).toInt()
        val y = (e.getDouble("y") * maxHeight).toInt()
        val scale = e.getDouble("scale").toFloat()
        return when (e.getString("type")) {
            "STICK" -> {
                var half = 6 * snap * scale
                val ring = e.optJSONObject("ring")
                if (ring != null) {
                    half += ceil((ring.getDouble("gap") + ring.getDouble("width")) * snap * scale).toFloat()
                }
                Shape.Circle(x.toFloat(), y.toFloat(), half)
            }
            "SCROLL_WHEEL" -> {
                val rotated = e.optInt("orientation", 0) == 1
                val hw = (if (rotated) 5 else 2) * snap * scale
                val hh = (if (rotated) 2 else 5) * snap * scale
                Shape.Rect(x - hw, y - hh, x + hw, y + hh)
            }
            else -> when (e.getString("shape")) {
                "CIRCLE" -> Shape.Circle(x.toFloat(), y.toFloat(), 3 * snap * scale)
                "PILL" -> {
                    val hw = 5 * snap * scale
                    val hh = (snap * 1.5f).toInt() * scale
                    Shape.Rect(x - hw, y - hh, x + hw, y + hh)
                }
                "SQUARE" -> Shape.Rect(x - 2.5f * snap * scale, y - 2.5f * snap * scale, x + 2.5f * snap * scale, y + 2.5f * snap * scale)
                else -> Shape.Rect(x - 4 * snap * scale, y - 2 * snap * scale, x + 4 * snap * scale, y + 2 * snap * scale)
            }
        }
    }

    private fun overlap(a: Shape, b: Shape): Boolean = when {
        a is Shape.Rect && b is Shape.Rect -> a.l < b.r && b.l < a.r && a.t < b.b && b.t < a.b
        a is Shape.Circle && b is Shape.Circle -> hypot(a.cx - b.cx, a.cy - b.cy) < a.radius + b.radius
        a is Shape.Circle && b is Shape.Rect -> circleRect(a, b)
        a is Shape.Rect && b is Shape.Circle -> circleRect(b, a)
        else -> false
    }

    private fun circleRect(c: Shape.Circle, r: Shape.Rect): Boolean {
        val nx = max(r.l, min(c.cx, r.r))
        val ny = max(r.t, min(c.cy, r.b))
        return hypot(c.cx - nx, c.cy - ny) < c.radius
    }

    private fun bounds(s: Shape): Shape.Rect = when (s) {
        is Shape.Rect -> s
        is Shape.Circle -> Shape.Rect(s.cx - s.radius, s.cy - s.radius, s.cx + s.radius, s.cy + s.radius)
    }

    /** Screen sizes around the Fold 8 inner display (2000 x 1510 in device screenshots, aspect 4:3.02). */
    private val screens = listOf(2000 to 1510, 2000 to 1500, 2000 to 1520, 1600 to 1208, 2480 to 1872)

    // --- the file itself -----------------------------------------------------------------------------------------

    @Test
    fun profile_hasItsOwnIdAndName() {
        assertEquals(7, json.getInt("id"))
        assertEquals(WowTouch.FULL_PROFILE_NAME, json.getString("name"))
        assertEquals("Touch (WoW komplett)", json.getString("name"))
    }

    @Test
    fun profile_idAndNameAreUniqueAmongAssets() {
        val ids = ArrayList<Int>()
        val names = ArrayList<String>()
        for (f in assetDir.listFiles()!!) {
            val j = JSONObject(f.readText())
            ids.add(j.getInt("id"))
            names.add(j.getString("name"))
        }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(names.size, names.toSet().size)
        assertFalse(names.contains(WowTouch.CUSTOM_PROFILE_NAME))
    }

    @Test
    fun everyElementHasTheFieldsTheLoaderReads() {
        for (e in all()) {
            ControlElement.Type.valueOf(e.getString("type"))
            ControlElement.Shape.valueOf(e.getString("shape"))
            e.getBoolean("toggleSwitch")
            e.getDouble("x"); e.getDouble("y"); e.getDouble("scale")
            e.getString("text"); e.getInt("iconId")
            assertTrue(e.getDouble("x") in 0.0..1.0)
            assertTrue(e.getDouble("y") in 0.0..1.0)
            for (name in bindingNames(e)) Binding.valueOf(name)
        }
    }

    @Test
    fun profile_hasAllElementTypes() {
        assertEquals(13, elements.length())
        assertEquals(2, ofType("STICK").size)
        assertEquals(1, ofType("SCROLL_WHEEL").size)
        assertEquals(10, ofType("BUTTON").size)
    }

    private fun leftStick() = ofType("STICK").single { it.getJSONArray("bindings").getString(0) == "GAMEPAD_LEFT_THUMB_UP" }
    private fun rightStick() = ofType("STICK").single { it.getJSONArray("bindings").getString(0) == "GAMEPAD_RIGHT_THUMB_UP" }
    private fun ringItems(stick: JSONObject): List<JSONObject> {
        val ring = stick.getJSONObject("ring")
        assertEquals(8, ring.getInt("segments"))
        assertEquals(8, StickRing.normalizeSegments(ring.getInt("segments")))
        val items = ring.getJSONArray("items")
        assertEquals(8, items.length())
        return (0 until 8).map { items.getJSONObject(it) }
    }

    @Test
    fun sticks_walkOnTheLeftLookOnTheRight() {
        val left = leftStick()
        val right = rightStick()
        assertEquals(listOf("GAMEPAD_LEFT_THUMB_UP", "GAMEPAD_LEFT_THUMB_RIGHT", "GAMEPAD_LEFT_THUMB_DOWN", "GAMEPAD_LEFT_THUMB_LEFT"), bindingNames(left))
        assertEquals(listOf("GAMEPAD_RIGHT_THUMB_UP", "GAMEPAD_RIGHT_THUMB_RIGHT", "GAMEPAD_RIGHT_THUMB_DOWN", "GAMEPAD_RIGHT_THUMB_LEFT"), bindingNames(right))
        assertEquals(0.304, left.getDouble("x"), 0.01)
        assertEquals(0.75, left.getDouble("y"), 0.02)
        assertEquals(0.725, right.getDouble("x"), 0.01)
        assertEquals(0.745, right.getDouble("y"), 0.02)
    }

    @Test
    fun leftRing_holdsTheEightGameMenusLikeTheMenuWheel() {
        val items = ringItems(leftStick())
        val actions = items.map { it.getString("wowAction") }
        assertEquals(WowActionCatalog.GAME_MENU_COMMANDS, actions)
        val menu = json.getJSONArray("radialMenus").getJSONObject(0).getJSONArray("slots")
        for (i in 0 until 8) {
            assertEquals(menu.getJSONObject(i).getString("binding"), items[i].getString("binding"))
            assertEquals("KEY_" + WowActionCatalog.find(actions[i])!!.defaultKey, items[i].getString("binding"))
            assertEquals("", items[i].getString("label"))
        }
    }

    @Test
    fun rightStick_middleIsJump() {
        val center = rightStick().getJSONObject("center")
        assertEquals("JUMP", center.getString("wowAction"))
        assertEquals("KEY_SPACE", center.getString("binding"))
        assertEquals("SPACE", WowActionCatalog.find("JUMP")!!.defaultKey)
        assertTrue("the left stick has no middle action", !leftStick().has("center"))
    }

    @Test
    fun rightRing_holdsActionBarOneToSevenAndTab() {
        val items = ringItems(rightStick())
        for (n in 1..7) {
            val item = items[n - 1]
            assertEquals("ACTIONBUTTON$n", item.getString("wowAction"))
            assertEquals("KEY_$n", item.getString("binding"))
            assertEquals("", item.getString("label"))
            assertEquals("$n", WowActionCatalog.find("ACTIONBUTTON$n")!!.defaultKey)
        }
        val tab = items[7]
        assertEquals("TARGETNEARESTENEMY", tab.getString("wowAction"))
        assertEquals("KEY_TAB", tab.getString("binding"))
        assertEquals("TAB", WowActionCatalog.find("TARGETNEARESTENEMY")!!.defaultKey)
        for (item in items) {
            Binding.valueOf(item.getString("binding"))
            assertNotNull(WowActionCatalog.find(item.getString("wowAction")))
        }
    }

    @Test
    fun noLooseActionKeysPillsOrJumpButtonAnyMore() {
        val buttons = ofType("BUTTON")
        assertTrue(buttons.none { it.getString("shape") == "PILL" })
        assertTrue(buttons.none { it.optString("wowAction").startsWith("ACTIONBUTTON") })
        assertTrue(buttons.none { it.optString("wowAction") == "JUMP" })
        assertTrue(buttons.none { bindingNames(it).any { b -> b == "KEY_SPACE" || b == "KEY_TAB" || b.matches(Regex("KEY_[1-6]")) } })
        assertTrue(buttons.none { it.getString("text").startsWith("Leiste") })
    }

    private fun buttonWithBinding(name: String) = ofType("BUTTON").single { it.getJSONArray("bindings").get(0) == name }
    private fun at(e: JSONObject, px: Int, py: Int) {
        assertEquals("x of ${e.getString("text")}", px / 2000.0, e.getDouble("x"), 0.012)
        assertEquals("y of ${e.getString("text")}", py / 1510.0, e.getDouble("y"), 0.012)
    }

    @Test
    fun lowerLeftCluster_followsTheScreenshot() {
        at(buttonWithBinding("OPEN_NAVIGATION_MENU"), 58, 1175)
        val menus = ofType("BUTTON").filter { it.getJSONArray("bindings").get(0) == "OPEN_RADIAL_MENU" }
        at(menus.single { it.getString("radialMenuId") == "default" }, 177, 1175)
        at(menus.single { it.getString("radialMenuId") == "emotes" }, 177, 1290)
        at(menus.single { it.getString("radialMenuId") == "attacks" }, 295, 1410)
        at(buttonWithBinding("VOICE_CHAT"), 58, 1290)
        at(buttonWithBinding("MOUSE_RIGHT_BUTTON"), 295, 1290)
        at(buttonWithBinding("KEY_ESC"), 58, 1430)
        at(buttonWithBinding("SHOW_KEYBOARD"), 177, 1430)
        at(buttonWithBinding("OPEN_CONTROLS_EDITOR"), 167, 510)
    }

    @Test
    fun barsButton_togglesTheAddonBarsWithCtrlShiftF5() {
        val b = ofType("BUTTON").single { it.optString("wowAction") == "WFB_TOGGLEBARS" }
        val combo = b.getJSONArray("bindings").getJSONArray(0)
        assertEquals(listOf("KEY_CTRL_L", "KEY_SHIFT_L", "KEY_F5"), (0 until combo.length()).map { combo.getString(it) })
        assertNotNull(WowActionCatalog.find("WFB_TOGGLEBARS"))
        assertEquals("CTRL-SHIFT-F5", WowActionCatalog.find("WFB_TOGGLEBARS")!!.defaultKey)
        at(b, 295, 1175)
    }

    @Test
    fun menuAndSystemButtons_exist() {
        val first = ofType("BUTTON").map { it.getJSONArray("bindings").get(0) }.filterIsInstance<String>()
        for (needed in listOf("OPEN_NAVIGATION_MENU", "VOICE_CHAT", "SHOW_KEYBOARD", "KEY_ESC", "OPEN_CONTROLS_EDITOR")) {
            assertTrue("$needed missing", needed in first)
        }
        assertEquals(3, first.count { it == "OPEN_RADIAL_MENU" })
    }

    @Test
    fun scrollWheel_sitsInTheMiddleAndTapDoesNothing() {
        val wheel = ofType("SCROLL_WHEEL").single()
        assertEquals(0.5, wheel.getDouble("x"), 0.01)
        assertEquals(0.788, wheel.getDouble("y"), 0.01)
        assertEquals(listOf("NONE", "NONE", "NONE", "NONE"), bindingNames(wheel))
    }

    // --- radial menus ---------------------------------------------------------------------------------------------

    @Test
    fun radialMenus_existForEveryRadialButton() {
        val menus = json.getJSONArray("radialMenus")
        val ids = (0 until menus.length()).map { menus.getJSONObject(it).getString("id") }
        assertEquals("default", ids.first())
        assertEquals(listOf("default", "emotes", "attacks"), ids)
        val buttons = ofType("BUTTON").filter { it.getJSONArray("bindings").get(0) == "OPEN_RADIAL_MENU" }
        for (b in buttons) assertTrue(b.getString("radialMenuId") in ids)
        assertEquals(setOf("default", "emotes", "attacks"), buttons.map { it.getString("radialMenuId") }.toSet())
    }

    @Test
    fun attackWheel_holdsActionBarOneToEight() {
        val slots = json.getJSONArray("radialMenus").getJSONObject(2).getJSONArray("slots")
        assertEquals(8, slots.length())
        assertEquals(WowActionCatalog.ATTACK_COMMANDS, (0 until 8).map { slots.getJSONObject(it).getString("wowAction") })
        for (i in 0 until 8) assertEquals("KEY_${i + 1}", slots.getJSONObject(i).getString("binding"))
    }

    @Test
    fun gameMenuWheel_holdsTheEightGameMenusWithWowActions() {
        val menu = json.getJSONArray("radialMenus").getJSONObject(0)
        val slots = menu.getJSONArray("slots")
        assertEquals(8, slots.length())
        val actions = (0 until 8).map { slots.getJSONObject(it).getString("wowAction") }
        assertEquals(WowActionCatalog.GAME_MENU_COMMANDS, actions)
        for (i in 0 until 8) {
            val action = WowActionCatalog.find(actions[i])!!
            assertEquals("KEY_" + action.defaultKey, slots.getJSONObject(i).getString("binding"))
            assertEquals("", slots.getJSONObject(i).getString("label"))
        }
    }

    @Test
    fun emoteWheel_holdsEightChatCommands() {
        val menu = json.getJSONArray("radialMenus").getJSONObject(1)
        val slots = menu.getJSONArray("slots")
        assertEquals(8, slots.length())
        assertEquals(WowActionCatalog.EMOTE_COMMANDS, (0 until 8).map { slots.getJSONObject(it).getString("wowAction") })
        for (i in 0 until 8) {
            val slot = slots.getJSONObject(i)
            assertEquals(WowActionCatalog.find(slot.getString("wowAction"))!!.chatCommand, slot.getString("chatCommand"))
        }
    }

    // --- layout -----------------------------------------------------------------------------------------------------

    @Test
    fun noElementsOverlap() {
        for ((w, h) in screens) {
            val shapes = all().map { shapeOf(it, w, h) }
            for (i in shapes.indices) for (j in i + 1 until shapes.size) {
                assertFalse("elements $i and $j overlap at ${w}x$h", overlap(shapes[i], shapes[j]))
            }
        }
    }

    @Test
    fun allElementsAreInsideTheScreen() {
        for ((w, h) in screens) {
            for ((i, e) in all().withIndex()) {
                val r = bounds(shapeOf(e, w, h))
                assertTrue("element $i left at ${w}x$h", r.l >= 0f)
                assertTrue("element $i top at ${w}x$h", r.t >= 0f)
                assertTrue("element $i right at ${w}x$h", r.r <= w.toFloat())
                assertTrue("element $i bottom at ${w}x$h", r.b <= h.toFloat())
            }
        }
    }

    /** Where WoW draws its HUD in device screenshots (fractions of the screen). Nothing of the template may sit on it. */
    private val hudZones = mapOf(
        "Minimap" to doubleArrayOf(0.835, 0.0, 1.0, 0.27),
        "Questtracker" to doubleArrayOf(0.73, 0.28, 0.99, 0.51),
        "Aktionsleisten" to doubleArrayOf(0.215, 0.88, 1.0, 1.0),
        "Spielerrahmen" to doubleArrayOf(0.04, 0.04, 0.24, 0.14),
        "Zielrahmen" to doubleArrayOf(0.36, 0.04, 0.52, 0.14),
        "Mikro-Menue links" to doubleArrayOf(0.0, 0.0, 0.04, 0.62),
        "Erfahrungsleiste" to doubleArrayOf(0.37, 0.0, 0.74, 0.025),
    )

    @Test
    fun nothingCoversTheWowHud() {
        for ((w, h) in screens) {
            for ((i, e) in all().withIndex()) {
                val shape = shapeOf(e, w, h)
                for ((name, z) in hudZones) {
                    val zone = Shape.Rect((z[0] * w).toFloat(), (z[1] * h).toFloat(), (z[2] * w).toFloat(), (z[3] * h).toFloat())
                    assertFalse("element $i covers $name at ${w}x$h", overlap(shape, zone))
                }
            }
        }
    }

    @Test
    fun sticksSitInTheThumbZonesAndTheBigElementsDoNotTouch() {
        val left = leftStick()
        val right = rightStick()
        assertTrue("walk stick on the left", left.getDouble("x") < 0.4)
        assertTrue("look stick on the right", right.getDouble("x") > 0.6)
        assertTrue("sticks in the lower half", left.getDouble("y") > 0.6 && right.getDouble("y") > 0.6)
        for ((w, h) in screens) {
            val l = shapeOf(left, w, h)
            val r = shapeOf(right, w, h)
            val wheel = shapeOf(ofType("SCROLL_WHEEL").single(), w, h)
            assertFalse("sticks overlap at ${w}x$h", overlap(l, r))
            assertFalse("left stick and wheel overlap at ${w}x$h", overlap(l, wheel))
            assertFalse("right stick and wheel overlap at ${w}x$h", overlap(r, wheel))
        }
    }

    // --- menu entry, reset, update protection -------------------------------------------------------------------------

    @Test
    fun menuOffersTheFullTemplateBetweenTouchAndOwnLayout() {
        assertEquals(
            listOf(InputMode.CLASSIC, InputMode.TOUCH, InputMode.TOUCH_FULL, InputMode.TOUCH_CUSTOM),
            InputMode.entries.toList(),
        )
        assertEquals("Touch (WoW komplett)", InputMode.TOUCH_FULL.label)
        assertEquals(json.getString("name"), InputMode.TOUCH_FULL.profileName)
        assertTrue(InputMode.TOUCH_FULL.isTouch)
    }

    @Test
    fun newInstallation_startsWithTheFullTemplateAnUpdatedOneKeepsStandard() {
        assertEquals(InputMode.TOUCH_FULL, InputMode.defaultFor(freshInstall = true))
        assertEquals(InputMode.CLASSIC, InputMode.defaultFor(freshInstall = false))
        assertTrue(InputMode.isFreshInstall(1000L, 1000L))
        assertFalse(InputMode.isFreshInstall(1000L, 2000L))
    }

    @Test
    fun fullTemplate_launchesLikeTheOtherTouchModes() {
        assertEquals(WowTouch.settingsFor(InputMode.TOUCH, wasTouch = false), WowTouch.settingsFor(InputMode.TOUCH_FULL, wasTouch = false))
        assertEquals(WowTouch.settingsFor(InputMode.TOUCH, wasTouch = true), WowTouch.settingsFor(InputMode.TOUCH_FULL, wasTouch = true))
    }

    @Test
    fun reset_ofTheFullTemplateWritesItsOwnAsset() {
        assertEquals(InputMode.TOUCH_FULL, WowTouchProfiles.resetTarget(InputMode.TOUCH_FULL))
        assertEquals(WowTouchProfiles.FULL_ASSET_PATH, WowTouchProfiles.assetPathFor(InputMode.TOUCH_FULL))
        assertEquals(WowTouchProfiles.ASSET_PATH, WowTouchProfiles.assetPathFor(InputMode.TOUCH))
        assertEquals(WowTouchProfiles.ASSET_PATH, WowTouchProfiles.assetPathFor(InputMode.TOUCH_CUSTOM))
        assertEquals(WowTouchProfiles.ASSET_PATH, WowTouchProfiles.assetPathFor(InputMode.CLASSIC))
        assertTrue(TestTexts.de(resetConfirmText(InputMode.TOUCH_FULL)).contains("Touch (WoW komplett)"))
    }

    @Test
    fun findProfileId_findsTheFullTemplateByName() {
        val profiles = listOf(6 to "Touch (WoW)", 7 to "Touch (WoW komplett)", 9 to "Touch (Mein Layout)")
        assertEquals(7, WowTouchProfiles.findProfileId(profiles, InputMode.TOUCH_FULL.profileName!!))
    }

    @Test
    fun update_overwritesAnUntouchedCopyAndKeepsAnEditedOne() {
        val hash = ProfileUpdatePolicy.sha256Hex(file.readBytes())
        assertEquals(ProfileUpdatePolicy.Action.OVERWRITE, ProfileUpdatePolicy.decide(true, hash, hash, hash))
        assertEquals(ProfileUpdatePolicy.Action.OVERWRITE, ProfileUpdatePolicy.decide(true, "old", "old", hash))
        assertEquals(ProfileUpdatePolicy.Action.KEEP, ProfileUpdatePolicy.decide(true, "edited", "old", hash))
        assertEquals(ProfileUpdatePolicy.Action.CREATE, ProfileUpdatePolicy.decide(false, null, null, hash))
        assertFalse(ProfileUpdatePolicy.hashKey("controls-7.icp") == ProfileUpdatePolicy.hashKey("controls-6.icp"))
    }

    @Test
    fun fileUsesLineFeedsAndNoByteOrderMark() {
        val bytes = file.readBytes()
        assertFalse(bytes.any { it == '\r'.code.toByte() })
        assertFalse(bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte())
    }
}
