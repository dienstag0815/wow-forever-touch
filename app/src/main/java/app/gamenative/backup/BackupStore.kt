package app.gamenative.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.gamenative.BuildConfig
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.cooldown.CooldownService
import app.gamenative.ui.screen.wow.FirstLayoutChoice
import app.gamenative.ui.screen.wow.InputMode
import app.gamenative.ui.screen.wow.WowTouchProfiles
import app.gamenative.ui.screen.wow.launcher.AppLanguage
import app.gamenative.ui.screen.wow.launcher.LauncherArtSettings
import app.gamenative.update.UpdateManager
import app.gamenative.utils.LocaleHelper
import com.winlator.core.FileUtils
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.inputcontrols.ProfileUpdatePolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.Date
import timber.log.Timber
import com.winlator.PrefManager as WinlatorPrefs

/** Was ein Import getan hat; die Oberfläche macht daraus die Meldung. */
internal data class ImportResult(
    val created: Int,
    val replaced: Int,
    val renamed: Int,
    val identical: Int,
    val settings: Int,
    /** Profile, die nicht geschrieben werden konnten. */
    val failed: Int,
    /** Name des Profils, das die gesicherte Betriebsart jetzt benutzt; leer, wenn sie keins hat. */
    val activeProfile: String,
)

/**
 * Der Android-Teil des Backups: liest Profile und Einstellungen vom Gerät ([export]) und schreibt ein Backup zurück ([apply]).
 * Format, Erlaubnisliste und Import-Plan sind rein und liegen in [BackupFormat], [BackupAllowlist] und [BackupImportPlan].
 *
 * Nicht dabei, mit Absicht: gespeicherte Anmeldung, Geräte-Identität, Spielordner und seine Rechte, Update-Feed und Zeitstempel,
 * "schon gefragt"-Merker, Protokolle, Zwischenspeicher; ebenso die Einstellungen des Gamepad-Managers und die "Zuletzt benutzt"-Listen
 * des Editors (kein Eintrag der Erlaubnisliste).
 */
internal object BackupStore {
    private val PROFILE_FILE = Regex("""controls-(\d+)\.icp""")

    /** Datei in filesDir mit dem Zufallswert dieser Installation (Grundlage der Gerätekennung); nie Teil eines Backups. */
    private const val DEVICE_SEED_FILE = "backup_device.seed"
    private const val DEVICE_SEED_BYTES = 16

    /**
     * Gerätekennung dieser Installation für Dateiname und Manifest ([BackupFormat.deviceTag]). Der Zufallswert wird beim ersten Mal erzeugt
     * und in filesDir abgelegt; keine Geräte-ID des Systems. Lässt sich die Datei nicht schreiben, gilt der Wert nur für diesen Aufruf.
     */
    fun deviceTag(context: Context): String {
        val file = File(context.applicationContext.filesDir, DEVICE_SEED_FILE)
        val stored = try {
            if (file.isFile) file.readText().trim() else ""
        } catch (e: IOException) {
            ""
        }
        if (stored.length == 2 * DEVICE_SEED_BYTES && stored.all { it in '0'..'9' || it in 'a'..'f' }) return BackupFormat.deviceTag(stored)
        val bytes = ByteArray(DEVICE_SEED_BYTES).also { SecureRandom().nextBytes(it) }
        val seed = bytes.joinToString("") { "%02x".format(it) }
        try {
            file.writeText(seed)
        } catch (e: IOException) {
            Timber.w(e, "Backup: device seed not saved")
        }
        return BackupFormat.deviceTag(seed)
    }

    /** Vorschlag für den Dateinamen im Auswahlfenster: Zeitpunkt auf die Sekunde und Gerätekennung, damit keine zwei Backups gleich heißen. */
    fun suggestedFileName(context: Context): String = BackupFormat.fileName(BackupFormat.fileStamp(Date()), deviceTag(context))

    // --- Export ---------------------------------------------------------------------------------------------------------

    /** Das ganze Backup als Zip-Bytes. */
    fun export(context: Context): ByteArray {
        val app = context.applicationContext
        val profiles = exportProfiles(app)
        val settings = exportSettings(app)
        val mode = InputMode.current(app)
        val manifest = BackupManifest(
            formatVersion = BackupFormat.FORMAT_VERSION,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            createdAt = BackupFormat.createdAtText(Date()),
            profileCount = profiles.size,
            settingCount = settings.size,
            inputMode = mode.name,
            activeProfile = mode.profileName.orEmpty(),
            device = deviceTag(app),
        )
        return BackupFormat.pack(manifest, profiles, settings)
    }

