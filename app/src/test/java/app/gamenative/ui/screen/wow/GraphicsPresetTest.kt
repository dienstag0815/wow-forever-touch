package app.gamenative.ui.screen.wow

import app.gamenative.TestTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GraphicsPresetTest {
    @get:Rule
    val texts = TestTexts.rule("de")


    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun configWtf(root: File) = File(root, "${WowClientDownloader.FLAVOR_DIR}/WTF/Config.wtf")

    private fun install(config: String? = null): File {
        val root = tempFolder.newFolder()
        File(root, WowClientDownloader.BUILD_INFO).writeText(
            "Branch!STRING:0|Product!STRING:0\nus|${WowClientDownloader.TARGET_PRODUCT}\n",
        )
        config?.let { configWtf(root).apply { parentFile?.mkdirs() }.writeText(it) }
        return root
    }

    private fun lines(root: File) = configWtf(root).readLines()

    @Test
    fun custom_hasNoCvars() {
        assertTrue(graphicsCvars(GraphicsPreset.CUSTOM).isEmpty())
    }

    @Test
    fun defaultIsBalanced() {
        assertEquals(GraphicsPreset.BALANCED, GraphicsPreset.DEFAULT)
    }

    @Test
    fun allPresetsWriteTheSameKeys() {
        val keys = graphicsCvars(GraphicsPreset.BALANCED).keys
        assertTrue(keys.isNotEmpty())
        assertEquals(keys, graphicsCvars(GraphicsPreset.BATTERY).keys)
        assertEquals(keys, graphicsCvars(GraphicsPreset.BEAUTIFUL).keys)
    }

    @Test
    fun renderScale_batteryBalancedBeautiful() {
        assertEquals("0.6", graphicsCvars(GraphicsPreset.BATTERY).getValue("RenderScale"))
        assertEquals("0.75", graphicsCvars(GraphicsPreset.BALANCED).getValue("RenderScale"))
        assertEquals("1", graphicsCvars(GraphicsPreset.BEAUTIFUL).getValue("RenderScale"))
    }

    @Test
    fun presetsGetHeavierFromBatteryToBeautiful() {
        val battery = graphicsCvars(GraphicsPreset.BATTERY)
        val balanced = graphicsCvars(GraphicsPreset.BALANCED)
        val beautiful = graphicsCvars(GraphicsPreset.BEAUTIFUL)
        for (key in listOf("graphicsShadowQuality", "graphicsViewDistance", "graphicsSSAO", "graphicsParticleDensity")) {
            assertTrue(key, battery.getValue(key).toInt() <= balanced.getValue(key).toInt())
            assertTrue(key, balanced.getValue(key).toInt() <= beautiful.getValue(key).toInt())
        }
        assertTrue(battery.getValue("RenderScale").toFloat() < balanced.getValue("RenderScale").toFloat())
        assertTrue(balanced.getValue("RenderScale").toFloat() < beautiful.getValue("RenderScale").toFloat())
    }

    @Test
    fun raidVariantsMatchTheNormalOnes() {
        for (preset in GraphicsPreset.entries) {
            val cvars = graphicsCvars(preset)
            assertEquals(cvars["graphicsShadowQuality"], cvars["raidGraphicsShadowQuality"])
            assertEquals(cvars["graphicsViewDistance"], cvars["raidGraphicsViewDistance"])
            assertEquals(cvars["graphicsSSAO"], cvars["raidGraphicsSSAO"])
            assertEquals(cvars["graphicsParticleDensity"], cvars["raidGraphicsParticleDensity"])
        }
    }

    @Test
    fun custom_leavesTheGraphicsLinesOfConfigAlone() {
        val root = install("SET RenderScale \"0.9\"\nSET graphicsSSAO \"3\"\n")
        ensureGameConfig(root, "1920x1080", null, graphics = graphicsCvars(GraphicsPreset.CUSTOM))
        assertTrue(lines(root).contains("SET RenderScale \"0.9\""))
        assertTrue(lines(root).contains("SET graphicsSSAO \"3\""))
    }

    @Test
    fun preset_isForcedOverExistingValues() {
        val root = install("SET RenderScale \"0.9\"\nSET graphicsSSAO \"3\"\nSET somethingElse \"1\"\n")
        ensureGameConfig(root, "1920x1080", null, graphics = graphicsCvars(GraphicsPreset.BATTERY))
        val written = lines(root)
        assertEquals(1, written.count { it.startsWith("SET RenderScale ") })
        assertTrue(written.contains("SET RenderScale \"0.6\""))
        assertTrue(written.contains("SET graphicsSSAO \"0\""))
        assertTrue(written.contains("SET raidGraphicsSSAO \"0\""))
        assertTrue(written.contains("SET somethingElse \"1\""))
    }

    @Test
    fun preset_onFirstBootOverridesTheDefaults() {
        val root = install()
        ensureGameConfig(root, "1920x1080", null, graphics = graphicsCvars(GraphicsPreset.BALANCED))
        val written = lines(root)
        assertEquals(1, written.count { it.startsWith("SET RenderScale ") })
        assertTrue(written.contains("SET RenderScale \"0.75\""))
        assertEquals(1, written.count { it.startsWith("SET graphicsShadowQuality ") })
        assertTrue(written.contains("SET graphicsShadowQuality \"1\""))
    }

    @Test
    fun preset_isStableOnSecondStart() {
        val root = install()
        val cvars = graphicsCvars(GraphicsPreset.BEAUTIFUL)
        ensureGameConfig(root, "1920x1080", null, graphics = cvars)
        val first = configWtf(root).readText()
        ensureGameConfig(root, "1920x1080", null, graphics = cvars)
        assertEquals(first, configWtf(root).readText())
    }

    @Test
    fun switchingBackToCustom_keepsWhatTheLastPresetWrote() {
        val root = install()
        ensureGameConfig(root, "1920x1080", null, graphics = graphicsCvars(GraphicsPreset.BATTERY))
        ensureGameConfig(root, "1920x1080", null, graphics = graphicsCvars(GraphicsPreset.CUSTOM))
        assertTrue(lines(root).contains("SET RenderScale \"0.6\""))
    }
}
