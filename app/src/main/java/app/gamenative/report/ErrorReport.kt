package app.gamenative.report

import app.gamenative.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.gamenative.BuildConfig
import app.gamenative.ui.screen.wow.BattleNetSignIn
import app.gamenative.ui.screen.wow.LoginLevelStore
import app.gamenative.ui.screen.wow.FpsLimit
import app.gamenative.ui.components.AppText
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
            SnackbarManager.show(AppText.get(R.string.game_er_running))
            return
        }
        val appContext = context.applicationContext
        say(AppText.get(R.string.game_er_creating), onNote)
        worker.execute {
            try {
                val zip = create(appContext, context)
                mainHandler.post { runCatching { share(appContext, zip) }.onFailure { fail(appContext, it, onNote) } }
                onNote(AppText.get(R.string.game_er_done))
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
        say(AppText.get(R.string.game_er_failed), onNote)
    }

    /** Der fertige Bericht: die Zip im Cache und die Kurzfassung (`kurz.txt`, geschwärzt, höchstens [ReportSummary.MAX_CHARS] Zeichen). */
    internal class Built(val zip: File, val summary: String, val device: String, val android: String)

    internal fun create(context: Context, uiContext: Context): File = build(context, uiContext).zip

    /**
     * Baut die Zip im Cache und löscht ältere Berichte. [uiContext] liefert die Fenstergröße (Activity, sonst App). [answers] sind die
     * Antworten auf die Fragen von "Problem melden" (null beim Fehlerbericht ohne Fragen: dann stehen keine Antwortzeilen in `kurz.txt`).
     */
    internal fun build(context: Context, uiContext: Context, answers: ProblemAnswers? = null): Built {
        val dir = File(context.cacheDir, "fehlerbericht").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val zip = File(dir, "${ReportBuilder.REPORT_PREFIX}$stamp.zip")

        val gameRoot = File(GamePath.load(context))
        val flavorDirs = listOf(WowClientDownloader.FLAVOR_DIR, WowClientDownloader.LEGACY_FLAVOR_DIR)
            .filter { File(gameRoot, it).isDirectory }
        val container = runCatching { ContainerManager(context).getContainerById(WOW_CONTAINER_ID) }.getOrNull()
        val systemReg = container?.rootDir?.let { File(it, ".wine/system.reg") }

        val redactor = buildRedactor(context, gameRoot, flavorDirs, systemReg)

        // Anmeldeverlauf und Zeitleiste: das Journal (für den jüngsten Eintrag mit den Versuchen aus der Connection.log von jetzt),
        // Installationsdaten, andere Start-Apps und Spuren im Spielordner.
        val logText = ConnectionLogFile.latestText(gameRoot, flavorDirs)
        val journal = LoginHistory.withLatestCapture(LoginJournalRecorder.load(context), logText)
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val timeline = timelineInput(context, packageInfo, gameRoot, flavorDirs, journal)
        val installed = { name: String -> isInstalled(context, name) }
        val traces = runCatching { OtherLaunchers.scan(gameRoot, flavorDirs) }.getOrDefault(emptyList())
        val oldFiles = OtherLaunchers.summarizeOld(runCatching { OtherLaunchers.oldFileTraces(gameRoot, flavorDirs, packageInfo?.firstInstallTime) }.getOrDefault(emptyList()))
        val originLine = FolderOrigin.line(timeline.gameFolder?.oldestMs, packageInfo?.firstInstallTime, ownHistoryFirstMs(gameRoot))

        val deviceText = deviceText(context, uiContext, gameRoot, container, systemReg, InstallTimeline.lines(timeline))
        val sources = sources(context, gameRoot, flavorDirs)
        val logcat = logcat()

        // Anmeldung: Versuche aus der Connection.log, Journal der App, Kurzfassung (ReportBuilder.write schwärzt alles nochmals).
        val attemptsText = LoginAttempts.report(logText)
        val journalText = LoginJournal.report(journal)
        val launcherText = OtherLaunchers.report(OtherLaunchers.installedLines(installed), traces, originLine, oldFiles)
        val input = summaryInput(context, gameRoot, systemReg, journal, logText, timeline, OtherLaunchers.compact(OtherLaunchers.installedLabels(installed), traces, originLine), answers)
        val summary = ReportSummary.build(input, redactor)
        val extras = listOf("kurz.txt" to summary, "anmeldung.txt" to attemptsText, "anmeldejournal.txt" to journalText, "spuren.txt" to launcherText)

        zip.outputStream().buffered().use { ReportBuilder.write(it, deviceText, logcat, sources, redactor, extras) }
        ReportBuilder.pruneOldReports(dir, KEEP_REPORTS)
        return Built(zip, summary, "${Build.MANUFACTURER} ${Build.MODEL}", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    /** Der älteste Eintrag der eigenen Verlaufsdatei der Windows-Kennung im Spielordner (in Millisekunden); null, wenn es sie nicht gibt oder sie leer ist. */
    private fun ownHistoryFirstMs(gameRoot: File): Long? = runCatching {
        val file = File(gameRoot, MachineIdentityLog.HISTORY_FILE_NAME)
        if (file.isFile && file.length() < 1_000_000) MachineIdentityLog.parse(file.readText(Charsets.UTF_8)).minOfOrNull { it.time }?.times(1000) else null
    }.getOrNull()

    /** Ob das Paket installiert ist (nur für die feste Liste in [OtherLaunchers], die in `<queries>` des Manifests steht). */
    private fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /** Die Installationsquelle der App als Paketname (null, wenn Android keine meldet). */
    @Suppress("DEPRECATION")
    private fun installerOf(context: Context): String? = runCatching {
        InstallTimeline.installerName(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            else context.packageManager.getInstallerPackageName(context.packageName),
        )
    }.getOrNull()

    private fun timelineInput(context: Context, info: PackageInfo?, gameRoot: File, flavorDirs: List<String>, journal: List<JournalEntry>): TimelineInput {
        val identity = runCatching { MachineIdentityApply.logSummary(context) }.getOrNull()
        val (versions, firstVersion, lastVersion) = InstallTimeline.versionsIn(journal)
        return TimelineInput(
            firstInstallMs = info?.firstInstallTime,
            lastUpdateMs = info?.lastUpdateTime,
            installer = installerOf(context),
            gameFolder = runCatching { InstallTimeline.scan(gameRoot) }.getOrNull(),
            wtfFolder = runCatching { flavorDirs.mapNotNull { InstallTimeline.scan(File(File(gameRoot, it), "WTF")) }.minByOrNull { it.oldestMs ?: Long.MAX_VALUE } }.getOrNull(),
            identityTrace = identity?.trace.orEmpty(),
            identitySinceSec = identity?.since,
            journalVersions = versions,
            journalFirstVersion = firstVersion,
            journalLastVersion = lastVersion,
        )
    }

    private fun summaryInput(
        context: Context,
        gameRoot: File,
        systemReg: File?,
        journal: List<JournalEntry>,
        logText: String?,
        timeline: TimelineInput,
        otherLaunchers: String,
        answers: ProblemAnswers?,
    ): SummaryInput {
        val attempts = LoginAttempts.parse(logText.orEmpty())
        val identity = runCatching { MachineIdentityApply.logSummary(context) }.getOrNull()
        val system = runCatching { MachineIdentityApply.systemSummary(context) }.getOrNull()
        return SummaryInput(
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            chip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE,
            appVersion = "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
            clientBuild = WowClientDownloader.readBuildInfo(File(gameRoot, WowClientDownloader.BUILD_INFO))?.get("Version") ?: "unknown",
            region = runCatching { GameRegion.current(context, gameRoot.path).label }.getOrDefault("unknown"),
            loginLevel = "${LoginLevelStore.current(context).key} (scope ${LoginLevelStore.scope(context).key})",
            machineIdSet = if (systemReg != null && RegistryValues.hasMachineGuidInFile(systemReg)) "yes" else "no",
            machineIdFingerprint = identity?.fingerprint ?: "unknown",
            machineIdChanges = identity?.changes?.toString() ?: "unknown",
            systemViewChanges = system?.changes?.toString() ?: "unknown",
            attemptsHeader = LoginAttempts.headerLine(LoginAttempts.stats(attempts)),
            lastAttempts = attempts.takeLast(5).map { LoginAttempts.attemptShort(it) },
            lastJournal = journal.takeLast(3).map { LoginJournal.shortLine(it) },
            answers = answers?.lines(),
            history = if (journal.isEmpty()) "" else LoginHistory.overviewCompact(LoginHistory.overview(journal)),
            timeline = InstallTimeline.compact(timeline),
            otherLaunchers = otherLaunchers,
        )
    }

    // --- Problem melden ---

    /**
     * "Problem melden": baut den Bericht, legt die Zip im Ordner Downloads ab, kopiert die Kurzfassung in die Zwischenablage und
     * speichert die Karte "Bericht bereit". Es öffnet sich nichts: ist der Bericht fertig und steht die App im Vordergrund
     * ([appVisible]), wird der Dialog "Bericht bereit" fällig ([ReportDialog]); sonst bleibt die Karte. Abgeschickt wird die Meldung
     * vom Spieler; die Zip hängt er selbst an. Lässt sich die Zip nicht in Downloads ablegen, öffnet sich wie beim Fehlerbericht das Teilen-Menü.
     */
    fun startProblem(context: Context, answers: ProblemAnswers = ProblemAnswers(), appVisible: () -> Boolean = { true }, onNote: (String) -> Unit = {}) {
        if (!running.compareAndSet(false, true)) {
            SnackbarManager.show(AppText.get(R.string.game_er_running))
            return
        }
        val appContext = context.applicationContext
        say(AppText.get(R.string.game_er_creating), onNote)
        worker.execute {
            try {
                val built = build(appContext, context, answers.normalized())
                val savedName = runCatching { saveToDownloads(appContext, built.zip) }.onFailure { Timber.w(it, "Zip nicht in Downloads abgelegt") }.getOrNull()
                val repo = ReportIssueLink.repoUrl(BuildConfig.UPDATE_FEED_URL, BuildConfig.REPORT_REPO_URL)
                val url = repo?.let {
                    ReportIssueLink.build(
                        repoUrl = it,
                        title = ReportIssueLink.issueTitle(BuildConfig.VERSION_NAME),
                        summary = built.summary,
                        zipName = savedName ?: built.zip.name,
                        device = built.device,
                        android = built.android,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                }
                // Die Karte "Bericht bereit" bleibt stehen, auch nach einem Wechsel in eine andere App; ein neuer Bericht ersetzt sie.
                val card = ReportCard(savedName ?: built.zip.name, System.currentTimeMillis(), built.zip.path, savedName != null, url)
                ReportCardStore.set(appContext, card)
                mainHandler.post {
                    runCatching {
                        copyToClipboard(appContext, built.summary)
                        if (savedName == null) {
                            share(appContext, built.zip)
                            say(AppText.get(R.string.game_pm_not_saved), onNote)
                        } else {
                            say(AppText.get(if (url == null) R.string.game_pm_no_repo else R.string.game_pm_saved, savedName), onNote)
                        }
                        ReportDialog.state.reportReady(card, appVisible())
                    }.onFailure { fail(appContext, it, onNote) }
                }
            } catch (e: Throwable) {
                fail(appContext, e, onNote)
            } finally {
                running.set(false)
            }
        }
    }

    /**
     * Baut den Bericht neu, weil die Zip der Karte nicht mehr im Cache liegt (Android räumt den Cache auf, spätere Berichte verdrängen
     * sie). Die Karte zeigt danach auf die neue Zip; Name und Datei in Downloads bleiben, wie sie waren. [then] läuft im Hauptthread.
     */
    internal fun rebuild(context: Context, card: ReportCard, onNote: (String) -> Unit, then: (Context, ReportCard, File) -> Unit) {
        if (!running.compareAndSet(false, true)) {
            SnackbarManager.show(AppText.get(R.string.game_er_running))
            return
        }
        val appContext = context.applicationContext
        say(AppText.get(R.string.game_er_creating), onNote)
        worker.execute {
            try {
                val built = build(appContext, context)
                val renewed = card.copy(zipPath = built.zip.path)
                ReportCardStore.set(appContext, renewed)
                mainHandler.post { runCatching { then(appContext, renewed, built.zip) }.onFailure { fail(appContext, it, onNote) } }
            } catch (e: Throwable) {
                fail(appContext, e, onNote)
            } finally {
                running.set(false)
            }
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WoW Forever report", text))
    }

    /** Legt die Zip im öffentlichen Ordner Downloads ab (MediaStore ab Android 10, sonst direkt). Gibt den Dateinamen dort zurück (bei gleichem Namen hängt Android eine Zahl an) oder wirft. */
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

    /** Öffnet das Teilen-Menü für die Zip (ACTION_SEND über den FileProvider der App). */
    fun share(context: Context, zip: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, AppText.get(R.string.game_er_subject, BuildConfig.VERSION_NAME))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, AppText.get(R.string.game_er_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    /**
     * Die Anmeldung im Bericht: ob Daten gespeichert sind, die Stufe (off / fill / self) und was "fill" eintippt. Nie E-Mail,
     * Passwort, deren Länge oder ein Teil davon; die Daten werden hier nicht einmal entschlüsselt.
     */
    internal fun loginSection(context: Context): String = buildString {
        appendLine("Login saved: ${if (BattleNetSignIn.hasSaved(context)) "yes" else "no"}")
        appendLine("Login mode: ${LoginLevelStore.current(context).key}")
        appendLine("Login fill scope: ${LoginLevelStore.scope(context).key}")
    }

    private fun deviceText(context: Context, uiContext: Context, gameRoot: File, container: Container?, systemReg: File?, timelineLines: List<String>): String = buildString {
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
        append(loginSection(context))
        appendLine()
        appendLine("Timeline of the installation (dates in UTC):")
        timelineLines.forEach { appendLine(it) }
        appendLine()
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
