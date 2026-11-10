package app.gamenative.wowicons

/**
 * WoW icon names (as in `Interface\Icons\<name>`) for the game menu actions of the catalog (map, talents, bags ...).
 * [WowIcons] loads them the same way as the spell icons (Blizzard render CDN, Wowhead CDN as second source),
 * caches them privately and draws them instead of the vector symbol. Without picture the symbol/emoji stays.
 *
 * All names were checked on 08.11.2026 with a request to both CDNs (HTTP 200 and a JPEG body at
 * `render.worldofwarcraft.com/us/icons/56/<name>.jpg` and `wow.zamimg.com/images/wow/icons/large/<name>.jpg`).
 */
object MenuIcons {
    private val NAMES: Map<String, String> = linkedMapOf(
        "TOGGLEWORLDMAP" to "inv_misc_map_01",
        "TOGGLETALENTS" to "ability_marksmanship",
        "TOGGLEPROFESSIONBOOK" to "trade_blacksmithing",
        "TOGGLESPELLBOOK" to "inv_misc_book_09",
        "OPENALLBAGS" to "inv_misc_bag_08",
        "TOGGLEQUESTLOG" to "inv_misc_note_01",
        "TOGGLECHARACTER0" to "inv_shirt_white_01",
        "TOGGLEGROUPFINDER" to "inv_misc_groupneedmore",
        "TOGGLECOLLECTIONS" to "ability_mount_ridinghorse",
        "TOGGLEACHIEVEMENT" to "achievement_general",
        "TOGGLEGUILDTAB" to "inv_shirt_guildtabard_01",
        "TOGGLESOCIAL" to "achievement_guildperk_everybodysfriend",
        "TOGGLEENCOUNTERJOURNAL" to "inv_misc_book_06",
        "TOGGLEGAMEMENU" to "inv_misc_gear_01",
    )

    /** Icon name for the action id, null for actions without a real icon (action bar slots show the icon of their spell). */
    @JvmStatic
    fun nameFor(command: String?): String? = if (command == null) null else NAMES[command]

    /** Action ids that have an icon, in menu order. */
    val commands: List<String> get() = NAMES.keys.toList()

    /** All icons to load in the background at the start (each name once). */
    fun refs(): List<IconRef> = NAMES.values.distinct().map { IconRef(name = it) }
}
