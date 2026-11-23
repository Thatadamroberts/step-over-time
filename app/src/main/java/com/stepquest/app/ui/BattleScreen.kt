package com.stepquest.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stepquest.core.party.SkillEffect
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepquest.app.Game
import com.stepquest.app.art.PixelArt
import com.stepquest.app.stage.Diorama
import com.stepquest.app.stage.anchor
import com.stepquest.core.combat.Battle
import com.stepquest.core.combat.BattleCommand
import com.stepquest.core.combat.BattleEventKind
import com.stepquest.core.combat.BattleOutcome
import com.stepquest.core.combat.CommandKind
import com.stepquest.core.combat.Combatant
import com.stepquest.core.data.GameData
import com.stepquest.core.economy.ConsumableEffect
import com.stepquest.core.party.Element
import com.stepquest.core.party.SkillDef
import com.stepquest.core.party.TargetKind
import com.stepquest.core.session.BattleSummary
import com.stepquest.core.steps.StepLedger
import kotlinx.coroutines.launch

private enum class Mode { Root, Skills, Items, Target }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleScreen(nav: Nav) {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.Root) }
    var pending by remember { mutableStateOf<BattleCommand?>(null) }
    var pendingTarget by remember { mutableStateOf(TargetKind.SingleEnemy) }
    var log by remember { mutableStateOf("") }
    val shownHp = remember { mutableStateMapOf<Int, Int>() }
    val shownMp = remember { mutableStateMapOf<Int, Int>() }
    var summaryShown by remember { mutableStateOf<BattleSummary?>(null) }
    val stage = nav.stage

    // The session clears the battle the moment the last blow lands, so keep the battle being
    // animated on screen until its playback finishes; only then show the results.
    var playing by remember { mutableStateOf<Battle?>(null) }
    val b = s.battle ?: playing
    LaunchedEffect(b == null) { if (b != null) stage.showBattle(b) }
    LaunchedEffect(s.battle, s.lastSummary, busy) {
        if (busy) return@LaunchedEffect
        val sum = s.lastSummary
        if (s.battle == null) {
            if (sum != null && summaryShown !== sum) { summaryShown = sum; showResults(nav, sum) }
            else if (sum == null && nav.dialog == null) nav.screen = Screen.Home
        }
    }
    if (b == null) { Box(Modifier.fillMaxSize()); return }
    val region = GameData.region(b.regionId) ?: s.currentRegion
    if (!busy) stage.highlight = b.currentActor?.id ?: -1

    fun execute(cmd: BattleCommand) {
        if (busy) return
        s.validateCommand(cmd)?.let { Game.toast(it); return }
        val element = if (cmd.kind == CommandKind.Skill) GameData.skill(cmd.skillId!!).element
            else GameData.classDef(b.heroClasses[b.currentActor!!.heroIndex]).weaponElement
        b.units.forEach { shownHp[it.id] = it.hp; shownMp[it.id] = it.mp }
        playing = b
        val events = runCatching { s.command(cmd) }.getOrElse { Game.toast(it.message ?: "Can't do that"); shownHp.clear(); shownMp.clear(); return }
        mode = Mode.Root; pending = null; busy = true
        stage.highlight = -1
        scope.launch {
            stage.play(b, events, element) { e ->
                if (e.text.isNotEmpty()) log = e.text
                when (e.kind) {
                    BattleEventKind.Damage -> shownHp[e.targetId] = maxOf(0, (shownHp[e.targetId] ?: 0) - e.amount)
                    BattleEventKind.Heal -> if (e.targetId >= 0) {
                        if (e.text.contains(" MP")) shownMp[e.targetId] = (shownMp[e.targetId] ?: 0) + e.amount
                        else shownHp[e.targetId] = (shownHp[e.targetId] ?: 0) + e.amount
                    }
                    BattleEventKind.Revive -> shownHp[e.targetId] = e.amount
                    BattleEventKind.Info -> if (e.sourceId >= 0) b.unit(e.sourceId)?.let { shownMp[it.id] = it.mp }
                    else -> {}
                }
            }
            if (s.battle == null) kotlinx.coroutines.delay(700) // let the finishing blow sink in
            shownHp.clear(); shownMp.clear()
            playing = null
            busy = false
            Game.refresh()
        }
    }

    fun beginTarget(cmd: BattleCommand, kind: TargetKind) {
        val targets = validTargets(b, kind)
        if (targets.isEmpty()) { Game.toast("No valid targets"); return }
        if (targets.size == 1 && kind == TargetKind.SingleEnemy) { execute(cmd.copy(targetId = targets[0].id)); return }
        pending = cmd; pendingTarget = kind; mode = Mode.Target
    }

    // MMO-style targeting: tap an enemy to target it; offensive abilities go to the current target.
    var targetId by remember { mutableStateOf(-1) }
    if (b.unit(targetId)?.let { !it.isHero && it.isAlive } != true) targetId = b.enemies.firstOrNull { it.isAlive }?.id ?: -1

    fun use(cmd: BattleCommand, kind: TargetKind) = when (kind) {
        TargetKind.SingleEnemy -> if (targetId >= 0) execute(cmd.copy(targetId = targetId)) else Game.toast("No target")
        TargetKind.SingleAlly, TargetKind.DeadAlly -> beginTarget(cmd, kind)
        else -> execute(cmd)
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(0.44f).drawWithContent { drawContent(); ironFrame(3.5.dp.toPx(), 0f, rivets = true) }) {
            val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
            val unitPx = w / 12f
            Diorama(region, stage, onTapUnit = { id ->
                if (busy) return@Diorama
                val unit = b.unit(id) ?: return@Diorama
                if (mode == Mode.Target && validTargets(b, pendingTarget).any { it.id == id }) pending?.let { execute(it.copy(targetId = id)) }
                else if (!unit.isHero && unit.isAlive) targetId = id
            })
            // Nameplates over enemies, laid out so they never overlap each other or the HUD.
            val plates = b.enemies.filter { (it.isAlive || busy) && (stage.fade[it.id] ?: 1f) >= 0.05f }
                .mapNotNull { e -> stage.anchor(e.id, w, h, unitPx)?.let { e to it } }
            NameplateLayer(plates.map { it.second }) {
                plates.forEach { (e, _) -> Nameplate(e, shownHp[e.id] ?: e.hp, e.id == targetId, Modifier) }
            }
            for (f in stage.floaters.toList()) {
                val anchor = stage.anchor(f.unitId, w, h, unitPx) ?: continue
                androidx.compose.runtime.key(f.key) { FloatText(f.text, f.color, f.big, anchor.x, anchor.y + unitPx) { stage.floaters.remove(f) } }
            }
            // Target frame (top-left) and zone text (top-right).
            b.unit(targetId)?.let { TargetFrame(b, it, shownHp[it.id] ?: it.hp, Modifier.align(Alignment.TopStart).padding(10.dp)) }
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp).background(Color(0x99080503), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.End) {
                T(if (b.isBoss) "Boss Encounter" else region.name, 14.sp, if (b.isBoss) Color(0xFFFF7A4A) else Sq.gold, bold = true)
                Tiny("Round ${b.round}", Sq.text)
            }
        }

        Column(Modifier.weight(0.56f).vscroll().padding(horizontal = 10.dp, vertical = 6.dp)) {
            TurnStrip(b)
            // Party unit frames, two per row.
            b.heroes.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { u -> UnitFrame(b, u, shownHp[u.id] ?: u.hp, shownMp[u.id] ?: u.mp, !busy && b.currentActor == u,
                        selectable = mode == Mode.Target && validTargets(b, pendingTarget).any { it.id == u.id },
                        modifier = Modifier.weight(1f)) { pending?.let { execute(it.copy(targetId = u.id)) } } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            // While animating, only show lines that have actually played (the session already knows the outcome).
            val logLine = log.ifEmpty { if (busy) "" else b.log.lastOrNull()?.text ?: "" }
            if (logLine.isNotEmpty())
                T(logLine, 12.sp, Color(0xFFFFD100), modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))

            val actor = b.currentActor
            if (s.ledger.bankedActions <= 0 && !busy) {
                Panel(glow = true) {
                    H2("Out of actions")
                    Small("Your party holds its ground. Every 1,000 steps you walk gives them another action — the battle will wait for you.", Sq.text)
                    if (!s.todayUnlocked) Small("Today's actions unlock once you reach 80% of your goal: ${n(s.stepsToUnlock)} more steps" +
                        (if (s.lockedActions > 0) " (${s.lockedActions} waiting)." else "."), Sq.gold)
                    Spacer(Modifier.height(6.dp))
                    Btn("Return to town", Modifier.fillMaxWidth()) { nav.screen = Screen.Home }
                    if (s.data.settings.debugTools) Btn("+1k steps (debug)", small = true, ghost = true) { s.addDebugSteps(1000) }
                }
            } else if (actor != null && actor.isHero && !busy) {
                val hero = s.party[actor.heroIndex]
                when (mode) {
                    Mode.Target -> Panel(inset = true) {
                        H3(if (pendingTarget == TargetKind.DeadAlly) "Choose a fallen ally" else "Choose an ally", Sq.gold)
                        Small("Tap a party frame above.")
                        Btn("Cancel", small = true, ghost = true) { mode = if (pending?.kind == CommandKind.Item) Mode.Items else Mode.Root; pending = null }
                    }
                    Mode.Items -> ActionBar("Bags — ${actor.name}") {
                        s.inventory.items.toSortedMap().forEach { (id, count) ->
                            val item = GameData.itemOrNull(id) ?: return@forEach
                            if (!item.isConsumable) return@forEach
                            val icon = when (item.use) { ConsumableEffect.HealHp -> "heart"; ConsumableEffect.HealMp -> "ice"; ConsumableEffect.Revive -> "holy"; else -> "leaf" }
                            ActionSlot(icon, item.name, count.toString()) { use(BattleCommand.item(id, -1), if (item.use == ConsumableEffect.Revive) TargetKind.DeadAlly else TargetKind.SingleAlly) }
                        }
                        ActionSlot("home", "Back", null) { mode = Mode.Root }
                    }
                    else -> ActionBar("${actor.name}'s abilities") {
                        ActionSlot("sword", "Attack", null, primary = true) { use(BattleCommand.attack(-1), TargetKind.SingleEnemy) }
                        for (sk in hero.skills())
                            ActionSlot(skillIcon(sk), sk.name, sk.mpCost.toString(), enabled = actor.mp >= sk.mpCost, costIsMana = true) { use(BattleCommand.skill(sk.id), sk.target) }
                        ActionSlot("helm", "Defend", null) { execute(BattleCommand.defend()) }
                        ActionSlot("bag", "Items", null) { mode = Mode.Items }
                        ActionSlot("boot", "Flee", null, enabled = !b.isBoss) { execute(BattleCommand.flee()) }
                    }
                }
                Tiny("Tap an enemy to target it. Hitting a weakness (?) cracks its shield; at zero it's BROKEN and takes double damage.",
                    modifier = Modifier.padding(4.dp))
            }
        }
    }
}

