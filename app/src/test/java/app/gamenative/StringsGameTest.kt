package app.gamenative

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The texts of the game part (`strings_game.xml`): English is the default, German stays; both files describe the same texts. */
class StringsGameTest {
    private fun load(dir: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings_game.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private val placeholder = Regex("%(\\d+)\\$[sd]")

    @Test
    fun englishAndGermanHaveTheSameKeys() {
        val english = load("values")
        val german = load("values-de")
        assertEquals(english.keys.sorted(), german.keys.sorted())
        assertTrue(english.size > 100)
    }

    @Test
    fun placeholdersMatchBetweenTheLanguages() {
        val english = load("values")
        val german = load("values-de")
        for ((key, en) in english) {
            val de = german.getValue(key)
            assertEquals("Platzhalter in $key", placeholder.findAll(en).map { it.value }.sorted().toList(), placeholder.findAll(de).map { it.value }.sorted().toList())
        }
    }

    @Test
    fun aTextWithPlaceholdersHasNoLoneProcentSign() {
        for (dir in listOf("values", "values-de")) {
            for ((key, text) in load(dir)) {
                if (!placeholder.containsMatchIn(text)) continue
                val withoutPlaceholders = placeholder.replace(text, "")
                assertFalse("Prozentzeichen in $dir/$key", withoutPlaceholders.contains('%') && !withoutPlaceholders.contains("%%"))
            }
        }
    }

    @Test
    fun noTextIsEmptyExceptTheDeliberateOnes() {
        for (dir in listOf("values", "values-de")) {
            for ((key, text) in load(dir)) assertTrue("leer: $dir/$key", text.isNotBlank())
        }
    }

    @Test
    fun theEnglishTextsContainNoGermanUmlauts() {
        // Names of the game and of the recognition languages are the only places where such letters may stay.
        for ((key, text) in load("values")) {
            assertFalse("Umlaut im englischen Text $key: $text", Regex("[äöüÄÖÜß]").containsMatchIn(text.replace("ä, ö, ü", "")))
        }
    }
}
