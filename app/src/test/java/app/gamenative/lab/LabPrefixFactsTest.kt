package app.gamenative.lab

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Dateiprüfung des Prefix für das Protokoll (Punkt 9 von labstart2). */
class LabPrefixFactsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun reportsPresentMissingAndBothLayers() {
        val upper = File(tmp.root, "upper").apply { mkdirs() }
        val lower = File(tmp.root, "lower").apply { mkdirs() }
        File(lower, "drive_c/windows/system32").mkdirs()
        File(lower, "drive_c/windows/winhandler.exe").writeBytes(ByteArray(1234))
        File(upper, "drive_c/windows/system32").mkdirs()
        File(upper, "drive_c/windows/system32/wowbox64.dll").writeBytes(ByteArray(10))

        val lines = LabPrefixFacts.lines(listOf(LabPrefixFacts.Layer("upper", upper), LabPrefixFacts.Layer("lower", lower)))
        assertEquals(LabPrefixFacts.FILES.size, lines.size)
        assertEquals("winhandler.exe: upper=missing; lower=present (1234 bytes)", lines[0])
        assertEquals("wfm.exe: upper=missing; lower=missing", lines[1])
        assertEquals("wowbox64.dll: upper=present (10 bytes); lower=missing", lines[2])
        assertEquals("libwow64fex.dll: upper=missing; lower=missing", lines[3])
        assertEquals("libarm64ecfex.dll: upper=missing; lower=missing", lines[4])
    }

    @Test
    fun aMissingLayerIsSaid() {
        assertEquals("no such layer", LabPrefixFacts.describe(null, "drive_c/windows/wfm.exe"))
    }

    @Test
    fun linksAreRecognised() {
        val root = File(tmp.root, "p").apply { mkdirs() }
        File(root, "drive_c/windows/system32").mkdirs()
        val target = File(tmp.root, "real.dll").apply { writeBytes(ByteArray(77)) }
        val link = File(root, "drive_c/windows/system32/wowbox64.dll")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isSuccess
        if (!created) return // Symbolische Verknüpfungen sind auf diesem Rechner nicht erlaubt.
        assertEquals("present (77 bytes, link)", LabPrefixFacts.describe(root, "drive_c/windows/system32/wowbox64.dll"))
        target.delete()
        assertEquals("dangling link", LabPrefixFacts.describe(root, "drive_c/windows/system32/wowbox64.dll"))
    }

    @Test
    fun hodllLineShowsTheValueOrThatItIsNotSet() {
        assertEquals("HODLL=wowbox64.dll", LabPrefixFacts.hodllLine("wowbox64.dll"))
        assertEquals("HODLL=libwow64fex.dll", LabPrefixFacts.hodllLine("libwow64fex.dll"))
        assertEquals("HODLL=(not set)", LabPrefixFacts.hodllLine(""))
        assertEquals("HODLL=(not set)", LabPrefixFacts.hodllLine(null))
        assertTrue(LabPrefixFacts.FILES.any { it.endsWith("winhandler.exe") } && LabPrefixFacts.FILES.any { it.endsWith("wfm.exe") })
    }
}
