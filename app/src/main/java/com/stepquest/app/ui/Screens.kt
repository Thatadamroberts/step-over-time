package com.stepquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.stepquest.app.Game
import com.stepquest.app.art.PixelArt
import com.stepquest.core.data.GameData
import com.stepquest.core.economy.ConsumableEffect
import com.stepquest.core.economy.EquipSlot
import com.stepquest.core.economy.Rarity
import com.stepquest.core.economy.Shop
import com.stepquest.core.meta.AchievementCategory
import com.stepquest.core.meta.AchievementDef
import com.stepquest.core.meta.Achievements
import com.stepquest.core.meta.Metric
import com.stepquest.core.meta.QuestDef
import com.stepquest.core.meta.QuestPeriod
import com.stepquest.core.meta.Streaks
import com.stepquest.core.party.Leveling
import com.stepquest.core.party.StatType
import com.stepquest.core.party.Stats
import com.stepquest.core.steps.DistanceCalc

fun statLine(s: Stats): String = StatType.entries.filter { s[it] != 0 }.joinToString("  ") { "${short(it)} ${if (s[it] > 0) "+" else ""}${s[it]}" }.ifEmpty { "—" }
private fun short(t: StatType) = when (t) { StatType.MaxHp -> "HP"; StatType.MaxMp -> "MP"; else -> t.name.uppercase() }
private fun diffLine(next: Stats, cur: Stats) = StatType.entries.mapNotNull { t -> (next[t] - cur[t]).takeIf { it != 0 }?.let { "${short(t)} ${if (it > 0) "+" else ""}$it" } }.joinToString("  ").ifEmpty { "No change" }

@Composable
private fun LabeledBar(label: String, value: Int, max: Int, color: androidx.compose.ui.graphics.Color) = RowC(Modifier.padding(top = 3.dp)) {
    Tiny(label, modifier = Modifier.width(34.dp))
    Bar(if (max <= 0) 0f else value / max.toFloat(), color, modifier = Modifier.weight(1f))
    Tiny("$value/$max", modifier = Modifier.width(78.dp).padding(start = 6.dp))
}

// ───────────────────────────── Party ─────────────────────────────

@Composable
fun PartyScreen(nav: Nav) {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    ScreenColumn {
        if (selected < 0) {
            Title("Party")
            s.party.forEachIndexed { i, h ->
                val st = h.totalStats()
                Card(onClick = { selected = i }) {
                    RowC {
                        Pixel(PixelArt.hero(h.heroClass), Modifier.size(64.dp, 80.dp).padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            RowC { H3(h.name, modifier = Modifier.weight(1f)); H3("Lv ${h.level}", Sq.gold) }
                            Small(h.def.name + if (h.skillPoints > 0) "  ·  ${h.skillPoints} skill point${if (h.skillPoints > 1) "s" else ""} to spend!" else "", if (h.skillPoints > 0) Sq.good else Sq.muted)
                            LabeledBar("HP", h.hp, st.maxHp, if (h.hp < st.maxHp * 0.3f) Sq.danger else Sq.hp)
                            LabeledBar("MP", h.mp, st.maxMp, Sq.mp)
                            val need = Leveling.expToNext(h.level)
                            LabeledBar("EXP", h.exp, if (need == Int.MAX_VALUE) 1 else need, Sq.exp)
                        }
                    }
                }
            }
        } else HeroDetail(nav, selected) { selected = -1 }
    }
}

