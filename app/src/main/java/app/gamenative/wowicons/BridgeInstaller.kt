package app.gamenative.wowicons

import java.io.File

/** Puts the addon "TouchForever" into the game folder. Pure file code, the asset reading is done by the caller. */
object BridgeInstaller {
    const val ADDON_NAME = "TouchForever"

    /** Name of the addon up to version 1.4.0; its folder is removed and its saved settings are taken over. */
    const val OLD_ADDON_NAME = "WowForeverBridge"

    /** Folder of the bundled addon below `assets/`. */
    const val ASSET_DIR = "wow/addon/TouchForever"

    /** Flavor folders that get the addon when they exist (the full release; the beta shares the install). */
    private val FLAVOR_DIRS = listOf("_forever_", "_classic_beta_")

    /**
     * Copies [files] (name -> content) to `<gameRoot>/<flavor>/Interface/AddOns/TouchForever/` for each flavor folder
     * that exists, changing only files whose content differs. Removes the folder of the old addon name (else WoW would load
     * both) and copies its SavedVariables once to the new name ([migrateSavedVariables]). Returns the number of files written.
     */
    fun install(gameRoot: File, files: Map<String, ByteArray>): Int {
        var written = 0
        for (flavor in FLAVOR_DIRS) {
            val flavorDir = File(gameRoot, flavor)
            if (!flavorDir.isDirectory) continue
            val target = File(flavorDir, "Interface/AddOns/$ADDON_NAME")
            for ((name, content) in files) {
                val file = File(target, name)
                if (file.isFile && file.length() == content.size.toLong() && file.readBytes().contentEquals(content)) continue
                file.parentFile?.mkdirs()
                file.writeBytes(content)
                written++
            }
            File(flavorDir, "Interface/AddOns/$OLD_ADDON_NAME").takeIf { it.isDirectory }?.deleteRecursively()
            migrateSavedVariables(File(flavorDir, "WTF/Account"))
        }
        return written
    }

    /**
     * Whether the addon on the disk is exactly [files] in every existing flavor folder (and there is at least one). This is what
     * "the game will load the current addon" means; [install] followed by [verify] is the check after the copy.
     */
    fun verify(gameRoot: File, files: Map<String, ByteArray>): Boolean {
        if (files.isEmpty()) return false
        var flavors = 0
        for (flavor in FLAVOR_DIRS) {
            if (!File(gameRoot, flavor).isDirectory) continue
            flavors++
            val target = File(gameRoot, "$flavor/Interface/AddOns/$ADDON_NAME")
            for ((name, content) in files) {
                val file = File(target, name)
                if (!file.isFile || file.length() != content.size.toLong() || !file.readBytes().contentEquals(content)) return false
            }
        }
        return flavors > 0
    }

    /**
     * Copies every `SavedVariables/WowForeverBridge.lua` below [accountRoot] (account-wide and per character) to
     * `TouchForever.lua` next to it, with the variable renamed, unless that file exists already. The old file stays.
     * Returns the number of files written.
     */
    fun migrateSavedVariables(accountRoot: File): Int {
        var written = 0
        accountRoot.walkTopDown().maxDepth(5)
            .filter { it.isFile && it.name == "$OLD_ADDON_NAME.lua" && it.parentFile?.name.equals("SavedVariables", ignoreCase = true) }
            .toList()
            .forEach { old ->
                val target = File(old.parentFile, "$ADDON_NAME.lua")
                if (target.exists()) return@forEach
                val text = old.readText(Charsets.UTF_8).replace("${OLD_ADDON_NAME}DB", "${ADDON_NAME}DB")
                target.writeText(text, Charsets.UTF_8)
                written++
            }
        return written
    }

    /**
     * Makes sure WoW loads the addon even if its interface number does not match the client's: adds `SET checkAddonVersion "0"`
     * to Config.wtf unless the file already says something about it (a choice of the player is never changed).
     * Returns true if the line was added. A missing Config.wtf is left alone.
     */
    fun allowOutOfDateAddons(configWtf: File): Boolean {
        if (!configWtf.isFile) return false
        val lines = configWtf.readLines()
        if (lines.any { it.startsWith("SET checkAddonVersion ", ignoreCase = true) }) return false
        configWtf.writeText((lines + "SET checkAddonVersion \"0\"").joinToString("\n") + "\n")
        return true
    }
}
