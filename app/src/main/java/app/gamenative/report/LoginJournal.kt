package app.gamenative.report

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Ein Eintrag des Anmeldejournals: ein Spielstart. Alle Zeiten in Millisekunden seit 1970. Es gibt keine Felder für E-Mail,
 * Passwort oder deren Länge, keine Adressen und keinen WLAN-Namen (das Journal kennt nur diese Felder).
 */
data class JournalEntry(
    val startMs: Long,
    val appVersion: String,
    /** `LoginLevel.key`: off / fill / self. */
    val level: String,
    /** `FillScope.key`: all / password. */
    val scope: String,
    val loginFileWritten: Boolean,
    val loginFileRemovedMs: Long? = null,
    val fillTapMs: Long? = null,
    /** `done` oder `cancelled`. */
    val fillResult: String? = null,
    /** Ob die Welt-Erkennung lief (sonst ist "nicht erreicht" nicht aussagekräftig). */
    val worldSignal: Boolean = true,
    val worldMs: Long? = null,
    /** Art des Endes, siehe [LoginJournal.endKind]. */
    val endKind: String? = null,
    val endMs: Long? = null,
    /** `wifi`, `cellular`, `other`, `none`. */
    val network: String = "none",
    val vpn: Boolean? = null,
    val ipv6: Boolean? = null,
    /** Kurzkennung der Windows-Kennung und der Systemsicht (je 8 Hex-Zeichen). */
    val idFingerprint: String? = null,
    val systemFingerprint: String? = null,
    /** Sekunden vom Ende des vorigen Spiels bis zu diesem Start. */
    val sinceLastEndSec: Long? = null,
    /** Die Anmeldeversuche dieses Laufs aus der `Connection.log` (null = nicht übernommen). Siehe [LoginHistory.capture]. */
    val attempts: AttemptSummary? = null,
)

/**
 * Das Anmeldejournal (reine Kotlin-Logik, JUnit-getestet): höchstens [MAX_ENTRIES] Einträge, eine Zeile je Eintrag
 * (`schluessel=wert`, durch Leerzeichen getrennt, Werte nur aus [A-Za-z0-9._:+-]), atomar geschrieben (temporäre Datei, dann
 * Umbenennen). Eine kaputte Datei (nicht lesbar, Binärmüll, keine einzige gültige Zeile) wird neu begonnen.
 */
object LoginJournal {
    const val MAX_ENTRIES = 60
    const val FILE_NAME = "anmeldejournal.txt"

    /** Art des Endes: das Spiel hat sich selbst beendet / hart beendet (Seitenmenü, Notbremse) / die App ging in den Hintergrund. */
    fun endKind(reason: String): String = when (reason) {
        "processes_exited", "guest_terminated" -> "game_exit"
        "quick_menu", "force_close" -> "hard_kill"
        "activity_destroyed" -> "app_background"
        else -> "other_" + safe(reason)
    }

    /** Wie [safe], dazu das Komma (nur für die Liste der Versuche). */
    private fun safeList(value: String): String = value.take(600).replace(Regex("[^A-Za-z0-9._:+,-]"), "_").ifEmpty { "_" }

    private fun flagChar(b: Boolean?) = when (b) { true -> 't'; false -> 'f'; null -> 'u' }

    private fun charFlag(c: Char): Boolean? = when (c) { 't' -> true; 'f' -> false; else -> null }

    /**
     * `ok:ff:0:realm_auth:WinA:ftf,e14400:ff:41:form_submission:WinA:ftt` (Ergebnis; usedToken und authenticatorRequired als t/f/u; Sekunden seit dem
     * vorigen Versuch; Schritt; Plattform; launcherPortal gesetzt, usedSSO und Schritt fehlgeschlagen als t/f/u). Ohne die letzten drei Angaben (`-`)
     * bei Versuchen, die ohne sie gespeichert wurden.
     */
    private fun notesText(a: AttemptSummary): String =
        if (a.notes.isEmpty()) "none" else a.notes.joinToString(",") {
            val core = "${it.result}:${flagChar(it.usedToken)}${flagChar(it.authenticatorRequired)}:${it.gapSec}"
            if (it.step == null && it.platform == null && it.launcherPortalSet == null && it.usedSso == null) core
            else "$core:${it.step?.takeIf { s -> LoginStep.isValid(s) } ?: "-"}:${it.platform?.takeIf { p -> AttemptNote.isValidPlatform(p) } ?: "-"}:" +
                "${flagChar(it.launcherPortalSet)}${flagChar(it.usedSso)}${flagChar(it.stepFailed)}"
        }

