package app.gamenative.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ChangelogTest {

    private val sample = """
        # Änderungen

        Einleitung, wird ignoriert.

        ## 2.3.3-fold8.14 – 10.11.2026

        ### Neu
        - Änderungsliste mit Umlauten: größer, schöner
        - Zweiter Punkt,
          über zwei Zeilen

        ### Behoben
        - Fehler ÄÖÜ ß behoben

        ## 2.3.3-fold8.13 – 10.11.2026

        ### Neu
        - Kontextmenü

        ### Verbessert
        - Addon schneller

        ## 2.3.3-fold8.9 – 09.11.2026

        ### Neu
        - Wischen

        ## 2.3.3-fold8.8
        ### Merkwürdig
        - Etwas Unbekanntes
    """.trimIndent()

    // ---- Parser -----------------------------------------------------------------------------------------------

    @Test
    fun parsesVersionsDatesSectionsAndItems() {
        val e = Changelog.parse(sample)
        assertEquals(listOf("2.3.3-fold8.14", "2.3.3-fold8.13", "2.3.3-fold8.9", "2.3.3-fold8.8"), e.map { it.version })
        assertEquals("10.11.2026", e[0].date)
        assertEquals("", e[3].date)
        assertEquals(listOf(ChangeKind.Neu, ChangeKind.Behoben), e[0].sections.map { it.kind })
        assertEquals(listOf(ChangeKind.Neu, ChangeKind.Verbessert), e[1].sections.map { it.kind })
        assertEquals(3, e[0].itemCount)
    }

    @Test
    fun keepsUmlautsAndJoinsContinuationLines() {
        val e = Changelog.parse(sample)[0]
        assertEquals("Änderungsliste mit Umlauten: größer, schöner", e.sections[0].items[0])
        assertEquals("Zweiter Punkt, über zwei Zeilen", e.sections[0].items[1])
        assertEquals("Fehler ÄÖÜ ß behoben", e.sections[1].items[0])
    }

    @Test
    fun unknownSectionTitleBecomesSonstiges() {
        val last = Changelog.parse(sample).last()
        assertEquals(ChangeKind.Sonstiges, last.sections.single().kind)
        assertEquals("Etwas Unbekanntes", last.sections.single().items.single())
    }

    @Test
    fun versionNameWithDashesIsNotSplit() {
        assertEquals("2.3.3-fold8.13" to "10.11.2026", Changelog.splitHeading("2.3.3-fold8.13 – 10.11.2026"))
        assertEquals("2.3.3-fold8.13" to "10.11.2026", Changelog.splitHeading("2.3.3-fold8.13 - 10.11.2026"))
        assertEquals("2.3.3-fold8.13" to "", Changelog.splitHeading("2.3.3-fold8.13"))
    }

    @Test
    fun brokenInputNeverThrows() {
        assertTrue(Changelog.parse(null).isEmpty())
        assertTrue(Changelog.parse("").isEmpty())
        assertTrue(Changelog.parse("   \n\n").isEmpty())
        assertTrue(Changelog.parse("nur Text ohne Überschrift\n- Punkt").isEmpty())
        assertTrue(Changelog.parse("##\n### \n- \n## ").isEmpty())
        // Version without any item is dropped; items without section go to Sonstiges.
        val e = Changelog.parse("## 1.0\n## 2.0\n- lose Zeile\n### Neu\n###\n")
        assertEquals(listOf("2.0"), e.map { it.version })
        assertEquals(ChangeKind.Sonstiges, e[0].sections.single().kind)
        // Binary-looking junk and a lone heading marker.
        assertTrue(Changelog.parse("\u0000\u0001## \n###").isEmpty())
    }

    @Test
    fun crLfAndLiteralBackslashNLineBreaksWork() {
        val crlf = sample.replace("\n", "\r\n")
        assertEquals(Changelog.parse(sample), Changelog.parse(crlf))
        val oneLine = "## 2.3.3-fold8.14 – 10.11.2026\\n### Neu\\n- Eins\\n- Zwei"
        val e = Changelog.parse(oneLine)
        assertEquals(listOf("Eins", "Zwei"), e.single().sections.single().items)
    }

    @Test
    fun commandLineTransportFormDecodesUmlautEscapes() {
        val transport = "## 2.3.3-fold8.14 \\u2013 10.11.2026\\n### Neu\\n- Gr\\u00f6\\u00dfere \\u00c4nderungsliste, \\u201eEigene\\u201c"
        val e = Changelog.parse(transport).single()
        assertEquals("2.3.3-fold8.14", e.version)
        assertEquals("10.11.2026", e.date)
        assertEquals("Größere Änderungsliste, „Eigene“", e.sections.single().items.single())
        // Same through the feed path.
        val offer = Changelog.forOffer(transport, "2.3.3-fold8.14", "2.3.3-fold8.13")
        assertEquals("Größere Änderungsliste, „Eigene“", offer.single().sections.single().items.single())
    }

    // ---- Versionen seit installiert ---------------------------------------------------------------------------

    @Test
    fun sinceReturnsNewerVersionsNewestFirst() {
        val e = Changelog.parse(sample)
        assertEquals(listOf("2.3.3-fold8.14", "2.3.3-fold8.13"), Changelog.since(e, "2.3.3-fold8.9").map { it.version })
        assertTrue(Changelog.since(e, "2.3.3-fold8.14").isEmpty())
        assertEquals(4, Changelog.since(e, "2.3.3-fold7").size)
    }

    @Test
    fun sinceComparesNumbersNotText() {
        // fold8.10 > fold8.9 (numeric), also for entries stored in the wrong order.
        val text = "## 2.3.3-fold8.9\n- a\n## 2.3.3-fold8.10\n- b\n## 2.3.3-fold8.2\n- c"
        val e = Changelog.parse(text)
        assertEquals(listOf("2.3.3-fold8.10", "2.3.3-fold8.9"), Changelog.since(e, "2.3.3-fold8.2").map { it.version })
        assertEquals(listOf("2.3.3-fold8.10"), Changelog.since(e, "2.3.3-fold8.9").map { it.version })
    }

    @Test
    fun sinceHonoursUpperBound() {
        val e = Changelog.parse(sample)
        val r = Changelog.since(e, "2.3.3-fold8.8", upToName = "2.3.3-fold8.13")
        assertEquals(listOf("2.3.3-fold8.13", "2.3.3-fold8.9"), r.map { it.version })
    }

    @Test
    fun forVersionFindsExactEntry() {
        val e = Changelog.parse(sample)
        assertEquals("2.3.3-fold8.13", Changelog.forVersion(e, "2.3.3-fold8.13")?.version)
        assertEquals("2.3.3-fold8.13", Changelog.forVersion(e, " v2.3.3-FOLD8.13 ")?.version)
        assertNull(Changelog.forVersion(e, "2.3.3-fold8.12"))
    }

    @Test
    fun mergeAddsBundledHistoryWithoutDuplicates() {
        val offer = Changelog.parse("## 2.3.3-fold8.14\n- neu\n## 2.3.3-fold8.13\n- feed")
        val bundled = Changelog.parse("## 2.3.3-fold8.13\n- gebuendelt\n## 2.3.3-fold8.9\n- alt")
        val m = Changelog.merge(offer, bundled)
        assertEquals(listOf("2.3.3-fold8.14", "2.3.3-fold8.13", "2.3.3-fold8.9"), m.map { it.version })
        assertEquals("feed", m[1].sections.single().items.single())
    }

    // ---- Feed-Text --------------------------------------------------------------------------------------------

    @Test
    fun offerFromStructuredFeedTextCutsToNewVersions() {
        val r = Changelog.forOffer(sample, offeredName = "2.3.3-fold8.14", installedName = "2.3.3-fold8.9")
        assertEquals(listOf("2.3.3-fold8.14", "2.3.3-fold8.13"), r.map { it.version })
    }

    @Test
    fun offerFromStructuredFeedTextIgnoresVersionsAboveTheOfferedOne() {
        val r = Changelog.forOffer(sample, offeredName = "2.3.3-fold8.13", installedName = "2.3.3-fold8.8")
        assertEquals(listOf("2.3.3-fold8.13", "2.3.3-fold8.9"), r.map { it.version })
    }

    @Test
    fun offerFromOldOneLineFeedTextBecomesPoints() {
        val text = "Neu: Selbst-Update mit Fortschritt. Kontextmenue beim Halten; Fehler im Editor behoben."
        val r = Changelog.forOffer(text, "2.3.3-fold8.14", "2.3.3-fold8.13")
        val entry = r.single()
        assertEquals("2.3.3-fold8.14", entry.version)
        assertEquals(ChangeKind.Sonstiges, entry.sections.single().kind)
        assertEquals(
            listOf("Neu: Selbst-Update mit Fortschritt.", "Kontextmenue beim Halten", "Fehler im Editor behoben."),
            entry.sections.single().items,
        )
    }

    @Test
    fun offerWithEmptyOrNoiseTextIsEmpty() {
        assertTrue(Changelog.forOffer("", "2.3.3-fold8.14", "2.3.3-fold8.13").isEmpty())
        assertTrue(Changelog.forOffer("  \n ", "2.3.3-fold8.14", "2.3.3-fold8.13").isEmpty())
        assertTrue(Changelog.forOffer(";;;", "2.3.3-fold8.14", "2.3.3-fold8.13").isEmpty())
    }

    @Test
    fun plainPointsDoNotSplitInsideVersionNumbers() {
        assertEquals(listOf("Version 2.3.3 ist da."), Changelog.plainPoints("Version 2.3.3 ist da."))
    }

    // ---- "Neu in ..." genau einmal ----------------------------------------------------------------------------

    @Test
    fun whatsNewShowsOnceAfterUpdate() {
        // Update 266 -> 267 with an entry, not shown before.
        assertTrue(Changelog.whatsNewDue(shownCode = 266, currentCode = 267, hasEntry = true, freshInstall = false))
        // Never recorded but the app was installed before (upgrade from a build without this feature).
        assertTrue(Changelog.whatsNewDue(shownCode = 0, currentCode = 267, hasEntry = true, freshInstall = false))
        // After "Verstanden" the shown code equals the current one: not again, also not at the next start.
        assertFalse(Changelog.whatsNewDue(shownCode = 267, currentCode = 267, hasEntry = true, freshInstall = false))
    }

    @Test
    fun whatsNewSkippedWithoutEntryOrOnFreshInstallOrDowngrade() {
        assertFalse(Changelog.whatsNewDue(266, 267, hasEntry = false, freshInstall = false))
        assertFalse(Changelog.whatsNewDue(0, 267, hasEntry = true, freshInstall = true))
        assertFalse(Changelog.whatsNewDue(300, 267, hasEntry = true, freshInstall = false))
    }

    // ---- Sprache der gebündelten Datei und englische Köpfe ----------------------------------------------------

    @Test
    fun englishAndGermanSectionHeadingsMeanTheSameKinds() {
        val en = Changelog.parse("## 1.0 – 01.01.2027\n### New\n- a\n### Improved\n- b\n### Fixed\n- c\n")
        val de = Changelog.parse("## 1.0 – 01.01.2027\n### Neu\n- a\n### Verbessert\n- b\n### Behoben\n- c\n")
        assertEquals(de, en)
        assertEquals(listOf(ChangeKind.Neu, ChangeKind.Verbessert, ChangeKind.Behoben), en.single().sections.map { it.kind })
    }

    @Test
    fun theGermanFileIsUsedOnlyForGermanAndWhenItExists() {
        assertEquals("CHANGELOG.de.md", Changelog.assetName("de", true))
        assertEquals("CHANGELOG.de.md", Changelog.assetName("DE", true))
        assertEquals("CHANGELOG.md", Changelog.assetName("de", false))
        assertEquals("CHANGELOG.md", Changelog.assetName("en", true))
        assertEquals("CHANGELOG.md", Changelog.assetName("fr", true))
    }

    // ---- Gebündelte Datei -------------------------------------------------------------------------------------

    private fun repoFile(vararg candidates: String): File? = candidates.map { File(it) }.firstOrNull { it.isFile }

    @Test
    fun bundledAssetEqualsRepoChangelogAndParses() {
        val root = repoFile("../CHANGELOG.md", "CHANGELOG.md")
        val asset = repoFile("src/main/assets/CHANGELOG.md", "app/src/main/assets/CHANGELOG.md")
        assumeTrue("CHANGELOG-Dateien nicht gefunden (Arbeitsordner)", root != null && asset != null)
        val a = root!!.readText(Charsets.UTF_8)
        val b = asset!!.readText(Charsets.UTF_8)
        assertEquals("app/src/main/assets/CHANGELOG.md muss eine Kopie von CHANGELOG.md sein", a, b)
        assertFalse(a.contains("\r"))

        val entries = Changelog.parse(a)
        assertTrue(entries.size >= 7)
        entries.forEach { e ->
            assertTrue("Version ${e.version} ohne Punkte", e.itemCount > 0)
            assertTrue("Datum fehlt bei ${e.version}", e.date.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}")))
        }
        // Newest first, strictly descending.
        for (i in 0 until entries.lastIndex) {
            assertTrue(UpdateLogic.compareVersionNames(entries[i].version, entries[i + 1].version) > 0)
        }
        assertNotNull(Changelog.forVersion(entries, "2.3.3-fold8.13"))
        assertNotNull(Changelog.forVersion(entries, "2.3.3-fold8.7"))
        // No internal vocabulary in the user text.
        val allText = entries.joinToString(" ") { e -> e.sections.joinToString(" ") { it.items.joinToString(" ") } }
        assertFalse(allText.contains("Worker", ignoreCase = true))
        assertFalse(Regex("\\b[0-9a-f]{7,}\\b").containsMatchIn(allText))
    }
}