@Composable
private fun HeroDetail(nav: Nav, index: Int, back: () -> Unit) {
    val s = Game.session
    val h = s.party[index]
    val st = h.totalStats()
    Btn("< Party", small = true, ghost = true, onClick = back)
    Panel {
        RowC {
            Pixel(PixelArt.hero(h.heroClass), Modifier.size(80.dp, 100.dp).padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Title(h.name); H3("Level ${h.level} ${h.def.name}", Sq.gold); Small(h.def.blurb)
                LabeledBar("HP", h.hp, st.maxHp, Sq.hp); LabeledBar("MP", h.mp, st.maxMp, Sq.mp)
            }
        }
    }
    Panel(inset = true) {
        H2("Stats")
        val cells = listOf("ATK" to st.atk, "DEF" to st.def, "MAG" to st.mag, "RES" to st.res, "SPD" to st.spd, "CRIT" to st.crit)
        cells.chunked(3).forEach { row -> RowC { row.forEach { (k, v) -> RowC(Modifier.weight(1f).padding(end = 12.dp), h = androidx.compose.foundation.layout.Arrangement.SpaceBetween) { Small(k); Small("$v", Sq.text) } } } }
    }
    Panel {
        H2("Equipment")
        for ((slot, id) in listOf(EquipSlot.Weapon to h.weaponId, EquipSlot.Armor to h.armorId, EquipSlot.Accessory to h.accessoryId)) {
            val item = GameData.itemOrNull(id)
            Card {
                RowC {
                    Column(Modifier.weight(1f)) {
                        Tiny(slot.name.uppercase())
                        H3(item?.name ?: "— empty —", item?.let { Sq.rarity(it.rarity) } ?: Sq.muted)
                        item?.let { Tiny(statLine(it.bonus)) }
                    }
                    Btn("Change", small = true, enabled = s.battle == null) { chooseGear(nav, index, slot) }
                }
            }
        }
        if (s.battle != null) Tiny("Gear can't be changed mid-battle.")
    }
    Panel {
        H2("Skills")
        h.skills().forEach { sk -> RowC(Modifier.padding(vertical = 2.dp)) { H3(sk.name, modifier = Modifier.width(120.dp)); Small("${sk.mpCost} MP · ${sk.description}", modifier = Modifier.weight(1f)) } }
    }
    Panel {
        RowC { H2("Skill tree", Modifier.weight(1f)); H3("${h.skillPoints} SP", if (h.skillPoints > 0) Sq.good else Sq.muted) }
        for (node in h.def.tree) {
            val learned = node.id in h.unlockedNodes
            val blocker = h.unlockBlocker(node.id)
            Card(done = learned, locked = !learned && blocker != null) {
                RowC {
                    Column(Modifier.weight(1f)) {
                        H3(node.name + if (node.unlocksSkill != null) " (new skill)" else "")
                        val req = node.requires?.let { r -> " · needs " + h.def.tree.first { it.id == r }.name } ?: ""
                        Tiny("${node.description} · Lv ${node.minLevel} · ${node.cost} SP$req")
                    }
                    if (learned) Small("Learned", Sq.good)
                    else Btn("Learn", small = true, enabled = blocker == null) { if (s.learnNode(index, node.id)) Game.toast("${h.name} learned ${node.name}!") }
                }
            }
        }
    }
    Panel {
        H2("Use item")
        val usable = s.inventory.items.toSortedMap().mapNotNull { (id, c) -> GameData.itemOrNull(id)?.takeIf { it.isConsumable && it.use != ConsumableEffect.Cleanse }?.let { it to c } }
        if (usable.isEmpty()) Small("No usable items. Buy some in the shop.")
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow { usable.forEach { (item, c) -> Btn("${item.name} ×$c", small = true, enabled = s.battle == null) { if (!s.useItem(item.id, index)) Game.toast("It would have no effect.") } } }
    }
}

private fun chooseGear(nav: Nav, heroIndex: Int, slot: EquipSlot) {
    val s = Game.session
    val hero = s.party[heroIndex]
    val current = GameData.itemOrNull(when (slot) { EquipSlot.Weapon -> hero.weaponId; EquipSlot.Armor -> hero.armorId; else -> hero.accessoryId })
    nav.dialog = Dialog("${hero.name}: $slot", {
        val options = s.inventory.items.mapNotNull { (id, c) -> GameData.itemOrNull(id)?.takeIf { it.slot == slot && it.canEquip(hero.heroClass) }?.let { it to c } }
        Column {
            if (options.isEmpty()) Small("Nothing in your bags that a ${hero.def.name} can use here. Visit the shop!", Sq.text)
            options.forEach { (item, c) ->
                Card {
                    RowC {
                        Column(Modifier.weight(1f)) { H3("${item.name} ×$c", Sq.rarity(item.rarity)); Small(diffLine(item.bonus, current?.bonus ?: Stats()), Sq.text) }
                        Btn("Equip", small = true, primary = true) { if (s.equip(heroIndex, item.id)) { Game.toast("${hero.name} equipped ${item.name}"); nav.dialog = null } }
                    }
                }
            }
        }
    }, emptyList())
}

