package app.gamenative.wow.addons

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.io.File
import java.util.zip.ZipFile

/*
 * Eigene Addons (Auftrag addonlink): Einbau der im Zip gefundenen Addons nach `Interface/AddOns/<Ordner>`. Gleiche Technik wie
 * [AddonZip] (Zwischenordner im selben Dateisystem, Tausch per Umbenennen, Rollback), aber die Ordner kommen aus
 * [CustomAddonScan], nicht aus dem Katalog:
 *  - Mitgelieferte und geprüfte Addons (Katalogordner, TouchForever, Blizzard_*) werden nie überschrieben: [Verdict.BLOCKED].
 *  - Existiert der Zielordner, muss der Aufrufer das Ersetzen ausdrücklich erlauben; der alte Ordner bleibt als `<Ordner>_bak`
 *    (eine Generation) erhalten.
 *  - Versteckte Dateien und Ordner (`.github`, `.gitignore`) aus GitHub-Archiven kommen nicht mit.
 */
object CustomAddonInstall {
    const val BACKUP_SUFFIX = "_bak"

    enum class Verdict { NEW, REPLACE, BLOCKED }

    data class Assessment(val addon: FoundAddon, val verdict: Verdict, val reason: UiText?)

    /** Kleinbuchstaben-Namen aller Ordner, die nicht überschrieben werden dürfen: die Katalogordner. */
    fun protectedFolders(catalog: AddonCatalog?): Set<String> =
        catalog?.entries?.flatMap { e -> e.folders.map { it.lowercase() } }?.toSet().orEmpty()

    /** Bewertet jedes gefundene Addon gegen den Inhalt von [addonsDir]. */
    fun assess(addons: List<FoundAddon>, addonsDir: File, protectedFolders: Set<String>): List<Assessment> {
        val existing = addonsDir.listFiles().orEmpty().map { it.name }
        return addons.map { a ->
            val name = a.folder
            val similar = existing.firstOrNull { it.equals(name, ignoreCase = true) }
            when {
                !CustomAddonScan.FOLDER_NAME.matches(name) -> Assessment(a, Verdict.BLOCKED, UiText.res(R.string.launcher_addon_assess_bad_name, name))
                AddonReserved.isReserved(name) || name.lowercase() in protectedFolders ->
                    Assessment(a, Verdict.BLOCKED, UiText.res(R.string.launcher_addon_assess_protected, name))
                name.endsWith(BACKUP_SUFFIX, ignoreCase = true) ->
                    Assessment(a, Verdict.BLOCKED, UiText.res(R.string.launcher_addon_assess_bak_reserved, BACKUP_SUFFIX))
                similar != null && similar != name -> Assessment(a, Verdict.BLOCKED, UiText.res(R.string.launcher_addon_assess_similar, similar))
                similar != null && !File(addonsDir, name).isDirectory -> Assessment(a, Verdict.BLOCKED, UiText.res(R.string.launcher_addon_assess_not_folder, name))
                similar != null -> Assessment(a, Verdict.REPLACE, null)
                else -> Assessment(a, Verdict.NEW, null)
            }
        }
    }

    /**
     * Wohin kommt der Zip-Eintrag [segments]? Der Eintrag gehört zum Addon mit dem längsten passenden Pfad; null, wenn er zu
     * keinem gehört (Dateien im Hüllordner) oder versteckt ist.
     */
    internal fun mapEntry(segments: List<String>, addons: List<FoundAddon>): List<String>? {
        var best: FoundAddon? = null
        for (a in addons) {
            val n = a.prefix.size
            if (segments.size > n && segments.subList(0, n) == a.prefix && (best == null || n > best.prefix.size)) best = a
        }
        val target = best ?: return null
        val rest = segments.drop(target.prefix.size)
        if (rest.any { it.startsWith(".") }) return null
        return listOf(target.folder) + rest
    }

