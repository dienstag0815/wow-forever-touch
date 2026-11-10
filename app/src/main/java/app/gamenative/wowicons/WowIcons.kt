package app.gamenative.wowicons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.gamenative.PluviaApp
import app.gamenative.utils.Net
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Request
import timber.log.Timber

/**
 * Real icons for buttons, ring segments and wheel fields that store a WoW action bar slot (`ACTIONBUTTONn`,
 * `MULTIACTIONBARnBUTTONm`).
 *
 * Data flow: the addon TouchForever (installed into the game folder at the start) writes the slots, the spellbook,
 * mounts, toys and consumables into its SavedVariables file when WoW reloads or quits; this object reads that file (again
 * whenever it changed), downloads all icons it lists once into the app's private folder ([IconCache]) in the background,
 * and hands out small bitmaps to the drawing code. Without data or picture every call answers null and the old symbol,
 * emoji or text is drawn.
 */
object WowIcons {
    private const val PREFS = "wow_forever"
    private const val KEY_ENABLED = "icons_from_wow"
    private const val CHECK_INTERVAL_MS = 5_000L
    private const val BITMAP_CACHE_SIZE = 160
    private const val RETRY_SLOT_MS = 30_000L

    private class State(val cache: IconCache, val loader: IconLoader, val gameRoot: File, val backupDir: File)

    @Volatile private var state: State? = null
    @Volatile private var data: BridgeData? = null
    @Volatile private var slotFiles: Map<Int, File> = emptyMap()
    @Volatile private var lastCheck = 0L
    @Volatile private var loadedStamp = ""

