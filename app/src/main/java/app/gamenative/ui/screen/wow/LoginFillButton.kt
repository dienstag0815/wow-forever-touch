package app.gamenative.ui.screen.wow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.theme.WowGold
import kotlinx.coroutines.delay

/**
 * Knopf "Anmeldedaten ausfüllen" der Stufe "Ausfüllen" im Spielbild: ein Tipp, und die App tippt E-Mail und Passwort (oder nur das
 * Passwort, je nach Wahl auf der Karte) ins Anmeldefenster, ohne abzusenden. Er sitzt oben neben Escape und Tastatur, rechts davon,
 * damit das Anmeldefenster in der Mitte frei bleibt, und verschwindet in der Welt, bei offenem Seitenmenü und in den anderen Stufen
 * ([LoginLevels.fillButtonVisible]). Stufe und Daten werden beim Öffnen des Seitenmenüs neu gelesen (Speichern dort kann die Stufe ändern).
 */
@Composable
internal fun LoginFillButton(menuOpen: Boolean) {
    val context = LocalContext.current
    var level by remember(menuOpen) { mutableStateOf(LoginLevelStore.current(context)) }
    var hasSaved by remember(menuOpen) { mutableStateOf(BattleNetSignIn.hasSaved(context)) }
    var inWorld by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    // Nur die Welt-Erkennung ändert sich im Spiel von selbst; die Stufe ändert sich nicht mitten im Spiel.
    LaunchedEffect(level) {
        if (level != LoginLevel.FILL) return@LaunchedEffect
        while (true) {
            inWorld = LoginTouchGate.inWorld()
            delay(WORLD_POLL_MS)
        }
    }
    LaunchedEffect(note) {
        if (note != null) {
            delay(NOTE_SHOWN_MS)
            note = null
        }
    }

    if (!LoginLevels.fillButtonVisible(level, hasSaved, inWorld, menuOpen)) return
    val noLogin = stringResource(R.string.game_note_nologin)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = maxWidth * BUTTON_X_FRACTION, y = BUTTON_TOP_DP.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = {
                    val scope = LoginLevelStore.scope(context)
                    val result = LoginFill.start(context, scope.action, R.string.game_login_done_fill, withClock = false, settleMs = SETTLE_MS) { note = it }
                    if (result == LoginFill.Start.NO_SECRET) note = noLogin
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WowGold, contentColor = Color.Black),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.game_fill_button), fontSize = 15.sp)
            }
            note?.let {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.7f)) {
                    Text(it, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

/** Wie oft die Welt-Erkennung abgefragt wird (der Lesedienst misst alle paar hundert Millisekunden). */
private const val WORLD_POLL_MS = 500L

/** So lange bleibt die Meldung nach dem Tippen stehen. */
private const val NOTE_SHOWN_MS = 6_000L

/** Wartezeit vor der ersten Taste: das Spiel läuft hier schon, anders als nach dem Seitenmenü. */
private const val SETTLE_MS = 150L

/** Lage: rechts von Escape und Tastatur der mitgelieferten Profile (dort bei 27 % und 33 % der Breite), oben. */
private const val BUTTON_X_FRACTION = 0.40f
private const val BUTTON_TOP_DP = 8
