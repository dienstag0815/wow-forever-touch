package app.gamenative.utils

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Protokoll der Windows-Kennung (rein, ohne Android, per JUnit geprüft).
 *
 * Wozu: Ändert sich die Kennung unbemerkt (Datei weg, Speichern misslungen, Neuinstallation, Prefix neu), sieht Battle.net bei jedem
 * Login einen neuen PC. Das Protokoll macht jeden Wechsel nachverfolgbar, ohne die Kennung selbst preiszugeben: gespeichert wird
 * nur die Kurzkennung ([fingerprint], erste 8 Hex-Zeichen von SHA-256 über die serialisierte Kennung), nie ein Wert der Kennung.
 *
 * Eine Zeile je Ereignis: `Zeit(Sekunden)<TAB>Kurzkennung<TAB>versionCode<TAB>Ereignis<TAB>Detail`. Das Detail (Grund oder Namen
 * abweichender Felder) enthält nur Namen, nie Werte, und fehlt in der Verlaufsdatei im Spielordner.
 */
object MachineIdentityLog {
    /** Höchstzahl der Einträge in der Datei im App-Speicher und in der Verlaufsdatei; die ältesten fallen weg. */
    const val MAX_ENTRIES = 50

    /** Name der Protokolldatei im App-Speicher (`filesDir`). */
    const val LOG_FILE_NAME = "wow_identity_log.txt"

    /** Name der Verlaufsdatei im Spielordner (übersteht eine Neuinstallation der App; nur Zeit, Kurzkennung, Version, Ereignis). */
    const val HISTORY_FILE_NAME = "wowforever-kennung.txt"

    /** Die Ereignisse. [id] steht in der Datei. */
    enum class Event(val id: String) {
        /** Keine Kennungsdatei da: neue Kennung erzeugt. */
        CREATED("erzeugt"),

        /** Kennungsdatei da, aber unlesbar oder ungültig: neue Kennung erzeugt (Detail nennt den Grund). */
        CREATED_INVALID("erzeugt_ungueltig"),

        /** Die Kennung ließ sich nicht speichern (sie ändert sich dann bei jedem Start des Prozesses). */
        SAVE_FAILED("speichern_fehlgeschlagen"),

        /** Ein erneuter Versuch hat die zuvor nicht gespeicherte Kennung doch gespeichert. */
        SAVED_LATER("gespeichert"),

        /** Kennung geladen; nur protokolliert, wenn sich Kurzkennung oder App-Version gegenüber dem letzten Eintrag geändert haben. */
        LOADED("geladen"),

        /** Der Prefix enthält nicht, was die Kennung sagt (Detail: Zeitpunkt und Namen der abweichenden Felder). */
        PREFIX_MISMATCH("prefix_abweichung"),

        /** Der Spieler hat eine neue Kennung erzeugen lassen (Detail: `alt:<Kurzkennung>`; die Kurzkennung des Eintrags ist die neue). */
        NEW_ON_REQUEST("neu_auf_wunsch"),

        /** Der Spieler hat eine frühere Kennung aus der Ablage zurückgeholt (Detail: `alt:<Kurzkennung>`; der Eintrag trägt die zurückgeholte). */
        RESTORED("wiederhergestellt"),

        /** Erste Erfassung der Systemsicht (Detail: `sys:<Gesamt-Fingerabdruck>`; die Kurzkennung des Eintrags ist die der Windows-Kennung). */
        SYSTEM_CAPTURED("systemsicht_erfasst"),

        /** Die Systemsicht hat sich seit dem letzten Start geändert (Detail: `sys:<Gesamt-Fingerabdruck>:<Teilnamen>`). */
        SYSTEM_CHANGED("systemsicht_geaendert"),

        /** Die Kennungsdatei war nicht ganz gültig: gültige Felder blieben, nur die Felder im Detail (Namen) wurden neu erzeugt. */
        REPAIRED("repariert"),