private fun skillIcon(sk: SkillDef) = when (sk.effect) {
    SkillEffect.Heal, SkillEffect.Regen -> "heart"
    SkillEffect.Revive -> "holy"
    SkillEffect.BuffDef, SkillEffect.Taunt -> "helm"
    SkillEffect.BuffAtk -> "flame"
    SkillEffect.StealCoins -> "coin"
    SkillEffect.Poison -> if (sk.element == Element.Nature) "leaf" else "dark"
    SkillEffect.Damage -> PixelArt.elementIcon(sk.element)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionBar(title: String, content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).mmoFrame(Interior.Slate, band = 2.5.dp, seed = 8).padding(8.dp)) {
        T(title, 12.sp, Sq.gold, bold = true, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        FlowRow(Modifier.fillMaxWidth()) { content() }
    }

/** A square ability slot: icon, name underneath, cost in the corner. */
@Composable
private fun ActionSlot(icon: String, name: String, corner: String?, enabled: Boolean = true, primary: Boolean = false, costIsMana: Boolean = false, onClick: () -> Unit) =
    Column(Modifier.width(58.dp).padding(2.dp).clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(50.dp).mmoFrame(if (primary) Interior.Crimson else Interior.Bronze, band = 2.dp, radius = 3.dp, rivets = false)
            .drawWithContent { drawContent(); if (!enabled) drawRect(Color.Black.copy(alpha = 0.6f)) }, contentAlignment = Alignment.Center) {
            Icon(icon, 32.dp)
            if (corner != null) T(corner, 11.sp, if (costIsMana) Color(0xFF8FB8FF) else Color.White, bold = true, serif = false,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 1.dp))
        }
        T(name, 10.sp, if (enabled) Sq.text else Sq.muted, maxLines = 1, align = TextAlign.Center)
    }

