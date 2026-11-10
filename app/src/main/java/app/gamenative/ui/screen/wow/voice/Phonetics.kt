package app.gamenative.ui.screen.wow.voice

import java.text.Normalizer

/**
 * Pure string helpers for matching spoken text against the WoW word list: folding (case, accents, umlauts),
 * Cologne phonetics (German), a simplified Metaphone (English) and the Levenshtein distance.
 */
internal object Phonetics {
    /** Lower case, umlauts and accents removed (ä -> a, ß -> ss), only letters and digits kept. */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            when (ch) {
                'ß' -> sb.append("ss")
                'æ' -> sb.append("ae")
                'œ' -> sb.append("oe")
                'ø' -> sb.append('o')
                else -> sb.append(ch)
            }
        }
        val decomposed = Normalizer.normalize(sb, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            if (ch.isLetterOrDigit()) out.append(ch)
        }
        return out.toString()
    }

    /** Only the letters a-z of the folded text, upper case. */
    private fun asciiLetters(text: String): String {
        val folded = fold(text)
        val sb = StringBuilder(folded.length)
        for (ch in folded) if (ch in 'a'..'z') sb.append(ch.uppercaseChar())
        return sb.toString()
    }

    /** Kölner Phonetik. "Müller" -> "657", "Wikipedia" -> "3412". Empty for text without letters. */
    fun cologne(text: String): String {
        val w = asciiLetters(text)
        if (w.isEmpty()) return ""
        val raw = StringBuilder()
        for (i in w.indices) {
            val c = w[i]
            val prev = if (i > 0) w[i - 1] else ' '
            val next = if (i + 1 < w.length) w[i + 1] else ' '
            val code: String = when (c) {
                'A', 'E', 'I', 'J', 'O', 'U', 'Y' -> "0"
                'H' -> ""
                'B' -> "1"
                'P' -> if (next == 'H') "3" else "1"
                'D', 'T' -> if (next == 'C' || next == 'S' || next == 'Z') "8" else "2"
                'F', 'V', 'W' -> "3"
                'G', 'K', 'Q' -> "4"
                'C' ->
                    if (i == 0) {
                        if ("AHKLOQRUX".indexOf(next) >= 0) "4" else "8"
                    } else {
                        if (prev == 'S' || prev == 'Z') "8" else if ("AHKOQUX".indexOf(next) >= 0) "4" else "8"
                    }
                'X' -> if (prev == 'C' || prev == 'K' || prev == 'Q') "8" else "48"
                'L' -> "5"
                'M', 'N' -> "6"
                'R' -> "7"
                'S', 'Z' -> "8"
                else -> ""
            }
            raw.append(code)
        }
        val collapsed = StringBuilder()
        for (ch in raw) {
            if (collapsed.isEmpty() || collapsed[collapsed.length - 1] != ch) collapsed.append(ch)
        }
        val out = StringBuilder()
        for (i in collapsed.indices) {
            val ch = collapsed[i]
            if (ch != '0' || i == 0) out.append(ch)
        }
        return out.toString()
    }

    /** Simplified Metaphone for English names ("Kel'Thuzad" and "Kelthusad" share a key). */
    fun metaphone(text: String): String {
        var w = asciiLetters(text)
        if (w.isEmpty()) return ""
        if (w.startsWith("AE") || w.startsWith("GN") || w.startsWith("KN") || w.startsWith("PN") || w.startsWith("WR")) {
            w = w.substring(1)
        } else if (w.startsWith("WH")) {
            w = "W" + w.substring(2)
        } else if (w.startsWith("X")) {
            w = "S" + w.substring(1)
        }
        val word = w
        fun at(k: Int): Char = if (k >= 0 && k < word.length) word[k] else '\u0000'
        fun isVowel(c: Char): Boolean = c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U'

        val out = StringBuilder()
        var i = 0
        while (i < word.length) {
            val c = word[i]
            if (c == at(i - 1) && c != 'C') {
                i++
                continue
            }
            when (c) {
                'A', 'E', 'I', 'O', 'U' -> {
                    if (i == 0) out.append(c)
                }
                'B' -> {
                    if (!(at(i - 1) == 'M' && i == word.length - 1)) out.append('B')
                }
                'C' -> {
                    if (at(i + 1) == 'I' && at(i + 2) == 'A') {
                        out.append('X')
                    } else if (at(i + 1) == 'H') {
                        out.append('X')
                        i++
                    } else if (at(i + 1) == 'I' || at(i + 1) == 'E' || at(i + 1) == 'Y') {
                        if (at(i - 1) != 'S') out.append('S')
                    } else {
                        out.append('K')
                    }
                }
                'D' -> {
                    if (at(i + 1) == 'G' && (at(i + 2) == 'E' || at(i + 2) == 'I' || at(i + 2) == 'Y')) {
                        out.append('J')
                        i += 2
                    } else {
                        out.append('T')
                    }
                }
                'G' -> {
                    if (at(i + 1) == 'H') {
                        if (isVowel(at(i + 2))) out.append('K')
                        i++
                    } else if (at(i + 1) == 'N' &&
                        (i + 2 >= word.length || (at(i + 2) == 'E' && at(i + 3) == 'D' && i + 4 >= word.length))
                    ) {
                        // silent G in "gn" / "gned" at the end
                    } else if ((at(i + 1) == 'I' || at(i + 1) == 'E' || at(i + 1) == 'Y') && at(i - 1) != 'G') {
                        out.append('J')
                    } else {
                        out.append('K')
                    }
                }
                'H' -> {
                    val p = at(i - 1)
                    if (isVowel(at(i + 1)) && p != 'C' && p != 'S' && p != 'P' && p != 'T' && p != 'G') out.append('H')
                }
                'K' -> {
                    if (at(i - 1) != 'C') out.append('K')
                }
                'P' -> {
                    if (at(i + 1) == 'H') {
                        out.append('F')
                        i++
                    } else {
                        out.append('P')
                    }
                }
                'Q' -> out.append('K')
                'S' -> {
                    if (at(i + 1) == 'H') {
                        out.append('X')
                        i++
                    } else if (at(i + 1) == 'I' && (at(i + 2) == 'O' || at(i + 2) == 'A')) {
                        out.append('X')
                    } else {
                        out.append('S')
                    }
                }
                'T' -> {
                    if (at(i + 1) == 'I' && (at(i + 2) == 'O' || at(i + 2) == 'A')) {
                        out.append('X')
                    } else if (at(i + 1) == 'H') {
                        out.append('0')
                        i++
                    } else if (at(i + 1) == 'C' && at(i + 2) == 'H') {
                        // "tch": the T is silent
                    } else {
                        out.append('T')
                    }
                }
                'V' -> out.append('F')
                'W', 'Y' -> {
                    if (isVowel(at(i + 1))) out.append(c)
                }
                'X' -> out.append("KS")
                'Z' -> out.append('S')
                'F', 'J', 'L', 'M', 'N', 'R' -> out.append(c)
                else -> Unit
            }
            i++
        }
        return out.toString()
    }

    /** Classic edit distance (insert, delete, replace = 1). */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }

    /**
     * Similarity of two phonetic keys (or any strings) from 0.0 to 1.0: 1 minus the edit distance relative to the
     * longer key. 0.0 when one of them is empty, because an empty key says nothing about the sound.
     */
    fun codeSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
    }
}
