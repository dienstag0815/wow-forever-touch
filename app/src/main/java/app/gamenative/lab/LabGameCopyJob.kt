package app.gamenative.lab

import android.content.Context
import android.os.PowerManager
import android.os.StatFs
import timber.log.Timber
import java.io.File

/** Stand der Kopie für die Seite (alles Zahlen, die Seite macht den Text). */
data class LabCopyView(
    val originalPresent: Boolean,
    val originalFiles: Int,
    val originalBytes: Long,
    val originalSkipped: Int,
    val freeBytes: Long,
    val alreadyInTarget: Long,
    val info: LabCopyInfo?,
    val partial: Boolean,
    /** Der Umzug in den Unterordner `World of Warcraft` ist gescheitert: die Kopie ist nicht einbindbar (nichts wurde gelöscht). */
    val layoutProblem: LayoutResult.Failed? = null,
) {
    val needed: Long get() = LabCopySpace.needed(originalBytes, alreadyInTarget)
    val fits: Boolean get() = LabCopySpace.fits(freeBytes, originalBytes, alreadyInTarget)
    val complete: Boolean get() = info?.complete == true
}

/**
 * Die Spielkopie des Labors: Zahlen für die Seite, Kopieren im Hintergrund (eigener Faden, kein Dienst; ein Teil-Wakelock hält die CPU
 * wach, solange kopiert wird) und Löschen. Das Original wird nie geschrieben; alle Schreibzugriffe laufen über [LabCopyTarget].
 */
object LabGameCopyJob {
    private const val WAKE_TAG = "bnetlab:gamecopy"
    private const val WAKE_MAX_MS = 3L * 60 * 60 * 1000

    @Volatile private var thread: Thread? = null
    @Volatile private var cancelFlag = false
    @Volatile var progress: CopyProgress? = null
        private set

    /** Ausgang des letzten Laufs in diesem Prozess (null = noch keiner oder die Seite hat ihn schon abgeholt). */
    @Volatile var outcome: CopyOutcome? = null
        private set

    fun isRunning(): Boolean = thread?.isAlive == true

    fun cancel() {
        cancelFlag = true
    }

    fun takeOutcome(): CopyOutcome? = outcome.also { if (!isRunning()) outcome = null }

    fun info(context: Context): LabCopyInfo? =
        LabCopyInfo.parse(runCatching { LabDirs.gameCopyInfo(context).readText(Charsets.UTF_8) }.getOrNull())

    /** Zahlen für die Seite: liest das Original (zählen) und misst den Platz; im Hintergrund aufrufen. */
    fun view(context: Context): LabCopyView {
        // Eine Kopie im alten Aufbau wird beim Öffnen der Seite umgezogen (nur Umbenennen), solange nichts kopiert wird und kein Versuch läuft.
        ensureLayout(context)
        val original = LabOriginalCheck.originalFolder(context)
        val scan = original?.let { LabGameScan.scan(it) }
        val content = LabDirs.gameCopyContent(context)
        val info = info(context)
        val inTarget = if (content.isDirectory) LabDirs.sizeOf(content).coerceAtMost(scan?.bytes ?: 0L) else 0L
        return LabCopyView(
            originalPresent = original != null,
            originalFiles = scan?.fileCount ?: 0,
            originalBytes = scan?.bytes ?: 0L,
            originalSkipped = scan?.skipped ?: 0,
            freeBytes = freeBytes(LabDirs.root(context)),
            alreadyInTarget = inTarget,
            info = info,
            partial = content.isDirectory && info?.complete != true && inTarget > 0L,
            layoutProblem = layoutProblem,
        )
    }

    private val layoutLock = Any()

    /** Der letzte gescheiterte Umzug (null = keiner oder gelungen). */
    @Volatile var layoutProblem: LayoutResult.Failed? = null
        private set

