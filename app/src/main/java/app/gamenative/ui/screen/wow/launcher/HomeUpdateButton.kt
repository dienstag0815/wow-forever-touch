package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.BuildConfig
import app.gamenative.update.UpdateManager
import kotlinx.coroutines.delay

/*
 * Knopf "Nach Updates suchen" auf der Startseite. Er benutzt denselben Weg wie die Einstellungs-Zeile
 * (UpdateManager.checkManually) und die Update-Karte (UpdateManager.install); was er zeigt, entscheidet allein
 * homeUpdateButtonState (HomeUiState.kt). Er sitzt im Kopf (aufgeklappt) oder in der Fußzeile (zugeklappt), siehe HomeMetrics.kt.
 */

private fun HomeUpdateKind.tint(): Color = when (this) {
    HomeUpdateKind.Search, HomeUpdateKind.Checking, HomeUpdateKind.Working -> LauncherColors.Gold
    HomeUpdateKind.UpToDate -> LauncherColors.Ok
    HomeUpdateKind.Offered -> LauncherColors.GoldBright
    HomeUpdateKind.Failed -> LauncherColors.Warn
}

private fun HomeUpdateKind.icon(): ImageVector = when (this) {
    HomeUpdateKind.Search, HomeUpdateKind.Checking, HomeUpdateKind.Working -> Icons.Filled.Refresh
    HomeUpdateKind.UpToDate -> Icons.Filled.Check
    HomeUpdateKind.Offered -> Icons.Filled.Download
    HomeUpdateKind.Failed -> Icons.Filled.Warning
}

/** Der Knopf als Zeichnung: Pille wie die Statuspille, Symbol (oder Fortschrittskreis) und, wenn [showText], der Text daneben. */
@Composable
internal fun HomeUpdateButton(ui: HomeUpdateButtonUi, showText: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = ui.kind.tint()
    val shape = RoundedCornerShape(999.dp)
    val borderColor = if (ui.kind == HomeUpdateKind.Offered) LauncherColors.Gold else LauncherColors.Bronze
    val description = ui.description.asString()
    Row(
        modifier = modifier
            .heightIn(min = HOME_UPDATE_BUTTON_DP.dp)
            .widthIn(min = HOME_UPDATE_BUTTON_DP.dp)
            .clip(shape)
            .background(LauncherColors.StoneDark.copy(alpha = 0.7f))
            .border(1.dp, borderColor, shape)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
            .clickable(enabled = ui.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (showText) 14.dp else 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (ui.spinner) {
            CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(ui.kind.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        if (showText) {
            Text(
                text = ui.label.asString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (ui.kind == HomeUpdateKind.Search) LauncherColors.Body else tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}

/**
 * Der Knopf mit seinem Zustand: liest UpdateManager.state und checkPhase, löst Suche oder Installation aus und setzt flüchtige
 * Ergebnisse ("Aktuell", Suchfehler) nach [HOME_UPDATE_RESULT_MS] zurück. [canInstall] ist false, solange ein Spiel startet oder
 * Dateien geladen werden; dann bleibt "Installieren" gesperrt (wie im Streifen).
 */
@Composable
internal fun HomeUpdateButtonHost(canInstall: Boolean, showText: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by UpdateManager.state.collectAsState()
    val phase by UpdateManager.checkPhase.collectAsState()
    val ui = homeUpdateButtonState(state, phase, BuildConfig.VERSION_NAME, canInstall)
    LaunchedEffect(ui.kind, ui.autoReset) {
        if (ui.autoReset) {
            delay(HOME_UPDATE_RESULT_MS)
            UpdateManager.dismiss()
        }
    }
    HomeUpdateButton(
        ui = ui,
        showText = showText,
        modifier = modifier,
        onClick = {
            when (ui.action) {
                HomeUpdateAction.Check -> UpdateManager.checkManually(context)
                HomeUpdateAction.Install -> UpdateManager.install(context)
                HomeUpdateAction.None -> Unit
            }
        },
    )
}
