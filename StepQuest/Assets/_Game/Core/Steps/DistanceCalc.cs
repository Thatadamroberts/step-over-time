using System;

namespace StepQuest.Core.Steps
{
    public static class DistanceCalc
    {
        public const double DefaultStrideMeters = 0.76;
        public const double StrideToHeightRatio = 0.415;

        /// <summary>Approximate rise per stair step, used for "climb" comparisons.</summary>
        public const double StairRiseMeters = 0.17;

        public static double StrideFromHeightCm(double heightCm)
        {
            if (heightCm < 50 || heightCm > 260) return DefaultStrideMeters;
            return Math.Round(heightCm / 100.0 * StrideToHeightRatio, 3);
        }

        public static double Meters(long steps, double strideMeters) => steps * strideMeters;
        public static double Kilometers(long steps, double strideMeters) => steps * strideMeters / 1000.0;

        /// <summary>Equivalent height climbed if every step were a stair.</summary>
        public static double ClimbMeters(long steps) => steps * StairRiseMeters;
    }
}
