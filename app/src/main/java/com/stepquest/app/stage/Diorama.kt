package com.stepquest.app.stage

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stepquest.app.art.PixelArt
import com.stepquest.app.art.PixelCanvas
import com.stepquest.app.art.RegionPalette
import com.stepquest.app.art.lerp
import com.stepquest.core.combat.Battle
import com.stepquest.core.combat.BattleEvent
import com.stepquest.core.combat.BattleEventKind
import com.stepquest.core.party.Element
import com.stepquest.core.party.Hero
import com.stepquest.core.world.RegionDef
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One thing standing on stage: a hero, enemy or idle party member. */
class Actor(val id: Int, val sprite: Bitmap, val x: Float, val z: Float, val facingLeft: Boolean, val phase: Float = Random.nextFloat() * 6f)

class Floater(val key: Long, val unitId: Int, val text: String, val color: Color, val big: Boolean)

/** Mutable animation state, driven by [play]. */
class StageState {
    var actors by mutableStateOf2(listOf<Actor>())
    val lunge = mutableStateMapOf<Int, Float>()
    val flash = mutableStateMapOf<Int, Pair<Float, Color>>()
    val shake = mutableStateMapOf<Int, Float>()
    val fade = mutableStateMapOf<Int, Float>()
    val fallen = mutableStateMapOf<Int, Boolean>()
    val floaters = mutableStateListOf<Floater>()
    val bursts = mutableStateListOf<Burst>()
    var highlight by mutableIntStateOf(-1)
    private var key = 0L

    class Burst(val key: Long, val unitId: Int, val color: Color, val t0: Long = System.nanoTime())

    fun showParty(party: List<Hero>) {
        reset()
        actors = party.mapIndexed { i, h -> Actor(i, PixelArt.hero(h.heroClass), 0.46f + 0.11f * i, 0.8f - 0.15f * i, true) }
        party.forEachIndexed { i, h -> fallen[i] = h.hp <= 0 }
    }

    fun showBattle(b: Battle) {
        reset()
        val list = mutableListOf<Actor>()
        b.heroes.forEachIndexed { i, u ->
            list += Actor(u.id, PixelArt.hero(b.heroClasses[u.heroIndex]), 0.64f + 0.065f * i, 0.82f - 0.16f * i, true)
            fallen[u.id] = !u.isAlive
        }
        val enemies = b.enemies
        enemies.forEachIndexed { i, u ->
            val (x, z) = when {
                b.isBoss -> 0.25f to 0.45f
                enemies.size == 1 -> 0.27f to 0.5f
                else -> (0.3f - 0.09f * (i % 2)) to (0.78f - 0.27f * i)
            }
            list += Actor(u.id, PixelArt.enemy(u.enemyId!!), x, z, false)
            if (!u.isAlive) fade[u.id] = 0f
        }
        actors = list
    }

    private fun reset() { lunge.clear(); flash.clear(); shake.clear(); fade.clear(); fallen.clear(); floaters.clear(); bursts.clear(); highlight = -1 }

    fun float(unitId: Int, text: String, color: Color, big: Boolean = false) {
        val f = Floater(key++, unitId, text, color, big)
        floaters += f
    }

