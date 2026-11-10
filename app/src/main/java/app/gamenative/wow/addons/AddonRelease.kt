package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import org.json.JSONException
import org.json.JSONObject

/*
 * Release-Metadaten lesen und die richtige Datei wählen (reine Logik, ohne Netz).
 * Ansatz der Asset-Wahl nach WowUp / WoW Foreverywhere (GitHubProvider.kt, GPL-3.0): die BigWigs-Packager-Datei release.json
 * nennt je Archiv die Spielversionen. Anders als dort wird hier NICHT geraten: ohne ausdrückliches "forever" kein Archiv.
 */

/** Eine Datei eines GitHub-Releases. */
data class ReleaseAsset(val name: String, val url: String, val size: Long, val contentType: String)

data class GitHubRelease(
    val tag: String,
    val publishedAt: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
) {
    /** Die Packager-Datei mit den Spielversionen je Archiv. */
    val metaAsset: ReleaseAsset? get() = assets.firstOrNull { it.name == AddonAssetPicker.META_NAME }
}

/** Die gewählte Datei samt Release-Angaben. */
data class PickedRelease(val tag: String, val publishedAt: String?, val asset: ReleaseAsset, val md5: String? = null)

sealed class PickResult {
    data class Ok(val picked: PickedRelease) : PickResult()
    data class Fail(val reason: UiText) : PickResult()
}

object GitHubReleaseParser {
    /** Antwort von `GET /repos/{owner}/{repo}/releases/latest`; null bei ungültigem Inhalt. */
    fun parse(json: String?): GitHubRelease? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            val tag = o.optString("tag_name", "").trim()
            if (tag.isEmpty()) return null
            val list = o.optJSONArray("assets")
            val assets = if (list == null) emptyList() else (0 until list.length()).mapNotNull { i ->
                val a = list.optJSONObject(i) ?: return@mapNotNull null
                val name = a.optString("name", "")
                val url = a.optString("browser_download_url", "")
                if (name.isEmpty() || url.isEmpty()) return@mapNotNull null
                ReleaseAsset(name, url, a.optLong("size", 0L), a.optString("content_type", ""))
            }
            GitHubRelease(
                tag = tag,
                publishedAt = o.optString("published_at", "").ifEmpty { null },
                draft = o.optBoolean("draft", false),
                prerelease = o.optBoolean("prerelease", false),
                assets = assets,
            )
        } catch (e: JSONException) {
            null
        }
    }
}

object AddonAssetPicker {
    const val META_NAME = "release.json"
    const val FOREVER_FLAVOR = "forever"

    /** Interface-Nummern der Forever-Reihe (16001 laut TouchForever.toc und release.json von Questie/BigWigs/HandyNotes). */
    val FOREVER_INTERFACES = 16000..16999

    private val ZIP_TYPES = setOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

    /**
     * Welche Archive nennt release.json für Forever? Namen mit Forever-Eintrag der passenden Interface-Reihe; Archive mit
     * `nolib` bleiben draußen. Null, wenn der Text kein gültiges release.json ist.
     */
    fun foreverFiles(metaText: String?): Set<String>? {
        if (metaText.isNullOrBlank()) return null
        return try {
            val releases = JSONObject(metaText).optJSONArray("releases") ?: return null
            val result = linkedSetOf<String>()
            for (i in 0 until releases.length()) {
                val r = releases.optJSONObject(i) ?: continue
                if (r.optBoolean("nolib", false)) continue
                val file = r.optString("filename", "")
                val meta = r.optJSONArray("metadata") ?: continue
                val forever = (0 until meta.length()).any { j ->
                    val m = meta.optJSONObject(j)
                    m != null && m.optString("flavor", "").equals(FOREVER_FLAVOR, ignoreCase = true) &&
                        m.optInt("interface", 0) in FOREVER_INTERFACES
                }
                if (forever && file.isNotEmpty()) result += file
            }
            result
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * Wählt das Archiv. Reihenfolge der Prüfungen: Release endgültig -> Name passt zum Katalogmuster (und ist ein Zip ohne
     * `nolib`, von erlaubtem Host) -> bei [AddonSource.GitHub.requireForever] nennt release.json das Archiv für Forever ->
     * Größe im Limit. Jede Absage hat einen Grund ([UiText]) für die Anzeige.
     */
    fun pick(entry: CatalogEntry, source: AddonSource.GitHub, release: GitHubRelease, metaText: String?): PickResult {
        if (release.draft || release.prerelease) return PickResult.Fail(UiText.res(R.string.launcher_addon_pick_prerelease))
        val candidates = release.assets.filter { a ->
            a.name != META_NAME &&
                a.name.endsWith(".zip", ignoreCase = true) &&
                !a.name.contains("nolib", ignoreCase = true) &&
                source.matchesAsset(a.name) &&
                (a.contentType.isEmpty() || a.contentType.lowercase() in ZIP_TYPES) &&
                AddonHosts.isAllowed(a.url, AddonHosts.GITHUB)
        }
        if (candidates.isEmpty()) return PickResult.Fail(UiText.res(R.string.launcher_addon_pick_no_archive, release.tag))

        val chosen = if (source.requireForever) {
            val forever = foreverFiles(metaText)
                ?: return PickResult.Fail(UiText.res(R.string.launcher_addon_pick_no_release_json, release.tag))
            candidates.filter { it.name in forever }.minByOrNull { it.name }
                ?: return PickResult.Fail(UiText.res(R.string.launcher_addon_pick_no_forever, release.tag))
        } else {
            candidates.singleOrNull()
                ?: return PickResult.Fail(UiText.res(R.string.launcher_addon_pick_ambiguous, release.tag))
        }
        if (chosen.size <= 0L) return PickResult.Fail(UiText.res(R.string.launcher_addon_pick_size_unknown, chosen.name))
        if (chosen.size > entry.maxDownloadBytes) {
            return PickResult.Fail(UiText.res(R.string.launcher_addon_pick_too_big, chosen.name, mb(chosen.size), mb(entry.maxDownloadBytes)))
        }
        return PickResult.Ok(PickedRelease(release.tag, release.publishedAt, chosen))
    }

    private fun mb(bytes: Long): Long = (bytes + 1024 * 1024 - 1) / (1024 * 1024)
}

/** Antwort von `filedetails/{id}.json` der WoWInterface-API (api.mmoui.com). */
data class WowInterfaceFile(val version: String, val downloadUrl: String, val md5: String?, val title: String?)

object WowInterfaceParser {
    /** Der Download-Link muss auf einen erlaubten WoWInterface-Host zeigen, sonst null. */
    fun parseDetails(json: String?): WowInterfaceFile? {
        if (json.isNullOrBlank()) return null
        return try {
            val text = json.trim()
            val o = if (text.startsWith("[")) org.json.JSONArray(text).optJSONObject(0) else JSONObject(text)
            o ?: return null
            val version = o.optString("UIVersion", "").trim()
            val url = o.optString("UIDownload", "").trim()
            if (version.isEmpty() || !AddonHosts.isAllowed(url, AddonHosts.WOWINTERFACE)) return null
            WowInterfaceFile(
                version = version,
                downloadUrl = url,
                md5 = o.optString("UIMD5", "").trim().lowercase().takeIf { Regex("^[0-9a-f]{32}$").matches(it) },
                title = o.optString("UIName", "").ifEmpty { null },
            )
        } catch (e: JSONException) {
            null
        }
    }
}
