package app.gamenative.ui.components

import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.lab.LabIds

/**
 * Reine Logik des Startbildschirms (ohne Android): Zuordnung der Splash-Texte zu den Stufen, Stufenreihenfolge,
 * deutsche Detailzeile, Hinweise für Touch und (nur mit Gamepad) Gamepad.
 *
 * Quellen der Splash-Texte (Stand fold8.1):
 *  - PluviaMain: Starttext vor dem ersten Ereignis ("WoW wird gestartet …")
 *  - MainState / BootingSplash: Vorgabetexte ("Umgebung wird vorbereitet …", "WoW wird gestartet …"; früher "Booting...", "Initializing...")
 *  - BasePrefix.java: "Preparing <wine> base..." (Wine-Grundlage wird erstmalig oder nach Update aufgebaut)
 *  - ContainerOverlayMigrator.java: "Migrating Wine prefix..."
 *  - XServerScreen.kt: "Wine wird eingerichtet …", "Grafiktreiber werden geladen …" (neu) und "Launching game..."
 */
enum class BootStage(@StringRes val labelRes: Int) {
    Environment(R.string.game_boot_stage_env),
    Wine(R.string.game_boot_stage_wine),
    Graphics(R.string.game_boot_stage_graphics),
    Launch(R.string.game_boot_stage_launch),
    Login(R.string.game_boot_stage_login),
}

/** Ergebnis für einen Splash-Text: Stufe (null = unbekannt, Stufe bleibt) und Detailzeile (noch nicht übersetzt). */
data class BootInfo(val stage: BootStage?, val detail: TextRef)

object BootingSplashLogic {
    /**
     * Kennungen der Texte, die die App selbst setzt. Sie sind sprachneutral; [classify] macht daraus Stufe und Detailzeile in der
     * Sprache der Anzeige. (Früher standen hier die deutschen Texte selbst, das Zuordnen hing am Wortlaut.)
     */
    const val TEXT_START = "@boot:start"
    const val TEXT_ENV = "@boot:env"
    const val TEXT_WINE = "@boot:wine"
    const val TEXT_GRAPHICS = "@boot:graphics"

    private val preparingBase = Regex("""^\s*Preparing\s+(.+?)\s+base\.*\s*$""", RegexOption.IGNORE_CASE)

    /** Text/Ereignis -> Stufe + Detailzeile. Unbekannte Texte: Stufe null, der Rohtext als Detail. Wirft nie. */
    fun classify(text: String?): BootInfo {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return BootInfo(null, TextRef.plain(""))
        val t = raw.lowercase()
        preparingBase.matchEntire(raw)?.let {
            return BootInfo(BootStage.Environment, TextRef(R.string.game_boot_base, it.groupValues[1]))
        }
        return when {
            raw == TEXT_START -> BootInfo(BootStage.Environment, TextRef(R.string.game_boot_start))
            raw == TEXT_ENV -> BootInfo(BootStage.Environment, TextRef(R.string.game_boot_env))
            raw == TEXT_WINE -> BootInfo(BootStage.Wine, TextRef(R.string.game_boot_wine))
            raw == TEXT_GRAPHICS -> BootInfo(BootStage.Graphics, TextRef(R.string.game_boot_graphics))
            t.startsWith("migrating wine prefix") -> BootInfo(BootStage.Environment, TextRef(R.string.game_boot_prefix))
            t.startsWith("booting") -> BootInfo(BootStage.Environment, TextRef(R.string.game_boot_env))
            t.startsWith("initializing") -> BootInfo(BootStage.Environment, TextRef(R.string.game_boot_env))
            t.startsWith("launching game") -> BootInfo(BootStage.Launch, TextRef(R.string.game_boot_launch))
            else -> BootInfo(null, TextRef.plain(raw))
        }
    }