    /** Animates battle events one by one; [onEvent] fires as each lands (for HP bars / log). */
    suspend fun play(b: Battle, events: List<BattleEvent>, castElement: Element, onEvent: (BattleEvent) -> Unit) = coroutineScope {
        var lastSource = -1
        for (e in events) {
            val src = e.sourceId; val dst = e.targetId
            if (src >= 0 && src != lastSource && (e.kind == BattleEventKind.Damage || e.kind == BattleEventKind.Heal || e.kind == BattleEventKind.Info)) {
                lastSource = src
                val su = b.unit(src); val tu = b.unit(dst)
                if (e.kind == BattleEventKind.Damage && su != null && tu != null && su.isHero != tu.isHero) {
                    animate(0f, 1f, animationSpec = tween(140)) { v, _ -> lunge[src] = v }
                    animate(1f, 0f, animationSpec = tween(160)) { v, _ -> lunge[src] = v }
                } else {
                    animate(0f, 1f, animationSpec = tween(260)) { v, _ -> lunge[src] = -sin(v * PI.toFloat()) * 0.35f }
                }
            }
            onEvent(e)
            when (e.kind) {
                BattleEventKind.Damage -> {
                    val col = if (e.weak) elementColor(castElement) else Color.White
                    bursts += Burst(key++, dst, col)
                    float(dst, e.amount.toString(), if (e.crit) Color(0xFFFFD86B) else if (e.weak) Color(0xFFFFB36B) else Color.White, e.crit)
                    launch { animate(1f, 0f, animationSpec = tween(220)) { v, _ -> flash[dst] = v to Color.White } }
                    animate(1f, 0f, animationSpec = tween(260)) { v, _ -> shake[dst] = v * (if (e.crit) 1.6f else 1f) }
                }
                BattleEventKind.Heal, BattleEventKind.Revive -> {
                    if (e.kind == BattleEventKind.Revive) fallen[dst] = false
                    if (dst >= 0) {
                        float(dst, if (e.kind == BattleEventKind.Revive) "Revived!" else "+${e.amount}", Color(0xFF7BE08A))
                        bursts += Burst(key++, dst, Color(0xFF7BE08A))
                        animate(1f, 0f, animationSpec = tween(320)) { v, _ -> flash[dst] = v to Color(0xFF66FF88) }
                    }
                }
                BattleEventKind.Break -> {
                    float(dst, "BREAK!", Color(0xFFFFD86B), true)
                    bursts += Burst(key++, dst, Color(0xFFFFD86B))
                    animate(1f, 0f, animationSpec = tween(450)) { v, _ -> shake[dst] = v * 2.4f }
                }
                BattleEventKind.Defeated -> {
                    if (b.unit(dst)?.isHero == true) fallen[dst] = true
                    else animate(1f, 0f, animationSpec = tween(550)) { v, _ -> fade[dst] = v }
                }
                BattleEventKind.Coins -> float(dst, "+${e.amount}c", Color(0xFFFFD86B))
                BattleEventKind.Recover -> float(dst, "Recovered", Color(0xFFB9C8FF))
                BattleEventKind.Status -> if (dst >= 0) animate(1f, 0f, animationSpec = tween(260)) { v, _ -> flash[dst] = v to Color(0xFF99AAFF) }
                else -> delay(140)
            }
            delay(70)
        }
    }

    companion object {
        fun elementColor(e: Element) = when (e) {
            Element.Fire -> Color(0xFFFF7326); Element.Ice -> Color(0xFF8CE6FF); Element.Lightning -> Color(0xFFFFF25A)
            Element.Nature -> Color(0xFF80FF5A); Element.Holy -> Color(0xFFFFF2C0); Element.Dark -> Color(0xFFC05AFF)
            Element.Physical -> Color.White
        }
    }
}

private fun <T> mutableStateOf2(v: T) = androidx.compose.runtime.mutableStateOf(v)

/** Precomputed textures for a region's set. */
private class SetDressing(region: RegionDef) {
    val pal = RegionPalette.of(region.palette)
    val far = silhouette(pal.far, 7, region.index * 31 + 1)
    val mid = silhouette(pal.mid, 13, region.index * 31 + 2)
    val props: List<Triple<Float, Float, ImageBitmap>>
    init {
        val rnd = Random(region.index * 977 + 13)
        props = List(16) {
            val kind = pal.props[rnd.nextInt(pal.props.size)]
            var x = rnd.nextFloat() * 1.3f - 0.15f
            val z = rnd.nextFloat()
            if (z < 0.85f && x in 0.15f..0.85f) x = if (x < 0.5f) x - 0.3f else x + 0.3f // keep the arena clear
            Triple(x, z, PixelArt.prop(kind, region.palette).asImageBitmap())
        }.sortedByDescending { it.second }
    }

