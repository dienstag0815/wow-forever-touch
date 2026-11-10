package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Der dunkle Textbalken am unteren Rand von Hero und Kacheln: Titel und Zeile stehen nie direkt auf dem Bild, sondern auf einem
 * Verlauf von klar (oben) über 72 % (unter dem Auslauf, dort beginnt der Text) bis 80 % (unten) in dunklem Braun der Palette.
 * Die Höhe richtet sich nach dem Inhalt. Der Kontrast der Schrift gegen den schwächsten Balkenwert über hellstem Bild prüft
 * LauncherStyleTest (LauncherContrast.barPairs).
 */

/** Leichter Schatten der Schrift auf Bildern, zusätzlich zum Balken. */
internal val ImageTextShadow = Shadow(color = LauncherColors.TextShadowSoft, offset = Offset(0f, 1f), blurRadius = 4f)

/** Textstil mit [ImageTextShadow]; Größe, Gewicht und Farbe setzt der Aufrufer am Text. */
internal val ImageTextStyle = TextStyle(shadow = ImageTextShadow)

/**
 * Balken am unteren Rand: [content] steht in einer Spalte, links und rechts mit [horizontalPad] Abstand, unten mit [bottomPad]; oben bleibt
 * der Auslauf ([LauncherStyle.BAR_FADE_DP]) frei, damit der Text auf dem 72-%-Wert beginnt. Wird in einem Box-Inhalt mit
 * `Modifier.align(Alignment.BottomStart)` an den unteren Rand gesetzt.
 */
@Composable
internal fun ImageTextBar(
    modifier: Modifier = Modifier,
    horizontalPad: Dp = 12.dp,
    bottomPad: Dp = 10.dp,
    gap: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawTextBar() }
            .padding(start = horizontalPad, end = horizontalPad, top = LauncherStyle.BAR_FADE_DP.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

private fun DrawScope.drawTextBar() {
    val height = size.height
    if (height <= 0f) return
    val stop = (LauncherStyle.BAR_FADE_DP.dp.toPx() / height).coerceIn(0.02f, 0.98f)
    drawRect(
        Brush.verticalGradient(
            0f to LauncherColors.BarClear,
            stop to LauncherColors.BarTextZone,
            1f to LauncherColors.BarBottom,
            startY = 0f,
            endY = height,
        ),
    )
}
