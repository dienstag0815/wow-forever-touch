package app.gamenative.lab

import java.io.File
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Was das Labor-Protokoll über den Update Agent von Battle.net festhält (Abschnitt `== Agent ==`): Dateien unter `ProgramData\Battle.net\Agent`,
 * Rechnerart aus dem PE-Kopf, Länge und Port aus `Agent.dat`, Erreichbarkeit von `GET /agent` aus der App, Starts und Enden der Agent-Prozesse
 * aus Wines Ausgabe. Es wird nie Inhalt einer Datei oder einer Antwort festgehalten (nur Namen, Größen, Zeiten, Codes und Längen).
 * Dateizugriffe nur lesend; die Logik ist sonst rein und JUnit-getestet.
 */
object LabAgentFiles {
    /** Höchstens so viele Zeilen hat die Dateiliste. */
    const val MAX_LIST_LINES = 80

    /** Der Abschnitt liest höchstens so viele Einträge, auch bei einem unerwartet großen Ordner. */
    private const val MAX_SCAN = 1500
    private const val MAX_DEPTH = 6
    const val AGENT_DAT = "Agent.dat"
    const val PRODUCT_DB = "product.db"
    const val HELPER = "AgentHelper.exe"
    private val VERSION_DIR = Regex("^Agent\\.(\\d+)$", RegexOption.IGNORE_CASE)

    private fun child(dir: File, name: String, ignoreCase: Boolean = true): File? =
        dir.listFiles().orEmpty().firstOrNull { it.name.equals(name, ignoreCase = ignoreCase) }

    /** `<C>/ProgramData/Battle.net/Agent` oder null. */
    fun agentDir(driveC: File): File? {
        val programData = child(driveC, "ProgramData")?.takeIf { it.isDirectory } ?: return null
        val bnet = child(programData, "Battle.net")?.takeIf { it.isDirectory } ?: return null
        return child(bnet, "Agent")?.takeIf { it.isDirectory }
    }

    /** Die Ordner `Agent.<Zahl>` im Agent-Ordner, die höchste Zahl zuerst. */
    fun versionDirs(agentDir: File): List<File> =
        agentDir.listFiles().orEmpty().filter { it.isDirectory && VERSION_DIR.matches(it.name) }
            .sortedByDescending { VERSION_DIR.find(it.name)?.groupValues?.get(1)?.toLongOrNull() ?: -1L }

    /** `Agent.exe` der neuesten Fassung (`Agent.<Zahl>/Agent.exe`) oder null. */
    fun newestAgentExe(agentDir: File): File? =
        versionDirs(agentDir).firstNotNullOfOrNull { dir -> child(dir, "Agent.exe")?.takeIf { it.isFile } }

    /** Der Stub `Agent/Agent.exe` (der Wechsler, den Battle.net startet). */
    fun stubAgentExe(agentDir: File): File? = child(agentDir, "Agent.exe")?.takeIf { it.isFile }

