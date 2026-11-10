package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import app.gamenative.ui.screen.wow.WindowVisibility
import app.gamenative.ui.screen.wow.chatbar.ChatButton
import app.gamenative.ui.screen.xserver.editor.ControlsEditorSession
import app.gamenative.wowaction.StandardNames
import app.gamenative.wowaction.WowAction
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.RadialMenu
import com.winlator.widget.InputControlsView
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.EditorText

/**
 * State of the settings sheet of the controls editor: where the player is in the sheet ([nav]), the change tracking that
 * turns live changes into undo steps. One instance lives as long as the editor.
 *
 * Every change goes through [change] (discrete: chip, switch, key) or [live] (slider, typing; ended by [flush]), so the
 * game picture shows it at once and the editor's undo history gets exactly one step per change.
 */
class ElementSheetState(val session: ControlsEditorSession, val view: InputControlsView) {
    var nav by mutableStateOf(SheetNav())
        private set

    /** Counts every change; pages read it so they redraw after the (plain Java) element changed. */
    var tick by mutableIntStateOf(0)
        private set

    private val tracker = ChangeTracker(
        refresh = {
            view.invalidate()
            tick++
        },
        commit = { session.commit() },
    )

    val pending: Boolean get() = tracker.pending

    init {
        // Undo and redo of the top bar first finish what is still being typed (see ControlsEditorSession.flushPending).
        session.flushPending = { tracker.flush() }
    }

    // Field of the preview (radial menu or ring) chosen last; kept while the picker is open, so the player can click through.
    private var selectedFieldState by mutableStateOf<Pair<ControlElement, Int>?>(null)

    /** Index of the field of [element] marked in the preview, -1 if none. */
    fun selectedField(element: ControlElement): Int = selectedFieldState?.takeIf { it.first === element }?.second ?: -1

    fun selectField(element: ControlElement, index: Int) {
        selectedFieldState = if (index < 0) null else element to index
    }

    fun open(route: SheetRoute) {
        flush()
        nav = nav.open(route)
    }

    fun back() {
        flush()
        nav = nav.back()
    }

    fun toOverview() {
        flush()
        nav = nav.toOverview()
    }

    /** Back to the pages the element offers (after an undo or a type change). */
    fun sanitize(element: ControlElement) {
        val pages = SheetPages.available(element.type, element.isRadialMenuButton, element.isCustomRange)
        nav = nav.sanitize(pages)
    }

    /** One undo step for [block]. */
    fun change(block: () -> Unit) = tracker.discrete(block)

    /** Applies [block] now; the undo step comes with [flush]. */
    fun live(block: () -> Unit) = tracker.continuous(block)

    fun flush() = tracker.flush()

    /** Redraws the sheet and the picture without an undo step (for changes the session already knows). */
    fun touch() {
        view.invalidate()
        tick++
    }

    /** Own menu of a radial button; created (with the number keys 1 to 6) if the button has none yet. */
    fun ensureRadialMenu(element: ControlElement): String? {
        val profile = view.profile ?: return null
        val id = element.radialMenuId
        if (id != null && profile.findRadialMenu(id) != null) return id
        val menu = profile.createButtonRadialMenu(RadialMenu.DEFAULT_BUTTON_SLOTS)
        element.radialMenuId = menu.id
        return menu.id
    }

    companion object {
        private const val PREFS = "editor_sheet"
        private const val KEY_RECENT = "recent_actions"

        /** WoW actions chosen last, newest first (kept across sessions). */
        fun loadRecent(context: Context): RecentList =
            RecentList.parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECENT, null))

        fun saveRecent(context: Context, recent: RecentList) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RECENT, recent.serialize()).apply()
        }
    }
}

/**
 * What the key picker needs to know about the thing it assigns to, and how to store the three kinds of choice
 * (plain key, WoW action, free chat command). The callbacks apply the choice to the element at once.
 */
internal class PickSpec(
    val title: EditorText,
    val currentCombo: BindingCombo,
    val currentWowActionId: String?,
    val currentChatCommand: String?,
    val currentChatLabel: String?,
    val allowWow: Boolean,
    val onKey: (BindingCombo?) -> Unit,
    val onWow: (WowAction, BindingCombo?) -> Unit,
    val onChat: (String, String) -> Unit,
    /** Chat control (voice, send, keyboard, channel, cancel) the button runs now; only buttons offer them ([allowChatAction]). */
    val currentChatAction: String? = null,
    val allowChatAction: Boolean = false,
    val onChatAction: (ChatButton) -> Unit = {},
)

