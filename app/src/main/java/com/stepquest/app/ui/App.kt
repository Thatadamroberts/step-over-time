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
import androidx.compose.foundation.layout.Box
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
    Row(Modifier.fillMaxWidth().background(Color(0xD9080B16)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("TODAY", n(s.todaySteps), Modifier.weight(1f))
        Chip("ACTIONS", s.ledger.bankedActions.toString(), Modifier.weight(1f), Sq.gold, big = true)
        Chip("COINS", n(s.inventory.coins), Modifier.weight(1f))
        Chip("STREAK", s.streak.current.let { if (it > 0) "$it" else "-" }, Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { nav.screen = Screen.Settings }.padding(6.dp)) { Icon("gear", 26.dp) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Sq.goldDim))
}

@Composable
private fun Chip(label: String, value: String, modifier: Modifier, color: Color = Sq.text, big: Boolean = false) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(value, if (big) 22.sp else 17.sp, color, bold = true)
        T(label, 9.sp, Sq.muted)
    }

@Composable
private fun NavBar(nav: Nav) {
    val s = Game.session
    val claimable = s.activeQuests.any { !s.isClaimed(it) && s.questProgress(it) >= it.target }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Sq.goldDim))
    Row(Modifier.fillMaxWidth().background(Color(0xF0080B16)).padding(vertical = 6.dp)) {
        for (sc in listOf(Screen.Home, Screen.Map, Screen.Party, Screen.Shop, Screen.Quests, Screen.Trophies)) {
            val active = nav.screen == sc
            Column(Modifier.weight(1f).clickable { nav.screen = sc }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Icon(sc.icon, if (active) 30.dp else 26.dp)
                    if (sc == Screen.Quests && claimable) Box(Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(6.dp)).background(Sq.danger).padding(horizontal = 4.dp)) { T("!", 10.sp, bold = true) }
                }
                T(sc.label, 11.sp, if (active) Sq.gold else Sq.muted)
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
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xF2140F08))
                        .border(1.5.dp, Sq.gold, RoundedCornerShape(12.dp)).padding(12.dp)) {
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
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sq.panel).border(1.5.dp, Sq.gold, RoundedCornerShape(14.dp)).padding(20.dp)) {
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
