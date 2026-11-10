package app.gamenative.ui.screen.wow.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhoneticsTest {
    @Test
    fun fold_removesCaseUmlautsAndPunctuation() {
        assertEquals("kelthuzad", Phonetics.fold("Kel'Thuzad"))
        assertEquals("apfelstrasse", Phonetics.fold("Äpfel Straße"))
        assertEquals("ungorokrater", Phonetics.fold("Un'Goro-Krater"))
        assertEquals("", Phonetics.fold("?!"))
    }

    @Test
    fun cologne_matchesTheKnownReferenceValues() {
        assertEquals("657", Phonetics.cologne("Müller"))
        assertEquals("3412", Phonetics.cologne("Wikipedia"))
        assertEquals("", Phonetics.cologne(""))
    }

    @Test
    fun cologne_equalSoundGivesEqualKey() {
        assertEquals(Phonetics.cologne("Meier"), Phonetics.cologne("Mayer"))
        assertEquals(Phonetics.cologne("Kel'Thuzad"), Phonetics.cologne("Kel Thusad"))
        assertEquals(Phonetics.cologne("Orgrimmar"), Phonetics.cologne("Orgrimar"))
        assertEquals(Phonetics.cologne("Ragnaros"), Phonetics.cologne("Ragnaroos"))
        assertNotEquals(Phonetics.cologne("Orgrimmar"), Phonetics.cologne("Sturmwind"))
    }

    @Test
    fun metaphone_handlesEnglishSpellings() {
        assertEquals("NT", Phonetics.metaphone("Knight"))
        assertEquals("FNKS", Phonetics.metaphone("Phoenix"))
        assertEquals(Phonetics.metaphone("Kel'Thuzad"), Phonetics.metaphone("Kelthusad"))
        assertEquals("", Phonetics.metaphone("123"))
    }

    @Test
    fun levenshtein_countsEdits() {
        assertEquals(0, Phonetics.levenshtein("naxxramas", "naxxramas"))
        assertEquals(3, Phonetics.levenshtein("kitten", "sitting"))
        assertEquals(3, Phonetics.levenshtein("", "abc"))
        assertEquals(1, Phonetics.levenshtein("orgrimar", "orgrimmar"))
    }
}
