package app.gamenative

import app.gamenative.ui.components.AppText
import app.gamenative.ui.components.TextRef
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.rules.ExternalResource

/**
 * Texts for JUnit: resolves string resource ids (the pure logic returns ids, see [TextRef]) with the real
 * `values/strings_game.xml` (English) or `values-de/strings_game.xml` (German), so a test can compare what the player would read
 * in either language. The unit tests run in the module folder, like the other tests that read `src/main/...`.
 */
object TestTexts {
    private val cache = HashMap<String, Map<String, String>>()

    /** id -> name of the `R.string` field. */
    private val nameById: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    private fun load(language: String): Map<String, String> = cache.getOrPut(language) {
        val dir = if (language == "de") "values-de" else "values"
        val files = File("src/main/res/$dir").listFiles { f -> f.name.startsWith("strings_") && f.name.endsWith(".xml") }.orEmpty()
        files.sortedBy { it.name }.flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to unescape(node.textContent)
            }
        }.toMap()
    }

    /** What Android does with the text of a resource file: `\'`, `\"`, `\n`, `\@`, `\?` and `\\`. */
    private fun unescape(raw: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                out.append(if (raw[i + 1] == 'n') '\n' else raw[i + 1])
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    fun text(res: Int, args: List<Any>, language: String): String {
        val name = nameById[res] ?: error("unknown string id $res")
        val format = load(language)[name] ?: error("no $language text for $name")
        if (args.isEmpty()) return format
        return String.format(format, *args.map { if (it is TextRef) text(it.res, it.args, language) else it }.toTypedArray())
    }

    fun text(ref: TextRef, language: String): String = text(ref.res, ref.args, language)

    /** The text in German. */
    fun de(ref: TextRef): String = text(ref, "de")

    /** The text in English. */
    fun en(ref: TextRef): String = text(ref, "en")

    /** Rule: [AppText] answers in [language] during the test (for code that reads texts without a Context). */
    fun rule(language: String = "de"): ExternalResource = object : ExternalResource() {
        override fun before() {
            AppText.testGerman = language == "de"
            AppText.testResolver = { res, args -> text(res, args, language) }
        }

        override fun after() {
            AppText.testGerman = null
            AppText.testResolver = null
        }
    }
}
