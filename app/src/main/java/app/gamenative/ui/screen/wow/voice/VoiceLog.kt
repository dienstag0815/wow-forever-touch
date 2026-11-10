package app.gamenative.ui.screen.wow.voice

import app.gamenative.R
import app.gamenative.ui.components.AppText
import android.content.Context
import app.gamenative.ui.screen.wow.wowPrefs

/**
 * One recognized utterance in the local recognition log.
 *
 * @param heard what the recognizer wrote
 * @param corrected what the preview showed after the word-list correction
 * @param outcome what the player did with it ([VoiceLog.OUTCOME_SENT] and so on, empty while it is still open)
 * @param alternatives the recognizer's other hypotheses, separated by " | "
 */
internal class VoiceLogEntry(
    val heard: String,
    val corrected: String,
    val outcome: String = "",
    val alternatives: String = "",
)

/**
 * The last [MAX_ENTRIES] recognitions as "heard -> corrected" pairs, kept only on the device (launcher
 * SharedPreferences). The player can show them in the settings and copy them to send examples of mis-heard words.
 * The list logic is pure (newest first); the Android part only reads and writes one string.
 */
internal object VoiceLog {
    const val MAX_ENTRIES = 30
    const val OUTCOME_SENT = "gesendet"
    const val OUTCOME_SENT_EDITED = "bearbeitet gesendet"
    const val OUTCOME_AGAIN = "nochmal"
    const val OUTCOME_DISCARDED = "verworfen"
    const val OUTCOME_NOT_SENT = "Chat nicht offen, nicht gesendet"

    private const val KEY = "voice_log"

    /** Tabs and line breaks separate the fields and entries, so they must not occur inside a field. */
    fun clean(text: String): String = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    fun encode(entries: List<VoiceLogEntry>): String =
        entries.take(MAX_ENTRIES).joinToString("\n") { entry ->
            listOf(entry.heard, entry.corrected, entry.outcome, entry.alternatives).joinToString("\t") { clean(it) }
        }

    fun decode(text: String): List<VoiceLogEntry> {
        val out = ArrayList<VoiceLogEntry>()
        for (line in text.split('\n')) {
            if (line.isBlank()) continue
            val fields = line.split('\t')
            out.add(
                VoiceLogEntry(
                    heard = fields.getOrElse(0) { "" },
                    corrected = fields.getOrElse(1) { "" },
                    outcome = fields.getOrElse(2) { "" },
                    alternatives = fields.getOrElse(3) { "" },
                ),
            )
            if (out.size >= MAX_ENTRIES) break
        }
        return out
    }

    /** [entry] as the newest, the oldest entries beyond [MAX_ENTRIES] fall off. */
    fun add(entries: List<VoiceLogEntry>, entry: VoiceLogEntry): List<VoiceLogEntry> =
        (listOf(entry) + entries).take(MAX_ENTRIES)

    /** Sets what the player did with the newest entry. */
    fun withOutcome(entries: List<VoiceLogEntry>, outcome: String): List<VoiceLogEntry> {
        if (entries.isEmpty()) return entries
        val newest = entries[0]
        return listOf(VoiceLogEntry(newest.heard, newest.corrected, outcome, newest.alternatives)) + entries.drop(1)
    }

    /** The outcome as shown: the stored values stay German (older entries), the display follows the app language. */
    fun outcomeLabel(outcome: String): String = when (outcome) {
        OUTCOME_SENT -> AppText.get(R.string.game_voice_out_sent)
        OUTCOME_SENT_EDITED -> AppText.get(R.string.game_voice_out_sent_edited)
        OUTCOME_AGAIN -> AppText.get(R.string.game_voice_out_again)
        OUTCOME_DISCARDED -> AppText.get(R.string.game_voice_out_discarded)
        OUTCOME_NOT_SENT -> AppText.get(R.string.game_voice_out_not_sent)
        else -> outcome
    }

    /** "heard -> corrected [outcome]" for one entry, a second line with the alternatives if there are any. */
    fun line(entry: VoiceLogEntry): String = buildString {
        append(entry.heard)
        append(" → ")
        append(if (entry.corrected == entry.heard) AppText.get(R.string.game_voice_unchanged) else entry.corrected)
        if (entry.outcome.isNotEmpty()) append("  [").append(outcomeLabel(entry.outcome)).append(']')
        if (entry.alternatives.isNotEmpty()) append("\n    ").append(AppText.get(R.string.game_voice_alternatives)).append(" ").append(entry.alternatives)
    }

    /** The text for the clipboard: a header and the numbered entries, newest first. */
    fun format(entries: List<VoiceLogEntry>): String = buildString {
        append(AppText.get(R.string.game_voice_log_header))
        for ((index, entry) in entries.withIndex()) {
            append('\n').append(index + 1).append(". ").append(line(entry))
        }
    }

    fun load(context: Context): List<VoiceLogEntry> = decode(wowPrefs(context).getString(KEY, "") ?: "")

    fun append(context: Context, entry: VoiceLogEntry) {
        save(context, add(load(context), entry))
    }

    fun setLastOutcome(context: Context, outcome: String) {
        val current = load(context)
        if (current.isEmpty()) return
        save(context, withOutcome(current, outcome))
    }

    fun clear(context: Context) {
        wowPrefs(context).edit().remove(KEY).apply()
    }

    private fun save(context: Context, entries: List<VoiceLogEntry>) {
        wowPrefs(context).edit().putString(KEY, encode(entries)).apply()
    }
}
