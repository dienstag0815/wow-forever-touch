package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonCatalogTest {

    private fun entry(
        id: String = "\"id\":\"demo\"",
        name: String = "\"name\":\"Demo\"",
        kurz: String = "\"kurz\":\"Kurz\"",
        warum: String = "\"warum\":\"Weil\"",
        lizenz: String = "\"lizenz\":\"MIT\"",
        quelle: String = "\"quelle\":{\"typ\":\"github\",\"repo\":\"o/r\",\"asset\":\"^Demo-.*\\\\.zip$\"}",
        ordner: String = "\"ordner\":[\"Demo\"]",
        extra: String = "",
    ) = listOf(id, name, kurz, warum, lizenz, quelle, ordner, extra).filter { it.isNotEmpty() }.joinToString(",", "{", "}")

    private fun catalog(vararg entries: String, version: Int = 1) =
        "{\"version\":$version,\"stand\":\"2026-11-08\",\"addons\":[${entries.joinToString(",")}]}"

    private fun invalid(json: String?): UiText {
        val r = AddonCatalogParser.parse(json)
        assertTrue("sollte ungültig sein: $r", r is CatalogResult.Invalid)
        return (r as CatalogResult.Invalid).reason
    }

    private fun ok(json: String): AddonCatalog {
        val r = AddonCatalogParser.parse(json)
        assertTrue("sollte gültig sein: $r", r is CatalogResult.Ok)
        return (r as CatalogResult.Ok).catalog
    }

    @Test
    fun validEntryIsParsedWithDefaults() {
        val e = ok(catalog(entry())).entries.single()
        assertEquals("demo", e.id)
        assertEquals(listOf("Demo"), e.folders)
        assertEquals(AddonCatalogParser.DEFAULT_MAX_DOWNLOAD_BYTES, e.maxDownloadBytes)
        assertEquals(AddonCatalogParser.DEFAULT_MAX_UNPACKED_BYTES, e.maxUnpackedBytes)
        val s = e.source as AddonSource.GitHub
        assertEquals("o/r", s.slug)
        assertTrue(s.requireForever)
        assertTrue(s.matchesAsset("Demo-v1.2.zip"))
        assertTrue(s.matchesAsset("demo-v1.2.ZIP"))
        assertFalse(s.matchesAsset("Other-v1.2.zip"))
        assertFalse(s.matchesAsset("Demo-v1.2.zip.sig"))
    }

    @Test
    fun wowInterfaceSourceNeedsNumericId() {
        val good = entry(quelle = "\"quelle\":{\"typ\":\"wowinterface\",\"id\":\"12345\"}")
        assertEquals(AddonSource.WowInterface("12345"), ok(catalog(good)).entries.single().source)
        invalid(catalog(entry(quelle = "\"quelle\":{\"typ\":\"wowinterface\",\"id\":\"12a\"}")))
        invalid(catalog(entry(quelle = "\"quelle\":{\"typ\":\"wowinterface\"}")))
    }

    @Test
    fun limitsCanBeRaisedButNotBeyondTheHardCap() {
        val e = ok(catalog(entry(extra = "\"maxMB\":100,\"maxEntpacktMB\":250"))).entries.single()
        assertEquals(100L * 1024 * 1024, e.maxDownloadBytes)
        assertEquals(250L * 1024 * 1024, e.maxUnpackedBytes)
        invalid(catalog(entry(extra = "\"maxMB\":500")))
        invalid(catalog(entry(extra = "\"maxEntpacktMB\":1000")))
        invalid(catalog(entry(extra = "\"maxMB\":0")))
        invalid(catalog(entry(extra = "\"maxMB\":-5")))
    }

    @Test
    fun brokenInputIsRejectedAsAWhole() {
        invalid(null)
        invalid("")
        invalid("kein json")
        invalid("[]")
        invalid(catalog(entry(), version = 2))
        invalid("{\"version\":1}")
        // Ein schlechter Eintrag macht den ganzen Katalog ungültig.
        invalid(catalog(entry(), entry(id = "\"id\":\"zwei\"", ordner = "\"ordner\":[\"../Boese\"]")))
    }

    @Test
    fun requiredTextFieldsMustBePresent() {
        invalid(catalog(entry(name = "")))
        invalid(catalog(entry(kurz = "")))
        invalid(catalog(entry(warum = "")))
        invalid(catalog(entry(lizenz = "")))
        invalid(catalog(entry(quelle = "")))
        invalid(catalog(entry(ordner = "")))
        invalid(catalog(entry(ordner = "\"ordner\":[]")))
    }

    @Test
    fun idsAndFoldersMustBeUnique() {
        invalid(catalog(entry(), entry()))
        val other = entry(id = "\"id\":\"zwei\"", ordner = "\"ordner\":[\"demo\"]")
        assertEquals(UiText.res(R.string.launcher_addon_catalog_shared_folder), invalid(catalog(entry(), other)))
        invalid(catalog(entry(ordner = "\"ordner\":[\"Demo\",\"demo\"]")))
    }

    @Test
    fun badIdsAndFolderNamesAreRejected() {
        for (id in listOf("", "Demo", "-demo", "de mo", "a".repeat(41), "../x")) {
            invalid(catalog(entry(id = "\"id\":\"$id\"")))
        }
        for (folder in listOf("", ".hidden", "a/b", "a\\\\b", "..", "../x", " Demo", "Demo ", "D:x", "a".repeat(65))) {
            invalid(catalog(entry(ordner = "\"ordner\":[\"$folder\"]")))
        }
    }

    @Test
    fun ownAddonAndBlizzardFoldersAreProtected() {
        for (folder in listOf("TouchForever", "touchforever", "WowForeverBridge", "TouchForeverExtra", "Blizzard_ClassTalentUI", "blizzard_x")) {
            assertTrue("$folder muss geschützt sein", AddonReserved.isReserved(folder))
            invalid(catalog(entry(ordner = "\"ordner\":[\"$folder\"]")))
        }
        assertFalse(AddonReserved.isReserved("Questie"))
    }

    @Test
    fun badSourcesAreRejected() {
        invalid(catalog(entry(quelle = "\"quelle\":{\"typ\":\"curseforge\",\"id\":\"1\"}")))
        invalid(catalog(entry(quelle = "\"quelle\":{\"typ\":\"github\",\"repo\":\"nur-name\",\"asset\":\"x\"}")))
        invalid(catalog(entry(quelle = "\"quelle\":{\"typ\":\"github\",\"repo\":\"a/b/c\",\"asset\":\"x\"}")))
        invalid(catalog(entry(quelle = "\"quelle\":{\"typ\":\"github\",\"repo\":\"../b\",\"asset\":\"x\"}")))
        invalid(catalog(entry(quelle = "\"quelle\":{\"typ\":\"github\",\"repo\":\"o/r\"}")))
        invalid(catalog(entry(quelle = "\"quelle\":{\"typ\":\"github\",\"repo\":\"o/r\",\"asset\":\"(\"}")))
    }

    @Test
    fun theShippedCatalogIsValidAndSafe() {
        val file = listOf("src/main/assets/wow/addons/katalog.json", "app/src/main/assets/wow/addons/katalog.json")
            .map(::File).first { it.isFile }
        val catalog = ok(file.readText(Charsets.UTF_8))
        assertTrue(catalog.entries.size in 3..6)
        for (e in catalog.entries) {
            assertTrue("${e.id}: Quelle GitHub oder WoWInterface", e.source is AddonSource.GitHub || e.source is AddonSource.WowInterface)
            assertTrue(e.folders.none { AddonReserved.isReserved(it) })
            assertTrue(e.summary.isNotBlank() && e.why.isNotBlank() && e.license.isNotBlank())
            assertTrue(e.maxDownloadBytes <= AddonCatalogParser.HARD_MAX_DOWNLOAD_BYTES)
            assertTrue(e.maxUnpackedBytes >= e.maxDownloadBytes)
        }
        assertEquals("TouchForever kommt im Katalog nicht vor", null, catalog.byId("touchforever"))
    }
}
