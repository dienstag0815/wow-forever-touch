package app.gamenative.ui.screen.xserver.editor

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Where new and duplicated elements go. Pure Kotlin, no Android classes. */
object ElementPlacement {
    /** A duplicate sits this many grid steps right and down of the original. */
    const val DUPLICATE_STEPS = 5

    private val DIRECTIONS = listOf(
        1 to 1, -1 to 1, 1 to -1, -1 to -1,
        1 to 0, -1 to 0, 0 to 1, 0 to -1,
    )

    /**
     * Returns [preferredX]/[preferredY] if no element center is within half a grid [step] of it and the point lies inside
     * the area (minus a margin of two steps); otherwise the nearest free point found by walking away from it in grid steps.
     */
    fun findFreeCenter(
        preferredX: Float,
        preferredY: Float,
        step: Float,
        areaWidth: Float,
        areaHeight: Float,
        occupied: List<Pair<Float, Float>>,
    ): Pair<Float, Float> {
        val s = if (step > 0f) step else 1f
        val margin = 2f * s

        fun inside(x: Float, y: Float) =
            x >= margin && x <= areaWidth - margin && y >= margin && y <= areaHeight - margin

        fun free(x: Float, y: Float) = occupied.none { abs(it.first - x) < s * 0.5f && abs(it.second - y) < s * 0.5f }

        if (inside(preferredX, preferredY) && free(preferredX, preferredY)) return preferredX to preferredY
        for (k in 1..MAX_RINGS) {
            for ((sx, sy) in DIRECTIONS) {
                val x = preferredX + sx * k * s
                val y = preferredY + sy * k * s
                if (inside(x, y) && free(x, y)) return x to y
            }
        }
        return clampToArea(preferredX, preferredY, margin, areaWidth, areaHeight)
    }

    /** Center for a copy of the element at [x]/[y]: five grid steps right and down, or the nearest free point. */
    fun duplicateCenter(
        x: Float,
        y: Float,
        step: Float,
        areaWidth: Float,
        areaHeight: Float,
        occupied: List<Pair<Float, Float>>,
    ): Pair<Float, Float> {
        val offset = DUPLICATE_STEPS * (if (step > 0f) step else 1f)
        return findFreeCenter(x + offset, y + offset, step, areaWidth, areaHeight, occupied)
    }

    fun clampToArea(x: Float, y: Float, margin: Float, areaWidth: Float, areaHeight: Float): Pair<Float, Float> {
        val maxX = max(margin, areaWidth - margin)
        val maxY = max(margin, areaHeight - margin)
        return min(max(x, margin), maxX) to min(max(y, margin), maxY)
    }

    private const val MAX_RINGS = 60
}
