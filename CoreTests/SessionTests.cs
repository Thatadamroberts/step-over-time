using System;
using System.Linq;
using StepQuest.Core.Combat;
using StepQuest.Core.Data;
using StepQuest.Core.Meta;
using StepQuest.Core.Save;
using StepQuest.Core.Session;
using Xunit;

namespace StepQuest.Tests
{
    public class SessionTests
    {
        static readonly DateTime Start = new DateTime(2026, 3, 2, 10, 0, 0, DateTimeKind.Utc);

        static (GameSession s, FakeClock c) New(double offset = 0)
        {
            var clock = new FakeClock(Start, offset);
            var data = SaveData.NewGame(clock.UtcNow);
            data.RngState = 12345;
            return (new GameSession(data, clock), clock);
        }

        [Fact]
        public void OfflineCatchUp_SplitsStepsAcrossLocalMidnight()
        {
            // UTC+10: local midnight is 14:00 UTC.
            var (s, clock) = New(10);
            var src = new FakeStepSource();
            src.Add(Start.AddHours(2), 3000);   // 22:00 local, day 1 (Mar 2)
            src.Add(Start.AddHours(5), 4500);   // 01:00 local, day 2 (Mar 3)
            clock.Advance(TimeSpan.FromHours(8));
            int earned = s.SyncSteps(src);

            Assert.Equal(7, earned);
            Assert.Equal(3000, s.Ledger.StepsOn(new DateTime(2026, 3, 2)));
            Assert.Equal(4500, s.Ledger.StepsOn(new DateTime(2026, 3, 3)));

            // Re-syncing doesn't double count.
            Assert.Equal(0, s.SyncSteps(src));
            Assert.Equal(7500, s.Ledger.TotalSteps);
        }

        [Fact]
        public void UnavailableSource_DoesNotAdvanceSyncPoint()
        {
            var (s, clock) = New();
            var src = new FakeStepSource { Available = false };
            src.Add(Start.AddHours(1), 2000);
            clock.Advance(TimeSpan.FromHours(2));
            Assert.Equal(0, s.SyncSteps(src));
            src.Available = true;
            Assert.Equal(2, s.SyncSteps(src));
        }

        [Fact]
        public void BattleCommandsCostOneActionEach_AndPauseAtZero()
        {
            var (s, _) = New();
            s.AddDebugSteps(2000);
            Assert.Equal(2, s.Ledger.BankedActions);
            var b = s.StartBattle();
            Assert.True(b.AwaitingHeroCommand || b.Outcome != BattleOutcome.Ongoing);

            int commands = 0;
            while (s.Battle != null && s.Ledger.BankedActions > 0)
            {
                s.Command(BattleCommand.Defend());
                commands++;
            }
            Assert.Equal(2, commands);
            Assert.Equal(0, s.Ledger.BankedActions);
            Assert.NotNull(s.ValidateCommand(BattleCommand.Defend()));
            Assert.Throws<InvalidOperationException>(() => s.Command(BattleCommand.Defend()));
        }

        [Fact]
        public void InvalidCommandDoesNotSpendAction()
        {
            var (s, _) = New();
            s.AddDebugSteps(1000);
            s.StartBattle();
            Assert.Throws<InvalidOperationException>(() => s.Command(BattleCommand.Attack(-99)));
            Assert.Equal(1, s.Ledger.BankedActions);
        }

        [Fact]
        public void MeadowBattleCanBeWon_GivesExpCoins()
        {
            var (s, _) = New();
            s.AddDebugSteps(200_000);
            int coinsBefore = s.Inventory.Coins;
            s.StartBattle();
            int guard = 0;
            while (s.Battle != null && guard++ < 200)
            {
                var b = s.Battle;
                var target = b.Enemies.First(e => e.IsAlive);
                s.Command(BattleCommand.Attack(target.Id));
            }
            Assert.NotNull(s.LastSummary);
            Assert.Equal(BattleOutcome.Victory, s.LastSummary.Outcome);
            Assert.True(s.Inventory.Coins > coinsBefore);
            Assert.True(s.Party.All(h => h.Exp > 0 || h.Level > 1));
            Assert.Equal(1, s.Data.Counters.BattlesWon);
            Assert.True(s.IsUnlocked(Achievements.All.First(a => a.Id == "win_1")));
        }

