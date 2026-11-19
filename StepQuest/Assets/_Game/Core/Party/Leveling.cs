using System;

namespace StepQuest.Core.Party
{
    public static class Leveling
    {
        public const int MaxLevel = 50;

        /// <summary>Total EXP needed to go from <paramref name="level"/> to level+1.</summary>
        public static int ExpToNext(int level)
        {
            if (level >= MaxLevel) return int.MaxValue;
            // Tuned so that, at ~8k steps/day, level growth keeps pace with the distance-gated regions
            // (≈L6 by Whisperwood, L9 by the Dunes, L20 by the Peaks, L26 by the Ruins). See BalanceTests.
            return (int)Math.Round(6 * Math.Pow(level, 2.2) + 12);
        }

        /// <summary>One skill point per level, plus a bonus point every 5 levels.</summary>
        public static int SkillPointsForLevel(int newLevel) => newLevel % 5 == 0 ? 2 : 1;
    }
}
