package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Fehler beim sicheren Entpacken; [text] ist die Meldung für die Anzeige ([message] ist nur für Logs und Debugger). */
class AddonInstallException(val text: UiText) : IOException(text.toString())

/**
 * Entpackt ein Addon-Archiv sicher nach `Interface/AddOns` (reine Dateilogik, läuft in JUnit).
 * Ansatz (Staging-Ordner im selben Dateisystem, Tausch per Umbenennen, Rollback) nach WoW Foreverywhere
 * `wow/addons/AddonArchive.kt` (GPL-3.0); die Regeln sind strenger:
 *  - Einträge mit absolutem Pfad, Laufwerksbuchstaben, `..`, NUL oder `:` brechen alles ab.
 *  - Nur Ordner aus dem Katalog ([allowedFolders]) dürfen entstehen; jeder andere oberste Ordner bricht ab, Dateien auf
 *    oberster Ebene (README, LICENSE) werden übergangen. Ein einzelner Hüllordner um die erlaubten Ordner wird entfernt.
 *  - Obergrenzen: Anzahl Einträge, entpackte Gesamtgröße (gezählt beim Schreiben, nicht nach den Angaben im Archiv).
 *  - Der Hauptordner (erster Eintrag der Liste) muss eine `.toc` enthalten.
 *  - Ein defektes Archiv lässt vorhandene Ordner unberührt; scheitert der Tausch, kommen alle Ordner zurück.
 */
object AddonZip {
    const val MAX_ENTRIES = 20_000
    const val STAGING_PREFIX = ".wf-staging-"
    const val BACKUP_PREFIX = ".wf-old-"
    private const val MAX_SEGMENT = 255

    private val DRIVE = Regex("^[A-Za-z]:")

