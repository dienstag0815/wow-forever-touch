package app.gamenative.ui.screen.wow

import android.content.Context
import app.gamenative.ui.components.TextRef
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class LoginHint(val reason: LoginHintReason, val text: TextRef)

data class StartCheckReport(
    val measurements: StartCheckMeasurements,
    val verdict: StartCheckVerdict,
    val networkKey: String?,
    /** Null, wenn kein Hinweis noetig ist. */
    val loginHint: LoginHint?,
)

/** Startcheck vor dem Spielstart: Einstellung, Netzmessung, Login-Gedaechtnis. Siehe [StartCheckLogic.kt] fuer die Bewertung. */
object StartCheck {
    private const val PREFS = "wow_forever_startcheck"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LOGIN_AT = "login_at"
    private const val KEY_LOGIN_NET = "login_net"

    private const val DNS_TIMEOUT_MS = 1_500L
    private const val PORT_TIMEOUT_MS = 2_500
    private const val SERIES_COUNT = 10
    private const val SERIES_ATTEMPT_TIMEOUT_MS = 1_000
    private const val SERIES_BUDGET_MS = 3_500L
    private const val WATCH_TIMEOUT_MS = 600_000L
    private const val WATCH_POLL_MS = 2_000L
    private const val HOTFIX_LOG = "Logs/Hotfix.log"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun lastLogin(context: Context): LoginRecord? {
        val p = prefs(context)
        val at = p.getLong(KEY_LOGIN_AT, 0L)
        val net = p.getString(KEY_LOGIN_NET, null)
        return if (at > 0L && net != null) LoginRecord(at, net) else null
    }

