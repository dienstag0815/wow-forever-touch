package app.gamenative.utils

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * "Systemsicht": was das Betriebssystem dem Spiel (Wine) bei jedem Start frisch zeigt und je Gerät, Netz und Einstellung wechseln kann
 * (rein, ohne Android, per JUnit geprüft; die Android-Seite ist [SystemViewCapture]).
 *
 * Datenschutz: Hostname, MAC-Adressen und IP-Adressen verlassen [compute] nie. Je Teil bleiben ein Fingerabdruck (8 Hex-Zeichen
 * eines SHA-256) und grobe Merkmale (Art, Anzahl, ja/nein, Stufen). Der Gesamt-Fingerabdruck läuft über alle Teile.
 *
 * Eine Erfassung ist ein [Snapshot]; ein Teil, der sich nicht lesen ließ, ist `null` und erscheint als `unreadable` (nie als Absturz).
 */
object SystemView {
    /** Die Teile; [id] steht im Protokoll und in der Zustandsdatei, die Reihenfolge ist die der Anzeige und des Gesamt-Fingerabdrucks. */
    enum class Part(val id: String) {
        HOSTNAME("hostname"),
        INTERFACES("schnittstellen"),
        NETWORK("netz"),
        CPU("prozessor"),
        GAME_FOLDER("spielordner"),
        USER("nutzer"),
        FREE_SPACE("speicher"),
        ANDROID("android"),
        ;

        companion object {
            fun fromId(id: String): Part? = values().firstOrNull { it.id == id }
        }
    }

    /** Eine Netzwerkschnittstelle, wie sie erfasst wurde. [mac] ist roh und wird nur zum Hashen und für "lokal vergeben" gebraucht. */
    class Iface(val name: String, val up: Boolean, val mac: String?)

    /** Das aktive Netz: [transports] aus `wlan`, `mobil`, `vpn`, `ethernet`, `bluetooth`, `andere`; leer = kein aktives Netz. */
    data class Network(
        val transports: Set<String>,
        val ipv4: Boolean,
        val ipv6: Boolean,
        /** `aus`, `automatisch` oder `anbieter` (nur der Modus, nie der Hostname des Anbieters). */
        val privateDns: String,
    )

    /**
     * Rohdaten einer Erfassung. [systemHostname] ist der Name, den der Prozess vom System bekommt; [shimActive] ob die Umleitung
     * (WOWFOREVER_HOSTNAME) gesetzt wird. [gameFolderKind] siehe [storageKind].
     */
    class Snapshot(
        val systemHostname: String? = null,
        val shimActive: Boolean? = null,
        val interfaces: List<Iface>? = null,
        val network: Network? = null,
        val cpuPresent: Int? = null,
        val cpuOnline: Int? = null,
        val gameFolderKind: String? = null,
        val userId: Int? = null,
        val freeBytes: Long? = null,
        val androidSdk: Int? = null,
    )

    /** Ein Teil der Systemsicht: Fingerabdruck und grobe Merkmale (nur ASCII aus [cleanFeature]). */
    data class PartInfo(val part: Part, val fingerprint: String, val features: List<String>)

    data class View(val parts: List<PartInfo>, val total: String)

    const val UNREADABLE = "unreadable"

    /** 8 Hex-Zeichen (klein) von SHA-256 über [text]. */
    fun fingerprintOf(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
        val hex = StringBuilder(8)
        for (i in 0 until 4) hex.append("%02x".format(digest[i].toInt() and 0xff))
        return hex.toString()
    }

    fun compute(s: Snapshot): View {
        val parts = Part.values().map { part ->
            runCatching { partOf(part, s) }.getOrNull() ?: unreadable(part)
        }
        return View(parts, totalOf(parts))
    }

    /** Gesamt-Fingerabdruck: Teilnamen mit ihren Fingerabdrücken in fester Reihenfolge. */
    fun totalOf(parts: List<PartInfo>): String =
        fingerprintOf(parts.sortedBy { it.part.ordinal }.joinToString("\n") { it.part.id + "=" + it.fingerprint })

