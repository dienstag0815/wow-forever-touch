package app.gamenative.utils

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * "Prefix-Merkmale": Werte im Wine-Prefix außerhalb der fünf Felder der Windows-Kennung, die Wine selbst erzeugt und die ein Gerät
 * kennzeichnen könnten (rein, ohne Android, per JUnit geprüft). Sie werden bei jedem Spielstart **vor** dem Anwenden der Kennung gemessen,
 * damit sichtbar wird, ob ein Update (oder Wines eigene Prefix-Aktualisierung) etwas neu erzeugt hat. **Nur messen, nie festschreiben
 * oder überschreiben**: jede Änderung an dem, was die App in die Registry schreibt, ließe das Gerät einmal als neuen PC erscheinen.
 *
 * Datenschutz wie bei [SystemView]: je Teil ein Fingerabdruck (8 Hex-Zeichen eines SHA-256 über die Rohwerte) und grobe Merkmale (da/nicht
 * da, Anzahl, Form); kein Wert, keine GUID, keine SID und keine Adresse steht im Protokoll, in der Verlaufsdatei, in der Zustandsdatei
 * oder im Fehlerbericht.
 */
object PrefixTraits {
    /** Die Teile; [id] steht im Protokoll und in der Zustandsdatei (nur Kleinbuchstaben), die Reihenfolge ist die der Anzeige. */
    enum class Part(val id: String) {
        /** `HwProfileGuid` (Hardware-Profil). */
        HW_PROFILE("hwprofil"),

        /** `SQMClient\MachineId`. */
        SQM("sqm"),

        /** `DigitalProductId` (trägt die Produkt-ID als Text in sich). */
        PRODUCT_KEY("produktschluessel"),

        /** Benutzer-SID(s) unter `ProfileList`. */
        USER("benutzer"),

        /** Kennungen der Netzwerkkarten (`Control\Class\{4d36e972-…}`, `Tcpip\Parameters\Interfaces`). */
        NETWORK_CARDS("netzwerkkarten"),

        /** `.update-timestamp` im Prefix (Wines Marke der letzten Prefix-Aktualisierung). */
        UPDATE_STAMP("updatezeit"),
        ;

        companion object {
            fun fromId(id: String): Part? = values().firstOrNull { it.id == id }
        }
    }

    /**
     * Rohdaten einer Messung. [systemReg] ist der Text von `system.reg` (null = nicht da oder nicht lesbar: alle Registry-Teile gelten dann
     * als unlesbar), [updateStamp] der Inhalt von `.update-timestamp` (null = Datei gibt es nicht).
     */
    class Snapshot(val systemReg: String? = null, val updateStamp: String? = null)

    data class PartInfo(val part: Part, val fingerprint: String, val features: List<String>)

    data class View(val parts: List<PartInfo>, val total: String)

    const val UNREADABLE = "unreadable"

    private fun fingerprintOf(text: String) = SystemView.fingerprintOf(text)

    // ---- Messen ------------------------------------------------------------------------------------------------------------

