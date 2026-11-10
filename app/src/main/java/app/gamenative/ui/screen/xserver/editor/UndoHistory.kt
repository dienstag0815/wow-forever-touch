package app.gamenative.ui.screen.xserver.editor

/**
 * Undo/redo history over whole states (snapshots). [reset] sets the start state, [record] adds the state after a change.
 * Keeps at most [limit] undo steps; the oldest ones fall away. Pure Kotlin, no Android classes.
 */
class UndoHistory<T : Any>(private val limit: Int = DEFAULT_LIMIT) {
    private val states = ArrayList<T>()
    private var index = -1

    init {
        require(limit >= 1) { "limit must be at least 1" }
    }

    /** The state the editor is currently in, null before [reset]. */
    val current: T? get() = if (index in states.indices) states[index] else null

    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index >= 0 && index < states.size - 1

    /** Number of steps [undo] can go back. */
    val undoSteps: Int get() = if (index > 0) index else 0

    /** Number of steps [redo] can go forward. */
    val redoSteps: Int get() = if (index >= 0) states.size - 1 - index else 0

    fun reset(initial: T) {
        states.clear()
        states.add(initial)
        index = 0
    }

    /** Adds [state] as the new current state and drops the redo branch. Returns false if it equals the current state. */
    fun record(state: T): Boolean {
        if (index < 0) {
            reset(state)
            return true
        }
        if (states[index] == state) return false
        while (states.size > index + 1) states.removeAt(states.size - 1)
        states.add(state)
        index++
        while (states.size > limit + 1) {
            states.removeAt(0)
            index--
        }
        return true
    }

    /** Replaces the current state in place (e.g. to refine data stored with it); no new undo step. */
    fun replaceCurrent(state: T) {
        if (index >= 0) states[index] = state
    }

    /** Goes one step back and returns the state to restore, or null if there is nothing to undo. */
    fun undo(): T? {
        if (!canUndo) return null
        index--
        return states[index]
    }

    /** Goes one step forward and returns the state to restore, or null if there is nothing to redo. */
    fun redo(): T? {
        if (!canRedo) return null
        index++
        return states[index]
    }

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