    private fun unreadable(part: Part) = PartInfo(part, fingerprintOf(part.id + ":" + UNREADABLE), listOf(UNREADABLE))

    private fun partOf(part: Part, s: Snapshot): PartInfo? = when (part) {
        Part.HOSTNAME -> {
            val name = s.systemHostname
            val shim = s.shimActive
            if (name == null || shim == null) null else {
                val kind = when {
                    name.isBlank() -> "empty"
                    name.equals("localhost", ignoreCase = true) -> "localhost"
                    else -> "set"
                }
                // Der Name selbst geht nur in den Hash ein, nie in ein Merkmal.
                PartInfo(part, fingerprintOf("host:" + name + ":" + shim), listOf("system=$kind", "shim=" + yesNo(shim)))
            }
        }
        Part.INTERFACES -> s.interfaces?.let { interfacesPart(it) }
        Part.NETWORK -> s.network?.let { n ->
            val transports = n.transports.sorted()
            val features = listOf("net=" + if (transports.isEmpty()) "none" else transports.joinToString("+"), "ipv4=" + yesNo(n.ipv4), "ipv6=" + yesNo(n.ipv6), "privatedns=" + n.privateDns)
            PartInfo(part, fingerprintOf("net:" + features.joinToString("|")), features.map(::cleanFeature))
        }
        Part.CPU -> if (s.cpuPresent == null || s.cpuOnline == null) null else {
            val features = listOf("present=${s.cpuPresent}", "online=${s.cpuOnline}")
            PartInfo(part, fingerprintOf("cpu:" + features.joinToString("|")), features)
        }
        Part.GAME_FOLDER -> s.gameFolderKind?.let { PartInfo(part, fingerprintOf("folder:$it"), listOf("kind=" + cleanFeature(it))) }
        Part.USER -> s.userId?.let { PartInfo(part, fingerprintOf("user:" + (it != 0)), listOf("second_profile=" + yesNo(it != 0))) }
        Part.FREE_SPACE -> s.freeBytes?.let { PartInfo(part, fingerprintOf("free:" + freeLevel(it)), listOf("level=" + freeLevel(it))) }
        Part.ANDROID -> s.androidSdk?.let { PartInfo(part, fingerprintOf("sdk:$it"), listOf("sdk=$it")) }
    }

    private fun yesNo(b: Boolean) = if (b) "yes" else "no"

    private fun interfacesPart(all: List<Iface>): PartInfo {
        val shown = all.filter { it.name != "lo" }.sortedBy { it.name }
        val canonical = StringBuilder()
        val features = ArrayList<String>()
        features += "count=${shown.size}"
        for (i in shown) {
            val kind = interfaceKind(i.name)
            val mac = parseMac(i.mac)
            val macState = when {
                mac == null -> "none"
                isLocalMac(mac) -> "local"
                else -> "global"
            }
            features += cleanFeature(i.name + " " + kind + " " + (if (i.up) "up" else "down") + " mac=" + macState)
            // Die MAC geht nur als Hash in den Fingerabdruck: wechselt eine zufällige Adresse, wechselt der Teil.
            canonical.append(i.name).append('|').append(kind).append('|').append(i.up).append('|')
                .append(if (mac == null) "-" else fingerprintOf("mac:" + mac.joinToString(":") { "%02x".format(it.toInt() and 0xff) })).append('\n')
        }
        return PartInfo(Part.INTERFACES, fingerprintOf("if:$canonical"), features)
    }

