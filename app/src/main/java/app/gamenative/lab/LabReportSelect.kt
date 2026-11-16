package app.gamenative.lab

import app.gamenative.report.LogKind
import app.gamenative.report.ReportBuilder
import app.gamenative.report.ReportRedactor
import app.gamenative.report.ReportSource
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Auswahl der Dateien für den "Labor-Bericht": die Protokolle des Labors und die jüngsten Protokolle von Battle.net aus dem Prefix.
 * Der Aufbau der Zip und das Schwärzen übernimmt der vorhandene [ReportBuilder] mit [ReportRedactor]; hier steht nur, was hinein darf
 * und wie viel. Rein (nur `java.io.File`), JUnit-getestet.
 */
object LabReportSelect {
    /** Höchstens so viele Battle.net-Protokolle (die jüngsten). */
    const val MAX_BNET_LOGS = 20

    /** Zusätzlich höchstens so viele Protokolle des Update Agent (`Agent-*.log`, `AgentErrors-*.log` u. ä., die jüngsten). */
    const val MAX_AGENT_LOGS = 30

    /** Höchstens so viele Bytes je Battle.net-Protokoll (vom Ende). */
    const val MAX_BNET_LOG_BYTES = 2L * 1024 * 1024

    /** Höchstens so viele Protokolle des Labors (die jüngsten) und Bytes je Protokoll. */
    const val MAX_LAB_LOGS = 5
    const val MAX_LAB_LOG_BYTES = 3_500_000L

    /** Das Protokoll der geladenen und gewählten Pakete (Größe, SHA-256), geschrieben von `LabPackageInstaller`. */
    const val INSTALL_LOG = "pakete.log"

    private const val MAX_DEPTH = 6

    /** Priorität der Agent-Protokolle (kleiner = früher im Budget): hinter dem jüngsten Labor-Protokoll (0), vor den älteren (ab 2). */
    private const val AGENT_PRIORITY = 1

    /** Die Ordner, in denen Battle.net Protokolle ablegt: `ProgramData/Battle.net...` und `users/<Name>/AppData/<Zweig>/Battle.net...`. */
    fun battleNetRoots(driveC: File): List<File> {
        val roots = mutableListOf<File>()
        fun battleNetDirs(parent: File) {
            parent.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("Battle.net", ignoreCase = true) }.forEach { roots += it }
        }
        driveC.listFiles().orEmpty().firstOrNull { it.isDirectory && it.name.equals("ProgramData", ignoreCase = true) }?.let { battleNetDirs(it) }
        for (user in driveC.listFiles().orEmpty().firstOrNull { it.isDirectory && it.name.equals("users", ignoreCase = true) }?.listFiles().orEmpty()) {
            val appData = user.listFiles().orEmpty().firstOrNull { it.isDirectory && it.name.equals("AppData", ignoreCase = true) } ?: continue
            for (branch in appData.listFiles().orEmpty().filter { it.isDirectory }) battleNetDirs(branch)
        }
        return roots
    }

    /** Ein Protokoll des Update Agent: Dateiname beginnt mit `Agent` (`Agent-2026….log`, `AgentErrors-….log`). */
    fun isAgentLog(f: File): Boolean = f.name.startsWith("Agent", ignoreCase = true)

    /** Die jüngsten [MAX_BNET_LOGS] Dateien `*.log` unter den [battleNetRoots], ohne Agent-Protokolle (neueste zuerst). */
    fun battleNetLogs(driveC: File): List<File> =
        allLogs(driveC).filter { !isAgentLog(it) }.sortedByDescending { it.lastModified() }.take(MAX_BNET_LOGS)

    /** Die jüngsten [MAX_AGENT_LOGS] Agent-Protokolle (neueste zuerst), zusätzlich zu [battleNetLogs]. */
    fun agentLogs(driveC: File): List<File> =
        allLogs(driveC).filter { isAgentLog(it) }.sortedByDescending { it.lastModified() }.take(MAX_AGENT_LOGS)

    private fun allLogs(driveC: File): List<File> {
        val logs = mutableListOf<File>()
        fun walk(dir: File, depth: Int) {
            for (f in dir.listFiles().orEmpty()) {
                if (f.isDirectory) {
                    if (depth < MAX_DEPTH) walk(f, depth + 1)
                } else if (f.isFile && f.extension.equals("log", ignoreCase = true) && ReportBuilder.isAllowed(f)) {
                    logs += f
                }
            }
        }
        battleNetRoots(driveC).forEach { walk(it, 0) }
        return logs
    }

    /** Die Protokolle des Labors (`lab-*.log`), neueste zuerst. */
    fun labLogs(protocolDir: File): List<File> =
        protocolDir.listFiles { f -> f.isFile && f.name.startsWith("lab-") && ReportBuilder.isAllowed(f) }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .take(MAX_LAB_LOGS)

    /**
     * Die Quellen der Zip: Protokolle des Labors zuerst (Priorität 0, 1, ...), die Agent-Protokolle (bis 30) und die Battle.net-Protokolle (bis 20)
     * mit dem Pfad im Prefix als Name (`battlenet/ProgramData/Battle.net/Logs/x.log`).
     */
    fun sources(protocolDir: File, driveC: File): List<ReportSource> {
        val list = mutableListOf<ReportSource>()
        labLogs(protocolDir).forEachIndexed { i, f ->
            list += ReportSource("labor/${f.name}", f, LogKind.GENERAL, i, MAX_LAB_LOG_BYTES)
        }
        File(protocolDir, INSTALL_LOG).takeIf { it.isFile }?.let { list += ReportSource("labor/$INSTALL_LOG", it, LogKind.GENERAL, 6, MAX_LAB_LOG_BYTES) }
        val prefix = driveC.absoluteFile.toPath()
        fun rel(f: File) = runCatching { prefix.relativize(f.absoluteFile.toPath()).toString().replace('\\', '/') }.getOrDefault(f.name)
        // Agent-Protokolle sind klein und gehören zum Lauf: gleich nach dem jüngsten Labor-Protokoll, damit das Budget der großen Labor-Protokolle sie nicht verdrängt.
        agentLogs(driveC).forEach { f -> list += ReportSource("battlenet/${rel(f)}", f, LogKind.GENERAL, AGENT_PRIORITY, MAX_BNET_LOG_BYTES) }
        battleNetLogs(driveC).forEachIndexed { i, f ->
            list += ReportSource("battlenet/${rel(f)}", f, LogKind.GENERAL, 10 + i, MAX_BNET_LOG_BYTES)
        }
        return list
    }

    /** Baut die Zip in den Speicher (für Tests und kleine Berichte) und gibt die Namen der Einträge zurück. */
    fun writeZip(
        sources: List<ReportSource>,
        deviceText: String,
        redactor: ReportRedactor,
        out: java.io.OutputStream,
        extras: List<Pair<String, String>> = emptyList(),
        logcat: String = "",
    ): List<String> = ReportBuilder.write(out, deviceText, logcat, sources, redactor, extras)

    fun zipBytes(sources: List<ReportSource>, deviceText: String, redactor: ReportRedactor): Pair<ByteArray, List<String>> {
        val bytes = ByteArrayOutputStream()
        val names = writeZip(sources, deviceText, redactor, bytes)
        return bytes.toByteArray() to names
    }
}
