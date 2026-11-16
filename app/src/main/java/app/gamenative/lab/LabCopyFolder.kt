package app.gamenative.lab

import android.content.Context
import timber.log.Timber
import java.io.File
import java.nio.file.Files

/**
 * Abschnitt `== Game copy folder ==` im Labor-Bericht: was der Launcher an der Kopie tut. Nur lesen (`listFiles`, `length`, `lastModified`);
 * es werden weder Dateien geöffnet noch Inhalte gelesen und nichts wird geschrieben außer dem Bericht selbst. Pfade sind relativ zum Kopie-Bereich
 * (Windows `K:\`), also `World of Warcraft/...`.
 */
object LabCopyFolderLogic {
    /** Höchstens so viele Zeilen: Liste der beiden Ebenen und Liste der Änderungen im Lauf. */
    const val MAX_LISTING = 80
    const val MAX_CHANGES = 40

    /** Ebenen der Liste: der Spielordner selbst und `_forever_` darin (nicht rekursiv). */
    const val FOREVER_DIR = "_forever_"

    /** Abdruck des Kopie-Bereichs ohne Inhalte (Pfad, Art, Größe, Zeit); ohne Ordner `NOT_PRESENT`. Folgt keiner Verknüpfung. */
    fun take(area: File?, clock: () -> Long = System::nanoTime): OriginalPrint {
        if (area == null || !area.isDirectory) return OriginalPrint.NOT_PRESENT
        val t0 = clock()
        val out = LinkedHashMap<String, PrintEntry>()
        var truncated = false
        val stack = ArrayDeque<Pair<File, String>>()
        stack.add(area to "")
        while (stack.isNotEmpty() && !truncated) {
            val (dir, rel) = stack.removeLast()
            for (f in dir.listFiles().orEmpty().sortedBy { it.name }) {
                if (out.size >= LabOriginalPrintLogic.MAX_ENTRIES) {
                    truncated = true
                    break
                }
                val path = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                when {
                    Files.isSymbolicLink(f.toPath()) -> out[path] = PrintEntry(PrintKind.LINK, 0L, 0L)
                    f.isDirectory -> {
                        out[path] = PrintEntry(PrintKind.DIR, 0L, 0L)
                        stack.add(f to path)
                    }
                    f.isFile -> out[path] = PrintEntry(PrintKind.FILE, f.length(), f.lastModified())
                }
            }
        }
        return OriginalPrint(true, out, (clock() - t0) / 1_000_000L, truncated)
    }

    private fun clean(text: String): String = text.map { if (it.code < 32 || it.code == 127) '?' else it }.joinToString("")

    private fun kindWord(e: PrintEntry): String = when (e.kind) {
        PrintKind.FILE -> "file"
        PrintKind.DIR -> "folder"
        PrintKind.LINK -> "link"
    }

    private fun entryLine(name: String, e: PrintEntry): String {
        val size = if (e.kind == PrintKind.FILE) e.size.toString() else "-"
        val time = if (e.kind == PrintKind.FILE) LabProtocol.timestamp(e.modifiedMs) else "-"
        return "${clean(name)} | ${kindWord(e)} | $size | $time"
    }

    /**
     * Die Einträge im Spielordner `World of Warcraft` und in `World of Warcraft/_forever_` (je eine Ebene, nicht rekursiv). Name relativ zum
     * Spielordner. Höchstens [MAX_LISTING] Zeilen; wird gekürzt, nennt die letzte den Rest.
     */
    fun listing(print: OriginalPrint): List<String> {
        val game = LabIds.COPY_GAME_DIR
        val forever = "$game/$FOREVER_DIR"
        val main = print.entries.filter { (path, _) -> path.startsWith("$game/") && path.removePrefix("$game/").let { it.isNotEmpty() && '/' !in it } }
        val deeper = print.entries.filter { (path, _) -> path.startsWith("$forever/") && path.removePrefix("$forever/").let { it.isNotEmpty() && '/' !in it } }
        val all = (main.entries.sortedBy { it.key } + deeper.entries.sortedBy { it.key }).map { (path, e) -> entryLine(path.removePrefix("$game/"), e) }
        if (all.size <= MAX_LISTING) return all
        return all.take(MAX_LISTING - 1) + "... and ${all.size - (MAX_LISTING - 1)} more entries not listed"
    }

    /** Eine Zeile je Änderung (neu, geändert, entfernt), höchstens [MAX_CHANGES]. Nur relative Pfade, Größe und Zeit. */
    fun changeLines(before: OriginalPrint, after: OriginalPrint): List<String> {
        val all = LabOriginalPrintLogic.compare(before, after).map { d ->
            val text = LabOriginalPrintLogic.line(d)
            if (d.kind == PrintDeviation.Kind.MISSING) "removed" + text.removePrefix("missing") else text
        }
        if (all.size <= MAX_CHANGES) return all
        return all.take(MAX_CHANGES - 1) + "... and ${all.size - (MAX_CHANGES - 1)} more changes not listed"
    }

