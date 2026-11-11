package app.gamenative.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.gamenative.PrefManager
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.xenvironment.ImageFs
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Rundreise des Spielbildschirms: Export von einem Gerät mit eigenen Einstellungen → frische Installation (Standardwerte, Container mit
 * Shooter-Modus an wie ihn ein neuer Container hat) → Import → nächster Start wendet die vorgemerkten Container-Werte an → gleicher
 * Zustand. Läuft mit echten SharedPreferences, DataStore und Container-Datei unter Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class BackupGameScreenRoundTripTest {
    private lateinit var app: Context

    private val gestures = """{"holdLeftDelay":420,"clickWheelEnabled":true,"showClickHighlight":false}"""
    private val ownLayout = "Touch (Urlaub)"

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PrefManager.init(app)
    }

    private fun containerDir(): File = File(ImageFs.find(app).rootDir, "home/${ImageFs.USER}-wow_forever")

    /** Legt die Container-Datei an, wie der Start sie schreibt; [data] überschreibt die Felder. */
    private fun writeContainer(data: Map<String, Any>) {
        val dir = containerDir()
        dir.deleteRecursively()
        dir.mkdirs()
        val json = JSONObject().put("id", "wow_forever").put("name", "WoW Forever")
        for ((k, v) in data) json.put(k, v)
        File(dir, ".container").writeText(json.toString())
    }

    private fun container(): Container = ContainerManager(app).getContainerById("wow_forever")!!

    private fun prefs(file: String) = app.getSharedPreferences(file, Context.MODE_PRIVATE)

    private fun awaitHud(expected: Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (PrefManager.showFps != expected && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(expected, PrefManager.showFps)
    }

    private fun clearAllPrefs() {
        for (file in BackupAllowlist.FILES) if (file != BackupAllowlist.DATASTORE && file != BackupAllowlist.FILE_CONTAINER) prefs(file).edit().clear().commit()
    }

    private fun importBackup(bytes: ByteArray): ImportResult {
        val backup = (BackupFormat.unpack(bytes) as UnpackResult.Ok).backup
        return BackupStore.apply(app, backup, ConflictPolicy.REPLACE)
    }

    @Test
    fun `game screen settings come back after a reinstall`() {
        // --- Gerät vorher: eigene Einstellungen ---
        writeContainer(
            mapOf(
                "touchscreenMode" to true,
                "shooterMode" to false,
                "disableMouseInput" to true,
                "gestureConfig" to gestures,
                "extraData" to JSONObject()
                    .put("gyroMode", "2").put("gyroSensitivity", "1.5").put("fpsLimiterEnabled", "false")
                    .put("screenEffectsScalingMode", "5").put("profileId", "7").put("wowTouch", "1"),
            ),
        )
        prefs(BackupAllowlist.FILE_MAIN).edit()
            .putString("input_mode", "TOUCH_CUSTOM")
            .putString("touch_layout_name", ownLayout)
            .putString("touch_setup_mode", "TOUCH_CUSTOM")
            .putString("touch_setup_gesture_base", """{"holdLeftDelay":300}""")
            .putBoolean("touch_setup_force_defaults", true)
            .putString("resolution", "1280x720")
            .putBoolean("touch_hide_in_login", false)
            .commit()
        PrefManager.showFps = true
        PrefManager.performanceHudXFraction = 0.25f
        PrefManager.performanceHudShowCpuUsage = true
        awaitHud(true)

        val bytes = BackupStore.export(app)
        val exported = (BackupFormat.unpack(bytes) as UnpackResult.Ok).backup.settings.associate { (it.spec.file to it.spec.key) to it.value }
        assertEquals(true, exported[BackupAllowlist.FILE_CONTAINER to "touchscreenMode"])
        assertEquals(false, exported[BackupAllowlist.FILE_CONTAINER to "shooterMode"])
        assertEquals(true, exported[BackupAllowlist.FILE_CONTAINER to "disableMouseInput"])
        assertEquals(gestures, exported[BackupAllowlist.FILE_CONTAINER to "gestureConfig"])
        assertEquals("2", exported[BackupAllowlist.FILE_CONTAINER to "extra.gyroMode"])
        // Was der Start selbst ableitet oder der Spieler nur anfordert, bleibt draußen.
        assertFalse(exported.containsKey(BackupAllowlist.FILE_CONTAINER to "extra.profileId"))
        assertFalse(exported.containsKey(BackupAllowlist.FILE_CONTAINER to "extra.wowTouch"))
        assertFalse(exported.containsKey(BackupAllowlist.FILE_MAIN to "touch_setup_force_defaults"))

        // --- Frische Installation: Standardwerte, neuer Container (Shooter-Modus an, kein Touchscreen) ---
        clearAllPrefs()
        PrefManager.showFps = false
        PrefManager.performanceHudXFraction = 0f
        PrefManager.performanceHudShowCpuUsage = false
        awaitHud(false)
        writeContainer(mapOf("shooterMode" to true, "touchscreenMode" to false))
        assertTrue(container().isShooterMode)

        // --- Import, dann der nächste Start ---
        val result = importBackup(bytes)
        assertTrue(result.settings > 0)
        assertEquals("TOUCH_CUSTOM", prefs(BackupAllowlist.FILE_MAIN).getString("input_mode", null))
        assertEquals(ownLayout, prefs(BackupAllowlist.FILE_MAIN).getString("touch_layout_name", null))
        assertEquals("TOUCH_CUSTOM", prefs(BackupAllowlist.FILE_MAIN).getString("touch_setup_mode", null))
        assertEquals("1280x720", prefs(BackupAllowlist.FILE_MAIN).getString("resolution", null))
        assertFalse(prefs(BackupAllowlist.FILE_MAIN).getBoolean("touch_hide_in_login", true))
        assertFalse(prefs(BackupAllowlist.FILE_MAIN).contains("touch_setup_force_defaults"))
        awaitHud(true)
        assertEquals(0.25f, PrefManager.performanceHudXFraction, 0.0001f)
        assertTrue(PrefManager.performanceHudShowCpuUsage)

        // Der Import allein ändert den Container nicht (er kann fehlen); der Start wendet die Vormerkung an.
        assertTrue(container().isShooterMode)
        val started = container()
        assertTrue(ContainerBackupStore.applyPending(app, started))
        assertTrue(started.isTouchscreenMode)
        assertFalse(started.isShooterMode)
        assertTrue(started.isDisableMouseInput)
        assertEquals(gestures, started.gestureConfig)
        assertEquals("2", started.getExtra("gyroMode"))
        assertEquals("1.5", started.getExtra("gyroSensitivity"))
        assertEquals("false", started.getExtra("fpsLimiterEnabled"))
        assertEquals("5", started.getExtra("screenEffectsScalingMode"))
        // Einmalig: ein zweiter Start findet nichts mehr vor.
        assertFalse(ContainerBackupStore.applyPending(app, container()))
    }

    @Test
    fun `a backup from before the container section still switches shooter mode off`() {
        // Gerät vorher ohne Container (oder Backup aus einer älteren Version): das Backup hat keinen Container-Eintrag.
        containerDir().deleteRecursively()
        prefs(BackupAllowlist.FILE_MAIN).edit().putString("region", "EU").commit()
        val bytes = BackupStore.export(app)
        val settings = (BackupFormat.unpack(bytes) as UnpackResult.Ok).backup.settings
        assertTrue(settings.none { it.spec.file == BackupAllowlist.FILE_CONTAINER })

        clearAllPrefs()
        writeContainer(mapOf("shooterMode" to true))
        importBackup(bytes)
        assertEquals("EU", prefs(BackupAllowlist.FILE_MAIN).getString("region", null))

        val started = container()
        assertNotNull(started)
        assertTrue(ContainerBackupStore.applyPending(app, started))
        assertFalse("Shooter-Modus muss nach dem Import aus sein", started.isShooterMode)
    }
}
