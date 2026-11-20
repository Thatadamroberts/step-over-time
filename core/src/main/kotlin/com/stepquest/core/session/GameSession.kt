package com.stepquest.core.session

import com.stepquest.core.Rng
import com.stepquest.core.combat.Battle
import com.stepquest.core.combat.BattleCommand
import com.stepquest.core.combat.BattleEvent
import com.stepquest.core.combat.BattleEventKind
import com.stepquest.core.combat.BattleOutcome
import com.stepquest.core.combat.CommandKind
import com.stepquest.core.data.GameData
import com.stepquest.core.economy.ConsumableEffect
import com.stepquest.core.economy.EquipSlot
import com.stepquest.core.economy.Rarity
import com.stepquest.core.economy.Shop
import com.stepquest.core.meta.AchievementDef
import com.stepquest.core.meta.Achievements
import com.stepquest.core.meta.Metric
import com.stepquest.core.meta.QuestDef
import com.stepquest.core.meta.QuestMetric
import com.stepquest.core.meta.QuestPeriod
import com.stepquest.core.meta.Quests
import com.stepquest.core.meta.Streaks
import com.stepquest.core.save.SaveData
import com.stepquest.core.steps.Clock
import com.stepquest.core.steps.DistanceCalc
import com.stepquest.core.steps.StepLedger
import com.stepquest.core.steps.StepSource
import com.stepquest.core.world.RegionDef
import com.stepquest.core.world.WorldMap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BattleSummary(
    val outcome: BattleOutcome,
    var exp: Int = 0,
    var coins: Int = 0,
    var coinMultiplier: Double = 1.0,
    val drops: MutableList<String> = mutableListOf(),
    val levelsGained: MutableMap<String, Int> = mutableMapOf(),
    var coinsLost: Int = 0,
    var bossDefeated: Boolean = false,
    var regionHint: String? = null,
)

/**
 * The single entry point the UI talks to. Owns the save data and enforces the rules:
 * one banked action per hero command, rewards, achievements and quests.
 */
class GameSession(val data: SaveData, private val clock: Clock) {
    private val rng = Rng(if (data.rngState == 0L) clock.nowMillis() else data.rngState)

    var onChanged: (() -> Unit)? = null
    var onActionsEarned: ((Int) -> Unit)? = null
    var onAchievements: ((List<AchievementDef>) -> Unit)? = null
    /** Locked actions were lost because a day ended below 80% of the goal: (count, day). */
    var onActionsForfeited: ((Int, String) -> Unit)? = null

    var lastSummary: BattleSummary? = null
        private set

    init {
        Quests.refresh(data.quests, clock.today)
        ledger.unlockThreshold = unlockThreshold(data.settings.dailyStepGoal)
        ledger.rollover(clock.today)
    }

    // ───────── Daily 80% rule ─────────

    /** Actions earned today, waiting for today's steps to reach 80% of the goal. */
    val lockedActions get() = if (ledger.lockedDay == StepLedger.dayKey(clock.today)) ledger.lockedActions else 0
    val stepsToUnlock get() = ledger.stepsToUnlock(clock.today)
    val todayUnlocked get() = ledger.isUnlocked(clock.today)

    /** Rolls into a new day; reports locked actions lost because yesterday fell short. Cheap; call often. */
    fun checkDay() {
        ledger.rollover(clock.today)
        if (ledger.forfeitedActions > 0) {
            val n = ledger.forfeitedActions; val day = ledger.forfeitedDay
            ledger.forfeitedActions = 0
            onActionsForfeited?.invoke(n, day)
            commit()
        }
    }

    val ledger get() = data.ledger
    val party get() = data.party
    val inventory get() = data.inventory
    val battle get() = data.activeBattle
    val stride get() = data.settings.strideMeters
    val totalKm get() = DistanceCalc.kilometers(ledger.totalSteps, stride)
    val todaySteps get() = ledger.stepsOn(clock.today)
    val weekSteps: Int
        get() {
            val today = clock.today
            return (0 until today.dayOfWeek.value).sumOf { ledger.stepsOn(today.minusDays(it.toLong())) }
        }
    val streak get() = Streaks.compute(ledger.dailySteps, clock.today, data.settings.dailyStepGoal)
    val coinMultiplier get() = Streaks.coinMultiplier(streak.current)

