package app.gamenative.report

import java.text.SimpleDateFormat
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Ein Anmeldeversuch eines Spielstarts, wie er im Journal steht. Nur diese Felder; nie Konto, Adresse, Token oder Servername.
 */
data class AttemptNote(
    /** `ok`, `open` (kein Ergebnis im Log) oder `e<Code>` (z. B. `e14400`). */
    val result: String,
    val usedToken: Boolean?,
    val authenticatorRequired: Boolean?,
    /** Sekunden seit dem Beginn des vorigen Versuchs im selben Lauf; beim ersten 0. */
    val gapSec: Long,
    /** Fehlerschritt oder letzter erreichter Schritt ([LoginStep]); null = nicht erfasst (ältere Journalzeilen). */
    val step: String? = null,
    val stepFailed: Boolean? = null,
    /** Plattform des Clients (vier Zeichen), `launcherPortal` gesetzt/nicht gesetzt und `usedSSO`. */
    val platform: String? = null,
    val launcherPortalSet: Boolean? = null,
    val usedSso: Boolean? = null,
) {
    val isOk: Boolean get() = result == OK
    val isError: Boolean get() = result.startsWith("e")

    companion object {
        const val OK = "ok"
        const val OPEN = "open"
        private val VALID = Regex("""ok|open|e\d{1,6}""")
        private val PLATFORM = Regex("""[A-Za-z0-9]{4}""")
        fun isValidPlatform(value: String?) = value != null && PLATFORM.matches(value)
        fun error(code: Int) = "e$code"
        fun isValid(result: String) = VALID.matches(result)
    }
}

/**
 * Die Anmeldeversuche eines Spielstarts: [total] alle Versuche des Laufs, [firstOk] die laufende Nummer (ab 1) des ersten Erfolgs,
 * [notes] die letzten höchstens [LoginHistory.MAX_NOTES] Versuche (ältester zuerst). Leer = die `Connection.log` des Laufs enthielt keinen Versuch.
 */
data class AttemptSummary(val total: Int, val firstOk: Int?, val notes: List<AttemptNote>)

/** Wie die Anmeldung über die Spielstarts hinweg aussieht (abgeleitet aus dem Journal). */
enum class Verdict(val text: String) {
    NEVER("never worked (no start with a successful login in the journal)"),
    WORKED_BEFORE("worked before, failing now"),
    FAILED_BEFORE("failed before, works now"),
    WORKS("works"),
    NO_DATA("not enough data (no start with login attempts in the journal)"),
}

/** Zahlen über alle Journaleinträge. Die Summe aus den vier Erfolgs-/Fehlerzahlen und [noAttempts] ist [withData]. */
data class HistoryOverview(
    val starts: Int,
    val withData: Int,
    val noAttempts: Int,
    val okFirst: Int,
    val okSecond: Int,
    val okLater: Int,
    val noSuccess: Int,
    val firstSuccessMs: Long?,
    val lastSuccessMs: Long?,
    val firstRejectedMs: Long?,
    val verdict: Verdict,
)

/**
 * Anmeldeverlauf über Spielstarts hinweg (reine Kotlin-Logik, JUnit-getestet). Die `Connection.log` zeigt nur den jüngsten Lauf (oder
 * wächst an, siehe [capture]); darum wird die Zusammenfassung am Ende eines Laufs und spätestens vor dem nächsten Start ins Journal
 * übernommen und hier über alle Einträge ausgewertet.
 */
object LoginHistory {
    const val MAX_NOTES = 20

    /** So viel früher als der Start der App darf der Beginn eines Laufs liegen (das Log nennt nur ganze Sekunden; das Spiel startet nach der App). */
    const val RUN_TOLERANCE_MS = 2_000L

    private const val HALF_YEAR_MS = 183L * 24 * 3600 * 1000
    private val STAMP = Regex("""^(\d{2})/(\d{2}) (\d{2}):(\d{2}):(\d{2})$""")