        /** Die Hauptdatei fehlte oder war unbrauchbar; die Kennung kam aus der Zweitkopie (Detail: `haupt_fehlt` oder `haupt_ungueltig`). */
        FROM_COPY("aus_zweitkopie"),

        /** Erste Erfassung der Prefix-Merkmale vor dem Anwenden (Detail: `pm:<Gesamt-Fingerabdruck>`). */
        PREFIX_TRAITS_CAPTURED("prefix_merkmale_erfasst"),

        /** Die Prefix-Merkmale haben sich seit dem letzten Start geändert (Detail: `pm:<Gesamt-Fingerabdruck>:<Teilnamen>`). */
        PREFIX_TRAITS_CHANGED("prefix_merkmale_geaendert"),

        /** Keine Kennung im App-Speicher (oder nur eine noch nie benutzte neue): die Kopie im Spielordner wurde übernommen (Detail: `keine_kennung` oder `verfallen:<Kurzkennung der unbenutzten neuen>`). */
        FROM_FOLDER("aus_spielordner"),

        /** Die Kopie im Spielordner war unbrauchbar und wurde ignoriert (Detail: Grund, siehe [MachineIdentityKeep.REASONS]). */
        KEEP_IGNORED("kopie_ignoriert"),

        /** Eine gültige Kopie im Spielordner wurde nicht übernommen, sondern unter "Frühere Kennungen" abgelegt (Detail: `anderes_modell` oder `neue_benutzt`). */
        KEEP_ARCHIVED("kopie_abgelegt"),

        /** Die Kopie im Spielordner ließ sich nicht schreiben (kein Zugriff); nur einmal, bis sie wieder geschrieben werden kann. */
        KEEP_FAILED("kopie_nicht_geschrieben"),

        /** Die Kopie im Spielordner wurde nach einem Fehlschlag wieder geschrieben. */
        KEEP_WRITTEN("kopie_geschrieben"),

        /** Der Spieler hat "Kennung im Spielordner sichern" ausgeschaltet; die Kopie wurde gelöscht. */
        KEEP_OFF("kopie_aus"),

        /** Der Spieler hat "Kennung im Spielordner sichern" eingeschaltet. */
        KEEP_ON("kopie_an"),
        ;

        /** Ereignis der Systemsicht: gehört nicht zur Geschichte der Kennung selbst. */
        val isSystem: Boolean get() = this == SYSTEM_CAPTURED || this == SYSTEM_CHANGED

        /** Ereignis der Prefix-Merkmale (Messung, keine Änderung der Kennung). */
        val isPrefixTraits: Boolean get() = this == PREFIX_TRAITS_CAPTURED || this == PREFIX_TRAITS_CHANGED

        /** Messereignis: ändert weder Beginn noch Wechsel der Kennung und steht nicht unter "Letzte Ereignisse". */
        val isMeasurement: Boolean get() = isSystem || isPrefixTraits

        /** Hinweis zur Kopie im Spielordner: ändert die Kennung nicht und verdeckt die Speicherwarnung nicht. */
        val isKeepNote: Boolean
            get() = this == KEEP_IGNORED || this == KEEP_ARCHIVED || this == KEEP_FAILED || this == KEEP_WRITTEN || this == KEEP_OFF || this == KEEP_ON

        /** Das Detail ist nur eine Kurzkennung und darf deshalb auch in die Verlaufsdatei im Spielordner. */
        val detailIsFingerprint: Boolean get() = this == NEW_ON_REQUEST || this == RESTORED

        companion object {
            fun fromId(id: String): Event? = values().firstOrNull { it.id == id }
        }
    }

    /** Ein Eintrag; [time] in Sekunden seit 1970. */
    data class Entry(
        val time: Long,
        val fingerprint: String,
        val versionCode: Int,
        val event: Event,
        val detail: String = "",
    )

