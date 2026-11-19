using System;
using System.Collections.Generic;
using StepQuest.Core.Combat;
using StepQuest.Core.Economy;
using StepQuest.Core.Meta;
using StepQuest.Core.Party;
using StepQuest.Core.Steps;

namespace StepQuest.Core.Save
{
    [Serializable]
    public class Settings
    {
        public int HeightCm = 0; // 0 = not set → default stride
        public double StrideMeters = DistanceCalc.DefaultStrideMeters;
        public int DailyStepGoal = Streaks.DefaultGoal;
        public bool NotificationsEnabled = true;
        public bool DebugTools = false;
        public bool HapticsEnabled = true;
        public float MusicVolume = 0.7f;
        public float SfxVolume = 0.9f;
    }

    [Serializable]
    public class Counters
    {
        public int BattlesWon;
        public int BattlesLost;
        public int EnemiesDefeated;
        public int BossesDefeated;
        public int BreaksLanded;
        public int SkillsUsed;
        public long CoinsEarned;
        public int LongestStreak;
    }

    [Serializable]
    public class SaveData
    {
        public const int CurrentVersion = 1;

        public int Version = CurrentVersion;
        public long CreatedUtcTicks;
        public StepLedger Ledger = new StepLedger();
        public List<Hero> Party = new List<Hero>();
        public Inventory Inventory = new Inventory();
        public Battle ActiveBattle;
        public string CurrentRegionId = "meadow";
        public List<string> BossesDefeated = new List<string>();
        public Counters Counters = new Counters();
        public Dictionary<string, long> Achievements = new Dictionary<string, long>();
        public QuestState Quests = new QuestState();
        public Settings Settings = new Settings();
        public ulong RngState;

        public static SaveData NewGame(DateTime nowUtc)
        {
            var s = new SaveData
            {
                CreatedUtcTicks = nowUtc.Ticks,
                Party = Data.GameData.StartingParty(),
                RngState = (ulong)nowUtc.Ticks | 1UL,
            };
            s.Ledger.LastSyncUtcTicks = nowUtc.Ticks;
            s.Inventory.Coins = 100;
            s.Inventory.Add("tonic", 3);
            s.Inventory.Add("ether", 1);
            return s;
        }
    }
}
