package app.gamenative.backup

import android.content.Context
import app.gamenative.ui.screen.wow.wowPrefs
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import timber.log.Timber

/**
 * Der Android-Teil der Container-Sicherung ([ContainerBackup]): liest die Werte vom Spiel-Container für den Export, merkt die Werte eines
 * Imports vor und wendet sie beim nächsten Start an.
 */
internal object ContainerBackupStore {
    /** Kennung des Spiel-Containers (wie in `WoWForeverScreen`). */
    private const val CONTAINER_ID = "wow_forever"

    /** Eintrag in den Einstellungen "wow_forever": die vorgemerkten Werte eines Imports. Nie Teil eines Backups. */
    private const val KEY_PENDING = "backup_container_pending"

    /** Die Container-Werte für den Export; leer, wenn es den Container (noch) nicht gibt. */
    fun read(app: Context): List<BackupSetting> {
        val container = try {
            ContainerManager(app).getContainerById(CONTAINER_ID)
        } catch (e: Exception) {
            Timber.w(e, "Backup: container not readable")
            null
        } ?: return emptyList()
        val result = ArrayList<BackupSetting>()
        for (spec in ContainerBackup.SPECS) {
            val raw: Any? = when {
                ContainerBackup.isExtra(spec.key) -> container.getExtra(ContainerBackup.extraName(spec.key)).takeIf { it.isNotEmpty() }
                else -> field(container, spec.key)
            }
            val value = BackupAllowlist.coerce(spec, raw) ?: continue
            result.add(BackupSetting(spec, value))
        }
        return result
    }

    private fun field(container: Container, key: String): Any? = when (key) {
        ContainerBackup.KEY_TOUCHSCREEN -> container.isTouchscreenMode
        ContainerBackup.KEY_SHOOTER -> container.isShooterMode
        ContainerBackup.KEY_DISABLE_MOUSE -> container.isDisableMouseInput
        ContainerBackup.KEY_GESTURES -> container.gestureConfig
        ContainerBackup.KEY_SHOOTER_CONFIG -> container.shooterConfig
        else -> null
    }

    /** Merkt die Werte vor, die der nächste Start in den Container schreibt. */
    fun savePending(app: Context, overrides: Map<String, Any>) {
        wowPrefs(app).edit().putString(KEY_PENDING, ContainerBackup.encode(overrides)).apply()
    }

    /**
     * Schreibt vorgemerkte Werte in [container] (Aufruf vor dem Festlegen des Touch-Setups) und löscht die Vormerkung. Der Aufrufer
     * speichert den Container danach. Gibt zurück, ob etwas angewendet wurde.
     */
    fun applyPending(app: Context, container: Container): Boolean {
        val prefs = wowPrefs(app)
        val overrides = ContainerBackup.decode(prefs.getString(KEY_PENDING, null))
        if (!prefs.contains(KEY_PENDING)) return false
        prefs.edit().remove(KEY_PENDING).apply()
        if (overrides.isEmpty()) return false
        for ((key, value) in overrides) write(container, key, value)
        return true
    }

    private fun write(container: Container, key: String, value: Any) {
        if (ContainerBackup.isExtra(key)) {
            container.putExtra(ContainerBackup.extraName(key), value as String)
            return
        }
        when (key) {
            ContainerBackup.KEY_TOUCHSCREEN -> container.setTouchscreenMode(value as Boolean)
            ContainerBackup.KEY_SHOOTER -> container.setShooterMode(value as Boolean)
            ContainerBackup.KEY_DISABLE_MOUSE -> container.setDisableMouseInput(value as Boolean)
            ContainerBackup.KEY_GESTURES -> container.setGestureConfig(value as String)
            ContainerBackup.KEY_SHOOTER_CONFIG -> container.setShooterConfig(value as String)
        }
    }
}
