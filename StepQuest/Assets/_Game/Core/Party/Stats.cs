using System;

namespace StepQuest.Core.Party
{
    public enum StatType { MaxHp, MaxMp, Atk, Def, Mag, Res, Spd, Crit }

    [Serializable]
    public struct Stats
    {
        public int MaxHp, MaxMp, Atk, Def, Mag, Res, Spd, Crit;

        public Stats(int hp, int mp, int atk, int def, int mag, int res, int spd, int crit)
        {
            MaxHp = hp; MaxMp = mp; Atk = atk; Def = def; Mag = mag; Res = res; Spd = spd; Crit = crit;
        }

        public static Stats operator +(Stats a, Stats b) => new Stats(
            a.MaxHp + b.MaxHp, a.MaxMp + b.MaxMp, a.Atk + b.Atk, a.Def + b.Def,
            a.Mag + b.Mag, a.Res + b.Res, a.Spd + b.Spd, a.Crit + b.Crit);

        public static Stats operator *(Stats a, int n) => new Stats(
            a.MaxHp * n, a.MaxMp * n, a.Atk * n, a.Def * n, a.Mag * n, a.Res * n, a.Spd * n, a.Crit * n);

        public int Get(StatType t)
        {
            switch (t)
            {
                case StatType.MaxHp: return MaxHp;
                case StatType.MaxMp: return MaxMp;
                case StatType.Atk: return Atk;
                case StatType.Def: return Def;
                case StatType.Mag: return Mag;
                case StatType.Res: return Res;
                case StatType.Spd: return Spd;
                default: return Crit;
            }
        }

        public static Stats Of(StatType t, int v)
        {
            var s = new Stats();
            switch (t)
            {
                case StatType.MaxHp: s.MaxHp = v; break;
                case StatType.MaxMp: s.MaxMp = v; break;
                case StatType.Atk: s.Atk = v; break;
                case StatType.Def: s.Def = v; break;
                case StatType.Mag: s.Mag = v; break;
                case StatType.Res: s.Res = v; break;
                case StatType.Spd: s.Spd = v; break;
                default: s.Crit = v; break;
            }
            return s;
        }
    }
}
