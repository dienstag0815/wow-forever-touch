package app.gamenative.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.screen.wow.SettingActionRow
import app.gamenative.ui.screen.wow.SettingSwitchRow
import app.gamenative.ui.screen.wow.launcher.ExpandRow
import app.gamenative.ui.screen.wow.launcher.InsetPanel
import app.gamenative.ui.screen.wow.launcher.LauncherColors
import app.gamenative.ui.screen.wow.launcher.asString
import app.gamenative.ui.screen.wow.launcher.ornateFrame
import kotlinx.coroutines.delay

private val BannerFill = LauncherColors.Surface
private val TextLight = LauncherColors.Body

/** Ein neuerer Launcher steht bereit oder wird gerade geladen/installiert (für Pille, Kachel "Neuigkeiten" und Hero). */
internal fun UpdateState.isOffered(): Boolean =
    this is UpdateState.Available || this is UpdateState.Downloading || this is UpdateState.Installing

/** Das angebotene Update, soweit bekannt (für die Änderungsliste auf der Seite "Neuigkeiten"). */
internal fun offeredInfo(state: UpdateState): UpdateInfo? = when (state) {
    is UpdateState.Available -> state.info
    is UpdateState.Downloading -> state.info
    is UpdateState.Installing -> state.info
    is UpdateState.Error -> state.info
    is UpdateState.Installed -> state.info
    else -> null
}

/**
 * Launcher-Update als schmaler Streifen über den Kacheln (statt der großen Karte): Angebot mit "Details" (öffnet die Seite
 * "Neuigkeiten" mit der Änderungsliste) und "Installieren", dazu Download- und Installationsfortschritt, Fehler und
 * "Update installiert". Ohne Update und während der Suche zeigt er nichts. [canInstall] ist false, solange ein Spiel startet
 * oder Dateien geladen werden, damit nie während des Spiels ein Update beginnt.
 *
 * Mit dem Schalter "Updates automatisch installieren" lädt der Streifen ein angebotenes Update sofort und installiert es nach
 * einem 5-Sekunden-Hinweis mit "Abbrechen", sobald [UpdateLogic.autoInstallDecision] es erlaubt: App im Vordergrund, kein
 * Spielstart oder Download ([canInstall]), kein Autostart-Countdown ([autoStartCounting]) und nichts über der Startseite
 * ([homeCovered], z. B. Einstellungen oder eine Seite hinter den Kacheln, wo der Hinweis nicht zu sehen wäre).
 *
 * [showCheckResults] false: "Du hast die neueste Version" und Suchfehler (Fehler ohne Update) zeigt der Streifen nicht, weil der
 * Knopf "Nach Updates suchen" der Startseite sie trägt (HomeUpdateButton.kt).
 */
