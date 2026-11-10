package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/*
 * Dateien rund um installierte Addons (reine Dateilogik, läuft in JUnit):
 *  - Verzeichnis der von uns installierten Addons (.wf-addons.json in Interface/AddOns),
 *  - Aktiv-Schalter über Umbenennen der .toc-Dateien,
 *  - Entfernen (nur die Ordner des Katalogeintrags),
 *  - Status eines Katalogeintrags für die Anzeige.
 *
 * Aktiv-Schalter, Entscheidung: `<Ordner>.toc` -> `<Ordner>.toc.aus`. Ein Ordner ohne .toc ist für WoW kein Addon, es lädt
 * ihn nicht. Das gilt für jedes Konto und jeden Charakter, auch vor der ersten Anmeldung (die WTF/Account-Ordner und die
 * AddOns.txt gibt es erst danach, und ihr Format auf WoW Forever ist ungeprüft). Preis: im Spiel erscheint ein
 * abgeschaltetes Addon gar nicht in der Addon-Liste. Ein Update ersetzt den Ordner; [AddonService] stellt den Schalter danach wieder her.
 * Unser TouchForever ist über [AddonReserved] nie betroffen.
 */

/** Eintrag im Verzeichnis: was wir von einem Katalogeintrag installiert haben. */
data class InstalledRecord(
    val id: String,
    val tag: String,
    val sha256: String,
    val bytes: Long,
    val installedAt: Long,
    val folders: List<String>,
    val asset: String,
)

object AddonManifest {
    const val FILE = ".wf-addons.json"
    private const val VERSION = 1

    fun read(addonsDir: File): Map<String, InstalledRecord> {
        val file = File(addonsDir, FILE)
        if (!file.isFile) return emptyMap()
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val addons = root.optJSONObject("addons") ?: return emptyMap()
            val result = LinkedHashMap<String, InstalledRecord>()
            for (id in addons.keys()) {
                val o = addons.optJSONObject(id) ?: continue
                val folders = o.optJSONArray("folders")?.let { a -> (0 until a.length()).map { a.optString(it, "") } }.orEmpty()
                    .filter { it.isNotEmpty() }
                val tag = o.optString("tag", "")
                if (tag.isEmpty() || folders.isEmpty()) continue
                result[id] = InstalledRecord(
                    id = id,
                    tag = tag,
                    sha256 = o.optString("sha256", ""),
                    bytes = o.optLong("bytes", 0L),
                    installedAt = o.optLong("installedAt", 0L),
                    folders = folders,
                    asset = o.optString("asset", ""),
                )
            }
            result
        } catch (e: JSONException) {
            emptyMap()
        }
    }

    fun write(addonsDir: File, records: Map<String, InstalledRecord>) {
        addonsDir.mkdirs()
        val addons = JSONObject()
        for ((id, r) in records) {
            addons.put(
                id,
                JSONObject()
                    .put("tag", r.tag)
                    .put("sha256", r.sha256)
                    .put("bytes", r.bytes)
                    .put("installedAt", r.installedAt)
                    .put("folders", JSONArray(r.folders))
                    .put("asset", r.asset),
            )
        }
        val text = JSONObject().put("version", VERSION).put("addons", addons).toString(2) + "\n"
        val tmp = File(addonsDir, "$FILE.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        val target = File(addonsDir, FILE)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw AddonInstallException(UiText.res(R.string.launcher_addon_manifest_save_failed))
        }
    }

    fun put(addonsDir: File, record: InstalledRecord) = write(addonsDir, read(addonsDir) + (record.id to record))

    fun remove(addonsDir: File, id: String) = write(addonsDir, read(addonsDir) - id)
}

enum class EnableState { ENABLED, DISABLED, MIXED, MISSING }

object AddonToggle {
    const val OFF_SUFFIX = ".aus"

    private fun rootTocs(folder: File): List<File> =
        folder.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".toc", ignoreCase = true) }

    private fun rootOffTocs(folder: File): List<File> =
        folder.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".toc$OFF_SUFFIX", ignoreCase = true) }

    /** Zustand über alle [folders]: aktiv, aus, teils (nur ein Teil der Ordner hat eine aktive .toc) oder keiner vorhanden. */
    fun state(addonsDir: File, folders: List<String>): EnableState {
        val existing = folders.map { File(addonsDir, it) }.filter { it.isDirectory }
        if (existing.isEmpty()) return EnableState.MISSING
        val active = existing.count { rootTocs(it).isNotEmpty() }
        return when (active) {
            existing.size -> EnableState.ENABLED
            0 -> EnableState.DISABLED
            else -> EnableState.MIXED
        }
    }

    /** Stellt alle [folders] auf [enabled]; liefert die Zahl umbenannter Dateien. Geschützte Ordner werden nie angefasst. */
    fun setEnabled(addonsDir: File, folders: List<String>, enabled: Boolean): Int {
        if (folders.any { AddonReserved.isReserved(it) }) throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_protected))
        var changed = 0
        for (name in folders) {
            val folder = File(addonsDir, name)
            if (!folder.isDirectory) continue
            if (enabled) {
                for (off in rootOffTocs(folder)) {
                    val on = File(folder, off.name.dropLast(OFF_SUFFIX.length))
                    if (on.exists()) off.delete() else if (off.renameTo(on)) changed++
                }
            } else {
                for (on in rootTocs(folder)) {
                    val off = File(folder, on.name + OFF_SUFFIX)
                    if (off.exists()) off.delete()
                    if (on.renameTo(off)) changed++
                }
            }
        }
        return changed
    }
}

object AddonFiles {
    /** Löscht die [folders] des Katalogeintrags (nur diese, keine geschützten) und liefert, wie viele es gab. */
    fun remove(addonsDir: File, folders: List<String>): Int {
        if (folders.any { AddonReserved.isReserved(it) }) throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_protected))
        var removed = 0
        for (name in folders) {
            val folder = File(addonsDir, name)
            if (folder.isDirectory) {
                if (!folder.deleteRecursively()) throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_delete_failed, name))
                removed++
            }
        }
        return removed
    }
}

enum class AddonInstallState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE, FOREIGN }

/** Was die Liste zu einem Katalogeintrag zeigt. */
data class AddonStatus(
    val entry: CatalogEntry,
    val state: AddonInstallState,
    val installedTag: String?,
    val latestTag: String?,
    val enable: EnableState,
)

object AddonStatusLogic {
    /**
     * - ohne Eintrag im Verzeichnis, aber Hauptordner da: [AddonInstallState.FOREIGN] (nicht von uns, wird nicht überschrieben);
     * - mit Eintrag, aber Hauptordner fehlt: gilt als nicht installiert;
     * - mit Eintrag und neuerem [latestTag]: Update.
     */
    fun compute(entry: CatalogEntry, record: InstalledRecord?, addonsDir: File, latestTag: String?): AddonStatus {
        val mainThere = File(addonsDir, entry.folders.first()).isDirectory
        val enable = AddonToggle.state(addonsDir, entry.folders)
        return when {
            record == null && mainThere -> AddonStatus(entry, AddonInstallState.FOREIGN, null, latestTag, enable)
            record == null || !mainThere -> AddonStatus(entry, AddonInstallState.NOT_INSTALLED, null, latestTag, EnableState.MISSING)
            latestTag != null && AddonVersion.isUpdate(record.tag, latestTag) ->
                AddonStatus(entry, AddonInstallState.UPDATE_AVAILABLE, record.tag, latestTag, enable)
            else -> AddonStatus(entry, AddonInstallState.INSTALLED, record.tag, latestTag, enable)
        }
    }
}
