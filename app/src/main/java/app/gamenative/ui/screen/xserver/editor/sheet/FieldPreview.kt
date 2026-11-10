package app.gamenative.ui.screen.xserver.editor.sheet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.ui.touchstyle.RadialArc
import app.gamenative.wowaction.BindingCaption
import app.gamenative.wowaction.WowActionCatalog
import app.gamenative.wowicons.WowIcons
import com.winlator.inputcontrols.RadialMenu
import com.winlator.inputcontrols.StickRing
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.res.stringResource
import app.gamenative.R

/** What one field of the preview shows: its number and a short text, an emoji or the real icon of its action. */
internal class FieldCell(
    val number: Int,
    val text: String,
    val emoji: String?,
    val wowAction: String?,
    val active: Boolean,
)

/** Preview cell of a radial menu field or ring segment [slot]. */
internal fun fieldCellOf(slot: RadialMenu.Slot, number: Int): FieldCell {
    val text = BindingCaption.fit(WowActionCatalog.displayLabel(slot.label, slot.wowAction, slot.bindingCombo).ifBlank { slot.displayLabel }, 6)
    return FieldCell(number, text, BindingCaption.emojiForAction(slot.wowAction), slot.wowAction, slot.isEnabled)
}

/**
 * Clickable picture of a radial menu (as in the game, see [menuAngles]) or of a stick ring: one round cell per field with its
 * number and symbol or label. [angles] are the cell angles in degrees (0 = right, clockwise). [radiusFractions] puts a field closer
 * to the middle (1 = outer edge; radial menus with a second ring, see [menuRadiusFractions]). The cell [selected] is
 * highlighted; a tap calls [onSelect] with the index.
 */
@Composable
internal fun FieldPreview(
    cells: List<FieldCell>,
    angles: List<Float>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    radiusFractions: List<Float>? = null,
) {
    if (cells.isEmpty()) return
    val side: Dp = 264.dp
    val twoRings = radiusFractions != null && radiusFractions.any { it < 0.99f }
    val cell: Dp = if (twoRings) 34.dp else if (cells.size > 8) 46.dp else 54.dp
    val iconSize: Dp = if (twoRings) 14.dp else if (cells.size > 8) 18.dp else 22.dp
    val outerRadius = side / 2 - cell / 2 - 2.dp
    Box(modifier = modifier.size(side)) {
        Text(
            text = if (selected in cells.indices) stringResource(R.string.editor_field_n, selected + 1)
            else stringResource(R.string.editor_tap_field),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
        )
        cells.forEachIndexed { index, info ->
            val radians = Math.toRadians(angles.getOrElse(index) { 0f }.toDouble())
            val radius = outerRadius * (radiusFractions?.getOrNull(index) ?: 1f)
            val x = side / 2 + radius * cos(radians).toFloat() - cell / 2
            val y = side / 2 + radius * sin(radians).toFloat() - cell / 2
            val isSelected = index == selected
            val fieldDescription = stringResource(R.string.editor_field_named, info.number, info.text)
            Column(
                modifier = Modifier
                    .offset(x, y)
                    .size(cell)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (info.active) 0.9f else 0.45f),
                    )
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .semantics { contentDescription = fieldDescription }
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = info.number.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val icon = WowIcons.bitmapFor(info.wowAction)
                when {
                    icon != null -> Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize).clip(CircleShape),
                    )
                    info.emoji != null -> Text(text = info.emoji, fontSize = 15.sp)
                    else -> Text(
                        text = info.text,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Angles of the [count] fields of a radial menu as the game draws them ([RadialArc]): an arc of 270 degrees open at the bottom, field 1
 * at the left end, then clockwise; from the ninth field on a second ring outside.
 */
internal fun menuAngles(count: Int): List<Float> =
    RadialArc.ringSizes(count).flatMap { n ->
        RadialArc.slotAngles(n, RadialArc.ARC_DEG, RadialArc.CENTER_DEG, RadialArc.WIDE_STEP_DEG, RadialArc.Order.SEQUENTIAL)
    }

/** Distance of each field of [menuAngles] from the middle as a fraction of the outer edge: the first ring lies inside the second. */
internal fun menuRadiusFractions(count: Int): List<Float> {
    val sizes = RadialArc.ringSizes(count)
    return sizes.flatMapIndexed { ring, n -> List(n) { if (sizes.size > 1 && ring == 0) INNER_RING_FRACTION else 1f } }
}

private const val INNER_RING_FRACTION = 0.55f

/** Angles of the segments of a stick ring (as drawn: centre of segment 1 at [startAngle]). */
internal fun ringAngles(count: Int, startAngle: Float): List<Float> =
    List(count) { StickRing.segmentCenterAngle(it, count, startAngle) }

/** Question before a template replaces fields the player may have set by hand. */
@Composable
internal fun OverwriteDialog(templateName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_apply_template_q)) },
        text = {
            Text(stringResource(R.string.editor_template_overwrite, templateName))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.editor_replace)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } },
    )
}
