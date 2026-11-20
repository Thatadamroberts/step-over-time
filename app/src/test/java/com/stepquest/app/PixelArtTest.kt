package com.stepquest.app

import com.stepquest.app.art.PixelArt
import com.stepquest.app.art.PixelCanvas
import com.stepquest.app.art.alphaOf
import com.stepquest.core.data.GameData
import com.stepquest.core.party.HeroClass
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every sprite draws without errors and isn't blank. Also writes build/sprite-preview.png. */
class PixelArtTest {
    @Test fun allSpritesDraw() {
        val canvases = mutableListOf<PixelCanvas>()
        HeroClass.entries.forEach { canvases += PixelArt.drawHero(it) }
        GameData.enemies.keys.forEach { canvases += PixelArt.drawEnemy(it) }
        listOf("tree", "rock", "grass", "pillar", "cactus", "crystal").forEach { canvases += PixelArt.drawProp(it, "forest") }
        listOf("sword", "map", "helm", "bag", "scroll", "trophy", "star", "star_empty", "gear", "flame", "fire", "ice", "bolt", "leaf",
            "holy", "dark", "phys", "skull", "lock", "check", "coin", "boot", "home", "heart").forEach { canvases += PixelArt.drawIcon(it) }
        canvases.forEach { c -> assertTrue("blank sprite ${c.w}x${c.h}", c.px.count { alphaOf(it) > 0 } > 10) }

        // Raw dump (w, h, ARGB pixels per sprite) for an external preview script.
        File("build").mkdirs()
        java.io.DataOutputStream(File("build/sprites.raw").outputStream().buffered()).use { out ->
            out.writeInt(canvases.size)
            canvases.forEach { c -> out.writeInt(c.w); out.writeInt(c.h); c.px.forEach { out.writeInt(it) } }
        }
    }
}
