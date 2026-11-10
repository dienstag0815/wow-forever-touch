package app.gamenative.ui.screen.wow.addons

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.screen.wow.ScopeKind
import app.gamenative.ui.screen.wow.SettingActionRow
import app.gamenative.ui.screen.wow.SettingGroupCard
import app.gamenative.ui.screen.wow.SettingNote
import app.gamenative.ui.screen.wow.SettingRow
import app.gamenative.ui.screen.wow.SettingScope
import app.gamenative.ui.screen.wow.SettingSwitchRow
import app.gamenative.ui.screen.wow.launcher.CardActionRow
import app.gamenative.ui.screen.wow.launcher.CardValueRow
import app.gamenative.ui.screen.wow.launcher.UiText
import app.gamenative.ui.screen.wow.launcher.asString
import app.gamenative.ui.theme.WowBronze
import app.gamenative.ui.theme.WowError
import app.gamenative.ui.theme.WowGold
import app.gamenative.ui.theme.WowMuted
import app.gamenative.wow.addons.AddonLink
import app.gamenative.wow.addons.AddonLinkTexts
import app.gamenative.wow.addons.AddonText
import app.gamenative.wow.addons.AddonVersion
import app.gamenative.wow.addons.CustomAddonInstall
import app.gamenative.wow.addons.CustomAddonLabels
import app.gamenative.wow.addons.CustomAddonRecord
import app.gamenative.wow.addons.CustomAddonService
import app.gamenative.wow.addons.CustomAddonText
import app.gamenative.wow.addons.CustomAddonView
import app.gamenative.wow.addons.CustomInstallOutcome
import app.gamenative.wow.addons.DownloadPlan
import app.gamenative.wow.addons.EnableState
import app.gamenative.wow.addons.OkAddonHttp
import app.gamenative.wow.addons.PlanResult
import app.gamenative.wow.addons.Prepared
import app.gamenative.wow.addons.PrepareResult
import app.gamenative.wow.addons.UpdateCheck
import app.gamenative.wow.addons.resolveAddonLink
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private data class CustomMessage(val text: UiText, val error: Boolean)

private val DIALOG_FILL = Color(0xFF1B2838)
private val DIALOG_TEXT = Color(0xFFE2E8F0)

/** Abzeichen "Nur hier · ab nächstem Start": Änderungen an Addons gelten erst beim nächsten Spielstart. */
private val NEXT_START = SettingScope(ScopeKind.OnlyHere, nextStart = true)

/**
 * Bereich "Eigene Addons" der Addon-Seite (Auftrag addonlink): Addon per Link oder aus einer .zip-Datei einbauen, mit Prüfung
 * vor dem Einbau, Liste, An/Aus, Entfernen und bei GitHub-Quellen "Auf Update prüfen". Regeln und Dateilogik stehen in
 * `wow/addons/` (CustomAddons.kt, CustomAddonScan.kt, CustomAddonInstall.kt, AddonLinkResolver.kt), hier ist nur Anzeige.
 *
 * [otherBusy]: die Seite führt gerade eine andere Aktion aus. [onBusyChange] meldet, dass hier etwas läuft (die Seite sperrt dann
 * Zurück und die Katalog-Aktionen). [protectedFolders] sind die Ordner der kuratierten Addons (kleingeschrieben).
 *
 * Seit fold8.26 hält diese Funktion nur noch Zustand, Aktionen und Dialoge ("Host"); die Seite zeichnet die Übersicht
 * ([CustomAddonsOverviewRows]) und die Detailansicht ([CustomAddonsDetailContent]) in [content] aus dem übergebenen [CustomAddonsUi].
 */
