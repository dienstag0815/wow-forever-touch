package app.gamenative.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.system.Os
import app.gamenative.ui.screen.wow.GamePath
import timber.log.Timber
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

/**
 * Android side of the system view ([SystemView]): reads what the OS shows this process, as near as possible to what Wine sees.
 *
 * Wine runs in the process tree of this app (same user id, same SELinux domain), so reading `/sys/class/net/<if>/address` here
 * gives the answer Wine would get for that file; `ioctl` and netlink paths that Wine may use instead cannot be tried from Kotlin.
 * Every part is read on its own and defensively: a failure leaves that part null (shown as unreadable), never an exception.
 * [captureBounded] runs the whole capture on a helper thread and gives up after [TIMEOUT_MS], so a start is never delayed.
 */
object SystemViewCapture {
    const val TIMEOUT_MS = 1500L

    private const val NET_DIR = "/sys/class/net"
    private const val CPU_DIR = "/sys/devices/system/cpu"

    /** The capture within [TIMEOUT_MS]; null if it did not finish in time or failed as a whole. */
    @JvmStatic
    fun captureBounded(context: Context, shimActive: Boolean): SystemView.Snapshot? {
        val app = context.applicationContext ?: context
        var result: SystemView.Snapshot? = null
        val worker = Thread({ result = runCatching { capture(app, shimActive) }.getOrNull() }, "system-view")
        worker.isDaemon = true
        return try {
            worker.start()
            worker.join(TIMEOUT_MS)
            if (worker.isAlive) {
                Timber.w("System view capture timed out")
                null
            } else {
                result
            }
        } catch (e: Exception) {
            null
        }
    }

    fun capture(context: Context, shimActive: Boolean): SystemView.Snapshot {
        val cpuPresent = guard { SystemView.countCpuList(readText(File(CPU_DIR, "present"))) }
        val cpuOnline = guard { SystemView.countCpuList(readText(File(CPU_DIR, "online"))) ?: Runtime.getRuntime().availableProcessors() }
        return SystemView.Snapshot(
            systemHostname = guard { Os.uname().nodename ?: "" },
            shimActive = shimActive,
            interfaces = guard { interfaces() },
            network = guard { network(context) },
            // Present falls back to online when the file is not readable (both then show the same number, which is still a change signal).
            cpuPresent = cpuPresent ?: cpuOnline,
            cpuOnline = cpuOnline,
            gameFolderKind = guard { gameFolderKind(context) },
            userId = guard { Process.myUid() / 100_000 },
            freeBytes = guard { StatFs(context.filesDir.path).availableBytes },
            androidSdk = guard { Build.VERSION.SDK_INT },
        )
    }

    private inline fun <T> guard(block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        null
    } catch (e: LinkageError) {
        null
    }

    private fun readText(file: File): String? = try {
        if (file.isFile) file.readText().trim() else null
    } catch (e: Exception) {
        null
    }

    /** Names from the Java API and from `/sys/class/net` (the latter is what a plain file read in Wine's domain sees). */
    private fun interfaces(): List<SystemView.Iface> {
        val byName = LinkedHashMap<String, SystemView.Iface>()
        val java = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        for (ni in java) {
            val up = try { ni.isUp } catch (e: Exception) { false }
            val mac = try {
                ni.hardwareAddress?.joinToString(":") { "%02x".format(it.toInt() and 0xff) }
            } catch (e: Exception) {
                null
            }
            byName[ni.name] = SystemView.Iface(ni.name, up, mac)
        }
        val dir = File(NET_DIR)
        for (name in dir.list().orEmpty()) {
            val file = readText(File(File(dir, name), "address"))
            val known = byName[name]
            val mac = file?.takeIf { SystemView.parseMac(it) != null } ?: known?.mac
            val up = known?.up ?: (readText(File(File(dir, name), "operstate")).let { it == "up" || it == "unknown" })
            byName[name] = SystemView.Iface(name, up, mac)
        }
        return byName.values.toList()
    }

    private fun network(context: Context): SystemView.Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val active = cm.activeNetwork ?: return SystemView.Network(emptySet(), ipv4 = false, ipv6 = false, privateDns = "off")
        val caps = cm.getNetworkCapabilities(active)
        val transports = LinkedHashSet<String>()
        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports += "wlan"
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports += "mobil"
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports += "vpn"
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports += "ethernet"
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transports += "bluetooth"
            if (transports.isEmpty()) transports += "andere"
        }
        val link = cm.getLinkProperties(active)
        val addresses = link?.linkAddresses.orEmpty().map { it.address }
        // Only the mode of the private DNS, never the provider's host name.
        val dns = when {
            link == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !link.isPrivateDnsActive -> "off"
            link.privateDnsServerName != null -> "provider"
            else -> "automatic"
        }
        return SystemView.Network(
            transports,
            ipv4 = addresses.any { it is Inet4Address && !it.isLoopbackAddress },
            ipv6 = addresses.any { it is Inet6Address && !it.isLoopbackAddress && !it.isLinkLocalAddress },
            privateDns = dns,
        )
    }

    private fun gameFolderKind(context: Context): String {
        val path = File(GamePath.load(context)).absolutePath
        val internal = Environment.getExternalStorageDirectory().absolutePath
        val sd = context.getExternalFilesDirs(null).drop(1).filterNotNull().map { it.path.substringBefore("/Android/") }
        return SystemView.storageKind(path, internal, sd)
    }
}
