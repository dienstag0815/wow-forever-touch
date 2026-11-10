package app.gamenative.ui.touchstyle

import app.gamenative.wowaction.BindingCaption
import app.gamenative.wowaction.WowActionCatalog
import com.winlator.inputcontrols.Binding
import com.winlator.inputcontrols.BindingCombo
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Symbols instead of big words on the touch keys (job tastensymbole). Pure Kotlin without Android classes: which key gets which
 * symbol and how big a label may be. The drawing is in [KeyGlyphDraw]; the editor can ask [glyphFor] / [labelFor] for a preview.
 *
 * Only automatic captions (field without own label, no WoW action, no chat command) are replaced; own labels and emojis stay.
 */
enum class Glyph(
    /** Width divided by height of the symbol (its height is [KeyGlyphs.GLYPH_HEIGHT] of the font size). */
    val aspect: Float,
) {
    SPACE(1.6f),
    TAB(1.3f),
    SHIFT(0.95f),
    ENTER(1.1f),
    BACKSPACE(1.4f),
    ARROW_UP(0.9f),
    ARROW_DOWN(0.9f),
    ARROW_LEFT(0.9f),
    ARROW_RIGHT(0.9f),
    MOUSE_LEFT(0.7f),
    MOUSE_RIGHT(0.7f),
    MOUSE_MIDDLE(0.7f),
    WHEEL_UP(1.2f),
    WHEEL_DOWN(1.2f),
}

/** One piece of a label: a symbol ([glyph] set, [text] empty) or a piece of text ([glyph] null). */
data class LabelPart(val glyph: Glyph?, val text: String)

/**
 * What a key shows when at least one of its keys has a symbol: the pieces from left to right ("Shift 1" gives the shift symbol
 * and "1"). [fallbackText] is the old short text for places that cannot draw a symbol.
 */
class KeyLabel(val parts: List<LabelPart>, val fallbackText: String) {
    val hasText: Boolean = parts.any { it.glyph == null }
    val glyphCount: Int = parts.count { it.glyph != null }

    override fun toString(): String = "KeyLabel($parts)"
}

object KeyGlyphs {
    /** Height of a symbol in units of the font size (letters are about 0.7 high, symbols look right a little bigger). */
    const val GLYPH_HEIGHT = 0.85f

    /** Gap between two pieces of a label in units of the font size. */
    const val PART_GAP = 0.1f

    private val NO_LABEL = KeyLabel(emptyList(), "")
    private val cache = ConcurrentHashMap<BindingCombo, KeyLabel>()
    private const val CACHE_LIMIT = 256

    /** The symbol of one key, or null if the key keeps its text (Strg, Alt, letters, digits, gamepad, mouse movement ...). */
    @JvmStatic
    fun glyphFor(binding: Binding?): Glyph? = when (binding) {
        Binding.KEY_SPACE -> Glyph.SPACE
        Binding.KEY_TAB -> Glyph.TAB
        Binding.KEY_SHIFT_L, Binding.KEY_SHIFT_R -> Glyph.SHIFT
        Binding.KEY_ENTER -> Glyph.ENTER
        Binding.KEY_BKSP -> Glyph.BACKSPACE
        Binding.KEY_UP -> Glyph.ARROW_UP
        Binding.KEY_DOWN -> Glyph.ARROW_DOWN
        Binding.KEY_LEFT -> Glyph.ARROW_LEFT
        Binding.KEY_RIGHT -> Glyph.ARROW_RIGHT
        Binding.MOUSE_LEFT_BUTTON -> Glyph.MOUSE_LEFT
        Binding.MOUSE_RIGHT_BUTTON -> Glyph.MOUSE_RIGHT
        Binding.MOUSE_MIDDLE_BUTTON -> Glyph.MOUSE_MIDDLE
        Binding.MOUSE_SCROLL_UP -> Glyph.WHEEL_UP
        Binding.MOUSE_SCROLL_DOWN -> Glyph.WHEEL_DOWN
        else -> null
    }

    /**
     * The label of a key (combination), or null when none of its keys has a symbol (then the caller keeps drawing the text). A
     * shift sign sits directly in front of its key ("shift 1"), other keys are joined by "+" (sequence: arrow). Cached, so the
     * drawing code can ask every frame without allocating.
     */
    @JvmStatic
    fun labelFor(combo: BindingCombo?): KeyLabel? {
        if (combo == null || combo.isEmpty) return null
        var label = cache[combo]
        if (label == null) {
            if (cache.size >= CACHE_LIMIT) cache.clear()
            label = build(combo) ?: NO_LABEL
            cache[combo] = label
        }
        return if (label === NO_LABEL) null else label
    }

