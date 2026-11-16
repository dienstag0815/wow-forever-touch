package app.gamenative.lab

/**
 * Zustand des Launchers im laufenden Versuch, nur aus der Fensterliste des X-Servers abgeleitet (Klasse, Titel, Größe; nie Inhalte).
 * [text] steht so im Protokoll (`-- Watch: window_ok after 13.4 s`).
 */
enum class LabWatchState(val text: String) {
    /** Noch kein Fenster des Launchers gesehen, die Zeit bis [LabWatch.NO_WINDOW_AFTER_MS] läuft noch. */
    STARTING("starting"),

    /** Ein Fenster des Launchers (oder des Spiels) ist offen, oder das letzte ist erst kurz weg. */
    WINDOW_OK("window_ok"),

    /** Seit dem Start oder seit dem letzten Fenster ist länger als [LabWatch.NO_WINDOW_AFTER_MS] kein Fenster des Launchers sichtbar. */
    NO_WINDOW("no_window"),

    /** Ein Fehlerfenster des Launchers ist offen (Titel mit „Fehler“ oder „Error“). */
    ERROR_WINDOW("error_window"),
}

/** [lastWindowMs]: Zeit seit Start des Laufs, zu der zuletzt ein Fenster des Launchers oder des Spiels offen war (null: noch nie). */
data class LabWatchStatus(val state: LabWatchState = LabWatchState.STARTING, val lastWindowMs: Long? = null)

/** Ein Zustandswechsel, den der Aufrufer ins Protokoll schreibt. [atMs]: Zeit seit Start des Laufs. */
data class LabWatchChange(val from: LabWatchState, val to: LabWatchState, val atMs: Long)

data class LabWatchStep(val status: LabWatchStatus, val change: LabWatchChange? = null)

/**
 * Hänger-Erkennung des Labors: rein, ohne Android, JUnit-getestet. Sie wird mit jedem Schnappschuss der Fensterliste gefüttert (die Tastenleiste
 * holt ihn ohnehin) und sagt, ob der Nutzer einen Hinweis „Launcher neu starten?“ sehen soll. Sie beendet und startet nie etwas selbst.
 *
 * Der Agent-Probe (`LabAgentProbe`) fließt **nicht** ein: in den Protokollen vom 15.11. war der Agent im Lauf ohne Fenster erreichbar (`status=401`) und im
 * gesunden Lauf `connection refused`; er unterscheidet die Fälle nicht (siehe Bericht labweg1, Befund B2).
 */
object LabWatch {
    /**
     * So lange darf nach dem Start (oder nach dem letzten Fenster) kein Fenster des Launchers sichtbar sein, bevor `no_window` gilt. In den
     * Protokollen vom 15.11. erschien das erste Fenster des Launchers nach +13,1 bis +20,5 s und das Hauptfenster nach +15,1 bis +31,4 s (ein Lauf: erst
     * +169 s, aber das Anmeldefenster stand ab +17,8 s, es gab also nie 60 s ohne Fenster); 60 s ist knapp das Dreifache des spätesten ersten Fensters.
     */
    const val NO_WINDOW_AFTER_MS = 60_000L

    /** Teil der Fensterklasse des Launchers (`battle.net.exe`), ohne Rücksicht auf Groß-/Kleinschreibung. */
    const val LAUNCHER_CLASS_PART = "battle.net"

    fun isLauncherWindow(entry: LabWinEntry): Boolean = entry.className.contains(LAUNCHER_CLASS_PART, ignoreCase = true)

    /** Ein Fenster des Launchers mit einem Fehler-Titel ([LabWindowsReport.isErrorTitle]). */
    fun isErrorWindow(entry: LabWinEntry): Boolean = isLauncherWindow(entry) && LabWindowsReport.isErrorTitle(entry.title)

    /** Neuer Schnappschuss der Fensterliste, [elapsedMs] Millisekunden nach dem Start des Laufs. */
    fun onSnapshot(status: LabWatchStatus, snapshot: LabWinSnapshot, elapsedMs: Long): LabWatchStep {
        val game = snapshot.entries.any { LabGameControlsLogic.isGameWindow(it) }
        val error = snapshot.entries.any { isErrorWindow(it) }
        val launcher = snapshot.entries.any { isLauncherWindow(it) }
        val seen = game || launcher
        val last = if (seen) elapsedMs else status.lastWindowMs
        val next = when {
            // Solange ein Spielfenster offen ist, gibt es keinen Hinweis (auch kein Fehlerfenster des Launchers daneben).
            game -> LabWatchState.WINDOW_OK
            error -> LabWatchState.ERROR_WINDOW
            launcher -> LabWatchState.WINDOW_OK
            elapsedMs - (last ?: 0L) >= NO_WINDOW_AFTER_MS -> LabWatchState.NO_WINDOW
            last == null -> LabWatchState.STARTING
            // Das letzte Fenster ist erst kurz weg (Anmeldefenster → Hauptfenster, Fehlerfenster geschlossen): kein Hinweis.
            else -> LabWatchState.WINDOW_OK
        }
        val change = if (next != status.state) LabWatchChange(status.state, next, elapsedMs) else null
        return LabWatchStep(LabWatchStatus(next, last), change)
    }

    /** Ob die Tastenleiste den Hinweis mit dem Knopf „Launcher neu starten“ zeigt. */
    fun showsHint(state: LabWatchState): Boolean = state == LabWatchState.NO_WINDOW || state == LabWatchState.ERROR_WINDOW

    private fun seconds(ms: Long): String = "%d.%d".format(ms / 1000, (ms % 1000) / 100)

    /** Zeile im Protokoll bei einem Zustandswechsel. */
    fun changeLine(change: LabWatchChange): String = "-- Watch: ${change.to.text} after ${seconds(change.atMs)} s"

    /** Zeile im Protokoll, wenn der Nutzer „Launcher neu starten“ getippt hat. */
    fun restartLine(elapsedMs: Long): String = "-- Restart requested by user after ${seconds(elapsedMs)} s"
}

/**
 * Der Neustart-Wunsch des Nutzers, wenn er „Launcher neu starten“ getippt hat: der Lauf wird wie bei „Exit“ beendet, danach bietet die Labor-Seite
 * „Erneut starten“ für dasselbe Ziel an. Er startet nie von selbst; der Wunsch verfällt nach [OFFER_TTL_MS]. Nur im Speicher, nichts wird gespeichert.
 */
object LabRestart {
    const val OFFER_TTL_MS = 10 * 60 * 1000L

    private val lock = Any()
    private var target: LabTarget? = null
    private var atMs = 0L

    fun offer(newTarget: LabTarget, nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        target = newTarget
        atMs = nowMs
    }

    /** Das Ziel, das die Seite anbietet, oder null (kein Wunsch oder verfallen). */
    fun pending(nowMs: Long = System.currentTimeMillis()): LabTarget? = synchronized(lock) {
        val t = target ?: return@synchronized null
        if (nowMs - atMs > OFFER_TTL_MS) {
            target = null
            null
        } else {
            t
        }
    }

    fun clear() = synchronized(lock) { target = null }
}