private fun chooseSkill(sk: SkillDef, execute: (BattleCommand) -> Unit, beginTarget: (BattleCommand, TargetKind) -> Unit) {
    val cmd = BattleCommand.skill(sk.id)
    if (sk.target == TargetKind.SingleEnemy || sk.target == TargetKind.SingleAlly || sk.target == TargetKind.DeadAlly) beginTarget(cmd, sk.target) else execute(cmd)
}

private fun validTargets(b: Battle, k: TargetKind): List<Combatant> = when (k) {
    TargetKind.SingleEnemy -> b.enemies.filter { it.isAlive }
    TargetKind.SingleAlly -> b.heroes.filter { it.isAlive }
    TargetKind.DeadAlly -> b.heroes.filter { !it.isAlive }
    else -> emptyList()
}

/** Places a composable so its bottom-centre sits at (x, y) in the parent's pixel space. */
private fun Modifier.centeredAt(x: Float, y: Float) = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(c.maxWidth, c.maxHeight) { p.place((x - p.width / 2).toInt(), (y - p.height).toInt()) }
}

/**
 * Places each nameplate centred above its enemy's head, front-most enemies first. A plate that
 * would collide with one already placed (or with the target frame / zone name) moves up past it;
 * if there's no room above, it slides sideways to the nearest free spot.
 */
