package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameConfigTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun configWtf(root: File) = File(root, "${WowClientDownloader.FLAVOR_DIR}/WTF/Config.wtf")

    private fun portals(root: File) = configWtf(root).readLines().filter { it.startsWith("SET portal ") }

    private fun install(branch: String, config: String? = null): File {
        val root = tempFolder.newFolder()
        File(root, WowClientDownloader.BUILD_INFO).writeText(
            "Branch!STRING:0|Product!STRING:0\n$branch|${WowClientDownloader.TARGET_PRODUCT}\n",
        )
        config?.let { configWtf(root).apply { parentFile?.mkdirs() }.writeText(it) }
        return root
    }

    @Test
    fun noRegionPicked_newConfig_usesInstallRegion() {
        val root = install("eu")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(listOf("SET portal \"EU\""), portals(root))
    }

    @Test
    fun noRegionPicked_keepsExistingPortal() {
        val root = install("us", "SET portal \"EU\"\n")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(listOf("SET portal \"EU\""), portals(root))
    }

    @Test
    fun pickedRegion_replacesExistingPortal() {
        val root = install("us", "SET portal \"US\"\nSET gxApi \"D3D11\"\n")
        ensureGameConfig(root, "1920x1080", GameRegion.EU)
        assertEquals(listOf("SET portal \"EU\""), portals(root))
    }

    private fun fpsLines(root: File) = configWtf(root).readLines().filter { it.startsWith("SET maxFPS") }

    @Test
    fun fps_defaultsTo60WithBackground8() {
        val root = install("us")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(setOf("SET maxFPS \"60\"", "SET maxFPSBk \"8\""), fpsLines(root).toSet())
    }

    @Test
    fun fps_isForcedOnEveryStart() {
        val root = install("us", "SET maxFPS \"144\"\nSET maxFPSBk \"30\"\n")
        ensureGameConfig(root, "1920x1080", null, maxFps = 30)
        assertEquals(setOf("SET maxFPS \"30\"", "SET maxFPSBk \"8\""), fpsLines(root).toSet())
        ensureGameConfig(root, "1920x1080", null, maxFps = 45)
        assertEquals(setOf("SET maxFPS \"45\"", "SET maxFPSBk \"8\""), fpsLines(root).toSet())
        assertEquals(2, fpsLines(root).size)
    }

    private fun useMaxFpsLines(root: File) = configWtf(root).readLines().filter { it.startsWith("SET useMaxFPS") }

    @Test
    fun fps_useMaxFpsFlagsAreForcedOn() {
        val root = install("us", "SET useMaxFPS \"0\"\nSET useMaxFPSBk \"0\"\n")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(setOf("SET useMaxFPS \"1\"", "SET useMaxFPSBk \"1\""), useMaxFpsLines(root).toSet())
        assertEquals(2, useMaxFpsLines(root).size)
    }

    @Test
    fun fpsLimitSteps_are60_45_30() {
        assertEquals(listOf(60, 45, 30), FpsLimit.entries.map { it.fps })
    }

    @Test
    fun resolution_alwaysMatchesContainer() {
        val root = install("us", "SET gxFullscreenResolution \"1280x720\"\nSET gxWindowedResolution \"953x720\"\n")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(
            listOf("SET gxFullscreenResolution \"1920x1080\"", "SET gxWindowedResolution \"1920x1080\""),
            configWtf(root).readLines().filter { it.contains("Resolution ") },
        )
    }

    private fun styleLines(root: File) = configWtf(root).readLines().filter { it.startsWith("SET InputDeviceInterfaceStyle") }

    @Test
    fun interfaceStyleNull_firstBootGamepad_thenPlayersChoiceStays() {
        val root = install("us")
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(listOf("SET InputDeviceInterfaceStyle \"1\""), styleLines(root))
        configWtf(root).writeText(configWtf(root).readText().replace("InputDeviceInterfaceStyle \"1\"", "InputDeviceInterfaceStyle \"0\""))
        ensureGameConfig(root, "1920x1080", null)
        assertEquals(listOf("SET InputDeviceInterfaceStyle \"0\""), styleLines(root))
    }

    @Test
    fun interfaceStyleMouse_isForcedOverGamepadDefault() {
        val root = install("us")
        ensureGameConfig(root, "1920x1080", null)
        ensureGameConfig(root, "1920x1080", null, interfaceStyle = WowTouch.STYLE_MOUSE)
        assertEquals(listOf("SET InputDeviceInterfaceStyle \"0\""), styleLines(root))
        ensureGameConfig(root, "1920x1080", null, interfaceStyle = WowTouch.STYLE_GAMEPAD)
        assertEquals(listOf("SET InputDeviceInterfaceStyle \"1\""), styleLines(root))
    }
}
