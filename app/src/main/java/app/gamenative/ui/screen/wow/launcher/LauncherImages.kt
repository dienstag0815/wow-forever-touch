package app.gamenative.ui.screen.wow.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.gamenative.ui.screen.wow.WowClientDownloader
import app.gamenative.utils.Net
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

/*
 * Bilder des Launchers laden (Android): eigene Screenshots des Spiels, WoW-Artwork aus dem Netz (einmal in den App-Speicher), mitgelieferte
 * freie Bilder aus den Assets. Alles Laden und Dekodieren läuft auf einem Hintergrund-Thread, verkleinert (inSampleSize); die Oberfläche
 * zeigt bis dahin die gezeichnete Kunst. Welche Quelle zuerst gilt, steht in LauncherImageLogic.kt (heroSourceOrder, tileSourceOrder).
 */

/** Die beiden Schalter "Eigene Screenshots im Launcher zeigen" und "Echte WoW-Bilder im Launcher" (Standard an). */
internal object LauncherArtSettings {
    private const val PREFS = "wow_forever_launcher_art"
    private const val KEY_SCREENSHOTS = "screenshots"
    private const val KEY_WOW_ART = "wow_art"

    private val screenshots = MutableStateFlow(true)
    private val wowArt = MutableStateFlow(true)

    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val p = prefs(context)
            screenshots.value = p.getBoolean(KEY_SCREENSHOTS, true)
            wowArt.value = p.getBoolean(KEY_WOW_ART, true)
            loaded = true
        }
    }

    /** Der Zustand als Fluss: die Startseite bleibt unter der Einstellungsseite sichtbar und folgt dem Schalter sofort. */
    fun screenshotsFlow(context: Context): StateFlow<Boolean> {
        ensureLoaded(context)
        return screenshots
    }

    fun wowArtFlow(context: Context): StateFlow<Boolean> {
        ensureLoaded(context)
        return wowArt
    }

    fun setScreenshots(context: Context, on: Boolean) {
        ensureLoaded(context)
        prefs(context).edit().putBoolean(KEY_SCREENSHOTS, on).apply()
        screenshots.value = on
    }

    fun setWowArt(context: Context, on: Boolean) {
        ensureLoaded(context)
        prefs(context).edit().putBoolean(KEY_WOW_ART, on).apply()
        wowArt.value = on
    }
}

/** Was geladen ist: das Hero-Bild und die Kachelbilder (je nach Quelle). */
internal data class LoadedArt(val hero: ImageBitmap?, val tiles: Map<LauncherTile, ImageBitmap>, val heroFocus: ArtFocus = ArtFocus())

/** Ein geladenes Hero-Bild samt Fokuspunkt (Mitte, wenn die Quelle keinen kennt). */
private class HeroPick(val image: ImageBitmap, val focus: ArtFocus = ArtFocus())

/** Ergebnis des schnellen Durchgangs ohne Netz. */
private class LocalArt(val art: LoadedArt, val heroSource: ArtSource, val wowTiles: Set<LauncherTile>)

private class ArtRequest(val context: Context, val gamePath: String, val screenshotsOn: Boolean, val wowArtOn: Boolean)

private const val HERO_REQ_W = 1280
private const val HERO_REQ_H = 720
private const val TILE_REQ_W = 640
private const val TILE_REQ_H = 420
private const val OFFLINE_PAUSE_MS = 120_000L

/** Prozessweite Dinge des Bildladens: Auswahl-Startwert, Bildspeicher, Netz. */
private object ArtRuntime {
    /** Je Start der App ein anderer Wert: bestimmt, welches der fünf Hero-Bilder vorn steht. */
    val seed: Long = System.currentTimeMillis()

    @Volatile var offlineUntil = 0L
    private var cacheInstance: ArtCache? = null

    fun cache(context: Context): ArtCache = synchronized(this) {
        cacheInstance ?: ArtCache(File(context.applicationContext.filesDir, "launcher_art")).also { cacheInstance = it }
    }

    fun isOffline(): Boolean = System.currentTimeMillis() < offlineUntil

    fun loader(context: Context): WowArtLoader = WowArtLoader(cache(context), http)

    private val http: ArtHttp by lazy {
        val client = Net.http.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        ArtHttp { url ->
            val request = Request.Builder().url(url).header("User-Agent", "WoWForeverLauncher").build()
            try {
                client.newCall(request).execute().use { response ->
                    val source = response.body?.source()
                    val bytes = when {
                        source == null -> ByteArray(0)
                        source.request(ART_MAX_BYTES + 1L) -> ByteArray(0)
                        else -> source.readByteArray()
                    }
                    ArtResponse(response.code, bytes)
                }
            } catch (e: IOException) {
                throw e
            } catch (e: RuntimeException) {
                throw IOException(e)
            }
        }
    }
}

