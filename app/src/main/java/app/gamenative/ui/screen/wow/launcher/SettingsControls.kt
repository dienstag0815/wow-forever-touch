package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Bausteine der Einstellungsseiten, damit man auf einen Blick sieht, was ein Knopf, ein Wert, ein Aufklapper und eine Gruppe ist:
 *  - ActionButton: jede Aktion ist ein Knopf (Fläche, Rahmen, mindestens 48 dp hoch). Hauptaktion gefüllt in Gold, Nebenaktion
 *    umrandet, gefährliche Aktion rot umrandet.
 *  - ValueChip: ein Wert, den man antippen kann (Auswahl, Stufe): umrandetes Feld mit Pfeil. Reine Werte sind ValueText (gedämpft, ohne Rahmen).
 *  - ExpandRow und InsetPanel: Klappzeile mit Pfeil; der aufgeklappte Inhalt steht in einer helleren Fläche mit Goldlinie links.
 *  - RadioCard: eine Auswahlkarte; StatusChip und StatusLine: Zustand in Farbe.
 * Die Farben je Art stehen als Rohwerte in [ActionStyle] und [ToneStyle], damit der Kontrasttest sie ohne Android rechnen kann.
 */

/** Art eines Knopfes. */
internal enum class ActionKind { Primary, Secondary, Danger }

/** Farben eines Knopfes als ARGB-Werte. */
internal data class ActionColors(val fill: Long, val border: Long, val text: Long)

internal object ActionStyle {
    /** Mindesthöhe jedes Knopfes und jeder tippbaren Zeile (Touch-Ziel). */
    const val MIN_HEIGHT_DP = 48

    fun colors(kind: ActionKind, enabled: Boolean = true, pressed: Boolean = false): ActionColors = with(LauncherPalette) {
        if (!enabled) return ActionColors(DISABLED_FILL, BRONZE_DEEP, DISABLED_TEXT)
        when (kind) {
            ActionKind.Primary -> ActionColors(if (pressed) GOLD_BRIGHT else GOLD, GOLD_BRIGHT, INK)
            ActionKind.Secondary -> ActionColors(if (pressed) SURFACE_PRESSED else SURFACE_RAISED, GOLD, GOLD_BRIGHT)
            ActionKind.Danger -> ActionColors(if (pressed) SURFACE_PRESSED else ERR_FILL, ERR, ERR_TEXT)
        }
    }
}

/** Farbe eines Zustands (Chip, Statuszeile): Fläche, Rahmen, Schrift. */
internal data class ToneColors(val fill: Long, val border: Long, val text: Long)

internal object ToneStyle {
    fun colors(tone: PillTone): ToneColors = with(LauncherPalette) {
        when (tone) {
            PillTone.Ok -> ToneColors(OK_FILL, OK_BORDER, OK)
            PillTone.Warn -> ToneColors(WARN_FILL, WARN_BORDER, WARN)
            PillTone.Error -> ToneColors(ERR_FILL, BAD_BORDER, ERR_TEXT)
            PillTone.Info -> ToneColors(INFO_FILL, INFO_BORDER, INFO)
            PillTone.Muted -> ToneColors(SURFACE_INSET, BRONZE, BODY)
        }
    }
}

private fun Long.c(): Color = Color(this)

private val ButtonShape = RoundedCornerShape(24.dp)
private val ChipShape = RoundedCornerShape(10.dp)

/**
 * Der Knopf: abgerundetes Rechteck mit Fläche und Rahmen, Schrift 15 sp halbfett, mindestens [ActionStyle.MIN_HEIGHT_DP] hoch.
 * [trailingIcon] steht hinter dem Text (zum Beispiel der Pfeil bei "Öffnen").
 */
@Composable
internal fun ActionButton(
    text: String,
    kind: ActionKind = ActionKind.Secondary,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = ActionStyle.colors(kind, enabled, pressed)
    Row(
        modifier = modifier
            .widthIn(min = 72.dp, max = 190.dp)
            .heightIn(min = ActionStyle.MIN_HEIGHT_DP.dp)
            .clip(ButtonShape)
            .background(colors.fill.c())
            .border(if (kind == ActionKind.Primary) 0.dp else 1.5.dp, colors.border.c(), ButtonShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.text.c(),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = colors.text.c(), modifier = Modifier.padding(start = 4.dp).size(20.dp))
        }
    }
}

