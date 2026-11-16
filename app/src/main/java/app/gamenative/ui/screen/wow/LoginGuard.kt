package app.gamenative.ui.screen.wow

import app.gamenative.lab.LabIds
import app.gamenative.lab.LabRun

/**
 * Sperre der gespeicherten Zugangsdaten im Battle.net-Labor (Container `bnetlab`): dort tippt die App sie nie in ein Fenster und schreibt
 * nie eine Anmeldedatei. Die Entscheidung ist rein ([mayType], [mayWriteFile]); die Wege rufen sie an einer Stelle auf:
 * `LoginFill.start` (Knopf und Menüzeilen), der Knopf selbst, die Menüzeilen und `prepareLoginFile`.
 *
 * [setActiveContainer] merkt sich, welcher Container gerade seinen Wine-Prozess aufbaut (gesetzt von `XServerScreen` bei jedem Start,
 * für jeden Container). Ohne Angabe (kein Start gesehen) gilt der Container nicht als Labor; zusätzlich sperrt ein laufender Labor-Versuch
 * ([LabRun.isActive]) auf jeden Fall.
 */
object LoginGuard {
    @Volatile
    private var activeContainerId: String? = null

    fun setActiveContainer(containerId: String?) {
        activeContainerId = containerId
    }

    /** Rein: darf in diesem Container (und bei diesem Stand des Labors) ein Zugangsdatum getippt werden? */
    fun mayType(containerId: String?, labActive: Boolean): Boolean = !labActive && containerId != LabIds.CONTAINER_ID

    /** Rein: darf eine Anmeldedatei geschrieben werden? Nie während eines Labor-Versuchs. */
    fun mayWriteFile(labActive: Boolean): Boolean = !labActive

    /** Für den Knopf und die Menüzeilen, die den Container des Bildschirms kennen. */
    fun mayType(containerId: String?): Boolean = mayType(containerId, LabRun.isActive()) && mayType(activeContainerId, false)

    /** Für `LoginFill.start`: der zuletzt gestartete Container. */
    fun mayType(): Boolean = mayType(activeContainerId, LabRun.isActive())

    fun mayWriteFile(): Boolean = mayWriteFile(LabRun.isActive())
}
