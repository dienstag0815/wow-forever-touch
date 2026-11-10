package app.gamenative.cooldown

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.math.min

/**
 * Pure helpers for reading the presented game picture (no Android classes, unit tested). The picture is a BGRA buffer
 * (little-endian ints are 0xAARRGGBB, exactly Android's ARGB) whose row length (the stride) is usually larger than the
 * visible width; the Drawable of a hardware-buffer pixmap reports the stride as its width.
 */
object FrameAccess {
    /** Only frames of at least this share of the screen size are the game picture (not launcher windows or tooltips). */
    private const val MAIN_FRAME_SHARE = 0.6

    /** An int view of [buffer] with the byte order of the BGRA data (little-endian), starting at 0. */
    @JvmStatic
    fun intView(buffer: ByteBuffer): IntBuffer {
        val copy = buffer.duplicate()
        copy.clear()
        return copy.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    }

    /** Whether a presented window of [frameWidth] x [frameHeight] is the game picture of a screen [screenWidth] x [screenHeight]. */
    @JvmStatic
    fun isMainFrame(frameWidth: Int, frameHeight: Int, screenWidth: Int, screenHeight: Int): Boolean {
        if (frameWidth < 64 || frameHeight < 64 || screenWidth <= 0 || screenHeight <= 0) return false
        return frameWidth >= screenWidth * MAIN_FRAME_SHARE && frameHeight >= screenHeight * MAIN_FRAME_SHARE
    }

    /** Visible frame width of a buffer: the window width, but never more than the buffer stride ([strideInts] ints per row). */
    @JvmStatic
    fun visibleWidth(windowWidth: Int, strideInts: Int): Int = if (windowWidth <= 0) strideInts else min(windowWidth, strideInts)
}
