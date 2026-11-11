package app.gamenative.ui.screen.wow

import app.gamenative.TestTexts
import app.gamenative.backup.BackupAllowlist
import app.gamenative.wowaction.ReloadGuard
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowicons.AddonLayoutSignal
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.StickRing
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The first-start standard layout "Touch (Standard)" (controls-8.icp, the layout built on the device): loads, both sticks carry a ring,
 * ESC/keyboard/menu on top, no two elements really overlap at the Fold 8 sizes (neighbouring buttons may touch by half a snapping size);
 * the basis template of "Eigenes Layout"; the pure first-start logic.
 */
class StandardLayoutTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private val assetDir = File("src/main/assets/inputcontrols/profiles")
    private val json = JSONObject(File("src/main/assets/" + WowTouchProfiles.STANDARD_ASSET_PATH).readText())
    private val fullJson = JSONObject(File("src/main/assets/" + WowTouchProfiles.FULL_ASSET_PATH).readText())
    private val basisJson = JSONObject(File("src/main/assets/" + WowTouchProfiles.BASIS_ASSET_PATH).readText())
    private val elements = json.getJSONArray("elements")

    private fun all() = (0 until elements.length()).map { elements.getJSONObject(it) }
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

    private fun leftStick() = ofType("STICK").single { it.getJSONArray("bindings").getString(0) == "GAMEPAD_LEFT_THUMB_UP" }
    private fun rightStick() = ofType("STICK").single { it.getJSONArray("bindings").getString(0) == "GAMEPAD_RIGHT_THUMB_UP" }
    private val topRowBindings = listOf("KEY_ESC", "SHOW_KEYBOARD", "OPEN_NAVIGATION_MENU")
    private fun topRow(j: JSONObject): List<JSONObject> {
        val list = j.getJSONArray("elements")
        val rows = (0 until list.length()).map { list.getJSONObject(it) }
        return topRowBindings.map { name -> rows.single { bindingNames(it).contains(name) } }
    }

    // --- geometry, mirrors ControlElement.computeBoundingBox (incl. the outer ring of a stick) -----------------------

    private sealed class Shape {
        data class Rect(val l: Float, val t: Float, val r: Float, val b: Float) : Shape()
        data class Circle(val cx: Float, val cy: Float, val radius: Float) : Shape()
    }

    private fun shapeOf(e: JSONObject, width: Int, height: Int): Shape {
        val snap = width / 100
        val maxHeight = (Math.round(height / snap.toFloat()) * snap)
        val x = (e.getDouble("x") * width).toInt()
        val y = (e.getDouble("y") * maxHeight).toInt()
        val scale = e.getDouble("scale").toFloat()
        val ws = scale * e.optDouble("widthScale", 1.0).toFloat()
        val hs = scale * e.optDouble("heightScale", 1.0).toFloat()
        return when (e.getString("type")) {
            "STICK" -> {
                var half = 6 * snap * scale
                val ring = e.optJSONObject("ring")
                if (ring != null) {
                    val outer = ring.optJSONArray("outer")
                    val hasOuter = outer != null && (0 until outer.length()).any { !outer.isNull(it) }
                    val outerExtent = if (hasOuter) StickRing.OUTER_GAP + ring.getDouble("width").toFloat() else 0f
                    half += ceil((ring.getDouble("gap") + ring.getDouble("width") + outerExtent) * snap * scale).toFloat()
                }
                Shape.Circle(x.toFloat(), y.toFloat(), half)
            }
            "SCROLL_WHEEL" -> {
                val rotated = e.optInt("orientation", 0) == 1
                val hw = (if (rotated) 5 else 2) * snap * ws
                val hh = (if (rotated) 2 else 5) * snap * hs
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

    /** How deep two shapes reach into each other (0 when apart), in pixels. */
    private fun depth(a: Shape, b: Shape): Float = when {
        a is Shape.Rect && b is Shape.Rect -> max(0f, min(min(a.r, b.r) - max(a.l, b.l), min(a.b, b.b) - max(a.t, b.t)))
        a is Shape.Circle && b is Shape.Circle -> max(0f, a.radius + b.radius - hypot(a.cx - b.cx, a.cy - b.cy))
        a is Shape.Circle && b is Shape.Rect -> circleRectDepth(a, b)
        a is Shape.Rect && b is Shape.Circle -> circleRectDepth(b, a)
        else -> 0f
    }

    private fun circleRectDepth(c: Shape.Circle, r: Shape.Rect): Float {
        val nx = max(r.l, min(c.cx, r.r))
        val ny = max(r.t, min(c.cy, r.b))
        return max(0f, c.radius - hypot(c.cx - nx, c.cy - ny))
    }

    private fun bounds(s: Shape): Shape.Rect = when (s) {
        is Shape.Rect -> s
        is Shape.Circle -> Shape.Rect(s.cx - s.radius, s.cy - s.radius, s.cx + s.radius, s.cy + s.radius)
    }

    /** Same five sizes as WowTouchFullProfileTest (Fold 8 inner display and neighbours). */
    private val screens = listOf(2000 to 1510, 2000 to 1500, 2000 to 1520, 1600 to 1208, 2480 to 1872)

    // --- the file --------------------------------------------------------------------------------------------------

    @Test
    fun profile_hasItsOwnIdAndName_uniqueAmongAssets() {
        assertEquals(8, json.getInt("id"))
        assertEquals(WowTouch.STANDARD_PROFILE_NAME, json.getString("name"))
        assertEquals("Touch (Standard)", json.getString("name"))
        assertEquals(InputMode.TOUCH_STANDARD.profileName, json.getString("name"))
        val ids = ArrayList<Int>()
        val names = ArrayList<String>()
        for (f in assetDir.listFiles()!!) {
            val j = JSONObject(f.readText())
            ids.add(j.getInt("id"))
            names.add(j.getString("name"))
        }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(names.size, names.toSet().size)
        assertFalse("the player's own profile is never an asset", names.contains(WowTouch.CUSTOM_PROFILE_NAME))
    }

    @Test
    fun everyElementHasTheFieldsTheLoaderReads_andEveryNameResolves() {
        val menuIds = (0 until json.getJSONArray("radialMenus").length()).map { json.getJSONArray("radialMenus").getJSONObject(it).getString("id") }.toSet()
        for (e in all()) {
            ControlElement.Type.valueOf(e.getString("type"))
            ControlElement.Shape.valueOf(e.getString("shape"))
            e.getBoolean("toggleSwitch")
            e.getString("text"); e.getInt("iconId")
            assertTrue(e.getDouble("x") in 0.0..1.0)
            assertTrue(e.getDouble("y") in 0.0..1.0)
            assertTrue(e.getDouble("scale") > 0.0)
            for (name in bindingNames(e)) Binding.valueOf(name)
            val action = e.optString("wowAction")
            if (action.isNotEmpty()) assertTrue(action, action == ReloadGuard.ACTION_ID || WowActionCatalog.find(action) != null)
            val menu = e.optString("radialMenuId")
            if (menu.isNotEmpty()) assertTrue("radial menu $menu exists", menu in menuIds)
        }
        val menus = json.getJSONArray("radialMenus")
        for (i in 0 until menus.length()) {
            val slots = menus.getJSONObject(i).getJSONArray("slots")
            for (s in 0 until slots.length()) assertNotNull(WowActionCatalog.find(slots.getJSONObject(s).getString("wowAction")))
        }
    }

    // --- sticks and rings ----------------------------------------------------------------------------------------------

    private fun ringItems(stick: JSONObject): List<JSONObject> {
        val ring = stick.getJSONObject("ring")
        assertEquals(8, ring.getInt("segments"))
        assertEquals(8, StickRing.normalizeSegments(ring.getInt("segments")))
        val items = ring.getJSONArray("items")
        assertEquals(8, items.length())
        return (0 until 8).map { items.getJSONObject(it) }
    }

    @Test
    fun twoSticksBothWithAnEightSegmentRing() {
        val sticks = ofType("STICK")
        assertEquals(2, sticks.size)
        for (s in sticks) ringItems(s)
        assertTrue(leftStick().getDouble("x") < 0.4)
        assertTrue(rightStick().getDouble("x") > 0.6)
        assertTrue(leftStick().getDouble("y") > 0.6 && rightStick().getDouble("y") > 0.6)
    }

    @Test
    fun leftRing_isActionBarTwo_nineElevenTwelveThenOneToFive() {
        val items = ringItems(leftStick())
        assertEquals(
            listOf(9, 11, 12, 1, 2, 3, 4, 5).map { "MULTIACTIONBAR1BUTTON$it" },
            items.map { it.getString("wowAction") },
        )
        // WoW has no standard key for bar 2: a place counts when it has a key or a WoW action.
        for (item in items) {
            assertTrue(item.getString("binding") != "NONE" || item.optString("wowAction").isNotEmpty())
            assertNotNull(WowActionCatalog.find(item.getString("wowAction")))
        }
        assertFalse(leftStick().has("center"))
    }

    @Test
    fun rightRing_isActionBarOneToEight_outerRingTabAndRightClick_middleJump() {
        val stick = rightStick()
        val items = ringItems(stick)
        for (n in 1..8) {
            assertEquals("ACTIONBUTTON$n", items[n - 1].getString("wowAction"))
            assertEquals("KEY_$n", items[n - 1].getString("binding"))
            assertEquals("$n", WowActionCatalog.find("ACTIONBUTTON$n")!!.defaultKey)
        }
        val outer = stick.getJSONObject("ring").getJSONArray("outer")
        assertEquals("KEY_TAB", outer.getJSONObject(0).getString("binding"))
        assertEquals("MOUSE_RIGHT_BUTTON", outer.getJSONObject(1).getString("binding"))
        for (i in 2 until outer.length()) assertTrue(outer.isNull(i))
        val center = stick.getJSONObject("center")
        assertEquals("JUMP", center.getString("wowAction"))
        assertEquals("KEY_SPACE", center.getString("binding"))
    }

    // --- top row ----------------------------------------------------------------------------------------------------

    @Test
    fun escKeyboardMenu_sitOnTopInTheMiddle_inTheOrderEscKeyboardMenu() {
        val mine = topRow(json)
        for (a in mine) {
            assertTrue("y < 0.15", a.getDouble("y") < 0.15)
            assertTrue("in the middle", a.getDouble("x") in 0.4..0.6)
        }
        assertTrue(mine[0].getDouble("x") < mine[1].getDouble("x") && mine[1].getDouble("x") < mine[2].getDouble("x"))
        assertEquals(mine[0].getDouble("y"), mine[1].getDouble("y"), 1e-9)
        assertEquals(mine[1].getDouble("y"), mine[2].getDouble("y"), 1e-9)
    }

    @Test
    fun bookButton_sitsInTheTopRowNextToMenu() {
        val esc = topRow(json).first { it.getJSONArray("bindings").getString(0) == "KEY_ESC" }
        val list = json.getJSONArray("elements")
        val book = (0 until list.length()).map { list.getJSONObject(it) }.first { it.optString("text") == "📖" }
        assertEquals(esc.getDouble("y"), book.getDouble("y"), 1e-9)
        val menu = topRow(json).last()
        assertTrue("book right of Menu", book.getDouble("x") > menu.getDouble("x"))
        assertTrue("row flush with the top edge (y < 0.04)", book.getDouble("y") < 0.04)
    }

    @Test
    fun basisTemplate_hasExactlyTheTopRow_andIsNotAProfileAsset() {
        val list = basisJson.getJSONArray("elements")
        assertEquals(3, list.length())
        // The template keeps its own positions (it is a start for "Eigenes Layout", not a copy of the standard layout).
        for (a in topRow(basisJson)) {
            assertTrue("y < 0.15", a.getDouble("y") < 0.15)
            assertTrue("in the middle", a.getDouble("x") in 0.2..0.5)
        }
        assertEquals("Touch (Mein Layout)", basisJson.getString("name"))
        assertTrue("not copied into the profile list", !WowTouchProfiles.BASIS_ASSET_PATH.startsWith("inputcontrols/profiles/"))
        assertNull(assetDir.listFiles()!!.firstOrNull { JSONObject(it.readText()).getString("name") == basisJson.getString("name") })
    }

    // --- layout -----------------------------------------------------------------------------------------------------

    @Test
    fun noElementsOverlap_andAllInsideTheScreen_atFiveSizes() {
        for ((w, h) in screens) {
            val shapes = all().map { shapeOf(it, w, h) }
            for (i in shapes.indices) {
                for (j in i + 1 until shapes.size) {
                    // buttons set next to each other on the device may touch; a real overlap is deeper than half a snapping size
                    assertTrue("elements $i and $j overlap at ${w}x$h", depth(shapes[i], shapes[j]) <= 0.5f * (w / 100))
                }
                val r = bounds(shapes[i])
                assertTrue("element $i left at ${w}x$h", r.l >= 0f)
                assertTrue("element $i top at ${w}x$h", r.t >= 0f)
                assertTrue("element $i right at ${w}x$h", r.r <= w.toFloat())
                assertTrue("element $i bottom at ${w}x$h", r.b <= h.toFloat())
            }
        }
    }

    // --- mode, reset ------------------------------------------------------------------------------------------------

    @Test
    fun modeAndResetPointAtTheStandardAsset() {
        assertTrue(InputMode.TOUCH_STANDARD.isTouch)
        assertEquals(WowTouchProfiles.STANDARD_ASSET_PATH, WowTouchProfiles.assetPathFor(InputMode.TOUCH_STANDARD))
        assertEquals(WowTouchProfiles.FULL_ASSET_PATH, WowTouchProfiles.assetPathFor(InputMode.TOUCH_FULL))
        assertEquals(InputMode.TOUCH_STANDARD, WowTouchProfiles.resetTarget(InputMode.TOUCH_STANDARD))
        assertEquals(12, WowTouchProfiles.findProfileId(listOf(7 to "Touch (WoW komplett)", 12 to "Touch (Standard)"), "Touch (Standard)"))
        assertEquals(12, WowTouchProfiles.findProfileId(listOf(8 to "Touch (Mein Layout)", 12 to "Touch (Standard)"), "Touch (Standard)"))
    }

    @Test
    fun freshInstallAndFirstTimePlayer_getTheStandardLayoutWithoutAnyAction() {
        assertEquals(InputMode.TOUCH_STANDARD, InputMode.resolve(null, freshInstall = true, launchedBefore = false))
        assertEquals(InputMode.TOUCH_STANDARD, InputMode.resolve(null, freshInstall = false, launchedBefore = false))
        assertEquals(InputMode.TOUCH_STANDARD, InputMode.resolve(null, freshInstall = null, launchedBefore = false))
        assertEquals(InputMode.CLASSIC, InputMode.resolve(null, freshInstall = false, launchedBefore = true))
    }

    // --- first-start logic ------------------------------------------------------------------------------------------

    @Test
    fun shouldAsk_onlyWhenNothingDecidedAndNeverLaunched() {
        assertTrue(FirstLayoutChoice.shouldAsk(decided = false, launchedBefore = false))
        assertFalse(FirstLayoutChoice.shouldAsk(decided = true, launchedBefore = false))
        assertFalse(FirstLayoutChoice.shouldAsk(decided = false, launchedBefore = true))
        assertFalse(FirstLayoutChoice.shouldAsk(decided = true, launchedBefore = true))
    }

    @Test
    fun planFor_standard() {
        val plan = FirstLayoutChoice.planFor(LayoutChoice.STANDARD)
        assertEquals(InputMode.TOUCH_STANDARD, plan.mode)
        assertFalse(plan.createOwnProfile)
        assertTrue(plan.shooterModeOff)
        assertFalse(plan.openEditor)
        assertTrue(plan.addonSignal)
    }

    @Test
    fun planFor_own() {
        val plan = FirstLayoutChoice.planFor(LayoutChoice.OWN)
        assertEquals(InputMode.TOUCH_CUSTOM, plan.mode)
        assertTrue(plan.createOwnProfile)
        assertTrue(plan.shooterModeOff)
        assertTrue(plan.openEditor)
        assertFalse(plan.addonSignal)
    }

    @Test
    fun shooterModeFor_pendingOffWinsOnce_elseTheContainerKeepsItsValue() {
        assertFalse(FirstLayoutChoice.shooterModeFor(pendingOff = true, current = true))
        assertFalse(FirstLayoutChoice.shooterModeFor(pendingOff = true, current = false))
        assertTrue(FirstLayoutChoice.shooterModeFor(pendingOff = false, current = true))
        assertFalse(FirstLayoutChoice.shooterModeFor(pendingOff = false, current = false))
    }

    // --- addon signal and backup ---------------------------------------------------------------------------------------

    @Test
    fun addonSignalKey_isBackedUp_theAskedMarkerIsNot() {
        assertEquals("layout_standard", AddonLayoutSignal.KEY)
        val spec = BackupAllowlist.find(BackupAllowlist.FILE_MAIN, AddonLayoutSignal.KEY)
        assertNotNull(spec)
        assertEquals(app.gamenative.backup.SettingType.BOOL, spec!!.type)
        assertNull(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, "layout_choice"))
        assertNull(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, "layout_shooter_off_pending"))
        assertNull(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, "layout_open_editor_pending"))
    }
}
