package app.gamenative.lab

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.security.MessageDigest

/** Art eines Eintrags im Abdruck. Verknüpfungen werden nicht verfolgt, nur ihr Name gemerkt. */
enum class PrintKind(val key: String) {
    FILE("f"), DIR("d"), LINK("l");

    companion object {
        fun fromKey(key: String?): PrintKind = entries.firstOrNull { it.key == key } ?: FILE
    }
}

/** Ein Eintrag: Größe und Änderungszeit (Ordner und Verknüpfungen: 0) und, soweit verlangt, SHA-256 des Inhalts. */
data class PrintEntry(val kind: PrintKind, val size: Long, val modifiedMs: Long, val sha256: String? = null)

/**
 * Abdruck des Originalordners: je Datei relativer Pfad, Größe, Änderungszeit; dazu SHA-256 der kleinen Kennungsdateien und aller Dateien
 * bis [HASH_MAX_BYTES] direkt im Hauptordner. [present] false = es gibt keinen Spielordner. Wird nur durch Lesen gewonnen.
 */
class OriginalPrint(
    val present: Boolean,
    val entries: Map<String, PrintEntry>,
    val durationMs: Long,
    val truncated: Boolean = false,
) {
    val fileCount: Int get() = entries.values.count { it.kind == PrintKind.FILE }
    val bytes: Long get() = entries.values.filter { it.kind == PrintKind.FILE }.sumOf { it.size }

    fun toJson(): JSONObject = JSONObject().apply {
        put("present", present)
        put("durationMs", durationMs)
        put("truncated", truncated)
        put(
            "entries",
            JSONArray().also { arr ->
                for ((path, e) in entries) arr.put(JSONArray().put(path).put(e.kind.key).put(e.size).put(e.modifiedMs).put(e.sha256 ?: ""))
            },
        )
    }

    companion object {
        val NOT_PRESENT = OriginalPrint(false, emptyMap(), 0L)

        fun fromJson(o: JSONObject?): OriginalPrint? {
            o ?: return null
            val arr = o.optJSONArray("entries") ?: return null
            val map = LinkedHashMap<String, PrintEntry>()
            for (i in 0 until arr.length()) {
                val row = arr.optJSONArray(i) ?: continue
                map[row.optString(0)] = PrintEntry(
                    PrintKind.fromKey(row.optString(1)), row.optLong(2), row.optLong(3), row.optString(4).takeIf { it.isNotEmpty() },
                )
            }
            return OriginalPrint(o.optBoolean("present", false), map, o.optLong("durationMs"), o.optBoolean("truncated", false))
        }
    }
}

/** Eine Abweichung zwischen zwei Abdrücken. [was] ist der alte, [now] der neue Eintrag (null = gab es nicht). */
data class PrintDeviation(val kind: Kind, val path: String, val was: PrintEntry?, val now: PrintEntry?) {
    enum class Kind(val word: String) { NEW("new"), MISSING("missing"), CHANGED("changed") }
}

/**
 * Abdruck nehmen, vergleichen, kürzen, sperren. Rein bis auf das Lesen des Ordners; es gibt hier keinen Aufruf, der im Original
 * schreibt, umbenennt oder löscht (nur `listFiles`, `length`, `lastModified`, `FileInputStream`).
 */
object LabOriginalPrintLogic {
    /** Dateien bis zu dieser Größe direkt im Hauptordner bekommen einen SHA-256. */
    const val HASH_MAX_BYTES = 1L * 1024 * 1024

    /** Kennungsdateien, die immer einen SHA-256 bekommen (im Hauptordner und eine Ebene tiefer, dort liegt `.flavor.info`). */
    val IDENT_FILES = setOf(".build.info", ".flavor.info", "Launcher.db", ".product.db", ".patch.result")

    /** Mehr Einträge nimmt der Abdruck nicht auf (Schutz vor einem verirrten Ordner); danach gilt er als gekürzt. */
    const val MAX_ENTRIES = 300_000

    /** Höchstens so viele Zeilen mit Abweichungen im Protokoll (die letzte nennt ggf. den Rest). */
    const val MAX_LINES = 40

