package app.gamenative.lab

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Angaben für den Kopf eines Protokolls (alles Text, damit der Kopf ohne Android getestet werden kann). */
data class LabProtocolInfo(
    val startedMs: Long,
    val appVersion: String,
    val device: String,
    val android: String,
    val settings: LabSettings,
    val plan: LabPlan,
    val freeBytes: Long,
    /** Zeile `Game copy: ...` im Kopf: Laufwerk und Windows-Pfad der Kopie oder `off`. */
    val gameCopyLine: String = "Game copy: off",
    /** Zeile `Game controls: off|on (manual|auto)`; ein Lauf beginnt immer mit „off (manual)“, Umschaltungen stehen als Zeilen im Verlauf. */
    val gameControlsLine: String = LabGameControlsLogic.headerLine(LabGcState()),
    /** Zeile `Game setup on copy: ...`; null = die Einrichtung der Kopie lief nicht (keine Zeile). */
    val gameSetupLine: String? = null,
    /** Zeilen `Start mode:` und `Started from:`; leer = der Aufrufer weiß es nicht (keine Zeilen). */
    val startLines: List<String> = emptyList(),
)

/** Eine Datei der Dateiliste (nur Name, Größe, Datum). */
data class LabFileEntry(val path: String, val size: Long, val modifiedMs: Long)

/**
 * Aufbau der Protokolle des Labors: je Start eine Textdatei. Kopf (Zeit, Einstellungen, Befehlszeile, Umgebung mit geschwärzten
 * Geheimnissen), dann Wines Ausgabe zeilenweise, Prozesslisten, am Ende Ende-Code, Laufzeit und Dateiliste. Rein, JUnit-getestet; die
 * Datei schreibt `LabRun`.
 */
object LabProtocol {
    const val FORMAT = 1

    /** Die Zeile `Crashes:` im Kopf, solange der Lauf nicht zu Ende ist; `LabRun` ersetzt sie am Ende durch [crashesLine]. */
    const val CRASHES_PENDING = "Crashes: unknown (the run has not finished)"

    fun crashesLine(count: Int): String = "Crashes: $count"

    /**
     * Setzt am Ende des Laufs die Abschnitte `== Translator ==` und `== Crashes ==` direkt nach dem Kopf (vor `== Settings ==`) ein und ersetzt die
     * Zeile `Crashes:` im Kopf. Null, wenn die Marken nicht da sind (dann hängt der Aufrufer die Abschnitte hinten an).
     */
    fun insertAfterHead(text: String, translator: List<String>, crashes: List<String>, crashCount: Int): String? {
        val marker = "\n== Settings ==\n"
        val at = text.indexOf(marker)
        val pending = text.indexOf(CRASHES_PENDING)
        if (at < 0 || pending < 0 || pending > at) return null
        val sections = buildString {
            appendLine("== Translator ==")
            translator.forEach { appendLine(it) }
            appendLine()
            appendLine("== Crashes ==")
            crashes.forEach { appendLine(it) }
        }
        val patched = text.replaceFirst(CRASHES_PENDING, crashesLine(crashCount))
        val cut = patched.indexOf(marker)
        return patched.substring(0, cut + 1) + sections + patched.substring(cut)
    }