    /** `AgentHelper.exe`: erste Fundstelle unter den Ordnern `Battle.net...` in `ProgramData` und `Program Files...` (begrenzt). */
    fun helperExe(driveC: File): File? {
        var seen = 0
        fun find(dir: File, depth: Int): File? {
            for (f in dir.listFiles().orEmpty().sortedBy { it.name }) {
                if (++seen > MAX_SCAN * 2) return null
                if (f.isFile && f.name.equals(HELPER, ignoreCase = true)) return f
                if (f.isDirectory && depth < MAX_DEPTH) find(f, depth + 1)?.let { return it }
            }
            return null
        }
        for (top in driveC.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }) {
            val n = top.name
            if (!n.equals("ProgramData", ignoreCase = true) && !n.startsWith("Program Files", ignoreCase = true)) continue
            for (app in top.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("Battle.net", ignoreCase = true) }.sortedBy { it.name }) {
                find(app, 0)?.let { return it }
            }
        }
        return null
    }

    /** Pfad relativ zu [driveC] mit `/`. */
    fun relative(driveC: File, f: File): String =
        runCatching { driveC.absoluteFile.toPath().relativize(f.absoluteFile.toPath()).toString().replace('\\', '/') }.getOrDefault(f.name)

    /** Alle Dateien unter [dir] (Tiefe und Zahl begrenzt) mit Pfad relativ zu [driveC]; die zweite Angabe: die Suche wurde abgebrochen. */
    fun scan(driveC: File, dir: File): Pair<List<LabFileEntry>, Boolean> {
        val out = ArrayList<LabFileEntry>()
        var cut = false
        fun walk(d: File, depth: Int) {
            for (f in d.listFiles().orEmpty().sortedBy { it.name }) {
                if (out.size >= MAX_SCAN) {
                    cut = true
                    return
                }
                if (f.isDirectory) {
                    if (depth < MAX_DEPTH) walk(f, depth + 1)
                } else {
                    out += LabFileEntry(relative(driveC, f), f.length(), f.lastModified())
                }
            }
        }
        walk(dir, 0)
        return out to cut
    }

    /**
     * Die Dateiliste als Zeilen (`Pfad | Größe | Datum`): zuerst die Dateien, auf die es ankommt (`Agent.dat`, `product.db`, Stub, `Agent.exe` je
     * Fassung, `AgentHelper.exe`; fehlt eine, steht sie als `(missing) <Name>`), dann der übrige Inhalt außer `Logs` nach Pfad, dann die Protokolle
     * (jüngste zuerst). Höchstens [maxLines] Zeilen; was nicht mehr hineinpasst, steht als Zähler in der letzten Zeile.
     */
    fun listLines(agentDirPath: String, entries: List<LabFileEntry>, helper: LabFileEntry?, scanCut: Boolean, maxLines: Int = MAX_LIST_LINES): List<String> {
        fun key(e: LabFileEntry) = e.path
        fun isNamed(e: LabFileEntry, name: String) = e.path.substringAfterLast('/').equals(name, ignoreCase = true)
        val direct = entries.filter { it.path.removePrefix("$agentDirPath/").let { rest -> !rest.contains('/') } }
        val required = mutableListOf<String>()
        val picked = LinkedHashMap<String, LabFileEntry>()
        for (name in listOf(AGENT_DAT, PRODUCT_DB)) {
            val hit = direct.firstOrNull { isNamed(it, name) }
            if (hit != null) picked[key(hit)] = hit else required += "(missing) $agentDirPath/$name"
        }
        direct.filter { isNamed(it, "Agent.exe") }.forEach { picked[key(it)] = it }
        val versionExes = entries.filter { e ->
            val rest = e.path.removePrefix("$agentDirPath/")
            rest.count { it == '/' } == 1 && VERSION_DIR.matches(rest.substringBefore('/')) && isNamed(e, "Agent.exe")
        }.sortedByDescending { VERSION_DIR.find(it.path.removePrefix("$agentDirPath/").substringBefore('/'))?.groupValues?.get(1)?.toLongOrNull() ?: -1L }
        versionExes.forEach { picked[key(it)] = it }
        if (helper != null) picked[key(helper)] = helper else required += "(missing) $HELPER"
        val rest = entries.filter { key(it) !in picked && !it.path.removePrefix("$agentDirPath/").startsWith("Logs/", ignoreCase = true) && !it.path.contains("/Logs/", ignoreCase = true) }
        val logs = entries.filter { key(it) !in picked && (it.path.removePrefix("$agentDirPath/").startsWith("Logs/", ignoreCase = true) || it.path.contains("/Logs/", ignoreCase = true)) }
            .sortedByDescending { it.modifiedMs }
        val lines = ArrayList<String>()
        lines += required
        picked.values.forEach { lines += fileLine(it) }
        rest.forEach { lines += fileLine(it) }
        logs.forEach { lines += fileLine(it) }
        if (lines.size <= maxLines) {
            if (scanCut) lines += "(list cut off: more than $MAX_SCAN entries)"
            return lines.take(maxLines)
        }
        val shown = lines.take(maxLines - 1)
        return shown + "(${lines.size - shown.size} more not listed${if (scanCut) ", scan cut off" else ""})"
    }

    fun fileLine(e: LabFileEntry) = "${e.path} | ${e.size} | ${LabProtocol.timestamp(e.modifiedMs)}"

    /** `Agent.dat` lesen: Länge und, nur wenn der Inhalt (ohne Leerraum am Rand) aus Ziffern besteht und ein Port ist, der Port. */
    fun readDat(agentDir: File?): LabAgentDat {
        val f = agentDir?.let { child(it, AGENT_DAT) }?.takeIf { it.isFile } ?: return LabAgentDat(null, null, false)
        val length = f.length()
        val head = runCatching { RandomAccessFile(f, "r").use { raf -> ByteArray(minOf(length, 64L).toInt()).also { raf.readFully(it) } } }.getOrNull()
            ?: return LabAgentDat(length, null, false)
        return LabAgentDat.parse(head, length)
    }

    /** Der Dateiteil des Abschnitts (ohne Erreichbarkeit und Prozesse): erst die Kurzangaben (Ordner, Port, Binärdateien), dann die Dateiliste. */
    fun facts(driveC: File): LabAgentFacts {
        val dir = agentDir(driveC)
        val out = ArrayList<String>()
        if (dir == null) {
            out += "Agent folder: ProgramData/Battle.net/Agent does not exist"
        } else {
            val agentPath = relative(driveC, dir)
            out += "Agent folder: $agentPath"
            out += "Agent version folders: " + versionDirs(dir).joinToString(", ") { it.name }.ifEmpty { "(none)" }
        }
        out += LabAgentDat.line(dir?.let { readDat(it) } ?: LabAgentDat(null, null, false))
        out += "Binaries (size, machine type from the PE header):"
        val newest = dir?.let { newestAgentExe(it) }
        out += "  Agent.exe (newest version folder${newest?.parentFile?.name?.let { " $it" } ?: ""}): " + LabPeHeader.describe(newest)
        out += "  Agent.exe (stub in the Agent folder): " + LabPeHeader.describe(dir?.let { stubAgentExe(it) })
        val helper = helperExe(driveC)
        out += "  $HELPER${helper?.let { " (${relative(driveC, it)})" } ?: ""}: " + LabPeHeader.describe(helper)
        val files = ArrayList<String>()
        if (dir != null) {
            val (entries, cut) = scan(driveC, dir)
            val agentPath = relative(driveC, dir)
            files += "Files under $agentPath (path | size | date), ${entries.size} found${if (cut) "+" else ""}:"
            val helperEntry = helper?.let { LabFileEntry(relative(driveC, it), it.length(), it.lastModified()) }
            listLines(agentPath, entries, helperEntry, cut).forEach { files += "  $it" }
        } else {
            files += "Files: (no Agent folder)"
        }
        return LabAgentFacts(out, files)
    }
}

