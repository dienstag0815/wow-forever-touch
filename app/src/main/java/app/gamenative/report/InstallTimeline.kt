package app.gamenative.report

import app.gamenative.utils.MachineIdentityLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Wie alt ein Ordner ist: das älteste Änderungsdatum der geprüften Einträge; [complete] false, wenn die Prüfung vorzeitig endete. */
data class FolderAge(val oldestMs: Long?, val checked: Int, val complete: Boolean)

/** Die Angaben der Zeitleiste. Zeiten in Millisekunden seit 1970 (null = unbekannt); keine Kennungswerte, nur Daten und Ereignisnamen. */
data class TimelineInput(
    val firstInstallMs: Long?,
    val lastUpdateMs: Long?,
    /** Paketname der Installationsquelle (z. B. eines Stores oder des Paketinstallers); null, wenn Android keine meldet. */
    val installer: String?,
    val gameFolder: FolderAge?,
    val wtfFolder: FolderAge?,
    /** Verlauf der Windows-Kennung (nur Zeit, Kurzkennung, Ereignis). */
    val identityTrace: List<MachineIdentityLog.Entry>,
    /** Beginn der aktuellen Reihe gleicher Kurzkennungen (Sekunden seit 1970). */
    val identitySinceSec: Long?,
    val journalVersions: Int,
    val journalFirstVersion: String?,
    val journalLastVersion: String?,
)

/**
 * Zeitleiste der Installation für `geraet.txt` und `kurz.txt` (reine Kotlin-Logik, JUnit-getestet): erste Installation und letztes Update
 * der App, Installationsquelle, Alter der Ordner, Datum der Windows-Kennung und ob sie aus dem Spielordner übernommen wurde.
 * Daten als UTC-Datum (wie der Kennungsverlauf).
 */
object InstallTimeline {
    private const val MAX_ENTRIES = 3000
    private const val MAX_DEPTH = 4
    private val PACKAGE_NAME = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")

    fun date(ms: Long?): String =
        ms?.let { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(it)) } ?: "unknown"

    /** Nur ein gültiger Paketname wird übernommen (nichts anderes kann in den Bericht gelangen). */
    fun installerName(raw: String?): String? = raw?.trim()?.takeIf { it.length <= 100 && PACKAGE_NAME.matches(it) }

    /**
     * Das älteste Änderungsdatum unter [dir] (Dateien und Ordner), breit vor tief, höchstens [maxEntries] Einträge und [maxDepth] Ebenen
     * tief. Ein Näherungswert nach unten: bei [FolderAge.complete] false können noch ältere Einträge unbesehen sein.
     */
    fun scan(dir: File, maxEntries: Int = MAX_ENTRIES, maxDepth: Int = MAX_DEPTH): FolderAge? {
        if (!dir.isDirectory) return null
        var oldest = dir.lastModified().takeIf { it > 0 }
        var checked = 0
        var complete = true
        var level = listOf(dir)
        var depth = 0
        while (level.isNotEmpty()) {
            if (depth >= maxDepth) { complete = false; break }
            val next = mutableListOf<File>()
            for (d in level) {
                val children = runCatching { d.listFiles() }.getOrNull() ?: continue
                for (c in children) {
                    if (checked >= maxEntries) return FolderAge(oldest, checked, false)
                    checked++
                    val t = c.lastModified()
                    if (t > 0 && (oldest == null || t < oldest)) oldest = t
                    if (c.isDirectory) next += c
                }
            }
            level = next
            depth++
        }
        return FolderAge(oldest, checked, complete)
    }

    private fun age(a: FolderAge?): String = when {
        a == null -> "not present"
        a.oldestMs == null -> "unknown (${a.checked} entries)"
        else -> "oldest change ${date(a.oldestMs)} (${a.checked} entries checked${if (a.complete) "" else ", not complete: older entries may exist"})"
    }

    /** Wann die Kennung zuerst und zuletzt gesetzt wurde und ob sie aus dem Spielordner kam (Ereignis `aus_spielordner`). */
    private fun identityLines(input: TimelineInput): List<String> {
        val first = input.identityTrace.firstOrNull()
        val fromFolder = input.identityTrace.filter { it.event == MachineIdentityLog.Event.FROM_FOLDER }
        return listOf(
            "Windows ID first recorded: ${first?.let { MachineIdentityLog.formatDate(it.time) } ?: "unknown"}; current ID since: ${input.identitySinceSec?.let { MachineIdentityLog.formatDate(it) } ?: "unknown"}",
            "Windows ID taken over from the game folder: " +
                if (fromFolder.isEmpty()) "no" else "yes (${fromFolder.size}x, last ${MachineIdentityLog.formatDate(fromFolder.last().time)})",
        )
    }

    /** Die Zeilen für `geraet.txt`. */
    fun lines(input: TimelineInput): List<String> = listOf(
        "App first installed: ${date(input.firstInstallMs)}; last updated: ${date(input.lastUpdateMs)} (UTC)",
        "Install source: ${input.installer ?: "not reported (sideloaded or unknown)"}",
        "Game folder: ${age(input.gameFolder)}",
        "WTF folder: ${age(input.wtfFolder)}",
    ) + identityLines(input) + listOf(
        "App versions in the login journal: ${input.journalVersions}" +
            if (input.journalVersions > 0) " (first ${input.journalFirstVersion ?: "?"}, last ${input.journalLastVersion ?: "?"})" else "",
    )

    /** Dieselben Angaben in zwei kurzen Zeilen für `kurz.txt`. */
    fun compact(input: TimelineInput): List<String> {
        val fromFolder = input.identityTrace.any { it.event == MachineIdentityLog.Event.FROM_FOLDER }
        return listOf(
            "App installed ${date(input.firstInstallMs)}, updated ${date(input.lastUpdateMs)}; source ${input.installer ?: "unknown"}; " +
                "game folder since ${date(input.gameFolder?.oldestMs)}, WTF since ${date(input.wtfFolder?.oldestMs)}",
            "Windows ID since ${input.identityTrace.firstOrNull()?.let { MachineIdentityLog.formatDate(it.time) } ?: "unknown"}, from game folder: ${if (fromFolder) "yes" else "no"}; " +
                "app versions in journal ${input.journalVersions}",
        )
    }

    /** Zahl und erste/letzte Version der App im Journal. */
    fun versionsIn(entries: List<JournalEntry>): Triple<Int, String?, String?> =
        Triple(entries.map { it.appVersion }.distinct().size, entries.firstOrNull()?.appVersion, entries.lastOrNull()?.appVersion)
}
