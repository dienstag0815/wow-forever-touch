package app.gamenative.ui.screen.wow.launcher

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WowArtTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // --- Adressen ---

    @Test
    fun heroUrlsCarryTheSizeAndFormatParameters() {
        assertEquals(5, WowArtSources.HERO.size)
        for (art in WowArtSources.HERO) {
            val url = WowArtSources.heroUrl(art)
            assertTrue(url, url.startsWith("https://blz-contentstack-images.akamaized.net/v3/assets/blt9c12f249ac15c7ec/"))
            assertTrue(url, url.endsWith("?width=1280&format=jpg"))
            assertEquals(1, url.count { it == '?' })
        }
        assertEquals(
            "https://blz-contentstack-images.akamaized.net/v3/assets/blt9c12f249ac15c7ec/" +
                "bltdb388a4abd197586/6a86a1886cec0d30e9b13fe8/classic-masthead-lg.png?width=1280&format=jpg",
            WowArtSources.heroUrl(WowArtSources.HERO.first()),
        )
    }

    @Test
    fun heroNamesAndKeysAreUnique() {
        val keys = WowArtSources.HERO.map { WowArtSources.heroKey(it) }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(setOf("masthead", "ragnaros", "hardcore", "sod", "bcc"), WowArtSources.HERO.map { it.name }.toSet())
    }

    @Test
    fun tileMapsUseTheAgreedZoneIds() {
        assertEquals(14, WowArtSources.mapId(LauncherTile.Touch))
        assertEquals(141, WowArtSources.mapId(LauncherTile.Addons))
        assertEquals(1537, WowArtSources.mapId(LauncherTile.Settings))
        assertEquals(1519, WowArtSources.mapId(LauncherTile.Files))
        assertEquals(12, WowArtSources.mapId(LauncherTile.News))
        assertEquals(1637, WowArtSources.mapId(LauncherTile.Help))
        assertEquals("https://wow.zamimg.com/images/wow/classic/maps/enus/zoom/1537.jpg", WowArtSources.tileUrl(LauncherTile.Settings))
        assertEquals("https://wow.zamimg.com/images/wow/classic/maps/enus/zoom/14.jpg", WowArtSources.tileUrl(LauncherTile.Touch))
        // jede Kachel hat eine eigene Karte und einen eigenen Schlüssel
        val ids = LauncherTile.entries.map { WowArtSources.mapId(it) }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(LauncherTile.entries.size, LauncherTile.entries.map { WowArtSources.tileKey(it) }.toSet().size)
    }

    // --- Zuschnitt der weißen Ränder ---

    @Test
    fun noCropKeepsTheWholeImage() {
        assertEquals(0 to 957, WowArtSources.heroCropRows(957, 0f, 0f))
        assertEquals(0 to 1, WowArtSources.heroCropRows(1, 0.1f, 0.1f))
        assertEquals(0 to 0, WowArtSources.heroCropRows(0, 0.1f, 0.1f))
    }

    @Test
    fun cropRowsAreRoundedUpAndCleanNumbersStayExact() {
        // 5 % von 100 sind genau 5 Zeilen (kein Float-Rest, der eine sechste kostet)
        assertEquals(5 to 90, WowArtSources.heroCropRows(100, 0.05f, 0.05f))
        // 957 * 0,05 = 47,85 -> 48 oben, 957 * 0,045 = 43,07 -> 44 unten
        assertEquals(48 to (957 - 48 - 44), WowArtSources.heroCropRows(957, 0.05f, 0.045f))
        // verkleinert dekodiert (Faktor 2) bleibt der Anteil gleich
        assertEquals(24 to (479 - 24 - 22), WowArtSources.heroCropRows(479, 0.05f, 0.045f))
    }

    @Test
    fun cropIsLimitedAndAlwaysLeavesRows() {
        val (first, rows) = WowArtSources.heroCropRows(100, 0.9f, 0.9f)
        assertEquals(20, first)
        assertEquals(60, rows)
        val (f2, r2) = WowArtSources.heroCropRows(100, -1f, -1f)
        assertEquals(0 to 100, f2 to r2)
        val (f3, r3) = WowArtSources.heroCropRows(3, 0.2f, 0.2f)
        assertTrue(f3 >= 0 && r3 >= 1 && f3 + r3 <= 3)
    }

    @Test
    fun everyHeroSourceCutsItsWhiteEdgesButKeepsMostOfThePicture() {
        for (art in WowArtSources.HERO) {
            assertTrue(art.name, art.cropTop in 0f..WowArtSources.MAX_CROP)
            assertTrue(art.name, art.cropBottom in 0f..WowArtSources.MAX_CROP)
            val (first, rows) = WowArtSources.heroCropRows(957, art.cropTop, art.cropBottom)
            assertTrue(art.name, rows > 957 * 0.8)
            assertTrue(art.name, first + rows <= 957)
        }
        // Gemessen: masthead hat die weiße Blende unten (ab etwa 89 % der Höhe), die vier anderen oben (Zackenrand bis etwa 4,8 %)
        // und unten (ab etwa 95,5 %).
        val masthead = WowArtSources.HERO.first { it.name == "masthead" }
        assertEquals(0f, masthead.cropTop)
        assertTrue(masthead.cropBottom >= 0.11f)
        for (art in WowArtSources.HERO.filter { it.name != "masthead" }) {
            assertTrue(art.name, art.cropTop >= 0.048f)
            assertTrue(art.name, art.cropBottom >= 0.04f)
        }
    }

    // --- Bildspeicher und Laden ---

    private class FakeHttp(private val answers: Map<String, ArtResponse>, private val failing: Set<String> = emptySet()) : ArtHttp {
        val asked = mutableListOf<String>()

        override fun get(url: String): ArtResponse {
            asked += url
            if (url in failing) throw IOException("kein Netz")
            return answers[url] ?: ArtResponse(404, ByteArray(0))
        }
    }

    private fun makeLoader(http: ArtHttp, dir: File = tmp.newFolder()): Pair<WowArtLoader, ArtCache> {
        val cache = ArtCache(dir)
        return WowArtLoader(cache, http) to cache
    }

    @Test
    fun aDownloadIsStoredAndNotAskedTwice() {
        val url = "https://example.test/a.jpg"
        val http = FakeHttp(mapOf(url to ArtResponse(200, byteArrayOf(1, 2, 3))))
        val (loader, cache) = makeLoader(http)
        val first = loader.fetch("hero_a", url)
        assertTrue(first is ArtFetch.Ok)
        assertEquals(listOf<Byte>(1, 2, 3), (first as ArtFetch.Ok).file.readBytes().toList())
        assertNotNull(cache.lookup("hero_a"))
        val second = loader.fetch("hero_a", url)
        assertTrue(second is ArtFetch.Ok)
        assertEquals("zweiter Abruf kommt aus dem Speicher", 1, http.asked.size)
    }

    @Test
    fun aMissingPictureIsGoneAndNothingIsStored() {
        val url = "https://example.test/missing.jpg"
        val http = FakeHttp(emptyMap())
        val (loader, cache) = makeLoader(http)
        assertEquals(ArtFetch.Gone, loader.fetch("k", url))
        assertNull(cache.lookup("k"))
    }

    @Test
    fun networkTroubleAndServerErrorsAreOffline() {
        val bad = "https://example.test/bad.jpg"
        val broken = "https://example.test/500.jpg"
        val http = FakeHttp(mapOf(broken to ArtResponse(503, ByteArray(0))), failing = setOf(bad))
        val (loader, cache) = makeLoader(http)
        assertEquals(ArtFetch.Offline, loader.fetch("k1", bad))
        assertEquals(ArtFetch.Offline, loader.fetch("k2", broken))
        assertNull(cache.lookup("k1"))
        assertNull(cache.lookup("k2"))
    }

    @Test
    fun emptyOrTooBigAnswersAreDiscarded() {
        val empty = "https://example.test/empty.jpg"
        val big = "https://example.test/big.jpg"
        val http = FakeHttp(
            mapOf(
                empty to ArtResponse(200, ByteArray(0)),
                big to ArtResponse(200, ByteArray(ART_MAX_BYTES + 1)),
            ),
        )
        val (loader, cache) = makeLoader(http)
        assertEquals(ArtFetch.Gone, loader.fetch("e", empty))
        assertEquals(ArtFetch.Gone, loader.fetch("b", big))
        assertNull(cache.lookup("e"))
        assertNull(cache.lookup("b"))
    }

    @Test
    fun cacheKeysAreSafeFileNamesAndRemoveWorks() {
        val dir = tmp.newFolder()
        val cache = ArtCache(dir)
        val f = cache.store("../evil/key", byteArrayOf(9))
        assertNotNull(f)
        assertEquals(dir.canonicalPath, f!!.parentFile!!.canonicalPath)
        assertNull(cache.store("empty", ByteArray(0)))
        cache.remove("../evil/key")
        assertNull(cache.lookup("../evil/key"))
        cache.store("a", byteArrayOf(1))
        cache.store("b", byteArrayOf(1))
        cache.clear()
        assertFalse(cache.lookup("a") != null || cache.lookup("b") != null)
    }
}
