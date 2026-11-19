using System;
using System.Collections.Generic;
using System.Linq;
using StepQuest.Core.Combat;
using StepQuest.Core.Data;
using StepQuest.Core.Economy;
using StepQuest.Core.Meta;
using StepQuest.Core.Party;
using StepQuest.Core.Save;
using StepQuest.Core.Steps;
using StepQuest.Core.World;

namespace StepQuest.Core.Session
{
    public sealed class BattleSummary
    {
        public BattleOutcome Outcome;
        public int Exp;
        public int Coins;
        public double CoinMultiplier = 1;
        public List<string> Drops = new List<string>();
        public Dictionary<string, int> LevelsGained = new Dictionary<string, int>();
        public int CoinsLost;
        public bool BossDefeated;
        public string RegionUnlockedHint;
    }

    /// <summary>
    /// The single entry point the Unity layer talks to. Owns the save data and enforces the
    /// rules: one banked action per hero command, rewards, achievements and quests.
    /// </summary>
    public sealed class GameSession
    {
        public const int MaxCatchUpDays = 30;
        public const double DefeatCoinPenalty = 0.10;

        public SaveData Data { get; }
        readonly IClock _clock;
        readonly Rng _rng;

        public event Action Changed;
        public event Action<int> ActionsEarned;
        public event Action<List<AchievementDef>> AchievementsUnlocked;

        public BattleSummary LastSummary { get; private set; }

        public GameSession(SaveData data, IClock clock)
        {
            Data = data;
            _clock = clock;
            _rng = new Rng(data.RngState == 0 ? (ulong)clock.UtcNow.Ticks : data.RngState);
            Quests.Refresh(Data.Quests, _clock.LocalNow);
        }

        public StepLedger Ledger => Data.Ledger;
        public IList<Hero> Party => Data.Party;
        public Inventory Inventory => Data.Inventory;
        public Battle Battle => Data.ActiveBattle;
        public double StrideMeters => Data.Settings.StrideMeters;
        public double TotalKm => DistanceCalc.Kilometers(Ledger.TotalSteps, StrideMeters);
        public int TodaySteps => Ledger.StepsOn(_clock.LocalNow);

        public int WeekSteps
        {
            get
            {
                var today = _clock.LocalNow.Date;
                int offset = ((int)today.DayOfWeek + 6) % 7;
                int sum = 0;
                for (int i = 0; i <= offset; i++) sum += Ledger.StepsOn(today.AddDays(-i));
                return sum;
            }
        }

        public Streaks.Result Streak => Streaks.Compute(Ledger.DailySteps, _clock.LocalNow, Data.Settings.DailyStepGoal);
        public double CoinMultiplier => Streaks.CoinMultiplier(Streak.Current);

        // ───────────────────────────── Steps ─────────────────────────────

        /// <summary>
        /// Pulls steps recorded since the last sync, split at local midnights so each day's bucket is accurate.
        /// Returns the actions earned.
        /// </summary>
        public int SyncSteps(IStepSource source)
        {
            var nowUtc = _clock.UtcNow;
            var fromUtc = new DateTime(Math.Max(Ledger.LastSyncUtcTicks, nowUtc.AddDays(-MaxCatchUpDays).Ticks), DateTimeKind.Utc);
            if (fromUtc >= nowUtc) return 0;

            int earned = 0;
            var cursor = fromUtc;
            while (cursor < nowUtc)
            {
                var local = _clock.ToLocal(cursor);
                var nextMidnightUtc = _clock.ToUtc(local.Date.AddDays(1));
                var sliceEnd = nextMidnightUtc < nowUtc ? nextMidnightUtc : nowUtc;
                if (sliceEnd <= cursor) sliceEnd = nowUtc; // DST/clock oddities
                long steps = source.ReadSteps(cursor, sliceEnd);
                if (steps < 0) return earned; // source unavailable: try again later, don't advance the sync point
                if (steps > 0) earned += Ledger.AddSteps(steps, cursor, sliceEnd, local.Date);
                else Ledger.LastSyncUtcTicks = Math.Max(Ledger.LastSyncUtcTicks, sliceEnd.Ticks);
                cursor = sliceEnd;
            }
            AfterSteps(earned);
            return earned;
        }

        /// <summary>Live updates from the hardware step counter while the app is open.</summary>
        public int OnRawStepCounter(long raw)
        {
            int earned = Ledger.AddRawSensorReading(raw, _clock.UtcNow, _clock.LocalNow.Date);
            AfterSteps(earned);
            return earned;
        }

