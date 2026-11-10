package app.gamenative.ui.screen.xserver.editor

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A text of the editor as string resource plus arguments. Pure logic returns this instead of a finished string, so JUnit can
 * compare ids; nested [EditorText] arguments are resolved as well. The UI turns it into text with [text] inside a composable,
 * or with [resolve] where only a [Context] is at hand (callbacks that are not composable).
 * [raw] wraps text that is already final (data or text from another module) and is passed through unchanged.
 */
data class EditorText(@StringRes val res: Int, val args: List<Any> = emptyList()) {
    companion object {
        /** Resource id 0 marks a passed-through text; its only argument is the text itself. */
        fun raw(text: String): EditorText = EditorText(0, listOf(text))
    }
}

@Composable
fun EditorText.text(): String {
    if (res == 0) return args.firstOrNull()?.toString().orEmpty()
    val resolved = args.map { if (it is EditorText) it.text() else it }
    return if (resolved.isEmpty()) stringResource(res) else stringResource(res, *resolved.toTypedArray())
}

fun EditorText.resolve(context: Context): String {
    if (res == 0) return args.firstOrNull()?.toString().orEmpty()
    val resolved = args.map { if (it is EditorText) it.resolve(context) else it }
    return if (resolved.isEmpty()) context.getString(res) else context.getString(res, *resolved.toTypedArray())
}
