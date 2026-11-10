package app.gamenative.ui.screen.xserver.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoHistoryTest {
    @Test
    fun `fresh history has nothing to undo or redo`() {
        val history = UndoHistory<String>()
        history.reset("start")

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo())
        assertNull(history.redo())
        assertEquals("start", history.current)
    }

    @Test
    fun `undo and redo walk through the recorded states`() {
        val history = UndoHistory<String>()
        history.reset("a")
        history.record("b")
        history.record("c")

        assertEquals("b", history.undo())
        assertEquals("a", history.undo())
        assertNull(history.undo())
        assertEquals("b", history.redo())
        assertEquals("c", history.redo())
        assertNull(history.redo())
        assertEquals("c", history.current)
    }

    @Test
    fun `recording after undo drops the redo branch`() {
        val history = UndoHistory<String>()
        history.reset("a")
        history.record("b")
        history.record("c")
        history.undo() // back at b

        history.record("x")

        assertFalse(history.canRedo)
        assertEquals("x", history.current)
        assertEquals("b", history.undo())
    }

    @Test
    fun `recording the current state again is ignored`() {
        val history = UndoHistory<String>()
        history.reset("a")

        assertFalse(history.record("a"))
        assertTrue(history.record("b"))
        assertFalse(history.record("b"))
        assertEquals(1, history.undoSteps)
    }

    @Test
    fun `keeps at least twenty steps and drops the oldest beyond the limit`() {
        val history = UndoHistory<Int>(limit = 25)
        history.reset(0)
        for (i in 1..40) history.record(i)

        assertEquals(25, history.undoSteps)
        var last: Int? = null
        var steps = 0
        while (true) {
            val state = history.undo() ?: break
            last = state
            steps++
        }
        assertEquals(25, steps)
        assertEquals(15, last) // states 0..14 fell away
    }

    @Test
    fun `default limit covers at least twenty steps`() {
        assertTrue(UndoHistory.DEFAULT_LIMIT >= 20)
        val history = UndoHistory<Int>()
        history.reset(0)
        for (i in 1..20) history.record(i)
        var steps = 0
        while (history.undo() != null) steps++
        assertEquals(20, steps)
    }

    @Test
    fun `reset clears everything`() {
        val history = UndoHistory<String>()
        history.reset("a")
        history.record("b")
        history.undo()

        history.reset("z")

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals("z", history.current)
    }

    @Test
    fun `replacing the current state adds no undo step`() {
        val history = UndoHistory<String>()
        history.reset("a")
        history.record("b")

        history.replaceCurrent("b2")

        assertEquals("b2", history.current)
        assertEquals(1, history.undoSteps)
        assertEquals("a", history.undo())
        assertEquals("b2", history.redo())
    }

    @Test
    fun `step counters follow the position`() {
        val history = UndoHistory<String>()
        history.reset("a")
        history.record("b")
        history.record("c")
        history.undo()

        assertEquals(1, history.undoSteps)
        assertEquals(1, history.redoSteps)
    }
}
