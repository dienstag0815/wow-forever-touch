package app.gamenative.ui.screen.xserver.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PluviaApp
import app.gamenative.ui.screen.xserver.editor.sheet.EditorBarColor
import app.gamenative.ui.screen.xserver.editor.sheet.EditorPalette
import app.gamenative.ui.screen.xserver.editor.sheet.EditorTheme
import app.gamenative.ui.screen.xserver.editor.sheet.ElementSheet
import app.gamenative.ui.screen.xserver.editor.sheet.ElementSheetState
import app.gamenative.ui.screen.xserver.editor.sheet.SheetPlacement
import app.gamenative.ui.screen.xserver.editor.sheet.alignment
import app.gamenative.ui.screen.xserver.editor.sheet.editorColor
import app.gamenative.wowaction.ActionBarTemplates
import com.winlator.container.Container
import com.winlator.widget.InputControlsView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import app.gamenative.R

private val BarColor get() = EditorBarColor
private val DoneColor get() = editorColor(EditorPalette.DONE)

/**
 * The controls editor on top of the game picture: toolbar (cancel, undo/redo, add, done), quick actions for the selected
 * element, the settings sheet (side panel or above/below, see [ElementSheet]) and the "+" gallery. Moving, sizing and selecting happen directly on the picture
 * (see [EditGestureController]); the state lives in [session].
 */
@Composable
fun ControlsEditorOverlay(
    session: ControlsEditorSession,
    view: InputControlsView,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onCopyFromProfile: (Int) -> Unit,
    modifier: Modifier = Modifier,
    container: Container? = null,
) {
    // Calm dark colors with a bronze accent for the whole editor (bars, sheet, dialogs), not the app's purple.
    EditorTheme {
        ControlsEditorOverlayContent(session, view, onDone, onCancel, onCopyFromProfile, modifier, container)
    }
}

@Composable
private fun ControlsEditorOverlayContent(
    session: ControlsEditorSession,
    view: InputControlsView,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onCopyFromProfile: (Int) -> Unit,
    modifier: Modifier = Modifier,
    container: Container? = null,
) {
    LaunchedEffect(view) { session.begin(view) }
    DisposableEffect(session) { onDispose { session.detach() } }

    var paletteOpen by remember { mutableStateOf(false) }
    val templateGuard = remember(session) { TemplateEditGuard(session) }
    val sheetState = remember(session, view) { ElementSheetState(session, view) }
    val selected = session.selected
    val sheetOpen = session.sheetOpen
    val profiles = remember {
        PluviaApp.inputControlsManager?.getProfiles(false)?.map { it.id to it.name } ?: emptyList()
    }

    // A different element starts on the overview page of the sheet.
    val selectedIndex = session.selectedIndex()
    LaunchedEffect(selectedIndex) { sheetState.toOverview() }

    Box(modifier = modifier.fillMaxSize()) {
        EditorTopBar(
            session = session,
            profiles = profiles,
            onAdd = { paletteOpen = true },
            onDone = { if (!templateGuard.intercept(view.profile)) onDone() },
            onCancel = onCancel,
            onCopyFromProfile = onCopyFromProfile,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        templateGuard.Dialogs(onDone = onDone, onCancel = onCancel, container = container)

        if (selected != null && sheetOpen) {
            // The sheet sits opposite the element (side panel) or above/below it, so the element stays visible while it is edited.
            val side = SheetPlacement.choose(
                screenWidthDp = LocalConfiguration.current.screenWidthDp,
                elementX = selected.x.toFloat(),
                elementY = selected.y.toFloat(),
                viewWidth = view.width.toFloat(),
                viewHeight = view.height.toFloat(),
            )
            ElementSheet(
                state = sheetState,
                element = selected,
                side = side,
                onClose = { session.sheetOpen = false },
                modifier = Modifier.align(side.alignment()),
            )
        } else {
            if (selected != null) {
                SelectionBar(session = session, modifier = Modifier.align(Alignment.BottomCenter))
            }
            if (session.deleteNoticeVisible) {
                DeleteNotice(
                    session = session,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (selected != null) 76.dp else 12.dp),
                )
            } else if (selected == null) {
                Text(
                    text = stringResource(R.string.editor_hint_canvas),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        if (paletteOpen) {
            PalettePanel(
                onPick = { kind, bar ->
                    paletteOpen = false
                    session.addElement(kind, bar)
                },
                onClose = { paletteOpen = false },
            )
        }
    }
}

@Composable
private fun EditorTopBar(
    session: ControlsEditorSession,
    profiles: List<Pair<Int, String>>,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onCopyFromProfile: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var menuOpen by remember { mutableStateOf(false) }
    val compact = LocalConfiguration.current.screenWidthDp < 600

    Surface(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .padding(top = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = BarColor,
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = stringResource(R.string.editor_move_bar),
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        }
                    },
            )

            if (compact) {
                IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.editor_cancel), tint = Color.White)
                }
            } else {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.editor_cancel))
                }
            }

            IconButton(onClick = { session.undo() }, enabled = session.canUndo, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.editor_undo),
                    tint = Color.White.copy(alpha = if (session.canUndo) 1f else 0.3f),
                )
            }
            IconButton(onClick = { session.redo() }, enabled = session.canRedo, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.editor_redo),
                    tint = Color.White.copy(alpha = if (session.canRedo) 1f else 0.3f),
                )
            }

            Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (compact) stringResource(R.string.editor_add_short) else stringResource(R.string.editor_add))
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.editor_more), tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_copy_layout_from), fontWeight = FontWeight.SemiBold) },
                        enabled = false,
                        onClick = {},
                    )
                    if (profiles.isEmpty()) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.editor_no_other_profiles)) }, enabled = false, onClick = {})
                    }
                    for ((profileId, profileName) in profiles) {
                        DropdownMenuItem(
                            text = { Text(profileName) },
                            onClick = {
                                menuOpen = false
                                onCopyFromProfile(profileId)
                            },
                        )
                    }
                }
            }

            Button(
                onClick = onDone,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DoneColor, contentColor = Color.White),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.editor_done))
            }
        }
    }
}