    /**
     * Alle Steuerungsprofile bis auf die unveränderten Vorlagen der App (deren Datei ist noch so, wie die App sie geschrieben hat:
     * der gemerkte Hash passt). Eigene und veränderte Profile, auch "Touch (Mein Layout)", sind dabei.
     */
    private fun exportProfiles(app: Context): List<BackupProfile> {
        WinlatorPrefs.init(app)
        val result = ArrayList<BackupProfile>()
        val files = InputControlsManager.getProfilesDir(app).listFiles() ?: return result
        for (file in files.sortedBy { it.name }) {
            if (!PROFILE_FILE.matches(file.name)) continue
            val bytes = FileUtils.read(file) ?: continue
            val stored = WinlatorPrefs.getString(ProfileUpdatePolicy.hashKey(file.name), "")
            if (stored.isNotEmpty() && stored == ProfileUpdatePolicy.sha256Hex(bytes)) continue
            val profile = BackupFormat.parseProfile(bytes) ?: continue
            result.add(profile)
        }
        return result
    }

    private fun exportSettings(app: Context): List<BackupSetting> {
        val result = ArrayList<BackupSetting>()
        val maps = HashMap<String, Map<String, *>>()
        // Die Einstellungen des Spiel-Containers kommen vom Container selbst (gleiche Reihenfolge wie in der Erlaubnisliste).
        val container = ContainerBackupStore.read(app).associateBy { it.spec.key }
        for (spec in BackupAllowlist.ALL) {
            if (spec.file == BackupAllowlist.FILE_CONTAINER) {
                container[spec.key]?.let { result.add(it) }
                continue
            }
            val raw: Any? = if (spec.file == BackupAllowlist.DATASTORE) {
                datastoreValue(spec.key)
            } else {
                maps.getOrPut(spec.file) { app.getSharedPreferences(spec.file, Context.MODE_PRIVATE).all }[spec.key]
            }
            val value = BackupAllowlist.coerce(spec, raw) ?: continue
            result.add(BackupSetting(spec, value))
        }
        return result
    }

    /** Die Einstellungen im DataStore der App; ohne gewählte Sprache gibt es nichts zu sichern. */
    private fun datastoreValue(key: String): Any? = when (key) {
        BackupAllowlist.KEY_APP_LANGUAGE -> PrefManager.appLanguage.takeIf { it.isNotEmpty() }
        "wow_emoji_symbols" -> PrefManager.wowEmojiSymbols
        "show_fps" -> PrefManager.showFps
        "performance_hud_compact_mode" -> PrefManager.performanceHudCompactMode
        "performance_hud_size" -> PrefManager.performanceHudSize
        "performance_hud_background_opacity" -> PrefManager.performanceHudBackgroundOpacity
        "performance_hud_color_intensity" -> PrefManager.performanceHudColorIntensity
        "performance_hud_x_fraction" -> PrefManager.performanceHudXFraction
        "performance_hud_y_fraction" -> PrefManager.performanceHudYFraction
        else -> hudFlag(key)?.get()
    }

    /** Die Schalter der Leistungsanzeige nach ihrem Namen im DataStore (siehe [BackupAllowlist.HUD_FLAGS]). */
    private fun hudFlag(key: String): kotlin.reflect.KMutableProperty0<Boolean>? = when (key) {
        "performance_hud_show_frame_rate" -> PrefManager::performanceHudShowFrameRate
        "performance_hud_show_cpu_usage" -> PrefManager::performanceHudShowCpuUsage
        "performance_hud_show_gpu_usage" -> PrefManager::performanceHudShowGpuUsage
        "performance_hud_show_ram_usage" -> PrefManager::performanceHudShowRamUsage
        "performance_hud_show_battery_level" -> PrefManager::performanceHudShowBatteryLevel
        "performance_hud_show_power_draw" -> PrefManager::performanceHudShowPowerDraw
        "performance_hud_show_battery_runtime" -> PrefManager::performanceHudShowBatteryRuntime
        "performance_hud_show_battery_temperature" -> PrefManager::performanceHudShowBatteryTemperature
        "performance_hud_show_clock_time" -> PrefManager::performanceHudShowClockTime
        "performance_hud_show_cpu_temperature" -> PrefManager::performanceHudShowCpuTemperature
        "performance_hud_show_gpu_temperature" -> PrefManager::performanceHudShowGpuTemperature
        "performance_hud_show_fan" -> PrefManager::showPerformanceHudFan
        "performance_hud_show_tuner_caps" -> PrefManager::showPerformanceHudTunerCaps
        "performance_hud_show_frame_rate_graph" -> PrefManager::performanceHudShowFrameRateGraph
        "performance_hud_show_cpu_usage_graph" -> PrefManager::performanceHudShowCpuUsageGraph
        "performance_hud_show_gpu_usage_graph" -> PrefManager::performanceHudShowGpuUsageGraph
        "performance_hud_show_text_outline" -> PrefManager::performanceHudShowTextOutline
        else -> null
    }

