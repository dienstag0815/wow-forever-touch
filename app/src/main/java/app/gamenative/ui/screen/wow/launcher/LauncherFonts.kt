package app.gamenative.ui.screen.wow.launcher

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.gamenative.R

/**
 * Schrift der Überschriften: Cinzel (SIL Open Font License 1.1, Lizenztext in assets/licenses/Cinzel-OFL.txt), eine Variable-Font-Datei
 * mit Gewichtsachse 400 bis 900. Eingesetzt für Wortmarke, Seitentitel, Hero-Titel, Kacheltitel und den Spielen-Knopf; Fließtext bleibt
 * in der Systemschrift (Lesbarkeit). Jedes Gewicht ist ein eigener Eintrag mit eigener Achseneinstellung, damit Compose das richtige
 * Gewicht aus derselben Datei schneidet.
 */
internal object LauncherFonts {
    @OptIn(ExperimentalTextApi::class)
    private fun cinzel(weight: Int): Font = Font(
        resId = R.font.cinzel_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val Cinzel: FontFamily by lazy {
        FontFamily(cinzel(400), cinzel(500), cinzel(600), cinzel(700), cinzel(800), cinzel(900))
    }
}
