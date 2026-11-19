using System;
using System.Collections.Generic;
using System.Linq;
using StepQuest.Core.Meta;
using StepQuest.Core.Steps;
using Xunit;

namespace StepQuest.Tests
{
    public class AchievementTests
    {
        [Fact]
        public void MoonIsOneHundredPercentAt384400Km()
        {
            var moon = Achievements.All.First(a => a.Id == "moon_100");
            Assert.Equal(1.0, Achievements.Progress(moon, m => 384_400));
            Assert.Equal(0.5, Achievements.Progress(moon, m => 192_200), 6);
            Assert.True(moon.ShowPercent);
        }

        [Fact]
        public void EvaluateUnlocksOnceAndOnlyWhenMet()
        {
            var unlocked = new Dictionary<string, long>();
            var newly = Achievements.Evaluate(m => m == Metric.TotalKm ? 1.0 : 0, unlocked, DateTime.UtcNow);
            Assert.Single(newly);
            Assert.Equal("km_1", newly[0].Id);
            Assert.Empty(Achievements.Evaluate(m => m == Metric.TotalKm ? 1.0 : 0, unlocked, DateTime.UtcNow));
            var more = Achievements.Evaluate(m => m == Metric.TotalKm ? 43 : 0, unlocked, DateTime.UtcNow);
            Assert.Contains(more, a => a.Id == "km_marathon");
            Assert.Contains(more, a => a.Id == "km_channel");
            Assert.DoesNotContain(more, a => a.Id == "km_100");
        }

        [Fact]
        public void AchievementIdsAreUnique()
        {
            Assert.Equal(Achievements.All.Count, Achievements.All.Select(a => a.Id).Distinct().Count());
        }

        [Fact]
        public void ComparisonsMentionMoonAndNextLandmark()
        {
            var lines = Achievements.Comparisons(50, 1000);
            Assert.Contains(lines, l => l.Contains("Moon"));
            Assert.Contains(lines, l => l.Contains("edge of space"));
            Assert.Equal("0.013%", Achievements.Percent(50 / Achievements.MoonKm));
        }
    }

    public class StreakTests
    {
        static Dictionary<string, int> Days(DateTime last, params int[] stepsOldestFirst)
        {
            var d = new Dictionary<string, int>();
            for (int i = 0; i < stepsOldestFirst.Length; i++)
                d[StepLedger.DayKey(last.AddDays(i - stepsOldestFirst.Length + 1))] = stepsOldestFirst[i];
            return d;
        }

        // Thursday 2026-03-05
        static readonly DateTime Thu = new DateTime(2026, 3, 5);

        [Fact]
        public void CountsConsecutiveDaysEndingToday()
        {
            var r = Streaks.Compute(Days(Thu, 6000, 6000, 6000), Thu, 5000);
            Assert.Equal(3, r.Current);
            Assert.True(r.TodayDone);
        }

        [Fact]
        public void UnfinishedTodayDoesNotBreakStreak()
        {
            var r = Streaks.Compute(Days(Thu, 6000, 6000, 1200), Thu, 5000);
            Assert.Equal(2, r.Current);
            Assert.False(r.TodayDone);
        }

        [Fact]
        public void OneMissedDayPerWeekIsShielded()
        {
            // Mon ok, Tue miss, Wed ok, Thu ok
            var r = Streaks.Compute(Days(Thu, 6000, 0, 6000, 6000), Thu, 5000);
            Assert.Equal(3, r.Current);
            Assert.Equal(1, r.ShieldsUsed);

            // Sun ok, Mon miss, Tue ok, Wed miss, Thu ok: Wed uses the week's shield, Mon then breaks it.
            var r2 = Streaks.Compute(Days(Thu, 6000, 0, 6000, 0, 6000), Thu, 5000);
            Assert.Equal(2, r2.Current);
            Assert.Equal(1, r2.ShieldsUsed);
        }

        [Fact]
        public void LongestStreakFromHistory()
        {
            var d = Days(Thu, 6000, 6000, 6000, 6000, 0, 0, 6000, 6000);
            Assert.Equal(4, Streaks.Longest(d, 5000));
        }

        [Fact]
        public void CoinMultiplierIsCapped()
        {
            Assert.Equal(1.0, Streaks.CoinMultiplier(0));
            Assert.Equal(1.25, Streaks.CoinMultiplier(10), 6);
            Assert.Equal(1.5, Streaks.CoinMultiplier(400), 6);
        }

        [Fact]
        public void WeekKeyChangesOnMonday()
        {
            var sun = new DateTime(2026, 3, 8);
            var mon = new DateTime(2026, 3, 9);
            Assert.Equal(Streaks.WeekKey(new DateTime(2026, 3, 2)), Streaks.WeekKey(sun));
            Assert.NotEqual(Streaks.WeekKey(sun), Streaks.WeekKey(mon));
        }
    }

    public class QuestTests
    {
        [Fact]
        public void DailyQuestsRollOverAndAlwaysIncludeSteps()
        {
            var s = new QuestState();
            var day1 = new DateTime(2026, 3, 2);
            Quests.Refresh(s, day1);
            Assert.Equal(Quests.DailyCount, s.ActiveDaily.Count);
            Assert.Contains(s.ActiveDaily, id => Quests.Def(id).Metric == QuestMetric.Steps);
            Quests.Record(s, QuestMetric.BattlesWon, 2);
            s.Claimed.Add(s.ActiveDaily[0]);

            Quests.Refresh(s, day1); // same day: unchanged
            Assert.Equal(2, s.DailyCounters["BattlesWon"]);

            Quests.Refresh(s, day1.AddDays(1));
            Assert.Empty(s.DailyCounters);
            Assert.DoesNotContain(s.Claimed, id => Quests.Def(id).Period == QuestPeriod.Daily);
            Assert.Equal(2, s.WeeklyCounters["BattlesWon"]); // same week
        }

        [Fact]
        public void SameDayPicksSameQuests()
        {
            var a = new QuestState(); var b = new QuestState();
            Quests.Refresh(a, new DateTime(2026, 5, 1));
            Quests.Refresh(b, new DateTime(2026, 5, 1));
            Assert.Equal(a.ActiveDaily, b.ActiveDaily);
        }
    }
}
