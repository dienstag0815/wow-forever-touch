package app.gamenative.ui.screen.wow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.screen.wow.addons.CustomAddonsDetailContent
import app.gamenative.ui.screen.wow.addons.CustomAddonsHost
import app.gamenative.ui.screen.wow.addons.CustomAddonsOverviewRows
import app.gamenative.ui.screen.wow.launcher.CardActionRow
import app.gamenative.ui.screen.wow.launcher.CardNoteRow
import app.gamenative.ui.screen.wow.launcher.CardSwitchRow
import app.gamenative.ui.screen.wow.launcher.CardText
import app.gamenative.ui.screen.wow.launcher.CardValueRow
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.ui.screen.wow.launcher.LauncherOverviewPage
import app.gamenative.ui.screen.wow.launcher.LauncherPage
import app.gamenative.ui.screen.wow.launcher.LauncherTexts
import app.gamenative.ui.screen.wow.launcher.OverviewCard
import app.gamenative.ui.screen.wow.launcher.PageDetail
import app.gamenative.ui.screen.wow.launcher.PageTexts
import app.gamenative.ui.screen.wow.launcher.UiText
import app.gamenative.ui.screen.wow.launcher.addonsPlan
import app.gamenative.ui.screen.wow.launcher.asString
import app.gamenative.ui.screen.wow.launcher.card
import app.gamenative.ui.screen.wow.launcher.LauncherTitle
import app.gamenative.ui.theme.WowBackgroundGradient
import app.gamenative.ui.theme.WowBronze
import app.gamenative.ui.theme.WowError
import app.gamenative.ui.theme.WowGold
import app.gamenative.ui.theme.WowMuted
import app.gamenative.wow.addons.AddonCache
import app.gamenative.wow.addons.AddonCatalogStore
import app.gamenative.wow.addons.AddonInstallState
import app.gamenative.wow.addons.AddonService
import app.gamenative.wow.addons.AddonStatus
import app.gamenative.wow.addons.AddonText
import app.gamenative.wow.addons.AddonVersion
import app.gamenative.wow.addons.CatalogEntry
import app.gamenative.wow.addons.CustomAddonInstall
import app.gamenative.wow.addons.CatalogResult
import app.gamenative.wow.addons.EnableState
import app.gamenative.wow.addons.InstallOutcome
import app.gamenative.wow.addons.LatestResult
import app.gamenative.wow.addons.OkAddonHttp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Ergebnis einer Aktion für die Meldungszeile: Text und ob es ein Fehler ist. */
private data class AddonMessage(val text: UiText, val error: Boolean)

/**
 * Seite "Addons" des Launchers: die Liste aus dem kuratierten Katalog (assets/wow/addons/katalog.json) mit Zustand und den
 * Knöpfen Installieren/Aktualisieren/Entfernen und dem Schalter "Aktiv". Geändert wird nur, wenn das Spiel nicht läuft
 * ([gameBusy]); während einer Aktion ist Zurück gesperrt, damit nichts halb bleibt. Alle Regeln (Hosts, Größen, Pfade)
 * stehen in `wow/addons/`, hier ist nur Anzeige. [addonsDir] ist `<Spielordner>/_forever_/Interface/AddOns`.
 */
