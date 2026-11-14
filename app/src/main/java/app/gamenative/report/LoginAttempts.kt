package app.gamenative.report

/** Wie ein Anmeldeversuch endete. */
sealed class AttemptResult {
    /** `Battle.net logon completed result="ERROR_OK (0)"`. */
    object Success : AttemptResult()

    /** Fehlercode aus `Fatal login error received` oder der Antwort auf das Formular, z. B. 14400. */
    data class Error(val code: Int) : AttemptResult()

    /** Kein Ergebnis im Log (Datei abgeschnitten, Spiel beendet, Authenticator offen, Format unbekannt). */
    object Open : AttemptResult()
}

/** Der Realm-Teil eines Versuchs (`Realm authentication completed result=...`); nie eine Realm-Adresse. */
sealed class RealmResult {
    object Ok : RealmResult()
    data class Error(val code: Int) : RealmResult()
    object NotReached : RealmResult()
}

/** Die festen Kürzel der Schritte einer Anmeldung (in der Reihenfolge des Ablaufs) und ihre Zeilen in der `Connection.log`. */
object LoginStep {
    const val LOGON_RESPONSE = "logon_response"
    const val LOGON_FORM = "logon_form"
    const val SRP = "srp"
    const val FORM_SUBMISSION = "form_submission"
    const val CREDENTIALS = "credentials"
    const val LOGON_COMPLETED = "logon_completed"
    const val REALM_AUTH = "realm_auth"
    const val FATAL = "fatal"
    const val UNKNOWN = "unknown"

    /** Alle Kürzel in der Reihenfolge des Ablaufs; `fatal` und `unknown` zuletzt. */
    val ALL = listOf(LOGON_RESPONSE, LOGON_FORM, SRP, FORM_SUBMISSION, CREDENTIALS, LOGON_COMPLETED, REALM_AUTH, FATAL, UNKNOWN)

    fun isValid(code: String?) = code != null && code in ALL
}

/**
 * Ein Anmeldeversuch aus der `Connection.log`. Nur diese Felder gibt es; was nicht geparst und geprüft wurde, steht nirgends
 * (Erlaubnisliste durch den Aufbau: [LoginAttempts.parse] liest je Feld nur ein enges Muster, nie eine ganze Zeile).
 */
data class LoginAttempt(
    /** `MM/dd HH:mm:ss` wie im Log (Ortszeit des Geräts, ohne Jahr). */
    val time: String,
    /** Nummer des Spiellaufs (Zähler der Zeilen „Client connection initialized“); 0 = vor dem ersten Zähler im vorhandenen Ausschnitt. */
    val run: Int,
    val connectionId: Int?,
    val usedToken: Boolean?,
    val authenticatorRequired: Boolean?,
    val result: AttemptResult,
    /** Millisekunden vom Beginn des Versuchs bis zum Ergebnis; null ohne Ergebnis. */
    val durationMs: Long?,
    /** Plattform des Clients (`platform="WinA"`, vier Zeichen aus Buchstaben und Ziffern) und Sprache (`locale="enUS"`). */
    val platform: String? = null,
    val locale: String? = null,
    /** `(enum)N` aus `loginType`. */
    val loginType: String? = null,
    /** `launcherPortal`: true = ein Wert gesetzt, false = `nullopt` (der Wert selbst wird nie ausgegeben). */
    val launcherPortalSet: Boolean? = null,
    /** Region aus `loginPortal`/`host`: nur `US`, `EU`, `KR`, `TW`, `CN`, sonst `other`. */
    val region: String? = null,
    val usedSso: Boolean? = null,
    val hasPayload: Boolean? = null,
    val hasCaptcha: Boolean? = null,
    val legalRequired: Boolean? = null,
    /** `numGameAccounts` (0..99). */
    val numGameAccounts: Int? = null,
    /** Platz in der Warteschlange (höchstens sechs Ziffern); der zuletzt gemeldete. */
    val queuePosition: Int? = null,
    /** Der Fehlerschritt ([stepFailed] true: das erste Ergebnis ungleich `ERROR_OK (0)`; sonst der letzte erreichte Schritt bei Erfolg), ein Kürzel aus [LoginStep]. */
    val step: String = LoginStep.UNKNOWN,
    val stepFailed: Boolean = false,
    /** Millisekunden vom Beginn des Versuchs bis zur Zeile des Schritts; null bei [LoginStep.UNKNOWN]. */
    val stepMs: Long? = null,
    val realm: RealmResult = RealmResult.NotReached,
)

