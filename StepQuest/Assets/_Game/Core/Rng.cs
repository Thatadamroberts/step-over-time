using System;

namespace StepQuest.Core
{
    /// <summary>Deterministic, serializable RNG (xorshift) so battles can be saved mid-fight and tested.</summary>
    [Serializable]
    public class Rng
    {
        public ulong State;

        public Rng() : this((ulong)DateTime.UtcNow.Ticks) { }
        public Rng(ulong seed) { State = seed == 0 ? 0x9E3779B97F4A7C15UL : seed; }

        public ulong NextULong()
        {
            ulong x = State;
            x ^= x << 13; x ^= x >> 7; x ^= x << 17;
            State = x;
            return x;
        }

        /// <summary>[0,1)</summary>
        public double NextDouble() => (NextULong() >> 11) * (1.0 / (1UL << 53));

        /// <summary>[min, maxExclusive)</summary>
        public int Range(int min, int maxExclusive)
        {
            if (maxExclusive <= min) return min;
            return min + (int)(NextULong() % (ulong)(maxExclusive - min));
        }

        public bool Chance(double p) => NextDouble() < p;
    }
}
