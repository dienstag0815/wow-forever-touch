package app.gamenative.ui.screen.wow.voice

import kotlin.math.abs

/**
 * One entry of the WoW word list.
 *
 * @param display the spelling that goes into the chat ("Kel'Thuzad", "Todesminen")
 * @param protectedWord common word (line starts with "="): never fuzzy-matched and never re-cased, it only takes part
 *                      in merging ("Todes Minen" -> "Todesminen") and in the exact-match protection
 * @param bias name from the list's "#!bias" block (English client names): handed to the recognizer first
 */
internal class VoiceTerm(val display: String, val protectedWord: Boolean, val bias: Boolean = false) {
    val plain: String = Phonetics.fold(display)
    val cologne: String = Phonetics.cologne(display)
    val metaphone: String = Phonetics.metaphone(display)

    /** Fuzzy matching only for distinctive names: long enough and not marked as a common word. */
    val fuzzy: Boolean get() = !protectedWord && plain.length >= MIN_FUZZY_LENGTH

    /**
     * A pure case difference is corrected only for spellings that are clearly not an everyday word: capitals inside
     * ("DPS", "AoE", "Kel'Thuzad"), an apostrophe, hyphen, space or digit. "hinrichten", "tank", "loot" stay as spoken.
     */
    val fixesCase: Boolean = display.drop(1).any { it.isUpperCase() } || display.any { !it.isLetter() }

    companion object {
        const val MIN_FUZZY_LENGTH = 6
    }
}

/** The word list in lookup form: by folded spelling, plus the list of terms that may be matched by sound. */
internal class VoiceVocabulary(val terms: List<VoiceTerm>) {
    private val byPlain = HashMap<String, MutableList<VoiceTerm>>()

    /** Distinctive names that take part in fuzzy matching. */
    val fuzzyTerms: List<VoiceTerm> = terms.filter { it.fuzzy && it.plain.length >= 2 }

    init {
        for (term in terms) {
            if (term.plain.length < 2) continue
            byPlain.getOrPut(term.plain) { ArrayList() }.add(term)
        }
    }

    fun exact(plain: String): List<VoiceTerm> = byPlain[plain].orEmpty()

    /**
     * Strings for Android's recognition bias (API 33+), at most [max]: the player's own words first (at most
     * [MAX_CUSTOM_BIAS]), then the English client names of the list's "#!bias" block, then the other distinctive names.
     * Common words ("=") are left out, the recognizer knows them anyway.
     */
    fun biasStrings(custom: List<String>, max: Int = MAX_BIAS_STRINGS): List<String> {
        val out = LinkedHashSet<String>()
        val customLimit = minOf(max, MAX_CUSTOM_BIAS)
        for (word in custom) {
            if (out.size >= customLimit) break
            out.add(word)
        }
        for (term in terms) {
            if (out.size >= max) break
            if (term.bias && !term.protectedWord) out.add(term.display)
        }
        for (term in terms) {
            if (out.size >= max) break
            if (!term.protectedWord) out.add(term.display)
        }
        return out.toList()
    }

    companion object {
        const val MAX_BIAS_STRINGS = 200
        const val MAX_CUSTOM_BIAS = 50
        private const val BIAS_ON = "#!bias"
        private const val BIAS_OFF = "#!normal"

        val EMPTY = VoiceVocabulary(emptyList())

        /**
         * Reads the word list text: one term per line, `#` starts a comment, a leading `=` marks a common word, the
         * comment lines `#!bias` and `#!normal` switch the recognizer-hint block on and off.
         * Terms of the player ([custom]) come last so that the list's spelling wins for equal words.
         */
        fun parse(listText: String, custom: List<String> = emptyList()): VoiceVocabulary {
            val terms = ArrayList<VoiceTerm>()
            val seen = HashSet<String>()
            var bias = false
            fun add(line: String) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return
                if (trimmed.startsWith("#")) {
                    if (trimmed.startsWith(BIAS_ON)) bias = true else if (trimmed.startsWith(BIAS_OFF)) bias = false
                    return
                }
                val protectedWord = trimmed.startsWith("=")
                val display = (if (protectedWord) trimmed.substring(1) else trimmed).trim()
                if (display.isEmpty()) return
                if (seen.add(display.lowercase())) terms.add(VoiceTerm(display, protectedWord, bias && !protectedWord))
            }
            listText.lineSequence().forEach(::add)
            bias = false
            custom.forEach(::add)
            return VoiceVocabulary(terms)
        }
    }
}

