package app.gamenative.update

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import app.gamenative.ui.theme.WowGold
import app.gamenative.ui.theme.WowMuted
import app.gamenative.ui.theme.WowSubtle

private val PanelBackground = Color(0xFF1A2438)
private val BodyColor = Color(0xFFE2E8F0)
private val NewColor = Color(0xFFF2C46D)
private val ImprovedColor = Color(0xFF63B3ED)
private val FixedColor = Color(0xFF68D391)

private fun kindColor(kind: ChangeKind): Color = when (kind) {
    ChangeKind.Neu -> NewColor
    ChangeKind.Verbessert -> ImprovedColor
    ChangeKind.Behoben -> FixedColor
    ChangeKind.Sonstiges -> WowMuted
}

private fun kindIcon(kind: ChangeKind): ImageVector = when (kind) {
    ChangeKind.Neu -> Icons.Default.Add
    ChangeKind.Verbessert -> Icons.Default.Build
    ChangeKind.Behoben -> Icons.Default.Check
    ChangeKind.Sonstiges -> Icons.Default.Info
}

/**
 * The change list: per version a header (tap to fold, the newest is open unless [open] says otherwise) and its sections
 * with icon and colour, text in reading size (15 sp). With [maxHeight] the list scrolls inside that height ([scroll] is
 * hoisted so the position survives the card changing from offer to download); without it the list takes the height it needs
 * and must not sit in an unbounded scroll area of its own. [showHeaders] false shows one entry without version header.
 */
@Composable
internal fun ChangelogList(
    entries: List<ChangelogEntry>,
    open: SnapshotStateMap<String, Boolean>,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Unspecified,
    showHeaders: Boolean = true,
) {
    val sizing = if (maxHeight == Dp.Unspecified) {
        Modifier
    } else {
        Modifier.heightIn(max = maxHeight).verticalScroll(scroll)
    }
    Column(modifier = modifier.fillMaxWidth().then(sizing)) {
        entries.forEachIndexed { index, entry ->
            val isOpen = !showHeaders || (open[entry.version] ?: (index == 0))
            if (showHeaders) {
                VersionHeader(entry, isOpen) { open[entry.version] = !isOpen }
            }
            if (isOpen) {
                entry.sections.forEach { SectionBlock(it) }
            }
            if (showHeaders && index < entries.lastIndex) Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun VersionHeader(entry: ChangelogEntry, isOpen: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.version, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (entry.date.isNotEmpty()) {
            Text(entry.date, fontSize = 12.sp, color = WowSubtle, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(if (isOpen) R.string.launcher_changelog_collapse else R.string.launcher_changelog_expand),
            tint = WowMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SectionBlock(section: ChangelogSection) {
    val color = kindColor(section.kind)
    Row(
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(section.kind), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            stringResource(section.kind.titleRes),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
    section.items.forEach { item ->
        Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
            Text("•", fontSize = 15.sp, color = color, modifier = Modifier.padding(end = 8.dp))
            Text(item, fontSize = 15.sp, lineHeight = 21.sp, color = BodyColor)
        }
    }
}

/** Full view "Alle Änderungen": all given versions, newest first, each foldable. */
@Composable
internal fun ChangelogDialog(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val scroll = rememberScrollState()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .border(1.dp, WowGold.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = PanelBackground),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.launcher_news_all_changes), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WowGold)
                Spacer(modifier = Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Text(stringResource(R.string.launcher_news_no_list), fontSize = 15.sp, color = BodyColor)
                } else {
                    ChangelogList(entries, open, scroll, maxHeight = maxHeight)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WowGold, contentColor = Color.Black),
                ) {
                    Text(stringResource(R.string.launcher_close), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * "Neu in <Version>" once after an update (from the bundled changelog), closed with "Verstanden". Shows nothing when
 * there is nothing pending ([UpdateManager.whatsNew] null).
 */
@Composable
internal fun WhatsNewCard() {
    val context = LocalContext.current
    val entry by UpdateManager.whatsNew.collectAsState()
    val current = entry ?: return
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val scroll = rememberScrollState()
    var showAll by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(16.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .border(1.dp, WowGold.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = PanelBackground),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.launcher_changelog_new_in, current.version), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WowGold)
            if (current.date.isNotEmpty()) {
                Text(current.date, fontSize = 12.sp, color = WowSubtle)
            }
            Spacer(modifier = Modifier.height(6.dp))
            ChangelogList(listOf(current), open, scroll, showHeaders = false)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { UpdateManager.dismissWhatsNew(context) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WowGold, contentColor = Color.Black),
            ) {
                Text(stringResource(R.string.launcher_news_got_it), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.Start) {
                TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.launcher_news_all_changes), fontSize = 13.sp, color = WowGold) }
            }
        }
    }
    if (showAll) {
        ChangelogDialog(UpdateManager.bundledChangelog(context)) { showAll = false }
    }
}
