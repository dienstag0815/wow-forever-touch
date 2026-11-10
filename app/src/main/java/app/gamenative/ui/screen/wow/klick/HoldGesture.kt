package app.gamenative.ui.screen.wow.klick

import kotlin.math.hypot

/**
 * Two-finger tap = right click at the first finger. A tap needs both fingers down for at most [maxDurationMs]
 * (counted from the first finger's touch-down) and hardly any movement ([maxTravelPx] per finger), so a pinch or a
 * two-finger drag never counts.
 */
class TwoFingerTapDetector(
    private val maxDurationMs: Long = MAX_DURATION_MS,
    private val maxTravelPx: Float = MAX_TRAVEL_PX,
) {
    private var firstDownMs = 0L
    private var startX0 = 0f
    private var startY0 = 0f
    private var startX1 = 0f
    private var startY1 = 0f
    private var armed = false

    /** Last known position of the first finger (the right click goes here). */
    var firstX = 0f
        private set
    var firstY = 0f
        private set

    /** False as soon as one finger moved too far. */
    var stillTap = false
        private set

    /**
     * @param firstDownMs time the first finger came down; [firstDownX]/[firstDownY] where it came down
     * @param x0 y0 current position of the first finger, [x1] [y1] position of the second finger (just came down)
     */
    fun begin(firstDownMs: Long, firstDownX: Float, firstDownY: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
        this.firstDownMs = firstDownMs
        startX0 = firstDownX
        startY0 = firstDownY
        startX1 = x1
        startY1 = y1
        firstX = x0
        firstY = y0
        armed = true
        stillTap = hypot(x0 - firstDownX, y0 - firstDownY) <= maxTravelPx
    }

    fun move(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (!armed) return
        firstX = x0
        firstY = y0
        if (hypot(x0 - startX0, y0 - startY0) > maxTravelPx || hypot(x1 - startX1, y1 - startY1) > maxTravelPx) {
            stillTap = false
        }
    }

    /** A finger lifted at [upMs]: true when this was a two-finger tap. */
    fun isTap(upMs: Long): Boolean = armed && stillTap && (upMs - firstDownMs) <= maxDurationMs

    fun reset() {
        armed = false
        stillTap = false
    }

    companion object {
        const val MAX_DURATION_MS = 250L
        const val MAX_TRAVEL_PX = 30f
    }
}
