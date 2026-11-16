package app.gamenative.ui.screen.wow.lab

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.lab.ChooseResult
import app.gamenative.lab.EffectiveStart
import app.gamenative.lab.GuideStep
import app.gamenative.lab.LabFailure
import app.gamenative.lab.LabGuard
import app.gamenative.lab.LabIds
import app.gamenative.lab.LabLauncher
import app.gamenative.lab.LabRestart
import app.gamenative.lab.LabStart
import app.gamenative.lab.LabStartFacts
import app.gamenative.lab.LabStartMode
import app.gamenative.lab.LabStartOrigin
import app.gamenative.lab.LabStartSources
import app.gamenative.lab.LabStore
import app.gamenative.lab.LabTarget
import app.gamenative.lab.StartMode
import app.gamenative.ui.theme.WowGold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zustand der Startwahl auf der Startseite („Über Battle.net starten“ / „Direkt starten“). Die Entscheidungen liegen in der reinen [LabStartMode];
 * hier hängen nur Speicher, Hintergrundarbeit und die Seiten daran. Der Direktstart selbst läuft über [play] mit dem übergebenen `direct`,
 * unverändert. Der Battle.net-Weg startet nie von selbst: nur ein Tipp auf „Spielen“ ruft [LabLauncher.prepare].
 */
internal class StartChoiceState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onLaunch: (String) -> Unit,
    private val openLab: () -> Unit,
) {
    var facts by mutableStateOf<LabStartFacts?>(null)
        private set

    /** Eine Meldung der Startseite (Spiel läuft, Versuch läuft); null = keine. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Ein Satz für die Anleitung, wenn der Weg dorthin führt (was fehlt); die Seite liest ihn einmal. */
    var guideNote by mutableStateOf<String?>(null)
        private set

    /** Ob die Anleitung bei Teil B („Wenn der direkte Start abgelehnt wird“) aufgehen soll (Wahl „Über Battle.net“ ohne eingerichteten Teil B). */
    var guidePartB by mutableStateOf(false)
        private set

    private var starting = false

    val effective: EffectiveStart? get() = facts?.let { LabStartMode.effective(it) }

    /** Ob die Startseite die Zeile „Tipp: einmal bei Battle.net anmelden“ zeigt (Schritt 4 nicht gemerkt); ohne Fakten keine Zeile. */
    val signInTip: Boolean get() = facts?.let { LabStartMode.showSignInTip(it) } == true

    /** Liest die Fakten neu (Hintergrund). Ein Fehler lässt den alten Stand stehen. */
    suspend fun load() {
        val loaded = withContext(Dispatchers.IO) { LabStartSources.facts(context) }
        if (loaded != null) facts = loaded
    }

    /** Fakten für die Weiche: sind sie nicht ermittelbar, gilt nur die gespeicherte Wahl (ohne Wahl: Direktstart wie bisher). */
    private fun factsOrFallback(): LabStartFacts = facts ?: LabStartFacts(chosen = LabStore.startMode(context))

    /** Ob beim Öffnen der App der Countdown laufen darf (nur beim Direktstart); lädt die Fakten, falls nötig. */
    suspend fun autoStartAllowed(): Boolean {
        if (facts == null) load()
        return LabStartMode.autoStartAllowed(LabStartMode.effective(factsOrFallback()))
    }

    /** Tipp auf „Spielen“: [direct] ist der bisherige Weg (`startLaunch`), unverändert. */
    fun play(direct: () -> Unit) {
        LabStartMode.dispatch(factsOrFallback(), direct = direct, launcher = ::startLauncher, guide = { openGuide(missingText(it)) })
    }

    private fun startLauncher() {
        if (starting) return
        starting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { LabLauncher.prepare(context, LabTarget.BATTLENET, LabStartOrigin.HOME) }
            starting = false
            when (result) {
                is LabStart.Ready -> {
                    LabRestart.clear()
                    onLaunch(LabIds.CONTAINER_ID)
                }
                is LabStart.Blocked -> message = context.getString(
                    if (result.block == LabGuard.Block.GAME_RUNNING) R.string.launcher_start_blocked_game else R.string.launcher_start_blocked_run,
                )
                else -> openGuide(
                    context.getString(
                        if (result is LabStart.Failed && result.failure == LabFailure.NO_BASE) R.string.launcher_start_no_base
                        else R.string.launcher_start_failed,
                    ),
                )
            }
        }
    }

    /** Tipp auf „Battle.net einrichten“: öffnet die Battle.net-Seite wie [openLab], ohne Satz für die Anleitung. */
    fun openSetup() {
        guidePartB = false
        openLab()
    }

    private fun openGuide(note: String, partB: Boolean = false) {
        guideNote = note
        guidePartB = partB
        openLab()
    }

    private fun missingText(step: GuideStep): String = context.getString(
        when (step) {
            GuideStep.PACKAGES -> R.string.launcher_start_missing_packages
            GuideStep.LAUNCHER -> R.string.launcher_start_missing_launcher
            GuideStep.COPY -> R.string.launcher_start_missing_copy
            GuideStep.GAME -> R.string.launcher_start_missing_game
            else -> R.string.launcher_start_missing_setting
        },
    )

    /** Die Anleitung wurde geschlossen: der Satz ist verbraucht, und was dort eingerichtet wurde, gilt ab jetzt (Fakten neu lesen). */
    fun guideClosed() {
        guideNote = null
        guidePartB = false
        scope.launch { load() }
    }

    fun dismissMessage() {
        message = null
    }

    /**
     * Tipp auf einen Eintrag der Startwahl. „Direkt starten“ gilt sofort. „Über Battle.net starten“ gilt nur, wenn Teil B eingerichtet ist; sonst
     * öffnet die Anleitung bei Teil B mit einem Satz, was fehlt (nichts wird gespeichert, nichts startet).
     */
    fun choose(mode: StartMode) {
        val f = facts ?: return
        when (val result = LabStartMode.choose(f, mode)) {
            is ChooseResult.Commit -> commit(result.mode)
            is ChooseResult.OpenGuide -> openGuide(missingText(result.missing), partB = true)
        }
    }

    private fun commit(mode: StartMode) {
        LabStore.saveStartMode(context, mode)
        facts = facts?.copy(chosen = mode)
    }
}

/** Der Zustand der Startwahl; lädt die Fakten beim Öffnen. [openLab] öffnet die Anleitung (die versteckte Seite bleibt versteckt, sie öffnet nur hier). */
@Composable
internal fun rememberStartChoice(onLaunch: (String) -> Unit, openLab: () -> Unit): StartChoiceState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { StartChoiceState(context.applicationContext, scope, onLaunch, openLab) }
    LaunchedEffect(Unit) { state.load() }
    return state
}

/** Die Meldung der Startseite (Spiel läuft, Versuch läuft). */
@Composable
internal fun StartChoiceDialogs(state: StartChoiceState) {
    state.message?.let { text ->
        AlertDialog(
            onDismissRequest = { state.dismissMessage() },
            text = { Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFFE2E8F0)) },
            confirmButton = { TextButton(onClick = { state.dismissMessage() }) { Text(stringResource(R.string.launcher_start_ok), color = WowGold) } },
        )
    }
}
