package app.gamenative.wowicons

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Fake network: answers by URL; counts the requests. No real network is used. */
private class FakeHttp : IconHttp {
    val answers = ConcurrentHashMap<String, IconResponse>()
    val requests = ConcurrentHashMap<String, AtomicInteger>()
    val failing = ConcurrentHashMap.newKeySet<String>()
    var offline = false
    val total = AtomicInteger()

    override fun get(url: String): IconResponse {
        total.incrementAndGet()
        requests.computeIfAbsent(url) { AtomicInteger() }.incrementAndGet()
        if (offline || url in failing) throw IOException("offline")
        return answers[url] ?: IconResponse(404, ByteArray(0))
    }

    fun count(url: String) = requests[url]?.get() ?: 0

    fun picture(url: String, marker: Int = 1) {
        answers[url] = IconResponse(200, jpeg(marker))
    }

    fun json(url: String, text: String) {
        answers[url] = IconResponse(200, text.toByteArray())
    }

    companion object {
        fun jpeg(marker: Int): ByteArray = ByteArray(64) { i -> if (i == 0) 0xFF.toByte() else if (i == 1) 0xD8.toByte() else marker.toByte() }
    }
}

class IconLoaderTest {
    private lateinit var dir: File
    private lateinit var cache: IconCache
    private val http = FakeHttp()
    private lateinit var loader: IconLoader
    private var now = 1_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("wowicons-cache").toFile()
        cache = IconCache(dir) { now }
        loader = IconLoader(cache, http)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun loadsByFileIdFromTheBlizzardCdnAndCaches() {
        http.picture(IconUrls.render(135812L))
        val first = loader.load(IconRef(fileId = 135812))
        assertTrue(first is IconResult.Loaded)
        assertTrue((first as IconResult.Loaded).file.isFile)
        val second = loader.load(IconRef(fileId = 135812))
        assertTrue(second is IconResult.Loaded)
        assertEquals(1, http.count(IconUrls.render(135812L)))
        assertEquals(1, http.total.get())
    }

    @Test
    fun usesWowheadNameWhenThereIsNoFileId() {
        http.json(IconUrls.wowheadSpell(133), "{\"name\":\"Fireball\",\"icon\":\"spell_fire_flamebolt\"}")
        http.picture(IconUrls.render("spell_fire_flamebolt"))
        val result = loader.load(IconRef(spellId = 133))
        assertTrue(result is IconResult.Loaded)
        // The name is kept: a second look needs no network at all.
        val requests = http.total.get()
        assertTrue(loader.load(IconRef(spellId = 133)) is IconResult.Loaded)
        assertEquals(requests, http.total.get())
        assertEquals("spell_fire_flamebolt", cache.nameOf(IconRef(spellId = 133)))
    }

    @Test
    fun nameTableSurvivesANewCacheObject() {
        http.json(IconUrls.wowheadItem(6948), "{\"name\":\"Hearthstone\",\"icon\":\"inv_misc_rune_01\"}")
        http.picture(IconUrls.render("inv_misc_rune_01"))
        loader.load(IconRef(itemId = 6948))
        val again = IconCache(dir) { now }
        assertEquals("inv_misc_rune_01", again.nameOf(IconRef(itemId = 6948)))
        assertTrue(again.lookup(IconRef(itemId = 6948))!!.isFile)
    }

    @Test
    fun fallsBackToWowheadCdnWhenBlizzardLacksTheName() {
        http.json(IconUrls.wowheadSpell(5), "{\"icon\":\"spell_x\"}")
        http.picture(IconUrls.zamimg("spell_x"))
        assertTrue(loader.load(IconRef(spellId = 5)) is IconResult.Loaded)
    }

    @Test
    fun fileIdNotFoundFallsBackToNameAndStoresUnderTheId() {
        http.json(IconUrls.wowheadSpell(7), "{\"icon\":\"spell_y\"}")
        http.picture(IconUrls.render("spell_y"), marker = 9)
        // 404 for the file id (the fake answers 404 for everything unknown)
        val result = loader.load(IconRef(fileId = 777, spellId = 7))
        assertTrue(result is IconResult.Loaded)
        assertTrue(cache.imageFile("f777").isFile)
        assertTrue(cache.isMissing("f777"))
    }

