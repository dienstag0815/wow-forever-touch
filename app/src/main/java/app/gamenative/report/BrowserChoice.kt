package app.gamenative.report

/** Das Ziel von "Im Browser öffnen": ein bestimmter Browser (Paketname) oder keiner (dann gilt der Standardweg). */
sealed interface BrowserTarget {
    data class Browser(val packageName: String) : BrowserTarget
    data object None : BrowserTarget
}

/**
 * Welcher Browser den Link öffnet (reine Kotlin-Logik, JUnit-getestet). Die GitHub-App und ähnliche Apps fangen github.com-Links ab;
 * "Im Browser öffnen" richtet den Link darum gezielt an einen Browser. Die Kandidaten sind die Apps, die einen allgemeinen
 * https-Link öffnen (Browser, nicht Apps für eine bestimmte Seite), der Standard-Handler ist die App, die Android für einen solchen
 * Link wählt (Standard-Browser, oder eine Auswahl-Aktivität des Systems, wenn keiner festgelegt ist).
 */
object BrowserChoice {
    /** Apps, die nie als Browser gelten, auch wenn sie als Kandidat auftauchen. */
    private val NEVER = setOf("com.github.android")

    /**
     * 1. Ist der Standard-Handler einer der Kandidaten, gilt er (der Standard-Browser des Spielers).
     * 2. Sonst (Standard ist eine andere App oder die Auswahl des Systems) der erste Kandidat.
     * 3. Gibt es keinen Kandidaten: [BrowserTarget.None].
     * [ownPackage] und die [NEVER]-Liste scheiden aus.
     */
    fun pick(candidates: List<String>, defaultHandler: String?, ownPackage: String): BrowserTarget {
        val usable = candidates.map { it.trim() }.filter { it.isNotEmpty() && it != ownPackage && it !in NEVER }.distinct()
        val chosen = usable.firstOrNull { it == defaultHandler } ?: usable.firstOrNull()
        return if (chosen == null) BrowserTarget.None else BrowserTarget.Browser(chosen)
    }
}
