package app.gamenative.report

/** Hat die Anmeldung mit dieser App auf diesem Gerät schon einmal geklappt? */
enum class WorkedBefore(val text: String) { YES("yes"), NO("no"), UNKNOWN("not sure") }

/** Eine andere App für dieses Spiel auf diesem Gerät vorher benutzt? */
enum class OtherAppBefore(val text: String) {
    NO("no"),
    YES_WORKED("yes, the login worked there"),
    YES_FAILED("yes, the login did not work there"),
}

/** Wo hat der Spieler sich kurz vorher angemeldet? */
enum class LoggedInElsewhere(val text: String) {
    PC_APP("on a PC in the Battle.net app"),
    BROWSER("in a browser"),
    NONE("nowhere else"),
}

/**
 * Die vier freiwilligen Fragen von "Problem melden" (reine Kotlin-Logik, JUnit-getestet). Null oder leer = nicht beantwortet. Die Antworten
 * kommen in `kurz.txt` und damit in den Text der Meldung; sie sind Auswahlwerte, kein freier Text.
 */
data class ProblemAnswers(
    val workedBefore: WorkedBefore? = null,
    val otherApp: OtherAppBefore? = null,
    val authenticator: Boolean? = null,
    val elsewhere: Set<LoggedInElsewhere> = emptySet(),
) {
    /** "Nirgends sonst" schließt die anderen Antworten aus: stehen beide da, gelten die konkreten. */
    fun normalized(): ProblemAnswers =
        if (LoggedInElsewhere.NONE in elsewhere && elsewhere.size > 1) copy(elsewhere = elsewhere - LoggedInElsewhere.NONE) else this

    fun lines(): List<String> {
        val a = normalized()
        val none = "not answered"
        return listOf(
            "Login with this app on this device worked before: ${a.workedBefore?.text ?: none}",
            "Another app for this game used on this device before: ${a.otherApp?.text ?: none}",
            "Account has an authenticator: ${a.authenticator?.let { if (it) "yes" else "no" } ?: none}",
            "Logged in elsewhere shortly before: " +
                if (a.elsewhere.isEmpty()) none else LoggedInElsewhere.values().filter { it in a.elsewhere }.joinToString(", ") { it.text },
        )
    }
}
