package com.stepquest.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepquest.app.Game
import com.stepquest.app.stage.Diorama
import com.stepquest.core.data.GameData
import com.stepquest.core.steps.DistanceCalc
import com.stepquest.core.steps.StepLedger
import com.stepquest.core.world.WorldMap

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(nav: Nav, stepPermission: Boolean, requestPermission: () -> Unit) {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val region = s.currentRegion
    LaunchedEffect(region.id, s.battle == null, v) { if (s.battle == null) nav.stage.showParty(s.party) else nav.stage.showBattle(s.battle!!) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(0.42f).drawWithContent { drawContent(); ironFrame(3.5.dp.toPx(), 0f, rivets = true) }) {
            Diorama(region, nav.stage)
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                T(region.name, 22.sp, Sq.gold, bold = true, serif = true)
                Tiny("${region.description}  ·  Recommended Lv ${region.recommendedLevel}", Sq.text)
            }
        }
        Column(Modifier.weight(0.58f).vscroll().padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (!stepPermission) Panel(glow = true) {
                H3("Step tracking is off", Sq.danger)
                Small("Allow Physical Activity access so your walking can power the party.", Sq.text)
                Btn("Allow step tracking", primary = true, onClick = requestPermission)
            } else if (!Game.hasSensor) Panel(glow = true) {
                H3("No step sensor found", Sq.danger)
                Small("This device has no hardware step counter. You can still try the game with the debug step buttons in Settings.", Sq.text)
            }

            val l = s.ledger
            Panel {
                RowC {
                    Column(Modifier.weight(1f)) {
                        Tiny("ACTIONS READY")
                        T(l.bankedActions.toString(), 40.sp, Sq.gold, bold = true, serif = true)
                    }
                    Column(Modifier.weight(2f)) {
                        Small("${n(l.stepsToNextAction)} steps to your next action", Sq.text)
                        Spacer(Modifier.height(6.dp))
                        Bar(l.stepRemainder / StepLedger.STEPS_PER_ACTION.toFloat(), height = 14.dp)
                        Spacer(Modifier.height(4.dp))
                        Tiny("Every 1,000 steps = 1 action in battle")
                    }
                }
                if (!s.todayUnlocked) {
                    Spacer(Modifier.height(8.dp))
                    RowC {
                        Icon("lock", 20.dp)
                        Small(
                            (if (s.lockedActions > 0) "${s.lockedActions} action${if (s.lockedActions == 1) "" else "s"} locked. " else "") +
                                "Walk ${n(s.stepsToUnlock)} more steps today to reach 80% of your goal and unlock today's actions.",
                            Sq.text, Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Bar(s.todaySteps / StepLedger.STEPS_PER_ACTION.toFloat() / (s.ledger.unlockThreshold / 1000f).coerceAtLeast(0.001f), Sq.gold, 12.dp,
                        label = "${n(s.todaySteps)} / ${n(s.ledger.unlockThreshold)} to unlock")
                }
            }
            val goal = s.data.settings.dailyStepGoal
            Panel(inset = true) {
                Small("Today: ${n(s.todaySteps)} / ${n(goal)} steps", Sq.text)
                Spacer(Modifier.height(4.dp))
                Bar(s.todaySteps / goal.toFloat(), Sq.good)
                Spacer(Modifier.height(4.dp))
                RowC {
                    Tiny("Today ${km(DistanceCalc.kilometers(s.todaySteps.toLong(), s.stride))} · Lifetime ${km(s.totalKm)}", modifier = Modifier.weight(1f))
                    val st = s.streak
                    Tiny(if (st.current > 0) "${st.current}-day streak · coins ×%.2f".format(s.coinMultiplier) else "Hit your goal to start a streak", Sq.gold)
                }
            }
            FlowRow(Modifier.fillMaxWidth()) {
                if (s.battle != null) Btn("Continue battle", Modifier.fillMaxWidth(), primary = true) { nav.screen = Screen.Battle }
                else {
                    Btn("Seek a battle", Modifier.weight(1f), enabled = s.canStartBattle(false) == null, primary = true) { start(nav, false) }
                    if (region.bossId !in s.data.bossesDefeated) {
                        val boss = GameData.enemy(region.bossId)
                        Btn("Boss: ${boss.name}", Modifier.weight(1f), danger = true) {
                            nav.confirm("Boss battle", "${boss.name} awaits. Bosses take many actions — you can leave mid-fight and come back after more walking.\n\nRecommended level ${region.recommendedLevel + 3}. You can't flee.", "Fight!", danger = true) { start(nav, true) }
                        }
                    }
                }
            }
            if (s.battle == null && s.party.any { it.hp < it.totalStats().maxHp || it.mp < it.totalStats().maxMp })
                Btn("Rest at the inn (${s.innCost} coins)", Modifier.fillMaxWidth()) {
                    if (s.restAtInn()) Game.toast("The party is fully rested.") else Game.toast("Not enough coins")
                }
            if (s.data.settings.debugTools) RowC {
                Btn("+1k steps", small = true, ghost = true) { s.addDebugSteps(1000) }
                Btn("+10k steps", small = true, ghost = true) { s.addDebugSteps(10_000) }
            }
        }
    }
}

private fun start(nav: Nav, boss: Boolean) {
    val s = Game.session
    s.canStartBattle(boss)?.let { Game.toast(it); return }
    if (s.ledger.bankedActions == 0) Game.toast("No actions yet", "You can look, but each command costs 1,000 steps.")
    s.startBattle(boss)
    nav.screen = Screen.Battle
}

@Composable
fun MapScreen(nav: Nav) {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    ScreenColumn {
        Panel {
            Title("The Long Road")
            val next = GameData.regions.firstOrNull { !s.isRegionUnlocked(it) }
            Small(if (next == null) "You've walked ${km(s.totalKm)}. Every region is open to you."
                  else "You've walked ${km(s.totalKm)}. Next: ${next.name} — ${WorldMap.lockReason(next, s.totalKm, s.data.bossesDefeated)}.", Sq.text)
            Spacer(Modifier.height(6.dp))
            Bar((s.totalKm / GameData.regions.last().unlockKm).toFloat(), height = 14.dp)
        }
        for (r in GameData.regions) {
            val unlocked = s.isRegionUnlocked(r)
            val here = s.currentRegion.id == r.id
            val bossDown = r.bossId in s.data.bossesDefeated
            Card(done = here, locked = !unlocked) {
                RowC { H3("${r.index + 1}. ${r.name}", modifier = Modifier.weight(1f)); Small("Lv ${r.recommendedLevel}+") }
                Small(r.description)
                Spacer(Modifier.height(4.dp))
                RowC {
                    val boss = GameData.enemy(r.bossId)
                    Tiny(if (bossDown) "Defeated ${boss.name}" else "Boss: ${boss.name}", if (bossDown) Sq.good else Sq.muted, Modifier.weight(1f))
                    when {
                        !unlocked -> { Icon("lock", 16.dp); Tiny(" " + WorldMap.lockReason(r, s.totalKm, s.data.bossesDefeated)) }
                        here -> Tiny("You are here", Sq.gold)
                        else -> Btn("Travel", small = true) {
                            if (s.travelTo(r.id)) { Game.toast("Arrived at ${r.name}"); nav.screen = Screen.Home } else Game.toast("Finish your battle first.")
                        }
                    }
                }
            }
        }
    }
}

