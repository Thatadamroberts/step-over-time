package com.stepquest.core.meta

import com.stepquest.core.Rng
import com.stepquest.core.party.Leveling
import com.stepquest.core.steps.StepLedger
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

enum class Metric {
    TotalKm, TotalSteps, BestDaySteps, LongestStreak, ClimbMeters, BattlesWon, EnemiesDefeated,
    BossesDefeated, BreaksLanded, MaxHeroLevel, RegionsCleared, LegendariesOwned, ActionsEarned, CoinsEarned
}

enum class AchievementCategory { Distance, Steps, Climb, Streak, Adventure }

class AchievementDef(
    val id: String, val category: AchievementCategory, val name: String, val description: String,
    val metric: Metric, val threshold: Double, val rewardCoins: Int, val showPercent: Boolean = false,
)

class Landmark(val name: String, val km: Double, val verb: String)

object Achievements {
    const val MOON_KM = 384_400.0
    const val EARTH_KM = 40_075.0

    val landmarks = listOf(
        Landmark("a lap of a running track", 0.4, "lapped"),
        Landmark("the Golden Gate Bridge", 2.7, "crossed"),
        Landmark("Central Park end to end", 4.0, "walked"),
        Landmark("the English Channel", 33.3, "crossed"),
        Landmark("a marathon", 42.195, "completed"),
        Landmark("the edge of space (Kármán line)", 100.0, "reached"),
        Landmark("the orbit of the ISS", 408.0, "reached"),
        Landmark("the length of Great Britain", 1_407.0, "walked"),
        Landmark("Route 66", 3_940.0, "walked"),
        Landmark("the Nile", 6_650.0, "followed"),
        Landmark("the Great Wall of China", 21_196.0, "walked"),
        Landmark("the Earth's equator", EARTH_KM, "circled"),
        Landmark("the Moon", MOON_KM, "travelled to"),
    )

