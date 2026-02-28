package com.blackbox.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Applies a neon-colored border with a simulated outer glow halo.
 *
 * Draws three expanding, fading rectangles behind the composable to simulate
 * a soft neon glow, then adds a sharp [border] on top. This is a pure
 * Compose commonMain implementation that avoids Android-specific BlurMaskFilter.
 *
 * @param color The neon color for the border and glow.
 * @param borderWidth Thickness of the hard border line.
 * @param cornerRadius Corner rounding for both border and glow.
 */
fun Modifier.neonBorder(
    color: Color,
    borderWidth: Dp = Dimens.NeonBorderWidth,
    cornerRadius: Dp = 4.dp,
): Modifier = this
    .drawBehind {
        // Simulate glow with 3 expanding semi-transparent rects
        val expansions = listOf(8f to 0.08f, 5f to 0.12f, 3f to 0.18f)
        for ((expand, alpha) in expansions) {
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(-expand, -expand),
                size = Size(size.width + expand * 2, size.height + expand * 2),
                cornerRadius = CornerRadius(cornerRadius.toPx() + expand),
            )
        }
    }
    .border(
        width = borderWidth,
        color = color,
        shape = RoundedCornerShape(cornerRadius),
    )

/**
 * Applies a subtle neon-tinted background fill for the card glow effect.
 *
 * @param color The neon color to use as a faint background tint.
 */
fun Modifier.neonGlowBackground(color: Color): Modifier = this.drawBehind {
    drawRect(color = color)
}

/**
 * Wraps [content] in an animated scan-line overlay.
 *
 * A semi-transparent stripe sweeps top-to-bottom every 3 seconds,
 * providing the classic CRT/terminal ambiance. Uses only KMP-safe APIs.
 *
 * @param content The content to overlay with the scan line effect.
 */
@Composable
fun ScanLineOverlay(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        val infiniteTransition = rememberInfiniteTransition(label = "scanline")
        val scanY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "scanline_y",
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val stripeH = Dimens.ScanLineHeight.toPx() * 4
            val y = scanY * size.height
            drawRect(
                color = Color(0x0800FF41), // ~3% neon green — very subtle
                topLeft = Offset(0f, y),
                size = Size(size.width, stripeH),
            )
        }
    }
}

/**
 * A pulsing neon indicator — solid dot with an expanding ring.
 *
 * Continuously animates using [rememberInfiniteTransition].
 * Built entirely from KMP-safe Compose Canvas APIs.
 *
 * @param color The neon color for the pulse and dot.
 * @param size Total bounding box size of the indicator.
 */
@Composable
fun NeonPulseIndicator(
    color: Color,
    size: Dp = 48.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_radius",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_alpha",
    )

    Canvas(modifier = Modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = this.size.minDimension / 2f
        val dotRadius = maxRadius * 0.25f

        // Expanding ring
        drawCircle(
            color = color.copy(alpha = pulseAlpha),
            radius = dotRadius + (maxRadius - dotRadius) * pulseRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Solid dot center
        drawCircle(
            color = color,
            radius = dotRadius,
            center = center,
        )
    }
}

/**
 * Displays [text] with a blinking block cursor character appended.
 *
 * Toggles the cursor every 500ms to simulate a classic terminal cursor.
 *
 * @param text The text content to display before the cursor.
 * @param style The [TextStyle] applied to the text.
 * @param color The color applied to text and cursor.
 */
@Composable
fun BlinkingCursorText(
    text: String,
    style: TextStyle,
    color: Color,
) {
    var showCursor by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            showCursor = !showCursor
        }
    }
    val cursor = if (showCursor) "▮" else " "
    Text(
        text = "$text$cursor",
        style = style,
        color = color,
    )
}
