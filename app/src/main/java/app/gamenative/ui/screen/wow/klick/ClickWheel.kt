package app.gamenative.ui.screen.wow.klick

import app.gamenative.R
import androidx.annotation.StringRes
import app.gamenative.ui.components.AppText
import kotlin.math.abs
import kotlin.math.hypot

/** Modifier keys that the click wheel and the on-screen buttons can press or latch. */
enum class ModKey { SHIFT, CTRL, ALT }

enum class WheelButton { LEFT, RIGHT }

enum class WheelDirection { CENTER, UP, RIGHT, DOWN, LEFT }

/**
 * One field of the click wheel. [button] null means "only point": the cursor stays where it is and nothing is clicked.
 * [modifier] is pressed around the click.
 */
data class ClickWheelField(
    val id: String,
    val label: String,
    val direction: WheelDirection,
    val modifier: ModKey?,
    val button: WheelButton?,
) {
    val pointsOnly: Boolean get() = button == null
}

/**
 * One click the wheel can perform, selectable per direction. [id] is stored in the preferences: keep it stable.
 * [button] null means "only point".
 */
data class ClickWheelAction(val id: String, @StringRes val labelRes: Int, val modifier: ModKey?, val button: WheelButton?) {
    /** The name in the current app language (for code without a Context). */
    val label: String get() = AppText.get(labelRes)
}

/**
 * The click wheel that opens when a finger rests on the touch surface. Each of the five directions (centre and the four
 * sides) performs one [ClickWheelAction]. The default: left click in the centre, right click below, Shift/Strg/Alt clicks
 * on the other sides. Pure geometry and the field list, no Android types, unit tested.
 */
object ClickWheel {
    /** Every action a direction can be given, in the order of the settings. */
    val ACTIONS: List<ClickWheelAction> = listOf(
        ClickWheelAction("left", R.string.game_wheel_left_click, null, WheelButton.LEFT),
        ClickWheelAction("right", R.string.game_wheel_right_click, null, WheelButton.RIGHT),
        ClickWheelAction("shift", R.string.game_wheel_shift_click, ModKey.SHIFT, WheelButton.LEFT),
        ClickWheelAction("ctrl", R.string.game_wheel_ctrl_click, ModKey.CTRL, WheelButton.LEFT),
        ClickWheelAction("alt", R.string.game_wheel_alt_click, ModKey.ALT, WheelButton.LEFT),
        ClickWheelAction("shift_right", R.string.game_wheel_shift_right_click, ModKey.SHIFT, WheelButton.RIGHT),
        ClickWheelAction("point", R.string.game_wheel_point_only, null, null),
    )

    /** The directions in drawing order. */
    val DIRECTIONS: List<WheelDirection> = listOf(
        WheelDirection.CENTER,
        WheelDirection.UP,
        WheelDirection.RIGHT,
        WheelDirection.DOWN,
        WheelDirection.LEFT,
    )

    /** Standard assignment: action id per direction. */
    val DEFAULT_ASSIGNMENT: Map<WheelDirection, String> = mapOf(
        WheelDirection.CENTER to "left",
        WheelDirection.UP to "shift",
        WheelDirection.RIGHT to "ctrl",
        WheelDirection.DOWN to "right",
        WheelDirection.LEFT to "alt",
    )

    /** The fields in drawing order for the standard assignment. */
    val FIELDS: List<ClickWheelField> = fieldsFor(DEFAULT_ASSIGNMENT)

    fun actionById(id: String?): ClickWheelAction? = ACTIONS.firstOrNull { it.id == id }

    /** Action of [direction] for a stored [id]; a missing or unknown id gives the standard action of that direction. */
    fun actionFor(direction: WheelDirection, id: String?): ClickWheelAction {
        val fallback = DEFAULT_ASSIGNMENT.getValue(direction)
        return actionById(id) ?: ACTIONS.first { it.id == fallback }
    }

    /** The assignment as stored: every direction gets a valid action id (unknown ids become the standard one). */
    fun assignmentFrom(stored: (WheelDirection) -> String?): Map<WheelDirection, String> =
        DIRECTIONS.associateWith { actionFor(it, stored(it)).id }

