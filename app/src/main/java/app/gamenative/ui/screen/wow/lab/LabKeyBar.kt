package app.gamenative.ui.screen.wow.lab

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import app.gamenative.lab.LabBarKey
import app.gamenative.lab.LabConfirmTap
import app.gamenative.lab.LabIds
import app.gamenative.lab.LabInput
import app.gamenative.lab.LabInputSource
import app.gamenative.lab.LabKeySender
import app.gamenative.lab.LabFocusKind
import app.gamenative.lab.LabGameControlsApply
import app.gamenative.lab.LabGameControlsLogic
import app.gamenative.lab.LabGcChange
import app.gamenative.lab.LabGcSource
import app.gamenative.lab.LabGcState
import app.gamenative.lab.LabReport
import app.gamenative.lab.LabRun
import app.gamenative.lab.LabSignInCheck
import app.gamenative.lab.LabSignInContext
import app.gamenative.lab.LabSignInKeys
import app.gamenative.lab.LabSignInKind
import app.gamenative.lab.LabSignInState
import app.gamenative.lab.LabTarget
import app.gamenative.lab.LabWatch
import app.gamenative.lab.LabWatchState
import app.gamenative.lab.LabWatchStatus
import app.gamenative.lab.LabWinEntry
import app.gamenative.lab.LabWinSnapshot
import app.gamenative.lab.LabWindowList
import app.gamenative.lab.LabWindows
import app.gamenative.ui.screen.wow.voice.KeySink
import app.gamenative.ui.util.SnackbarManager
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Ob die Android-Tastatur sichtbar ist; von der Abfrage der Leiste gesetzt, damit die Tastenhörer nie eine Ansicht berühren. */
@Volatile private var keyboardVisibleCache = false

private const val POLL_MS = 500L
private const val SETTLE_MS = 150L
private val BAR_COLOR = Color.Black.copy(alpha = 0.55f)

/**
 * Tastenleiste des Battle.net-Labors (nur Container `bnetlab`; im Spiel kehrt die Funktion sofort zurück und zeichnet nichts): eine
 * schmale, halbtransparente, ein- und ausklappbare Leiste am oberen Rand mit Esc, Tab, Umschalt+Tab, Enter, Rücktaste, den Pfeilen,
 * "Tastatur" (öffnet die Android-Tastatur auf dem Weg des Spiels), "Text" (flüchtiges Eingabefeld, sendet den Text als Tastenfolge)
 * und "Beenden" (zweimal tippen). Die Tasten gehen über `XServer.injectKeyPress/Release`, den Weg von `LoginFill` im Spiel.
 * Dieselbe Funktion hängt die Eingabe-Diagnose des Protokolls an den X-Server und fragt dafür etwa zweimal in der Sekunde den Fokus ab.
 */
