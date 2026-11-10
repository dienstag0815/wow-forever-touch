package app.gamenative.ui.screen.wow

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import app.gamenative.ui.components.AppText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.gamenative.ui.touchstyle.TouchStyle
import app.gamenative.ui.touchstyle.TouchStyleMath
import app.gamenative.ui.touchstyle.TouchStyleSettings

/**
 * Zeilen der Einstellungsseite (Gruppe Steuerung): Aussehen der Touch-Elemente (Aktuell, F Classic ruhig, G Classic ruhig plus
 * Touch-Muster) und Kontrast. Dieselben Werte wie im Seitenmenü des Spiels; sie wirken sofort, auch im laufenden Spiel.
 */
@Composable
internal fun TouchLookSettingsBlock(enabled: Boolean) {
    val context = LocalContext.current
    var style by remember {
        TouchStyleSettings.init(context)
        mutableStateOf(TouchStyleSettings.style)
    }
    var contrast by remember { mutableStateOf(TouchStyleSettings.contrast) }
    Column {
        SettingChoiceRow(
            title = stringResource(R.string.game_look_title),
            value = stringResource(style.labelRes),
            options = TouchStyle.entries,
            selected = style,
            optionLabel = { AppText.get(it.labelRes) },
            description = stringResource(R.string.game_look_desc),
            enabled = enabled,
            onSelect = { next ->
                TouchStyleSettings.setStyle(context, next)
                style = next
            },
        )
        SettingChoiceRow(
            title = stringResource(R.string.game_look_contrast),
            value = TouchStyleMath.contrastLabel(contrast),
            options = TouchStyleMath.CONTRAST_STEPS,
            selected = TouchStyleMath.CONTRAST_STEPS.firstOrNull { kotlin.math.abs(it - contrast) < 0.001f } ?: contrast,
            optionLabel = { TouchStyleMath.contrastLabel(it) },
            description = stringResource(R.string.game_look_contrast_desc),
            enabled = enabled,
            onSelect = { next ->
                TouchStyleSettings.setContrast(context, next)
                contrast = next
            },
        )
    }
}
