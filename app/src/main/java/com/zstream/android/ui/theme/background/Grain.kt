package com.zstream.android.ui.theme.background

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.random.Random

private fun buildNoiseBitmap(size: Int = 64): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val v = Random.nextInt(256)
            val a = Random.nextInt(40, 90)
            bitmap.setPixel(x, y, (a shl 24) or (v shl 16) or (v shl 8) or v)
        }
    }
    return bitmap
}

@Composable
fun Grain(opacity: Float, modifier: Modifier = Modifier) {
    if (opacity <= 0f) return
    val bitmap = remember { buildNoiseBitmap() }
    val shader = remember(bitmap) {
        android.graphics.BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    val brush = remember(shader) { ShaderBrush(shader) }

    Canvas(modifier = modifier) {
        drawRect(brush = brush, alpha = opacity.coerceIn(0f, 1f))
    }
}
