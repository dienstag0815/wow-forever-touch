package app.gamenative.lab

import app.gamenative.ui.screen.wow.LoginKeys
import java.util.Locale

/** Welcher gespeicherte Wert ein Anmelde-Knopf tippt. */
enum class LabSignInKind(val key: String) { EMAIL("email"), PASSWORD("password") }

/** Alles, wovon die Sichtbarkeit der Anmelde-Knöpfe abhängt. */
data class LabSignInContext(
    /** Der Schalter „Sign-in buttons“ in den Labor-Einstellungen. */
    val switchOn: Boolean,
    /** Läuft gerade ein Labor-Versuch? */
    val labRunActive: Boolean,
    /** Ziel des laufenden Versuchs; null ohne Versuch. */
    val target: LabTarget?,
    /** Sind im Spielbereich E-Mail und Passwort gespeichert? */
    val hasCredentials: Boolean,
)

enum class LabSignInState {
    /** Keine Knöpfe. */
    HIDDEN,

    /** Knöpfe da, aber ausgegraut: im Spielbereich ist nichts gespeichert (Hinweis, wo man es speichert). */
    NO_CREDENTIALS,

    /** Knöpfe bedienbar. */
    READY,
}

/** Die gespeicherte Anmeldung als Zeichenfelder (nie als String); [wipe] überschreibt beide. */
class LabSignInValues(val email: CharArray, val password: CharArray) {
    /** Das Feld für [kind]; das andere wird sofort überschrieben. Der Aufrufer überschreibt das gelieferte nach dem Senden. */
    fun take(kind: LabSignInKind): CharArray {
        val keep = if (kind == LabSignInKind.EMAIL) email else password
        (if (kind == LabSignInKind.EMAIL) password else email).fill('\u0000')
        return keep
    }

    fun wipe() {
        email.fill('\u0000')
        password.fill('\u0000')
    }
}

/** Ergebnis der Prüfung vor dem Senden: [unsupported] Zeichen ohne Taste, [empty] = nichts zu senden. */
data class LabSignInCheck(val unsupported: Int, val empty: Boolean) {
    val sendable: Boolean get() = !empty && unsupported == 0
}

/**
 * Anmelde-Knöpfe „E-Mail“ und „Passwort“ der Labor-Tastenleiste: rein (Freigabe, Prüfung vor dem Senden, Sperrzeit, Protokollzeilen),
 * ohne Android. Die Protokollzeilen nennen nie einen Wert und nie Teile davon, nur Art, Zahl der Zeichen und Zeit seit dem Start.
 */
object LabSignInKeys {
    /** Mindestens so lange nach einem Tipp (und nach dem Ende der Folge) sind beide Knöpfe gesperrt (Doppeltipp). */
    const val COOLDOWN_MS = 2_000L

    fun state(c: LabSignInContext): LabSignInState = when {
        !c.switchOn || !c.labRunActive || c.target != LabTarget.BATTLENET -> LabSignInState.HIDDEN
        !c.hasCredentials -> LabSignInState.NO_CREDENTIALS
        else -> LabSignInState.READY
    }

    /** Ob die Knöpfe in der Leiste stehen (bedienbar oder ausgegraut). */
    fun visible(c: LabSignInContext): Boolean = state(c) != LabSignInState.HIDDEN

    /** Ob ein Tipp jetzt eine Folge starten darf: freigegeben, keine Folge läuft, Sperrzeit vorbei. */
    fun mayTap(c: LabSignInContext, sequenceRunning: Boolean, lastActivityMs: Long?, nowMs: Long): Boolean =
        state(c) == LabSignInState.READY && !sequenceRunning && cooldownLeftMs(lastActivityMs, nowMs) == 0L

    /** Wie lange die Sperre nach der letzten Tätigkeit ([lastActivityMs]: Tipp oder Ende der Folge) noch gilt. */
    fun cooldownLeftMs(lastActivityMs: Long?, nowMs: Long): Long {
        if (lastActivityMs == null) return 0L
        val passed = nowMs - lastActivityMs
        if (passed < 0) return COOLDOWN_MS
        return (COOLDOWN_MS - passed).coerceAtLeast(0L)
    }

    /** Prüft den ganzen Wert, bevor etwas gesendet wird: nie nur ein Teil des Passworts. */
    internal fun check(value: CharArray): LabSignInCheck {
        var unsupported = 0
        for (ch in value) if (LoginKeys.strokeFor(ch) == null) unsupported++
        return LabSignInCheck(unsupported, value.isEmpty())
    }

    fun headerLine(on: Boolean): String = "Sign-in buttons: ${if (on) "on" else "off"}"

    fun sentLine(kind: LabSignInKind, sent: Int, elapsedMs: Long): String =
        "-- Sign-in key: ${kind.key} sent $sent chars after ${seconds(elapsedMs)} s"

    fun notSentLine(kind: LabSignInKind, check: LabSignInCheck): String =
        if (check.empty) "-- Sign-in key: ${kind.key} not sent (empty)"
        else "-- Sign-in key: ${kind.key} not sent (unsupported chars: ${check.unsupported})"

    /** Abgebrochene Folge (Leiste geschlossen): nur die Zahl der schon gesendeten Zeichen. */
    fun cancelledLine(kind: LabSignInKind, sent: Int, elapsedMs: Long): String =
        "-- Sign-in key: ${kind.key} cancelled after $sent chars at ${seconds(elapsedMs)} s"

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.1f", ms.coerceAtLeast(0L) / 1000.0)
}
