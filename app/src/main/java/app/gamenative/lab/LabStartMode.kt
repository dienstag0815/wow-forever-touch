package app.gamenative.lab

/** Die Startart der Startseite: über den Battle.net-Launcher oder der bisherige Direktstart. Gespeichert in [LabStore] (`key`). */
enum class StartMode(val key: String) {
    BATTLENET("battlenet"),
    DIRECT("direct"),
    ;

    companion object {
        /** Unbekanntes oder fehlendes ergibt null („nie gewählt“), nie einen Fehler. */
        fun fromKey(key: String?): StartMode? = entries.firstOrNull { it.key == key }
    }
}

/** Ob die Startart vom Nutzer gewählt wurde oder die Vorgabe der App ist (Zeile `Start mode:` im Protokoll). */
enum class StartSource(val text: String) { CHOSEN("chosen"), DEFAULT("default") }

/** Die wirksame Startart und woher sie kommt. */
data class EffectiveStart(val mode: StartMode, val source: StartSource)

/** Von wo ein Start kam (Zeile `Started from:` im Protokoll). */
enum class LabStartOrigin(val text: String) { HOME("home"), TEST_AREA("test area") }

/** Was ein Tipp auf „Spielen“ tut. */
sealed class StartRoute {
    /** Der bisherige Weg (`startLaunch`), unverändert. */
    object Direct : StartRoute()

    /** Der Start des Battle.net-Wegs wie der Hauptknopf der Labor-Seite (`LabLauncher.prepare` mit dem Ziel Battle.net). */
    object Launcher : StartRoute()

    /** Voraussetzungen fehlen: die Anleitung öffnen statt zu starten; [missing] ist der erste fehlende Schritt (Paket, Launcher, Einstellung). */
    data class Guide(val missing: GuideStep) : StartRoute()
}

/**
 * Was die Startwahl über den Zustand wissen muss, alles aus App-Eigenem (Assistent [LabGuide], gespeicherte Wahl).
 * [chosen]: die bewusste Wahl des Nutzers, null = nie gewählt. [startable]: Pakete, Launcher-Datei und Voreinstellung vorhanden (Schritte 1–3
 * des Assistenten), der Start über den Launcher kann beginnen. [setUp]: Teil A erledigt (Schritte 1–4). [signedIn]: Schritt 4 ist als erledigt gemerkt (Selbstauskunft „angemeldet“).
 * [partBDone]: Teil B eingerichtet (Spielkopie und Spiel im Launcher, [LabGuide.partBDone]). [missingB]: der erste Schritt, der für „Über Battle.net“
 * fehlt (Schritte 1–3, dann Teil B); null, wenn nichts fehlt.
 */
data class LabStartFacts(
    val chosen: StartMode? = null,
    val startable: Boolean = false,
    val setUp: Boolean = false,
    val signedIn: Boolean = false,
    val missing: GuideStep? = null,
    val partBDone: Boolean = false,
    val missingB: GuideStep? = null,
)

/** Was der Tipp auf einen Eintrag der Startwahl tut: die Wahl speichern oder die Anleitung bei dem Schritt öffnen, der für „Über Battle.net“ fehlt (keine Wahl, kein Start). */
sealed class ChooseResult {
    data class Commit(val mode: StartMode) : ChooseResult()

    data class OpenGuide(val missing: GuideStep) : ChooseResult()
}

/**
 * Entscheidungslogik der Startwahl auf der Startseite (rein, ohne Android, JUnit-getestet).
 *
 * Vorgabe ohne Wahl: **Direkt**, in allen Fällen (Konzept „einmal anmelden“: der Nutzer meldet sich einmal im Battle.net-Programm an, danach startet
 * das Spiel direkt; der Start über den Launcher bleibt als Ausweichweg wählbar). Der Direktstart selbst (`startLaunch`) wird hier nicht berührt.
 */
object LabStartMode {
    /** Die ersten drei Schritte des Assistenten: ohne sie startet der Battle.net-Weg nicht. */
    private val START_STEPS = listOf(GuideStep.PACKAGES, GuideStep.LAUNCHER, GuideStep.SETTING)

