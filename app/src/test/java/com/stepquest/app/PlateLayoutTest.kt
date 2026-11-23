package com.stepquest.app

import com.stepquest.app.ui.PRect
import com.stepquest.app.ui.placePlates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateLayoutTest {
    private fun assertNoOverlaps(rs: List<PRect>, obstacles: List<PRect> = emptyList()) {
        for (i in rs.indices) {
            for (j in i + 1 until rs.size) assertFalse("plates $i and $j overlap: ${rs[i]} ${rs[j]}", rs[i].overlaps(rs[j]))
            obstacles.forEach { assertFalse("plate $i overlaps HUD $it", rs[i].overlaps(it)) }
        }
    }

    @Test fun separatedPlatesStayOnTheirAnchors() {
        val rs = placePlates(listOf(100f to 300f, 400f to 300f), listOf(90 to 30, 90 to 30), 1000)
        assertEquals(PRect(55, 270, 145, 300), rs[0])
        assertEquals(PRect(355, 270, 445, 300), rs[1])
    }

    @Test fun stackedEnemiesNeverOverlap() {
        // The real 3-enemy formation: heads close together on the left side.
        val anchors = listOf(330f to 520f, 230f to 470f, 330f to 420f)
        val rs = placePlates(anchors, List(3) { 250 to 80 }, 1080, gap = 8, minTop = 16)
        assertNoOverlaps(rs)
        rs.forEach { assertTrue(it.l >= 0 && it.r <= 1080 && it.t >= 16) }
    }

    @Test fun differentHeightsAreRespected() {
        // A broken enemy's plate is taller (extra "BROKEN" line).
        val rs = placePlates(listOf(300f to 500f, 310f to 480f), listOf(250 to 110, 250 to 80), 1080, gap = 8)
        assertNoOverlaps(rs)
    }

    @Test fun slidesSidewaysWhenThereIsNoRoomAbove() {
        val hud = listOf(PRect(0, 0, 640, 230))
        val anchors = listOf(300f to 360f, 300f to 350f, 300f to 340f)
        val rs = placePlates(anchors, List(3) { 250 to 80 }, 1080, hud, gap = 8, minTop = 16, areaHeight = 1000)
        assertNoOverlaps(rs, hud)
    }

    @Test fun crowdedRandomLayoutsNeverOverlap() {
        val rnd = java.util.Random(42)
        repeat(500) {
            val n = 1 + rnd.nextInt(3)
            val anchors = List(n) { (150f + rnd.nextFloat() * 300f) to (300f + rnd.nextFloat() * 300f) }
            val sizes = List(n) { 250 to (70 + rnd.nextInt(40)) }
            val hud = listOf(PRect(0, 0, 640, 230), PRect(600, 0, 1080, 170))
            assertNoOverlaps(placePlates(anchors, sizes, 1080, hud, gap = 8, minTop = 16, areaHeight = 1000), hud)
        }
    }
}
