package app.gamenative.ui.screen.wow

import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.R
import app.gamenative.ui.components.AppText

/**
 * Graphics preset of the launcher. [CUSTOM] leaves the graphics lines of Config.wtf alone (the player's own in-game
 * settings stick); the others are written to Config.wtf on every start, see [graphicsCvars].
 */
internal enum class GraphicsPreset(@StringRes val labelRes: Int) {
    CUSTOM(R.string.game_gfx_custom),
    BATTERY(R.string.game_gfx_battery),
    BALANCED(R.string.game_gfx_balanced),
    BEAUTIFUL(R.string.game_gfx_beautiful);

    /** The name in the current app language. */
    val label: String get() = AppText.get(labelRes)

    companion object {
        private const val KEY_GRAPHICS = "graphics_preset"

        val DEFAULT = BALANCED

        fun current(context: Context): GraphicsPreset =
            wowPrefs(context).getString(KEY_GRAPHICS, null)?.let { saved -> entries.firstOrNull { it.name == saved } } ?: DEFAULT

        fun save(context: Context, preset: GraphicsPreset) {
            wowPrefs(context).edit().putString(KEY_GRAPHICS, preset.name).apply()
        }
    }
}

/**
 * The Config.wtf cvars (name -> value without quotes) a preset forces on every start. Empty for [GraphicsPreset.CUSTOM].
 *
 * Only cvars the client is known to read: RenderScale, ResampleQuality, graphicsShadowQuality and raidGraphicsShadowQuality
 * already stand in the launcher's first-boot Config.wtf (CONFIG_DEFAULTS); graphicsViewDistance, graphicsSSAO and
 * graphicsParticleDensity (each with the raid variant) are the cvars of the client's own graphics page. Anti-aliasing
 * (ffxAntiAliasingMode, MSAAQuality) is left alone on purpose: MSAA is costly on a tile-based GPU and the value meaning is unverified.
 *
 * ResampleQuality: 0 = plain scaling, 3 = FidelityFX Super Resolution 1.0 (used when the 3D image is rendered below
 * full size, RenderScale < 1).
 */
internal fun graphicsCvars(preset: GraphicsPreset): Map<String, String> = when (preset) {
    GraphicsPreset.CUSTOM -> emptyMap()
    GraphicsPreset.BATTERY -> presetValues(renderScale = "0.6", resample = "3", shadow = "0", viewDistance = "1", ssao = "0", particles = "1")
    GraphicsPreset.BALANCED -> presetValues(renderScale = "0.75", resample = "3", shadow = "1", viewDistance = "4", ssao = "0", particles = "2")
    GraphicsPreset.BEAUTIFUL -> presetValues(renderScale = "1", resample = "0", shadow = "2", viewDistance = "7", ssao = "1", particles = "4")
}

private fun presetValues(
    renderScale: String,
    resample: String,
    shadow: String,
    viewDistance: String,
    ssao: String,
    particles: String,
): Map<String, String> = linkedMapOf(
    "RenderScale" to renderScale,
    "ResampleQuality" to resample,
    "graphicsShadowQuality" to shadow,
    "raidGraphicsShadowQuality" to shadow,
    "graphicsViewDistance" to viewDistance,
    "raidGraphicsViewDistance" to viewDistance,
    "graphicsSSAO" to ssao,
    "raidGraphicsSSAO" to ssao,
    "graphicsParticleDensity" to particles,
    "raidGraphicsParticleDensity" to particles,
)
