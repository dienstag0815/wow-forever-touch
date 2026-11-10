package app.gamenative.update

import androidx.annotation.StringRes
import app.gamenative.R

/**
 * Kind of a changelog section (`### New` / `### Neu`, `### Improved` / `### Verbessert`, `### Fixed` / `### Behoben`); anything
 * else is [Sonstiges]. [titleRes] is the heading the player sees, in the app language.
 */
enum class ChangeKind(@StringRes val titleRes: Int) {
    Neu(R.string.launcher_changelog_kind_new),
    Verbessert(R.string.launcher_changelog_kind_improved),
    Behoben(R.string.launcher_changelog_kind_fixed),
    Sonstiges(R.string.launcher_changelog_kind_other),
}

data class ChangelogSection(val kind: ChangeKind, val items: List<String>)

/** One version of the changelog: name like `2.3.3-fold8.13`, date as written (may be empty) and its sections. */
data class ChangelogEntry(val version: String, val date: String, val sections: List<ChangelogSection>) {
    val itemCount: Int get() = sections.sumOf { it.items.size }
}

/**
 * Pure logic of the changelog (no Android classes, runs in plain JVM tests): parser for `CHANGELOG.md`, choice of the
 * versions newer than the installed one, fallback for the old one-line feed text and the "Neu in ..." show-once rule.
 *
 * Format (lenient, never throws): `## <Version> – <Datum>` starts a version (separator en dash, em dash or " - " with
 * spaces, date optional), `### Neu` / `### Verbessert` / `### Behoben` start a section, `- Punkt` (or `*`, `•`) is an
 * item, an indented line continues the previous item. Text outside a version is ignored. The feed text may also use the
 * two-character sequence `\n` instead of real line breaks (a command-line argument cannot carry them safely).
 */
object Changelog {
    private val VERSION_HEADING = Regex("^##\\s+(.+)$")
    private val SECTION_HEADING = Regex("^###\\s+(.+)$")
    private val BULLET = Regex("^[-*•]\\s+(.*)$")
    private val DATE_SEPARATOR = Regex("\\s+[–—-]\\s+")

