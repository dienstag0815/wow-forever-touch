package app.gamenative.ui.screen.wow

import app.gamenative.R
import app.gamenative.ui.components.TextRef
import java.security.MessageDigest
import kotlin.math.abs

// Reine Logik des Startchecks (ohne Android-Klassen, damit sie als JVM-Unit-Test laeuft):
// Messwerte -> Ampel/Text, "Login-Hinweis noetig?", Netz-Schluessel, Host-Zuordnung.

internal const val PORT_LOGIN = 1119
internal const val PORT_GAME = 3724
internal const val PORT_WEB = 443

/**
 * Ob ein Timeout auf Port 3724 (am Login-Host) als Sperre gilt. Auf dem Geraet pruefen: antwortet `<region>.actual.battle.net`
 * auf 3724 auch im Mobilfunk nicht, dann hier auf false stellen (3724 wird dann nur gemessen, nicht bewertet).
 */
internal const val GAME_PORT_DECISIVE = false

internal const val LOGIN_MAX_AGE_MS = 12L * 60 * 60 * 1000

enum class NetKind { NONE, WIFI, MOBILE, ETHERNET, OTHER }

enum class PortOutcome {
    /** Verbindung aufgebaut. */
    OPEN,

    /** Server/Netz hat aktiv abgelehnt (RST). */
    REFUSED,

    /** Keine Antwort in der Frist (typisch fuer eine Firewall, die Pakete verwirft). */
    TIMEOUT,

    /** Keine Route / Netz nicht erreichbar / Name nicht aufloesbar. */
    UNREACHABLE,
}

enum class Light { GREEN, YELLOW, RED }

data class PortProbeResult(val port: Int, val outcome: PortOutcome)

data class NetworkState(val kind: NetKind, val validated: Boolean, val captivePortal: Boolean, val vpn: Boolean)

data class StartCheckMeasurements(
    val network: NetworkState,
    /** Mindestens einer der beiden Blizzard-Hosts liess sich nicht aufloesen. */
    val dnsFailed: Boolean,
    val ports: List<PortProbeResult>,
    /** Latenzen der erfolgreichen Verbindungsmessungen in ms. */
    val latenciesMs: List<Long>,
    /** Anzahl der gestarteten Verbindungsmessungen (Erfolge + Ausfaelle). */
    val attempts: Int,
)

data class StartCheckVerdict(
    val light: Light,
    val headline: TextRef,
    val details: List<TextRef>,
    val tips: List<TextRef>,
    val medianMs: Long?,
    val jitterMs: Long?,
    val failures: Int,
    val attempts: Int,
)

internal val TIPS_RED = listOf(
    TextRef(R.string.game_sc_tip_other_net),
    TextRef(R.string.game_sc_tip_samsung),
)

