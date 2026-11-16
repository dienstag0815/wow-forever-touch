package app.gamenative.lab

import android.content.Context
import android.os.Build
import android.os.StatFs
import app.gamenative.BuildConfig
import com.winlator.core.ProcessHelper
import com.winlator.core.Callback
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * Das Protokoll eines Versuchs. `begin` schreibt den Kopf (Einstellungen, Befehlszeile, Umgebung), `attachOutput` hängt sich in die
 * Ausgabe von Wine (stdout und stderr), ein Zähler listet alle [SAMPLE_SECONDS] Sekunden die Prozesse, `finish` schreibt Prozessliste,
 * Dateiliste von Battle.net und das Ende. Es läuft nur, solange ein Versuch läuft: keine Hintergrundarbeit, kein Autostart.
 *
 * Die drei Einhängestellen in `XServerScreen` rufen [attachOutput], [onExit] und [onTerminated] nur für den Container `bnetlab`.
 */
object LabRun {
    const val SAMPLE_SECONDS = 15L

    /** Ein `begin` ohne Start des Gastprogramms (Fehler davor) sperrt das Spiel nicht länger als so lange. */
    private const val STALE_MS = 3 * 60 * 1000L

    private class Session(
        val context: Context,
        val file: File,
        val startedMs: Long,
        val plan: LabPlan,
    ) {
        val writer: BufferedWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
        /** Anfang und Ende von Wines Ausgabe; die Mitte entfällt bei Überlänge (siehe [LabOutputCap]). */
        val cap = LabOutputCap()
        /** Zählt Abstürze und fängt den Block des Übersetzers; sieht jede Zeile, auch die, die [cap] später in der Mitte weglässt. */
        val crashes = LabCrashScanner()
        val translator = LabTranslatorCapture()
        /** Starts und Enden der Prozesse `Agent*` aus Wines Ausgabe (Abschnitt `== Agent ==`). */
        val agentProcs = LabAgentProcessScanner()
        /** Die Zeilen der Abfragen `GET /agent` (der Zeitgeber schreibt, das Ende liest; nur unter [lock]). */
        val probes = ArrayList<String>()
        var prober: Thread? = null
        var lines = 0

        /** Zustand der Hänger-Erkennung zuletzt, ob sie je „kein Fenster“ gemeldet hat und ob der Nutzer „Launcher neu starten“ getippt hat (für die Liste der Läufe). */
        var watchState: LabWatchState? = null
        var noWindowSeen = false
        var restartRequested = false
        /** Stand des Schalters „Sign-in buttons“ beim Start des Versuchs (für die Anmelde-Knöpfe der Leiste). */
        var signInKeys = false
        var attached = false
        var finished = false
        var sampler: Thread? = null
        var callback: Callback<String>? = null
    }

    private val lock = Any()
    private var session: Session? = null

    /** Für Abschnitte, die andere Fäden in eine schon geschlossene Protokolldatei einsetzen (gleiche Sperre wie `finishAgentSection`). */
    internal fun <T> withFileLock(block: () -> T): T = synchronized(lock) { block() }

    /** Läuft gerade ein Versuch des Labors (begonnen und noch nicht beendet)? Ein nie gestarteter Versuch zählt nach [STALE_MS] nicht mehr. */
    fun isActive(nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val s = session ?: return@synchronized false
        if (s.finished) return@synchronized false
        s.attached || nowMs - s.startedMs < STALE_MS
    }

    /**
     * Ob der Container [containerId] sein Programm direkt startet (ohne `winhandler.exe`): nur für `bnetlab` und nur, wenn der Startplan
     * den direkten Weg trägt. Ohne laufenden Versuch gilt im Labor die Vorgabe (direkt). Für jeden anderen Container `false`.
     */
    fun startsDirect(containerId: String): Boolean {
        if (containerId != LabIds.CONTAINER_ID) return false
        val way = synchronized(lock) { session?.takeIf { !it.finished }?.plan?.startWay }
        return LabStartWay.isDirect(way)
    }