/** Zusammenfassung über alle Versuche (Kopfzeile von `anmeldung.txt`). */
data class LoginAttemptStats(
    val total: Int,
    val successes: Int,
    val open: Int,
    val errorsByCode: Map<Int, Int>,
    /** In mindestens einem Lauf war die erste Anmeldung ok und eine spätere im selben Lauf wurde abgelehnt. */
    val firstOkThenRejectedInRun: Boolean,
    /** Irgendwann ein Erfolg und danach ein Fehler (auch über Läufe hinweg). */
    val successThenError: Boolean,
    /** Fehler je Schritt ([LoginAttempt.stepFailed]), in der Reihenfolge des Ablaufs. */
    val errorSteps: Map<String, Int> = emptyMap(),
    val realmOk: Int = 0,
    val realmError: Int = 0,
    val realmNotReached: Int = 0,
)

/**
 * Liest die Anmeldeversuche aus dem Text einer `Connection.log` (reine Kotlin-Logik, JUnit-getestet).
 *
 * Belegtes Format (Connection.log-Auszüge in den Issues #57 und #28 des Upstream-Projekts): `MM/dd HH:mm:ss.SSS  [N][Connection] <Text> key="value" ...`.
 * Gelesen werden nur diese Zeilen:
 *  - `Client connection initialized` zählt den Spiellauf.
 *  - `Glue login started` beginnt einen Versuch.
 *  - `Battle.net front connected connectionId="1" ... usedToken="false"` liefert connectionId und usedToken.
 *  - `Battle.net logon form submission response received result="..." authenticatorRequired="false"` liefert authenticatorRequired
 *    und bei einem Code ungleich 0 den Fehler.
 *  - `Fatal login error received result="Status has no name. (14400)"` ist der Fehler.
 *  - `Battle.net logon completed result="ERROR_OK (0)"` ist der Erfolg.
 *  - Die Schrittzeilen (`logon response`, `logon form`, `SRP endpoint`, `credentials`, `Realm authentication completed`) liefern den Fehlerschritt
 *    ([LoginStep]) und den Realm-Teil; weitere Felder (platform, locale, loginType, launcherPortal als set/none, Region als Kürzel, usedSSO,
 *    hasPayload, hasCaptcha, legalRequired, numGameAccounts, position) nur in engen Mustern.
 * Alle anderen Zeilen, auch unbekannte und abgeschnittene, werden übergangen. Aus Werten wird nur Ziffern-/true/false-Inhalt
 * übernommen; Kontoname, E-Mail, Token, Sitzungsschlüssel, IP und Servername sind nie ein Feld.
 */
object LoginAttempts {
    const val MAX_SHOWN = 20

