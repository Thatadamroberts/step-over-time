using System;
using System.Collections.Generic;
using StepQuest.Core.Data;
using StepQuest.Core.Economy;

namespace StepQuest.Core.Party
{
    [Serializable]
    public class Hero
    {
        public string Name;
        public HeroClass Class;
        public int Level = 1;
        public int Exp;
        public int Hp;
        public int Mp;
        public int SkillPoints;
        public List<string> UnlockedNodes = new List<string>();
        public string WeaponId;
        public string ArmorId;
        public string AccessoryId;

        public Hero() { }

        public Hero(string name, HeroClass cls)
        {
            Name = name;
            Class = cls;
            var s = TotalStats();
            Hp = s.MaxHp;
            Mp = s.MaxMp;
        }

        public ClassDef Def => GameData.Class(Class);
        public bool IsAlive => Hp > 0;

        public Stats BaseStats() => Def.Base + Def.Growth * (Level - 1);

        public Stats TotalStats()
        {
            var s = BaseStats();
            foreach (var nodeId in UnlockedNodes)
            {
                var node = Def.Tree.Find(n => n.Id == nodeId);
                if (node != null) s = s + node.Bonus;
            }
            foreach (var id in new[] { WeaponId, ArmorId, AccessoryId })
            {
                var item = GameData.ItemOrNull(id);
                if (item != null) s = s + item.Bonus;
            }
            return s;
        }

        public IEnumerable<SkillDef> Skills()
        {
            foreach (var id in Def.StartingSkills) yield return GameData.Skill(id);
            foreach (var nodeId in UnlockedNodes)
            {
                var node = Def.Tree.Find(n => n.Id == nodeId);
                if (node?.UnlocksSkill != null) yield return GameData.Skill(node.UnlocksSkill);
            }
        }

        /// <summary>Adds EXP; returns the number of levels gained.</summary>
        public int GainExp(int amount)
        {
            if (amount <= 0 || Level >= Leveling.MaxLevel) return 0;
            Exp += amount;
            int gained = 0;
            while (Level < Leveling.MaxLevel && Exp >= Leveling.ExpToNext(Level))
            {
                var before = TotalStats();
                Exp -= Leveling.ExpToNext(Level);
                Level++;
                gained++;
                SkillPoints += Leveling.SkillPointsForLevel(Level);
                var after = TotalStats();
                // Level-ups also restore the HP/MP that was gained.
                Hp += after.MaxHp - before.MaxHp;
                Mp += after.MaxMp - before.MaxMp;
            }
            if (Level >= Leveling.MaxLevel) Exp = 0;
            return gained;
        }

        public bool CanUnlock(string nodeId, out string reason)
        {
            var node = Def.Tree.Find(n => n.Id == nodeId);
            reason = null;
            if (node == null) { reason = "Unknown node"; return false; }
            if (UnlockedNodes.Contains(nodeId)) { reason = "Already learned"; return false; }
            if (node.Requires != null && !UnlockedNodes.Contains(node.Requires)) { reason = "Requires an earlier node"; return false; }
            if (Level < node.MinLevel) { reason = $"Requires level {node.MinLevel}"; return false; }
            if (SkillPoints < node.Cost) { reason = "Not enough skill points"; return false; }
            return true;
        }

        public bool Unlock(string nodeId)
        {
            if (!CanUnlock(nodeId, out _)) return false;
            var node = Def.Tree.Find(n => n.Id == nodeId);
            var before = TotalStats();
            SkillPoints -= node.Cost;
            UnlockedNodes.Add(nodeId);
            var after = TotalStats();
            Hp += Math.Max(0, after.MaxHp - before.MaxHp);
            Mp += Math.Max(0, after.MaxMp - before.MaxMp);
            return true;
        }

        public void Equip(ItemDef item)
        {
            if (item == null) return;
            switch (item.Slot)
            {
                case EquipSlot.Weapon: WeaponId = item.Id; break;
                case EquipSlot.Armor: ArmorId = item.Id; break;
                case EquipSlot.Accessory: AccessoryId = item.Id; break;
                default: return;
            }
            ClampVitals();
        }

        public void ClampVitals()
        {
            var s = TotalStats();
            Hp = Math.Min(Hp, s.MaxHp);
            Mp = Math.Min(Mp, s.MaxMp);
        }

        public void FullRestore()
        {
            var s = TotalStats();
            Hp = s.MaxHp;
            Mp = s.MaxMp;
        }
    }
}