    val all: List<AchievementDef> = buildList {
        fun a(id: String, c: AchievementCategory, n: String, d: String, m: Metric, t: Double, coins: Int, pct: Boolean = false) =
            add(AchievementDef(id, c, n, d, m, t, coins, pct))
        val D = AchievementCategory.Distance
        a("km_1", D, "First Kilometre", "Walk 1 km.", Metric.TotalKm, 1.0, 20)
        a("km_5", D, "Parkrunner", "Walk 5 km.", Metric.TotalKm, 5.0, 40)
        a("km_10", D, "Ten Kay", "Walk 10 km.", Metric.TotalKm, 10.0, 60)
        a("km_half", D, "Half Marathon", "Walk 21.1 km.", Metric.TotalKm, 21.0975, 100)
        a("km_channel", D, "Channel Crosser", "Walk the width of the English Channel (33.3 km).", Metric.TotalKm, 33.3, 120)
        a("km_marathon", D, "Marathoner", "Walk a full marathon distance (42.195 km).", Metric.TotalKm, 42.195, 150)
        a("km_100", D, "Edge of Space", "Walk 100 km — the height of the Kármán line.", Metric.TotalKm, 100.0, 250)
        a("km_iss", D, "Orbital", "Walk 408 km — as high as the ISS orbits.", Metric.TotalKm, 408.0, 500)
        a("km_1000", D, "Thousand-Kilometre Pilgrim", "Walk 1,000 km.", Metric.TotalKm, 1_000.0, 800)
        a("km_lejog", D, "End to End", "Walk the length of Great Britain (1,407 km).", Metric.TotalKm, 1_407.0, 1_000)
        a("km_route66", D, "Get Your Kicks", "Walk the length of Route 66 (3,940 km).", Metric.TotalKm, 3_940.0, 2_000)
        a("km_wall", D, "Wall Walker", "Walk the length of the Great Wall (21,196 km).", Metric.TotalKm, 21_196.0, 5_000)
        a("km_earth", D, "Circumnavigator", "Walk the Earth's circumference (40,075 km).", Metric.TotalKm, EARTH_KM, 10_000)
        a("moon_1", D, "Moonward I", "Walk 1% of the way to the Moon.", Metric.TotalKm, MOON_KM * 0.01, 1_500, true)
        a("moon_5", D, "Moonward II", "Walk 5% of the way to the Moon.", Metric.TotalKm, MOON_KM * 0.05, 4_000, true)
        a("moon_10", D, "Moonward III", "Walk 10% of the way to the Moon.", Metric.TotalKm, MOON_KM * 0.10, 8_000, true)
        a("moon_25", D, "Moonward IV", "Walk a quarter of the way to the Moon.", Metric.TotalKm, MOON_KM * 0.25, 15_000, true)
        a("moon_100", D, "Moonwalker", "Walk the full 384,400 km to the Moon.", Metric.TotalKm, MOON_KM, 100_000, true)
        val S = AchievementCategory.Steps
        a("day_10k", S, "Ten Thousand", "Walk 10,000 steps in one day.", Metric.BestDaySteps, 10_000.0, 50)
        a("day_20k", S, "Double Time", "Walk 20,000 steps in one day.", Metric.BestDaySteps, 20_000.0, 150)
        a("day_30k", S, "Ultra Day", "Walk 30,000 steps in one day.", Metric.BestDaySteps, 30_000.0, 300)
        a("steps_100k", S, "Hundred Thousand", "Walk 100,000 steps.", Metric.TotalSteps, 100_000.0, 100)
        a("steps_1m", S, "Millionaire", "Walk 1,000,000 steps.", Metric.TotalSteps, 1_000_000.0, 1_000)
        a("steps_10m", S, "Ten Million", "Walk 10,000,000 steps.", Metric.TotalSteps, 10_000_000.0, 10_000)
        a("actions_100", S, "Century of Blows", "Earn 100 combat actions.", Metric.ActionsEarned, 100.0, 100)
        val C = AchievementCategory.Climb
        a("climb_eiffel", C, "Eiffel Tower", "Climb the equivalent of the Eiffel Tower (330 m).", Metric.ClimbMeters, 330.0, 40)
        a("climb_burj", C, "Burj Khalifa", "Climb the equivalent of the Burj Khalifa (828 m).", Metric.ClimbMeters, 828.0, 80)
        a("climb_kili", C, "Kilimanjaro", "Climb the equivalent of Kilimanjaro (5,895 m).", Metric.ClimbMeters, 5_895.0, 300)
        a("climb_everest", C, "Everest", "Climb the equivalent of Everest (8,849 m).", Metric.ClimbMeters, 8_849.0, 500)
        a("climb_cruise", C, "Cruising Altitude", "Climb the equivalent of a jet's cruising altitude (11,000 m).", Metric.ClimbMeters, 11_000.0, 700)
        val K = AchievementCategory.Streak
        a("streak_3", K, "Habit Forming", "Hit your step goal 3 days in a row.", Metric.LongestStreak, 3.0, 50)
        a("streak_7", K, "Week Warrior", "Hit your step goal 7 days in a row.", Metric.LongestStreak, 7.0, 150)
        a("streak_30", K, "Monthly March", "Hit your step goal 30 days in a row.", Metric.LongestStreak, 30.0, 600)
        a("streak_100", K, "Centurion", "Hit your step goal 100 days in a row.", Metric.LongestStreak, 100.0, 2_000)
        a("streak_365", K, "Year of Walking", "Hit your step goal 365 days in a row.", Metric.LongestStreak, 365.0, 10_000)
        val V = AchievementCategory.Adventure
        a("win_1", V, "First Blood", "Win your first battle.", Metric.BattlesWon, 1.0, 20)
        a("win_100", V, "Seasoned", "Win 100 battles.", Metric.BattlesWon, 100.0, 300)
        a("kills_1000", V, "Monster Bane", "Defeat 1,000 enemies.", Metric.EnemiesDefeated, 1_000.0, 1_000)
        a("breaks_50", V, "Shieldbreaker", "Break enemy shields 50 times.", Metric.BreaksLanded, 50.0, 200)
        a("boss_1", V, "Giant Slayer", "Defeat your first boss.", Metric.BossesDefeated, 1.0, 150)
        a("regions_all", V, "Skyreach Conqueror", "Defeat every region's boss.", Metric.RegionsCleared, 6.0, 5_000)
        a("level_10", V, "Journeyman", "Reach level 10 with any hero.", Metric.MaxHeroLevel, 10.0, 100)
        a("level_25", V, "Veteran", "Reach level 25 with any hero.", Metric.MaxHeroLevel, 25.0, 500)
        a("level_50", V, "Living Legend", "Reach the level cap with any hero.", Metric.MaxHeroLevel, Leveling.MAX_LEVEL.toDouble(), 2_000)
        a("legend_gear", V, "Legendary", "Own a legendary item.", Metric.LegendariesOwned, 1.0, 500)
        a("coins_10k", V, "Treasure Hoarder", "Earn 10,000 coins in total.", Metric.CoinsEarned, 10_000.0, 500)
    }

