package app.gamenative

import android.content.Context
import android.content.Intent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * A local running crash handler.
 * Any uncaught exceptions will be saved located locally in a text file, aka: Crash Report.
 * File location: /<user storage>/Android/data/app.gamenative/files/crash_logs/
 *
 * Besides the file, the report is shown on screen by [CrashReportActivity] (own process ":crash") so that a user without adb can
 * copy or share it. The main process then ends itself; if the screen cannot be started, the system's own handler runs as before.
 */
class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val LOG_CAT_COUNT = 256
        private const val CRASH_FILE_HISTORY_COUNT = 1
        private const val SHOWN_MARKER = "crash_report_shown"

        val timestamp: String
            get() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())

        /**
         * Logcat command
         */
        private fun logcatCommand(count: Int): String = "logcat -d -t $count --pid=${android.os.Process.myPid()}"

        fun initialize(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            val crashHandler = CrashHandler(context.applicationContext, defaultHandler)
            Thread.setDefaultUncaughtExceptionHandler(crashHandler)
        }

        /**
         * Helper method to get logcat info live
         */
        fun getAppLogs(lineCount: Int = LOG_CAT_COUNT): String {
            var process: Process? = null
            var reader: BufferedReader? = null

            return try {
                process = Runtime.getRuntime().exec(logcatCommand(lineCount))
                reader = BufferedReader(InputStreamReader(process.inputStream))

                val log = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    log.append(line).append("\n")
                }

                log.toString()
            } catch (e: Exception) {
                "Failed to capture logs: ${e.message}"
            } finally {
                reader?.close()
                process?.destroy()
            }
        }
    }

    private val crashFileDir by lazy {
        File(context.getExternalFilesDir(null), "crash_logs").apply {
            if (!exists()) mkdirs()
        }
    }

    private val recentLogcat: String
        get() = try {
            val process = Runtime.getRuntime().exec("logcat -d -t $LOG_CAT_COUNT --pid=${android.os.Process.myPid()}")
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Failed to retrieve logcat: ${e.message}"
        }

    private val cleanupOldCrashFiles: () -> Unit = {
        crashFileDir.listFiles()?.let { files ->
            if (files.size > CRASH_FILE_HISTORY_COUNT) {
                files.sortByDescending { it.lastModified() }
                files.drop(CRASH_FILE_HISTORY_COUNT).forEach { it.delete() }
            }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // The screen first (the system starts it asynchronously), then the file with the logcat, which takes longer.
        val shown = showCrashReport(throwable)
        saveCrashToFile(throwable)
        if (shown) {
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    /** Starts [CrashReportActivity] with the report; false when it was not started (too soon after the last one, or an error). */
    private fun showCrashReport(throwable: Throwable): Boolean {
        return try {
            val marker = File(context.filesDir, SHOWN_MARKER)
            val now = System.currentTimeMillis()
            if (!CrashReport.shouldShow(marker.lastModified(), now)) return false
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            val report = CrashReport.build(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                androidVersion = "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                device = "${android.os.Build.MANUFACTURER} - ${android.os.Build.BRAND} - ${android.os.Build.MODEL}",
                stackTrace = stackTrace,
            )
            marker.writeText(now.toString())
            marker.setLastModified(now)
            val intent = Intent(context, CrashReportActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(CrashReport.EXTRA_REPORT, report)
                .putExtra(CrashReport.EXTRA_LANGUAGE, Locale.getDefault().language)
            context.startActivity(intent)
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun saveCrashToFile(throwable: Throwable) {
        try {
            val stackTrace = StringWriter().apply {
                val pw = PrintWriter(this)
                throwable.printStackTrace(pw)
            }.toString()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

            val crashReport = buildString {
                appendLine("Timestamp: $timestamp")
                appendLine("App Version: ${packageInfo.versionName} (${packageInfo.longVersionCode})")
                appendLine()
                appendLine("---------- Device Information ----------")
                appendLine("${android.os.Build.MANUFACTURER} - ${android.os.Build.BRAND} - ${android.os.Build.MODEL}")
                appendLine("Android Version: ${android.os.Build.VERSION.RELEASE}")
                appendLine()
                appendLine("---------- Cause ----------")
                appendLine("Exception: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message}")
                appendLine()
                appendLine("---------- Stack Trace ----------")
                appendLine(stackTrace)
                appendLine()
                appendLine("---------- Logcat ----------")
                appendLine(recentLogcat)
            }

            File(crashFileDir, "pluvia_crash_$timestamp.txt").writeText(crashReport)

            cleanupOldCrashFiles()
        } catch (e: Exception) {
            // The file is only a copy; the caller decides what happens to the process.
        }
    }
}