    /** Abdruck von [root]; ohne Ordner `NOT_PRESENT`. Folgt keiner Verknüpfung. [clock] nur für Tests. */
    fun take(root: File?, clock: () -> Long = System::nanoTime): OriginalPrint {
        if (root == null || !root.isDirectory) return OriginalPrint.NOT_PRESENT
        val t0 = clock()
        val out = LinkedHashMap<String, PrintEntry>()
        var truncated = false
        val stack = ArrayDeque<Pair<File, String>>()
        stack.add(root to "")
        while (stack.isNotEmpty() && !truncated) {
            val (dir, rel) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children.sortedBy { it.name }) {
                if (out.size >= MAX_ENTRIES) {
                    truncated = true
                    break
                }
                val path = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                val depth = if (rel.isEmpty()) 0 else rel.count { it == '/' } + 1
                when {
                    Files.isSymbolicLink(f.toPath()) -> out[path] = PrintEntry(PrintKind.LINK, 0L, 0L)
                    f.isDirectory -> {
                        out[path] = PrintEntry(PrintKind.DIR, 0L, 0L)
                        stack.add(f to path)
                    }
                    f.isFile -> {
                        val size = f.length()
                        val hash = if (wantsHash(f.name, size, depth)) sha256(f) else null
                        out[path] = PrintEntry(PrintKind.FILE, size, f.lastModified(), hash)
                    }
                }
            }
        }
        return OriginalPrint(true, out, (clock() - t0) / 1_000_000L, truncated)
    }

    /** Kennungsdatei (Hauptordner oder eine Ebene tiefer) oder eine kleine Datei direkt im Hauptordner. */
    fun wantsHash(name: String, size: Long, depth: Int): Boolean =
        (name in IDENT_FILES && depth <= 1) || (depth == 0 && size <= HASH_MAX_BYTES)

    fun sha256(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Neue, fehlende, geänderte Einträge (geändert = Größe, Zeit oder SHA-256 anders, oder die Art). Nach Pfad sortiert. */
    fun compare(before: OriginalPrint, after: OriginalPrint): List<PrintDeviation> {
        val out = ArrayList<PrintDeviation>()
        for ((path, a) in after.entries) {
            val b = before.entries[path]
            when {
                b == null -> out += PrintDeviation(PrintDeviation.Kind.NEW, path, null, a)
                differs(b, a) -> out += PrintDeviation(PrintDeviation.Kind.CHANGED, path, b, a)
            }
        }
        for ((path, b) in before.entries) if (path !in after.entries) out += PrintDeviation(PrintDeviation.Kind.MISSING, path, b, null)
        return out.sortedBy { it.path }
    }

    private fun differs(a: PrintEntry, b: PrintEntry): Boolean =
        a.kind != b.kind || a.size != b.size || a.modifiedMs != b.modifiedMs || (a.sha256 != null && b.sha256 != null && a.sha256 != b.sha256)

    /** Eine Zeile: Art, Pfad, Größe (alt -> neu), Zeit (alt -> neu). Steuerzeichen im Pfad werden zu `?`. */
    fun line(d: PrintDeviation): String {
        val path = d.path.map { if (it.code < 32 || it.code == 127) '?' else it }.joinToString("")
        fun time(e: PrintEntry?) = if (e == null || e.kind != PrintKind.FILE) "-" else LabProtocol.timestamp(e.modifiedMs)
        fun size(e: PrintEntry?) = if (e == null) "-" else if (e.kind == PrintKind.FILE) e.size.toString() else e.kind.name.lowercase()
        val hash = if (d.kind == PrintDeviation.Kind.CHANGED && d.was?.sha256 != null && d.now?.sha256 != null && d.was.sha256 != d.now.sha256) " | content differs" else ""
        return when (d.kind) {
            PrintDeviation.Kind.NEW -> "new | $path | ${size(d.now)} | ${time(d.now)}"
            PrintDeviation.Kind.MISSING -> "missing | $path | ${size(d.was)} | ${time(d.was)}"
            PrintDeviation.Kind.CHANGED -> "changed | $path | ${size(d.was)} -> ${size(d.now)} | ${time(d.was)} -> ${time(d.now)}$hash"
        }
    }

    /** Höchstens [MAX_LINES] Zeilen; wird gekürzt, ist die letzte Zeile die Zahl der weggelassenen. */
    fun lines(deviations: List<PrintDeviation>): List<String> {
        if (deviations.size <= MAX_LINES) return deviations.map { line(it) }
        val shown = deviations.take(MAX_LINES - 1).map { line(it) }
        return shown + "... and ${deviations.size - (MAX_LINES - 1)} more deviations not listed"
    }

    /** Die Zeilen des Abschnitts `== Original game folder ==`. [after] null = der Abdruck nach dem Ende fehlt (dann kein Vergleich). */
    fun sectionLines(before: OriginalPrint?, after: OriginalPrint?, deviations: List<PrintDeviation>, note: String? = null): List<String> {
        val out = ArrayList<String>()
        if (after == null || before == null) {
            out += "unchanged: unknown (${if (before == null) "no fingerprint before the start" else "no fingerprint after the end"})"
            note?.let { out += it }
            return out
        }
        if (!after.present && !before.present) {
            out += "not present"
            out += "unchanged: yes"
            note?.let { out += it }
            return out
        }
        out += "Files: ${after.fileCount}, bytes: ${after.bytes} (before: ${before.fileCount} files, ${before.bytes} bytes)"
        out += "Fingerprint time: before ${before.durationMs} ms, after ${after.durationMs} ms"
        if (before.truncated || after.truncated) out += "(fingerprint cut off at ${MAX_ENTRIES} entries)"
        out += "unchanged: ${if (deviations.isEmpty()) "yes" else "no"}"
        out += lines(deviations)
        note?.let { out += it }
        return out
    }
}

