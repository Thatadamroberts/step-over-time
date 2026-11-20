package com.stepquest.core

import com.stepquest.core.combat.Battle
import com.stepquest.core.combat.BattleCommand
import com.stepquest.core.combat.BattleEventKind
import com.stepquest.core.combat.BattleOutcome
import com.stepquest.core.combat.Damage
import com.stepquest.core.combat.StatusKind
import com.stepquest.core.data.GameData
import com.stepquest.core.economy.Inventory
import com.stepquest.core.meta.Achievements
import com.stepquest.core.meta.QuestMetric
import com.stepquest.core.meta.QuestPeriod
import com.stepquest.core.meta.QuestState
import com.stepquest.core.meta.Quests
import com.stepquest.core.meta.Streaks
import com.stepquest.core.party.Element
import com.stepquest.core.party.Hero
import com.stepquest.core.party.HeroClass
import com.stepquest.core.party.Leveling
import com.stepquest.core.save.SaveCodec
import com.stepquest.core.save.SaveData
import com.stepquest.core.session.GameSession
import com.stepquest.core.steps.DistanceCalc
import com.stepquest.core.steps.StepLedger
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepLedgerTest {
    private val day = LocalDate.of(2026, 3, 2)

    @Test fun everyThousandStepsIsOneAction() {
        val l = StepLedger()
        assertEquals(0, l.addSteps(999, START, START + 3_600_000, day))
        assertEquals(1, l.addSteps(1, START + 3_600_000, START + 7_200_000, day))
        assertEquals(2, l.addSteps(2500, START + 7_200_000, START + 10_800_000, day))
        assertEquals(3, l.bankedActions); assertEquals(500, l.stepRemainder); assertEquals(500, l.stepsToNextAction)
    }

    @Test fun idempotentAndCapped() {
        val l = StepLedger()
        l.addSteps(5000, START, START + 3_600_000, day)
        assertEquals(0, l.addSteps(5000, START, START + 3_600_000, day))
        val c = StepLedger()
        c.addSteps(1_000_000, START, START + 600_000, day)
        assertEquals(10L * StepLedger.MAX_STEPS_PER_MINUTE, c.totalSteps)
    }

    @Test fun rawSensorRebootSafe() {
        val l = StepLedger()
        l.addRawSensorReading(50_000, START, day)
        l.addRawSensorReading(51_200, START, day)
        l.addRawSensorReading(300, START, day)
        assertEquals(1500, l.totalSteps)
    }

    @Test fun stride() {
        assertEquals(0.747, DistanceCalc.strideFromHeightCm(180), 1e-9)
        assertEquals(DistanceCalc.DEFAULT_STRIDE_METERS, DistanceCalc.strideFromHeightCm(0))
    }
}

class SessionTest {
    private fun newSession(offset: Int = 0, goal: Int = 1000): Pair<GameSession, FakeClock> {
        val c = FakeClock(START, offset)
        val d = SaveData.newGame(c.now).also { it.rngState = 12345; it.settings.dailyStepGoal = goal }
        return GameSession(d, c) to c
    }

    // ───────── Daily 80% rule ─────────

    @Test fun actionsStayLockedUntilEightyPercentOfGoal() {
        val (s, _) = newSession(goal = 5000) // threshold 4,000
        s.addDebugSteps(3000)
        assertEquals(0, s.ledger.bankedActions)
        assertEquals(3, s.lockedActions)
        assertEquals(1000, s.stepsToUnlock)
        assertFalse(s.todayUnlocked)
        s.addDebugSteps(1000) // reaches 4,000: everything unlocks
        assertEquals(4, s.ledger.bankedActions)
        assertEquals(0, s.lockedActions)
        s.addDebugSteps(1000) // past the threshold, new actions are usable immediately
        assertEquals(5, s.ledger.bankedActions)
    }

    @Test fun lockedActionsAreLostWhenTheDayEndsShort() {
        val (s, c) = newSession(goal = 5000)
        var lost = 0
        s.onActionsForfeited = { n, _ -> lost += n }
        s.addDebugSteps(3500)
        assertEquals(3, s.lockedActions)
        c.advanceDays(1)
        s.checkDay()
        assertEquals(3, lost)
        assertEquals(0, s.lockedActions)
        assertEquals(0, s.ledger.bankedActions)
        // The new day starts fresh: its own 80% applies.
        s.addDebugSteps(4000)
        assertEquals(4, s.ledger.bankedActions)
        assertEquals(500, s.ledger.stepRemainder) // partial progress toward an action still carries over
    }