internal fun medianOf(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/** Schwankung = mittlere absolute Abweichung vom Median (ein einzelner Ausreisser dominiert nicht). */
internal fun jitterOf(values: List<Long>): Long? {
    val median = medianOf(values) ?: return null
    if (values.size < 2) return 0L
    return values.sumOf { abs(it - median) } / values.size
}

internal fun classifyConnectFailure(message: String?): PortOutcome {
    val text = message.orEmpty().lowercase()
    return when {
        "refused" in text || "econnrefused" in text -> PortOutcome.REFUSED
        "timed out" in text || "timeout" in text || "etimedout" in text -> PortOutcome.TIMEOUT
        else -> PortOutcome.UNREACHABLE
    }
}

private fun netName(kind: NetKind) = when (kind) {
    NetKind.WIFI -> TextRef(R.string.game_sc_net_wifi)
    NetKind.MOBILE -> TextRef(R.string.game_sc_net_mobile)
    NetKind.ETHERNET -> TextRef(R.string.game_sc_net_ethernet)
    else -> TextRef(R.string.game_sc_net_other)
}

private fun alternativeTo(kind: NetKind) = when (kind) {
    NetKind.WIFI -> TextRef(R.string.game_sc_alt_wifi)
    NetKind.MOBILE -> TextRef(R.string.game_sc_alt_mobile)
    else -> TextRef(R.string.game_sc_alt_other)
}

private fun statsLine(attempts: Int, median: Long?, jitter: Long?, failures: Int): TextRef? {
    if (attempts <= 0) return null
    val timing = if (median != null) {
        TextRef(R.string.game_sc_stats_timing, median, jitter ?: 0L)
    } else {
        TextRef(R.string.game_sc_stats_none)
    }
    return TextRef(R.string.game_sc_stats, attempts, timing, failures, attempts)
}

fun evaluateStartCheck(m: StartCheckMeasurements, gamePortDecisive: Boolean = GAME_PORT_DECISIVE): StartCheckVerdict {
    val net = m.network
    val median = medianOf(m.latenciesMs)
    val jitter = jitterOf(m.latenciesMs)
    val failures = (m.attempts - m.latenciesMs.size).coerceAtLeast(0)

    if (net.kind == NetKind.NONE) {
        return StartCheckVerdict(
            Light.RED,
            TextRef(R.string.game_sc_no_net),
            emptyList(),
            TIPS_RED,
            null,
            null,
            0,
            0,
        )
    }

    val reds = mutableListOf<TextRef>()
    val yellows = mutableListOf<TextRef>()
    val name = netName(net.kind)

    fun outcomeOf(port: Int) = m.ports.firstOrNull { it.port == port }?.outcome
    val webOpen = outcomeOf(PORT_WEB) == PortOutcome.OPEN

    if (net.captivePortal) {
        reds += TextRef(R.string.game_sc_captive, name)
    } else if (!net.validated) {
        if (webOpen) {
            yellows += TextRef(R.string.game_sc_not_validated, name)
        } else {
            reds += TextRef(R.string.game_sc_no_internet, name)
        }
    }

    if (m.dnsFailed) {
        reds += TextRef(R.string.game_sc_dns)
    }

    // Port 3724 zaehlt nur, wenn gamePortDecisive gesetzt ist; ein ablehnendes (RST) 3724 ist nie ein Sperrbeweis.
    val relevant = buildList<Int> {
        add(PORT_LOGIN)
        add(PORT_WEB)
        if (gamePortDecisive) add(PORT_GAME)
    }.filter { port -> m.ports.any { it.port == port } }
    val blocked = relevant.filter { port ->
        val outcome = outcomeOf(port)
        outcome == PortOutcome.TIMEOUT || (outcome == PortOutcome.REFUSED && port != PORT_GAME)
    }
    val unrouted = if (m.dnsFailed) emptyList() else relevant.filter { outcomeOf(it) == PortOutcome.UNREACHABLE }

    if (blocked.isNotEmpty()) {
        reds += if (relevant.size >= 2 && blocked.size == relevant.size) {
            TextRef(R.string.game_sc_blocked_all, blocked.sorted().joinToString(", "), name, alternativeTo(net.kind))
        } else {
            val ports = blocked.sorted()
            if (ports.size == 1) {
                TextRef(R.string.game_sc_blocked_one, name, ports[0], alternativeTo(net.kind))
            } else {
                TextRef(R.string.game_sc_blocked_many, name, ports.joinToString(", "), alternativeTo(net.kind))
            }
        }
    }
    if (unrouted.isNotEmpty()) {
        reds += TextRef(R.string.game_sc_unrouted, unrouted.sorted().joinToString(", "))
    }

    if (m.attempts > 0 && !m.dnsFailed) {
        if (m.latenciesMs.isEmpty()) {
            if (PORT_WEB !in blocked && PORT_WEB !in unrouted) {
                reds += TextRef(R.string.game_sc_all_failed, m.attempts)
            }
        } else {
            val ratioHigh = failures * 3 >= m.attempts
            if (failures > 0 && ratioHigh) {
                reds += TextRef(R.string.game_sc_drops, failures, m.attempts)
            } else if (failures > 0) {
                yellows += TextRef(R.string.game_sc_few_drops, failures, m.attempts)
            }
            if (jitter != null && jitter >= JITTER_RED_MS) {
                reds += TextRef(R.string.game_sc_jitter_red, jitter, median ?: 0L)
            } else if (jitter != null && jitter >= JITTER_YELLOW_MS) {
                yellows += TextRef(R.string.game_sc_jitter_yellow, jitter, median ?: 0L)
            }
            if (median != null && median >= MEDIAN_RED_MS) {
                reds += TextRef(R.string.game_sc_slow_red, median)
            } else if (median != null && median >= MEDIAN_YELLOW_MS) {
                yellows += TextRef(R.string.game_sc_slow_yellow, median)
            }
        }
    }

    if (net.vpn) {
        yellows += TextRef(R.string.game_sc_vpn)
    }

    val light = when {
        reds.isNotEmpty() -> Light.RED
        yellows.isNotEmpty() -> Light.YELLOW
        else -> Light.GREEN
    }
    val findings = reds + yellows
    val headline = findings.firstOrNull() ?: TextRef(R.string.game_sc_ok)
    val details = findings.drop(1) + listOfNotNull(statsLine(m.attempts, median, jitter, failures))
    return StartCheckVerdict(
        light = light,
        headline = headline,
        details = details,
        tips = if (light == Light.RED) TIPS_RED else emptyList(),
        medianMs = median,
        jitterMs = jitter,
        failures = failures,
        attempts = m.attempts,
    )
}

private const val JITTER_YELLOW_MS = 80L
private const val JITTER_RED_MS = 250L
private const val MEDIAN_YELLOW_MS = 300L
private const val MEDIAN_RED_MS = 800L

// --- Login-Vorbereitung -------------------------------------------------------------------------------------------

data class LoginRecord(val atMs: Long, val networkKey: String)

enum class LoginHintReason { NEVER, NETWORK_CHANGED, STALE }

/**
 * Ob vor dem Start der Hinweis "im Browser/in der Battle.net-App anmelden" erscheint. Blizzard stuft ein unbekanntes
 * Netz/Geraet haeufiger als Risiko ein; ein frischer Erfolg im selben Netz gilt als sicher.
 */
fun loginHintReason(
    last: LoginRecord?,
    currentKey: String?,
    nowMs: Long,
    maxAgeMs: Long = LOGIN_MAX_AGE_MS,
): LoginHintReason? {
    if (last == null) return LoginHintReason.NEVER
    if (currentKey != null && currentKey != last.networkKey) return LoginHintReason.NETWORK_CHANGED
    val age = nowMs - last.atMs
    if (age < 0 || age > maxAgeMs) return LoginHintReason.STALE
    return null
}

internal fun describeAge(ms: Long): TextRef {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> TextRef(R.string.game_sc_age_now)
        minutes < 60 -> TextRef(R.string.game_sc_age_min, minutes)
        minutes < 48 * 60 -> TextRef(R.string.game_sc_age_hours, minutes / 60)
        else -> TextRef(R.string.game_sc_age_days, minutes / (24 * 60))
    }
}

