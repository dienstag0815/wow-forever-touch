package app.gamenative.ui.screen.wow.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun englishIsTheDefaultForEveryInstallation() {
        assertEquals("en", AppLanguage.DEFAULT)
        assertEquals("en", AppLanguage.tagFor(null))
        assertEquals("en", AppLanguage.tagFor(""))
        assertEquals("en", AppLanguage.tagFor("   "))
    }

    @Test
    fun germanIsAChoiceInAnyWritingOfTheCode() {
        assertEquals("de", AppLanguage.tagFor("de"))
        assertEquals("de", AppLanguage.tagFor("DE"))
        assertEquals("de", AppLanguage.tagFor(" de-AT "))
        assertEquals("de", AppLanguage.tagFor("de_DE".replace('_', '-')))
    }

    @Test
    fun anythingElseFallsBackToEnglishInsteadOfTheSystemLanguage() {
        for (stored in listOf("en", "fr", "pt-BR", "zh-CN", "system", "xx")) assertEquals(stored, "en", AppLanguage.tagFor(stored))
    }

    @Test
    fun theChoiceListHasEnglishFirstAndNamesAreNativeAndDistinct() {
        assertEquals(listOf("en", "de"), AppLanguage.CHOICES)
        assertEquals("English", AppLanguage.nativeName("en"))
        assertEquals("Deutsch", AppLanguage.nativeName("de"))
        assertEquals("English", AppLanguage.nativeName("fr"))
        assertEquals(AppLanguage.CHOICES.size, AppLanguage.CHOICES.map { AppLanguage.nativeName(it) }.toSet().size)
    }
}
