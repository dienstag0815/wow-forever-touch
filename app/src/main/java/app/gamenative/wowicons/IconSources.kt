package app.gamenative.wowicons

/** Where icons come from. Nothing is bundled with the app; the pictures are fetched on the device and cached privately. */
object IconUrls {
    /** Neutral name for the User-Agent header of every request. */
    const val USER_AGENT = "WoWForeverLauncher"

    /** Blizzard's own render CDN (the one the Armory and the API media assets use); takes file ids and icon names. */
    fun render(fileIdOrName: String): String = "https://render.worldofwarcraft.com/us/icons/56/$fileIdOrName.jpg"

    fun render(fileId: Long): String = render(fileId.toString())

    /** Wowhead's icon CDN, by icon name only; used when the Blizzard CDN does not have the picture. */
    fun zamimg(name: String): String = "https://wow.zamimg.com/images/wow/icons/large/$name.jpg"

    /** Wowhead tooltip data of a spell: JSON with the field `icon` (the icon name). */
    fun wowheadSpell(id: Long): String = "https://nether.wowhead.com/tooltip/spell/$id"

    fun wowheadItem(id: Long): String = "https://nether.wowhead.com/tooltip/item/$id"
}

/** Reads the Wowhead tooltip JSON (`{"name":"Fireball","icon":"spell_fire_flamebolt","tooltip":"<table>...", ...}`). */
object WowheadJson {
    data class Tooltip(val name: String?, val icon: String?)

    /** The icon name of a tooltip answer, null for error answers (`{"error":"ID is out of range"}`) or unusable text. */
    fun iconName(json: String): String? = parse(json)?.icon

    fun parse(json: String): Tooltip? {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return null
        if (root["error"] != null) return null
        val icon = (root["icon"] as? String)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it.all(::isIconChar) }
        return Tooltip((root["name"] as? String)?.takeIf { it.isNotBlank() }, icon)
    }

    private fun isIconChar(c: Char) = c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-'
}

/** Smallest JSON reader that is enough for the tooltip answers: objects, arrays, strings, numbers, booleans, null. */
internal object MiniJson {
    fun parse(text: String): Any? =
        try {
            val reader = Reader(text)
            val value = reader.value(0)
            reader.skipSpace()
            if (reader.atEnd()) value else null
        } catch (e: IllegalArgumentException) {
            null
        }

    private class Reader(private val s: String) {
        private var pos = 0

        fun atEnd() = pos >= s.length

        fun skipSpace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(depth: Int): Any? {
            require(depth < 40) { "too deep" }
            skipSpace()
            require(pos < s.length) { "end" }
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("unexpected $c")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, pos)) { "literal" }
            pos += word.length
            return value
        }

        private fun obj(depth: Int): Map<String, Any?> {
            pos++
            val map = LinkedHashMap<String, Any?>()
            skipSpace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipSpace()
                val key = str()
                skipSpace()
                require(peek() == ':') { "colon" }
                pos++
                map[key] = value(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("object")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            pos++
            val list = ArrayList<Any?>()
            skipSpace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(value(depth + 1))
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("array")
                }
            }
        }

        private fun str(): String {
            require(peek() == '"') { "string" }
            pos++
            val out = StringBuilder()
            while (true) {
                require(pos < s.length) { "string end" }
                val c = s[pos++]
                if (c == '"') return out.toString()
                if (c != '\\') {
                    out.append(c)
                    continue
                }
                require(pos < s.length) { "escape" }
                when (val e = s[pos++]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'u' -> {
                        require(pos + 4 <= s.length) { "unicode" }
                        out.append(s.substring(pos, pos + 4).toInt(16).toChar())
                        pos += 4
                    }
                    else -> out.append(e)
                }
            }
        }

        private fun num(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("number")
        }

        private fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'
    }
}