// --- Dekodieren (verkleinert) ---

private fun decodeFile(file: File, reqWidth: Int, reqHeight: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight) }
        BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/**
 * Hero-Bild aus dem Bildspeicher, verkleinert und ohne die weißen Ränder der Quelle (obere und untere Zeilen nach [WowArtSources.HeroArt]).
 * Der Zuschnitt gilt beim Anzeigen: auch schon früher gespeicherte, ungeschnittene Bilder in `launcher_art` werden so beschnitten.
 */
private fun decodeHeroFile(file: File, art: WowArtSources.HeroArt): ImageBitmap? {
    val bitmap = decodeFile(file, HERO_REQ_W, HERO_REQ_H) ?: return null
    return cropHero(bitmap, art)
}

private fun cropHero(image: ImageBitmap, art: WowArtSources.HeroArt): ImageBitmap {
    if (art.cropTop <= 0f && art.cropBottom <= 0f) return image
    val source = image.asAndroidBitmap()
    val (firstRow, rows) = WowArtSources.heroCropRows(source.height, art.cropTop, art.cropBottom)
    if (firstRow == 0 && rows >= source.height) return image
    return try {
        Bitmap.createBitmap(source, 0, firstRow, source.width, rows).asImageBitmap()
    } catch (e: Exception) {
        image
    } catch (e: OutOfMemoryError) {
        image
    }
}

private fun decodeAsset(context: Context, path: String, reqWidth: Int, reqHeight: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight) }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    }
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

private fun bundledNames(context: Context): List<String> =
    runCatching { context.applicationContext.assets.list(BUNDLED_ART_DIR)?.toList() }.getOrNull().orEmpty()

private val FLAVOR_DIRS = listOf(WowClientDownloader.FLAVOR_DIR, WowClientDownloader.LEGACY_FLAVOR_DIR)

// --- Durchgang 1: ohne Netz (Screenshot, Bildspeicher, Assets) ---

/** Das erste Hero-Bild des Bildspeichers in der Reihenfolge des Startwerts; ein unlesbares wird gelöscht. */
private fun cachedWowHero(cache: ArtCache): HeroPick? {
    for (art in rotatedBy(WowArtSources.HERO, ArtRuntime.seed)) {
        val key = WowArtSources.heroKey(art)
        val file = cache.lookup(key) ?: continue
        val image = decodeHeroFile(file, art)
        if (image != null) return HeroPick(image, art.focus())
        cache.remove(key)
    }
    return null
}

private fun loadLocalArt(req: ArtRequest): LocalArt {
    val cache = ArtRuntime.cache(req.context)
    val names = bundledNames(req.context)
    val shot = if (req.screenshotsOn) newestScreenshot(req.gamePath, FLAVOR_DIRS) else null
    val bundledHero = pickBundledHero(names, ArtRuntime.seed)

    var hero: HeroPick? = null
    var heroSource = ArtSource.Drawn
    for (source in heroSourceOrder(req.screenshotsOn, shot != null, req.wowArtOn, bundledHero != null)) {
        val pick: HeroPick? = when (source) {
            ArtSource.Screenshot -> shot?.let { file -> decodeFile(file, HERO_REQ_W, HERO_REQ_H)?.let { HeroPick(it) } }
            ArtSource.WowArt -> cachedWowHero(cache)
            ArtSource.Bundled -> bundledHero?.let { name ->
                decodeAsset(req.context, "$BUNDLED_ART_DIR/$name", HERO_REQ_W, HERO_REQ_H)?.let { HeroPick(it, bundledHeroFocus(name)) }
            }
            ArtSource.Drawn -> null
        }
        if (pick != null) {
            hero = pick
            heroSource = source
            break
        }
    }

    val tiles = HashMap<LauncherTile, ImageBitmap>()
    val wowTiles = HashSet<LauncherTile>()
    for (tile in LauncherTile.entries) {
        val bundledTile = bundledTileName(tile, names)
        for (source in tileSourceOrder(req.wowArtOn, bundledTile != null)) {
            val image = when (source) {
                ArtSource.WowArt -> cache.lookup(WowArtSources.tileKey(tile))?.let { file ->
                    decodeFile(file, TILE_REQ_W, TILE_REQ_H) ?: run {
                        cache.remove(WowArtSources.tileKey(tile))
                        null
                    }
                }
                ArtSource.Bundled -> bundledTile?.let { decodeAsset(req.context, "$BUNDLED_ART_DIR/$it", TILE_REQ_W, TILE_REQ_H) }
                else -> null
            }
            if (image != null) {
                tiles[tile] = image
                if (source == ArtSource.WowArt) wowTiles += tile
                break
            }
        }
    }
    return LocalArt(LoadedArt(hero?.image, tiles, hero?.focus ?: ArtFocus()), heroSource, wowTiles)
}