    companion object {
        fun silhouette(color: Int, bumps: Int, seed: Int): ImageBitmap {
            val w = 160; val h = 30
            val p = PixelCanvas(w, h)
            val rnd = Random(seed)
            val hs = FloatArray(bumps + 2) { 0.35f + rnd.nextFloat() * 0.6f }
            for (x in 0 until w) {
                val fx = x / w.toFloat() * (bumps + 1); val i0 = fx.toInt(); val f = fx - i0
                val hh = hs[i0] + (hs[i0 + 1] - hs[i0]) * (f * f * (3 - 2 * f))
                val top = ((1 - hh) * h).toInt()
                for (y in top until h) p.set(x, y, if (y < top + 2) lerp(color, com.stepquest.app.art.WHITE, 0.12f) else color)
            }
            return p.toBitmap().asImageBitmap()
        }
    }
}

private const val HORIZON = 0.40f
private fun groundY(z: Float, h: Float) = h * (HORIZON + 0.08f + (1f - z) * 0.47f)   // z: 0 = front, 1 = back
private fun depthScale(z: Float) = 1f - z * 0.38f

/**
 * The HD-2D style diorama, drawn in layers: a blurred far set (tilt-shift), a sharp mid-ground with
 * the actors, blurred foreground props, then additive torch light, fireflies and a vignette.
 */
@Composable
fun Diorama(region: RegionDef, state: StageState, modifier: Modifier = Modifier, onTapUnit: ((Int) -> Unit)? = null) {
    val set = remember(region.id) { SetDressing(region) }
    val time by rememberInfiniteTransition(label = "t").animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing)), label = "time")
    val images = remember(state.actors) { state.actors.associate { it.id to it.sprite.asImageBitmap() } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val unitPx = with(androidx.compose.ui.platform.LocalDensity.current) { (maxWidth / 12f).toPx() } // ~16 sprite pixels
        // 1) Far set: sky, mountains, far props — blurred like a tilt-shift lens.
        Canvas(Modifier.fillMaxSize().blur(3.dp)) { drawFar(set) }
        // 2) Ground + actors, sharp.
        Canvas(Modifier.fillMaxSize().pointerInput(state.actors, onTapUnit) {
            if (onTapUnit != null) detectTapGestures { pos ->
                hit(state, images, pos, size.width.toFloat(), size.height.toFloat(), unitPx)?.let(onTapUnit)
            }
        }) {
            drawGround(set)
            set.props.filter { it.second in 0.55f..0.98f }.forEach { (x, z, img) -> drawSprite(img, x * size.width, groundY(z, size.height), unitPx * depthScale(z) * 0.9f) }
            val t = time
            state.actors.sortedByDescending { it.z }.forEach { a -> drawActor(a, images.getValue(a.id), state, t, unitPx) }
            drawBursts(state, unitPx)
        }
        // 3) Foreground framing props, blurred.
        Canvas(Modifier.fillMaxSize().blur(4.dp)) {
            set.props.filter { it.second < 0.08f }.take(3).forEach { (x, _, img) ->
                drawSprite(img, (if (x < 0.5f) -0.02f else 1.02f) * size.width, size.height * 1.03f, unitPx * 1.5f)
            }
        }
        // 4) Light, particles, vignette.
        Canvas(Modifier.fillMaxSize()) { drawAtmosphere(set.pal, time) }
    }
}

private fun DrawScope.drawFar(set: SetDressing) {
    val w = size.width; val h = size.height
    val hz = h * (HORIZON + 0.08f)
    for (i in 0 until 10) { // banded sky
        val c = Color(lerp(set.pal.skyTop, set.pal.skyBottom, i / 9f))
        drawRect(c, Offset(0f, hz * i / 10f), Size(w, hz / 10f + 1))
    }
    drawImage(set.far, dstOffset = IntOffset(0, (hz - h * 0.2f).toInt()), dstSize = IntSize(w.toInt(), (h * 0.2f).toInt()), filterQuality = FilterQuality.None)
    drawImage(set.mid, dstOffset = IntOffset((-w * 0.05f).toInt(), (hz - h * 0.11f).toInt()), dstSize = IntSize((w * 1.1f).toInt(), (h * 0.12f).toInt()), filterQuality = FilterQuality.None)
    drawRect(Color(set.pal.fog).copy(alpha = 0.25f), Offset(0f, hz - h * 0.08f), Size(w, h * 0.12f))
    set.props.filter { it.second > 0.98f }.forEach { (x, z, img) -> drawSprite(img, x * w, groundY(z, h), w / 12f * depthScale(z) * 0.8f) }
}

