package com.stepquest.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stepquest.core.steps.StepLedger
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepquest.app.Game
import com.stepquest.app.stage.StageState
import kotlinx.coroutines.delay

enum class Screen(val icon: String, val label: String) {
    Home("home", "Town"), Map("map", "Map"), Party("helm", "Party"), Shop("bag", "Shop"),
    Quests("scroll", "Quests"), Trophies("trophy", "Trophies"), Battle("sword", "Battle"), Settings("gear", "Settings")
}

/** A modal dialog request. */
class Dialog(val title: String, val body: @Composable () -> Unit, val buttons: List<Triple<String, Boolean, () -> Unit>>)

class Nav {
    var screen by mutableStateOf(Screen.Home)
    var dialog by mutableStateOf<Dialog?>(null)
    val stage = StageState()

    fun confirm(title: String, text: String, ok: String, danger: Boolean = false, onOk: () -> Unit) {
        dialog = Dialog(title, { Small(text, Sq.text) }, listOf(Triple("Cancel", false) {}, Triple(ok, danger, onOk)))
    }
}

@Composable
fun StepQuestApp(stepPermission: Boolean, requestPermission: () -> Unit) {
    val nav = remember { Nav() }
    val v = Game.version // subscribe to game changes
    LaunchedEffect(Unit) { if (Game.session.battle != null) nav.screen = Screen.Battle }
    BackHandler(nav.screen != Screen.Home) { nav.screen = if (Game.session.battle != null && nav.screen == Screen.Settings) Screen.Battle else Screen.Home }

    Box(Modifier.fillMaxSize().background(Sq.ink)) {
        HallBackground()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            TopBar(nav)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (nav.screen) {
                    Screen.Home -> HomeScreen(nav, stepPermission, requestPermission)
                    Screen.Map -> MapScreen(nav)
                    Screen.Battle -> BattleScreen(nav)
                    Screen.Party -> PartyScreen(nav)
                    Screen.Shop -> ShopScreen(nav)
                    Screen.Quests -> QuestsScreen(nav)
                    Screen.Trophies -> TrophiesScreen(nav)
                    Screen.Settings -> SettingsScreen(nav, stepPermission, requestPermission)
                }
            }
            StepBar()
            if (nav.screen != Screen.Battle) NavBar(nav)
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        Toasts()
        nav.dialog?.let { DialogView(nav, it) }
    }
}

@Composable
private fun TopBar(nav: Nav) {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    Row(Modifier.fillMaxWidth().mmoFrame(Interior.Leather, band = 2.5.dp, radius = 0.dp, rivets = true, seed = 9).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Gold-ringed badge holding your banked actions, like a level badge on a portrait.
        Box(Modifier.size(58.dp).drawBehind {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF5A1712), Color(0xFF1A0605))))
            drawCircle(Brush.sweepGradient(listOf(Color(0xFFFFE79A), Mmo.gold, Mmo.goldDeep, Mmo.gold, Color(0xFFFFE79A))), radius = size.minDimension / 2 - 1.25.dp.toPx(), style = Stroke(2.5.dp.toPx()))
        }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(s.ledger.bankedActions.toString(), 22.sp, Color(0xFFFFE08A), bold = true)
                T("ACTIONS", 7.sp, Sq.muted, serif = false)
            }
        }
        Stat("boot", n(s.todaySteps), "steps today", Modifier.weight(1f))
        Stat("coin", n(s.inventory.coins), "gold", Modifier.weight(1f))
        Stat("flame", s.streak.current.toString(), "day streak", Modifier.weight(0.8f))
        Box(Modifier.size(42.dp).mmoFrame(Interior.Slate, band = 2.dp, radius = 4.dp, rivets = false).clickable { nav.screen = Screen.Settings },
            contentAlignment = Alignment.Center) { Icon("gear", 24.dp) }
    }
}

@Composable
private fun Stat(icon: String, value: String, label: String, modifier: Modifier) =
    Row(modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, 22.dp)
        Column(Modifier.padding(start = 5.dp)) {
            T(value, 15.sp, Sq.text, bold = true, maxLines = 1)
            T(label, 9.sp, Sq.muted, serif = false, maxLines = 1)
        }
    }

/** The "experience bar" of StepQuest: 20 segments filling toward your next action. */
@Composable
private fun StepBar() {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val l = s.ledger
    Box(Modifier.fillMaxWidth().background(Color(0xFF0A0806)).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Bar(l.stepRemainder / StepLedger.STEPS_PER_ACTION.toFloat(), Color(0xFFB04AD0), 14.dp,
            label = "${n(l.stepRemainder)} / ${n(StepLedger.STEPS_PER_ACTION)} steps to next action", ticks = 20)
    }
}

@Composable
private fun NavBar(nav: Nav) {
    val s = Game.session
    val claimable = s.activeQuests.any { !s.isClaimed(it) && s.questProgress(it) >= it.target }
    // Micro-menu: square framed slots, the active one glowing gold.
    Row(Modifier.fillMaxWidth().background(Color(0xFF0A0806)).padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (sc in listOf(Screen.Home, Screen.Map, Screen.Party, Screen.Shop, Screen.Quests, Screen.Trophies)) {
            val active = nav.screen == sc
            Column(Modifier.weight(1f).clickable { nav.screen = sc }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(46.dp).mmoFrame(if (active) Interior.Bronze else Interior.Slate, band = 2.dp, radius = 4.dp, rivets = false)
                    .drawBehind { if (active) drawRect(Brush.radialGradient(listOf(Color(0x66FFD86B), Color.Transparent))) },
                    contentAlignment = Alignment.Center) {
                    Icon(sc.icon, 28.dp)
                    if (sc == Screen.Quests && claimable) T("!", 18.sp, Color(0xFFFFD100), bold = true, modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp))
                }
                T(sc.label, 10.sp, if (active) Sq.gold else Sq.muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Toasts() {
    val list = Game.toasts
    Column(Modifier.fillMaxWidth().padding(top = 90.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        list.take(3).forEach { t ->
            key(t.id) {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(t.id) { shown = true; delay(3000); shown = false; delay(400); list.remove(t) }
                AnimatedVisibility(shown, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).mmoFrame(Interior.Bronze, band = 2.5.dp, seed = 4)
                        .padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        H3(t.title, Sq.gold)
                        t.body?.let { Small(it, Sq.text) }
                    }
                }
            }
        }
    }
}

@Composable private fun key(k: Any, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

@Composable
private fun DialogView(nav: Nav, d: Dialog) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(enabled = true) {}.padding(28.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().mmoFrame(Interior.Leather, band = 3.5.dp, radius = 8.dp, seed = 6).padding(20.dp)) {
            Title(d.title)
            Box(Modifier.weight(1f, fill = false)) { Column(Modifier.vscroll()) { d.body() } }
            Spacer(Modifier.height(10.dp))
            Row {
                val buttons = d.buttons.ifEmpty { listOf(Triple("Close", false) {}) }
                buttons.forEachIndexed { i, (label, danger, action) ->
                    Btn(label, Modifier.weight(1f), primary = !danger && i == buttons.lastIndex, danger = danger) { nav.dialog = null; action() }
                }
            }
        }
    }
}

@Composable
fun Modifier.vscroll(): Modifier = this.verticalScroll(rememberScrollState())

/** Standard scrolling screen body. */
@Composable
fun ScreenColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().vscroll().padding(horizontal = 14.dp, vertical = 10.dp), content = content)
