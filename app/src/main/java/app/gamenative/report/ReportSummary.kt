package app.gamenative.report

/** Die Angaben der Kurzfassung. Texte sind schon fertig formuliert; nichts davon darf E-Mail, Passwort, Adresse oder volle Kennung sein. */
data class SummaryInput(
    val device: String,
    val android: String,
    val chip: String,
    val appVersion: String,
    val clientBuild: String,
    val region: String,
    val loginLevel: String,
    val machineIdSet: String,
    val machineIdFingerprint: String,
    val machineIdChanges: String,
    val systemViewChanges: String,
    /** Kopfzeile von `anmeldung.txt`. */
    val attemptsHeader: String,
    /** Die letzten Versuche (höchstens 5, ältester zuerst). */
    val lastAttempts: List<String>,
    /** Die letzten Journal-Einträge (höchstens 3, ältester zuerst). */
    val lastJournal: List<String>,
    /** Die Antworten auf die Fragen von "Problem melden" (null beim Fehlerbericht ohne Fragen). */
    val answers: List<String>? = null,
    /** Übersicht über alle Journaleinträge (eine Zeile, [LoginHistory.overviewCompact]); leer = keine. */
    val history: String = "",
    /** Zeitleiste der Installation (zwei Zeilen, [InstallTimeline.compact]). */
    val timeline: List<String> = emptyList(),
    /** Andere Start-Apps und Spuren im Spielordner (eine Zeile, [OtherLaunchers.compact]); leer = keine. */
    val otherLaunchers: String = "",
)

/**
 * `kurz.txt`: die Kurzfassung für Zip, Zwischenablage und Meldung (reine Kotlin-Logik, JUnit-getestet). Höchstens [MAX_CHARS]
 * Zeichen; alles läuft durch den [ReportRedactor], danach werden stehengebliebene IPv4-Adressen ersetzt.
 */
object ReportSummary {
    const val MAX_CHARS = 2500
    const val ADDRESS = AddressRedactor.IPV4

    /** Gerät, Android und die Kennungs-/Systemsicht-Zeilen. */
    private fun head(i: SummaryInput): List<String> = listOf(
        "Device: ${i.device}",
        "Android: ${i.android}",
        "Chip: ${i.chip}",
        "App: ${i.appVersion}",
        "Client build: ${i.clientBuild}",
        "Region: ${i.region}",
        "Login level: ${i.loginLevel}",
        "Machine ID set: ${i.machineIdSet}; fingerprint ${i.machineIdFingerprint}; changes ${i.machineIdChanges}",
        "System view changes: ${i.systemViewChanges}",
    )

    fun build(input: SummaryInput, redactor: ReportRedactor): String {
        val lines = mutableListOf<String>()
        lines += head(input)
        input.answers?.let {
            lines += "Answers from the player:"
            lines += it
        }
        lines += "Login attempts: ${input.attemptsHeader}"
        if (input.history.isNotEmpty()) lines += "History over game starts: ${input.history}"
        if (input.lastAttempts.isNotEmpty()) {
            lines += "Last ${input.lastAttempts.size} attempts:"
            lines += input.lastAttempts.takeLast(5)
        }
        if (input.otherLaunchers.isNotEmpty()) lines += "Other apps: ${input.otherLaunchers}"
        if (input.timeline.isNotEmpty()) {
            lines += "Timeline:"
            lines += input.timeline
        }
        if (input.lastJournal.isNotEmpty()) {
            lines += "Last ${input.lastJournal.size} game starts:"
            lines += input.lastJournal.takeLast(3)
        }
        return fit(lines.joinToString("\n") { sanitize(it, redactor) }, MAX_CHARS)
    }

    /** Eine Zeile: Zeilenumbrüche entfallen, dann Schwärzen, dann Adressen. */
    internal fun sanitize(text: String, redactor: ReportRedactor): String =
        AddressRedactor.redact(redactor.redact(text.replace('\n', ' ').replace('\r', ' ')))

    /** Höchstens [max] Zeichen; zu langer Text wird am Ende gekürzt und endet mit `...`. */
    fun fit(text: String, max: Int): String = if (text.length <= max) text else text.take(max - 3).trimEnd() + "..."
}