    fun progress(a: AchievementDef, metric: (Metric) -> Double) = (metric(a.metric) / a.threshold).coerceIn(0.0, 1.0)

    /** Unlocks every newly met achievement; returns the new ones. */
    fun evaluate(metric: (Metric) -> Double, unlocked: MutableMap<String, Long>, nowMillis: Long): List<AchievementDef> =
        all.filter { it.id !in unlocked && metric(it.metric) >= it.threshold }.onEach { unlocked[it.id] = nowMillis }

    fun comparisons(totalKm: Double, climbMeters: Double): List<String> = buildList {
        add("You've walked ${percent(totalKm / MOON_KM)} of the way to the Moon.")
        add("You've walked ${percent(totalKm / EARTH_KM)} of the way around the Earth.")
        val passed = landmarks.lastOrNull { totalKm >= it.km }
        val next = landmarks.filter { totalKm < it.km }.minByOrNull { it.km }
        if (passed != null) {
            val times = totalKm / passed.km
            add(if (times >= 2) "That's ${passed.name} %.1f times over.".format(times) else "You've ${passed.verb} ${passed.name}.")
        }
        if (next != null) add("%,.1f km until you've ${next.verb} ${next.name}.".format(next.km - totalKm))
        add("In stairs, you've climbed %,.0f m — %.2f× Everest.".format(climbMeters, climbMeters / 8_849))
    }

    fun percent(f: Double): String {
        val p = f * 100
        return when {
            p >= 10 -> "%.1f%%".format(p)
            p >= 0.1 -> "%.2f%%".format(p)
            else -> "%.4f%%".format(p)
        }.replace(Regex("\\.?0+%$"), "%")
    }
}

/**
 * Streaks are derived from the daily step history. A day counts when steps ≥ goal. An unfinished
 * today never breaks the streak. One missed day per calendar week (Mon–Sun) is forgiven.
 */
object Streaks {
    const val DEFAULT_GOAL = 5000
    const val BONUS_PER_DAY = 0.025
    const val MAX_BONUS_DAYS = 20

    data class Result(val current: Int, val shieldsUsed: Int, val todayDone: Boolean)

    fun compute(daily: Map<String, Int>, today: LocalDate, goal: Int, lookback: Int = 4000): Result {
        val todayDone = get(daily, today) >= goal
        var current = if (todayDone) 1 else 0
        var shields = 0
        val shielded = HashSet<Long>()
        var day = today.minusDays(1)
        repeat(lookback) {
            if (get(daily, day) >= goal) { current++; day = day.minusDays(1); return@repeat }
            val week = weekKey(day)
            if (week !in shielded && get(daily, day.minusDays(1)) >= goal && current > 0) {
                shielded += week; shields++; day = day.minusDays(1); return@repeat
            }
            return Result(current, shields, todayDone)
        }
        return Result(current, shields, todayDone)
    }

    fun longest(daily: Map<String, Int>, goal: Int): Int {
        val days = daily.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        if (days.isEmpty()) return 0
        var best = 0; var run = 0
        val shielded = HashSet<Long>()
        var d = days.first()
        while (!d.isAfter(days.last())) {
            if (get(daily, d) >= goal) { run++; best = max(best, run) }
            else if (run > 0 && weekKey(d) !in shielded && get(daily, d.plusDays(1)) >= goal) shielded += weekKey(d)
            else { run = 0; shielded.clear() }
            d = d.plusDays(1)
        }
        return best
    }

    fun coinMultiplier(streak: Int) = 1.0 + min(streak, MAX_BONUS_DAYS) * BONUS_PER_DAY

    private fun get(d: Map<String, Int>, day: LocalDate) = d[StepLedger.dayKey(day)] ?: 0

