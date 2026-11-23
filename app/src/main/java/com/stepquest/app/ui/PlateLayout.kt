package com.stepquest.app.ui

/** Integer rectangle (left, top, right, bottom) in pixels. */
data class PRect(val l: Int, val t: Int, val r: Int, val b: Int) {
    val w get() = r - l
    val h get() = b - t
    fun overlaps(o: PRect, gap: Int = 0) = l < o.r + gap && o.l < r + gap && t < o.b + gap && o.t < b + gap
}

/**
 * Lays out labels (enemy nameplates) so none overlap each other or the [obstacles].
 * Each label wants to sit with its bottom-centre at its anchor; front-most anchors (largest y) are
 * placed first. If the ideal spot is taken, candidate spots are tried nearest-first, preferring to
 * move up, then sideways, then down, and the first free spot inside the area wins.
 */
fun placePlates(
    anchors: List<Pair<Float, Float>>, sizes: List<Pair<Int, Int>>, areaWidth: Int,
    obstacles: List<PRect> = emptyList(), gap: Int = 3, minTop: Int = 0, areaHeight: Int = Int.MAX_VALUE,
): List<PRect> {
    val result = arrayOfNulls<PRect>(anchors.size)
    val placed = mutableListOf<PRect>()
    fun free(r: PRect) = (placed + obstacles).none { it.overlaps(r, gap) }
    fun inside(r: PRect) = r.l >= 0 && r.r <= areaWidth && r.t >= minTop && r.b <= areaHeight
    for (i in anchors.indices.sortedByDescending { anchors[it].second }) {
        val (pw, ph) = sizes[i]
        fun at(x: Int, y: Int) = PRect(x, y, x + pw, y + ph)
        val x0 = (anchors[i].first - pw / 2f).toInt().coerceIn(0, (areaWidth - pw).coerceAtLeast(0))
        val y0 = (anchors[i].second - ph).toInt().coerceIn(minTop, (areaHeight - ph).coerceAtLeast(minTop))
        val sx = pw / 4 + gap
        val sy = ph / 2 + gap
        val candidates = buildList {
            for (dy in -12..12) for (dx in -12..12) {
                val cost = (if (dy < 0) -dy * 1.0 else dy * 2.2) * sy + kotlin.math.abs(dx) * 1.4 * sx
                add(Triple(cost, x0 + dx * sx, y0 + dy * sy))
            }
        }.sortedBy { it.first }
        val r = candidates.asSequence().map { (_, x, y) -> at(x, y) }.firstOrNull { inside(it) && free(it) } ?: at(x0, y0)
        placed += r
        result[i] = r
    }
    return result.map { it!! }
}
