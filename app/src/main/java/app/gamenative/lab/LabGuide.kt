package app.gamenative.lab

import org.json.JSONObject

/** Die sieben Schritte des Assistenten „Über den Launcher anmelden und spielen“, in der Reihenfolge, in der sie empfohlen werden. */
enum class GuideStep(val number: Int) {
    PACKAGES(1),
    LAUNCHER(2),
    SETTING(3),
    SIGN_IN(4),
    COPY(5),
    GAME(6),
    PLAY(7),
}

/**
 * Zustand eines Schritts. [UNKNOWN] heißt: die Quelle trägt keine Aussage (der Schritt hält die Empfehlung nicht auf). [SKIPPED]: der Nutzer hat
 * den Schritt abgewählt (Selbstauskunft „ohne Spielkopie“).
 */
enum class GuideState { DONE, OPEN, RUNNING, BLOCKED, ERROR, SKIPPED, UNKNOWN }

/** Woran eine Zustandsangabe hängt; die Seite schreibt es hinter jede Zeile („Quelle: …“). */
enum class GuideSource {
    INSTALLED_PACKAGES,
    PACKAGE_LIST,
    FILE_CHECK,
    SETTINGS,
    SELF_REPORT,
    LAST_RUN,
    COPY_MARK,
    APP_STATE,
    DISK_SPACE,
}

/**
 * Jede mögliche Zustandsangabe des Assistenten. Jede hat genau eine [source]: eine Angabe ohne Quelle gibt es nicht (der Typ lässt sie nicht zu,
 * `LabGuideTest` geht alle Eingabekombinationen durch). Die Seite übersetzt die Angabe in Text.
 */
enum class GuideFact(val source: GuideSource) {
    // Schritt 1
    PKG_FEX_INSTALLED(GuideSource.INSTALLED_PACKAGES),
    PKG_FEX_MISSING(GuideSource.INSTALLED_PACKAGES),
    PKG_FEX_NOT_LISTED(GuideSource.PACKAGE_LIST),

    // Schritt 2
    LAUNCHER_FILE_PRESENT(GuideSource.FILE_CHECK),
    LAUNCHER_FILE_MISSING(GuideSource.FILE_CHECK),
    INSTALLER_FILE_MISSING(GuideSource.FILE_CHECK),
    NO_GAME_CONTAINER(GuideSource.FILE_CHECK),

    // Schritt 3
    SETTING_APPLIED(GuideSource.SETTINGS),
    SETTING_NOT_APPLIED(GuideSource.SETTINGS),
    SETTING_NEEDS_PACKAGE(GuideSource.INSTALLED_PACKAGES),

    // Schritt 4
    SIGNED_IN_CONFIRMED(GuideSource.SELF_REPORT),
    SIGNED_IN_NOT_CONFIRMED(GuideSource.SELF_REPORT),
    SIGN_IN_NEEDS_LAUNCHER(GuideSource.FILE_CHECK),
    MAIN_WINDOW_SEEN(GuideSource.LAST_RUN),
    MAIN_WINDOW_NOT_SEEN(GuideSource.LAST_RUN),
    NO_RUN_YET(GuideSource.LAST_RUN),

    // Schritt 5
    COPY_SKIPPED(GuideSource.SELF_REPORT),
    COPY_RUNNING(GuideSource.APP_STATE),
    COPY_LAYOUT_FAILED(GuideSource.APP_STATE),
    COPY_READY(GuideSource.COPY_MARK),
    COPY_NOT_MOUNTED(GuideSource.SETTINGS),
    COPY_NO_ORIGINAL(GuideSource.FILE_CHECK),
    COPY_NO_SPACE(GuideSource.DISK_SPACE),
    COPY_PARTIAL(GuideSource.FILE_CHECK),
    COPY_NONE(GuideSource.COPY_MARK),

    // Schritt 6
    GAME_NOT_NEEDED(GuideSource.SELF_REPORT),
    GAME_NEEDS_COPY(GuideSource.COPY_MARK),
    GAME_MARKER_PRESENT(GuideSource.FILE_CHECK),
    GAME_MARKER_UNRELIABLE(GuideSource.FILE_CHECK),
    GAME_NOT_INSTALLED(GuideSource.FILE_CHECK),