        /// <summary>Debug/editor only.</summary>
        public int AddDebugSteps(int steps)
        {
            var now = _clock.UtcNow;
            int earned = Ledger.AddSteps(steps, now.AddMinutes(-Math.Max(1, steps / StepLedger.MaxStepsPerMinute + 1)), now.AddTicks(1), _clock.LocalNow.Date);
            AfterSteps(earned);
            return earned;
        }

        void AfterSteps(int earned)
        {
            Quests.Refresh(Data.Quests, _clock.LocalNow);
            if (earned > 0) ActionsEarned?.Invoke(earned);
            CheckAchievements();
            Commit();
        }

        // ───────────────────────────── World ─────────────────────────────

        public bool IsRegionUnlocked(RegionDef r) => WorldMap.IsUnlocked(r, TotalKm, Data.BossesDefeated);
        public int HighestRegion => WorldMap.HighestUnlocked(TotalKm, Data.BossesDefeated);
        public RegionDef CurrentRegion => GameData.Region(Data.CurrentRegionId) ?? GameData.Regions[0];

        public bool TravelTo(string regionId)
        {
            if (Battle != null) return false;
            var r = GameData.Region(regionId);
            if (r == null || !IsRegionUnlocked(r)) return false;
            Data.CurrentRegionId = regionId;
            Commit();
            return true;
        }

        // ───────────────────────────── Battle ─────────────────────────────

        public string CanStartBattle(bool boss)
        {
            if (Battle != null) return "A battle is already in progress.";
            if (!Party.Any(h => h.Hp > 0)) return "Your party needs to rest at the inn.";
            if (boss && Data.BossesDefeated.Contains(CurrentRegion.BossId)) return "This region's boss is already defeated.";
            return null;
        }

        public Battle StartBattle(bool boss = false)
        {
            var err = CanStartBattle(boss);
            if (err != null) throw new InvalidOperationException(err);
            var region = CurrentRegion;
            var enemies = boss ? new List<string> { region.BossId } : WorldMap.RollEncounter(region, _rng);
            Data.ActiveBattle = Battle.Create(Party, enemies, new Rng(_rng.NextULong()), region.Id);
            LastSummary = null;
            FinishIfOver();
            Commit();
            return Data.ActiveBattle;
        }

        public string ValidateCommand(BattleCommand cmd)
        {
            if (Battle == null) return "No battle.";
            if (Ledger.BankedActions <= 0) return "No actions left — go for a walk!";
            return Battle.Validate(cmd, Inventory);
        }

        /// <summary>Spends one banked action and performs the command.</summary>
        public List<BattleEvent> Command(BattleCommand cmd)
        {
            var err = ValidateCommand(cmd);
            if (err != null) throw new InvalidOperationException(err);
            Ledger.TrySpendAction();

            var events = Battle.Execute(cmd, Inventory);

            if (cmd.Kind == CommandKind.Skill) { Data.Counters.SkillsUsed++; Quests.Record(Data.Quests, QuestMetric.SkillsUsed, 1); }
            int breaks = events.Count(e => e.Kind == BattleEventKind.Break);
            Data.Counters.BreaksLanded += breaks;
            Quests.Record(Data.Quests, QuestMetric.BreaksLanded, breaks);

            FinishIfOver();
            Commit();
            return events;
        }

        void FinishIfOver()
        {
            var b = Battle;
            if (b == null || b.Outcome == BattleOutcome.Ongoing) return;

            b.WriteBack(Party);
            var summary = new BattleSummary { Outcome = b.Outcome };
            int killed = b.Enemies.Count(e => !e.IsAlive);
            Data.Counters.EnemiesDefeated += killed;
            Quests.Record(Data.Quests, QuestMetric.EnemiesDefeated, killed);

            switch (b.Outcome)
            {
                case BattleOutcome.Victory:
                    Data.Counters.BattlesWon++;
                    Quests.Record(Data.Quests, QuestMetric.BattlesWon, 1);
                    summary.CoinMultiplier = CoinMultiplier;
                    summary.Coins = (int)Math.Round(b.RewardCoins * summary.CoinMultiplier);
                    summary.Exp = b.RewardExp;
                    Inventory.Coins += summary.Coins;
                    Data.Counters.CoinsEarned += summary.Coins;
                    foreach (var h in Party)
                    {
                        // Fallen heroes still learn, at half rate.
                        int exp = h.Hp > 0 ? summary.Exp : summary.Exp / 2;
                        int lv = h.GainExp(exp);
                        if (lv > 0) summary.LevelsGained[h.Name] = lv;
                    }
                    foreach (var d in b.Drops) { Inventory.Add(d); summary.Drops.Add(d); }
                    if (b.IsBoss)
                    {
                        var bossId = b.Enemies.First(e => GameData.Enemy(e.EnemyId).IsBoss).EnemyId;
                        if (!Data.BossesDefeated.Contains(bossId)) Data.BossesDefeated.Add(bossId);
                        Data.Counters.BossesDefeated++;
                        summary.BossDefeated = true;
                        var region = GameData.Regions.Find(r => r.BossId == bossId);
                        if (region != null && region.Index + 1 < GameData.Regions.Count)
                        {
                            var next = GameData.Regions[region.Index + 1];
                            summary.RegionUnlockedHint = IsRegionUnlocked(next)
                                ? $"{next.Name} is now open!"
                                : $"{next.Name} opens after {next.UnlockKm:0} km walked.";
                        }
                    }
                    break;

                case BattleOutcome.Defeat:
                    Data.Counters.BattlesLost++;
                    summary.CoinsLost = (int)Math.Floor(Inventory.Coins * DefeatCoinPenalty);
                    Inventory.Coins -= summary.CoinsLost;
                    // Carried back to town and patched up.
                    foreach (var h in Party) { h.FullRestore(); h.Hp = Math.Max(1, h.TotalStats().MaxHp / 2); }
                    break;
            }

            Data.ActiveBattle = null;
            LastSummary = summary;
            CheckAchievements();
        }

