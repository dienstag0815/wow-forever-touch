package app.gamenative.lab

/** Zustand des Siebenmal-Tippens auf die Versionszeile der Hilfeseite. */
data class LabTapState(val count: Int = 0, val lastMs: Long = 0L)

/**
 * Der versteckte Einstieg: siebenmal auf die Versionszeile der Hilfeseite tippen (höchstens [MAX_GAP_MS] zwischen zwei Tipps), dann zeigt
 * die Hilfeseite den Eintrag "Labor". Ohne diesen Handgriff ist vom Labor nichts zu sehen. Rein, JUnit-getestet.
 */
object LabEntryLogic {
    const val TAPS_NEEDED = 7
    const val MAX_GAP_MS = 2_000L

    data class Step(val state: LabTapState, val unlocked: Boolean)

    /** Ein Tipp zur Zeit [nowMs]: zu lange Pause = von vorn zählen; der siebte Tipp schaltet frei und zählt neu. */
    fun tap(state: LabTapState, nowMs: Long): Step {
        val count = if (state.count > 0 && nowMs - state.lastMs in 0..MAX_GAP_MS) state.count + 1 else 1
        return if (count >= TAPS_NEEDED) Step(LabTapState(), true) else Step(LabTapState(count, nowMs), false)
    }
}

/** Sperre zwischen Labor und Spiel: nie beides zugleich (es teilen sich dieselbe Wine-Umgebung und denselben X-Server). */
object LabGuard {
    enum class Block { NONE, GAME_RUNNING, LAB_RUNNING }

    /**
     * Darf das Labor starten? [keepAlive]: ein Gastprogramm läuft (App-weit, auch das Spiel). [labActive]: der laufende Versuch ist ein
     * Labor-Versuch. Läuft eines davon, startet das Labor nicht.
     */
    fun forLabStart(keepAlive: Boolean, labActive: Boolean): Block = when {
        labActive -> Block.LAB_RUNNING
        keepAlive -> Block.GAME_RUNNING
        else -> Block.NONE
    }

    /**
     * Darf der Wächter "Programmfenster weg und nur noch Kern-Prozesse" die Sitzung beenden? Im Labor nie: ein Installer schließt sein
     * Fenster und startet ein anderes Programm (Battle.net), das Fenster des ersten ist dann zu Recht weg.
     */
    fun exitWatchAllowed(containerId: String): Boolean = containerId != LabIds.CONTAINER_ID

    /** Darf das Spiel starten? Nicht, solange ein Labor-Versuch läuft. */
    fun gameMayStart(labActive: Boolean): Boolean = !labActive
}
