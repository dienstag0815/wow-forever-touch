package app.gamenative.report

import java.io.File
import java.util.zip.ZipInputStream

/**
 * Die Karte "Bericht bereit" (kleiner gespeicherter Zustand, damit sie den Wechsel in eine andere App und einen Neustart übersteht):
 * Dateiname der Zip, Zeit, Pfad der Zip im Cache der App, ob sie in Downloads liegt, und die Adresse der Meldeseite (null, wenn die
 * Version keine hat). Kein Bericht-Inhalt: die Kurzfassung steht in der Zip (`kurz.txt`) und wird von dort gelesen.
 */
data class ReportCard(
    val zipName: String,
    val createdMs: Long,
    val zipPath: String,
    val inDownloads: Boolean,
    val issueUrl: String?,
)

/** Lesen, Schreiben und Prüfen der [ReportCard] (reine Kotlin-Logik mit Dateien, JUnit-getestet). Eine Datei, ein Eintrag; ein neuer ersetzt den alten. */
object ReportCardFile {
    const val FILE_NAME = "meldekarte.txt"
    private const val MAX_FILE_BYTES = 16 * 1024L
    private const val MAX_FIELD = 7000

    fun serialize(card: ReportCard): String = listOf(
        "name=${oneLine(card.zipName)}",
        "time=${card.createdMs}",
        "zip=${oneLine(card.zipPath)}",
        "downloads=${if (card.inDownloads) 1 else 0}",
        "url=${oneLine(card.issueUrl.orEmpty())}",
    ).joinToString("\n") + "\n"

    /** Null, wenn Name, Zeit oder Pfad fehlen oder unbrauchbar sind (kaputte Datei = keine Karte). */
    fun parse(text: String?): ReportCard? {
        if (text.isNullOrBlank()) return null
        val values = text.lineSequence().mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }.toMap()
        val name = values["name"]?.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('\\') } ?: return null
        val time = values["time"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val zip = values["zip"]?.takeIf { it.isNotBlank() } ?: return null
        val url = values["url"]?.takeIf { it.startsWith("https://") }
        return ReportCard(name, time, zip, values["downloads"] == "1", url)
    }

    fun load(file: File): ReportCard? =
        runCatching { if (file.isFile && file.length() < MAX_FILE_BYTES) parse(file.readText(Charsets.UTF_8)) else null }.getOrNull()

    /** Schreibt atomar (Nachbardatei, dann Umbenennen). Gibt false zurück, wenn es nicht ging. */
    fun save(file: File, card: ReportCard): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".neu")
        temp.writeText(serialize(card), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "rename failed" }
        }
        true
    }.getOrDefault(false)

    fun clear(file: File): Boolean = runCatching { !file.exists() || file.delete() }.getOrDefault(false)

    /** Ob die Zip im Cache noch da ist. */
    fun zipExists(card: ReportCard): Boolean = runCatching { File(card.zipPath).isFile }.getOrDefault(false)

    /** Die Kurzfassung (`kurz.txt`) aus der Zip; null, wenn die Zip fehlt, kaputt ist oder die Datei nicht enthält. */
    fun summaryFromZip(zip: File): String? = runCatching {
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            generateSequence { input.nextEntry }.firstOrNull { it.name == "kurz.txt" }?.let { String(input.readBytes(), Charsets.UTF_8) }
        }
    }.getOrNull()

    private fun oneLine(text: String): String = text.replace('\n', ' ').replace('\r', ' ').take(MAX_FIELD)
}