    /** `MM/dd HH:mm:ss` (Ortszeit, ohne Jahr) als Millisekunden; das Jahr ist das, das [referenceMs] am nächsten liegt. Null bei ungültiger Zeit. */
    fun toMillis(stamp: String, referenceMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val m = STAMP.matchEntire(stamp) ?: return null
        val (month, day, hour, minute, second) = m.destructured
        val refYear = java.time.Instant.ofEpochMilli(referenceMs).atZone(zone).year
        fun at(year: Int): Long? = try {
            LocalDateTime.of(year, month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt()).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
        val candidate = at(refYear) ?: return at(refYear - 1) ?: at(refYear + 1)
        return when {
            candidate - referenceMs > HALF_YEAR_MS -> at(refYear - 1) ?: candidate
            referenceMs - candidate > HALF_YEAR_MS -> at(refYear + 1) ?: candidate
            else -> candidate
        }
    }

    /** Eine Zusammenfassung aus den Versuchen eines Laufs; [referenceMs] bestimmt das Jahr der Zeitangaben. */
    fun summarize(attempts: List<LoginAttempt>, referenceMs: Long): AttemptSummary {
        val times = attempts.map { toMillis(it.time, referenceMs) }
        val notes = attempts.mapIndexed { i, a ->
            val gap = if (i == 0) 0L else {
                val now = times[i]
                val before = times[i - 1]
                if (now != null && before != null) ((now - before) / 1000).coerceAtLeast(0) else 0L
            }
            AttemptNote(
                result = when (val r = a.result) {
                    AttemptResult.Success -> AttemptNote.OK
                    is AttemptResult.Error -> AttemptNote.error(r.code)
                    AttemptResult.Open -> AttemptNote.OPEN
                },
                usedToken = a.usedToken,
                authenticatorRequired = a.authenticatorRequired,
                gapSec = gap,
                step = a.step,
                stepFailed = a.stepFailed,
                platform = a.platform,
                launcherPortalSet = a.launcherPortalSet,
                usedSso = a.usedSso,
            )
        }
        val firstOk = notes.indexOfFirst { it.isOk }.takeIf { it >= 0 }?.plus(1)
        return AttemptSummary(total = notes.size, firstOk = firstOk, notes = notes.takeLast(MAX_NOTES))
    }

    /**
     * Die Versuche des Laufs, der zum Spielstart um [startMs] gehört, aus dem Text der `Connection.log`. Gilt der jüngste Lauf der Datei
     * (die Datei zeigt je Start den neuen Lauf; wüchse sie an, ist es trotzdem der letzte) und begann er nicht deutlich vor dem Start der
     * App, ist es dieser. Sonst (kein Lauf in der Datei, der Lauf ist älter als der Start: das Spiel hat nichts geschrieben) null.
     */
    fun capture(startMs: Long, logText: String?): AttemptSummary? {
        if (logText.isNullOrEmpty()) return null
        val runs = LoginAttempts.runStarts(logText)
        val lastRun = runs.lastOrNull() ?: return null
        val runMs = toMillis(lastRun, startMs) ?: return null
        if (runMs < startMs - RUN_TOLERANCE_MS) return null
        return summarize(LoginAttempts.parse(logText).filter { it.run == runs.size }, startMs)
    }

    /** Der Eintrag mit den Versuchen aus [logText], wenn die Datei zu diesem Start gehört; sonst unverändert. */
    fun withCapture(entry: JournalEntry, logText: String?): JournalEntry =
        capture(entry.startMs, logText)?.let { entry.copy(attempts = it) } ?: entry

    /** Wie [withCapture], aber nur für den jüngsten Eintrag (für den Bericht, ohne das Journal zu ändern). */
    fun withLatestCapture(entries: List<JournalEntry>, logText: String?): List<JournalEntry> =
        if (entries.isEmpty()) entries else entries.dropLast(1) + withCapture(entries.last(), logText)

    // --- Übersicht ---

    fun overview(entries: List<JournalEntry>): HistoryOverview {
        val data = entries.filter { it.attempts != null }
        val empty = data.count { it.attempts!!.total == 0 }
        val okFirst = data.count { it.attempts!!.firstOk == 1 }
        val okSecond = data.count { it.attempts!!.firstOk == 2 }
        val okLater = data.count { (it.attempts!!.firstOk ?: 0) >= 3 }
        val noSuccess = data.count { it.attempts!!.total > 0 && it.attempts.firstOk == null }
        val successes = entries.filter { it.attempts?.firstOk != null }
        val rejected = entries.firstOrNull { e -> e.attempts?.notes?.any { it.isError } == true }
        return HistoryOverview(
            starts = entries.size,
            withData = data.size,
            noAttempts = empty,
            okFirst = okFirst,
            okSecond = okSecond,
            okLater = okLater,
            noSuccess = noSuccess,
            firstSuccessMs = successes.firstOrNull()?.startMs,
            lastSuccessMs = successes.lastOrNull()?.startMs,
            firstRejectedMs = rejected?.startMs,
            verdict = verdict(entries),
        )
    }

    /** "Ging früher, jetzt nicht" und Verwandte: aus den Starts mit mindestens einem Versuch im Log. */
    fun verdict(entries: List<JournalEntry>): Verdict {
        val conclusive = entries.filter { (it.attempts?.total ?: 0) > 0 }
        if (conclusive.isEmpty()) return Verdict.NO_DATA
        fun ok(e: JournalEntry) = e.attempts?.firstOk != null
        val last = conclusive.last()
        if (conclusive.none { ok(it) }) return Verdict.NEVER
        if (!ok(last)) return Verdict.WORKED_BEFORE
        val lastSuccessIndex = conclusive.indexOfLast { ok(it) }
        return if (conclusive.take(lastSuccessIndex).any { !ok(it) }) Verdict.FAILED_BEFORE else Verdict.WORKS
    }

    private fun date(ms: Long?): String =
        ms?.let { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }.format(Date(it)) } ?: "-"