@Composable
internal fun AddonsPage(addonsDir: File, gameBusy: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalogResult = remember { AddonCatalogStore.load(context) }
    val catalog = (catalogResult as? CatalogResult.Ok)?.catalog
    val catalogProblem = (catalogResult as? CatalogResult.Invalid)?.reason
    val cache = remember { AddonCache(File(context.cacheDir, "addons/api")) }
    val service = remember {
        AddonService(OkAddonHttp, cache, File(context.cacheDir, "addons/dl"), log = { Timber.i("Addons: %s", it) })
    }

    var latest by remember { mutableStateOf<Map<String, LatestResult>>(emptyMap()) }
    var statuses by remember { mutableStateOf<List<AddonStatus>>(emptyList()) }
    var working by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<UiText?>(null) }
    var message by remember { mutableStateOf<AddonMessage?>(null) }
    var removeAsk by remember { mutableStateOf<CatalogEntry?>(null) }

    var customBusy by remember { mutableStateOf(false) }

    val canChange = !gameBusy && working == null && !customBusy

    suspend fun reloadStatuses() {
        val list = catalog ?: return
        val tags = latest.mapNotNull { (id, r) -> (r as? LatestResult.Ok)?.let { id to it.picked.tag } }.toMap()
        statuses = withContext(Dispatchers.IO) { service.statuses(list, addonsDir, tags) }
    }

    suspend fun checkLatest(force: Boolean) {
        val list = catalog ?: return
        checking = true
        try {
            if (force) withContext(Dispatchers.IO) { cache.clear() }
            val found = LinkedHashMap<String, LatestResult>()
            for (entry in list.entries) {
                found[entry.id] = withContext(Dispatchers.IO) { service.latest(entry) }
                latest = found.toMap()
                reloadStatuses()
            }
        } finally {
            checking = false
        }
    }

    LaunchedEffect(Unit) {
        reloadStatuses()
        checkLatest(force = false)
    }

    /** Führt [task] auf dem Hintergrund-Thread aus; nur eine Aktion zugleich, nie während das Spiel läuft. */
    fun runTask(entry: CatalogEntry, task: () -> AddonMessage) {
        if (!canChange) return
        working = entry.id
        progress = null
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    task()
                } catch (e: Exception) {
                    Timber.w(e, "Addon-Aktion fehlgeschlagen")
                    AddonMessage(UiText.res(R.string.launcher_addons_failed, e.message ?: e.javaClass.simpleName), error = true)
                }
            }
            message = result
            working = null
            progress = null
            reloadStatuses()
        }
    }

    fun install(entry: CatalogEntry) = runTask(entry) {
        when (val o = service.install(entry, addonsDir) { done, total -> progress = AddonText.progress(done, total) }) {
            is InstallOutcome.Installed ->
                AddonMessage(UiText.res(R.string.launcher_addons_installed, entry.name, AddonVersion.display(o.record.tag)), error = false)
            is InstallOutcome.Failed -> AddonMessage(UiText.res(R.string.launcher_addons_name_colon, entry.name, o.reason), error = true)
        }
    }

    fun toggle(entry: CatalogEntry, enabled: Boolean) = runTask(entry) {
        val problem = service.setEnabled(entry, addonsDir, enabled)
        if (problem == null) {
            AddonMessage(UiText.res(if (enabled) R.string.launcher_addons_is_active else R.string.launcher_addons_is_off, entry.name), error = false)
        } else {
            AddonMessage(UiText.res(R.string.launcher_addons_name_colon, entry.name, problem), error = true)
        }
    }

    fun remove(entry: CatalogEntry) = runTask(entry) {
        val problem = service.remove(entry, addonsDir)
        if (problem == null) {
            AddonMessage(UiText.res(R.string.launcher_addons_is_removed, entry.name), error = false)
        } else {
            AddonMessage(UiText.res(R.string.launcher_addons_name_colon, entry.name, problem), error = true)
        }
    }

    var detailId by remember { mutableStateOf<String?>(null) }
    val entries = catalog?.entries.orEmpty()
    val plan = remember(entries.size) { addonsPlan(entries.size) }
    // Zurück ist während einer Aktion gesperrt, sonst bliebe ein Entpacken halb stehen.
    val backEnabled = working == null && !customBusy

    CustomAddonsHost(
        addonsDir = addonsDir,
        gameBusy = gameBusy,
        otherBusy = working != null,
        protectedFolders = remember(catalog) { CustomAddonInstall.protectedFolders(catalog) },
        onBusyChange = { customBusy = it },
    ) { custom ->
        val noGame = addonsDir.parentFile?.parentFile?.isDirectory != true
        val note = PageTexts.addonsNote(
            progress = progress,
            message = message?.text,
            messageError = message?.error == true,
            customBusy = custom.busyText,
            customMessage = custom.messageText,
            customError = custom.messageError,
            catalogProblem = catalogProblem,
            gameBusy = gameBusy,
            noGame = noGame,
            busyHint = AddonText.BUSY_HINT,
            noGameHint = AddonText.NO_GAME_HINT,
            intro = AddonText.INTRO,
        )
        val titleAddons = LauncherTexts.pageTitle(LauncherPage.Addons).asString()
        val cards = buildList<OverviewCard> {
            add(
                plan.card("note", null, onOpen = { detailId = "note" }) {
                    CardNoteRow(note.text.asString(), 3, if (note.error) LauncherColors.Err else LauncherColors.Body, hasDetail = true)
                },
            )
            entries.forEachIndexed { index, entry ->
                val status = statuses.firstOrNull { it.entry.id == entry.id }
                add(
                    plan.card("addon$index", entry.name, onOpen = { detailId = "addon:${entry.id}" }) {
                        if (status == null) {
                            CardText(stringResource(R.string.launcher_addons_checking_entry), 2)
                        } else {
                            AddonCardRows(
                                status = status,
                                latest = latest[entry.id],
                                canChange = canChange,
                                isWorking = working == entry.id,
                                onInstall = { install(entry) },
                                onToggle = { toggle(entry, it) },
                            )
                        }
                    },
                )
            }
            add(plan.card("custom", stringResource(R.string.launcher_addons_custom), onOpen = { detailId = "custom" }) { CustomAddonsOverviewRows(custom) })
        }
        val openId = detailId
        val closeDetail: () -> Unit = { detailId = null }
        val openStatus = if (openId != null && openId.startsWith("addon:")) {
            statuses.firstOrNull { it.entry.id == openId.removePrefix("addon:") }
        } else {
            null
        }
        val detail: PageDetail? = when {
            openId == "note" -> PageDetail(stringResource(R.string.launcher_addons_note), closeDetail) {
                SettingGroupCard(stringResource(R.string.launcher_addons_note)) {
                    SettingNote(AddonText.INTRO.asString())
                    if (gameBusy) SettingNote(AddonText.BUSY_HINT.asString())
                    if (noGame) SettingNote(AddonText.NO_GAME_HINT.asString())
                    if (catalogProblem != null) SettingNote(UiText.res(R.string.launcher_catalog_invalid, catalogProblem).asString())
                    val shown = progress ?: message?.text
                    if (shown != null) {
                        Text(
                            text = shown.asString(),
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (progress == null && message?.error == true) WowError else WowGold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            openId == "custom" -> PageDetail(stringResource(R.string.launcher_addons_custom), closeDetail) { CustomAddonsDetailContent(custom) }
            openStatus != null -> PageDetail(openStatus.entry.name, closeDetail) {
                AddonCard(
                    status = openStatus,
                    latest = latest[openStatus.entry.id],
                    canChange = canChange,
                    isWorking = working == openStatus.entry.id,
                    onInstall = { install(openStatus.entry) },
                    onToggle = { toggle(openStatus.entry, it) },
                    onRemove = { removeAsk = openStatus.entry },
                )
            }
            else -> null
        }
        LauncherOverviewPage(
            title = titleAddons,
            onBack = onBack,
            cards = cards,
            detail = detail,
            backEnabled = backEnabled,
            headerAction = {
                TextButton(onClick = { scope.launch { checkLatest(force = true) } }, enabled = !checking && working == null && catalog != null) {
                    Text(stringResource(if (checking) R.string.launcher_addons_checking_btn else R.string.launcher_addons_check_again), color = WowGold, fontSize = 14.sp)
                }
            },
        )
    }

    removeAsk?.let { entry ->
        AlertDialog(
            onDismissRequest = { removeAsk = null },
            title = { Text(stringResource(R.string.launcher_addons_remove_q, entry.name), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
            text = {
                Text(
                    stringResource(R.string.launcher_addons_remove_body, entry.folders.joinToString(", ")),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFFE2E8F0),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        removeAsk = null
                        remove(entry)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.launcher_addons_remove), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { removeAsk = null }) { Text(stringResource(R.string.launcher_cancel), color = WowMuted) } },
            containerColor = Color(0xFF1B2838),
            shape = RoundedCornerShape(16.dp),
        )
    }
}

/**
 * Zeilen der Karte eines kuratierten Addons in der Übersicht: Zustand, Installieren oder Aktualisieren, Schalter Aktiv. Beschreibung,
 * Herkunft, Lizenz und Entfernen stehen in der Detailansicht ([AddonCard]).
 */
@Composable
private fun AddonCardRows(
    status: AddonStatus,
    latest: LatestResult?,
    canChange: Boolean,
    isWorking: Boolean,
    onInstall: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val installed = status.state == AddonInstallState.INSTALLED || status.state == AddonInstallState.UPDATE_AVAILABLE
    CardValueRow(title = AddonText.statusTitle(status).asString())
    when (status.state) {
        AddonInstallState.NOT_INSTALLED -> CardActionRow(
            title = stringResource(R.string.launcher_addons_install),
            actionText = stringResource(if (isWorking) R.string.launcher_addons_running else R.string.launcher_addons_get),
            enabled = canChange && latest is LatestResult.Ok,
            onClick = onInstall,
            primary = true,
        )
        AddonInstallState.UPDATE_AVAILABLE -> CardActionRow(
            title = stringResource(R.string.launcher_addons_update),
            actionText = stringResource(if (isWorking) R.string.launcher_addons_running else R.string.launcher_addons_get),
            enabled = canChange && latest is LatestResult.Ok,
            onClick = onInstall,
            primary = true,
        )
        AddonInstallState.FOREIGN -> CardText(stringResource(R.string.launcher_addons_foreign_short), 2)
        AddonInstallState.INSTALLED -> Unit
    }
    if (installed) {
        CardSwitchRow(title = stringResource(R.string.launcher_addons_active), checked = status.enable == EnableState.ENABLED, enabled = canChange, onChange = onToggle)
    }
}

@Composable
private fun AddonCard(
    status: AddonStatus,
    latest: LatestResult?,
    canChange: Boolean,
    isWorking: Boolean,
    onInstall: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val entry = status.entry
    val installed = status.state == AddonInstallState.INSTALLED || status.state == AddonInstallState.UPDATE_AVAILABLE
    SettingGroupCard(entry.name) {
        // Bei installiertem Addon steht unter dem Zustand, wenn die neueste Version nicht zu erfragen war.
        val unreachable = if (installed && latest is LatestResult.Fail) "\n" + stringResource(R.string.launcher_addons_latest_unknown, latest.reason.asString()) else ""
        SettingRow(title = AddonText.statusTitle(status).asString(), description = entry.summary + unreachable)
        when (status.state) {
            AddonInstallState.NOT_INSTALLED -> SettingActionRow(
                title = stringResource(R.string.launcher_addons_install),
                description = AddonText.latestLine(latest).asString(),
                actionText = stringResource(if (isWorking) R.string.launcher_addons_running else R.string.launcher_addons_install),
                enabled = canChange && latest is LatestResult.Ok,
                onClick = onInstall,
                primary = true,
            )
            AddonInstallState.UPDATE_AVAILABLE -> SettingActionRow(
                title = stringResource(R.string.launcher_addons_update),
                description = AddonText.latestLine(latest).asString(),
                actionText = stringResource(if (isWorking) R.string.launcher_addons_running else R.string.launcher_addons_update),
                enabled = canChange && latest is LatestResult.Ok,
                onClick = onInstall,
                primary = true,
            )
            AddonInstallState.FOREIGN -> SettingNote(stringResource(R.string.launcher_addons_foreign_long, entry.folders.first()))
            AddonInstallState.INSTALLED -> Unit
        }
        if (installed) {
            SettingSwitchRow(
                title = stringResource(R.string.launcher_addons_active),
                description = stringResource(R.string.launcher_addons_active_desc),
                checked = status.enable == EnableState.ENABLED,
                enabled = canChange,
                onChange = onToggle,
            )
            SettingActionRow(
                title = stringResource(R.string.launcher_addons_remove),
                description = stringResource(R.string.launcher_addons_deletes, entry.folders.joinToString(", ")),
                actionText = stringResource(R.string.launcher_addons_remove),
                danger = true,
                enabled = canChange,
                onClick = onRemove,
            )
        }
        SettingNote(stringResource(R.string.launcher_addons_why, entry.why, entry.license, AddonText.sourceLabel(entry).asString()))
    }
}
