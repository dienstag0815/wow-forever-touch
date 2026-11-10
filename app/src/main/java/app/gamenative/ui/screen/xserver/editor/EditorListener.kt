package app.gamenative.ui.screen.xserver.editor

import com.winlator.inputcontrols.ControlElement

/** What the on-screen controls view reports to the editor while edit mode is on. */
interface EditorListener {
    /** The selected element changed (null = nothing selected). */
    fun onSelectionChanged(element: ControlElement?)

    /** Double tap or pencil handle on [element]: open its settings. */
    fun onEditRequested(element: ControlElement)

    /** A touch gesture ended; [changed] = position or size of the touched element differs from before the gesture. */
    fun onGestureFinished(changed: Boolean)
}
