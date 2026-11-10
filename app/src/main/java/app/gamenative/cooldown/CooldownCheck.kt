package app.gamenative.cooldown

import app.gamenative.R
import app.gamenative.ui.components.TextRef

/**
 * The wording of the dialog "Symbole und Abklingzeiten prüfen" (see [CooldownCheckDialog]): from the facts about the addon
 * data and the cooldown strip it builds the summary, the detail lines and a hint. Pure Kotlin, unit tested.
 */
object CooldownCheck {
    /** What the addon TouchForever delivered: the character it wrote for and how many entries there are. */
    data class BridgeInfo(val character: String?, val realm: String?, val slots: Int, val spells: Int, val entries: Int)

    class Report(val summary: TextRef, val lines: List<TextRef>, val hint: TextRef)

    @JvmStatic
    fun build(
        iconsEnabled: Boolean,
        bridge: BridgeInfo?,
        cooldownsEnabled: Boolean,
        stripReadable: Boolean,
        stripReason: String,
        coolingCount: Int,
        lockedInCombat: Boolean,
    ): Report {
        val symbolsOk = iconsEnabled && bridge != null
        val cooldownsOk = cooldownsEnabled && stripReadable

        val addon = when {
            !iconsEnabled -> TextRef(R.string.game_cc_addon_off)
            bridge == null -> TextRef(R.string.game_cc_addon_nodata)
            else -> TextRef(R.string.game_cc_addon_found, characterOf(bridge))
        }
        val data = if (bridge == null) {
            TextRef(R.string.game_cc_data_none)
        } else {
            TextRef(R.string.game_cc_data, bridge.slots, bridge.spells, bridge.entries)
        }
        val strip = when {
            !cooldownsEnabled -> TextRef(R.string.game_cc_strip_off)
            stripReadable ->
                TextRef(if (lockedInCombat) R.string.game_cc_strip_ok_locked else R.string.game_cc_strip_ok, coolingCount)
            else -> TextRef(R.string.game_cc_strip_bad, stripReason)
        }
        val summary = TextRef(
            when {
                symbolsOk && cooldownsOk -> R.string.game_cc_sum_both
                symbolsOk -> R.string.game_cc_sum_icons
                cooldownsOk -> R.string.game_cc_sum_cooldowns
                else -> R.string.game_cc_sum_none
            },
        )
        val hint = when {
            iconsEnabled && bridge == null -> TextRef(R.string.game_cc_hint_addon)
            cooldownsEnabled && !stripReadable -> TextRef(R.string.game_cc_hint_strip)
            else -> TextRef.plain("")
        }
        return Report(
            summary = summary,
            lines = listOf(
                TextRef(R.string.game_cc_line_addon, addon),
                TextRef(R.string.game_cc_line_data, data),
                TextRef(R.string.game_cc_line_strip, strip),
            ),
            hint = hint,
        )
    }

    private fun characterOf(bridge: BridgeInfo): TextRef {
        val name = bridge.character?.takeIf { it.isNotBlank() } ?: return TextRef(R.string.game_cc_unknown_char)
        val realm = bridge.realm?.takeIf { it.isNotBlank() }
        return TextRef.plain(if (realm != null) "$name-$realm" else name)
    }
}