/** Builds the [PickSpec] of [target] on [element], or null if the target does not exist (e.g. after an undo). */
internal fun ElementSheetState.pickSpecFor(element: ControlElement, target: PickTarget): PickSpec? = when (target) {
    is PickTarget.Slot -> slotSpec(element, target.index)
    is PickTarget.RingSegment -> ringSpec(element, target.index)
    is PickTarget.RingOuter -> ringOuterSpec(element, target.index)
    is PickTarget.RadialSlot -> radialSpec(element, target.index)
    is PickTarget.RangeItem -> rangeItemSpec(element, target.index)
    PickTarget.Center -> centerSpec(element)
}

private fun ElementSheetState.slotSpec(element: ControlElement, index: Int): PickSpec? {
    if (index !in 0 until SheetPages.slotCount(element.type)) return null
    val first = index == 0
    val isButton = element.type == ControlElement.Type.BUTTON
    val afterBinding = {
        // A button switched to the radial menu key gets its own menu with the number keys.
        if (element.isRadialMenuButton && element.radialMenuId == null) ensureRadialMenu(element)
    }
    return PickSpec(
        title = SheetPages.slotLabel(element.type, index),
        currentCombo = element.getBindingComboAt(index),
        currentWowActionId = if (first) element.wowAction else null,
        currentChatCommand = if (first && element.wowAction == null) element.chatCommand else null,
        currentChatLabel = if (first) element.text else null,
        allowWow = isButton && first,
        currentChatAction = if (first) element.chatAction else null,
        allowChatAction = isButton && first,
        onChatAction = { button ->
            change {
                // A chat control replaces key, WoW action and chat command; the symbol replaces the text. Chat works with big windows
                // open, so the button stays visible over them unless the player chose otherwise.
                element.wowAction = null
                element.chatCommand = null
                element.chatAction = button.id
                element.setBindingComboAt(0, BindingCombo.none())
                element.setText(null)
                if (element.windowRule == null) element.windowRule = WindowVisibility.KEEP.key
            }
        },
        onKey = { combo ->
            change {
                // A plain key replaces a WoW action, chat command or chat control.
                val oldLabel = element.getBindingComboAt(0).toString()
                if (first) {
                    element.wowAction = null
                    element.chatCommand = null
                    element.chatAction = null
                }
                if (combo != null) element.setBindingComboAt(index, combo) else element.setBindingAt(index, Binding.NONE)
                if (isButton && first) {
                    val ownText = element.text
                    if (ownText.isNullOrEmpty() || ownText == oldLabel) element.setText(null)
                }
                afterBinding()
            }
        },
        onWow = { action, combo ->
            change {
                // The button stores the action and the key WoW currently has for it; the symbol replaces the text.
                element.wowAction = action.command
                element.chatCommand = action.chatCommand
                element.chatAction = null
                element.setBindingComboAt(0, if (action.chatCommand != null) BindingCombo.none() else combo ?: BindingCombo.none())
                element.setText(null)
            }
        },
        onChat = { label, command ->
            change {
                // Free chat command: the button types the text into the WoW chat, no key; the label is its text.
                element.wowAction = null
                element.chatCommand = command
                element.chatAction = null
                element.setBindingComboAt(0, BindingCombo.none())
                element.setText(label.ifBlank { command })
            }
        },
    )
}

private fun ElementSheetState.ringSpec(element: ControlElement, index: Int): PickSpec? {
    if (element.type != ControlElement.Type.STICK || index !in 0 until element.ringSegments) return null
    return ringSlotSpec(EditorText(R.string.editor_ring_key_n, listOf(index + 1)), element.getRingItem(index))
}

/** Outer field of segment [index] (second ring); null if the segment has none (e.g. after an undo). */
private fun ElementSheetState.ringOuterSpec(element: ControlElement, index: Int): PickSpec? {
    if (element.type != ControlElement.Type.STICK || index !in 0 until element.ringSegments) return null
    val item = element.getRingOuterItem(index) ?: return null
    return ringSlotSpec(EditorText(R.string.editor_ring_outer_key_n, listOf(index + 1)), item)
}