    fun timestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    /** Dateiname eines Protokolls: `lab-<Zeit>-<Ziel>.log`. */
    fun fileName(startedMs: Long, target: LabTarget): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(startedMs))
        return "lab-$stamp-${target.name.lowercase()}.log"
    }

    fun header(info: LabProtocolInfo): String = buildString {
        val s = info.settings
        val p = info.plan
        appendLine("WoW Forever lab protocol (format $FORMAT)")
        appendLine("Started: ${timestamp(info.startedMs)}")
        appendLine("App version: ${info.appVersion}")
        appendLine("Device: ${info.device}")
        appendLine("Android: ${info.android}")
        appendLine("Free space (app data): ${if (info.freeBytes < 0) "unknown" else "${info.freeBytes / (1024 * 1024)} MB"}")
        appendLine("Stability: ${p.stability.level} ${p.stability.englishName}")
        appendLine("Translator log: ${if (p.translatorLog) "on" else "off"}")
        appendLine(info.gameCopyLine)
        info.gameSetupLine?.let { appendLine(it) }
        appendLine(info.gameControlsLine)
        appendLine(LabLoginWay.headerLine(s))
        info.startLines.forEach { appendLine(it) }
        appendLine(LabSignInKeys.headerLine(s.signInKeys))
        appendLine(CRASHES_PENDING)
        appendLine()
        appendLine("== Settings ==")
        appendLine("Wine package: ${s.wine.ifBlank { "bundled" }} -> ${p.wineVersion}")
        appendLine("Translator: ${s.translator.key} -> emulator=${p.emulator}${p.fexVersion?.let { " fex=$it" } ?: ""}")
        appendLine("Start line (Battle.net): ${s.battleNetLine}")
        appendLine("Any program: ${s.anyProgram}")
        appendLine("WINEDEBUG: ${s.wineDebug.key} (${p.env.firstOrNull { it.first == "WINEDEBUG" }?.second ?: s.wineDebug.value})")
        appendLine("DLL overrides: ${s.dllOverrides.replace('\n', ' ').trim()}")
        appendLine("Extra environment (lines): ${s.extraEnv.lines().count { it.isNotBlank() }}")
        appendLine("Screen size: ${p.screenSize}")
        appendLine("Same Windows identity as the game: ${if (s.sameIdentity) "on" else "off"}")
        appendLine("X11 driver options: ${s.x11.key}")
        appendLine()
        appendLine("== Command ==")
        appendLine("Target: ${p.target.name.lowercase()}")
        appendLine("Start way: ${startWayText(p.startWay)}")
        appendLine("Program: ${p.program}")
        appendLine("Absolute Windows path: ${if (LabCommand.isAbsoluteWindowsPath(p.program)) "yes" else "no"}")
        appendLine("Arguments: ${if (p.args.isEmpty()) "(none)" else p.args.joinToString(" ") { LabCommand.quote(it) }}")
        appendLine()
        appendLine("== Environment (values of names with PASS, TOKEN, SECRET, KEY hidden) ==")
        LabCommand.redactedEnvLines(p.env).forEach { appendLine(it) }
        appendLine()
        appendLine("== Wine output ==")
    }

    fun startWayText(way: LabStartWay): String = when (way) {
        LabStartWay.DIRECT -> "direct (wine explorer /desktop=shell,<size> <program> <arguments>, like the ARM64 program of the game, no winhandler.exe)"
        LabStartWay.WINHANDLER -> "winhandler (wine explorer /desktop=shell,<size> winhandler.exe \"<program>\" <arguments>)"
    }

    /** Eine Zeile der Prozessliste: Kennung und Befehlszeile (Nullbytes der Befehlszeile werden zu Leerzeichen). */
    fun processLine(pid: String, cmdline: String): String = "$pid ${cmdline.replace('\u0000', ' ').trim().take(300)}"

    fun processBlock(label: String, elapsedMs: Long, lines: List<String>): String = buildString {
        appendLine("-- Processes ($label, after ${elapsedMs / 1000} s): ${lines.size}")
        if (lines.isEmpty()) appendLine("(none)") else lines.forEach { appendLine("  $it") }
    }

    /** Abschnitt "Input": Zähler der Tasten (nie die Zeichen), Fokusfenster, Tastatur; Zeilen aus `LabInputStats.lines`. */
    fun inputBlock(lines: List<String>): String = buildString {
        appendLine()
        appendLine("== Input ==")
        if (lines.isEmpty()) appendLine("(no input counters)") else lines.forEach { appendLine(it) }
    }

    /** Abschnitt "Windows": Zeitverlauf der Fenster der obersten Ebene und die am Ende offenen (Zeilen aus `LabWindowsReport.lines`, Titel geschwärzt). */
    fun windowsBlock(lines: List<String>): String = buildString {
        appendLine()
        appendLine("== Windows ==")
        if (lines.isEmpty()) appendLine("(no window record)") else lines.forEach { appendLine(it) }
    }

    /** Eine Zeile je Datei: Pfad, Größe in Bytes, Datum (UTC). */
    fun fileBlock(entries: List<LabFileEntry>, truncated: Boolean): String = buildString {
        appendLine("== Battle.net files (name, size, date) ==")
        if (entries.isEmpty()) appendLine("(none found)")
        for (e in entries) appendLine("${e.path} | ${e.size} | ${timestamp(e.modifiedMs)}")
        if (truncated) appendLine("(list cut off)")
    }

    fun footer(exitCode: Int?, startedMs: Long, endedMs: Long, reason: String): String = buildString {
        appendLine()
        appendLine("== End ==")
        appendLine("Ended: ${timestamp(endedMs)}")
        appendLine("Runtime: ${(endedMs - startedMs) / 1000} s")
        appendLine("Reason: $reason")
        appendLine("Exit code of the Wine process: ${exitCode?.toString() ?: "unknown"}")
    }

    /** Sammelt die Dateiliste der Ordner `Battle.net...` unter `Program Files...` und `ProgramData` im Laufwerk C (nur Lesen). */
    fun scanBattleNetFiles(driveC: File, maxEntries: Int = 2000): Pair<List<LabFileEntry>, Boolean> {
        val out = mutableListOf<LabFileEntry>()
        var truncated = false
        fun walk(dir: File, rel: String, depth: Int) {
            if (truncated) return
            val children = dir.listFiles()?.sortedBy { it.name } ?: return
            for (f in children) {
                if (out.size >= maxEntries) {
                    truncated = true
                    return
                }
                val path = "$rel/${f.name}"
                if (f.isDirectory) {
                    if (depth < 8) walk(f, path, depth + 1)
                } else {
                    out += LabFileEntry(path, f.length(), f.lastModified())
                }
            }
        }
        for (top in driveC.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }) {
            val n = top.name
            val programFiles = n.startsWith("Program Files", ignoreCase = true)
            val programData = n.equals("ProgramData", ignoreCase = true)
            if (!programFiles && !programData) continue
            for (app in top.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("Battle.net", ignoreCase = true) }.sortedBy { it.name }) {
                walk(app, "$n/${app.name}", 0)
            }
        }
        return out to truncated
    }

    /**
     * Sucht `Battle.net.exe` unter `Program Files...\Battle.net...` (zuerst im Ordner selbst, dann eine Ebene tiefer, wo Battle.net seine
     * Versionsordner anlegt); gibt den Windows-Pfad zurück (`C:\Program Files (x86)\Battle.net\Battle.net.exe`) oder null.
     */
    fun findBattleNetExe(driveC: File): String? {
        for (top in driveC.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("Program Files", ignoreCase = true) }.sortedBy { it.name }) {
            for (app in top.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("Battle.net", ignoreCase = true) }.sortedBy { it.name }) {
                fun exeIn(dir: File) = dir.listFiles().orEmpty().firstOrNull { it.isFile && it.name.equals("Battle.net.exe", ignoreCase = true) }
                exeIn(app)?.let { return "C:\\${top.name}\\${app.name}\\${it.name}" }
                for (sub in app.listFiles().orEmpty().filter { it.isDirectory }.sortedByDescending { it.name }) {
                    exeIn(sub)?.let { return "C:\\${top.name}\\${app.name}\\${sub.name}\\${it.name}" }
                }
            }
        }
        return null
    }
}
