package com.blackbox.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Card / surface modifiers ───────────────────────────────────────────────────

/**
 * Applies the Obsidian card styling: surface background, subtle border,
 * and the given corner radius. Use this on any card container.
 *
 * @param cornerRadius Corner rounding. Defaults to [Dimens.RadiusMd] (16 dp).
 * @param borderColor  Border colour. Defaults to [BlackBoxColors.Border].
 * @param bgColor      Background fill. Defaults to [BlackBoxColors.Surface].
 */
fun Modifier.obsidianCard(
    cornerRadius: Dp = 16.dp,
    borderColor: Color = BlackBoxColors.Border,
    bgColor: Color = BlackBoxColors.Surface,
): Modifier = this
    .background(bgColor, RoundedCornerShape(cornerRadius))
    .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))

/**
 * Applies a subtle tinted border used for active/selected state or coloured card sections.
 *
 * @param color        The accent colour for the border.
 * @param cornerRadius Corner rounding.
 * @param alpha        Opacity of the border line.
 */
fun Modifier.accentBorder(
    color: Color,
    cornerRadius: Dp = 16.dp,
    alpha: Float = 0.45f,
): Modifier = this
    .background(color.copy(alpha = 0.06f), RoundedCornerShape(cornerRadius))
    .border(1.dp, color.copy(alpha = alpha), RoundedCornerShape(cornerRadius))

// ── Accent bar (left vertical stripe) ─────────────────────────────────────────

/**
 * Draws a coloured vertical stripe on the left edge of the composable.
 *
 * Used to visually code cards by data category (location, activity, etc.)
 * without adding heavy borders everywhere.
 *
 * @param color Stripe colour.
 * @param width Stripe width in dp.
 */
fun Modifier.accentLeftBar(color: Color, width: Dp = 3.dp): Modifier = this.drawBehind {
    drawRect(
        color = color,
        topLeft = Offset.Zero,
        size = Size(width.toPx(), size.height),
    )
}

// ── Shimmer loading effect ─────────────────────────────────────────────────────

/**
 * Applies a sweeping shimmer gradient to simulate a loading skeleton.
 *
 * The shimmer sweeps left-to-right using an infinite [animateFloat] transition.
 * Drawn as a [Brush.linearGradient] using the [drawBehind] modifier so it is
 * fully KMP-compatible (no BlurMaskFilter or Android-only APIs).
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_x",
    )
    return this.drawBehind {
        val shimmerColors = listOf(
            BlackBoxColors.SurfaceVariant,
            BlackBoxColors.SurfaceElevated,
            BlackBoxColors.SurfaceVariant,
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(translateX, 0f),
                end = Offset(translateX + 600f, size.height),
            ),
        )
    }
}

// ── Loading indicator ──────────────────────────────────────────────────────────

/**
 * Three pulsing dots loading indicator.
 *
 * Each dot fades in/out with a slight phase offset for a wave effect.
 * Uses only KMP-safe Compose APIs.
 *
 * @param color Dot colour.
 * @param dotSize Diameter of each dot.
 */
@Composable
fun PulseDotsIndicator(
    color: Color = BlackBoxColors.Indigo,
    dotSize: Dp = 8.dp,
) {
    val transition = rememberInfiniteTransition(label = "pulse_dots")

    val delays = listOf(0, 200, 400)
    val alphas = delays.map { delay ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600,
                    delayMillis = delay,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot_alpha_$delay",
        )
    }

    Row {
        alphas.forEachIndexed { _, alphaState ->
            val alpha by alphaState
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
            if (alphaState != alphas.last()) {
                Box(modifier = Modifier.width(6.dp))
            }
        }
    }
}

// ── Gradient helpers ───────────────────────────────────────────────────────────

/**
 * Returns an indigo→teal [Brush.linearGradient] for hero number text or chart fills.
 *
 * Pass this to [TextStyle.brush] or use in Canvas [drawBehind] calls.
 */
fun indigoTealGradient(): Brush = Brush.linearGradient(
    colors = listOf(BlackBoxColors.IndigoLight, BlackBoxColors.TealLight),
)

/**
 * Returns a top-fade gradient useful for chart bar fills:
 * full [color] at the top, transparent at the bottom.
 */
fun barGradient(color: Color): Brush = Brush.verticalGradient(
    colors = listOf(color, color.copy(alpha = 0.15f)),
)

// ── Pulse ring (for onboarding / ready state) ──────────────────────────────────

/**
 * A single animated expanding ring — used on the onboarding ready screen.
 *
 * @param color Ring colour.
 * @param size  Bounding box size.
 */
@Composable
fun PulseRingIndicator(
    color: Color = BlackBoxColors.Indigo,
    size: Dp = 80.dp,
) {
    val transition = rememberInfiniteTransition(label = "ring_pulse")
    val radius by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring_radius",
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring_alpha",
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxR = this.size.minDimension / 2f
        val dotR = maxR * 0.28f

        // Expanding ring
        drawCircle(
            color = color.copy(alpha = ringAlpha),
            radius = dotR + (maxR - dotR) * radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        // Solid centre dot
        drawCircle(
            color = color,
            radius = dotR,
            center = center,
        )
    }
}

// ── Backward-compat stubs ──────────────────────────────────────────────────────
// These keep legacy call sites compiling while the screen rewrites are in progress.

/** @deprecated Use obsidianCard() instead. */
fun Modifier.neonBorder(
    color: Color,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 16.dp,
): Modifier = this
    .background(BlackBoxColors.Surface, RoundedCornerShape(cornerRadius))
    .border(borderWidth, color.copy(alpha = 0.4f), RoundedCornerShape(cornerRadius))

/** @deprecated No-op in Obsidian theme. */
fun Modifier.neonGlowBackground(color: Color): Modifier = this

/** @deprecated Removed — no scan lines in Obsidian theme. */
@Composable
fun ScanLineOverlay(content: @Composable () -> Unit) = content()

/** @deprecated Use PulseRingIndicator instead. */
@Composable
fun NeonPulseIndicator(color: Color, size: Dp = 48.dp) = PulseRingIndicator(color = color, size = size)

/** @deprecated Removed — no blinking cursors in Obsidian theme. */
@Composable
fun BlinkingCursorText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color) {
    androidx.compose.material3.Text(text = text, style = style, color = color)
}
