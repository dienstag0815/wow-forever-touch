package app.gamenative.ui.screen.wow

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.ui.components.asString
import app.gamenative.ui.theme.WowBronze
import app.gamenative.ui.theme.WowError
import app.gamenative.ui.theme.WowGold
import app.gamenative.ui.theme.WowMuted
import app.gamenative.ui.theme.WowSubtle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val BATTLE_NET_APP_PACKAGE = "com.blizzard.messenger"
private const val BATTLE_NET_ACCOUNT_URL = "https://account.battle.net/"

private val LightGreen = Color(0xFF4ADE80)
private val LightYellow = Color(0xFFFACC15)
private val TextColor = Color(0xFFE2E8F0)

private fun Light.color() = when (this) {
    Light.GREEN -> LightGreen
    Light.YELLOW -> LightYellow
    Light.RED -> WowError
}

private fun startExternal(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    Timber.w(e, "Startcheck: Intent nicht startbar")
    false
}

/** Oeffnet https://account.battle.net/ im Standardbrowser; er teilt die Browser-Anmeldung. */
internal fun openBattleNetInBrowser(context: Context): Boolean =
    startExternal(context, Intent(Intent.ACTION_VIEW, Uri.parse(BATTLE_NET_ACCOUNT_URL)))

/** Startet die Battle.net-App; ist sie nicht installiert, geht es zum Play Store. */
internal fun openBattleNetApp(context: Context): Boolean {
    val launch = context.packageManager.getLaunchIntentForPackage(BATTLE_NET_APP_PACKAGE)
    if (launch != null && startExternal(context, launch)) return true
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$BATTLE_NET_APP_PACKAGE"))
    if (startExternal(context, market)) return true
    return startExternal(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$BATTLE_NET_APP_PACKAGE")))
}

/**
 * Prueft das Netz (bis ca. 5 s) und zeigt die Ampel. Gruen ohne Login-Hinweis geht von selbst weiter, sonst entscheidet
 * der Spieler ("Weiter" blockiert nie).
 */
@Composable
fun StartCheckDialog(
    region: GameRegion,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onDisable: () -> Unit,
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<StartCheckReport?>(null) }
    var runId by remember { mutableIntStateOf(0) }

    LaunchedEffect(runId) {
        report = null
        val result = withContext(Dispatchers.IO) { StartCheck.run(context, region) }
        report = result
        if (result.verdict.light == Light.GREEN && result.loginHint == null) onContinue()
    }

    val current = report
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.game_sc_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (current == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = WowGold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.game_sc_checking), fontSize = 13.sp, color = TextColor)
                    }
                } else {
                    ResultContent(current, context)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                enabled = current != null,
                colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    stringResource(if (current == null || current.verdict.light == Light.RED) R.string.game_sc_start_anyway else R.string.game_sc_continue),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDisable) { Text(stringResource(R.string.game_sc_disable), fontSize = 12.sp, color = WowSubtle) }
                TextButton(onClick = { runId++ }, enabled = current != null) { Text(stringResource(R.string.game_sc_recheck), color = WowMuted) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.game_cancel), color = WowMuted) }
            }
        },
        containerColor = Color(0xFF1B2838),
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun ResultContent(report: StartCheckReport, context: Context) {
    val verdict = report.verdict
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.padding(top = 4.dp).size(14.dp).background(verdict.light.color(), CircleShape))
        Spacer(modifier = Modifier.width(10.dp))
        Text(verdict.headline.asString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextColor, lineHeight = 19.sp)
    }
    verdict.details.forEach { line ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(line.asString(), fontSize = 12.sp, color = WowSubtle, lineHeight = 16.sp)
    }
    if (verdict.tips.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.game_sc_tips), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WowGold)
        verdict.tips.forEach { tip ->
            Spacer(modifier = Modifier.height(4.dp))
            Text("• ${tip.asString()}", fontSize = 12.sp, color = TextColor, lineHeight = 16.sp)
        }
    }
    report.loginHint?.let { hint ->
        Spacer(modifier = Modifier.height(14.dp))
        Text(stringResource(R.string.game_sc_before_login), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WowGold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(hint.text.asString(), fontSize = 12.sp, color = TextColor, lineHeight = 16.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { openBattleNetInBrowser(context) },
                colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
            ) { Text(stringResource(R.string.game_sc_signin_browser), fontSize = 12.sp) }
            Button(
                onClick = { openBattleNetApp(context) },
                colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
            ) { Text(stringResource(R.string.game_sc_open_app), fontSize = 12.sp) }
        }
    }
}