    /** Schreibt den Kopf des Protokolls und merkt den Versuch vor. Gibt die Protokolldatei zurück. */
    fun begin(context: Context, settings: LabSettings, plan: LabPlan, gameCopyLine: String = "Game copy: off", gameSetupLine: String? = null, startLines: List<String> = emptyList()): File {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val dir = LabDirs.logs(app).apply { mkdirs() }
        val file = File(dir, LabProtocol.fileName(now, plan.target))
        synchronized(lock) {
            session?.let { finishLocked(it, null, "replaced by a new start") }
            val s = Session(app, file, now, plan)
            s.signInKeys = settings.signInKeys
            val info = LabProtocolInfo(
                startedMs = now,
                appVersion = "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                settings = settings,
                plan = plan,
                freeBytes = runCatching { StatFs(app.filesDir.path).availableBytes }.getOrDefault(-1L),
                gameCopyLine = gameCopyLine,
                gameSetupLine = gameSetupLine,
                startLines = startLines,
            )
            s.writer.write(LabProtocol.header(info))
            s.writer.flush()
            session = s
            LabInput.begin(now)
            LabWindows.begin(now)
        }
        pruneOld(dir)
        return file
    }

    /** Hängt sich in die Ausgabe von Wine; Einhängestelle 1 in `XServerScreen`, nach dem Zurücksetzen der Callbacks. */
    fun attachOutput(containerId: String) {
        if (containerId != LabIds.CONTAINER_ID) return
        val s = synchronized(lock) { session?.takeIf { !it.finished } } ?: return
        val cb = Callback<String> { line -> append(s, line) }
        ProcessHelper.addDebugCallback(cb)
        synchronized(lock) {
            s.callback = cb
            s.attached = true
        }
        val thread = Thread({
            try {
                while (true) {
                    Thread.sleep(SAMPLE_SECONDS * 1000)
                    val current = synchronized(lock) { session?.takeIf { it === s && !it.finished } } ?: return@Thread
                    sample(current, "running")
                }
            } catch (_: InterruptedException) {
            }
        }, "lab-sampler").apply { isDaemon = true }
        synchronized(lock) { s.sampler = thread }
        thread.start()
        startProber(s)
    }

    /**
     * Fragt 20 s nach dem Start und danach alle 60 s (höchstens [LabAgentProbe.MAX_TIMED] Mal) `GET /agent` am Port aus `Agent.dat` ab. Eigener
     * Faden, nie unter der Sperre im Netz; jede Abfrage ist kurz begrenzt.
     */
    private fun startProber(s: Session) {
        val thread = Thread({
            try {
                for (i in 0 until LabAgentProbe.MAX_TIMED) {
                    val dueMs = s.startedMs + (LabAgentProbe.FIRST_AFTER_SEC + i * LabAgentProbe.EVERY_SEC) * 1000
                    val wait = dueMs - System.currentTimeMillis()
                    if (wait > 0) Thread.sleep(wait)
                    if (synchronized(lock) { session !== s || s.finished }) return@Thread
                    val line = probeLine(s.context, "+${(System.currentTimeMillis() - s.startedMs) / 1000}s")
                    synchronized(lock) { if (!s.finished) s.probes += line }
                }
            } catch (_: InterruptedException) {
            }
        }, "lab-agent-probe").apply { isDaemon = true }
        synchronized(lock) { s.prober = thread }
        thread.start()
    }

    /** Liest den Port aus `Agent.dat` und fragt ab; die Zeile für das Protokoll. Blockiert höchstens einige Sekunden. */
    private fun probeLine(context: Context, label: String): String = runCatching {
        val driveC = LabDirs.driveC(context)
        val dat = LabAgentFiles.readDat(LabAgentFiles.agentDir(driveC))
        val port = dat.port
        if (port == null) {
            LabAgentProbe.line(label, null, "skipped: " + (if (dat.length == null) "Agent.dat missing" else "Agent.dat holds no port"))
        } else {
            LabAgentProbe.line(label, port, LabAgentProbe.run(port))
        }
    }.getOrElse { LabAgentProbe.line(label, null, "error=${it.javaClass.simpleName}") }

    /** Hängt einen Abschnitt an das Protokoll des laufenden Versuchs (Windows-Kennung, Einhängestelle 4 in `XServerScreen`). */
    fun appendSection(containerId: String, title: String, lines: List<String>) {
        if (containerId != LabIds.CONTAINER_ID) return
        synchronized(lock) {
            val s = session?.takeIf { !it.finished } ?: return
            runCatching {
                s.writer.write("== $title ==" + System.lineSeparator())
                lines.forEach { s.writer.write(it); s.writer.write(System.lineSeparator()) }
                s.writer.flush()
            }
        }
    }

