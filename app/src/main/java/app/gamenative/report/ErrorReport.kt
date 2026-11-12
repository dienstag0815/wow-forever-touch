package app.gamenative.report

import app.gamenative.R
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StatFs
import androidx.core.content.FileProvider
import app.gamenative.BuildConfig
import app.gamenative.ui.screen.wow.BattleNetSignIn
import app.gamenative.ui.screen.wow.FpsLimit
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.ui.screen.wow.GameRegion
import app.gamenative.ui.screen.wow.GraphicsPreset
import app.gamenative.ui.screen.wow.WowClientDownloader
import app.gamenative.ui.screen.wow.isFoldedShut
import app.gamenative.ui.screen.wow.windowSize
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.BoundedProcess
import app.gamenative.utils.LibredirectChoice
import app.gamenative.utils.MachineIdentity
import app.gamenative.utils.MachineIdentityApply
import app.gamenative.utils.MachineIdentityLog
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fehlerbericht per Knopf: eine Zip mit Gerätedaten, App-Log, Wine-Log und den Logs des Spiels, alles geschwärzt
 * ([ReportRedactor]), danach öffnet sich das Android-Teilen-Menü. Nichts wird von selbst irgendwohin gesendet; die Zip liegt
 * nur im Cache der App (höchstens [KEEP_REPORTS] Stück). `login.txt` kommt nie hinein ([ReportBuilder.isAllowed]).
 * Aufbau, Auswahl und Schwärzen stehen in [ReportBuilder] und [ReportRedactor] (JUnit-getestet); hier steckt nur Android.
 *
 * Die Gliederung (Gerätedatei, Logcat des eigenen Prozesses, Wine-Log, Spielordner-Logs, Teilen per FileProvider) folgt der
 * Idee des Berichts in WoW Foreverywhere (YoruDev-Ryland, GPL-3.0); der Code ist eigenständig geschrieben.
 */
object ErrorReport {
    private const val KEEP_REPORTS = 3
    private const val WOW_CONTAINER_ID = "wow_forever"
    private const val LOGCAT_TIMEOUT_MS = 8_000L

