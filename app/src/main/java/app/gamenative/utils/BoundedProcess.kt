package app.gamenative.utils

import java.util.concurrent.TimeUnit

/**
 * Hilfen, damit ein hängender Hilfsprozess (z. B. `wineserver -k`) nie die Oberfläche einfriert:
 * [awaitOrKill] wartet höchstens eine feste Zeit und zerstört den Prozess dann, [runOffMain] schiebt blockierende Arbeit
 * vom Hauptthread weg. Beides ohne Android-Klassen, damit es als JUnit-Test läuft.
 */
object BoundedProcess {
    /** Zeitlimit für `wineserver -k`; danach wird der Prozess zerstört und das Beenden geht weiter. */
    const val WINESERVER_KILL_TIMEOUT_MS = 3_000L

    /** Nach dem Zerstören wartet [awaitOrKill] höchstens so lange auf das Ende des Prozesses. */
    const val KILL_GRACE_MS = 500L

    /**
     * Wartet bis zu [timeoutMs] auf das Ende von [process]. Läuft er dann noch, wird er mit `destroyForcibly` beendet und
     * höchstens [graceMs] auf das Ende gewartet. Gibt true zurück, wenn der Prozess rechtzeitig von selbst fertig wurde,
     * false nach einem Abbruch (auch wenn das Warten unterbrochen wird).
     */
    @JvmStatic
    @JvmOverloads
    fun awaitOrKill(process: Process, timeoutMs: Long, graceMs: Long = KILL_GRACE_MS): Boolean {
        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (finished) return true
        process.destroyForcibly()
        try {
            process.waitFor(graceMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return false
    }

    /**
     * Führt [task] sofort aus, außer auf dem Hauptthread ([onMainThread] = true): dort läuft sie in einem eigenen
     * Hintergrundthread und der Aufrufer wartet nicht. Gibt den Thread zurück (oder null, wenn die Arbeit inline lief).
     */
    @JvmStatic
    fun runOffMain(onMainThread: Boolean, name: String, task: Runnable): Thread? {
        if (!onMainThread) {
            task.run()
            return null
        }
        return Thread(task, name).apply {
            isDaemon = true
            start()
        }
    }
}
