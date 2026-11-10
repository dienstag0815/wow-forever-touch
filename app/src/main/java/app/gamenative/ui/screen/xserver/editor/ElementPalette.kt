package app.gamenative.ui.screen.xserver.editor

import android.content.Context
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.ui.screen.xserver.editor.sheet.actionBarData
import app.gamenative.wowaction.ActionBarTemplates
import app.gamenative.wowaction.ReloadGuard
import app.gamenative.wowaction.WowKeys
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowaction.WowActionSource
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.RadialMenu
import java.io.File
import androidx.annotation.StringRes
import app.gamenative.R

/**
 * Entries of the "+" gallery. [typeName] is the name of the element type for kinds that need a type of their own; a kind
 * whose type does not exist in this build is hidden (see [isAvailable]), so types added later show up by themselves.
 */
enum class PaletteKind(@StringRes val title: Int, val preview: PreviewKind, val typeName: String? = null) {
    BUTTON(R.string.editor_kind_button, PreviewKind.BUTTON),
    WIDE_BUTTON(R.string.editor_kind_wide_button, PreviewKind.WIDE_BUTTON),
    KEY_STRIP(R.string.editor_kind_key_strip, PreviewKind.KEY_STRIP, "RANGE_BUTTON"),
    D_PAD(R.string.editor_kind_d_pad, PreviewKind.D_PAD, "D_PAD"),
    STICK(R.string.editor_kind_stick, PreviewKind.STICK, "STICK"),
    STICK_RING(R.string.editor_kind_stick_ring, PreviewKind.STICK_RING, "STICK"),
    SCROLL_WHEEL(R.string.editor_kind_scroll_wheel, PreviewKind.SCROLL_WHEEL, "SCROLL_WHEEL"),
    RADIAL_MENU(R.string.editor_kind_radial, PreviewKind.RADIAL),
    ACTION_BAR_MENU(R.string.editor_kind_radial_bar, PreviewKind.RADIAL_BAR),
    GAME_MENUS(R.string.editor_kind_radial_game, PreviewKind.RADIAL_GAME),
    WOW_MENU(R.string.editor_kind_wow_menu, PreviewKind.RADIAL_GAME),
    EMOTE_MENU(R.string.editor_kind_emote, PreviewKind.EMOTE),
    VOICE(R.string.editor_kind_voice, PreviewKind.VOICE),
    MENU(R.string.editor_kind_menu, PreviewKind.MENU),
    KEYBOARD(R.string.editor_kind_keyboard, PreviewKind.KEYBOARD),
    EDIT_BUTTON(R.string.editor_kind_edit, PreviewKind.EDIT),
    RELOAD_BUTTON(R.string.editor_kind_reload, PreviewKind.RELOAD);

    val isAvailable: Boolean
        get() = typeName == null || ControlElement.Type.values().any { it.name == typeName }

    companion object {
        fun available(): List<PaletteKind> = values().filter { it.isAvailable }
    }
}

