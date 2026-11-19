using System;
using System.Collections.Generic;

namespace StepQuest.Core.Meta
{
    public enum Metric
    {
        TotalKm, TotalSteps, BestDaySteps, LongestStreak, ClimbMeters,
        BattlesWon, EnemiesDefeated, BossesDefeated, BreaksLanded, MaxHeroLevel,
        RegionsCleared, LegendariesOwned, ActionsEarned, CoinsEarned
    }

    public enum AchievementCategory { Distance, Steps, Climb, Streak, Adventure }

    public sealed class AchievementDef
    {
        public string Id;
        public AchievementCategory Category;
        public string Name;
        public string Description;
        public Metric Metric;
        public double Threshold;
        public int RewardCoins;
        /// <summary>Shown as a percentage bar rather than a value bar (e.g. distance to the Moon).</summary>
        public bool ShowPercent;
    }

    /// <summary>Real-world landmark used for live "you've walked X% of …" comparisons.</summary>
    public sealed class Landmark
    {
        public string Name;
        public double Km;
        public string Verb;
        public Landmark(string name, double km, string verb) { Name = name; Km = km; Verb = verb; }
    }

    public static class Achievements
    {
        public const double MoonKm = 384_400;
        public const double EarthCircumferenceKm = 40_075;

        public static readonly List<AchievementDef> All = new List<AchievementDef>();

        public static readonly List<Landmark> Landmarks = new List<Landmark>
        {
            new Landmark("a lap of a running track", 0.4, "lapped"),
            new Landmark("the Golden Gate Bridge", 2.7, "crossed"),
            new Landmark("Central Park end to end", 4.0, "walked"),
            new Landmark("a marathon", 42.195, "completed"),
            new Landmark("the English Channel", 33.3, "crossed"),
            new Landmark("the edge of space (Kármán line)", 100, "reached"),
            new Landmark("the orbit of the ISS", 408, "reached"),
            new Landmark("the length of Great Britain", 1_407, "walked"),
            new Landmark("Route 66", 3_940, "driven, on foot,"),
            new Landmark("the Nile", 6_650, "followed"),
            new Landmark("the Great Wall of China", 21_196, "walked"),
            new Landmark("the Earth's equator", EarthCircumferenceKm, "circled"),
            new Landmark("the Moon", MoonKm, "travelled to"),
        };

        static void A(string id, AchievementCategory cat, string name, string desc, Metric m, double threshold, int coins, bool pct = false)
            => All.Add(new AchievementDef { Id = id, Category = cat, Name = name, Description = desc, Metric = m, Threshold = threshold, RewardCoins = coins, ShowPercent = pct });

