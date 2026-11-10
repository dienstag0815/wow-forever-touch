package app.gamenative

import app.gamenative.report.ReportRedactor

/**
 * Der Text der Absturzanzeige (reine Kotlin-Funktionen, ohne Android, per JUnit getestet). Die Anzeige läuft in einem eigenen
 * Prozess (`:crash`) und darf nichts vom Zustand des Hauptprozesses brauchen; deshalb kommt der fertige Text als Intent-Extra.
 */
internal object CrashReport {
    /** Obergrenze des Berichts in Zeichen (für ASCII = Bytes); ein Intent-Extra verträgt rund 1 MB. */
    const val MAX_CHARS = 60_000

    /** Mindestabstand zwischen zwei Anzeigen: eine Absturzschleife zeigt nicht endlos Fenster. */
    const val MIN_GAP_MS = 5_000L

    /** Name des eigenen Prozesses der Anzeige (Suffix nach dem Paketnamen, wie im Manifest). */
    const val PROCESS_SUFFIX = ":crash"

    const val EXTRA_REPORT = "app.gamenative.crash.REPORT"
    const val EXTRA_LANGUAGE = "app.gamenative.crash.LANGUAGE"

    private const val CUT_MARK = "\n... [gekürzt / truncated] ...\n"

    /** True im Prozess der Anzeige selbst; dort wird kein Handler eingebaut und nichts gestartet. */
    fun isCrashProcess(processName: String?): Boolean = processName?.endsWith(PROCESS_SUFFIX) == true

    /** Ob ein neuer Absturz angezeigt wird: nicht, wenn die letzte Anzeige weniger als [MIN_GAP_MS] zurückliegt. */
    fun shouldShow(lastShownMs: Long, nowMs: Long): Boolean = lastShownMs <= 0L || nowMs < lastShownMs || nowMs - lastShownMs >= MIN_GAP_MS

    /**
     * Bericht für die Anzeige: Kopf (App, Android, Gerät) und Stacktrace. Anmeldedaten, E-Mail-Adressen und Tickets sind
     * geschwärzt ([ReportRedactor]); der Stacktrace selbst bleibt lesbar. Auf [maxChars] gekürzt.
     */
    fun build(
        appVersion: String,
        androidVersion: String,
        device: String,
        stackTrace: String,
        maxChars: Int = MAX_CHARS,
    ): String {
        val header = buildString {
            appendLine("App: $appVersion")
            appendLine("Android: $androidVersion")
            appendLine("Device: $device")
            appendLine()
        }
        val safeTrace = try {
            ReportRedactor().redact(stackTrace)
        } catch (e: Exception) {
            stackTrace
        }
        return truncate(header + safeTrace, maxChars)
    }

    /**
     * Kürzt [text] auf höchstens [maxChars] Zeichen und behält Anfang (Ausnahme und erste Stellen) und Ende (`Caused by`),
     * die Mitte fällt weg. Kürzere Texte bleiben unverändert.
     */
    fun truncate(text: String, maxChars: Int = MAX_CHARS): String {
        if (maxChars <= 0) return ""
        if (text.length <= maxChars) return text
        if (maxChars <= CUT_MARK.length + 2) return text.take(maxChars)
        val budget = maxChars - CUT_MARK.length
        val head = budget * 2 / 5
        val tail = budget - head
        return text.substring(0, head) + CUT_MARK + text.substring(text.length - tail)
    }
}
