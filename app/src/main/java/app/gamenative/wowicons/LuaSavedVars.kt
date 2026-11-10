package app.gamenative.wowicons

/** A Lua table as WoW writes it into a SavedVariables file. Keys are [Long] (whole numbers), [Double] or [String]. */
class LuaTable(val entries: Map<Any, Any?>) {
    operator fun get(key: Any): Any? = entries[normalizeKey(key)]

    fun string(key: Any): String? = get(key) as? String

    fun long(key: Any): Long? = when (val v = get(key)) {
        is Long -> v
        is Double -> if (v == Math.floor(v) && !v.isInfinite() && Math.abs(v) < 9.0e15) v.toLong() else null
        else -> null
    }

    fun bool(key: Any): Boolean? = get(key) as? Boolean

    fun table(key: Any): LuaTable? = get(key) as? LuaTable

    /** The values of the keys 1, 2, 3, ... in order (stops at the first missing number). */
    fun list(): List<Any?> {
        val result = ArrayList<Any?>()
        var index = 1L
        while (entries.containsKey(index)) {
            result.add(entries[index])
            index++
        }
        return result
    }

    /** All whole-number keys with their values, ascending (also with gaps, as the action bar slots have). */
    fun numbered(): List<Pair<Long, Any?>> =
        entries.entries.filter { it.key is Long }.map { (it.key as Long) to it.value }.sortedBy { it.first }

    val size: Int get() = entries.size

    companion object {
        fun normalizeKey(key: Any): Any = when (key) {
            is Int -> key.toLong()
            is Double -> if (key == Math.floor(key) && !key.isInfinite() && Math.abs(key) < 9.0e15) key.toLong() else key
            else -> key
        }
    }
}

class LuaParseException(message: String, val position: Int) : Exception("$message (at $position)")

/**
 * Reads the text of a SavedVariables file: a sequence of global assignments `Name = <value>` where a value is nil, a
 * boolean, a number, a string (quoted with escapes, or long brackets) or a table `{ [key] = v, name = v, v, ... }`.
 * Comments (`--` and `--[[ ]]`) are skipped. Nothing is executed. A broken file (for instance cut off by a crash) throws
 * [LuaParseException]; the caller treats that as "no data".
 */
object LuaParser {
    private const val MAX_DEPTH = 64

    /** Global variables of the file by name. A variable set to nil is left out. */
    fun parse(text: String): Map<String, Any?> = Reader(text.removePrefix("﻿")).parseChunk()

    private class Reader(private val s: String) {
        private var pos = 0

        fun parseChunk(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            while (true) {
                skipSpace()
                if (pos >= s.length) return result
                if (s.startsWith("local", pos) && !isNameChar(s.getOrNull(pos + 5))) pos += 5
                skipSpace()
                val name = readName() ?: fail("variable name expected")
                // Field assignments such as Foo.bar = 1 do not occur in SavedVariables; accept simple names only.
                skipSpace()
                expect('=')
                val value = readValue(0)
                if (value == null) result.remove(name) else result[name] = value
                skipSpace()
                if (pos < s.length && s[pos] == ';') pos++
            }
        }

        private fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("nested too deeply")
            skipSpace()
            if (pos >= s.length) fail("value expected")
            val c = s[pos]
            return when {
                c == '{' -> readTable(depth + 1)
                c == '"' || c == '\'' -> readQuoted(c)
                c == '[' && isLongBracketStart(pos) -> readLongString()
                c == '-' || c == '+' || c == '.' || c.isDigit() -> readNumber()
                else -> {
                    when (val word = readName() ?: fail("unexpected '$c'")) {
                        "nil" -> null
                        "true" -> true
                        "false" -> false
                        "inf", "huge" -> Double.POSITIVE_INFINITY
                        "math" -> if (s.startsWith(".huge", pos)) {
                            pos += 5
                            Double.POSITIVE_INFINITY
                        } else {
                            fail("unexpected word 'math'")
                        }
                        "nan" -> Double.NaN
                        else -> fail("unexpected word '$word'")
                    }
                }
            }
        }

        private fun readTable(depth: Int): LuaTable {
            expect('{')
            val map = LinkedHashMap<Any, Any?>()
            var next = 1L
            while (true) {
                skipSpace()
                if (pos >= s.length) fail("table not closed")
                if (s[pos] == '}') {
                    pos++
                    break
                }
                if (s[pos] == '[' && !isLongBracketStart(pos)) {
                    pos++
                    val key = readValue(depth) ?: fail("nil as key")
                    skipSpace()
                    expect(']')
                    skipSpace()
                    expect('=')
                    put(map, LuaTable.normalizeKey(key), readValue(depth))
                } else if (isNameStart(s[pos]) && isNamedField()) {
                    val name = readName()!!
                    skipSpace()
                    expect('=')
                    put(map, name, readValue(depth))
                } else {
                    put(map, next, readValue(depth))
                    next++
                }
                skipSpace()
                if (pos < s.length && (s[pos] == ',' || s[pos] == ';')) pos++
            }
            return LuaTable(map)
        }

        private fun put(map: MutableMap<Any, Any?>, key: Any, value: Any?) {
            if (value == null) map.remove(key) else map[key] = value
        }

