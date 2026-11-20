package com.stepquest.app.art

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Colours are ARGB ints. Alpha 254 marks "emissive": the shading pass leaves it alone. */
fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
fun hex(s: String): Int {
    val h = s.removePrefix("#")
    val r = h.substring(0, 2).toInt(16); val g = h.substring(2, 4).toInt(16); val b = h.substring(4, 6).toInt(16)
    val a = if (h.length >= 8) h.substring(6, 8).toInt(16) else 255
    return argb(a, r, g, b)
}
fun alphaOf(c: Int) = (c ushr 24) and 0xff
fun withAlpha(c: Int, a: Int) = (c and 0x00ffffff) or (a shl 24)
fun glow(c: Int) = withAlpha(c, 254)
fun lerp(a: Int, b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    fun ch(sh: Int) = (((a ushr sh) and 0xff) + ((((b ushr sh) and 0xff) - ((a ushr sh) and 0xff)) * tt)).toInt()
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
const val CLEAR = 0
val WHITE = hex("#ffffff")
val BLACK = hex("#000000")

class V(x: Number, y: Number) { val x = x.toFloat(); val y = y.toFloat() }
fun i(f: Number): Int = f.toFloat().toInt()

/** Tiny raster for drawing pixel art in code. (0,0) is the top-left. */
class PixelCanvas(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h
    fun get(x: Int, y: Int) = if (inside(x, y)) px[y * w + x] else CLEAR
    fun set(x: Number, y: Number, c: Int) { val xi = x.toInt(); val yi = y.toInt(); if (inside(xi, yi)) px[yi * w + xi] = c }

    fun rect(x: Number, y: Number, rw: Number, rh: Number, c: Int) {
        val x0 = x.toInt(); val y0 = y.toInt()
        for (j in y0 until y0 + rh.toInt()) for (k in x0 until x0 + rw.toInt()) set(k, j, c)
    }

    fun ellipse(cx: Number, cy: Number, rx: Number, ry: Number, c: Int) {
        val fx = cx.toFloat(); val fy = cy.toFloat(); val frx = rx.toFloat(); val fry = ry.toFloat()
        for (j in floor(fy - fry).toInt()..ceil(fy + fry).toInt())
            for (k in floor(fx - frx).toInt()..ceil(fx + frx).toInt()) {
                val dx = (k + 0.5f - fx) / frx; val dy = (j + 0.5f - fy) / fry
                if (dx * dx + dy * dy <= 1f) set(k, j, c)
            }
    }

    fun circle(cx: Number, cy: Number, r: Number, c: Int) = ellipse(cx, cy, r, r, c)

    fun line(x0: Number, y0: Number, x1: Number, y1: Number, c: Int, thickness: Int = 1) {
        var x = x0.toInt(); var y = y0.toInt(); val xe = x1.toInt(); val ye = y1.toInt()
        val dx = abs(xe - x); val dy = -abs(ye - y); val sx = if (x < xe) 1 else -1; val sy = if (y < ye) 1 else -1
        var err = dx + dy
        while (true) {
            for (t in 0 until thickness) for (u in 0 until thickness) set(x + t, y + u, c)
            if (x == xe && y == ye) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    fun tri(a: V, b: V, c: V, col: Int) {
        val minY = floor(min(a.y, min(b.y, c.y))).toInt(); val maxY = ceil(max(a.y, max(b.y, c.y))).toInt()
        val minX = floor(min(a.x, min(b.x, c.x))).toInt(); val maxX = ceil(max(a.x, max(b.x, c.x))).toInt()
        fun sign(px: Float, py: Float, p2: V, p3: V) = (px - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (py - p3.y)
        for (y in minY..maxY) for (x in minX..maxX) {
            val px = x + 0.5f; val py = y + 0.5f
            val d1 = sign(px, py, a, b); val d2 = sign(px, py, b, c); val d3 = sign(px, py, c, a)
            val neg = d1 < 0 || d2 < 0 || d3 < 0; val pos = d1 > 0 || d2 > 0 || d3 > 0
            if (!(neg && pos)) set(x, y, col)
        }
    }

    fun mirrorX() {
        for (y in 0 until h) for (x in 0 until w / 2) {
            val a = px[y * w + x]; px[y * w + x] = px[y * w + (w - 1 - x)]; px[y * w + (w - 1 - x)] = a
        }
    }

    /** The "HD pixel" pass: top-left highlights, bottom-right shading and a dark tinted outline. */
    fun finish(outline: Boolean = true, light: Float = 0.18f, shade: Float = 0.28f) {
        val src = px.clone()
        fun f(x: Int, y: Int) = x in 0 until w && y in 0 until h && alphaOf(src[y * w + x]) > 0
        fun same(x0: Int, y0: Int, x1: Int, y1: Int) = (src[y0 * w + x0] and 0xffffff) == (src[y1 * w + x1] and 0xffffff)
        val hi = argb(255, 255, 250, 230); val lo = argb(255, 20, 10, 30)
        for (y in 0 until h) for (x in 0 until w) {
            if (!f(x, y)) continue
            val c = src[y * w + x]
            if (alphaOf(c) == 254) continue
            val top = !f(x, y - 1) || !same(x, y, x, y - 1); val left = !f(x - 1, y) || !same(x, y, x - 1, y)
            val bottom = !f(x, y + 1) || !same(x, y, x, y + 1); val right = !f(x + 1, y) || !same(x, y, x + 1, y)
            px[y * w + x] = when {
                top && !bottom -> withAlpha(lerp(c, hi, light), alphaOf(c))
                left && !right -> withAlpha(lerp(c, hi, light * 0.6f), alphaOf(c))
                bottom || right -> withAlpha(lerp(c, lo, shade), alphaOf(c))
                else -> c
            }
        }
        if (outline) {
            val shaded = px.clone()
            val ink = argb(255, 14, 8, 20)
            for (y in 0 until h) for (x in 0 until w) {
                if (f(x, y)) continue
                val n = when {
                    f(x + 1, y) -> src[y * w + x + 1]; f(x - 1, y) -> src[y * w + x - 1]
                    f(x, y + 1) -> src[(y + 1) * w + x]; f(x, y - 1) -> src[(y - 1) * w + x]
                    else -> null
                }
                if (n != null) shaded[y * w + x] = withAlpha(lerp(n, ink, 0.75f), 255)
            }
            shaded.copyInto(px)
        }
        for (k in px.indices) if (alphaOf(px[k]) == 254) px[k] = withAlpha(px[k], 255)
    }

    fun toBitmap(): Bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}