    /** Schreibt das Backup in die vom Benutzer gewählte Datei [uri]; null, wenn es nicht ging (sonst der Kopf des geschriebenen Backups). */
    fun exportTo(context: Context, uri: Uri): BackupManifest? {
        return try {
            val bytes = export(context)
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: return null
            out.use { it.write(bytes) }
            (BackupFormat.unpack(bytes) as? UnpackResult.Ok)?.backup?.manifest
        } catch (e: Exception) {
            Timber.w(e, "Backup export failed")
            null
        }
    }

    /**
     * Schreibt das Backup unter dem üblichen Namen ([suggestedFileName]) in den Ordner [dir] (Rückstufen: gemeinsamer Downloads-Ordner,
     * der eine Deinstallation übersteht). Gibt die Datei zurück, wenn sie vollständig geschrieben und wieder lesbar ist, sonst null.
     */
    fun exportToFile(context: Context, dir: File): File? {
        return try {
            val bytes = export(context)
            if (BackupFormat.unpack(bytes) !is UnpackResult.Ok) return null
            dir.mkdirs()
            val target = File(dir, suggestedFileName(context))
            val part = File(dir, target.name + ".part")
            part.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            target.takeIf { it.length() == bytes.size.toLong() }
        } catch (e: Exception) {
            Timber.w(e, "Backup file export failed")
            null
        }
    }

    // --- Import ---------------------------------------------------------------------------------------------------------

