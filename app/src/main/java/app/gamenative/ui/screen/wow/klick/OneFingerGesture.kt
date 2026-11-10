package app.gamenative.ui.screen.wow.klick

import app.gamenative.ui.screen.wow.SwipeScrollMath
import com.winlator.inputcontrols.ScrollWheelMath
import kotlin.math.hypot

/** One thing the touch view has to do, decided by [OneFingerGesture]. Coordinates are raw view pixels. */
class GestureEvent(val kind: Kind, val x: Float = 0f, val y: Float = 0f, val field: ClickWheelField? = null, val ticks: Int = 0) {
    enum class Kind {
        /** Left click (press + release) at x/y. */
        CLICK,

        /** Two quick left clicks at x/y. */
        DOUBLE_CLICK,

        /** Hold: press the left button at x/y; the pointer then follows the finger ([LEFT_MOVE]) until [LEFT_UP]. */
        LEFT_DOWN,
        LEFT_MOVE,
        LEFT_UP,

        /** Tap + hold: the wheel opens around x/y. */
        WHEEL_OPENED,
        WHEEL_CHANGED,

        /** The finger lifted over the wheel: run [field] (null = left the circle, cancelled). */
        WHEEL_COMMIT,

        /** The finger moved too early for hold or wheel: camera drag (right button, relative), handled by the view's drag code. */
        CAMERA_STARTED,
        CAMERA_ENDED,

        /** A vertical swipe began inside a scroll area: the view puts the pointer at x/y (the touch-down point) and keeps it there. */
        SCROLL_STARTED,

        /** [GestureEvent.ticks] mouse wheel steps at the touch-down point; positive = wheel up (finger moved down), negative = wheel down. */
        SCROLL,

        /** The finger lifted while swiping fast: [GestureEvent.ticks] (signed like [SCROLL]) coasting steps with growing delays. */
        SCROLL_FLING,

        /** Hold on a unit: the context menu opens around x/y (the finger now); the pointer stays at the touch-down point (centerX/centerY). */
        CONTEXT_OPENED,

        /** The finger moved while the context menu is open (x/y = the finger). */
        CONTEXT_MOVED,

        /** The finger lifted at x/y while the context menu was open: run the entry there, if any. */
        CONTEXT_COMMIT,

        /** A second finger or a cancelled touch closed the context menu without running anything. */
        CONTEXT_CANCELLED,
    }
}

/**
 * Answer of every call: what to do now ([events], in order) and what to do with the single gesture timer.
 * [armMs] >= 0: cancel the running timer and start one that calls [OneFingerGesture.timerFired] after that many ms.
 * [disarm]: cancel the running timer. Neither: leave the timer alone.
 */
class GestureStep(val events: List<GestureEvent> = emptyList(), val armMs: Long = -1L, val disarm: Boolean = false)