    private fun parseNotes(text: String): List<AttemptNote>? {
        if (text == "none") return emptyList()
        return text.split(',').map { part ->
            val f = part.split(':')
            if ((f.size != 3 && f.size != 6) || !AttemptNote.isValid(f[0]) || f[1].length != 2) return null
            val gap = f[2].toLongOrNull()?.coerceAtLeast(0) ?: return null
            if (f.size == 3) return@map AttemptNote(f[0], charFlag(f[1][0]), charFlag(f[1][1]), gap)
            if (f[5].length != 3) return null
            AttemptNote(
                f[0], charFlag(f[1][0]), charFlag(f[1][1]), gap,
                step = f[3].takeIf { LoginStep.isValid(it) },
                stepFailed = charFlag(f[5][2]),
                platform = f[4].takeIf { AttemptNote.isValidPlatform(it) },
                launcherPortalSet = charFlag(f[5][0]),
                usedSso = charFlag(f[5][1]),
            )
        }
    }

    /** Erlaubte Zeichen eines Werts; alles andere wird zu `_`, damit eine Zeile nie mehr Felder enthält als vorgesehen. */
    fun safe(value: String): String = value.take(40).replace(Regex("[^A-Za-z0-9._:+-]"), "_").ifEmpty { "_" }

    // --- Datei ---

    fun serialize(entries: List<JournalEntry>): String = entries.takeLast(MAX_ENTRIES).joinToString("") { line(it) + "\n" }

    private fun line(e: JournalEntry): String {
        val fields = mutableListOf<String>()
        fun put(key: String, value: Any?) {
            if (value != null) fields += "$key=${safe(value.toString())}"
        }
        put("start", e.startMs)
        put("v", e.appVersion)
        put("level", e.level)
        put("scope", e.scope)
        put("loginfile", if (e.loginFileWritten) 1 else 0)
        put("removed", e.loginFileRemovedMs)
        put("fillTap", e.fillTapMs)
        put("fill", e.fillResult)
        put("signal", if (e.worldSignal) 1 else 0)
        put("world", e.worldMs)
        put("end", e.endKind)
        put("endAt", e.endMs)
        put("net", e.network)
        put("vpn", e.vpn?.let { if (it) 1 else 0 })
        put("ipv6", e.ipv6?.let { if (it) 1 else 0 })
        put("id", e.idFingerprint)
        put("sys", e.systemFingerprint)
        put("since", e.sinceLastEndSec)
        e.attempts?.let { a ->
            fields += "att=" + safeList(notesText(a))
            put("attN", a.total)
            put("attOk", a.firstOk)
        }
        return fields.joinToString(" ")
    }

    /** Liest die Datei; Zeilen, die sich nicht lesen lassen, entfallen. Eine Datei ohne gültige Zeile ergibt eine leere Liste. */
    fun parse(text: String): List<JournalEntry> {
        if (text.indexOf('\u0000') >= 0) return emptyList()
        return text.lineSequence().mapNotNull { parseLine(it) }.toList().takeLast(MAX_ENTRIES)
    }

    private fun parseLine(raw: String): JournalEntry? {
        val values = raw.trim().split(' ').filter { it.isNotEmpty() }
            .mapNotNull { part -> part.indexOf('=').takeIf { it > 0 }?.let { part.substring(0, it) to part.substring(it + 1) } }
            .toMap()
        val start = values["start"]?.toLongOrNull() ?: return null
        val version = values["v"] ?: return null
        val level = values["level"] ?: return null
        fun flag(key: String): Boolean? = when (values[key]) { "1" -> true; "0" -> false; else -> null }
        return JournalEntry(
            startMs = start,
            appVersion = version,
            level = level,
            scope = values["scope"] ?: "all",
            loginFileWritten = flag("loginfile") ?: false,
            loginFileRemovedMs = values["removed"]?.toLongOrNull(),
            fillTapMs = values["fillTap"]?.toLongOrNull(),
            fillResult = values["fill"],
            worldSignal = flag("signal") ?: true,
            worldMs = values["world"]?.toLongOrNull(),
            endKind = values["end"],
            endMs = values["endAt"]?.toLongOrNull(),
            network = values["net"] ?: "none",
            vpn = flag("vpn"),
            ipv6 = flag("ipv6"),
            idFingerprint = values["id"],
            systemFingerprint = values["sys"],
            sinceLastEndSec = values["since"]?.toLongOrNull(),
            attempts = values["att"]?.let { text ->
                parseNotes(text)?.let { notes ->
                    val total = (values["attN"]?.toIntOrNull() ?: notes.size).coerceAtLeast(notes.size)
                    val firstOk = values["attOk"]?.toIntOrNull()?.takeIf { it in 1..total }
                        ?: notes.indexOfFirst { it.isOk }.takeIf { it >= 0 }?.let { it + 1 + (total - notes.size) }
                    AttemptSummary(total, firstOk, notes)
                }
            },
        )
    }