/** A stretch of the corrected text [start, end) that was changed (for highlighting). */
internal class VoiceSpan(val start: Int, val end: Int)

/** One replacement: what the recognizer wrote (words of the window, without punctuation) and the list's spelling. */
internal class VoiceChange(val spoken: String, val corrected: String, val score: Double)

/**
 * Result of the correction.
 *
 * @param heard the recognizer's text (blanks collapsed)
 * @param text the corrected text
 * @param spans the changed stretches of [text]
 * @param nameScore sum of the scores of all recognized names (exact ones count 1.0), used to pick between the
 *                  recognizer's alternatives
 */
internal class VoiceCorrection(
    val heard: String,
    val text: String,
    val changes: List<VoiceChange>,
    val spans: List<VoiceSpan>,
    val nameScore: Double,
) {
    val changed: Boolean get() = changes.isNotEmpty()

    /**
     * The highlight stretches for a text that was cleaned after the correction ([shown], e.g. cut or trimmed):
     * unchanged text keeps [spans], otherwise every corrected word is searched again in order.
     */
    fun spansIn(shown: String): List<VoiceSpan> {
        if (shown == text) return spans
        val result = ArrayList<VoiceSpan>()
        var from = 0
        for (change in changes) {
            val at = shown.indexOf(change.corrected, from)
            if (at < 0) continue
            result.add(VoiceSpan(at, at + change.corrected.length))
            from = at + change.corrected.length
        }
        return result
    }
}

/**
 * Corrects what the recognizer made of WoW names: "Todes Minen" -> "Todesminen", "Kel Thusad" -> "Kel'Thuzad",
 * "iron Ford" -> "Ironforge". A window of one to three words is joined and compared with every distinctive name of the
 * list: exact (folded spelling) wins, otherwise the window gets a similarity score
 *
 *     score = 0.5 * (1 - levenshtein / longer length)                       spelling
 *           + 0.5 * (2 * max(cologne, metaphone) + min(cologne, metaphone)) / 3   sound, both keys always compared
 *           + bonus (0.012 per letter of the name beyond 6, at most 0.06)         long names are rarely a coincidence
 *
 * where the sound similarity of a key is 1 - levenshtein(keys) / longer key. A name is taken when the score reaches
 * [SINGLE_THRESHOLD] (one word) or [MULTI_THRESHOLD] (several words), the edit distance stays small, the first sound
 * agrees, the word is no inflected form of the name and the second best name is at least [AMBIGUITY_GAP] behind.
 * Everything else stays as the recognizer wrote it. Pure Kotlin, no Android classes.
 */
internal object VoiceCorrector {
    const val MAX_WINDOW = 3
    const val SINGLE_THRESHOLD = 0.86
    const val MULTI_THRESHOLD = 0.80
    const val AMBIGUITY_GAP = 0.03

    /** An alternative of the recognizer is only preferred when it holds at least this much more name score. */
    const val ALTERNATIVE_MARGIN = 0.9

    private const val MIN_FUZZY_TOKEN_LENGTH = 3
    private const val LENGTH_WEIGHT = 0.5
    private const val BONUS_PER_LETTER = 0.012
    private const val BONUS_MAX = 0.06
    private const val BONUS_FROM_LENGTH = 6
    private const val MAX_LENGTH_DIFFERENCE = 3

