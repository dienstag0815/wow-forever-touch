package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

sealed class ReleaseListResult {
    data class Ok(val releases: List<ReleaseEntry>) : ReleaseListResult()
    data class Failed(val reason: UiText) : ReleaseListResult()
}

/**
 * Reads the releases of a custom update source through the releases API (Gitea or GitHub): the newest usable one as an
 * [UpdateInfo] ([fetch]), the whole list ([list]) and the info of one chosen release ([infoFor]), see [UpdateSourceLogic]. Blocking; call it off the main thread. [http] should not follow https-to-http redirects
 * (see [forSource]); the token goes only to the repository's own hosts ([RepoRef.isTokenTarget]) and never appears in a text.
 */
class ReleaseFetcher(private val http: OkHttpClient, private val userAgent: String) {

    fun fetch(source: CustomSource, token: String?): SourceResult {
        return when (val listed = list(source, token)) {
            is ReleaseListResult.Failed -> SourceResult.Failed(listed.reason)
            is ReleaseListResult.Ok -> {
                val release = UpdateSourceLogic.pickRelease(listed.releases, source.includePrereleases)
                    ?: return SourceResult.Failed(UiText.res(R.string.launcher_source_err_no_release))
                infoFor(source, token, release)
            }
        }
    }

    /** The releases the server lists (newest first, as it sends them), unfiltered. */
    fun list(source: CustomSource, token: String?): ReleaseListResult {
        return try {
            val body = get(source.ref, token, source.ref.releasesUrl(), wantFile = false, maxBytes = MAX_LIST_BYTES)
            UpdateSourceLogic.parseReleases(body)?.let { ReleaseListResult.Ok(it) }
                ?: ReleaseListResult.Failed(UiText.res(R.string.launcher_source_err_bad_json))
        } catch (e: HttpFailure) {
            ReleaseListResult.Failed(UpdateSourceLogic.httpFailure(e.code))
        } catch (e: IOException) {
            ReleaseListResult.Failed(UiText.raw(UpdateSourceLogic.redact(e.message ?: e.javaClass.simpleName, token)))
        }
    }

    /** The update info of one [release] (its `latest.json` is read when it has one). */
    fun infoFor(source: CustomSource, token: String?, release: ReleaseEntry): SourceResult {
        val ref = source.ref
        val hasToken = !token.isNullOrBlank()
        return try {
            val feedAsset = UpdateSourceLogic.feedAsset(release)
            val feedJson = feedAsset?.let { asset ->
                val url = UpdateSourceLogic.downloadUrl(ref, asset, hasToken)
                    ?: return SourceResult.Failed(UiText.res(R.string.launcher_update_apk_address_unusable))
                get(ref, token, url, wantFile = true, maxBytes = MAX_FEED_BYTES)
            }
            UpdateSourceLogic.infoFor(ref, release, feedJson, hasToken)
        } catch (e: HttpFailure) {
            SourceResult.Failed(UpdateSourceLogic.httpFailure(e.code))
        } catch (e: IOException) {
            SourceResult.Failed(UiText.raw(UpdateSourceLogic.redact(e.message ?: e.javaClass.simpleName, token)))
        }
    }

    private class HttpFailure(val code: Int) : IOException("HTTP $code")

    private fun get(ref: RepoRef, token: String?, url: String, wantFile: Boolean, maxBytes: Long): String {
        val builder = Request.Builder().url(url).header("User-Agent", userAgent)
        UpdateSourceLogic.requestHeaders(ref, token, url, wantFile).forEach { (name, value) -> builder.header(name, value) }
        return http.newCall(builder.build()).execute().use { rsp ->
            if (!rsp.isSuccessful) throw HttpFailure(rsp.code)
            rsp.peekBody(maxBytes).string()
        }
    }

    companion object {
        private const val MAX_LIST_BYTES = 2L * 1024 * 1024
        private const val MAX_FEED_BYTES = 64L * 1024

        /** The client for a custom source: [base] without redirects from https to http, so a token or file never travels in clear text. */
        fun forSource(base: OkHttpClient): OkHttpClient = base.newBuilder().followSslRedirects(false).build()
    }
}