    fun recordLoginSuccess(context: Context, networkKey: String, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LOGIN_AT, nowMs).putString(KEY_LOGIN_NET, networkKey).apply()
    }

    // --- Netz-Momentaufnahme (ohne Verbindungstest) ---------------------------------------------------------------

    data class NetSnapshot(val state: NetworkState, val key: String?)

    private val NO_NETWORK = NetSnapshot(NetworkState(NetKind.NONE, validated = false, captivePortal = false, vpn = false), null)

    fun snapshot(context: Context): NetSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NO_NETWORK
        return try {
            val network = cm.activeNetwork ?: return NO_NETWORK
            val caps = cm.getNetworkCapabilities(network) ?: return NO_NETWORK
            val kind = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.MOBILE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetKind.ETHERNET
                else -> NetKind.OTHER
            }
            val state = NetworkState(
                kind = kind,
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
                vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            )
            val token = if (kind == NetKind.WIFI) wifiTokenOf(cm, network, caps) else null
            NetSnapshot(state, networkKey(kind, token))
        } catch (e: Exception) {
            // z. B. SecurityException auf manchen ROMs; ohne Netzdaten lieber "kein Hinweis" als ein falscher Alarm.
            Timber.w(e, "Startcheck: Netzstatus nicht lesbar")
            NO_NETWORK
        }
    }

    private fun wifiTokenOf(cm: ConnectivityManager, network: android.net.Network, caps: NetworkCapabilities): String? {
        // Die SSID ist ohne Standort-Berechtigung geschwaerzt ("<unknown ssid>"); dann zaehlen Gateway und Subnetz.
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (caps.transportInfo as? WifiInfo)?.ssid?.trim('"')?.takeIf { it.isNotBlank() && !it.startsWith("<unknown") }
        } else {
            null
        }
        val link = cm.getLinkProperties(network)
        val gateway = link?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        val subnet = link?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.let { subnetOf(it.address.address, it.prefixLength) }
        return wifiToken(ssid, gateway, subnet)
    }

    // --- Messung (blockierend, in Dispatchers.IO aufrufen; dauert hoechstens ca. 5 s) -----------------------------------

    fun run(context: Context, region: GameRegion): StartCheckReport {
        val snap = snapshot(context)
        val measurements = measure(snap.state, startCheckHosts(region.name))
        val verdict = evaluateStartCheck(measurements)
        val now = System.currentTimeMillis()
        val reason = loginHintReason(lastLogin(context), snap.key, now)
        val hint = reason?.let { LoginHint(it, loginHintMessage(it, lastLogin(context), now)) }
        Timber.i(
            "Startcheck: %s %s, %s, Hinweis=%s, Ports=%s",
            snap.key, verdict.light, verdict.headline.res, reason, measurements.ports.joinToString { "${it.port}:${it.outcome}" },
        )
        return StartCheckReport(measurements, verdict, snap.key, hint)
    }

    private fun measure(network: NetworkState, hosts: StartCheckHosts): StartCheckMeasurements {
        if (network.kind == NetKind.NONE) {
            return StartCheckMeasurements(network, dnsFailed = false, ports = emptyList(), latenciesMs = emptyList(), attempts = 0)
        }
        val pool = Executors.newCachedThreadPool { r -> Thread(r, "startcheck").apply { isDaemon = true } }
        try {
            val loginAddr = pool.submit(Callable { resolve(hosts.login) })
            val versionAddr = pool.submit(Callable { resolve(hosts.version) })
            val dnsDeadline = System.currentTimeMillis() + DNS_TIMEOUT_MS
            val login = awaitOrNull(loginAddr, dnsDeadline - System.currentTimeMillis())
            val version = awaitOrNull(versionAddr, dnsDeadline - System.currentTimeMillis())

            val portJobs = listOf(
                PORT_LOGIN to login,
                PORT_GAME to login,
                PORT_WEB to version,
            ).map { (port, addr) ->
                port to pool.submit(Callable { if (addr == null) PortOutcome.UNREACHABLE else connect(addr, port, PORT_TIMEOUT_MS).first })
            }
            val series = if (version == null) null else pool.submit(Callable { latencySeries(version) })

            val ports = portJobs.map { (port, job) -> PortProbeResult(port, job.get()) }
            val (latencies, attempts) = series?.get() ?: (emptyList<Long>() to 0)
            return StartCheckMeasurements(network, dnsFailed = login == null || version == null, ports = ports, latenciesMs = latencies, attempts = attempts)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun <T> awaitOrNull(job: Future<T?>, timeoutMs: Long): T? = try {
        job.get(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
    } catch (e: Exception) {
        job.cancel(true)
        null
    }

    private fun resolve(host: String): InetAddress? = try {
        val all = InetAddress.getAllByName(host)
        all.firstOrNull { it is Inet4Address } ?: all.firstOrNull()
    } catch (e: IOException) {
        null
    }

    /** Zehn TCP-Verbindungen nacheinander zum Web-Port; bricht nach dem Zeitbudget ab. Liefert (Latenzen der Erfolge, Versuche). */
    private fun latencySeries(addr: InetAddress): Pair<List<Long>, Int> {
        val start = System.nanoTime()
        val latencies = mutableListOf<Long>()
        var attempts = 0
        while (attempts < SERIES_COUNT) {
            val remainingMs = SERIES_BUDGET_MS - (System.nanoTime() - start) / 1_000_000
            if (remainingMs < 150) break
            attempts++
            val (outcome, ms) = connect(addr, PORT_WEB, minOf(SERIES_ATTEMPT_TIMEOUT_MS.toLong(), remainingMs).toInt())
            if (outcome == PortOutcome.OPEN) latencies += ms
        }
        return latencies to attempts
    }

    private fun connect(addr: InetAddress, port: Int, timeoutMs: Int): Pair<PortOutcome, Long> {
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(addr, port), timeoutMs)
            PortOutcome.OPEN to elapsed()
        } catch (e: SocketTimeoutException) {
            PortOutcome.TIMEOUT to elapsed()
        } catch (e: IOException) {
            classifyConnectFailure(e.message) to elapsed()
        } finally {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    // --- Login-Erfolg merken ------------------------------------------------------------------------------------

    @Volatile
    private var watchJob: Job? = null

    /**
     * Beim Spielstart aufrufen. Beobachtet Hotfix.log (> 50 kB, nach dem Start geschrieben = Login-Bildschirm
     * passiert) und merkt dann Zeit und Netz des erfolgreichen Logins. Unabhaengig vom gespeicherten Login.
     */
    fun onLaunch(context: Context, gameRoot: File) {
        val appContext = context.applicationContext
        val launchMs = System.currentTimeMillis()
        val key = snapshot(appContext).key ?: return
        val log = File(File(gameRoot, WowClientDownloader.FLAVOR_DIR), HOTFIX_LOG)
        watchJob?.cancel()
        watchJob = CoroutineScope(Dispatchers.IO).launch {
            val deadline = System.currentTimeMillis() + WATCH_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                delay(WATCH_POLL_MS)
                val exists = log.exists()
                val bytes = if (exists) log.length() else null
                val modified = if (exists) log.lastModified() else null
                if (loginSucceeded(bytes, modified, launchMs)) {
                    recordLoginSuccess(appContext, key)
                    Timber.i("Startcheck: Login in Netz %s gemerkt", key)
                    break
                }
            }
        }
    }
}
