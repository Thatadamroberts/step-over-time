using System;
using System.Collections.Generic;
using System.Linq;
using StepQuest.Core;
using StepQuest.Core.Combat;
using StepQuest.Core.Data;
using StepQuest.Core.Economy;
using StepQuest.Core.Party;
using Xunit;

namespace StepQuest.Tests
{
    public class CombatTests
    {
        static Battle NewBattle(params string[] enemies)
        {
            var party = GameData.StartingParty();
            return Battle.Create(party, enemies, new Rng(42), "meadow");
        }

        static Combatant HeroOfClass(Battle b, HeroClass c) => b.Heroes.First(h => b.SessionHeroClasses[h.HeroIndex] == c);

        /// <summary>Plays Defend with every hero until the given class is the actor.</summary>
        static void AdvanceTo(Battle b, HeroClass c, Inventory inv)
        {
            int guard = 0;
            while (b.Outcome == BattleOutcome.Ongoing && b.SessionHeroClasses[b.CurrentActor.HeroIndex] != c && guard++ < 50)
                b.Execute(BattleCommand.Defend(), inv);
        }

        [Fact]
        public void BattleStartsOnAHeroTurn()
        {
            var b = NewBattle("slime");
            Assert.True(b.AwaitingHeroCommand);
            Assert.Equal(1, b.Round);
        }

        [Fact]
        public void TurnOrderIsBySpeed()
        {
            var b = NewBattle("slime");
            // Rogue (SPD 17 with dagger) is fastest of the party and faster than a slime.
            Assert.Equal(HeroClass.Rogue, b.SessionHeroClasses[b.CurrentActor.HeroIndex]);
        }

        [Fact]
        public void HittingWeaknessesBreaksShields_AndBrokenEnemiesTakeDoubleDamage()
        {
            var inv = new Inventory();
            var b = NewBattle("boss_boar"); // weak to Fire, shield 5
            var boar = b.Enemies.First();
            AdvanceTo(b, HeroClass.Mage, inv);
            int shieldBefore = boar.Shield;
            var events = b.Execute(BattleCommand.Skill("fire", boar.Id), inv);
            var hit = events.First(e => e.Kind == BattleEventKind.Damage && e.TargetId == boar.Id);
            Assert.True(hit.Weak);
            Assert.Equal(shieldBefore - 1, boar.Shield);
            Assert.True(b.IsRevealed(boar, Element.Fire));

            // Force a break and compare damage multipliers directly.
            var rngA = new Rng(7); var rngB = new Rng(7);
            var mage = HeroOfClass(b, HeroClass.Mage);
            boar.Broken = false;
            var normal = Damage.Compute(mage, boar, 150, true, Element.Ice, rngA);
            boar.Broken = true;
            var broken = Damage.Compute(mage, boar, 150, true, Element.Ice, rngB);
            Assert.InRange(broken.Amount, normal.Amount * 2 - 1, normal.Amount * 2 + 1);
        }

        [Fact]
        public void BrokenEnemySkipsItsNextTurnThenRecovers()
        {
            var inv = new Inventory();
            var b = NewBattle("slime");
            var slime = b.Enemies.First();
            slime.Shield = 1;
            AdvanceTo(b, HeroClass.Mage, inv);
            var events = b.Execute(BattleCommand.Skill("fire", slime.Id), inv);
            if (!slime.IsAlive) return; // one-shot is fine too
            Assert.Contains(events, e => e.Kind == BattleEventKind.Break);

            var all = new List<BattleEvent>(events);
            int guard = 0;
            while (b.Outcome == BattleOutcome.Ongoing && !all.Any(e => e.Kind == BattleEventKind.Recover) && guard++ < 20)
                all.AddRange(b.Execute(BattleCommand.Defend(), inv));
            Assert.Contains(all, e => e.Kind == BattleEventKind.Recover && e.TargetId == slime.Id);
            Assert.False(slime.Broken);
            Assert.Equal(slime.MaxShield, slime.Shield);
        }

        [Fact]
        public void DefendHalvesDamage()
        {
            var b = NewBattle("slime");
            var w = HeroOfClass(b, HeroClass.Warrior);
            var slime = b.Enemies.First();
            var a = Damage.Compute(slime, w, 300, false, Element.Physical, new Rng(3));
            w.Apply(StatusKind.Defending, 1);
            var d = Damage.Compute(slime, w, 300, false, Element.Physical, new Rng(3));
            Assert.InRange(d.Amount, a.Amount / 2 - 1, a.Amount / 2 + 1);
        }

        [Fact]
        public void PriestHealsAndRaises()
        {
            var inv = new Inventory();
            var party = GameData.StartingParty();
            party[4].Level = 4; party[4].UnlockedNodes.Add("p_raise"); party[4].FullRestore();
            var b = Battle.Create(party, new[] { "slime" }, new Rng(1), "meadow");
            var warrior = HeroOfClass(b, HeroClass.Warrior);
            warrior.Hp = 0;
            AdvanceTo(b, HeroClass.Priest, inv);
            if (b.Outcome != BattleOutcome.Ongoing) return;
            var ev = b.Execute(BattleCommand.Skill("raise", warrior.Id), inv);
            Assert.Contains(ev, e => e.Kind == BattleEventKind.Revive);
            Assert.True(warrior.IsAlive);
        }