    /** Art einer Schnittstelle nach ihrem Namen: wlan, rmnet (Mobilfunk), clat (IPv4-Übersetzung), tun (VPN), p2p, eth, bt, ap, dummy, lo, other. */
    fun interfaceKind(name: String): String {
        val n = name.lowercase()
        return when {
            n == "lo" -> "lo"
            n.startsWith("v4-") || n.startsWith("clat") -> "clat"
            n.startsWith("wlan") || n.startsWith("wifi") -> "wlan"
            n.startsWith("swlan") || n.startsWith("ap") -> "ap"
            n.startsWith("p2p") -> "p2p"
            n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("pdp") || n.startsWith("seth") -> "rmnet"
            n.startsWith("tun") || n.startsWith("ppp") || n.startsWith("wg") || n.startsWith("tap") -> "tun"
            n.startsWith("eth") || n.startsWith("usb") || n.startsWith("rndis") -> "eth"
            n.startsWith("bt") || n.startsWith("bnep") -> "bt"
            n.startsWith("dummy") -> "dummy"
            else -> "other"
        }
    }

    /** Sechs Byte aus `aa:bb:cc:dd:ee:ff`; null bei Unlesbarem, bei lauter Nullen und bei der festen Platzhalter-Adresse 02:00:00:00:00:00. */
    fun parseMac(text: String?): ByteArray? {
        val parts = text?.trim()?.split(':', '-') ?: return null
        if (parts.size != 6) return null
        val bytes = ByteArray(6)
        for ((i, p) in parts.withIndex()) {
            if (p.length != 2) return null
            bytes[i] = (p.toIntOrNull(16) ?: return null).toByte()
        }
        if (bytes.all { it == 0.toByte() }) return null
        if (bytes[0] == 0x02.toByte() && bytes.drop(1).all { it == 0.toByte() }) return null
        return bytes
    }

    /** Lokal vergebene (zufällige) Adresse: das "locally administered"-Bit im ersten Byte. */
    fun isLocalMac(mac: ByteArray): Boolean = (mac[0].toInt() and 0x02) != 0

