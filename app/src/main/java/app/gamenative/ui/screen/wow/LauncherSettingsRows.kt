package app.gamenative.ui.screen.wow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import app.gamenative.ui.screen.wow.launcher.ActionButton
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.ui.screen.wow.launcher.ValueChip
import app.gamenative.ui.screen.wow.launcher.ValueText
import app.gamenative.ui.screen.wow.launcher.actionKindOf
import app.gamenative.ui.screen.wow.launcher.LauncherSectionTitle
import app.gamenative.ui.screen.wow.launcher.asString
import app.gamenative.ui.screen.wow.launcher.ornateFrame

/*
 * Gemeinsamer Zeilenstil der Einstellungsseite: Titel, kleiner Erklärtext, rechts ein Knopf (Aktion), ein Auswahlfeld (antippbarer
 * Wert), ein Schalter oder gedämpfter Text (reiner Wert), mindestens 48 dp hoch (Daumen). Die Bausteine dafür stehen in
 * launcher/SettingsControls.kt; die Blöcke für Klick-Rad, Sprache, Symbole und Updates benutzen dieselben Zeilen.
 */

private val RowTitleColor = LauncherColors.Body
private val DividerColor = LauncherColors.Divider

/**
 * Gruppe als Karte: oben ein Kopfband mit der Überschrift in Gold (Serif) auf eigener, hellerer Fläche, darunter die Zeilen auf Leder
 * mit doppeltem Goldrahmen. Der Abstand zwischen Gruppen ist größer als der zwischen Zeilen.
 */
@Composable
internal fun SettingGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ornateFrame(fill = LauncherColors.Surface.copy(alpha = 0.94f), corners = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(LauncherColors.SurfaceRaised)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            LauncherSectionTitle(text = title)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp), content = content)
    }
}

/** Abzeichen "Nur hier" (Bronze) oder "Auch im Spiel" (Grün-Gold). Bei reiner Anzeige entsteht keins. */
@Composable
internal fun SettingScopeBadge(scope: SettingScope, modifier: Modifier = Modifier) {
    val text = scope.badgeText?.asString() ?: return
    val shape = RoundedCornerShape(999.dp)
    val inGame = scope.inGame
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (inGame) LauncherColors.BadgeGameText else LauncherColors.BadgeHereText,
        modifier = modifier
            .clip(shape)
            .background(if (inGame) LauncherColors.BadgeGameFill else LauncherColors.BadgeHereFill)
            .border(1.dp, if (inGame) LauncherColors.BadgeGameBorder else LauncherColors.BadgeHereBorder, shape)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** Abzeichen samt Notiz als eigene Zeile über einem Block aus mehreren Zeilen, deren Zeilen das Abzeichen nicht selbst tragen können. */
@Composable
internal fun SettingScopeStrip(scope: SettingScope) {
    if (scope.badgeText == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 5.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SettingScopeBadge(scope)
        scope.note?.let { Text(text = it.asString(), fontSize = 13.sp, lineHeight = 18.sp, color = LauncherColors.Muted) }
    }
}

/** Eine Zeile: Titel und Erklärtext links, [trailing] rechts. Mit [onClick] ist die ganze Zeile tippbar. */
@Composable
internal fun SettingRow(
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    scope: SettingScope? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .drawBehind {
                val inset = 16.dp.toPx()
                val y = size.height - 0.5f
                drawLine(DividerColor, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1f)
            }
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = RowTitleColor)
            if (scope != null) SettingScopeBadge(scope, Modifier.padding(top = 2.dp))
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = LauncherColors.Muted,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            scope?.note?.let { note ->
                Text(
                    text = note.asString(),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = LauncherColors.Muted,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Schalter an/aus; die ganze Zeile schaltet um. */
@Composable
internal fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    description: String? = null,
    enabled: Boolean = true,
    scope: SettingScope? = null,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(title = title, description = description, enabled = enabled, onClick = { onChange(!checked) }, scope = scope) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LauncherColors.TextHead,
                checkedTrackColor = LauncherColors.Bronze,
                uncheckedThumbColor = LauncherColors.Muted,
                uncheckedTrackColor = DividerColor,
                uncheckedBorderColor = LauncherColors.Bronze,
            ),
        )
    }
}

/** Wert rechts: mit [onClick] ein Auswahlfeld (Antippen wechselt zum nächsten Wert, ein Stepper, der herumläuft), sonst gedämpfter Text. */
@Composable
internal fun SettingValueRow(
    title: String,
    value: String,
    description: String? = null,
    enabled: Boolean = true,
    scope: SettingScope? = null,
    onClick: (() -> Unit)? = null,
) {
    SettingRow(title = title, description = description, enabled = enabled, onClick = onClick, scope = scope) {
        if (onClick != null) ValueChip(value, enabled) else ValueText(value)
    }
}

/** Auswahl aus einer Liste: Auswahlfeld mit Pfeil rechts, Antippen öffnet ein Menü direkt darunter. */
@Composable
internal fun <T> SettingChoiceRow(
    title: String,
    value: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    description: String? = null,
    enabled: Boolean = true,
    scope: SettingScope? = null,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingRow(title = title, description = description, enabled = enabled, onClick = { expanded = true }, scope = scope) {
        Box {
            ValueChip(value, enabled)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        trailingIcon = { if (option == selected) Icon(Icons.Default.Check, contentDescription = null, tint = LauncherColors.Gold) },
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
 * Zeile, die etwas auslöst: rechts ein Knopf ([ActionButton]) mit [actionText]; [primary] = Hauptaktion des Bereichs (gefüllt in Gold),
 * [danger] = gefährlich (rot umrandet), sonst umrandet. Mit Knopf ist nur der Knopf tippbar; ohne [actionText] ist die ganze Zeile
 * die Aktion (dann mit Pfeil rechts).
 */
@Composable
internal fun SettingActionRow(
    title: String,
    description: String? = null,
    actionText: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    primary: Boolean = false,
    scope: SettingScope? = null,
    onClick: () -> Unit,
) {
    if (actionText != null) {
        SettingRow(title = title, description = description, enabled = enabled, scope = scope) {
            ActionButton(text = actionText, kind = actionKindOf(primary, danger), enabled = enabled, onClick = onClick)
        }
    } else {
        SettingRow(title = title, description = description, enabled = enabled, onClick = onClick, scope = scope) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = LauncherColors.Gold)
        }
    }
}

/** Erklärtext ohne Bedienelement, innen in einer Gruppe. */
@Composable
internal fun SettingNote(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        color = LauncherColors.Muted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
