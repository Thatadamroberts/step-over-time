package com.stepquest.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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

    val b = s.battle
    LaunchedEffect(b == null) { if (b != null) stage.showBattle(b) }
    LaunchedEffect(b, s.lastSummary, busy) {
        if (busy) return@LaunchedEffect
        val sum = s.lastSummary
        if (b == null) {
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
            shownHp.clear(); shownMp.clear()
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

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(0.46f)) {
            val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
            val unitPx = w / 12f
            Diorama(region, stage, onTapUnit = { id ->
                if (busy) return@Diorama
                val unit = b.unit(id) ?: return@Diorama
                if (mode == Mode.Target && validTargets(b, pendingTarget).any { it.id == id }) pending?.let { execute(it.copy(targetId = id)) }
                else if (mode == Mode.Root && !unit.isHero && unit.isAlive && s.ledger.bankedActions > 0) execute(BattleCommand.attack(id))
            })
            // Enemy tags: name, HP, shield pips and discovered weaknesses.
            for (e in b.enemies) {
                if (!e.isAlive && !busy) continue
                if ((stage.fade[e.id] ?: 1f) < 0.05f) continue
                val anchor = stage.anchor(e.id, w, h, unitPx) ?: continue
                EnemyTag(b, e, shownHp[e.id] ?: e.hp, Modifier.centeredAt(anchor.x, anchor.y - 6))
            }
            // Floating numbers.
            for (f in stage.floaters.toList()) {
                val anchor = stage.anchor(f.unitId, w, h, unitPx) ?: continue
                androidx.compose.runtime.key(f.key) { FloatText(f.text, f.color, f.big, anchor.x, anchor.y + unitPx) { stage.floaters.remove(f) } }
            }
            RowC(Modifier.align(Alignment.TopCenter).padding(10.dp)) {
                T(if (b.isBoss) "BOSS BATTLE" else region.name, 16.sp, Sq.gold, bold = true, serif = true, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    val a = s.ledger.bankedActions
                    T(if (a == 1) "1 action" else "$a actions", 18.sp, bold = true)
                    Tiny("${n(s.ledger.stepsToNextAction)} steps to next", Sq.text)
                }
            }
        }

        Column(Modifier.weight(0.54f).vscroll().padding(horizontal = 12.dp, vertical = 6.dp)) {
            TurnStrip(b)
            Panel {
                for (u in b.heroes) HeroRow(b, u, shownHp[u.id] ?: u.hp, shownMp[u.id] ?: u.mp, !busy && b.currentActor == u)
                if (log.isNotEmpty() || b.log.isNotEmpty()) Tiny(log.ifEmpty { b.log.last().text }, Sq.muted)
            }
            val actor = b.currentActor
            if (s.ledger.bankedActions <= 0 && !busy) {
                Panel(glow = true) {
                    H2("Out of actions")
                    Small("Your party holds its ground. Every 1,000 steps you walk gives them another action — the battle will wait for you.", Sq.text)
                    Spacer(Modifier.height(6.dp))
                    Bar(s.ledger.stepRemainder / StepLedger.STEPS_PER_ACTION.toFloat(), height = 14.dp)
                    Btn("Return to town", Modifier.fillMaxWidth()) { nav.screen = Screen.Home }
                    if (s.data.settings.debugTools) Btn("+1k steps (debug)", small = true, ghost = true) { s.addDebugSteps(1000) }
                }
            } else if (actor != null && actor.isHero && !busy) {
                val hero = s.party[actor.heroIndex]
                FlowRow(Modifier.fillMaxWidth()) {
                    val cell = Modifier.fillMaxWidth(0.33f)
                    val half = Modifier.fillMaxWidth(0.5f)
                    when (mode) {
                        Mode.Root -> {
                            Btn("Attack", cell, primary = true) { beginTarget(BattleCommand.attack(-1), TargetKind.SingleEnemy) }
                            Btn("Skills", cell) { mode = Mode.Skills }
                            Btn("Defend", cell) { execute(BattleCommand.defend()) }
                            Btn("Items", half) { mode = Mode.Items }
                            Btn("Flee", half, enabled = !b.isBoss, ghost = true) { execute(BattleCommand.flee()) }
                        }
                        Mode.Skills -> {
                            for (sk in hero.skills()) Btn("${sk.name} · ${sk.mpCost} MP", half, enabled = actor.mp >= sk.mpCost, small = true) { chooseSkill(sk, ::execute, ::beginTarget) }
                            Btn("< Back", half, ghost = true, small = true) { mode = Mode.Root }
                        }
                        Mode.Items -> {
                            s.inventory.items.toSortedMap().forEach { (id, count) ->
                                val item = GameData.itemOrNull(id)
                                if (item != null && item.isConsumable) Btn("${item.name} ×$count", half, small = true) {
                                    beginTarget(BattleCommand.item(id, -1), if (item.use == ConsumableEffect.Revive) TargetKind.DeadAlly else TargetKind.SingleAlly)
                                }
                            }
                            Btn("< Back", half, ghost = true, small = true) { mode = Mode.Root }
                        }
                        Mode.Target -> {
                            for (t in validTargets(b, pendingTarget))
                                Btn(t.name + if (t.isHero) "  ${t.hp}/${t.stats.maxHp}" else "", half, small = true) { pending?.let { execute(it.copy(targetId = t.id)) } }
                            Btn("< Back", half, ghost = true, small = true) {
                                mode = when (pending?.kind) { CommandKind.Skill -> Mode.Skills; CommandKind.Item -> Mode.Items; else -> Mode.Root }
                                pending = null
                            }
                        }
                    }
                }
                if (mode == Mode.Skills) Tiny("Tip: hitting a weakness lowers the shield. At zero the enemy is BROKEN — it loses a turn and takes double damage.")
                if (mode == Mode.Root) Tiny("Tip: tap an enemy to attack it directly.")
            }
        }
    }
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

