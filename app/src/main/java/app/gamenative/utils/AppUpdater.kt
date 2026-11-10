package app.gamenative.utils

import app.gamenative.BuildConfig
import org.json.JSONObject

/**
 * Release parsing for the upstream project's GitHub releases. This upstream path stays switched off: this fork is
 * signed with a different key, so an upstream APK could never be installed over it. The fork's own self-update
 * (own download page, SHA-256 check, PackageInstaller) lives in the package `app.gamenative.update`.
 */
object AppUpdater {
    data class Release(
        val version: String,
        val name: String,
        val notes: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    fun isNewer(latest: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        val parse = { v: String ->
            v.trimStart('v', 'V')
                .split('-', '_', '+')[0]
                .split('.')
                .mapNotNull { it.toIntOrNull() }
        }
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    fun parseRelease(jsonString: String, currentVersion: String = BuildConfig.VERSION_NAME): Release? = runCatching {
        val json = JSONObject(jsonString)
        val tag = json.optString("tag_name")
        if (!isNewer(tag, currentVersion)) return null

        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                return Release(
                    version = tag.trimStart('v', 'V'),
                    name = json.optString("name", tag),
                    notes = json.optString("body"),
                    downloadUrl = url,
                    sizeBytes = asset.optLong("size", 0L),
                )
            }
        }
        null
    }.getOrNull()
}