    /**
     * Einhängestelle 5 (`BionicProgramLauncherComponent`, nach `getFinalCommand`): der tatsächliche Wert von `HODLL` und was im Prefix
     * liegt. [upper] und [lower] sind die Ordner `.wine` der beiden Schichten des Overlays (leer = keine).
     */
    fun onLaunchEnvironment(containerId: String, hodll: String?, upper: String?, lower: String?) {
        if (containerId != LabIds.CONTAINER_ID) return
        val layers = listOf(
            LabPrefixFacts.Layer("upper", upper?.takeIf { it.isNotEmpty() }?.let { File(it) }),
            LabPrefixFacts.Layer("lower", lower?.takeIf { it.isNotEmpty() }?.let { File(it) }),
        )
        val lines = listOf(LabPrefixFacts.hodllLine(hodll)) + LabPrefixFacts.lines(layers)
        appendSection(containerId, "Launch environment (after getFinalCommand) and prefix files", lines)
    }

    /**
     * Eine Umschaltung der Spielsteuerung im Lauf ([LabGameControlsLogic.changeLine]) mit der Zeit seit dem Start; wird wie die Prozessblöcke
     * zwischen Wines Ausgabe in die Datei geschrieben. Ohne laufenden Versuch geschieht nichts.
     */
    fun gameControlsChanged(change: LabGcChange, applied: Boolean) {
        synchronized(lock) {
            val s = session?.takeIf { !it.finished } ?: return
            runCatching {
                s.writer.write(LabGameControlsLogic.changeLine(change, System.currentTimeMillis() - s.startedMs, applied))
                s.writer.write('\n'.code)
                s.writer.flush()
            }
        }
    }

    /** Millisekunden seit dem Start des laufenden Versuchs (für die Hänger-Erkennung der Tastenleiste), null ohne laufenden Versuch. */
    fun elapsedMs(nowMs: Long = System.currentTimeMillis()): Long? = synchronized(lock) {
        val s = session?.takeIf { !it.finished } ?: return@synchronized null
        nowMs - s.startedMs
    }

    /** Das Ziel des laufenden Versuchs (für „Launcher neu starten“), null ohne laufenden Versuch. */
    fun activeTarget(): LabTarget? = synchronized(lock) { session?.takeIf { !it.finished }?.plan?.target }

    /** Stand des Schalters „Sign-in buttons“ im laufenden Versuch; false ohne laufenden Versuch. */
    fun signInKeysOn(): Boolean = synchronized(lock) { session?.takeIf { !it.finished }?.signInKeys == true }

    /**
     * Eine Zeile zu einem Tipp auf „E-Mail“ / „Passwort“ ([LabSignInKeys.sentLine] und Verwandte, nie mit Wert); wird wie die Umschaltungen der
     * Spielsteuerung zwischen Wines Ausgabe in die Datei geschrieben. Ohne laufenden Versuch geschieht nichts.
     */
    fun signInKeyLine(line: String) = writeLine(line)

    /** Ein Zustandswechsel der Hänger-Erkennung ([LabWatch.changeLine]); wird wie die Umschaltungen der Spielsteuerung zwischen Wines Ausgabe in die Datei geschrieben. */
    fun watchChanged(change: LabWatchChange) {
        synchronized(lock) {
            session?.takeIf { !it.finished }?.let {
                it.watchState = change.to
                if (change.to == LabWatchState.NO_WINDOW) it.noWindowSeen = true
            }
        }
        writeLine(LabWatch.changeLine(change))
    }

    /**
     * Der Nutzer hat „Launcher neu starten“ getippt: schreibt die Zeile ins Protokoll und merkt das Ziel für das Angebot „Erneut starten“ auf der
     * Labor-Seite ([LabRestart]). Beendet nichts; das Beenden löst der Aufrufer auf dem Weg von „Exit“ aus. Gibt false zurück, wenn kein Versuch läuft.
     */
    fun restartRequested(): Boolean {
        val target = activeTarget() ?: return false
        val elapsed = elapsedMs() ?: return false
        writeLine(LabWatch.restartLine(elapsed))
        synchronized(lock) { session?.takeIf { !it.finished }?.restartRequested = true }
        LabRestart.offer(target)
        return true
    }

    private fun writeLine(text: String) {
        synchronized(lock) {
            val s = session?.takeIf { !it.finished } ?: return
            runCatching {
                s.writer.write(text)
                s.writer.write('\n'.code)
                s.writer.flush()
            }
        }
    }

    /** Einhängestelle 2: `exit()` in `XServerScreen`, vor dem Beenden der Wine-Prozesse. */
    fun onExit(containerId: String, reason: String) {
        if (containerId != LabIds.CONTAINER_ID) return
        val s = synchronized(lock) { session } ?: return
        synchronized(lock) { finishLocked(s, null, "exit requested: $reason") }
    }

