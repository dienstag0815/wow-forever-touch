package app.gamenative.ui.screen.wow.launcher

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A user-visible text as data: a string resource plus its arguments (or a plural plus a count, or a text that is already final).
 * Pure logic (states, planners, domain results, JUnit-tested without Android) returns this instead of a finished string; the
 * screen turns it into text with [asString] (Compose) or [resolve] (anywhere with a Context). Arguments may be [UiText] themselves
 * (nested) and are resolved first. Equality compares id and arguments, so tests compare ids, not words.
 */
sealed class UiText {
    /** A string resource; [args] fill its placeholders (`%1$s`, `%2$d`, ...). */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText()

    /** A plural resource; [count] picks the form, [args] fill the placeholders (usually the count itself first). */
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText()

    /** Several texts one after the other, each resolved in the app language ("Access, Program, Data"). */
    data class Join(val parts: List<UiText>, val separator: String = ", ") : UiText()

    /** A text that is final and not translatable: names, paths, file or exception messages from outside. */
    data class Raw(val text: String) : UiText()

    fun resolve(context: Context): String = when (this) {
        is Raw -> text
        is Join -> parts.joinToString(separator) { it.resolve(context) }
        is Res -> if (args.isEmpty()) context.getString(id) else context.getString(id, *resolveArgs(args, context))
        is Plural -> context.resources.getQuantityString(id, count, *resolveArgs(args, context))
    }

    final override fun toString(): String = when (this) {
        is Raw -> text
        is Join -> parts.joinToString(separator)
        is Res -> "res#$id$args"
        is Plural -> "plural#$id($count)$args"
    }

    companion object {
        /** [id] with [args]; use for every text that comes from `strings_launcher.xml`. */
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        /** [id] plural form for [count]; [args] default to the count itself, so `%d` in the resource shows it. */
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
            Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

        fun raw(text: String): UiText = Raw(text)

        fun join(parts: List<UiText>, separator: String = ", "): UiText = Join(parts, separator)

        private fun resolveArgs(args: List<Any>, context: Context): Array<Any> =
            Array(args.size) { index -> (args[index] as? UiText)?.resolve(context) ?: args[index] }
    }
}

/** Turns the text into a string in the language of the running screen. */
@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
