package app.gamenative.cooldown

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameAccessTest {
    @Test
    fun `bgra bytes read as argb ints and the source buffer is left alone`() {
        val bytes = ByteBuffer.allocateDirect(8).order(ByteOrder.BIG_ENDIAN)
        // Pixel 0: B 0x30, G 0x20, R 0x10, A 0x80; pixel 1: B 1, G 2, R 3, A 4.
        bytes.put(0, 0x30).put(1, 0x20).put(2, 0x10).put(3, 0x80.toByte())
        bytes.put(4, 1).put(5, 2).put(6, 3).put(7, 4)
        bytes.position(5)
        val ints = FrameAccess.intView(bytes)
        assertEquals(0x80102030.toInt(), ints.get(0))
        assertEquals(0x04030201, ints.get(1))
        assertEquals(2, ints.limit())
        assertEquals(5, bytes.position()) // the original keeps its position
        assertEquals(ByteOrder.BIG_ENDIAN, bytes.order())
    }

    @Test
    fun `the visible width is the window width but never more than the stride`() {
        assertEquals(1600, FrameAccess.visibleWidth(windowWidth = 1600, strideInts = 1664))
        assertEquals(1664, FrameAccess.visibleWidth(windowWidth = 2000, strideInts = 1664))
        assertEquals(1664, FrameAccess.visibleWidth(windowWidth = 0, strideInts = 1664))
    }

    @Test
    fun `only a nearly screen sized frame is the game picture`() {
        assertTrue(FrameAccess.isMainFrame(1600, 900, 1600, 900))
        assertTrue(FrameAccess.isMainFrame(1200, 900, 1200, 900))
        assertFalse(FrameAccess.isMainFrame(1600, 900, 2448, 1848)) // 49 % of the screen height
        assertFalse(FrameAccess.isMainFrame(300, 200, 1600, 900))
        assertFalse(FrameAccess.isMainFrame(0, 0, 1600, 900))
        assertFalse(FrameAccess.isMainFrame(1600, 900, 0, 0))
    }
}
