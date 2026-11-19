using System;
using System.Collections.Generic;

namespace StepQuest.Core.Meta
{
    public enum QuestMetric { Steps, BattlesWon, SkillsUsed, EnemiesDefeated, BreaksLanded, ItemsBought }

    public enum QuestPeriod { Daily, Weekly }

    public sealed class QuestDef
    {
        public string Id;
        public QuestPeriod Period;
        public QuestMetric Metric;
        public int Target;
        public int RewardCoins;
        public string RewardItem;
        public string Text => string.Format(TextFormat, Target);
        public string TextFormat;
    }

    [Serializable]
    public class QuestState
    {
        public string DailyKey;
        public string WeeklyKey;
        public List<string> ActiveDaily = new List<string>();
        public List<string> ActiveWeekly = new List<string>();
        /// <summary>Counters for the current period (metric name → value), per period.</summary>
        public Dictionary<string, int> DailyCounters = new Dictionary<string, int>();
        public Dictionary<string, int> WeeklyCounters = new Dictionary<string, int>();
        public List<string> Claimed = new List<string>();
    }

    public static class Quests
    {
        public const int DailyCount = 3;
        public const int WeeklyCount = 2;

        public static readonly List<QuestDef> Pool = new List<QuestDef>
        {
            Q("d_steps_5k", QuestPeriod.Daily, QuestMetric.Steps, 5_000, 40, "Walk {0:#,0} steps today"),
            Q("d_steps_8k", QuestPeriod.Daily, QuestMetric.Steps, 8_000, 70, "Walk {0:#,0} steps today", "tonic"),
            Q("d_steps_12k", QuestPeriod.Daily, QuestMetric.Steps, 12_000, 120, "Walk {0:#,0} steps today", "ether"),
            Q("d_win_2", QuestPeriod.Daily, QuestMetric.BattlesWon, 2, 50, "Win {0} battles"),
            Q("d_skills_3", QuestPeriod.Daily, QuestMetric.SkillsUsed, 3, 40, "Use {0} skills in battle"),
            Q("d_break_2", QuestPeriod.Daily, QuestMetric.BreaksLanded, 2, 60, "Break {0} enemy shields"),
            Q("d_kill_5", QuestPeriod.Daily, QuestMetric.EnemiesDefeated, 5, 50, "Defeat {0} enemies"),
            Q("w_steps_50k", QuestPeriod.Weekly, QuestMetric.Steps, 50_000, 400, "Walk {0:#,0} steps this week", "ember_feather"),
            Q("w_steps_70k", QuestPeriod.Weekly, QuestMetric.Steps, 70_000, 600, "Walk {0:#,0} steps this week", "hi_tonic"),
            Q("w_win_15", QuestPeriod.Weekly, QuestMetric.BattlesWon, 15, 350, "Win {0} battles this week"),
            Q("w_break_15", QuestPeriod.Weekly, QuestMetric.BreaksLanded, 15, 350, "Break {0} shields this week"),
            Q("w_kill_40", QuestPeriod.Weekly, QuestMetric.EnemiesDefeated, 40, 400, "Defeat {0} enemies this week"),
        };

        static QuestDef Q(string id, QuestPeriod p, QuestMetric m, int target, int coins, string text, string item = null)
            => new QuestDef { Id = id, Period = p, Metric = m, Target = target, RewardCoins = coins, TextFormat = text, RewardItem = item };

        public static QuestDef Def(string id) => Pool.Find(q => q.Id == id);

        /// <summary>Rolls over to a new day/week if needed, picking quests deterministically from the date.</summary>
        public static void Refresh(QuestState s, DateTime localToday)
        {
            string dayKey = Steps.StepLedger.DayKey(localToday);
            string weekKey = "W" + Streaks.WeekKey(localToday);
            if (s.DailyKey != dayKey)
            {
                s.DailyKey = dayKey;
                s.DailyCounters.Clear();
                s.ActiveDaily = Pick(QuestPeriod.Daily, DailyCount, (ulong)localToday.Date.Ticks);
                s.Claimed.RemoveAll(id => Def(id)?.Period == QuestPeriod.Daily);
            }
            if (s.WeeklyKey != weekKey)
            {
                s.WeeklyKey = weekKey;
                s.WeeklyCounters.Clear();
                s.ActiveWeekly = Pick(QuestPeriod.Weekly, WeeklyCount, (ulong)Streaks.WeekKey(localToday) * 7919UL);
                s.Claimed.RemoveAll(id => Def(id)?.Period == QuestPeriod.Weekly);
            }
        }

        static List<string> Pick(QuestPeriod period, int count, ulong seed)
        {
            var candidates = Pool.FindAll(q => q.Period == period);
            var rng = new Rng(seed);
            var picked = new List<string>();
            // Always include one step quest: walking is the point.
            var stepQuests = candidates.FindAll(q => q.Metric == QuestMetric.Steps);
            picked.Add(stepQuests[rng.Range(0, stepQuests.Count)].Id);
            var others = candidates.FindAll(q => q.Metric != QuestMetric.Steps);
            while (picked.Count < count && others.Count > 0)
            {
                int i = rng.Range(0, others.Count);
                picked.Add(others[i].Id);
                others.RemoveAt(i);
            }
            return picked;
        }

        public static void Record(QuestState s, QuestMetric m, int amount)
        {
            if (amount <= 0) return;
            string key = m.ToString();
            s.DailyCounters.TryGetValue(key, out var d); s.DailyCounters[key] = d + amount;
            s.WeeklyCounters.TryGetValue(key, out var w); s.WeeklyCounters[key] = w + amount;
        }

        public static int ProgressOf(QuestState s, QuestDef q)
        {
            var counters = q.Period == QuestPeriod.Daily ? s.DailyCounters : s.WeeklyCounters;
            return counters.TryGetValue(q.Metric.ToString(), out var v) ? v : 0;
        }

        public static bool IsComplete(QuestState s, QuestDef q) => ProgressOf(s, q) >= q.Target;

        public static IEnumerable<QuestDef> Active(QuestState s)
        {
            foreach (var id in s.ActiveDaily) yield return Def(id);
            foreach (var id in s.ActiveWeekly) yield return Def(id);
        }
    }
}