    @Test fun catchUpAppliesTheRuleDayByDay() {
        val (s, c) = newSession(goal = 5000)
        val src = FakeSteps()
        src.add(START + 3_600_000, 2000)               // day 1: only 2,000 steps -> locked, then lost
        src.add(START + 86_400_000L + 3_600_000, 6000)  // day 2: 6,000 steps -> unlocked
        c.advanceDays(2)
        s.syncSteps(src)
        assertEquals(6, s.ledger.bankedActions)
        assertEquals(2L, s.ledger.totalActionsForfeited)
    }

    @Test fun loweringTheGoalCanUnlockToday() {
        val (s, _) = newSession(goal = 10_000) // threshold 8,000
        s.addDebugSteps(5000)
        assertEquals(5, s.lockedActions)
        s.setStepGoal(6000) // threshold 4,800: already met
        assertEquals(5, s.ledger.bankedActions)
        assertEquals(0, s.lockedActions)
    }

    @Test fun lockedRuleSurvivesSaveAndLoad() {
        val (s, c) = newSession(goal = 5000)
        s.addDebugSteps(2000)
        val loaded = GameSession(SaveCodec.decode(SaveCodec.encode(s.data), c.now), c)
        assertEquals(2, loaded.lockedActions)
        loaded.addDebugSteps(2000)
        assertEquals(4, loaded.ledger.bankedActions)
    }

    @Test fun catchUpSplitsAtLocalMidnight() {
        val (s, c) = newSession(10) // UTC+10: local midnight is 14:00 UTC
        val src = FakeSteps()
        src.add(START + 2 * 3_600_000, 3000)
        src.add(START + 5 * 3_600_000, 4500)
        c.advanceHours(8)
        assertEquals(7, s.syncSteps(src))
        assertEquals(3000, s.ledger.stepsOn(LocalDate.of(2026, 3, 2)))
        assertEquals(4500, s.ledger.stepsOn(LocalDate.of(2026, 3, 3)))
        assertEquals(0, s.syncSteps(src))
    }

    @Test fun unavailableSourceRetriesLater() {
        val (s, c) = newSession()
        val src = FakeSteps().apply { available = false; add(START + 3_600_000, 2000) }
        c.advanceHours(2)
        assertEquals(0, s.syncSteps(src))
        src.available = true
        assertEquals(2, s.syncSteps(src))
    }

    @Test fun commandsCostActionsAndPause() {
        val (s, _) = newSession()
        s.addDebugSteps(2000)
        s.startBattle()
        var n = 0
        while (s.battle != null && s.ledger.bankedActions > 0) { s.command(BattleCommand.defend()); n++ }
        assertEquals(2, n)
        assertNotNull(s.validateCommand(BattleCommand.defend()))
        assertFailsWith<IllegalStateException> { s.command(BattleCommand.defend()) }
    }

    @Test fun invalidCommandDoesNotSpend() {
        val (s, _) = newSession()
        s.addDebugSteps(1000)
        s.startBattle()
        assertFailsWith<IllegalStateException> { s.command(BattleCommand.attack(-99)) }
        assertEquals(1, s.ledger.bankedActions)
    }

    @Test fun winMeadowBattle() {
        val (s, _) = newSession()
        s.addDebugSteps(200_000)
        val coins = s.inventory.coins
        s.startBattle()
        var g = 0
        while (s.battle != null && g++ < 200) s.command(BattleCommand.attack(s.battle!!.enemies.first { it.isAlive }.id))
        assertEquals(BattleOutcome.Victory, s.lastSummary!!.outcome)
        assertTrue(s.inventory.coins > coins)
        assertTrue(s.isUnlocked(Achievements.all.first { it.id == "win_1" }))
    }