    /**
     * Liest die Rohdaten aus dem Prefix [wineDir] (das obere `.wine` des Containers); fehlt dort eine Datei, zählt die im Basis-Prefix
     * [lowerDir] (dünner Container). Wirft nie.
     */
    fun capture(wineDir: File, lowerDir: File? = null): Snapshot {
        fun read(name: String): String? {
            for (dir in listOfNotNull(wineDir, lowerDir)) {
                val file = File(dir, name)
                if (!file.isFile) continue
                return try {
                    String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1)
                } catch (e: Exception) {
                    null
                }
            }
            return null
        }
        return Snapshot(systemReg = read("system.reg"), updateStamp = read(".update-timestamp"))
    }

    fun compute(s: Snapshot): View {
        val sections = s.systemReg?.let { runCatching { sections(it) }.getOrNull() }
        val parts = Part.values().map { part ->
            runCatching { partOf(part, sections, s) }.getOrNull() ?: unreadable(part)
        }
        return View(parts, totalOf(parts))
    }

    fun totalOf(parts: List<PartInfo>): String =
        fingerprintOf(parts.sortedBy { it.part.ordinal }.joinToString("\n") { it.part.id + "=" + it.fingerprint })

    private fun unreadable(part: Part) = PartInfo(part, fingerprintOf(part.id + ":" + UNREADABLE), listOf(UNREADABLE))

    private fun absent(part: Part) = PartInfo(part, fingerprintOf(part.id + ":absent"), listOf("present=no"))

    /** Ein Abschnitt der Registry-Datei: [key] mit einfachen Backslashes, [values] nach kleingeschriebenem Namen. */
    class Section(val key: String, val values: Map<String, String>)

    /** Zerlegt eine Wine-`*.reg` in Abschnitte; Werte über mehrere Zeilen (`hex:…,\`) werden zusammengezogen. Ohne Zeitstempel und Kommentare. */
    fun sections(text: String): List<Section> {
        val result = ArrayList<Section>()
        var key: String? = null
        var values = LinkedHashMap<String, String>()
        fun close() {
            key?.let { result += Section(it, values) }
            values = LinkedHashMap()
        }
        var pendingName: String? = null
        val pendingValue = StringBuilder()
        for (rawLine in text.split('\n')) {
            val line = rawLine.trimEnd('\r')
            if (pendingName != null) {
                val more = line.trim()
                if (more.endsWith("\\")) {
                    pendingValue.append(more.dropLast(1))
                } else {
                    pendingValue.append(more)
                    values[pendingName] = pendingValue.toString()
                    pendingName = null
                    pendingValue.clear()
                }
                continue
            }
            if (line.startsWith("[")) {
                close()
                val end = line.lastIndexOf(']')
                key = if (end > 0) line.substring(1, end).replace("\\\\", "\\") else null
            } else if (key != null && line.startsWith("\"")) {
                val eq = line.indexOf("\"=", 1)
                if (eq < 0) continue
                val name = line.substring(1, eq).lowercase()
                val value = line.substring(eq + 2)
                if (value.endsWith("\\") && value.startsWith("hex")) {
                    pendingName = name
                    pendingValue.append(value.dropLast(1))
                } else {
                    values[name] = value
                }
            }
        }
        close()
        return result
    }

    private val GUID_FORM = Regex("^\"\\{?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\}?\"$")
    private val PRODUCT_ID_TEXT = Regex("[0-9]{5}-[0-9]{5}-[0-9]{5}-[A-Z]{5}")

    private fun partOf(part: Part, sections: List<Section>?, s: Snapshot): PartInfo? {
        if (part == Part.UPDATE_STAMP) {
            val stamp = s.updateStamp?.trim() ?: return absent(part)
            val kind = if (stamp.isNotEmpty() && stamp.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) "hexdigits" else "text"
            return PartInfo(part, fingerprintOf("stamp:" + stamp.take(64)), listOf("present=yes", "form=$kind", "length=${stamp.length.coerceAtMost(64)}"))
        }
        if (sections == null) return null
        return when (part) {
            Part.HW_PROFILE -> valuesNamed(sections, "hwprofileguid") { it.key.contains("hardware profiles", ignoreCase = true) }
                ?.let { found -> guidPart(part, found) } ?: absent(part)
            Part.SQM -> valuesNamed(sections, "machineid") { it.key.contains("sqmclient", ignoreCase = true) }
                ?.let { found -> guidPart(part, found) } ?: absent(part)
            Part.PRODUCT_KEY -> productKeyPart(sections)
            Part.USER -> userPart(sections)
            Part.NETWORK_CARDS -> networkPart(sections)
            Part.UPDATE_STAMP -> null
        }
    }

    /** `key|rohwert` aller Werte namens [name] in passenden Abschnitten, sortiert; null wenn es keinen gibt. */
    private fun valuesNamed(sections: List<Section>, name: String, match: (Section) -> Boolean): List<String>? {
        val found = sections.filter(match).mapNotNull { sec -> sec.values[name]?.let { sec.key.lowercase() + "|" + it } }.sorted()
        return found.ifEmpty { null }
    }

    private fun guidPart(part: Part, found: List<String>): PartInfo {
        val raws = found.map { it.substringAfter('|') }
        val form = if (raws.all { GUID_FORM.matches(it) }) "guid" else "other"
        return PartInfo(part, fingerprintOf(part.id + ":" + found.joinToString("\n")), listOf("present=yes", "entries=${found.size}", "form=$form"))
    }

    private fun productKeyPart(sections: List<Section>): PartInfo {
        val own = sections.filter { it.key.contains("windows nt\\currentversion", ignoreCase = true) && it.values.containsKey("digitalproductid") }
        if (own.isEmpty()) return absent(Part.PRODUCT_KEY)
        var inside = 0
        var same = 0
        for (sec in own) {
            val raw = sec.values.getValue("digitalproductid")
            val ascii = hexBytes(raw)?.let { String(it, StandardCharsets.ISO_8859_1) } ?: continue
            val id = PRODUCT_ID_TEXT.find(ascii)?.value ?: continue
            inside++
            val productId = sec.values["productid"]?.trim('"')
            if (productId != null && productId == id) same++
        }
        val features = listOf("present=yes", "entries=${own.size}", "product_id_inside=$inside", "same_as_own_product_id=$same")
        val canonical = own.map { it.key.lowercase() + "|" + it.values.getValue("digitalproductid") }.sorted().joinToString("\n")
        return PartInfo(Part.PRODUCT_KEY, fingerprintOf("key:$canonical"), features)
    }

    private fun hexBytes(raw: String): ByteArray? {
        if (!raw.startsWith("hex:")) return null
        val parts = raw.removePrefix("hex:").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val bytes = ByteArray(parts.size)
        for ((i, p) in parts.withIndex()) bytes[i] = (p.toIntOrNull(16) ?: return null).toByte()
        return bytes
    }

    private fun userPart(sections: List<Section>): PartInfo {
        val profiles = sections.filter { it.key.contains("profilelist\\s-1-", ignoreCase = true) }
        if (profiles.isEmpty()) return absent(Part.USER)
        val canonical = profiles.map { it.key.lowercase() + "|" + it.values.toSortedMap().entries.joinToString(";") { e -> e.key + "=" + e.value } }.sorted().joinToString("\n")
        val sidForm = if (profiles.all { Regex("(?i)profilelist\\\\s-1-5-[0-9-]+$").containsMatchIn(it.key) }) "nt" else "other"
        return PartInfo(Part.USER, fingerprintOf("user:$canonical"), listOf("present=yes", "profiles=${profiles.size}", "sid_form=$sidForm"))
    }

    private val CLASS_NET = "\\control\\class\\{4d36e972-e325-11ce-bfc1-08002be10318}"
    private val TCPIP_IF = "\\services\\tcpip\\parameters\\interfaces\\{"

    private fun networkPart(sections: List<Section>): PartInfo {
        val cards = sections.filter { it.key.lowercase().contains(CLASS_NET + "\\") }
        val ifaces = sections.filter { it.key.lowercase().contains(TCPIP_IF) }
        if (cards.isEmpty() && ifaces.isEmpty()) return absent(Part.NETWORK_CARDS)
        fun canon(list: List<Section>) = list.map { sec ->
            sec.key.lowercase() + "|" + listOf("netcfginstanceid", "networkaddress")
                .mapNotNull { n -> sec.values[n]?.let { "$n=$it" } }.joinToString(";")
        }.sorted().joinToString("\n")
        val withAddress = cards.count { it.values.containsKey("networkaddress") }
        val features = listOf("present=yes", "class_entries=${cards.size}", "interfaces=${ifaces.size}", "network_address=" + if (withAddress > 0) "yes" else "no")
        return PartInfo(Part.NETWORK_CARDS, fingerprintOf("net:" + canon(cards) + "\n--\n" + canon(ifaces)), features)
    }

    // ---- Vergleich alt/neu -------------------------------------------------------------------------------------------------

    /** Der gespeicherte Stand der letzten Messung (App-Speicher). */
    data class Stored(val total: String, val parts: List<PartInfo>)

    fun stored(view: View): Stored = Stored(view.total, view.parts)

    sealed class Change {
        /** Es gab noch keinen gespeicherten Stand. */
        object First : Change()
        object Same : Change()

        /** [parts] = Teile (feste Reihenfolge), deren Fingerabdruck sich geändert hat oder die neu/weggefallen sind. */
        class Changed(val parts: List<Part>) : Change()
    }

    fun compare(old: Stored?, view: View): Change {
        if (old == null) return Change.First
        val before = old.parts.associate { it.part to it.fingerprint }
        val after = view.parts.associate { it.part to it.fingerprint }
        val changed = Part.values().filter { before[it] != after[it] }
        return if (changed.isEmpty()) Change.Same else Change.Changed(changed)
    }

    // ---- Protokolldetail ---------------------------------------------------------------------------------------------------

    /** Detail des Ereignisses: `pm:<Gesamt>` beim ersten Mal, `pm:<Gesamt>:<Teile>` bei einer Änderung. */
    fun eventDetail(view: View, change: Change): String = when (change) {
        is Change.Changed -> "pm:" + view.total + ":" + change.parts.joinToString(",") { it.id }
        else -> "pm:" + view.total
    }

    private val DETAIL = Regex("^pm:([0-9a-f]{8})(?::([a-z]+(?:,[a-z]+)*))?$")

    /** True, wenn [detail] genau diese Form hat (dann darf es in die Verlaufsdatei im Spielordner). */
    fun isSafeDetail(detail: String): Boolean = DETAIL.matches(detail)

    /** Gesamt-Fingerabdruck und Teilnamen aus einem Ereignisdetail. */
    fun parseDetail(detail: String): Pair<String, List<String>>? {
        val m = DETAIL.matchEntire(detail) ?: return null
        return m.groupValues[1] to m.groupValues[2].split(',').filter { it.isNotEmpty() }
    }

    // ---- Zustandsdatei (App-Speicher) --------------------------------------------------------------------------------------

    const val FILE_NAME = "wow_prefix_traits.txt"
    private const val HEADER = "prefixmerkmale1"
    private val HEX8 = Regex("^[0-9a-f]{8}$")
    private val FEATURE_BAD = Regex("[^A-Za-z0-9_:,.=+\\- ]")

    fun serialize(stored: Stored): String = buildString {
        append(HEADER).append('\n')
        append("total\t").append(stored.total).append('\n')
        for (p in stored.parts) {
            append("part\t").append(p.part.id).append('\t').append(p.fingerprint).append('\t').append(p.features.joinToString(";")).append('\n')
        }
    }

    /** Liest die Zustandsdatei; bei jedem Formfehler null (dann zählt die nächste Messung als erste). */
    fun parseStored(text: String?): Stored? {
        val lines = text?.lines()?.map { it.trimEnd('\r') }?.filter { it.isNotEmpty() } ?: return null
        if (lines.firstOrNull() != HEADER) return null
        var total: String? = null
        val parts = ArrayList<PartInfo>()
        for (line in lines.drop(1)) {
            val cols = line.split('\t')
            when (cols[0]) {
                "total" -> total = cols.getOrNull(1)?.takeIf { HEX8.matches(it) } ?: return null
                "part" -> {
                    if (cols.size < 3) return null
                    val part = Part.fromId(cols[1]) ?: continue
                    val fp = cols[2].takeIf { HEX8.matches(it) } ?: return null
                    val features = cols.getOrElse(3) { "" }.split(';').filter { it.isNotEmpty() }.map { FEATURE_BAD.replace(it.take(80), "_") }
                    parts += PartInfo(part, fp, features)
                }
            }
        }
        if (total == null || parts.isEmpty()) return null
        return Stored(total, parts)
    }

    // ---- Anzeige ----------------------------------------------------------------------------------------------------------

    /** Was die Karte zeigt: wie [SystemView.Summary], für die Prefix-Merkmale. */
    data class Summary(
        val total: String?,
        val since: Long?,
        val changedParts: List<String>,
        val changedAt: Long?,
        val changes: Int,
        val parts: List<PartInfo>,
    )

    fun summarize(log: List<MachineIdentityLog.Entry>, history: List<MachineIdentityLog.Entry>, stored: Stored?): Summary {
        val a = log.filter { it.event.isPrefixTraits }
        val b = history.filter { it.event.isPrefixTraits }
        val events = if ((b.lastOrNull()?.time ?: -1L) > (a.lastOrNull()?.time ?: -1L)) b else a
        val last = events.lastOrNull()
        val lastParsed = last?.let { parseDetail(it.detail) }
        val lastChange = events.lastOrNull { it.event == MachineIdentityLog.Event.PREFIX_TRAITS_CHANGED }
        return Summary(
            total = stored?.total ?: lastParsed?.first,
            since = last?.time,
            changedParts = lastChange?.let { parseDetail(it.detail)?.second }.orEmpty(),
            changedAt = lastChange?.time,
            changes = events.count { it.event == MachineIdentityLog.Event.PREFIX_TRAITS_CHANGED },
            parts = stored?.parts.orEmpty(),
        )
    }
}