    private val LINE = Regex("""^(\d{2}/\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})\s+\[[A-Z]]\[Connection]\s+(.*)$""")
    private val CONNECTION_ID = Regex("""\bconnectionId="(\d{1,6})"""")
    private val USED_TOKEN = Regex("""\busedToken="(true|false)"""")
    private val AUTH_REQUIRED = Regex("""\bauthenticatorRequired="(true|false)"""")
    private val RESULT_CODE = Regex("""\bresult="[^"]{0,80}\((\d{1,6})\)"""")
    private val PLATFORM = Regex("""\bplatform="([A-Za-z0-9]{4})"""")
    private val LOCALE = Regex("""\blocale="([a-z]{2}[A-Z]{2})"""")
    private val LOGIN_TYPE = Regex("""\bloginType="(\(enum\)\d{1,2})"""")
    private val LAUNCHER_PORTAL = Regex("""\blauncherPortal="([^"]{0,300})"""")
    private val LOGIN_PORTAL = Regex("""\bloginPortal="([^"]{0,200})"""")
    private val HOST = Regex("""\bhost="([^"]{0,200})"""")
    private val USED_SSO = Regex("""\busedSSO="(true|false)"""")
    private val HAS_PAYLOAD = Regex("""\bhasPayload="(true|false)"""")
    private val HAS_CAPTCHA = Regex("""\bhasCaptcha="(true|false)"""")
    private val LEGAL_REQUIRED = Regex("""\blegalRequired="(true|false)"""")
    private val NUM_GAME_ACCOUNTS = Regex("""\bnumGameAccounts="(\d{1,2})"""")
    private val POSITION = Regex("""\bposition="(\d{1,6})"""")
    private val REGION = Regex("""^(US|EU|KR|TW|CN)(?=\.)""", RegexOption.IGNORE_CASE)

    private const val RUN_START = "Client connection initialized"
    private const val ATTEMPT_START = "Glue login started"
    private const val FRONT_CONNECTED = "Battle.net front connected"
    private const val QUEUE_UPDATE = "Battle.net logon queue update received"
    private const val EXTERNAL_CHALLENGE = "Battle.net external challenge received"
    private const val FORM_RESPONSE = "Battle.net logon form submission response received"
    private const val FATAL = "Fatal login error received"
    private const val COMPLETED = "Battle.net logon completed result"
    private const val LOGON_REQUESTED = "Battle.net logon requested"

    /** Zeilen mit `result="… (Code)"`, je ein Schritt (Anfang der Zeile -> Kürzel). */
    private val STEP_LINES = listOf(
        "Battle.net logon response received" to LoginStep.LOGON_RESPONSE,
        "Battle.net logon form received" to LoginStep.LOGON_FORM,
        "Battle.net SRP endpoint response received" to LoginStep.SRP,
        FORM_RESPONSE to LoginStep.FORM_SUBMISSION,
        "Battle.net credentials response received" to LoginStep.CREDENTIALS,
        COMPLETED to LoginStep.LOGON_COMPLETED,
        "Realm authentication completed" to LoginStep.REALM_AUTH,
    )

    private const val DAY_MS = 24L * 3600 * 1000

    private class Builder(val time: String, val run: Int, val startMs: Long) {
        var connectionId: Int? = null
        var usedToken: Boolean? = null
        var authenticatorRequired: Boolean? = null
        var result: AttemptResult = AttemptResult.Open
        var durationMs: Long? = null
        var platform: String? = null
        var locale: String? = null
        var loginType: String? = null
        var launcherPortalSet: Boolean? = null
        var region: String? = null
        var usedSso: Boolean? = null
        var hasPayload: Boolean? = null
        var hasCaptcha: Boolean? = null
        var legalRequired: Boolean? = null
        var numGameAccounts: Int? = null
        var queuePosition: Int? = null

        /** Der erste Schritt mit Ergebnis ungleich 0 und seine Zeit. */
        var failedStep: String? = null
        var failedMs: Long? = null

        /** Der letzte Schritt mit Ergebnis 0 aus `logon_completed` und `realm_auth` und seine Zeit. */
        var reachedStep: String? = null
        var reachedMs: Long? = null
        var realm: RealmResult = RealmResult.NotReached
        fun done() = result !is AttemptResult.Open
        fun build(): LoginAttempt {
            val failed = failedStep
            val reached = reachedStep
            val (step, isFailed, stepMs) = when {
                failed != null -> Triple(failed, true, failedMs)
                result is AttemptResult.Success && reached != null -> Triple(reached, false, reachedMs)
                else -> Triple(LoginStep.UNKNOWN, false, null)
            }
            return LoginAttempt(
                time, run, connectionId, usedToken, authenticatorRequired, result, durationMs,
                platform, locale, loginType, launcherPortalSet, region, usedSso, hasPayload, hasCaptcha, legalRequired, numGameAccounts, queuePosition,
                step, isFailed, stepMs, realm,
            )
        }
    }

    /** `US`, `EU`, `KR`, `TW`, `CN` aus dem ersten Teil von `EU.actual.battle.net:1119`, sonst `other`; null ohne Wert. */
    private fun regionOf(value: String?): String? =
        value?.let { v -> REGION.find(v.trim())?.groupValues?.get(1)?.uppercase() ?: "other" }

    /** Alle Versuche in der Reihenfolge der Datei. */
    fun parse(text: String): List<LoginAttempt> {
        val attempts = mutableListOf<LoginAttempt>()
        var runNo = 0
        var current: Builder? = null

        fun close() {
            current?.let { attempts += it.build() }
            current = null
        }

        for (raw in text.lineSequence()) {
            val m = LINE.matchEntire(raw.trimEnd()) ?: continue
            val (day, hh, mm, ss, ms, message) = m.destructured
            val timeOfDayMs = ((hh.toLong() * 60 + mm.toLong()) * 60 + ss.toLong()) * 1000 + ms.toLong()
            val stamp = "$day $hh:$mm:$ss"

            fun begin(): Builder = Builder(stamp, runNo, timeOfDayMs).also { current = it }
            fun open(): Builder = current?.takeIf { !it.done() } ?: run { close(); begin() }
            // Zeilen nach dem Ergebnis (z. B. "Fatal login error" nach der Formular-Antwort) gehören noch zum selben Versuch.
            fun sameOrNew(): Builder = current ?: begin()
            fun since(b: Builder): Long {
                var d = timeOfDayMs - b.startMs
                if (d < 0) d += DAY_MS
                return d
            }
            fun finish(b: Builder, result: AttemptResult) {
                if (b.done()) return
                b.result = result
                b.durationMs = since(b)
            }
            fun fail(b: Builder, step: String, code: Int?) {
                if (b.failedStep == null) {
                    b.failedStep = step
                    b.failedMs = since(b)
                }
                // Ein Fehler im Realm-Teil ändert nichts daran, dass die Anmeldung selbst geklappt hat.
                if (code != null && code != 0 && step != LoginStep.REALM_AUTH) finish(b, AttemptResult.Error(code))
            }
            fun bool(r: Regex): Boolean? = r.find(message)?.groupValues?.get(1)?.let { it == "true" }

            when {
                message.startsWith(RUN_START) -> {
                    close()
                    runNo++
                }
                message.startsWith(ATTEMPT_START) -> {
                    close()
                    val b = begin()
                    LOGIN_TYPE.find(message)?.let { b.loginType = it.groupValues[1] }
                    LAUNCHER_PORTAL.find(message)?.let { b.launcherPortalSet = it.groupValues[1].isNotBlank() && it.groupValues[1] != "nullopt" }
                    regionOf(LOGIN_PORTAL.find(message)?.groupValues?.get(1))?.let { b.region = it }
                }
                message.startsWith(LOGON_REQUESTED) -> {
                    // Ohne vorherige Zeile "Glue login started" (abgeschnitten) beginnt hier der Versuch.
                    if (current == null || current!!.done()) {
                        close()
                        begin()
                    }
                    val b = current!!
                    if (b.region == null) regionOf(HOST.find(message)?.groupValues?.get(1))?.let { b.region = it }
                }
                message.startsWith(FRONT_CONNECTED) -> {
                    val b = open()
                    CONNECTION_ID.find(message)?.let { b.connectionId = it.groupValues[1].toIntOrNull() }
                    USED_TOKEN.find(message)?.let { b.usedToken = it.groupValues[1] == "true" }
                    PLATFORM.find(message)?.let { b.platform = it.groupValues[1] }
                    LOCALE.find(message)?.let { b.locale = it.groupValues[1] }
                }
                message.startsWith(QUEUE_UPDATE) -> {
                    val b = open()
                    POSITION.find(message)?.let { b.queuePosition = it.groupValues[1].toIntOrNull() }
                }
                message.startsWith(EXTERNAL_CHALLENGE) -> {
                    val b = open()
                    bool(USED_SSO)?.let { b.usedSso = it }
                    bool(HAS_PAYLOAD)?.let { b.hasPayload = it }
                }
                message.startsWith(FATAL) -> {
                    val b = sameOrNew()
                    fail(b, LoginStep.FATAL, RESULT_CODE.find(message)?.groupValues?.get(1)?.toIntOrNull())
                }
                else -> {
                    val step = STEP_LINES.firstOrNull { message.startsWith(it.first) }?.second ?: continue
                    // Das Ende der Anmeldung und der Realm-Teil gehören zum laufenden Versuch, auch wenn er schon ein Ergebnis hat.
                    val b = if (step == LoginStep.LOGON_COMPLETED || step == LoginStep.REALM_AUTH) sameOrNew() else open()
                    val code = RESULT_CODE.find(message)?.groupValues?.get(1)?.toIntOrNull()
                    when (step) {
                        LoginStep.LOGON_FORM -> bool(HAS_CAPTCHA)?.let { b.hasCaptcha = it }
                        LoginStep.FORM_SUBMISSION -> {
                            bool(AUTH_REQUIRED)?.let { b.authenticatorRequired = it }
                            bool(LEGAL_REQUIRED)?.let { b.legalRequired = it }
                        }
                        LoginStep.LOGON_COMPLETED -> NUM_GAME_ACCOUNTS.find(message)?.let { b.numGameAccounts = it.groupValues[1].toIntOrNull() }
                    }
                    when {
                        code == null -> Unit
                        code != 0 -> {
                            fail(b, step, code)
                            if (step == LoginStep.REALM_AUTH) b.realm = RealmResult.Error(code)
                        }
                        step == LoginStep.LOGON_COMPLETED -> {
                            finish(b, AttemptResult.Success)
                            b.reachedStep = step
                            b.reachedMs = since(b)
                        }
                        step == LoginStep.REALM_AUTH -> {
                            b.realm = RealmResult.Ok
                            b.reachedStep = step
                            b.reachedMs = since(b)
                        }
                    }
                }
            }
        }
        close()
        return attempts
    }

    /** Die Zeit (`MM/dd HH:mm:ss`) jeder Zeile „Client connection initialized“, in der Reihenfolge der Datei: ein Eintrag je Spiellauf im Text. */
    fun runStarts(text: String): List<String> = text.lineSequence().mapNotNull { raw ->
        LINE.matchEntire(raw.trimEnd())?.takeIf { it.groupValues[6].startsWith(RUN_START) }
            ?.let { "${it.groupValues[1]} ${it.groupValues[2]}:${it.groupValues[3]}:${it.groupValues[4]}" }
    }.toList()

    fun stats(attempts: List<LoginAttempt>): LoginAttemptStats {
        val errors = sortedMapOf<Int, Int>()
        for (a in attempts) (a.result as? AttemptResult.Error)?.let { errors[it.code] = (errors[it.code] ?: 0) + 1 }
        val firstOkThenRejected = attempts.groupBy { it.run }.values.any { inRun ->
            inRun.firstOrNull()?.result is AttemptResult.Success && inRun.drop(1).any { it.result is AttemptResult.Error }
        }
        val firstSuccess = attempts.indexOfFirst { it.result is AttemptResult.Success }
        val successThenError = firstSuccess >= 0 && attempts.drop(firstSuccess + 1).any { it.result is AttemptResult.Error }
        val failedSteps = attempts.filter { it.stepFailed }.groupingBy { it.step }.eachCount()
        return LoginAttemptStats(
            total = attempts.size,
            successes = attempts.count { it.result is AttemptResult.Success },
            open = attempts.count { it.result is AttemptResult.Open },
            errorsByCode = errors,
            firstOkThenRejectedInRun = firstOkThenRejected,
            successThenError = successThenError,
            errorSteps = LoginStep.ALL.filter { it in failedSteps }.associateWith { failedSteps.getValue(it) },
            realmOk = attempts.count { it.realm is RealmResult.Ok },
            realmError = attempts.count { it.realm is RealmResult.Error },
            realmNotReached = attempts.count { it.realm is RealmResult.NotReached },
        )
    }

    /** Der Name des Fehlers wie im Spiel: Code 14400 -> `BLZ51914400` (Muster aus den Berichten; nur für 14000..14999, sonst null). */
    fun blzName(code: Int): String? = if (code in 14000..14999) "BLZ5191${code - 10000}" else null

    fun resultText(result: AttemptResult): String = when (result) {
        AttemptResult.Success -> "ok"
        is AttemptResult.Error -> "error ${result.code}" + (blzName(result.code)?.let { " ($it)" } ?: "")
        AttemptResult.Open -> "no result in log"
    }

    /** Kopfzeile, eine Zeile Text. */
    fun headerLine(stats: LoginAttemptStats): String {
        val errors = if (stats.errorsByCode.isEmpty()) "none" else stats.errorsByCode.entries.joinToString(", ") { (code, n) -> "$code x$n" }
        val steps = if (stats.errorSteps.isEmpty()) "none" else stats.errorSteps.entries.joinToString(", ") { (step, n) -> "$step x$n" }
        val yesNo = { b: Boolean -> if (b) "yes" else "no" }
        return "attempts ${stats.total}, ok ${stats.successes}, errors: $errors, open ${stats.open}; error steps: $steps; " +
            "realm ok ${stats.realmOk}, error ${stats.realmError}, not reached ${stats.realmNotReached}; " +
            "first login in a run ok, later rejected: ${yesNo(stats.firstOkThenRejectedInRun)}; ok then rejected (any run): ${yesNo(stats.successThenError)}"
    }

    private fun flag(b: Boolean?) = when (b) { true -> "true"; false -> "false"; null -> "?" }

    private fun seconds(ms: Long?) = ms?.let { "%.1f s".format(java.util.Locale.ROOT, it / 1000.0) } ?: "-"

    /** `none` (`nullopt`), `set` (irgendein Wert; der Wert selbst steht nirgends) oder `?`. */
    fun portalText(set: Boolean?) = when (set) { true -> "set"; false -> "none"; null -> "?" }

    /** `form_submission failed +1.3 s`, `realm_auth reached +4.2 s` oder `unknown`. */
    fun stepText(a: LoginAttempt): String = when {
        a.step == LoginStep.UNKNOWN -> LoginStep.UNKNOWN
        else -> "${a.step} ${if (a.stepFailed) "failed" else "reached"} +${seconds(a.stepMs)}"
    }

    /** `ok`, `error 4` oder `not reached` (nie eine Realm-Adresse). */
    fun realmText(realm: RealmResult): String = when (realm) {
        RealmResult.Ok -> "ok"
        is RealmResult.Error -> "error ${realm.code}"
        RealmResult.NotReached -> "not reached"
    }

    /** Die ersten Spalten, die auch die Kurzform hat. */
    private fun baseColumns(a: LoginAttempt): List<String> = listOf(
        a.time,
        "run " + (if (a.run > 0) a.run.toString() else "?"),
        "conn " + (a.connectionId?.toString() ?: "?"),
        "usedToken " + flag(a.usedToken),
        "authenticatorRequired " + flag(a.authenticatorRequired),
        resultText(a.result),
        seconds(a.durationMs),
    )

    /** Eine Zeile je Versuch (für `anmeldung.txt`). */
    fun attemptLine(a: LoginAttempt): String = (
        baseColumns(a) + listOf(
            "step " + stepText(a),
            "realm_auth " + realmText(a.realm),
            "platform " + (a.platform ?: "?"),
            "locale " + (a.locale ?: "?"),
            "loginType " + (a.loginType ?: "?"),
            "launcherPortal " + portalText(a.launcherPortalSet),
            "region " + (a.region ?: "?"),
            "usedSSO " + flag(a.usedSso),
            "hasPayload " + flag(a.hasPayload),
            "hasCaptcha " + flag(a.hasCaptcha),
            "legalRequired " + flag(a.legalRequired),
            "gameAccounts " + (a.numGameAccounts?.toString() ?: "?"),
            "queue " + (a.queuePosition?.toString() ?: "-"),
        )
        ).joinToString(" | ")

    /** Kurzform für `kurz.txt`: die Spalten oben und dazu Fehlerschritt, Plattform, `launcherPortal set/none` und `usedSSO`. */
    fun attemptShort(a: LoginAttempt): String = (
        baseColumns(a) + listOf(
            "step " + stepText(a),
            a.platform ?: "?",
            "portal " + portalText(a.launcherPortalSet),
            "sso " + flag(a.usedSso),
        )
        ).joinToString(" | ")

    /** Inhalt von `anmeldung.txt`: Kopfzeile, Spaltenerklärung, die letzten [MAX_SHOWN] Versuche (älteste zuerst). */
    fun report(logText: String?): String {
        val attempts = if (logText.isNullOrEmpty()) emptyList() else parse(logText)
        val shown = attempts.takeLast(MAX_SHOWN)
        return buildString {
            appendLine("Login attempts from Connection.log (last ${shown.size} of ${attempts.size} found; fixed list of fields only)")
            appendLine(headerLine(stats(attempts)))
            appendLine(
                "columns: time (device clock) | game run | connectionId | usedToken | authenticatorRequired | result | time to result | " +
                    "step (first result other than ERROR_OK, else last step reached; time since the start) | realm_auth | platform | locale | loginType | " +
                    "launcherPortal (set/none, value never shown) | region (US/EU/KR/TW/CN/other) | usedSSO | hasPayload | hasCaptcha | legalRequired | " +
                    "game accounts | queue position",
            )
            if (shown.isEmpty()) appendLine("(no login attempts found in the log)")
            for (a in shown) appendLine(attemptLine(a))
        }
    }
}
