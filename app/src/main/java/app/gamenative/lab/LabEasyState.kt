package app.gamenative.lab

/** Der Zustandssatz der Ansicht „Einfach“ (Entwurf 3.1, Nummer 1). */
enum class EasyHeadline {
    /** „Als Nächstes: <Schritt>.“ */
    NEXT,

    /** Der nächste Schritt hat gerade keinen Knopf (wartet auf einen anderen Schritt oder eine laufende Arbeit). */
    WAITING,

    /** Alle Schritte erledigt oder abgewählt. */
    ALL_DONE,

    /** Hänger-Hinweis: der Lauf hat kein Fenster gezeigt oder ein Fehlerfenster. Er ersetzt den Satz „Als Nächstes“. */
    STUCK,

    /** Teil A („Einmal anmelden“, Schritte 1–4) ist erledigt: „Angemeldet. Du kannst das Spiel jetzt direkt starten.“ Teil B ist Ausweichweg und hält nicht auf. */
    PART_A_DONE,

    /** Ein Lauf wurde aus der Tastenleiste beendet („Launcher neu starten“); die Seite bietet an, denselben Plan noch einmal zu starten. */
    RESTART_PENDING,
}

/** Was der Hauptknopf tut. [NONE]: kein Knopf. */
enum class EasyMain {
    /** „Weiter: <Schritt>“: die Aktion des nächsten Schritts. */
    CONTINUE,

    /** „Spiel direkt starten“ nach Teil A: führt zurück auf die Startseite (der Direktstart selbst bleibt dort unverändert der Knopf „Spielen“). */
    DIRECT_START,

    /** „Battle.net starten“, wenn alle Schritte erledigt sind. */
    START,

    /** „Battle.net neu starten“ im Hänger-Zustand (beendet nichts selbst, startet denselben Plan noch einmal). */
    RESTART,

    /** Der Neustart-Vorschlag aus der Tastenleiste („Erneut starten“). */
    RESTART_PENDING,

    NONE,
}

/** Haken vor einer Schrittzeile: ✓ erledigt, ○ offen, ! Problem, – abgewählt oder ohne Aussage. */
enum class EasyMark { DONE, OPEN, PROBLEM, SKIPPED }

/**
 * Was die Ansicht „Einfach“ zeigt, reduziert auf die Entscheidung: Zustandssatz, Hauptknopf, Haken je Schritt. Rein, aus [LabGuide.evaluate]. Hängt
 * nichts Neues an: die Aktion des Hauptknopfs ist [LabGuideState.continueAction], der Hänger-Zustand ist [LabGuide.suggestsRestart], der Vorschlag
 * aus der Tastenleiste ist [LabRestart.pending] (heute `restart`-Karte).
 */
data class LabEasyState(
    val headline: EasyHeadline,
    /** Der Schritt, den der Satz nennt (bei [EasyHeadline.NEXT] und [EasyHeadline.WAITING]). */
    val step: GuideStep?,
    val main: EasyMain,
    /** Die Aktion hinter [EasyMain.CONTINUE] und [EasyMain.START]. */
    val action: GuideAction,
    /** Ob der Hauptknopf bedienbar wäre, wenn nichts anderes läuft (beim Neustart: Launcher-Datei und Container da). */
    val mainUsable: Boolean,
    val marks: Map<GuideStep, EasyMark>,
) {
    companion object {
        fun mark(state: GuideState): EasyMark = when (state) {
            GuideState.DONE -> EasyMark.DONE
            GuideState.OPEN, GuideState.RUNNING -> EasyMark.OPEN
            GuideState.BLOCKED, GuideState.ERROR -> EasyMark.PROBLEM
            GuideState.SKIPPED, GuideState.UNKNOWN -> EasyMark.SKIPPED
        }

        fun of(guide: LabGuideState, inputs: LabGuideInputs, restartPending: Boolean): LabEasyState {
            val marks = guide.steps.associate { it.step to mark(it.state) }
            val canRestart = inputs.launcherExePresent && inputs.gameContainerPresent
            val next = guide.nextStatus
            return when {
                restartPending -> LabEasyState(EasyHeadline.RESTART_PENDING, null, EasyMain.RESTART_PENDING, GuideAction.NONE, true, marks)
                LabGuide.suggestsRestart(inputs.lastRun) ->
                    LabEasyState(EasyHeadline.STUCK, null, EasyMain.RESTART, GuideAction.START_LAUNCHER, canRestart, marks)
                LabGuide.partADone(guide) -> LabEasyState(EasyHeadline.PART_A_DONE, null, EasyMain.DIRECT_START, GuideAction.NONE, true, marks)
                guide.continueAction != GuideAction.NONE && next != null ->
                    LabEasyState(EasyHeadline.NEXT, next.step, EasyMain.CONTINUE, guide.continueAction, true, marks)
                guide.continueAction != GuideAction.NONE ->
                    LabEasyState(EasyHeadline.ALL_DONE, null, EasyMain.START, guide.continueAction, true, marks)
                next != null -> LabEasyState(EasyHeadline.WAITING, next.step, EasyMain.NONE, GuideAction.NONE, false, marks)
                else -> LabEasyState(EasyHeadline.ALL_DONE, null, EasyMain.NONE, GuideAction.NONE, false, marks)
            }
        }
    }
}