private fun ElementSheetState.ringSlotSpec(title: EditorText, item: RadialMenu.Slot): PickSpec {
    return PickSpec(
        title = title,
        currentCombo = item.bindingCombo,
        currentWowActionId = item.wowAction,
        currentChatCommand = item.chatCommand.takeIf { item.wowAction == null },
        currentChatLabel = item.label,
        allowWow = true,
        onKey = { combo ->
            change {
                item.setWowAction(null)
                item.setChatCommand(null)
                item.setBindingCombo(
                    if (combo?.bindings?.contains(Binding.OPEN_RADIAL_MENU) == true) BindingCombo.none() else combo ?: BindingCombo.none(),
                )
                // An own label is kept, otherwise the key names the segment.
            }
        },
        onWow = { action, combo ->
            change {
                // The symbol of the action names the segment, an empty label lets it show.
                item.setWowAction(action.command)
                item.setChatCommand(action.chatCommand)
                item.setBindingCombo(if (action.chatCommand != null) BindingCombo.none() else combo ?: BindingCombo.none())
                item.setLabel("")
            }
        },
        onChat = { label, command ->
            change {
                item.setWowAction(null)
                item.setChatCommand(command)
                item.setBindingCombo(BindingCombo.none())
                // No label given: the ring names the segment after the command (automatic).
                item.setLabel(label)
            }
        },
    )
}

/** Entry [index] of a key strip with the free list; null if it is gone (e.g. after an undo or another range). */
private fun ElementSheetState.rangeItemSpec(element: ControlElement, index: Int): PickSpec? {
    if (element.type != ControlElement.Type.RANGE_BUTTON || !element.isCustomRange) return null
    val item = element.getRangeItem(index) ?: return null
    return ringSlotSpec(EditorText(R.string.editor_entry_n, listOf(index + 1)), item)
}

private fun ElementSheetState.centerSpec(element: ControlElement): PickSpec? {
    if (element.type != ControlElement.Type.STICK) return null
    val item = element.getCenterItem()
    return PickSpec(
        title = EditorText(R.string.editor_press_center),
        currentCombo = item.bindingCombo,
        currentWowActionId = item.wowAction,
        currentChatCommand = item.chatCommand.takeIf { item.wowAction == null },
        currentChatLabel = item.label,
        allowWow = true,
        onKey = { combo ->
            change {
                item.setWowAction(null)
                item.setChatCommand(null)
                item.setLabel("")
                item.setBindingCombo(
                    if (combo?.bindings?.contains(Binding.OPEN_RADIAL_MENU) == true) BindingCombo.none() else combo ?: BindingCombo.none(),
                )
            }
        },
        onWow = { action, combo ->
            change {
                // The symbol of the action names the middle; an empty label lets it show.
                item.setWowAction(action.command)
                item.setChatCommand(action.chatCommand)
                item.setBindingCombo(if (action.chatCommand != null) BindingCombo.none() else combo ?: BindingCombo.none())
                item.setLabel("")
            }
        },
        onChat = { label, command ->
            change {
                item.setWowAction(null)
                item.setChatCommand(command)
                item.setBindingCombo(BindingCombo.none())
                item.setLabel(label)
            }
        },
    )
}

private fun ElementSheetState.radialSpec(element: ControlElement, index: Int): PickSpec? {
    val profile = view.profile ?: return null
    val menu = profile.getRadialMenu(element.radialMenuId)
    val slot = menu.slots.getOrNull(index) ?: return null
    return PickSpec(
        title = EditorText(R.string.editor_field_n, listOf(index + 1)),
        currentCombo = slot.bindingCombo,
        currentWowActionId = slot.wowAction,
        currentChatCommand = slot.chatCommand.takeIf { slot.wowAction == null },
        currentChatLabel = slot.label,
        allowWow = true,
        onKey = { combo ->
            change {
                slot.setWowAction(null)
                slot.setChatCommand(null)
                slot.setBindingCombo(
                    if (combo?.bindings?.contains(Binding.OPEN_RADIAL_MENU) == true) BindingCombo.none() else combo ?: BindingCombo.none(),
                )
            }
        },
        onWow = { action, combo ->
            change {
                // Name the field after the action unless the player wrote an own label.
                val previous = app.gamenative.wowaction.WowActionCatalog.find(slot.wowAction)
                if (slot.label.isNullOrBlank() || slot.label == previous?.wheelLabel || StandardNames.isActionName(slot.label, previous)) slot.setLabel(action.wheelLabel)
                slot.setWowAction(action.command)
                slot.setChatCommand(action.chatCommand)
                slot.setBindingCombo(if (action.chatCommand != null) BindingCombo.none() else combo ?: BindingCombo.none())
            }
        },
        onChat = { label, command ->
            change {
                slot.setWowAction(null)
                slot.setChatCommand(command)
                slot.setBindingCombo(BindingCombo.none())
                slot.setLabel(label.ifBlank { command.take(14) })
            }
        },
    )
}