    // Schritt 7
    PLAY_NOT_NEEDED(GuideSource.SELF_REPORT),
    PLAY_NEEDS_COPY(GuideSource.COPY_MARK),
    GAME_WINDOW_SEEN(GuideSource.LAST_RUN),
    GAME_WINDOW_NOT_SEEN(GuideSource.LAST_RUN),
}

/**
 * Was der eine Knopf eines Schritts tut. Alles App-eigene: nichts davon klickt oder tippt im Launcher. [ASK_COPY]: die Rückfrage der Karte „Spielkopie“
 * (Größe, freier Platz) und danach das Kopieren. [NONE]: der Schritt hat im Moment keinen
 * Knopf (er wartet auf einen anderen Schritt oder auf eine laufende Arbeit).
 */
enum class GuideAction {
    LOAD_FEX,
    LOAD_INSTALLER,
    START_INSTALLER,
    SET_LOGIN_WAY,
    START_LAUNCHER,
    ENABLE_COPY,
    ASK_COPY,
    NONE,
}

/** Ein Hinweis neben dem Zustand eines Schritts (z. B. „Hauptfenster im letzten Lauf gesehen“); auch er hat eine Quelle ([fact]). */
data class GuideEvidence(val fact: GuideFact, val atMs: Long? = null)

/** Zustand eines Schritts. [atMs]: Datum der Selbstauskunft, des Laufs oder der Kopie, auf die sich die Angabe stützt. */
data class GuideStatus(
    val step: GuideStep,
    val state: GuideState,
    val fact: GuideFact,
    val action: GuideAction = GuideAction.NONE,
    val atMs: Long? = null,
    val evidence: GuideEvidence? = null,
) {
    val source: GuideSource get() = fact.source
}

/** Die Selbstauskünfte des Nutzers (jede mit Datum, jede lässt sich zurücknehmen). Wird als Text im App-Speicher des Labors gehalten. */
data class LabGuideSelf(val signedInMs: Long? = null, val noCopyMs: Long? = null) {
    fun toJson(): String = JSONObject().apply {
        signedInMs?.let { put("signedIn", it) }
        noCopyMs?.let { put("noCopy", it) }
    }.toString()

    companion object {
        /** Kaputtes oder Fehlendes ergibt „nichts bestätigt“, nie einen Fehler. Null oder 0 zählt als nicht gesetzt. */
        fun fromJson(text: String?): LabGuideSelf {
            if (text.isNullOrBlank()) return LabGuideSelf()
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return LabGuideSelf()
            return LabGuideSelf(
                signedInMs = o.optLong("signedIn", 0L).takeIf { it > 0L },
                noCopyMs = o.optLong("noCopy", 0L).takeIf { it > 0L },
            )
        }
    }
}

/** Die Kopie, wie der Assistent sie braucht (aus [LabCopyView] und [LabGameCopyJob.isRunning]). */
data class LabGuideCopy(
    val originalPresent: Boolean = false,
    val running: Boolean = false,
    val complete: Boolean = false,
    val partial: Boolean = false,
    val layoutFailed: Boolean = false,
    val finishedMs: Long = 0L,
    val fits: Boolean = true,
    val neededBytes: Long = 0L,
    val freeBytes: Long = 0L,
)

/** Alles, was der Assistent wissen muss; jedes Feld kommt aus einer Quelle der App (Datei da, Einstellung, Marke), nie aus Dateien des Launchers. */
data class LabGuideInputs(
    val gameContainerPresent: Boolean = true,
    val fexListed: Boolean = true,
    val fexInstalled: Boolean = false,
    val installerFilePresent: Boolean = false,
    val launcherExePresent: Boolean = false,
    /** Einstellungen entsprechen der Voreinstellung „Anmeldeweg“, **ohne** den Schalter „Spielkopie“ ([LabLoginWay.isAppliedCore]). */
    val loginWayApplied: Boolean = false,
    val useGameCopy: Boolean = false,
    val self: LabGuideSelf = LabGuideSelf(),
    val copy: LabGuideCopy = LabGuideCopy(),
    /** Der jüngste Lauf mit dem Ziel Battle.net (null: noch keiner). */
    val lastRun: LabRunEntry? = null,
    /** Existenz der Kennungsdatei des Launchers in der Kopie (`.product.db`) und im Original; nur Existenz, nie der Inhalt. */
    val gameMarkerInCopy: Boolean = false,
    val gameMarkerInOriginal: Boolean = false,
)