    /**
     * Baut [addons] aus [zip] in [addonsDir] ein und liefert die Ordnernamen. Wirft [AddonInstallException], wenn ein Ordner
     * gesperrt ist, ein vorhandener Ordner ohne [replace] ersetzt würde, ein Pfad unsicher ist oder das Limit überschritten wird.
     * Bei jedem Fehler bleibt der alte Stand erhalten.
     */
    fun install(
        zip: File,
        addonsDir: File,
        addons: List<FoundAddon>,
        protectedFolders: Set<String>,
        replace: Boolean,
        maxUnpackedBytes: Long,
    ): List<String> {
        require(addons.isNotEmpty()) { "no addons" }
        addonsDir.mkdirs()
        AddonZip.recoverLeftovers(addonsDir)
        val verdicts = assess(addons, addonsDir, protectedFolders)
        verdicts.firstOrNull { it.verdict == Verdict.BLOCKED }?.let { throw AddonInstallException(it.reason ?: UiText.raw("")) }
        verdicts.firstOrNull { it.verdict == Verdict.REPLACE && !replace }
            ?.let { throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_exists, it.addon.folder)) }

        val staging = File(addonsDir, "${AddonZip.STAGING_PREFIX}${System.nanoTime()}")
        if (!staging.mkdirs()) throw AddonInstallException(UiText.res(R.string.launcher_addon_staging_failed))
        try {
            extract(zip, addons, staging, maxUnpackedBytes)
            for (a in addons) {
                val folder = File(staging, a.folder)
                val hasToc = folder.listFiles().orEmpty().any { it.isFile && it.name.endsWith(".toc", ignoreCase = true) }
                if (!hasToc) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_no_toc, a.folder))
            }
            swap(addons.map { it.folder }, staging, addonsDir)
            return addons.map { it.folder }.sorted()
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extract(zip: File, addons: List<FoundAddon>, staging: File, maxUnpackedBytes: Long) {
        val rootPrefix = staging.canonicalFile.path + File.separator
        var total = 0L
        try {
            ZipFile(zip).use { z ->
                val list = z.entries()
                while (list.hasMoreElements()) {
                    val e = list.nextElement()
                    val isDir = e.isDirectory || e.name.endsWith("/") || e.name.endsWith("\\")
                    val mapped = mapEntry(AddonZip.safeSegments(e.name), addons) ?: continue
                    var target = staging
                    for (s in mapped) target = File(target, s)
                    if (!target.canonicalFile.path.startsWith(rootPrefix)) throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_unsafe_path))
                    if (isDir) {
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
        } catch (e: AddonInstallException) {
            throw e
        } catch (e: java.io.IOException) {
            throw AddonInstallException(UiText.res(R.string.launcher_addon_zip_corrupt))
        }
    }

    private class Step(val target: File, val backup: File, val hadTarget: Boolean, val parkedOldBackup: File?)

    /** Tauscht die fertigen Ordner ein. Alter Ordner -> `<Ordner>_bak`; eine ältere Sicherung wird erst nach Erfolg gelöscht. */
    private fun swap(folders: List<String>, staging: File, addonsDir: File) {
        val nonce = System.nanoTime()
        val done = ArrayList<Step>()
        try {
            for (name in folders) {
                val target = File(addonsDir, name)
                val backup = File(addonsDir, name + BACKUP_SUFFIX)
                var parked: File? = null
                val hadTarget = target.exists()
                // Nur wenn eine neue Sicherung entsteht (alter Ordner da), muss die ältere weichen. Fehlt der Ordner, bleibt
                // die vorhandene Sicherung unberührt (sonst ginge sie beim Neueinbau verloren).
                if (hadTarget && backup.exists()) {
                    // Als ".wf-old-"-Ordner geparkt: bricht alles ab, holt recoverLeftovers die Sicherung zurück.
                    parked = File(addonsDir, "${AddonZip.BACKUP_PREFIX}$nonce-$name$BACKUP_SUFFIX")
                    if (!backup.renameTo(parked)) throw AddonInstallException(UiText.res(R.string.launcher_addon_old_backup_park_failed, name))
                }
                if (hadTarget && !target.renameTo(backup)) {
                    parked?.renameTo(backup)
                    throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_replace_failed, name))
                }
                done += Step(target, backup, hadTarget, parked)
                if (!File(staging, name).renameTo(target)) throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_insert_failed, name))
            }
        } catch (e: AddonInstallException) {
            for (step in done.asReversed()) {
                step.target.deleteRecursively()
                if (step.hadTarget) step.backup.renameTo(step.target)
                step.parkedOldBackup?.renameTo(step.backup)
            }
            throw e
        }
        done.forEach { it.parkedOldBackup?.deleteRecursively() }
    }

    /** Entfernt [folders] samt `_bak`-Sicherung. Geschützte Ordner und Katalogordner werden nie angefasst. */
    fun remove(addonsDir: File, folders: List<String>, protectedFolders: Set<String>) {
        for (name in folders) {
            if (AddonReserved.isReserved(name) || name.lowercase() in protectedFolders) throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_protected))
        }
        for (name in folders) {
            for (dir in listOf(File(addonsDir, name), File(addonsDir, name + BACKUP_SUFFIX))) {
                if (dir.exists() && !dir.deleteRecursively()) throw AddonInstallException(UiText.res(R.string.launcher_addon_folder_delete_failed, dir.name))
            }
        }
    }
}
