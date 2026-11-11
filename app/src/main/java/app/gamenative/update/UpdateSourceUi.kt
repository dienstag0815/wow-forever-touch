package app.gamenative.update

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.screen.wow.launcher.ActionButton
import app.gamenative.ui.screen.wow.launcher.ActionKind
import app.gamenative.ui.screen.wow.launcher.InsetPanel
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.ui.screen.wow.launcher.PillTone
import app.gamenative.ui.screen.wow.launcher.RadioCard
import app.gamenative.ui.screen.wow.launcher.SegmentChoice
import app.gamenative.ui.screen.wow.launcher.StatusChip
import app.gamenative.ui.screen.wow.launcher.StatusLine
import app.gamenative.ui.screen.wow.launcher.UiText
import app.gamenative.ui.screen.wow.launcher.asString
import kotlinx.coroutines.launch

/** Outcome line of the source form: [tone] Ok = green, Error = red with the plain reason, Info = busy. */
private data class SourceNote(val text: UiText, val tone: PillTone)

private fun okNote(text: UiText) = SourceNote(text, PillTone.Ok)

private fun errNote(text: UiText) = SourceNote(text, PillTone.Error)

/** Kurzform der Quelle für die Übersicht: Art und, bei eigener Quelle oder Tester, der Server. */
internal data class SourceShort(val kind: Kind, val host: String?) {
    enum class Kind { Official, Own, Tester }
}

internal fun updateSourceShort(form: UpdateSettings.SourceForm): SourceShort {
    val ref = (UpdateSourceLogic.parseRepo(form.kind, form.url) as? RepoParse.Ok)?.ref?.takeIf { form.active }
    return when {
        ref != null && form.tester -> SourceShort(SourceShort.Kind.Tester, ref.authority)
        ref != null -> SourceShort(SourceShort.Kind.Own, ref.authority)
        else -> SourceShort(SourceShort.Kind.Official, null)
    }
}

/** Wert der Zeile "Update-Quelle" in der Karte "Start und Updates": Offiziell, Eigene · Server oder Tester · Server. */
@Composable
internal fun updateSourceValue(form: UpdateSettings.SourceForm): String {
    val short = updateSourceShort(form)
    return when (short.kind) {
        SourceShort.Kind.Tester -> stringResource(R.string.launcher_source_value_tester, short.host.orEmpty())
        SourceShort.Kind.Own -> stringResource(R.string.launcher_source_value_own, short.host.orEmpty())
        SourceShort.Kind.Official -> stringResource(R.string.launcher_source_value_official)
    }
}

/** Ausführlicher Wert im aufgeklappten Block. */
@Composable
internal fun updateSourceSummary(form: UpdateSettings.SourceForm): String {
    val activeRef = (UpdateSourceLogic.parseRepo(form.kind, form.url) as? RepoParse.Ok)?.ref?.takeIf { form.active }
    return when {
        activeRef != null && form.tester -> stringResource(R.string.launcher_tester_active, activeRef.authority)
        activeRef != null -> stringResource(R.string.launcher_source_active, activeRef.displayUrl)
        else -> stringResource(R.string.launcher_source_official)
    }
}

/** Farbe des Chips "Aktiv: ..." je Quelle: offiziell grün, Tester und eigener Server blau (Abweichung vom Standard, aber kein Fehler). */
internal fun sourceChipTone(kind: SourceShort.Kind): PillTone = if (kind == SourceShort.Kind.Official) PillTone.Ok else PillTone.Info

/**
 * Settings → Updates → "Update-Quelle": oben der Titel mit dem Chip der aktiven Quelle, darunter drei Auswahlkarten (Offiziell,
 * Tester mit Code, Eigener Server). Nur die gewählte Art zeigt ihre Felder in einer eingerückten Fläche: der Tester ein Feld für den
 * Code, der eigene Server Art, Adresse, Token und Pre-Releases. Offiziell hat nichts weiter; die Karte wählen setzt eine aktive
 * Fremdquelle zurück. Das Ergebnis steht als Statuszeile in Farbe darunter. The token field is masked and never shows the stored
 * value; the stored token is only replaced or removed here.
 */