object ElementPalette {
    /**
     * Builds the element for [kind] (not yet in the profile and without a position). Radial buttons get their own menu in
     * [profile]; the emote menu is filled with the WoW emotes.
     */
    fun create(
        kind: PaletteKind,
        view: com.winlator.widget.InputControlsView,
        profile: ControlsProfile,
        context: Context,
        bar: Int = 1,
    ): ControlElement {
        val element = ControlElement(view)
        when (kind) {
            PaletteKind.BUTTON -> element.setType(ControlElement.Type.BUTTON)
            PaletteKind.WIDE_BUTTON -> {
                element.setType(ControlElement.Type.BUTTON)
                element.setShape(ControlElement.Shape.PILL)
            }
            PaletteKind.KEY_STRIP -> {
                // Several keys in one scrolling strip; starts as the A - Z template, "Eigene" in the sheet makes the entries free.
                element.setType(ControlElement.Type.RANGE_BUTTON)
                element.setShape(ControlElement.Shape.ROUND_RECT)
                element.setRange(ControlElement.Range.FROM_A_TO_Z)
            }
            PaletteKind.STICK_RING -> {
                // The ring is an option of the stick (no type of its own): 8 segments, prefilled with keys 1 to 8.
                element.setType(ControlElement.Type.STICK)
                element.setRingSegments(8)
            }
            PaletteKind.D_PAD,
            PaletteKind.STICK,
            PaletteKind.SCROLL_WHEEL -> {
                val typeName = kind.typeName ?: "BUTTON"
                val type = ControlElement.Type.values().firstOrNull { it.name == typeName } ?: ControlElement.Type.BUTTON
                element.setType(type)
            }
            PaletteKind.RADIAL_MENU -> {
                keyButton(element, Binding.OPEN_RADIAL_MENU, "Kreis")
                element.radialMenuId = profile.createButtonRadialMenu(RadialMenu.DEFAULT_BUTTON_SLOTS).id
            }
            PaletteKind.EMOTE_MENU -> {
                keyButton(element, Binding.OPEN_RADIAL_MENU, "Emote")
                val menuId = profile.createButtonRadialMenu(RadialMenu.DEFAULT_BUTTON_SLOTS).id
                element.radialMenuId = menuId
                val keys = runCatching {
                    WowActionSource.combos(WowActionSource.resolveFor(File(GamePath.load(context))))
                }.getOrNull() ?: emptyMap()
                profile.putRadialMenu(
                    WowActionCatalog.buildMenu(menuId, "WoW Emotes", WowActionCatalog.EMOTE_COMMANDS, keys),
                )
            }
            PaletteKind.ACTION_BAR_MENU -> {
                keyButton(element, Binding.OPEN_RADIAL_MENU, "Leiste $bar")
                val menuId = profile.createButtonRadialMenu(RadialMenu.DEFAULT_BUTTON_SLOTS).id
                element.radialMenuId = menuId
                profile.putRadialMenu(ActionBarTemplates.buildMenu(menuId, bar, wowKeys(context), actionBarData(context)).menu)
            }
            PaletteKind.GAME_MENUS -> {
                keyButton(element, Binding.OPEN_RADIAL_MENU, "Menüs")
                val menuId = profile.createButtonRadialMenu(RadialMenu.DEFAULT_BUTTON_SLOTS).id
                element.radialMenuId = menuId
                profile.putRadialMenu(
                    WowActionCatalog.buildMenu(menuId, "Spielmenüs", WowActionCatalog.GAME_MENU_COMMANDS, wowKeys(context)),
                )
            }
            PaletteKind.WOW_MENU -> {
                keyButton(element, Binding.OPEN_RADIAL_MENU, "WoW-Menü")
                val menuId = profile.createButtonRadialMenu(RadialMenu.DEFAULT_BUTTON_SLOTS).id
                element.radialMenuId = menuId
                profile.putRadialMenu(
                    WowActionCatalog.buildMenu(menuId, "WoW-Menü komplett", WowActionCatalog.WOW_MENU_COMMANDS, wowKeys(context)),
                )
            }
            PaletteKind.VOICE -> keyButton(element, Binding.VOICE_CHAT, "Voice")
            PaletteKind.MENU -> keyButton(element, Binding.OPEN_NAVIGATION_MENU, "Menü")
            PaletteKind.KEYBOARD -> keyButton(element, Binding.SHOW_KEYBOARD, "")
            PaletteKind.EDIT_BUTTON -> keyButton(element, Binding.OPEN_CONTROLS_EDITOR, "Edit")
            PaletteKind.RELOAD_BUTTON -> reloadButton(element, context)
        }
        return element
    }

    private fun wowKeys(context: Context): Map<String, BindingCombo> = runCatching {
        WowActionSource.combos(WowActionSource.resolveFor(File(GamePath.load(context))))
    }.getOrNull() ?: emptyMap()

    /**
     * "Neuladen": a normal button that stores the catalog action [ReloadGuard.ACTION_ID]; its key is CTRL-SHIFT-F4 (the addon's
     * secure reload button), taken from the player's resolved keys when there are any. No text: the symbol is the circular arrow.
     * A tap reloads, a second tap within 2 s is dropped ([ReloadGuard]).
     */
    private fun reloadButton(element: ControlElement, context: Context) {
        element.setType(ControlElement.Type.BUTTON)
        element.setWowAction(ReloadGuard.ACTION_ID)
        val resolved = wowKeys(context)[ReloadGuard.ACTION_ID] ?: WowKeys.toCombo(ReloadGuard.RELOAD_KEY) ?: BindingCombo.none()
        element.setBindingComboAt(0, resolved)
        element.setText("")
    }

    private fun keyButton(element: ControlElement, binding: Binding, text: String) {
        element.setType(ControlElement.Type.BUTTON)
        element.setBindingComboAt(0, BindingCombo.of(binding))
        element.setText(text)
    }
}
