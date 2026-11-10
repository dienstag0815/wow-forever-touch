package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.net.URI
import java.net.URISyntaxException

/*
 * Eigene Addons per Link (Auftrag addonlink, 11.11.2026): was ein eingefügter Link bedeutet, als reine Funktion ohne Netz
 * (läuft in JUnit). Das Laden selbst macht [CustomAddonService]. Nur https; jede Weiterleitung prüft [AddonFetch] einzeln.
 */

/** Was [resolveAddonLink] aus einem Link macht. */
sealed class AddonLink {
    /** Direkter Link auf eine .zip-Datei (beliebiger öffentlicher https-Host). */
    data class DirectZip(val url: String) : AddonLink()

    /** GitHub-Repository; [tag] gesetzt, wenn der Link auf ein bestimmtes Release zeigt. */
    data class GitHubRepo(val owner: String, val repo: String, val tag: String?) : AddonLink() {
        val slug: String get() = "$owner/$repo"
    }

    /** Datei auf wowinterface.com. */
    data class WowInterface(val fileId: String) : AddonLink()

    /** CurseForge oder Wago: braucht einen API-Schlüssel, geht nur über den Browser. [pageUrl] ist die Seite zum Öffnen. */
    data class NeedsBrowser(val site: String, val pageUrl: String) : AddonLink() {
        val message: UiText get() = AddonLinkTexts.needsBrowser(site)
    }

    /** Nicht unterstützt; [reason] ist die Meldung für die Anzeige. */
    data class Unsupported(val reason: UiText) : AddonLink()
}

object AddonLinkTexts {
    val SUPPORTED: UiText = UiText.res(R.string.launcher_addon_link_supported)

    fun needsBrowser(site: String): UiText = UiText.res(R.string.launcher_addon_link_needs_browser, site)

    val EMPTY: UiText = UiText.res(R.string.launcher_addon_link_empty, SUPPORTED)
    val NOT_HTTPS: UiText = UiText.res(R.string.launcher_addon_link_not_https)
}

/** Hilfen für Hostnamen von Links, die der Nutzer selbst eingibt. */
object LinkHosts {
    /** Öffentlicher Name: mit Punkt, keine IP-Adresse, kein localhost, keine lokalen Endungen. */
    fun isPublicName(host: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        if (h.isEmpty() || '.' !in h || ':' in h || h.startsWith("[")) return false
        if (h.all { it.isDigit() || it == '.' }) return false
        val local = listOf(".localhost", ".local", ".internal", ".lan", ".home", ".intranet")
        return local.none { h.endsWith(it) }
    }
}

private val GITHUB_HOSTS = setOf("github.com", "www.github.com")
private val WOWI_HOSTS = setOf("wowinterface.com", "www.wowinterface.com", "cdn.wowinterface.com")
private val GITHUB_NOT_OWNERS = setOf(
    "orgs", "settings", "topics", "marketplace", "sponsors", "features", "about", "explore", "notifications", "login", "search",
    "pricing", "enterprise", "collections", "trending", "apps", "users", "organizations", "join", "signup", "pulls", "issues",
)
private val NAME = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,99}$")
private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
private val WOWI_PATH = Regex("^/downloads/(?:info|download)([0-9]{1,9})(?:[^0-9].*)?$", RegexOption.IGNORE_CASE)
private val QUERY_ID = Regex("(?:^|&)id=([0-9]{1,9})(?:&|$)")

/** Deutet [input] (Text aus dem Eingabefeld) als Link. Keine Netzzugriffe. */
fun resolveAddonLink(input: String?): AddonLink {
    var text = input.orEmpty().trim().trim('"', '\'', '<', '>').trim()
    if (text.isEmpty()) return AddonLink.Unsupported(AddonLinkTexts.EMPTY)
    if (text.any { it.isWhitespace() }) return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_has_spaces, AddonLinkTexts.SUPPORTED))
    if (SCHEME.containsMatchIn(text)) {
        if (!text.startsWith("https://", ignoreCase = true)) return AddonLink.Unsupported(AddonLinkTexts.NOT_HTTPS)
    } else if ('.' in text.substringBefore('/')) {
        text = "https://$text"
    } else {
        return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_not_a_link, AddonLinkTexts.SUPPORTED))
    }
    val uri = try {
        URI(text)
    } catch (e: URISyntaxException) {
        return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_invalid, AddonLinkTexts.SUPPORTED))
    }
    val host = uri.host?.lowercase()?.trimEnd('.')
        ?: return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_invalid, AddonLinkTexts.SUPPORTED))
    if (uri.userInfo != null) return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_userinfo))
    if (uri.port != -1 && uri.port != 443) return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_port))
    val path = uri.path.orEmpty()
    val segments = path.split('/').filter { it.isNotEmpty() }
    val clean = cleanUrl(uri)

    if (host == "curseforge.com" || host.endsWith(".curseforge.com")) return AddonLink.NeedsBrowser("CurseForge", clean)
    if (host == "wago.io" || host.endsWith(".wago.io")) return AddonLink.NeedsBrowser("Wago", clean)

    if (host in GITHUB_HOSTS) return resolveGitHub(segments, clean)
    if (host == "api.github.com") {
        val isZipball = segments.size >= 4 && segments[0] == "repos" && segments[3] == "zipball"
        return if (isZipball) AddonLink.DirectZip(clean) else AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_github_unknown, AddonLinkTexts.SUPPORTED))
    }
    if (host in WOWI_HOSTS) return resolveWowInterface(host, path, uri.query.orEmpty(), clean)

    if (path.endsWith(".zip", ignoreCase = true)) {
        return if (LinkHosts.isPublicName(host)) AddonLink.DirectZip(clean) else AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_untrusted))
    }
    return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_cannot_load, AddonLinkTexts.SUPPORTED))
}

private fun cleanUrl(uri: URI): String {
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    val host = uri.host.lowercase()
    return "https://$host${uri.rawPath.orEmpty()}$query"
}

private fun resolveGitHub(segments: List<String>, clean: String): AddonLink {
    if (segments.size < 2) return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_not_repo))
    val owner = segments[0]
    val repo = segments[1].removeSuffix(".git")
    if (owner.lowercase() in GITHUB_NOT_OWNERS || !NAME.matches(owner) || !NAME.matches(repo)) {
        return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_not_repo))
    }
    val third = segments.getOrNull(2)
    val last = segments.last()
    if (third == "releases" && segments.getOrNull(3) == "download" && last.endsWith(".zip", ignoreCase = true)) {
        return AddonLink.DirectZip(clean)
    }
    if (third == "archive" && last.endsWith(".zip", ignoreCase = true)) return AddonLink.DirectZip(clean)
    if (third == "releases" && segments.getOrNull(3) == "tag" && segments.size >= 5) {
        return AddonLink.GitHubRepo(owner, repo, segments.drop(4).joinToString("/"))
    }
    return AddonLink.GitHubRepo(owner, repo, null)
}

private fun resolveWowInterface(host: String, path: String, query: String, clean: String): AddonLink {
    WOWI_PATH.matchEntire(path)?.let { return AddonLink.WowInterface(it.groupValues[1]) }
    val lower = path.lowercase()
    if (lower.endsWith("/downloads/fileinfo.php") || lower.endsWith("/downloads/getfile.php")) {
        QUERY_ID.find(query)?.let { return AddonLink.WowInterface(it.groupValues[1]) }
    }
    if (lower.endsWith(".zip") && host == "cdn.wowinterface.com") return AddonLink.DirectZip(clean)
    return AddonLink.Unsupported(UiText.res(R.string.launcher_addon_link_wowi_not_download))
}
