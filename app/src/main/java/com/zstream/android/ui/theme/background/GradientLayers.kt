package com.zstream.android.ui.theme.background

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun rememberDrift(periodMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "signature-drift")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "signature-drift",
    )
}

private fun radialBlobBrush(color: Color, center: Offset, radius: Float, peakAlpha: Float): Brush =
    Brush.radialGradient(
        colorStops = arrayOf(
            0f to color.copy(alpha = peakAlpha),
            0.55f to color.copy(alpha = peakAlpha * 0.35f),
            1f to color.copy(alpha = 0f),
        ),
        center = center,
        radius = radius,
    )

@Composable
fun AuroraBackground(colors: List<Color>, modifier: Modifier = Modifier) {
    val c1 = colors.getOrNull(0) ?: Color(0xFF34D8B0)
    val c2 = colors.getOrNull(1) ?: Color(0xFF7C6BFF)
    val c3 = colors.getOrNull(2) ?: c1
    val t by rememberDrift(26000)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val angle = t * 2f * PI.toFloat()

        val p1 = Offset(w * (0.22f + 0.06f * sin(angle)), h * (0.22f + 0.05f * cos(angle)))
        val p2 = Offset(w * (0.75f + 0.05f * cos(angle * 1.3f)), h * (0.3f + 0.06f * sin(angle * 1.3f)))
        val p3 = Offset(w * (0.5f + 0.07f * sin(angle * 0.7f)), h * (0.85f + 0.04f * cos(angle * 0.7f)))

        drawCircle(radialBlobBrush(c1, p1, w * 0.55f, 0.42f), radius = w * 0.55f, center = p1)
        drawCircle(radialBlobBrush(c2, p2, w * 0.5f, 0.35f), radius = w * 0.5f, center = p2)
        drawCircle(radialBlobBrush(c3, p3, w * 0.6f, 0.3f), radius = w * 0.6f, center = p3)
    }
}

@Composable
fun MeshBackground(colors: List<Color>, modifier: Modifier = Modifier) {
    val c1 = colors.getOrNull(0) ?: Color(0xFFFF2FD0)
    val c2 = colors.getOrNull(1) ?: Color(0xFF00E5FF)
    val t by rememberDrift(30000)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val angle = t * 2f * PI.toFloat()

        val p1 = Offset(w * (0.25f + 0.05f * sin(angle)), h * (0.3f + 0.05f * cos(angle)))
        val p2 = Offset(w * (0.75f + 0.05f * cos(angle * 1.2f)), h * (0.7f + 0.05f * sin(angle * 1.2f)))

        drawCircle(radialBlobBrush(c1, p1, w * 0.5f, 0.45f), radius = w * 0.5f, center = p1)
        drawCircle(radialBlobBrush(c2, p2, w * 0.5f, 0.4f), radius = w * 0.5f, center = p2)
    }
}

@Composable
fun WavesBackground(colors: List<Color>, modifier: Modifier = Modifier) {
    val c1 = colors.getOrNull(0) ?: Color(0xFF2BC7E0)
    val c2 = colors.getOrNull(1) ?: Color(0xFF1E90C9)
    val t by rememberDrift(24000)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val angle = t * 2f * PI.toFloat()

        val p1 = Offset(w * (0.3f + 0.05f * sin(angle)), h * (0.55f + 0.04f * cos(angle)))
        val p2 = Offset(w * (0.7f + 0.05f * cos(angle * 1.1f)), h * (0.42f + 0.04f * sin(angle * 1.1f)))
        val p3 = Offset(w * (0.5f + 0.04f * sin(angle * 0.6f)), h * (0.78f + 0.03f * cos(angle * 0.6f)))

        drawCircle(radialBlobBrush(c1, p1, w * 0.55f, 0.35f), radius = w * 0.55f, center = p1)
        drawCircle(radialBlobBrush(c2, p2, w * 0.5f, 0.3f), radius = w * 0.5f, center = p2)
        drawCircle(radialBlobBrush(c1, p3, w * 0.4f, 0.18f), radius = w * 0.4f, center = p3)
    }
}

@Composable
fun SynthwaveBackground(colors: List<Color>, modifier: Modifier = Modifier) {
    val c1 = colors.getOrNull(0) ?: Color(0xFFFF5C8A)
    val c2 = colors.getOrNull(1) ?: Color(0xFFFFB84D)
    val scroll by rememberDrift(9000)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(c1.copy(alpha = 0.22f), Color.Transparent, c2.copy(alpha = 0.15f)),
                startY = 0f,
                endY = h,
            ),
        )

        val gridTop = h * 0.55f
        val gridHeight = h - gridTop
        val vanishX = w * 0.5f
        val lineColor = c1.copy(alpha = 0.32f)

        val rowCount = 9
        for (i in 0 until rowCount) {
            val progress = ((i + scroll) / rowCount) % 1f
            val eased = progress * progress
            val y = gridTop + eased * gridHeight
            val alpha = (0.35f * (1f - eased)).coerceIn(0f, 0.35f)
            if (alpha <= 0.02f) continue
            drawLine(
                color = lineColor.copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5f,
            )
        }

        val colCount = 7
        for (i in 0..colCount) {
            val fx = i.toFloat() / colCount
            val bottomX = w * fx
            drawLine(
                color = lineColor,
                start = Offset(vanishX, gridTop),
                end = Offset(bottomX, h),
                strokeWidth = 1.2f,
            )
        }
    }
}