        // ───────────────────────────── Town ─────────────────────────────

        public bool Buy(string itemId, int qty = 1)
        {
            var item = GameData.ItemOrNull(itemId);
            if (item == null || item.ShopTier > HighestRegion) return false;
            if (!Shop.Buy(Inventory, item, qty)) return false;
            Quests.Record(Data.Quests, QuestMetric.ItemsBought, qty);
            CheckAchievements();
            Commit();
            return true;
        }

        public bool Sell(string itemId)
        {
            var item = GameData.ItemOrNull(itemId);
            if (item == null || IsEquipped(itemId) && Inventory.Count(itemId) <= EquippedCount(itemId)) return false;
            if (!Shop.Sell(Inventory, item)) return false;
            Commit();
            return true;
        }

        public bool IsEquipped(string itemId) => Party.Any(h => h.WeaponId == itemId || h.ArmorId == itemId || h.AccessoryId == itemId);
        int EquippedCount(string itemId) => Party.Count(h => h.WeaponId == itemId || h.ArmorId == itemId || h.AccessoryId == itemId);

        /// <summary>Equips an item from the inventory, returning the previous one to it.</summary>
        public bool Equip(int heroIndex, string itemId)
        {
            if (Battle != null || heroIndex < 0 || heroIndex >= Party.Count) return false;
            var hero = Party[heroIndex];
            var item = GameData.ItemOrNull(itemId);
            if (item == null || !item.CanEquip(hero.Class) || !Inventory.Remove(itemId)) return false;
            string old = item.Slot == EquipSlot.Weapon ? hero.WeaponId : item.Slot == EquipSlot.Armor ? hero.ArmorId : hero.AccessoryId;
            if (old != null) Inventory.Add(old);
            hero.Equip(item);
            CheckAchievements();
            Commit();
            return true;
        }

        public bool UseItemOutOfBattle(string itemId, int heroIndex)
        {
            var item = GameData.ItemOrNull(itemId);
            if (Battle != null || item == null || !item.IsConsumable || heroIndex < 0 || heroIndex >= Party.Count) return false;
            var h = Party[heroIndex];
            var s = h.TotalStats();
            switch (item.Use)
            {
                case ConsumableEffect.HealHp: if (h.Hp <= 0 || h.Hp >= s.MaxHp) return false; h.Hp = Math.Min(s.MaxHp, h.Hp + item.UseAmount); break;
                case ConsumableEffect.HealMp: if (h.Mp >= s.MaxMp) return false; h.Mp = Math.Min(s.MaxMp, h.Mp + item.UseAmount); break;
                case ConsumableEffect.Revive: if (h.Hp > 0) return false; h.Hp = Math.Max(1, s.MaxHp * item.UseAmount / 100); break;
                default: return false;
            }
            Inventory.Remove(itemId);
            Commit();
            return true;
        }

        public int InnCost => Shop.InnCost(Party);

        public bool RestAtInn()
        {
            if (Battle != null || !Shop.RestAtInn(Inventory, Party)) return false;
            Commit();
            return true;
        }

        public bool LearnNode(int heroIndex, string nodeId)
        {
            if (heroIndex < 0 || heroIndex >= Party.Count || !Party[heroIndex].Unlock(nodeId)) return false;
            Commit();
            return true;
        }

        // ───────────────────────────── Quests ─────────────────────────────