/** [head]: Kurzangaben des Abschnitts, [files]: die Dateiliste (steht am Ende des Abschnitts). */
data class LabAgentFacts(val head: List<String>, val files: List<String>)

/** `Agent.dat`: Länge und der Port, falls der Inhalt nur aus Ziffern besteht und ein gültiger Port ist (1..65535). */
data class LabAgentDat(val length: Long?, val port: Int?, val digitsOnly: Boolean) {
    companion object {
        /** [head] sind die ersten Bytes (höchstens 64), [length] die Länge der Datei. Ein Wert, der kein Port ist, wird nie ausgegeben. */
        fun parse(head: ByteArray, length: Long): LabAgentDat {
            if (length > head.size) return LabAgentDat(length, null, false)
            val text = String(head, Charsets.ISO_8859_1).trim { it == ' ' || it == '\t' || it == '\r' || it == '\n' || it == '\u0000' }
            val digits = text.isNotEmpty() && text.all { it in '0'..'9' }
            val port = if (digits && text.length <= 5) text.toInt().takeIf { it in 1..65535 } else null
            return LabAgentDat(length, port, digits)
        }

        fun line(d: LabAgentDat): String = when {
            d.length == null -> "Agent.dat: missing"
            d.port != null -> "Agent.dat: ${d.length} bytes, digits only, port ${d.port}"
            d.digitsOnly -> "Agent.dat: ${d.length} bytes, digits only, but not a port number"
            else -> "Agent.dat: ${d.length} bytes, content is not a port number"
        }
    }
}

