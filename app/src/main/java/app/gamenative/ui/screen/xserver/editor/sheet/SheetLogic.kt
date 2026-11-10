package app.gamenative.ui.screen.xserver.editor.sheet

import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo

/**
 * Turns the changes made in the sheet into undo steps. Every change is applied to the element at once (live), then
 * [refresh] redraws the game picture and the sheet. A discrete change (chip, switch, key) ends with one [commit]; a
 * continuous one (slider, typing) only marks the state as pending and [flush] commits it once when the finger lifts, the
 * typing pauses, or the page changes. The session's commit ignores unchanged profiles, so a flush without change is harmless.
 * Pure Kotlin.
 */
class ChangeTracker(private val refresh: () -> Unit, private val commit: () -> Unit) {
    /** A continuous change was applied and not yet committed. */
    var pending = false
        private set

    /** Applies [block] and records it as one undo step. */
    fun discrete(block: () -> Unit) {
        block()
        refresh()
        pending = false
        commit()
    }

    /** Applies [block] without an undo step yet; call [flush] when the gesture ends. */
    fun continuous(block: () -> Unit) {
        block()
        refresh()
        pending = true
    }

    /** Commits a pending continuous change as one undo step. */
    fun flush() {
        if (!pending) return
        pending = false
        commit()
    }
}

/** The last [limit] items used, newest first, without duplicates (WoW actions in the key picker: "Zuletzt benutzt"). */
class RecentList(private val limit: Int = 6, initial: List<String> = emptyList()) {
    private val items = ArrayList<String>()

    init {
        require(limit >= 1) { "limit must be at least 1" }
        initial.filter { it.isNotBlank() }.distinct().take(limit).forEach { items.add(it) }
    }

    val values: List<String> get() = items.toList()

    fun use(item: String) {
        if (item.isBlank()) return
        items.remove(item)
        items.add(0, item)
        while (items.size > limit) items.removeAt(items.size - 1)
    }

    /** One line for the preferences. */
    fun serialize(): String = items.joinToString("\n")

    companion object {
        fun parse(text: String?, limit: Int = 6): RecentList =
            RecentList(limit, text?.split('\n').orEmpty())
    }
}

/**
 * Splits [items] into those whose id is in [recentIds] (in the order of [recentIds], newest first) and the rest (original order).
 * Used for the "Zuletzt benutzt" row at the top of the WoW action list.
 */
fun <T> withRecentsFirst(items: List<T>, recentIds: List<String>, idOf: (T) -> String): Pair<List<T>, List<T>> {
    val byId = items.associateBy(idOf)
    val recent = recentIds.mapNotNull { byId[it] }
    val recentSet = recent.map(idOf).toSet()
    return recent to items.filter { idOf(it) !in recentSet }
}

enum class SheetSide { LEFT, RIGHT, TOP, BOTTOM }

/** Where the sheet sits so that the edited element stays visible. Pure Kotlin. */
object SheetPlacement {
    /** From this screen width on (inner display) the sheet is a side panel, below it (outer display, portrait) a sheet above or below. */
    const val SIDE_PANEL_MIN_WIDTH_DP = 600

    /**
     * @param elementX x of the element and [viewWidth] width of the controls view (same unit)
     * @param elementY y of the element and [viewHeight] height of the controls view
     */
    fun choose(screenWidthDp: Int, elementX: Float, elementY: Float, viewWidth: Float, viewHeight: Float): SheetSide =
        if (screenWidthDp >= SIDE_PANEL_MIN_WIDTH_DP) {
            if (elementX > viewWidth / 2f) SheetSide.LEFT else SheetSide.RIGHT
        } else {
            if (elementY > viewHeight / 2f) SheetSide.TOP else SheetSide.BOTTOM
        }

    /** Width of the side panel: about 40 % of the screen, but not narrower than 320 dp or wider than 460 dp. */
    fun panelWidthDp(screenWidthDp: Int): Int = (screenWidthDp * 0.4f).toInt().coerceIn(320, 460)
}

/**
 * The keys chosen in the key picker. A new key replaces the chosen one; with [comboMode] on keys are added up to a
 * combination (e.g. Shift + 1). [clear] only empties the choice - the picker stays open, ready for a new key. Pure Kotlin
 * apart from the binding classes.
 */
class BindingSelection(initial: BindingCombo?) {
    private val chosen = ArrayList<Binding>()

    var comboMode: Boolean = false
        private set
    var mode: BindingCombo.Mode = BindingCombo.Mode.SIMULTANEOUS
        private set
    var sequenceDelayMs: Int = BindingCombo.DEFAULT_SEQUENCE_DELAY_MS
        private set

    init {
        if (initial != null && !initial.isEmpty) {
            chosen.addAll(initial.bindings.filter { it != Binding.NONE })
            comboMode = chosen.size > 1
            mode = initial.mode
            sequenceDelayMs = initial.sequenceDelayMs
        }
    }

    val keys: List<Binding> get() = chosen.toList()
    val isEmpty: Boolean get() = chosen.isEmpty()

    fun setComboMode(on: Boolean) {
        comboMode = on
    }

    fun setMode(value: BindingCombo.Mode) {
        mode = value
    }

    fun setDelay(ms: Int) {
        sequenceDelayMs = ms.coerceIn(BindingCombo.MIN_SEQUENCE_DELAY_MS, BindingCombo.MAX_SEQUENCE_DELAY_MS)
    }

    /** Whether [binding] can be tapped now (a full combination only lets chosen keys be removed again). */
    fun canTap(binding: Binding): Boolean = binding in chosen || !comboMode || chosen.size < BindingCombo.MAX_BINDINGS

    /** Taps [binding]; returns true if the choice changed. */
    fun tap(binding: Binding): Boolean {
        if (binding == Binding.NONE) return clear()
        if (!canTap(binding)) return false
        when {
            binding in chosen -> chosen.remove(binding)
            !comboMode -> {
                chosen.clear()
                chosen.add(binding)
            }
            else -> chosen.add(binding)
        }
        return true
    }

    fun remove(binding: Binding): Boolean = chosen.remove(binding)

    /** Empties the choice (and the combination switch); returns true if there was something. */
    fun clear(): Boolean {
        val had = chosen.isNotEmpty()
        chosen.clear()
        comboMode = false
        return had
    }

    /** The choice as a binding, null if empty. */
    fun toCombo(): BindingCombo? {
        val combo = BindingCombo.fromBindings(chosen, mode, sequenceDelayMs)
        return if (combo.isEmpty) null else combo
    }
}
