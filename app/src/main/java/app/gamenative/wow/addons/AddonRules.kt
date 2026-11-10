package app.gamenative.wow.addons

import java.net.URI
import java.net.URISyntaxException

/**
 * Feste Liste der Hosts, von denen der Addon-Manager laden darf (Auftrag addons, 08.11.2026). Gilt für JEDEN Schritt,
 * auch für Weiterleitungen: jeder Hop wird einzeln geprüft ([AddonFetch]). Nur HTTPS, Port 443, keine Zugangsdaten in der URL.
 *
 * `release-assets.githubusercontent.com` ist ergänzt: GitHub leitet Release-Downloads seit 2025 dorthin um
 * (am 08.11.2026 an Questie/HandyNotes beobachtet), `objects.githubusercontent.com` ist der ältere Weg.
 */
object AddonHosts {
    val GITHUB: Set<String> = setOf(
        "github.com",
        "api.github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    val WOWINTERFACE: Set<String> = setOf(
        "wowinterface.com",
        "www.wowinterface.com",
        "cdn.wowinterface.com",
        "api.mmoui.com",
    )

    val ALL: Set<String> = GITHUB + WOWINTERFACE

    /** Zusätzlicher Host nur für eigene Addons: der Quellcode-Download (zipball) wird dorthin umgeleitet. */
    const val CODELOAD = "codeload.github.com"

    /** Marker für Links, die der Nutzer selbst eingibt: jeder öffentliche https-Host (siehe [LinkHosts.isPublicName]). */
    val ANY_PUBLIC_HTTPS: Set<String> = setOf("*")

    /** Die Hosts, die für Einträge dieser [source] gelten (GitHub-Einträge dürfen nie zu WoWInterface umleiten und umgekehrt). */
    fun forSource(source: AddonSource): Set<String> = when (source) {
        is AddonSource.GitHub -> GITHUB
        is AddonSource.WowInterface -> WOWINTERFACE
    }

    fun isAllowed(url: String?, allowed: Set<String> = ALL): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = try {
            URI(url.trim())
        } catch (e: URISyntaxException) {
            return false
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        if (uri.userInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        if ("*" in allowed) return LinkHosts.isPublicName(host)
        return host in allowed
    }
}

/** Versionsvergleich für Release-Tags wie `v12.6.4`, `bundle/v12.6.4+v1.0.12`, `v2026.41`. */
object AddonVersion {
    private val NUMBER = Regex("[0-9]+")

    /** Alle Zahlengruppen des Tags in Reihenfolge, null wenn der Tag keine Zahl enthält. */
    fun parts(tag: String): List<Long>? {
        val numbers = NUMBER.findAll(tag).map { it.value.toLongOrNull() ?: Long.MAX_VALUE }.toList()
        return numbers.ifEmpty { null }
    }

    /** Negativ, 0, positiv wie compareTo; fehlende Stellen zählen als 0 (`1.0` == `1`). Null, wenn ein Tag keine Zahl hat. */
    fun compare(a: String, b: String): Int? {
        val pa = parts(a) ?: return null
        val pb = parts(b) ?: return null
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = pa.getOrElse(i) { 0L }.compareTo(pb.getOrElse(i) { 0L })
            if (c != 0) return c
        }
        return 0
    }

    /**
     * Gibt es [latest] als Update für das installierte [installed]? Ohne installierte Version immer ja. Zahlen werden
     * verglichen; fehlen sie, zählt jeder Unterschied der Texte als Update.
     */
    fun isUpdate(installed: String?, latest: String): Boolean {
        if (installed.isNullOrBlank()) return true
        if (installed == latest) return false
        val c = compare(installed, latest) ?: return true
        return c < 0
    }

    /** Anzeige: ohne Pfadteil davor (`bundle/v12.6.4+v1.0.12` -> `v12.6.4+v1.0.12`). */
    fun display(tag: String): String = tag.substringAfterLast('/').ifBlank { tag }
}
