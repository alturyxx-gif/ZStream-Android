package com.zstream.android.ui.theme.background

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.random.Random

enum class ParticleVariant { STARFIELD, PETALS, EMBERS }

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var r: Float,
    var a: Float,
    var rot: Float,
    var vrot: Float,
)

private val particleCounts = mapOf(
    ParticleVariant.STARFIELD to 70,
    ParticleVariant.PETALS to 28,
    ParticleVariant.EMBERS to 40,
)

private fun rand(min: Float, max: Float) = min + Random.nextFloat() * (max - min)

private fun spawn(variant: ParticleVariant, w: Float, h: Float): Particle = when (variant) {
    ParticleVariant.STARFIELD -> Particle(
        x = rand(0f, w), y = rand(0f, h),
        vx = rand(-0.35f, 0.35f), vy = rand(-0.35f, 0.35f),
        r = rand(1.2f, 3.2f), a = rand(0.25f, 1f), rot = 0f, vrot = 0f,
    )
    ParticleVariant.PETALS -> Particle(
        x = rand(0f, w), y = rand(-h, 0f),
        vx = rand(-4f, 4f), vy = rand(4f, 10f),
        r = rand(5f, 10f), a = rand(0.4f, 0.9f),
        rot = rand(0f, (2 * PI).toFloat()), vrot = rand(-0.25f, 0.25f),
    )
    ParticleVariant.EMBERS -> Particle(
        x = rand(0f, w), y = rand(h * 0.35f, h),
        vx = rand(-2.4f, 2.4f), vy = rand(-6.5f, -2f),
        r = rand(1.6f, 4.6f), a = rand(0.3f, 0.9f), rot = 0f, vrot = 0f,
    )
}

@Composable
fun ParticleField(variant: ParticleVariant, colors: List<Color>, modifier: Modifier = Modifier) {
    val frame = remember { mutableLongStateOf(0L) }
    val particles = remember(variant) { mutableListOf<Particle>() }

    LaunchedEffect(variant) {
        while (true) {
            withFrameMillis { frame.longValue = it }
        }
    }

    Canvas(modifier = modifier) {
        frame.longValue
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        if (particles.isEmpty()) {
            val count = particleCounts.getValue(variant)
            repeat(count) { particles.add(spawn(variant, w, h)) }
        }

        val color = colors.firstOrNull() ?: Color.White
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            p.rot += p.vrot

            when (variant) {
                ParticleVariant.STARFIELD -> {
                    p.a += rand(-0.02f, 0.02f)
                    if (p.a < 0.15f) p.a = 0.15f
                    if (p.a > 1f) p.a = 1f
                    if (p.x < 0f) p.x = w
                    if (p.x > w) p.x = 0f
                    if (p.y < 0f) p.y = h
                    if (p.y > h) p.y = 0f
                }
                ParticleVariant.PETALS -> {
                    if (p.y > h + 20f) {
                        p.y = -20f
                        p.x = rand(0f, w)
                    }
                    if (p.x < -20f) p.x = w + 20f
                    if (p.x > w + 20f) p.x = -20f
                }
                ParticleVariant.EMBERS -> {
                    if (p.y < -20f) {
                        p.y = h + 20f
                        p.x = rand(0f, w)
                    }
                }
            }

            drawParticle(this, variant, p, color)
        }
    }
}

private fun drawParticle(scope: DrawScope, variant: ParticleVariant, p: Particle, color: Color) {
    when (variant) {
        ParticleVariant.PETALS -> {
            val rx = p.r
            val ry = p.r * 0.55f
            scope.rotate(degrees = p.rot * (180f / PI.toFloat()), pivot = Offset(p.x, p.y)) {
                drawOval(
                    color = color.copy(alpha = p.a),
                    topLeft = Offset(p.x - rx, p.y - ry),
                    size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
                    style = Fill,
                )
            }
        }
        ParticleVariant.EMBERS -> {
            scope.drawCircle(
                color = color.copy(alpha = (p.a * 0.35f).coerceIn(0f, 1f)),
                radius = p.r * 2.2f,
                center = Offset(p.x, p.y),
            )
            scope.drawCircle(color = color.copy(alpha = p.a), radius = p.r, center = Offset(p.x, p.y))
        }
        ParticleVariant.STARFIELD -> {
            scope.drawCircle(color = color.copy(alpha = p.a), radius = p.r, center = Offset(p.x, p.y))
        }
    }
}
