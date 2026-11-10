package app.gamenative.ui.components

import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A text that is not resolved yet: a string resource plus its format arguments. Pure logic (planners, formatters, state
 * holders) returns this instead of a finished sentence, so JUnit compares ids and the UI picks the language at the very end.
 *
 * Arguments are numbers, strings that are not translated (names, keys) or further [TextRef]s (a sentence that contains a
 * translated fragment such as the network name).
 */
data class TextRef(@StringRes val res: Int, val args: List<Any>) {
    constructor(@StringRes res: Int, vararg args: Any) : this(res, args.toList())

    companion object {
        /** A text that is shown as it is (numbers, names): nothing to translate. */
        fun plain(text: String) = TextRef(R.string.game_plain, text)
    }

    /** The text in the language of [context]; nested [TextRef] arguments are resolved first. */
    fun resolve(context: Context): String {
        if (args.isEmpty()) return context.getString(res)
        return context.getString(res, *args.map { if (it is TextRef) it.resolve(context) else it }.toTypedArray())
    }
}

/** The text in the current language, for Compose code. */
@Composable
fun TextRef.asString(): String = resolve(LocalContext.current)