@Composable
internal fun CustomAddonsHost(
    addonsDir: File,
    gameBusy: Boolean,
    otherBusy: Boolean,
    protectedFolders: Set<String>,
    onBusyChange: (Boolean) -> Unit,
    content: @Composable (CustomAddonsUi) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember {
        CustomAddonService(OkAddonHttp, File(context.cacheDir, "addons/custom"), log = { Timber.i("Eigene Addons: %s", it) })
    }
    val cancel = remember { AtomicBoolean(false) }

    var views by remember { mutableStateOf<List<CustomAddonView>>(emptyList()) }
    var busy by remember { mutableStateOf<UiText?>(null) }
    var message by remember { mutableStateOf<CustomMessage?>(null) }
    var linkOpen by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var linkProblem by remember { mutableStateOf<UiText?>(null) }
    var browser by remember { mutableStateOf<AddonLink.NeedsBrowser?>(null) }
    var preview by remember { mutableStateOf<Prepared?>(null) }
    var removeAsk by remember { mutableStateOf<CustomAddonRecord?>(null) }
    var updates by remember { mutableStateOf<Map<String, UpdateCheck>>(emptyMap()) }

    /** Darf jetzt eine Aktion starten? Liest den Zustand frisch (auch direkt nach dem Schließen der Vorschau). */
    fun canStart(): Boolean = !gameBusy && !otherBusy && busy == null
    val canChange = canStart() && preview == null

    suspend fun reload() {
        views = withContext(Dispatchers.IO) { service.list(addonsDir) }
    }

    LaunchedEffect(addonsDir) {
        withContext(Dispatchers.IO) { service.cleanTemp() }
        reload()
    }
    LaunchedEffect(busy, preview) { onBusyChange(busy != null || preview != null) }

    /** Führt [block] aus; nur eine Aktion zugleich, nie während das Spiel läuft. */
    fun launchJob(label: UiText, block: suspend () -> CustomMessage?) {
        if (!canStart()) return
        busy = label
        message = null
        cancel.set(false)
        scope.launch {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Eigenes Addon: Aktion fehlgeschlagen")
                CustomMessage(UiText.res(R.string.launcher_addons_failed, e.message ?: e.javaClass.simpleName), error = true)
            }
            message = result
            busy = null
            reload()
        }
    }

    suspend fun fetch(plan: DownloadPlan): PrepareResult =
        withContext(Dispatchers.IO) {
            service.prepare(plan, { done, total -> busy = AddonText.progress(done, total) }, { cancel.get() })
        }

    fun startLink(link: AddonLink) = launchJob(UiText.res(R.string.launcher_addons_busy_link)) {
        when (val planned = withContext(Dispatchers.IO) { service.plan(link) }) {
            is PlanResult.Fail -> CustomMessage(planned.reason, error = true)
            is PlanResult.Browser -> CustomMessage(planned.message, error = true)
            is PlanResult.Ok -> when (val r = fetch(planned.plan)) {
                is PrepareResult.Fail -> CustomMessage(r.reason, error = true)
                is PrepareResult.Ok -> {
                    preview = r.prepared
                    null
                }
            }
        }
    }

    fun confirmInstall(prepared: Prepared, replace: Boolean) {
        preview = null
        launchJob(UiText.res(R.string.launcher_addons_busy_install)) {
            when (val o = withContext(Dispatchers.IO) { service.install(prepared, addonsDir, protectedFolders, replace) }) {
                is CustomInstallOutcome.Installed ->
                    CustomMessage(UiText.res(R.string.launcher_addons_custom_installed, o.record.name), error = false)
                is CustomInstallOutcome.Failed -> CustomMessage(o.reason, error = true)
            }
        }
    }

    fun checkUpdate(record: CustomAddonRecord) = launchJob(UiText.res(R.string.launcher_addons_busy_check, record.name)) {
        val check = withContext(Dispatchers.IO) { service.checkUpdate(record) }
        updates = updates + (record.id to check)
        when (check) {
            is UpdateCheck.Newer ->
                CustomMessage(UiText.res(R.string.launcher_addons_custom_newer, record.name, AddonVersion.display(check.latestTag)), error = false)
            is UpdateCheck.Current -> CustomMessage(UiText.res(R.string.launcher_addons_custom_current, record.name), error = false)
            is UpdateCheck.Fail -> CustomMessage(UiText.res(R.string.launcher_addons_name_colon, record.name, check.reason), error = true)
            is UpdateCheck.NotApplicable -> CustomMessage(UiText.res(R.string.launcher_addons_custom_github_only, record.name), error = true)
        }
    }

    fun update(record: CustomAddonRecord, newer: UpdateCheck.Newer) = launchJob(UiText.res(R.string.launcher_addons_busy_download, record.name)) {
        when (val r = fetch(newer.plan)) {
            is PrepareResult.Fail -> CustomMessage(UiText.res(R.string.launcher_addons_name_colon, record.name, r.reason), error = true)
            is PrepareResult.Ok -> when (val o = withContext(Dispatchers.IO) { service.install(r.prepared, addonsDir, protectedFolders, true) }) {
                is CustomInstallOutcome.Installed -> {
                    updates = updates - record.id
                    CustomMessage(UiText.res(R.string.launcher_addons_custom_updated, o.record.name, AddonVersion.display(newer.latestTag)), error = false)
                }
                is CustomInstallOutcome.Failed -> CustomMessage(UiText.res(R.string.launcher_addons_name_colon, record.name, o.reason), error = true)
            }
        }
    }

    fun toggle(record: CustomAddonRecord, enabled: Boolean) = launchJob(UiText.res(R.string.launcher_addons_busy_switch)) {
        val problem = withContext(Dispatchers.IO) { service.setEnabled(record, addonsDir, enabled) }
        if (problem == null) {
            CustomMessage(UiText.res(if (enabled) R.string.launcher_addons_is_active else R.string.launcher_addons_is_off, record.name), error = false)
        } else {
            CustomMessage(UiText.res(R.string.launcher_addons_name_colon, record.name, problem), error = true)
        }
    }

    fun remove(record: CustomAddonRecord) = launchJob(UiText.res(R.string.launcher_addons_busy_remove, record.name)) {
        val problem = withContext(Dispatchers.IO) { service.remove(record, addonsDir, protectedFolders) }
        if (problem == null) {
            CustomMessage(UiText.res(R.string.launcher_addons_is_removed, record.name), error = false)
        } else {
            CustomMessage(UiText.res(R.string.launcher_addons_name_colon, record.name, problem), error = true)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            launchJob(UiText.res(R.string.launcher_addons_busy_read)) {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val name = displayName(context, uri)
                        val stream = context.contentResolver.openInputStream(uri)
                        if (stream == null) PrepareResult.Fail(UiText.res(R.string.launcher_addons_file_open_failed)) else stream.use { service.prepareStream(it, name) { cancel.get() } }
                    } catch (e: IOException) {
                        PrepareResult.Fail(UiText.res(R.string.launcher_addons_file_read_failed, e.message ?: e.javaClass.simpleName))
                    } catch (e: SecurityException) {
                        PrepareResult.Fail(UiText.res(R.string.launcher_addons_file_no_access))
                    }
                }
                when (result) {
                    is PrepareResult.Fail -> CustomMessage(result.reason, error = true)
                    is PrepareResult.Ok -> {
                        preview = result.prepared
                        null
                    }
                }
            }
        }
    }

    val ui = CustomAddonsUi(
        views = views,
        busyText = busy,
        messageText = message?.text,
        messageError = message?.error == true,
        canChange = canChange,
        updates = updates,
        onLink = {
            linkProblem = null
            browser = null
            linkOpen = true
        },
        onPickFile = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        onCancel = { cancel.set(true) },
        onToggle = { record, enabled -> toggle(record, enabled) },
        onCheckUpdate = { record -> checkUpdate(record) },
        onUpdate = { record, newer -> update(record, newer) },
        onRemoveAsk = { record -> removeAsk = record },
    )
    content(ui)

    if (linkOpen) {
        AlertDialog(
            onDismissRequest = { linkOpen = false },
            title = { Text(stringResource(R.string.launcher_addons_link_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = {
                            linkText = it
                            linkProblem = null
                            browser = null
                        },
                        label = { Text(stringResource(R.string.launcher_addons_link_label)) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = DIALOG_TEXT),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        pasteFromClipboard(context)?.let {
                            linkText = it
                            linkProblem = null
                            browser = null
                        }
                    }) { Text(stringResource(R.string.launcher_addons_paste), color = WowGold, fontSize = 13.sp) }
                    linkProblem?.let { Text(it.asString(), fontSize = 13.sp, lineHeight = 17.sp, color = WowError) }
                    browser?.let { page ->
                        Text(page.message.asString(), fontSize = 13.sp, lineHeight = 17.sp, color = DIALOG_TEXT, modifier = Modifier.padding(top = 4.dp))
                        Button(
                            onClick = { openPage(context, page.pageUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text(stringResource(R.string.launcher_addons_open_page), fontWeight = FontWeight.Bold) }
                    }
                    Text(AddonLinkTexts.SUPPORTED.asString(), fontSize = 12.sp, lineHeight = 16.sp, color = WowMuted, modifier = Modifier.padding(top = 10.dp))
                }
            },
            confirmButton = {
                Button(
                    enabled = linkText.isNotBlank(),
                    onClick = {
                        when (val link = resolveAddonLink(linkText)) {
                            is AddonLink.Unsupported -> linkProblem = link.reason
                            is AddonLink.NeedsBrowser -> browser = link
                            else -> {
                                linkOpen = false
                                startLink(link)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.launcher_addons_continue), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { linkOpen = false }) { Text(stringResource(R.string.launcher_cancel), color = WowMuted) } },
            containerColor = DIALOG_FILL,
            shape = RoundedCornerShape(16.dp),
        )
    }

    preview?.let { prepared ->
        val assessments = remember(prepared) { CustomAddonInstall.assess(prepared.scan.addons, addonsDir, protectedFolders) }
        val blocked = assessments.any { it.verdict == CustomAddonInstall.Verdict.BLOCKED }
        val replacing = assessments.filter { it.verdict == CustomAddonInstall.Verdict.REPLACE }
        AlertDialog(
            onDismissRequest = {
                service.discard(prepared)
                preview = null
            },
            title = { Text(stringResource(R.string.launcher_addons_preview_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        UiText.res(R.string.launcher_addons_source_line, CustomAddonLabels.planLine(prepared.plan), AddonText.megabytes(prepared.bytes)).asString(),
                        fontSize = 12.sp,
                        color = WowMuted,
                    )
                    for (a in assessments) {
                        val found = a.addon
                        Text(found.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DIALOG_TEXT, modifier = Modifier.padding(top = 10.dp))
                        val version = found.version?.let { stringResource(R.string.launcher_addons_version_suffix, it) }.orEmpty()
                        Text(UiText.res(R.string.launcher_addons_folder_line, found.folder, version, CustomAddonText.interfaceLine(found)).asString(), fontSize = 12.sp, lineHeight = 16.sp, color = WowMuted)
                        CustomAddonText.interfaceWarning(found)?.let { Text(it.asString(), fontSize = 12.sp, lineHeight = 16.sp, color = WowGold) }
                        when (a.verdict) {
                            CustomAddonInstall.Verdict.NEW -> Text(stringResource(R.string.launcher_addons_verdict_new), fontSize = 12.sp, color = DIALOG_TEXT)
                            CustomAddonInstall.Verdict.REPLACE ->
                                Text(stringResource(R.string.launcher_addons_verdict_replace, found.folder), fontSize = 12.sp, lineHeight = 16.sp, color = WowGold)
                            CustomAddonInstall.Verdict.BLOCKED -> Text(stringResource(R.string.launcher_addons_verdict_blocked, a.reason?.asString().orEmpty()), fontSize = 12.sp, lineHeight = 16.sp, color = WowError)
                        }
                    }
                    if (replacing.isNotEmpty()) {
                        Text(
                            stringResource(R.string.launcher_addons_replace_ask, replacing.joinToString(", ") { it.addon.folder }),
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = DIALOG_TEXT,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    Text(stringResource(R.string.launcher_addons_next_start), fontSize = 12.sp, color = WowMuted, modifier = Modifier.padding(top = 10.dp))
                    if (gameBusy) Text(AddonText.BUSY_HINT.asString(), fontSize = 12.sp, lineHeight = 16.sp, color = WowError, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                Button(
                    enabled = !blocked && !gameBusy && !otherBusy && busy == null,
                    onClick = { confirmInstall(prepared, replacing.isNotEmpty()) },
                    colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(if (replacing.isNotEmpty()) R.string.launcher_addons_replace_install else R.string.launcher_addons_do_install), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    service.discard(prepared)
                    preview = null
                }) { Text(stringResource(R.string.launcher_cancel), color = WowMuted) }
            },
            containerColor = DIALOG_FILL,
            shape = RoundedCornerShape(16.dp),
        )
    }

    removeAsk?.let { record ->
        AlertDialog(
            onDismissRequest = { removeAsk = null },
            title = { Text(stringResource(R.string.launcher_addons_remove_q, record.name), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
            text = {
                Text(
                    stringResource(R.string.launcher_addons_remove_custom_body, record.folders.joinToString(", ")),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = DIALOG_TEXT,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        removeAsk = null
                        remove(record)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.launcher_addons_remove), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { removeAsk = null }) { Text(stringResource(R.string.launcher_cancel), color = WowMuted) } },
            containerColor = DIALOG_FILL,
            shape = RoundedCornerShape(16.dp),
        )
    }
}

/** Text aus der Zwischenablage, getrimmt; null, wenn leer oder nicht lesbar. */
private fun pasteFromClipboard(context: Context): String? = try {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.ifEmpty { null }
} catch (e: SecurityException) {
    null
}

/** Öffnet [url] im Browser (nur https). */
private fun openPage(context: Context, url: String) {
    if (!url.startsWith("https://")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "Eigene Addons: kein Browser gefunden")
    }
}

/** Dateiname einer gewählten Datei für die Anzeige. */
private fun displayName(context: Context, uri: Uri): String {
    val queried = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
    return queried ?: uri.lastPathSegment ?: "addon.zip"
}

/**
 * Zustand und Aktionen der eigenen Addons für die Oberfläche: [CustomAddonsHost] hält alles, die Seite zeichnet Übersicht und
 * Detailansicht daraus. [busyText] ist der laufende Schritt (null = nichts läuft), [messageText] das Ergebnis der letzten Aktion.
 */
internal class CustomAddonsUi(
    val views: List<CustomAddonView>,
    val busyText: UiText?,
    val messageText: UiText?,
    val messageError: Boolean,
    val canChange: Boolean,
    val updates: Map<String, UpdateCheck>,
    val onLink: () -> Unit,
    val onPickFile: () -> Unit,
    val onCancel: () -> Unit,
    val onToggle: (CustomAddonRecord, Boolean) -> Unit,
    val onCheckUpdate: (CustomAddonRecord) -> Unit,
    val onUpdate: (CustomAddonRecord, UpdateCheck.Newer) -> Unit,
    val onRemoveAsk: (CustomAddonRecord) -> Unit,
)

/** Karte "Eigene Addons" in der Übersicht: Link, Datei und die Zahl der eingebauten Addons (während einer Aktion stattdessen Abbrechen). */
@Composable
internal fun CustomAddonsOverviewRows(ui: CustomAddonsUi) {
    CardActionRow(stringResource(R.string.launcher_addons_add_link), stringResource(R.string.launcher_addons_link_action), enabled = ui.canChange, onClick = ui.onLink)
    CardActionRow(stringResource(R.string.launcher_addons_from_file), stringResource(R.string.launcher_addons_choose_action), enabled = ui.canChange, onClick = ui.onPickFile)
    if (ui.busyText != null) {
        CardActionRow(stringResource(R.string.launcher_addons_running_now), stringResource(R.string.launcher_cancel), danger = true, onClick = ui.onCancel)
    } else {
        CardValueRow(stringResource(R.string.launcher_addons_installed_count), ui.views.size.toString())
    }
}

/** Detailansicht der eigenen Addons: der Hinweis, Link und Datei mit Abzeichen, dann jedes Addon mit An/Aus, Update und Entfernen. */
@Composable
internal fun CustomAddonsDetailContent(ui: CustomAddonsUi) {
    SettingGroupCard(stringResource(R.string.launcher_addons_custom)) {
        SettingNote(stringResource(R.string.launcher_addons_custom_intro))
        SettingActionRow(
            title = stringResource(R.string.launcher_addons_add_link),
            description = stringResource(R.string.launcher_addons_link_desc),
            actionText = stringResource(R.string.launcher_addons_link_action),
            enabled = ui.canChange,
            scope = NEXT_START,
            onClick = ui.onLink,
        )
        SettingActionRow(
            title = stringResource(R.string.launcher_addons_from_file),
            description = stringResource(R.string.launcher_addons_file_desc),
            actionText = stringResource(R.string.launcher_addons_choose_action),
            enabled = ui.canChange,
            scope = NEXT_START,
            onClick = ui.onPickFile,
        )
        val shown = ui.busyText ?: ui.messageText
        if (shown != null) {
            Text(
                text = shown.asString(),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (ui.busyText == null && ui.messageError) WowError else WowGold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (ui.busyText != null) {
            SettingActionRow(title = stringResource(R.string.launcher_cancel), description = stringResource(R.string.launcher_addons_cancel_desc), actionText = stringResource(R.string.launcher_cancel), danger = true, onClick = ui.onCancel)
        }
    }

    for (view in ui.views) {
        val record = view.record
        val check = ui.updates[record.id]
        SettingGroupCard(record.name) {
            val state = when (view.enable) {
                EnableState.DISABLED -> stringResource(R.string.launcher_addons_state_off)
                EnableState.MIXED -> stringResource(R.string.launcher_addons_state_partly)
                EnableState.MISSING -> stringResource(R.string.launcher_addons_state_missing)
                EnableState.ENABLED -> ""
            }
            SettingRow(
                title = CustomAddonLabels.versionLine(record).asString() + state,
                description = UiText.res(R.string.launcher_addons_source_folders, CustomAddonLabels.sourceLine(record), record.folders.joinToString(", ")).asString(),
            )
            if (view.enable != EnableState.MISSING) {
                SettingSwitchRow(
                    title = stringResource(R.string.launcher_addons_active),
                    description = stringResource(R.string.launcher_addons_active_desc),
                    checked = view.enable == EnableState.ENABLED,
                    enabled = ui.canChange,
                    scope = NEXT_START,
                    onChange = { ui.onToggle(record, it) },
                )
            }
            if (check is UpdateCheck.Newer) {
                SettingActionRow(
                    title = stringResource(R.string.launcher_addons_update_to, AddonVersion.display(check.latestTag)),
                    description = stringResource(R.string.launcher_addons_old_kept, record.folders.first()),
                    actionText = stringResource(R.string.launcher_addons_update),
                    enabled = ui.canChange,
                    scope = NEXT_START,
                    onClick = { ui.onUpdate(record, check) },
                )
            } else if (record.repo != null) {
                SettingActionRow(
                    title = stringResource(R.string.launcher_addons_check_update),
                    description = stringResource(if (check is UpdateCheck.Current) R.string.launcher_addons_last_current else R.string.launcher_addons_ask_github),
                    actionText = stringResource(R.string.launcher_action_verify),
                    enabled = ui.canChange,
                    onClick = { ui.onCheckUpdate(record) },
                )
            }
            SettingActionRow(
                title = stringResource(R.string.launcher_addons_remove),
                description = stringResource(R.string.launcher_addons_deletes_backup, record.folders.joinToString(", ")),
                actionText = stringResource(R.string.launcher_addons_remove),
                danger = true,
                enabled = ui.canChange,
                onClick = { ui.onRemoveAsk(record) },
            )
        }
    }
}
