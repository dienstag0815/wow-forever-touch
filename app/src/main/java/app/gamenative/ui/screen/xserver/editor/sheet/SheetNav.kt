package app.gamenative.ui.screen.xserver.editor.sheet

import com.winlator.inputcontrols.ControlElement
import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.EditorText

/** Pages of the settings sheet of an on-screen element. */
enum class SheetPage(@StringRes val title: Int) {
    OVERVIEW(R.string.editor_page_overview),
    BINDING(R.string.editor_page_binding),
    KEY_PICKER(R.string.editor_page_key_picker),
    RADIAL_MENU(R.string.editor_page_radial_menu),
    RING(R.string.editor_page_ring),
    RANGE_ITEMS(R.string.editor_page_range_items),
    APPEARANCE(R.string.editor_page_appearance),
    ADVANCED(R.string.editor_page_advanced),
}

/** What a key picker page assigns to. */
sealed interface PickTarget {
    /** Binding slot [index] of the element (primary action, direction, ...). */
    data class Slot(val index: Int) : PickTarget

    /** Key ring segment [index] of a stick. */
    data class RingSegment(val index: Int) : PickTarget

    /** Outer field of key ring segment [index] (the optional second ring of a stick). */
    data class RingOuter(val index: Int) : PickTarget

    /** Field [index] of the element's own radial menu. */
    data class RadialSlot(val index: Int) : PickTarget

    /** Entry [index] of a key strip with the free list ("Eigene"). */
    data class RangeItem(val index: Int) : PickTarget

    /** Tap on the middle of a stick ("Mitte drücken"). */
    object Center : PickTarget
}

/** One place in the sheet: a [page], for [SheetPage.KEY_PICKER] together with what it assigns to. */
data class SheetRoute(val page: SheetPage, val target: PickTarget? = null) {
    companion object {
        val OVERVIEW = SheetRoute(SheetPage.OVERVIEW)
        val BINDING = SheetRoute(SheetPage.BINDING)
        val RADIAL_MENU = SheetRoute(SheetPage.RADIAL_MENU)
        val RING = SheetRoute(SheetPage.RING)
        val RANGE_ITEMS = SheetRoute(SheetPage.RANGE_ITEMS)
        val APPEARANCE = SheetRoute(SheetPage.APPEARANCE)
        val ADVANCED = SheetRoute(SheetPage.ADVANCED)

        fun picker(target: PickTarget) = SheetRoute(SheetPage.KEY_PICKER, target)
    }
}

/**
 * Navigation inside the sheet (an immutable back stack): overview, then sub pages with a back arrow. Every function returns
 * the new state; the stack always starts with the overview. Pure Kotlin.
 */
data class SheetNav(val stack: List<SheetRoute> = listOf(SheetRoute.OVERVIEW)) {
    init {
        require(stack.isNotEmpty() && stack.first() == SheetRoute.OVERVIEW) { "stack starts with the overview" }
    }

    val current: SheetRoute get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** The page below the current one (the overview if there is none). */
    val parent: SheetRoute get() = if (stack.size > 1) stack[stack.size - 2] else SheetRoute.OVERVIEW

    /** Opens [route] on top; opening the current route again changes nothing, opening one already on the stack goes back to it. */
    fun open(route: SheetRoute): SheetNav {
        if (route == current) return this
        val existing = stack.indexOf(route)
        if (existing >= 0) return SheetNav(stack.subList(0, existing + 1).toList())
        return SheetNav(stack + route)
    }

    fun back(): SheetNav = if (stack.size > 1) SheetNav(stack.dropLast(1)) else this

    fun toOverview(): SheetNav = if (stack.size > 1) SheetNav() else this

    /** Drops every route whose page [available] does not offer (e.g. after the element type changed or an undo). */
    fun sanitize(available: Collection<SheetPage>): SheetNav {
        val kept = ArrayList<SheetRoute>()
        for (route in stack) {
            val allowed = route.page == SheetPage.OVERVIEW ||
                (route.page == SheetPage.KEY_PICKER && kept.lastOrNull()?.page.let { it == SheetPage.BINDING || it == SheetPage.RING || it == SheetPage.RADIAL_MENU || it == SheetPage.RANGE_ITEMS }) ||
                route.page in available
            if (!allowed) break
            kept.add(route)
        }
        return if (kept.size == stack.size) this else SheetNav(kept)
    }
}

/** Which pages the overview offers for an element, and the names of its binding slots. */
object SheetPages {
    /** Pages reachable from the overview, in display order. */
    fun available(type: ControlElement.Type, isRadialMenuButton: Boolean, customRange: Boolean = false): List<SheetPage> = buildList {
        if (slotCount(type) > 0) add(SheetPage.BINDING)
        if (type == ControlElement.Type.BUTTON && isRadialMenuButton) add(SheetPage.RADIAL_MENU)
        if (type == ControlElement.Type.STICK) add(SheetPage.RING)
        if (type == ControlElement.Type.RANGE_BUTTON && customRange) add(SheetPage.RANGE_ITEMS)
        add(SheetPage.APPEARANCE)
        add(SheetPage.ADVANCED)
    }

    /** Number of binding slots the player can assign (range buttons and dynamic joysticks generate their own). */
    fun slotCount(type: ControlElement.Type): Int = when (type) {
        ControlElement.Type.BUTTON, ControlElement.Type.SCROLL_WHEEL -> 2
        ControlElement.Type.D_PAD, ControlElement.Type.STICK, ControlElement.Type.TRACKPAD -> 4
        else -> 0
    }

    fun slotLabel(type: ControlElement.Type, index: Int): EditorText = when (type) {
        ControlElement.Type.BUTTON -> EditorText(if (index == 0) R.string.editor_slot_primary else R.string.editor_slot_secondary)
        ControlElement.Type.SCROLL_WHEEL ->
            EditorText(if (index == 0) R.string.editor_slot_tap_no_swipe else R.string.editor_slot_held_scroll)
        ControlElement.Type.D_PAD, ControlElement.Type.STICK -> EditorText(direction(index))
        ControlElement.Type.TRACKPAD -> EditorText(directionMouse(index))
        else -> EditorText(R.string.editor_slot_n, listOf(index + 1))
    }

    @StringRes
    private fun direction(index: Int): Int = when (index) {
        0 -> R.string.editor_dir_up
        1 -> R.string.editor_dir_right
        2 -> R.string.editor_dir_down
        else -> R.string.editor_dir_left
    }

    @StringRes
    private fun directionMouse(index: Int): Int = when (index) {
        0 -> R.string.editor_dir_up_mouse
        1 -> R.string.editor_dir_right_mouse
        2 -> R.string.editor_dir_down_mouse
        else -> R.string.editor_dir_left_mouse
    }

    /** Title of a route's page; the picker is named after what it assigns to. */
    fun title(route: SheetRoute, type: ControlElement.Type): EditorText = when (val target = route.target) {
        is PickTarget.Slot -> slotLabel(type, target.index)
        is PickTarget.RingSegment -> EditorText(R.string.editor_ring_key_n, listOf(target.index + 1))
        is PickTarget.RingOuter -> EditorText(R.string.editor_ring_outer_key_n, listOf(target.index + 1))
        is PickTarget.RadialSlot -> EditorText(R.string.editor_field_n, listOf(target.index + 1))
        is PickTarget.RangeItem -> EditorText(R.string.editor_entry_n, listOf(target.index + 1))
        PickTarget.Center -> EditorText(R.string.editor_press_center)
        null -> EditorText(route.page.title)
    }
}
