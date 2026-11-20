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
    val ink = Color(0xFF0B1020)
    val panel = Color(0xE60C1121)
    val panel2 = Color(0xEB161E36)
    val gold = Color(0xFFD8B765)
    val goldDim = Color(0xFF8C6B3A)
    val text = Color(0xFFF3E9D2)
    val muted = Color(0xFFB9AB8A)
    val hp = Color(0xFF6FD36F)
    val mp = Color(0xFF5AA8FF)
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

@Composable
fun T(text: String, size: TextUnit = 15.sp, color: Color = Sq.text, bold: Boolean = false, serif: Boolean = false,
      modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = color, fontSize = size, maxLines = maxLines, textAlign = align,
        style = TextStyle(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFamily = if (serif) Sq.serif else FontFamily.Default))

@Composable fun Title(text: String, modifier: Modifier = Modifier) = T(text, 24.sp, Sq.gold, bold = true, serif = true, modifier = modifier.padding(bottom = 6.dp))
@Composable fun H2(text: String, modifier: Modifier = Modifier) = T(text, 18.sp, Sq.gold, bold = true, serif = true, modifier = modifier.padding(bottom = 4.dp))
@Composable fun H3(text: String, color: Color = Sq.text, modifier: Modifier = Modifier) = T(text, 16.sp, color, bold = true, modifier = modifier)
@Composable fun Small(text: String, color: Color = Sq.muted, modifier: Modifier = Modifier) = T(text, 13.sp, color, modifier = modifier)
@Composable fun Tiny(text: String, color: Color = Sq.muted, modifier: Modifier = Modifier) = T(text, 11.sp, color, modifier = modifier)

@Composable
fun Panel(modifier: Modifier = Modifier, inset: Boolean = false, glow: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(10.dp))
            .background(if (inset) Sq.panel2 else Sq.panel)
            .border(1.5.dp, if (glow) Sq.gold else Sq.goldDim, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )

@Composable
fun Card(modifier: Modifier = Modifier, done: Boolean = false, locked: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier.fillMaxWidth().padding(bottom = 8.dp).alpha(if (locked) 0.55f else 1f).clip(RoundedCornerShape(8.dp))
            .background(Sq.panel2)
            .border(1.dp, if (done) Sq.good else Sq.gold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, danger: Boolean = false,
        ghost: Boolean = false, small: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        selected -> Color(0xFF5A4320); primary -> Color(0xFF6B4F1D); danger -> Color(0xFF4A1A16)
        ghost -> Color.Transparent; else -> Color(0xFF2B2416)
    }
    val border = when { danger -> Sq.danger; ghost -> Sq.goldDim; selected -> Color(0xFFFFF3CF); else -> Sq.gold }
    Box(
        modifier.padding(3.dp).alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(8.dp)).background(bg)
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 6.dp else 11.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, if (small) 13.sp else 15.sp, if (primary) Color(0xFFFFF3CF) else Sq.text, bold = true, align = TextAlign.Center) }
}

@Composable
fun Bar(fraction: Float, color: Color = Sq.gold, height: Dp = 9.dp, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(Color(0x8C000000))
        .border(0.5.dp, Color(0x1FFFFFFF), RoundedCornerShape(height / 2))) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(color))
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