    /** Baut die Fakten aus dem Zustand des Assistenten. */
    fun facts(chosen: StartMode?, guide: LabGuideState): LabStartFacts {
        val missing = START_STEPS.firstOrNull { guide.status(it).state != GuideState.DONE }
        val startable = missing == null
        return LabStartFacts(
            chosen = chosen,
            startable = startable,
            setUp = LabGuide.partADone(guide),
            signedIn = guide.status(GuideStep.SIGN_IN).state == GuideState.DONE,
            missing = missing,
            partBDone = LabGuide.partBDone(guide),
            missingB = missing ?: LabGuide.partBMissing(guide),
        )
    }

    /** Wirksame Startart: die Wahl des Nutzers, sonst immer Direkt. */
    fun effective(f: LabStartFacts): EffectiveStart =
        if (f.chosen != null) EffectiveStart(f.chosen, StartSource.CHOSEN) else EffectiveStart(StartMode.DIRECT, StartSource.DEFAULT)

    /** Der Tipp auf „Spielen“: Direktstart wie bisher, Start des Launcher-Wegs oder (bei fehlenden Voraussetzungen) die Anleitung. */
    fun route(f: LabStartFacts): StartRoute = when (effective(f).mode) {
        StartMode.DIRECT -> StartRoute.Direct
        StartMode.BATTLENET -> if (f.startable) StartRoute.Launcher else StartRoute.Guide(f.missing ?: GuideStep.PACKAGES)
    }

    /**
     * Führt die Weiche aus: genau einer der drei Wege wird gerufen. [direct] ist der bisherige Start (`startLaunch`), er bekommt hier keine
     * Parameter und keine Zusatzbedingung; der Aufrufer reicht ihn unverändert durch (Durchstich-Test: bei `DIRECT` nur dieser Aufruf).
     */
    fun dispatch(f: LabStartFacts, direct: () -> Unit, launcher: () -> Unit, guide: (GuideStep) -> Unit) {
        when (val route = route(f)) {
            StartRoute.Direct -> direct()
            StartRoute.Launcher -> launcher()
            is StartRoute.Guide -> guide(route.missing)
        }
    }

    /**
     * Der Tipp auf einen Eintrag der Startwahl. „Direkt starten“ gilt immer sofort (keine Rückfrage). „Über Battle.net starten“ ist nur wählbar, wenn
     * Teil B eingerichtet ist (und die Schritte 1–3 stehen); sonst öffnet der Tipp die Anleitung bei dem Schritt, der fehlt, ohne etwas zu speichern oder zu starten.
     * Wer „Über Battle.net“ in 9.34–9.37 schon gewählt hat, behält die Wahl: sie liegt in [LabStartFacts.chosen] und [route] ändert sich für sie nicht.
     */
    fun choose(f: LabStartFacts, target: StartMode): ChooseResult = when {
        target == StartMode.DIRECT -> ChooseResult.Commit(StartMode.DIRECT)
        f.chosen == StartMode.BATTLENET -> ChooseResult.Commit(StartMode.BATTLENET)
        f.partBDone && f.startable -> ChooseResult.Commit(StartMode.BATTLENET)
        else -> ChooseResult.OpenGuide(f.missingB ?: GuideStep.COPY)
    }

    /** Ob die Startseite die Zeile „Tipp: einmal bei Battle.net anmelden“ zeigt: solange Schritt 4 (Teil A) nicht als erledigt gemerkt ist. */
    fun showSignInTip(f: LabStartFacts): Boolean = !f.signedIn

    /** Ob beim Öffnen der App der Spielstart-Countdown laufen darf: nur beim Direktstart. Beim Battle.net-Weg startet nie etwas von selbst. */
    fun autoStartAllowed(e: EffectiveStart): Boolean = e.mode == StartMode.DIRECT

    /** Die Zeile `Start mode:` im Protokoll. [e] null = nicht ermittelbar. */
    fun modeLine(e: EffectiveStart?): String =
        if (e == null) "Start mode: unknown" else "Start mode: ${e.mode.key} (${e.source.text})"

    /** Die Zeilen `Guide part A done:` und `Guide part B done:` im Protokollkopf (aus dem App-eigenen Zustand der Anleitung). [f] null = nicht ermittelbar: keine Zeilen. */
    fun guideLines(f: LabStartFacts?): List<String> =
        if (f == null) emptyList() else listOf("Guide part A done: ${yesNo(f.setUp)}", "Guide part B done: ${yesNo(f.partBDone)}")

    private fun yesNo(b: Boolean): String = if (b) "yes" else "no"

    /** Die Zeile `Started from:` im Protokoll. */
    fun originLine(origin: LabStartOrigin): String = "Started from: ${origin.text}"
}