private fun DrawScope.drawGround(set: SetDressing) {
    val w = size.width; val h = size.height
    val top = h * (HORIZON + 0.08f)
    // Perspective bands: thin near the horizon, wide near the camera.
    var y = top; var band = 0
    while (y < h) {
        val t = (y - top) / (h - top)
        val bh = 2f + t * t * 26f
        val base = if (band % 2 == 0) set.pal.ground else set.pal.groundAlt
        val c = lerp(lerp(base, set.pal.fog, (1 - t) * 0.45f), set.pal.ground, 0.35f)
        drawRect(Color(c), Offset(0f, y), Size(w, bh + 1))
        y += bh; band++
    }
    // Speckles for texture.
    val rnd = Random(7)
    repeat(140) {
        val t = rnd.nextFloat()
        val yy = top + (h - top) * t * t
        val s = 2f + t * 5f
        drawRect(Color(lerp(set.pal.groundAlt, com.stepquest.app.art.BLACK, 0.18f)), Offset(rnd.nextFloat() * w, yy), Size(s, s * 0.6f))
    }
}

private fun DrawScope.drawSprite(img: ImageBitmap, cx: Float, footY: Float, unitPx: Float, alpha: Float = 1f, tint: Color? = null, scaleY: Float = 1f, flip: Boolean = false) {
    val sw = img.width / 16f * unitPx; val sh = img.height / 16f * unitPx * scaleY
    withTransform({ if (flip) scale(-1f, 1f, Offset(cx, footY)) }) {
        drawImage(img, dstOffset = IntOffset((cx - sw / 2).toInt(), (footY - sh).toInt()), dstSize = IntSize(sw.toInt(), sh.toInt()),
            alpha = alpha, filterQuality = FilterQuality.None,
            colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.SrcAtop) })
    }
}

private fun actorPos(a: Actor, state: StageState, w: Float, h: Float): Offset {
    val lunge = state.lunge[a.id] ?: 0f
    val dir = if (a.facingLeft) -1f else 1f
    val shake = (state.shake[a.id] ?: 0f) * sin(System.nanoTime() / 1e7f) * 0.012f * w
    return Offset(a.x * w + dir * lunge * w * 0.12f + shake, groundY(a.z, h) - (if (lunge < 0) -lunge * h * 0.08f else 0f))
}

private fun DrawScope.drawActor(a: Actor, img: ImageBitmap, state: StageState, t: Float, unitPx: Float) {
    val alpha = state.fade[a.id] ?: 1f
    if (alpha <= 0.01f) return
    val p = actorPos(a, state, size.width, size.height)
    val s = unitPx * depthScale(a.z)
    val sw = img.width / 16f * s
    // Contact shadow.
    drawOval(Color.Black.copy(alpha = 0.35f * alpha), Offset(p.x - sw * 0.38f, p.y - s * 0.18f), Size(sw * 0.76f, s * 0.36f))
    if (state.highlight == a.id) drawOval(Color(0x88FFD86B), Offset(p.x - sw * 0.45f, p.y - s * 0.22f), Size(sw * 0.9f, s * 0.44f))
    val fallen = state.fallen[a.id] == true
    val breathe = if (fallen) 1f else 1f + sin(t * 2.2f + a.phase) * 0.035f
    val (flash, flashColor) = state.flash[a.id] ?: (0f to Color.White)
    if (fallen) {
        rotate(if (a.facingLeft) 80f else -80f, Offset(p.x, p.y)) { drawSprite(img, p.x, p.y, s, alpha * 0.8f, Color(0x55302040)) }
    } else {
        drawSprite(img, p.x, p.y, s, alpha, if (alpha < 1f) Color.Red.copy(alpha = (1 - alpha) * 0.6f) else null, breathe)
        if (flash > 0.01f) drawSprite(img, p.x, p.y, s, flash * 0.85f, flashColor, breathe)
    }
}

