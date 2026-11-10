package app.gamenative.ui.screen.wow.voice

/**
 * The preview after a recognition: either the text is only shown ([editing] = false) or the player edits it in a text
 * field. [shown] is the text the preview was opened with (already cleaned).
 */
internal data class VoicePreviewState(val shown: String, val editing: Boolean = false)

/** What a button press decides, besides the new state. */
internal sealed class VoicePreviewStep {
    /** Nothing happens (for example an empty edited text: there is nothing to send). */
    object None : VoicePreviewStep()

    /** The preview is closed and nothing is sent. */
    object Discard : VoicePreviewStep()

    /** The preview is closed and a new recognition starts. */
    object Again : VoicePreviewStep()

    /** The preview is closed and [text] is typed into the chat; [edited] = it differs from what was recognized. */
    class Send(val text: String, val edited: Boolean) : VoicePreviewStep()
}

internal class VoicePreviewTransition(val state: VoicePreviewState, val step: VoicePreviewStep)

/** The pure rules of the preview (no Android): display <-> edit, and which text is sent. */
internal object VoicePreviewFlow {
    fun open(text: String): VoicePreviewState = VoicePreviewState(text, editing = false)

    /** The pencil: switches to the text field (only from the display). */
    fun edit(state: VoicePreviewState): VoicePreviewTransition =
        VoicePreviewTransition(state.copy(editing = true), VoicePreviewStep.None)

    /**
     * The cross: while editing it only leaves the text field (the recognized text stays, the edit is dropped), in the
     * display it discards the whole preview.
     */
    fun cancel(state: VoicePreviewState): VoicePreviewTransition =
        if (state.editing) {
            VoicePreviewTransition(state.copy(editing = false), VoicePreviewStep.None)
        } else {
            VoicePreviewTransition(state, VoicePreviewStep.Discard)
        }

    /** The repeat sign: only from the display. */
    fun again(state: VoicePreviewState): VoicePreviewTransition =
        if (state.editing) {
            VoicePreviewTransition(state, VoicePreviewStep.None)
        } else {
            VoicePreviewTransition(state, VoicePreviewStep.Again)
        }

    /**
     * The check mark. In the display [state].shown is sent; while editing the cleaned [editorText] is sent, and an
     * empty one keeps the text field open.
     */
    fun send(state: VoicePreviewState, editorText: String): VoicePreviewTransition {
        if (!state.editing) {
            return VoicePreviewTransition(state, VoicePreviewStep.Send(state.shown, edited = false))
        }
        val text = VoiceKeys.sanitize(editorText)
        if (text.isEmpty()) return VoicePreviewTransition(state, VoicePreviewStep.None)
        return VoicePreviewTransition(state, VoicePreviewStep.Send(text, edited = text != state.shown))
    }
}
