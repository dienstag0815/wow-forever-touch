package app.gamenative.ui.screen.wow.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.update.ChangeKind
import app.gamenative.update.Changelog
import app.gamenative.update.ChangelogEntry
import app.gamenative.update.ChangelogList
import app.gamenative.update.UpdateLogic
import app.gamenative.update.UpdateManager
import app.gamenative.update.UpdateState
import app.gamenative.update.offeredInfo

/**
 * Seite "Neuigkeiten" als Übersicht: oben, wenn vorhanden, das angebotene Launcher-Update (mit "Jetzt aktualisieren") und der
 * Hinweis "Neu dabei" (mit "Verstanden"), dann die neueste Version groß (Neu, Verbessert, Behoben nebeneinander, die ersten Punkte),
 * daneben die zwei Vorgänger kurz und "Alle Änderungen". Die ganze Liste, die Änderungen seit der eigenen Version und jede einzelne
 * Version stehen vollständig in der Detailansicht der Karte. Die kleinen Karten (Hinweis, Vorgänger, Alle Änderungen) haben nur
 * Inhaltshöhe; den übrigen Platz bekommt die neueste Version, und ihre Punkte nutzen ihn (Zeilenzahl aus der Kartenhöhe).
 */
@Composable
internal fun NewsPage(canInstall: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by UpdateManager.state.collectAsState()
    val whatsNew by UpdateManager.whatsNew.collectAsState()
    val bundled = remember(context.resources.configuration) { UpdateManager.bundledChangelog(context) }
    val offered = offeredInfo(state)
    val offerEntries = remember(offered?.versionName, offered?.notes) {
        if (offered == null) emptyList() else Changelog.forOffer(offered.notes, offered.versionName, BuildConfig.VERSION_NAME)
    }
    val offerOpen = remember { mutableStateMapOf<String, Boolean>() }
    val allOpen = remember { mutableStateMapOf<String, Boolean>() }
    val oneOpen = remember { mutableStateMapOf<String, Boolean>() }
    val offerScroll = rememberScrollState()
    val allScroll = rememberScrollState()
    val oneScroll = rememberScrollState()
    var detailId by remember { mutableStateOf<String?>(null) }

    val plan = remember(whatsNew != null, offered != null, bundled.size) { newsPlan(whatsNew != null, offered != null, bundled.size) }
    val latest = bundled.firstOrNull()
    val previous = bundled.drop(1).take(NEWS_PREVIOUS_SHOWN)

    val offerBody = stringResource(R.string.launcher_news_offer_body)
    val offerBlocked = stringResource(R.string.launcher_news_offer_blocked)
    val updateNow = stringResource(R.string.launcher_news_update_now)

    val cards = buildList<OverviewCard> {
        if (offered != null) {
            add(
                plan.card(
                    "offer",
                    stringResource(R.string.launcher_news_update_available),
                    onOpen = if (offerEntries.isNotEmpty()) ({ detailId = "offer" }) else null,
                ) {
                    val transition = UpdateLogic.versionTransition(BuildConfig.VERSION_NAME, offered.versionName)
                    CardText("$transition · ${UpdateLogic.megabytes(offered.size)}", 1, LauncherColors.Body)
                    CardText(if (canInstall) offerBody else offerBlocked, 2)
                    if (state is UpdateState.Available) {
                        Gap(8)
                        Box(modifier = Modifier.widthIn(max = 380.dp)) {
                            PagePrimaryButton(updateNow, canInstall) { UpdateManager.install(context) }
                        }
                    }
                },
            )
        }
        if (whatsNew != null) {
            add(
                plan.card("whatsnew", stringResource(R.string.launcher_news_whats_new)) {
                    CardActionRow(
                        stringResource(R.string.launcher_news_version, whatsNew?.version.orEmpty()),
                        stringResource(R.string.launcher_news_got_it),
                    ) { UpdateManager.dismissWhatsNew(context) }
                },
            )
        }
        if (latest == null) {
            add(plan.card("empty", null) { CardText(stringResource(R.string.launcher_news_no_list), 1, LauncherColors.Body) })
        } else {
            add(
                plan.card("latest", PageTexts.latestTitle(latest.version, latest.date), onOpen = { detailId = "ver:${latest.version}" }) {
                    LatestColumns(latest)
                },
            )
            previous.forEachIndexed { index, entry ->
                add(
                    plan.card("prev$index", entry.version, onOpen = { detailId = "ver:${entry.version}" }) {
                        CardText(PageTexts.versionLine(entry.date, entry.itemCount).asString(), 1, LauncherColors.Body)
                        CardText(entry.sections.firstOrNull()?.items?.firstOrNull().orEmpty(), 2)
                    },
                )
            }
            add(
                plan.card("all", stringResource(R.string.launcher_news_all_changes), onOpen = { detailId = "all" }) {
                    CardText(PageTexts.allVersionsLine(bundled.size).asString(), 1)
                },
            )
        }
    }

    val close: () -> Unit = { detailId = null }
    val openId = detailId
    val openEntry = if (openId != null && openId.startsWith("ver:")) bundled.firstOrNull { it.version == openId.removePrefix("ver:") } else null
    val detail: PageDetail? = when {
        openId == "offer" && offered != null -> PageDetail(stringResource(R.string.launcher_news_update_title, offered.versionName), close) {
            InfoPanel(
                title = stringResource(
                    R.string.launcher_news_update_title,
                    UpdateLogic.versionTransition(BuildConfig.VERSION_NAME, offered.versionName),
                ),
                body = stringResource(R.string.launcher_news_size_and_body, UpdateLogic.megabytes(offered.size), offerBody),
            ) {
                if (offerEntries.isNotEmpty()) {
                    Text(
                        stringResource(R.string.launcher_news_changes_since),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = LauncherFonts.Cinzel,
                        color = LauncherColors.Body,
                    )
                    ChangelogList(offerEntries, offerOpen, offerScroll)
                }
                if (state is UpdateState.Available) {
                    PagePrimaryButton(updateNow, canInstall) { UpdateManager.install(context) }
                    if (!canInstall) {
                        Text(offerBlocked, fontSize = 14.sp, color = LauncherColors.Muted)
                    }
                }
            }
        }
        openId == "all" -> PageDetail(stringResource(R.string.launcher_news_all_changes), close) {
            ChangelogList(bundled, allOpen, allScroll)
        }
        openEntry != null -> PageDetail(openEntry.version, close) {
            if (openEntry.date.isNotBlank()) {
                Text(openEntry.date, fontSize = 14.sp, color = LauncherColors.Muted)
            }
            ChangelogList(listOf(openEntry), oneOpen, oneScroll, showHeaders = false)
        }
        else -> null
    }

    LauncherOverviewPage(title = LauncherTexts.pageTitle(LauncherPage.News).asString(), onBack = onBack, cards = cards, detail = detail)
}

