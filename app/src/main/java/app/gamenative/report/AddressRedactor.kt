package app.gamenative.report

/**
 * Ersetzt Netzwerkadressen in jedem Text des Berichts (reine Kotlin-Logik, JUnit-getestet): IPv4 -> [IP], IPv6 (volle und gekürzte
 * Formen, mit eingebettetem IPv4-Teil, Zonenkennung und in eckigen Klammern mit Port) -> [IP6]. Hostnamen bleiben lesbar.
 *
 * Nicht getroffen werden sollen Versionsnummern (`1.60.1.70938`: ein Teil über 255; `2.3.3`: nur drei Teile), Zeitstempel
 * (`11/14 19:58:12.255`, `19:58:12`: nur drei Gruppen ohne `::`) und Build-Nummern. Eine vierteilige Versionsnummer mit lauter Teilen bis 255
 * (z. B. `1.2.3.4`) ist von einer Adresse nicht zu unterscheiden und wird ersetzt.
 */
object AddressRedactor {
    const val IPV4 = "[IP]"
    const val IPV6 = "[IP6]"

    private const val OCTET = """(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)"""

    /** Vier Teile bis 255, nicht Teil einer längeren Zahlen-/Punktfolge und nicht an einem Wort hängend. */
    private val V4 = Regex("""(?<![\w.])(?:$OCTET\.){3}$OCTET(?![\w]|\.\d)""")

    private val V4_STRICT = Regex("""^(?:$OCTET\.){3}$OCTET$""")

    /**
     * Ein Kandidat für IPv6: Hex-Gruppen mit mindestens zwei Doppelpunkten, optional ein IPv4-Teil, eine Zonenkennung und eckige Klammern.
     * Die Prüfung, ob es wirklich eine Adresse ist, macht [isIpv6] (so bleiben Zeitstempel wie `19:58:12` unberührt).
     */
    private val V6_CANDIDATE = Regex(
        """(?<![\w:.\[])(\[)?([0-9A-Fa-f]*+(?::[0-9A-Fa-f]*+){2,}+)((?:\.\d{1,3}){3})?(%[0-9A-Za-z_.\-]{1,20})?(\])?(?!\w|\.\d)""",
    )

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        if (out.indexOf(':') >= 0) out = redactV6(out)
        if (out.indexOf('.') >= 0) out = V4.replace(out, IPV4)
        return out
    }

    private fun redactV6(text: String): String = V6_CANDIDATE.replace(text) { m ->
        val open = m.groupValues[1]
        var body = m.groupValues[2]
        val v4tail = m.groupValues[3]
        val zone = m.groupValues[4]
        val close = m.groupValues[5]
        // Ein einzelner Doppelpunkt am Ende gehört zum Satz ("... 1:2:3:4:5:6:7:8: weiter"), nicht zur Adresse.
        var trailing = ""
        if (v4tail.isEmpty() && body.endsWith(":") && !body.endsWith("::")) {
            body = body.dropLast(1)
            trailing = ":"
        }
        val address = body + v4tail
        // Klammern nur ersetzen, wenn beide da sind oder keine: sonst bleiben sie stehen.
        val bracketsOk = open.isEmpty() == close.isEmpty()
        if (isIpv6(address) && bracketsOk && address.any { it.isDigit() }) IPV6 + trailing else m.value
    }

    /** Ob [address] (ohne Klammern und Zonenkennung) eine gültige IPv6-Adresse ist, auch mit IPv4-Teil am Ende. */
    fun isIpv6(address: String): Boolean {
        if (address.count { it == ':' } < 2) return false
        var body = address
        val lastColon = body.lastIndexOf(':')
        val last = body.substring(lastColon + 1)
        if (last.contains('.')) {
            if (!V4_STRICT.matches(last)) return false
            body = body.substring(0, lastColon + 1) + "0:0"
        }
        val double = body.indexOf("::")
        if (double >= 0 && body.indexOf("::", double + 1) >= 0) return false
        fun groupsOk(part: String): Int? {
            if (part.isEmpty()) return 0
            val groups = part.split(':')
            return if (groups.all { it.length in 1..4 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }) groups.size else null
        }
        if (double < 0) return groupsOk(body) == 8
        val head = groupsOk(body.substring(0, double)) ?: return false
        val tail = groupsOk(body.substring(double + 2)) ?: return false
        return head + tail <= 7
    }
}
