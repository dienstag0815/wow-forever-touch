package app.gamenative.ui.screen.wow.voice

import android.content.Context
import app.gamenative.ui.screen.wow.wowPrefs

/** Recognition language of the voice button. */
enum class VoiceLanguage(val tag: String, val label: String) {
    DE("de-DE", "Deutsch"),
    EN("en-US", "English"),
}

/**
 * Settings of the voice button, kept in the launcher's SharedPreferences ("wow_forever"): recognition language,
 * umlaut handling and the player's own words (one per line or separated by commas).
 */
internal object VoiceSettings {
    private const val KEY_LANGUAGE = "voice_language"
    private const val KEY_WORDS = "voice_words"
    private const val KEY_UMLAUTS = "voice_umlauts"
    private const val WORD_LIST_ASSET = "wow/begriffe.txt"

    /**
     * Without a saved choice: English, the main language of the game. The value is only written when the player taps the
     * language row (settings page or side menu), so a saved value is always a deliberate choice and is never overwritten.
     */
    val DEFAULT_LANGUAGE = VoiceLanguage.EN

    const val MAX_CUSTOM_WORDS = 100
    const val MAX_WORD_LENGTH = 40

    fun language(context: Context): VoiceLanguage =
        wowPrefs(context).getString(KEY_LANGUAGE, null)?.let { saved -> VoiceLanguage.entries.firstOrNull { it.name == saved } }
            ?: DEFAULT_LANGUAGE

    fun saveLanguage(context: Context, language: VoiceLanguage) {
        wowPrefs(context).edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun umlautMode(context: Context): UmlautMode =
        wowPrefs(context).getString(KEY_UMLAUTS, null)?.let { saved -> UmlautMode.entries.firstOrNull { it.name == saved } }
            ?: UmlautMode.DIRECT

    fun saveUmlautMode(context: Context, mode: UmlautMode) {
        wowPrefs(context).edit().putString(KEY_UMLAUTS, mode.name).apply()
    }

    /** The raw text of the "own words" field as the player typed it. */
    fun customWordsText(context: Context): String = wowPrefs(context).getString(KEY_WORDS, "") ?: ""

    fun saveCustomWordsText(context: Context, text: String) {
        wowPrefs(context).edit().putString(KEY_WORDS, text).apply()
    }

    /** Own words as a clean list (see [parseCustomWords]). */
    fun customWords(context: Context): List<String> = parseCustomWords(customWordsText(context))

    /** The word list of the app plus the player's own words, ready for the corrector. */
    fun loadVocabulary(context: Context): VoiceVocabulary {
        val listText = try {
            context.assets.open(WORD_LIST_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            ""
        }
        return VoiceVocabulary.parse(listText, customWords(context))
    }

    /**
     * Splits the free text of the settings field at line breaks, commas and semicolons, trims, drops empty and
     * duplicate entries (ignoring case) and cuts very long entries. At most [MAX_CUSTOM_WORDS] words are kept.
     */
    fun parseCustomWords(raw: String): List<String> {
        val seen = HashSet<String>()
        val words = ArrayList<String>()
        for (part in raw.split('\n', '\r', ',', ';')) {
            val word = part.trim().trimStart('=', '#').trim().take(MAX_WORD_LENGTH)
            if (word.isEmpty()) continue
            if (!seen.add(word.lowercase())) continue
            words.add(word)
            if (words.size >= MAX_CUSTOM_WORDS) break
        }
        return words
    }
}
