package app.gamenative.ui.screen.wow.lab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.lab.StartMode
import app.gamenative.ui.screen.wow.launcher.ImageTextStyle
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.ui.screen.wow.launcher.StartChoiceUi
import app.gamenative.ui.screen.wow.launcher.asString

/**
 * Die Startwahl unter dem Knopf „Spielen“: zwei kleine Einträge nebeneinander, einer gewählt (Goldrand), „Über Battle.net“ trägt die Unterzeile
 * „Ausweichweg“; darunter, solange die einmalige Anmeldung nicht gemerkt ist, eine ruhige Tipp-Zeile. Sie liegt oben im Bild des Hero, nicht im
 * Textbalken: weder die Startseite noch der Textbalken werden dadurch höher.
 */
@Composable
internal fun StartChoiceChips(choice: StartChoiceUi, compact: Boolean, tight: Boolean, onSelect: (StartMode) -> Unit) {
    Column(modifier = Modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(max = 420.dp)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in choice.options) {
                val shape = RoundedCornerShape(6.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = if (tight) 30.dp else 34.dp)
                        .clip(shape)
                        .background(if (option.selected) Color(0x66000000) else Color(0x33000000))
                        .border(BorderStroke(if (option.selected) 1.5.dp else 1.dp, if (option.selected) LauncherColors.Gold else LauncherColors.Bronze), shape)
                        .semantics { selected = option.selected }
                        .clickable(role = Role.RadioButton) { onSelect(option.mode) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = option.label.asString(),
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (option.selected) LauncherColors.GoldBright else LauncherColors.TextBarSub,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = ImageTextStyle,
                    )
                    option.note?.let { note ->
                        Text(
                            text = note.asString(),
                            fontSize = 10.sp,
                            color = LauncherColors.TextBarSub,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = ImageTextStyle,
                        )
                    }
                }
            }
        }
        choice.tip?.let { tip ->
            Text(
                text = tip.asString(),
                fontSize = if (tight) 11.sp else 12.sp,
                lineHeight = if (tight) 14.sp else 16.sp,
                color = LauncherColors.TextBarSub,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = ImageTextStyle,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/**
 * Der kleine Knopf „Battle.net-Launcher“ direkt neben „Spielen“: öffnet die Battle.net-Seite (dieselbe Stelle wie früher der Eintrag
 * „Einrichten“). Er hat die Höhe der Hauptschaltfläche und wird nicht gewählt; nur der Rand ist bronze.
 */
@Composable
internal fun BnetLauncherButton(compact: Boolean, fillWidth: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val description = stringResource(R.string.lab_start_launcher_desc)
    Column(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.widthIn(min = 84.dp, max = 124.dp))
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(Color(0x66000000))
            .border(BorderStroke(1.dp, LauncherColors.Bronze), shape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.lab_start_launcher),
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            color = LauncherColors.TextBarSub,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = ImageTextStyle,
        )
    }
}
