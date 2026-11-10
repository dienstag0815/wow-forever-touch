package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gamenative.ui.screen.xserver.editor.PreviewIcon
import app.gamenative.ui.screen.xserver.editor.previewKindOf
import com.winlator.inputcontrols.ControlElement
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.screen.xserver.editor.text

/** Where a sheet on [side] sits inside the editor's box. */
fun SheetSide.alignment(): Alignment = when (this) {
    SheetSide.LEFT -> Alignment.CenterStart
    SheetSide.RIGHT -> Alignment.CenterEnd
    SheetSide.TOP -> Alignment.TopCenter
    SheetSide.BOTTOM -> Alignment.BottomCenter
}

/**
 * The settings sheet of the selected element: not modal (the game picture and the element stay visible and can be moved
 * while it is open), a side panel on the inner display, a sheet above or below on the outer one. It has pages (overview,
 * binding, ring keys, appearance, advanced) with a back arrow instead of nested dialogs; every change is applied to the
 * element live and is one step of the editor's undo history.
 */
@Composable
fun ElementSheet(
    state: ElementSheetState,
    element: ControlElement,
    side: SheetSide,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val route = state.nav.current
    val picker = route.page == SheetPage.KEY_PICKER

    // Stay on a page the element offers (an undo or a type change can take pages away).
    LaunchedEffect(element, element.type, element.isRadialMenuButton, state.tick, state.session.externalRevision) {
        state.sanitize(element)
    }
    // Typing and sliders commit once the input pauses; leaving the sheet commits what is left.
    LaunchedEffect(state.tick, state.pending) {
        if (state.pending) {
            delay(700)
            state.flush()
        }
    }
    DisposableEffect(state) { onDispose { state.flush() } }
    BackHandler(enabled = state.nav.canGoBack) { state.back() }

    val maxHeight = (configuration.screenHeightDp * if (picker) 0.62f else 0.55f).dp
    val sizeModifier = when (side) {
        SheetSide.LEFT, SheetSide.RIGHT -> Modifier
            .width(SheetPlacement.panelWidthDp(configuration.screenWidthDp).dp)
            .fillMaxHeight()
            .padding(start = 8.dp, end = 8.dp, top = 64.dp, bottom = 8.dp)
        SheetSide.TOP -> Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 64.dp)
        SheetSide.BOTTOM -> Modifier.fillMaxWidth().padding(8.dp)
    }
    val stacked = side == SheetSide.TOP || side == SheetSide.BOTTOM
    val heightModifier = when {
        !stacked -> Modifier.fillMaxSize()
        picker -> Modifier.height(maxHeight)
        else -> Modifier.heightIn(max = maxHeight)
    }

    Surface(
        modifier = modifier.imePadding().then(sizeModifier),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        color = EditorBarColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = heightModifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
            SheetTop(state = state, element = element, route = route, onClose = onClose)
            Spacer(Modifier.height(8.dp))
            if (picker) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    key(route) { KeyPickerPage(state = state, element = element, route = route) }
                }
            } else {
                Box(modifier = Modifier.weight(1f, fill = !stacked).fillMaxWidth()) {
                    key(route) {
                        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            SheetPageContent(state = state, element = element, route = route)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetPageContent(state: ElementSheetState, element: ControlElement, route: SheetRoute) {
    when (route.page) {
        SheetPage.OVERVIEW -> OverviewPage(state, element)
        SheetPage.BINDING -> BindingPage(state, element)
        SheetPage.RADIAL_MENU -> RadialMenuPage(state, element)
        SheetPage.RING -> RingPage(state, element)
        SheetPage.RANGE_ITEMS -> RangeItemsPage(state, element)
        SheetPage.APPEARANCE -> AppearancePage(state, element)
        SheetPage.ADVANCED -> AdvancedPage(state, element)
        SheetPage.KEY_PICKER -> {}
    }
}

@Composable
private fun SheetTop(state: ElementSheetState, element: ControlElement, route: SheetRoute, onClose: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val redraw = state.tick
    val kind = previewKindOf(element)
    if (route.page == SheetPage.OVERVIEW) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                PreviewIcon(kind = kind, color = Color.White, modifier = Modifier.size(32.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(kind.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.editor_sheet_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { state.session.duplicateSelected() }, modifier = Modifier.size(SheetTarget)) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.editor_duplicate))
            }
            IconButton(onClick = { state.session.deleteSelected() }, modifier = Modifier.size(SheetTarget)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.editor_delete))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(SheetTarget)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.editor_close_sheet))
            }
        }
    } else {
        SheetHeader(
            title = SheetPages.title(route, element.type).text(),
            subtitle = stringResource(kind.label),
            onBack = { state.back() },
            onClose = onClose,
        )
    }
}
