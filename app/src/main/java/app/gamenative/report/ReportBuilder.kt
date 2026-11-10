package app.gamenative.report

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Eine Datei, die in den Fehlerbericht soll. [priority]: kleiner = wichtiger (kommt bei knappem Platz zuerst dran).
 * [maxBytes]: höchstens so viel vom Dateiende (das Ende eines Logs zeigt das Problem).
 */
data class ReportSource(
    val entryName: String,
    val file: File,
    val kind: LogKind,
    val priority: Int,
    val maxBytes: Long = ReportBuilder.PER_FILE_MAX_BYTES,
)

/** Eine geplante Datei: so viele Bytes vom Ende werden gelesen. */
data class PlannedSource(val source: ReportSource, val bytes: Long)

/**
 * Auswahl und Aufbau der Zip des Fehlerberichts, ohne Android-Klassen (JUnit-getestet). Der Bericht enthält nur Textdateien
 * (`.log`, `.txt`, `.wtf`), nie `login.txt` und keine Dumps; jeder Text läuft durch [ReportRedactor].
 */
object ReportBuilder {
    const val TOTAL_FILE_BUDGET_BYTES = 8L * 1024 * 1024
    const val PER_FILE_MAX_BYTES = 2L * 1024 * 1024
    const val LOGCAT_MAX_CHARS = 2 * 1024 * 1024

    /** Kleinere Reste des Budgets lohnen nicht, eine Datei halb abzuschneiden. */
    private const val MIN_USEFUL_BYTES = 1024L

    val ALLOWED_EXTENSIONS = setOf("log", "txt", "wtf")
    private val FORBIDDEN_NAMES = setOf("login.txt")

    /** Darf die Datei in den Bericht? Nie `login.txt`, nur Text-Endungen. */
    fun isAllowed(file: File): Boolean {
        val name = file.name.lowercase()
        return name !in FORBIDDEN_NAMES && file.extension.lowercase() in ALLOWED_EXTENSIONS
    }

    /** Die jüngsten [count] erlaubten Dateien eines Ordners, neueste zuerst. */
    fun newest(dir: File, count: Int): List<File> =
        dir.listFiles { f -> f.isFile && isAllowed(f) }.orEmpty().sortedByDescending { it.lastModified() }.take(count)

    /**
     * Legt fest, wie viel von jeder Quelle in den Bericht kommt: nach [ReportSource.priority] (stabil), je Datei höchstens
     * [ReportSource.maxBytes] vom Ende, insgesamt höchstens [budget]. Unerlaubte, leere und fehlende Dateien entfallen.
     */
    fun plan(sources: List<ReportSource>, budget: Long = TOTAL_FILE_BUDGET_BYTES): List<PlannedSource> {
        var remaining = budget
        val planned = mutableListOf<PlannedSource>()
        for (source in sources.sortedBy { it.priority }) {
            val file = source.file
            if (!isAllowed(file) || !file.isFile) continue
            val size = file.length()
            if (size <= 0L) continue
            val take = minOf(size, source.maxBytes, remaining)
            if (take <= 0L || (take < size && take < MIN_USEFUL_BYTES)) continue
            planned += PlannedSource(source, take)
            remaining -= take
        }
        return planned
    }

    /** Die letzten [maxBytes] einer Datei als Text; beginnt das Stück mitten in einer Zeile, entfällt diese Anfangszeile. */
    fun readTail(file: File, maxBytes: Long): String = RandomAccessFile(file, "r").use { raf ->
        val length = raf.length()
        val start = (length - maxBytes).coerceAtLeast(0)
        raf.seek(start)
        val bytes = ByteArray((length - start).toInt())
        raf.readFully(bytes)
        var text = String(bytes, Charsets.UTF_8)
        if (start > 0) {
            val firstBreak = text.indexOf('\n')
            text = "[earlier lines omitted]\n" + if (firstBreak >= 0) text.substring(firstBreak + 1) else ""
        }
        text
    }

    /** Entry-Namen ohne Pfad-Ausbruch: keine führenden Schrägstriche, kein `..`, nur `/` als Trenner. */
    fun safeEntryName(name: String): String {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
        return parts.joinToString("/").ifEmpty { "datei" }
    }

    /**
     * Schreibt die Zip: `geraet.txt`, `logcat.txt` (die letzten [LOGCAT_MAX_CHARS] Zeichen) und die geplanten Dateien, alles
     * geschwärzt. Gibt die Namen der geschriebenen Einträge zurück.
     */
    fun write(
        out: OutputStream,
        deviceText: String,
        logcat: String,
        sources: List<ReportSource>,
        redactor: ReportRedactor,
    ): List<String> {
        val names = mutableListOf<String>()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                var safe = safeEntryName(name)
                var n = 2
                while (safe in names) safe = safeEntryName(name) + "." + n++
                zip.putNextEntry(ZipEntry(safe))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                names += safe
            }
            put("geraet.txt", redactor.redact(deviceText))
            put("logcat.txt", redactor.redact(logcat.takeLast(LOGCAT_MAX_CHARS)))
            for (planned in plan(sources)) {
                val text = runCatching { readTail(planned.source.file, planned.bytes) }.getOrNull() ?: continue
                put(planned.source.entryName, redactor.redact(text, planned.source.kind))
            }
        }
        return names
    }

    /** Hält höchstens [keep] frühere Berichte im Ordner [dir] (die jüngsten), löscht die übrigen. */
    fun pruneOldReports(dir: File, keep: Int) {
        dir.listFiles { f -> f.isFile && f.name.startsWith(REPORT_PREFIX) && f.name.endsWith(".zip") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(keep)
            .forEach { it.delete() }
    }

    const val REPORT_PREFIX = "WoWForever-Bericht-"
}
