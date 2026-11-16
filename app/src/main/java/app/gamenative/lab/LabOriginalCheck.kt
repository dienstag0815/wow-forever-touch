package app.gamenative.lab

import android.content.Context
import app.gamenative.ui.screen.wow.GamePath
import timber.log.Timber
import java.io.File

/**
 * Nachweis "Originalordner unverändert": ein Abdruck des Spielordners direkt vor jedem Labor-Start und direkt nach jedem Labor-Ende (auch
 * nach einem Abbruch; war die App dazwischen beendet, holt [recover] den Abdruck beim nächsten Öffnen der Seite nach). Der Stand liegt in
 * `files/bnetlab/original-check.json`. Es wird nur gelesen; im Originalordner schreibt dieser Code nie.
 */
object LabOriginalCheck {
    private val lock = Any()

    /** Der Spielordner der App, wenn es ihn gibt; sonst null. Nur lesen (`GamePath.load` ändert nichts). */
    fun originalFolder(context: Context): File? =
        runCatching { File(GamePath.load(context)) }.getOrNull()?.takeIf { it.isDirectory }

    fun state(context: Context): OriginalCheckState = synchronized(lock) {
        OriginalCheckState.fromJson(runCatching { LabDirs.originalCheckFile(context).readText(Charsets.UTF_8) }.getOrNull())
    }

    private fun save(context: Context, state: OriginalCheckState) {
        runCatching {
            val file = LabDirs.originalCheckFile(context)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(state.toJson(), Charsets.UTF_8)
            if (!tmp.renameTo(file)) file.writeText(state.toJson(), Charsets.UTF_8)
        }.onFailure { Timber.w(it, "Labor: Stand der Originalprüfung nicht gespeichert") }
    }

    /** Weitere Labor-Starts sind gesperrt, solange eine Abweichung nicht bestätigt ist. */
    fun blocksStart(context: Context): Boolean = LabOriginalGate.blocksStart(state(context))

    /** Der Nutzer hat die rote Karte bestätigt. */
    fun acknowledge(context: Context) {
        synchronized(lock) { save(context, LabOriginalGate.acknowledge(state(context))) }
    }

    /** Abdruck vor dem Start (liest nur). Der Aufrufer reicht ihn nach `LabRun.begin` an [remember]. */
    fun snapshot(context: Context): OriginalPrint = LabOriginalPrintLogic.take(originalFolder(context))

    /** Merkt den Abdruck vor dem Start samt Protokolldatei; der Abdruck nach dem Ende gehört dazu. */
    fun remember(context: Context, before: OriginalPrint, protocol: File) {
        synchronized(lock) { save(context, LabOriginalGate.begun(state(context), before, protocol.name)) }
    }

    /**
     * Abdruck nach dem Ende, Vergleich, Abschnitt `== Original game folder ==` in das Protokoll, Stand speichern. Gibt den Stand zurück; ohne
     * offenen Abdruck vor dem Start tut es nichts. [note] kommt als letzte Zeile in den Abschnitt.
     */
    fun finish(context: Context, note: String? = null): OriginalCheckState {
        val s = state(context)
        val before = s.before
        if (!s.pending || before == null) return s
        val after = LabOriginalPrintLogic.take(originalFolder(context))
        val deviations = LabOriginalPrintLogic.compare(before, after)
        val lines = LabOriginalPrintLogic.sectionLines(before, after, deviations, note)
        val next = synchronized(lock) {
            val updated = LabOriginalGate.afterCheck(s, deviations, System.currentTimeMillis())
            save(context, updated)
            updated
        }
        runCatching { insertIntoProtocol(context, s.protocol, lines) }.onFailure { Timber.w(it, "Labor-Protokoll: Abschnitt Originalordner nicht eingesetzt") }
        return next
    }

    /**
     * Beim Öffnen der Seite: war ein Abdruck vor dem Start offen und läuft kein Labor-Versuch, wurde die App dazwischen beendet; dann jetzt den
     * Abdruck nach dem Ende nachholen. Gibt true zurück, wenn etwas nachgeholt wurde.
     */
    fun recover(context: Context): Boolean {
        if (LabRun.isActive()) return false
        val s = state(context)
        if (!s.pending || s.before == null) return false
        finish(context, "(taken later: the app was closed or the start did not happen before the end of the run)")
        return true
    }

    /** Wie [finish], aber in einem eigenen Faden (das Ende kommt aus dem X-Server-Bildschirm; das Lesen darf den Haupt-Thread nicht aufhalten). */
    fun finishAsync(context: Context) {
        val app = context.applicationContext
        Thread({ runCatching { finish(app) }.onFailure { Timber.w(it, "Labor: Originalprüfung nach dem Ende") } }, "lab-original-final")
            .apply { isDaemon = true }.start()
    }

    private fun insertIntoProtocol(context: Context, protocolName: String, lines: List<String>) {
        if (protocolName.isEmpty()) return
        val file = File(LabDirs.logs(context), protocolName)
        if (!file.isFile) return
        LabRun.withFileLock {
            val text = file.readText(Charsets.UTF_8)
            val patched = LabOriginalSection.insert(text, lines)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(patched, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(patched, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }
}

/** Der Abschnitt im Protokoll (rein, getestet). */
object LabOriginalSection {
    const val TITLE = "Original game folder"
    private const val END_MARKER = "\n== End ==\n"

    fun sectionText(lines: List<String>): String = "\n== $TITLE ==\n" + lines.joinToString("") { it + "\n" }

    /** Vor `== End ==` (fehlt die Marke: hinten); gibt es den Abschnitt schon, bleibt der Text, wie er ist. */
    fun insert(text: String, lines: List<String>): String {
        if (text.contains("\n== $TITLE ==\n")) return text
        val at = text.lastIndexOf(END_MARKER)
        return if (at < 0) text + sectionText(lines) else text.substring(0, at) + sectionText(lines) + text.substring(at)
    }
}
