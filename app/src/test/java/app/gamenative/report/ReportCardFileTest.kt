package app.gamenative.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Der Zustand der Karte "Bericht bereit": speichern, laden, ersetzen, schließen, fehlende Zip. */
class ReportCardFileTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun card(name: String = "WoWForever-Bericht-20261114-1000.zip", time: Long = 1_700_000_000_000L, zip: String = "/cache/fehlerbericht/$name") =
        ReportCard(name, time, zip, inDownloads = true, issueUrl = "https://github.com/example/app/issues/new?template=bug_report.yml&title=T")

    @Test
    fun aSavedCardIsLoadedAgainUnchanged() {
        val file = File(tmp.root, ReportCardFile.FILE_NAME)
        assertTrue(ReportCardFile.save(file, card()))
        assertEquals(card(), ReportCardFile.load(file))
    }

    @Test
    fun aCardWithoutDownloadsFlagAndWithoutAddressKeepsBoth() {
        val file = File(tmp.root, ReportCardFile.FILE_NAME)
        val bare = card().copy(inDownloads = false, issueUrl = null)
        ReportCardFile.save(file, bare)
        assertEquals(bare, ReportCardFile.load(file))
    }

    @Test
    fun aNewReportReplacesTheCard() {
        val file = File(tmp.root, ReportCardFile.FILE_NAME)
        ReportCardFile.save(file, card("a.zip", 1_700_000_000_000L))
        ReportCardFile.save(file, card("b.zip", 1_700_000_900_000L))
        assertEquals("b.zip", ReportCardFile.load(file)?.zipName)
        assertEquals(1_700_000_900_000L, ReportCardFile.load(file)?.createdMs)
        // Keine Nachbardatei bleibt liegen.
        assertEquals(listOf(ReportCardFile.FILE_NAME), tmp.root.list()!!.toList())
    }

    @Test
    fun closingRemovesTheCardAndClosingTwiceIsFine() {
        val file = File(tmp.root, ReportCardFile.FILE_NAME)
        ReportCardFile.save(file, card())
        assertTrue(ReportCardFile.clear(file))
        assertNull(ReportCardFile.load(file))
        assertFalse(file.exists())
        assertTrue(ReportCardFile.clear(file))
    }

    @Test
    fun aMissingOrBrokenFileGivesNoCard() {
        val file = File(tmp.root, ReportCardFile.FILE_NAME)
        assertNull(ReportCardFile.load(file))
        file.writeText("")
        assertNull(ReportCardFile.load(file))
        file.writeText("müll ohne gleichheitszeichen")
        assertNull(ReportCardFile.load(file))
        file.writeText("name=a.zip\ntime=abc\nzip=/x/a.zip\n")
        assertNull(ReportCardFile.load(file))
        file.writeText("name=../a.zip\ntime=5\nzip=/x/a.zip\n")
        assertNull(ReportCardFile.load(file))
        file.writeText("name=a.zip\ntime=5\n")
        assertNull(ReportCardFile.load(file))
    }

    @Test
    fun anAddressThatIsNotHttpsIsDropped() {
        val parsed = ReportCardFile.parse("name=a.zip\ntime=5\nzip=/x/a.zip\ndownloads=1\nurl=javascript:alert(1)\n")
        assertEquals("a.zip", parsed?.zipName)
        assertNull(parsed?.issueUrl)
    }

    @Test
    fun lineBreaksInValuesCannotAddFields() {
        val text = ReportCardFile.serialize(card(name = "a\nurl=https:evil"))
        assertEquals(5, text.trimEnd().lines().size)
        val parsed = ReportCardFile.parse(text)
        assertEquals("a url=https:evil", parsed?.zipName)
        assertTrue(parsed?.issueUrl?.startsWith("https://github.com/") == true)
    }

    @Test
    fun theZipOfTheCardIsThereOrNot() {
        val zip = File(tmp.root, "report.zip").apply { writeBytes(byteArrayOf(1)) }
        assertTrue(ReportCardFile.zipExists(card(zip = zip.path)))
        zip.delete()
        assertFalse(ReportCardFile.zipExists(card(zip = zip.path)))
    }

    @Test
    fun theSummaryComesFromTheZipAndAMissingOrBrokenZipGivesNone() {
        val zip = File(tmp.root, "report.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("geraet.txt")); out.write("device".toByteArray()); out.closeEntry()
            out.putNextEntry(ZipEntry("kurz.txt")); out.write("Device: Maker Model\nÄ ö".toByteArray()); out.closeEntry()
        }
        assertEquals("Device: Maker Model\nÄ ö", ReportCardFile.summaryFromZip(zip))
        assertNull(ReportCardFile.summaryFromZip(File(tmp.root, "missing.zip")))
        val broken = File(tmp.root, "broken.zip").apply { writeText("no zip") }
        assertNull(ReportCardFile.summaryFromZip(broken))
        val without = File(tmp.root, "without.zip")
        ZipOutputStream(without.outputStream()).use { out -> out.putNextEntry(ZipEntry("geraet.txt")); out.write("x".toByteArray()); out.closeEntry() }
        assertNull(ReportCardFile.summaryFromZip(without))
    }
}
