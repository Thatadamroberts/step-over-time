using System;
using System.Collections.Generic;
using StepQuest.Core.Party;

namespace StepQuest.Core.Economy
{
    public enum EquipSlot { None, Weapon, Armor, Accessory }

    public enum Rarity { Common, Uncommon, Rare, Epic, Legendary }

    public enum ConsumableEffect { None, HealHp, HealMp, Revive, Cleanse }

    public sealed class ItemDef
    {
        public string Id;
        public string Name;
        public string Description;
        public EquipSlot Slot;
        public Rarity Rarity;
        public int Price;
        public Stats Bonus;
        /// <summary>Which classes may equip it (empty = any).</summary>
        public HeroClass[] Classes = Array.Empty<HeroClass>();
        /// <summary>Region index at which it appears in the shop (-1 = never sold).</summary>
        public int ShopTier = -1;
        public ConsumableEffect Use;
        public int UseAmount;

        public bool IsConsumable => Slot == EquipSlot.None && Use != ConsumableEffect.None;
        public bool CanEquip(HeroClass c) => Slot != EquipSlot.None && (Classes.Length == 0 || Array.IndexOf(Classes, c) >= 0);
    }

    [Serializable]
    public class Inventory
    {
        public int Coins;
        public Dictionary<string, int> Items = new Dictionary<string, int>();

        public int Count(string id) => Items.TryGetValue(id, out var n) ? n : 0;

        public void Add(string id, int n = 1)
        {
            if (n <= 0) return;
            Items[id] = Count(id) + n;
        }

        public bool Remove(string id, int n = 1)
        {
            int have = Count(id);
            if (have < n) return false;
            if (have == n) Items.Remove(id); else Items[id] = have - n;
            return true;
        }

        public bool Spend(int coins)
        {
            if (coins < 0 || Coins < coins) return false;
            Coins -= coins;
            return true;
        }
    }

    public static class Shop
    {
        public const int InnCostPerLevel = 2;

        public static IEnumerable<ItemDef> Stock(int highestRegionUnlocked)
        {
            foreach (var item in Data.GameData.Items.Values)
                if (item.ShopTier >= 0 && item.ShopTier <= highestRegionUnlocked)
                    yield return item;
        }

        public static bool Buy(Inventory inv, ItemDef item, int qty = 1)
        {
            if (item == null || qty <= 0 || item.ShopTier < 0) return false;
            if (!inv.Spend(item.Price * qty)) return false;
            inv.Add(item.Id, qty);
            return true;
        }

        public static int SellPrice(ItemDef item) => item.Price / 2;

        public static bool Sell(Inventory inv, ItemDef item)
        {
            if (!inv.Remove(item.Id)) return false;
            inv.Coins += SellPrice(item);
            return true;
        }

        public static int InnCost(IEnumerable<Hero> party)
        {
            int total = 0;
            foreach (var h in party) total += h.Level * InnCostPerLevel;
            return total;
        }

        public static bool RestAtInn(Inventory inv, IList<Hero> party)
        {
            if (!inv.Spend(InnCost(party))) return false;
            foreach (var h in party) h.FullRestore();
            return true;
        }
    }

    public sealed class LootEntry
    {
        public string ItemId;
        public double Chance;
        public LootEntry(string id, double chance) { ItemId = id; Chance = chance; }
    }
}
