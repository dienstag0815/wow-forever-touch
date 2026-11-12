package app.gamenative.utils

import android.content.Context
import android.os.Build
import app.gamenative.BuildConfig
import app.gamenative.ui.screen.wow.GamePath
import com.winlator.container.Container
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Writes the [MachineIdentity] into the WoW container's own (upper) Wine prefix.
 *
 * Wine facts this relies on (wine-10.0 sources):
 * - wineboot (create_computer_name_keys) calls gethostname() at every wineboot run and stores the
 *   result via SetComputerNameEx as Tcpip\Parameters\Hostname and ControlSet001\...\ComputerName
 *   (upper case, 15 characters), then copies it into the volatile ActiveComputerName key.
 *   GetComputerNameEx(NetBIOS) reads ActiveComputerName, GetComputerNameEx(DnsHostname) reads
 *   Hostname, and winsock gethostname() calls the libc gethostname() (our shim). The registry
 *   values written here and the shim hostname ([MachineIdentity.ENV_HOSTNAME]) are therefore
 *   the same string and all roads lead to the same name.
 * - The registry file stores the machine hive under ControlSet001 (CurrentControlSet is only a
 *   symbolic link key), so the values are written there.
 * - mountmgr.sys (get_filesystem_serial) reads drive_c/.windows-serial as hexadecimal text for the
 *   volume serial of C:.
 */
object MachineIdentityApply {
    private const val CRYPTOGRAPHY = "Software\\Microsoft\\Cryptography"
    private const val WINDOWS_CV = "Software\\Microsoft\\Windows\\CurrentVersion"
    private const val NT_CV = "Software\\Microsoft\\Windows NT\\CurrentVersion"
    private const val WOW_WINDOWS_CV = "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion"
    private const val WOW_NT_CV = "Software\\Wow6432Node\\Microsoft\\Windows NT\\CurrentVersion"
    private const val COMPUTER_NAME_KEY = "System\\ControlSet001\\Control\\ComputerName\\ComputerName"
    private const val TCPIP_KEY = "System\\ControlSet001\\Services\\Tcpip\\Parameters"

    const val SERIAL_FILE = "drive_c/.windows-serial"

    @JvmStatic
    fun identityFile(context: Context): File = File(context.filesDir, MachineIdentity.FILE_NAME)

    @Volatile
    private var store: MachineIdentityStore? = null

    /** The one store of the process: it keeps an identity that could not be saved, so every start needs the same instance. */
    @JvmStatic
    fun store(context: Context): MachineIdentityStore {
        store?.let { return it }
        val app = context.applicationContext ?: context
        return synchronized(this) {
            store ?: MachineIdentityStore(
                identityFile = identityFile(app),
                logFile = File(app.filesDir, MachineIdentityLog.LOG_FILE_NAME),
                historyFile = { File(GamePath.load(app), MachineIdentityLog.HISTORY_FILE_NAME) },
                versionCode = BuildConfig.VERSION_CODE,
                // Second copy in another folder of the app storage (not in filesDir, not in a backup, survives an app update).
                copyFile = File(app.noBackupFilesDir, MachineIdentity.FILE_NAME),
                // Backup copy in the game folder (survives a reinstall); the folder is read and written like the history file, never created.
                keepFile = { File(GamePath.load(app), MachineIdentityKeep.FILE_NAME) },
                deviceTrait = MachineIdentityKeep.deviceTrait(Build.MANUFACTURER, Build.MODEL),
            ).also { store = it }
        }
    }

    /**
     * The identity for a game start; creates one when there is none. Only the game start may call this ([applyToContainer], [shimHostname]):
     * a new identity must not appear before the game folder is readable, because a copy there would have to be taken over first.
     * Display paths use [MachineIdentityStore.peek] and the summaries, which never create one.
     */
    @JvmStatic
    fun identity(context: Context): MachineIdentity = store(context).loadOrCreate()

    /** Identity log summary for the diagnostics page and the error report; never creates an identity. */
    @JvmStatic
    fun logSummary(context: Context): MachineIdentityLog.Summary = store(context).summary()

    /** Hostname for the shim, only for the WoW container; null otherwise (shim keeps old behaviour). */
    @JvmStatic
    fun shimHostname(context: Context, container: Container?): String? {
        if (container == null || container.id != MachineIdentity.WOW_CONTAINER_ID) return null
        return identity(context).computerName.takeIf { MachineIdentity.isValidHostname(it) }
    }

    /** Applies the identity to the WoW container only; other containers stay as they are. */
    @JvmStatic
    fun applyToContainer(context: Context, container: Container?) {
        if (container == null || container.id != MachineIdentity.WOW_CONTAINER_ID) return
        val wineDir = File(container.rootDir, ".wine")
        try {
            val identity = identity(context)
            val store = store(context)
            // What the last run (or a new prefix) left behind, looked at before it is overwritten; after the first start of a
            // new identity it is expected to differ (shared defaults of the shipped prefix).
            val before = deviations(wineDir, identity)
            store.recordPrefixMismatch(identity, "vor_anwenden", before)
            // What Wine itself keeps in the prefix and could renew after an update; measured before anything is written, never changed.
            recordPrefixTraits(container, wineDir, identity, store)
            val changed = applyToPrefix(wineDir, identity)
            // From now on a copy found in the game folder never replaces this identity (it has been used for a start).
            store.markApplied(identity)
            if (before.isNotEmpty()) store.recordPrefixMismatch(identity, "nach_anwenden", deviations(wineDir, identity))
            Timber.i("Machine identity applied to prefix, %d value(s) changed", changed)
            recordSystemView(context, identity, store)
        } catch (e: Exception) {
            Timber.w("Machine identity could not be applied: %s", e.javaClass.simpleName)
        }
    }