@Composable
private fun EnemyTag(b: Battle, e: Combatant, hp: Int, modifier: Modifier) {
    Column(modifier.widthIn(min = 70.dp, max = 120.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC080B16))
        .border(1.dp, Color(0x99FF7864), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        T(e.name, 10.sp, align = TextAlign.Center, maxLines = 1)
        Bar(hp / e.stats.maxHp.toFloat(), Sq.hp, 4.dp)
        if (e.broken) T("BROKEN", 10.sp, Color(0xFFFFD86B), bold = true)
        else Row(Modifier.padding(top = 2.dp)) {
            repeat(e.maxShield) { i -> Box(Modifier.padding(0.5.dp).size(5.dp, 7.dp).clip(RoundedCornerShape(1.dp)).background(if (i < e.shield) Color(0xFF8FB4FF) else Color(0x26FFFFFF))) }
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            for (w in GameData.enemy(e.enemyId!!).weaknesses) {
                if (b.isRevealed(e, w)) Icon(PixelArt.elementIcon(w), 14.dp) else T("?", 11.sp, Sq.gold, bold = true, modifier = Modifier.width(14.dp), align = TextAlign.Center)
            }
        }
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
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xB3080B16)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Tiny("TURN ")
        order.forEachIndexed { i, u ->
            val bmp = if (u.isHero) PixelArt.hero(b.heroClasses[u.heroIndex]) else PixelArt.enemy(u.enemyId!!)
            val sz = if (i == 0) 40.dp else 32.dp
            Box(Modifier.padding(end = 4.dp).size(sz).clip(RoundedCornerShape(6.dp)).background(Color(0x66000000))
                .border(1.5.dp, if (i == 0) Sq.gold else if (u.isHero) Color(0x9978B4FF) else Color(0x99FF6E5A), RoundedCornerShape(6.dp)).padding(2.dp)) {
                Pixel(bmp, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun HeroRow(b: Battle, u: Combatant, hp: Int, mp: Int, active: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp))
        .background(if (active) Sq.gold.copy(alpha = 0.18f) else Color.Transparent).alpha(if (u.isAlive || hp > 0) 1f else 0.45f)
        .padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Pixel(PixelArt.hero(b.heroClasses[u.heroIndex]), Modifier.size(24.dp, 30.dp))
        T((if (active) "> " else "") + u.name + if (u.broken) " (broken)" else "", 13.sp, modifier = Modifier.width(118.dp).padding(start = 4.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            val f = hp / u.stats.maxHp.toFloat()
            Bar(f, if (f < 0.3f) Sq.danger else Sq.hp, 8.dp)
            Spacer(Modifier.height(3.dp))
            Bar(if (u.stats.maxMp == 0) 0f else mp / u.stats.maxMp.toFloat(), Sq.mp, 5.dp)
        }
        T("$hp/${u.stats.maxHp}\n$mp MP", 10.sp, Sq.muted, modifier = Modifier.width(64.dp), align = TextAlign.End)
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

