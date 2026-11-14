package app.gamenative.ui.components

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppTextLocaleTest {

    private var savedDefault: Locale? = null

    @Before
    fun setUp() {
        savedDefault = Locale.getDefault()
        AppText.testGerman = null
        AppText.languageTag = null
    }

    @After
    fun tearDown() {
        AppText.languageTag = null
        AppText.testGerman = null
        Locale.setDefault(savedDefault ?: Locale.US)
    }

    @Test
    fun localeFor_usesTheTag() {
        assertEquals("en", localeFor("en", Locale.GERMANY).language)
        assertEquals("de", localeFor("de", Locale.US).language)
    }

    @Test
    fun localeFor_nullOrBlankFallsBack() {
        assertEquals(Locale.GERMANY, localeFor(null, Locale.GERMANY))
        assertEquals(Locale.GERMANY, localeFor("", Locale.GERMANY))
        assertEquals(Locale.GERMANY, localeFor("  ", Locale.GERMANY))
    }

    @Test
    fun isGerman_followsTheSavedTagNotTheSystemLocale() {
        Locale.setDefault(Locale.GERMANY)
        AppText.languageTag = { "en" }
        assertFalse(AppText.isGerman())

        AppText.languageTag = { "de" }
        assertTrue(AppText.isGerman())
    }

    @Test
    fun isGerman_withoutTagFollowsTheDefaultLocale() {
        Locale.setDefault(Locale.GERMANY)
        AppText.languageTag = null
        assertTrue(AppText.isGerman())

        Locale.setDefault(Locale.US)
        assertFalse(AppText.isGerman())
    }
}
