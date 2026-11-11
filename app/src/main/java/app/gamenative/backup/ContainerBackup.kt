package app.gamenative.backup

import org.json.JSONObject

/**
 * Die Einstellungen des Spiel-Containers im Backup (rein, ohne Android, per JUnit geprüft). Sie machen mit aus, ob der Spielbildschirm nach
 * einer Neuinstallation sofort wieder so aussieht und sich so bedient wie vorher: Touchscreen-Modus, Shooter-Modus (an = Sticks weg, dafür
 * oben der Knopf "Maus aktivieren"), Maus aus, Gesten, Gyro, Bildeffekte, FPS-Grenze.
 *
 * Im Backup stehen sie unter der Pseudo-Datei [BackupAllowlist.FILE_CONTAINER]. Schlüssel ohne Vorsatz sind Felder des Containers
 * ([FIELDS]), Schlüssel mit [EXTRA_PREFIX] sind seine Zusatzwerte ([EXTRAS], immer Zeichenketten). Nicht dabei: alles, was der Start selbst
 * aus App-Einstellungen ableitet (Bildschirmgröße, Treiber, Laufwerke, Profil-ID, Touch-Kennzeichen).
 *
 * Beim Import wird nicht in den Container geschrieben (er kann noch fehlen): die Werte werden vorgemerkt ([encode]) und der nächste Start
 * wendet sie an, bevor er das Touch-Setup festlegt.
 */
internal object ContainerBackup {
    const val EXTRA_PREFIX = "extra."

    const val KEY_TOUCHSCREEN = "touchscreenMode"
    const val KEY_SHOOTER = "shooterMode"
    const val KEY_DISABLE_MOUSE = "disableMouseInput"
    const val KEY_GESTURES = "gestureConfig"
    const val KEY_SHOOTER_CONFIG = "shooterConfig"

    /** Felder des Containers und ihr Typ. */
    val FIELDS: Map<String, SettingType> = linkedMapOf(
        KEY_TOUCHSCREEN to SettingType.BOOL,
        KEY_SHOOTER to SettingType.BOOL,
        KEY_DISABLE_MOUSE to SettingType.BOOL,
        KEY_GESTURES to SettingType.STRING,
        KEY_SHOOTER_CONFIG to SettingType.STRING,
    )

    /** Zusatzwerte des Containers (Gyro, Bildeffekte, FPS-Grenze); ohne [EXTRA_PREFIX]. */
    val EXTRAS: List<String> = listOf(
        "fpsLimiterEnabled", "fpsLimiterTarget",
        "gyroMode", "gyroLastTarget", "gyroActivation", "gyroSensitivity", "gyroVerticalScale", "gyroSteadyingDps", "gyroSmoothingMs",
        "gyroStickAntiDeadzone", "gyroTiltSteering", "gyroTiltFullScaleDegrees", "gyroTiltDeadzoneDegrees", "gyroInvertX", "gyroInvertY",
        "screenEffectsBrightness", "screenEffectsContrast", "screenEffectsGamma", "screenEffectsScalingMode", "screenEffectsFsrSharpness",
        "screenEffectsEnableToon", "screenEffectsEnableFXAA", "screenEffectsEnableVivid", "screenEffectsEnableCRT", "screenEffectsEnableNTSC",
    )

    /** Alle Einträge der Erlaubnisliste für den Container. */
    val SPECS: List<SettingSpec> =
        FIELDS.map { (key, type) -> SettingSpec(BackupAllowlist.FILE_CONTAINER, key, type) } +
            EXTRAS.map { SettingSpec(BackupAllowlist.FILE_CONTAINER, EXTRA_PREFIX + it, SettingType.STRING) }

    fun isExtra(key: String): Boolean = key.startsWith(EXTRA_PREFIX)

    fun extraName(key: String): String = key.removePrefix(EXTRA_PREFIX)

    /**
     * Was der Container nach dem Import bekommt: die Container-Werte des Backups. Hat das Backup keinen Shooter-Modus (ein Backup aus einer
     * Version davor, oder es gab noch keinen Container), gilt er als aus: für WoW ist er nur ein Fremdkörper, er blendet die Sticks aus.
     */
    fun overridesFrom(settings: List<BackupSetting>): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        for (s in settings) if (s.spec.file == BackupAllowlist.FILE_CONTAINER) result[s.spec.key] = s.value
        if (KEY_SHOOTER !in result) result[KEY_SHOOTER] = false
        return result
    }

    /** Die vorgemerkten Werte als Text für die Einstellungen. */
    fun encode(overrides: Map<String, Any>): String {
        val json = JSONObject()
        for ((key, value) in overrides) json.put(key, value)
        return json.toString()
    }

    /** Liest die vorgemerkten Werte; Unbekanntes und falsch Getyptes fällt weg, kaputter Text ergibt eine leere Liste. */
    fun decode(text: String?): Map<String, Any> {
        if (text.isNullOrBlank()) return emptyMap()
        val json = try {
            JSONObject(text)
        } catch (e: org.json.JSONException) {
            return emptyMap()
        }
        val result = LinkedHashMap<String, Any>()
        for (key in json.keys()) {
            val spec = BackupAllowlist.find(BackupAllowlist.FILE_CONTAINER, key) ?: continue
            val value = BackupAllowlist.coerce(spec, json.opt(key)) ?: continue
            result[key] = value
        }
        return result
    }
}
