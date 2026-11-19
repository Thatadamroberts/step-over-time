using System.Linq;
using StepQuest.Core.Data;
using StepQuest.Core.Economy;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI.Screens
{
    public sealed class ShopScreen : ScreenBase
    {
        enum Tab { Buy, Sell, Inn }
        Tab _tab = Tab.Buy;
        ScrollView _sv;
        VisualElement _tabs;

        protected override void Build(VisualElement root)
        {
            root.Add(UIx.Text("Market & Inn", "title"));
            _tabs = UIx.Div("tab-row");
            root.Add(_tabs);
            _sv = Scroll(root);
        }

        public override void Refresh()
        {
            _tabs.Clear();
            foreach (Tab t in System.Enum.GetValues(typeof(Tab)))
            {
                var tab = t;
                var b = UIx.Btn(t.ToString(), () => { _tab = tab; Refresh(); }, "tab");
                if (t == _tab) b.AddToClassList("selected");
                _tabs.Add(b);
            }
            _sv.Clear();
            switch (_tab)
            {
                case Tab.Buy: BuildBuy(); break;
                case Tab.Sell: BuildSell(); break;
                default: BuildInn(); break;
            }
        }

        void BuildBuy()
        {
            int tier = S.HighestRegion;
            _sv.Add(UIx.Text($"New stock arrives as you reach new regions.", "small", "muted", "wrap-text"));
            foreach (var group in Shop.Stock(tier).GroupBy(i => i.Slot).OrderBy(g => g.Key))
            {
                _sv.Add(UIx.Text(group.Key == EquipSlot.None ? "Supplies" : group.Key + "s", "h2"));
                foreach (var item in group.OrderBy(i => i.Price))
                {
                    var card = UIx.Div("card", "row");
                    var col = UIx.Div("col"); col.style.flexGrow = 1;
                    col.Add(UIx.Text(item.Name, "h3", "rarity-" + item.Rarity));
                    string who = item.Classes.Length == 0 ? "" : "  ·  " + string.Join("/", item.Classes);
                    string detail = item.IsConsumable ? item.Description : PartyScreen.StatLine(item.Bonus) + who;
                    col.Add(UIx.Text(detail, "tiny", "muted", "wrap-text"));
                    int owned = S.Inventory.Count(item.Id) + S.Party.Count(h => h.WeaponId == item.Id || h.ArmorId == item.Id || h.AccessoryId == item.Id);
                    if (owned > 0) col.Add(UIx.Text($"Owned: {owned}", "tiny", "gold"));
                    card.Add(col);
                    var id = item.Id;
                    var b = UIx.Btn($"{UIx.N(item.Price)}c", () => { if (S.Buy(id)) UI.Toast("Bought " + item.Name); else UI.Toast("Not enough coins"); }, "small", "primary");
                    b.SetEnabled(S.Inventory.Coins >= item.Price);
                    card.Add(b);
                    _sv.Add(card);
                }
            }
        }

        void BuildSell()
        {
            bool any = false;
            foreach (var kv in S.Inventory.Items.OrderBy(k => k.Key))
            {
                var item = GameData.ItemOrNull(kv.Key);
                if (item == null) continue;
                any = true;
                var card = UIx.Div("card", "row");
                var col = UIx.Div("col"); col.style.flexGrow = 1;
                col.Add(UIx.Text($"{item.Name} ×{kv.Value}", "h3", "rarity-" + item.Rarity));
                col.Add(UIx.Text(item.IsConsumable ? item.Description : PartyScreen.StatLine(item.Bonus), "tiny", "muted"));
                card.Add(col);
                var id = item.Id;
                int price = Shop.SellPrice(item);
                var b = UIx.Btn($"Sell {UIx.N(price)}c", () =>
                {
                    if (item.Rarity >= Rarity.Rare) UI.Confirm("Sell " + item.Name + "?", "Rare items are hard to replace.", "Sell", () => S.Sell(id), "danger");
                    else S.Sell(id);
                }, "small");
                b.SetEnabled(price > 0);
                card.Add(b);
                _sv.Add(card);
            }
            if (!any) _sv.Add(UIx.Text("Your bags are empty. Equipped gear isn't shown here.", "small", "muted", "wrap-text"));
        }

        void BuildInn()
        {
            var panel = UIx.Div("panel");
            panel.Add(UIx.Text("The Wandering Boot Inn", "h2"));
            panel.Add(UIx.Text("A warm meal and a soft bed restore all HP and MP and revive the fallen.", "small", "wrap-text"));
            foreach (var h in S.Party)
            {
                var s = h.TotalStats();
                panel.Add(UIx.Text($"{h.Name}: {h.Hp}/{s.MaxHp} HP · {h.Mp}/{s.MaxMp} MP", "small", h.Hp <= 0 ? "bad" : "muted"));
            }
            var b = UIx.Btn($"Rest ({UIx.N(S.InnCost)} coins)", () =>
            {
                if (S.RestAtInn()) UI.Toast("Fully rested!");
                else UI.Toast(S.Battle != null ? "Finish your battle first." : "Not enough coins");
            }, "primary", "wide");
            panel.Add(b);
            _sv.Add(panel);
        }
    }
}