    /**
     * Bringt eine Kopie im alten Aufbau in den neuen (siehe [LabCopyLayout]), ohne Daten zu kopieren. Wiederholbar. Gibt null zurück, wenn
     * gerade kopiert wird oder ein Versuch läuft (dann wird nichts umbenannt), und auch, wenn es noch gar keine Kopie gibt.
     */
    fun ensureLayout(context: Context): LayoutResult? {
        val app = context.applicationContext
        if (isRunning() || LabRun.isActive()) return null
        if (!LabDirs.gameCopy(app).exists()) return null
        return migrateLayout(app)
    }

    private fun migrateLayout(app: Context): LayoutResult = synchronized(layoutLock) {
        val result = LabCopyLayout.migrate(LabDirs.gameCopy(app), LabDirs.gameCopyMount(app), LabOriginalCheck.originalFolder(app))
        layoutProblem = result as? LayoutResult.Failed
        result
    }

    private fun freeBytes(dir: File): Long =
        generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }?.let { runCatching { StatFs(it.path).availableBytes }.getOrNull() } ?: 0L

    /** Startet das Kopieren (oder setzt es fort). Gibt false zurück, wenn schon eines läuft, ein Labor-Versuch läuft oder es kein Original gibt. */
    fun start(context: Context): Boolean {
        val app = context.applicationContext
        if (isRunning() || LabRun.isActive()) return false
        val original = LabOriginalCheck.originalFolder(app) ?: return false
        cancelFlag = false
        progress = null
        outcome = null
        val t = Thread({ run(app, original) }, "lab-game-copy").apply { isDaemon = true }
        thread = t
        t.start()
        return true
    }

    private fun run(context: Context, original: File) {
        val wake = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
                .apply { acquire(WAKE_MAX_MS) }
        }.getOrNull()
        try {
            val infoFile = LabDirs.gameCopyInfo(context)
            val copyDir = LabDirs.gameCopy(context)
            copyDir.mkdirs()
            // Zuerst der Aufbau: eine Kopie im alten Aufbau zieht in den Unterordner um, bei einer neuen wird die Marke vor dem ersten Schreiben gesetzt.
            val layout = migrateLayout(context)
            if (layout is LayoutResult.Failed) {
                outcome = CopyOutcome.Failed(CopyOutcome.Reason.LAYOUT, layout.rel)
                return
            }
            // War die Kopie fertig (der Launcher darf an ihr gearbeitet haben), behält „fortsetzen“ alles, was das Original nicht hat.
            val previous = info(context)
            val keepExtras = LabCopyRefresh.keepsExtras(previous)
            // Eine angefangene Kopie ist nie fertig: die Marke fällt vor dem ersten Schreiben weg bzw. wird auf „nicht fertig“ gesetzt.
            val running = LabCopyRefresh.markerWhileRunning(previous)
            if (running != null) writeInfo(infoFile, running) else if (infoFile.exists()) infoFile.delete()
            val result = LabGameCopier.copy(original, LabDirs.gameCopyContent(context), { cancelFlag }, { progress = it }, keepExtras = keepExtras)
            if (result is CopyOutcome.Done) writeInfo(infoFile, result.info)
            outcome = result
        } catch (e: Exception) {
            Timber.w(e, "Labor: Spielkopie")
            outcome = CopyOutcome.Failed(CopyOutcome.Reason.IO)
        } finally {
            runCatching { if (wake?.isHeld == true) wake.release() }
        }
    }

    private fun writeInfo(file: File, info: LabCopyInfo) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(info.toJson(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) file.writeText(info.toJson(), Charsets.UTF_8)
    }

    /** "Delete copy": nur der Kopie-Ordner unter `files/bnetlab`; nie während eines Labor-Versuchs oder Kopierens, nie etwas am Original. */
    fun delete(context: Context): LabCopyDelete.Result {
        if (isRunning() || LabRun.isActive()) return LabCopyDelete.Result.REFUSED
        val app = context.applicationContext
        layoutProblem = null
        return LabCopyDelete.delete(LabDirs.gameCopy(app), LabDirs.root(app), LabOriginalCheck.originalFolder(app))
    }
}