    private val running = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "fehlerbericht").apply { isDaemon = true } }
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Erstellt den Bericht im Hintergrund, zeigt "Bericht wird erstellt ..." (Snackbar, kein Toast) und öffnet dann das
     * Teilen-Menü. [onNote] bekommt die gleichen Statuszeilen (für Zeilen im Seitenmenü). Läuft schon ein Bericht, passiert
     * nichts Neues.
     */
    fun start(context: Context, onNote: (String) -> Unit = {}) {
        if (!running.compareAndSet(false, true)) {
            SnackbarManager.show(context.getString(R.string.game_er_running))
            return
        }
        val appContext = context.applicationContext
        say(appContext.getString(R.string.game_er_creating), onNote)
        worker.execute {
            try {
                val zip = create(appContext, context)
                mainHandler.post { runCatching { share(appContext, zip) }.onFailure { fail(appContext, it, onNote) } }
                onNote(appContext.getString(R.string.game_er_done))
            } catch (e: Throwable) {
                fail(appContext, e, onNote)
            } finally {
                running.set(false)
            }
        }
    }

    private fun say(text: String, onNote: (String) -> Unit) {
        SnackbarManager.show(text)
        onNote(text)
    }

    private fun fail(context: Context, error: Throwable, onNote: (String) -> Unit) {
        Timber.w(error, "Fehlerbericht fehlgeschlagen")
        say(context.getString(R.string.game_er_failed), onNote)
    }

    /** Baut die Zip im Cache und löscht ältere Berichte. [uiContext] liefert die Fenstergröße (Activity, sonst App). */
    internal fun create(context: Context, uiContext: Context): File {
        val dir = File(context.cacheDir, "fehlerbericht").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val zip = File(dir, "${ReportBuilder.REPORT_PREFIX}$stamp.zip")

        val gameRoot = File(GamePath.load(context))
        val flavorDirs = listOf(WowClientDownloader.FLAVOR_DIR, WowClientDownloader.LEGACY_FLAVOR_DIR)
            .filter { File(gameRoot, it).isDirectory }
        val container = runCatching { ContainerManager(context).getContainerById(WOW_CONTAINER_ID) }.getOrNull()
        val systemReg = container?.rootDir?.let { File(it, ".wine/system.reg") }

        val redactor = buildRedactor(context, gameRoot, flavorDirs, systemReg)
        val deviceText = deviceText(context, uiContext, gameRoot, container, systemReg)
        val sources = sources(context, gameRoot, flavorDirs)
        val logcat = logcat()

        zip.outputStream().buffered().use { ReportBuilder.write(it, deviceText, logcat, sources, redactor) }
        ReportBuilder.pruneOldReports(dir, KEEP_REPORTS)
        return zip
    }

    /** Öffnet das Teilen-Menü für die Zip (ACTION_SEND über den FileProvider der App). */
    fun share(context: Context, zip: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.game_er_subject, BuildConfig.VERSION_NAME))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.game_er_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // --- Quellen ---

    private fun sources(context: Context, gameRoot: File, flavorDirs: List<String>): List<ReportSource> {
        val list = mutableListOf<ReportSource>()
        for (flavor in flavorDirs) {
            val base = File(gameRoot, flavor)
            list += ReportSource("spiel/$flavor/WTF/Config.wtf", File(base, "WTF/Config.wtf"), LogKind.CONFIG_WTF, 0)
            val logs = File(base, "Logs")
            list += ReportSource("spiel/$flavor/Logs/Connection.log", File(logs, "Connection.log"), LogKind.CONNECTION, 1)
            ReportBuilder.newest(logs, 6).filter { !it.name.equals("Connection.log", ignoreCase = true) }
                .forEach { list += ReportSource("spiel/$flavor/Logs/${it.name}", it, LogKind.GENERAL, 3) }
            ReportBuilder.newest(File(base, "Errors"), 5)
                .forEach { list += ReportSource("spiel/$flavor/Errors/${it.name}", it, LogKind.GENERAL, 2) }
        }
        val external = context.getExternalFilesDir(null)
        if (external != null) {
            ReportBuilder.newest(File(external, "wine_logs"), 2)
                .forEach { list += ReportSource("wine/${it.name}", it, LogKind.GENERAL, 4) }
            ReportBuilder.newest(File(external, "crash_logs"), 3)
                .forEach { list += ReportSource("app/crash_logs/${it.name}", it, LogKind.GENERAL, 2) }
        }
        return list
    }

    /** Logcat der eigenen App: alle Prozesse der App (Wine eingeschlossen), sonst nur dieser Prozess. */
    private fun logcat(): String = runCatching {
        val byUid = runLogcat("--uid", Process.myUid().toString())
        byUid ?: runLogcat("--pid", Process.myPid().toString()) ?: "logcat not available"
    }.getOrElse { "logcat not available: ${it.javaClass.simpleName}" }

    private fun runLogcat(vararg filter: String): String? {
        val process = ProcessBuilder(listOf("logcat", "-d", "-v", "threadtime") + filter).redirectErrorStream(true).start()
        val buffer = java.io.ByteArrayOutputStream()
        val reader = Thread { runCatching { process.inputStream.use { it.copyTo(buffer) } } }.apply { isDaemon = true; start() }
        val finished = BoundedProcess.awaitOrKill(process, LOGCAT_TIMEOUT_MS)
        reader.join(1_000)
        if (!finished || process.exitValue() != 0) return null
        return buffer.toString(Charsets.UTF_8.name())
    }

    // --- Schwärzen ---

    private fun buildRedactor(context: Context, gameRoot: File, flavorDirs: List<String>, systemReg: File?): ReportRedactor {
        val emails = linkedSetOf<String>()
        val passwords = linkedSetOf<String>()
        BattleNetSignIn.loadSecret(context)?.let { secret ->
            emails += String(secret.email)
            passwords += String(secret.password)
            secret.wipe()
        }
        // Eine noch liegende login.txt (wird beim Start geschrieben, danach gelöscht) wird nie eingepackt, aber ihre
        // Werte werden zusätzlich geschwärzt.
        for (flavor in flavorDirs) {
            runCatching {
                File(File(gameRoot, flavor), "login.txt").takeIf { it.isFile && it.length() < 4096 }?.readLines()?.let { lines ->
                    lines.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { emails += it.trim() }
                    lines.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { passwords += it.trim() }
                }
            }
        }
        // Die eigene Kennung (MachineIdentity) steht nicht in system.reg aller Fälle: sie wird zusätzlich geschwärzt.
        val own = MachineIdentity.load(MachineIdentityApply.identityFile(context))
        val machine = systemReg?.let { RegistryValues.secretsFromFile(it) }.orEmpty() +
            listOfNotNull(own?.machineGuid, own?.computerName, own?.volumeSerial, own?.productId)
        return ReportRedactor(emails, passwords, machine)
    }

    // --- Gerätedaten ---

    private fun deviceText(context: Context, uiContext: Context, gameRoot: File, container: Container?, systemReg: File?): String = buildString {
        fun line(label: String, value: Any?) = appendLine("$label: ${value ?: "unknown"}")
        appendLine("WoW Forever error report")
        line("Created", SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
        appendLine()
        line("Manufacturer", Build.MANUFACTURER)
        line("Model", "${Build.MODEL} (${Build.DEVICE})")
        line("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line("Chip", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE)
        line("App version", "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        runCatching {
            val (w, h) = windowSize(uiContext, maximum = false)
            val (mw, mh) = windowSize(uiContext, maximum = true)
            line("Window", "${w}x$h of ${mw}x$mh, folded: ${if (isFoldedShut(w, h, mw, mh)) "yes" else "no"}")
            line("Density", "${context.resources.displayMetrics.densityDpi} dpi")
        }
        appendLine()
        if (container == null) {
            appendLine("Container: not readable or not created yet")
        } else {
            line("Wine", container.getWineVersion())
            line("Variant", container.getContainerVariant())
            line("Emulator", container.getEmulator())
            line("FEXCore", container.getFEXCoreVersion())
            line("Box64", container.getBox64Version())
            line("DXVK/DX-Wrapper", container.getDXWrapper())
            line("Graphics driver", "${container.getGraphicsDriver()} ${container.getGraphicsDriverVersion()}")
            line("Screen size (container)", container.getScreenSize())
        }
        line("libredirect", LibredirectChoice.pick(BuildConfig.MODERN_ANDROID, false, Build.VERSION.SDK_INT, BuildConfig.PRELOAD_BIONIC_SO))
        appendLine()
        runCatching {
            line("Region", GameRegion.current(context, gameRoot.path).label)
            line("Frame rate limit", FpsLimit.current(context).label)
            line("Graphics", GraphicsPreset.current(context).label)
        }
        line("Game folder", gameRoot.path)
        line("Client-Build", WowClientDownloader.readBuildInfo(File(gameRoot, WowClientDownloader.BUILD_INFO))?.get("Version"))
        line("Free space (game folder)", megabytes(GamePath.freeSpaceBytes(gameRoot.path)))
        line("Free space (app data)", megabytes(freeBytes(context.filesDir)))
        appendLine()
        line("Login saved", if (BattleNetSignIn.hasSaved(context)) "yes" else "no")
        line("Machine ID set", if (systemReg != null && RegistryValues.hasMachineGuidInFile(systemReg)) "yes" else "no")
        // Windows-Kennung: nur die Kurzkennung (8 Hex-Zeichen eines SHA-256) und der Verlauf, nie ein Wert der Kennung.
        runCatching {
            val summary = MachineIdentityApply.logSummary(context)
            line("Machine ID fingerprint", summary.fingerprint)
            line("Machine ID changes", summary.changes)
            line("Machine ID last update", MachineIdentityLog.updateText(summary.update))
            appendLine()
            appendLine("Machine ID history (short ids only, no values):")
            append(MachineIdentityLog.historyText(summary.trace))
        }
        // Systemsicht: Gesamt-Fingerabdruck und je Teil Fingerabdruck und grobe Merkmale; nie Hostname, MAC oder IP (siehe SystemView).
        runCatching {
            val system = MachineIdentityApply.systemSummary(context)
            appendLine()
            line("System view fingerprint", system.total)
            line("System view changes", system.changes)
            for (p in system.parts) line("System view " + p.part.id, p.fingerprint + " " + p.features.joinToString(" ; "))
        }
        // Prefix-Merkmale (vor dem Anwenden gemessen, nie geändert): Gesamt-Fingerabdruck und je Teil Fingerabdruck und grobe Merkmale; keine Werte.
        runCatching {
            val traits = MachineIdentityApply.prefixSummary(context)
            appendLine()
            line("Prefix traits fingerprint", traits.total)
            line("Prefix traits changes", traits.changes)
            for (p in traits.parts) line("Prefix traits " + p.part.id, p.fingerprint + " " + p.features.joinToString(" ; "))
        }
    }

    private fun freeBytes(dir: File): Long = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(-1L)

    private fun megabytes(bytes: Long): String = if (bytes < 0) "unknown" else "${bytes / (1024 * 1024)} MB"
}