    // ───────── Steps ─────────

    /** Pulls steps since the last sync, split at local midnights so each day's bucket is right. */
    fun syncSteps(source: StepSource): Int {
        checkDay()
        val now = clock.nowMillis()
        var cursor = max(ledger.lastSyncMillis, now - MAX_CATCH_UP_DAYS * 86_400_000L)
        if (cursor >= now) return 0
        var earned = 0
        val before = ledger.totalSteps
        while (cursor < now) {
            val day = clock.localDate(cursor)
            var end = min(clock.startOfDayMillis(day.plusDays(1)), now)
            if (end <= cursor) end = now
            val steps = source.readSteps(cursor, end)
            if (steps < 0) break
            if (steps > 0) earned += ledger.addSteps(steps, cursor, end, day)
            else ledger.lastSyncMillis = max(ledger.lastSyncMillis, end)
            cursor = end
        }
        if (ledger.totalSteps != before) afterSteps(earned)
        return earned
    }

    fun addDebugSteps(steps: Int): Int {
        val now = clock.nowMillis()
        val minutes = steps / StepLedger.MAX_STEPS_PER_MINUTE + 1
        val earned = ledger.addSteps(steps.toLong(), now - minutes * 60_000L, max(now, ledger.lastSyncMillis + 1), clock.today)
        afterSteps(earned)
        return earned
    }

    private fun afterSteps(earned: Int) {
        Quests.refresh(data.quests, clock.today)
        if (ledger.forfeitedActions > 0) { val n = ledger.forfeitedActions; ledger.forfeitedActions = 0; onActionsForfeited?.invoke(n, ledger.forfeitedDay) }
        if (earned > 0) onActionsEarned?.invoke(earned)
        checkAchievements()
        commit()
    }

    // ───────── World ─────────

    fun isRegionUnlocked(r: RegionDef) = WorldMap.isUnlocked(r, totalKm, data.bossesDefeated)
    val highestRegion get() = WorldMap.highestUnlocked(totalKm, data.bossesDefeated)
    val currentRegion get() = GameData.region(data.currentRegionId) ?: GameData.regions[0]

    fun travelTo(id: String): Boolean {
        val r = GameData.region(id)
        if (battle != null || r == null || !isRegionUnlocked(r)) return false
        data.currentRegionId = id
        commit()
        return true
    }

    // ───────── Battle ─────────

    fun canStartBattle(boss: Boolean): String? = when {
        battle != null -> "A battle is already in progress."
        party.none { it.hp > 0 } -> "Your party needs to rest at the inn."
        boss && currentRegion.bossId in data.bossesDefeated -> "This region's boss is already defeated."
        else -> null
    }

    fun startBattle(boss: Boolean = false): Battle {
        canStartBattle(boss)?.let { throw IllegalStateException(it) }
        val r = currentRegion
        val enemies = if (boss) listOf(r.bossId) else WorldMap.rollEncounter(r, rng)
        data.activeBattle = Battle.create(party, enemies, Rng(rng.nextLong()), r.id)
        lastSummary = null
        finishIfOver()
        commit()
        return data.activeBattle!!
    }

    fun validateCommand(cmd: BattleCommand): String? {
        val b = battle ?: return "No battle."
        if (ledger.bankedActions <= 0) return "No actions left — go for a walk!"
        return b.validate(cmd, inventory)
    }

    /** Spends one banked action and performs the command. */
    fun command(cmd: BattleCommand): List<BattleEvent> {
        validateCommand(cmd)?.let { throw IllegalStateException(it) }
        ledger.trySpendAction()
        val events = battle!!.execute(cmd, inventory)
        if (cmd.kind == CommandKind.Skill) { data.counters.skillsUsed++; Quests.record(data.quests, QuestMetric.SkillsUsed, 1) }
        val breaks = events.count { it.kind == BattleEventKind.Break }
        data.counters.breaksLanded += breaks
        Quests.record(data.quests, QuestMetric.BreaksLanded, breaks)
        finishIfOver()
        commit()
        return events
    }