@Composable
internal fun LabKeyBar(containerId: String?) {
    if (containerId != LabIds.CONTAINER_ID) return
    val view = LocalView.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(true) }
    var showText by remember { mutableStateOf(false) }
    var armedAt by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var windows by remember { mutableStateOf(LabWinSnapshot.EMPTY) }
    var showWindows by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Spielsteuerung (Schalter "Spiel"): Vorgabe aus; die Automatik folgt dem Spielfenster aus der Fensterliste (siehe LabGameControlsLogic).
    var gc by remember { mutableStateOf(LabGcState()) }
    var gcBusy by remember { mutableStateOf(false) }
    // Hänger-Erkennung (nur für den Start von Battle.net): aus der Fensterliste abgeleitet, zeigt einen Hinweis mit dem Knopf „Launcher neu starten“.
    // Sie beendet und startet nie von selbst; der Knopf wirkt einmal je Tipp (danach ist er weg, bis die Leiste neu entsteht).
    var watch by remember { mutableStateOf(LabWatchStatus()) }
    var restarting by remember { mutableStateOf(false) }
    // Anmelde-Knöpfe (Schalter „Sign-in buttons“ der Labor-Einstellungen): Freigabe rein in LabSignInKeys, hier nur der zuletzt abgefragte Stand.
    var signIn by remember { mutableStateOf(LabSignInState.HIDDEN) }

    /** Wendet eine Umschaltung an (Dateien auf dem Hintergrundfaden, Ansichten auf dem Haupt-Thread), schreibt die Protokollzeile und meldet sie. */
    suspend fun applyGameControls(change: LabGcChange) {
        gcBusy = true
        try {
            val prepared = withContext(Dispatchers.IO) { runCatching { LabGameControlsApply.prepare(context, change.on) }.getOrNull() }
            val applied = prepared != null && runCatching { LabGameControlsApply.apply(prepared, change.on) }.getOrDefault(false)
            if (!applied && change.on) gc = LabGameControlsLogic.applyFailed(gc)
            LabRun.gameControlsChanged(change, applied)
            val auto = change.source == LabGcSource.AUTO
            SnackbarManager.show(
                context.getString(
                    when {
                        !applied && change.on -> R.string.lab_game_unavailable
                        change.on -> if (auto) R.string.lab_game_on_auto else R.string.lab_game_on
                        else -> if (auto) R.string.lab_game_off_auto else R.string.lab_game_off
                    },
                ),
            )
        } finally {
            gcBusy = false
        }
    }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            keyboardVisibleCache = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            val server = PluviaApp.xServerView?.getxServer()
            server?.let {
                LabInput.attach(it) { keyboardVisibleCache }
                LabWindows.attach(it)
            }
            LabInput.sample()
            signIn = LabSignInKeys.state(signInContext(context))
            // Die Fensterzahl neben dem Knopf (und die Liste, wenn offen): höchstens zweimal je Sekunde, nur solange die Leiste sichtbar ist.
            // Eingeklappt fragt nur die Automatik der Spielsteuerung, alle zwei Sekunden.
            if (server != null && (expanded || tick % 4 == 0)) {
                val shot = withContext(Dispatchers.Default) { runCatching { LabWindows.snapshot(server) }.getOrNull() }
                if (shot != null && expanded && shot != windows) windows = shot
                if (shot != null && !gcBusy) {
                    val step = LabGameControlsLogic.onSnapshot(gc, shot, System.currentTimeMillis())
                    gc = step.state
                    step.change?.let { applyGameControls(it) }
                }
                if (shot != null && LabRun.activeTarget() == LabTarget.BATTLENET) {
                    LabRun.elapsedMs()?.let { elapsed ->
                        val watched = LabWatch.onSnapshot(watch, shot, elapsed)
                        watch = watched.status
                        watched.change?.let { LabRun.watchChanged(it) }
                    }
                }
            }
            tick++
            now = System.currentTimeMillis()
            delay(POLL_MS)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            LabKeyBarSend.cancel()
            LabInput.detach()
            LabWindows.detach()
        }
    }

    fun raise(entry: LabWinEntry) {
        val server = PluviaApp.xServerView?.getxServer() ?: return
        scope.launch(Dispatchers.Default) {
            val result = runCatching {
                LabWindows.bringToFront(server, entry.id, viaWinHandler = !LabRun.startsDirect(LabIds.CONTAINER_ID))
            }.getOrNull()
            SnackbarManager.show(
                context.getString(
                    when {
                        result == null || !result.found -> R.string.lab_win_gone
                        result.focusEventSent || result.viaWinHandler -> R.string.lab_win_raised
                        else -> R.string.lab_win_raised_nomsg
                    },
                ),
            )
        }
    }

    fun closeWindow(entry: LabWinEntry) {
        val server = PluviaApp.xServerView?.getxServer() ?: return
        scope.launch(Dispatchers.Default) {
            val sent = runCatching { LabWindows.requestClose(server, entry.id) }.getOrDefault(false)
            SnackbarManager.show(context.getString(if (sent) R.string.lab_win_close_sent else R.string.lab_win_close_failed))
        }
    }

    fun tapGame() {
        if (gcBusy) return
        val step = LabGameControlsLogic.onTap(gc)
        gc = step.state
        step.change?.let { scope.launch { applyGameControls(it) } }
    }

    /** „Launcher neu starten“: Zeile ins Protokoll, Lauf beenden wie „Exit“; die Labor-Seite bietet danach „Erneut starten“ an. */
    fun restartLauncher() {
        if (restarting) return
        if (LabRun.restartRequested()) {
            restarting = true
            PluviaApp.events.emitJava(AndroidEvent.ForceCloseApp)
        }
    }
    val hint = LabWatch.showsHint(watch.state) && !restarting

    fun send(key: LabBarKey) {
        PluviaApp.xServerView?.getxServer()?.let { LabKeyBarSend.tap(it, key) }
    }

    /**
     * Tipp auf „E-Mail“ oder „Passwort“: genau ein gespeicherter Wert, getippt wie der Text-Knopf (nur Zeichen, kein Enter, kein Tab). Die Freigabe wird beim Tipp
     * neu geprüft; der Wert wird nur als CharArray gehalten, vorher auf nicht sendbare Zeichen geprüft (sonst geht nichts raus) und danach überschrieben.
     */
    fun tapSignIn(kind: LabSignInKind) {
        val ctx = signInContext(context)
        when (LabSignInKeys.state(ctx)) {
            LabSignInState.HIDDEN -> return
            LabSignInState.NO_CREDENTIALS -> {
                SnackbarManager.show(context.getString(R.string.lab_signin_keys_none))
                return
            }
            LabSignInState.READY -> Unit
        }
        val nowMs = System.currentTimeMillis()
        if (!LabSignInKeys.mayTap(ctx, LabKeyBarSend.isBusy(), LabKeyBarSend.lastActivityMs(), nowMs)) return
        val server = PluviaApp.xServerView?.getxServer() ?: return
        LabKeyBarSend.markActivity(nowMs)
        val elapsedMs = LabRun.elapsedMs() ?: 0L
        scope.launch(Dispatchers.Default) {
            val values = runCatching { LabReport.signInValues(context) }.getOrNull()
            if (values == null) {
                SnackbarManager.show(context.getString(R.string.lab_signin_keys_none))
                return@launch
            }
            val value = values.take(kind)
            val check = LabSignInKeys.check(value)
            if (!check.sendable) {
                value.fill('\u0000')
                LabRun.signInKeyLine(LabSignInKeys.notSentLine(kind, check))
                SnackbarManager.show(signInNotSentText(context, check))
                return@launch
            }
            val accepted = LabKeyBarSend.typeSignIn(server, kind, value, elapsedMs) { r ->
                SnackbarManager.show(
                    if (r.cancelled) context.getString(R.string.lab_text_cancelled) else context.getString(R.string.lab_signin_keys_sent, r.sent),
                )
            }
            if (!accepted) SnackbarManager.show(context.getString(R.string.lab_signin_keys_busy))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!expanded) {
            BarButton(label = "⌨", description = stringResource(R.string.lab_bar_show), modifier = Modifier.align(Alignment.TopCenter)) { expanded = true }
            if (hint) LabWatchHint(watch.state, modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp)) { restartLauncher() }
        } else Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp, start = 8.dp, end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = BAR_COLOR, shape = RoundedCornerShape(10.dp)) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    BarButton("▲", stringResource(R.string.lab_bar_hide)) { expanded = false }
                    BarButton(
                        stringResource(R.string.lab_bar_windows, windows.count),
                        stringResource(R.string.lab_bar_windows_desc, windows.count),
                    ) { showWindows = !showWindows }
                    BarButton(
                        stringResource(R.string.lab_bar_game) + if (gc.on) " ●" else "",
                        stringResource(if (gc.on) R.string.lab_bar_game_desc_on else R.string.lab_bar_game_desc_off),
                        active = gc.on,
                    ) { tapGame() }
                    BarButton("Esc") { send(LabBarKey.ESC) }
                    BarButton("Tab") { send(LabBarKey.TAB) }
                    BarButton("⇧Tab") { send(LabBarKey.SHIFT_TAB) }
                    BarButton("Enter") { send(LabBarKey.ENTER) }
                    BarButton("⌫") { send(LabBarKey.BACKSPACE) }
                    BarButton("←") { send(LabBarKey.LEFT) }
                    BarButton("→") { send(LabBarKey.RIGHT) }
                    BarButton("↑") { send(LabBarKey.UP) }
                    BarButton("↓") { send(LabBarKey.DOWN) }
                    BarButton(stringResource(R.string.lab_bar_keyboard)) { showAndroidKeyboard(view) }
                    BarButton(stringResource(R.string.lab_bar_text)) { showText = true }
                    if (signIn != LabSignInState.HIDDEN) {
                        // Ausgegraut, solange nichts gespeichert ist, eine Folge läuft oder die Sperrzeit nach einem Tipp gilt (der Tipp zeigt dann den Hinweis bzw. tut nichts).
                        val ready = signIn == LabSignInState.READY && !LabKeyBarSend.isBusy() && LabSignInKeys.cooldownLeftMs(LabKeyBarSend.lastActivityMs(), now) == 0L
                        val desc = stringResource(if (signIn == LabSignInState.READY) R.string.lab_bar_signin_desc else R.string.lab_bar_signin_desc_none)
                        BarButton(stringResource(R.string.lab_bar_email), desc, dim = !ready) { tapSignIn(LabSignInKind.EMAIL) }
                        BarButton(stringResource(R.string.lab_bar_password), desc, dim = !ready) { tapSignIn(LabSignInKind.PASSWORD) }
                    }
                    val armed = LabConfirmTap.isArmed(armedAt, now)
                    BarButton(stringResource(if (armed) R.string.lab_bar_exit_confirm else R.string.lab_bar_exit)) {
                        val step = LabConfirmTap.tap(armedAt, System.currentTimeMillis())
                        armedAt = step.armedAtMs
                        if (step.fire) PluviaApp.events.emitJava(AndroidEvent.ForceCloseApp)
                    }
                }
            }
            if (hint) LabWatchHint(watch.state) { restartLauncher() }
            if (showWindows) {
                LabWindowPanel(windows, onRaise = { raise(it) }, onClose = { closeWindow(it) })
            }
        }
    }

    if (showText) {
        LabTextDialog(
            onSend = { chars ->
                val server = PluviaApp.xServerView?.getxServer()
                if (server == null) {
                    chars.fill('\u0000')
                } else {
                    LabKeyBarSend.typeText(server, chars) { r ->
                        SnackbarManager.show(
                            when {
                                r.cancelled -> context.getString(R.string.lab_text_cancelled)
                                r.skipped > 0 -> context.getString(R.string.lab_text_sent_skipped, r.sent, r.skipped)
                                else -> context.getString(R.string.lab_text_sent, r.sent)
                            },
                        )
                    }
                }
            },
            onClose = { showText = false },
        )
    }
}