    /** Common words that are never turned into a name by fuzzy matching. */
    private val STOP_WORDS = setOf(
        "und", "oder", "aber", "dass", "denn", "wenn", "dann", "doch", "noch", "auch", "nicht", "kein", "keine", "ich",
        "bin", "bist", "ist", "sind", "war", "habe", "hast", "haben", "wir", "ihr", "sie", "der", "die", "das", "den",
        "dem", "des", "ein", "eine", "einen", "mit", "von", "zum", "zur", "fuer", "auf", "aus", "bei", "nach", "wer",
        "wie", "was", "wann", "wo", "hier", "dort", "jetzt", "gleich", "kommt", "komme", "kommen", "gehen", "gehe",
        "brauche", "brauchen", "suche", "suchen", "gruppe", "hilfe", "danke", "bitte", "hallo", "gerne", "gern",
        "the", "and", "for", "you", "your", "are", "not", "with", "this", "that", "have", "need", "looking", "group",
        "please", "thanks", "thank", "hello", "from", "come", "going", "will", "can", "anyone", "someone", "help",
        "there", "what", "when", "where", "just", "been", "would", "could", "should", "really", "also", "very", "some",
        "they", "them", "their", "then", "than", "were", "has", "had", "who", "why", "how", "all", "but", "get", "got",
        "let", "lets", "see", "yes", "ja", "nein", "mal", "schon", "mir", "mich", "dir", "dich", "uns", "euch", "mein",
        "dein", "sein", "hat", "wird", "werden", "wurde", "kann", "koennen", "muss", "soll", "wollen", "heute",
        "morgen", "gestern", "alles", "alle", "nur", "sehr", "viel", "viele", "mehr", "weil", "dabei",
    )

    /** Endings that make "Feuerbälle" or "Portale" a form of "Feuerball" / "Portal" instead of a mis-heard name. */
    private val INFLECTION_ENDINGS = setOf("e", "n", "s", "t", "en", "es", "er", "em", "st", "te", "ten", "ern")

    private class Match(
        val size: Int,
        val term: VoiceTerm,
        val score: Double,
        /** Exact folded spelling (as opposed to a similarity score). */
        val exact: Boolean,
        /** The window already is the term (nothing to replace). */
        val unchanged: Boolean,
    )

    private class Scored(val term: VoiceTerm, val score: Double)

    private class Token(val lead: String, val core: String, val trail: String, val raw: String)

    /** One output word group: its text, the length of leading punctuation and the change it stands for, if any. */
    private class Piece(val text: String, val leadLength: Int, val change: VoiceChange?)

    fun correct(text: String, vocabulary: VoiceVocabulary): String = correctDetailed(text, vocabulary).text

