package com.zstream.android.ui.theme.background

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun Vignette(opacity: Float, modifier: Modifier = Modifier) {
    if (opacity <= 0f) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.4f to Color.Transparent,
                    1f to Color.Black.copy(alpha = opacity.coerceIn(0f, 1f)),
                ),
                center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.4f),
                radius = kotlin.math.max(w, h) * 0.8f,
            ),
        )
    }
}
