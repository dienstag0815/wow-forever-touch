package app.gamenative.lab

/** Die drei Arten Absturzmeldung von Wine, getrennt gezählt. */
enum class LabCrashKind(val label: String, val marker: String) {
    PAGE_FAULT("page fault", "wine: Unhandled page fault"),
    ILLEGAL_INSTRUCTION("illegal instruction", "wine: Unhandled illegal instruction"),
    EXCEPTION("exception", "wine: Unhandled exception"),
}

/** Ein Absturz: [elapsedSec] seit Start des Laufs (null = nicht bekannt), [pid] Wine-Prozesskennung (4 Stellen hex), [program] und [type] aus den `+process`-Meldungen. */
data class LabCrash(
    val kind: LabCrashKind,
    val elapsedSec: Long?,
    val pid: String?,
    /** Dateiname des Programms, null = nicht ableitbar. */
    val program: String?,
    /** Wert von `--type=` (`renderer`, `gpu-process` ...); [TYPE_NONE] wenn der Prozess bekannt ist, aber keinen `--type=` hat; null = nicht ableitbar. */
    val type: String?,
) {
    companion object {
        const val TYPE_NONE = "(none)"
    }
}

/**
 * Wertet Wines Ausgabe Zeile für Zeile auf Abstürze aus; rein, ohne Android, JUnit-getestet. Er sieht jede Zeile, auch die, die [LabOutputCap]
 * später in der Mitte weglässt: die Zahlen gelten für den ganzen Lauf, nicht nur für das, was in der Datei bleibt.
 *
 * Die Prozesskennung kommt aus der Zeile `winedbg --auto <pid dezimal> <ereignis>`, die Wine nach der Meldung startet; Programmname und `--type=`
 * aus den Zeilen `NtCreateUserProcess ... image L"..." cmdline L"..."` und `... pid <hex> tid <hex>` (nur mit `+process`). Fehlt etwas davon,
 * bleibt das Feld null (im Protokoll "unknown").
 */
class LabCrashScanner {
    private class Info(val program: String?, val type: String?)

    private class Entry(val kind: LabCrashKind, val elapsedSec: Long?, var pid: String?, val thread: String?) {
        var linesSince = 0
    }

    private val entries = mutableListOf<Entry>()
    private val counts = IntArray(LabCrashKind.entries.size)
    private val pending = LinkedHashMap<String, Info>()
    private val processes = LinkedHashMap<String, Info>()
    private val mainThreads = HashMap<String, String>()
    private var firstStamp: Double? = null
    private var lastStamp: Double? = null
    private var lastCrash: Entry? = null

    /** Zahl aller gezählten Abstürze (nicht nur der gemerkten). */
    val total: Int get() = counts.sum()

    fun count(kind: LabCrashKind): Int = counts[kind.ordinal]

    /** Die ersten [MAX_KEPT] Abstürze in der Reihenfolge der Ausgabe. */
    val crashes: List<LabCrash>
        get() = entries.map { e ->
            val pid = e.pid ?: e.thread?.let { mainThreads[it] }
            val info = pid?.let { processes[it] }
            LabCrash(
                kind = e.kind,
                elapsedSec = e.elapsedSec,
                pid = pid,
                program = info?.program,
                type = info?.let { it.type ?: LabCrash.TYPE_NONE },
            )
        }

    /**
     * Nimmt eine Zeile. [elapsedMs] ist die Zeit seit Start des Laufs (Uhr des Geräts); fehlt sie, rechnet der Scanner aus den
     * Zeitstempeln von `+timestamp` (Sekunden seit der ersten Zeile mit Zeitstempel).
     */
    fun add(line: String, elapsedMs: Long? = null) {
        val prefix = PREFIX.find(line)
        if (prefix != null) {
            val stamp = prefix.groupValues[1].toDoubleOrNull()
            if (stamp != null) {
                if (firstStamp == null) firstStamp = stamp
                lastStamp = stamp
            }
        }
        lastCrash?.let { it.linesSince++ }
        // Keine Auswahl mit `else`: die Zeile `NtCreateUserProcess ... cmdline L"winedbg --auto ..."` gehört zu beiden Fällen.
        if (line.contains("wine: Unhandled ")) crash(line, elapsedMs)
        if (line.contains("winedbg --auto")) debugger(line)
        if (line.contains("NtCreateUserProcess")) processCreation(line, prefix?.groupValues?.get(2).orEmpty())
    }

    private fun crash(line: String, elapsedMs: Long?) {
        val kind = LabCrashKind.entries.firstOrNull { line.contains(it.marker) } ?: return
        val thread = THREAD.find(line)?.groupValues?.get(1)?.lowercase()?.padStart(4, '0')
        val sec = when {
            elapsedMs != null -> elapsedMs / 1000
            firstStamp != null && lastStamp != null -> (lastStamp!! - firstStamp!!).toLong()
            else -> null
        }
        counts[kind.ordinal]++
        val e = Entry(kind, sec, null, thread)
        if (entries.size < MAX_KEPT) entries += e
        lastCrash = e
    }