        [Fact]
        public void SaveRoundTrip_IncludingMidBattle()
        {
            var (s, clock) = New();
            s.AddDebugSteps(3000);
            s.StartBattle();
            s.Command(BattleCommand.Defend());

            var storage = new MemoryStorage();
            var svc = new SaveService(new NewtonsoftSerializer(), storage);
            svc.Save(s.Data);
            var loaded = svc.LoadOrCreate(clock.UtcNow);

            Assert.Equal(s.Ledger.TotalSteps, loaded.Ledger.TotalSteps);
            Assert.Equal(s.Ledger.BankedActions, loaded.Ledger.BankedActions);
            Assert.NotNull(loaded.ActiveBattle);
            Assert.Equal(s.Battle.Units.Count, loaded.ActiveBattle.Units.Count);
            Assert.Equal(s.Battle.Rng.State, loaded.ActiveBattle.Rng.State);
            Assert.Equal(5, loaded.Party.Count);

            // Resumed battle keeps working.
            var resumed = new GameSession(loaded, clock);
            Assert.True(resumed.Battle.AwaitingHeroCommand);
            Assert.Null(resumed.ValidateCommand(BattleCommand.Defend()));
            resumed.Command(BattleCommand.Defend());
            Assert.Equal(1, resumed.Ledger.BankedActions);
        }

        [Fact]
        public void Migration_FromUnversionedSave()
        {
            var storage = new MemoryStorage { Contents = "{\"Ledger\":{\"TotalSteps\":4200,\"BankedActions\":4},\"Settings\":null}" };
            var svc = new SaveService(new NewtonsoftSerializer(), storage);
            var d = svc.LoadOrCreate(Start);
            Assert.Equal(SaveData.CurrentVersion, d.Version);
            Assert.NotNull(d.Settings);
            Assert.Equal(4200, d.Ledger.TotalSteps);
            Assert.Equal(5, d.Party.Count);
        }

        [Fact]
        public void NewerSaveVersionIsRejected()
        {
            var d = new SaveData { Version = SaveData.CurrentVersion + 1 };
            Assert.Throws<InvalidOperationException>(() => SaveService.Migrate(d));
        }

        [Fact]
        public void DefeatCostsTenPercentCoins_AndRevivesParty()
        {
            var (s, _) = New();
            foreach (var h in s.Party) h.Hp = 1;
            s.Data.CurrentRegionId = "ruins"; // brutal enemies
            s.AddDebugSteps(100_000);
            s.StartBattle();
            s.Inventory.Coins = 1000; // set after step achievements have paid out
            int guard = 0;
            while (s.Battle != null && guard++ < 200) s.Command(BattleCommand.Defend());
            Assert.Equal(BattleOutcome.Defeat, s.LastSummary.Outcome);
            Assert.Equal(100, s.LastSummary.CoinsLost);
            Assert.Equal(900, s.Inventory.Coins);
            Assert.All(s.Party, h => Assert.True(h.Hp > 0));
        }

        [Fact]
        public void RegionsUnlockByDistanceAndBoss()
        {
            var (s, _) = New();
            var forest = GameData.Region("forest");
            Assert.False(s.IsRegionUnlocked(forest));
            s.Data.BossesDefeated.Add("boss_boar");
            Assert.False(s.IsRegionUnlocked(forest)); // still need 15 km
            s.Ledger.TotalSteps = (long)(forest.UnlockKm * 1000 / s.StrideMeters) + 1;
            Assert.True(s.IsRegionUnlocked(forest));
            Assert.True(s.TravelTo("forest"));
        }

        [Fact]
        public void ShopAndEquip()
        {
            var (s, _) = New();
            s.Inventory.Coins = 500;
            Assert.True(s.Buy("iron_sword"));
            Assert.False(s.Buy("knight_blade")); // tier 2, not unlocked
            int atkBefore = s.Party[0].TotalStats().Atk;
            Assert.True(s.Equip(0, "iron_sword"));
            Assert.True(s.Party[0].TotalStats().Atk > atkBefore);
            Assert.Equal(1, s.Inventory.Count("rusty_sword"));
            Assert.False(s.Equip(2, "rusty_sword")); // mage can't wield a sword
        }

        [Fact]
        public void QuestsCompleteAndClaim()
        {
            var (s, _) = New();
            var stepQuest = s.ActiveQuests.First(q => q.Metric == QuestMetric.Steps && q.Period == QuestPeriod.Daily);
            Assert.False(s.ClaimQuest(stepQuest.Id));
            s.AddDebugSteps(stepQuest.Target);
            int coins = s.Inventory.Coins;
            Assert.True(s.ClaimQuest(stepQuest.Id));
            Assert.Equal(coins + stepQuest.RewardCoins, s.Inventory.Coins);
            Assert.False(s.ClaimQuest(stepQuest.Id));
        }
    }
}