    /** Kerne aus Dateien wie `/sys/devices/system/cpu/present` ("0-7", "0-3,5-6"); null bei Unlesbarem. */
    fun countCpuList(text: String?): Int? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        var count = 0
        for (piece in trimmed.split(',')) {
            val range = piece.trim().split('-')
            when (range.size) {
                1 -> { range[0].toIntOrNull() ?: return null; count++ }
                2 -> {
                    val a = range[0].toIntOrNull() ?: return null
                    val b = range[1].toIntOrNull() ?: return null
                    if (b < a) return null
                    count += b - a + 1
                }
                else -> return null
            }
        }
        return count
    }

    private const val GB = 1024L * 1024 * 1024

    /** Freier Speicher in vier Stufen, damit kleine Schwankungen kein Ereignis machen. */
    fun freeLevel(bytes: Long): String = when {
        bytes < 0 -> UNREADABLE
        bytes < GB -> "lt1gb"
        bytes < 4 * GB -> "1to4gb"
        bytes < 16 * GB -> "4to16gb"
        else -> "ge16gb"
    }

    /** Art des Spielordners: `intern`, `sd` (Wurzel einer Speicherkarte) oder `anderes`. Der Pfad selbst wird nicht weitergegeben. */
    fun storageKind(path: String, internalRoot: String, sdRoots: List<String>): String {
        fun under(root: String) = root.isNotEmpty() && (path == root || path.startsWith(root.trimEnd('/') + "/"))
        return when {
            sdRoots.any { under(it) } -> "sd"
            under(internalRoot) -> "intern"
            else -> "anderes"
        }
    }

    private val FEATURE_BAD = Regex("[^A-Za-z0-9_:,.=+\\- ]")

    /** Merkmale sind kurze Wörter; alles andere (Tabs, Zeilenumbrüche, Semikolon) wird zu `_`. */
    fun cleanFeature(text: String): String = FEATURE_BAD.replace(text.take(80), "_")

    // ---- Vergleich alt/neu -------------------------------------------------------------------------------------------------

    /** Der gespeicherte Stand der letzten Erfassung (App-Speicher). */
    data class Stored(val total: String, val parts: List<PartInfo>)

    fun stored(view: View): Stored = Stored(view.total, view.parts)

    sealed class Change {
        /** Es gab noch keinen gespeicherten Stand. */
        object First : Change()
        object Same : Change()

        /** [parts] = Teilnamen (in fester Reihenfolge), deren Fingerabdruck sich geändert hat oder die neu/weggefallen sind. */
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

    /** Detail des Ereignisses: `sys:<Gesamt>` beim ersten Mal, `sys:<Gesamt>:<Teile>` bei einer Änderung. */
    fun eventDetail(view: View, change: Change): String = when (change) {
        is Change.Changed -> "sys:" + view.total + ":" + change.parts.joinToString(",") { it.id }
        else -> "sys:" + view.total
    }

    private val DETAIL = Regex("^sys:([0-9a-f]{8})(?::([a-z]+(?:,[a-z]+)*))?$")

    /** True, wenn [detail] genau diese Form hat (dann darf es in die Verlaufsdatei im Spielordner). */
    fun isSafeDetail(detail: String): Boolean = DETAIL.matches(detail)

    /** Gesamt-Fingerabdruck und Teilnamen aus einem Ereignisdetail. */
    fun parseDetail(detail: String): Pair<String, List<String>>? {
        val m = DETAIL.matchEntire(detail) ?: return null
        return m.groupValues[1] to m.groupValues[2].split(',').filter { it.isNotEmpty() }
    }

    // ---- Zustandsdatei (App-Speicher) --------------------------------------------------------------------------------------

    private const val HEADER = "systemsicht1"

    fun serialize(stored: Stored): String = buildString {
        append(HEADER).append('\n')
        append("total\t").append(stored.total).append('\n')
        for (p in stored.parts) {
            append("part\t").append(p.part.id).append('\t').append(p.fingerprint).append('\t').append(p.features.joinToString(";")).append('\n')
        }
    }

    private val HEX8 = Regex("^[0-9a-f]{8}$")

    /** Liest die Zustandsdatei; bei jedem Formfehler null (dann zählt die nächste Erfassung als erste). */
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
                    val features = cols.getOrElse(3) { "" }.split(';').filter { it.isNotEmpty() }.map(::cleanFeature)
                    parts += PartInfo(part, fp, features)
                }
            }
        }
        if (total == null || parts.isEmpty()) return null
        return Stored(total, parts)
    }

    // ---- Anzeige ----------------------------------------------------------------------------------------------------------

    /**
     * Was die Diagnose-Karte zeigt. [total] ist der aktuelle Gesamt-Fingerabdruck, [since] der Zeitpunkt des letzten Ereignisses
     * (erster Stand oder letzte Änderung), [changedParts]/[changedAt] die Teile der letzten Änderung, [changes] die Zahl der
     * Änderungen im Verlauf, [parts] der gespeicherte Stand (für die aufklappbaren Merkmale).
     */
    data class Summary(
        val total: String?,
        val since: Long?,
        val changedParts: List<String>,
        val changedAt: Long?,
        val changes: Int,
        val parts: List<PartInfo>,
    )

    /** Fasst die Ereignisse beider Verläufe zusammen; der Verlauf mit dem jüngsten Systemereignis gilt. */
    fun summarize(log: List<MachineIdentityLog.Entry>, history: List<MachineIdentityLog.Entry>, stored: Stored?): Summary {
        val a = log.filter { it.event.isSystem }
        val b = history.filter { it.event.isSystem }
        val events = if ((b.lastOrNull()?.time ?: -1L) > (a.lastOrNull()?.time ?: -1L)) b else a
        val last = events.lastOrNull()
        val lastParsed = last?.let { parseDetail(it.detail) }
        val lastChange = events.lastOrNull { it.event == MachineIdentityLog.Event.SYSTEM_CHANGED }
        return Summary(
            total = stored?.total ?: lastParsed?.first,
            since = last?.time,
            changedParts = lastChange?.let { parseDetail(it.detail)?.second }.orEmpty(),
            changedAt = lastChange?.time,
            changes = events.count { it.event == MachineIdentityLog.Event.SYSTEM_CHANGED },
            parts = stored?.parts.orEmpty(),
        )
    }
}