    /** Monday-based week id. */
    fun weekKey(day: LocalDate): Long {
        val monday = day.minusDays((day.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        return ChronoUnit.DAYS.between(LocalDate.of(1970, 1, 5), monday) / 7
    }
}

enum class QuestMetric { Steps, BattlesWon, SkillsUsed, EnemiesDefeated, BreaksLanded, ItemsBought }
enum class QuestPeriod { Daily, Weekly }

class QuestDef(
    val id: String, val period: QuestPeriod, val metric: QuestMetric, val target: Int,
    val rewardCoins: Int, private val textFormat: String, val rewardItem: String? = null,
) {
    val text: String get() = textFormat.replace("{n}", "%,d".format(target))
}

@Serializable
class QuestState(
    var dailyKey: String = "",
    var weeklyKey: String = "",
    var activeDaily: MutableList<String> = mutableListOf(),
    var activeWeekly: MutableList<String> = mutableListOf(),
    var dailyCounters: MutableMap<String, Int> = mutableMapOf(),
    var weeklyCounters: MutableMap<String, Int> = mutableMapOf(),
    var claimed: MutableList<String> = mutableListOf(),
)

object Quests {
    const val DAILY_COUNT = 3
    const val WEEKLY_COUNT = 2

    val pool = listOf(
        QuestDef("d_steps_5k", QuestPeriod.Daily, QuestMetric.Steps, 5_000, 40, "Walk {n} steps today"),
        QuestDef("d_steps_8k", QuestPeriod.Daily, QuestMetric.Steps, 8_000, 70, "Walk {n} steps today", "tonic"),
        QuestDef("d_steps_12k", QuestPeriod.Daily, QuestMetric.Steps, 12_000, 120, "Walk {n} steps today", "ether"),
        QuestDef("d_win_2", QuestPeriod.Daily, QuestMetric.BattlesWon, 2, 50, "Win {n} battles"),
        QuestDef("d_skills_3", QuestPeriod.Daily, QuestMetric.SkillsUsed, 3, 40, "Use {n} skills in battle"),
        QuestDef("d_break_2", QuestPeriod.Daily, QuestMetric.BreaksLanded, 2, 60, "Break {n} enemy shields"),
        QuestDef("d_kill_5", QuestPeriod.Daily, QuestMetric.EnemiesDefeated, 5, 50, "Defeat {n} enemies"),
        QuestDef("w_steps_50k", QuestPeriod.Weekly, QuestMetric.Steps, 50_000, 400, "Walk {n} steps this week", "ember_feather"),
        QuestDef("w_steps_70k", QuestPeriod.Weekly, QuestMetric.Steps, 70_000, 600, "Walk {n} steps this week", "hi_tonic"),
        QuestDef("w_win_15", QuestPeriod.Weekly, QuestMetric.BattlesWon, 15, 350, "Win {n} battles this week"),
        QuestDef("w_break_15", QuestPeriod.Weekly, QuestMetric.BreaksLanded, 15, 350, "Break {n} shields this week"),
        QuestDef("w_kill_40", QuestPeriod.Weekly, QuestMetric.EnemiesDefeated, 40, 400, "Defeat {n} enemies this week"),
    )

    fun def(id: String) = pool.firstOrNull { it.id == id }

    /** Rolls over to a new day/week if needed, picking quests deterministically from the date. */
    fun refresh(s: QuestState, today: LocalDate) {
        val dk = StepLedger.dayKey(today)
        val wk = "W" + Streaks.weekKey(today)
        if (s.dailyKey != dk) {
            s.dailyKey = dk
            s.dailyCounters.clear()
            s.activeDaily = pick(QuestPeriod.Daily, DAILY_COUNT, today.toEpochDay() * 31 + 7)
            s.claimed.removeAll { def(it)?.period == QuestPeriod.Daily }
        }
        if (s.weeklyKey != wk) {
            s.weeklyKey = wk
            s.weeklyCounters.clear()
            s.activeWeekly = pick(QuestPeriod.Weekly, WEEKLY_COUNT, Streaks.weekKey(today) * 7919 + 3)
            s.claimed.removeAll { def(it)?.period == QuestPeriod.Weekly }
        }
    }

    private fun pick(period: QuestPeriod, count: Int, seed: Long): MutableList<String> {
        val rng = Rng(seed)
        val c = pool.filter { it.period == period }
        val steps = c.filter { it.metric == QuestMetric.Steps }
        val picked = mutableListOf(steps[rng.range(0, steps.size)].id) // walking is the point
        val others = c.filter { it.metric != QuestMetric.Steps }.toMutableList()
        while (picked.size < count && others.isNotEmpty()) picked += others.removeAt(rng.range(0, others.size)).id
        return picked
    }

    fun record(s: QuestState, m: QuestMetric, amount: Int) {
        if (amount <= 0) return
        s.dailyCounters[m.name] = (s.dailyCounters[m.name] ?: 0) + amount
        s.weeklyCounters[m.name] = (s.weeklyCounters[m.name] ?: 0) + amount
    }

    fun progressOf(s: QuestState, q: QuestDef) =
        (if (q.period == QuestPeriod.Daily) s.dailyCounters else s.weeklyCounters)[q.metric.name] ?: 0

    fun active(s: QuestState) = (s.activeDaily + s.activeWeekly).mapNotNull { def(it) }
}