        /** True when the name at [pos] is followed by a single '=' (a field), not a value word such as true. */
        private fun isNamedField(): Boolean {
            var p = pos
            while (p < s.length && isNameChar(s[p])) p++
            while (p < s.length && s[p].isWhitespace()) p++
            return p < s.length && s[p] == '=' && s.getOrNull(p + 1) != '='
        }

        private fun readQuoted(quote: Char): String {
            pos++
            val out = StringBuilder()
            while (true) {
                if (pos >= s.length) fail("string not closed")
                val c = s[pos++]
                when {
                    c == quote -> return out.toString()
                    c == '\n' -> fail("line break in string")
                    c == '\\' -> readEscape(out)
                    else -> out.append(c)
                }
            }
        }

        private fun readEscape(out: StringBuilder) {
            if (pos >= s.length) fail("string not closed")
            val c = s[pos++]
            when (c) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'a' -> out.append('\u0007')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'v' -> out.append('\u000B')
                '\\', '"', '\'' -> out.append(c)
                '\n' -> out.append('\n')
                'x' -> {
                    val hex = s.substring(pos, minOf(pos + 2, s.length))
                    out.append(hex.toIntOrNull(16)?.toChar() ?: fail("bad \\x escape"))
                    pos += 2
                }
                'z' -> while (pos < s.length && s[pos].isWhitespace()) pos++
                'u' -> {
                    expect('{')
                    val end = s.indexOf('}', pos)
                    if (end < 0) fail("bad \\u escape")
                    val cp = s.substring(pos, end).toIntOrNull(16) ?: fail("bad \\u escape")
                    out.appendCodePoint(cp)
                    pos = end + 1
                }
                in '0'..'9' -> {
                    var digits = c.toString()
                    while (digits.length < 3 && pos < s.length && s[pos] in '0'..'9') digits += s[pos++]
                    val value = digits.toInt()
                    if (value > 255) fail("decimal escape too large")
                    out.append(value.toChar())
                }
                else -> out.append(c)
            }
        }

        private fun isLongBracketStart(at: Int): Boolean {
            if (s.getOrNull(at) != '[') return false
            var p = at + 1
            while (s.getOrNull(p) == '=') p++
            return s.getOrNull(p) == '['
        }

        private fun readLongString(): String {
            var level = 0
            pos++
            while (s[pos] == '=') {
                level++
                pos++
            }
            pos++ // second [
            if (s.getOrNull(pos) == '\r') pos++
            if (s.getOrNull(pos) == '\n') pos++
            val close = "]" + "=".repeat(level) + "]"
            val end = s.indexOf(close, pos)
            if (end < 0) fail("long string not closed")
            val text = s.substring(pos, end)
            pos = end + close.length
            return text
        }

        private fun readNumber(): Any {
            val start = pos
            if (s[pos] == '-' || s[pos] == '+') pos++
            if (s.startsWith("inf", pos) || s.startsWith("1.#INF", pos) || s.startsWith("math.huge", pos)) {
                val negative = s[start] == '-'
                pos = when {
                    s.startsWith("math.huge", pos) -> pos + 9
                    s.startsWith("1.#INF", pos) -> pos + 6
                    else -> pos + 3
                }
                return if (negative) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
            }
            if (s.startsWith("nan", pos)) {
                pos += 3
                return Double.NaN
            }
            if (s.startsWith("0x", pos) || s.startsWith("0X", pos)) {
                pos += 2
                val hexStart = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos].lowercaseChar() in 'a'..'f')) pos++
                val value = s.substring(hexStart, pos).toLongOrNull(16) ?: fail("bad hex number")
                return if (s[start] == '-') -value else value
            }
            var isFloat = false
            while (pos < s.length) {
                val c = s[pos]
                if (c.isDigit()) {
                    pos++
                } else if (c == '.') {
                    isFloat = true
                    pos++
                } else if (c == 'e' || c == 'E') {
                    isFloat = true
                    pos++
                    if (pos < s.length && (s[pos] == '-' || s[pos] == '+')) pos++
                } else {
                    break
                }
            }
            val text = s.substring(start, pos)
            if (!isFloat) text.toLongOrNull()?.let { return it }
            return text.toDoubleOrNull() ?: fail("bad number '$text'")
        }

        private fun readName(): String? {
            if (pos >= s.length || !isNameStart(s[pos])) return null
            val start = pos
            while (pos < s.length && isNameChar(s[pos])) pos++
            return s.substring(start, pos)
        }

        private fun isNameStart(c: Char) = c == '_' || c.isLetter()

        private fun isNameChar(c: Char?) = c != null && (c == '_' || c.isLetterOrDigit())

        private fun expect(c: Char) {
            if (pos >= s.length || s[pos] != c) fail("'$c' expected")
            pos++
        }

        private fun skipSpace() {
            while (pos < s.length) {
                val c = s[pos]
                if (c.isWhitespace()) {
                    pos++
                } else if (c == '-' && s.startsWith("--", pos)) {
                    skipComment()
                } else {
                    return
                }
            }
        }

        private fun skipComment() {
            pos += 2
            if (isLongBracketStart(pos)) {
                var level = 0
                var p = pos + 1
                while (s[p] == '=') {
                    level++
                    p++
                }
                val close = "]" + "=".repeat(level) + "]"
                val end = s.indexOf(close, p + 1)
                pos = if (end < 0) s.length else end + close.length
                return
            }
            while (pos < s.length && s[pos] != '\n') pos++
        }

        private fun fail(message: String): Nothing = throw LuaParseException(message, pos)
    }
}
