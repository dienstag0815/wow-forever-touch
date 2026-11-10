package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.screen.wow.AutoStartLogic
import app.gamenative.ui.screen.wow.AutoStartUi

internal fun PillTone.color(): Color = when (this) {
    PillTone.Ok -> LauncherColors.Ok
    PillTone.Warn -> LauncherColors.Warn
    PillTone.Error -> LauncherColors.Err
    PillTone.Info -> LauncherColors.Info
    PillTone.Muted -> LauncherColors.Muted
}

// --- Kopf ---

@Composable
internal fun HomeHeader(
    edition: String,
    pill: PillUi,
    compact: Boolean,
    art: LauncherArt,
    density: HomeDensity = HomeDensity.Roomy,
    onPill: () -> Unit,
) {
    val tight = density == HomeDensity.Tight
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        art.Crest(Modifier.size(if (compact) 30.dp else if (tight) 32.dp else 36.dp))
        Column(modifier = Modifier.weight(1f)) {
            LauncherTitle(
                text = LauncherTexts.WORDMARK,
                fontSize = if (compact) 19.sp else if (tight) 21.sp else 23.sp,
                maxLines = 1,
            )
            Text(
                text = LauncherTexts.UNOFFICIAL_TAG.asString().let { tag -> if (compact) "$tag · $edition" else "$tag · $edition Edition" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = LauncherColors.TextHead,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        StatusPill(pill, compact, onPill)
    }
}

/** Statuspille als Medaillon: ein Edelstein mit Goldring in der Farbe des Zustands, der Text daneben; ein Tipp öffnet "Hilfe und Diagnose". */
@Composable
internal fun StatusPill(pill: PillUi, compact: Boolean, onClick: () -> Unit) {
    val color = pill.tone.color()
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(LauncherColors.StoneDark.copy(alpha = 0.7f))
            .border(1.dp, LauncherColors.Bronze, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusGem(color, diameter = 18.dp)
        Text(
            text = (if (compact) pill.shortText else pill.text).asString(),
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

// --- Kachel ---

/**
 * Kachel der Startseite: Bild (oder Linienmotiv) vollflächig, leicht abgedunkelt und entsättigt, unten der Textbalken mit Titel
 * (Cinzel Bold in Versalien wie der Hero, 23 sp; zugeklappt 20, kurze Kachel 17, lange Titel passen sich bis 15 sp an; bis zwei Zeilen) und Zeile (15 sp, kurz 13). Rahmen: eine dünne, gedämpfte Goldlinie mit
 * 14 dp Rundung; Warnung und Fehler färben die Linie, gedrückt oder fokussiert wird sie hell.
 */
@Composable
internal fun HomeTile(
    tile: TileUi,
    art: LauncherArt,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    short: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bright = pressed || focused
    val line = when {
        tile.tone == TileTone.Bad -> LauncherColors.Err
        tile.tone == TileTone.Warn -> LauncherColors.Warn
        bright -> LauncherColors.GoldBright
        else -> LauncherColors.GoldMuted
    }
    val pad = if (short) 10.dp else if (compact) 12.dp else 16.dp
    val hasImage = art.hasTileImage(tile.tile)
    val titleStartSp = tileTitleSp(compact, short)
    val lineSp = tileLineSp(short)
    // Breite hier lesen (Wurzel ist BoxWithConstraints), damit der Titel auf die Kachelbreite passt.
    BoxWithConstraints(
        modifier = modifier
            .alpha(if (tile.locked) 0.6f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .softFrame(
                fill = if (pressed) LauncherColors.SurfacePressed else LauncherColors.Surface,
                line = line,
            )
            .then(if (focused) Modifier.border(2.dp, LauncherColors.FocusRing, CardShape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !tile.locked,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        if (hasImage) {
            // Kartenbild vollflächig; die Entsättigung macht das Bild selbst, hier kommt die Abdunkelung (20 %) darüber, der Balken unten trägt die Schrift.
            art.TileBackdrop(tile.tile, Modifier.matchParentSize())
            Box(modifier = Modifier.matchParentSize().background(LauncherColors.TileDim))
        } else {
            art.TileIcon(
                tile.tile,
                LauncherColors.Gold.copy(alpha = if (bright) 0.42f else 0.30f),
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (short) 8.dp else if (compact) 12.dp else 16.dp)
                    .fillMaxHeight(if (short) 0.45f else 0.55f)
                    .aspectRatio(1f),
            )
        }
        ImageTextBar(
            modifier = Modifier.align(Alignment.BottomStart),
            horizontalPad = pad,
            bottomPad = if (short) 8.dp else 12.dp,
            gap = 2.dp,
        ) {
            val innerWidthDp = this@BoxWithConstraints.maxWidth.value - 2f * pad.value
            val titleText = tile.title.asString().uppercase()
            val titleSize = fitTitleSp(titleText, innerWidthDp, titleStartSp, TILE_TITLE_MIN_SP)
            Text(
                text = titleText,
                fontSize = titleSize.sp,
                lineHeight = (titleSize * 1.15f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LauncherFonts.Cinzel,
                letterSpacing = 0.03.em,
                color = LauncherColors.TextBright,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = ImageTextStyle,
            )
            Text(
                text = tile.subtitle.asString(),
                fontSize = lineSp.sp,
                lineHeight = (lineSp * 1.28f).sp,
                fontWeight = FontWeight.Medium,
                color = LauncherColors.TextBarSub,
                // Zugeklappt (Außendisplay) bleibt die Zeile einzeilig; eine zweite Zeile würde die Kachel zerreißen.
                maxLines = if (short || compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                style = ImageTextStyle,
            )
        }
        tile.badge?.let { badge ->
            Text(
                text = badge.asString(),
                fontSize = HOME_MIN_SP.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LauncherColors.Ink,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (short || compact) 10.dp else 12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(LauncherColors.GoldBright)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

// --- Hero ---

/**
 * Hero der Startseite: das Bild füllt den Platz (Zuschnitt auf den Fokus, siehe [LauncherArt.Hero]), unten der Textbalken mit Titel
 * (Cinzel Bold in Versalien, 40 bis 48 sp aufgeklappt), Zeile (15 bis 16 sp), Anmelde-Chip, Hauptknopf und Countdown. Rahmen wie die Kacheln.
 */
@Composable
internal fun HeroCard(
    hero: HeroUi,
    hasSavedLogin: Boolean,
    autoUi: AutoStartUi,
    compact: Boolean,
    art: LauncherArt,
    onAction: (HeroAction) -> Unit,
    onLogin: () -> Unit,
    onCancelCountdown: () -> Unit,
    modifier: Modifier = Modifier,
    density: HomeDensity = HomeDensity.Roomy,
) {
    val tight = density == HomeDensity.Tight
    val roomy = density == HomeDensity.Roomy
    val heroPad = if (compact) (if (tight) 14.dp else if (roomy) 20.dp else 16.dp) else (if (tight) 16.dp else if (roomy) 24.dp else 20.dp)
    val heroGap = if (tight) 6.dp else if (compact) 8.dp else 10.dp
    BoxWithConstraints(
        modifier = modifier.softFrame(fill = LauncherColors.StoneDark),
    ) {
        // Breite hier lesen: in der Column ist der Empfänger von BoxWithConstraints nicht mehr implizit (Falle aus fold8.20).
        val innerWidthDp = maxWidth.value - 2f * heroPad.value
        art.Hero(Modifier.matchParentSize())
        ImageTextBar(
            modifier = Modifier.align(Alignment.BottomStart),
            horizontalPad = heroPad,
            bottomPad = heroPad,
            gap = heroGap,
        ) {
            val titleText = hero.title.asString().uppercase()
            val titleSize = fitTitleSp(titleText, innerWidthDp, heroTitleSp(compact, density), HERO_TITLE_MIN_SP)
            Text(
                text = titleText,
                fontSize = titleSize.sp,
                lineHeight = (titleSize * 1.15f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LauncherFonts.Cinzel,
                letterSpacing = 0.03.em,
                color = LauncherColors.GoldBright,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = ImageTextStyle,
            )
            val lineSp = heroLineSp(compact, density)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hero.showSpinner) {
                    CircularProgressIndicator(color = LauncherColors.Gold, strokeWidth = 3.dp, modifier = Modifier.size(if (tight) 22.dp else 28.dp))
                }
                Text(
                    text = hero.line.asString(),
                    fontSize = lineSp.sp,
                    fontWeight = FontWeight.Medium,
                    color = LauncherColors.TextBarSub,
                    lineHeight = (lineSp * 1.35f).sp,
                    maxLines = if (tight || (compact && !roomy)) 3 else if (compact) 4 else 5,
                    overflow = TextOverflow.Ellipsis,
                    style = ImageTextStyle,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (hero.showLogin) {
                LoginChip(hasSavedLogin, onLogin)
            }
            hero.primary?.let { PlayButton(it, compact, density) { onAction(it.action) } }
            hero.secondary?.let { sec ->
                TextButton(onClick = { onAction(sec.action) }, enabled = sec.enabled) {
                    Text(
                        text = sec.text.asString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (sec.danger) LauncherColors.Err else LauncherColors.TextBarSub,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }
            if (hero.showCountdown) {
                CountdownLine(autoUi, onCancelCountdown)
            }
        }
    }
}

/** Anmeldung als Chip im Hero: ein Tipp richtet sie ein oder ändert sie (Vergessen steht auf der Seite "Spieldateien"). */
@Composable
private fun LoginChip(hasSavedLogin: Boolean, onClick: () -> Unit) {
    val color = if (hasSavedLogin) LauncherColors.Ok else LauncherColors.Gold
    Box(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(LauncherColors.StoneDark.copy(alpha = 0.78f))
            .border(1.dp, LauncherColors.Bronze, RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = loginChipText(hasSavedLogin).asString(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/**
 * Die große Hauptschaltfläche: karmesinroter Verlauf, Goldrand mit Glanzlinie oben, Versalien in Hellgold und ein leichter Schein
 * dahinter. Gedrückt wird sie heller. Gesperrt ist sie dunkelgrau mit Bronzerand und ohne Schein. Alle Hero-Zustände (Spielen,
 * Installieren, Update ...) benutzen dieselbe Form.
 */
@Composable
internal fun PlayButton(button: HeroButton, compact: Boolean, density: HomeDensity = HomeDensity.Roomy, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val enabled = button.enabled
    val fill: Brush = when {
        !enabled -> SolidColor(LauncherColors.DisabledFill)
        pressed -> Brush.verticalGradient(listOf(LauncherColors.Crimson, LauncherColors.Crimson))
        else -> Brush.verticalGradient(listOf(LauncherColors.Crimson, LauncherColors.CrimsonDark))
    }
    Box(
        modifier = Modifier
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = 240.dp))
            .heightIn(min = if (density == HomeDensity.Tight) 46.dp else if (density == HomeDensity.Normal) 50.dp else 56.dp)
            .drawBehind {
                if (enabled) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(LauncherColors.Crimson.copy(alpha = if (pressed) 0.35f else 0.22f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.75f,
                        ),
                        topLeft = Offset(-size.width * 0.2f, -size.height * 0.5f),
                        size = Size(size.width * 1.4f, size.height * 2f),
                    )
                }
            }
            .clip(shape)
            .background(fill)
            .drawWithContent {
                drawContent()
                val edge = 2.dp.toPx()
                val rim: Brush = if (enabled) {
                    Brush.verticalGradient(listOf(LauncherColors.Gold, LauncherColors.Bronze))
                } else {
                    SolidColor(LauncherColors.Bronze)
                }
                drawRoundRect(
                    brush = rim,
                    topLeft = Offset(edge / 2f, edge / 2f),
                    size = Size(size.width - edge, size.height - edge),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = edge),
                )
                if (enabled) {
                    val y = 4.dp.toPx()
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, LauncherColors.Gold.copy(alpha = 0.45f), Color.Transparent)),
                        start = Offset(10.dp.toPx(), y),
                        end = Offset(size.width - 10.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 18.dp else 26.dp, vertical = if (density == HomeDensity.Tight) 8.dp else 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = button.text.asString().uppercase(),
            fontSize = if (compact) 16.sp else 19.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LauncherFonts.Cinzel,
            letterSpacing = 0.1.em,
            color = if (enabled) LauncherColors.GoldBright else LauncherColors.DisabledText,
            textAlign = TextAlign.Center,
            style = if (enabled) {
                TextStyle(shadow = Shadow(color = LauncherColors.TextShadowSoft, offset = Offset(0f, 1f), blurRadius = 3f))
            } else {
                TextStyle.Default
            },
        )
    }
}

/** Unter dem Spielen-Knopf: Countdown mit Fortschrittsbalken und "Abbrechen", nach einem Stopp höchstens eine Erklärzeile. */
@Composable
internal fun CountdownLine(ui: AutoStartUi, onCancel: () -> Unit) {
    when (ui) {
        AutoStartUi.Off -> Unit
        is AutoStartUi.Stopped -> {
            val text = AutoStartLogic.blockText(ui.reason) ?: return
            Text(text = text, fontSize = 13.sp, color = LauncherColors.TextBarSub, lineHeight = 18.sp, style = ImageTextStyle)
        }
        is AutoStartUi.Counting -> {
            Column(modifier = Modifier.widthIn(max = 420.dp)) {
                LinearProgressIndicator(
                    progress = { if (ui.waitingForCheck) 1f else 1f - ui.secondsLeft.toFloat() / ui.total.coerceAtLeast(1) },
                    color = LauncherColors.Gold,
                    trackColor = LauncherColors.SurfaceRaised,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = AutoStartLogic.countdownText(ui.secondsLeft, ui.waitingForCheck),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = LauncherColors.GoldBright,
                    )
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.launcher_cancel), fontSize = 15.sp, color = LauncherColors.TextBarSub) }
                }
            }
        }
    }
}

// --- Banner und Fuß ---

/** Roter Streifen unter dem Kopf: die Fehlermeldung des Starts oder der Installation, mit Weg zur Hilfe. */
@Composable
internal fun ErrorBanner(message: String, onHelp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .ornateFrame(
                fill = LauncherColors.ErrFill.copy(alpha = 0.92f),
                outer = LauncherColors.Err.copy(alpha = 0.75f),
                inner = LauncherColors.BadBorder,
                corners = false,
            )
            .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.launcher_error_prefix, message),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = LauncherColors.ErrText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
        )
        TextButton(onClick = onHelp) { Text(stringResource(R.string.launcher_help), fontSize = 14.sp, color = LauncherColors.Gold) }
    }
}

/**
 * Fußzeile in einer Zeile: Version, Lizenzen, Quellcode, Lizenzname. Aufgeklappt steht dazu der Hinweis "Inoffizielles Fan-Projekt"
 * (zugeklappt fehlt der Platz; dort trägt der Kopf "Inoffiziell" und die Seite "Über" den vollen Satz).
 */
@Composable
internal fun HomeFooter(appVersion: String, compact: Boolean, onLicenses: () -> Unit, onSource: () -> Unit) {
    val style = HOME_MIN_SP.sp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.launcher_footer_app, appVersion),
            fontSize = style,
            color = LauncherColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        FooterLink(LauncherTexts.FOOTER_LICENSES.asString(), onLicenses)
        FooterLink(LauncherTexts.FOOTER_SOURCE.asString(), onSource)
        if (compact) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Text(
                text = LauncherTexts.UNOFFICIAL_NOTICE.asString(),
                fontSize = style,
                color = LauncherColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(LauncherTexts.LICENSE_NAME, fontSize = style, color = LauncherColors.Muted, maxLines = 1)
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = HOME_MIN_SP.sp,
            color = LauncherColors.Gold,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
        )
    }
}

// --- Knöpfe der Unterseiten ---

@Composable
internal fun PagePrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    PlayButton(HeroButton(UiText.raw(text), HeroAction.Play, enabled), compact = true, onClick = onClick)
}

@Composable
internal fun PageSecondaryButton(text: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = FrameShape,
        border = BorderStroke(1.dp, if (danger) LauncherColors.BadBorder else LauncherColors.Bronze),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = LauncherColors.SurfaceRaised),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) LauncherColors.Err else LauncherColors.Gold,
        )
    }
}

/** Kleiner Platzhalter, damit Seiten Abstände mit einer Zeile setzen können. */
@Composable
internal fun Gap(height: Int) {
    Spacer(modifier = Modifier.height(height.dp).width(1.dp))
}
