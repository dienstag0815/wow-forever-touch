package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText

/** Where a release of the version list stands against the installed app. */
enum class VersionRelation { Newer, Installed, Older }

/** One line of the tester's version list. [name] is the tag without a leading `v`; [notes] is the short changelog text. */
data class VersionRow(
    val tag: String,
    val name: String,
    val title: String,
    val notes: String,
    val apkSize: Long,
    val prerelease: Boolean,
    val relation: VersionRelation,
)

/** One paragraph of the downgrade dialog: [text] and whether it warns about lost data. */
data class DialogPart(val text: UiText, val warning: Boolean = false)

/** The three automatic steps of a downgrade; the fourth (install and import) is done by the player. */
enum class RollbackStep { Backup, Download, Uninstall }

/**
 * Pure logic of "Versionen" in the tester area (no Android classes): list, sorting, relation to the installed app, short notes,
 * the data rules of a downgrade and the text of its dialog.
 *
 * Why a dialog and no direct install: Android refuses an APK with a lower versionCode over a newer one
 * (INSTALL_FAILED_VERSION_DOWNGRADE). It works only after uninstalling, and that deletes the app's private storage (see
 * [GameFilesPlace]). So the app exports a backup and loads the old APK into the shared Downloads folder first.
 */
object RollbackLogic {
    private const val NOTES_MAX = 140

    /** Name of a release as shown and compared: the tag without a leading `v`. */
    fun nameOf(tag: String): String = tag.trim().trimStart('v', 'V')

    /**
     * The rows of the list: no drafts, no release without a tag or APK, newest first (by [UpdateLogic.compareVersionNames]; equal
     * names keep the order of the server, which lists newest first). Pre-releases are included only when [includePrereleases].
     */
    fun rows(releases: List<ReleaseEntry>, installedName: String, includePrereleases: Boolean = true): List<VersionRow> =
        releases
            .filter { !it.draft && it.tag.isNotEmpty() && (includePrereleases || !it.prerelease) && it.assets.any(UpdateSourceLogic::isApk) }
            .sortedWith { a, b -> UpdateLogic.compareVersionNames(nameOf(b.tag), nameOf(a.tag)) }
            .map { release ->
                val name = nameOf(release.tag)
                VersionRow(
                    tag = release.tag,
                    name = name,
                    title = release.title,
                    notes = shortNotes(release.body),
                    apkSize = release.assets.first(UpdateSourceLogic::isApk).size,
                    prerelease = release.prerelease,
                    relation = relation(name, installedName),
                )
            }

    fun relation(name: String, installedName: String): VersionRelation {
        if (nameOf(name).equals(nameOf(installedName), ignoreCase = true)) return VersionRelation.Installed
        return if (UpdateLogic.compareVersionNames(name, installedName) > 0) VersionRelation.Newer else VersionRelation.Older
    }

    /**
     * Direction of [info] against the installed app, final once the feed is read: a feed `versionCode` decides (it is what Android
     * compares), otherwise the names do.
     */
    fun relation(info: UpdateInfo, installedCode: Int, installedName: String): VersionRelation = when {
        info.versionCode > 0 && info.versionCode == installedCode -> VersionRelation.Installed
        info.versionCode > 0 -> if (info.versionCode > installedCode) VersionRelation.Newer else VersionRelation.Older
        else -> relation(info.versionName, installedName)
    }

