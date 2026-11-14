package app.gamenative.report

import java.net.URLEncoder

/**
 * Die Adresse der neuen Meldung im öffentlichen Repo der App (reine Kotlin-Logik, JUnit-getestet). Sie füllt die Felder der
 * Meldevorlage `.github/ISSUE_TEMPLATE/bug_report.yml` (Feld-IDs [FIELD_REPORT] usw.) und den Titel. Nichts wird gesendet: die Adresse
 * wird nur im Browser geöffnet, abgeschickt wird die Meldung vom Spieler.
 */
object ReportIssueLink {
    const val MAX_URL_CHARS = 6000
    const val TEMPLATE = "bug_report.yml"
    const val FIELD_DEVICE = "device"
    const val FIELD_ANDROID = "android"
    const val FIELD_VERSION = "app_version"
    const val FIELD_REPORT = "report"

    private val GITHUB_RELEASE_FEED = Regex("""^https://github\.com/([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+)/releases/""")
    private val GITHUB_REPO = Regex("""^https://github\.com/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$""")

    /**
     * Das Repo der Meldungen: das Repo aus der Adresse der Update-Quelle der App ([feedUrl], wenn sie auf GitHub-Releases zeigt),
     * sonst [fallback] (das Repo der Build-Einstellungen, aus dem auch die Update-Quelle gebaut wird). Null, wenn keins brauchbar ist.
     */
    fun repoUrl(feedUrl: String?, fallback: String?): String? {
        feedUrl?.trim()?.let { feed -> GITHUB_RELEASE_FEED.find(feed)?.let { return "https://github.com/${it.groupValues[1]}/${it.groupValues[2]}" } }
        return fallback?.trim()?.trimEnd('/')?.takeIf { GITHUB_REPO.matches(it) }
    }

    /** Der Titel der Meldung: immer englisch, weil die Meldeseite öffentlich und englisch ist (nicht die Gerätesprache nehmen). */
    fun issueTitle(version: String): String = "Problem with WoW Forever $version"

    /** Prozent-Kodierung für Abfrageteile: Leerzeichen als %20, UTF-8. */
    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    /** Die Zeile am Ende des Textes: die zwei Handgriffe (Zip aus Downloads anhängen, Kurzfassung aus der Zwischenablage einfügen) und der Ausweg ohne Anhang. */
    fun attachLine(zipName: String): String =
        "Please attach the file $zipName from your Downloads folder. The full short summary is in your clipboard: paste it if the text above is empty or cut off. " +
            "If your app cannot attach a file, open this report in a browser."

    /**
     * `<repo>/issues/new?template=...&title=...&device=...&report=...`. [summary] kommt ins Feld [FIELD_REPORT], gefolgt von der Zeile
     * [attachLine]; ist die Adresse länger als [maxChars], wird die Kurzfassung (nicht die Anhängzeile) am Ende gekürzt. Die Zwischenablage
     * bekommt die volle Kurzfassung (das ist nicht Sache dieser Funktion).
     */
    fun build(
        repoUrl: String,
        title: String,
        summary: String,
        zipName: String,
        device: String,
        android: String,
        appVersion: String,
        maxChars: Int = MAX_URL_CHARS,
    ): String {
        val attach = attachLine(zipName)
        fun url(shortSummary: String): String {
            val report = if (shortSummary.isEmpty()) attach else shortSummary + "\n\n" + attach
            return repoUrl.trimEnd('/') + "/issues/new?template=" + TEMPLATE +
                "&title=" + encode(title) +
                "&$FIELD_DEVICE=" + encode(device) +
                "&$FIELD_ANDROID=" + encode(android) +
                "&$FIELD_VERSION=" + encode(appVersion) +
                "&$FIELD_REPORT=" + encode(report)
        }
        val full = url(summary)
        if (full.length <= maxChars) return full
        // Kürzen: größte Länge der Kurzfassung finden, bei der die Adresse noch passt (Kodierung wächst nicht gleichmäßig).
        var low = 0
        var high = summary.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (url(shorten(summary, mid)).length <= maxChars) low = mid else high = mid - 1
        }
        return url(shorten(summary, low))
    }

    private fun shorten(summary: String, keep: Int): String {
        if (keep >= summary.length) return summary
        if (keep <= 0) return ""
        var cut = summary.take(keep)
        if (cut.last().isHighSurrogate()) cut = cut.dropLast(1)
        return cut.trimEnd() + "..."
    }
}