    /** Corrects the first non-blank candidate and takes an alternative only when it clearly contains more names. */
    fun best(candidates: List<String>, vocabulary: VoiceVocabulary): VoiceCorrection? {
        var best: VoiceCorrection? = null
        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            val correction = correctDetailed(candidate, vocabulary)
            val current = best
            if (current == null || correction.nameScore >= current.nameScore + ALTERNATIVE_MARGIN) best = correction
        }
        return best
    }

    fun correctDetailed(text: String, vocabulary: VoiceVocabulary): VoiceCorrection {
        val heard = text.trim().replace(Regex("\\s+"), " ")
        if (vocabulary.terms.isEmpty() || heard.isEmpty()) return VoiceCorrection(heard, heard, emptyList(), emptyList(), 0.0)
        val tokens = heard.split(' ').filter { it.isNotEmpty() }.map(::tokenOf)

        val pieces = ArrayList<Piece>(tokens.size)
        var nameScore = 0.0
        var i = 0
        while (i < tokens.size) {
            val match = bestMatch(tokens, i, vocabulary)
            if (match == null) {
                pieces.add(Piece(tokens[i].raw, 0, null))
                i++
            } else if (match.unchanged) {
                for (k in i until i + match.size) pieces.add(Piece(tokens[k].raw, 0, null))
                if (!match.term.protectedWord) nameScore += match.score
                i += match.size
            } else {
                val first = tokens[i]
                val last = tokens[i + match.size - 1]
                val spoken = (i until i + match.size).joinToString(" ") { tokens[it].core }
                if (!match.term.protectedWord) nameScore += match.score
                pieces.add(
                    Piece(
                        first.lead + match.term.display + last.trail,
                        first.lead.length,
                        VoiceChange(spoken, match.term.display, match.score),
                    ),
                )
                i += match.size
            }
        }

        val sb = StringBuilder()
        val changes = ArrayList<VoiceChange>()
        val spans = ArrayList<VoiceSpan>()
        for ((index, piece) in pieces.withIndex()) {
            if (index > 0) sb.append(' ')
            val start = sb.length
            sb.append(piece.text)
            val change = piece.change ?: continue
            changes.add(change)
            spans.add(VoiceSpan(start + piece.leadLength, start + piece.leadLength + change.corrected.length))
        }
        return VoiceCorrection(heard, sb.toString(), changes, spans, nameScore)
    }

    private fun tokenOf(raw: String): Token {
        val lead = raw.takeWhile { !it.isLetterOrDigit() }
        if (lead.length == raw.length) return Token(raw, "", "", raw)
        val trail = raw.takeLastWhile { !it.isLetterOrDigit() }
        val core = raw.substring(lead.length, raw.length - trail.length)
        return Token(lead, core, trail, raw)
    }

    /** The spoken word is the term plus a grammatical ending: not a recognition error, leave it alone. */
    private fun isInflectionOf(spoken: String, term: String): Boolean =
        spoken.length > term.length && spoken.startsWith(term) && spoken.substring(term.length) in INFLECTION_ENDINGS

    /** Largest edit distance still accepted: a quarter of the longer spelling for one word, a third for several. */
    private fun maxDistance(longest: Int, size: Int): Int = maxOf(1, longest / if (size == 1) 4 else 3)

    private fun bestMatch(tokens: List<Token>, start: Int, vocabulary: VoiceVocabulary): Match? {
        var best: Match? = null
        val maxSize = minOf(MAX_WINDOW, tokens.size - start)
        for (size in maxSize downTo 1) {
            val window = tokens.subList(start, start + size)
            if (window.any { it.core.isEmpty() }) continue
            // No punctuation inside a window: "Todes, Minen" are two separate words.
            if (size > 1 && window.dropLast(1).any { it.trail.isNotEmpty() }) continue
            if (size > 1 && window.drop(1).any { it.lead.isNotEmpty() }) continue

            val candidate = matchWindow(window, size, vocabulary) ?: continue
            if (best == null || isBetter(candidate, best)) best = candidate
        }
        return best
    }

    /** Exact matches beat fuzzy ones, then the higher score wins, then the longer window. */
    private fun isBetter(a: Match, b: Match): Boolean {
        if (a.exact != b.exact) return a.exact
        if (abs(a.score - b.score) > 1e-9) return a.score > b.score
        return a.size > b.size
    }

    private fun matchWindow(window: List<Token>, size: Int, vocabulary: VoiceVocabulary): Match? {
        val joinedCore = window.joinToString("") { it.core }
        val plain = Phonetics.fold(joinedCore)
        if (plain.length < 2) return null

        val exact = vocabulary.exact(plain)
        if (exact.isNotEmpty()) {
            val term = exact.firstOrNull { it.display == window.joinToString(" ") { t -> t.core } } ?: exact.first()
            val spoken = window.joinToString(" ") { it.core }
            if (size == 1 && term.protectedWord) return Match(size, term, 1.0, exact = true, unchanged = true)
            // One word that only differs in case: fix the spelling of names ("orgrimmar" -> "Orgrimmar", "dps" -> "DPS"),
            // but leave short everyday words ("loot", "tank", "pull") as spoken.
            if (size == 1 && spoken != term.display && spoken.equals(term.display, ignoreCase = true) && !term.fixesCase) {
                return Match(size, term, 1.0, exact = true, unchanged = true)
            }
            return Match(size, term, 1.0, exact = true, unchanged = spoken == term.display)
        }

        if (size == 1 && plain in STOP_WORDS) return null
        var firstStop: String? = null
        var lastStop: String? = null
        if (size > 1) {
            // A fuzzy match over several words needs real words: "a healer" must not swallow the "a".
            if (window.any { it.core.length < MIN_FUZZY_TOKEN_LENGTH }) return null
            val first = Phonetics.fold(window.first().core)
            val last = Phonetics.fold(window.last().core)
            if (window.all { Phonetics.fold(it.core) in STOP_WORDS }) return null
            // "in iron Ford" must not swallow the "in": a common word at the edge only belongs to names that start
            // (or end) with that very word ("The Barrens", "Die Exodar").
            if (first in STOP_WORDS) firstStop = first
            if (last in STOP_WORDS) lastStop = last
        }
        if (plain.length < VoiceTerm.MIN_FUZZY_LENGTH - 1) return null

        val scored = fuzzyMatch(plain, size, vocabulary, firstStop, lastStop) ?: return null
        return Match(size, scored.term, scored.score, exact = false, unchanged = false)
    }

    /** The one name the window sounds like, or null when none is close enough or two are equally close. */
    private fun fuzzyMatch(
        plain: String,
        size: Int,
        vocabulary: VoiceVocabulary,
        firstStop: String?,
        lastStop: String?,
    ): Scored? {
        val cologne = Phonetics.cologne(plain)
        val metaphone = Phonetics.metaphone(plain)
        // Best score per distinct spelling (the list may hold a name twice under different display forms).
        val byPlain = HashMap<String, Scored>()
        for (term in vocabulary.fuzzyTerms) {
            if (abs(term.plain.length - plain.length) > MAX_LENGTH_DIFFERENCE) continue
            if (firstStop != null && !term.plain.startsWith(firstStop)) continue
            if (lastStop != null && !term.plain.endsWith(lastStop)) continue
            if (isInflectionOf(plain, term.plain)) continue
            if (!sameStart(plain, cologne, term)) continue
            val distance = Phonetics.levenshtein(plain, term.plain)
            if (distance > maxDistance(maxOf(plain.length, term.plain.length), size)) continue
            val score = similarity(plain, cologne, metaphone, term, distance)
            val known = byPlain[term.plain]
            if (known == null || score > known.score) byPlain[term.plain] = Scored(term, score)
        }
        if (byPlain.isEmpty()) return null
        val ranked = byPlain.values.sortedByDescending { it.score }
        val top = ranked[0]
        if (top.score < (if (size == 1) SINGLE_THRESHOLD else MULTI_THRESHOLD)) return null
        // Ambiguous when another name is practically as close.
        if (ranked.size > 1 && top.score - ranked[1].score < AMBIGUITY_GAP) return null
        return top
    }

    /** The first sound has to agree: same first letter, or same first Cologne code for a consonant. */
    private fun sameStart(plain: String, cologne: String, term: VoiceTerm): Boolean {
        if (plain.isEmpty() || term.plain.isEmpty()) return false
        if (plain[0] == term.plain[0]) return true
        return cologne.isNotEmpty() && term.cologne.isNotEmpty() && cologne[0] == term.cologne[0] && plain[0] !in "aeiou"
    }

    /** Similarity of a spoken window and a name, see the class comment. Higher is closer, an exact copy is about 1. */
    private fun similarity(plain: String, cologne: String, metaphone: String, term: VoiceTerm, distance: Int): Double {
        val lengthSimilarity = 1.0 - distance.toDouble() / maxOf(plain.length, term.plain.length)
        val cologneSimilarity = Phonetics.codeSimilarity(cologne, term.cologne)
        val metaphoneSimilarity = Phonetics.codeSimilarity(metaphone, term.metaphone)
        val sound = (2 * maxOf(cologneSimilarity, metaphoneSimilarity) + minOf(cologneSimilarity, metaphoneSimilarity)) / 3.0
        val bonus = minOf(BONUS_MAX, maxOf(0.0, BONUS_PER_LETTER * (term.plain.length - BONUS_FROM_LENGTH)))
        return LENGTH_WEIGHT * lengthSimilarity + (1.0 - LENGTH_WEIGHT) * sound + bonus
    }
}