        static Achievements()
        {
            var D = AchievementCategory.Distance;
            A("km_1", D, "First Kilometre", "Walk 1 km.", Metric.TotalKm, 1, 20);
            A("km_5", D, "Parkrunner", "Walk 5 km.", Metric.TotalKm, 5, 40);
            A("km_10", D, "Ten Kay", "Walk 10 km.", Metric.TotalKm, 10, 60);
            A("km_half", D, "Half Marathon", "Walk 21.1 km.", Metric.TotalKm, 21.0975, 100);
            A("km_channel", D, "Channel Crosser", "Walk the width of the English Channel (33.3 km).", Metric.TotalKm, 33.3, 120);
            A("km_marathon", D, "Marathoner", "Walk a full marathon distance (42.195 km).", Metric.TotalKm, 42.195, 150);
            A("km_100", D, "Edge of Space", "Walk 100 km — the height of the Kármán line.", Metric.TotalKm, 100, 250);
            A("km_iss", D, "Orbital", "Walk 408 km — as high as the ISS orbits.", Metric.TotalKm, 408, 500);
            A("km_1000", D, "Thousand-Kilometre Pilgrim", "Walk 1,000 km.", Metric.TotalKm, 1_000, 800);
            A("km_lejog", D, "End to End", "Walk the length of Great Britain (1,407 km).", Metric.TotalKm, 1_407, 1_000);
            A("km_route66", D, "Get Your Kicks", "Walk the length of Route 66 (3,940 km).", Metric.TotalKm, 3_940, 2_000);
            A("km_wall", D, "Wall Walker", "Walk the length of the Great Wall (21,196 km).", Metric.TotalKm, 21_196, 5_000);
            A("km_earth", D, "Circumnavigator", "Walk the Earth's circumference (40,075 km).", Metric.TotalKm, EarthCircumferenceKm, 10_000);
            A("moon_1", D, "Moonward I", "Walk 1% of the way to the Moon.", Metric.TotalKm, MoonKm * 0.01, 1_500, true);
            A("moon_5", D, "Moonward II", "Walk 5% of the way to the Moon.", Metric.TotalKm, MoonKm * 0.05, 4_000, true);
            A("moon_10", D, "Moonward III", "Walk 10% of the way to the Moon.", Metric.TotalKm, MoonKm * 0.10, 8_000, true);
            A("moon_25", D, "Moonward IV", "Walk a quarter of the way to the Moon.", Metric.TotalKm, MoonKm * 0.25, 15_000, true);
            A("moon_100", D, "Moonwalker", "Walk the full 384,400 km to the Moon.", Metric.TotalKm, MoonKm, 100_000, true);

            var S = AchievementCategory.Steps;
            A("day_10k", S, "Ten Thousand", "Walk 10,000 steps in one day.", Metric.BestDaySteps, 10_000, 50);
            A("day_20k", S, "Double Time", "Walk 20,000 steps in one day.", Metric.BestDaySteps, 20_000, 150);
            A("day_30k", S, "Ultra Day", "Walk 30,000 steps in one day.", Metric.BestDaySteps, 30_000, 300);
            A("steps_100k", S, "Hundred Thousand", "Walk 100,000 steps.", Metric.TotalSteps, 100_000, 100);
            A("steps_1m", S, "Millionaire", "Walk 1,000,000 steps.", Metric.TotalSteps, 1_000_000, 1_000);
            A("steps_10m", S, "Ten Million", "Walk 10,000,000 steps.", Metric.TotalSteps, 10_000_000, 10_000);
            A("actions_100", S, "Century of Blows", "Earn 100 combat actions.", Metric.ActionsEarned, 100, 100);

            var C = AchievementCategory.Climb;
            A("climb_eiffel", C, "Eiffel Tower", "Climb the equivalent of the Eiffel Tower (330 m).", Metric.ClimbMeters, 330, 40);
            A("climb_burj", C, "Burj Khalifa", "Climb the equivalent of the Burj Khalifa (828 m).", Metric.ClimbMeters, 828, 80);
            A("climb_kili", C, "Kilimanjaro", "Climb the equivalent of Kilimanjaro (5,895 m).", Metric.ClimbMeters, 5_895, 300);
            A("climb_everest", C, "Everest", "Climb the equivalent of Everest (8,849 m).", Metric.ClimbMeters, 8_849, 500);
            A("climb_cruise", C, "Cruising Altitude", "Climb the equivalent of a jet's cruising altitude (11,000 m).", Metric.ClimbMeters, 11_000, 700);

            var K = AchievementCategory.Streak;
            A("streak_3", K, "Habit Forming", "Hit your step goal 3 days in a row.", Metric.LongestStreak, 3, 50);
            A("streak_7", K, "Week Warrior", "Hit your step goal 7 days in a row.", Metric.LongestStreak, 7, 150);
            A("streak_30", K, "Monthly March", "Hit your step goal 30 days in a row.", Metric.LongestStreak, 30, 600);
            A("streak_100", K, "Centurion", "Hit your step goal 100 days in a row.", Metric.LongestStreak, 100, 2_000);
            A("streak_365", K, "Year of Walking", "Hit your step goal 365 days in a row.", Metric.LongestStreak, 365, 10_000);

            var V = AchievementCategory.Adventure;
            A("win_1", V, "First Blood", "Win your first battle.", Metric.BattlesWon, 1, 20);
            A("win_100", V, "Seasoned", "Win 100 battles.", Metric.BattlesWon, 100, 300);
            A("kills_1000", V, "Monster Bane", "Defeat 1,000 enemies.", Metric.EnemiesDefeated, 1_000, 1_000);
            A("breaks_50", V, "Shieldbreaker", "Break enemy shields 50 times.", Metric.BreaksLanded, 50, 200);
            A("boss_1", V, "Giant Slayer", "Defeat your first boss.", Metric.BossesDefeated, 1, 150);
            A("regions_all", V, "Skyreach Conqueror", "Defeat every region's boss.", Metric.RegionsCleared, 6, 5_000);
            A("level_10", V, "Journeyman", "Reach level 10 with any hero.", Metric.MaxHeroLevel, 10, 100);
            A("level_25", V, "Veteran", "Reach level 25 with any hero.", Metric.MaxHeroLevel, 25, 500);
            A("level_50", V, "Living Legend", "Reach the level cap with any hero.", Metric.MaxHeroLevel, Party.Leveling.MaxLevel, 2_000);
            A("legend_gear", V, "Legendary", "Own a legendary item.", Metric.LegendariesOwned, 1, 500);
            A("coins_10k", V, "Treasure Hoarder", "Earn 10,000 coins in total.", Metric.CoinsEarned, 10_000, 500);
        }

        public static double Progress(AchievementDef a, Func<Metric, double> metric)
            => Math.Max(0, Math.Min(1, metric(a.Metric) / a.Threshold));

        /// <summary>Unlocks every newly met achievement; returns the new ones.</summary>
        public static List<AchievementDef> Evaluate(Func<Metric, double> metric, IDictionary<string, long> unlocked, DateTime nowUtc)
        {
            var newly = new List<AchievementDef>();
            foreach (var a in All)
            {
                if (unlocked.ContainsKey(a.Id)) continue;
                if (metric(a.Metric) >= a.Threshold)
                {
                    unlocked[a.Id] = nowUtc.Ticks;
                    newly.Add(a);
                }
            }
            return newly;
        }

        /// <summary>Human-friendly comparisons for the Achievements screen.</summary>
        public static List<string> Comparisons(double totalKm, double climbMeters)
        {
            var lines = new List<string>();
            lines.Add($"You've walked {Percent(totalKm / MoonKm)} of the way to the Moon.");
            lines.Add($"You've walked {Percent(totalKm / EarthCircumferenceKm)} of the way around the Earth.");

            Landmark lastPassed = null, next = null;
            foreach (var l in Landmarks)
            {
                if (totalKm >= l.Km) lastPassed = l;
                else if (next == null || l.Km < next.Km) next = l;
            }
            if (lastPassed != null)
            {
                double times = totalKm / lastPassed.Km;
                lines.Add(times >= 2
                    ? $"That's {lastPassed.Name} {times:0.#} times over."
                    : $"You've {lastPassed.Verb} {lastPassed.Name}.");
            }
            if (next != null)
                lines.Add($"{next.Km - totalKm:#,0.0} km until you've {next.Verb} {next.Name}.");

            lines.Add($"In stairs, you've climbed {climbMeters:#,0} m — {climbMeters / 8_849:0.##}× Everest.");
            return lines;
        }

        public static string Percent(double fraction)
        {
            double p = fraction * 100;
            if (p >= 10) return $"{p:0.#}%";
            if (p >= 0.1) return $"{p:0.##}%";
            return $"{p:0.####}%";
        }
    }
}
