package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Bausteine der Karten auf den Unterseiten: kompakte Zeilen (48 dp, Titel bis zu zwei Zeilen, rechts Knopf, Auswahlfeld, Schalter
 * oder gedämpfter Wert) und Textzeilen. Die Höhen kommen aus PageGrid.kt (CARD_ROW_DP, CARD_TEXT_LINE_DP), damit die Rechnung des Rasters stimmt. Das lange
 * Erklärende (Beschreibungen, Abzeichen) steht in der Detailansicht, nicht in der Übersicht.
 */

/** true, solange die Seite scrollt (zugeklappt oder zu wenig Höhe): dann werden Texte nicht gekürzt, es ist ja Platz nach unten. */
internal val LocalPageScroll = compositionLocalOf { false }

private val RowDivider = LauncherColors.Divider

/** Rahmen einer Zeile: [content] liegt in einer Reihe mindestens [CARD_ROW_DP] hoch, unten eine feine Trennlinie. */
@Composable
private fun CardRowShell(enabled: Boolean, onClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .drawBehind {
                val y = size.height - 0.5f
                drawLine(RowDivider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = CARD_ROW_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.CardRowTitle(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = LauncherColors.Body,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
}

/**
 * Wert rechts: antippbar ([onClick]) als Feld mit Rahmen und Pfeil ([ValueChip]), sonst gedämpfter Text ([ValueText]). Die ganze Zeile
 * ist das Touch-Ziel. Ohne [value] steht nur der Titel.
 */
@Composable
internal fun CardValueRow(title: String, value: String = "", enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    CardRowShell(enabled, onClick) {
        CardRowTitle(title)
        if (value.isNotEmpty()) {
            Box(modifier = Modifier.padding(start = 8.dp).widthIn(max = 150.dp)) {
                if (onClick != null) ValueChip(value, enabled) else ValueText(value)
            }
        }
    }
}

/**
 * Zeile, die etwas auslöst: rechts der Knopf ([ActionButton]; [primary] gefüllt in Gold, [danger] rot umrandet, sonst umrandet).
 * Die Zeile selbst ist nicht tippbar, nur der Knopf.
 */
@Composable
internal fun CardActionRow(
    title: String,
    actionText: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    CardRowShell(enabled, onClick = null) {
        CardRowTitle(title)
        ActionButton(
            text = actionText,
            kind = actionKindOf(primary, danger),
            enabled = enabled,
            modifier = Modifier.padding(start = 8.dp),
            onClick = onClick,
        )
    }
}

/** Sprung in einen eigenen Bereich (Detailansicht oder andere Seite): Titel, rechts der Knopf "Öffnen" ([openText]) mit Pfeil. */
@Composable
internal fun CardOpenRow(title: String, openText: String, enabled: Boolean = true, onClick: () -> Unit) {
    CardRowShell(enabled, onClick = null) {
        CardRowTitle(title)
        ActionButton(
            text = openText,
            enabled = enabled,
            modifier = Modifier.padding(start = 8.dp),
            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = onClick,
        )
    }
}

/** Welche Art Knopf zu den Angaben einer Zeile gehört: gefährlich vor Hauptaktion vor Nebenaktion. */
internal fun actionKindOf(primary: Boolean, danger: Boolean): ActionKind = when {
    danger -> ActionKind.Danger
    primary -> ActionKind.Primary
    else -> ActionKind.Secondary
}

/** Schalter an/aus; die ganze Zeile schaltet um. */
@Composable
internal fun CardSwitchRow(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    CardRowShell(enabled, { onChange(!checked) }) {
        CardRowTitle(title)
        Box(modifier = Modifier.padding(start = 8.dp)) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = LauncherColors.TextHead,
                    checkedTrackColor = LauncherColors.Bronze,
                    uncheckedThumbColor = LauncherColors.Muted,
                    uncheckedTrackColor = LauncherColors.Divider,
                    uncheckedBorderColor = LauncherColors.Bronze,
                ),
            )
        }
    }
}

/** Auswahl aus einer Liste: Wert rechts, Antippen öffnet ein Menü direkt darunter. */
@Composable
internal fun <T> CardChoiceRow(
    title: String,
    value: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    CardRowShell(enabled, { expanded = true }) {
        CardRowTitle(title)
        Box(modifier = Modifier.padding(start = 8.dp)) {
            ValueChip(value, enabled)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        trailingIcon = {
                            if (option == selected) Icon(Icons.Default.Check, contentDescription = null, tint = LauncherColors.Gold)
                        },
                        onClick = {
                            expanded = false
                            if (option != selected) onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Textzeilen der Karte (14 sp, Zeilenhöhe 20 sp), höchstens [lines]; scrollt die Seite ([LocalPageScroll]), wird nichts gekürzt.
 */
@Composable
internal fun CardText(text: String, lines: Int, color: Color = LauncherColors.Muted, modifier: Modifier = Modifier) {
    val scrolls = LocalPageScroll.current
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = CARD_TEXT_LINE_DP.sp,
        color = color,
        maxLines = if (scrolls) Int.MAX_VALUE else lines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Hinweis über einer Detailansicht (zum Beispiel die Erklärzeile der Einstellungen). */
@Composable
internal fun DetailHint(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        color = LauncherColors.Muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

/** Hinweisleiste ohne Titel: Text (höchstens [lines] Zeilen) und, wenn [hasDetail], der Pfeil rechts. */
@Composable
internal fun CardNoteRow(text: String, lines: Int, color: Color, hasDetail: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CardText(text = text, lines = lines, color = color, modifier = Modifier.weight(1f))
        if (hasDetail) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = LauncherColors.Muted, modifier = Modifier.padding(start = 8.dp).size(24.dp))
        }
    }
}