@Composable
internal fun UpdateBanner(
    canInstall: Boolean,
    autoStartCounting: Boolean = false,
    homeCovered: Boolean = false,
    showCheckResults: Boolean = true,
    onDetails: () -> Unit,
) {
    val context = LocalContext.current
    val state by UpdateManager.state.collectAsState()
    val autoOn by remember { UpdateManager.autoInstallState(context) }.collectAsState()
    val ready by UpdateManager.readyInfo.collectAsState()
    val held by UpdateManager.autoHeld.collectAsState()
    val phase by UpdateManager.checkPhase.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val offered = (state as? UpdateState.Available)?.info
    val installAllowed = remember(resumed, offered) { UpdateManager.canInstallPackages(context) }
    val downloaded = offered != null && ready == offered
    val action = UpdateLogic.autoInstallDecision(
        enabled = autoOn,
        updateOffered = offered != null && held != offered.versionName,
        downloaded = downloaded,
        launcherVisible = resumed && !homeCovered,
        gameBusy = !canInstall,
        countdownRunning = autoStartCounting,
        installAllowed = installAllowed,
    )
    // Seconds left of the announced automatic installation; null = no notice.
    var noticeLeft by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(action, offered, phase) {
        when (action) {
            AutoInstallAction.Download -> UpdateManager.prefetch(context)
            AutoInstallAction.Install -> try {
                for (left in UpdateLogic.AUTO_INSTALL_NOTICE_SECONDS downTo 1) {
                    noticeLeft = left
                    delay(1000)
                }
                noticeLeft = null
                UpdateManager.install(context)
            } finally {
                noticeLeft = null
            }
            else -> { noticeLeft = null }
        }
    }
    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> Unit
        UpdateState.UpToDate -> if (showCheckResults) {
            BannerFrame(error = false) {
                BannerText(stringResource(R.string.launcher_banner_up_to_date, BuildConfig.VERSION_NAME), modifier = Modifier.weight(1f))
                BannerButton(stringResource(R.string.launcher_close), solid = false) { UpdateManager.dismiss() }
            }
        }
        is UpdateState.Available -> BannerFrame(error = false) {
            val left = noticeLeft
            Column(modifier = Modifier.weight(1f)) {
                if (left != null) {
                    BannerText(stringResource(R.string.launcher_banner_installing_in, left), bold = true)
                    Text(
                        text = stringResource(R.string.launcher_banner_restarts, s.info.versionName),
                        fontSize = 11.sp,
                        color = LauncherColors.Subtle,
                    )
                } else {
                    BannerText(stringResource(R.string.launcher_banner_new_version, UpdateLogic.versionTransition(BuildConfig.VERSION_NAME, s.info.versionName)), bold = true)
                    Text(
                        text = when {
                            !canInstall -> stringResource(R.string.launcher_news_offer_blocked)
                            autoOn && !installAllowed -> stringResource(R.string.launcher_banner_needs_permission)
                            autoOn && downloaded -> stringResource(R.string.launcher_banner_downloaded, UpdateLogic.megabytes(s.info.size))
                            else -> stringResource(R.string.launcher_banner_kept, UpdateLogic.megabytes(s.info.size))
                        },
                        fontSize = 11.sp,
                        color = LauncherColors.Subtle,
                    )
                }
            }
            if (left != null) {
                BannerButton(stringResource(R.string.launcher_cancel), solid = false) { UpdateManager.holdAutoInstall(s.info.versionName) }
            } else {
                BannerButton(stringResource(R.string.launcher_banner_details), solid = false, onClick = onDetails)
                BannerButton(stringResource(R.string.launcher_banner_install), solid = true, enabled = canInstall) { UpdateManager.install(context) }
            }
        }
        is UpdateState.Downloading -> BannerFrame(error = false) {
            Column(modifier = Modifier.weight(1f)) {
                BannerText(stringResource(R.string.launcher_banner_downloading, s.info.versionName, (s.fraction * 100).toInt(), UpdateLogic.megabytes(s.info.size)), bold = true)
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(progress = { s.fraction.coerceIn(0f, 1f) }, color = LauncherColors.Gold, modifier = Modifier.fillMaxWidth())
            }
            BannerButton(stringResource(R.string.launcher_cancel), solid = false) { UpdateManager.cancel() }
        }
        is UpdateState.Installing -> BannerFrame(error = false) {
            Column(modifier = Modifier.weight(1f)) {
                BannerText(s.info?.let { stringResource(R.string.launcher_banner_installing_version, it.versionName) } ?: stringResource(R.string.launcher_home_installing_desc), bold = true)
                Spacer(modifier = Modifier.height(6.dp))
                val fraction = s.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, color = LauncherColors.Gold, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(color = LauncherColors.Gold, modifier = Modifier.fillMaxWidth())
                }
                Text(s.text.asString(), fontSize = 11.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
            }
            BannerButton(stringResource(R.string.launcher_banner_reset), solid = false) { UpdateManager.dismiss() }
        }
        is UpdateState.Error -> if (showCheckResults || s.info != null) {
            BannerFrame(error = true) {
                Column(modifier = Modifier.weight(1f)) {
                    BannerText(stringResource(R.string.launcher_banner_failed), bold = true, color = LauncherColors.Err)
                    Text(s.message.asString(), fontSize = 11.sp, color = LauncherColors.ErrText, maxLines = 2)
                }
                if (s.info != null) {
                    BannerButton(stringResource(R.string.launcher_home_try_again), solid = false, enabled = canInstall) { UpdateManager.install(context) }
                }
                BannerButton(stringResource(R.string.launcher_close), solid = false) { UpdateManager.dismiss() }
            }
        }
        is UpdateState.Installed -> BannerFrame(error = false) {
            BannerText(stringResource(R.string.launcher_banner_done, s.versionName), modifier = Modifier.weight(1f))
            BannerButton(stringResource(R.string.launcher_ok), solid = true) { UpdateManager.dismiss() }
        }
    }
}

