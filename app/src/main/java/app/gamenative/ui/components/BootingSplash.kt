package app.gamenative.ui.components

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.theme.WowBackgroundGradient
import app.gamenative.ui.theme.WowGold
import app.gamenative.ui.theme.WowMuted
import app.gamenative.ui.theme.WowSubtle
import com.winlator.inputcontrols.ExternalController
import kotlinx.coroutines.delay

@Composable
fun BootingSplash(
    visible: Boolean = true,
    text: String = BootingSplashLogic.TEXT_START,
    progress: Float = -1f,
    onAbort: (() -> Unit)? = null,
) {
    if (visible && onAbort != null) {
        DisposableEffect(visible) {
            var start = false
            var select = false
            var l2 = false
            var r2 = false
            var aborted = false

            fun check(): Boolean = (!aborted && start && select && l2 && r2).also {
                if (it) {
                    aborted = true
                    onAbort()
                }
            }

            val keyHandler: (AndroidEvent.KeyEvent) -> Boolean = keyHandler@ { event ->
                val down = event.event.action == KeyEvent.ACTION_DOWN
                when (event.event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_START -> start = down
                    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> select = down
                    KeyEvent.KEYCODE_BUTTON_L2 -> l2 = down
                    KeyEvent.KEYCODE_BUTTON_R2 -> r2 = down
                    else -> return@keyHandler false
                }
                check() || (down && (start || select || l2 || r2))
            }

            val motionHandler: (AndroidEvent.MotionEvent) -> Boolean = { event ->
                event.event?.let { me ->
                    l2 = maxOf(me.getAxisValue(MotionEvent.AXIS_LTRIGGER), me.getAxisValue(MotionEvent.AXIS_BRAKE)) >= 0.3f
                    r2 = maxOf(me.getAxisValue(MotionEvent.AXIS_RTRIGGER), me.getAxisValue(MotionEvent.AXIS_GAS)) >= 0.3f
                    check()
                } ?: false
            }

            PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(keyHandler)
            PluviaApp.events.on<AndroidEvent.MotionEvent, Boolean>(motionHandler)
            onDispose {
                PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(keyHandler)
                PluviaApp.events.off<AndroidEvent.MotionEvent, Boolean>(motionHandler)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 400)),
        exit = fadeOut(animationSpec = tween(durationMillis = 300)),
    ) {
        // Stufe: springt nie zurück (BootingSplashLogic.advance); unbekannte Texte lassen sie stehen.
        var reached by remember { mutableStateOf<BootStage?>(null) }
        val info = remember(text) { BootingSplashLogic.classify(text) }
        val shown = BootingSplashLogic.advance(reached, info.stage)
        LaunchedEffect(info) { reached = shown }

        val gamepad = remember { isGamepadConnected() }
        val tips = remember(gamepad) { BootingSplashLogic.tips(gamepad) }
        var tipIndex by remember { mutableIntStateOf(0) }
        LaunchedEffect(tips.size) {
            while (true) {
                delay(7000)
                tipIndex = (tipIndex + 1) % tips.size
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(WowBackgroundGradient)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                Text(
                    text = "WORLD OF WARCRAFT",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 3.sp,
                    color = TitleLight,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Forever · Touch",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = WowGold,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                ProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    BootStage.values().forEach { step ->
                        StepRow(step = step, state = BootingSplashLogic.stepState(step, shown))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = info.detail.asString(),
                    fontSize = 12.sp,
                    color = WowMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(18.dp))

                Crossfade(targetState = tipIndex, animationSpec = tween(600), label = "bootTip") { index ->
                    Text(
                        text = tips[index.coerceIn(0, tips.lastIndex)].asString(),
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = WowSubtle,
                        textAlign = TextAlign.Center,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = BootingSplashLogic.backHint(gamepad).asString(),
                    fontSize = 11.sp,
                    color = WowGold.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }

            if (onAbort != null) {
                IconButton(
                    onClick = onAbort,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.game_boot_back_to_launcher),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private val TitleLight = Color(0xFFF0E6D2)
private val TextLight = Color(0xFFE2E8F0)
private val TrackColor = Color(0xFF2A3C54)

/** Gamepad verbunden? Dieselbe Erkennung wie im Spiel (ExternalController.isGameController); nie ein Absturz. */
private fun isGamepadConnected(): Boolean =
    try {
        ExternalController.getControllers().isNotEmpty()
    } catch (_: Throwable) {
        false
    }

@Composable
private fun StepRow(step: BootStage, state: BootingSplashLogic.StepState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (state) {
                BootingSplashLogic.StepState.Done -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.game_boot_done),
                    tint = WowGold,
                    modifier = Modifier.size(18.dp),
                )
                BootingSplashLogic.StepState.Active -> CircularProgressIndicator(
                    color = WowGold,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                BootingSplashLogic.StepState.Pending -> Box(
                    modifier = Modifier
                        .size(8.dp)
                        .border(1.dp, WowSubtle.copy(alpha = 0.6f), CircleShape),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(step.labelRes),
            fontSize = 14.sp,
            fontWeight = if (state == BootingSplashLogic.StepState.Active) FontWeight.SemiBold else FontWeight.Normal,
            color = when (state) {
                BootingSplashLogic.StepState.Done -> WowMuted
                BootingSplashLogic.StepState.Active -> TextLight
                BootingSplashLogic.StepState.Pending -> WowSubtle.copy(alpha = 0.7f)
            },
        )
    }
}

/**
 * Schmaler goldener Balken. progress < 0: unbestimmt, ein ruhiger Abschnitt läuft hin und her;
 * sonst ein echter Füllstand von 0..1.
 */
@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val isIndeterminate = progress < 0f
    val transition = rememberInfiniteTransition(label = "bootBar")
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bootBarPos",
    )

    Canvas(modifier = modifier.clip(RoundedCornerShape(2.dp))) {
        drawRect(color = TrackColor.copy(alpha = 0.7f))
        if (isIndeterminate) {
            val segment = size.width * 0.35f
            val x = (size.width - segment) * position
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, WowGold, Color.Transparent),
                    startX = x,
                    endX = x + segment,
                ),
                topLeft = Offset(x, 0f),
                size = Size(segment, size.height),
            )
        } else {
            drawRect(
                color = WowGold,
                size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
            )
        }
    }
}

@Preview(name = "BootingSplash - Indeterminate")
@Composable
fun BootingSplashPreview() {
    PluviaTheme {
        BootingSplash(visible = true, text = BootingSplashLogic.TEXT_WINE, onAbort = {})
    }
}

@Preview(name = "BootingSplash - 50% Progress")
@Composable
fun BootingSplashProgressPreview() {
    PluviaTheme {
        BootingSplash(
            visible = true,
            text = "Preparing wine-9.0 base...",
            progress = 0.5f,
        )
    }
}

@Preview(name = "BootingSplash - Querformat", device = "spec:width=1920px,height=1080px,dpi=440")
@Composable
fun BootingSplashLandscapePreview() {
    PluviaTheme {
        BootingSplash(
            visible = true,
            text = "Launching game...",
            progress = -1f,
        )
    }
}