    @Test fun saveRoundTripMidBattle() {
        val (s, c) = newSession()
        s.addDebugSteps(3000)
        s.startBattle()
        s.command(BattleCommand.defend())
        val loaded = SaveCodec.decode(SaveCodec.encode(s.data), c.now)
        assertEquals(s.ledger.totalSteps, loaded.ledger.totalSteps)
        assertEquals(s.battle!!.rng.state, loaded.activeBattle!!.rng.state)
        val resumed = GameSession(loaded, c)
        assertNull(resumed.validateCommand(BattleCommand.defend()))
        resumed.command(BattleCommand.defend())
        assertEquals(1, resumed.ledger.bankedActions)
    }

    @Test fun newerSaveRejected() {
        assertFailsWith<IllegalStateException> { SaveCodec.decode("""{"version":99}""", START) }
    }

    @Test fun defeatCostsTenPercent() {
        val (s, _) = newSession()
        s.party.forEach { it.hp = 1 }
        s.data.currentRegionId = "ruins"
        s.addDebugSteps(100_000)
        s.startBattle()
        s.inventory.coins = 1000
        var g = 0
        while (s.battle != null && g++ < 200) s.command(BattleCommand.defend())
        assertEquals(BattleOutcome.Defeat, s.lastSummary!!.outcome)
        assertEquals(900, s.inventory.coins)
        assertTrue(s.party.all { it.hp > 0 })
    }

    @Test fun regionsUnlock() {
        val (s, _) = newSession()
        val forest = GameData.region("forest")!!
        assertFalse(s.isRegionUnlocked(forest))
        s.data.bossesDefeated += "boss_boar"
        assertFalse(s.isRegionUnlocked(forest))
        s.ledger.totalSteps = (forest.unlockKm * 1000 / s.stride).toLong() + 1
        assertTrue(s.travelTo("forest"))
    }

    @Test fun shopAndEquip() {
        val (s, _) = newSession()
        s.inventory.coins = 500
        assertTrue(s.buy("iron_sword")); assertFalse(s.buy("knight_blade"))
        val atk = s.party[0].totalStats().atk
        assertTrue(s.equip(0, "iron_sword"))
        assertTrue(s.party[0].totalStats().atk > atk)
        assertFalse(s.equip(2, "rusty_sword"))
    }

    @Test fun quests() {
        val (s, _) = newSession()
        val q = s.activeQuests.first { it.metric == QuestMetric.Steps && it.period == QuestPeriod.Daily }
        assertFalse(s.claimQuest(q.id))
        s.addDebugSteps(q.target)
        assertTrue(s.claimQuest(q.id)); assertFalse(s.claimQuest(q.id))
    }
}

class CombatTest {
    private fun battle(vararg e: String) = Battle.create(GameData.startingParty(), e.toList(), Rng(42), "meadow")
    private fun advanceTo(b: Battle, c: HeroClass, inv: Inventory) {
        var g = 0
        while (b.outcome == BattleOutcome.Ongoing && b.heroClasses[b.currentActor!!.heroIndex] != c && g++ < 50) b.execute(BattleCommand.defend(), inv)
    }

    @Test fun startsOnHeroTurnFastestFirst() {
        val b = battle("slime")
        assertTrue(b.awaitingHeroCommand)
        assertEquals(HeroClass.Rogue, b.heroClasses[b.currentActor!!.heroIndex])
    }

    @Test fun weaknessBreaksShield() {
        val inv = Inventory()
        val b = battle("boss_boar")
        val boar = b.enemies.first()
        advanceTo(b, HeroClass.Mage, inv)
        val before = boar.shield
        val ev = b.execute(BattleCommand.skill("fire", boar.id), inv)
        assertTrue(ev.first { it.kind == BattleEventKind.Damage && it.targetId == boar.id }.weak)
        assertEquals(before - 1, boar.shield)
        assertTrue(b.isRevealed(boar, Element.Fire))
    }

    @Test fun brokenTakesDoubleAndDefendHalves() {
        val b = battle("slime")
        val mage = b.heroes.first { b.heroClasses[it.heroIndex] == HeroClass.Mage }
        val slime = b.enemies.first()
        val n = Damage.compute(mage, slime, 150, true, Element.Ice, Rng(7)).amount
        slime.broken = true
        val br = Damage.compute(mage, slime, 150, true, Element.Ice, Rng(7)).amount
        assertTrue(br in n * 2 - 1..n * 2 + 1)
        val w = b.heroes.first()
        val a = Damage.compute(slime, w, 300, false, Element.Physical, Rng(3)).amount
        w.apply(StatusKind.Defending, 1)
        val d = Damage.compute(slime, w, 300, false, Element.Physical, Rng(3)).amount
        assertTrue(d in a / 2 - 1..a / 2 + 1)
    }

