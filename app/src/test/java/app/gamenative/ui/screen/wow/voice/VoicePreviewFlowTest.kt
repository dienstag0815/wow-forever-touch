package app.gamenative.ui.screen.wow.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePreviewFlowTest {
    private val open = VoicePreviewFlow.open("Hallo Welt")

    private fun sent(step: VoicePreviewStep): VoicePreviewStep.Send = step as VoicePreviewStep.Send

    @Test
    fun displaySendsTheRecognizedTextUnchanged() {
        val result = VoicePreviewFlow.send(open, "ignored")
        val step = sent(result.step)
        assertEquals("Hallo Welt", step.text)
        assertFalse(step.edited)
    }

    @Test
    fun pencilOpensTheEditorAndKeepsTheText() {
        val result = VoicePreviewFlow.edit(open)
        assertTrue(result.state.editing)
        assertEquals("Hallo Welt", result.state.shown)
        assertSame(VoicePreviewStep.None, result.step)
    }

    @Test
    fun editedTextIsSentAndMarkedAsEdited() {
        val editing = VoicePreviewFlow.edit(open).state
        val step = sent(VoicePreviewFlow.send(editing, "  Hallo   Gilde\n").step)
        assertEquals("Hallo Gilde", step.text)
        assertTrue(step.edited)
    }

    @Test
    fun untouchedEditorSendsTheSameTextAsNotEdited() {
        val editing = VoicePreviewFlow.edit(open).state
        val step = sent(VoicePreviewFlow.send(editing, "Hallo Welt").step)
        assertEquals("Hallo Welt", step.text)
        assertFalse(step.edited)
    }

    @Test
    fun emptyEditedTextSendsNothingAndStaysInTheEditor() {
        val editing = VoicePreviewFlow.edit(open).state
        for (blank in listOf("", "   ", "\n\t", "/")) {
            val result = VoicePreviewFlow.send(editing, blank)
            assertSame(VoicePreviewStep.None, result.step)
            assertTrue(result.state.editing)
        }
    }

    @Test
    fun editedTextLosesALeadingSlashSoNoCommandRuns() {
        val editing = VoicePreviewFlow.edit(open).state
        val step = sent(VoicePreviewFlow.send(editing, "/quit jetzt").step)
        assertEquals("quit jetzt", step.text)
    }

    @Test
    fun crossInTheEditorGoesBackToTheDisplayWithTheRecognizedText() {
        val editing = VoicePreviewFlow.edit(open).state
        val back = VoicePreviewFlow.cancel(editing)
        assertFalse(back.state.editing)
        assertEquals("Hallo Welt", back.state.shown)
        assertSame(VoicePreviewStep.None, back.step)
        // The edit is gone: sending now sends the original.
        assertEquals("Hallo Welt", sent(VoicePreviewFlow.send(back.state, "Ganz anders").step).text)
    }

    @Test
    fun crossInTheDisplayDiscardsTheWholePreview() {
        assertSame(VoicePreviewStep.Discard, VoicePreviewFlow.cancel(open).step)
    }

    @Test
    fun repeatWorksOnlyFromTheDisplay() {
        assertSame(VoicePreviewStep.Again, VoicePreviewFlow.again(open).step)
        val editing = VoicePreviewFlow.edit(open).state
        assertSame(VoicePreviewStep.None, VoicePreviewFlow.again(editing).step)
    }

    @Test
    fun overlongEditedTextIsCutLikeEverySentText() {
        val editing = VoicePreviewFlow.edit(open).state
        val long = "wort ".repeat(100)
        val step = sent(VoicePreviewFlow.send(editing, long).step)
        assertTrue(step.text.length <= VoiceKeys.MAX_CHAT_LENGTH)
        assertTrue(step.edited)
    }
}
