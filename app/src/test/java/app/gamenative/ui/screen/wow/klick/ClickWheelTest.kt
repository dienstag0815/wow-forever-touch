package app.gamenative.ui.screen.wow.klick

import org.junit.Rule
import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickWheelTest {
    @get:Rule
    val texts = TestTexts.rule("de")

    private val geo = ClickWheel.Geometry(innerRadius = 30f, outerRadius = 100f)

    private fun idAt(dx: Float, dy: Float, fields: List<ClickWheelField> = ClickWheel.FIELDS): String? =
        ClickWheel.fieldAt(dx, dy, geo, fields)?.id

    /** The standard fields, but with [direction] given the action [actionId]. */
    private fun fieldsWith(direction: WheelDirection, actionId: String): List<ClickWheelField> =
        ClickWheel.fieldsFor(ClickWheel.DEFAULT_ASSIGNMENT + (direction to actionId))

    @Test
    fun fieldList_hasFiveFieldsWithTheStandardAssignment() {
        val byDirection = ClickWheel.FIELDS.associateBy { it.direction }
        assertEquals(5, ClickWheel.FIELDS.size)
        assertEquals(WheelButton.LEFT, byDirection.getValue(WheelDirection.CENTER).button)
        assertNull(byDirection.getValue(WheelDirection.CENTER).modifier)
        assertEquals(ModKey.SHIFT, byDirection.getValue(WheelDirection.UP).modifier)
        assertEquals(WheelButton.LEFT, byDirection.getValue(WheelDirection.UP).button)
        assertEquals(ModKey.CTRL, byDirection.getValue(WheelDirection.RIGHT).modifier)
        assertEquals(WheelButton.LEFT, byDirection.getValue(WheelDirection.RIGHT).button)
        assertEquals(WheelButton.RIGHT, byDirection.getValue(WheelDirection.DOWN).button)
        assertNull(byDirection.getValue(WheelDirection.DOWN).modifier)
        assertEquals(ModKey.ALT, byDirection.getValue(WheelDirection.LEFT).modifier)
        assertEquals(WheelButton.LEFT, byDirection.getValue(WheelDirection.LEFT).button)
    }

    @Test
    fun actionIds_areUniqueAndLabelsNonEmpty() {
        assertEquals(ClickWheel.ACTIONS.size, ClickWheel.ACTIONS.map { it.id }.toSet().size)
        assertTrue(ClickWheel.ACTIONS.all { it.label.isNotBlank() })
        assertTrue(ClickWheel.ACTIONS.any { it.id == "point" && it.button == null })
    }

    @Test
    fun restingFinger_isCentre() {
        assertEquals("left", idAt(0f, 0f))
        assertEquals("left", idAt(10f, -12f))
        assertEquals("left", idAt(30f, 0f)) // exactly on the inner radius still counts as centre
    }

    @Test
    fun fourDirections() {
        assertEquals("shift", idAt(0f, -60f))
        assertEquals("ctrl", idAt(60f, 0f))
        assertEquals("right", idAt(0f, 60f))
        assertEquals("alt", idAt(-60f, 0f))
    }

    @Test
    fun diagonals_goToTheNearerAxis() {
        assertEquals("shift", idAt(20f, -60f))
        assertEquals("ctrl", idAt(60f, -20f))
        assertEquals("right", idAt(-20f, 60f))
        assertEquals("alt", idAt(-60f, 20f))
    }

    @Test
    fun exactDiagonal_verticalWins() {
        assertEquals("shift", idAt(50f, -50f))
        assertEquals("right", idAt(-50f, 50f))
    }

    @Test
    fun beyondTheCircle_cancels() {
        assertNull(idAt(0f, -101f))
        assertNull(idAt(80f, 80f))
        assertEquals("shift", idAt(0f, -100f)) // the rim itself is still inside
    }

    @Test
    fun geometry_scalesWithDensity() {
        val g = ClickWheel.Geometry.forDensity(2f)
        assertEquals(60f, g.innerRadius, 0f)
        assertEquals(208f, g.outerRadius, 0f)
        assertEquals(134f, g.labelRadius, 0f)
    }

    @Test
    fun defaultAssignment_matchesTheFields() {
        assertEquals(ClickWheel.DEFAULT_ASSIGNMENT, ClickWheel.assignmentFrom { null })
        assertEquals(
            listOf("left", "shift", "ctrl", "right", "alt"),
            ClickWheel.FIELDS.map { it.id },
        )
    }

    @Test
    fun assignmentFrom_keepsValidIds_andFallsBackForMissingOrUnknown() {
        val stored = mapOf(
            WheelDirection.CENTER to "point",
            WheelDirection.UP to "no_such_action",
        )
        val assignment = ClickWheel.assignmentFrom { stored[it] }
        assertEquals("point", assignment.getValue(WheelDirection.CENTER))
        assertEquals("shift", assignment.getValue(WheelDirection.UP)) // unknown id: standard
        assertEquals("ctrl", assignment.getValue(WheelDirection.RIGHT)) // missing: standard
        assertEquals("right", assignment.getValue(WheelDirection.DOWN))
        assertEquals("alt", assignment.getValue(WheelDirection.LEFT))
    }

    @Test
    fun storedAssignment_roundTrips() {
        // Writing an id and reading it back gives the same id for every direction.
        val written = mapOf(
            WheelDirection.CENTER to "shift_right",
            WheelDirection.UP to "point",
            WheelDirection.RIGHT to "left",
            WheelDirection.DOWN to "alt",
            WheelDirection.LEFT to "ctrl",
        )
        assertEquals(written, ClickWheel.assignmentFrom { written[it] })
    }

    @Test
    fun wheelKey_isStablePerDirection() {
        assertEquals("touch_wheel_center", TouchOptionStore.wheelKey(WheelDirection.CENTER))
        assertEquals("touch_wheel_down", TouchOptionStore.wheelKey(WheelDirection.DOWN))
    }

    @Test
    fun nextActionId_cyclesThroughAllActionsAndWraps() {
        var id = "left"
        val seen = mutableListOf(id)
        repeat(ClickWheel.ACTIONS.size) {
            id = ClickWheel.nextActionId(id)
            seen += id
        }
        assertEquals(ClickWheel.ACTIONS.map { it.id } + "left", seen)
        assertEquals("left", ClickWheel.nextActionId(ClickWheel.ACTIONS.last().id))
        assertEquals("left", ClickWheel.nextActionId("no_such_action"))
    }

    @Test
    fun customAssignment_drawsAndHitsTheChosenAction() {
        val fields = fieldsWith(WheelDirection.DOWN, "point")
        assertEquals("point", idAt(0f, 60f, fields))
        assertTrue(ClickWheel.fieldFor(WheelDirection.DOWN, fields).pointsOnly)
        assertEquals(ModKey.SHIFT, ClickWheel.fieldFor(WheelDirection.UP, fieldsWith(WheelDirection.UP, "shift_right")).modifier)
    }
}

