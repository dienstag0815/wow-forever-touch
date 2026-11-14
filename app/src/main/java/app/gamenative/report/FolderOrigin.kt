package app.gamenative.report

/**
 * Wie alt der Spielordner im Verhältnis zur Installation dieser App ist (reine Kotlin-Logik, JUnit-getestet). Ein Spielordner, der nur
 * älter ist als die App, ist nach einer Neuinstallation normal und kein Befund: der Bericht meldet dann eine neutrale Zeile mit den zwei
 * Daten und dem Unterschied, keine Zahl von "Spuren". Belegt ist eine frühere Installation dieser App nur, wenn ihre eigene Verlaufsdatei
 * der Windows-Kennung im Spielordner älter ist als die Installation.
 */
object FolderOrigin {
    private const val DAY_MS = 86_400_000L

    /** Ab wie vielen ganzen Tagen Unterschied der Spielordner als älter als die Installation gilt. */
    const val MIN_DAYS = 1L

    /**
     * Die Zeile für `kurz.txt` und `spuren.txt`. [folderSinceMs] ist das älteste Änderungsdatum im Spielordner, [installMs] die erste
     * Installation dieser App, [ownHistoryFirstMs] der älteste Eintrag der eigenen Verlaufsdatei im Spielordner (null, wenn es sie nicht gibt).
     */
    fun line(folderSinceMs: Long?, installMs: Long?, ownHistoryFirstMs: Long?): String {
        val head = "game folder since ${InstallTimeline.date(folderSinceMs)}, this install since ${InstallTimeline.date(installMs)}"
        if (folderSinceMs == null || installMs == null) return "$head, difference unknown"
        val days = (installMs - folderSinceMs) / DAY_MS
        val own = ownHistoryFirstMs?.takeIf { it < installMs }
        return when {
            own != null -> "$head, difference ${maxOf(days, 0)} days: earlier install of this app confirmed (its own Windows ID history in the game folder since ${InstallTimeline.date(own)})"
            days >= MIN_DAYS -> "$head, difference $days days: earlier install of this or another app"
            else -> "$head, no earlier install indicated"
        }
    }
}
