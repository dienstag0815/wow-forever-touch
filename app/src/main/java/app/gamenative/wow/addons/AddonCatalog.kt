package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/*
 * Kuratierter Addon-Katalog (assets/wow/addons/katalog.json), reine Logik ohne Android-Klassen (läuft in JUnit).
 *
 * Der Katalog ist KEIN offener Marktplatz: nur Einträge, die wir geprüft haben, und für jeden steht fest, aus welcher
 * Quelle er kommt, welche Ordner er in Interface/AddOns anlegen darf und wie groß Download und entpackter Inhalt
 * höchstens sein dürfen. Ein einziger fehlerhafter Eintrag macht den ganzen Katalog ungültig (nichts wird halb geladen).
 *
 * Format (Schlüssel deutsch wie beim Update-Feed):
 * {"version":1,"stand":"2026-11-08","addons":[{"id":"questie","name":"Questie","kurz":"...","warum":"...","lizenz":"...",
 *   "quelle":{"typ":"github","repo":"Questie/Questie","asset":"^Questie-.*\\.zip$","forever":true},
 *   "ordner":["Questie","QuestieDB"],"maxMB":120,"maxEntpacktMB":260}]}
 * oder "quelle":{"typ":"wowinterface","id":"12345"}.
 */

/** Woher ein Addon kommt. Die erlaubten Hosts je Quelle stehen in [AddonHosts]. */
sealed class AddonSource {
    /** Release-Archiv eines GitHub-Repos: [assetPattern] ist ein Regex auf den ganzen Dateinamen. */
    data class GitHub(
        val owner: String,
        val repo: String,
        val assetPattern: String,
        /** true = release.json des Releases muss die Version "forever" für das Archiv nennen. */
        val requireForever: Boolean = true,
    ) : AddonSource() {
        val slug: String get() = "$owner/$repo"
        fun matchesAsset(name: String): Boolean = Regex(assetPattern, RegexOption.IGNORE_CASE).matches(name)
    }

    /** Datei-ID auf wowinterface.com (Zahl). */
    data class WowInterface(val fileId: String) : AddonSource()
}

data class CatalogEntry(
    val id: String,
    val name: String,
    val summary: String,
    /** Der Satz "Warum kuratiert": wieso wir dieses Addon für gut und vernünftig halten. */
    val why: String,
    val license: String,
    val source: AddonSource,
    /** Die Ordner in Interface/AddOns, die das Addon anlegt (der erste ist der Hauptordner). Nur diese werden angefasst. */
    val folders: List<String>,
    val maxDownloadBytes: Long,
    val maxUnpackedBytes: Long,
)

data class AddonCatalog(val version: Int, val updated: String, val entries: List<CatalogEntry>) {
    fun byId(id: String): CatalogEntry? = entries.firstOrNull { it.id == id }
}

sealed class CatalogResult {
    data class Ok(val catalog: AddonCatalog) : CatalogResult()
    data class Invalid(val reason: UiText) : CatalogResult()
}

object AddonCatalogParser {
    const val SUPPORTED_VERSION = 1

    const val DEFAULT_MAX_DOWNLOAD_BYTES = 30L * 1024 * 1024
    const val DEFAULT_MAX_UNPACKED_BYTES = 120L * 1024 * 1024

    /** Obergrenzen, über die auch ein Katalogeintrag nicht hinaus darf. */
    const val HARD_MAX_DOWNLOAD_BYTES = 120L * 1024 * 1024
    const val HARD_MAX_UNPACKED_BYTES = 300L * 1024 * 1024

    private const val MB = 1024L * 1024