    /** Die Übersicht als Zeilen (für `anmeldejournal.txt`). */
    fun overviewLines(o: HistoryOverview): List<String> = listOf(
        "overview of ${o.starts} game starts in the journal: login data for ${o.withData}, no login attempt in the log for ${o.noAttempts}, no data for ${o.starts - o.withData}",
        "login ok on attempt 1: ${o.okFirst}, attempt 2: ${o.okSecond}, attempt 3 or later: ${o.okLater}; starts with attempts but no success: ${o.noSuccess}",
        "first success ${date(o.firstSuccessMs)}, last success ${date(o.lastSuccessMs)}, first rejected attempt ${date(o.firstRejectedMs)}",
        "verdict: ${o.verdict.text}",
    )

    /** Dieselbe Übersicht in einer Zeile (für `kurz.txt`). */
    fun overviewCompact(o: HistoryOverview): String =
        "starts ${o.starts} (data ${o.withData}); ok at 1st/2nd/3rd+ attempt ${o.okFirst}/${o.okSecond}/${o.okLater}; never ok ${o.noSuccess}; no attempt ${o.noAttempts}; " +
            "first ok ${date(o.firstSuccessMs)}, last ok ${date(o.lastSuccessMs)}, first rejected ${date(o.firstRejectedMs)}; ${o.verdict.text}"

    // --- Text eines Eintrags ---

    private fun flag(b: Boolean?) = when (b) { true -> "true"; false -> "false"; null -> "?" }

    private fun noteText(n: AttemptNote, number: Int): String {
        val result = when {
            n.isOk -> "ok"
            n.isError -> "error ${n.result.drop(1)}"
            else -> "no result"
        }
        val gap = if (n.gapSec > 0) " +${n.gapSec}s" else ""
        val extra = if (n.step == null && n.platform == null) "" else
            ", step ${n.step ?: "?"}${if (n.step != null && n.step != LoginStep.UNKNOWN) (if (n.stepFailed == true) " failed" else " reached") else ""}, platform ${n.platform ?: "?"}, " +
                "launcherPortal ${LoginAttempts.portalText(n.launcherPortalSet)}, usedSSO ${flag(n.usedSso)}"
        return "#$number$gap $result (usedToken ${flag(n.usedToken)}, authenticatorRequired ${flag(n.authenticatorRequired)}$extra)"
    }

    /** Alle Versuche eines Starts in einer Zeile; `not captured` ohne Daten. */
    fun attemptsText(a: AttemptSummary?): String = when {
        a == null -> "not captured"
        a.total == 0 -> "none in the log"
        else -> {
            val firstNumber = a.total - a.notes.size + 1
            "${a.total}: " + a.notes.mapIndexed { i, n -> noteText(n, firstNumber + i) }.joinToString("; ") +
                "; first ok " + (a.firstOk?.let { "#$it" } ?: "none")
        }
    }

    /** Kurzform für `kurz.txt`: Zahl und erster Erfolg. */
    fun attemptsShort(a: AttemptSummary?): String = when {
        a == null -> "?"
        a.total == 0 -> "0"
        else -> "${a.total}, first ok " + (a.firstOk?.let { "#$it" } ?: "none") + lastNoteShort(a.notes.lastOrNull())
    }

    /** `, last: step realm_auth, WinA, portal none, sso false` aus dem letzten Versuch; leer ohne Angaben. */
    private fun lastNoteShort(n: AttemptNote?): String {
        if (n == null || (n.step == null && n.platform == null)) return ""
        return ", last: step ${n.step ?: "?"}${if (n.stepFailed == true) " failed" else ""}, ${n.platform ?: "?"}, portal ${LoginAttempts.portalText(n.launcherPortalSet)}, sso ${flag(n.usedSso)}"
    }
}
