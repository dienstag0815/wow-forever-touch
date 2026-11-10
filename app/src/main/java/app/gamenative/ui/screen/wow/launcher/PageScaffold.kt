package app.gamenative.ui.screen.wow.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R

/*
 * Rahmen der Unterseiten als Übersicht: Kopf mit Zurück und Titel, darunter ein Raster aus Karten über die volle Breite und Höhe
 * (PageGrid.kt rechnet Spalten und ob alles ohne Scrollen passt). Jede Karte zeigt nur das Wichtigste; was dahinter liegt, öffnet
 * sich als Detailansicht über die ganze Seite, darin darf gescrollt werden. Zurück (Taste, Geste, Pfeil) schließt zuerst die
 * Detailansicht, dann die Seite.
 */

/**
 * Eine Karte der Übersicht. [id] und [size] stammen aus dem Plan der Seite (PagePlans.kt), [title] steht in Cinzel oben (null =
 * ohne Titelzeile). Mit [onOpen] ist die ganze Karte tippbar und zeigt einen Pfeil. [content] sind die Zeilen unter dem Titel.
 */
internal class OverviewCard(
    val id: String,
    val size: CardSize,
    val title: String?,
    val onOpen: (() -> Unit)? = null,
    val content: @Composable ColumnScope.() -> Unit,
)

/** Baut die Karte [id] des Plans mit der dort festgelegten Größe. */
internal fun List<CardPlan>.card(
    id: String,
    title: String?,
    onOpen: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
): OverviewCard = OverviewCard(id, sizeOf(id), title, onOpen, content)

/** Die geöffnete Detailansicht: [onClose] schließt sie, [content] steht in einer scrollenden Spalte. */
internal class PageDetail(
    val title: String,
    val onClose: () -> Unit,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * Vollbildseite mit [cards] im Raster. [detail] (wenn nicht null) liegt über der ganzen Seite. [backEnabled] false sperrt Pfeil und
 * Zurück-Taste der Seite (nicht der Detailansicht), zum Beispiel während etwas gespeichert wird. [headerAction] steht rechts im Kopf.
 */
@Composable
internal fun LauncherOverviewPage(
    title: String,
    onBack: () -> Unit,
    cards: List<OverviewCard>,
    detail: PageDetail? = null,
    backEnabled: Boolean = true,
    headerAction: (@Composable RowScope.() -> Unit)? = null,
) {
    BackHandler(enabled = detail == null) { if (backEnabled) onBack() }
    val fontScale = LocalDensity.current.fontScale
    Box(
        modifier = Modifier
            .fillMaxSize()
            .launcherBackground()
            // Die Startseite liegt darunter: Tipps auf freie Flächen dürfen nicht zu ihr durchfallen.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Breite und Höhe hier lesen, nicht in den Lambdas darunter (Falle aus fold8.20).
            val widthDp = maxWidth.value
            val heightDp = maxHeight.value
            val grid = pageGrid(widthDp, heightDp, cards.map { it.size }, fontScale)
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                OverviewHeader(title, onBack, backEnabled, headerAction, grid.columns == 1)
                OverviewBody(cards, grid)
            }
        }
        if (detail != null) DetailSheet(detail)
    }
}

@Composable
private fun OverviewHeader(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean,
    action: (@Composable RowScope.() -> Unit)?,
    narrow: Boolean,
) {
    val limit = if (narrow) Modifier.widthIn(max = PAGE_MAX_WIDTH_ONE_COLUMN_DP.dp) else Modifier
    Row(
        modifier = limit
            .fillMaxWidth()
            .padding(start = (PAGE_SIDE_DP / 2f).dp, end = (PAGE_SIDE_DP / 2f).dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = backEnabled) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.launcher_action_back), tint = LauncherColors.Muted)
        }
        LauncherTitle(text = title, fontSize = 24.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (action != null) action(this)
    }
    OrnamentDivider(limit.fillMaxWidth().padding(horizontal = PAGE_SIDE_DP.dp))
}

/** Das Raster: passt es (grid.fits), füllen die Reihen die ganze Höhe im Verhältnis ihrer Höhen; sonst scrollt die Spalte. */
@Composable
private fun ColumnScope.OverviewBody(cards: List<OverviewCard>, grid: PageGridResult) {
    val limit = if (grid.columns == 1) Modifier.widthIn(max = PAGE_MAX_WIDTH_ONE_COLUMN_DP.dp) else Modifier
    val frame = limit
        .fillMaxWidth()
        .padding(start = PAGE_SIDE_DP.dp, end = PAGE_SIDE_DP.dp, top = PAGE_BODY_TOP_DP.dp, bottom = PAGE_BODY_BOTTOM_DP.dp)
    CompositionLocalProvider(LocalPageScroll provides grid.scroll) {
        if (grid.fits) {
            Column(modifier = frame.weight(1f), verticalArrangement = Arrangement.spacedBy(PAGE_GAP_DP.dp)) {
                GridRows(cards, grid, stretch = true)
            }
        } else {
            Column(
                modifier = frame.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PAGE_GAP_DP.dp),
            ) {
                GridRows(cards, grid, stretch = false)
            }
        }
    }
}

