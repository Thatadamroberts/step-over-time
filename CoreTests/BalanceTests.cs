using System;
using System.Linq;
using StepQuest.Core;
using StepQuest.Core.Combat;
using StepQuest.Core.Data;
using StepQuest.Core.Economy;
using StepQuest.Core.Party;
using Xunit;
using Xunit.Abstractions;

namespace StepQuest.Tests
{
    /// <summary>
    /// Guards the pacing: one action is 1,000 real steps, so normal fights must be short.
    /// Uses a simple "sensible player" policy: heal when low, use weakness skills, else attack.
    /// </summary>
    public class BalanceTests
    {
        readonly ITestOutputHelper _out;
        public BalanceTests(ITestOutputHelper o) { _out = o; }

        internal static BattleCommand Policy(Battle b, Inventory inv)
        {
            var actor = b.CurrentActor;
            var cls = b.SessionHeroClasses[actor.HeroIndex];
            var hero = b.Heroes.ToList();
            var enemies = b.Enemies.Where(e => e.IsAlive).ToList();
            var party = GameData.StartingParty();
            var skills = new Hero("x", cls).Skills().ToList();

            if (cls == HeroClass.Priest)
            {
                var low = hero.Where(h => h.IsAlive && h.Hp < h.Stats.MaxHp * 0.5).OrderBy(h => h.Hp).FirstOrDefault();
                if (low != null && actor.Mp >= 4) return BattleCommand.Skill("heal", low.Id);
            }
            var target = enemies.OrderBy(e => e.Hp).First();
            foreach (var s in skills.Where(s => s.Effect == SkillEffect.Damage && s.Target == TargetKind.SingleEnemy && actor.Mp >= s.MpCost))
                if (b.IsWeak(target, s.Element)) return BattleCommand.Skill(s.Id, target.Id);
            return BattleCommand.Attack(target.Id);
        }

        static (int commands, BattleOutcome outcome) Play(string[] enemies, int level, ulong seed)
        {
            var party = GameData.StartingParty();
            foreach (var h in party) { while (h.Level < level) h.GainExp(Leveling.ExpToNext(h.Level)); h.FullRestore(); }
            var inv = new Inventory();
            var b = Battle.Create(party, enemies, new Rng(seed), null);
            int n = 0;
            while (b.Outcome == BattleOutcome.Ongoing && n < 500) { b.Execute(Policy(b, inv), inv); n++; }
            return (n, b.Outcome);
        }

        [Theory]
        [InlineData("meadow", 1)]
        [InlineData("forest", 5)]
        [InlineData("desert", 10)]
        [InlineData("coast", 16)]
        [InlineData("peaks", 22)]
        [InlineData("ruins", 28)]
        public void NormalFightsAreShortAndWinnableAtRecommendedLevel(string regionId, int level)
        {
            var region = GameData.Region(regionId);
            int total = 0, wins = 0, runs = 60;
            for (int i = 0; i < runs; i++)
            {
                var rng = new Rng((ulong)(i + 1) * 7919);
                var enc = StepQuest.Core.World.WorldMap.RollEncounter(region, rng).ToArray();
                var (n, o) = Play(enc, level, (ulong)i + 1);
                total += n; if (o == BattleOutcome.Victory) wins++;
            }
            double avg = total / (double)runs;
            _out.WriteLine($"{regionId} L{level}: avg {avg:0.0} commands, win {wins}/{runs}");
            Assert.InRange(avg, 1.5, 6.5);
            Assert.True(wins >= runs * 0.9, $"win rate {wins}/{runs}");
        }

        [Theory]
        [InlineData("meadow", 4)]
        [InlineData("forest", 9)]
        [InlineData("desert", 15)]
        [InlineData("coast", 21)]
        [InlineData("peaks", 27)]
        [InlineData("ruins", 33)]
        public void BossesAreLongerButBeatable(string regionId, int level)
        {
            var boss = GameData.Region(regionId).BossId;
            int total = 0, wins = 0, runs = 20;
            for (int i = 0; i < runs; i++)
            {
                var (n, o) = Play(new[] { boss }, level, (ulong)i + 11);
                total += n; if (o == BattleOutcome.Victory) wins++;
            }
            double avg = total / (double)runs;
            _out.WriteLine($"{boss} L{level}: avg {avg:0.0} commands, win {wins}/{runs}");
            Assert.InRange(avg, 8, 50);
            Assert.True(wins >= runs * 0.6, $"win rate {wins}/{runs}");
        }
    }
}
