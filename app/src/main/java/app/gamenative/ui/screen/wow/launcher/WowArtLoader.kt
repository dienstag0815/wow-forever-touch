package app.gamenative.ui.screen.wow.launcher

import java.io.File
import java.io.IOException

/*
 * Holt das WoW-Artwork (WowArtSources.kt) einmal aus dem Netz in einen eigenen Ordner des App-Speichers. Reines JVM (ohne Android),
 * damit es per JUnit mit einer Attrappe für das Netz geprüft werden kann. Die App benutzt OkHttp (siehe LauncherImages.kt).
 * Fehler sind still: 404 oder kein Netz geben nur "nicht da" zurück, die Oberfläche nimmt dann die nächste Quelle.
 */

class ArtResponse(val code: Int, val body: ByteArray)

/** Die einzige Netztür des Bildcodes (Tests ersetzen sie). */
fun interface ArtHttp {
    /** GET [url]. Wirft [IOException] bei Netzproblemen; jeder HTTP-Status ist eine Antwort. */
    @Throws(IOException::class)
    fun get(url: String): ArtResponse
}

/** Größte angenommene Antwort; größere Bilder (kein verkleinertes JPEG) werden verworfen. */
internal const val ART_MAX_BYTES = 2 * 1024 * 1024

/** Ordner mit den geladenen Bildern; Dateiname = Schlüssel, Inhalt = die Bytes (der Dekoder erkennt das Format am Inhalt). */
internal class ArtCache(private val dir: File) {
    init {
        dir.mkdirs()
    }

    fun file(key: String): File = File(dir, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".img")

    fun lookup(key: String): File? = file(key).takeIf { it.isFile && it.length() > 0L }

    /** Schreibt erst in eine Nebendatei und benennt dann um, damit nie ein halbes Bild liegt. */
    fun store(key: String, bytes: ByteArray): File? {
        if (bytes.isEmpty()) return null
        val target = file(key)
        val tmp = File(dir, target.name + ".tmp")
        return try {
            tmp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (tmp.renameTo(target)) target else null
        } catch (e: IOException) {
            tmp.delete()
            null
        }
    }

    fun remove(key: String) {
        file(key).delete()
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}

internal sealed class ArtFetch {
    data class Ok(val file: File) : ArtFetch()

    /** Gibt es dort nicht (4xx), zu groß oder leer: nächste Quelle versuchen. */
    object Gone : ArtFetch()

    /** Netzproblem oder Serverfehler: weitere Netzabrufe lohnen jetzt nicht. */
    object Offline : ArtFetch()
}

internal class WowArtLoader(private val cache: ArtCache, private val http: ArtHttp) {
    /** Das Bild im Speicher, sonst aus dem Netz (und dann gespeichert). */
    fun fetch(key: String, url: String): ArtFetch {
        cache.lookup(key)?.let { return ArtFetch.Ok(it) }
        val response = try {
            http.get(url)
        } catch (e: IOException) {
            return ArtFetch.Offline
        } catch (e: RuntimeException) {
            return ArtFetch.Offline
        }
        return when {
            response.code == 200 -> {
                if (response.body.isEmpty() || response.body.size > ART_MAX_BYTES) {
                    ArtFetch.Gone
                } else {
                    cache.store(key, response.body)?.let { ArtFetch.Ok(it) } ?: ArtFetch.Offline
                }
            }
            response.code in 400..499 -> ArtFetch.Gone
            else -> ArtFetch.Offline
        }
    }
}