    private fun finishIfOver() {
        val b = battle ?: return
        if (b.outcome == BattleOutcome.Ongoing) return
        b.writeBack(party)
        val s = BattleSummary(b.outcome)
        val killed = b.enemies.count { !it.isAlive }
        data.counters.enemiesDefeated += killed
        Quests.record(data.quests, QuestMetric.EnemiesDefeated, killed)
        when (b.outcome) {
            BattleOutcome.Victory -> {
                data.counters.battlesWon++
                Quests.record(data.quests, QuestMetric.BattlesWon, 1)
                s.coinMultiplier = coinMultiplier
                s.coins = (b.rewardCoins * s.coinMultiplier).roundToInt()
                s.exp = b.rewardExp
                inventory.coins += s.coins
                data.counters.coinsEarned += s.coins
                for (h in party) {
                    val lv = h.gainExp(if (h.hp > 0) s.exp else s.exp / 2) // the fallen learn at half rate
                    if (lv > 0) s.levelsGained[h.name] = lv
                }
                b.drops.forEach { inventory.add(it); s.drops += it }
                if (b.isBoss) {
                    val bossId = b.enemies.first { GameData.enemy(it.enemyId!!).isBoss }.enemyId!!
                    if (bossId !in data.bossesDefeated) data.bossesDefeated += bossId
                    data.counters.bossesDefeated++
                    s.bossDefeated = true
                    val region = GameData.regions.firstOrNull { it.bossId == bossId }
                    if (region != null && region.index + 1 < GameData.regions.size) {
                        val next = GameData.regions[region.index + 1]
                        s.regionHint = if (isRegionUnlocked(next)) "${next.name} is now open!"
                        else "${next.name} opens after %.0f km walked.".format(next.unlockKm)
                    }
                }
            }
            BattleOutcome.Defeat -> {
                data.counters.battlesLost++
                s.coinsLost = floor(inventory.coins * DEFEAT_COIN_PENALTY).toInt()
                inventory.coins -= s.coinsLost
                party.forEach { it.fullRestore(); it.hp = max(1, it.totalStats().maxHp / 2) } // carried back to town
            }
            else -> {}
        }
        data.activeBattle = null
        lastSummary = s
        checkAchievements()
    }

    fun dismissSummary() { lastSummary = null }

    // ───────── Town ─────────

    fun buy(id: String): Boolean {
        val item = GameData.itemOrNull(id) ?: return false
        if (item.shopTier > highestRegion || !Shop.buy(inventory, item)) return false
        Quests.record(data.quests, QuestMetric.ItemsBought, 1)
        checkAchievements(); commit()
        return true
    }

    fun sell(id: String): Boolean {
        val item = GameData.itemOrNull(id) ?: return false
        if (!Shop.sell(inventory, item)) return false
        commit()
        return true
    }

    /** Equips from the inventory, returning the previous item to it. */
    fun equip(heroIndex: Int, id: String): Boolean {
        val hero = party.getOrNull(heroIndex) ?: return false
        val item = GameData.itemOrNull(id) ?: return false
        if (battle != null || !item.canEquip(hero.heroClass) || !inventory.remove(id)) return false
        val old = when (item.slot) { EquipSlot.Weapon -> hero.weaponId; EquipSlot.Armor -> hero.armorId; else -> hero.accessoryId }
        old?.let { inventory.add(it) }
        hero.equip(item)
        checkAchievements(); commit()
        return true
    }

    fun useItem(id: String, heroIndex: Int): Boolean {
        val item = GameData.itemOrNull(id) ?: return false
        val h = party.getOrNull(heroIndex) ?: return false
        if (battle != null || !item.isConsumable) return false
        val s = h.totalStats()
        when (item.use) {
            ConsumableEffect.HealHp -> { if (h.hp <= 0 || h.hp >= s.maxHp) return false; h.hp = min(s.maxHp, h.hp + item.useAmount) }
            ConsumableEffect.HealMp -> { if (h.mp >= s.maxMp) return false; h.mp = min(s.maxMp, h.mp + item.useAmount) }
            ConsumableEffect.Revive -> { if (h.hp > 0) return false; h.hp = max(1, s.maxHp * item.useAmount / 100) }
            else -> return false
        }
        inventory.remove(id)
        commit()
        return true
    }

    val innCost get() = Shop.innCost(party)
    fun restAtInn(): Boolean {
        if (battle != null || !Shop.rest(inventory, party)) return false
        commit(); return true
    }

    fun learnNode(heroIndex: Int, nodeId: String): Boolean {
        if (party.getOrNull(heroIndex)?.unlock(nodeId) != true) return false
        commit(); return true
    }