/** Ergebnis von [LabGuide.evaluate]: je Schritt ein Zustand, der nächste empfohlene Schritt und was der Hauptknopf tut. */
data class LabGuideState(
    val steps: List<GuideStatus>,
    /** Der erste Schritt, der noch Arbeit braucht; null = alles erledigt (oder abgewählt). */
    val next: GuideStep?,
    /** Was „Weiter“ tut; [GuideAction.NONE]: nichts (der nächste Schritt wartet). Sind alle Schritte erledigt, startet „Weiter“ den Launcher (zum Spielen). */
    val continueAction: GuideAction,
) {
    fun status(step: GuideStep): GuideStatus = steps.first { it.step == step }
    val nextStatus: GuideStatus? get() = next?.let { status(it) }
}

/**
 * Zustandslogik des Assistenten: Eingaben → Zustand je Schritt, nächster empfohlener Schritt. Rein, ohne Android, JUnit-getestet. Keine Angabe ohne
 * Quelle ([GuideFact.source]); die Selbstauskunft „angemeldet“ ist nie mehr als eine Selbstauskunft (der Launcher kann nach einer Zeit abmelden),
 * das Hauptfenster im letzten Lauf ist nur ein Indiz daneben.
 */
object LabGuide {
    /** Die Kennungsdatei, die der Launcher im Spielordner anlegt (die App schreibt sie nie): ihre Existenz in der Kopie ist das Indiz „im Launcher installiert“. */
    const val GAME_MARKER = ".product.db"

    /** Ab dieser Breite zählt ein Fenster des Launchers als Hauptfenster (Anmeldefenster 362×693 und 640×480, Hauptfenster 1440×641 und 1440×696). */
    const val MAIN_WINDOW_MIN_WIDTH = 1000

    fun evaluate(i: LabGuideInputs): LabGuideState {
        val steps = listOf(packages(i), launcher(i), setting(i), signIn(i), copy(i), game(i), play(i))
        val next = steps.firstOrNull { it.state in NEEDS_WORK }?.step
        val action = when {
            next != null -> steps.first { it.step == next }.action
            i.gameContainerPresent && i.launcherExePresent -> GuideAction.START_LAUNCHER
            else -> GuideAction.NONE
        }
        return LabGuideState(steps, next, action)
    }

    private val NEEDS_WORK = setOf(GuideState.OPEN, GuideState.RUNNING, GuideState.BLOCKED, GuideState.ERROR)

    /** Teil A der Anleitung „Einmal anmelden“: Pakete, Launcher installieren, Einstellung, Anmelden. */
    val PART_A = listOf(GuideStep.PACKAGES, GuideStep.LAUNCHER, GuideStep.SETTING, GuideStep.SIGN_IN)

    /** Teil B „Wenn der direkte Start abgelehnt wird“: Spielkopie, Spiel im Launcher installieren, über den Launcher spielen. */
    val PART_B = listOf(GuideStep.COPY, GuideStep.GAME, GuideStep.PLAY)

    /** Die Einrichtung von Teil B: Spielkopie und Spiel im Launcher; Schritt 7 ist das Spielen selbst und zählt nicht dazu. */
    private val PART_B_SETUP = listOf(GuideStep.COPY, GuideStep.GAME)

    /** Teil A ist erledigt, wenn die Schritte 1 bis 4 erledigt sind (Schritt 4 ist die Selbstauskunft des Nutzers). */
    fun partADone(state: LabGuideState): Boolean = PART_A.all { state.status(it).state == GuideState.DONE }

    /** Der erste Schritt von Teil B, der noch Arbeit braucht (Kopie, Spiel im Launcher); null = Teil B ist eingerichtet. „Ohne Spielkopie“ und „ohne Aussage“ halten nicht auf. */
    fun partBMissing(state: LabGuideState): GuideStep? = PART_B_SETUP.firstOrNull { state.status(it).state in NEEDS_WORK }

    fun partBDone(state: LabGuideState): Boolean = partBMissing(state) == null

    private fun packages(i: LabGuideInputs): GuideStatus = when {
        i.fexInstalled -> GuideStatus(GuideStep.PACKAGES, GuideState.DONE, GuideFact.PKG_FEX_INSTALLED)
        !i.fexListed -> GuideStatus(GuideStep.PACKAGES, GuideState.ERROR, GuideFact.PKG_FEX_NOT_LISTED)
        else -> GuideStatus(GuideStep.PACKAGES, GuideState.OPEN, GuideFact.PKG_FEX_MISSING, GuideAction.LOAD_FEX)
    }

