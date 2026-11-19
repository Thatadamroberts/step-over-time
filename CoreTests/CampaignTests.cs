using System;
using System.Linq;
using StepQuest.Core.Combat;
using StepQuest.Core.Data;
using StepQuest.Core.Save;
using StepQuest.Core.Session;
using Xunit;
using Xunit.Abstractions;

namespace StepQuest.Tests
{
    /// <summary>
    /// End-to-end pacing: a player walking 8,000 steps a day, spending every action, resting at the inn
    /// when hurt and challenging each boss once at the recommended level.
    /// </summary>
    public class CampaignTests
    {
        readonly ITestOutputHelper _out;
        public CampaignTests(ITestOutputHelper o) { _out = o; }

        [Fact]
        public void EightThousandStepsADayFinishesTheGameInAFewMonths()
        {
            var clock = new FakeClock(new DateTime(2026, 1, 5, 7, 0, 0, DateTimeKind.Utc));
            var data = SaveData.NewGame(clock.UtcNow);
            data.RngState = 2024;
            var s = new GameSession(data, clock);
            int day = 0, lastRegion = -1;

            for (day = 1; day <= 365 && s.Data.BossesDefeated.Count < GameData.Regions.Count; day++)
            {
                clock.Advance(TimeSpan.FromDays(1));
                s.AddDebugSteps(8000);

                int highest = s.HighestRegion;
                if (s.Battle == null && highest != lastRegion)
                {
                    s.TravelTo(GameData.Regions[highest].Id);
                    _out.WriteLine($"Day {day,3}: entered {GameData.Regions[highest].Name} at L{s.Party.Max(h => h.Level)} ({s.TotalKm:0} km, {s.Inventory.Coins} coins, earned {s.Data.Counters.CoinsEarned})");
                    lastRegion = highest;
                }

                int guard = 0;
                while (s.Ledger.BankedActions > 0 && guard++ < 200)
                {
                    if (s.Battle == null)
                    {
                        if (s.Party.Any(h => h.Hp < h.TotalStats().MaxHp * 0.5) && s.Inventory.Coins >= s.InnCost) s.RestAtInn();
                        var region = s.CurrentRegion;
                        bool bossReady = !s.Data.BossesDefeated.Contains(region.BossId)
                                         && s.Party.Min(h => h.Level) >= region.RecommendedLevel + 3
                                         && s.Ledger.BankedActions >= 8;
                        if (bossReady) s.RestAtInn();
                        s.StartBattle(bossReady);
                        continue;
                    }
                    s.Command(BalanceTests.Policy(s.Battle, s.Inventory));
                    if (s.LastSummary?.BossDefeated == true)
                        _out.WriteLine($"Day {day,3}: beat {GameData.Regions[lastRegion].BossId} at L{s.Party.Max(h => h.Level)}");
                }
            }

            _out.WriteLine($"Finished on day {day - 1}: L{s.Party.Max(h => h.Level)}, {s.TotalKm:0} km, {s.Inventory.Coins} coins, {s.Data.Counters.BattlesWon} wins / {s.Data.Counters.BattlesLost} losses, {s.Data.Achievements.Count} achievements");
            Assert.Equal(GameData.Regions.Count, s.Data.BossesDefeated.Count);
            Assert.InRange(day - 1, 100, 220); // ~5 months at 8k steps/day
        }
    }
}
