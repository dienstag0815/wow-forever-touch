package app.gamenative.ui.screen.xserver.editor

import kotlin.math.abs
import kotlin.math.floor

/** Scale arithmetic of the on-screen resize handle and the two-finger pinch. Pure Kotlin. */
object ResizeMath {
    const val MIN_SCALE = 0.3f
    const val MAX_SCALE = 4.0f

    /** Scales are rounded to this step so values stay clean ("1.25x"), and 1.0 attracts. */
    const val SCALE_STEP = 0.05f

    fun clampScale(scale: Float): Float = when {
        scale.isNaN() -> 1.0f
        scale < MIN_SCALE -> MIN_SCALE
        scale > MAX_SCALE -> MAX_SCALE
        else -> scale
    }

    /** New scale when a handle/finger distance changes from [startDistance] to [currentDistance]. */
    fun scaleFromDistance(startScale: Float, startDistance: Float, currentDistance: Float): Float {
        if (startDistance <= 0.5f) return clampScale(startScale)
        return tidy(clampScale(startScale * (currentDistance / startDistance)))
    }

    /** Rounds to [SCALE_STEP]; values within one step of 1.0 become exactly 1.0. */
    fun tidy(scale: Float): Float {
        val rounded = floor(scale / SCALE_STEP + 0.5f) * SCALE_STEP
        val result = if (abs(rounded - 1.0f) < SCALE_STEP * 1.01f) 1.0f else rounded
        return clampScale(result)
    }
}
