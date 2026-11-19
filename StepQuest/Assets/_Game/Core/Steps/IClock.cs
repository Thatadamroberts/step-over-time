using System;

namespace StepQuest.Core.Steps
{
    public interface IClock
    {
        DateTime UtcNow { get; }
        DateTime LocalNow { get; }
        DateTime ToLocal(DateTime utc);
        DateTime ToUtc(DateTime local);
    }

    public sealed class SystemClock : IClock
    {
        public DateTime UtcNow => DateTime.UtcNow;
        public DateTime LocalNow => DateTime.Now;
        public DateTime ToLocal(DateTime utc) => DateTime.SpecifyKind(utc, DateTimeKind.Utc).ToLocalTime();
        public DateTime ToUtc(DateTime local) => DateTime.SpecifyKind(local, DateTimeKind.Local).ToUniversalTime();
    }

    /// <summary>A source of steps (Health Connect, hardware sensor, debug button).</summary>
    public interface IStepSource
    {
        /// <summary>Returns steps recorded between the two instants, or -1 if unavailable.</summary>
        long ReadSteps(DateTime fromUtc, DateTime toUtc);
    }
}