@Composable
internal fun UpdateSourceBlock(enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by UpdateManager.state.collectAsState()
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing

    var form by remember { mutableStateOf(UpdateSettings.sourceForm(context)) }
    var mode by remember { mutableStateOf(updateSourceShort(form).kind) }
    var kind by remember { mutableStateOf(form.kind) }
    var url by remember { mutableStateOf(form.url) }
    var prerelease by remember { mutableStateOf(form.includePrereleases) }
    var tokenInput by remember { mutableStateOf("") }
    var tokenStored by remember { mutableStateOf(UpdateSettings.hasSourceToken(context)) }
    var testing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<SourceNote?>(null) }
    var codeInput by remember { mutableStateOf("") }

    val canEdit = enabled && !busy && !testing
    val active = updateSourceShort(form)

    /** Prüft die Verbindung zur Quelle und schreibt das Ergebnis in die Statuszeile (grün: Server und neueste Version, rot: Klartext). */
    fun connect(ref: RepoRef, includePrereleases: Boolean, token: String?) {
        testing = true
        note = SourceNote(UiText.res(R.string.launcher_source_connecting, ref.authority), PillTone.Info)
        scope.launch {
            note = when (val result = UpdateManager.testSource(CustomSource(ref, includePrereleases), token)) {
                is SourceResult.Failed -> errNote(result.reason)
                is SourceResult.Found -> {
                    val newer = UpdateLogic.isNewer(result.info, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
                    okNote(
                        UiText.res(
                            if (newer) R.string.launcher_source_connected_newer else R.string.launcher_source_connected,
                            ref.authority,
                            result.info.versionName,
                        ),
                    )
                }
            }
            testing = false
        }
    }

    // Beim Öffnen im Tester-Modus einmal prüfen, damit die letzte Prüfung (grün oder rot) schon dasteht.
    LaunchedEffect(active.kind) {
        val ref = (UpdateSourceLogic.parseRepo(form.kind, form.url) as? RepoParse.Ok)?.ref
        if (active.kind == SourceShort.Kind.Tester && note == null && !testing && ref != null) {
            connect(ref, true, UpdateSettings.sourceToken(context))
        }
    }

    fun selectOfficial() {
        mode = SourceShort.Kind.Official
        if (!form.active) {
            note = null
            return
        }
        if (!UpdateManager.canChangeSource()) {
            note = errNote(UiText.res(R.string.launcher_source_busy))
            return
        }
        val wasTester = form.tester
        UpdateSettings.clearCustomSource(context)
        form = UpdateSettings.sourceForm(context)
        kind = form.kind
        url = ""
        prerelease = false
        tokenInput = ""
        codeInput = ""
        tokenStored = false
        note = okNote(UiText.res(if (wasTester) R.string.launcher_tester_end_done else R.string.launcher_source_reset_done))
        UpdateManager.sourceChanged(context)
    }

    fun parsed(): RepoRef? = when (val p = UpdateSourceLogic.parseRepo(kind, url)) {
        is RepoParse.Ok -> p.ref
        is RepoParse.Invalid -> {
            note = errNote(p.reason)
            null
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.launcher_source_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LauncherColors.TextHead,
                modifier = Modifier.weight(1f),
            )
            StatusChip(stringResource(R.string.launcher_source_chip, updateSourceValue(form)), sourceChipTone(active.kind))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioCard(
                title = stringResource(R.string.launcher_source_card_official),
                description = stringResource(R.string.launcher_source_card_official_desc),
                selected = mode == SourceShort.Kind.Official,
                enabled = canEdit,
                onSelect = ::selectOfficial,
            )
            RadioCard(
                title = stringResource(R.string.launcher_source_card_tester),
                description = stringResource(R.string.launcher_source_card_tester_desc),
                selected = mode == SourceShort.Kind.Tester,
                enabled = canEdit,
            ) {
                mode = SourceShort.Kind.Tester
                note = null
            }
            RadioCard(
                title = stringResource(R.string.launcher_source_card_own),
                description = stringResource(R.string.launcher_source_card_own_desc),
                selected = mode == SourceShort.Kind.Own,
                enabled = canEdit,
            ) {
                mode = SourceShort.Kind.Own
                note = null
            }
        }
        when (mode) {
            SourceShort.Kind.Official -> Unit
            SourceShort.Kind.Tester -> if (active.kind == SourceShort.Kind.Tester) {
                // Tester-Modus läuft: nur Status und zwei Knöpfe, weder Adresse noch Token noch Schalter (Pre-Releases sind fest an).
                InsetPanel {
                    Text(
                        stringResource(R.string.launcher_tester_status, active.host.orEmpty()),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LauncherColors.TextHead,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton(
                            text = stringResource(if (testing) R.string.launcher_source_testing else R.string.launcher_source_test),
                            enabled = canEdit,
                            modifier = Modifier.weight(1f),
                        ) {
                            val ref = (UpdateSourceLogic.parseRepo(form.kind, form.url) as? RepoParse.Ok)?.ref ?: return@ActionButton
                            connect(ref, true, UpdateSettings.sourceToken(context))
                        }
                        ActionButton(
                            text = stringResource(R.string.launcher_tester_end),
                            kind = ActionKind.Danger,
                            enabled = canEdit,
                            modifier = Modifier.weight(1f),
                            onClick = ::selectOfficial,
                        )
                    }
                    TesterVersionsPanel(canEdit)
                }
            } else
    InsetPanel {
                    Text(stringResource(R.string.launcher_tester_desc), fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Muted)
                    SourceField(
                        value = codeInput,
                        onChange = { codeInput = it },
                        label = stringResource(R.string.launcher_tester_code_label),
                        placeholder = "WFT1-...",
                        enabled = canEdit,
                        secret = true,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton(
                            text = stringResource(R.string.launcher_tester_paste_action),
                            enabled = canEdit,
                            modifier = Modifier.weight(1f),
                        ) {
                            val pasted = clipboardText(context)
                            if (pasted.isNullOrBlank()) {
                                note = errNote(UiText.res(R.string.launcher_tester_err_clipboard))
                            } else {
                                codeInput = pasted.trim()
                                note = null
                            }
                        }
                        ActionButton(
                            text = stringResource(R.string.launcher_source_apply_action),
                            kind = ActionKind.Primary,
                            enabled = canEdit && codeInput.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (!UpdateManager.canChangeSource()) {
                                note = errNote(UiText.res(R.string.launcher_source_busy))
                                return@ActionButton
                            }
                            when (val decoded = TesterCode.decode(codeInput)) {
                                is TesterCodeResult.Invalid -> note = errNote(decoded.reason)
                                is TesterCodeResult.Ok -> {
                                    val data = decoded.data
                                    val saved = runCatching {
                                        UpdateSettings.saveCustomSource(context, data.repo, includePrereleases = true, token = data.token, tester = true)
                                    }
                                    if (saved.isFailure) {
                                        note = errNote(UiText.res(R.string.launcher_source_save_failed))
                                    } else {
                                        // The code is not kept: the field is emptied, the token lives sealed in the Keystore like a typed one.
                                        codeInput = ""
                                        form = UpdateSettings.sourceForm(context)
                                        kind = form.kind
                                        url = form.url
                                        prerelease = form.includePrereleases
                                        tokenInput = ""
                                        tokenStored = UpdateSettings.hasSourceToken(context)
                                        connect(data.repo, true, data.token)
                                        UpdateManager.sourceChanged(context)
                                    }
                                }
                            }
                        }
                    }
                }
            SourceShort.Kind.Own -> InsetPanel {
                Text(stringResource(R.string.launcher_source_own_hint), fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Muted)
                Text(stringResource(R.string.launcher_source_kind_label), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LauncherColors.Body)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((value, label) in listOf(
                        SourceKind.Gitea to R.string.launcher_source_kind_gitea,
                        SourceKind.GitHub to R.string.launcher_source_kind_github,
                    )) {
                        SegmentChoice(stringResource(label), selected = kind == value, enabled = canEdit, modifier = Modifier.weight(1f)) { kind = value }
                    }
                }
                SourceField(
                    value = url,
                    onChange = { url = it },
                    label = stringResource(R.string.launcher_source_url_label),
                    placeholder = "https://host/owner/repo",
                    enabled = canEdit,
                    keyboard = KeyboardType.Uri,
                )
                SourceField(
                    value = tokenInput,
                    onChange = { tokenInput = it },
                    label = stringResource(R.string.launcher_source_token_label),
                    placeholder = if (tokenStored) {
                        UpdateSourceLogic.maskToken("x".repeat(12)) + " " + stringResource(R.string.launcher_source_token_saved)
                    } else {
                        ""
                    },
                    enabled = canEdit,
                    secret = true,
                )
                if (tokenStored) {
                    ActionButton(
                        text = stringResource(R.string.launcher_source_token_remove),
                        kind = ActionKind.Danger,
                        enabled = canEdit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        runCatching { UpdateSettings.saveSourceToken(context, "") }
                        tokenStored = false
                        tokenInput = ""
                        note = null
                    }
                }
                PanelSwitch(
                    title = stringResource(R.string.launcher_source_prerelease_title),
                    description = stringResource(R.string.launcher_source_prerelease_desc),
                    checked = prerelease,
                    enabled = canEdit,
                ) { prerelease = it }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        text = stringResource(if (testing) R.string.launcher_source_testing else R.string.launcher_source_test),
                        enabled = canEdit,
                        modifier = Modifier.weight(1f),
                    ) {
                        val ref = parsed() ?: return@ActionButton
                        connect(ref, prerelease, tokenInput.trim().ifEmpty { UpdateSettings.sourceToken(context) })
                    }
                    ActionButton(
                        text = stringResource(R.string.launcher_source_apply_action),
                        kind = ActionKind.Primary,
                        enabled = canEdit,
                        modifier = Modifier.weight(1f),
                    ) {
                        val ref = parsed() ?: return@ActionButton
                        if (!UpdateManager.canChangeSource()) {
                            note = errNote(UiText.res(R.string.launcher_source_busy))
                            return@ActionButton
                        }
                        val token = tokenInput.trim().ifEmpty { null }
                        val saved = runCatching { UpdateSettings.saveCustomSource(context, ref, prerelease, token) }
                        if (saved.isFailure) {
                            note = errNote(UiText.res(R.string.launcher_source_save_failed))
                            return@ActionButton
                        }
                        tokenStored = UpdateSettings.hasSourceToken(context)
                        tokenInput = ""
                        url = ref.displayUrl
                        form = UpdateSettings.sourceForm(context)
                        connect(ref, prerelease, token ?: UpdateSettings.sourceToken(context))
                        UpdateManager.sourceChanged(context)
                    }
                }
            }
        }
        note?.let { StatusLine(it.text.asString(), it.tone, modifier = Modifier.padding(horizontal = 14.dp)) }
    }
}

/** Eingabefeld der Quellen-Formulare (maskiert bei [secret]); die Beschriftung steht im Feld, nicht daneben. */
@Composable
private fun SourceField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Password,
) {
    NoExtractOutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        label = { Text(label, fontSize = 12.sp, color = LauncherColors.Subtle) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, fontSize = 13.sp, color = LauncherColors.Subtle) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LauncherColors.Gold,
            unfocusedBorderColor = LauncherColors.Bronze,
            cursorColor = LauncherColors.Gold,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        ),
    )
}

/** Schalter innerhalb einer eingerückten Fläche (ohne Trennlinie): die ganze Zeile schaltet um. */
@Composable
private fun PanelSwitch(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = LauncherColors.Body)
            Text(description, fontSize = 14.sp, lineHeight = 19.sp, color = LauncherColors.Muted)
        }
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

/** Text on the clipboard (first item), or null when empty or unreadable. */
private fun clipboardText(context: Context): String? = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()
