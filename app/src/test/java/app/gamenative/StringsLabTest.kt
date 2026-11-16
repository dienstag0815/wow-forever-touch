package app.gamenative

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The texts of the Battle.net lab (`strings_lab.xml`): English is the default, German stays; both files describe the same texts. */
class StringsLabTest {
    private fun load(dir: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings_lab.xml"))
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
            assertEquals("placeholders in $key", placeholder.findAll(en).map { it.value }.sorted().toList(), placeholder.findAll(de).map { it.value }.sorted().toList())
        }
    }

    @Test
    fun aTextWithPlaceholdersHasNoLoneProcentSign() {
        for (dir in listOf("values", "values-de")) {
            for ((key, text) in load(dir)) {
                if (!placeholder.containsMatchIn(text)) continue
                val rest = placeholder.replace(text, "")
                assertFalse("percent sign in $dir/$key", rest.contains('%') && !rest.contains("%%"))
            }
        }
    }

    @Test
    fun noTextIsEmpty() {
        for (dir in listOf("values", "values-de")) {
            for ((key, text) in load(dir)) assertTrue("empty: $dir/$key", text.isNotBlank())
        }
    }

    @Test
    fun theEnglishTextsContainNoGermanUmlauts() {
        for ((key, text) in load("values")) assertFalse("umlaut in values/$key", text.any { it in "äöüÄÖÜß" })
    }

    @Test
    fun theBannerSentenceIsOnTheLabPage() {
        assertTrue(load("values-de").getValue("lab_banner").startsWith("Versuchsaufbau. Battle.net wird von Blizzard unter Wine nicht unterstützt"))
        assertTrue(load("values").getValue("lab_banner").contains("not supported by Blizzard under Wine"))
    }

    @Test
    fun everyLabStringUsedInTheCodeExists() {
        val keys = load("values").keys
        val used = mutableSetOf<String>()
        val root = File("src/main/java")
        root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.forEach { f ->
            Regex("R\\.string\\.(lab_[a-z0-9_]+)").findAll(f.readText()).forEach { used += it.groupValues[1] }
        }
        assertTrue(used.isNotEmpty())
        assertEquals(emptySet<String>(), used - keys)
    }
}
