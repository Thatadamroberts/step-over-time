using System;
using System.Collections.Generic;
using System.Globalization;

namespace StepQuest.Core.Meta
{
    /// <summary>
    /// Streaks are derived from the daily step history, so they are always consistent with it.
    /// A day counts when steps ≥ goal. Today counts once the goal is met, but an unfinished
    /// today never breaks the streak. One missed day per calendar week (Mon–Sun) is forgiven
    /// by a "streak shield"; shielded days keep the streak alive but don't add to it.
    /// </summary>
    public static class Streaks
    {
        public const int DefaultGoal = 5000;
        public const double BonusPerDay = 0.025;
        public const int MaxBonusDays = 20; // caps the coin multiplier at 1.5×

        public struct Result
        {
            public int Current;
            public int ShieldsUsed;
            public bool TodayDone;
        }

        public static Result Compute(IDictionary<string, int> dailySteps, DateTime today, int goal, int maxLookbackDays = 4000)
        {
            var r = new Result();
            today = today.Date;
            r.TodayDone = Get(dailySteps, today) >= goal;
            if (r.TodayDone) r.Current = 1;

            var shieldedWeeks = new HashSet<int>();
            var day = today.AddDays(-1);
            for (int i = 0; i < maxLookbackDays; i++, day = day.AddDays(-1))
            {
                if (Get(dailySteps, day) >= goal) { r.Current++; continue; }

                int week = WeekKey(day);
                bool earlierDayMet = Get(dailySteps, day.AddDays(-1)) >= goal;
                if (!shieldedWeeks.Contains(week) && earlierDayMet && r.Current > 0)
                {
                    shieldedWeeks.Add(week);
                    r.ShieldsUsed++;
                    continue;
                }
                break;
            }
            return r;
        }

        /// <summary>Longest streak anywhere in the history (same rules, no "today" special-case).</summary>
        public static int Longest(IDictionary<string, int> dailySteps, int goal)
        {
            if (dailySteps.Count == 0) return 0;
            var days = new List<DateTime>();
            foreach (var k in dailySteps.Keys)
                if (DateTime.TryParseExact(k, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var d)) days.Add(d);
            if (days.Count == 0) return 0;
            days.Sort();
            int best = 0, run = 0;
            var shieldedWeeks = new HashSet<int>();
            for (var d = days[0]; d <= days[days.Count - 1]; d = d.AddDays(1))
            {
                if (Get(dailySteps, d) >= goal) { run++; best = Math.Max(best, run); continue; }
                int week = WeekKey(d);
                if (run > 0 && !shieldedWeeks.Contains(week) && Get(dailySteps, d.AddDays(1)) >= goal)
                {
                    shieldedWeeks.Add(week);
                    continue;
                }
                run = 0;
                shieldedWeeks.Clear();
            }
            return best;
        }

        public static double CoinMultiplier(int streak) => 1.0 + Math.Min(streak, MaxBonusDays) * BonusPerDay;

        static int Get(IDictionary<string, int> d, DateTime day)
            => d.TryGetValue(Steps.StepLedger.DayKey(day), out var s) ? s : 0;

        /// <summary>Monday-based week number, unique across years.</summary>
        public static int WeekKey(DateTime day)
        {
            int offset = ((int)day.DayOfWeek + 6) % 7; // Monday = 0
            var monday = day.Date.AddDays(-offset);
            return (int)(monday.Ticks / TimeSpan.TicksPerDay);
        }
    }
}