    /** Die erreichte Stufe springt nie zurück: neue Stufe zählt nur, wenn sie weiter vorn liegt. */
    fun advance(current: BootStage?, next: BootStage?): BootStage? = when {
        next == null -> current
        current == null -> next
        next.ordinal > current.ordinal -> next
        else -> current
    }

    /** Anzeigezustand einer Stufe bei erreichter Stufe [reached]. */
    enum class StepState { Done, Active, Pending }

    fun stepState(step: BootStage, reached: BootStage?): StepState {
        val r = reached?.ordinal ?: BootStage.Environment.ordinal
        return when {
            step.ordinal < r -> StepState.Done
            step.ordinal == r -> StepState.Active
            else -> StepState.Pending
        }
    }

    /** Fester Hinweis unter der Stufenliste: Gamepad-Kombination nur mit verbundenem Gamepad. */
    fun backHint(gamepadConnected: Boolean): TextRef =
        TextRef(if (gamepadConnected) R.string.game_boot_back_pad else R.string.game_boot_back)

    /** Wechselnde Kurz-Infos (nur Funktionen, die es in der App gibt). */
    fun tips(gamepadConnected: Boolean): List<TextRef> = buildList {
        add(TextRef(R.string.game_boot_tip_menu))
        add(TextRef(R.string.game_boot_tip_login))
        add(TextRef(R.string.game_boot_tip_reload))
        add(TextRef(R.string.game_boot_tip_first))
        if (gamepadConnected) add(TextRef(R.string.game_boot_tip_pad))
    }
}

/** Titelzeilen der Ladeanzeige; [sub] null = keine zweite Zeile. */
data class SplashTitle(val main: String, val sub: String?)

/**
 * Ladeanzeige je Container (reine Logik). Das Spiel zeigt alle Stufen und blendet erst aus, wenn sein Fenster erscheint (MainViewModel).
 * Der Container des Battle.net-Labors (`bnetlab`) zeigt nur die Stufen bis zum Grafiktreiber und dann "Programm wird gestartet"; mit
 * dem Schritt, in dem der Startbefehl abgesetzt wird, verschwindet die Anzeige, damit das Windows-Fenster sichtbar ist.
 */
object BootingSplashContainer {
    fun isLab(containerId: String?): Boolean = containerId == LabIds.CONTAINER_ID

    /** Die angezeigten Stufen: Spiel alle, Labor ohne "Anmeldung" (die letzte Stufe heißt dort "Programm wird gestartet"). */
    fun steps(containerId: String?): List<BootStage> =
        if (isLab(containerId)) listOf(BootStage.Environment, BootStage.Wine, BootStage.Graphics, BootStage.Launch) else BootStage.values().toList()

    @StringRes
    fun stepLabel(step: BootStage, containerId: String?): Int =
        if (isLab(containerId) && step == BootStage.Launch) R.string.game_boot_stage_program else step.labelRes

    fun title(containerId: String?): SplashTitle =
        if (isLab(containerId)) SplashTitle("Battle.net Lab", null) else SplashTitle("WORLD OF WARCRAFT", "Forever · Touch")

    /** Detailzeile: im Labor statt der Spieltexte "Programm wird gestartet …". */
    fun detail(containerId: String?, info: BootInfo): TextRef =
        if (isLab(containerId) && (info.detail.res == R.string.game_boot_start || info.detail.res == R.string.game_boot_launch)) {
            TextRef(R.string.game_boot_program)
        } else {
            info.detail
        }

    /**
     * Anzeige sichtbar? [showRequested] = Zustand des Haupt-Bildschirms. Im Labor verschwindet sie, sobald der Text der Stufe
     * "Launch" (Startbefehl wird abgesetzt) gesetzt ist; für das Spiel gilt allein [showRequested].
     */
    fun isVisible(containerId: String?, showRequested: Boolean, text: String?): Boolean =
        showRequested && !(isLab(containerId) && BootingSplashLogic.classify(text).stage == BootStage.Launch)
}