@Composable
private fun NameplateLayer(anchors: List<androidx.compose.ui.geometry.Offset>, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, Modifier.fillMaxSize()) { measurables, c ->
        val ps = measurables.map { it.measure(androidx.compose.ui.unit.Constraints()) }
        val obstacles = listOf( // HUD over the diorama: target frame (top-left), zone name (top-right)
            PRect(0, 0, 240.dp.roundToPx(), 86.dp.roundToPx()),
            PRect(c.maxWidth - 180.dp.roundToPx(), 0, c.maxWidth, 64.dp.roundToPx()),
        )
        val result = placePlates(anchors.map { it.x to it.y }, ps.map { it.width to it.height }, c.maxWidth,
            obstacles, gap = 6.dp.roundToPx(), minTop = 6.dp.roundToPx(), areaHeight = c.maxHeight - 6.dp.roundToPx())
        layout(c.maxWidth, c.maxHeight) { ps.forEachIndexed { i, p -> p.place(result[i].l, result[i].t) } }
    }
}

@Composable
private fun Nameplate(e: Combatant, hp: Int, targeted: Boolean, modifier: Modifier) =
    Column(modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T(e.name, 10.sp, if (targeted) Color(0xFFFFD100) else Color(0xFFFF6A5A), bold = true, maxLines = 1)
        Bar(hp / e.stats.maxHp.toFloat(), Color(0xFFD4302A), 7.dp)
        if (e.broken) T("BROKEN", 9.sp, Color(0xFFFFD86B), bold = true)
    }

/** MMO target frame: portrait ring, name, labelled HP, shield pips and weakness icons. */
@Composable
private fun TargetFrame(b: Battle, e: Combatant, hp: Int, modifier: Modifier) =
    Row(modifier.width(220.dp).mmoFrame(Interior.Leather, band = 2.dp, radius = 5.dp, rivets = false).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Portrait(PixelArt.enemy(e.enemyId!!), 42.dp, if (GameData.enemy(e.enemyId!!).isBoss) Color(0xFFFF7A4A) else Mmo.gold)
        Column(Modifier.padding(start = 6.dp)) {
            T(e.name, 12.sp, Color(0xFFFFD100), bold = true, maxLines = 1)
            Bar(hp / e.stats.maxHp.toFloat(), Color(0xFFD4302A), 12.dp, label = "$hp / ${e.stats.maxHp}")
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e.broken) T("BROKEN ", 10.sp, Color(0xFFFFD86B), bold = true)
                else repeat(e.maxShield) { i -> Box(Modifier.padding(end = 1.dp).size(7.dp, 10.dp).background(if (i < e.shield) Color(0xFF7FA8FF) else Color(0x33FFFFFF))) }
                Spacer(Modifier.width(6.dp))
                for (w in GameData.enemy(e.enemyId!!).weaknesses)
                    if (b.isRevealed(e, w)) Icon(PixelArt.elementIcon(w), 14.dp) else T("?", 11.sp, Sq.gold, bold = true, modifier = Modifier.width(12.dp), align = TextAlign.Center)
            }
        }
    }