/** Rechnerart aus dem PE-Kopf; gelesen werden nur der DOS-Kopf (64 Bytes) und 6 Bytes am Offset `e_lfanew`. */
object LabPeHeader {
    private const val DOS_HEAD = 64
    private const val MAX_LFANEW = 1 shl 20

    fun machineName(code: Int): String = when (code) {
        0x014c -> "x86"
        0x8664 -> "x64"
        0xAA64 -> "ARM64"
        0xA641 -> "ARM64EC"
        0xA64E -> "ARM64X"
        0x01c4 -> "ARM (Thumb-2)"
        else -> "unknown (0x" + Integer.toHexString(code) + ")"
    }

    /** Offset des PE-Kopfs aus dem DOS-Kopf (`MZ`, 4 Bytes bei 0x3C) oder null. */
    fun peOffset(dos: ByteArray): Int? {
        if (dos.size < DOS_HEAD || dos[0] != 'M'.code.toByte() || dos[1] != 'Z'.code.toByte()) return null
        val off = (dos[0x3C].toInt() and 0xFF) or ((dos[0x3D].toInt() and 0xFF) shl 8) or ((dos[0x3E].toInt() and 0xFF) shl 16) or ((dos[0x3F].toInt() and 0xFF) shl 24)
        return off.takeIf { it in DOS_HEAD..MAX_LFANEW }
    }

    /** Der Maschinencode aus den 6 Bytes `PE\0\0` + 2 Bytes oder null. */
    fun machine(pe: ByteArray): Int? {
        if (pe.size < 6 || pe[0] != 'P'.code.toByte() || pe[1] != 'E'.code.toByte() || pe[2] != 0.toByte() || pe[3] != 0.toByte()) return null
        return (pe[4].toInt() and 0xFF) or ((pe[5].toInt() and 0xFF) shl 8)
    }

    /** `7379664 bytes, machine x64`, oder der Grund, warum die Art nicht zu lesen ist; für eine fehlende Datei `missing`. */
    fun describe(file: File?): String {
        if (file == null || !file.isFile) return "missing"
        val size = file.length()
        val machine = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val dos = ByteArray(DOS_HEAD)
                if (raf.length() < DOS_HEAD) return@use null
                raf.readFully(dos)
                val off = peOffset(dos) ?: return@use null
                if (raf.length() < off + 6L) return@use null
                raf.seek(off.toLong())
                val pe = ByteArray(6)
                raf.readFully(pe)
                machine(pe)
            }
        }.getOrElse { return "$size bytes, header not readable (${it.javaClass.simpleName})" }
        return if (machine == null) "$size bytes, not a PE file" else "$size bytes, machine ${machineName(machine)}"
    }
}

/** Eine Abfrage `GET /agent` an den Port aus `Agent.dat` (nur Statuscode, Länge der Antwort und ob JSON; nie Inhalt). */
object LabAgentProbe {
    const val PATH = "/agent"
    const val FIRST_AFTER_SEC = 20L
    const val EVERY_SEC = 60L
    const val MAX_TIMED = 30
    private const val CONNECT_MS = 1500
    private const val READ_MS = 2000
    private const val MAX_BYTES = 64 * 1024

    fun requestBytes(port: Int): ByteArray = "GET $PATH HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\nAccept: */*\r\n\r\n".toByteArray(Charsets.US_ASCII)

    /** Was von der Antwort bleibt: Statuscode (null = keine HTTP-Antwort), Bytes nach den Kopfzeilen, JSON ja/nein; [partial]: Lesen brach ab. */
    data class Answer(val status: Int?, val totalBytes: Int, val bodyBytes: Int, val json: Boolean, val partial: Boolean)