    private val bitmaps = LruCache<String, Bitmap>(BITMAP_CACHE_SIZE)
    private val decoding = ConcurrentHashMap.newKeySet<String>()
    private val prefetching = AtomicBoolean(false)
    // Quick jobs (read the addon file, decode a picture) and slow ones (downloads) run on separate threads.
    private val worker by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "wow-icons-main").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    }
    private val network by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "wow-icons-net").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    }

    private val http: IconHttp by lazy {
        val client = Net.http.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        IconHttp { url ->
            val request = Request.Builder().url(url).header("User-Agent", IconUrls.USER_AGENT).build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body
                    val length = body?.contentLength() ?: 0L
                    IconResponse(response.code, if (body == null || length > 512 * 1024) ByteArray(0) else body.bytes())
                }
            } catch (e: IOException) {
                throw e
            } catch (e: RuntimeException) {
                throw IOException(e)
            }
        }
    }

    // --- Setting -----------------------------------------------------------------------------------------------

    /** "Echte Symbole aus WoW", on by default. */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) stop()
    }

    private fun cacheDir(context: Context) = File(context.filesDir, "wowicons")

    /** (number of pictures, bytes) on the device, for the settings line. */
    fun cacheStats(context: Context): Pair<Int, Long> = IconCache(cacheDir(context)).stats()

    fun clearCache(context: Context) {
        IconCache(cacheDir(context)).clear()
        bitmaps.evictAll()
        slotFiles = emptyMap()
        menuFiles.clear()
        spellFiles.clear()
        loadedStamp = ""
    }

    // --- Start (called while the game is prepared; never throws) -------------------------------------------------

    /**
     * Reads the addon's data and starts the icon download in the background (if the setting is on). The addon itself is
     * installed by [AddonSetup] before the game starts, independent of the setting. Never fails the game start.
     */
    fun start(context: Context, gameRoot: File) {
        try {
            if (!isEnabled(context)) {
                stop()
                return
            }
            val app = context.applicationContext
            val cache = IconCache(cacheDir(app))
            state = State(cache, IconLoader(cache, http), gameRoot, File(app.filesDir, "wowicons_last"))
            lastCheck = 0L
            loadedStamp = ""
            worker.execute { refresh() }
            val started = state
            if (started != null) network.execute { prefetchMenus(started) }
        } catch (e: Throwable) {
            Timber.w(e, "WowIcons: start failed")
        }
    }

    private fun stop() {
        state = null
        data = null
        slotFiles = emptyMap()
        menuFiles.clear()
        spellFiles.clear()
        bitmaps.evictAll()
    }

    // --- Reading and downloading (background) --------------------------------------------------------------------

    private fun refresh() {
        val st = state ?: return
        try {
            val file = BridgeLocator.find(st.gameRoot)
            val stamp = if (file == null) "none" else "${file.path}|${file.lastModified()}|${file.length()}"
            if (stamp == loadedStamp) return
            val text = file?.let { try { it.readText(Charsets.UTF_8) } catch (e: Exception) { null } }
            val fromFile = text?.let { BridgeParser.parse(it) }
            // The file written by WoW (only at /reload and logout) or, when it is missing, empty or older, the last good copy
            // of the app (BridgeBackup): icons are there right at the start.
            val parsed = BridgeBackup.choose(fromFile, BridgeBackup.load(st.backupDir)) ?: return
            if (parsed === fromFile && text != null) BridgeBackup.save(st.backupDir, parsed, text)
            else if (parsed !== fromFile) Timber.i("WowIcons: using the saved copy of ${parsed.character} (game file missing, empty or older)")
            loadedStamp = stamp
            data = parsed
            requested.clear()
            Timber.i("WowIcons: read ${parsed.slots.size} slots, ${parsed.spells.size} spells, ${parsed.mounts.size} mounts, " +
                "${parsed.toys.size} toys, ${parsed.items.size} items")
            rebuildSlots(st)
            network.execute { prefetch(st, parsed) }
        } catch (e: Throwable) {
            Timber.w(e, "WowIcons: refresh failed")
        }
    }

    private fun rebuildSlots(st: State) {
        val current = data ?: return
        val map = HashMap<Int, File>()
        for ((slot, entry) in current.slots) {
            if (!entry.hasIconSource) continue
            st.cache.lookup(IconRef.of(entry))?.let { map[slot] = it }
        }
        slotFiles = map
        PluviaApp.inputControlsView?.postInvalidate()
    }

    private fun prefetch(st: State, parsed: BridgeData) {
        if (!prefetching.compareAndSet(false, true)) return
        try {
            val refs = IconPrefetch.plan(parsed).filter { !st.loader.isCached(it) }
            if (refs.isEmpty()) return
            var sinceInvalidate = 0
            val report = IconPrefetch.run(
                refs, st.loader,
                shouldStop = { state !== st },
                onLoaded = { _, _ ->
                    // Redraw now and then while the pictures arrive (slot pictures come first).
                    if (++sinceInvalidate % 8 == 1) rebuildSlots(st)
                },
            )
            rebuildSlots(st)
            Timber.i("WowIcons: prefetch $report")
        } finally {
            prefetching.set(false)
        }
    }

    // --- Drawing side (UI thread, never blocks) ------------------------------------------------------------------

    /**
     * Small square bitmap of the icon in the action bar slot [wowAction] stands for, or null (setting off, no addon data,
     * not downloaded yet, no such slot). Also nudges a re-read of the addon file (at most every few seconds).
     */
    @JvmStatic
    fun bitmapFor(wowAction: String?): Bitmap? {
        if (wowAction == null) return null
        val st = state ?: return null
        MenuIcons.nameFor(wowAction)?.let { return menuBitmap(st, it) }
        val slot = ActionSlotMap.slotFor(wowAction) ?: return null
        maybeRefresh()
        val file = slotFiles[slot]
        if (file == null) {
            requestSlot(st, slot)
            return null
        }
        return decoded(file)
    }

    /** Bitmap of [file] from the memory cache; decodes it in the background on the first call (null until then). */
    private fun decoded(file: File): Bitmap? {
        val path = file.path
        bitmaps.get(path)?.let { return it }
        if (decoding.add(path)) {
            worker.execute {
                try {
                    BitmapFactory.decodeFile(path)?.let { bitmaps.put(path, it) }
                    PluviaApp.inputControlsView?.postInvalidate()
                } finally {
                    decoding.remove(path)
                }
            }
        }
        return null
    }

    /** What the addon wrote last (null without addon data or with the setting off); used by the editor templates. */
    @JvmStatic
    fun bridgeData(): BridgeData? = if (state == null) null else data

    // --- Game menu icons (map, talents, bags ...), by name, independent of the addon data ---------------------------

    private val menuFiles = ConcurrentHashMap<String, File>()
    private val menuRequested = ConcurrentHashMap<String, Long>()

    private fun menuBitmap(st: State, name: String): Bitmap? {
        var file = menuFiles[name]
        if (file == null) {
            file = st.cache.lookup(IconRef(name = name))
            if (file == null) {
                requestMenu(st, name)
                return null
            }
            menuFiles[name] = file
        }
        return decoded(file)
    }

    private fun requestMenu(st: State, name: String) {
        val now = System.currentTimeMillis()
        val previous = menuRequested[name]
        if (previous != null && now - previous < RETRY_SLOT_MS) return
        menuRequested[name] = now
        network.execute {
            if (state === st && st.loader.load(IconRef(name = name)) is IconResult.Loaded) {
                PluviaApp.inputControlsView?.postInvalidate()
            }
        }
    }

    // All menu icons once at the start (about 14 small pictures; what is cached already is skipped).
    private fun prefetchMenus(st: State) {
        try {
            val refs = (MenuIcons.refs() + GlyphIcons.refs()).distinct().filter { !st.loader.isCached(it) }
            if (refs.isEmpty()) return
            val report = IconPrefetch.run(refs, st.loader, shouldStop = { state !== st })
            PluviaApp.inputControlsView?.postInvalidate()
            Timber.i("WowIcons: menu icons $report")
        } catch (e: Throwable) {
            Timber.w(e, "WowIcons: menu icons failed")
        }
    }

    /**
     * Icon for a fixed entry of the long-press menu (attack, loot, skinning, sell) by the entry's glyph name (see [GlyphIcons]), or null
     * (setting off, not downloaded yet, no icon for that glyph): the caller then draws its own symbol.
     */
    @JvmStatic
    fun bitmapForGlyph(glyph: String?): Bitmap? {
        val name = GlyphIcons.nameFor(glyph) ?: return null
        val st = state ?: return null
        return menuBitmap(st, name)
    }

    // --- Spell icons by spell id (the spells of the long-press menu, independent of the action bar) ------------------

    private val spellFiles = ConcurrentHashMap<Int, File>()
    private val spellRequested = ConcurrentHashMap<Int, Long>()

    /**
     * Icon of the spell [spellId]: the entry of the spellbook list in the addon data (its file id) when there is one, else looked up
     * by the spell id at Wowhead. Loaded at run time from the game's icon servers like every other icon, nothing is shipped in the app.
     * Null while it is not downloaded or decoded yet (the menu shows the spell's name then).
     */
    @JvmStatic
    fun bitmapForSpell(spellId: Int): Bitmap? {
        if (spellId <= 0) return null
        val st = state ?: return null
        spellFiles[spellId]?.let { return decoded(it) }
        maybeRefresh()
        val id = spellId.toLong()
        val ref = data?.spells?.firstOrNull { it.spellId == id }?.let { IconRef.of(it) } ?: IconRef(spellId = id)
        val file = st.cache.lookup(ref)
        if (file != null) {
            spellFiles[spellId] = file
            return decoded(file)
        }
        requestSpell(st, spellId, ref)
        return null
    }

    private fun requestSpell(st: State, spellId: Int, ref: IconRef) {
        val now = System.currentTimeMillis()
        val previous = spellRequested[spellId]
        if (previous != null && now - previous < RETRY_SLOT_MS) return
        spellRequested[spellId] = now
        network.execute {
            if (state === st && st.loader.load(ref) is IconResult.Loaded) PluviaApp.inputControlsView?.postInvalidate()
        }
    }

    /** Name of the thing on the slot (for a caption), null if unknown. */
    @JvmStatic
    fun nameFor(wowAction: String?): String? {
        if (state == null) return null
        val slot = ActionSlotMap.slotFor(wowAction) ?: return null
        return data?.slots?.get(slot)?.name
    }

    private fun maybeRefresh() {
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now
        worker.execute { refresh() }
    }

    // A slot whose picture is not on the disk although the data names it (new on the bar, not pre-fetched): load it now.
    private val requested = ConcurrentHashMap<Int, Long>()

    private fun requestSlot(st: State, slot: Int) {
        val entry = data?.slots?.get(slot) ?: return
        if (!entry.hasIconSource) return
        val now = System.currentTimeMillis()
        // Asked again only after a while (the network may have been down).
        val previous = requested[slot]
        if (previous != null && now - previous < RETRY_SLOT_MS) return
        requested[slot] = now
        network.execute {
            if (state === st && st.loader.load(IconRef.of(entry)) is IconResult.Loaded) rebuildSlots(st)
        }
    }
}