/** Ein Wert, den man antippen kann (Auswahl, Stufe): Feld mit Rahmen und Pfeil. Das Antippen übernimmt die Zeile darum, nicht das Feld. */
@Composable
internal fun ValueChip(text: String, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(max = 190.dp)
            .clip(ChipShape)
            .background(LauncherColors.SurfaceInset)
            .border(1.dp, LauncherColors.Bronze, ChipShape)
            .alpha(if (enabled) 1f else 0.6f)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = LauncherColors.TextHead,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = LauncherColors.Gold)
    }
}

/** Reiner Wert (Version, Build): gedämpft, ohne Rahmen, nichts zum Tippen. */
@Composable
internal fun ValueText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = LauncherColors.Muted,
        textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Klappzeile: Titel (und Erklärtext), rechts ein Pfeil (▸ zu, ▾ auf). [summary] steht als Zustandschip vor dem Pfeil. Der Inhalt
 * gehört in ein [InsetPanel] direkt darunter.
 */
@Composable
internal fun ExpandRow(
    title: String,
    expanded: Boolean,
    description: String? = null,
    enabled: Boolean = true,
    summary: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .heightIn(min = ActionStyle.MIN_HEIGHT_DP.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "expanded" else "collapsed" }
            .padding(start = 14.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = LauncherColors.Body)
            if (!description.isNullOrBlank()) {
                Text(text = description, fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Muted, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (summary != null) {
            Box(modifier = Modifier.padding(start = 8.dp)) { summary() }
        }
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = LauncherColors.Gold,
            modifier = Modifier.padding(start = 4.dp).size(28.dp),
        )
    }
}

/** Aufgeklappter Inhalt: hellere Fläche, eingerückt, mit Goldlinie links; so sieht man, was zur Klappzeile darüber gehört. */
@Composable
internal fun InsetPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val accent = LauncherColors.Gold
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 10.dp, top = 2.dp, bottom = 10.dp)
            .clip(shape)
            .background(LauncherColors.SurfaceInset)
            .border(1.dp, LauncherColors.Divider, shape)
            .drawBehind { drawRect(accent, topLeft = Offset.Zero, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 15.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Auswahlkarte (Radio): Titel, Erklärtext, links der Punkt; die gewählte Karte hat Goldrahmen und hellere Fläche. */
@Composable
internal fun RadioCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .heightIn(min = ActionStyle.MIN_HEIGHT_DP.dp)
            .clip(shape)
            .background(if (selected) LauncherColors.SurfaceInset else LauncherColors.SurfaceRaised)
            .border(if (selected) 2.dp else 1.dp, if (selected) LauncherColors.Gold else LauncherColors.Bronze, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioDot(selected)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (selected) LauncherColors.GoldBright else LauncherColors.TextHead)
            Text(text = description, fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Muted, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(2.dp, if (selected) LauncherColors.Gold else LauncherColors.Muted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(LauncherColors.Gold))
    }
}

/** Kleine Pille mit Zustand (zum Beispiel die aktive Update-Quelle): Farbpunkt, Text, Rahmen in der Farbe des Zustands. */
@Composable
internal fun StatusChip(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val colors = ToneStyle.colors(tone)
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(colors.fill.c())
            .border(1.dp, colors.border.c(), shape)
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusGem(colors.text.c(), diameter = 12.dp)
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.text.c(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
    }
}

/** Ergebniszeile in Farbe (grün = geklappt, rot = Fehler im Klartext) mit Häkchen oder Warnzeichen. */
@Composable
internal fun StatusLine(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val colors = ToneStyle.colors(tone)
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.fill.c())
            .border(1.dp, colors.border.c(), shape)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (tone == PillTone.Ok) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null,
            tint = colors.text.c(),
            modifier = Modifier.size(20.dp),
        )
        Text(text = text, fontSize = 14.sp, lineHeight = 19.sp, color = colors.text.c(), modifier = Modifier.weight(1f))
    }
}

/** Eine von mehreren Möglichkeiten nebeneinander (zum Beispiel die Server-Art): die gewählte hat Goldrahmen, hellere Fläche und ein Häkchen. */
@Composable
internal fun SegmentChoice(text: String, selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .heightIn(min = ActionStyle.MIN_HEIGHT_DP.dp)
            .clip(shape)
            .background(if (selected) LauncherColors.SurfaceRaised else LauncherColors.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) LauncherColors.Gold else LauncherColors.Bronze, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = LauncherColors.GoldBright, modifier = Modifier.padding(end = 6.dp).size(18.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) LauncherColors.GoldBright else LauncherColors.Body,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