@Composable
private fun ColumnScope.GridRows(cards: List<OverviewCard>, grid: PageGridResult, stretch: Boolean) {
    for ((r, row) in grid.rows.withIndex()) {
        val flex = grid.rowFlex.getOrElse(r) { true }
        val rowModifier = when {
            // Eine Reihe ohne flexible Karte bleibt bei ihrer Inhaltshöhe; den übrigen Platz teilen die flexiblen Reihen.
            stretch && !flex -> Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            stretch -> Modifier.fillMaxWidth().weight(maxOf(grid.rowHeightsDp[r], 1f))
            grid.columns > 1 -> Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            else -> Modifier.fillMaxWidth()
        }
        Row(modifier = rowModifier, horizontalArrangement = Arrangement.spacedBy(PAGE_GAP_DP.dp)) {
            for (index in row) {
                val card = cards[index]
                val span = card.size.span.coerceIn(1, grid.columns)
                key(card.id) {
                    CardFrame(
                        card = card,
                        modifier = Modifier
                            .weight(span.toFloat())
                            .then(if (stretch || grid.columns > 1) Modifier.fillMaxHeight() else Modifier),
                    )
                }
            }
        }
    }
}

/** Eine Karte: ruhiger Rahmen wie auf der Startseite (1 dp Goldlinie, 14 dp Ecken), Titelzeile, darunter ihre Zeilen. */
@Composable
private fun CardFrame(card: OverviewCard, modifier: Modifier) {
    val open = card.onOpen
    Column(
        modifier = modifier
            .heightIn(min = card.size.heightDp.dp)
            .softFrame(fill = LauncherColors.Surface)
            .then(if (open != null) Modifier.clickable(role = Role.Button, onClick = open) else Modifier)
            .padding(horizontal = CARD_PAD_H_DP.dp, vertical = CARD_PAD_V_DP.dp),
    ) {
        val title = card.title
        if (title != null) CardTitle(title, hasDetail = open != null)
        card.content(this)
    }
}

@Composable
private fun CardTitle(text: String, hasDetail: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().height(CARD_TITLE_DP.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LauncherFonts.Cinzel,
            color = LauncherColors.Gold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hasDetail) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = LauncherColors.Muted, modifier = Modifier.size(24.dp))
        }
    }
}

/** Detailansicht über der ganzen Seite: Kopf mit Zurück, darunter eine scrollende Spalte. Zurück schließt nur sie. */
@Composable
private fun DetailSheet(detail: PageDetail) {
    BackHandler(onBack = detail.onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .launcherBackground()
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = PAGE_MAX_WIDTH_ONE_COLUMN_DP.dp)
                .fillMaxWidth()
                .padding(start = (PAGE_SIDE_DP / 2f).dp, end = (PAGE_SIDE_DP / 2f).dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = detail.onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.launcher_action_back), tint = LauncherColors.Muted)
            }
            LauncherTitle(text = detail.title, fontSize = 22.sp, maxLines = 1, modifier = Modifier.weight(1f))
        }
        OrnamentDivider(
            Modifier
                .widthIn(max = PAGE_MAX_WIDTH_ONE_COLUMN_DP.dp)
                .fillMaxWidth()
                .padding(horizontal = PAGE_SIDE_DP.dp),
        )
        Column(
            modifier = Modifier
                .widthIn(max = PAGE_MAX_WIDTH_ONE_COLUMN_DP.dp)
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = PAGE_SIDE_DP.dp, end = PAGE_SIDE_DP.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = detail.content,
        )
    }
}

/** Hinweiskarte mit Goldrahmen in [accent] (bei Fehlern rot), Titel, Text und darunter Knöpfe oder Zeilen. */
@Composable
internal fun InfoPanel(
    title: String,
    body: String,
    accent: Color = LauncherColors.Gold,
    fill: Color = LauncherColors.SurfaceRaised,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ornateFrame(fill = fill, outer = accent, inner = accent.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = LauncherFonts.Cinzel, color = accent)
        Text(text = body, fontSize = 14.sp, color = LauncherColors.Body, lineHeight = 19.sp)
        content()
    }
}
