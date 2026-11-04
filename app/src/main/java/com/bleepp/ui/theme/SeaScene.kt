package com.bleepp.ui.theme

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The moving scene behind every screen. Reads the clock only while drawing, so nothing recomposes per frame. */
@Composable
fun SeaScene(look: Look, modifier: Modifier = Modifier) {
    val p = look.palette
    val context = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var seconds by remember { mutableFloatStateOf(0f) }
    val style = look.seaBackground
    LaunchedEffect(style, look.speed, animationsOff) {
        if (style == SeaBackground.OFF || animationsOff) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            seconds += (now - last) / 1000f * look.speed
            last = now
        }
    }
    val stars = remember { List(70) { Star(Random(it * 31 + 7)) } }
    val drops = remember { List(80) { Star(Random(it * 17 + 3)) } }
    Box(modifier.fillMaxSize().background(p.sea)) {
        if (style == SeaBackground.OFF) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val t = seconds
            val k = look.strength
            when (style) {
                SeaBackground.BEAM -> {
                    stars(stars, t, p, k * 0.8f)
                    beam(t, p, k)
                    swells(t, p, k, count = 3, top = 0.86f)
                }
                SeaBackground.TIDE -> swells(t, p, k, count = 5, top = 0.56f)
                SeaBackground.STARS -> stars(stars, t, p, k)
                SeaBackground.RAIN -> rain(drops, t, p, k)
                SeaBackground.FOG -> fog(t, p, k)
                SeaBackground.AURORA -> aurora(t, p, k)
                SeaBackground.OFF -> Unit
            }
        }
    }
}

private class Star(random: Random) {
    val x = random.nextFloat()
    val y = random.nextFloat()
    val size = 0.6f + random.nextFloat() * 1.3f
    val phase = random.nextFloat() * 6.28f
    val pace = 0.4f + random.nextFloat() * 1.2f
}

private fun DrawScope.beam(t: Float, p: Palette, k: Float) {
    val lamp = Offset(size.width * 0.92f, size.height * 0.07f)
    val reach = size.height * 1.5f
    val sweep = 118f + 44f * sin(t * 0.22f)
    wedge(lamp, reach, sweep, half = 9f, colour = p.beam.copy(alpha = 0.26f * k))
    wedge(lamp, reach, sweep, half = 3.5f, colour = p.beam.copy(alpha = 0.16f * k))
    drawCircle(
        Brush.radialGradient(listOf(p.beam.copy(alpha = 0.6f * k), Color.Transparent), lamp, size.minDimension * 0.16f),
        size.minDimension * 0.16f, lamp,
    )
    drawCircle(p.beam.copy(alpha = 0.9f * k), size.minDimension * 0.012f, lamp)
}

private fun DrawScope.wedge(origin: Offset, reach: Float, angle: Float, half: Float, colour: Color) {
    fun edge(degrees: Float): Offset {
        val r = degrees * PI.toFloat() / 180f
        return Offset(origin.x + cos(r) * reach, origin.y + sin(r) * reach)
    }
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        edge(angle - half).let { lineTo(it.x, it.y) }
        edge(angle + half).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, Brush.radialGradient(listOf(colour, Color.Transparent), origin, reach))
}

private fun DrawScope.swells(t: Float, p: Palette, k: Float, count: Int, top: Float) {
    for (i in 0 until count) {
        val base = size.height * (top + (1f - top) * i / count)
        val wave = size.height * (0.012f + 0.006f * i)
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, base)
            var x = 0f
            while (x <= size.width + 16f) {
                val y = base + sin(x / size.width * 6.28f * (1.2f + i * 0.3f) + t * (0.5f + i * 0.13f) + i) * wave
                lineTo(x, y)
                x += 16f
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, lerp(p.sea, p.swell, (0.35f + 0.65f * (i + 1) / count) * k))
    }
}

private fun DrawScope.stars(stars: List<Star>, t: Float, p: Palette, k: Float) {
    for (s in stars) {
        val glow = 0.45f + 0.55f * sin(t * s.pace + s.phase)
        drawCircle(p.text.copy(alpha = (0.5f * glow * k).coerceIn(0f, 1f)), s.size * density * 0.8f, Offset(s.x * size.width, s.y * size.height * 0.8f))
    }
}

private fun DrawScope.rain(drops: List<Star>, t: Float, p: Palette, k: Float) {
    for (d in drops) {
        val fall = (d.y + t * 0.32f * d.pace) % 1f
        val x = (d.x + fall * 0.12f) % 1f * size.width
        val y = fall * size.height
        val length = 14f * d.size * density
        drawLine(p.text.copy(alpha = 0.2f * k), Offset(x, y), Offset(x + length * 0.2f, y + length), strokeWidth = density, cap = StrokeCap.Round)
    }
}

private fun DrawScope.fog(t: Float, p: Palette, k: Float) {
    for (i in 0 until 5) {
        val x = size.width * (0.5f + 0.6f * sin(t * 0.06f * (i + 1) + i * 1.7f))
        val y = size.height * (0.15f + 0.18f * i)
        val r = size.width * (0.6f + 0.1f * i)
        drawCircle(Brush.radialGradient(listOf(p.text.copy(alpha = 0.09f * k), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
    }
}

private fun DrawScope.aurora(t: Float, p: Palette, k: Float) {
    for (i in 0 until 3) {
        val path = Path().apply {
            var x = 0f
            val top = size.height * (0.08f + 0.1f * i)
            moveTo(0f, top)
            while (x <= size.width + 16f) {
                lineTo(x, top + sin(x / size.width * 5f + t * 0.3f + i * 2f) * size.height * 0.05f)
                x += 16f
            }
            lineTo(size.width, size.height * 0.62f)
            lineTo(0f, size.height * 0.62f)
            close()
        }
        val colour = lerp(p.beam, p.swell, i / 3f)
        drawPath(path, Brush.verticalGradient(listOf(colour.copy(alpha = 0.2f * k), Color.Transparent), startY = 0f, endY = size.height * 0.62f))
    }
}
