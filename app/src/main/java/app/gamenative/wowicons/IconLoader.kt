package app.gamenative.wowicons

import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IconResponse(val code: Int, val body: ByteArray)

/** The one network door of the icon code (tests replace it; the app uses OkHttp, see [WowIcons]). */
fun interface IconHttp {
    /** GET [url]. Throws [IOException] for network problems; any HTTP status is an answer. */
    @Throws(IOException::class)
    fun get(url: String): IconResponse
}

sealed class IconResult {
    data class Loaded(val file: File) : IconResult()

    /** Does not exist (permanent answer), do not ask again for a while. */
    object Missing : IconResult()

    /** Network trouble or a server error; try again later. */
    object Retry : IconResult()
}

/**
 * Gets one icon into the [cache]. Order: the file id at Blizzard's render CDN; then the icon name (own name from a
 * texture path, else asked at Wowhead via the spell or item id) at the render CDN and, as a second source, at Wowhead's
 * icon CDN. Permanent "not found" answers are remembered, network errors only cool down for a short while.
 */
class IconLoader(private val cache: IconCache, private val http: IconHttp) {
    /** True if the picture is on the disk already. */
    fun isCached(ref: IconRef): Boolean = cache.lookup(ref) != null

    fun load(ref: IconRef): IconResult {
        cache.lookup(ref)?.let { return IconResult.Loaded(it) }
        if (ref.isEmpty) return IconResult.Missing
        var retry = false

        val fileId = ref.fileId
        if (fileId != null) {
            val key = "f$fileId"
            if (!cache.isMissing(key) && !cache.isCoolingDown(key)) {
                when (val r = fetchPicture(key, IconUrls.render(fileId))) {
                    is Fetched.Ok -> return IconResult.Loaded(r.file)
                    Fetched.Gone -> cache.markMissing(key)
                    Fetched.Failed -> {
                        cache.markFailed(key)
                        retry = true
                    }
                }
            } else if (cache.isCoolingDown(key)) {
                retry = true
            }
        }
        if (retry) return IconResult.Retry

        val name = cache.nameOf(ref) ?: lookupName(ref)
        if (name == null) {
            return if (ref.nameKey?.let { cache.isCoolingDown(it) } == true) IconResult.Retry else IconResult.Missing
        }
        val key = "n$name"
        if (cache.isMissing(key)) return IconResult.Missing
        if (cache.isCoolingDown(key)) return IconResult.Retry
        for (url in listOf(IconUrls.render(name), IconUrls.zamimg(name))) {
            when (val r = fetchPicture(key, url)) {
                is Fetched.Ok -> {
                    // The same picture under the file id: next time no name lookup is needed.
                    if (fileId != null) runCatching { cache.store("f$fileId", r.file.readBytes()) }
                    return IconResult.Loaded(r.file)
                }
                Fetched.Gone -> Unit
                Fetched.Failed -> {
                    cache.markFailed(key)
                    return IconResult.Retry
                }
            }
        }
        cache.markMissing(key)
        return IconResult.Missing
    }

    /** Icon name of a spell or item from Wowhead's tooltip data; remembered in the name table. */
    private fun lookupName(ref: IconRef): String? {
        val nameKey = ref.nameKey ?: return null
        if (cache.isMissing(nameKey) || cache.isCoolingDown(nameKey)) return null
        val url = if (ref.spellId != null) IconUrls.wowheadSpell(ref.spellId) else IconUrls.wowheadItem(ref.itemId!!)
        val response = try {
            http.get(url)
        } catch (e: IOException) {
            cache.markFailed(nameKey)
            return null
        }
        return when {
            response.code == 200 -> {
                val icon = WowheadJson.iconName(String(response.body, Charsets.UTF_8))
                if (icon == null) cache.markMissing(nameKey) else cache.rememberName(nameKey, icon)
                icon
            }
            response.code in 400..499 -> {
                cache.markMissing(nameKey)
                null
            }
            else -> {
                cache.markFailed(nameKey)
                null
            }
        }
    }

    private sealed class Fetched {
        class Ok(val file: File) : Fetched()
        object Gone : Fetched()
        object Failed : Fetched()
    }

