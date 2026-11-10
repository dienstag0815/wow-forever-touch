package app.gamenative.wowaction

import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.components.AppText

/**
 * Display of the standard texts the editor and the templates once stored in German (button texts of the palette, names of
 * the radial menus, labels of the fields). Only the display changes: the stored value stays as it is, so existing layouts keep
 * working, and a text the player typed himself is shown as typed (it matches none of the standard names).
 */
object StandardNames {
    /** Stored text (German, or the English one older versions stored) -> resource with the text in the current language. */
    private val BY_STORED: Map<String, Int> = mapOf(
        // Buttons of the palette (ElementPalette.create) and of the standard profiles.
        "Kreis" to R.string.game_std_circle,
        "Emote" to R.string.game_std_emote,
        "Menüs" to R.string.game_std_menus,
        "WoW-Menü" to R.string.game_std_wow_menu,
        "Menü" to R.string.game_key_menu,
        "MENÜ" to R.string.game_key_menu,
        "Tastatur" to R.string.game_key_keyboard,
        "Bearb." to R.string.game_key_edit,
        "Edit" to R.string.game_key_edit,
        "Sprache" to R.string.game_key_voice,
        "Voice" to R.string.game_key_voice,
        // Names of the radial menus (palette, templates of the editor).
        "Spielmenüs" to R.string.game_tpl_name_game_menus,
        "Spielmenüs 2" to R.string.game_tpl_name_game_menus_2,
        "WoW-Menü komplett" to R.string.game_tpl_name_wow_full,
        "WoW-Menü komplett 2" to R.string.game_tpl_name_wow_full_2,
        "WoW-Menü Rest" to R.string.game_tpl_name_wow_rest,
        "Angriffe" to R.string.game_tpl_name_attacks,
        "Oberfläche" to R.string.game_tpl_name_interface,
        "WoW Emotes" to R.string.game_radial_emotes,
        "WoW Angriffe" to R.string.game_radial_attacks,
        "WoW Spielmenüs" to R.string.game_radial_game_menus,
    )

    /** "Leiste 3" (action bar button of the palette) -> "Bar 3". */
    private val BAR_LABEL = Regex("""^Leiste (\d{1,2})$""")

    /** German names of the catalog that belong to exactly one action (so a stored "Taschen" is known without the action id). */
    private val ACTION_BY_NAME_DE: Map<String, WowAction> by lazy {
        WowActionCatalog.all.groupBy { it.nameDe }.filterValues { it.size == 1 }.mapValues { it.value.first() }
    }

    /** All stored standard texts (for tests). */
    val storedTexts: Set<String> get() = BY_STORED.keys

    /** Resource of a stored standard text, null = not a standard text. */
    @JvmStatic
    @StringRes
    fun resOf(stored: String?): Int? = stored?.trim()?.let { BY_STORED[it] }

    /** Number of the bar of a stored "Leiste n", else null. */
    @JvmStatic
    fun barOf(stored: String?): Int? = BAR_LABEL.matchEntire(stored?.trim().orEmpty())?.groupValues?.get(1)?.toIntOrNull()

    /** [stored] as the player should read it in the current language; unknown texts come back unchanged. */
    @JvmStatic
    fun displayLabel(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        barOf(stored)?.let { return AppText.get(R.string.game_std_bar, it) }
        resOf(stored)?.let { return AppText.get(it) }
        return stored
    }

    /** True when [label] is only the name of [action] in German, in English or its short form (not a text the player wrote). */
    @JvmStatic
    fun isActionName(label: String?, action: WowAction?): Boolean {
        val trimmed = label?.trim().orEmpty()
        return action != null && trimmed.isNotEmpty() &&
            (trimmed == action.nameDe || trimmed == action.nameEn || trimmed == action.shortName)
    }

    /**
     * Label of a field of a ring or radial menu. A label that only repeats the name of the field's WoW action (in either
     * language, or its short form) is shown with the name of the current language; a German standard name of the catalog
     * without an action id as well; everything else like [displayLabel].
     */
    @JvmStatic
    fun slotLabel(stored: String?, wowAction: String?): String {
        if (stored.isNullOrBlank()) return stored.orEmpty()
        val trimmed = stored.trim()
        val action = WowActionCatalog.find(wowAction)
        if (action != null) {
            if (isActionName(trimmed, action)) return action.wheelLabel
        } else {
            ACTION_BY_NAME_DE[trimmed]?.let { return it.wheelLabel }
        }
        return displayLabel(stored)
    }
}