@Composable
private fun BannerFrame(error: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .ornateFrame(
                fill = if (error) LauncherColors.ErrFill else BannerFill,
                outer = if (error) LauncherColors.Err.copy(alpha = 0.75f) else LauncherColors.Gold,
                inner = if (error) LauncherColors.BadBorder else LauncherColors.Bronze,
                corners = false,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun BannerText(text: String, modifier: Modifier = Modifier, bold: Boolean = false, color: Color = TextLight) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun BannerButton(text: String, solid: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (solid) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LauncherColors.Gold, contentColor = LauncherColors.Ink),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    } else {
        TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(text, fontSize = 13.sp, color = if (enabled) LauncherColors.Gold else LauncherColors.Subtle)
        }
    }
}

/** Zeilen "Updates automatisch installieren", "Nach Updates suchen" und, eingeklappt unter "Erweitert", die Update-Adresse. */
@Composable
internal fun UpdateSettingsBlock(enabled: Boolean) {
    val context = LocalContext.current
    var advancedOpen by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(UpdateSettings.baseUrlOverride(context)) }
    val state by UpdateManager.state.collectAsState()
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing
    val autoInstall by remember { UpdateManager.autoInstallState(context) }.collectAsState()

    if (showChangelog) {
        ChangelogDialog(UpdateManager.bundledChangelog(context)) { showChangelog = false }
    }
    Column {
        SettingSwitchRow(
            title = stringResource(R.string.launcher_update_auto_title),
            checked = autoInstall,
            description = stringResource(R.string.launcher_update_auto_desc),
            enabled = enabled,
        ) { UpdateManager.setAutoInstall(context, it) }
        SettingActionRow(
            title = stringResource(R.string.launcher_home_check_updates),
            description = stringResource(R.string.launcher_update_check_desc),
            actionText = stringResource(if (state is UpdateState.Checking) R.string.launcher_home_checking else R.string.launcher_action_check),
            enabled = enabled && !busy,
            primary = true,
        ) { UpdateManager.checkManually(context) }
        SettingActionRow(
            title = stringResource(R.string.launcher_update_changes_title),
            description = stringResource(R.string.launcher_update_changes_desc),
            actionText = stringResource(R.string.launcher_action_show),
        ) { showChangelog = true }
        UpdateSourceBlock(enabled)
        ExpandRow(
            title = stringResource(R.string.launcher_update_advanced),
            description = stringResource(R.string.launcher_update_advanced_desc),
            expanded = advancedOpen,
        ) { advancedOpen = !advancedOpen }
        if (advancedOpen) {
            InsetPanel {
                NoExtractOutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        UpdateSettings.saveBaseUrl(context, it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    label = { Text(stringResource(R.string.launcher_update_address_label, BuildConfig.UPDATE_FEED_URL), fontSize = 12.sp, color = LauncherColors.Subtle) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LauncherColors.Gold,
                        unfocusedBorderColor = LauncherColors.Bronze,
                        cursorColor = LauncherColors.Gold,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
            }
        }
    }
}
