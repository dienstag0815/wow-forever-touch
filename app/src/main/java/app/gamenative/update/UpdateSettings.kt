package app.gamenative.update

import android.content.Context
import android.util.Base64
import app.gamenative.BuildConfig
import app.gamenative.Crypto
import timber.log.Timber

/** Persistent state of the self-update: feed URL override, time of the last check, cached feed, last seen version. */
internal object UpdateSettings {
    private const val PREFS = "wow_forever_update"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_FEED = "last_feed"
    private const val KEY_SEEN_VERSION = "seen_version_code"
    private const val KEY_WHATS_NEW_SHOWN = "whats_new_shown_code"
    private const val KEY_AUTO_INSTALL = "auto_install"
    private const val KEY_BACKUP_SUGGEST = "backup_suggest_pending"

    // Custom update source. None of these keys is in the backup allowlist; the token is sealed with the Android Keystore key.
    private const val KEY_SOURCE_ON = "source_custom"
    private const val KEY_SOURCE_KIND = "source_kind"
    private const val KEY_SOURCE_URL = "source_url"
    private const val KEY_SOURCE_PRE = "source_prerelease"
    private const val KEY_SOURCE_TOKEN = "source_token"
    private const val KEY_SOURCE_TESTER = "source_tester"

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

    /** True after the first start of a fresh installation until the player decided about a backup found in Downloads (or none was found). Not in the backup. */
    fun backupSuggestPending(context: Context): Boolean = prefs(context).getBoolean(KEY_BACKUP_SUGGEST, false)

    fun setBackupSuggestPending(context: Context, pending: Boolean) {
        prefs(context).edit().putBoolean(KEY_BACKUP_SUGGEST, pending).apply()
    }

    /** "Updates automatisch installieren"; off by default. */
    fun autoInstall(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_INSTALL, false)

    fun saveAutoInstall(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_INSTALL, on).apply()
    }

    /** The entered fields of the custom source, also while it is switched off (to prefill the form); the address is raw text. */
    /** [tester] = the source came from a tester code (shown as "Tester-Updates aktiv"); the code itself is never stored. */
    data class SourceForm(val kind: SourceKind, val url: String, val includePrereleases: Boolean, val active: Boolean, val tester: Boolean = false)

    fun sourceForm(context: Context): SourceForm {
        val p = prefs(context)
        return SourceForm(
            kind = SourceKind.fromKey(p.getString(KEY_SOURCE_KIND, null)),
            url = p.getString(KEY_SOURCE_URL, "").orEmpty(),
            includePrereleases = p.getBoolean(KEY_SOURCE_PRE, false),
            active = p.getBoolean(KEY_SOURCE_ON, false),
            tester = p.getBoolean(KEY_SOURCE_TESTER, false),
        )
    }

    /** The active custom source, or null when the official one is used (also when the saved address no longer parses). */
    fun customSource(context: Context): CustomSource? {
        val form = sourceForm(context)
        if (!form.active) return null
        val ref = (UpdateSourceLogic.parseRepo(form.kind, form.url) as? RepoParse.Ok)?.ref ?: return null
        // Tester-Modus: Pre-Releases sind fest an (kein Schalter).
        return CustomSource(ref, form.includePrereleases || form.tester)
    }

    /** Switches the custom source on with a validated [ref]; [token] null keeps the stored one, blank removes it. A new source drops what the old one reported. */
    fun saveCustomSource(context: Context, ref: RepoRef, includePrereleases: Boolean, token: String?, tester: Boolean = false) {
        prefs(context).edit().apply {
            putBoolean(KEY_SOURCE_ON, true)
            putString(KEY_SOURCE_KIND, ref.kind.key)
            putString(KEY_SOURCE_URL, ref.displayUrl)
            putBoolean(KEY_SOURCE_PRE, includePrereleases)
            putBoolean(KEY_SOURCE_TESTER, tester)
            remove(KEY_FEED)
            remove(KEY_LAST_CHECK)
        }.apply()
        if (token != null) saveSourceToken(context, token)
    }

    /** "Back to official": switches the custom source off and removes the token; the other fields are forgotten, too. */
    fun clearCustomSource(context: Context) {
        prefs(context).edit().apply {
            remove(KEY_SOURCE_ON)
            remove(KEY_SOURCE_KIND)
            remove(KEY_SOURCE_URL)
            remove(KEY_SOURCE_PRE)
            remove(KEY_SOURCE_TOKEN)
            remove(KEY_SOURCE_TESTER)
            remove(KEY_FEED)
            remove(KEY_LAST_CHECK)
        }.apply()
    }

    fun hasSourceToken(context: Context): Boolean = !prefs(context).getString(KEY_SOURCE_TOKEN, null).isNullOrEmpty()

    /** The stored access token, or null (none, or the Keystore key is gone). Never logged. */
    fun sourceToken(context: Context): String? {
        val sealed = prefs(context).getString(KEY_SOURCE_TOKEN, null)?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            String(Crypto.decrypt(Base64.decode(sealed, Base64.NO_WRAP))).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.w("Stored update token is unreadable (%s)", e.javaClass.simpleName)
            null
        }
    }

    /** Blank removes the token. */
    fun saveSourceToken(context: Context, token: String) {
        val trimmed = token.trim()
        prefs(context).edit().apply {
            if (trimmed.isEmpty()) {
                remove(KEY_SOURCE_TOKEN)
            } else {
                putString(KEY_SOURCE_TOKEN, Base64.encodeToString(Crypto.encrypt(trimmed.toByteArray()), Base64.NO_WRAP))
            }
        }.apply()
    }
}