    /**
     * Changelog short text: the first lines of [body] without headings, bullets, emphasis and code marks, joined by spaces and cut
     * at [NOTES_MAX] characters (at a word, with an ellipsis). Empty when there is nothing.
     */
    fun shortNotes(body: String): String {
        val lines = body.lineSequence()
            .map { it.trim().trimStart('#', '-', '*', '+', '>', ' ').replace("**", "").replace("__", "").replace("`", "").trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val text = lines.joinToString(" ")
        if (text.length <= NOTES_MAX) return text
        val cut = text.take(NOTES_MAX)
        val atWord = cut.lastIndexOf(' ').takeIf { it >= NOTES_MAX / 2 } ?: NOTES_MAX
        return cut.take(atWord).trimEnd(',', ';', ':', '.', ' ') + "…"
    }

    /** File name of the old APK in Downloads: the release's own name when it is plain, else one made from the version. */
    fun apkFileName(info: UpdateInfo): String {
        val plain = Regex("^[A-Za-z0-9._-]{1,120}$")
        val own = info.file.trim()
        if (plain.matches(own) && own.endsWith(".apk", ignoreCase = true) && !own.startsWith(".")) return own
        val safe = info.versionName.filter { it.isLetterOrDigit() || it == '.' || it == '-' }.ifEmpty { "old" }
        return "wow-forever-$safe.apk"
    }

    // ---- what survives the uninstall --------------------------------------------------------------------------------

    /**
     * Where the game files (the WoW client, several GB) are, seen from an uninstall. Android deletes `/data/data/<package>` and
     * `/Android/data/<package>` (and `/Android/obb/<package>`) with the app; everything else on shared storage stays.
     */
    enum class GameFilesPlace { Shared, InAppStorage }

    fun gameFilesPlace(gamePath: String, packageName: String, dataDir: String): GameFilesPlace {
        val p = gamePath.replace('\\', '/').trimEnd('/')
        val data = dataDir.replace('\\', '/').trimEnd('/')
        val inApp = p.isNotEmpty() && (
            p.contains("/Android/data/$packageName") ||
                p.contains("/Android/obb/$packageName") ||
                p.contains("/data/data/$packageName") ||
                (data.isNotEmpty() && (p == data || p.startsWith("$data/")))
            )
        return if (inApp) GameFilesPlace.InAppStorage else GameFilesPlace.Shared
    }

    /** "1.5 GB", "320 MB" (binary units, one decimal from GB). */
    fun sizeText(bytes: Long): String {
        val mb = 1024.0 * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> "%.1f GB".format(java.util.Locale.ROOT, bytes / gb)
            else -> "${(bytes / mb).toLong().coerceAtLeast(if (bytes > 0) 1 else 0)} MB"
        }
    }

    /**
     * The text of the downgrade dialog, paragraph by paragraph. [gameBytes] is the size of the game files, only needed (and
     * measured) when they sit in the app's storage and are deleted with it; 0 = unknown.
     */
    fun dialogParts(target: VersionRow, place: GameFilesPlace, gamePath: String, gameBytes: Long, backupFile: String): List<DialogPart> = buildList {
        add(DialogPart(UiText.res(R.string.launcher_rollback_why)))
        add(DialogPart(UiText.res(R.string.launcher_rollback_steps, backupFile, target.name)))
        when (place) {
            GameFilesPlace.Shared -> add(DialogPart(UiText.res(R.string.launcher_rollback_game_kept, gamePath)))
            GameFilesPlace.InAppStorage -> add(
                DialogPart(
                    if (gameBytes > 0) {
                        UiText.res(R.string.launcher_rollback_game_lost_size, sizeText(gameBytes))
                    } else {
                        UiText.res(R.string.launcher_rollback_game_lost)
                    },
                    warning = true,
                ),
            )
        }
        add(DialogPart(UiText.res(R.string.launcher_rollback_lost_always), warning = true))
        add(DialogPart(UiText.res(R.string.launcher_rollback_after)))
    }

    // ---- backup file after a fresh install --------------------------------------------------------------------------

    private val BACKUP_NAME = Regex("""^wow-forever-touch-backup-(\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2})_[0-9a-f]+\.zip$""")

    fun isBackupName(name: String): Boolean = BACKUP_NAME.matches(name)

    /** The newest backup among [names] (by the time stamp in the name; the same stamp: the later name), or null. */
    fun newestBackup(names: List<String>): String? =
        names.mapNotNull { name -> BACKUP_NAME.matchEntire(name)?.let { it.groupValues[1] to name } }
            .maxWithOrNull(compareBy({ it.first }, { it.second }))?.second

    /** Suggest the import after a fresh install: only once, and only when a backup was found. */
    fun shouldSuggestImport(pending: Boolean, candidate: String?): Boolean = pending && candidate != null
}