    @Test fun cannotFleeBoss() = assertNotNull(battle("boss_boar").validate(BattleCommand.flee(), Inventory()))
}

class PartyTest {
    @Test fun levelUp() {
        val h = Hero.create("T", HeroClass.Warrior, null, null)
        val hp = h.totalStats().maxHp
        assertEquals(4, h.gainExp((1..4).sumOf { Leveling.expToNext(it) }))
        assertEquals(5, h.skillPoints)
        assertEquals(hp + 4 * GameData.classDef(HeroClass.Warrior).growth.maxHp, h.totalStats().maxHp)
    }

    @Test fun skillTree() {
        val h = Hero.create("T", HeroClass.Mage, null, null).apply { level = 10; skillPoints = 2 }
        assertFalse(h.unlock("m_blizzard")); assertTrue(h.unlock("m_inferno")); assertTrue(h.unlock("m_blizzard")); assertFalse(h.unlock("m_tempest"))
    }

    @Test fun contentResolves() {
        assertEquals(5, GameData.classes.size); assertEquals(6, GameData.regions.size); assertEquals(24, GameData.enemies.size)
        GameData.classes.values.forEach { c ->
            assertEquals(8, c.tree.size)
            c.tree.forEach { n -> n.requires?.let { r -> assertTrue(c.tree.any { it.id == r }) }; n.unlocksSkill?.let { assertNotNull(GameData.skillOrNull(it)) } }
        }
        GameData.regions.forEach { r -> assertTrue(GameData.enemy(r.bossId).isBoss); r.encounters.forEach { assertFalse(GameData.enemy(it).isBoss) } }
        GameData.enemies.values.forEach { e -> e.loot.forEach { assertNotNull(GameData.itemOrNull(it.itemId)) } }
        GameData.startingParty().forEach { assertTrue(GameData.item(it.weaponId!!).canEquip(it.heroClass)) }
    }
}

class MetaTest {
    private val thu = LocalDate.of(2026, 3, 5)
    private fun days(vararg steps: Int) = steps.withIndex().associate { (i, v) -> StepLedger.dayKey(thu.minusDays((steps.size - 1 - i).toLong())) to v }

    @Test fun moon() {
        val m = Achievements.all.first { it.id == "moon_100" }
        assertEquals(1.0, Achievements.progress(m) { 384_400.0 })
        assertEquals(0.5, Achievements.progress(m) { 192_200.0 }, 1e-9)
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
        assertEquals(46, Achievements.all.size)
    }

    @Test fun comparisons() {
        val lines = Achievements.comparisons(50.0, 1000.0)
        assertTrue(lines.any { "Moon" in it }); assertTrue(lines.any { "edge of space" in it })
    }

    @Test fun streaks() {
        assertEquals(3, Streaks.compute(days(6000, 6000, 6000), thu, 5000).current)
        assertEquals(2, Streaks.compute(days(6000, 6000, 1200), thu, 5000).current)
        val r = Streaks.compute(days(6000, 0, 6000, 6000), thu, 5000)
        assertEquals(3, r.current); assertEquals(1, r.shieldsUsed)
        assertEquals(2, Streaks.compute(days(6000, 0, 6000, 0, 6000), thu, 5000).current)
        assertEquals(4, Streaks.longest(days(6000, 6000, 6000, 6000, 0, 0, 6000, 6000), 5000))
        assertEquals(1.5, Streaks.coinMultiplier(400), 1e-9)
    }

    @Test fun questRollover() {
        val s = QuestState()
        val d1 = LocalDate.of(2026, 3, 2)
        Quests.refresh(s, d1)
        assertEquals(3, s.activeDaily.size)
        assertTrue(s.activeDaily.any { Quests.def(it)!!.metric == QuestMetric.Steps })
        Quests.record(s, QuestMetric.BattlesWon, 2)
        Quests.refresh(s, d1.plusDays(1))
        assertTrue(s.dailyCounters.isEmpty()); assertEquals(2, s.weeklyCounters["BattlesWon"])
    }
}