    private fun launcher(i: LabGuideInputs): GuideStatus = when {
        i.launcherExePresent -> GuideStatus(GuideStep.LAUNCHER, GuideState.DONE, GuideFact.LAUNCHER_FILE_PRESENT)
        !i.gameContainerPresent -> GuideStatus(GuideStep.LAUNCHER, GuideState.BLOCKED, GuideFact.NO_GAME_CONTAINER)
        i.installerFilePresent ->
            GuideStatus(GuideStep.LAUNCHER, GuideState.OPEN, GuideFact.LAUNCHER_FILE_MISSING, GuideAction.START_INSTALLER)
        else -> GuideStatus(GuideStep.LAUNCHER, GuideState.OPEN, GuideFact.INSTALLER_FILE_MISSING, GuideAction.LOAD_INSTALLER)
    }

    private fun setting(i: LabGuideInputs): GuideStatus = when {
        i.loginWayApplied -> GuideStatus(GuideStep.SETTING, GuideState.DONE, GuideFact.SETTING_APPLIED)
        !i.fexInstalled -> GuideStatus(GuideStep.SETTING, GuideState.OPEN, GuideFact.SETTING_NEEDS_PACKAGE)
        else -> GuideStatus(GuideStep.SETTING, GuideState.OPEN, GuideFact.SETTING_NOT_APPLIED, GuideAction.SET_LOGIN_WAY)
    }

    private fun signIn(i: LabGuideInputs): GuideStatus {
        val evidence = i.lastRun?.let {
            GuideEvidence(if (it.mainWindowSeen) GuideFact.MAIN_WINDOW_SEEN else GuideFact.MAIN_WINDOW_NOT_SEEN, it.startedMs)
        } ?: GuideEvidence(GuideFact.NO_RUN_YET)
        val signedIn = i.self.signedInMs
        return when {
            signedIn != null -> GuideStatus(GuideStep.SIGN_IN, GuideState.DONE, GuideFact.SIGNED_IN_CONFIRMED, atMs = signedIn, evidence = evidence)
            !i.gameContainerPresent -> GuideStatus(GuideStep.SIGN_IN, GuideState.BLOCKED, GuideFact.NO_GAME_CONTAINER, evidence = evidence)
            !i.launcherExePresent -> GuideStatus(GuideStep.SIGN_IN, GuideState.OPEN, GuideFact.SIGN_IN_NEEDS_LAUNCHER, evidence = evidence)
            else -> GuideStatus(GuideStep.SIGN_IN, GuideState.OPEN, GuideFact.SIGNED_IN_NOT_CONFIRMED, GuideAction.START_LAUNCHER, evidence = evidence)
        }
    }

    private fun copy(i: LabGuideInputs): GuideStatus {
        val c = i.copy
        val step = GuideStep.COPY
        i.self.noCopyMs?.let { return GuideStatus(step, GuideState.SKIPPED, GuideFact.COPY_SKIPPED, atMs = it) }
        return when {
            c.running -> GuideStatus(step, GuideState.RUNNING, GuideFact.COPY_RUNNING)
            c.layoutFailed -> GuideStatus(step, GuideState.ERROR, GuideFact.COPY_LAYOUT_FAILED)
            c.complete && i.useGameCopy -> GuideStatus(step, GuideState.DONE, GuideFact.COPY_READY, atMs = c.finishedMs.takeIf { it > 0L })
            c.complete -> GuideStatus(step, GuideState.OPEN, GuideFact.COPY_NOT_MOUNTED, GuideAction.ENABLE_COPY, atMs = c.finishedMs.takeIf { it > 0L })
            !c.originalPresent -> GuideStatus(step, GuideState.BLOCKED, GuideFact.COPY_NO_ORIGINAL)
            !c.fits -> GuideStatus(step, GuideState.BLOCKED, GuideFact.COPY_NO_SPACE)
            c.partial -> GuideStatus(step, GuideState.OPEN, GuideFact.COPY_PARTIAL, GuideAction.ASK_COPY)
            else -> GuideStatus(step, GuideState.OPEN, GuideFact.COPY_NONE, GuideAction.ASK_COPY)
        }
    }

