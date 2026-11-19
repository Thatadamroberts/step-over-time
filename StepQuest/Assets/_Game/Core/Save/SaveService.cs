using System;

namespace StepQuest.Core.Save
{
    /// <summary>Serializer abstraction: Unity uses Newtonsoft (com.unity.nuget.newtonsoft-json).</summary>
    public interface ISaveSerializer
    {
        string Serialize(SaveData data);
        SaveData Deserialize(string json);
        /// <summary>Reads only the "Version" field, so migrations can run before full deserialization.</summary>
        int PeekVersion(string json);
    }

    public interface ISaveStorage
    {
        string Read();
        void Write(string contents);
    }

    public sealed class SaveService
    {
        readonly ISaveSerializer _serializer;
        readonly ISaveStorage _storage;

        public SaveService(ISaveSerializer serializer, ISaveStorage storage)
        {
            _serializer = serializer;
            _storage = storage;
        }

        public SaveData LoadOrCreate(DateTime nowUtc)
        {
            string json = null;
            try { json = _storage.Read(); } catch { /* treat as no save */ }
            if (string.IsNullOrEmpty(json)) return SaveData.NewGame(nowUtc);

            var data = _serializer.Deserialize(json) ?? SaveData.NewGame(nowUtc);
            Migrate(data);
            Repair(data, nowUtc);
            return data;
        }

        public void Save(SaveData data) => _storage.Write(_serializer.Serialize(data));

        /// <summary>Brings older saves up to <see cref="SaveData.CurrentVersion"/>.</summary>
        public static void Migrate(SaveData d)
        {
            if (d.Version > SaveData.CurrentVersion)
                throw new InvalidOperationException($"Save is from a newer version ({d.Version}).");
            // v0 → v1: early prototype saves had no settings/quests and no version field.
            if (d.Version < 1)
            {
                if (d.Settings == null) d.Settings = new Settings();
                if (d.Quests == null) d.Quests = new Meta.QuestState();
                d.Version = 1;
            }
        }

        /// <summary>Fills any null collections so older or hand-edited saves don't crash.</summary>
        static void Repair(SaveData d, DateTime nowUtc)
        {
            if (d.Ledger == null) d.Ledger = new Steps.StepLedger { LastSyncUtcTicks = nowUtc.Ticks };
            if (d.Ledger.DailySteps == null) d.Ledger.DailySteps = new System.Collections.Generic.Dictionary<string, int>();
            if (d.Party == null || d.Party.Count == 0) d.Party = Data.GameData.StartingParty();
            if (d.Inventory == null) d.Inventory = new Economy.Inventory();
            if (d.BossesDefeated == null) d.BossesDefeated = new System.Collections.Generic.List<string>();
            if (d.Counters == null) d.Counters = new Counters();
            if (d.Achievements == null) d.Achievements = new System.Collections.Generic.Dictionary<string, long>();
            if (d.Settings == null) d.Settings = new Settings();
            if (d.Quests == null) d.Quests = new Meta.QuestState();
            if (Data.GameData.Region(d.CurrentRegionId ?? "") == null) d.CurrentRegionId = "meadow";
        }
    }
}
