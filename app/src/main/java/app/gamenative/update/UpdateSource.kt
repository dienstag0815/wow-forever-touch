package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.net.URI
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Kind of server a custom update source runs on; decides the releases API paths. */
enum class SourceKind(val key: String) {
    /** Gitea or a server with the same API: `https://host/api/v1/repos/{owner}/{repo}/releases`. */
    Gitea("gitea"),

    /** GitHub: `https://api.github.com/repos/{owner}/{repo}/releases` (other hosts as Enterprise: `https://host/api/v3/...`). */
    GitHub("github"),
    ;

    companion object {
        fun fromKey(key: String?): SourceKind = entries.firstOrNull { it.key == key } ?: Gitea
    }
}

/** A repository on a release server, parsed from `https://host/owner/repo`. Only https is accepted by [UpdateSourceLogic.parseRepo]. */
data class RepoRef(
    val kind: SourceKind,
    val scheme: String,
    /** Host with an optional `:port`. */
    val authority: String,
    val owner: String,
    val repo: String,
) {
    /** Authority of the API: GitHub's public API lives on its own host, everything else on the repository host. */
    val apiAuthority: String get() = if (kind == SourceKind.GitHub && authority.equals("github.com", ignoreCase = true)) "api.github.com" else authority

    /** Hosts that may receive the access token: the repository host and the API host. Nothing else, ever. */
    private val tokenAuthorities: Set<String> get() = setOf(authority.lowercase(), apiAuthority.lowercase())

    /** Where the repository would be opened in a browser (for the "active source" line). */
    val displayUrl: String get() = "$scheme://$authority/$owner/$repo"

    fun releasesUrl(limit: Int = RELEASE_LIMIT): String = when (kind) {
        SourceKind.Gitea -> "$scheme://$authority/api/v1/repos/$owner/$repo/releases?limit=$limit"
        SourceKind.GitHub ->
            if (apiAuthority == authority) {
                "$scheme://$authority/api/v3/repos/$owner/$repo/releases?per_page=$limit"
            } else {
                "$scheme://$apiAuthority/repos/$owner/$repo/releases?per_page=$limit"
            }
    }

    /** True when [url] is https (same scheme as the repository) and on a host that may receive the token. */
    fun isTokenTarget(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        val authorityOfUrl = if (uri.port >= 0) "$host:${uri.port}" else host
        return uri.scheme?.lowercase() == scheme && authorityOfUrl in tokenAuthorities
    }

    companion object {
        const val RELEASE_LIMIT = 20
    }
}

/** One file of a release. [apiUrl] is the API address of the asset (private GitHub repositories need it), may be empty. */
data class ReleaseAsset(
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val apiUrl: String,
    /** `sha256:<hex>` as some servers report it; empty when unknown. */
    val digest: String,
)

data class ReleaseEntry(
    val tag: String,
    val title: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val body: String,
    val assets: List<ReleaseAsset>,
)

sealed class RepoParse {
    data class Ok(val ref: RepoRef) : RepoParse()
    data class Invalid(val reason: UiText) : RepoParse()
}

sealed class SourceResult {
    /** [tag] is the release the update was derived from. */
    data class Found(val info: UpdateInfo, val tag: String) : SourceResult()
    data class Failed(val reason: UiText) : SourceResult()
}

/** The custom update source of the player (the address is validated, the token lives apart in the Keystore). */
data class CustomSource(val ref: RepoRef, val includePrereleases: Boolean)

/**
 * Pure logic of the custom update source (no Android classes): address check, releases JSON, choice of the release and its APK,
 * the update info derived from it, token rules (which host may see it, masking, redaction) and error texts.
 *
 * What a release needs: an APK asset and, recommended, a `latest.json` asset in the feed format of [UpdateLogic] (version,
 * versionCode, sha256, bytes, text). Without `latest.json` the version comes from the tag, the size from the asset, and the
 * checksum only when the server reports a `digest`; the version name then decides what is newer (no versionCode).
 */
object UpdateSourceLogic {
    private val SEGMENT = Regex("^[A-Za-z0-9._-]+$")
    private val DIGEST = Regex("^sha256:([0-9a-fA-F]{64})$")