    @Test
    fun permanentMissIsRemembered() {
        val ref = IconRef(fileId = 1)
        assertEquals(IconResult.Missing, loader.load(ref))
        val requests = http.total.get()
        assertEquals(IconResult.Missing, loader.load(ref))
        assertEquals(requests, http.total.get())
    }

    @Test
    fun missIsForgottenAfterAWeek() {
        val ref = IconRef(fileId = 1)
        loader.load(ref)
        val requests = http.total.get()
        now += IconCache.MISSING_TTL_MS + 1
        http.picture(IconUrls.render(1L))
        assertTrue(loader.load(ref) is IconResult.Loaded)
        assertEquals(requests + 1, http.total.get())
    }

    @Test
    fun networkErrorMeansRetryAndCoolsDown() {
        http.offline = true
        val ref = IconRef(fileId = 2)
        assertEquals(IconResult.Retry, loader.load(ref))
        val requests = http.total.get()
        // within the cool-down nothing is asked again
        assertEquals(IconResult.Retry, loader.load(ref))
        assertEquals(requests, http.total.get())
        assertFalse(cache.isMissing("f2"))
        // later, with network, it works
        now += IconCache.COOLDOWN_MS + 1
        http.offline = false
        http.picture(IconUrls.render(2L))
        assertTrue(loader.load(ref) is IconResult.Loaded)
    }

    @Test
    fun serverErrorIsTransient() {
        http.answers[IconUrls.render(3L)] = IconResponse(503, ByteArray(0))
        assertEquals(IconResult.Retry, loader.load(IconRef(fileId = 3)))
        assertFalse(cache.isMissing("f3"))
    }

    @Test
    fun htmlErrorPageWith200IsNotStoredAsPicture() {
        http.json(IconUrls.render(4L), "<html>oops</html>")
        assertEquals(IconResult.Missing, loader.load(IconRef(fileId = 4)))
        assertFalse(cache.imageFile("f4").exists())
    }

    @Test
    fun emptyRefIsMissingWithoutNetwork() {
        assertEquals(IconResult.Missing, loader.load(IconRef()))
        assertEquals(0, http.total.get())
    }

    @Test
    fun wowheadErrorAnswerMeansMissing() {
        http.json(IconUrls.wowheadSpell(99999999), "{\"error\":\"ID is out of range\"}")
        assertEquals(IconResult.Missing, loader.load(IconRef(spellId = 99999999)))
        assertTrue(cache.isMissing("spell:99999999"))
    }

    @Test
    fun cacheStatsAndClear() {
        http.picture(IconUrls.render(10L))
        http.picture(IconUrls.render(11L))
        loader.load(IconRef(fileId = 10))
        loader.load(IconRef(fileId = 11))
        val (count, bytes) = cache.stats()
        assertEquals(2, count)
        assertEquals(128L, bytes)
        cache.clear()
        assertEquals(0, cache.stats().first)
        assertFalse(loader.isCached(IconRef(fileId = 10)))
    }

    @Test
    fun imageSignatureCheck() {
        assertTrue(IconLoader.looksLikeImage(FakeHttp.jpeg(1)))
        assertFalse(IconLoader.looksLikeImage(ByteArray(64)))
        assertFalse(IconLoader.looksLikeImage(ByteArray(4)))
        assertFalse(IconLoader.looksLikeImage(ByteArray(IconLoader.MAX_IMAGE_BYTES + 1) { 0xFF.toByte() }))
    }

    @Test
    fun hostileNamesCannotLeaveTheCacheFolder() {
        val file = cache.imageFile("n../../evil")
        assertEquals(File(dir, "img").canonicalPath, file.parentFile.canonicalPath)
    }
}

class IconPrefetchTest {
    private lateinit var dir: File
    private lateinit var cache: IconCache
    private val http = FakeHttp()
    private lateinit var loader: IconLoader

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("wowicons-prefetch").toFile()
        cache = IconCache(dir)
        loader = IconLoader(cache, http)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun entry(file: Long? = null, spell: Long? = null, item: Long? = null, fav: Boolean = false, name: String? = null) =
        BridgeEntry(spellId = spell, itemId = item, iconFileId = file, iconName = name, favorite = fav)

