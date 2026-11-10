package app.gamenative.wowicons

import java.io.File

/**
 * What to look an icon up with. Picture keys: `f<fileId>` (Blizzard render CDN by file id) or `n<name>` (by icon name).
 * [spellId]/[itemId] only serve to find the icon name at Wowhead when the others fail.
 */
data class IconRef(
    val fileId: Long? = null,
    val name: String? = null,
    val spellId: Long? = null,
    val itemId: Long? = null,
) {
    val isEmpty: Boolean get() = fileId == null && name == null && spellId == null && itemId == null

    /** Key of the name lookup (`spell:133`, `item:6948`), null without ids. */
    val nameKey: String?
        get() = when {
            spellId != null -> "spell:$spellId"
            itemId != null -> "item:$itemId"
            else -> null
        }

    /** Stable text identifying the request (used to avoid asking twice). */
    val id: String get() = "f$fileId/n$name/s$spellId/i$itemId"

    companion object {
        fun of(entry: BridgeEntry) = IconRef(entry.iconFileId, entry.iconName, entry.spellId, entry.itemId)
    }
}

/**
 * Disk cache of the icons in the app's private folder (never the public one): pictures, the name table (`spell:133` ->
 * icon name) and the list of things that do not exist (so they are not asked again for a week). Thread-safe.
 */
class IconCache(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val imageDir = File(dir, "img")
    private val namesFile = File(dir, "names.txt")
    private val missingFile = File(dir, "missing.txt")
    private val names = HashMap<String, String>()
    private val missing = HashMap<String, Long>()
    private val cooling = HashMap<String, Long>()

    init {
        imageDir.mkdirs()
        readPairs(namesFile) { key, value -> names[key] = value }
        readPairs(missingFile) { key, value -> value.toLongOrNull()?.let { missing[key] = it } }
    }

    fun imageFile(pictureKey: String): File = File(imageDir, "${pictureKey.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.jpg")

    /** The cached picture for [ref] (by file id first, then by name), null if there is none yet. */
    fun lookup(ref: IconRef): File? {
        ref.fileId?.let { id -> imageFile("f$id").takeIf { it.isFile }?.let { return it } }
        val name = nameOf(ref) ?: return null
        return imageFile("n$name").takeIf { it.isFile }
    }

    /** Icon name known for [ref]: its own, else from the name table. */
    @Synchronized
    fun nameOf(ref: IconRef): String? = ref.name ?: ref.nameKey?.let { names[it] }

    @Synchronized
    fun rememberName(key: String, name: String) {
        if (names[key] == name) return
        names[key] = name
        writePairs(namesFile, names)
    }

    fun store(pictureKey: String, bytes: ByteArray) {
        val target = imageFile(pictureKey)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Could not write ${target.path}" }
        }
    }

    /** True while [key] is known not to exist (permanent answers 403/404/"out of range", kept [MISSING_TTL_MS]). */
    @Synchronized
    fun isMissing(key: String): Boolean {
        val since = missing[key] ?: return false
        if (clock() - since > MISSING_TTL_MS) {
            missing.remove(key)
            return false
        }
        return true
    }

    @Synchronized
    fun markMissing(key: String) {
        missing[key] = clock()
        writePairs(missingFile, missing.mapValues { it.value.toString() })
    }

    /** True for [COOLDOWN_MS] after a network failure of [key]; not saved (the next start tries again). */
    @Synchronized
    fun isCoolingDown(key: String): Boolean {
        val until = cooling[key] ?: return false
        if (clock() >= until) {
            cooling.remove(key)
            return false
        }
        return true
    }

    @Synchronized
    fun markFailed(key: String) {
        cooling[key] = clock() + COOLDOWN_MS
    }

    /** Number of cached pictures and their size in bytes. */
    fun stats(): Pair<Int, Long> {
        val files = imageDir.listFiles { f -> f.isFile && f.name.endsWith(".jpg") } ?: return 0 to 0L
        return files.size to files.sumOf { it.length() }
    }

    /** Deletes everything (setting "Symbole zurücksetzen"). */
    @Synchronized
    fun clear() {
        imageDir.listFiles()?.forEach { it.delete() }
        names.clear()
        missing.clear()
        cooling.clear()
        namesFile.delete()
        missingFile.delete()
    }

    private fun readPairs(file: File, sink: (String, String) -> Unit) {
        try {
            if (!file.isFile) return
            for (line in file.readLines(Charsets.UTF_8)) {
                val at = line.indexOf('=')
                if (at > 0) sink(line.substring(0, at), line.substring(at + 1))
            }
        } catch (e: Exception) {
            // A damaged table only costs a new lookup.
        }
    }

    private fun writePairs(file: File, map: Map<String, String>) {
        try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(map.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }, Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            // Cache only; ignore.
        }
    }

    companion object {
        const val MISSING_TTL_MS = 7L * 24 * 60 * 60 * 1000
        const val COOLDOWN_MS = 2L * 60 * 1000
    }
}
