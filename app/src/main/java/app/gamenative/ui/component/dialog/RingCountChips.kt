package app.gamenative.ui.component.dialog

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.winlator.inputcontrols.ControlElement

/** Number of ring keys that can be chosen; the order is the order of the chips. */
internal val RING_COUNT_OPTIONS = listOf(0, 4, 6, 8)

/**
 * Chips "Aus / 4 / 6 / 8" for the number of ring keys. The number can be changed in both directions at any time
 * (4 to 6 to 8 to 4 to off to 8): keys of segments that exist stay as they are, new segments start with the number keys
 * and an empty (automatic) label. The chips redraw themselves after a click, independent of the surrounding screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RingCountChips(element: ControlElement, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val ignored = tick
    val current = element.ringSegments
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RING_COUNT_OPTIONS.forEachIndexed { index, option ->
            FilterChip(
                selected = current == option,
                onClick = {
                    element.setRingSegments(option)
                    tick++
                    onChanged()
                },
                label = { Text(if (option == 0) stringResource(R.string.game_off) else option.toString()) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}