/** Round portrait in a gold ring. */
@Composable
private fun Portrait(bmp: android.graphics.Bitmap, size: androidx.compose.ui.unit.Dp, ring: Color, badge: String? = null) =
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF3A3024), Color(0xFF0E0A08)))).padding(size / 10)) {
            Pixel(bmp, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().drawWithContent {
            drawCircle(Brush.sweepGradient(listOf(Color(0xFFFFE79A), ring, Mmo.goldDeep, ring, Color(0xFFFFE79A))), radius = drawContext.size.minDimension / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        })
        if (badge != null) Box(Modifier.align(Alignment.BottomStart).size(18.dp).clip(CircleShape).background(Color(0xFF1A0605))
            .drawWithContent { drawCircle(Mmo.gold, radius = drawContext.size.minDimension / 2 - 0.5.dp.toPx(), style = Stroke(1.dp.toPx())); drawContent() }, contentAlignment = Alignment.Center) {
            T(badge, 9.sp, Color(0xFFFFE08A), bold = true, serif = false)
        }
    }

@Composable
private fun FloatText(text: String, color: Color, big: Boolean, x: Float, y: Float, onDone: () -> Unit) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(900)); onDone() }
    val dy = with(LocalDensity.current) { 40.dp.toPx() } * rise.value
    Box(Modifier.centeredAt(x, y - dy).alpha(1f - rise.value * rise.value)) {
        T(text, if (big) 30.sp else 24.sp, color, bold = true, serif = true)
    }
}

@Composable
private fun TurnStrip(b: Battle) {
    val order = listOfNotNull(b.currentActor) + b.upcomingTurns().take(7)
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).mmoFrame(Interior.Slate, band = 2.dp, radius = 4.dp, rivets = false).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        T("Turn order ", 10.sp, Sq.muted)
        order.forEachIndexed { i, u ->
            val bmp = if (u.isHero) PixelArt.hero(b.heroClasses[u.heroIndex]) else PixelArt.enemy(u.enemyId!!)
            Box(Modifier.padding(end = 4.dp)) { Portrait(bmp, if (i == 0) 36.dp else 28.dp, if (i == 0) Mmo.gold else if (u.isHero) Color(0xFF6FA0FF) else Color(0xFFE0574A)) }
        }
    }
}

/** Party unit frame: portrait with level badge, name, labelled health and mana. */
@Composable
private fun UnitFrame(b: Battle, u: Combatant, hp: Int, mp: Int, active: Boolean, selectable: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val hero = Game.session.party[u.heroIndex]
    Row(modifier.padding(2.dp).alpha(if (u.isAlive || hp > 0) 1f else 0.5f)
        .mmoFrame(if (active) Interior.Bronze else if (selectable) Interior.Moss else Interior.Leather, band = 2.dp, radius = 5.dp, rivets = false)
        .clickable(enabled = selectable, onClick = onSelect).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Portrait(PixelArt.hero(b.heroClasses[u.heroIndex]), 40.dp, if (active) Color(0xFFFFE08A) else Mmo.gold, badge = hero.level.toString())
        Column(Modifier.padding(start = 5.dp)) {
            T(u.name, 11.sp, if (active) Color(0xFFFFE08A) else Sq.text, bold = true, maxLines = 1)
            Bar(hp / u.stats.maxHp.toFloat(), if (hp < u.stats.maxHp * 0.3f) Color(0xFFD4302A) else Sq.hp, 12.dp, label = "$hp / ${u.stats.maxHp}")
            Spacer(Modifier.height(2.dp))
            Bar(if (u.stats.maxMp == 0) 0f else mp / u.stats.maxMp.toFloat(), Sq.mp, 9.dp, label = "$mp")
        }
    }
}

private fun showResults(nav: Nav, s: BattleSummary) {
    val session = Game.session
    val title = when (s.outcome) {
        BattleOutcome.Victory -> if (s.bossDefeated) "Boss defeated!" else "Victory!"
        BattleOutcome.Defeat -> "Defeated..."
        else -> "Escaped"
    }
    nav.dialog = Dialog(title, {
        Column {
            when (s.outcome) {
                BattleOutcome.Victory -> {
                    H3("+${n(s.exp)} EXP")
                    H3("+${n(s.coins)} coins" + if (s.coinMultiplier > 1.001) "  (streak ×%.2f)".format(s.coinMultiplier) else "", Sq.gold)
                    s.drops.forEach { d -> GameData.itemOrNull(d)?.let { Small("Found: ${it.name}", Sq.rarity(it.rarity)) } }
                    s.levelsGained.forEach { (name, _) -> Small("$name reached level ${session.party.first { it.name == name }.level}!", Sq.good) }
                    s.regionHint?.let { Small(it, Sq.gold) }
                }
                BattleOutcome.Defeat -> {
                    Small("Your party was carried back to town. You dropped ${n(s.coinsLost)} coins.", Sq.text)
                    Small("Tip: rest at the inn, upgrade gear, or level up on easier foes.")
                }
                else -> Small("You live to walk another day.", Sq.text)
            }
        }
    }, listOf(Triple("Continue", false) { session.dismissSummary(); nav.screen = Screen.Home }))
}