    private fun debugger(line: String) {
        val e = lastCrash ?: return
        if (e.linesSince > DEBUGGER_WITHIN_LINES) return
        val dec = DEBUGGER.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return
        e.pid = hex4(dec)
        lastCrash = null
    }

    private fun processCreation(line: String, creatorThread: String) {
        val created = CREATED.find(line)
        if (created != null) {
            val pid = created.groupValues[1].lowercase().padStart(4, '0')
            val tid = created.groupValues[2].lowercase().padStart(4, '0')
            val info = pending.remove(creatorThread) ?: Info(null, null)
            if (processes.size < MAX_PROCESSES) processes[pid] = info
            if (mainThreads.size < MAX_PROCESSES) mainThreads[tid] = pid
            return
        }
        val image = IMAGE.find(line)?.groupValues?.get(1) ?: return
        val type = TYPE.find(line)?.groupValues?.get(1)
        if (pending.size >= MAX_PENDING) pending.remove(pending.keys.first())
        pending[creatorThread] = Info(programName(image), type)
    }

    companion object {
        const val MAX_KEPT = 200
        private const val MAX_PROCESSES = 2000
        private const val MAX_PENDING = 100

        /** Die Meldung `winedbg --auto ...` kommt wenige Zeilen nach der Absturzmeldung; weiter weg gehört sie zu etwas anderem. */
        private const val DEBUGGER_WITHIN_LINES = 12

        private val PREFIX = Regex("^(\\d+\\.\\d+):([0-9a-fA-F]{4,8}):")
        private val THREAD = Regex("\\(thread ([0-9a-fA-F]{1,8})\\)")
        private val DEBUGGER = Regex("winedbg --auto (\\d+) \\d+")
        private val CREATED = Regex("NtCreateUserProcess L\"[^\"]*\" pid ([0-9a-fA-F]{1,8}) tid ([0-9a-fA-F]{1,8})")
        private val IMAGE = Regex("image L\"([^\"]*)\"")
        private val TYPE = Regex("--type=([A-Za-z0-9_-]{1,32})")

        private fun hex4(n: Int) = Integer.toHexString(n).padStart(4, '0')

        /** Der Dateiname aus einem Windows-Pfad (Wine schreibt `\\` doppelt). */
        fun programName(path: String): String? = path.substringAfterLast('\\').substringAfterLast('/').takeIf { it.isNotBlank() }?.take(64)

        /** Bequem für Tests und fertige Texte: eine ganze Ausgabe auswerten. */
        fun scan(lines: Iterable<String>): LabCrashScanner = LabCrashScanner().also { s -> lines.forEach { s.add(it) } }
    }
}

/** Die Texte für das Protokoll und die Liste der Läufe (rein). */
object LabCrashReport {
    /** Höchstens so viele Zeilen hat der Abschnitt `== Crashes ==` (ohne die Überschrift). */
    const val MAX_LINES = 40

    /** Die Zeile `Crashes: N (...)` für den Abschnitt. */
    fun summaryLine(scanner: LabCrashScanner): String {
        val parts = LabCrashKind.entries.joinToString(", ") { "${it.label}: ${scanner.count(it)}" }
        return "Crashes: ${scanner.total} ($parts)"
    }

    /**
     * Der Inhalt von `== Crashes ==`: Zusammenfassung, Vermerk bei gekürzter Ausgabe, dann je Absturz eine Zeile (Zeit seit Start, Prozess,
     * Programm, `--type=`); keine Befehlszeilen-Reste außer dem Wert von `--type=`. Höchstens [MAX_LINES] Zeilen.
     */
    fun lines(scanner: LabCrashScanner, outputCut: Boolean, omittedLines: Long = 0L): List<String> {
        val out = mutableListOf(summaryLine(scanner))
        out += if (outputCut) {
            "Output cut: $omittedLines lines from the middle of the Wine output are not in this file; the scan saw every line while the run was going, so the numbers cover the whole run."
        } else {
            "Output complete: the numbers match the Wine output of this file."
        }
        val shown = scanner.crashes
        val room = MAX_LINES - out.size - 1
        for ((i, c) in shown.take(room).withIndex()) out += crashLine(i + 1, c)
        val rest = scanner.total - minOf(shown.size, room)
        if (rest > 0) out += "(+$rest more not listed)"
        return out.take(MAX_LINES)
    }

    fun crashLine(number: Int, c: LabCrash): String =
        "#$number ${c.kind.label}, " +
            (c.elapsedSec?.let { "after $it s" } ?: "time unknown") +
            ", pid ${c.pid ?: "unknown"}, program ${c.program ?: "unknown"}, type=${c.type ?: "unknown"}"
}