/**
 * Decision logic of the one-finger gestures of the touch mode, without Android types and without clocks of its own
 * (the view passes times in and runs one timer).
 *
 * - Rest the finger for [holdMs]: the left mouse button goes down and the pointer follows the finger absolutely until it
 *   lifts (move talents, spells, bag items, UI elements). Disabled when [holdLeftEnabled] is false.
 * - Move further than [moveTolerancePx] before that: camera drag, neither hold nor wheel.
 * - Tap: the left click is held back for [tapWindowMs] (only while the wheel is on). A second touch within the window and
 *   [secondTapDistancePx]: lifting at once = double click (both clicks); resting [wheelHoldMs] = the click wheel opens and
 *   the held-back click is dropped. No second touch: the click is sent when the window ends.
 * - [tapWindowMs] 0 (or wheel off): the click goes out at the lift. The wheel can still be reached by touching again
 *   within [WHEEL_CHANCE_MS] after the click, which then has already happened.
 * - Wheel open: lifting commits the selected field (null = cancelled).
 * - Swipe scroll: a touch that begins where [scrollAreaAt] says "scroll list" and moves vertically ([SwipeScrollMath.isVerticalSwipe]:
 *   at least [scrollThresholdPx], clearly more vertical than horizontal) before hold or the move tolerance decides becomes phase
 *   [Phase.SCROLL]: the finger distance turns into mouse wheel steps ([GestureEvent.Kind.SCROLL], one step per [scrollStepPx] pixels,
 *   finger down = wheel up = content follows the finger like on a phone), and a fast lift adds a short coast
 *   ([GestureEvent.Kind.SCROLL_FLING]). Anything else (horizontal or diagonal move, hold first, touch outside the lists) is as before.
 * - Context menu: when the hold time runs out and [contextAt] says the finger rests on a unit (asked with the time of the touch-down),
 *   the left button is NOT pressed; the phase becomes [Phase.CONTEXT] ([GestureEvent.Kind.CONTEXT_OPENED]) and the finger only selects
 *   an entry until it lifts ([GestureEvent.Kind.CONTEXT_COMMIT]). With [contextAt] false (no unit, setting off, no fresh data) the hold
 *   is the left button as ever. Moving further than [moveTolerancePx] before the time is up is the camera in every case.
 */