/** Stand der Prüfung "Originalordner unverändert" zwischen zwei Aufrufen (liegt als Datei, übersteht ein Beenden der App). */
data class OriginalCheckState(
    /** Ein Abdruck vor dem Start liegt vor, der nach dem Ende fehlt noch. */
    val pending: Boolean = false,
    val before: OriginalPrint? = null,
    /** Dateiname des Protokolls, in das der Abschnitt gehört (leer, wenn keins). */
    val protocol: String = "",
    /** Ergebnis der letzten fertigen Prüfung; null = keine. */
    val unchanged: Boolean? = null,
    val deviationLines: List<String> = emptyList(),
    val acknowledged: Boolean = false,
    val checkedMs: Long = 0L,
) {
    /** Rote Karte: Abweichung, noch nicht bestätigt. */
    val needsAck: Boolean get() = unchanged == false && !acknowledged

    fun toJson(): String = JSONObject().apply {
        put("pending", pending)
        before?.let { put("before", it.toJson()) }
        put("protocol", protocol)
        unchanged?.let { put("unchanged", it) }
        put("deviationLines", JSONArray(deviationLines))
        put("acknowledged", acknowledged)
        put("checkedMs", checkedMs)
    }.toString()

    companion object {
        fun fromJson(text: String?): OriginalCheckState {
            if (text.isNullOrBlank()) return OriginalCheckState()
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return OriginalCheckState()
            val lines = o.optJSONArray("deviationLines")
            return OriginalCheckState(
                pending = o.optBoolean("pending", false),
                before = OriginalPrint.fromJson(o.optJSONObject("before")),
                protocol = o.optString("protocol", ""),
                unchanged = if (o.has("unchanged")) o.optBoolean("unchanged") else null,
                deviationLines = (0 until (lines?.length() ?: 0)).map { lines!!.optString(it) },
                acknowledged = o.optBoolean("acknowledged", false),
                checkedMs = o.optLong("checkedMs", 0L),
            )
        }
    }
}

/** Entscheidungen auf dem Stand, rein. */
object LabOriginalGate {
    /** Weitere Labor-Starts sind gesperrt, solange eine Abweichung nicht bestätigt ist. */
    fun blocksStart(state: OriginalCheckState): Boolean = state.needsAck

    /** Zustand nach einer fertigen Prüfung (der Abdruck vor dem Start wird freigegeben). */
    fun afterCheck(state: OriginalCheckState, deviations: List<PrintDeviation>, nowMs: Long): OriginalCheckState =
        state.copy(
            pending = false,
            before = null,
            unchanged = deviations.isEmpty(),
            deviationLines = if (deviations.isEmpty()) emptyList() else LabOriginalPrintLogic.lines(deviations),
            acknowledged = deviations.isEmpty(),
            checkedMs = nowMs,
        )

    /** Der Nutzer hat die rote Karte bestätigt. */
    fun acknowledge(state: OriginalCheckState): OriginalCheckState = state.copy(acknowledged = true)

    /** Zustand nach dem Abdruck vor einem Start. */
    fun begun(state: OriginalCheckState, before: OriginalPrint, protocol: String): OriginalCheckState =
        state.copy(pending = true, before = before, protocol = protocol)
}