    /** Lädt das Journal; fehlende oder kaputte Datei = leer. */
    fun load(file: File): List<JournalEntry> = runCatching { if (file.isFile && file.length() < MAX_FILE_BYTES) parse(file.readText(Charsets.UTF_8)) else emptyList() }
        .getOrDefault(emptyList())

    /** Schreibt atomar: erst eine Nachbardatei, dann Umbenennen über die alte. Gibt false zurück, wenn es nicht ging. */
    fun save(file: File, entries: List<JournalEntry>): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(serialize(entries), Charsets.UTF_8)
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    }.getOrDefault(false)

    /** Ein Eintrag mit Start, danach die Reihenfolge der Datei begrenzt auf [MAX_ENTRIES]. */
    fun append(entries: List<JournalEntry>, entry: JournalEntry): List<JournalEntry> = (entries + entry).takeLast(MAX_ENTRIES)

    /** Sekunden seit dem Ende des vorigen Spiels (null, wenn der vorige Eintrag kein Ende hat). */
    fun sinceLastEnd(previous: JournalEntry?, nowMs: Long): Long? = previous?.endMs?.let { ((nowMs - it) / 1000).coerceAtLeast(0) }

    // --- Text für den Bericht ---

    private const val MAX_FILE_BYTES = 256 * 1024L

    private fun clock(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }.format(Date(ms))

    private fun after(ms: Long?, start: Long): String? = ms?.let { "+" + ((it - start) / 1000).coerceAtLeast(0) + "s" }

    /** Eine lesbare Zeile je Eintrag. */
    fun readableLine(e: JournalEntry): String {
        val yesNo = { b: Boolean? -> when (b) { true -> "yes"; false -> "no"; null -> "?" } }
        val loginFile = if (e.loginFileWritten) "written, removed " + (after(e.loginFileRemovedMs, e.startMs) ?: "not seen") else "not written"
        val fill = if (e.fillTapMs == null) "not tapped" else "tapped ${after(e.fillTapMs, e.startMs)} (${e.fillResult ?: "?"})"
        val world = when {
            e.worldMs != null -> "reached ${after(e.worldMs, e.startMs)}"
            !e.worldSignal -> "not detected (detection off)"
            else -> "not reached"
        }
        val end = e.endKind?.let { it + (after(e.endMs, e.startMs)?.let { t -> " $t" } ?: "") } ?: "unknown (app ended or still running)"
        return "${clock(e.startMs)} | app ${e.appVersion} | level ${e.level}, scope ${e.scope} | login.txt $loginFile | fill button $fill | " +
            "world $world | end $end | net ${e.network}, vpn ${yesNo(e.vpn)}, ipv6 ${yesNo(e.ipv6)} | " +
            "windows id ${e.idFingerprint ?: "?"}, system view ${e.systemFingerprint ?: "?"} | since last end ${e.sinceLastEndSec?.let { "${it}s" } ?: "?"} | " +
            "login attempts ${LoginHistory.attemptsText(e.attempts)}"
    }

    /** Eine kurze Zeile je Eintrag für `kurz.txt` (wenig Platz): Zeit, Stufe, Ausfüllen, Welt, Ende, Netz, Versuche. */
    fun shortLine(e: JournalEntry): String {
        val fill = e.fillTapMs?.let { "fill ${e.fillResult ?: "?"}" } ?: "no fill"
        val world = when {
            e.worldMs != null -> "world +${((e.worldMs - e.startMs) / 1000).coerceAtLeast(0)}s"
            !e.worldSignal -> "world ?"
            else -> "no world"
        }
        return "${clock(e.startMs).take(16)} | ${e.level} | $fill | $world | end ${e.endKind ?: "?"} | ${e.network}${if (e.vpn == true) "+vpn" else ""} | attempts ${LoginHistory.attemptsShort(e.attempts)}"
    }

    /** Inhalt von `anmeldejournal.txt` in der Zip: neueste Einträge zuletzt. */
    fun report(entries: List<JournalEntry>): String = buildString {
        appendLine("Login journal of the app (one line per game start, last ${entries.size} of at most $MAX_ENTRIES; no e-mail, password, address or network name)")
        appendLine("times: device clock; +Ns = seconds after the start; login attempts are copied from Connection.log at the end of a run or before the next start")
        for (line in LoginHistory.overviewLines(LoginHistory.overview(entries))) appendLine(line)
        if (entries.isEmpty()) appendLine("(no game start recorded yet)")
        for (e in entries) appendLine(readableLine(e))
    }
}
