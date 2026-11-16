package app.gamenative.lab

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Ergebnis eines Downloads: die fertige Datei, ihre Größe und SHA-256 (gerechnet beim Laden). */
data class LabDownload(val file: File, val bytes: Long, val sha256: String)

/**
 * Lädt ein Paket in eine Datei: nur HTTPS (auch bei jeder Weiterleitung), Fortschritt in Bytes, Abbruch über [isCancelled]. Die Datei
 * entsteht als `.part` und wird erst nach vollständigem Laden umbenannt. Läuft im Hintergrund; nichts wird ausgeführt.
 */
object LabDownloader {
    private const val MAX_REDIRECTS = 5
    private const val BUFFER = 64 * 1024

    class DownloadException(val code: Code, message: String) : IOException(message) {
        enum class Code { INSECURE, HTTP, TOO_MANY_REDIRECTS, CANCELLED, IO }
    }

    fun download(url: String, target: File, expectedBytes: Long, isCancelled: () -> Boolean, onProgress: (done: Long, total: Long) -> Unit): LabDownload {
        if (!LabPackages.isHttps(url)) throw DownloadException(DownloadException.Code.INSECURE, "not https")
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        part.delete()
        var current = url
        var redirects = 0
        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "WoWForever-Lab")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val next = connection.getHeaderField("Location") ?: throw DownloadException(DownloadException.Code.HTTP, "redirect without location")
                    val resolved = URL(URL(current), next).toString()
                    if (!LabPackages.isHttps(resolved)) throw DownloadException(DownloadException.Code.INSECURE, "redirect to a non-https address")
                    if (++redirects > MAX_REDIRECTS) throw DownloadException(DownloadException.Code.TOO_MANY_REDIRECTS, "too many redirects")
                    current = resolved
                    continue
                }
                if (code != 200) throw DownloadException(DownloadException.Code.HTTP, "HTTP $code")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedBytes
                return connection.inputStream.use { input -> copy(input, part, target, total, isCancelled, onProgress) }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun copy(input: InputStream, part: File, target: File, total: Long, isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit): LabDownload {
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        try {
            part.outputStream().buffered().use { out ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    if (isCancelled()) throw DownloadException(DownloadException.Code.CANCELLED, "cancelled")
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    done += n
                    onProgress(done, total)
                }
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        }
        target.delete()
        if (!part.renameTo(target)) {
            part.delete()
            throw DownloadException(DownloadException.Code.IO, "rename failed")
        }
        return LabDownload(target, done, hex(digest.digest()))
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** SHA-256 und Größe eines Stroms (für "Paket aus Datei wählen"). */
    fun hash(input: InputStream): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var total = 0L
        input.use {
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                total += n
            }
        }
        return total to hex(digest.digest())
    }
}
