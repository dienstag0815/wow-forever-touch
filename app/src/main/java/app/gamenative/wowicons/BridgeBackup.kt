package app.gamenative.wowicons

import java.io.File

/**
 * Last good icon data per character, kept in the app's own storage (outside the game folder and outside the icon cache, which
 * "Cache leeren" empties). The addon's SavedVariables file is written by WoW only on /reload or logout, and a game that is
 * killed writes nothing; the old file normally stays in the game folder, so this copy only helps when that file is gone, empty
 * or unreadable (new WTF folder, reinstall, a half-written file). Used at the start: the newer of file and copy wins.
 * Pure file code.
 */
object BridgeBackup {
    private const val PREFIX = "last_"
    private const val SUFFIX = ".lua"

    /** File name for a character: realm and name with everything but letters and digits replaced. */
    fun fileNameFor(data: BridgeData): String {
        fun clean(s: String?) = (s ?: "x").map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(40).ifEmpty { "x" }
        return "$PREFIX${clean(data.realm)}_${clean(data.character)}$SUFFIX"
    }

    /** Stores [text] (the SavedVariables text that parsed to [data]) for the character; false if it could not be written. */
    fun save(dir: File, data: BridgeData, text: String): Boolean {
        if (data.totalEntries == 0) return false
        return try {
            dir.mkdirs()
            val target = File(dir, fileNameFor(data))
            val temp = File(dir, target.name + ".tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (target.exists()) target.delete()
            temp.renameTo(target)
        } catch (e: Exception) {
            false
        }
    }

    /** The saved copy with the newest `updated` over all characters, null if there is none. */
    fun load(dir: File): BridgeData? {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) } ?: return null
        var best: BridgeData? = null
        for (file in files) {
            val parsed = try {
                BridgeParser.parse(file.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                null
            } ?: continue
            if (parsed.totalEntries == 0) continue
            if (best == null || parsed.updated > best.updated) best = parsed
        }
        return best
    }

    /** What to use: the file's data if it has content and is not older than the copy, else the copy (null if neither has content). */
    fun choose(fromFile: BridgeData?, backup: BridgeData?): BridgeData? {
        val file = fromFile?.takeIf { it.totalEntries > 0 }
        val copy = backup?.takeIf { it.totalEntries > 0 }
        return when {
            file == null -> copy
            copy == null -> file
            copy.updated > file.updated -> copy
            else -> file
        }
    }
}
