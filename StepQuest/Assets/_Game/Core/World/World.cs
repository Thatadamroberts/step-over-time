using System;
using System.Collections.Generic;
using StepQuest.Core.Economy;
using StepQuest.Core.Party;

namespace StepQuest.Core.World
{
    public sealed class EnemyDef
    {
        public string Id;
        public string Name;
        public string Sprite;
        public Stats Stats;
        public Element[] Weaknesses = Array.Empty<Element>();
        public int Shield = 2;
        public int Exp;
        public int Coins;
        public bool IsBoss;
        /// <summary>Boss uses an all-party attack every N turns (0 = never).</summary>
        public int AoeEvery;
        public Element AttackElement = Element.Physical;
        public List<LootEntry> Loot = new List<LootEntry>();
    }

    public sealed class RegionDef
    {
        public int Index;
        public string Id;
        public string Name;
        public string Description;
        /// <summary>Total distance walked (km) required to enter.</summary>
        public double UnlockKm;
        /// <summary>Enemy ids; a battle picks 1–3 of these.</summary>
        public string[] Encounters;
        public string BossId;
        /// <summary>Scales enemy stats so a region's monsters get tougher deeper in.</summary>
        public int RecommendedLevel;
        public string Palette;
    }

    public static class WorldMap
    {
        public static bool IsUnlocked(RegionDef region, double totalKm, ICollection<string> bossesDefeated)
        {
            if (region.Index == 0) return true;
            var prev = Data.GameData.Regions[region.Index - 1];
            return totalKm >= region.UnlockKm && bossesDefeated.Contains(prev.BossId);
        }

        public static int HighestUnlocked(double totalKm, ICollection<string> bossesDefeated)
        {
            int best = 0;
            foreach (var r in Data.GameData.Regions)
                if (IsUnlocked(r, totalKm, bossesDefeated)) best = Math.Max(best, r.Index);
            return best;
        }

        /// <summary>Why a region is locked, for the map UI.</summary>
        public static string LockReason(RegionDef region, double totalKm, ICollection<string> bossesDefeated)
        {
            if (IsUnlocked(region, totalKm, bossesDefeated)) return null;
            var prev = Data.GameData.Regions[region.Index - 1];
            var parts = new List<string>();
            if (!bossesDefeated.Contains(prev.BossId)) parts.Add($"Defeat {Data.GameData.Enemy(prev.BossId).Name}");
            if (totalKm < region.UnlockKm) parts.Add($"Walk {region.UnlockKm - totalKm:0.0} km more");
            return string.Join(" · ", parts);
        }

        public static List<string> RollEncounter(RegionDef region, Rng rng)
        {
            int count = rng.Range(1, 4);
            var list = new List<string>();
            for (int i = 0; i < count; i++)
                list.Add(region.Encounters[rng.Range(0, region.Encounters.Length)]);
            return list;
        }
    }
}
