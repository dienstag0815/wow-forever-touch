package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round trip with `tools/tester_code.py`: the vector file is written by the Python tool (fake data), this side must read and write the same bytes. */
class TesterCodeTest {

    private data class Vector(val code: String, val kind: SourceKind, val address: String, val token: String, val prerelease: Boolean)

    private val vectors: List<Vector> by lazy {
        val stream = javaClass.getResourceAsStream("/update/tester_code_vectors.txt") ?: error("tester_code_vectors.txt missing")
        stream.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val f = line.split("|")
                Vector(f[0], SourceKind.fromKey(f[1]), f[2], f[3], f[4] == "1")
            }
    }

    private fun ok(code: String): TesterCodeData = (TesterCode.decode(code) as TesterCodeResult.Ok).data

    private fun reason(code: String?): UiText = (TesterCode.decode(code) as TesterCodeResult.Invalid).reason

    @Test
    fun vectorFileHasCases() {
        assertTrue(vectors.size >= 5)
    }

    @Test
    fun everyPythonCodeIsReadByKotlin() {
        for (v in vectors) {
            val data = ok(v.code)
            assertEquals(v.kind, data.kind)
            assertEquals(v.address, data.repo.displayUrl)
            assertEquals(v.token, data.token)
            assertEquals(v.prerelease, data.includePrereleases)
        }
    }

    @Test
    fun kotlinWritesTheSameBytesAsPython() {
        for (v in vectors) {
            assertEquals(v.code, TesterCode.encode(v.kind, v.address, v.token, v.prerelease))
        }
    }

    @Test
    fun everySingleTypingErrorIsCaught() {
        val code = vectors.first().code
        for (i in TesterCode.PREFIX.length until code.length) {
            for (replacement in "AZaz09-_") {
                if (code[i] == replacement) continue
                val broken = code.substring(0, i) + replacement + code.substring(i + 1)
                assertTrue("position $i -> $replacement", TesterCode.decode(broken) is TesterCodeResult.Invalid)
            }
        }
    }

    @Test
    fun aTruncatedOrExtendedCodeIsRefused() {
        val code = vectors.first().code
        assertEquals(UiText.res(R.string.launcher_tester_err_checksum), reason(code.dropLast(1)))
        assertEquals(UiText.res(R.string.launcher_tester_err_checksum), reason(code + "A"))
    }

    @Test
    fun copyingAddsWhitespaceWhichIsIgnored() {
        val v = vectors.first()
        val wrapped = "  " + v.code.substring(0, 20) + "\n" + v.code.substring(20, 60) + "\r\n " + v.code.substring(60) + "\n"
        assertEquals(v.token, ok(wrapped).token)
    }

    @Test
    fun nonsenseGetsAnExplanation() {
        assertEquals(UiText.res(R.string.launcher_tester_err_empty), reason(null))
        assertEquals(UiText.res(R.string.launcher_tester_err_empty), reason("  \n"))
        assertEquals(UiText.res(R.string.launcher_tester_err_prefix), reason("hello world"))
        assertEquals(UiText.res(R.string.launcher_tester_err_prefix), reason("WFT2-" + "A".repeat(40)))
        assertEquals(UiText.res(R.string.launcher_tester_err_checksum), reason("WFT1-abc"))
    }

    @Test
    fun aCodeWithAnUnusableAddressIsRefusedWithTheAddressReason() {
        val http = TesterCode.encode(SourceKind.Gitea, "http://git.example.org/o/r", "tok", false)
        assertEquals(UiText.res(R.string.launcher_source_err_not_https), reason(http))
        val broken = TesterCode.encode(SourceKind.Gitea, "https://git.example.org/o", "tok", false)
        assertEquals(UiText.res(R.string.launcher_source_err_malformed), reason(broken))
    }

    @Test
    fun aCodeWithGarbageInsideIsUnusableNotACrash() {
        // Valid prefix and checksum around a payload that is no base64 / no JSON.
        fun wrap(payload: String): String {
            val body = TesterCode.PREFIX + payload
            return body + checksumOf(body)
        }
        val unusable = UiText.res(R.string.launcher_tester_err_content)
        assertEquals(unusable, reason(wrap("!!!!!!!!!!!!")))
        assertEquals(unusable, reason(wrap("AAAAAAAAAAAAAAAA")))
    }

    // The checksum is private; recompute it the same way the format defines it (low 24 bits of CRC-32 as 4 base64url characters).
    private fun checksumOf(body: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val crc = java.util.zip.CRC32().apply { update(body.toByteArray(Charsets.US_ASCII)) }.value and 0xFFFFFFL
        return listOf(18, 12, 6, 0).map { alphabet[((crc shr it) and 0x3F).toInt()] }.joinToString("")
    }

    @Test
    fun addressAndTokenAreNotReadableInTheCode() {
        for (v in vectors) {
            assertFalse(v.code.contains("example", ignoreCase = true))
            assertFalse(v.code.contains("github", ignoreCase = true))
            if (v.token.length > 6) assertFalse(v.code.contains(v.token))
        }
    }

    @Test
    fun noReasonTextEchoesAnything() {
        // Reasons are resource ids without arguments: nothing of the pasted code can end up in a message.
        val r = reason("WFT1-secret-looking-garbage-0000")
        assertTrue(r is UiText.Res && (r as UiText.Res).args.isEmpty())
    }
}
