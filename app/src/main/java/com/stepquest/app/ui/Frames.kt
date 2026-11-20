package com.stepquest.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Classic fantasy-MMO style chrome, drawn in code: dark interiors inside bevelled iron frames
 * with a gold hairline and riveted corners.
 */
object Mmo {
    val ironLight = Color(0xFF9A8A68)
    val iron = Color(0xFF4A4034)
    val ironDark = Color(0xFF15110D)
    val gold = Color(0xFFD9B04C)
    val goldDeep = Color(0xFF7A5A1E)
    val leather = Color(0xFF1E1711)
    val leatherLight = Color(0xFF2E241A)
    val parchment = Color(0xFFE6D3A6)
    val parchmentDark = Color(0xFFC9AF7A)
    val ink = Color(0xFF3A2614)
}

enum class Interior(val top: Color, val bottom: Color) {
    Leather(Color(0xFF2A2017), Color(0xFF130E0A)),
    Slate(Color(0xFF1F2430), Color(0xFF0E1016)),
    Parchment(Color(0xFFEBDAB0), Color(0xFFD3BA85)),
    Crimson(Color(0xFF5A1712), Color(0xFF2A0907)),
    Bronze(Color(0xFF4A3618), Color(0xFF241809)),
    Moss(Color(0xFF1E3A22), Color(0xFF0C1A0F)),
}

/** Draws the bevelled iron frame. [band] is the metal width. */
fun DrawScope.ironFrame(band: Float, radius: Float, rivets: Boolean, goldLine: Boolean = true) {
    val w = size.width; val h = size.height
    // Outer black edge.
    drawRoundRect(Mmo.ironDark, cornerRadius = CornerRadius(radius + 1), style = Stroke(2f))
    // Metal band: lit from above, shadowed below.
    drawRoundRect(
        Brush.verticalGradient(listOf(Mmo.ironLight, Mmo.iron, Mmo.ironDark)),
        topLeft = Offset(1f, 1f), size = Size(w - 2, h - 2), cornerRadius = CornerRadius(radius), style = Stroke(band),
    )
    // Inner bevel line + gold hairline.
    val i = band / 2 + 1
    drawRoundRect(Color.Black.copy(alpha = 0.7f), Offset(i, i), Size(w - 2 * i, h - 2 * i), CornerRadius((radius - band / 2).coerceAtLeast(0f)), style = Stroke(2f))
    if (goldLine) drawRoundRect(Mmo.gold.copy(alpha = 0.55f), Offset(i + 2, i + 2), Size(w - 2 * i - 4, h - 2 * i - 4), CornerRadius((radius - band / 2).coerceAtLeast(0f)), style = Stroke(1f))
    if (rivets) {
        val r = band * 0.55f
        listOf(Offset(band / 2 + 1, band / 2 + 1), Offset(w - band / 2 - 1, band / 2 + 1), Offset(band / 2 + 1, h - band / 2 - 1), Offset(w - band / 2 - 1, h - band / 2 - 1)).forEach { c ->
            // Diamond-shaped gold stud with a highlight.
            val p = Path().apply { moveTo(c.x, c.y - r * 1.4f); lineTo(c.x + r * 1.4f, c.y); lineTo(c.x, c.y + r * 1.4f); lineTo(c.x - r * 1.4f, c.y); close() }
            drawPath(p, Brush.linearGradient(listOf(Color(0xFFFFE79A), Mmo.gold, Mmo.goldDeep), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
            drawPath(p, Mmo.ironDark, style = Stroke(1.2f))
        }
    }
}

/** A framed panel: textured interior inside an iron frame. */
fun Modifier.mmoFrame(interior: Interior = Interior.Leather, band: Dp = 5.dp, radius: Dp = 6.dp, rivets: Boolean = true, seed: Int = 0): Modifier = drawWithCache {
    val b = band.toPx(); val r = radius.toPx()
    val rnd = Random(seed + 17)
    // Grain specks for a leather / stone texture.
    val specks = List((size.width * size.height / 900f).toInt().coerceAtMost(500)) {
        Triple(Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height), rnd.nextFloat() * 1.8f + 0.6f, rnd.nextBoolean())
    }
    val fill = Brush.verticalGradient(listOf(interior.top, interior.bottom))
    val vignette = Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (interior == Interior.Parchment) 0.25f else 0.45f)),
        center = Offset(size.width / 2, size.height / 2), radius = maxOf(size.width, size.height) * 0.75f)
    onDrawWithContent {
        drawRoundRect(fill, cornerRadius = CornerRadius(r))
        specks.forEach { (o, s, light) -> drawCircle(if (light) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.10f), s, o) }
        drawRoundRect(vignette, cornerRadius = CornerRadius(r))
        ironFrame(b, r, rivets)
        drawContent()
    }
}

/** Glossy MMO resource bar: recessed trough, gradient fill with a highlight band. */
fun DrawScope.resourceBar(fraction: Float, color: Color, ticks: Int = 0) {
    val w = size.width; val h = size.height; val r = CornerRadius(h * 0.25f)
    drawRoundRect(Color(0xFF070505), cornerRadius = r)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF221A14), Color(0xFF0E0A08))), Offset(1f, 1f), Size(w - 2, h - 2), r)
    val fw = (w - 2) * fraction.coerceIn(0f, 1f)
    if (fw > 0) {
        drawRoundRect(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.18f), lerp(color, Color.Black, 0.15f), lerp(color, Color.Black, 0.5f))), Offset(1f, 1f), Size(fw, h - 2), r)
        drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(2f, 2f), Size((fw - 2).coerceAtLeast(0f), (h - 4) * 0.28f), r)
    }
    for (i in 1 until ticks) {
        val x = w * i / ticks
        drawLine(Color.Black.copy(alpha = 0.65f), Offset(x, 1f), Offset(x, h - 1), 1.5f)
        drawLine(Color.White.copy(alpha = 0.10f), Offset(x + 1.5f, 1f), Offset(x + 1.5f, h - 1), 1f)
    }
    drawRoundRect(Mmo.iron, cornerRadius = r, style = Stroke(1.5f))
}

/** Dark stone-and-timber backdrop behind every screen. */
@Composable
fun HallBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1C1712), Color(0xFF0C0907))))
        val plank = 46.dp.toPx()
        var x = 0f; var i = 0
        val rnd = Random(5)
        while (x < size.width) {
            drawRect(Color.White.copy(alpha = 0.012f + rnd.nextFloat() * 0.02f), Offset(x, 0f), Size(plank - 2, size.height))
            drawLine(Color.Black.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), 2f)
            x += plank; i++
        }
        drawRect(Brush.radialGradient(listOf(Color(0x22FFB050), Color.Transparent), Offset(size.width / 2, 0f), size.width))
    }
}
