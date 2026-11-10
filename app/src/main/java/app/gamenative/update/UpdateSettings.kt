package app.gamenative.update

import android.content.Context
import app.gamenative.BuildConfig

/** Persistent state of the self-update: feed URL override, time of the last check, cached feed, last seen version. */
internal object UpdateSettings {
    private const val PREFS = "wow_forever_update"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_FEED = "last_feed"
    private const val KEY_SEEN_VERSION = "seen_version_code"
    private const val KEY_WHATS_NEW_SHOWN = "whats_new_shown_code"
    private const val KEY_AUTO_INSTALL = "auto_install"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The user's override (Erweitert), or empty when the build default applies. */
    fun baseUrlOverride(context: Context): String = prefs(context).getString(KEY_BASE_URL, "").orEmpty()

    fun baseUrl(context: Context): String = baseUrlOverride(context).ifBlank { BuildConfig.UPDATE_FEED_URL }

    fun saveBaseUrl(context: Context, url: String) {
        val trimmed = url.trim()
        prefs(context).edit().apply {
            if (trimmed.isEmpty() || trimmed == BuildConfig.UPDATE_FEED_URL) remove(KEY_BASE_URL) else putString(KEY_BASE_URL, trimmed)
            // A different source invalidates what the old one reported.
            remove(KEY_FEED)
            remove(KEY_LAST_CHECK)
        }.apply()
    }

    fun lastCheck(context: Context): Long = prefs(context).getLong(KEY_LAST_CHECK, 0L)

    fun saveLastCheck(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, ms).apply()
    }

    /** Raw feed JSON of the last successful check, so the hint survives an app restart within the check interval. */
    fun cachedFeed(context: Context): String? = prefs(context).getString(KEY_FEED, null)

    fun saveFeed(context: Context, json: String) {
        prefs(context).edit().putString(KEY_FEED, json).apply()
    }

    /** 0 = never recorded (first start of an installation). */
    fun seenVersionCode(context: Context): Int = prefs(context).getInt(KEY_SEEN_VERSION, 0)

    fun saveSeenVersionCode(context: Context, code: Int) {
        prefs(context).edit().putInt(KEY_SEEN_VERSION, code).apply()
    }

    /** versionCode for which "Neu in ..." was already shown (or skipped); 0 = never. */
    fun whatsNewShownCode(context: Context): Int = prefs(context).getInt(KEY_WHATS_NEW_SHOWN, 0)

    fun saveWhatsNewShownCode(context: Context, code: Int) {
        prefs(context).edit().putInt(KEY_WHATS_NEW_SHOWN, code).apply()
    }

    /** "Updates automatisch installieren"; off by default. */
    fun autoInstall(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_INSTALL, false)

    fun saveAutoInstall(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_INSTALL, on).apply()
    }
}
