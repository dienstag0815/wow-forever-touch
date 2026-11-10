package app.gamenative.wowicons

/**
 * One thing the addon wrote down: an action bar slot, a spell, a mount, a toy or a consumable.
 *
 * @property type        action type of a slot ("spell", "item", "macro", ...), "" for list entries
 * @property id          spell, item, mount or macro id as written (null for text ids such as equipment sets)
 * @property spellId     spell id behind the entry (spell, mount, macro that casts a spell), if any
 * @property itemId      item id behind the entry (item, toy, consumable, macro that uses an item), if any
 * @property iconFileId  icon file id (what `GetActionTexture` returns in current clients)
 * @property iconName    icon name taken from a texture path ("Interface\Icons\Spell_Fire_Flamebolt" -> "spell_fire_flamebolt")
 */
data class BridgeEntry(
    val type: String = "",
    val id: Long? = null,
    val spellId: Long? = null,
    val itemId: Long? = null,
    val name: String? = null,
    val iconFileId: Long? = null,
    val iconName: String? = null,
    val favorite: Boolean = false,
) {
    /** True when the launcher has something to look the icon up with. */
    val hasIconSource: Boolean get() = iconFileId != null || iconName != null || spellId != null || itemId != null
}

/** Everything the addon wrote for one character. */
data class BridgeData(
    val updated: Long,
    val playerClass: String?,
    val specName: String?,
    val character: String?,
    val realm: String?,
    val slots: Map<Int, BridgeEntry>,
    val spells: List<BridgeEntry>,
    val mounts: List<BridgeEntry>,
    val toys: List<BridgeEntry>,
    val items: List<BridgeEntry>,
) {
    /** Number of entries over all lists (not unique icons). */
    val totalEntries: Int get() = slots.size + spells.size + mounts.size + toys.size + items.size
}

object BridgeParser {
    const val VARIABLE = "TouchForeverDB"

    /** Variable of the addon before it was renamed (up to 1.4.0). */
    const val OLD_VARIABLE = "WowForeverBridgeDB"

    /** Data of the addon from the text of its SavedVariables file, null if the variable is missing or the file is broken. */
    fun parse(text: String): BridgeData? {
        val root = try {
            LuaParser.parse(text).let { it[VARIABLE] ?: it[OLD_VARIABLE] } as? LuaTable
        } catch (e: LuaParseException) {
            null
        } catch (e: StackOverflowError) {
            null
        } ?: return null

        val slots = LinkedHashMap<Int, BridgeEntry>()
        root.table("slots")?.numbered()?.forEach { (slot, value) ->
            val table = value as? LuaTable ?: return@forEach
            if (slot in 1..1000) slots[slot.toInt()] = entryOf(table, Kind.SLOT)
        }
        return BridgeData(
            updated = root.long("updated") ?: 0L,
            playerClass = root.string("class"),
            specName = root.string("specName"),
            character = root.string("character"),
            realm = root.string("realm"),
            slots = slots,
            spells = entriesOf(root, "spells", Kind.SPELL),
            mounts = entriesOf(root, "mounts", Kind.OTHER),
            toys = entriesOf(root, "toys", Kind.OTHER),
            items = entriesOf(root, "items", Kind.OTHER),
        )
    }

    private enum class Kind { SLOT, SPELL, OTHER }

    private fun entriesOf(root: LuaTable, key: String, kind: Kind): List<BridgeEntry> =
        root.table(key)?.list()?.mapNotNull { (it as? LuaTable)?.let { table -> entryOf(table, kind) } }.orEmpty()

    // The addon writes explicit "spell"/"item" fields; in the spell list the plain id is the spell id.
    private fun entryOf(table: LuaTable, kind: Kind): BridgeEntry {
        val id = table.long("id")
        return BridgeEntry(
            type = if (kind == Kind.SLOT) table.string("type").orEmpty() else "",
            id = id,
            spellId = table.long("spell") ?: if (kind == Kind.SPELL) id else null,
            itemId = table.long("item"),
            name = table.string("name")?.takeIf { it.isNotBlank() },
            iconFileId = table.long("icon")?.takeIf { it > 0 },
            iconName = iconNameOf(table.string("iconPath")),
            favorite = table.bool("fav") == true,
        )
    }

    /** "Interface\\Icons\\Spell_Fire_Flamebolt" or "interface/icons/spell_fire_flamebolt.blp" -> "spell_fire_flamebolt". */
    fun iconNameOf(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val last = path.substringAfterLast('\\').substringAfterLast('/').substringBeforeLast('.')
        val name = last.lowercase().trim()
        return name.takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }
    }
}
