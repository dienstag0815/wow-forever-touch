package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colors of the controls editor (top bar, settings sheet and its dialogs): a calm dark slate with a muted bronze/gold accent that
 * matches the touch style "Classic ruhig + Touch-Muster" (G). The values are the ones of `TouchStyleMath` (G_FILL, G_BRONZE,
 * G_LIGHT, G_TEXT), copied on purpose so the editor does not depend on the style code. RGB without alpha. Pure Kotlin
 * (see [contrast] for the WCAG check in the tests).
 */
object EditorPalette {
    /** Slate (the stone color of the style): sheet and bars. */
    const val SURFACE = 0x1C1A17

    /** Rows, cards and chips on the sheet. */
    const val SURFACE_VARIANT = 0x2A2723

    /** Menus and dialogs (a step lighter than the sheet). */
    const val SURFACE_HIGH = 0x34302A

    /** Text on the surfaces (the parchment text of the style). */
    const val ON_SURFACE = 0xE8DCC0

    /** Secondary text. */
    const val ON_VARIANT = 0xB9AD96

    /** Accent (light bronze of the style): section titles, switches, sliders, the main button. */
    const val PRIMARY = 0xC9A15A
    const val ON_PRIMARY = 0x1C1A17

    /** Selected chip and tile. */
    const val PRIMARY_CONTAINER = 0x4A3B22
    const val ON_PRIMARY_CONTAINER = 0xF1E4C3

    /** Borders of fields and chips (bronze, lighter than the style's frame so it stays visible as a 3:1 line). */
    const val OUTLINE = 0x9A7A48
    const val OUTLINE_VARIANT = 0x4A4338

    const val ERROR = 0xE57373

    /** "Fertig": muted green with white text. */
    const val DONE = 0x3F6B45

    /** Opacity of the sheet and the bars over the game picture. */
    const val SURFACE_ALPHA = 0.92f

    /** WCAG contrast ratio of two RGB colors (1 to 21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [foreground] (RGB) with [alpha] over [background] (RGB), as RGB. */
    fun over(foreground: Int, alpha: Float, background: Int): Int {
        fun channel(shift: Int): Int {
            val f = (foreground shr shift) and 0xff
            val b = (background shr shift) and 0xff
            return (f * alpha + b * (1f - alpha)).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun luminance(rgb: Int): Double {
        fun linear(shift: Int): Double {
            val c = ((rgb shr shift) and 0xff) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }
}

internal fun editorColor(rgb: Int, alpha: Float = 1f): Color = Color(
    red = ((rgb shr 16) and 0xff) / 255f,
    green = ((rgb shr 8) and 0xff) / 255f,
    blue = (rgb and 0xff) / 255f,
    alpha = alpha,
)

/** Color of the bars and the sheet over the game picture. */
internal val EditorBarColor: Color get() = editorColor(EditorPalette.SURFACE, EditorPalette.SURFACE_ALPHA)

/** Wraps the editor in its calm dark color scheme; typography and shapes stay those of the app. */
@Composable
fun EditorTheme(content: @Composable () -> Unit) {
    val surface = editorColor(EditorPalette.SURFACE)
    val colors = darkColorScheme(
        primary = editorColor(EditorPalette.PRIMARY),
        onPrimary = editorColor(EditorPalette.ON_PRIMARY),
        primaryContainer = editorColor(EditorPalette.PRIMARY_CONTAINER),
        onPrimaryContainer = editorColor(EditorPalette.ON_PRIMARY_CONTAINER),
        inversePrimary = editorColor(EditorPalette.PRIMARY_CONTAINER),
        secondary = editorColor(EditorPalette.PRIMARY),
        onSecondary = editorColor(EditorPalette.ON_PRIMARY),
        secondaryContainer = editorColor(EditorPalette.PRIMARY_CONTAINER),
        onSecondaryContainer = editorColor(EditorPalette.ON_PRIMARY_CONTAINER),
        tertiary = editorColor(EditorPalette.PRIMARY),
        onTertiary = editorColor(EditorPalette.ON_PRIMARY),
        tertiaryContainer = editorColor(EditorPalette.PRIMARY_CONTAINER),
        onTertiaryContainer = editorColor(EditorPalette.ON_PRIMARY_CONTAINER),
        background = surface,
        onBackground = editorColor(EditorPalette.ON_SURFACE),
        surface = surface,
        onSurface = editorColor(EditorPalette.ON_SURFACE),
        surfaceVariant = editorColor(EditorPalette.SURFACE_VARIANT),
        onSurfaceVariant = editorColor(EditorPalette.ON_VARIANT),
        surfaceTint = Color.Transparent,
        inverseSurface = editorColor(EditorPalette.ON_SURFACE),
        inverseOnSurface = surface,
        error = editorColor(EditorPalette.ERROR),
        onError = editorColor(EditorPalette.ON_PRIMARY),
        errorContainer = editorColor(0x5C2B2B),
        onErrorContainer = editorColor(0xFFDAD6),
        outline = editorColor(EditorPalette.OUTLINE),
        outlineVariant = editorColor(EditorPalette.OUTLINE_VARIANT),
        scrim = Color.Black,
        surfaceBright = editorColor(EditorPalette.SURFACE_HIGH),
        surfaceDim = surface,
        surfaceContainer = editorColor(EditorPalette.SURFACE_VARIANT),
        surfaceContainerHigh = editorColor(EditorPalette.SURFACE_HIGH),
        surfaceContainerHighest = editorColor(EditorPalette.SURFACE_HIGH),
        surfaceContainerLow = surface,
        surfaceContainerLowest = surface,
    )
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
