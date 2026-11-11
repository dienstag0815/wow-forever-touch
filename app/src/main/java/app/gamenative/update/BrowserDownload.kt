package app.gamenative.update

import java.net.URI
import java.net.URLEncoder

/**
 * The manual way to get an update: open the release in the browser (ACTION_VIEW). The app then needs neither storage access nor the
 * right to install apps; the browser saves the APK in Downloads and Android asks by itself when it is tapped.
 *
 * Which address: a public source (official feed, or a custom source without token that is not a tester source) opens the APK itself.
 * A tester or private source cannot hand its token to the browser, so the release page opens and the player signs in there. The token is
 * never part of an address; addresses are built from the repository reference and the tag only.
 */
object BrowserDownload {
    sealed interface Target {
        val url: String

        /** The APK itself. */
        data class Apk(override val url: String) : Target

        /** The release page; the browser asks for the sign-in. */
        data class ReleasePage(override val url: String) : Target
    }

    /**
     * The address to open for [info], or null when there is no safe https address.
     * [custom] null = official feed, whose resolved APK address is [officialApkUrl]. [tester] and [token] mark a source as not public.
     */
    fun choose(info: UpdateInfo, custom: RepoRef?, tester: Boolean, token: String?, officialApkUrl: String?): Target? {
        val secret = !token.isNullOrBlank()
        if (custom == null) return officialApkUrl?.let(::httpsOrNull)?.let { Target.Apk(it) }?.takeIf { clean(it.url, token) }
        if (secret || tester) {
            val page = custom.releasePageUrl(info.tag)
            return Target.ReleasePage(page).takeIf { clean(page, token) }
        }
        return httpsOrNull(info.url)?.let { Target.Apk(it) }?.takeIf { clean(it.url, token) }
    }

    /** The release page of [tag] on [ref] (for the version list; the player signs in at the browser). */
    fun releasePage(ref: RepoRef, tag: String): Target.ReleasePage = Target.ReleasePage(ref.releasePageUrl(tag))

    /** The release list page when a release has no known tag (an offer restored from the cache). */
    private fun RepoRef.releasePageUrl(tag: String): String =
        if (tag.isBlank()) "$displayUrl/releases" else "$displayUrl/releases/tag/${URLEncoder.encode(tag.trim(), "UTF-8").replace("+", "%20")}"

    private fun httpsOrNull(url: String): String? = try {
        val uri = URI(url.trim())
        url.trim().takeIf { uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty() && uri.userInfo == null }
    } catch (e: Exception) {
        null
    }

    /** No token, and no credentials-looking query, in the address. */
    private fun clean(url: String, token: String?): Boolean {
        if (!token.isNullOrBlank() && url.contains(token.trim())) return false
        val query = try {
            URI(url).rawQuery.orEmpty().lowercase()
        } catch (e: Exception) {
            return false
        }
        return listOf("token=", "access_token", "authorization").none { it in query }
    }
}
