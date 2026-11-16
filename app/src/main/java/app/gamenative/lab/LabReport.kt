package app.gamenative.lab

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StatFs
import android.provider.MediaStore
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.report.ErrorReport
import app.gamenative.report.ReportRedactor
import app.gamenative.ui.components.AppText
import app.gamenative.ui.screen.wow.BattleNetSignIn
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.BoundedProcess
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Labor-Bericht teilen": baut eine Zip mit den Protokollen des Labors, den jüngsten Battle.net-Protokollen aus dem Prefix (siehe
 * [LabReportSelect]) und `geraet.txt`, alles durch den vorhandenen [ReportRedactor]. Die Zip liegt im Cache und in Downloads, danach öffnet
 * sich das Teilen-Menü (wie beim Fehlerbericht). Nichts wird von selbst gesendet. Ein Bildschirmfoto des Fensters liefert der X-Server-
 * Bildschirm ohne Umbau nicht; der Bericht enthält keines.
 */
object LabReport {
    private const val PREFIX = "WoWForever-Labor-"
    private const val KEEP = 3
    private const val LOGCAT_TIMEOUT_MS = 8_000L

    private val running = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "laborbericht").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun start(context: Context, onNote: (String) -> Unit) {
        if (!running.compareAndSet(false, true)) {
            say(AppText.get(R.string.lab_report_running), onNote)
            return
        }
        val app = context.applicationContext
        say(AppText.get(R.string.lab_report_creating), onNote)
        worker.execute {
            try {
                val zip = build(app)
                val saved = runCatching { saveToDownloads(app, zip) }.onFailure { Timber.w(it, "Labor-Bericht nicht in Downloads abgelegt") }.getOrNull()
                main.post {
                    runCatching { ErrorReport.share(app, zip) }.onFailure { Timber.w(it, "Teilen-Menü") }
                    say(if (saved != null) AppText.get(R.string.lab_report_saved, saved) else AppText.get(R.string.lab_report_not_saved), onNote)
                }
            } catch (e: Throwable) {
                Timber.w(e, "Labor-Bericht fehlgeschlagen")
                say(AppText.get(R.string.lab_report_failed), onNote)
            } finally {
                running.set(false)
            }
        }
    }

    private fun say(text: String, onNote: (String) -> Unit) {
        SnackbarManager.show(text)
        onNote(text)
    }

    private fun build(context: Context): File {
        val dir = File(context.cacheDir, "laborbericht").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val zip = File(dir, "$PREFIX$stamp.zip")
        val sources = LabReportSelect.sources(LabDirs.logs(context), LabDirs.driveC(context))
        zip.outputStream().buffered().use { out ->
            LabReportSelect.writeZip(sources, deviceText(context), redactor(context), out, extras = emptyList(), logcat = logcat())
        }
        dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) }.orEmpty().sortedByDescending { it.lastModified() }.drop(KEEP).forEach { it.delete() }
        return zip
    }

    /** Ob im Spielbereich eine Anmeldung gespeichert ist (ohne etwas zu entschlüsseln), für die Anmelde-Knöpfe der Tastenleiste. */
    fun hasSavedSignIn(context: Context): Boolean = BattleNetSignIn.hasSaved(context)

    /**
     * Die gespeicherte Anmeldung als Zeichenfelder für die Anmelde-Knöpfe der Tastenleiste (nur der vorhandene Lesezugriff, keine Kopie in Labor-Einstellungen);
     * null, wenn nichts gespeichert oder nicht lesbar ist. Steht hier, weil `LoginGuardTest` die Dateien festhält, die `loadSecret(` aufrufen dürfen.
     */
    fun signInValues(context: Context): LabSignInValues? =
        BattleNetSignIn.loadSecret(context)?.let { LabSignInValues(it.email, it.password) }

    /** E-Mail und Passwort der gespeicherten Anmeldung werden zusätzlich zu den allgemeinen Regeln des [ReportRedactor] geschwärzt. */
    private fun redactor(context: Context): ReportRedactor {
        val emails = linkedSetOf<String>()
        val passwords = linkedSetOf<String>()
        BattleNetSignIn.loadSecret(context)?.let { secret ->
            emails += String(secret.email)
            passwords += String(secret.password)
            secret.wipe()
        }
        return ReportRedactor(emails, passwords)
    }

    private fun deviceText(context: Context): String = buildString {
        fun line(label: String, value: Any?) = appendLine("$label: ${value ?: "unknown"}")
        appendLine("WoW Forever lab report")
        line("Created", SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
        line("Manufacturer", Build.MANUFACTURER)
        line("Model", "${Build.MODEL} (${Build.DEVICE})")
        line("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line("Chip", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE)
        line("App version", "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        line("Free space (app data)", runCatching { "${StatFs(context.filesDir.path).availableBytes / (1024 * 1024)} MB" }.getOrNull())
        appendLine()
        val settings = LabStore.settings(context)
        appendLine("Lab settings (JSON): ${settings.toJson()}")
        val installed = runCatching { LabLauncher.installedPackages(context) }.getOrDefault(emptyList())
        appendLine("Installed extra packages: " + if (installed.isEmpty()) "(none)" else installed.joinToString("; ") { "${it.kind.key} ${it.verName}-${it.verCode}" })
        line("Lab prefix present", LabContainer.exists(context))
        line("Lab size", "${LabContainer.usedBytes(context) / (1024 * 1024)} MB")
        line("Battle.net.exe found", LabProtocol.findBattleNetExe(LabDirs.driveC(context)))
        appendLine()
        // Ergebnis der letzten „Einrichtung prüfen“ (die Prüfung läuft nicht von selbst; ohne sie steht hier „not run yet“).
        LabSetupCheck.reportLines(runCatching { LabStore.setupCheck(context) }.getOrNull()).forEach { appendLine(it) }
    }

    /** Logcat der eigenen App (alle Prozesse der App, Wine eingeschlossen); bei Fehlern ein Hinweis statt Text. */
    private fun logcat(): String = runCatching {
        val process = ProcessBuilder(listOf("logcat", "-d", "-v", "threadtime", "--uid", Process.myUid().toString())).redirectErrorStream(true).start()
        val buffer = java.io.ByteArrayOutputStream()
        val reader = Thread { runCatching { process.inputStream.use { it.copyTo(buffer) } } }.apply { isDaemon = true; start() }
        val finished = BoundedProcess.awaitOrKill(process, LOGCAT_TIMEOUT_MS)
        reader.join(1_000)
        if (!finished || process.exitValue() != 0) "logcat not available" else buffer.toString(Charsets.UTF_8.name())
    }.getOrElse { "logcat not available: ${it.javaClass.simpleName}" }

    /** Legt die Zip im öffentlichen Ordner Downloads ab (MediaStore ab Android 10, sonst direkt); gibt den Dateinamen dort zurück oder wirft. */
    private fun saveToDownloads(context: Context, zip: File): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, zip.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore refused")
            context.contentResolver.openOutputStream(uri)!!.use { out -> zip.inputStream().use { it.copyTo(out) } }
            return context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: zip.name
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        zip.copyTo(File(dir, zip.name), overwrite = true)
        return zip.name
    }
}
