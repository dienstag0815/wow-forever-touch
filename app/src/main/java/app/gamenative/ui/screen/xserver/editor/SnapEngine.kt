package app.gamenative.ui.screen.xserver.editor

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Rectangle in view pixels (left/top inclusive). Pure data, no Android classes. */
data class SnapRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun translated(dx: Float, dy: Float) = SnapRect(left + dx, top + dy, right + dx, bottom + dy)

    companion object {
        fun centered(cx: Float, cy: Float, halfWidth: Float, halfHeight: Float) =
            SnapRect(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
    }
}

/**
 * A guide line shown while a snap is active.
 *
 * @param vertical true = the line is vertical at x = [position], running from y = [start] to y = [end];
 *                 false = horizontal at y = [position], running from x = [start] to x = [end]
 */
data class SnapGuide(val vertical: Boolean, val position: Float, val start: Float, val end: Float)

/** Identity of a locked line (without its length); used to vibrate once when a new line catches. */
data class SnapAnchor(val vertical: Boolean, val position: Int)

/**
 * Result of [SnapEngine.snap].
 *
 * @param rect the moved rectangle after snapping
 * @param objectSnapX true if the x position caught on an edge/center of another element or of the screen (not just the grid)
 * @param objectSnapY same for y
 */
data class SnapResult(
    val rect: SnapRect,
    val objectSnapX: Boolean,
    val objectSnapY: Boolean,
    val guides: List<SnapGuide>,
) {
    val centerX: Float get() = rect.centerX
    val centerY: Float get() = rect.centerY
    val anchors: Set<SnapAnchor> = guides.map { SnapAnchor(it.vertical, Math.round(it.position)) }.toSet()
}

/**
 * Snapping of a moved element:
 * 1. edges and centers of the moved rectangle catch on edges and centers of other elements and on the screen edges and
 *    center, if closer than [threshold] pixels (the nearest candidate wins, independently for x and y);
 * 2. otherwise the element center snaps to the grid of [gridSize] pixels (as before this editor existed).
 * Guides are returned for every line the final rectangle is aligned with.
 */
class SnapEngine(
    private val gridSize: Float,
    private val threshold: Float,
    private val gridEnabled: Boolean = true,
) {
    fun snap(proposed: SnapRect, others: List<SnapRect>, areaWidth: Float, areaHeight: Float): SnapResult {
        val xTargets = ArrayList<Float>()
        val yTargets = ArrayList<Float>()
        xTargets.add(0f); xTargets.add(areaWidth / 2f); xTargets.add(areaWidth)
        yTargets.add(0f); yTargets.add(areaHeight / 2f); yTargets.add(areaHeight)
        for (o in others) {
            xTargets.add(o.left); xTargets.add(o.centerX); xTargets.add(o.right)
            yTargets.add(o.top); yTargets.add(o.centerY); yTargets.add(o.bottom)
        }

        val objectDx = nearestOffset(listOf(proposed.left, proposed.centerX, proposed.right), xTargets)
        val objectDy = nearestOffset(listOf(proposed.top, proposed.centerY, proposed.bottom), yTargets)

        val dx = objectDx ?: gridOffset(proposed.centerX)
        val dy = objectDy ?: gridOffset(proposed.centerY)
        val rect = proposed.translated(dx, dy)

        return SnapResult(
            rect = rect,
            objectSnapX = objectDx != null,
            objectSnapY = objectDy != null,
            guides = collectGuides(rect, others, areaWidth, areaHeight),
        )
    }

    private fun nearestOffset(sources: List<Float>, targets: List<Float>): Float? {
        var best: Float? = null
        for (s in sources) {
            for (t in targets) {
                val d = t - s
                if (abs(d) <= threshold && (best == null || abs(d) < abs(best))) best = d
            }
        }
        return best
    }

    private fun gridOffset(center: Float): Float {
        if (!gridEnabled || gridSize <= 0f) return 0f
        val snapped = floor(center / gridSize + 0.5f) * gridSize
        return snapped - center
    }

    private fun collectGuides(rect: SnapRect, others: List<SnapRect>, areaWidth: Float, areaHeight: Float): List<SnapGuide> {
        val guides = ArrayList<SnapGuide>()
        val xs = listOf(rect.left, rect.centerX, rect.right)
        val ys = listOf(rect.top, rect.centerY, rect.bottom)

        fun addVertical(position: Float, other: SnapRect?) {
            val start = if (other == null) 0f else min(rect.top, other.top)
            val end = if (other == null) areaHeight else max(rect.bottom, other.bottom)
            guides.add(SnapGuide(true, position, start, end))
        }

        fun addHorizontal(position: Float, other: SnapRect?) {
            val start = if (other == null) 0f else min(rect.left, other.left)
            val end = if (other == null) areaWidth else max(rect.right, other.right)
            guides.add(SnapGuide(false, position, start, end))
        }

        for (target in listOf(0f, areaWidth / 2f, areaWidth)) {
            if (xs.any { abs(it - target) <= MATCH_EPSILON }) addVertical(target, null)
        }
        for (target in listOf(0f, areaHeight / 2f, areaHeight)) {
            if (ys.any { abs(it - target) <= MATCH_EPSILON }) addHorizontal(target, null)
        }
        for (o in others) {
            for (target in listOf(o.left, o.centerX, o.right)) {
                if (xs.any { abs(it - target) <= MATCH_EPSILON }) addVertical(target, o)
            }
            for (target in listOf(o.top, o.centerY, o.bottom)) {
                if (ys.any { abs(it - target) <= MATCH_EPSILON }) addHorizontal(target, o)
            }
        }
        return guides
    }

    companion object {
        /** Two lines count as aligned if they are closer than this many pixels. */
        const val MATCH_EPSILON = 0.5f
    }
}