    fun parseAnswer(bytes: ByteArray, partial: Boolean): Answer {
        val text = String(bytes, Charsets.ISO_8859_1)
        val status = Regex("^HTTP/1\\.[01] (\\d{3})").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (status == null) return Answer(null, bytes.size, 0, false, partial)
        val end = text.indexOf("\r\n\r\n")
        val headers = if (end >= 0) text.substring(0, end) else text
        val body = if (end >= 0) text.substring(end + 4) else ""
        val typeJson = Regex("(?im)^content-type:[^\\r\\n]*json").containsMatchIn(headers)
        val t = body.trim()
        val bodyJson = (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))
        return Answer(status, bytes.size, body.length, typeJson || bodyJson, partial)
    }

    fun answerText(a: Answer): String = when {
        a.status == null -> "no HTTP answer (${a.totalBytes} bytes received${if (a.partial) ", read aborted" else ""})"
        else -> "status=${a.status} length=${a.bodyBytes} json=${if (a.json) "yes" else "no"}${if (a.partial) " (read aborted)" else ""}"
    }

    /** Die Fehlerart als kurzer Text, ohne Meldungstext. */
    fun errorKind(t: Throwable): String = when (t) {
        is ConnectException -> if (t.message?.contains("refused", ignoreCase = true) == true) "connection refused" else "connect failed"
        is SocketTimeoutException -> "timeout"
        is NoRouteToHostException -> "no route"
        is SecurityException -> "not allowed (SecurityException)"
        else -> t.javaClass.simpleName.ifEmpty { "error" }
    }

    /** Die Zeile für das Protokoll: `+20s port=1120 status=200 length=57 json=yes`. [label] ist `+20s` oder `end`. */
    fun line(label: String, port: Int?, outcome: String): String = "  $label  " + (port?.let { "port=$it  " } ?: "") + outcome

    /** Führt die Abfrage aus (blockierend, höchstens einige Sekunden); nie auf dem Haupt-Thread aufrufen. Gibt den Ergebnistext zurück. */
    fun run(port: Int): String {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress("127.0.0.1", port), CONNECT_MS)
            socket.soTimeout = READ_MS
            socket.getOutputStream().write(requestBytes(port))
            socket.getOutputStream().flush()
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            var partial = false
            try {
                val input = socket.getInputStream()
                while (buf.size() < MAX_BYTES) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
                if (buf.size() >= MAX_BYTES) partial = true
            } catch (e: java.io.IOException) {
                partial = true
                if (buf.size() == 0) return "error=${errorKind(e)} (while reading)"
            }
            answerText(parseAnswer(buf.toByteArray(), partial))
        } catch (e: Throwable) {
            "error=${errorKind(e)}"
        } finally {
            runCatching { socket.close() }
        }
    }
}

/**
 * Starts und Enden von Prozessen, deren Programmname `Agent` enthält, aus Wines Ausgabe (`+process`). Start: `NtCreateUserProcess L"<Pfad>" pid <hex> tid <hex>`;
 * Ende: `NtTerminateProcess handle 0xffffffff…, exit_code <n>, process_exiting 1` vom Hauptthread des Prozesses (Wine beendet sich selbst). Endet ein
 * Prozess ohne diese Meldung (etwa durch `wineserver -k`), steht kein Ende da; die Zusammenfassung zählt das.
 */
class LabAgentProcessScanner {
    private class Proc(val pid: String, val program: String, var ended: Boolean = false)

    private val events = ArrayList<String>()
    private val byTid = HashMap<String, Proc>()
    private var started = 0
    private var endedWithMessage = 0
    private var dropped = 0

    val startCount: Int get() = started