/** Farbe der Überschrift eines Abschnitts: Neu in Gold, Verbessert in Blau, Behoben in Grün. */
private fun sectionColor(kind: ChangeKind): Color = when (kind) {
    ChangeKind.Neu -> LauncherColors.GoldBright
    ChangeKind.Verbessert -> LauncherColors.Info
    ChangeKind.Behoben -> LauncherColors.Ok
    ChangeKind.Sonstiges -> LauncherColors.Muted
}

/**
 * Die neueste Version in Spalten (bis zu drei Abschnitte nebeneinander): Überschrift mit Zahl, die ersten Punkte und "+ n weitere".
 * Wie viele Punkte und wie viele Zeilen je Punkt, richtet sich nach der Höhe, die die Karte hat ([newsColumnLayout]); scrollt die
 * Seite (oder ist die Höhe nicht begrenzt), stehen die Punkte in voller Länge. Alles andere steht in der Detailansicht.
 */
@Composable
private fun LatestColumns(entry: ChangelogEntry) {
    if (LocalPageScroll.current) {
        // Beim Scrollen gibt es keine Höhe zu füllen (und die Zeilen des Rasters messen ihre Karten über IntrinsicSize, was
        // BoxWithConstraints nicht verträgt): feste Vorgabe.
        LatestColumnsRow(entry, NewsColumnLayout(NEWS_ITEMS_SHOWN, NEWS_ITEM_LINES))
        return
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Höhe hier lesen, nicht in den Lambdas darunter (Falle aus fold8.20).
        val bounded = constraints.hasBoundedHeight
        val availableDp = maxHeight.value
        val layout = if (bounded) newsColumnLayout(availableDp, fontScale) else NewsColumnLayout(NEWS_ITEMS_SHOWN, NEWS_ITEM_LINES)
        LatestColumnsRow(entry, layout)
    }
}

@Composable
private fun LatestColumnsRow(entry: ChangelogEntry, layout: NewsColumnLayout) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (section in entry.sections.take(3)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NEWS_ITEM_GAP_DP.dp)) {
                Text(
                    text = stringResource(R.string.launcher_news_section_count, stringResource(section.kind.titleRes), section.items.size),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = sectionColor(section.kind),
                    maxLines = 1,
                    modifier = Modifier.height(NEWS_COLUMN_HEAD_DP.dp),
                )
                for (item in section.items.take(layout.items)) {
                    CardText("• $item", layout.lines, LauncherColors.Body)
                }
                PageTexts.moreLine(section.items.size, layout.items)?.let { CardText(it.asString(), 1) }
            }
        }
    }
}