@Composable
private fun SelectionBar(session: ControlsEditorSession, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(bottom = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = BarColor,
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarAction(Icons.Default.Edit, stringResource(R.string.editor_settings)) { session.sheetOpen = true }
            BarAction(Icons.Default.ContentCopy, stringResource(R.string.editor_duplicate)) { session.duplicateSelected() }
            BarAction(Icons.Default.Delete, stringResource(R.string.editor_delete)) { session.deleteSelected() }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
private fun DeleteNotice(session: ControlsEditorSession, modifier: Modifier = Modifier) {
    LaunchedEffect(session.deleteNoticeId) {
        delay(6000)
        session.dismissDeleteNotice()
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = BarColor, contentColor = Color.White) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.editor_element_deleted))
            TextButton(
                onClick = { session.undo() },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.editor_undo))
            }
        }
    }
}

@Composable
private fun PalettePanel(onPick: (PaletteKind, Int) -> Unit, onClose: () -> Unit) {
    val kinds = remember { PaletteKind.available() }
    // The action bar menu asks which bar (1 to 8) before it is added.
    var askBar by remember { mutableStateOf(false) }
    val screenHeight = LocalConfiguration.current.screenHeightDp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = (screenHeight * 0.9f).dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.editor_add_element),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.editor_close))
                    }
                }
                Spacer(Modifier.size(8.dp))
                if (askBar) {
                    BarChooser(
                        onPick = { bar -> onPick(PaletteKind.ACTION_BAR_MENU, bar) },
                        onBack = { askBar = false },
                    )
                    return@Column
                }
                BoxWithConstraints(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val columns = if (maxWidth < 420.dp) 3 else 4
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (rowKinds in kinds.chunked(columns)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (kind in rowKinds) {
                                    PaletteTile(kind = kind, modifier = Modifier.weight(1f)) {
                                        if (kind == PaletteKind.ACTION_BAR_MENU) askBar = true else onPick(kind, 1)
                                    }
                                }
                                repeat(columns - rowKinds.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Which action bar the new radial menu takes its fields from (bars as in WoW's edit mode). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BarChooser(onPick: (Int) -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.editor_which_bar), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (bar in 1..ActionBarTemplates.BAR_COUNT) {
                AssistChip(
                    onClick = { onPick(bar) },
                    label = { Text(stringResource(R.string.editor_bar_n, bar)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Text(
            stringResource(R.string.editor_bar_help),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onBack) { Text(stringResource(R.string.editor_back)) }
    }
}

@Composable
private fun PaletteTile(kind: PaletteKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .heightIn(min = 104.dp)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PreviewIcon(
            kind = kind.preview,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(kind.title),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