    /** Kurzkennung: erste 8 Hex-Zeichen (klein) von SHA-256 über die serialisierte Kennung. */
    fun fingerprint(identity: MachineIdentity): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.serialize().toByteArray(StandardCharsets.UTF_8))
        val hex = StringBuilder(8)
        for (i in 0 until 4) hex.append("%02x".format(digest[i].toInt() and 0xff))
        return hex.toString()
    }

    private val FINGERPRINT = Regex("^[0-9a-f]{8}$")
    private const val DETAIL_MAX = 120

    /** Nur Namen und Satzzeichen von Zeitpunkt und Feldliste: alles andere wird zu `_`, Tabs und Zeilenumbrüche können so nie stören. */
    fun cleanDetail(detail: String): String =
        detail.take(DETAIL_MAX).map { if (it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "_:,.-") it else '_' }.joinToString("")

    fun format(entry: Entry): String =
        listOf(entry.time.toString(), entry.fingerprint, entry.versionCode.toString(), entry.event.id, cleanDetail(entry.detail))
            .filterIndexed { i, part -> i < 4 || part.isNotEmpty() }
            .joinToString("\t")

    fun parseLine(line: String): Entry? {
        val parts = line.trimEnd('\r', '\n').split('\t')
        if (parts.size < 4) return null
        val time = parts[0].toLongOrNull() ?: return null
        val fingerprint = parts[1].takeIf { FINGERPRINT.matches(it) } ?: return null
        val version = parts[2].toIntOrNull() ?: return null
        val event = Event.fromId(parts[3]) ?: return null
        return Entry(time, fingerprint, version, event, cleanDetail(parts.getOrElse(4) { "" }))
    }

    /** Liest den Dateitext; unlesbare Zeilen fallen weg, mehr als [MAX_ENTRIES] werden von vorn gekappt. */
    fun parse(text: String?): List<Entry> =
        text?.lines()?.mapNotNull { parseLine(it) }.orEmpty().takeLast(MAX_ENTRIES)

    fun serialize(entries: List<Entry>, withDetail: Boolean = true): String =
        entries.joinToString("") { format(if (withDetail) it else forHistory(it)) + "\n" }

    /** Eintrag für die Verlaufsdatei: ohne Detail; es bleiben nur sichere Formen: bei Wechseln auf Wunsch die alte Kurzkennung (`alt:<Kurzkennung>`), bei Systemsicht und Prefix-Merkmalen Gesamt-Fingerabdruck und Teilnamen, bei `repariert` die Feldnamen, bei `aus_zweitkopie` der Grund, bei den Ereignissen zur Kopie im Spielordner feste Wörter. */
    fun forHistory(entry: Entry): Entry =
        when {
            entry.event.detailIsFingerprint && OLD_FINGERPRINT.matches(entry.detail) -> entry
            entry.event.isSystem && SystemView.isSafeDetail(entry.detail) -> entry
            entry.event.isPrefixTraits && PrefixTraits.isSafeDetail(entry.detail) -> entry
            entry.event == Event.REPAIRED && REPAIRED_FIELDS.matches(entry.detail) -> entry
            entry.event == Event.FROM_COPY && entry.detail in COPY_REASONS -> entry
            entry.event == Event.FROM_FOLDER && (entry.detail == "keine_kennung" || DISCARDED.matches(entry.detail)) -> entry
            entry.event == Event.KEEP_IGNORED && entry.detail in MachineIdentityKeep.REASONS -> entry
            entry.event == Event.KEEP_ARCHIVED && entry.detail in ARCHIVED_REASONS -> entry
            else -> entry.copy(detail = "")
        }

    private val REPAIRED_FIELDS = Regex("^(?:" + MachineIdentity.FIELD_NAMES.joinToString("|") + ")(?:,(?:" + MachineIdentity.FIELD_NAMES.joinToString("|") + "))*$")
    private val COPY_REASONS = setOf("haupt_fehlt", "haupt_ungueltig")
    private val ARCHIVED_REASONS = setOf("anderes_modell", "neue_benutzt")
    private val DISCARDED = Regex("^verfallen:[0-9a-f]{8}$")

    /** Detail von [Event.FROM_FOLDER], wenn eine noch nie benutzte neue Kennung durch die Kopie ersetzt wurde. */
    fun discardedDetail(discarded: String): String = "verfallen:$discarded"

    private val OLD_FINGERPRINT = Regex("^alt:[0-9a-f]{8}$")

    /** Detail der Ereignisse [Event.NEW_ON_REQUEST] und [Event.RESTORED]. */
    fun oldFingerprintDetail(old: String): String = "alt:$old"

    /** Sperrfrist für "Neue Kennung erzeugen": höchstens eine je 24 Stunden. */
    const val REGENERATE_LOCK_SECONDS = 24L * 60 * 60

    /**
     * Ab wann eine neue Kennung wieder erzeugt werden darf, wenn die Sperre noch gilt; sonst null. Zählt nur [Event.NEW_ON_REQUEST]
     * (Zurückholen weder sperrt noch entsperrt). Liegt der letzte Eintrag in der Zukunft (Uhr zurückgestellt), gilt keine Sperre.
     */
    fun regenerateLockedUntil(entries: List<Entry>, now: Long): Long? {
        val last = entries.lastOrNull { it.event == Event.NEW_ON_REQUEST }?.time ?: return null
        if (last > now) return null
        val until = last + REGENERATE_LOCK_SECONDS
        return if (now < until) until else null
    }

    /** True, wenn [entry] dem letzten Eintrag bis auf die Zeit gleicht: wiederholte Meldungen (jeder Start) füllen das Protokoll nicht. */
    fun isRepeat(entries: List<Entry>, entry: Entry): Boolean =
        entries.lastOrNull()?.let { it.copy(time = entry.time) == entry } == true

    /**
     * `geladen` wird nur eingetragen, wenn es noch keinen Eintrag gibt oder sich Kurzkennung oder App-Version gegenüber dem
     * letzten Eintrag geändert haben.
     */
    fun shouldLogLoaded(entries: List<Entry>, fingerprint: String, versionCode: Int): Boolean {
        val last = entries.lastOrNull() ?: return true
        return last.fingerprint != fingerprint || last.versionCode != versionCode
    }

    /** Hängt [entry] an (nichts bei einer Wiederholung) und kappt auf [MAX_ENTRIES]. */
    fun append(entries: List<Entry>, entry: Entry): List<Entry> =
        if (isRepeat(entries, entry)) entries else (entries + entry).takeLast(MAX_ENTRIES)

    /** Zeit als `2026-11-12 08:30:15` (UTC, ohne Sprachabhängigkeit). */
    fun formatTime(seconds: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(seconds))

    /** Datum als `2026-11-12` (UTC). */
    fun formatDate(seconds: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(seconds))

    /** Der Verlauf als Text zum Kopieren und für den Fehlerbericht: nur Zeit, Kurzkennung, Version, Ereignis und Detailnamen. */
    fun historyText(entries: List<Entry>): String =
        if (entries.isEmpty()) "(no entries)\n"
        else entries.joinToString("") { e ->
            val detail = if (e.detail.isEmpty()) "" else " " + e.detail
            "${formatTime(e.time)} UTC  ${e.fingerprint}  v${e.versionCode}  ${e.event.id}$detail\n"
        }

    /**
     * Was die Diagnose-Seite zeigt. [fingerprint] ist die Kurzkennung des letzten Eintrags, [since] der Beginn der Reihe gleicher
     * Kurzkennungen am Ende, [changes] die Zahl der Wechsel der Kurzkennung im Verlauf, [previous] die letzte andere Kurzkennung davor,
     * [recent] die letzten Ereignisse (neueste zuletzt), [saveFailed] ob das letzte Ereignis ein misslungenes Speichern war.
     */
    data class Summary(
        val fingerprint: String?,
        val since: Long?,
        val changes: Int,
        val previous: String?,
        val recent: List<Entry>,
        val saveFailed: Boolean,
        val trace: List<Entry>,
        val update: UpdateChange? = null,
    )

    /**
     * Der letzte Wechsel der App-Version im Verlauf: [from] und [to] sind die Versionen, [unchanged] ob die Kurzkennung des ersten Eintrags
     * der neuen Version (ohne Kennungswechsel auf Wunsch) gleich der des letzten Eintrags der alten ist, [at] die Zeit dieses Eintrags.
     */
    data class UpdateChange(val from: Int, val to: Int, val unchanged: Boolean, val at: Long)

    /** Die Update-Zeile für den Fehlerbericht: `299 -> 300: ID unchanged` oder `ID CHANGED`, `-` ohne Versionswechsel im Verlauf. */
    fun updateText(update: UpdateChange?): String =
        if (update == null) "-" else "${update.from} -> ${update.to}: " + (if (update.unchanged) "ID unchanged" else "ID CHANGED") + " (" + formatDate(update.at) + ")"

    /**
     * Sucht im [trace] den letzten Versionswechsel: der letzte Eintrag der alten Version gegen den ersten der neuen. Beginnt die neue Version
     * mit einem Eintrag, den der Spieler selbst ausgelöst hat (`neu_auf_wunsch`, `wiederhergestellt`), lässt sich der Wechsel nicht dem Update
     * zuschreiben: dann ist nichts zu sagen (null). Rein.
     */
    fun updateChange(trace: List<Entry>): UpdateChange? {
        var boundary = -1
        for (i in trace.size - 1 downTo 1) {
            if (trace[i].versionCode != trace[i - 1].versionCode) {
                boundary = i
                break
            }
        }
        if (boundary < 0) return null
        val before = trace[boundary - 1]
        val after = trace[boundary]
        if (after.event == Event.NEW_ON_REQUEST || after.event == Event.RESTORED) return null
        return UpdateChange(before.versionCode, after.versionCode, before.fingerprint == after.fingerprint, after.time)
    }

    const val RECENT_SHOWN = 5

    /**
     * [log] ist das Protokoll im App-Speicher, [history] die Verlaufsdatei im Spielordner (leer, wenn nicht lesbar). Die längere der
     * beiden zählt für Beginn, Wechsel und "vorher" (nach einer Neuinstallation ist nur die Verlaufsdatei vollständig), die letzten
     * Ereignisse stammen aus dem Protokoll, solange es Einträge hat, sonst aus der Verlaufsdatei.
     */
    fun summarize(log: List<Entry>, history: List<Entry>): Summary {
        val trace = if (history.size > log.size) history else log
        val last = trace.lastOrNull()
        val current = last?.fingerprint
        var start = trace.size - 1
        while (start > 0 && trace[start - 1].fingerprint == current) start--
        val changes = (1 until trace.size).count { trace[it].fingerprint != trace[it - 1].fingerprint }
        val previous = if (start > 0) trace[start - 1].fingerprint else null
        // Ereignisse der Systemsicht tragen die Kurzkennung der Windows-Kennung und ändern daher Beginn und Wechsel nicht; die Liste
        // der letzten Ereignisse und die Speicherwarnung gehören aber der Kennung allein (Systemereignisse stehen in SystemView.Summary).
        val shown = log.ifEmpty { history }.filterNot { it.event.isMeasurement }
        return Summary(
            fingerprint = current,
            since = if (last == null) null else trace[start].time,
            changes = changes,
            previous = previous,
            recent = shown.takeLast(RECENT_SHOWN),
            saveFailed = shown.lastOrNull { !it.event.isKeepNote }?.event == Event.SAVE_FAILED,
            trace = trace,
            update = updateChange(trace),
        )
    }
}