class OneFingerGesture(
    val holdMs: Long = DEFAULT_HOLD_MS,
    private val holdLeftEnabled: Boolean = true,
    private val wheelEnabled: Boolean = true,
    tapWindowMs: Long = DEFAULT_TAP_WINDOW_MS,
    private val wheelHoldMs: Long = DEFAULT_WHEEL_HOLD_MS,
    private val moveTolerancePx: Float = MOVE_TOLERANCE_PX,
    private val secondTapDistancePx: Float = SECOND_TAP_DISTANCE_PX,
    private val geometry: ClickWheel.Geometry,
    /** Current wheel assignment, read each time the wheel opens (so a changed setting applies without a restart). */
    private val fieldsProvider: () -> List<ClickWheelField> = { ClickWheel.FIELDS },
    /** Where swipe scrolling applies: asked once per touch-down with the raw view point; false (the default) = never. */
    private val scrollAreaAt: (Float, Float) -> Boolean = { _, _ -> false },
    /** Finger distance in pixels for one wheel step, read at touch-down (so a changed setting applies at once). */
    private val scrollStepPx: () -> Float = { SwipeScrollMath.stepPx(1f, SwipeScrollMath.DEFAULT_SPEED) },
    /** Travel before a vertical move counts as a swipe. */
    private val scrollThresholdPx: Float = SwipeScrollMath.thresholdPx(1f),
    /**
     * Asked once when the hold time runs out, with the time of the touch-down ([down]'s nowMs): true = open the context menu instead
     * of pressing the left button. Null = there is no context menu (the hold timer then only exists for [holdLeftEnabled]).
     */
    private val contextAt: ((Long) -> Boolean)? = null,
) {
    enum class Phase {
        IDLE,

        /** Finger down, quiet so far. */
        FIRST_DOWN,

        /** Tapped and lifted, the click is held back until the window ends or a second touch decides. */
        WAIT_SECOND,

        /** Second touch within the window, quiet so far. */
        SECOND_DOWN,

        /** Left button held, pointer follows the finger. */
        HOLD_LEFT,
        WHEEL,
        CAMERA,

        /** Swipe scroll: the finger moves a list, the pointer stays at the touch-down point. */
        SCROLL,

        /** The context menu is open: the finger selects an entry, lifting runs it. */
        CONTEXT,
    }

    /** Window in which a second touch counts as tap + hold; 0 when the wheel is off. */
    val tapWindowMs: Long = if (wheelEnabled) tapWindowMs.coerceIn(0L, MAX_TAP_WINDOW_MS) else 0L

    var phase: Phase = Phase.IDLE
        private set

    /** Field under the finger while the wheel is open; null = outside the circle (cancel). */
    var selected: ClickWheelField? = null
        private set

    /** The fields of the wheel that is (or was last) open; the painter draws these. */
    var wheelFields: List<ClickWheelField> = ClickWheel.FIELDS
        private set

    /** Time of the touch-down of the tap that the last [GestureEvent.Kind.CLICK] / [GestureEvent.Kind.DOUBLE_CLICK] belongs to. */
    var tapDownMs: Long = 0L
        private set

    /** Where the current touch came down (centre of the wheel). */
    var centerX: Float = 0f
        private set
    var centerY: Float = 0f
        private set

    private var lastX = 0f
    private var lastY = 0f
    private var downMs = 0L
    private var tapX = 0f
    private var tapY = 0f

    /** The first tap's click has not been sent yet. */
    private var tapHeldBack = false
    private var recentTapValid = false
    private var recentTapMs = 0L

    // Swipe scroll state of the current touch.
    private var scrollArmed = false
    private var scrollStep = 1f
    private var scrollAccum = 0f
    private var scrollLastY = 0f
    private var scrollVelocity = 0f
    private var scrollLastMoveMs = 0L

    private val events = ArrayList<GestureEvent>(2)

    private fun event(kind: GestureEvent.Kind, x: Float = 0f, y: Float = 0f, field: ClickWheelField? = null, ticks: Int = 0) {
        events.add(GestureEvent(kind, x, y, field, ticks))
    }

    private fun step(armMs: Long = -1L, disarm: Boolean = false): GestureStep {
        val out = GestureStep(if (events.isEmpty()) emptyList() else ArrayList(events), armMs, disarm)
        events.clear()
        return out
    }

    fun down(x: Float, y: Float, nowMs: Long): GestureStep {
        events.clear()
        // A touch that never saw its lift (lost event): finish it first.
        if (phase != Phase.IDLE && phase != Phase.WAIT_SECOND) cancelInto()
        centerX = x
        centerY = y
        lastX = x
        lastY = y
        downMs = nowMs
        scrollArmed = false
        if (phase == Phase.WAIT_SECOND) {
            if (hypot(x - tapX, y - tapY) <= secondTapDistancePx) {
                phase = Phase.SECOND_DOWN
                return step(armMs = wheelHoldMs)
            }
            // Somewhere else: the held-back click goes out, this is a fresh touch.
            sendHeldBackTap()
            phase = Phase.IDLE
        } else if (wheelEnabled && tapWindowMs == 0L && recentTapValid &&
            nowMs - recentTapMs in 0..WHEEL_CHANCE_MS && hypot(x - tapX, y - tapY) <= secondTapDistancePx
        ) {
            recentTapValid = false
            phase = Phase.SECOND_DOWN
            return step(armMs = wheelHoldMs)
        }
        recentTapValid = false
        phase = Phase.FIRST_DOWN
        scrollArmed = runCatching { scrollAreaAt(x, y) }.getOrDefault(false)
        if (scrollArmed) scrollStep = runCatching { scrollStepPx() }.getOrDefault(scrollStep).coerceAtLeast(MIN_SCROLL_STEP_PX)
        return if (holdLeftEnabled || contextAt != null) step(armMs = holdMs) else step(disarm = true)
    }

    /** [nowMs] only matters for the coast after a swipe (speed of the finger); without it (0) there is no coast. */
    @JvmOverloads
    fun move(x: Float, y: Float, nowMs: Long = 0L): GestureStep {
        events.clear()
        lastX = x
        lastY = y
        when (phase) {
            Phase.FIRST_DOWN, Phase.SECOND_DOWN -> {
                if (phase == Phase.FIRST_DOWN && scrollArmed && SwipeScrollMath.isVerticalSwipe(x - centerX, y - centerY, scrollThresholdPx)) {
                    phase = Phase.SCROLL
                    scrollAccum = 0f
                    scrollLastY = centerY
                    scrollVelocity = 0f
                    scrollLastMoveMs = nowMs
                    event(GestureEvent.Kind.SCROLL_STARTED, centerX, centerY)
                    scrollTo(y, nowMs)
                    return step(disarm = true)
                }
                if (hypot(x - centerX, y - centerY) > moveTolerancePx) {
                    if (phase == Phase.SECOND_DOWN) sendHeldBackTap()
                    phase = Phase.CAMERA
                    event(GestureEvent.Kind.CAMERA_STARTED)
                    return step(disarm = true)
                }
            }
            Phase.HOLD_LEFT -> event(GestureEvent.Kind.LEFT_MOVE, x, y)
            Phase.CONTEXT -> event(GestureEvent.Kind.CONTEXT_MOVED, x, y)
            Phase.SCROLL -> scrollTo(y, nowMs)
            Phase.WHEEL -> {
                val field = ClickWheel.fieldAt(x - centerX, y - centerY, geometry, wheelFields)
                if (field != selected) {
                    selected = field
                    event(GestureEvent.Kind.WHEEL_CHANGED)
                }
            }
            else -> {}
        }
        return step()
    }

    /** The timer armed by an earlier [GestureStep] ran out. Does nothing when the state moved on. */
    fun timerFired(): GestureStep {
        events.clear()
        when (phase) {
            Phase.FIRST_DOWN -> {
                if (runCatching { contextAt?.invoke(downMs) }.getOrNull() == true) {
                    phase = Phase.CONTEXT
                    event(GestureEvent.Kind.CONTEXT_OPENED, lastX, lastY)
                } else if (holdLeftEnabled) {
                    phase = Phase.HOLD_LEFT
                    event(GestureEvent.Kind.LEFT_DOWN, lastX, lastY)
                }
            }
            Phase.WAIT_SECOND -> {
                sendHeldBackTap()
                phase = Phase.IDLE
            }
            Phase.SECOND_DOWN -> {
                tapHeldBack = false // the first tap was only the first half of tap + hold
                phase = Phase.WHEEL
                wheelFields = runCatching { fieldsProvider() }.getOrNull() ?: ClickWheel.FIELDS
                selected = ClickWheel.fieldFor(WheelDirection.CENTER, wheelFields)
                event(GestureEvent.Kind.WHEEL_OPENED, centerX, centerY)
            }
            else -> {}
        }
        return step()
    }

    fun up(x: Float, y: Float, nowMs: Long): GestureStep {
        events.clear()
        lastX = x
        lastY = y
        when (phase) {
            Phase.FIRST_DOWN -> {
                tapX = x
                tapY = y
                tapDownMs = downMs
                if (tapWindowMs > 0L) {
                    tapHeldBack = true
                    phase = Phase.WAIT_SECOND
                    return step(armMs = tapWindowMs)
                }
                event(GestureEvent.Kind.CLICK, x, y)
                recentTapValid = wheelEnabled
                recentTapMs = nowMs
                phase = Phase.IDLE
                return step(disarm = true)
            }
            Phase.SECOND_DOWN -> {
                event(if (tapHeldBack) GestureEvent.Kind.DOUBLE_CLICK else GestureEvent.Kind.CLICK, x, y)
                tapHeldBack = false
                phase = Phase.IDLE
                return step(disarm = true)
            }
            Phase.HOLD_LEFT -> {
                event(GestureEvent.Kind.LEFT_UP, x, y)
                phase = Phase.IDLE
                return step(disarm = true)
            }
            Phase.CONTEXT -> {
                event(GestureEvent.Kind.CONTEXT_COMMIT, x, y)
                phase = Phase.IDLE
                return step(disarm = true)
            }
            Phase.WHEEL -> {
                event(GestureEvent.Kind.WHEEL_COMMIT, field = selected)
                phase = Phase.IDLE
                return step(disarm = true)
            }
            Phase.CAMERA -> {
                event(GestureEvent.Kind.CAMERA_ENDED)
                phase = Phase.IDLE
                return step(disarm = true)
            }
            Phase.SCROLL -> {
                // A finger that was still moving fast when it lifted coasts a few steps (like a phone's list).
                val idle = nowMs - scrollLastMoveMs
                if (scrollLastMoveMs > 0L && idle in 0..ScrollWheelMath.MAX_FLING_IDLE_MILLIS) {
                    val ticks = ScrollWheelMath.inertiaTicks(scrollVelocity, scrollStep).coerceAtMost(SwipeScrollMath.FLING_MAX_TICKS)
                    if (ticks > 0) event(GestureEvent.Kind.SCROLL_FLING, centerX, centerY, ticks = if (scrollVelocity > 0f) ticks else -ticks)
                }
                phase = Phase.IDLE
                return step(disarm = true)
            }
            else -> return step()
        }
    }

    /**
     * A second finger arrived or the touch was cancelled: a held left button is let go, a held-back click goes out,
     * the wheel closes without a click.
     */
    fun cancel(): GestureStep {
        events.clear()
        cancelInto()
        return step(disarm = true)
    }

    /** The view goes away or the system cancelled the touch: a held left button is let go, a held-back click is dropped. */
    fun abort(): GestureStep {
        events.clear()
        tapHeldBack = false
        cancelInto()
        return step(disarm = true)
    }

    private fun cancelInto() {
        when (phase) {
            Phase.HOLD_LEFT -> event(GestureEvent.Kind.LEFT_UP, lastX, lastY)
            Phase.CONTEXT -> event(GestureEvent.Kind.CONTEXT_CANCELLED)
            Phase.SECOND_DOWN, Phase.WAIT_SECOND -> sendHeldBackTap()
            else -> {}
        }
        phase = Phase.IDLE
        selected = null
        recentTapValid = false
    }

    /** The finger is at [y]: whole steps of the distance moved since the last call go out as one [GestureEvent.Kind.SCROLL]. */
    private fun scrollTo(y: Float, nowMs: Long) {
        val dy = y - scrollLastY
        scrollLastY = y
        if (nowMs > 0L) {
            scrollVelocity = ScrollWheelMath.blendVelocity(scrollVelocity, dy, nowMs - scrollLastMoveMs)
            scrollLastMoveMs = nowMs
        }
        scrollAccum += dy
        val whole = ScrollWheelMath.wholeSteps(scrollAccum, scrollStep)
        if (whole != 0) {
            scrollAccum -= whole * scrollStep
            event(GestureEvent.Kind.SCROLL, centerX, centerY, ticks = ScrollWheelMath.limitTicks(whole))
        }
    }

    private fun sendHeldBackTap() {
        if (tapHeldBack) {
            tapHeldBack = false
            event(GestureEvent.Kind.CLICK, tapX, tapY)
        }
    }

    companion object {
        /** Resting this long makes the left button go down (hold). */
        const val DEFAULT_HOLD_MS = 350L
        const val MIN_HOLD_MS = 200L
        const val MAX_HOLD_MS = 800L

        /** A tap's click waits this long for a second touch (tap + hold = wheel). */
        const val DEFAULT_TAP_WINDOW_MS = 250L
        const val MAX_TAP_WINDOW_MS = 400L

        /** After the second touch came down, resting this long opens the wheel. */
        const val DEFAULT_WHEEL_HOLD_MS = 250L

        /** Without a held-back click (window 0) the second touch still opens the wheel within this time after the click. */
        const val WHEEL_CHANCE_MS = 250L

        /** Same tolerance the old long press used: a quiet finger moves less than this. */
        const val MOVE_TOLERANCE_PX = 50f

        /** The second touch must come down this close to the first tap. */
        const val SECOND_TAP_DISTANCE_PX = 60f

        /** Lower limit of the finger distance per wheel step (a wrong setting must not flood the game). */
        const val MIN_SCROLL_STEP_PX = 6f
    }
}
