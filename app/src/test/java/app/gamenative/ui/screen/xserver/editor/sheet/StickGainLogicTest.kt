package app.gamenative.ui.screen.xserver.editor.sheet

import app.gamenative.ui.screen.wow.STICK_GAIN_STEPS
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import app.gamenative.R

class StickGainLogicTest {
    private fun combos(vararg bindings: Binding) = bindings.map { BindingCombo.of(it) }

    private val leftStick = combos(
        Binding.GAMEPAD_LEFT_THUMB_UP, Binding.GAMEPAD_LEFT_THUMB_RIGHT, Binding.GAMEPAD_LEFT_THUMB_DOWN, Binding.GAMEPAD_LEFT_THUMB_LEFT,
    )
    private val rightStick = combos(
        Binding.GAMEPAD_RIGHT_THUMB_UP, Binding.GAMEPAD_RIGHT_THUMB_RIGHT, Binding.GAMEPAD_RIGHT_THUMB_DOWN, Binding.GAMEPAD_RIGHT_THUMB_LEFT,
    )
    private val wasd = combos(Binding.KEY_W, Binding.KEY_D, Binding.KEY_S, Binding.KEY_A)
    private val arrows = combos(Binding.KEY_UP, Binding.KEY_RIGHT, Binding.KEY_DOWN, Binding.KEY_LEFT)
    private val mouse = combos(Binding.MOUSE_MOVE_UP, Binding.MOUSE_MOVE_RIGHT, Binding.MOUSE_MOVE_DOWN, Binding.MOUSE_MOVE_LEFT)

    @Test
    fun `stick with the left pad stick sets walking`() {
        assertEquals(StickGainScope.WALK, stickGainScope(ControlElement.Type.STICK, leftStick))
    }

    @Test
    fun `stick with the right pad stick sets the camera`() {
        assertEquals(StickGainScope.CAMERA, stickGainScope(ControlElement.Type.STICK, rightStick))
    }

    @Test
    fun `stick with WASD or arrow keys has no sensitivity`() {
        assertEquals(StickGainScope.NONE, stickGainScope(ControlElement.Type.STICK, wasd))
        assertEquals(StickGainScope.NONE, stickGainScope(ControlElement.Type.STICK, arrows))
    }

    @Test
    fun `stick with mouse movement has no sensitivity`() {
        assertEquals(StickGainScope.NONE, stickGainScope(ControlElement.Type.STICK, mouse))
    }

    @Test
    fun `empty stick has no sensitivity`() {
        assertEquals(StickGainScope.NONE, stickGainScope(ControlElement.Type.STICK, List(4) { BindingCombo.none() }))
        assertEquals(StickGainScope.NONE, stickGainScope(ControlElement.Type.STICK, emptyList()))
    }

    @Test
    fun `the first slot with a pad axis decides`() {
        val mixed = listOf(BindingCombo.of(Binding.KEY_W), BindingCombo.of(Binding.GAMEPAD_RIGHT_THUMB_RIGHT), BindingCombo.none(), BindingCombo.none())
        assertEquals(StickGainScope.CAMERA, stickGainScope(ControlElement.Type.STICK, mixed))
    }

    @Test
    fun `a sequence with a pad axis is no stick axis`() {
        val sequence = BindingCombo.fromBindings(
            listOf(Binding.GAMEPAD_RIGHT_THUMB_UP, Binding.KEY_W),
            BindingCombo.Mode.SEQUENCE,
        )
        assertTrue(sequence.isSequence)
        assertEquals(StickGainScope.NONE, stickGainScope(ControlElement.Type.STICK, listOf(sequence)))
    }

    @Test
    fun `a key held together with a pad axis still counts as the axis`() {
        val together = BindingCombo.fromBindings(listOf(Binding.GAMEPAD_LEFT_THUMB_UP, Binding.KEY_W))
        assertFalse(together.isSequence)
        assertEquals(StickGainScope.WALK, stickGainScope(ControlElement.Type.STICK, listOf(together)))
    }

    @Test
    fun `trackpad with a pad stick uses the camera value whichever stick it names`() {
        assertEquals(StickGainScope.CAMERA, stickGainScope(ControlElement.Type.TRACKPAD, rightStick))
        assertEquals(StickGainScope.CAMERA, stickGainScope(ControlElement.Type.TRACKPAD, leftStick))
    }

    @Test
    fun `trackpad with mouse or keys has no sensitivity`() {
        assertEquals(StickGainScope.NONE, stickGainScope(ControlElement.Type.TRACKPAD, mouse))
        assertEquals(StickGainScope.NONE, stickGainScope(ControlElement.Type.TRACKPAD, wasd))
    }

    @Test
    fun `trackpad slot with mouse movement ignores a pad axis in the same combo`() {
        val both = BindingCombo.fromBindings(listOf(Binding.MOUSE_MOVE_UP, Binding.GAMEPAD_RIGHT_THUMB_UP))
        assertEquals(StickGainScope.NONE, stickGainScope(ControlElement.Type.TRACKPAD, listOf(both)))
    }

    @Test
    fun `other element types have no stick sensitivity`() {
        for (type in listOf(
            ControlElement.Type.BUTTON,
            ControlElement.Type.D_PAD,
            ControlElement.Type.RANGE_BUTTON,
            ControlElement.Type.SCROLL_WHEEL,
            ControlElement.Type.SHOOTER_MODE,
        )) {
            assertEquals(type.name, StickGainScope.NONE, stickGainScope(type, rightStick))
        }
    }

    @Test
    fun `greyed out slider shows the value the element would use with a pad binding`() {
        assertEquals(StickGainScope.WALK, StickGainScope.NONE.orDefaultFor(ControlElement.Type.STICK))
        assertEquals(StickGainScope.CAMERA, StickGainScope.NONE.orDefaultFor(ControlElement.Type.TRACKPAD))
        assertEquals(StickGainScope.CAMERA, StickGainScope.CAMERA.orDefaultFor(ControlElement.Type.STICK))
        assertEquals(StickGainScope.WALK, StickGainScope.WALK.orDefaultFor(ControlElement.Type.TRACKPAD))
    }

    @Test
    fun `slider positions map to the offered steps`() {
        for ((index, step) in STICK_GAIN_STEPS.withIndex()) {
            assertEquals(index, gainStepIndex(step))
            assertEquals(step, gainForSliderValue(index.toFloat()), 0f)
        }
        assertEquals(STICK_GAIN_STEPS.first(), gainForSliderValue(-3f), 0f)
        assertEquals(STICK_GAIN_STEPS.last(), gainForSliderValue(99f), 0f)
        assertEquals(STICK_GAIN_STEPS[2], gainForSliderValue(2.4f), 0f)
        // a value between two steps (hand-edited) shows at the nearest one
        assertEquals(2, gainStepIndex(1.2f))
    }

    @Test
    fun `texts name the scope and where else to change it`() {
        assertEquals(R.string.editor_gain_hint_walk, stickGainHint(StickGainScope.WALK).res)
        assertEquals(R.string.editor_gain_hint_camera, stickGainHint(StickGainScope.CAMERA).res)
        assertEquals(R.string.editor_gain_hint_none, stickGainHint(StickGainScope.NONE).res)
        assertEquals(R.string.editor_gain_title_walk, stickGainTitle(StickGainScope.WALK).res)
        assertEquals(R.string.editor_gain_title_camera, stickGainTitle(StickGainScope.CAMERA).res)
    }
}