    fun add(line: String, elapsedMs: Long?) {
        if (!line.contains("NtCreateUserProcess") && !line.contains("NtTerminateProcess")) return
        val prefix = PREFIX.find(line) ?: return
        val stamp = prefix.groupValues[1]
        val thread = prefix.groupValues[2].lowercase().padStart(4, '0')
        val created = CREATED.find(line)
        if (created != null) {
            val program = LabCrashScanner.programName(created.groupValues[1]) ?: return
            if (!program.contains("agent", ignoreCase = true)) return
            val pid = created.groupValues[2].lowercase().padStart(4, '0')
            val tid = created.groupValues[3].lowercase().padStart(4, '0')
            if (byTid.size >= MAX_TRACKED) return
            byTid[tid] = Proc(pid, program)
            started++
            keep("${time(elapsedMs)}  wine=$stamp  start  pid=$pid  program=$program")
            return
        }
        val term = TERMINATE.find(line) ?: return
        val proc = byTid[thread] ?: return
        if (proc.ended) return
        proc.ended = true
        endedWithMessage++
        keep("${time(elapsedMs)}  wine=$stamp  end    pid=${proc.pid}  program=${proc.program}  exit code ${term.groupValues[1]}")
    }

    private fun time(elapsedMs: Long?): String = elapsedMs?.let { "+" + "%.1f".format(java.util.Locale.ROOT, it / 1000.0) + "s" } ?: "+?s"

    private fun keep(line: String) {
        if (events.size < MAX_KEPT) events += line else dropped++
    }

    /** Höchstens [MAX_LINES] Zeilen: Starts und Enden in der Reihenfolge, dann die Zusammenfassung. */
    fun lines(): List<String> {
        val out = ArrayList<String>()
        if (events.isEmpty()) {
            out += "(no start of a process named Agent* in the Wine output)"
        } else {
            val room = MAX_LINES - 1
            if (events.size + dropped <= room) {
                out += events
            } else {
                val front = room / 2
                val back = room - front - 1
                val omitted = events.size - front - back + dropped
                out += events.take(front)
                out += "($omitted events in the middle left out)"
                out += events.takeLast(back)
            }
        }
        out += "Started: $started, ended with a message: $endedWithMessage, without an end message: ${started - endedWithMessage} (running at the end of the log or killed)"
        return out.take(MAX_LINES)
    }

    companion object {
        const val MAX_LINES = 40
        private const val MAX_KEPT = 400
        private const val MAX_TRACKED = 400
        private val PREFIX = Regex("^(\\d+\\.\\d+):([0-9a-fA-F]{4,8}):")
        private val CREATED = Regex("NtCreateUserProcess L\"([^\"]*)\" pid ([0-9a-fA-F]{1,8}) tid ([0-9a-fA-F]{1,8})")
        private val TERMINATE = Regex("NtTerminateProcess handle 0x[fF]{8,16}, exit_code (-?\\w+), process_exiting 1")
    }
}

/** Zusammenbau des Abschnitts `== Agent ==` und sein Einsetzen in das Protokoll. */
object LabAgentReport {
    const val TITLE = "Agent"

    /** Die Zeilen des Abschnitts: Kurzangaben, Erreichbarkeit ([probes]), Prozesse ([processes]), zuletzt die Dateiliste. */
    fun lines(facts: LabAgentFacts, probes: List<String>, processes: List<String>): List<String> = buildList {
        add("Update Agent facts (no file content is recorded, only names, sizes, dates, codes and lengths)")
        addAll(facts.head)
        add("Reachability (GET http://127.0.0.1:<port>${LabAgentProbe.PATH} from the app process; status, answer length and JSON yes/no only):")
        if (probes.isEmpty()) add("  (no request was made)") else addAll(probes)
        add("Agent processes in the Wine output (time since start of the run; wine = Wine time stamp):")
        processes.forEach { add("  $it") }
        addAll(facts.files)
    }

    /** Setzt den Abschnitt vor `== End ==` ein; null, wenn die Marke fehlt (dann hängt der Aufrufer ihn hinten an). */
    fun insertBeforeEnd(text: String, lines: List<String>): String? {
        val at = text.lastIndexOf(END_MARKER)
        if (at < 0) return null
        return text.substring(0, at) + sectionText(lines) + text.substring(at)
    }

    fun sectionText(lines: List<String>): String = "\n== $TITLE ==\n" + lines.joinToString("") { it + "\n" }

    private const val END_MARKER = "\n== End ==\n"
}