private fun DrawScope.drawBursts(state: StageState, unitPx: Float) {
    val now = System.nanoTime()
    state.bursts.toList().forEach { b ->
        val age = (now - b.t0) / 1e9f
        if (age > 0.6f) { state.bursts.remove(b); return@forEach }
        val a = state.actors.firstOrNull { it.id == b.unitId } ?: return@forEach
        val p = actorPos(a, state, size.width, size.height)
        val cy = p.y - unitPx * 1.1f * depthScale(a.z)
        val rnd = Random(b.key)
        repeat(14) {
            val ang = rnd.nextFloat() * 2 * PI.toFloat(); val sp = (0.6f + rnd.nextFloat()) * unitPx * 2.2f
            val o = Offset(p.x + cos(ang) * sp * age, cy + sin(ang) * sp * age + age * age * unitPx * 3)
            drawCircle(b.color.copy(alpha = (1 - age / 0.6f)), unitPx * 0.12f, o, blendMode = BlendMode.Plus)
        }
    }
}

private fun DrawScope.drawAtmosphere(pal: RegionPalette, t: Float) {
    val w = size.width; val h = size.height
    // Warm torch glow by the party, cool key light from the top-left.
    drawCircle(Brush.radialGradient(listOf(Color(0x55FFB86A), Color.Transparent), Offset(w * 0.78f, h * 0.62f), w * 0.45f),
        w * 0.45f, Offset(w * 0.78f, h * 0.62f), blendMode = BlendMode.Plus, alpha = 0.7f + 0.3f * sin(t * 7f) * sin(t * 3.1f))
    drawRect(Brush.linearGradient(listOf(Color(pal.light).copy(alpha = 0.18f), Color.Transparent), Offset.Zero, Offset(w * 0.6f, h * 0.6f)), blendMode = BlendMode.Plus)
    // Fireflies / dust motes.
    val rnd = Random(3)
    repeat(if (pal.fireflies) 22 else 12) {
        val bx = rnd.nextFloat(); val by = 0.25f + rnd.nextFloat() * 0.6f; val sp = 0.05f + rnd.nextFloat() * 0.1f; val ph = rnd.nextFloat() * 6f
        val x = ((bx + sin(t * sp + ph) * 0.05f) % 1f) * w
        val y = (by + cos(t * sp * 1.3f + ph) * 0.03f) * h
        val a = 0.4f + 0.6f * ((sin(t * 2f + ph) + 1f) / 2f)
        val c = if (pal.fireflies) Color(0xFFFFF27A) else Color.White
        drawCircle(c.copy(alpha = a * 0.35f), w * 0.009f, Offset(x, y), blendMode = BlendMode.Plus)
        drawCircle(c.copy(alpha = a), w * 0.0035f, Offset(x, y), blendMode = BlendMode.Plus)
    }
    // Vignette.
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xAA000000)), Offset(w / 2, h * 0.5f), maxOf(w, h) * 0.75f))
}

private fun hit(state: StageState, images: Map<Int, ImageBitmap>, pos: Offset, w: Float, h: Float, unitPx: Float): Int? =
    state.actors.sortedBy { it.z }.firstOrNull { a ->
        if ((state.fade[a.id] ?: 1f) < 0.1f) return@firstOrNull false
        val img = images[a.id] ?: return@firstOrNull false
        val p = actorPos(a, state, w, h)
        val s = unitPx * depthScale(a.z)
        val sw = img.width / 16f * s; val sh = img.height / 16f * s
        pos.x in (p.x - sw / 2)..(p.x + sw / 2) && pos.y in (p.y - sh)..p.y
    }?.id

/** Screen position (fraction of stage size) above a unit's head, for floaters and tags. */
fun StageState.anchor(id: Int, stageW: Float, stageH: Float, unitPx: Float): Offset? {
    val a = actors.firstOrNull { it.id == id } ?: return null
    val p = actorPos(a, this, stageW, stageH)
    return Offset(p.x, p.y - a.sprite.height / 16f * unitPx * depthScale(a.z))
}