    /** Einhängestelle 3: Ende des Wine-Prozesses mit dem Code [status]. */
    fun onTerminated(containerId: String, status: Int) {
        if (containerId != LabIds.CONTAINER_ID) return
        synchronized(lock) {
            val s = session ?: return
            if (!s.finished) {
                finishLocked(s, status, "Wine process ended")
            } else {
                // Schon abgeschlossen (Beenden durch den Nutzer): den Code nur nachtragen.
                runCatching { s.file.appendText("Late: exit code of the Wine process: $status\n") }
            }
        }
    }

    /** "Alles beenden" der Labor-Seite: schließt den Versuch ab; die Prozesse beendet der Aufrufer. */
    fun abort(reason: String) {
        synchronized(lock) { session?.let { finishLocked(it, null, reason) } }
    }

    private fun append(s: Session, line: String) {
        synchronized(lock) {
            if (s.finished) return
            s.crashes.add(line, System.currentTimeMillis() - s.startedMs)
            s.translator.add(line)
            s.agentProcs.add(line, System.currentTimeMillis() - s.startedMs)
            val head = s.cap.add(line) ?: return
            runCatching {
                s.writer.write(head)
                s.writer.write('\n'.code)
                s.lines++
                if (s.lines % 20 == 0) s.writer.flush()
            }
        }
    }

    private fun sample(s: Session, label: String) {
        val lines = processLines()
        synchronized(lock) {
            if (s.finished) return
            runCatching {
                s.writer.write(LabProtocol.processBlock(label, System.currentTimeMillis() - s.startedMs, lines))
                s.writer.flush()
            }
        }
    }

    /** Die Prozesse von Wine und Windows-Programmen (Befehlszeile enthält `wine` oder `.exe`), wie sie `ProcessHelper` erkennt. */
    fun processLines(): List<String> = runCatching {
        ProcessHelper.listRunningWineProcesses().distinct().map { pid ->
            val cmd = runCatching { File("/proc/$pid/cmdline").readText() }.getOrDefault("")
            LabProtocol.processLine(pid, cmd)
        }
    }.getOrDefault(emptyList())

    private fun finishLocked(s: Session, exitCode: Int?, reason: String) {
        if (s.finished) return
        s.finished = true
        s.callback?.let { ProcessHelper.removeDebugCallback(it) }
        s.sampler?.interrupt()
        s.prober?.interrupt()
        val ended = System.currentTimeMillis()
        // Welche Fenster der Lauf gesehen hat (aus den Ereignissen der Zeitleiste), solange sie noch da ist: `LabWindows.end()` löscht sie.
        val seen = runCatching { LabRunWindows.summarize(LabWindows.events()) }.getOrDefault(LabRunWindows.Seen(false, false))
        val outcome = LabRunOutcome(
            endReason = reason,
            watchEnd = s.watchState?.text.orEmpty(),
            noWindowSeen = s.noWindowSeen,
            mainWindowSeen = seen.mainWindow,
            gameWindowSeen = seen.gameWindow,
            restartRequested = s.restartRequested,
        )
        runCatching {
            s.writer.write(s.cap.tailText())
            s.writer.write(LabProtocol.inputBlock(LabInput.lines()))
            LabInput.end()
            s.writer.write(LabProtocol.windowsBlock(LabWindows.lines()))
            LabWindows.end()
            if (s.plan.target == LabTarget.STEP_TEST) s.writer.write(stepTestBlock(s.context))
            s.writer.write(LabProtocol.processBlock("end", ended - s.startedMs, processLines()))
            val (files, truncated) = LabProtocol.scanBattleNetFiles(LabDirs.driveC(s.context))
            s.writer.write(LabProtocol.fileBlock(files, truncated))
            s.writer.write(LabProtocol.footer(exitCode, s.startedMs, ended, reason))
            s.writer.flush()
            s.writer.close()
        }.onFailure { Timber.w(it, "Labor-Protokoll nicht abgeschlossen") }
        // Abdruck des Originalordners nach dem Ende (eigener Faden: Dateien lesen, nie auf dem Haupt-Thread).
        runCatching { LabOriginalCheck.finishAsync(s.context) }.onFailure { Timber.w(it, "Labor: Originalprüfung nicht gestartet") }
        runCatching { LabCopyFolderCheck.finishAsync(s.context) }.onFailure { Timber.w(it, "Labor: Abschnitt Kopie-Ordner nicht gestartet") }
        runCatching { insertSections(s) }.onFailure { Timber.w(it, "Labor-Protokoll: Abschnitte Translator/Crashes nicht eingesetzt") }
        runCatching { finishAgentSection(s, ended) }.onFailure { Timber.w(it, "Labor-Protokoll: Abschnitt Agent nicht gestartet") }
        runCatching {
            LabStore.addRun(s.context, LabRunHistory.entryOf(s.plan, s.startedMs, ended, s.crashes.total, s.cap.omittedLines > 0, outcome))
        }.onFailure { Timber.w(it, "Labor: Lauf nicht in die Liste aufgenommen") }
    }

