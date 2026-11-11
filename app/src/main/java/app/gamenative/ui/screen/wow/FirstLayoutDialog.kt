package app.gamenative.ui.screen.wow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.theme.WowBronze
import app.gamenative.ui.theme.WowGold

/**
 * Shown once before the very first game start: use the standard layout (recommended) or build one's own. Dismissible: closing
 * the dialog (Back, tap outside) counts as the standard layout, so it never blocks the start; the choice can be changed later in
 * the settings (Steuerung).
 */
@Composable
internal fun FirstLayoutDialog(onChoose: (LayoutChoice) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoose(LayoutChoice.STANDARD) },
        title = { Text(text = stringResource(R.string.first_layout_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WowGold) },
        text = {
            Column {
                Text(text = stringResource(R.string.first_layout_text), fontSize = 13.sp, color = Color(0xFFE2E8F0), lineHeight = 18.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onChoose(LayoutChoice.STANDARD) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WowBronze, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.first_layout_standard), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onChoose(LayoutChoice.OWN) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(R.string.first_layout_own), color = WowGold)
                }
            }
        },
        confirmButton = {},
        containerColor = Color(0xFF1B2838),
        shape = RoundedCornerShape(16.dp),
    )
}
