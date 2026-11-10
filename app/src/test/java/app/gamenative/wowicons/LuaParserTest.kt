package app.gamenative.wowicons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LuaParserTest {
    private fun table(text: String, name: String = "X"): LuaTable = LuaParser.parse(text)[name] as LuaTable

    @Test
    fun readsNumbersStringsAndBooleans() {
        val vars = LuaParser.parse("A = 5\nB = -3\nC = 2.5\nD = \"hi\"\nE = true\nF = false\nG = 0x1F\nH = 1e3\nI = -1.5e-2")
        assertEquals(5L, vars["A"])
        assertEquals(-3L, vars["B"])
        assertEquals(2.5, vars["C"])
        assertEquals("hi", vars["D"])
        assertEquals(true, vars["E"])
        assertEquals(false, vars["F"])
        assertEquals(31L, vars["G"])
        assertEquals(1000.0, vars["H"])
        assertEquals(-0.015, vars["I"] as Double, 1e-12)
    }

    @Test
    fun nilAssignmentRemovesTheVariable() {
        val vars = LuaParser.parse("A = 1\nA = nil\nB = 2")
        assertFalse(vars.containsKey("A"))
        assertEquals(2L, vars["B"])
    }

    @Test
    fun nestedTablesWithBracketKeysNamesAndPositions() {
        val t = table("X = { [\"a\"] = { [1] = \"x\", [2] = \"y\" }, b = 7, \"first\", \"second\"; [10] = true, }")
        assertEquals("x", t.table("a")!!.get(1))
        assertEquals(listOf<Any?>("x", "y"), t.table("a")!!.list())
        assertEquals(7L, t.long("b"))
        assertEquals("first", t[1])
        assertEquals("second", t[2])
        assertEquals(true, t.bool(10))
    }

    @Test
    fun floatKeysWithWholeValuesEqualIntegerKeys() {
        val t = table("X = { [1.0] = \"one\" }")
        assertEquals("one", t[1])
        assertEquals("one", t[1L])
    }

    @Test
    fun stringEscapes() {
        val t = table("X = { a = \"say \\\"hi\\\"\", b = \"tab\\there\", c = \"back\\\\slash\", d = \"\\65\\066\", e = \"\\x41\", f = 'it\\'s', g = \"line\\nbreak\" }")
        assertEquals("say \"hi\"", t.string("a"))
        assertEquals("tab\there", t.string("b"))
        assertEquals("back\\slash", t.string("c"))
        assertEquals("AB", t.string("d"))
        assertEquals("A", t.string("e"))
        assertEquals("it's", t.string("f"))
        assertEquals("line\nbreak", t.string("g"))
    }

    @Test
    fun utf8TextPassesThrough() {
        assertEquals("Flammenstoß Größe", table("X = { s = \"Flammenstoß Größe\" }").string("s"))
    }

    @Test
    fun longStringsAndComments() {
        val text = """
            -- header comment
            X = { --[[ block
            comment ]] a = [[long
            text]], b = [==[with ]] inside]==], -- trailing
              c = 3 }
        """.trimIndent()
        val t = table(text)
        assertEquals("long\ntext", t.string("a"))
        assertEquals("with ]] inside", t.string("b"))
        assertEquals(3L, t.long("c"))
    }

    @Test
    fun nilInsideArrayLeavesAGap() {
        val t = table("X = { \"a\", nil, \"c\" }")
        assertEquals("a", t[1])
        assertNull(t[2])
        assertEquals("c", t[3])
        assertEquals(listOf<Any?>("a"), t.list())
        assertEquals(listOf(1L, 3L), t.numbered().map { it.first })
    }

    @Test
    fun nameFieldIsNotConfusedWithValueWords() {
        val t = table("X = { true, false, nil, trueish = 1, nilly = 2 }")
        assertEquals(true, t[1])
        assertEquals(false, t[2])
        assertEquals(1L, t.long("trueish"))
        assertEquals(2L, t.long("nilly"))
    }

    @Test
    fun multipleVariablesAndBom() {
        val vars = LuaParser.parse("\uFEFFA = { 1 }\nB = { 2 }\n")
        assertEquals(setOf("A", "B"), vars.keys)
    }

    @Test
    fun infinityAndNan() {
        val vars = LuaParser.parse("A = 1.#INF\nB = -inf\nC = math.huge")
        assertEquals(Double.POSITIVE_INFINITY, vars["A"])
        assertEquals(Double.NEGATIVE_INFINITY, vars["B"])
        assertEquals(Double.POSITIVE_INFINITY, vars["C"])
    }

    @Test
    fun truncatedFileThrows() {
        for (broken in listOf("X = { a = 1,", "X = { a = \"abc", "X = ", "X = { [1 = 2 }", "X = @")) {
            try {
                LuaParser.parse(broken)
                fail("should not parse: $broken")
            } catch (e: LuaParseException) {
                assertTrue(e.message!!.isNotEmpty())
            }
        }
    }

    @Test
    fun deepNestingIsRefusedNotCrashing() {
        val text = "X = " + "{".repeat(200) + "}".repeat(200)
        try {
            LuaParser.parse(text)
            fail("too deep")
        } catch (e: LuaParseException) {
            assertNotNull(e)
        }
    }

    @Test
    fun accessorsReturnNullForWrongTypes() {
        val t = table("X = { s = \"text\", n = 1.5 }")
        assertNull(t.long("s"))
        assertNull(t.long("n"))
        assertNull(t.string("n"))
        assertNull(t.table("s"))
        assertNull(t.bool("s"))
    }
}
