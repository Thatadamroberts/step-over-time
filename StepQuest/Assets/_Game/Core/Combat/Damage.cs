using System;
using StepQuest.Core.Party;

namespace StepQuest.Core.Combat
{
    public static class Damage
    {
        public const double WeaknessMultiplier = 1.3;
        public const double BrokenMultiplier = 2.0;
        public const double CritMultiplier = 1.5;
        public const double DefendMultiplier = 0.5;
        public const int BuffPercent = 30;

        public struct Result
        {
            public int Amount;
            public bool Crit;
            public bool Weak;
        }

        public static Result Compute(Combatant attacker, Combatant target, int power, bool magical, Element element, Rng rng, bool[] weakTable = null)
        {
            int atk = magical ? attacker.Stats.Mag : attacker.Stats.Atk;
            int def = magical ? target.Stats.Res : target.Stats.Def;
            if (!magical && attacker.Has(StatusKind.AtkUp)) atk = atk * (100 + BuffPercent) / 100;
            if (target.Has(StatusKind.DefUp)) def = def * (100 + BuffPercent) / 100;

            double raw = atk * power / 100.0 - def * 0.5;
            raw = Math.Max(raw, atk * power / 100.0 * 0.1);
            raw = Math.Max(1, raw);

            double variance = 0.9 + rng.NextDouble() * 0.2;
            raw *= variance;

            var r = new Result();
            if (!magical && rng.NextDouble() * 100 < attacker.Stats.Crit)
            {
                raw *= CritMultiplier;
                r.Crit = true;
            }
            if (weakTable != null && weakTable[(int)element])
            {
                raw *= WeaknessMultiplier;
                r.Weak = true;
            }
            if (target.Broken) raw *= BrokenMultiplier;
            if (target.Has(StatusKind.Defending)) raw *= DefendMultiplier;

            r.Amount = Math.Max(1, (int)Math.Round(raw));
            return r;
        }

        public static int Heal(Combatant caster, int power, Rng rng)
        {
            double raw = caster.Stats.Mag * power / 100.0 * (0.95 + rng.NextDouble() * 0.1);
            return Math.Max(1, (int)Math.Round(raw));
        }
    }
}