    private val ID = Regex("^[a-z0-9][a-z0-9-]{0,39}$")
    private val FOLDER = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")
    private val OWNER_OR_REPO = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,99}$")
    private val FILE_ID = Regex("^[0-9]{1,9}$")

    fun parse(json: String?): CatalogResult {
        if (json.isNullOrBlank()) return CatalogResult.Invalid(UiText.res(R.string.launcher_addon_catalog_empty))
        return try {
            parseObject(JSONObject(json))
        } catch (e: JSONException) {
            CatalogResult.Invalid(UiText.res(R.string.launcher_addon_catalog_bad_json))
        } catch (e: CatalogProblem) {
            CatalogResult.Invalid(e.text)
        }
    }

    private class CatalogProblem(val text: UiText) : Exception(text.toString())

    private fun parseObject(root: JSONObject): CatalogResult {
        val version = root.optInt("version", 0)
        if (version != SUPPORTED_VERSION) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_version, version))
        val list = root.optJSONArray("addons") ?: throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_addons_missing))
        val entries = (0 until list.length()).map { parseEntry(list, it) }
        val ids = entries.map { it.id }
        if (ids.size != ids.toSet().size) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_duplicate_id))
        val allFolders = entries.flatMap { e -> e.folders.map { it.lowercase() } }
        if (allFolders.size != allFolders.toSet().size) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_shared_folder))
        return CatalogResult.Ok(AddonCatalog(version, root.optString("stand", ""), entries))
    }

    private fun parseEntry(list: JSONArray, index: Int): CatalogEntry {
        val o = list.optJSONObject(index) ?: throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_entry_not_object, index + 1))
        val id = o.optString("id", "")
        if (!ID.matches(id)) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_entry_bad_id, index + 1, id))
        fun text(key: String): String =
            o.optString(key, "").trim().ifEmpty { throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_field_missing, id, key)) }

        val name = text("name")
        val summary = text("kurz")
        val why = text("warum")
        val license = text("lizenz")
        val folders = parseFolders(id, o.optJSONArray("ordner"))
        val source = parseSource(id, o.optJSONObject("quelle"))
        val maxDownload = parseLimit(id, o, "maxMB", DEFAULT_MAX_DOWNLOAD_BYTES, HARD_MAX_DOWNLOAD_BYTES)
        val maxUnpacked = parseLimit(id, o, "maxEntpacktMB", DEFAULT_MAX_UNPACKED_BYTES, HARD_MAX_UNPACKED_BYTES)
        return CatalogEntry(id, name, summary, why, license, source, folders, maxDownload, maxUnpacked)
    }

    private fun parseFolders(id: String, array: JSONArray?): List<String> {
        if (array == null || array.length() == 0) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_field_missing, id, "ordner"))
        val folders = (0 until array.length()).map { array.optString(it, "") }
        for (folder in folders) {
            if (!FOLDER.matches(folder)) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_folder_invalid, id, folder))
            if (AddonReserved.isReserved(folder)) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_folder_protected, id, folder))
        }
        if (folders.map { it.lowercase() }.toSet().size != folders.size) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_folder_twice, id))
        return folders
    }

    private fun parseSource(id: String, o: JSONObject?): AddonSource {
        o ?: throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_field_missing, id, "quelle"))
        return when (o.optString("typ", "")) {
            "github" -> {
                val repo = o.optString("repo", "").split('/')
                if (repo.size != 2 || !OWNER_OR_REPO.matches(repo[0]) || !OWNER_OR_REPO.matches(repo[1])) {
                    throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_repo_format, id))
                }
                val pattern = o.optString("asset", "")
                if (pattern.isBlank()) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_field_missing, id, "asset"))
                try {
                    Regex(pattern)
                } catch (e: IllegalArgumentException) {
                    throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_asset_bad_regex, id))
                }
                AddonSource.GitHub(repo[0], repo[1], pattern, o.optBoolean("forever", true))
            }
            "wowinterface" -> {
                val fileId = o.optString("id", "")
                if (!FILE_ID.matches(fileId)) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_wowi_id_invalid, id))
                AddonSource.WowInterface(fileId)
            }
            else -> throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_source_unknown, id, o.optString("typ", "")))
        }
    }

    private fun parseLimit(id: String, o: JSONObject, key: String, default: Long, hardMax: Long): Long {
        if (!o.has(key)) return default
        val mb = o.optLong(key, -1)
        if (mb <= 0) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_limit_invalid, id, key))
        val bytes = mb * MB
        if (bytes > hardMax) throw CatalogProblem(UiText.res(R.string.launcher_addon_catalog_limit_too_high, id, key, hardMax / MB))
        return bytes
    }
}

/** Ordner, die der Addon-Manager nie anlegt, ändert oder löscht. */
object AddonReserved {
    /** Unser eigenes Addon (und sein alter Name) und der Ordner mit den Blizzard-Oberflächen. */
    private val EXACT = setOf("touchforever", "wowforeverbridge")

    fun isReserved(folder: String): Boolean {
        val f = folder.lowercase()
        return f in EXACT || f.startsWith("blizzard_") || f.startsWith("touchforever")
    }
}
