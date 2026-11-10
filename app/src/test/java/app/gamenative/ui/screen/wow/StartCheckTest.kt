package app.gamenative.ui.screen.wow

import app.gamenative.R
import app.gamenative.ui.components.TextRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartCheckTest {

    private val wifi = NetworkState(NetKind.WIFI, validated = true, captivePortal = false, vpn = false)
    private val mobile = NetworkState(NetKind.MOBILE, validated = true, captivePortal = false, vpn = false)

    private fun ports(
        login: PortOutcome = PortOutcome.OPEN,
        game: PortOutcome = PortOutcome.OPEN,
        web: PortOutcome = PortOutcome.OPEN,
    ) = listOf(PortProbeResult(PORT_LOGIN, login), PortProbeResult(PORT_GAME, game), PortProbeResult(PORT_WEB, web))

    private fun measurements(
        network: NetworkState = wifi,
        dnsFailed: Boolean = false,
        ports: List<PortProbeResult> = ports(),
        latencies: List<Long> = List(10) { 40L },
        attempts: Int = 10,
    ) = StartCheckMeasurements(network, dnsFailed, ports, latencies, attempts)

    // --- Bewertung -------------------------------------------------------------------------------------------

    @Test
    fun allGood_isGreen() {
        val verdict = evaluateStartCheck(measurements())
        assertEquals(Light.GREEN, verdict.light)
        assertEquals(TextRef(R.string.game_sc_ok), verdict.headline)
        assertEquals(40L, verdict.medianMs)
        assertEquals(0L, verdict.jitterMs)
        assertEquals(0, verdict.failures)
        assertTrue(verdict.tips.isEmpty())
        assertEquals(
            TextRef(R.string.game_sc_stats, 10, TextRef(R.string.game_sc_stats_timing, 40L, 0L), 0, 10),
            verdict.details.single(),
        )
    }

    @Test
    fun wifiBlocksGamePort_isRedWithMobileAdvice() {
        val verdict = evaluateStartCheck(measurements(ports = ports(game = PortOutcome.TIMEOUT)), gamePortDecisive = true)
        assertEquals(Light.RED, verdict.light)
        assertEquals(TextRef(R.string.game_sc_blocked_one, TextRef(R.string.game_sc_net_wifi), 3724, TextRef(R.string.game_sc_alt_wifi)), verdict.headline)
        assertTrue(TextRef(R.string.game_sc_tip_samsung) in verdict.tips)
    }

    @Test
    fun mobileBlocksLoginPort_adviceIsWifi() {
        val verdict = evaluateStartCheck(measurements(network = mobile, ports = ports(login = PortOutcome.TIMEOUT)))
        assertEquals(Light.RED, verdict.light)
        assertEquals(
            TextRef(R.string.game_sc_blocked_one, TextRef(R.string.game_sc_net_mobile), 1119, TextRef(R.string.game_sc_alt_mobile)),
            verdict.headline,
        )
    }

    @Test
    fun twoPortsBlocked_namesBothPorts() {
        val verdict = evaluateStartCheck(measurements(ports = ports(login = PortOutcome.TIMEOUT, game = PortOutcome.TIMEOUT)), gamePortDecisive = true)
        assertEquals(Light.RED, verdict.light)
        assertEquals(
            TextRef(R.string.game_sc_blocked_many, TextRef(R.string.game_sc_net_wifi), "1119, 3724", TextRef(R.string.game_sc_alt_wifi)),
            verdict.headline,
        )
    }

    @Test
    fun everythingTimesOut_isOneGeneralMessage() {
        val all = PortOutcome.TIMEOUT
        val verdict = evaluateStartCheck(measurements(ports = ports(all, all, all), latencies = emptyList(), attempts = 3))
        assertEquals(Light.RED, verdict.light)
        assertEquals(R.string.game_sc_blocked_all, verdict.headline.res)
        assertTrue(verdict.details.none { it.res == R.string.game_sc_all_failed })
    }

    @Test
    fun captivePortal_isRedWithSignInAdvice() {
        val net = NetworkState(NetKind.WIFI, validated = false, captivePortal = true, vpn = false)
        val verdict = evaluateStartCheck(measurements(network = net))
        assertEquals(Light.RED, verdict.light)
        assertEquals(TextRef(R.string.game_sc_captive, TextRef(R.string.game_sc_net_wifi)), verdict.headline)
    }

    @Test
    fun notValidatedButBlizzardReachable_isYellow() {
        val net = NetworkState(NetKind.WIFI, validated = false, captivePortal = false, vpn = false)
        val verdict = evaluateStartCheck(measurements(network = net))
        assertEquals(Light.YELLOW, verdict.light)
    }

    @Test
    fun notValidatedAndBlizzardUnreachable_isRed() {
        val net = NetworkState(NetKind.WIFI, validated = false, captivePortal = false, vpn = false)
        val unreachable = PortOutcome.TIMEOUT
        val verdict = evaluateStartCheck(measurements(network = net, ports = ports(web = unreachable)))
        assertEquals(Light.RED, verdict.light)
    }

    @Test
    fun dnsFailure_isRedAndPortsAreNotBlamed() {
        val none = PortOutcome.UNREACHABLE
        val verdict = evaluateStartCheck(measurements(dnsFailed = true, ports = ports(none, none, none), latencies = emptyList(), attempts = 0))
        assertEquals(Light.RED, verdict.light)
        assertEquals(R.string.game_sc_dns, verdict.headline.res)
        assertTrue(
            verdict.details.none {
                it.res == R.string.game_sc_unrouted || it.res == R.string.game_sc_blocked_one || it.res == R.string.game_sc_blocked_many
            },
        )
    }

    @Test
    fun noRoute_isRed() {
        val verdict = evaluateStartCheck(measurements(ports = ports(web = PortOutcome.UNREACHABLE)))
        assertEquals(Light.RED, verdict.light)
        assertEquals(R.string.game_sc_unrouted, verdict.headline.res)
    }

    @Test
    fun refusedGamePort_isNoBlockProof() {
        val verdict = evaluateStartCheck(measurements(ports = ports(game = PortOutcome.REFUSED)))
        assertEquals(Light.GREEN, verdict.light)
    }

    @Test
    fun refusedWebPort_isRed() {
        val verdict = evaluateStartCheck(measurements(ports = ports(web = PortOutcome.REFUSED)))
        assertEquals(Light.RED, verdict.light)
    }

    @Test
    fun gamePortNotDecisive_ignoresItsTimeout() {
        val m = measurements(ports = ports(game = PortOutcome.TIMEOUT))
        assertEquals(Light.GREEN, evaluateStartCheck(m, gamePortDecisive = false).light)
        assertEquals(Light.RED, evaluateStartCheck(m, gamePortDecisive = true).light)
    }

    @Test
    fun strongJitter_isRed() {
        val latencies = List(5) { 20L } + List(5) { 700L }
        val verdict = evaluateStartCheck(measurements(latencies = latencies))
        assertEquals(360L, verdict.medianMs)
        assertEquals(340L, verdict.jitterMs)
        assertEquals(Light.RED, verdict.light)
        assertEquals(TextRef(R.string.game_sc_jitter_red, 340L, 360L), verdict.headline)
    }

    @Test
    fun moderateJitter_isYellow() {
        val latencies = List(5) { 50L } + List(5) { 500L }
        val verdict = evaluateStartCheck(measurements(latencies = latencies))
        assertEquals(275L, verdict.medianMs)
        assertEquals(225L, verdict.jitterMs)
        assertEquals(Light.YELLOW, verdict.light)
        assertEquals(TextRef(R.string.game_sc_jitter_yellow, 225L, 275L), verdict.headline)
    }

    @Test
    fun fewFailures_yellow_manyFailures_red() {
        val few = evaluateStartCheck(measurements(latencies = List(8) { 40L }))
        assertEquals(Light.YELLOW, few.light)
        assertEquals(2, few.failures)
        assertEquals(TextRef(R.string.game_sc_few_drops, 2, 10), few.headline)

        val many = evaluateStartCheck(measurements(latencies = List(6) { 40L }))
        assertEquals(Light.RED, many.light)
        assertEquals(TextRef(R.string.game_sc_drops, 4, 10), many.headline)
    }

    @Test
    fun slowButSteady_isYellow_veryslow_red() {
        val slow = evaluateStartCheck(measurements(latencies = List(10) { 350L }))
        assertEquals(Light.YELLOW, slow.light)
        assertEquals(TextRef(R.string.game_sc_slow_yellow, 350L), slow.headline)

        val verySlow = evaluateStartCheck(measurements(latencies = List(10) { 900L }))
        assertEquals(Light.RED, verySlow.light)
    }

    @Test
    fun vpnAlone_isYellow() {
        val verdict = evaluateStartCheck(measurements(network = wifi.copy(vpn = true)))
        assertEquals(Light.YELLOW, verdict.light)
        assertEquals(R.string.game_sc_vpn, verdict.headline.res)
    }

    @Test
    fun noNetwork_isRed() {
        val none = NetworkState(NetKind.NONE, validated = false, captivePortal = false, vpn = false)
        val verdict = evaluateStartCheck(StartCheckMeasurements(none, false, emptyList(), emptyList(), 0))
        assertEquals(Light.RED, verdict.light)
        assertEquals(R.string.game_sc_no_net, verdict.headline.res)
    }

    @Test
    fun allLatenciesFailedWithoutPortBlame_isRed() {
        val verdict = evaluateStartCheck(measurements(latencies = emptyList(), attempts = 4))
        assertEquals(Light.RED, verdict.light)
        assertEquals(TextRef(R.string.game_sc_all_failed, 4), verdict.headline)
    }

    // --- Statistik -------------------------------------------------------------------------------------------

    @Test
    fun medianAndJitter() {
        assertNull(medianOf(emptyList()))
        assertEquals(2L, medianOf(listOf(1L, 3L, 2L)))
        assertEquals(2L, medianOf(listOf(1L, 2L, 3L, 4L)))
        assertNull(jitterOf(emptyList()))
        assertEquals(0L, jitterOf(listOf(55L)))
        assertEquals(6L, jitterOf(listOf(10L, 20L, 30L)))
    }

    @Test
    fun classifyConnectFailure_byMessage() {
        val refused = "failed to connect to x/1.2.3.4 (port 3724) from /10.0.0.2 (port 5) after 2500ms: isConnected failed: ECONNREFUSED (Connection refused)"
        val timeout = "failed to connect to x/1.2.3.4 (port 3724) after 2500ms: isConnected failed: ETIMEDOUT (Connection timed out)"
        val noRoute = "connect failed: ENETUNREACH (Network is unreachable)"
        assertEquals(PortOutcome.REFUSED, classifyConnectFailure(refused))
        assertEquals(PortOutcome.TIMEOUT, classifyConnectFailure(timeout))
        assertEquals(PortOutcome.UNREACHABLE, classifyConnectFailure(noRoute))
        assertEquals(PortOutcome.UNREACHABLE, classifyConnectFailure(null))
    }

    // --- Login-Hinweis ---------------------------------------------------------------------------------------

    private val hour = 60L * 60 * 1000
    private val now = 100 * hour

    @Test
    fun neverLoggedIn_hintsNever() {
        assertEquals(LoginHintReason.NEVER, loginHintReason(null, "mobile", now))
    }

    @Test
    fun recentLoginInSameNetwork_noHint() {
        assertNull(loginHintReason(LoginRecord(now - hour, "wifi:abc"), "wifi:abc", now))
        // Genau 12 Stunden zaehlt noch als frisch.
        assertNull(loginHintReason(LoginRecord(now - 12 * hour, "mobile"), "mobile", now))
    }

    @Test
    fun otherNetwork_hintsNetworkChanged() {
        assertEquals(LoginHintReason.NETWORK_CHANGED, loginHintReason(LoginRecord(now - hour, "wifi:abc"), "mobile", now))
    }

    @Test
    fun oldLogin_hintsStale() {
        assertEquals(LoginHintReason.STALE, loginHintReason(LoginRecord(now - 13 * hour, "mobile"), "mobile", now))
    }

    @Test
    fun networkChangeWinsOverStale() {
        assertEquals(LoginHintReason.NETWORK_CHANGED, loginHintReason(LoginRecord(now - 40 * hour, "wifi:abc"), "mobile", now))
    }

    @Test
    fun unknownCurrentNetwork_onlyAgeCounts() {
        assertNull(loginHintReason(LoginRecord(now - hour, "mobile"), null, now))
        assertEquals(LoginHintReason.STALE, loginHintReason(LoginRecord(now - 20 * hour, "mobile"), null, now))
    }

    @Test
    fun clockGoingBackwards_countsAsStale() {
        assertEquals(LoginHintReason.STALE, loginHintReason(LoginRecord(now + hour, "mobile"), "mobile", now))
    }

    @Test
    fun hintMessages_nameTheReasonAndTheBlizzardCodes() {
        val tail = TextRef(R.string.game_sc_hint_tail)
        val never = loginHintMessage(LoginHintReason.NEVER, null, now)
        assertEquals(TextRef(R.string.game_sc_hint_full, TextRef(R.string.game_sc_hint_never), tail), never)
        val changed = loginHintMessage(LoginHintReason.NETWORK_CHANGED, LoginRecord(now - hour, "mobile"), now)
        assertEquals(TextRef(R.string.game_sc_hint_full, TextRef(R.string.game_sc_hint_changed), tail), changed)
        val stale = loginHintMessage(LoginHintReason.STALE, LoginRecord(now - 13 * hour, "mobile"), now)
        assertEquals(
            TextRef(R.string.game_sc_hint_full, TextRef(R.string.game_sc_hint_stale, TextRef(R.string.game_sc_age_hours, 13L)), tail),
            stale,
        )
    }

    @Test
    fun describeAge_units() {
        assertEquals(TextRef(R.string.game_sc_age_now), describeAge(30_000L))
        assertEquals(TextRef(R.string.game_sc_age_min, 5L), describeAge(5 * 60_000L))
        assertEquals(TextRef(R.string.game_sc_age_hours, 13L), describeAge(13 * hour))
        assertEquals(TextRef(R.string.game_sc_age_days, 3L), describeAge(72 * hour))
    }

    // --- Netz-Schluessel -------------------------------------------------------------------------------------

    private fun ip(a: Int, b: Int, c: Int, d: Int) = byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())

    @Test
    fun subnetOf_masksHostBits() {
        assertEquals("192.168.178.0/24", subnetOf(ip(192, 168, 178, 23), 24))
        assertEquals("10.1.192.0/20", subnetOf(ip(10, 1, 200, 5), 20))
        assertEquals("0.0.0.0/0", subnetOf(ip(10, 1, 200, 5), 0))
        assertNull(subnetOf(ByteArray(16), 64))
        assertNull(subnetOf(ip(1, 2, 3, 4), 33))
    }

    @Test
    fun wifiToken_isStableShortAndDistinguishes() {
        assertNull(wifiToken(null, null, null))
        val token = wifiToken("Home", "192.168.1.1", "192.168.1.0/24")
        assertNotNull(token)
        assertEquals(12, token!!.length)
        assertEquals(token, wifiToken("Home", "192.168.1.1", "192.168.1.0/24"))
        assertNotEquals(token, wifiToken("Home", "192.168.0.1", "192.168.1.0/24"))
        assertEquals(wifiToken(null, "g", "s"), wifiToken("", "g", "s"))
        assertFalse(token.contains("Home"))
    }

    @Test
    fun networkKey_perKind() {
        assertNull(networkKey(NetKind.NONE, null))
        assertEquals("mobile", networkKey(NetKind.MOBILE, "ignored"))
        assertEquals("ethernet", networkKey(NetKind.ETHERNET, null))
        assertEquals("wifi:abc", networkKey(NetKind.WIFI, "abc"))
        assertEquals("wifi:?", networkKey(NetKind.WIFI, null))
        assertEquals("other", networkKey(NetKind.OTHER, null))
    }

    // --- Hosts, Login-Erfolg ------------------------------------------------------------------------------------

    @Test
    fun hosts_followTheRegion() {
        assertEquals(StartCheckHosts("eu.actual.battle.net", "eu.version.battle.net"), startCheckHosts("EU"))
        assertEquals("kr.actual.battle.net", startCheckHosts(" kr ").login)
        assertEquals("us.version.battle.net", startCheckHosts("xyz").version)
        assertEquals("tw.actual.battle.net", startCheckHosts(GameRegion.TW.name).login)
    }

    @Test
    fun loginSucceeded_needsBigFreshLog() {
        val launch = 1_000_000L
        assertTrue(loginSucceeded(60_000L, launch + 1_000, launch))
        assertFalse(loginSucceeded(50_000L, launch + 1_000, launch))
        assertFalse(loginSucceeded(null, null, launch))
        assertFalse(loginSucceeded(60_000L, null, launch))
        // Ein altes Log vom vorigen Lauf zaehlt nicht.
        assertFalse(loginSucceeded(60_000L, launch - 60_000, launch))
    }
}
