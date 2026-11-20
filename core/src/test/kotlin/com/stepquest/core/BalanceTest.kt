package com.stepquest.core

import com.stepquest.core.combat.Battle
import com.stepquest.core.combat.BattleCommand
import com.stepquest.core.combat.BattleOutcome
import com.stepquest.core.data.GameData
import com.stepquest.core.economy.Inventory
import com.stepquest.core.party.HeroClass
import com.stepquest.core.party.Leveling
import com.stepquest.core.party.SkillEffect
import com.stepquest.core.party.TargetKind
import com.stepquest.core.save.SaveData
import com.stepquest.core.session.GameSession
import com.stepquest.core.world.WorldMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards pacing: one action is 1,000 real steps, so normal fights must be short. */
class BalanceTest {
    companion object {
        fun policy(b: Battle): BattleCommand {
            val actor = b.currentActor!!
            val cls = b.heroClasses[actor.heroIndex]
            if (cls == HeroClass.Priest && actor.mp >= 4) {
                b.heroes.filter { it.isAlive && it.hp < it.stats.maxHp / 2 }.minByOrNull { it.hp }?.let { return BattleCommand.skill("heal", it.id) }
            }
            val target = b.enemies.filter { it.isAlive }.minBy { it.hp }
            val skills = GameData.classDef(cls).startingSkills.map { GameData.skill(it) }
            skills.firstOrNull { it.effect == SkillEffect.Damage && it.target == TargetKind.SingleEnemy && actor.mp >= it.mpCost && b.isWeak(target, it.element) }
                ?.let { return BattleCommand.skill(it.id, target.id) }
            return BattleCommand.attack(target.id)
        }

        fun play(enemies: List<String>, level: Int, seed: Long): Pair<Int, BattleOutcome> {
            val party = GameData.startingParty()
            party.forEach { h -> while (h.level < level) h.gainExp(Leveling.expToNext(h.level)); h.fullRestore() }
            val inv = Inventory()
            val b = Battle.create(party, enemies, Rng(seed), null)
            var n = 0
            while (b.outcome == BattleOutcome.Ongoing && n < 500) { b.execute(policy(b), inv); n++ }
            return n to b.outcome
        }
    }

    @Test fun normalFightsShortAndWinnable() {
        for ((region, level) in listOf("meadow" to 1, "forest" to 5, "desert" to 10, "coast" to 16, "peaks" to 22, "ruins" to 28)) {
            val r = GameData.region(region)!!
            val results = (1..60).map { i -> play(WorldMap.rollEncounter(r, Rng(i * 7919L)), level, i.toLong()) }
            val avg = results.map { it.first }.average()
            val wins = results.count { it.second == BattleOutcome.Victory }
            println("$region L$level: avg %.1f commands, win $wins/60".format(avg))
            assertTrue(avg in 1.5..6.5, "$region avg $avg"); assertTrue(wins >= 54, "$region wins $wins")
        }
    }

    @Test fun bossesLongerButBeatable() {
        for ((region, level) in listOf("meadow" to 4, "forest" to 9, "desert" to 15, "coast" to 21, "peaks" to 27, "ruins" to 33)) {
            val boss = GameData.region(region)!!.bossId
            val results = (1..20).map { play(listOf(boss), level, it + 10L) }
            val avg = results.map { it.first }.average()
            val wins = results.count { it.second == BattleOutcome.Victory }
            println("$boss L$level: avg %.1f commands, win $wins/20".format(avg))
            assertTrue(avg in 8.0..55.0, "$boss avg $avg"); assertTrue(wins >= 12, "$boss wins $wins")
        }
    }

    @Test fun campaignAtEightThousandStepsADay() {
        val clock = FakeClock(START)
        val s = GameSession(SaveData.newGame(clock.now).also { it.rngState = 2024 }, clock)
        var day = 0; var lastRegion = -1
        while (day < 365 && s.data.bossesDefeated.size < GameData.regions.size) {
            day++
            clock.advanceDays(1)
            s.addDebugSteps(8000)
            if (s.battle == null && s.highestRegion != lastRegion) {
                lastRegion = s.highestRegion
                s.travelTo(GameData.regions[lastRegion].id)
                println("Day $day: entered ${GameData.regions[lastRegion].name} at L${s.party.maxOf { it.level }}")
            }
            var g = 0
            while (s.ledger.bankedActions > 0 && g++ < 200) {
                if (s.battle == null) {
                    if (s.party.any { it.hp < it.totalStats().maxHp / 2 } && s.inventory.coins >= s.innCost) s.restAtInn()
                    val r = s.currentRegion
                    val bossReady = r.bossId !in s.data.bossesDefeated && s.party.minOf { it.level } >= r.recommendedLevel + 3 && s.ledger.bankedActions >= 8
                    if (bossReady) s.restAtInn()
                    s.startBattle(bossReady)
                    continue
                }
                s.command(policy(s.battle!!))
            }
        }
        println("Finished on day $day at L${s.party.maxOf { it.level }}, ${s.data.counters.battlesWon} wins / ${s.data.counters.battlesLost} losses")
        assertEquals(GameData.regions.size, s.data.bossesDefeated.size)
        assertTrue(day in 90..240, "day $day")
    }
}
