using System;
using System.Collections.Generic;

namespace StepQuest.Core.Steps
{
    /// <summary>
    /// Converts real-world steps into banked combat actions.
    /// Every <see cref="StepsPerAction"/> steps grants one action; the remainder carries over.
    /// </summary>
    [Serializable]
    public class StepLedger
    {
        public const int StepsPerAction = 1000;

        /// <summary>Sanity cap: humans don't sustain more than ~250 steps/minute.</summary>
        public const int MaxStepsPerMinute = 250;

        public long TotalSteps;
        public int StepRemainder;
        public int BankedActions;
        public long TotalActionsEarned;
        public long LastSyncUtcTicks;

        /// <summary>Last raw value seen from the cumulative-since-boot hardware counter (-1 = none).</summary>
        public long LastRawSensorValue = -1;

        /// <summary>Steps per local day, key = yyyy-MM-dd.</summary>
        public Dictionary<string, int> DailySteps = new Dictionary<string, int>();

        public event Action<int> ActionsEarned;

        /// <summary>
        /// Adds steps that happened within the time span [fromUtc, toUtc]. Returns actions earned.
        /// Ignores ranges that end before the last sync (idempotent) and caps implausible step bursts.
        /// </summary>
        public int AddSteps(long steps, DateTime fromUtc, DateTime toUtc, DateTime localDay)
        {
            if (steps <= 0) return 0;
            if (toUtc.Ticks <= LastSyncUtcTicks) return 0;

            double minutes = Math.Max(1.0, (toUtc - fromUtc).TotalMinutes);
            long cap = (long)(minutes * MaxStepsPerMinute);
            if (steps > cap) steps = cap;

            LastSyncUtcTicks = toUtc.Ticks;
            return Credit(steps, localDay);
        }

        /// <summary>
        /// Feeds a raw reading from Android's TYPE_STEP_COUNTER (cumulative since boot).
        /// A drop in value means the device rebooted; the new value is then counted from zero.
        /// </summary>
        public int AddRawSensorReading(long rawValue, DateTime nowUtc, DateTime localDay)
        {
            if (rawValue < 0) return 0;
            long delta;
            if (LastRawSensorValue < 0) delta = 0;               // first reading only establishes a baseline
            else if (rawValue < LastRawSensorValue) delta = rawValue; // reboot
            else delta = rawValue - LastRawSensorValue;
            LastRawSensorValue = rawValue;
            if (delta == 0) return 0;
            LastSyncUtcTicks = Math.Max(LastSyncUtcTicks, nowUtc.Ticks);
            return Credit(delta, localDay);
        }

        public int StepsToNextAction => StepsPerAction - StepRemainder;

        public int StepsOn(DateTime localDay)
        {
            return DailySteps.TryGetValue(DayKey(localDay), out var s) ? s : 0;
        }

        public bool TrySpendAction()
        {
            if (BankedActions <= 0) return false;
            BankedActions--;
            return true;
        }

        public static string DayKey(DateTime localDay) => localDay.ToString("yyyy-MM-dd");

        private int Credit(long steps, DateTime localDay)
        {
            TotalSteps += steps;
            var key = DayKey(localDay);
            DailySteps.TryGetValue(key, out var today);
            DailySteps[key] = (int)Math.Min(int.MaxValue, today + steps);

            long pool = StepRemainder + steps;
            int earned = (int)(pool / StepsPerAction);
            StepRemainder = (int)(pool % StepsPerAction);
            BankedActions += earned;
            TotalActionsEarned += earned;
            if (earned > 0) ActionsEarned?.Invoke(earned);
            return earned;
        }
    }
}