fun loginHintMessage(reason: LoginHintReason, last: LoginRecord?, nowMs: Long): TextRef {
    val tail = TextRef(R.string.game_sc_hint_tail)
    val head = when (reason) {
        LoginHintReason.NEVER -> TextRef(R.string.game_sc_hint_never)
        LoginHintReason.NETWORK_CHANGED -> TextRef(R.string.game_sc_hint_changed)
        LoginHintReason.STALE -> {
            val age = if (last != null && nowMs >= last.atMs) describeAge(nowMs - last.atMs) else TextRef(R.string.game_sc_age_long)
            TextRef(R.string.game_sc_hint_stale, age)
        }
    }
    return TextRef(R.string.game_sc_hint_full, head, tail)
}

// --- Netz-Schluessel (ohne Standort-Berechtigung, ohne Fremddienst) -------------------------------------------------

/** Netzadresse "a.b.c.d/prefix" einer IPv4-Adresse; null bei ungueltiger Eingabe. */
internal fun subnetOf(address: ByteArray, prefixLength: Int): String? {
    if (address.size != 4 || prefixLength !in 0..32) return null
    val parts = IntArray(4) { i ->
        val bits = (prefixLength - i * 8).coerceIn(0, 8)
        val mask = if (bits == 0) 0 else (0xFF shl (8 - bits)) and 0xFF
        address[i].toInt() and mask
    }
    return parts.joinToString(".") + "/$prefixLength"
}

/** Kurzer Hash aus SSID (falls lesbar), Gateway und Subnetz; die Rohwerte werden nicht gespeichert. */
internal fun wifiToken(ssid: String?, gateway: String?, subnet: String?): String? {
    if (ssid.isNullOrBlank() && gateway.isNullOrBlank() && subnet.isNullOrBlank()) return null
    val digest = MessageDigest.getInstance("SHA-256").digest("${ssid.orEmpty()}|${gateway.orEmpty()}|${subnet.orEmpty()}".toByteArray())
    return digest.take(6).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

internal fun networkKey(kind: NetKind, wifiToken: String?): String? = when (kind) {
    NetKind.NONE -> null
    NetKind.MOBILE -> "mobile"
    NetKind.ETHERNET -> "ethernet"
    NetKind.WIFI -> "wifi:" + (wifiToken ?: "?")
    NetKind.OTHER -> "other"
}

// --- Hosts ------------------------------------------------------------------------------------------------------

data class StartCheckHosts(val login: String, val version: String)

/**
 * Login-Host (1119/3724): `<region>.actual.battle.net`, der Battle.net-Anmeldeserver, mit dem der Client nach dem Start spricht.
 * Version-Host (443): `<region>.version.battle.net`, derselbe, den [WowClientDownloader.versionServiceBase] abfragt (sicher vorhanden).
 */
internal fun startCheckHosts(regionCode: String): StartCheckHosts {
    val code = regionCode.trim().lowercase().takeIf { it.matches(Regex("[a-z]{2}")) } ?: "us"
    return StartCheckHosts("$code.actual.battle.net", "$code.version.battle.net")
}

// --- Login-Erfolg erkennen ---------------------------------------------------------------------------------------

/** Wie [BattleNetSignIn.loginConsumed], aber nur fuer ein Hotfix.log, das nach dem Start dieses Spiellaufs geschrieben wurde. */
internal fun loginSucceeded(hotfixLogBytes: Long?, modifiedMs: Long?, launchMs: Long): Boolean =
    BattleNetSignIn.loginConsumed(hotfixLogBytes) && modifiedMs != null && modifiedMs >= launchMs - MTIME_SLACK_MS

private const val MTIME_SLACK_MS = 2_000L
