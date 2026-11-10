package app.gamenative.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.gamenative.BuildConfig
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.cooldown.CooldownService
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
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /** Vorschlag für den Dateinamen im Auswahlfenster, mit dem heutigen Datum. */
    fun suggestedFileName(): String = BackupFormat.fileName(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()))

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
            createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date()),
            profileCount = profiles.size,
            settingCount = settings.size,
            inputMode = mode.name,
            activeProfile = mode.profileName.orEmpty(),
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
        for (spec in BackupAllowlist.ALL) {
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
        val plan = BackupImportPlan.plan(backup.profiles, existingProfiles(app), policy)
        var failed = 0
        // Der Hash wird bewusst nicht gemerkt: ein App-Update sieht die Datei dann als vom Spieler verändert und lässt sie in Ruhe.
        for (step in plan.writes) {
            if (!FileUtils.writeString(ControlsProfile.getProfileFile(app, step.targetId), step.json)) failed++
        }
        if (plan.writes.isNotEmpty()) PluviaApp.inputControlsManager?.loadProfiles(false)

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