    private fun data(
        slots: Map<Int, BridgeEntry> = emptyMap(),
        spells: List<BridgeEntry> = emptyList(),
        mounts: List<BridgeEntry> = emptyList(),
        toys: List<BridgeEntry> = emptyList(),
        items: List<BridgeEntry> = emptyList(),
    ) = BridgeData(0, null, null, null, null, slots, spells, mounts, toys, items)

    @Test
    fun planOrdersSlotsSpellsConsumablesThenFavouritesThenRest() {
        val plan = IconPrefetch.plan(
            data(
                slots = mapOf(5 to entry(file = 5), 1 to entry(file = 1)),
                spells = listOf(entry(file = 20), entry(file = 21)),
                mounts = listOf(entry(file = 40), entry(file = 41, fav = true)),
                toys = listOf(entry(file = 50), entry(file = 51, fav = true)),
                items = listOf(entry(file = 30)),
            ),
        )
        assertEquals(listOf<Long?>(1, 5, 20, 21, 30, 41, 51, 40, 50), plan.map { it.fileId })
    }

    @Test
    fun planRemovesDuplicatesAndEntriesWithoutSource() {
        val plan = IconPrefetch.plan(
            data(
                slots = mapOf(1 to entry(file = 7)),
                spells = listOf(entry(file = 7), entry(), entry(spell = 9), entry(spell = 9), entry(name = "spell_a"), entry(name = "spell_a")),
            ),
        )
        assertEquals(3, plan.size)
        assertEquals(7L, plan[0].fileId)
        assertEquals(9L, plan[1].spellId)
        assertEquals("spell_a", plan[2].name)
    }

    @Test
    fun runLoadsEverythingOnceAndReportsCounts() {
        for (id in 100L..119L) http.picture(IconUrls.render(id))
        val refs = (100L..121L).map { IconRef(fileId = it) } // 120 and 121 do not exist
        val seen = ConcurrentHashMap.newKeySet<Long>()
        val report = IconPrefetch.run(refs, loader, parallel = 4, pauseMs = 0, onLoaded = { ref, _ -> seen.add(ref.fileId!!) })
        assertEquals(22, report.total)
        assertEquals(20, report.loaded)
        assertEquals(2, report.missing)
        assertEquals(0, report.retryLater)
        assertEquals(20, seen.size)
        // second run: everything is cached or known missing, no new requests
        val requests = http.total.get()
        val second = IconPrefetch.run(refs, loader, pauseMs = 0)
        assertEquals(requests, http.total.get())
        assertEquals(20, second.alreadyThere)
        assertEquals(2, second.missing)
    }

    @Test
    fun offlineRunGivesUpEarlyAndKeepsTheRestForLater() {
        http.offline = true
        val refs = (1L..200L).map { IconRef(fileId = it) }
        val report = IconPrefetch.run(refs, loader, parallel = 1, pauseMs = 0)
        assertEquals(200, report.total)
        assertEquals(0, report.loaded)
        assertEquals(200, report.retryLater)
        assertTrue("stopped after the fail streak, was ${http.total.get()}", http.total.get() <= IconPrefetch.MAX_FAIL_STREAK + 4)
    }

    @Test
    fun stopFlagEndsTheRun() {
        for (id in 1L..50L) http.picture(IconUrls.render(id))
        val report = IconPrefetch.run((1L..50L).map { IconRef(fileId = it) }, loader, parallel = 1, pauseMs = 0, shouldStop = { true })
        assertEquals(0, report.loaded)
        assertEquals(0, http.total.get())
    }

    @Test
    fun neverMoreThanFourRequestsAtOnce() {
        val running = AtomicInteger()
        val peak = AtomicInteger()
        val slowHttp = IconHttp { url ->
            val now = running.incrementAndGet()
            peak.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            Thread.sleep(15)
            running.decrementAndGet()
            IconResponse(200, FakeHttp.jpeg(1))
        }
        val slowLoader = IconLoader(cache, slowHttp)
        IconPrefetch.run((1L..24L).map { IconRef(fileId = it) }, slowLoader, parallel = 4, pauseMs = 0)
        assertTrue("peak ${peak.get()}", peak.get() in 1..4)
    }
}
