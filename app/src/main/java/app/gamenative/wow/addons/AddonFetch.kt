package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/*
 * Sicherer Abruf (reine Logik über [AddonHttp], im Test mit einer Attrappe, in der App mit OkHttp).
 * Regeln: nur Hosts der Liste ([AddonHosts]), jede Weiterleitung einzeln geprüft (höchstens [MAX_REDIRECTS]), Größenlimit schon
 * am Kopf (Content-Length) und noch einmal beim Lesen, Prüfsumme SHA-256 während des Schreibens.
 */

/** Antwort eines einzelnen Abrufs ohne automatisches Folgen von Weiterleitungen. Der Aufrufer schließt sie. */
class AddonResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: InputStream?,
    private val onClose: () -> Unit = {},
) : AutoCloseable {
    /** Header-Namen ohne Beachtung der Groß-/Kleinschreibung. */
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    val contentLength: Long get() = header("Content-Length")?.trim()?.toLongOrNull() ?: -1L

    override fun close() {
        try {
            body?.close()
        } finally {
            onClose()
        }
    }
}

/** Ein Abruf ohne Weiterleitungen; [headers] sind Anfrage-Header. Wirft [IOException] bei Netzfehlern. */
fun interface AddonHttp {
    fun get(url: String, headers: Map<String, String>): AddonResponse
}

/** Fehler beim Laden; [text] ist die Meldung für die Anzeige ([message] ist nur für Logs und Debugger). */
class AddonNetworkException(val text: UiText, val rateLimited: Boolean = false, val notFound: Boolean = false) : IOException(text.toString())

data class DownloadResult(val bytes: Long, val sha256: String)

object AddonFetch {
    const val MAX_REDIRECTS = 5
    const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    /** Folgt Weiterleitungen, prüft jeden Hop und liefert die Antwort mit Code 2xx; der Aufrufer schließt sie. */
    private fun open(http: AddonHttp, url: String, allowed: Set<String>, headers: Map<String, String>, now: () -> Long): AddonResponse {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            if (!AddonHosts.isAllowed(current, allowed)) throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_host_not_allowed, hostOf(current)))
            val response = http.get(current, headers)
            try {
                when {
                    response.code in 200..299 -> return response
                    response.code in REDIRECT_CODES -> {
                        val location = response.header("Location") ?: throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_redirect_no_target))
                        current = resolve(current, location)
                    }
                    response.code == 403 || response.code == 429 -> throw rateLimit(response, now)
                    response.code == 404 -> throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_not_found), notFound = true)
                    else -> throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_server_error, response.code))
                }
            } catch (e: Throwable) {
                response.close()
                throw e
            }
            response.close()
        }
        throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_too_many_redirects))
    }

    private fun resolve(base: String, location: String): String = try {
        java.net.URI(base).resolve(location.trim()).toString()
    } catch (e: Exception) {
        throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_bad_redirect))
    }

    private fun hostOf(url: String): String = try {
        java.net.URI(url).host ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun rateLimit(response: AddonResponse, now: () -> Long): AddonNetworkException {
        val remaining = response.header("X-RateLimit-Remaining")?.trim()
        val reset = response.header("X-RateLimit-Reset")?.trim()?.toLongOrNull()
        val limited = response.code == 429 || remaining == "0"
        if (!limited) return AddonNetworkException(UiText.res(R.string.launcher_addon_net_forbidden))
        val wait = reset?.let { ((it * 1000 - now()) / 60_000).coerceAtLeast(1) }
        val text = if (wait != null) {
            UiText.plural(R.plurals.launcher_addon_net_rate_limited_minutes, wait.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        } else {
            UiText.res(R.string.launcher_addon_net_rate_limited_later)
        }
        return AddonNetworkException(text, rateLimited = true)
    }

    /** Lädt Text (z. B. API-Antwort), höchstens [maxBytes]. */
    fun getText(
        http: AddonHttp,
        url: String,
        allowed: Set<String>,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = MAX_TEXT_BYTES,
        now: () -> Long = System::currentTimeMillis,
    ): String {
        open(http, url, allowed, headers, now).use { response ->
            if (response.contentLength > maxBytes) throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_response_too_big))
            val body = response.body ?: throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_empty))
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = body.read(buffer)
                if (n < 0) break
                if (out.size() + n > maxBytes) throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_response_too_big))
                out.write(buffer, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    /**
     * Lädt [url] nach [dest] (über `dest.part`, am Ende umbenannt). Bei Überschreitung von [maxBytes] oder Fehlern bleibt
     * keine Datei zurück. [onProgress] bekommt (gelesene Bytes, erwartete Gesamtzahl oder -1).
     */
    fun download(
        http: AddonHttp,
        url: String,
        allowed: Set<String>,
        dest: File,
        maxBytes: Long,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        now: () -> Long = System::currentTimeMillis,
    ): DownloadResult {
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        part.delete()
        try {
            open(http, url, allowed, emptyMap(), now).use { response ->
                val expected = response.contentLength
                if (expected > maxBytes) throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_file_too_big_mb, expected / (1024 * 1024)))
                val body = response.body ?: throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_empty))
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = body.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_file_too_big))
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                        onProgress(total, expected)
                    }
                }
                if (expected >= 0 && total != expected) throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_incomplete))
                dest.delete()
                if (!part.renameTo(dest)) throw AddonNetworkException(UiText.res(R.string.launcher_addon_net_save_failed))
                return DownloadResult(total, hex(digest.digest()))
            }
        } finally {
            part.delete()
        }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

/** Zwischenspeicher für Texte (GitHub-API, release.json) mit Gültigkeitsdauer; ohne Android, Dateien in [dir]. */
class AddonCache(private val dir: File, private val ttlMs: Long = TTL_MS, private val now: () -> Long = System::currentTimeMillis) {
    companion object {
        const val TTL_MS = 60L * 60 * 1000
        private val SAFE = Regex("[^A-Za-z0-9._-]")
    }

    private fun file(key: String) = File(dir, SAFE.replace(key, "_") + ".json")

    /** Der gespeicherte Text, wenn er jünger als die Gültigkeitsdauer ist. */
    fun fresh(key: String): String? = read(key, requireFresh = true)

    /** Der gespeicherte Text auch wenn er veraltet ist (Rückfall bei Netzfehler oder GitHub-Limit). */
    fun stale(key: String): String? = read(key, requireFresh = false)

    /** Verwirft alles (Knopf "Neu prüfen"). */
    fun clear() {
        dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") }.forEach { it.delete() }
    }

    fun put(key: String, text: String) {
        dir.mkdirs()
        file(key).writeText(text, Charsets.UTF_8)
    }

    private fun read(key: String, requireFresh: Boolean): String? {
        val f = file(key)
        if (!f.isFile) return null
        if (requireFresh && now() - f.lastModified() >= ttlMs) return null
        return try {
            f.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            null
        }
    }
}
