package app.gamenative.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Texts for code that has no [Context] at hand (enum labels, canvas drawing, the catalog of WoW actions). The application
 * context is handed over once in [app.gamenative.PluviaApp.onCreate]. The language is the one of [Locale.getDefault], which the
 * app sets for its own language choice, so the texts match what Compose shows through `stringResource`.
 *
 * Pure logic should return [TextRef] instead; this object is for the few places where the label itself is the model.
 */
object AppText {
    private var app: Context? = null
    private var cachedLocale: Locale? = null
    private var cachedContext: Context? = null

    /** Unit tests (no Android): resolves the id to a text; null = use the real resources. */
    @Volatile
    var testResolver: ((Int, List<Any>) -> String)? = null

    /** Unit tests: true/false forces the language, null = follow [Locale.getDefault]. */
    @Volatile
    var testGerman: Boolean? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** True when the app language is German (the language the rest of the app texts were written in first). */
    fun isGerman(): Boolean = testGerman ?: (Locale.getDefault().language == "de")

    @Synchronized
    private fun localized(): Context? {
        val base = app ?: return null
        val locale = Locale.getDefault()
        if (locale != cachedLocale || cachedContext == null) {
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            cachedContext = base.createConfigurationContext(config)
            cachedLocale = locale
        }
        return cachedContext
    }

    fun get(@StringRes res: Int, vararg args: Any): String {
        testResolver?.let { return it(res, args.toList()) }
        val context = localized() ?: return ""
        val values = args.map { if (it is TextRef) it.resolve(context) else it }.toTypedArray()
        return if (values.isEmpty()) context.getString(res) else context.getString(res, *values)
    }

    /** A [TextRef] in the current language. */
    fun get(ref: TextRef): String = get(ref.res, *ref.args.toTypedArray())
}