        [Fact]
        public void ItemsAreConsumed()
        {
            var inv = new Inventory();
            inv.Add("tonic", 1);
            var b = NewBattle("slime");
            var actor = b.CurrentActor;
            actor.Hp = 10;
            b.Execute(BattleCommand.Item("tonic", actor.Id), inv);
            Assert.Equal(0, inv.Count("tonic"));
            Assert.True(actor.Hp > 10);
            Assert.NotNull(b.Validate(BattleCommand.Item("tonic", actor.Id), inv) ?? (b.AwaitingHeroCommand ? null : "n/a"));
        }

        [Fact]
        public void CannotFleeBosses()
        {
            var b = NewBattle("boss_boar");
            Assert.NotNull(b.Validate(BattleCommand.Flee(), new Inventory()));
        }

        [Fact]
        public void NotEnoughMpIsRejected()
        {
            var inv = new Inventory();
            var b = NewBattle("slime");
            AdvanceTo(b, HeroClass.Mage, inv);
            if (b.Outcome != BattleOutcome.Ongoing) return;
            b.CurrentActor.Mp = 0;
            Assert.Equal("Not enough MP", b.Validate(BattleCommand.Skill("fire", b.Enemies.First().Id), inv));
        }

        [Fact]
        public void RngIsDeterministic()
        {
            var a = new Rng(99); var c = new Rng(99);
            for (int i = 0; i < 100; i++) Assert.Equal(a.NextULong(), c.NextULong());
            var r = new Rng(5);
            for (int i = 0; i < 1000; i++) Assert.InRange(r.Range(3, 7), 3, 6);
        }
    }

    public class PartyTests
    {
        [Fact]
        public void ExpCurveIncreases()
        {
            for (int l = 1; l < Leveling.MaxLevel - 1; l++)
                Assert.True(Leveling.ExpToNext(l + 1) > Leveling.ExpToNext(l));
        }

        [Fact]
        public void LevelUpGrantsStatsAndSkillPoints()
        {
            var h = new Hero("Test", HeroClass.Warrior);
            int hp = h.TotalStats().MaxHp;
            int gained = h.GainExp(Leveling.ExpToNext(1) + Leveling.ExpToNext(2) + Leveling.ExpToNext(3) + Leveling.ExpToNext(4));
            Assert.Equal(4, gained);
            Assert.Equal(5, h.Level);
            Assert.Equal(1 + 1 + 1 + 2, h.SkillPoints); // level 5 grants a bonus point
            Assert.Equal(hp + 4 * GameData.Class(HeroClass.Warrior).Growth.MaxHp, h.TotalStats().MaxHp);
        }

        [Fact]
        public void SkillTreeRespectsPrereqsLevelsAndPoints()
        {
            var h = new Hero("Test", HeroClass.Mage) { Level = 10, SkillPoints = 2 };
            Assert.False(h.Unlock("m_blizzard")); // needs m_inferno
            Assert.True(h.Unlock("m_inferno"));
            Assert.True(h.Unlock("m_blizzard"));
            Assert.False(h.Unlock("m_tempest")); // out of points
            Assert.Contains(h.Skills(), s => s.Id == "blizzard");
        }

        [Fact]
        public void EveryClassTreeIsWellFormed()
        {
            foreach (var c in GameData.Classes.Values)
            {
                Assert.Equal(8, c.Tree.Count);
                foreach (var n in c.Tree)
                {
                    if (n.Requires != null) Assert.Contains(c.Tree, x => x.Id == n.Requires);
                    if (n.UnlocksSkill != null) Assert.NotNull(GameData.SkillOrNull(n.UnlocksSkill));
                }
                foreach (var s in c.StartingSkills) Assert.NotNull(GameData.SkillOrNull(s));
            }
        }

        [Fact]
        public void ContentReferencesResolve()
        {
            foreach (var r in GameData.Regions)
            {
                Assert.True(GameData.Enemy(r.BossId).IsBoss);
                foreach (var e in r.Encounters) Assert.False(GameData.Enemy(e).IsBoss);
            }
            foreach (var e in GameData.Enemies.Values)
                foreach (var l in e.Loot) Assert.NotNull(GameData.ItemOrNull(l.ItemId));
            foreach (var h in GameData.StartingParty())
            {
                Assert.True(GameData.Item(h.WeaponId).CanEquip(h.Class));
                Assert.True(GameData.Item(h.ArmorId).CanEquip(h.Class));
            }
            foreach (var q in StepQuest.Core.Meta.Quests.Pool)
                if (q.RewardItem != null) Assert.NotNull(GameData.ItemOrNull(q.RewardItem));
        }
    }
}
