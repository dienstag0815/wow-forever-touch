package app.gamenative.report

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wann der Dialog "Bericht bereit" fällig ist (reine Logik, JUnit-getestet). Fällig wird er, sobald ein Bericht fertig ist und die App
 * im Vordergrund steht; ein Schließen, ein neuer Bericht oder das Schließen der Karte nimmt ihn zurück. Der Zustand liegt nur im Speicher:
 * nach einem Neustart der App gibt es keinen Dialog, nur die gespeicherte Karte ([ReportCardStore]).
 */
class ReportDialogState {
    private val dueFor = MutableStateFlow<Long?>(null)

    /** Zeit ([ReportCard.createdMs]) des Berichts, dessen Dialog fällig ist; null = keiner. */
    val due: StateFlow<Long?> get() = dueFor

    /** Ein Bericht ist fertig. Steht die App im Hintergrund, bleibt es bei der Karte. */
    fun reportReady(card: ReportCard, appVisible: Boolean) {
        dueFor.value = if (appVisible) card.createdMs else null
    }

    /** Der Spieler hat den Dialog geschlossen: er kommt für diesen Bericht nicht wieder. */
    fun dismiss() {
        dueFor.value = null
    }

    companion object {
        /** Ist für [card] ein Dialog fällig? Ohne Karte (geschlossen) nie; ein anderer, älterer Bericht zählt nicht. */
        fun isDue(due: Long?, card: ReportCard?): Boolean = card != null && due != null && due == card.createdMs
    }
}

/** Der eine Halter für die Oberfläche. */
object ReportDialog {
    val state = ReportDialogState()
}