    /** `https://host/owner/repo` (also with `.git` or a trailing slash). Anything but https, credentials in the address, query or other paths are refused. */
    fun parseRepo(kind: SourceKind, input: String): RepoParse {
        val text = input.trim()
        if (text.isEmpty()) return RepoParse.Invalid(UiText.res(R.string.launcher_source_err_malformed))
        if (!text.startsWith("https://", ignoreCase = true)) {
            return RepoParse.Invalid(UiText.res(if (text.contains("://")) R.string.launcher_source_err_not_https else R.string.launcher_source_err_malformed))
        }
        val uri = try {
            URI(text)
        } catch (e: Exception) {
            return RepoParse.Invalid(UiText.res(R.string.launcher_source_err_malformed))
        }
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty() || uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            return RepoParse.Invalid(UiText.res(R.string.launcher_source_err_malformed))
        }
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }.toMutableList()
        if (segments.size != 2) return RepoParse.Invalid(UiText.res(R.string.launcher_source_err_malformed))
        segments[1] = segments[1].removeSuffix(".git")
        if (segments.any { !SEGMENT.matches(it) || it == "." || it == ".." }) {
            return RepoParse.Invalid(UiText.res(R.string.launcher_source_err_malformed))
        }
        val authority = if (uri.port >= 0) "$host:${uri.port}" else host
        return RepoParse.Ok(RepoRef(kind, "https", authority, segments[0], segments[1]))
    }

    /** Release list of the API; null when the answer is not a JSON array (error page, wrong server kind). */
    fun parseReleases(json: String?): List<ReleaseEntry>? {
        if (json.isNullOrBlank()) return null
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            return null
        }
        return (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            val assets = o.optJSONArray("assets")
            ReleaseEntry(
                tag = o.optString("tag_name", "").trim(),
                title = o.optString("name", "").trim(),
                draft = o.optBoolean("draft", false),
                prerelease = o.optBoolean("prerelease", false),
                body = o.optString("body", "").trim(),
                assets = (0 until (assets?.length() ?: 0)).mapNotNull { a ->
                    val asset = assets?.optJSONObject(a) ?: return@mapNotNull null
                    val name = asset.optString("name", "").trim()
                    if (name.isEmpty()) return@mapNotNull null
                    ReleaseAsset(
                        name = name,
                        size = asset.optLong("size", 0L),
                        downloadUrl = asset.optString("browser_download_url", "").trim(),
                        apiUrl = asset.optString("url", "").trim(),
                        digest = asset.optString("digest", "").trim(),
                    )
                },
            )
        }
    }

    fun isApk(asset: ReleaseAsset): Boolean = asset.name.endsWith(".apk", ignoreCase = true)

    fun feedAsset(release: ReleaseEntry): ReleaseAsset? = release.assets.firstOrNull { it.name.equals(UpdateLogic.FEED_FILE, ignoreCase = true) }

    /** The newest usable release: the API lists newest first; drafts never, pre-releases only when asked for, and it needs an APK. */
    fun pickRelease(releases: List<ReleaseEntry>, includePrereleases: Boolean): ReleaseEntry? =
        releases.firstOrNull { !it.draft && (includePrereleases || !it.prerelease) && it.tag.isNotEmpty() && it.assets.any(::isApk) }

    /**
     * Update info of [release]. With [feedJson] (the content of its `latest.json`) the feed decides version, checksum, size and
     * notes, and the APK asset is the one the feed names (by `datei` or the end of `url`), else the first APK. Without it the
     * info comes from tag, asset and server `digest`. The download address always comes from the release's own asset.
     */
    fun infoFor(ref: RepoRef, release: ReleaseEntry, feedJson: String?, hasToken: Boolean): SourceResult {
        val apks = release.assets.filter(::isApk)
        if (apks.isEmpty()) return SourceResult.Failed(UiText.res(R.string.launcher_source_err_no_release))
        if (feedJson != null) {
            return when (val parsed = UpdateLogic.parseFeed(feedJson)) {
                is FeedResult.Invalid -> SourceResult.Failed(UiText.res(R.string.launcher_update_feed_unusable, parsed.reason))
                is FeedResult.Ok -> {
                    val wanted = parsed.info.file.ifEmpty { parsed.info.url.substringBefore('?').substringAfterLast('/') }
                    val apk = apks.firstOrNull { it.name == wanted } ?: apks.first()
                    val url = downloadUrl(ref, apk, hasToken) ?: return SourceResult.Failed(UiText.res(R.string.launcher_update_apk_address_unusable))
                    SourceResult.Found(
                        parsed.info.copy(file = apk.name, url = url, notes = parsed.info.notes.ifEmpty { release.body }, tag = release.tag),
                        release.tag,
                    )
                }
            }
        }
        val apk = apks.first()
        if (apk.size <= 0L) return SourceResult.Failed(UiText.res(R.string.launcher_source_err_no_release))
        val url = downloadUrl(ref, apk, hasToken) ?: return SourceResult.Failed(UiText.res(R.string.launcher_update_apk_address_unusable))
        val sha = DIGEST.matchEntire(apk.digest)?.groupValues?.get(1)?.lowercase().orEmpty()
        val name = release.tag.trim().trimStart('v', 'V')
        return SourceResult.Found(UpdateInfo(0, name, apk.name, url, sha, apk.size, release.body, release.tag), release.tag)
    }

    /**
     * Address to load [asset] from. GitHub's `browser_download_url` of a private repository needs a browser session, so with a
     * token the API address of the asset is used (loaded with `Accept: application/octet-stream`); otherwise the public one.
     * Null when no address with the repository's scheme (https) is available.
     */
    fun downloadUrl(ref: RepoRef, asset: ReleaseAsset, hasToken: Boolean): String? {
        val candidate = if (hasToken && ref.kind == SourceKind.GitHub && asset.apiUrl.isNotEmpty()) asset.apiUrl else asset.downloadUrl.ifEmpty { asset.apiUrl }
        return candidate.takeIf { hasScheme(it, ref.scheme) }
    }

    /** [url] has the repository's scheme (always https in the app, [parseRepo] allows nothing else) and a host. */
    private fun hasScheme(url: String, scheme: String): Boolean = try {
        val uri = URI(url)
        uri.scheme.equals(scheme, ignoreCase = true) && !uri.host.isNullOrEmpty()
    } catch (e: Exception) {
        false
    }

    /**
     * Request headers for [url]. The token is added only for https addresses on the repository's own hosts (so a `latest.json`
     * or a redirect can never send it elsewhere); [wantFile] asks a GitHub API asset address for the file itself.
     */
    fun requestHeaders(ref: RepoRef, token: String?, url: String, wantFile: Boolean): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        headers["Accept"] = if (wantFile && ref.kind == SourceKind.GitHub && url.contains("/releases/assets/")) "application/octet-stream" else "application/json"
        if (!token.isNullOrBlank() && ref.isTokenTarget(url)) headers["Authorization"] = "token ${token.trim()}"
        return headers
    }

    /** Failure text of an HTTP answer from the releases API or an asset. */
    fun httpFailure(code: Int): UiText = when (code) {
        401 -> UiText.res(R.string.launcher_source_err_401)
        403 -> UiText.res(R.string.launcher_source_err_403)
        404 -> UiText.res(R.string.launcher_source_err_404)
        else -> UiText.res(R.string.launcher_update_http_error, code)
    }

    /** The feed JSON stored for a custom source (the cache of the last check); same format as `latest.json`, the checksum may be empty. */
    fun toFeedJson(info: UpdateInfo): String = JSONObject()
        .put("version", info.versionName)
        .put("datei", info.file)
        .put("url", info.url)
        .put("sha256", info.sha256)
        .put("bytes", info.size)
        .put("text", info.notes)
        .put("versionCode", info.versionCode)
        .toString()

    /** Dots instead of the token: never more than 12, nothing for an empty token. Nothing of the token itself is shown. */
    fun maskToken(token: String?): String = if (token.isNullOrEmpty()) "" else "•".repeat(token.length.coerceIn(1, 12))

    /** [text] with every occurrence of [token] replaced, for messages that could echo a request (log lines, error texts). */
    fun redact(text: String, token: String?): String =
        if (token.isNullOrBlank()) text else text.replace(token.trim(), "***")

    /** Android refused the update because the signature differs from the installed app (other key than the installed build). */
    fun isSignatureMismatch(message: String): Boolean {
        val m = message.uppercase()
        return "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in m ||
            "INCONSISTENT_CERTIFICATES" in m ||
            "SIGNATURES DO NOT MATCH" in m ||
            ("SIGNATURE" in m && "MISMATCH" in m)
    }
}
