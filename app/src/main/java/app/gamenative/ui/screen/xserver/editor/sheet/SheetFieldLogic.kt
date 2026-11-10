package app.gamenative.ui.screen.xserver.editor.sheet

import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.EditorText

/**
 * Note under a label field: a text that is not blank but not an own label (a bare number that only repeats the number of the
 * field, see `BindingCaption.isOwnLabel`) is saved but not shown on the element, the field then names itself automatically.
 * Null when the text shows as typed. [own] tells whether the element counts the text as an own label. Pure Kotlin.
 */
fun ignoredLabelHint(text: String, own: Boolean): EditorText? =
    if (text.isNotBlank() && !own) {
        EditorText(R.string.editor_ignored_label_hint)
    } else {
        null
    }

/**
 * Position of an element moved by hand (the numeric fields and arrows of the sheet): values stay inside 0 to the free room
 * of the controls area ([max] below 0 counts as 0). Pure Kotlin.
 */
object ElementPosition {
    fun clamp(value: Int, max: Int): Int = value.coerceIn(0, maxOf(0, max))

    /** One arrow step: [value] moved by [steps] times [step] pixels, kept inside the area. */
    fun nudge(value: Int, steps: Int, step: Int, max: Int): Int = clamp(value + steps * maxOf(1, step), max)

    /** Text of a number field to a value; null for anything that is not a whole, non-negative number. */
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 0 }
}
