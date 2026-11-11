package app.gamenative.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONException
import org.json.JSONObject

/** Ein gesichertes Steuerungsprofil: [json] ist der Inhalt der Datei `controls-<id>.icp` unverändert. */
internal data class BackupProfile(val id: Int, val name: String, val json: String)

/** Eine gesicherte Einstellung; [value] ist Boolean, Int, Long, Float oder String passend zu `spec.type`. */
internal data class BackupSetting(val spec: SettingSpec, val value: Any)

/** Kopf des Backups (manifest.json). Unbekannte Felder beim Lesen werden ignoriert. */
internal data class BackupManifest(
    val formatVersion: Int,
    val appVersionName: String,
    val appVersionCode: Int,
    val createdAt: String,
    val profileCount: Int,
    val settingCount: Int,
    /** Betriebsart, die beim Sichern aktiv war (bestimmt das aktive Profil); leer, wenn nie gewählt. */
    val inputMode: String = "",
    /** Name des aktiven Profils zu [inputMode] (nur zur Anzeige); leer, wenn die Betriebsart keins hat. */
    val activeProfile: String = "",
    /** Gerätekennung des Sichernden ([BackupFormat.deviceTag]); leer bei Backups aus älteren Versionen. */
    val device: String = "",
)

internal data class Backup(
    val manifest: BackupManifest,
    val profiles: List<BackupProfile>,
    val settings: List<BackupSetting>,
    /** Einträge, die ignoriert wurden: unbekannte oder falsch typisierte Einstellungen, unlesbare Profile. */
    val skipped: Int = 0,
)

internal enum class BackupError { NOT_A_BACKUP, NO_MANIFEST, WRONG_FORMAT, TOO_OLD, TOO_NEW, TOO_BIG, BROKEN }

internal sealed class UnpackResult {
    data class Ok(val backup: Backup) : UnpackResult()

    data class Failed(val error: BackupError) : UnpackResult()
}

/**
 * Das Backup-Format (rein, ohne Android, per JUnit geprüft): ein Zip mit
 *  - `manifest.json`: Format, Format-Version, App-Version, Zeitpunkt (mit Sekunden), Zähler, aktive Betriebsart, Gerätekennung,
 *  - `settings.json`: `{ "<Datei>": { "<Schlüssel>": Wert } }`, nur Einstellungen der [BackupAllowlist],
 *  - `profiles/controls-<id>.icp`: je Steuerungsprofil der Inhalt der Datei.
 * Beim Lesen zählt nur, was bekannt und gültig ist; alles andere wird ignoriert (und gezählt), nichts davon wird ausgeführt oder geschrieben.
 */
internal object BackupFormat {
    const val FORMAT_NAME = "wow-forever-touch-backup"
    const val FORMAT_VERSION = 1
    const val MIN_FORMAT_VERSION = 1

    const val MANIFEST_ENTRY = "manifest.json"
    const val SETTINGS_ENTRY = "settings.json"
    const val PROFILES_DIR = "profiles/"

    /** Grenzen gegen kaputte oder böse Dateien (Zip-Bombe): Größe je Eintrag, insgesamt und Zahl der Einträge. */
    const val MAX_ENTRY_BYTES = 4 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 32 * 1024 * 1024
    const val MAX_ENTRIES = 500
    const val MAX_NAME_LENGTH = 120

    /** Länge der Gerätekennung im Dateinamen und im Manifest (Hex-Zeichen). */
    const val DEVICE_TAG_LENGTH = 6

    /** Zeitpunkt für den Dateinamen: `yyyy-MM-dd_HH-mm-ss`. */
    fun fileStamp(date: Date): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(date)

    /** Zeitpunkt für das Manifest (`createdAt`): `yyyy-MM-dd HH:mm:ss`. Ältere Backups haben ihn ohne Sekunden; beide werden gelesen. */
    fun createdAtText(date: Date): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(date)

    /**
     * Kennung eines Geräts für den Dateinamen: die ersten [DEVICE_TAG_LENGTH] Hex-Zeichen von SHA-256 über [seed], einen Zufallswert, der je
     * Installation einmal erzeugt wird (nicht Teil des Backups). Kein Gerätemerkmal im Klartext, nicht umkehrbar.
     */
    fun deviceTag(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder()
        for (b in digest) hex.append(String.format(Locale.ROOT, "%02x", b))
        return hex.substring(0, DEVICE_TAG_LENGTH)
    }

    /** Eine gültige Gerätekennung: genau [DEVICE_TAG_LENGTH] Kleinbuchstaben/Ziffern (Hex). */
    fun isDeviceTag(text: String): Boolean = text.length == DEVICE_TAG_LENGTH && text.all { it in '0'..'9' || it in 'a'..'f' }

    /** Vorschlag für den Dateinamen: `wow-forever-touch-backup-<JJJJ-MM-TT>_<HH-MM-SS>_<gerät>.zip`; [stamp] kommt von [fileStamp]. */
    fun fileName(stamp: String, device: String): String = "wow-forever-touch-backup-${stamp}_$device.zip"

