package app.gamenative.ui.screen.wow.voice

import com.winlator.inputcontrols.Binding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSettingsTest {
    @Test
    fun customWordsAreSplitAtLinesCommasAndSemicolons() {
        assertEquals(
            listOf("Anna", "Sturmwacht", "Nachtbrut", "Xyz"),
            VoiceSettings.parseCustomWords("Anna, Sturmwacht\nNachtbrut;Xyz"),
        )
    }

    @Test
    fun customWordsDropEmptyEntriesAndDuplicates() {
        assertEquals(listOf("Anna", "Gilde"), VoiceSettings.parseCustomWords(" Anna ,, ;\n anna \r\nGilde, GILDE"))
        assertEquals(emptyList<String>(), VoiceSettings.parseCustomWords(" \n , ; "))
    }

    @Test
    fun customWordsCannotCarryListMarkers() {
        assertEquals(listOf("Extra", "Tag"), VoiceSettings.parseCustomWords("=Extra, #Tag"))
    }

    @Test
    fun customWordsAreLimited() {
        val long = "a".repeat(100)
        assertEquals(VoiceSettings.MAX_WORD_LENGTH, VoiceSettings.parseCustomWords(long).single().length)
        val many = (1..300).joinToString(",") { "wort$it" }
        assertEquals(VoiceSettings.MAX_CUSTOM_WORDS, VoiceSettings.parseCustomWords(many).size)
    }

    @Test
    fun languageTagsAreRecognizerLocales() {
        assertEquals("de-DE", VoiceLanguage.DE.tag)
        assertEquals("en-US", VoiceLanguage.EN.tag)
    }

    @Test
    fun voiceBindingIsAnExtraButtonLabelledSprache() {
        assertEquals("SPRACHE", Binding.VOICE_CHAT.toString())
        assertTrue(Binding.VOICE_CHAT.isExtra)
        assertFalse(Binding.VOICE_CHAT.isKeyboard)
        assertFalse(Binding.VOICE_CHAT.isGamepad)
        assertTrue(Binding.extraBindingValues().contains(Binding.VOICE_CHAT))
        assertEquals(Binding.VOICE_CHAT, Binding.fromString("VOICE_CHAT"))
    }
}