    /** Liest die vom Benutzer gewählte Datei [uri] (höchstens [BackupFormat.MAX_TOTAL_BYTES]) und prüft sie; schreibt nichts. */
    fun read(context: Context, uri: Uri): UnpackResult {
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Timber.w(e, "Backup open failed")
            null
        } ?: return UnpackResult.Failed(BackupError.BROKEN)
        val bytes = try {
            stream.use { readUpTo(it, BackupFormat.MAX_TOTAL_BYTES + 1) }
        } catch (e: IOException) {
            Timber.w(e, "Backup read failed")
            return UnpackResult.Failed(BackupError.BROKEN)
        }
        if (bytes.size > BackupFormat.MAX_TOTAL_BYTES) return UnpackResult.Failed(BackupError.TOO_BIG)
        return BackupFormat.unpack(bytes)
    }

    private fun readUpTo(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Die Profile, die schon auf dem Gerät liegen (für Konflikte und den Plan). Unlesbare Dateien belegen nur ihre Id. */
    fun existingProfiles(context: Context): List<ExistingProfile> {
        val result = ArrayList<ExistingProfile>()
        val files = InputControlsManager.getProfilesDir(context.applicationContext).listFiles() ?: return result
        for (file in files.sortedBy { it.name }) {
            val fileId = PROFILE_FILE.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val bytes = FileUtils.read(file)
            val profile = bytes?.let { BackupFormat.parseProfile(it) }
            if (profile == null) result.add(ExistingProfile(fileId, "", null)) else result.add(ExistingProfile(profile.id, profile.name, profile.json))
        }
        return result
    }

    /** Namen der Profile aus [backup], die auf dem Gerät mit anderem Inhalt liegen. */
    fun conflicts(context: Context, backup: Backup): List<String> =
        BackupImportPlan.conflicts(backup.profiles, existingProfiles(context))

    /** Übernimmt [backup]: Profile nach [policy], dann die Einstellungen. Wirft nicht; was nicht ging, steht in [ImportResult.failed]. */
    fun apply(context: Context, backup: Backup, policy: ConflictPolicy): ImportResult {
        val app = context.applicationContext
        // Wer sein Layout aus einem Backup holt, bekommt vor dem ersten Spielstart nicht noch die Layout-Wahl gezeigt.
        FirstLayoutChoice.markDecidedByImport(app)
        val plan = BackupImportPlan.plan(backup.profiles, existingProfiles(app), policy)
        var failed = 0
        // Der Hash wird bewusst nicht gemerkt: ein App-Update sieht die Datei dann als vom Spieler verändert und lässt sie in Ruhe.
        for (step in plan.writes) {
            if (!FileUtils.writeString(ControlsProfile.getProfileFile(app, step.targetId), step.json)) failed++
        }
        if (plan.writes.isNotEmpty()) PluviaApp.inputControlsManager?.loadProfiles(false)

        // Container-Werte: der Container kann noch fehlen (frische Installation), darum vormerken; der nächste Start schreibt sie.
        ContainerBackupStore.savePending(app, ContainerBackup.overridesFrom(backup.settings))
        val applied = applySettings(app, backup.settings)

        val mode = InputMode.current(app)
        val activeId = WowTouchProfiles.resolveProfileId(app, mode)
        val activeName = if (activeId == null) "" else mode.profileName.orEmpty()
        return ImportResult(
            created = plan.count(ProfileAction.CREATE),
            replaced = plan.count(ProfileAction.REPLACE),
            renamed = plan.count(ProfileAction.ADD_RENAMED),
            identical = plan.count(ProfileAction.IDENTICAL),
            settings = applied,
            failed = failed,
            activeProfile = activeName,
        )
    }

    /** Schreibt die Einstellungen und gibt zurück, wie viele übernommen wurden. */
    private fun applySettings(app: Context, settings: List<BackupSetting>): Int {
        var count = 0
        val editors = HashMap<String, SharedPreferences.Editor>()
        val accepted = ArrayList<BackupSetting>()
        for (setting in settings) {
            val spec = setting.spec
            val value = setting.value
            if (spec.file == BackupAllowlist.FILE_CONTAINER) {
                count++
                continue
            }
            // Eine Betriebsart, die es in dieser App-Version nicht gibt, bleibt außen vor.
            if (spec.key == BackupAllowlist.KEY_INPUT_MODE && spec.file == BackupAllowlist.FILE_MAIN &&
                InputMode.entries.none { it.name == value }
            ) continue
            if (spec.file == BackupAllowlist.DATASTORE) {
                writeDatastore(app, spec.key, value)
            } else {
                val editor = editors.getOrPut(spec.file) { app.getSharedPreferences(spec.file, Context.MODE_PRIVATE).edit() }
                when (spec.type) {
                    SettingType.BOOL -> editor.putBoolean(spec.key, value as Boolean)
                    SettingType.INT -> editor.putInt(spec.key, value as Int)
                    SettingType.LONG -> editor.putLong(spec.key, value as Long)
                    SettingType.FLOAT -> editor.putFloat(spec.key, value as Float)
                    SettingType.STRING -> editor.putString(spec.key, value as String)
                }
            }
            accepted.add(setting)
            count++
        }
        for (editor in editors.values) editor.commit()
        syncCaches(app, accepted)
        return count
    }

    private fun writeDatastore(app: Context, key: String, value: Any) {
        when (key) {
            BackupAllowlist.KEY_APP_LANGUAGE -> {
                val code = AppLanguage.tagFor(value as String)
                PrefManager.setAppLanguageNow(code)
                LocaleHelper.applyToApplication(app, code)
            }
            "wow_emoji_symbols" -> PrefManager.wowEmojiSymbols = value as Boolean
            "show_fps" -> PrefManager.showFps = value as Boolean
            "performance_hud_compact_mode" -> PrefManager.performanceHudCompactMode = value as Boolean
            "performance_hud_size" -> PrefManager.performanceHudSize = value as String
            "performance_hud_background_opacity" -> PrefManager.performanceHudBackgroundOpacity = value as Float
            "performance_hud_color_intensity" -> PrefManager.performanceHudColorIntensity = value as Float
            "performance_hud_x_fraction" -> PrefManager.performanceHudXFraction = value as Float
            "performance_hud_y_fraction" -> PrefManager.performanceHudYFraction = value as Float
            else -> hudFlag(key)?.set(value as Boolean)
        }
    }

    /** Einige Einstellungen merkt sich das laufende Programm zusätzlich im Speicher: dort über ihre eigenen Setter nachziehen. */
    private fun syncCaches(app: Context, accepted: List<BackupSetting>) {
        for (s in accepted) {
            val on = s.value as? Boolean ?: continue
            when (s.spec.file to s.spec.key) {
                BackupAllowlist.FILE_ART to "screenshots" -> LauncherArtSettings.setScreenshots(app, on)
                BackupAllowlist.FILE_ART to "wow_art" -> LauncherArtSettings.setWowArt(app, on)
                BackupAllowlist.FILE_UPDATE to "auto_install" -> UpdateManager.setAutoInstall(app, on)
                BackupAllowlist.FILE_COOLDOWN to "enabled" -> CooldownService.setEnabled(app, on)
                BackupAllowlist.FILE_COOLDOWN to "gcd" -> CooldownService.setShowGlobal(app, on)
            }
        }
    }
}