    private fun counts(p: OriginalPrint): String = "${p.fileCount} files, ${p.bytes} bytes" + if (p.truncated) " (cut off at ${LabOriginalPrintLogic.MAX_ENTRIES} entries)" else ""

    /** Die Zeilen des Abschnitts. [before] null = vor dem Start gab es keinen Abdruck (dann keine Änderungsliste). */
    fun sectionLines(before: OriginalPrint?, after: OriginalPrint, mounted: Boolean, note: String? = null): List<String> {
        val out = ArrayList<String>()
        if (!after.present && before?.present != true) {
            out += "no game copy folder"
            note?.let { out += it }
            return out
        }
        out += "Mounted in this run: ${if (mounted) "yes, as drive ${LabIds.COPY_DRIVE}:" else "no"}"
        out += "Game folder: ${LabCopyMount.windowsGameRoot()} (${if (after.entries.containsKey(LabIds.COPY_GAME_DIR)) "present" else "missing"})"
        out += "Whole copy after the run: ${counts(after)}"
        out += if (before == null) "Whole copy before the run: unknown (no fingerprint before the start)" else "Whole copy before the run: ${counts(before)}"
        out += "Scan time: ${before?.durationMs ?: 0} ms before, ${after.durationMs} ms after"
        out += "-- entries in ${LabCopyMount.windowsGameRoot()} and ${LabCopyMount.windowsGameRoot()}$FOREVER_DIR\\ after the run (name | kind | bytes | modified) --"
        out += listing(after).ifEmpty { listOf("(none)") }
        if (before == null) {
            out += "-- changes during the run: unknown --"
        } else {
            val changes = changeLines(before, after)
            out += "-- changes during the run (new / changed / removed): ${if (changes.isEmpty()) "none" else "listed" } --"
            out += changes
        }
        note?.let { out += it }
        return out
    }
}

/** Der Abschnitt im Protokoll (gleiches Verfahren wie [LabOriginalSection]). */
object LabCopyFolderSection {
    const val TITLE = "Game copy folder"
    private const val END_MARKER = "\n== End ==\n"

    fun sectionText(lines: List<String>): String = "\n== $TITLE ==\n" + lines.joinToString("") { it + "\n" }

    /** Vor `== End ==` (fehlt die Marke: hinten); gibt es den Abschnitt schon, bleibt der Text, wie er ist. */
    fun insert(text: String, lines: List<String>): String {
        if (text.contains("\n== $TITLE ==\n")) return text
        val at = text.lastIndexOf(END_MARKER)
        return if (at < 0) text + sectionText(lines) else text.substring(0, at) + sectionText(lines) + text.substring(at)
    }
}

/**
 * Merkt den Abdruck vor dem Start (nur im Speicher) und setzt am Ende des Laufs den Abschnitt in das Protokoll. Fehlt der Abdruck davor (die App
 * war dazwischen beendet), steht im Abschnitt, dass die Änderungen unbekannt sind.
 */
object LabCopyFolderCheck {
    private class Pending(val before: OriginalPrint, val mounted: Boolean, val protocol: String)

    private val lock = Any()
    private var pending: Pending? = null

    /** Abdruck vor dem Start (liest nur; im Hintergrund-Faden des Starts, nie auf dem Haupt-Thread des X-Servers). */
    fun snapshot(context: Context): OriginalPrint = runCatching { LabCopyFolderLogic.take(LabDirs.gameCopyMount(context)) }.getOrDefault(OriginalPrint.NOT_PRESENT)

    fun remember(before: OriginalPrint, mounted: Boolean, protocol: File) {
        synchronized(lock) { pending = Pending(before, mounted, protocol.name) }
    }

    /** Abdruck nach dem Ende und Abschnitt in das Protokoll; ohne vorgemerkten Lauf tut es nichts. */
    fun finish(context: Context) {
        val p = synchronized(lock) { pending.also { pending = null } } ?: return
        val after = snapshot(context)
        val lines = LabCopyFolderLogic.sectionLines(p.before, after, p.mounted)
        runCatching { insertIntoProtocol(context, p.protocol, lines) }.onFailure { Timber.w(it, "Labor-Protokoll: Abschnitt Kopie-Ordner nicht eingesetzt") }
    }

    /** Wie [finish], aber in einem eigenen Faden (das Ende kommt aus dem X-Server-Bildschirm; das Lesen darf den Haupt-Thread nicht aufhalten). */
    fun finishAsync(context: Context) {
        val app = context.applicationContext
        Thread({ runCatching { finish(app) }.onFailure { Timber.w(it, "Labor: Abschnitt Kopie-Ordner") } }, "lab-copy-folder-final")
            .apply { isDaemon = true }.start()
    }

    private fun insertIntoProtocol(context: Context, protocolName: String, lines: List<String>) {
        if (protocolName.isEmpty()) return
        val file = File(LabDirs.logs(context), protocolName)
        if (!file.isFile) return
        LabRun.withFileLock {
            val patched = LabCopyFolderSection.insert(file.readText(Charsets.UTF_8), lines)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(patched, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(patched, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }
}