    fun parse(text: String?): List<ChangelogEntry> {
        if (text.isNullOrBlank()) return emptyList()
        val lines = normalize(text).lines()
        val result = ArrayList<ChangelogEntry>()

        var version: String? = null
        var date = ""
        var kind: ChangeKind? = null
        val sections = ArrayList<ChangelogSection>()
        var items = ArrayList<String>()

        fun closeSection() {
            if (items.isNotEmpty()) sections += ChangelogSection(kind ?: ChangeKind.Sonstiges, items.toList())
            items = ArrayList()
        }

        fun closeVersion() {
            closeSection()
            val v = version
            if (v != null && sections.isNotEmpty()) result += ChangelogEntry(v, date, sections.toList())
            sections.clear()
        }

        for (raw in lines) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            val versionMatch = VERSION_HEADING.matchEntire(trimmed)
            if (versionMatch != null && !trimmed.startsWith("###")) {
                closeVersion()
                val (v, d) = splitHeading(versionMatch.groupValues[1])
                version = v.takeIf { it.isNotEmpty() }
                date = d
                kind = null
                continue
            }
            if (version == null) continue
            val sectionMatch = SECTION_HEADING.matchEntire(trimmed)
            if (sectionMatch != null) {
                closeSection()
                kind = kindOf(sectionMatch.groupValues[1])
                continue
            }
            if (trimmed.isEmpty()) continue
            val bullet = BULLET.matchEntire(trimmed)
            if (bullet != null) {
                val text1 = bullet.groupValues[1].trim()
                if (text1.isNotEmpty()) items += text1
            } else if (items.isNotEmpty() && raw.startsWith(" ")) {
                items[items.lastIndex] = items.last() + " " + trimmed
            } else if (!trimmed.startsWith("#")) {
                // Plain text line below a version without bullet: keep it as its own item.
                items += trimmed
            }
        }
        closeVersion()
        return result
    }

    private val UNICODE_ESCAPE = Regex("\\\\u([0-9a-fA-F]{4})")

    /**
     * Line breaks to `\n`, and the transport form of a command-line argument decoded: literal backslash-n instead of real
     * line breaks (only when the text has no real ones) and literal backslash-u plus 4 hex digits instead of non-ASCII
     * characters (tools/changelog_text.py writes this form, the console would mangle umlauts).
     */
    private fun normalize(text: String): String {
        var t = text.replace("\r\n", "\n").replace('\r', '\n')
        if (!t.contains('\n') && t.contains("\\n")) t = t.replace("\\n", "\n")
        if (t.contains("\\u")) {
            t = UNICODE_ESCAPE.replace(t) { it.groupValues[1].toInt(16).toChar().toString() }
        }
        return t
    }

    /** Splits `2.3.3-fold8.13 – 10.11.2026` into version and date; a version name itself may contain `-` without spaces. */
    internal fun splitHeading(heading: String): Pair<String, String> {
        val h = heading.trim()
        val m = DATE_SEPARATOR.find(h) ?: return h to ""
        return h.substring(0, m.range.first).trim() to h.substring(m.range.last + 1).trim()
    }

    /** File name of the bundled changelog for the app language: the German one when the language is German and the file exists. */
    fun assetName(language: String, germanExists: Boolean): String =
        if (language.equals("de", ignoreCase = true) && germanExists) ASSET_DE else ASSET_DEFAULT

    const val ASSET_DEFAULT = "CHANGELOG.md"
    const val ASSET_DE = "CHANGELOG.de.md"

    private fun kindOf(title: String): ChangeKind = when (title.trim().lowercase().trimEnd(':')) {
        "neu", "neues", "new" -> ChangeKind.Neu
        "verbessert", "verbesserungen", "geändert", "geaendert", "improved", "changed" -> ChangeKind.Verbessert
        "behoben", "fehlerbehebungen", "fehler behoben", "fixed" -> ChangeKind.Behoben
        else -> ChangeKind.Sonstiges
    }

    /**
     * Entries newer than [installedName] (and, when [upToName] is given, not newer than that), newest first. Versions
     * are compared like the update check does ([UpdateLogic.compareVersionNames]).
     */
    fun since(entries: List<ChangelogEntry>, installedName: String, upToName: String? = null): List<ChangelogEntry> =
        entries
            .filter { UpdateLogic.compareVersionNames(it.version, installedName) > 0 }
            .filter { upToName == null || UpdateLogic.compareVersionNames(it.version, upToName) <= 0 }
            .sortedWith { a, b -> UpdateLogic.compareVersionNames(b.version, a.version) }

    /** The entry of exactly this version (name compared without case and a leading `v`), or null. */
    fun forVersion(entries: List<ChangelogEntry>, versionName: String): ChangelogEntry? {
        val wanted = versionName.trim().trimStart('v', 'V')
        return entries.firstOrNull { it.version.trim().trimStart('v', 'V').equals(wanted, ignoreCase = true) }
    }

    /**
     * Whole history for the "Alle Änderungen" view: [primary] (e.g. the offered versions from the feed) plus the entries
     * of [secondary] (the bundled file) whose version is not in [primary]; newest first.
     */
    fun merge(primary: List<ChangelogEntry>, secondary: List<ChangelogEntry>): List<ChangelogEntry> {
        val extra = secondary.filter { forVersion(primary, it.version) == null }
        return (primary + extra).sortedWith { a, b -> UpdateLogic.compareVersionNames(b.version, a.version) }
    }

    /**
     * Changelog for an offered update from the feed field `text`. A text with `## ` version headings is parsed and cut to
     * the versions after the installed one up to the offered one. An old-style text (one line, no headings) becomes one
     * entry for the offered version, split into points. Empty text gives an empty list.
     */
    fun forOffer(feedText: String, offeredName: String, installedName: String): List<ChangelogEntry> {
        val text = normalize(feedText)
        if (text.isBlank()) return emptyList()
        if (text.lines().any { VERSION_HEADING.matches(it.trim()) && !it.trim().startsWith("###") }) {
            return since(parse(text), installedName, offeredName)
        }
        val points = plainPoints(text)
        if (points.isEmpty()) return emptyList()
        return listOf(ChangelogEntry(offeredName, "", listOf(ChangelogSection(ChangeKind.Sonstiges, points))))
    }

    /** Splits old one-line notes ("Neu: A. B; C") into readable points: by line, `;` and sentence ends. */
    internal fun plainPoints(text: String): List<String> {
        val sentenceEnd = Regex("(?<=[.!?])\\s+(?=[A-ZÄÖÜ0-9„\"])")
        return text.lines()
            .flatMap { it.split(";") }
            .flatMap { it.trim().split(sentenceEnd) }
            .map { it.trim().trimStart('-', '*', '•', ' ').trim().trimEnd(';').trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * True when "Neu in ..." is due once: the bundled changelog has an entry for the running version, it was not shown yet
     * ([shownCode] below [currentCode]) and this is not a fresh installation.
     */
    fun whatsNewDue(shownCode: Int, currentCode: Int, hasEntry: Boolean, freshInstall: Boolean): Boolean =
        hasEntry && !freshInstall && shownCode < currentCode
}