    private fun copyReady(i: LabGuideInputs): Boolean = i.copy.complete && i.useGameCopy

    private fun game(i: LabGuideInputs): GuideStatus {
        val step = GuideStep.GAME
        i.self.noCopyMs?.let { return GuideStatus(step, GuideState.SKIPPED, GuideFact.GAME_NOT_NEEDED, atMs = it) }
        val canStart = i.gameContainerPresent && i.launcherExePresent
        return when {
            !copyReady(i) -> GuideStatus(step, GuideState.OPEN, GuideFact.GAME_NEEDS_COPY)
            // Hat schon das Original diese Datei, sagt ihre Existenz in der Kopie nichts über den Launcher.
            i.gameMarkerInOriginal -> GuideStatus(step, GuideState.UNKNOWN, GuideFact.GAME_MARKER_UNRELIABLE)
            i.gameMarkerInCopy -> GuideStatus(step, GuideState.DONE, GuideFact.GAME_MARKER_PRESENT)
            else -> GuideStatus(step, GuideState.OPEN, GuideFact.GAME_NOT_INSTALLED, if (canStart) GuideAction.START_LAUNCHER else GuideAction.NONE)
        }
    }

    private fun play(i: LabGuideInputs): GuideStatus {
        val step = GuideStep.PLAY
        i.self.noCopyMs?.let { return GuideStatus(step, GuideState.SKIPPED, GuideFact.PLAY_NOT_NEEDED, atMs = it) }
        val run = i.lastRun
        val canStart = i.gameContainerPresent && i.launcherExePresent
        return when {
            run != null && run.gameWindowSeen -> GuideStatus(step, GuideState.DONE, GuideFact.GAME_WINDOW_SEEN, atMs = run.startedMs)
            !copyReady(i) -> GuideStatus(step, GuideState.OPEN, GuideFact.PLAY_NEEDS_COPY)
            else -> GuideStatus(
                step, GuideState.OPEN, GuideFact.GAME_WINDOW_NOT_SEEN,
                if (canStart) GuideAction.START_LAUNCHER else GuideAction.NONE, atMs = run?.startedMs,
            )
        }
    }

    /** Der jüngste Lauf mit dem Ziel Battle.net (nur der zählt für den Assistenten; Installer und Stufentest nicht). */
    fun lastLauncherRun(runs: List<LabRunEntry>): LabRunEntry? =
        runs.filter { it.target == LabTarget.BATTLENET.name.lowercase() }.maxByOrNull { it.startedMs }

    /**
     * Ob nach diesem Lauf „Launcher neu starten“ vorgeschlagen wird: die Hänger-Erkennung stand am Ende auf Hänger oder Fehlerfenster oder hat
     * im Lauf einmal „kein Fenster“ gemeldet, und der Nutzer hat nicht schon selbst neu gestartet. Ein gesehenes Spielfenster hebt den Vorschlag auf.
     */
    fun suggestsRestart(run: LabRunEntry?): Boolean {
        if (run == null || run.restartRequested || run.gameWindowSeen) return false
        return run.noWindowSeen || run.watchEnd == LabWatchState.NO_WINDOW.text || run.watchEnd == LabWatchState.ERROR_WINDOW.text
    }
}

/**
 * Was die Fensterereignisse eines Laufs über Launcher und Spiel sagen (Klasse und Breite, nie Inhalte). Rein, JUnit-getestet. Quelle sind die
 * Ereignisse der Fenster-Zeitleiste ([LabWindowTimeline]), die schon jetzt den Abschnitt „Windows“ des Protokolls füllen.
 */
object LabRunWindows {
    data class Seen(val mainWindow: Boolean, val gameWindow: Boolean)

    fun summarize(events: List<LabWinEvent>): Seen {
        var main = false
        var game = false
        for (e in events) {
            if (e.kind != LabWinEventKind.OPEN && e.kind != LabWinEventKind.SEEN && e.kind != LabWinEventKind.TITLE) continue
            val cls = e.className.trim()
            if (cls.equals(LabGameControlsLogic.GAME_CLASS, ignoreCase = true)) game = true
            if (cls.contains(LabWatch.LAUNCHER_CLASS_PART, ignoreCase = true) && e.width >= LabGuide.MAIN_WINDOW_MIN_WIDTH) main = true
        }
        return Seen(main, game)
    }
}