// --- Durchgang 2: aus dem Netz holen (nur mit Schalter "Echte WoW-Bilder") ---

/**
 * Lädt das Hero-Bild dieses Starts in den Bildspeicher, falls es dort fehlt. Zurück kommt ein Bild nur, wenn der Hero sonst keins hätte
 * ([needImage]); sonst wird es still für den nächsten Start gespeichert. Ein 404 versucht das nächste Bild, ein Netzfehler hört auf.
 */
private fun fetchHero(req: ArtRequest, needImage: Boolean): HeroPick? {
    val cache = ArtRuntime.cache(req.context)
    val loader = ArtRuntime.loader(req.context)
    for (art in rotatedBy(WowArtSources.HERO, ArtRuntime.seed)) {
        if (ArtRuntime.isOffline()) return null
        val key = WowArtSources.heroKey(art)
        if (cache.lookup(key) != null && !needImage) return null
        when (val result = loader.fetch(key, WowArtSources.heroUrl(art))) {
            is ArtFetch.Ok -> {
                if (!needImage) return null
                val image = decodeHeroFile(result.file, art)
                if (image != null) return HeroPick(image, art.focus())
                cache.remove(key)
            }
            ArtFetch.Gone -> Unit
            ArtFetch.Offline -> {
                ArtRuntime.offlineUntil = System.currentTimeMillis() + OFFLINE_PAUSE_MS
                return null
            }
        }
    }
    return null
}

/** Die Zonenkarte einer Kachel aus dem Netz; null bei Fehler (still). */
private fun fetchTile(req: ArtRequest, tile: LauncherTile): ImageBitmap? {
    if (ArtRuntime.isOffline()) return null
    val key = WowArtSources.tileKey(tile)
    return when (val result = ArtRuntime.loader(req.context).fetch(key, WowArtSources.tileUrl(tile))) {
        is ArtFetch.Ok -> decodeFile(result.file, TILE_REQ_W, TILE_REQ_H) ?: run {
            ArtRuntime.cache(req.context).remove(key)
            null
        }
        ArtFetch.Gone -> null
        ArtFetch.Offline -> {
            ArtRuntime.offlineUntil = System.currentTimeMillis() + OFFLINE_PAUSE_MS
            null
        }
    }
}

/**
 * Die Kunst der Startseite. Erst kommt sofort, was ohne Netz da ist (Screenshot, Bildspeicher, Assets), danach füllt sich nach und nach,
 * was aus dem Netz geholt wird. [refreshKey] lädt neu (z. B. nach dem Spiel, wenn neue Screenshots da sind). Ohne Bild: gezeichnete Kunst.
 */
@Composable
internal fun rememberLauncherArt(gamePath: String, refreshKey: Any?): LauncherArt {
    val context = LocalContext.current
    val screenshotsOn by LauncherArtSettings.screenshotsFlow(context).collectAsState()
    val wowArtOn by LauncherArtSettings.wowArtFlow(context).collectAsState()
    val loaded by produceState<LoadedArt?>(null, gamePath, screenshotsOn, wowArtOn, refreshKey) {
        val req = ArtRequest(context.applicationContext, gamePath, screenshotsOn, wowArtOn)
        val local = withContext(Dispatchers.IO) { loadLocalArt(req) }
        var art = local.art
        value = art
        if (wowArtOn) {
            if (local.heroSource != ArtSource.Screenshot) {
                val needImage = local.heroSource != ArtSource.WowArt
                val hero = withContext(Dispatchers.IO) { fetchHero(req, needImage) }
                if (hero != null) {
                    art = art.copy(hero = hero.image, heroFocus = hero.focus)
                    value = art
                }
            }
            for (tile in LauncherTile.entries) {
                if (tile in local.wowTiles) continue
                val image = withContext(Dispatchers.IO) { fetchTile(req, tile) } ?: continue
                art = art.copy(tiles = art.tiles + (tile to image))
                value = art
            }
        }
    }
    return remember<LauncherArt>(loaded) {
        loaded?.let { BitmapLauncherArt(it.hero, it.tiles, heroFocus = it.heroFocus) } ?: DrawnLauncherArt
    }
}
