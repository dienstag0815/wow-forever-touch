package app.gamenative.report

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.gamenative.BuildConfig
import app.gamenative.ui.screen.wow.FillScope
import app.gamenative.ui.screen.wow.LoginLevel
import app.gamenative.utils.MachineIdentityApply
import timber.log.Timber
import java.io.File
import java.net.Inet6Address
import java.util.concurrent.Executors

/**
 * Schreibt das Anmeldejournal ([LoginJournal]) mit, je Spielstart ein Eintrag. Die Aufrufe aus dem Spiel sind je eine Zeile und
 * kosten den Aufrufer nichts: die Arbeit läuft auf einem eigenen Thread, Fehler werden verschluckt (das Journal darf nie ein Spiel
 * stören). Ohne vorherigen [gameStarted] im laufenden Prozess tun die anderen Aufrufe nichts.
 *
 * Aufgezeichnet wird nie E-Mail, Passwort, deren Länge, eine Adresse oder ein WLAN-Name.
 */
internal object LoginJournalRecorder {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "anmeldejournal").apply { isDaemon = true } }

    // Nur auf dem Arbeitsthread benutzt.
    private var file: File? = null
    private var entries: List<JournalEntry> = emptyList()
    private var gameRoot: File? = null

    /** Netz-Einordnung ohne Android-Klassen (JUnit): Wi-Fi vor Mobilfunk vor Rest; ohne Netz `none`. */
    internal fun networkKind(hasNetwork: Boolean, wifi: Boolean, cellular: Boolean): String = when {
        !hasNetwork -> "none"
        wifi -> "wifi"
        cellular -> "cellular"
        else -> "other"
    }

    /**
     * Ein Spielstart: neuer Eintrag. [loginFileWritten] sagt, ob die Stufe "Selbst anmelden" eine login.txt hingelegt hat. [gameRoot] ist der
     * Spielordner: vor dem neuen Eintrag werden die Anmeldeversuche des vorigen Laufs aus der `Connection.log` ins Journal übernommen
     * (das Spiel hat sie noch nicht neu geschrieben, es startet erst nach diesem Aufruf).
     */
    fun gameStarted(context: Context, level: LoginLevel, scope: FillScope, loginFileWritten: Boolean, worldSignal: Boolean, gameRoot: File) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        post {
            val target = File(app.filesDir, LoginJournal.FILE_NAME)
            file = target
            this.gameRoot = gameRoot
            val loaded = LoginJournal.load(target)
            val logText = runCatching { ConnectionLogFile.latestText(gameRoot) }.getOrNull()
            val previous = if (loaded.isEmpty()) loaded else loaded.dropLast(1) + LoginHistory.withCapture(loaded.last(), logText)
            val net = snapshotNetwork(app)
            val entry = JournalEntry(
                startMs = now,
                appVersion = BuildConfig.VERSION_NAME,
                level = level.key,
                scope = scope.key,
                loginFileWritten = loginFileWritten,
                worldSignal = worldSignal,
                network = net.kind,
                vpn = net.vpn,
                ipv6 = net.ipv6,
                idFingerprint = runCatching { MachineIdentityApply.logSummary(app).fingerprint }.getOrNull(),
                systemFingerprint = runCatching { MachineIdentityApply.systemSummary(app).total }.getOrNull(),
                sinceLastEndSec = LoginJournal.sinceLastEnd(previous.lastOrNull(), now),
            )
            entries = LoginJournal.append(previous, entry)
            LoginJournal.save(target, entries)
        }
    }

    /** Die login.txt wurde gelöscht (Spiel hat sie gelesen, Frist, Wechsel der Stufe). Nur der erste Zeitpunkt je Start zählt. */
    fun loginFileRemoved() = update { it.copy(loginFileRemovedMs = it.loginFileRemovedMs ?: System.currentTimeMillis()) }

    /** Der Knopf oder die Zeile "Anmeldedaten ausfüllen" wurde getippt ([tapMs]) und das Tippen ist [done] oder abgebrochen. */
    fun fill(tapMs: Long, done: Boolean) = update { it.copy(fillTapMs = tapMs, fillResult = if (done) "done" else "cancelled") }

    /** Das Spiel steht in der Welt (erste Erkennung je Start). */
    fun worldReached() = update { it.copy(worldMs = it.worldMs ?: System.currentTimeMillis()) }

    /** Das Spiel endet ([reason] aus `XServerScreen.exit`, siehe [LoginJournal.endKind]). Nur das erste Ende je Start zählt. */
    fun gameEnded(reason: String) {
        update { it.copy(endKind = it.endKind ?: LoginJournal.endKind(reason), endMs = it.endMs ?: System.currentTimeMillis()) }
        // Die Anmeldeversuche dieses Laufs jetzt übernehmen; was dann noch fehlt (hart beendet), holt der nächste Start nach.
        update { entry -> LoginHistory.withCapture(entry, gameRoot?.let { runCatching { ConnectionLogFile.latestText(it) }.getOrNull() }) }
    }

    /** Alle Einträge für den Bericht (ältester zuerst). Liest die Datei, nicht den Arbeitsspeicher. */
    fun load(context: Context): List<JournalEntry> = LoginJournal.load(File(context.applicationContext.filesDir, LoginJournal.FILE_NAME))

    private fun update(change: (JournalEntry) -> JournalEntry) {
        post {
            val target = file ?: return@post
            // Nur der jüngste Eintrag ändert sich, und nur, solange er zu diesem Lauf der App gehört (nicht aus einer früheren Sitzung).
            val last = entries.lastOrNull() ?: return@post
            val changed = change(last)
            if (changed == last) return@post
            entries = entries.dropLast(1) + changed
            LoginJournal.save(target, entries)
        }
    }

    private fun post(block: () -> Unit) {
        try {
            worker.execute { runCatching(block).onFailure { Timber.w("Anmeldejournal: ${it.javaClass.simpleName}") } }
        } catch (_: Exception) {
            // Executor beendet: nichts zu tun.
        }
    }

    private class NetSnapshot(val kind: String, val vpn: Boolean?, val ipv6: Boolean?)

    /** Art des Netzes, VPN und IPv6 des aktiven Netzes. Keine Adressen, kein WLAN-Name (SSID wird nie gelesen). */
    private fun snapshotNetwork(context: Context): NetSnapshot = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        if (network == null || caps == null) return@runCatching NetSnapshot("none", null, null)
        val ipv6 = cm.getLinkProperties(network)?.linkAddresses.orEmpty().any {
            val a = it.address
            a is Inet6Address && !a.isLinkLocalAddress && !a.isLoopbackAddress && !a.isSiteLocalAddress
        }
        NetSnapshot(
            networkKind(true, caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI), caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)),
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            ipv6,
        )
    }.getOrElse { NetSnapshot("none", null, null) }
}
