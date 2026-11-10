package app.gamenative.ui.screen.wow

import app.gamenative.R
import app.gamenative.ui.components.TextRef
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Window size in pixels. [maximum] is the full display (the unfolded inner screen on a foldable), otherwise the current window. */
internal fun windowSize(context: Context, maximum: Boolean): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val bounds = (if (maximum) windowManager.maximumWindowMetrics else windowManager.currentWindowMetrics).bounds
        return bounds.width() to bounds.height()
    }
    return context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
}

private fun aspectOf(width: Int, height: Int) = maxOf(width, height).toFloat() / minOf(width, height)

/**
 * True when the app runs on a near-square foldable display (Fold, Pixel Fold) but in a much smaller window, i.e. the
 * device is folded shut. Windows on ordinary phones and tablets never count, so split-screen there raises no hint.
 */
internal fun isFoldedShut(currentWidth: Int, currentHeight: Int, maxWidth: Int, maxHeight: Int): Boolean {
    if (minOf(currentWidth, currentHeight, maxWidth, maxHeight) <= 0) return false
    if (aspectOf(maxWidth, maxHeight) >= FOLDABLE_MAX_ASPECT) return false
    return minOf(currentWidth, currentHeight) < minOf(maxWidth, maxHeight) * FOLDED_SHORT_SIDE_RATIO
}

/**
 * Which display the picture is fitted to at start: always the full display (the unfolded inner screen on a foldable), also
 * when started folded shut – the user starts folded and then unfolds, so the picture must fit the inner screen. Folded it is
 * shown scaled down. Only when the full display size is unknown, the current window is used. Later folding/unfolding never
 * changes the picture. ([foldedAtStart] kept for callers/tests.)
 */
@Suppress("UNUSED_PARAMETER")
internal fun fitBasis(foldedAtStart: Boolean, currentWidth: Int, currentHeight: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> =
    if (maxWidth > 0 && maxHeight > 0) maxWidth to maxHeight else currentWidth to currentHeight

/** What the player is told when the fold state differs from the one the game was started in. */
internal enum class FoldHint { NONE, FOLDED_AFTER_UNFOLDED_START, UNFOLDED_AFTER_FOLDED_START }

internal fun foldHintFor(startedFolded: Boolean, foldedNow: Boolean): FoldHint = when {
    foldedNow && !startedFolded -> FoldHint.FOLDED_AFTER_UNFOLDED_START
    !foldedNow && startedFolded -> FoldHint.UNFOLDED_AFTER_FOLDED_START
    else -> FoldHint.NONE
}

internal fun foldHintText(hint: FoldHint): TextRef? = when (hint) {
    FoldHint.NONE -> null
    FoldHint.FOLDED_AFTER_UNFOLDED_START -> TextRef(R.string.game_fold_hint_folded)
    FoldHint.UNFOLDED_AFTER_FOLDED_START -> TextRef(R.string.game_fold_hint_unfolded)
}

/** In which fold state the running game was started (set at launch, survives a recreated Activity). */
internal object FoldSession {
    @Volatile
    var startedFolded: Boolean? = null
}

/** The resolution to use until the player picks one: 900p on a large, nearly square inner display, else the GPU's default. */
internal fun defaultResolution(gpuDefault: String, maxWidth: Int, maxHeight: Int): RenderResolution {
    val largeSquareish = minOf(maxWidth, maxHeight) >= LARGE_INNER_SHORT_SIDE && aspectOf(maxWidth, maxHeight) < SQUAREISH_ASPECT
    return if (largeSquareish) RenderResolution.HD_PLUS else RenderResolution.entries.first { it.size == gpuDefault }
}

private const val FOLDABLE_MAX_ASPECT = 1.5f
private const val FOLDED_SHORT_SIDE_RATIO = 0.8f
private const val SQUAREISH_ASPECT = 1.4f
private const val LARGE_INNER_SHORT_SIDE = 1600
private const val HINT_VISIBLE_MS = 8_000L

/**
 * Shown over the running game when the fold state changes against the one the game was started in: the picture keeps
 * its start size and is only scaled, touch and controls keep working. Nothing is restarted automatically.
 */
@Composable
fun FoldClosedHint() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val fallbackStart = remember { isFoldedNow(context) }
    val startedFolded = FoldSession.startedFolded ?: fallbackStart
    var text by remember { mutableStateOf<String?>(null) }
    val foldedNow = remember(configuration) { isFoldedNow(context) }
    LaunchedEffect(foldedNow) {
        text = foldHintText(foldHintFor(startedFolded, foldedNow))?.resolve(context)
        if (text != null) {
            delay(HINT_VISIBLE_MS)
            text = null
        }
    }
    text?.let { shown ->
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = shown,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

internal fun isFoldedNow(context: Context): Boolean {
    val (curW, curH) = windowSize(context, maximum = false)
    val (maxW, maxH) = windowSize(context, maximum = true)
    return isFoldedShut(curW, curH, maxW, maxH)
}