class ClickScriptTest {
    private fun field(id: String) = ClickWheel.FIELDS.first { it.id == id }

    @Test
    fun shiftClick_pressesShiftThenClicksThenReleases() {
        val steps = ClickScript.build(field("shift"), emptySet())
        assertEquals(
            listOf(
                ClickStep(0, StepKind.KEY_DOWN, key = ModKey.SHIFT),
                ClickStep(30, StepKind.BUTTON_DOWN, button = WheelButton.LEFT),
                ClickStep(80, StepKind.BUTTON_UP, button = WheelButton.LEFT),
                ClickStep(110, StepKind.KEY_UP, key = ModKey.SHIFT),
                ClickStep(110, StepKind.DONE),
            ),
            steps,
        )
    }

    @Test
    fun ctrlAndAlt_useLeftClick() {
        val ctrl = ClickScript.build(field("ctrl"), emptySet())
        val alt = ClickScript.build(field("alt"), emptySet())
        assertEquals(ModKey.CTRL, ctrl.first().key)
        assertEquals(WheelButton.LEFT, ctrl[1].button)
        assertEquals(ModKey.ALT, alt.first().key)
        assertEquals(WheelButton.LEFT, alt[1].button)
    }

    @Test
    fun centre_isAPlainLeftClick() {
        val steps = ClickScript.build(field("left"), emptySet())
        assertEquals(
            listOf(
                ClickStep(0, StepKind.BUTTON_DOWN, button = WheelButton.LEFT),
                ClickStep(50, StepKind.BUTTON_UP, button = WheelButton.LEFT),
                ClickStep(50, StepKind.DONE),
            ),
            steps,
        )
    }

    @Test
    fun down_isAPlainRightClick() {
        val steps = ClickScript.build(field("right"), emptySet())
        assertEquals(
            listOf(
                ClickStep(0, StepKind.BUTTON_DOWN, button = WheelButton.RIGHT),
                ClickStep(50, StepKind.BUTTON_UP, button = WheelButton.RIGHT),
                ClickStep(50, StepKind.DONE),
            ),
            steps,
        )
    }

    @Test
    fun shiftRightClick_pressesShiftThenRightClick() {
        val fields = ClickWheel.fieldsFor(ClickWheel.DEFAULT_ASSIGNMENT + (WheelDirection.UP to "shift_right"))
        val steps = ClickScript.build(fields.first { it.direction == WheelDirection.UP }, emptySet())
        assertEquals(ModKey.SHIFT, steps.first().key)
        assertEquals(WheelButton.RIGHT, steps[1].button)
    }

    @Test
    fun alreadyHeldModifier_isNeitherPressedNorReleasedAgain() {
        val steps = ClickScript.build(field("shift"), setOf(ModKey.SHIFT))
        assertTrue(steps.none { it.kind == StepKind.KEY_DOWN || it.kind == StepKind.KEY_UP })
        assertEquals(listOf(StepKind.BUTTON_DOWN, StepKind.BUTTON_UP, StepKind.DONE), steps.map { it.kind })
    }

    @Test
    fun otherHeldModifier_doesNotSuppressTheWheelModifier() {
        val steps = ClickScript.build(field("shift"), setOf(ModKey.CTRL))
        assertEquals(StepKind.KEY_DOWN, steps.first().kind)
        assertEquals(ModKey.SHIFT, steps.first().key)
    }

    @Test
    fun onlyPoint_doesNothing() {
        val point = ClickWheel.fieldsFor(ClickWheel.DEFAULT_ASSIGNMENT + (WheelDirection.DOWN to "point"))
            .first { it.direction == WheelDirection.DOWN }
        assertTrue(ClickScript.build(point, setOf(ModKey.SHIFT)).isEmpty())
    }
}