        public IEnumerable<QuestDef> ActiveQuests
        {
            get { Quests.Refresh(Data.Quests, _clock.LocalNow); return Quests.Active(Data.Quests).Where(q => q != null).ToList(); }
        }

        public int QuestProgress(QuestDef q)
        {
            if (q.Metric == QuestMetric.Steps) return q.Period == QuestPeriod.Daily ? TodaySteps : WeekSteps;
            return Quests.ProgressOf(Data.Quests, q);
        }

        public bool IsQuestClaimed(QuestDef q) => Data.Quests.Claimed.Contains(q.Id);

        public bool ClaimQuest(string id)
        {
            var q = ActiveQuests.FirstOrDefault(x => x.Id == id);
            if (q == null || IsQuestClaimed(q) || QuestProgress(q) < q.Target) return false;
            Data.Quests.Claimed.Add(q.Id);
            Inventory.Coins += q.RewardCoins;
            Data.Counters.CoinsEarned += q.RewardCoins;
            if (q.RewardItem != null) Inventory.Add(q.RewardItem);
            CheckAchievements();
            Commit();
            return true;
        }

        // ───────────────────────────── Achievements ─────────────────────────────

        public double Metric(Metric m)
        {
            switch (m)
            {
                case Meta.Metric.TotalKm: return TotalKm;
                case Meta.Metric.TotalSteps: return Ledger.TotalSteps;
                case Meta.Metric.BestDaySteps: return Ledger.DailySteps.Count == 0 ? 0 : Ledger.DailySteps.Values.Max();
                case Meta.Metric.LongestStreak: return Math.Max(Data.Counters.LongestStreak, Streak.Current);
                case Meta.Metric.ClimbMeters: return DistanceCalc.ClimbMeters(Ledger.TotalSteps);
                case Meta.Metric.BattlesWon: return Data.Counters.BattlesWon;
                case Meta.Metric.EnemiesDefeated: return Data.Counters.EnemiesDefeated;
                case Meta.Metric.BossesDefeated: return Data.Counters.BossesDefeated;
                case Meta.Metric.BreaksLanded: return Data.Counters.BreaksLanded;
                case Meta.Metric.MaxHeroLevel: return Party.Count == 0 ? 0 : Party.Max(h => h.Level);
                case Meta.Metric.RegionsCleared: return GameData.Regions.Count(r => Data.BossesDefeated.Contains(r.BossId));
                case Meta.Metric.LegendariesOwned: return CountLegendaries();
                case Meta.Metric.ActionsEarned: return Ledger.TotalActionsEarned;
                case Meta.Metric.CoinsEarned: return Data.Counters.CoinsEarned;
                default: return 0;
            }
        }

        int CountLegendaries()
        {
            var ids = new HashSet<string>(Inventory.Items.Keys);
            foreach (var h in Party) { if (h.WeaponId != null) ids.Add(h.WeaponId); if (h.ArmorId != null) ids.Add(h.ArmorId); if (h.AccessoryId != null) ids.Add(h.AccessoryId); }
            return ids.Count(id => GameData.ItemOrNull(id)?.Rarity == Rarity.Legendary);
        }

        public double AchievementProgress(AchievementDef a) => Achievements.Progress(a, Metric);
        public bool IsUnlocked(AchievementDef a) => Data.Achievements.ContainsKey(a.Id);
        public List<string> Comparisons() => Achievements.Comparisons(TotalKm, DistanceCalc.ClimbMeters(Ledger.TotalSteps));

        void CheckAchievements()
        {
            Data.Counters.LongestStreak = Math.Max(Data.Counters.LongestStreak,
                Math.Max(Streak.Current, Streaks.Longest(Ledger.DailySteps, Data.Settings.DailyStepGoal)));
            var newly = Achievements.Evaluate(Metric, Data.Achievements, _clock.UtcNow);
            if (newly.Count == 0) return;
            foreach (var a in newly) Inventory.Coins += a.RewardCoins;
            AchievementsUnlocked?.Invoke(newly);
        }

        // ───────────────────────────── Settings ─────────────────────────────

        public void SetHeight(int heightCm)
        {
            Data.Settings.HeightCm = heightCm;
            Data.Settings.StrideMeters = DistanceCalc.StrideFromHeightCm(heightCm);
            CheckAchievements();
            Commit();
        }

        public void SetStepGoal(int goal)
        {
            Data.Settings.DailyStepGoal = Math.Max(1000, Math.Min(50_000, goal));
            Commit();
        }

        void Commit()
        {
            Data.RngState = _rng.State;
            Changed?.Invoke();
        }
    }
}