    private fun fetchPicture(key: String, url: String): Fetched {
        val response = try {
            http.get(url)
        } catch (e: IOException) {
            return Fetched.Failed
        }
        return when {
            response.code == 200 && looksLikeImage(response.body) -> {
                cache.store(key, response.body)
                Fetched.Ok(cache.imageFile(key))
            }
            response.code == 200 -> Fetched.Gone
            response.code in 400..499 && response.code != 429 && response.code != 408 -> Fetched.Gone
            else -> Fetched.Failed
        }
    }

    companion object {
        const val MAX_IMAGE_BYTES = 256 * 1024

        /** JPEG or PNG signature and a sane size (the CDNs also answer errors with a 200 page now and then). */
        fun looksLikeImage(body: ByteArray): Boolean {
            if (body.size < 16 || body.size > MAX_IMAGE_BYTES) return false
            val jpeg = body[0] == 0xFF.toByte() && body[1] == 0xD8.toByte()
            val png = body[0] == 0x89.toByte() && body[1] == 'P'.code.toByte() && body[2] == 'N'.code.toByte()
            return jpeg || png
        }
    }
}

/** What the pre-fetch did. */
data class PrefetchReport(val total: Int, val loaded: Int, val alreadyThere: Int, val missing: Int, val retryLater: Int)

/** Loads many icons in the background: at most [parallel] requests at once, with a short pause after each one. */
object IconPrefetch {
    /**
     * The wanted icons in the order they matter: bar slots, then the spellbook, then consumables, favourite mounts and
     * toys, then the other mounts and toys. Entries without any source are skipped; equal icons appear once.
     */
    fun plan(data: BridgeData): List<IconRef> {
        val ordered = ArrayList<BridgeEntry>()
        ordered += data.slots.entries.sortedBy { it.key }.map { it.value }
        ordered += data.spells
        ordered += data.items
        ordered += data.mounts.filter { it.favorite }
        ordered += data.toys.filter { it.favorite }
        ordered += data.mounts.filter { !it.favorite }
        ordered += data.toys.filter { !it.favorite }
        val seen = HashSet<String>()
        val result = ArrayList<IconRef>()
        for (entry in ordered) {
            if (!entry.hasIconSource) continue
            val ref = IconRef.of(entry)
            // Picture identity: the file id, else the name, else the spell/item id.
            val identity = ref.fileId?.let { "f$it" } ?: ref.name?.let { "n$it" } ?: ref.nameKey ?: continue
            if (seen.add(identity)) result.add(ref)
        }
        return result
    }

    /** Blocks until done or [shouldStop] says so. [pauseMs] is waited after each finished request in each worker. */
    fun run(
        refs: List<IconRef>,
        loader: IconLoader,
        parallel: Int = 4,
        pauseMs: Long = 60,
        shouldStop: () -> Boolean = { false },
        onLoaded: (IconRef, File) -> Unit = { _, _ -> },
    ): PrefetchReport {
        val loaded = AtomicInteger()
        val already = AtomicInteger()
        val missing = AtomicInteger()
        val retry = AtomicInteger()
        val failStreak = AtomicInteger()
        val pool = Executors.newFixedThreadPool(parallel.coerceIn(1, 8)) { r -> Thread(r, "wow-icons").apply { isDaemon = true } }
        try {
            val tasks = refs.map { ref ->
                Callable {
                    if (shouldStop() || failStreak.get() >= MAX_FAIL_STREAK) {
                        retry.incrementAndGet()
                        return@Callable
                    }
                    val hadIt = loader.isCached(ref)
                    when (val r = loader.load(ref)) {
                        is IconResult.Loaded -> {
                            failStreak.set(0)
                            if (hadIt) {
                                already.incrementAndGet()
                            } else {
                                loaded.incrementAndGet()
                                onLoaded(ref, r.file)
                                if (pauseMs > 0) Thread.sleep(pauseMs)
                            }
                        }
                        IconResult.Missing -> {
                            missing.incrementAndGet()
                            if (pauseMs > 0) Thread.sleep(pauseMs)
                        }
                        IconResult.Retry -> {
                            retry.incrementAndGet()
                            failStreak.incrementAndGet()
                        }
                    }
                }
            }
            pool.invokeAll(tasks)
        } finally {
            pool.shutdown()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
        return PrefetchReport(refs.size, loaded.get(), already.get(), missing.get(), retry.get())
    }

    /** After this many failed requests in a row the run gives up (no network); the rest is tried again at the next start. */
    const val MAX_FAIL_STREAK = 8
}
