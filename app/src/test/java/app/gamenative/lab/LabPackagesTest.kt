package app.gamenative.lab

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabPackagesTest {
    private val sha = "a".repeat(64)

    private fun entry(id: String = "p1", art: String = "wine", url: String = "https://example.invalid/p.wcp", sha256: String = sha, bytes: Long = 100, format: String = "wcp") =
        "{\"id\":\"$id\",\"art\":\"$art\",\"name\":\"Name $id\",\"version\":\"1\",\"url\":\"$url\",\"sha256\":\"$sha256\",\"bytes\":$bytes,\"lizenz\":\"L\",\"format\":\"$format\",\"hinweis\":\"H\"}"

    @Test
    fun emptyListIsFine() {
        val list = LabPackages.parse("[]")
        assertTrue(list.packages.isEmpty())
        assertTrue(list.errors.isEmpty())
    }

    @Test
    fun validEntriesAreRead() {
        val list = LabPackages.parse("[" + entry() + "," + entry(id = "f1", art = "fex", format = "tzst") + "]")
        assertEquals(listOf("p1", "f1"), list.packages.map { it.id })
        assertEquals(LabPackageKind.FEX, list.packages[1].kind)
        assertTrue(list.errors.isEmpty())
    }

    @Test
    fun brokenFileGivesOneFileError() {
        for (text in listOf("{not json", "{}", "", null)) {
            val list = LabPackages.parse(text)
            assertTrue(list.packages.isEmpty())
            assertEquals(listOf(LabPackageErrorCode.FILE_BROKEN), list.errors.map { it.code })
        }
    }

    @Test
    fun badEntriesAreRejectedAndTheRestStays() {
        val list = LabPackages.parse(
            "[" + entry(id = "ok") + "," + entry(id = "ok") + "," + entry(id = "http", url = "http://example.invalid/x") + "," +
                entry(id = "nosha", sha256 = "") + "," + entry(id = "badsha", sha256 = "zz") + "," + entry(id = "nobytes", bytes = 0) + "," +
                entry(id = "kind", art = "driver") + "," + entry(id = "fmt", format = "tzst") + ",5]",
        )
        assertEquals(listOf("ok"), list.packages.map { it.id })
        assertEquals(
            listOf(
                LabPackageErrorCode.ID_DUPLICATE, LabPackageErrorCode.URL_NOT_HTTPS, LabPackageErrorCode.SHA256_MISSING, LabPackageErrorCode.SHA256_INVALID,
                LabPackageErrorCode.BYTES_INVALID, LabPackageErrorCode.KIND_UNKNOWN, LabPackageErrorCode.FORMAT_INVALID, LabPackageErrorCode.NOT_AN_OBJECT,
            ),
            list.errors.map { it.code },
        )
    }

    @Test
    fun onlyHttpsCounts() {
        assertTrue(LabPackages.isHttps("https://host/x"))
        assertFalse(LabPackages.isHttps("http://host/x"))
        assertFalse(LabPackages.isHttps("https:///x"))
        assertFalse(LabPackages.isHttps("ftp://host/x"))
        assertFalse(LabPackages.isHttps("file:///x"))
    }

    private fun pkg(kind: LabPackageKind = LabPackageKind.WINE, sha256: String = sha, bytes: Long = 100, url: String = "https://example.invalid/p.wcp") =
        LabPackage("p", kind, "n", "1", url, sha256, bytes, "l", "wcp", "")

    @Test
    fun checkAcceptsMatchingSizeAndHash() {
        assertEquals(LabPackageCheck.Verified, LabPackages.check(pkg(), 100, sha.uppercase()))
    }

    @Test
    fun checkRejectsWrongHashSizeAndInsecureUrl() {
        assertTrue(LabPackages.check(pkg(), 100, "b".repeat(64)) is LabPackageCheck.HashMismatch)
        assertEquals(LabPackageCheck.SizeMismatch(100, 99), LabPackages.check(pkg(), 99, sha))
        assertEquals(LabPackageCheck.InsecureUrl, LabPackages.check(pkg(url = "http://example.invalid/x"), 100, sha))
    }

    @Test
    fun anEntryWithoutAHashStaysUnverifiedAndTheInstallerIsAdvisory() {
        val installer = pkg(kind = LabPackageKind.INSTALLER, sha256 = "", bytes = 0)
        assertEquals(LabPackageCheck.Unverified, LabPackages.check(installer, 4_700_000, "c".repeat(64)))
        assertTrue(LabPackages.isAdvisory(installer))
        assertFalse(LabPackages.isAdvisory(pkg()))
        // Mit Eintrag aus der Liste (Stand beim Eintragen): Abweichung bleibt ein Hinweis, keine Ablehnung des Installers.
        val listed = pkg(kind = LabPackageKind.INSTALLER)
        assertTrue(LabPackages.check(listed, 100, "d".repeat(64)) is LabPackageCheck.HashMismatch)
        assertTrue(LabPackages.isAdvisory(listed))
    }

    @Test
    fun wineIdentifierFollowsTheContainerNamingRules() {
        assertEquals("proton-10.0-34-arm64ec-7", LabPackages.wineIdentifier("proton-10.0-34-arm64ec", 7))
        assertEquals("wine-9.0-x86_64-1", LabPackages.wineIdentifier("wine-9.0-x86_64", 1))
        assertNull(LabPackages.wineIdentifier("proton-10.0-34-arm64ec", 12))
        assertNull(LabPackages.wineIdentifier("custom-build", 1))
    }

    @Test
    fun fexPackageWithoutProfileGetsALabOnlyName() {
        val fex = LabPackage("fex-2508", LabPackageKind.FEX, "n", "2508 (Paket-ID); intern x", "https://h/f.tzst", sha, 10, "", "tzst", "")
        assertTrue(LabPackages.isBareFex(fex))
        assertEquals("lab-2508", LabPackages.fexVerName(fex))
        assertEquals("lab-fex-x", LabPackages.fexVerName(fex.copy(id = "fex-x", version = "unbekannt")))
        assertEquals("fex-2508.tzst", LabPackages.fileName(fex))
        assertFalse(LabPackages.isBareFex(fex.copy(format = "wcp")))
    }

    @Test
    fun fexProfileJsonPutsBothDllsIntoSystem32() {
        val json = org.json.JSONObject(LabPackages.fexProfileJson("lab-2508"))
        assertEquals("FEXCore", json.getString("type"))
        assertEquals("lab-2508", json.getString("versionName"))
        val files = json.getJSONArray("files")
        assertEquals(2, files.length())
        assertEquals(setOf("libarm64ecfex.dll", "libwow64fex.dll"), (0 until 2).map { files.getJSONObject(it).getString("source") }.toSet())
        for (i in 0 until 2) assertTrue(files.getJSONObject(i).getString("target").startsWith("\${system32}/"))
    }

    @Test
    fun theShippedListIsValid() {
        val list = LabPackages.parse(File("src/main/assets/bnetlab_pakete.json").readText())
        assertTrue(list.errors.toString(), list.errors.isEmpty())
        assertEquals(4, list.packages.size)
        assertEquals(setOf(LabPackageKind.WINE, LabPackageKind.FEX, LabPackageKind.INSTALLER), list.packages.map { it.kind }.toSet())
        assertTrue(list.packages.all { LabPackages.isHttps(it.url) })
        val installer = list.packages.single { it.kind == LabPackageKind.INSTALLER }
        assertEquals(LabIds.INSTALLER_URL, installer.url)
        assertEquals("exe", installer.format)
        assertTrue(list.packages.single { it.id == LabQuickPicks.FEX_PACKAGE_ID }.let { LabPackages.isBareFex(it) })
        assertTrue(list.packages.any { it.id == LabQuickPicks.PROTON10_PACKAGE_ID })
    }
}
