package app.gamenative.update

import app.gamenative.R
import app.gamenative.ui.screen.wow.launcher.UiText
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.CRC32
import org.json.JSONObject

/** What a tester code carries; the token is empty for a public repository. */
data class TesterCodeData(val kind: SourceKind, val repo: RepoRef, val token: String, val includePrereleases: Boolean)

sealed class TesterCodeResult {
    data class Ok(val data: TesterCodeData) : TesterCodeResult()
    data class Invalid(val reason: UiText) : TesterCodeResult()
}

/**
 * Tester code for Settings -> Updates -> Update source -> "Ich bin Tester": ONE string with server kind, repository address, access
 * token and the pre-release switch. Made by `tools/tester_code.py` (same format, round trip checked with a shared vector file).
 *
 * `WFT1-` + payload + check. Payload: base64url (no padding) of the compact JSON `{"k":kind,"u":address,"t":token,"p":0|1}` XORed with
 * a keystream SHA-256(KEY || counter, 4 bytes big endian). Check: 4 base64url characters = the low 24 bits of CRC-32 over
 * `WFT1-` + payload, so a typing error is caught before anything is saved.
 *
 * The key is public (it is in this app), so this is only obfuscation: address and token are not readable at a glance. It is not
 * encryption. The protection comes from the token: one read-only token per tester, revoked on the server when the test ends.
 * Texts name the problem, never the content of a code.
 */
object TesterCode {
    const val PREFIX = "WFT1-"
    private const val MAX_LENGTH = 4000
    private val KEY = "wow-forever/tester-code/v1".toByteArray(Charsets.US_ASCII)
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun keystream(length: Int): ByteArray {
        val out = ByteArray(length)
        var filled = 0
        var counter = 0
        while (filled < length) {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(KEY)
            digest.update(byteArrayOf((counter ushr 24).toByte(), (counter ushr 16).toByte(), (counter ushr 8).toByte(), counter.toByte()))
            val block = digest.digest()
            val n = minOf(block.size, length - filled)
            System.arraycopy(block, 0, out, filled, n)
            filled += n
            counter++
        }
        return out
    }

    private fun xor(data: ByteArray): ByteArray {
        val stream = keystream(data.size)
        return ByteArray(data.size) { (data[it].toInt() xor stream[it].toInt()).toByte() }
    }

    private fun check(text: String): String {
        val crc = CRC32().apply { update(text.toByteArray(Charsets.US_ASCII)) }.value and 0xFFFFFFL
        return listOf(18, 12, 6, 0).map { ALPHABET[((crc shr it) and 0x3F).toInt()] }.joinToString("")
    }

    /** JSON string literal the way the Python tool writes it (ensure_ascii, no escaped slash), so both sides produce the same bytes. */
    private fun jsonString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c == '\b' -> append("\\b")
                c.code == 0x0c -> append("\\f")
                c.code < 0x20 || c.code > 0x7f -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    /** Builds a code (tests and tools; the code for testers comes from the Python tool, which writes the same bytes). */
    fun encode(kind: SourceKind, address: String, token: String, includePrereleases: Boolean): String {
        val json = buildString {
            append("{\"k\":").append(jsonString(kind.key))
            append(",\"p\":").append(if (includePrereleases) 1 else 0)
            if (token.isNotEmpty()) append(",\"t\":").append(jsonString(token))
            append(",\"u\":").append(jsonString(address)).append('}')
        }
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(xor(json.toByteArray(Charsets.US_ASCII)))
        val body = PREFIX + payload
        return body + check(body)
    }

    /** Reads a pasted code: spaces and line breaks are ignored; wrong prefix, length, checksum, content or address give the reason. */
    fun decode(input: String?): TesterCodeResult {
        val text = input.orEmpty().filterNot { it.isWhitespace() }
        if (text.isEmpty()) return TesterCodeResult.Invalid(UiText.res(R.string.launcher_tester_err_empty))
        if (!text.startsWith(PREFIX)) return TesterCodeResult.Invalid(UiText.res(R.string.launcher_tester_err_prefix))
        if (text.length > MAX_LENGTH || text.length < PREFIX.length + 8) {
            return TesterCodeResult.Invalid(UiText.res(R.string.launcher_tester_err_checksum))
        }
        val body = text.dropLast(4)
        if (check(body) != text.takeLast(4)) return TesterCodeResult.Invalid(UiText.res(R.string.launcher_tester_err_checksum))
        val unusable = TesterCodeResult.Invalid(UiText.res(R.string.launcher_tester_err_content))
        val obj = try {
            val raw = Base64.getUrlDecoder().decode(body.removePrefix(PREFIX))
            JSONObject(String(xor(raw), Charsets.UTF_8))
        } catch (e: Exception) {
            return unusable
        }
        val kind = SourceKind.entries.firstOrNull { it.key == obj.optString("k", "") } ?: return unusable
        val address = obj.optString("u", "").takeIf { it.isNotEmpty() } ?: return unusable
        val ref = when (val parsed = UpdateSourceLogic.parseRepo(kind, address)) {
            is RepoParse.Ok -> parsed.ref
            is RepoParse.Invalid -> return TesterCodeResult.Invalid(parsed.reason)
        }
        val token = obj.optString("t", "").trim()
        if (token.any { it.isWhitespace() }) return unusable
        return TesterCodeResult.Ok(TesterCodeData(kind, ref, token, obj.optInt("p", 0) == 1))
    }
}
