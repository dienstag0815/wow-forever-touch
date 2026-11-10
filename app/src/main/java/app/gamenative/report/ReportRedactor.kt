package app.gamenative.report

import java.net.URLEncoder

/** Art der Quelle; wenige Regeln gelten nur für eine Art. */
enum class LogKind { GENERAL, CONNECTION, CONFIG_WTF }

/**
 * Schwärzt Text, bevor er in den Fehlerbericht kommt (reine Kotlin-Klasse, JUnit-getestet). Der Bericht ist zum Weitergeben
 * gedacht, daher gilt: lieber zu viel als zu wenig schwärzen.
 *
 * Reihenfolge: gespeicherte Passwörter (exakt, auch URL-kodiert, ohne Beachtung der Groß-/Kleinschreibung) -> [PASSWORT];
 * gespeicherte E-Mail-Adressen (ebenso) -> [EMAIL]; weitere Geheimnisse (z. B. MachineGuid) -> [GEHEIM]; Konto-Zeilen der
 * Config.wtf; JWTs und Anmelde-Tickets; lange Werte hinter Schlüsseln wie token, ticket, session, auth -> [GEHEIM];
 * jede E-Mail-Adresse (auch `%40`) -> [EMAIL]; in der Connection.log zusätzlich lange Hex-/Base64-Werte ohne Schlüssel.
 *
 * Passwörter und Geheimnisse unter [MIN_SECRET_LENGTH] Zeichen werden nicht gesucht (sie würden das Log zerreißen; Battle.net
 * verlangt mindestens 8 Zeichen). Die Passwörter selbst werden nie geloggt und nur als Muster im Speicher gehalten.
 */
class ReportRedactor(
    emails: Collection<String> = emptyList(),
    passwords: Collection<String> = emptyList(),
    extraSecrets: Collection<String> = emptyList(),
) {
    private val passwordPatterns = literalPatterns(passwords.filter { it.length >= MIN_SECRET_LENGTH }, passwordVariants = true)
    private val emailPatterns = literalPatterns(emails.filter { it.isNotBlank() }, passwordVariants = false)
    private val secretPatterns = literalPatterns(extraSecrets.filter { it.length >= MIN_SECRET_LENGTH }, passwordVariants = false)

    fun redact(text: String, kind: LogKind = LogKind.GENERAL): String {
        var out = text
        for (p in passwordPatterns) out = p.replace(out, PASSWORD)
        for (p in emailPatterns) out = p.replace(out, EMAIL)
        for (p in secretPatterns) out = p.replace(out, SECRET)
        out = WTF_ACCOUNT_LINE.replace(out) { it.groupValues[1] + "\"" + ACCOUNT + "\"" }
        out = JWT.replace(out, SECRET)
        out = TICKET.replace(out, SECRET)
        out = KEYED_VALUE.replace(out) { m ->
            val scheme = if (m.groupValues[3].isNotEmpty()) m.groupValues[3] + " " else ""
            m.groupValues[1] + m.groupValues[2] + scheme + SECRET
        }
        out = WTF_ACCOUNT_PATH.replace(out) { it.groupValues[1] + ACCOUNT }
        out = ANY_EMAIL.replace(out, EMAIL)
        if (kind == LogKind.CONNECTION) {
            out = LONG_HEX.replace(out, SECRET)
            out = LONG_BASE64.replace(out, SECRET)
        }
        return out
    }

    companion object {
        const val PASSWORD = "[PASSWORT]"
        const val EMAIL = "[EMAIL]"
        const val SECRET = "[GEHEIM]"
        const val ACCOUNT = "[KONTO]"
        const val MIN_SECRET_LENGTH = 4

        private const val VALUE_CHARS = "A-Za-z0-9+/=_~%.\\-"

        private val ANY_EMAIL = Regex("""[A-Za-z0-9._%+\-]{1,64}(?:@|%40)[A-Za-z0-9.\-]{1,255}\.[A-Za-z]{2,}""", RegexOption.IGNORE_CASE)

        /** Kontoordner in Pfaden wie `WTF/Account/<Ordner>/…` (Logs, Pfadangaben); der Ordnername ist der Konto-Name. */
        private val WTF_ACCOUNT_PATH = Regex("""(WTF[\\/]+Account[\\/]+)[^\\/\s"']+""", RegexOption.IGNORE_CASE)

        /** `SET accountName "..."`, `SET accountList "..."`, `SET lastSelected... "..."`: der ganze Wert wird ersetzt. */
        private val WTF_ACCOUNT_LINE = Regex(
            """^([ \t]*SET[ \t]+(?:accountName|accountList|lastSelected\w*)[ \t]+)[^\r\n]*""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )

        private val JWT = Regex("""eyJ[A-Za-z0-9_\-]{5,}\.[A-Za-z0-9_\-]{5,}\.[A-Za-z0-9_\-]*""")

        /** Anmelde-Tickets der Form `US-<hex>-<zahl>`. */
        private val TICKET = Regex("""\b[A-Z]{2}-[0-9a-fA-F]{20,}(?:-\d+)?\b""")

        /**
         * Schlüssel mit token/ticket/session/auth/cookie/secret/..., danach `=` oder `:` und ein langer Wert. Kürzere Werte
         * zählen nur mit Ziffer (sonst würden Wörter wie `session=connected_to_realm` geschwärzt), ab 32 Zeichen immer.
         */
        private val KEYED_VALUE = Regex(
            """(\b[\w.\-]{0,40}(?:token|ticket|session|auth|cookie|secret|credential|apikey|api_key|password|passwd|pwd)[\w.\-]{0,40})""" +
                """(["']?[ \t]*[:=][ \t]*["']?)(?:(bearer|basic)[ \t]+)?""" +
                """((?:(?=[$VALUE_CHARS]*\d)[$VALUE_CHARS]{12,}|[$VALUE_CHARS]{32,}))""",
            RegexOption.IGNORE_CASE,
        )

        private val LONG_HEX = Regex("""\b[0-9a-fA-F]{32,}\b""")
        private val LONG_BASE64 = Regex("""(?<![A-Za-z0-9+/_\-])(?=[A-Za-z0-9+/_\-]*\d)[A-Za-z0-9+/_\-]{40,}={0,2}""")

        private fun literalPatterns(values: Collection<String>, passwordVariants: Boolean): List<Regex> {
            val all = linkedSetOf<String>()
            for (v in values) {
                all += v
                all += formEncoded(v)
                all += percentEncoded(v)
                if (passwordVariants) {
                    all += jsonEscaped(v)
                    all += v.trim()
                }
            }
            return all.filter { it.isNotEmpty() }
                .sortedByDescending { it.length }
                .map { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) }
        }

        private fun formEncoded(v: String) = URLEncoder.encode(v, "UTF-8")

        private fun percentEncoded(v: String) = formEncoded(v).replace("+", "%20")

        private fun jsonEscaped(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