// ───────────────────────────── Shop ─────────────────────────────

@Composable
fun ShopScreen(nav: Nav) {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenColumn {
        Title("Market & Inn")
        RowC { listOf("Buy", "Sell", "Inn").forEachIndexed { i, t -> Btn(t, Modifier.weight(1f), selected = tab == i) { tab = i } } }
        Spacer(Modifier.height(6.dp))
        when (tab) {
            0 -> {
                Small("New stock arrives as you reach new regions.")
                Shop.stock(s.highestRegion).groupBy { it.slot }.toSortedMap().forEach { (slot, items) ->
                    H2(if (slot == EquipSlot.None) "Supplies" else slot.name + "s", Modifier.padding(top = 8.dp))
                    items.sortedBy { it.price }.forEach { item ->
                        Card {
                            RowC {
                                Column(Modifier.weight(1f)) {
                                    H3(item.name, Sq.rarity(item.rarity))
                                    Tiny(if (item.isConsumable) item.description else statLine(item.bonus) + if (item.classes.isEmpty()) "" else "  ·  " + item.classes.joinToString("/"))
                                    val owned = s.inventory.count(item.id) + s.party.count { item.id in listOf(it.weaponId, it.armorId, it.accessoryId) }
                                    if (owned > 0) Tiny("Owned: $owned", Sq.gold)
                                }
                                Btn("${n(item.price)}c", small = true, primary = true, enabled = s.inventory.coins >= item.price) {
                                    if (s.buy(item.id)) Game.toast("Bought ${item.name}") else Game.toast("Not enough coins")
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                val items = s.inventory.items.toSortedMap().mapNotNull { (id, c) -> GameData.itemOrNull(id)?.let { it to c } }
                if (items.isEmpty()) Small("Your bags are empty. Equipped gear isn't shown here.")
                items.forEach { (item, c) ->
                    Card {
                        RowC {
                            Column(Modifier.weight(1f)) { H3("${item.name} ×$c", Sq.rarity(item.rarity)); Tiny(if (item.isConsumable) item.description else statLine(item.bonus)) }
                            val price = Shop.sellPrice(item)
                            Btn("Sell ${n(price)}c", small = true, enabled = price > 0) {
                                if (item.rarity >= Rarity.Rare) nav.confirm("Sell ${item.name}?", "Rare items are hard to replace.", "Sell", danger = true) { s.sell(item.id) }
                                else s.sell(item.id)
                            }
                        }
                    }
                }
            }
            else -> Panel {
                H2("The Wandering Boot Inn")
                Small("A warm meal and a soft bed restore all HP and MP and revive the fallen.", Sq.text)
                s.party.forEach { h -> val st = h.totalStats(); Small("${h.name}: ${h.hp}/${st.maxHp} HP · ${h.mp}/${st.maxMp} MP", if (h.hp <= 0) Sq.danger else Sq.muted) }
                Btn("Rest (${n(s.innCost)} coins)", Modifier.fillMaxWidth(), primary = true) {
                    if (s.restAtInn()) Game.toast("Fully rested!") else Game.toast(if (s.battle != null) "Finish your battle first." else "Not enough coins")
                }
            }
        }
    }
}

// ───────────────────────────── Quests ─────────────────────────────

@Composable
fun QuestsScreen(nav: Nav) {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    ScreenColumn {
        Title("Quests")
        val st = s.streak
        Panel(glow = true) {
            RowC {
                Icon("flame", 56.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    H2(if (st.current > 0) "${st.current}-day streak" else "No streak yet")
                    Small(if (st.todayDone) "Today's goal reached — see you tomorrow!"
                          else "Walk ${n(maxOf(0, s.data.settings.dailyStepGoal - s.todaySteps))} more steps today to keep it going.", Sq.text)
                    Tiny("Battle coins ×%.2f (max ×1.5) · One missed day a week is forgiven".format(s.coinMultiplier) + if (st.shieldsUsed > 0) " (${st.shieldsUsed} used)" else "")
                }
            }
        }
        for (period in QuestPeriod.entries) {
            H2(period.name, Modifier.padding(top = 6.dp))
            s.activeQuests.filter { it.period == period }.forEach { q -> QuestCard(q) }
        }
    }
}

@Composable
private fun QuestCard(q: QuestDef) {
    val s = Game.session
    val progress = s.questProgress(q)
    val claimed = s.isClaimed(q)
    val done = progress >= q.target
    val reward = "+${q.rewardCoins} coins" + (GameData.itemOrNull(q.rewardItem)?.let { " + ${it.name}" } ?: "")
    Card(done = claimed) {
        RowC {
            // Classic quest markers: "!" in progress, "?" ready to hand in.
            T(if (claimed) " " else if (done) "?" else "!", 26.sp, if (done && !claimed) Color(0xFFFFD100) else Color(0xFFC9A230), bold = true,
                modifier = Modifier.width(22.dp))
            H3(q.text, modifier = Modifier.weight(1f))
            if (claimed) { Icon("check", 18.dp); Small(" Claimed", Sq.good) }
            else Btn("Claim", small = true, primary = true, enabled = done) { if (s.claimQuest(q.id)) Game.toast("Quest complete!", reward) }
        }
        Spacer(Modifier.height(4.dp))
        Bar(progress / q.target.toFloat(), if (done) Sq.good else Sq.gold)
        RowC(Modifier.padding(top = 3.dp)) { Tiny("${n(minOf(progress, q.target))} / ${n(q.target)}", modifier = Modifier.weight(1f)); Tiny(reward, Sq.gold) }
    }
}

// ───────────────────────────── Trophies ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrophiesScreen(nav: Nav) {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenColumn {
        Title("Trophies")
        val km = s.totalKm
        val moon = km / Achievements.MOON_KM
        Panel(glow = true) {
            RowC {
                Box(Modifier.size(76.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFFE8E4D8)).border(2.dp, Sq.goldDim, CircleShape), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(moon.toFloat().coerceIn(0.03f, 1f)).background(androidx.compose.ui.graphics.Color(0xFFFFD86B)))
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    H2("Journey to the Moon")
                    T(Achievements.percent(moon), 32.sp, Sq.gold, bold = true, serif = true)
                    Small("${km(km)} of 384,400 km")
                }
            }
        }
        Panel(inset = true) {
            val cells = listOf(
                "Total steps" to n(s.ledger.totalSteps), "Distance" to km(km),
                "Best day" to "${n(s.metric(Metric.BestDaySteps).toLong())} steps", "Longest streak" to "${s.metric(Metric.LongestStreak).toInt()} days",
                "Climbed (as stairs)" to "%,.0f m".format(DistanceCalc.climbMeters(s.ledger.totalSteps)), "Actions earned" to n(s.ledger.totalActionsEarned),
                "Battles won" to n(s.data.counters.battlesWon), "Unlocked" to "${s.data.achievements.size} / ${Achievements.all.size}",
            )
            cells.chunked(2).forEach { row -> RowC(Modifier.padding(bottom = 6.dp)) { row.forEach { (k, v) -> Column(Modifier.weight(1f)) { Tiny(k); H3(v) } } } }
        }
        Panel {
            H2("Did you know?")
            s.comparisons().forEach { Small("• $it", Sq.text) }
        }
        FlowRow {
            Btn("All", small = true, selected = filter == null) { filter = null }
            AchievementCategory.entries.forEach { c -> Btn(c.name, small = true, selected = filter == c.name) { filter = c.name } }
        }
        Achievements.all.filter { filter == null || it.category.name == filter }
            .sortedWith(compareBy<AchievementDef> { if (s.isUnlocked(it)) 0 else 1 }.thenByDescending { s.achievementProgress(it) })
            .forEach { a -> TrophyRow(a) }
    }
}

@Composable
private fun TrophyRow(a: AchievementDef) {
    val s = Game.session
    val done = s.isUnlocked(a)
    val p = s.achievementProgress(a)
    Card(done = done) {
        RowC {
            Icon(if (done) "star" else "star_empty", 28.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) { H3(a.name); Tiny(a.description) }
            Tiny("+${n(a.rewardCoins)}c", Sq.gold)
        }
        if (!done) {
            Spacer(Modifier.height(4.dp))
            Bar(p.toFloat(), height = 5.dp)
            val v = s.metric(a.metric)
            Tiny(when {
                a.showPercent -> Achievements.percent(p) + " complete"
                a.metric == Metric.TotalKm -> "${km(v)} / ${km(a.threshold)}"
                a.metric == Metric.ClimbMeters -> "%,.0f / %,.0f m".format(v, a.threshold)
                else -> "%,.0f / %,.0f".format(v, a.threshold)
            })
        }
    }
}

// ───────────────────────────── Settings ─────────────────────────────

@Composable
fun SettingsScreen(nav: Nav, stepPermission: Boolean, requestPermission: () -> Unit) {
    val s = Game.session
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val set = s.data.settings
    var height by remember { mutableFloatStateOf((if (set.heightCm > 0) set.heightCm else 170).toFloat()) }
    var goal by remember { mutableFloatStateOf(set.dailyStepGoal / 1000f) }
    val sliderColors = SliderDefaults.colors(thumbColor = Sq.gold, activeTrackColor = Sq.gold, inactiveTrackColor = Sq.goldDim.copy(alpha = 0.4f), activeTickColor = Sq.ink.copy(alpha = 0.4f), inactiveTickColor = Sq.gold.copy(alpha = 0.3f))
    val switchColors = SwitchDefaults.colors(checkedThumbColor = Sq.gold, checkedTrackColor = Sq.goldDim)
    ScreenColumn {
        Btn("< Back", small = true, ghost = true) { nav.screen = if (s.battle != null) Screen.Battle else Screen.Home }
        Title("Settings")
        Panel {
            H2("Walking")
            Small(if (set.heightCm > 0) "Height ${set.heightCm} cm = stride %.2f m".format(set.strideMeters) else "Height not set: average stride %.2f m".format(set.strideMeters), Sq.text)
            Slider(height, { height = it }, valueRange = 120f..220f, steps = 99, onValueChangeFinished = { s.setHeight(height.toInt()) }, colors = sliderColors)
            Tiny("Stride is used for distance trophies (like the Moon). Steps → actions is always 1,000:1.")
            Spacer(Modifier.height(8.dp))
            Small("Daily goal: ${n(goal.toInt() * 1000)} steps", Sq.text)
            Slider(goal, { goal = it }, valueRange = 2f..30f, steps = 27, onValueChangeFinished = { s.setStepGoal(goal.toInt() * 1000) }, colors = sliderColors)
            Tiny("Your streak counts days you reach this goal.")
        }
        Panel {
            H2("Notifications & tracking")
            RowC {
                Small("Tell me when I've earned new actions", Sq.text, Modifier.weight(1f))
                Switch(set.notificationsEnabled, { set.notificationsEnabled = it; s.commit() }, colors = switchColors)
            }
            Small(if (stepPermission) "Step tracking is on. It keeps counting while the app is closed." else "Step tracking permission was denied.", if (stepPermission) Sq.good else Sq.danger)
            if (!stepPermission) Btn("Allow step tracking", primary = true, onClick = requestPermission)
            Tiny("Tip: add the StepQuest widget to your home screen to see steps and ready actions at a glance.")
        }
        Panel(inset = true) {
            H2("Developer")
            RowC {
                Small("Show debug step buttons", Sq.text, Modifier.weight(1f))
                Switch(set.debugTools, { set.debugTools = it; s.commit() }, colors = switchColors)
            }
            if (set.debugTools) RowC {
                Btn("+1,000", small = true) { s.addDebugSteps(1000) }
                Btn("+10,000", small = true) { s.addDebugSteps(10_000) }
                Btn("+100,000", small = true) { s.addDebugSteps(100_000) }
            }
        }
        Panel {
            H2("Start over")
            Small("Deletes your party, gear and trophies. Steps already walked are not re-counted.")
            Btn("Reset game", danger = true) { nav.confirm("Reset everything?", "This can't be undone.", "Reset", danger = true) { Game.reset(); nav.screen = Screen.Home } }
        }
        Panel(inset = true) {
            H2("About")
            Tiny("StepQuest 0.1 · every 1,000 steps is one action in battle. All art is generated procedurally in code.")
        }
    }
}