    /** Installiert [zip] und liefert die angelegten Ordner (Schreibweise wie im Katalog), sortiert. */
    fun install(zip: File, addonsDir: File, allowedFolders: List<String>, maxUnpackedBytes: Long): List<String> {
        require(allowedFolders.isNotEmpty()) { "no allowed folders" }
        if (allowedFolders.any { AddonReserved.isReserved(it) }) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_catalog_protected))
        addonsDir.mkdirs()
        recoverLeftovers(addonsDir)

        val canonical = allowedFolders.associateBy { it.lowercase() }
        val plan = plan(zip, canonical)

        val staging = File(addonsDir, "$STAGING_PREFIX${System.nanoTime()}")
        if (!staging.mkdirs()) throw AddonInstallException(UiText.res(R.string.launcher_addon_staging_failed))
        try {
            extract(zip, plan, staging, maxUnpackedBytes)
            val folders = staging.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }
            val main = allowedFolders.first()
            if (main !in folders) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_missing_main, main))
            val hasToc = File(staging, main).listFiles().orEmpty().any { it.isFile && it.name.endsWith(".toc", ignoreCase = true) }
            if (!hasToc) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_no_toc, main))
            swapIn(folders.map { File(staging, it) }, addonsDir)
            return folders.sorted()
        } finally {
            staging.deleteRecursively()
        }
    }

    private class Plan(val entries: List<PlanEntry>)

    private class PlanEntry(val raw: String, val segments: List<String>, val isDirectory: Boolean)

    private fun plan(zip: File, canonical: Map<String, String>): Plan {
        val all = ArrayList<PlanEntry>()
        try {
            ZipFile(zip).use { z ->
                val list = z.entries()
                while (list.hasMoreElements()) {
                    val e = list.nextElement()
                    if (all.size >= MAX_ENTRIES) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_too_many_entries))
                    val isDir = e.isDirectory || e.name.endsWith("/") || e.name.endsWith("\\")
                    all += PlanEntry(e.name, safeSegments(e.name), isDir)
                }
            }
        } catch (e: AddonInstallException) {
            throw e
        } catch (e: IOException) {
            throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_corrupt))
        }

        // Ordner, die Mac-Packprogramme anhängen, sind kein Addon-Inhalt.
        all.removeAll { it.segments.firstOrNull() == "__MACOSX" }

        // Einzelner Hüllordner (z. B. "Questie-12.6.4/Questie/...")? Dann eine Ebene abziehen.
        val tops = all.filter { it.segments.size >= 2 || it.isDirectory }.mapNotNull { it.segments.firstOrNull() }.toSet()
        val wrapper = if (tops.none { it.lowercase() in canonical } && tops.size == 1) tops.single() else null
        val effective = all.map { if (wrapper != null && it.segments.firstOrNull() == wrapper) PlanEntry(it.raw, it.segments.drop(1), it.isDirectory) else it }

        val kept = ArrayList<PlanEntry>()
        for (entry in effective) {
            if (entry.segments.isEmpty()) continue
            val top = entry.segments[0]
            if (entry.segments.size == 1 && !entry.isDirectory) continue // Datei auf oberster Ebene: übergehen
            val name = canonical[top.lowercase()]
                ?: throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_unexpected_folder, top))
            kept += PlanEntry(entry.raw, listOf(name) + entry.segments.drop(1), entry.isDirectory)
        }
        if (kept.isEmpty()) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_no_addon_folder))
        return Plan(kept)
    }

    /** Zerlegt einen Eintragsnamen in saubere Teile oder wirft bei unsicheren Namen. */
    internal fun safeSegments(raw: String): List<String> {
        if (raw.indexOf('\u0000') >= 0) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_unsafe_path))
        val name = raw.replace('\\', '/')
        if (name.startsWith("/") || DRIVE.containsMatchIn(name)) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_unsafe_path))
        val parts = name.split('/')
        if (parts.any { it == ".." }) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_unsafe_path))
        val segments = parts.filter { it.isNotEmpty() && it != "." }
        if (segments.any { ':' in it || it.length > MAX_SEGMENT }) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_unsafe_path))
        return segments
    }

    private fun extract(zip: File, plan: Plan, staging: File, maxUnpackedBytes: Long) {
        val root = staging.canonicalFile
        val rootPrefix = root.path + File.separator
        var total = 0L
        val byRaw = plan.entries.associateBy { it.raw }
        ZipFile(zip).use { z ->
            val list = z.entries()
            while (list.hasMoreElements()) {
                val e = list.nextElement()
                val entry = byRaw[e.name] ?: continue
                var target = staging
                for (s in entry.segments) target = File(target, s)
                if (!target.canonicalFile.path.startsWith(rootPrefix)) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_unsafe_path))
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                z.getInputStream(e).use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > maxUnpackedBytes) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_unpacked_too_big))
                            output.write(buffer, 0, n)
                        }
                    }
                }
            }
        }
    }

    private fun swapIn(folders: List<File>, addonsDir: File) {
        val nonce = System.nanoTime()
        val done = ArrayList<Pair<File, File?>>() // Ziel und seine Sicherung
        try {
            for (folder in folders) {
                val target = File(addonsDir, folder.name)
                var backup: File? = null
                if (target.exists()) {
                    backup = File(addonsDir, "$BACKUP_PREFIX$nonce-${folder.name}")
                    if (!target.renameTo(backup)) throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_replace_failed, folder.name))
                }
                done += target to backup
                if (!folder.renameTo(target)) throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_insert_failed, folder.name))
            }
        } catch (e: AddonInstallException) {
            for ((target, backup) in done.asReversed()) {
                if (backup != null) {
                    target.deleteRecursively()
                    backup.renameTo(target)
                } else {
                    target.deleteRecursively()
                }
            }
            throw e
        }
        done.forEach { (_, backup) -> backup?.deleteRecursively() }
    }

    /**
     * Räumt Reste eines abgebrochenen Vorgangs auf: Zwischenordner löschen; eine Sicherung (`.wf-old-<n>-<Ordner>`) kommt
     * zurück, wenn ihr Ordner fehlt, sonst wird sie gelöscht.
     */
    fun recoverLeftovers(addonsDir: File) {
        val children = addonsDir.listFiles().orEmpty()
        children.filter { it.isDirectory && it.name.startsWith(STAGING_PREFIX) }.forEach { it.deleteRecursively() }
        for (backup in children.filter { it.isDirectory && it.name.startsWith(BACKUP_PREFIX) }) {
            val original = backup.name.removePrefix(BACKUP_PREFIX).substringAfter('-', "")
            if (original.isEmpty()) continue
            val target = File(addonsDir, original)
            if (!target.exists()) backup.renameTo(target) else backup.deleteRecursively()
        }
    }
}
