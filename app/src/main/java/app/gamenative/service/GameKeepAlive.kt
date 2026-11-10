package app.gamenative.service

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Keeps Android from throttling Wi-Fi and the process while the game runs: a low-latency Wi-Fi lock, a partial wake
 * lock and a foreground service (see [GameKeepAliveService]). Without them the connection drops after a long session
 * ("Disconnected from the server", WOW51900319).
 *
 * [start] and [stop] are idempotent. [stop] is called from every end path (guest exit, screen disposal, activity
 * destruction, environment shutdown, task removal); a process kill drops the locks by itself.
 */
object GameKeepAlive {
    private const val TAG = "GameKeepAlive"
    private const val LOCK_NAME = "wowforever"

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @get:Synchronized
    val isActive: Boolean get() = wifiLock?.isHeld == true || wakeLock?.isHeld == true

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        acquireLocks(app)
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, GameKeepAliveService::class.java))
        }.onFailure { Timber.tag(TAG).w(it, "foreground service not started") }
    }

    @Synchronized
    fun stop(context: Context) {
        releaseLocks()
        runCatching { context.applicationContext.stopService(Intent(context.applicationContext, GameKeepAliveService::class.java)) }
            .onFailure { Timber.tag(TAG).w(it, "stopService failed") }
    }

    @Synchronized
    internal fun releaseLocks() {
        wifiLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() }.onFailure { Timber.tag(TAG).w(it, "wifi lock release failed") } }
        wakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() }.onFailure { Timber.tag(TAG).w(it, "wake lock release failed") } }
        wifiLock = null
        wakeLock = null
        Timber.tag(TAG).i("locks released")
    }

    private fun acquireLocks(app: Context) {
        if (wifiLock?.isHeld != true) {
            wifiLock = runCatching {
                app.getSystemService(WifiManager::class.java)
                    .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, LOCK_NAME)
                    .apply { setReferenceCounted(false); acquire() }
            }.onFailure { Timber.tag(TAG).w(it, "wifi lock not acquired") }.getOrNull()
        }
        if (wakeLock?.isHeld != true) {
            wakeLock = runCatching {
                app.getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$LOCK_NAME:game")
                    .apply { setReferenceCounted(false); acquire() } // no timeout: released in stop()/service end
            }.onFailure { Timber.tag(TAG).w(it, "wake lock not acquired") }.getOrNull()
        }
        Timber.tag(TAG).i("locks held: wifi=%s wake=%s", wifiLock?.isHeld, wakeLock?.isHeld)
    }
}