    // ───────── Quests ─────────

    val activeQuests: List<QuestDef> get() { Quests.refresh(data.quests, clock.today); return Quests.active(data.quests) }

    fun questProgress(q: QuestDef) =
        if (q.metric == QuestMetric.Steps) (if (q.period == QuestPeriod.Daily) todaySteps else weekSteps)
        else Quests.progressOf(data.quests, q)

    fun isClaimed(q: QuestDef) = q.id in data.quests.claimed

    fun claimQuest(id: String): Boolean {
        val q = activeQuests.firstOrNull { it.id == id } ?: return false
        if (isClaimed(q) || questProgress(q) < q.target) return false
        data.quests.claimed += q.id
        inventory.coins += q.rewardCoins
        data.counters.coinsEarned += q.rewardCoins
        q.rewardItem?.let { inventory.add(it) }
        checkAchievements(); commit()
        return true
    }

    // ───────── Achievements ─────────

    fun metric(m: Metric): Double = when (m) {
        Metric.TotalKm -> totalKm
        Metric.TotalSteps -> ledger.totalSteps.toDouble()
        Metric.BestDaySteps -> (ledger.dailySteps.values.maxOrNull() ?: 0).toDouble()
        Metric.LongestStreak -> max(data.counters.longestStreak, streak.current).toDouble()
        Metric.ClimbMeters -> DistanceCalc.climbMeters(ledger.totalSteps)
        Metric.BattlesWon -> data.counters.battlesWon.toDouble()
        Metric.EnemiesDefeated -> data.counters.enemiesDefeated.toDouble()
        Metric.BossesDefeated -> data.counters.bossesDefeated.toDouble()
        Metric.BreaksLanded -> data.counters.breaksLanded.toDouble()
        Metric.MaxHeroLevel -> (party.maxOfOrNull { it.level } ?: 0).toDouble()
        Metric.RegionsCleared -> GameData.regions.count { it.bossId in data.bossesDefeated }.toDouble()
        Metric.LegendariesOwned -> (inventory.items.keys + party.flatMap { listOfNotNull(it.weaponId, it.armorId, it.accessoryId) })
            .toSet().count { GameData.itemOrNull(it)?.rarity == Rarity.Legendary }.toDouble()
        Metric.ActionsEarned -> ledger.totalActionsEarned.toDouble()
        Metric.CoinsEarned -> data.counters.coinsEarned.toDouble()
    }

    fun achievementProgress(a: AchievementDef) = Achievements.progress(a, ::metric)
    fun isUnlocked(a: AchievementDef) = a.id in data.achievements
    fun comparisons() = Achievements.comparisons(totalKm, DistanceCalc.climbMeters(ledger.totalSteps))

    private fun checkAchievements() {
        data.counters.longestStreak = maxOf(data.counters.longestStreak, streak.current, Streaks.longest(ledger.dailySteps, data.settings.dailyStepGoal))
        val newly = Achievements.evaluate(::metric, data.achievements, clock.nowMillis())
        if (newly.isEmpty()) return
        newly.forEach { inventory.coins += it.rewardCoins }
        onAchievements?.invoke(newly)
    }

    // ───────── Settings ─────────

    fun setHeight(cm: Int) {
        data.settings.heightCm = cm
        data.settings.strideMeters = DistanceCalc.strideFromHeightCm(cm)
        checkAchievements(); commit()
    }

    fun setStepGoal(goal: Int) {
        data.settings.dailyStepGoal = goal.coerceIn(1000, 50_000)
        ledger.unlockThreshold = unlockThreshold(data.settings.dailyStepGoal)
        val unlocked = ledger.recheck(clock.today) // lowering the goal can unlock today's actions
        if (unlocked > 0) onActionsEarned?.invoke(unlocked)
        commit()
    }

    fun commit() {
        data.rngState = rng.state
        onChanged?.invoke()
    }

    companion object {
        const val MAX_CATCH_UP_DAYS = 30L
        const val DEFEAT_COIN_PENALTY = 0.10
        /** Actions only unlock on days you walk at least this share of your daily goal. */
        const val UNLOCK_FRACTION = 0.8
        fun unlockThreshold(goal: Int) = kotlin.math.ceil(goal * UNLOCK_FRACTION).toInt()
    }
}