    /**
     * Abschnitt `== Agent ==` am Lauf-Ende: Dateien, PE-Kopf, Port, die letzte Abfrage und die Agent-Prozesse. Läuft in einem eigenen Faden, weil die
     * Abfrage im Netz nicht auf dem Haupt-Thread laufen darf (das Ende kommt aus dem X-Server-Bildschirm); der Faden setzt den Abschnitt danach in die
     * schon geschlossene Datei ein. Wer den Bericht in den ersten Sekunden nach dem Ende baut, findet ihn noch nicht.
     */
    private fun finishAgentSection(s: Session, endedMs: Long) {
        val probes = ArrayList(s.probes)
        val processes = s.agentProcs.lines()
        val startedMs = s.startedMs
        Thread({
            val lines = runCatching {
                val facts = LabAgentFiles.facts(LabDirs.driveC(s.context))
                probes += probeLine(s.context, "end(+${(System.currentTimeMillis() - startedMs) / 1000}s)")
                LabAgentReport.lines(facts, probes, processes)
            }.getOrElse { listOf("(could not be collected: ${it.javaClass.simpleName})") }
            synchronized(lock) {
                runCatching {
                    val text = s.file.readText(Charsets.UTF_8)
                    val patched = LabAgentReport.insertBeforeEnd(text, lines) ?: (text + LabAgentReport.sectionText(lines))
                    val tmp = File(s.file.parentFile, s.file.name + ".tmp")
                    tmp.writeText(patched, Charsets.UTF_8)
                    if (!tmp.renameTo(s.file)) {
                        s.file.writeText(patched, Charsets.UTF_8)
                        tmp.delete()
                    }
                }.onFailure { Timber.w(it, "Labor-Protokoll: Abschnitt Agent nicht eingesetzt") }
            }
        }, "lab-agent-final").apply { isDaemon = true }.start()
    }

    /**
     * Nach dem Schließen der Datei: `== Translator ==` und `== Crashes ==` stehen direkt nach dem Kopf und die Zeile `Crashes:` im Kopf bekommt die
     * Zahl (die Datei ist höchstens einige MB groß). Gelingt das nicht, hängen beide Abschnitte hinten an.
     */
    private fun insertSections(s: Session) {
        val translator = LabTranslatorReport.lines(s.plan, s.translator)
        val crashes = LabCrashReport.lines(s.crashes, s.cap.omittedLines > 0, s.cap.omittedLines)
        val text = s.file.readText(Charsets.UTF_8)
        val patched = LabProtocol.insertAfterHead(text, translator, crashes, s.crashes.total)
        if (patched == null) {
            s.file.appendText(
                buildString {
                    appendLine()
                    appendLine("== Translator ==")
                    translator.forEach { appendLine(it) }
                    appendLine()
                    appendLine("== Crashes ==")
                    crashes.forEach { appendLine(it) }
                },
                Charsets.UTF_8,
            )
            return
        }
        val tmp = File(s.file.parentFile, s.file.name + ".tmp")
        tmp.writeText(patched, Charsets.UTF_8)
        if (!tmp.renameTo(s.file)) {
            s.file.writeText(patched, Charsets.UTF_8)
            tmp.delete()
        }
    }

    /** Abschnitt "Step test result": `labtest.txt` (höchstens 64 KB, geschwärzt) und die letzte erreichte Stufe. */
    private fun stepTestBlock(context: Context): String = runCatching {
        val text = LabStepTest.readResult(LabDirs.share(context))
        buildString {
            appendLine()
            appendLine("== Step test result ==")
            appendLine(LabStepTest.summary(LabStepTest.evaluate(text)))
            appendLine(if (text == null) "(labtest.txt does not exist)" else text.trimEnd())
        }
    }.getOrElse { "\n== Step test result ==\n(could not be read: ${it.javaClass.simpleName})\n" }

    /** Behält die jüngsten 30 Protokolle. */
    private fun pruneOld(dir: File) {
        dir.listFiles { f -> f.isFile && f.name.startsWith("lab-") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(30)
            .forEach { it.delete() }
    }
}
