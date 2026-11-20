package com.stepquest.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepquest.app.art.PixelArt

object Sq {
    val ink = Color(0xFF0C0907)
    val panel = Color(0xFF1E1711)
    val panel2 = Color(0xFF2A2017)
    val gold = Color(0xFFF0C75A)
    val goldDim = Color(0xFF8C6B3A)
    val text = Color(0xFFF3E9D2)
    val muted = Color(0xFFBFAE8A)
    val hp = Color(0xFF2FB83A)
    val mp = Color(0xFF2F6BDB)
    val exp = Color(0xFFC79BFF)
    val danger = Color(0xFFE0574A)
    val good = Color(0xFF7BE08A)
    val serif = FontFamily.Serif
    fun rarity(r: com.stepquest.core.economy.Rarity) = when (r) {
        com.stepquest.core.economy.Rarity.Common -> text
        com.stepquest.core.economy.Rarity.Uncommon -> Color(0xFF7BE08A)
        com.stepquest.core.economy.Rarity.Rare -> Color(0xFF6FB4FF)
        com.stepquest.core.economy.Rarity.Epic -> Color(0xFFC79BFF)
        com.stepquest.core.economy.Rarity.Legendary -> Color(0xFFFFB347)
    }
}

/** MMO-style lettering: serif with a hard black outline-like shadow. */
private val outline = Shadow(Color.Black, Offset(1.5f, 1.5f), 2f)

/**
 * Text with a true dark outline (a stroke drawn under the fill), so it stays readable on any
 * background, from bright bar fills to sky in the diorama.
 */
@Composable
fun T(text: String, size: TextUnit = 15.sp, color: Color = Sq.text, bold: Boolean = false, serif: Boolean = true,
      modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    val base = TextStyle(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFamily = if (serif) Sq.serif else FontFamily.Default,
        fontSize = size, textAlign = align ?: TextAlign.Unspecified)
    val strokeW = (size.value * 0.22f).coerceIn(2f, 5f)
    Box(modifier) {
        Text(text, color = Color(0xF2080503), maxLines = maxLines, style = base.copy(drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(strokeW, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
        Text(text, color = color, maxLines = maxLines, style = base.copy(shadow = outline))
    }
}

@Composable fun Title(text: String, modifier: Modifier = Modifier) = T(text, 24.sp, Sq.gold, bold = true, modifier = modifier.padding(bottom = 6.dp))
@Composable fun H2(text: String, modifier: Modifier = Modifier) = T(text, 18.sp, Sq.gold, bold = true, modifier = modifier.padding(bottom = 4.dp))
@Composable fun H3(text: String, color: Color = Sq.text, modifier: Modifier = Modifier) = T(text, 16.sp, color, bold = true, modifier = modifier)
@Composable fun Small(text: String, color: Color = Sq.muted, modifier: Modifier = Modifier) = T(text, 13.sp, color, modifier = modifier)
@Composable fun Tiny(text: String, color: Color = Sq.muted, modifier: Modifier = Modifier) = T(text, 11.sp, color, modifier = modifier)

/** Leather panel in a riveted iron frame. [inset] = slate, [glow] = bronze with a brighter trim. */
@Composable
fun Panel(modifier: Modifier = Modifier, inset: Boolean = false, glow: Boolean = false, interior: Interior? = null, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier.fillMaxWidth().padding(bottom = 10.dp)
            .mmoFrame(interior ?: when { glow -> Interior.Bronze; inset -> Interior.Slate; else -> Interior.Leather }, band = 3.5.dp, seed = 1)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )

/** A smaller framed row. Moss when done, dimmed when locked. */
@Composable
fun Card(modifier: Modifier = Modifier, done: Boolean = false, locked: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier.fillMaxWidth().padding(bottom = 8.dp).alpha(if (locked) 0.55f else 1f)
            .mmoFrame(if (done) Interior.Moss else Interior.Leather, band = 2.dp, radius = 4.dp, rivets = false, seed = 2)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )

/** Bevelled metal button with gold lettering: crimson = primary/danger, bronze = selected. */
@Composable
fun Btn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, danger: Boolean = false,
        ghost: Boolean = false, small: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val interior = when { primary || danger -> Interior.Crimson; selected -> Interior.Bronze; ghost -> Interior.Slate; else -> Interior.Leather }
    Box(
        modifier.padding(3.dp).alpha(if (enabled) 1f else 0.4f)
            .mmoFrame(interior, band = if (small) 2.dp else 2.5.dp, radius = 5.dp, rivets = false, seed = 3)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 14.dp, vertical = if (small) 8.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, if (small) 13.sp else 15.sp, if (primary || selected) Color(0xFFFFE08A) else Sq.gold, bold = true, align = TextAlign.Center) }
}

/** Glossy resource bar; optional [label] is drawn centred on it, MMO unit-frame style. */
@Composable
fun Bar(fraction: Float, color: Color = Sq.gold, height: Dp = 9.dp, modifier: Modifier = Modifier, label: String? = null, ticks: Int = 0) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "bar")
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { resourceBar(f, color, ticks) }
        if (label != null) T(label, (height.value * 0.62f).sp, Color.White, bold = true, serif = false)
    }
}

@Composable
fun Pixel(bitmap: Bitmap, modifier: Modifier = Modifier) {
    val img = remember(bitmap) { bitmap.asImageBitmap() }
    Image(img, null, modifier, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
}

@Composable fun Icon(kind: String, size: Dp = 22.dp, modifier: Modifier = Modifier) = Pixel(PixelArt.icon(kind), modifier.size(size))

@Composable
fun RowC(modifier: Modifier = Modifier, h: Arrangement.Horizontal = Arrangement.Start, content: @Composable RowScope.() -> Unit) =
    Row(modifier.fillMaxWidth(), horizontalArrangement = h, verticalAlignment = Alignment.CenterVertically, content = content)

fun n(v: Long) = "%,d".format(v)
fun n(v: Int) = "%,d".format(v)
fun km(v: Double) = when { v < 10 -> "%.2f km".format(v); v < 1000 -> "%.1f km".format(v); else -> "%,.0f km".format(v) }
