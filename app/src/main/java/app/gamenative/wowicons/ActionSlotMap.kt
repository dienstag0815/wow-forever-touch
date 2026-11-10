package app.gamenative.wowicons

import java.io.File

/**
 * Which action bar slot (the number `GetActionInfo` takes) a key binding command stands for.
 *
 * WoW numbers the slots in pages of 12: page 1 = slots 1-12 (the main bar, binding `ACTIONBUTTONn`), page 2 = 13-24,
 * page 3 = 25-36 (right bar, `MULTIACTIONBAR3BUTTONn`), page 4 = 37-48 (second right bar, `MULTIACTIONBAR4BUTTONn`),
 * page 5 = 49-60 (bottom right, `MULTIACTIONBAR2BUTTONn`), page 6 = 61-72 (bottom left, `MULTIACTIONBAR1BUTTONn`),
 * pages 13-15 = 145-180 (bars 6 to 8, `MULTIACTIONBAR5..7BUTTONn`). Slots 73-120 are the stance/form pages of the main bar:
 * while a stance page is active the binding `ACTIONBUTTONn` presses one of those instead of slot n; the map does not follow
 * that (documented limit: it always shows page 1).
 */
object ActionSlotMap {
    private val MAIN = Regex("^ACTIONBUTTON(\\d{1,2})$")
    private val MULTI = Regex("^MULTIACTIONBAR([1-7])BUTTON(\\d{1,2})$")

    /** First slot minus one of the multi bars 1 to 7. */
    private val MULTI_OFFSET = intArrayOf(0, 60, 48, 24, 36, 144, 156, 168)

    const val BUTTONS_PER_BAR = 12

    /** Slot 1..180 for a command id of the catalog (`ACTIONBUTTON3`, `MULTIACTIONBAR2BUTTON12`), null for everything else. */
    @JvmStatic
    fun slotFor(command: String?): Int? {
        if (command == null) return null
        MAIN.matchEntire(command)?.let { m ->
            val n = m.groupValues[1].toInt()
            return if (n in 1..BUTTONS_PER_BAR) n else null
        }
        MULTI.matchEntire(command)?.let { m ->
            val bar = m.groupValues[1].toInt()
            val n = m.groupValues[2].toInt()
            return if (n in 1..BUTTONS_PER_BAR) MULTI_OFFSET[bar] + n else null
        }
        return null
    }
}

/** Finds the SavedVariables file of the addon below the WoW folder. */
object BridgeLocator {
    const val FILE_NAME = "TouchForever.lua"

    /** File of the addon before it was renamed (up to 1.4.0); read only where no new file exists yet. */
    const val OLD_FILE_NAME = "WowForeverBridge.lua"
    private val FLAVOR_DIRS = listOf("_forever_", "_classic_beta_")

    /**
     * The file written most recently of all `WTF/Account/<account>/<realm>/<character>/SavedVariables/TouchForever.lua`
     * (the character played last; several accounts and characters can exist), in the first flavor folder that has one.
     * The account-wide location `WTF/Account/<account>/SavedVariables/` counts as well. Without any new file the old name
     * [OLD_FILE_NAME] is searched the same way. Null if there is none.
     */
    fun find(gameRoot: File): File? = find(gameRoot, FILE_NAME) ?: find(gameRoot, OLD_FILE_NAME)

    private fun find(gameRoot: File, fileName: String): File? {
        for (flavor in FLAVOR_DIRS) {
            val accountRoot = File(gameRoot, "$flavor/WTF/Account")
            val accounts = accountRoot.listFiles { f -> f.isDirectory } ?: continue
            val candidates = ArrayList<File>()
            for (account in accounts) {
                File(account, "SavedVariables/$fileName").takeIf { it.isFile }?.let(candidates::add)
                account.listFiles { f -> f.isDirectory && !f.name.equals("SavedVariables", ignoreCase = true) }?.forEach { realm ->
                    realm.listFiles { f -> f.isDirectory }?.forEach { character ->
                        File(character, "SavedVariables/$fileName").takeIf { it.isFile }?.let(candidates::add)
                    }
                }
            }
            val best = candidates.sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.path }).firstOrNull()
            if (best != null) return best
        }
        return null
    }

    /** Reads and parses [file]; null on any problem. */
    fun read(file: File): BridgeData? =
        try {
            BridgeParser.parse(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
}