    fun pack(manifest: BackupManifest, profiles: List<BackupProfile>, settings: List<BackupSetting>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, MANIFEST_ENTRY, manifestJson(manifest).toString(2).toByteArray(Charsets.UTF_8))
            put(zip, SETTINGS_ENTRY, settingsJson(settings).toString(2).toByteArray(Charsets.UTF_8))
            for (profile in profiles) put(zip, PROFILES_DIR + "controls-" + profile.id + ".icp", profile.json.toByteArray(Charsets.UTF_8))
        }
        return out.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    fun manifestJson(m: BackupManifest): JSONObject = JSONObject()
        .put("format", FORMAT_NAME)
        .put("formatVersion", m.formatVersion)
        .put("appVersionName", m.appVersionName)
        .put("appVersionCode", m.appVersionCode)
        .put("createdAt", m.createdAt)
        .put("profileCount", m.profileCount)
        .put("settingCount", m.settingCount)
        .put("inputMode", m.inputMode)
        .put("activeProfile", m.activeProfile)
        .put("device", m.device)

    fun settingsJson(settings: List<BackupSetting>): JSONObject {
        val root = JSONObject()
        for (s in settings) {
            val file = root.optJSONObject(s.spec.file) ?: JSONObject().also { root.put(s.spec.file, it) }
            // Float über seine Dezimaldarstellung, damit 0.1f als 0.1 im Backup steht und nicht als 0.10000000149011612.
            val raw: Any = s.value
            val value: Any = if (raw is Float) raw.toString().toDouble() else raw
            file.put(s.spec.key, value)
        }
        return root
    }

    fun unpack(bytes: ByteArray): UnpackResult {
        val entries = LinkedHashMap<String, ByteArray>()
        try {
            var total = 0
            var count = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_ENTRIES) return UnpackResult.Failed(BackupError.TOO_BIG)
                    if (entry.isDirectory) continue
                    val data = readLimited(zip, MAX_ENTRY_BYTES) ?: return UnpackResult.Failed(BackupError.TOO_BIG)
                    total += data.size
                    if (total > MAX_TOTAL_BYTES) return UnpackResult.Failed(BackupError.TOO_BIG)
                    entries[entry.name] = data
                }
            }
        } catch (e: IOException) {
            return UnpackResult.Failed(BackupError.NOT_A_BACKUP)
        } catch (e: RuntimeException) {
            return UnpackResult.Failed(BackupError.NOT_A_BACKUP)
        }
        if (entries.isEmpty()) return UnpackResult.Failed(BackupError.NOT_A_BACKUP)

        val manifestBytes = entries[MANIFEST_ENTRY] ?: return UnpackResult.Failed(BackupError.NO_MANIFEST)
        val manifestJson = parseObject(manifestBytes) ?: return UnpackResult.Failed(BackupError.BROKEN)
        if (manifestJson.optString("format") != FORMAT_NAME) return UnpackResult.Failed(BackupError.WRONG_FORMAT)
        val version = manifestJson.optInt("formatVersion", 0)
        if (version < MIN_FORMAT_VERSION) return UnpackResult.Failed(BackupError.TOO_OLD)
        if (version > FORMAT_VERSION) return UnpackResult.Failed(BackupError.TOO_NEW)

        var skipped = 0
        val settings = ArrayList<BackupSetting>()
        val settingsBytes = entries[SETTINGS_ENTRY]
        if (settingsBytes != null) {
            val root = parseObject(settingsBytes) ?: return UnpackResult.Failed(BackupError.BROKEN)
            val files = root.keys()
            while (files.hasNext()) {
                val file = files.next()
                val values = root.optJSONObject(file)
                if (values == null) {
                    skipped++
                    continue
                }
                val keys = values.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val spec = BackupAllowlist.find(file, key)
                    val value = if (spec == null) null else BackupAllowlist.coerce(spec, values.opt(key))
                    if (spec == null || value == null) skipped++ else settings.add(BackupSetting(spec, value))
                }
            }
        }

        val profiles = ArrayList<BackupProfile>()
        for ((name, data) in entries) {
            if (!name.startsWith(PROFILES_DIR) || !name.endsWith(".icp")) continue
            val profile = parseProfile(data)
            if (profile == null) skipped++ else profiles.add(profile)
        }
        profiles.sortBy { it.id }

        val manifest = BackupManifest(
            formatVersion = version,
            appVersionName = manifestJson.optString("appVersionName"),
            appVersionCode = manifestJson.optInt("appVersionCode", 0),
            createdAt = manifestJson.optString("createdAt"),
            profileCount = profiles.size,
            settingCount = settings.size,
            inputMode = manifestJson.optString("inputMode"),
            activeProfile = manifestJson.optString("activeProfile"),
            device = manifestJson.optString("device").takeIf { isDeviceTag(it) } ?: "",
        )
        return UnpackResult.Ok(Backup(manifest, profiles, settings, skipped))
    }

    /** Ein Profil muss ein JSON-Objekt mit ganzer `id` (nicht negativ) und nichtleerem `name` sein (wie `InputControlsManager.importProfile`). */
    fun parseProfile(data: ByteArray): BackupProfile? {
        val json = parseObject(data) ?: return null
        val name = json.optString("name", "").trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH || !json.has("id")) return null
        val id = json.optInt("id", -1)
        if (id < 0) return null
        return BackupProfile(id, name, String(data, Charsets.UTF_8))
    }

    private fun parseObject(data: ByteArray): JSONObject? = try {
        JSONObject(String(data, Charsets.UTF_8))
    } catch (e: JSONException) {
        null
    }

    /** Liest den Strom bis zum Ende, aber höchstens [limit] Bytes; null, wenn er größer ist. */
    private fun readLimited(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
