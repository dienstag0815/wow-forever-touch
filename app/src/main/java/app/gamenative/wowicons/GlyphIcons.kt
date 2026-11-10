package app.gamenative.wowicons

/**
 * WoW icon names for the fixed entries of the long-press menu (the glyph names of `ContextGlyph`), loaded at run time exactly like the
 * game menu icons ([MenuIcons]): from the game's icon servers into the app's private cache, nothing is shipped in the app.
 *
 * Why icons and not the mouse cursor pictures of the game (`Interface\\Cursor\\Attack`, `Speak`, `Pickup`, `Skin` ...): those are
 * textures inside the game's archive, not icons; neither the Blizzard render server nor Wowhead serves them (checked 10.11.2026: HTTP
 * 403 / 404), and the app has no reader for the game archive and its picture format. Where no icon fits, the menu draws its own symbol.
 *
 * All names were checked on 10.11.2026 with a request to both icon servers (HTTP 200 and a JPEG body at
 * `render.worldofwarcraft.com/us/icons/56/<name>.jpg` and `wow.zamimg.com/images/wow/icons/large/<name>.jpg`).
 */
object GlyphIcons {
    private val NAMES: Map<String, String> = linkedMapOf(
        "ATTACK" to "ability_meleedamage",
        "SKIN" to "inv_misc_pelt_wolf_01",
        "LOOT" to "inv_misc_bag_10",
        "SELL" to "inv_misc_coin_01",
    )

    /** Icon name for a glyph name, null for glyphs that have only the drawn symbol. */
    @JvmStatic
    fun nameFor(glyph: String?): String? = if (glyph == null) null else NAMES[glyph]

    /** All icons to load in the background at the start. */
    fun refs(): List<IconRef> = NAMES.values.distinct().map { IconRef(name = it) }
}