/** Hinweis unter der Leiste, wenn der Launcher kein Fenster zeigt oder ein Fehlerfenster offen ist, mit dem Knopf „Launcher neu starten“ (nur auf Tipp). */
@Composable
private fun LabWatchHint(state: LabWatchState, modifier: Modifier = Modifier, onRestart: () -> Unit) {
    Surface(color = BAR_COLOR, shape = RoundedCornerShape(10.dp), modifier = modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (state == LabWatchState.ERROR_WINDOW) R.string.lab_watch_error_window else R.string.lab_watch_no_window),
                color = Color(0xFFFFD28A),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp).widthIn(max = 260.dp),
            )
            BarButton(stringResource(R.string.lab_watch_restart), stringResource(R.string.lab_watch_restart_desc)) { onRestart() }
        }
    }
}

/** Die Fensterliste unter der Leiste: oberstes Fenster zuerst; Tipp auf einen Eintrag holt das Fenster nach vorn, "Schließen" nur bei Fenstern, die es anbieten. */
@Composable
private fun LabWindowPanel(snapshot: LabWinSnapshot, onRaise: (LabWinEntry) -> Unit, onClose: (LabWinEntry) -> Unit) {
    Surface(
        color = BAR_COLOR,
        shape = RoundedCornerShape(10.dp),
        // Taps auf die Fläche gehören der Liste und gehen nicht an das Fenster dahinter.
        modifier = Modifier.padding(top = 4.dp).widthIn(max = 560.dp).fillMaxWidth().pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp).heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.lab_win_title) + " (${snapshot.count})",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (snapshot.entries.isEmpty()) {
                Text(stringResource(R.string.lab_win_none), color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            } else {
                Text(stringResource(R.string.lab_win_hint), color = Color(0xFFCCCCCC), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }
            snapshot.entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onRaise(entry) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(LabWindowList.label(entry), color = Color.White, fontSize = 14.sp, maxLines = 2)
                        val marks = buildList {
                            if (entry.topmost) add(stringResource(R.string.lab_win_topmost))
                            if (entry.focused) add(stringResource(R.string.lab_win_focus))
                        }
                        val detail = (listOf(LabWindowList.geometry(entry), LabWindowList.hex(entry.id)) + marks).joinToString("  ·  ")
                        Text(detail, color = if (entry.focused) Color(0xFF9FE8A8) else Color(0xFFCCCCCC), fontSize = 12.sp, maxLines = 2)
                    }
                    if (entry.closable) {
                        TextButton(onClick = { onClose(entry) }) { Text(stringResource(R.string.lab_win_close)) }
                    }
                }
            }
            val focusNote = when (snapshot.focusKind) {
                LabFocusKind.DESKTOP -> R.string.lab_win_focus_desktop
                LabFocusKind.ROOT -> R.string.lab_win_focus_root
                LabFocusKind.OTHER -> R.string.lab_win_focus_other
                LabFocusKind.NONE -> R.string.lab_win_focus_none
                LabFocusKind.WINDOW -> null
            }
            if (focusNote != null) Text(stringResource(focusNote), color = Color(0xFFFFD27A), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun BarButton(label: String, description: String? = null, modifier: Modifier = Modifier, active: Boolean = false, dim: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .widthIn(min = 40.dp)
            .clickable(onClick = onClick)
            .semantics { description?.let { contentDescription = it } }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Color(0xFF9FE8A8) else if (dim) Color(0xFF8A8A8A) else Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

/** Flüchtiges Eingabefeld: nichts wird gespeichert, kein Verlauf, keine Vorschläge; der Inhalt wird beim Senden und Schließen gelöscht. */
@Composable
private fun LabTextDialog(onSend: (CharArray) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    fun close() {
        text = ""
        onClose()
    }
    fun send() {
        if (text.isEmpty()) return
        val chars = text.toCharArray()
        text = ""
        onSend(chars)
        onClose()
    }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Oben im Bild und ohne Abdunkeln: das Fenster dahinter bleibt sichtbar.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let {
            it.setGravity(Gravity.TOP)
            it.setDimAmount(0f)
        }
        Surface(color = Color(0xFF1A1A1A), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp).widthIn(max = 560.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lab_text_title), color = Color.White, fontSize = 16.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                    // Eingabetyp "Passwort": die Tastatur bietet dafür weder Vorschläge noch Verlauf an.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    supportingText = { Text(stringResource(R.string.lab_text_hint), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { hidden = !hidden }) {
                        Text(stringResource(if (hidden) R.string.lab_text_hidden else R.string.lab_text_visible))
                    }
                    Box(modifier = Modifier.weight(1f))
                    TextButton(onClick = { close() }) { Text(stringResource(R.string.lab_text_close)) }
                    Button(onClick = { send() }, enabled = text.isNotEmpty()) { Text(stringResource(R.string.lab_text_send)) }
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { text = "" } }
}