    /**
     * Label for a caption that [BindingCaption.forSlot] produced: only when it is the automatic short name of the key (no own
     * label, no WoW action, no chat command), else null.
     */
    @JvmStatic
    fun forCaption(caption: BindingCaption.Caption, wowAction: String?, chatCommand: String?, combo: BindingCombo?): KeyLabel? {
        if (!caption.automatic || caption.symbol != null) return null
        if (WowActionCatalog.find(wowAction) != null) return null
        if (!chatCommand.isNullOrBlank()) return null
        return labelFor(combo)
    }

    /** The old text of a key (combination) for places without symbols. */
    @JvmStatic
    fun fallbackText(combo: BindingCombo?): String = BindingCaption.forCombo(combo)

    private fun isShift(binding: Binding) = binding == Binding.KEY_SHIFT_L || binding == Binding.KEY_SHIFT_R

    private fun build(combo: BindingCombo): KeyLabel? {
        val bindings = combo.bindings
        val separator = if (combo.isSequence) "→" else "+"
        val parts = ArrayList<LabelPart>()
        val text = StringBuilder()
        var anyGlyph = false
        for ((index, binding) in bindings.withIndex()) {
            if (index > 0) {
                val joinTight = !combo.isSequence && isShift(bindings[index - 1])
                if (!joinTight) text.append(separator)
            }
            val glyph = glyphFor(binding)
            if (glyph != null) {
                if (text.isNotEmpty()) {
                    parts.add(LabelPart(null, text.toString()))
                    text.setLength(0)
                }
                parts.add(LabelPart(glyph, ""))
                anyGlyph = true
            } else {
                text.append(BindingCaption.forBinding(binding))
            }
        }
        if (text.isNotEmpty()) parts.add(LabelPart(null, text.toString()))
        return if (anyGlyph) KeyLabel(parts, BindingCaption.forCombo(combo)) else null
    }
}

/**
 * How big a label may be. [diameter] is the short side of the element (button, ring segment, key strip cell, stick knob), all
 * sizes in pixels.
 */
object TextFit {
    /** Text is at most this share of the element diameter high. */
    const val MAX_HEIGHT_FRACTION = 0.45f

    /** Ring and outer ring fields are smaller than the middle (the stick knob, the buttons). */
    const val RING_FACTOR = 0.85f

    /** Below this share of the maximum (and below [MIN_PIXELS]) text is cut instead of shrunk further. */
    const val MIN_SHARE = 0.5f
    const val MIN_PIXELS = 10f

    /** Biggest height of a label: 45 % of the diameter, 38 % for ring fields. */
    @JvmStatic
    fun maxSize(diameter: Float, ring: Boolean): Float {
        val base = max(0f, diameter) * MAX_HEIGHT_FRACTION
        return if (ring) base * RING_FACTOR else base
    }

    /** Smallest height before text is cut; never above [cap] or the maximum. */
    @JvmStatic
    fun minSize(diameter: Float, ring: Boolean, cap: Float = Float.MAX_VALUE): Float {
        val top = min(maxSize(diameter, ring), cap)
        return min(top, max(MIN_PIXELS, top * MIN_SHARE))
    }

    /**
     * Font size of a label whose width at size 1 is [widthPerUnit]: the maximum height ([cap] is an extra upper limit of the
     * caller), shrunk until the width fits [fitWidth]. 0 if there is no room at all.
     */
    @JvmStatic
    @JvmOverloads
    fun size(diameter: Float, fitWidth: Float, widthPerUnit: Float, ring: Boolean, cap: Float = Float.MAX_VALUE): Float {
        if (fitWidth <= 0f) return 0f
        val top = min(maxSize(diameter, ring), cap)
        if (widthPerUnit <= 0f) return top
        return min(top, fitWidth / widthPerUnit)
    }

    /** True if [size] is so small that the text has to be cut ([minSize]). */
    @JvmStatic
    @JvmOverloads
    fun needsCut(size: Float, diameter: Float, ring: Boolean, cap: Float = Float.MAX_VALUE): Boolean =
        size < minSize(diameter, ring, cap)

    /**
     * Cuts [text] code point by code point until [widthOf] says it fits [maxWidth] (at least one code point stays). Returns
     * [text] itself when it fits.
     */
    inline fun cut(text: String, maxWidth: Float, widthOf: (String) -> Float): String {
        var result = text
        while (result.codePointCount(0, result.length) > 1 && widthOf(result) > maxWidth) {
            result = result.substring(0, result.offsetByCodePoints(result.length, -1))
        }
        return result
    }
}