    /** The next action id in settings order after [currentId], wrapping around. Used by the tap-to-cycle setting. */
    fun nextActionId(currentId: String?): String {
        val index = ACTIONS.indexOfFirst { it.id == currentId }
        return ACTIONS[(index + 1) % ACTIONS.size].id
    }

    /** The fields in drawing order for an [assignment]; missing directions get their standard action. */
    fun fieldsFor(assignment: Map<WheelDirection, String>): List<ClickWheelField> = DIRECTIONS.map { direction ->
        val action = actionFor(direction, assignment[direction])
        ClickWheelField(action.id, action.label, direction, action.modifier, action.button)
    }

    /** Radius of the centre field in dp; roughly a fingertip, so a resting finger stays in the centre. */
    const val INNER_RADIUS_DP = 30f

    /** Outer radius in dp; a finger beyond this circle cancels the wheel. */
    const val OUTER_RADIUS_DP = 104f

    /** Radii in pixels. */
    data class Geometry(val innerRadius: Float, val outerRadius: Float) {
        val labelRadius: Float get() = (innerRadius + outerRadius) / 2f

        companion object {
            fun forDensity(density: Float): Geometry = Geometry(INNER_RADIUS_DP * density, OUTER_RADIUS_DP * density)
        }
    }

    fun fieldFor(direction: WheelDirection, fields: List<ClickWheelField> = FIELDS): ClickWheelField =
        fields.first { it.direction == direction }

    /**
     * The field under a finger that is ([dx], [dy]) pixels away from where the wheel opened (screen coordinates,
     * y grows downwards), or null when the finger left the circle: letting go there cancels.
     * On the diagonals the vertical fields win. [fields] is the current assignment, see [fieldsFor].
     */
    fun fieldAt(dx: Float, dy: Float, geometry: Geometry, fields: List<ClickWheelField> = FIELDS): ClickWheelField? {
        val distance = hypot(dx, dy)
        if (distance <= geometry.innerRadius) return fieldFor(WheelDirection.CENTER, fields)
        if (distance > geometry.outerRadius) return null
        val direction = if (abs(dy) >= abs(dx)) {
            if (dy < 0f) WheelDirection.UP else WheelDirection.DOWN
        } else {
            if (dx > 0f) WheelDirection.RIGHT else WheelDirection.LEFT
        }
        return fieldFor(direction, fields)
    }
}

/** One step of the key and mouse sequence that executes a wheel field. */
enum class StepKind { KEY_DOWN, KEY_UP, BUTTON_DOWN, BUTTON_UP, DONE }

data class ClickStep(val atMs: Long, val kind: StepKind, val key: ModKey? = null, val button: WheelButton? = null)

/** The timed sequence for a wheel field: modifier down, click, modifier up. */
object ClickScript {
    /** Lead time so the game sees the modifier before the click. */
    const val LEAD_MS = 30L

    /** How long the mouse button stays down. */
    const val CLICK_MS = 50L

    /** Gap between button up and modifier up. */
    const val TRAIL_MS = 30L

    /**
     * @param held modifiers that are already held down (latched or fixed by the player): they are neither pressed nor
     *             released again, so the latch keeps working until the click is done.
     * Empty for "only point". The last step is always [StepKind.DONE].
     */
    fun build(field: ClickWheelField, held: Set<ModKey>): List<ClickStep> {
        val button = field.button ?: return emptyList()
        val own = field.modifier?.takeIf { it !in held }
        val steps = ArrayList<ClickStep>()
        var t = 0L
        if (own != null) {
            steps += ClickStep(0L, StepKind.KEY_DOWN, key = own)
            t = LEAD_MS
        }
        steps += ClickStep(t, StepKind.BUTTON_DOWN, button = button)
        t += CLICK_MS
        steps += ClickStep(t, StepKind.BUTTON_UP, button = button)
        if (own != null) {
            t += TRAIL_MS
            steps += ClickStep(t, StepKind.KEY_UP, key = own)
        }
        steps += ClickStep(t, StepKind.DONE)
        return steps
    }
}