/** Öffnet die Android-Tastatur auf dem Weg des Spiels (`XServerScreen.showSoftKeyboard`): zuerst `IMEInputReceiver`, sonst die Umschaltung. */
private fun showAndroidKeyboard(anchor: android.view.View) {
    anchor.post {
        if (anchor.windowToken == null) return@post
        val show = {
            PluviaApp.imeInputReceiver?.showKeyboard()
                ?: (anchor.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            Unit
        }
        if (Build.VERSION.SDK_INT > 29) anchor.postDelayed({ show() }, 500) else show()
    }
}

/** Stand der Freigabe der Anmelde-Knöpfe jetzt (Schalter des Versuchs, Labor-Lauf, Ziel, gespeicherte Zugangsdaten; die Entscheidung selbst ist [LabSignInKeys.state]). */
private fun signInContext(context: android.content.Context): LabSignInContext {
    val on = LabRun.signInKeysOn()
    return LabSignInContext(
        switchOn = on,
        labRunActive = LabRun.isActive(),
        target = LabRun.activeTarget(),
        hasCredentials = on && runCatching { LabReport.hasSavedSignIn(context) }.getOrDefault(false),
    )
}

private fun signInNotSentText(context: android.content.Context, check: LabSignInCheck): String =
    if (check.empty) context.getString(R.string.lab_signin_keys_empty) else context.getString(R.string.lab_signin_keys_unsupported, check.unsupported)

/** Sendet im Hintergrund (ein Thread, nie zwei Folgen zugleich); jede Taste trägt die Quelle "Leiste" bzw. "Textfeld" für das Protokoll. */
internal object LabKeyBarSend {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "lab-keybar").apply { isDaemon = true } }

    @Volatile private var cancelled = false

    /** Zahl der Folgen (Text, Anmelde-Knopf), die gerade laufen oder warten; die Anmelde-Knöpfe sind gesperrt, solange sie nicht 0 ist. */
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)

    /** Zeit des letzten Tipps auf einen Anmelde-Knopf oder des Endes seiner Folge, für die Sperrzeit ([LabSignInKeys.COOLDOWN_MS]). */
    @Volatile private var activityMs: Long? = null

    fun cancel() {
        cancelled = true
    }

    fun isBusy(): Boolean = pending.get() > 0

    fun lastActivityMs(): Long? = activityMs

    fun markActivity(nowMs: Long) {
        activityMs = nowMs
    }

    private fun sinkOf(server: XServer) = object : KeySink {
        override fun press(key: XKeycode, keysym: Int) = server.injectKeyPress(key, keysym)

        override fun release(key: XKeycode) = server.injectKeyRelease(key)
    }

    fun tap(server: XServer, key: LabBarKey) {
        executor.execute {
            runCatching { LabInput.asSource(LabInputSource.BAR) { LabKeySender(sinkOf(server)).tap(key) } }
        }
    }

    /**
     * Tippt den Wert eines Anmelde-Knopfs ([value] wird in jedem Fall überschrieben) mit der Quelle SIGNIN und schreibt die Zeile `-- Sign-in key: ...` ins
     * Protokoll (nie den Wert, nie Teile davon; nach Timber nur die Zahl der Zeichen). Gibt false zurück und sendet nichts, wenn schon eine Folge läuft.
     * [elapsedMs]: Zeit seit dem Start des Versuchs beim Tipp.
     */
    fun typeSignIn(server: XServer, kind: LabSignInKind, value: CharArray, elapsedMs: Long, done: (LabKeySender.TextResult) -> Unit): Boolean {
        if (!pending.compareAndSet(0, 1)) {
            value.fill('\u0000')
            return false
        }
        cancelled = false
        try {
            executor.execute {
                try {
                    Thread.sleep(SETTLE_MS)
                    val result = LabInput.asSource(LabInputSource.SIGNIN) { LabKeySender(sinkOf(server), isCancelled = { cancelled }).typeText(value) }
                    LabRun.signInKeyLine(
                        if (result.cancelled) LabSignInKeys.cancelledLine(kind, result.sent, elapsedMs) else LabSignInKeys.sentLine(kind, result.sent, elapsedMs),
                    )
                    Timber.i("Anmelde-Knopf gesendet: %d Zeichen", result.sent)
                    done(result)
                } catch (e: Exception) {
                    Timber.w("Anmelde-Knopf senden fehlgeschlagen: %s", e.javaClass.simpleName)
                } finally {
                    value.fill('\u0000')
                    activityMs = System.currentTimeMillis()
                    pending.decrementAndGet()
                }
            }
        } catch (e: Exception) {
            value.fill('\u0000')
            pending.decrementAndGet()
            return false
        }
        return true
    }

    /** Tippt [text] (wird danach überschrieben) und meldet das Ergebnis an [done]. Protokolliert wird nur die Zahl der Zeichen. */
    fun typeText(server: XServer, text: CharArray, done: (LabKeySender.TextResult) -> Unit) {
        cancelled = false
        pending.incrementAndGet()
        try {
            executor.execute {
                try {
                    Thread.sleep(SETTLE_MS)
                    val result = LabInput.asSource(LabInputSource.TEXT) { LabKeySender(sinkOf(server), isCancelled = { cancelled }).typeText(text) }
                    LabInput.textSent(result.sent, result.skipped)
                    Timber.i("Text gesendet: %d Zeichen", result.sent)
                    done(result)
                } catch (e: Exception) {
                    Timber.w("Text senden fehlgeschlagen: %s", e.javaClass.simpleName)
                } finally {
                    text.fill('\u0000')
                    pending.decrementAndGet()
                }
            }
        } catch (e: Exception) {
            text.fill('\u0000')
            pending.decrementAndGet()
        }
    }
}