    /** Measures the prefix traits ([PrefixTraits]) and logs a change; whatever fails here is swallowed. Reads only, writes nothing in the prefix. */
    private fun recordPrefixTraits(container: Container, wineDir: File, identity: MachineIdentity, store: MachineIdentityStore) {
        try {
            val lower = container.basePrefix?.takeIf { it.isNotEmpty() }?.let { File(it) }
            val change = store.recordPrefixTraits(identity, PrefixTraits.capture(wineDir, lower))
            Timber.i("Prefix traits recorded: %s", change?.javaClass?.simpleName ?: "failed")
        } catch (e: Exception) {
            Timber.w("Prefix traits could not be recorded: %s", e.javaClass.simpleName)
        }
    }

    /**
     * Captures the system view of this start (bounded in time, see [SystemViewCapture.captureBounded]) and logs a change. Whatever
     * fails here is swallowed: the start never waits for it or fails because of it.
     */
    private fun recordSystemView(context: Context, identity: MachineIdentity, store: MachineIdentityStore) {
        try {
            val snapshot = SystemViewCapture.captureBounded(context, MachineIdentity.isValidHostname(identity.computerName))
            val change = store.recordSystemView(identity, snapshot)
            Timber.i("System view recorded: %s", change?.javaClass?.simpleName ?: "failed")
        } catch (e: Exception) {
            Timber.w("System view could not be recorded: %s", e.javaClass.simpleName)
        }
    }

    /** Prefix traits summary for the diagnostics page and the error report. */
    @JvmStatic
    fun prefixSummary(context: Context): PrefixTraits.Summary = store(context).prefixSummary()

    /** System view summary for the diagnostics page and the error report. */
    @JvmStatic
    fun systemSummary(context: Context): SystemView.Summary = store(context).systemSummary()

    /** The registry values of the identity, one entry per key the Wine prefix holds. */
    fun registryEdits(identity: MachineIdentity): List<WineRegText.Edit> {
        val edits = ArrayList<WineRegText.Edit>()
        edits += WineRegText.string(CRYPTOGRAPHY, "MachineGuid", identity.machineGuid)
        edits += WineRegText.string(COMPUTER_NAME_KEY, "ComputerName", identity.computerName)
        edits += WineRegText.string(TCPIP_KEY, "Hostname", identity.computerName)
        for (key in listOf(NT_CV, WOW_NT_CV)) {
            edits += WineRegText.string(key, "ProductId", identity.productId)
            edits += WineRegText.dword(key, "InstallDate", identity.installDate.toInt())
        }
        for (key in listOf(WINDOWS_CV, WOW_WINDOWS_CV)) {
            edits += WineRegText.string(key, "ProductId", identity.productId)
        }
        return edits
    }

    /**
     * Writes the identity into [wineDir]/system.reg and the volume serial file. Only values that
     * differ are written; the number of changed values (registry values plus serial file) is
     * returned, 0 on an up to date prefix. Keys the prefix does not have are not created.
     */
    @JvmStatic
    fun applyToPrefix(wineDir: File, identity: MachineIdentity): Int {
        var changed = 0
        val systemReg = File(wineDir, "system.reg")
        if (systemReg.isFile) changed += WineRegText.applyToFile(systemReg, registryEdits(identity))
        if (writeSerial(File(wineDir, SERIAL_FILE), identity.volumeSerial)) changed++
        return changed
    }

    /**
     * Names of the identity fields whose value in the prefix differs from [identity] (never the values): machineGuid, computerName,
     * hostname, installDate, productId, volumeSerial. Registry keys the prefix does not have are skipped (they are never created
     * either); without system.reg only the serial file is compared.
     */
    @JvmStatic
    fun deviations(wineDir: File, identity: MachineIdentity): List<String> {
        val found = LinkedHashSet<String>()
        val systemReg = File(wineDir, "system.reg")
        if (systemReg.isFile) {
            val text = try {
                String(Files.readAllBytes(systemReg.toPath()), StandardCharsets.ISO_8859_1)
            } catch (e: IOException) {
                null
            }
            if (text != null) {
                for (edit in registryEdits(identity)) {
                    if (!WineRegText.hasKey(text, edit.key)) continue
                    val raw = WineRegText.getRaw(text, edit.key, edit.name)
                    if (raw == null || !raw.equals(edit.raw, ignoreCase = true)) found += fieldOf(edit)
                }
            }
        }
        val serialFile = File(wineDir, SERIAL_FILE)
        val serial = try {
            if (serialFile.isFile) String(Files.readAllBytes(serialFile.toPath()), StandardCharsets.UTF_8).trim() else null
        } catch (e: IOException) {
            null
        }
        if (!identity.volumeSerial.equals(serial, ignoreCase = true)) found += "volumeSerial"
        return found.toList().sortedBy { FIELD_ORDER.indexOf(it) }
    }

    private val FIELD_ORDER = listOf("machineGuid", "computerName", "hostname", "installDate", "productId", "volumeSerial")

    private fun fieldOf(edit: WineRegText.Edit): String = when (edit.name) {
        "MachineGuid" -> "machineGuid"
        "ComputerName" -> "computerName"
        "Hostname" -> "hostname"
        "InstallDate" -> "installDate"
        else -> "productId"
    }

    private fun writeSerial(file: File, serial: String): Boolean {
        return try {
            val current = if (file.isFile) String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim() else null
            if (current.equals(serial, ignoreCase = true)) return false
            file.parentFile?.let { Files.createDirectories(it.toPath()) }
            Files.write(file.toPath(), (serial + "\n").toByteArray(StandardCharsets.UTF_8))
            true
        } catch (e: IOException) {
            false
        }
    }
}
