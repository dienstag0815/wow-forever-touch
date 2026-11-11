package app.gamenative.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerBackupTest {
    private fun setting(key: String, value: Any): BackupSetting {
        val spec = BackupAllowlist.find(BackupAllowlist.FILE_CONTAINER, key)
        assertNotNull("kein Eintrag $key", spec)
        return BackupSetting(spec!!, value)
    }

    private fun manifest(settings: Int) = BackupManifest(1, "x", 1, "2026-11-11 12:00", 0, settings)

    @Test
    fun `container entries are on the allowlist and in the container file only`() {
        assertTrue(ContainerBackup.SPECS.isNotEmpty())
        for (spec in ContainerBackup.SPECS) {
            assertEquals(BackupAllowlist.FILE_CONTAINER, spec.file)
            assertNotNull(BackupAllowlist.find(spec.file, spec.key))
        }
        assertNull(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, ContainerBackup.KEY_SHOOTER))
    }

    @Test
    fun `the fields that make up the game screen are in the backup`() {
        for (key in listOf("touchscreenMode", "shooterMode", "disableMouseInput", "gestureConfig", "shooterConfig")) {
            assertNotNull(key, BackupAllowlist.find(BackupAllowlist.FILE_CONTAINER, key))
        }
        for (name in listOf("gyroMode", "gyroSensitivity", "screenEffectsScalingMode", "fpsLimiterTarget")) {
            assertNotNull(name, BackupAllowlist.find(BackupAllowlist.FILE_CONTAINER, ContainerBackup.EXTRA_PREFIX + name))
        }
        assertEquals(SettingType.BOOL, BackupAllowlist.find(BackupAllowlist.FILE_CONTAINER, "shooterMode")!!.type)
        assertEquals(SettingType.STRING, BackupAllowlist.find(BackupAllowlist.FILE_CONTAINER, "extra.gyroMode")!!.type)
    }

    @Test
    fun `values that the start derives itself or that are secret are not in the backup`() {
        for (key in listOf("screenSize", "drives", "executablePath", "execArgs", "envVars", "profileId", "wowTouch")) {
            assertNull(key, BackupAllowlist.find(BackupAllowlist.FILE_CONTAINER, key))
            assertNull(key, BackupAllowlist.find(BackupAllowlist.FILE_CONTAINER, ContainerBackup.EXTRA_PREFIX + key))
        }
    }

    @Test
    fun `the touch setup state and the chosen layout are in the backup, the reset request is not`() {
        for (key in listOf("touch_layout_name", "touch_setup_mode", "touch_setup_gesture_base", "touch_setup_gesture_stash")) {
            assertNotNull(key, BackupAllowlist.find(BackupAllowlist.FILE_MAIN, key))
        }
        assertNull(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, "touch_setup_force_defaults"))
    }

    @Test
    fun `the performance display settings are in the backup`() {
        assertNotNull(BackupAllowlist.find(BackupAllowlist.DATASTORE, "show_fps"))
        assertNotNull(BackupAllowlist.find(BackupAllowlist.DATASTORE, "performance_hud_x_fraction"))
        assertEquals(17, BackupAllowlist.HUD_FLAGS.size)
        for (flag in BackupAllowlist.HUD_FLAGS) assertNotNull(flag, BackupAllowlist.find(BackupAllowlist.DATASTORE, flag))
    }

    @Test
    fun `a backup without shooter mode switches it off`() {
        val overrides = ContainerBackup.overridesFrom(listOf(setting("touchscreenMode", true)))
        assertEquals(false, overrides[ContainerBackup.KEY_SHOOTER])
        assertEquals(true, overrides[ContainerBackup.KEY_TOUCHSCREEN])
    }

    @Test
    fun `a backup without any container value still switches shooter mode off`() {
        val overrides = ContainerBackup.overridesFrom(emptyList())
        assertEquals(mapOf<String, Any>(ContainerBackup.KEY_SHOOTER to false), overrides)
    }

    @Test
    fun `shooter mode that the backup carries is kept`() {
        assertEquals(true, ContainerBackup.overridesFrom(listOf(setting("shooterMode", true)))[ContainerBackup.KEY_SHOOTER])
        assertEquals(false, ContainerBackup.overridesFrom(listOf(setting("shooterMode", false)))[ContainerBackup.KEY_SHOOTER])
    }

    @Test
    fun `settings of other files do not become container overrides`() {
        val region = BackupSetting(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, "region")!!, "EU")
        assertEquals(setOf(ContainerBackup.KEY_SHOOTER), ContainerBackup.overridesFrom(listOf(region)).keys)
    }

    @Test
    fun `pending values survive encode and decode with their types`() {
        val gestures = """{"holdLeftDelay":400,"clickWheelEnabled":true}"""
        val overrides = linkedMapOf<String, Any>(
            "touchscreenMode" to true,
            "shooterMode" to false,
            "disableMouseInput" to true,
            "gestureConfig" to gestures,
            "extra.gyroMode" to "0",
            "extra.screenEffectsGamma" to "1.2",
        )
        assertEquals(overrides, ContainerBackup.decode(ContainerBackup.encode(overrides)))
    }

    @Test
    fun `decode drops unknown keys and wrongly typed values and survives broken text`() {
        val text = """{"shooterMode":"yes","touchscreenMode":true,"screenSize":"1x1","extra.drives":"X","extra.gyroMode":5}"""
        assertEquals(mapOf<String, Any>("touchscreenMode" to true), ContainerBackup.decode(text))
        assertTrue(ContainerBackup.decode("{kaputt").isEmpty())
        assertTrue(ContainerBackup.decode("").isEmpty())
        assertTrue(ContainerBackup.decode(null).isEmpty())
    }

    @Test
    fun `extra helpers split the prefix`() {
        assertTrue(ContainerBackup.isExtra("extra.gyroMode"))
        assertFalse(ContainerBackup.isExtra("gyroMode"))
        assertEquals("gyroMode", ContainerBackup.extraName("extra.gyroMode"))
    }

    @Test
    fun `container values go through the zip and back`() {
        val gestures = """{"holdLeftDelay":300}"""
        val settings = listOf(
            setting("touchscreenMode", true),
            setting("shooterMode", false),
            setting("disableMouseInput", false),
            setting("gestureConfig", gestures),
            setting("extra.gyroSensitivity", "1.5"),
            BackupSetting(BackupAllowlist.find(BackupAllowlist.DATASTORE, "show_fps")!!, true),
            BackupSetting(BackupAllowlist.find(BackupAllowlist.FILE_MAIN, "touch_layout_name")!!, "Touch (Urlaub)"),
        )
        val bytes = BackupFormat.pack(manifest(settings.size), emptyList(), settings)
        val result = BackupFormat.unpack(bytes) as UnpackResult.Ok
        assertEquals(0, result.backup.skipped)
        val got = result.backup.settings.associate { (it.spec.file to it.spec.key) to it.value }
        for (s in settings) assertEquals("${s.spec.file}/${s.spec.key}", s.value, got[s.spec.file to s.spec.key])
        assertEquals(
            mapOf<String, Any>(
                "touchscreenMode" to true, "shooterMode" to false, "disableMouseInput" to false,
                "gestureConfig" to gestures, "extra.gyroSensitivity" to "1.5",
            ),
            ContainerBackup.overridesFrom(result.backup.settings),
        )
    }
}
