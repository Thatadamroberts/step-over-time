using System.Linq;
using StepQuest.Core.Data;
using StepQuest.Core.Economy;
using StepQuest.Core.Party;
using StepQuest.Unity.Art;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI.Screens
{
    /// <summary>Party overview, and per-hero detail: stats, equipment, skill tree and items.</summary>
    public sealed class PartyScreen : ScreenBase
    {
        ScrollView _sv;
        int _selected = -1;

        protected override void Build(VisualElement root) => _sv = Scroll(root);

        public override void OnShow() => _selected = -1;

        public override void Refresh()
        {
            _sv.Clear();
            if (_selected < 0) ShowList(); else ShowHero(_selected);
        }

        void ShowList()
        {
            _sv.Add(UIx.Text("Party", "title"));
            for (int i = 0; i < S.Party.Count; i++)
            {
                var h = S.Party[i];
                var s = h.TotalStats();
                int index = i;
                var card = UIx.Div("card", "row");
                card.RegisterCallback<ClickEvent>(_ => { _selected = index; Refresh(); });
                card.Add(UIx.Image(PixelArt.Hero(h.Class), "portrait"));
                var info = UIx.Div("col");
                info.style.flexGrow = 1;
                var top = UIx.Div("row");
                var name = UIx.Text(h.Name, "h3"); name.style.flexGrow = 1;
                top.Add(name);
                top.Add(UIx.Text($"Lv {h.Level}", "h3", "gold"));
                info.Add(top);
                info.Add(UIx.Text(h.Def.Name + (h.SkillPoints > 0 ? $"  ·  {h.SkillPoints} skill point{(h.SkillPoints > 1 ? "s" : "")} to spend!" : ""), "small", h.SkillPoints > 0 ? "good" : "muted"));
                info.Add(LabeledBar("HP", h.Hp, s.MaxHp, h.Hp < s.MaxHp * 0.3f ? "hp low" : "hp"));
                info.Add(LabeledBar("MP", h.Mp, s.MaxMp, "mp"));
                int need = Leveling.ExpToNext(h.Level);
                info.Add(LabeledBar("EXP", h.Exp, need == int.MaxValue ? 1 : need, "exp"));
                card.Add(info);
                _sv.Add(card);
            }
        }

        static VisualElement LabeledBar(string label, int value, int max, string cls)
        {
            var row = UIx.Div("row");
            row.style.marginTop = 4;
            var l = UIx.Text(label, "tiny", "muted"); l.style.width = 70;
            row.Add(l);
            var bar = UIx.Bar(max <= 0 ? 0 : value / (float)max, cls);
            bar.style.flexGrow = 1;
            row.Add(bar);
            var n = UIx.Text($"{value}/{max}", "tiny", "muted"); n.style.width = 160; n.AddToClassList("right");
            row.Add(n);
            return row;
        }

        void ShowHero(int index)
        {
            var h = S.Party[index];
            var s = h.TotalStats();
            _sv.Add(UIx.Btn("< Party", () => { _selected = -1; Refresh(); }, "small", "ghost"));

            var head = UIx.Div("panel", "row");
            head.Add(UIx.Image(PixelArt.Hero(h.Class), "portrait"));
            var info = UIx.Div("col"); info.style.flexGrow = 1;
            info.Add(UIx.Text(h.Name, "title"));
            info.Add(UIx.Text($"Level {h.Level} {h.Def.Name}", "h3", "gold"));
            info.Add(UIx.Text(h.Def.Blurb, "small", "muted", "wrap-text"));
            info.Add(LabeledBar("HP", h.Hp, s.MaxHp, "hp"));
            info.Add(LabeledBar("MP", h.Mp, s.MaxMp, "mp"));
            head.Add(info);
            _sv.Add(head);

            var stats = UIx.Div("panel", "inset");
            stats.Add(UIx.Text("Stats", "h2"));
            var grid = UIx.Div("stat-grid");
            void Cell(string n, int v) { var c = UIx.Div("stat-cell"); c.Add(UIx.Text(n, "small", "muted")); c.Add(UIx.Text(v.ToString(), "small")); grid.Add(c); }
            Cell("ATK", s.Atk); Cell("DEF", s.Def); Cell("MAG", s.Mag); Cell("RES", s.Res); Cell("SPD", s.Spd); Cell("CRIT", s.Crit);
            stats.Add(grid);
            _sv.Add(stats);

            // Equipment
            var eq = UIx.Div("panel");
            eq.Add(UIx.Text("Equipment", "h2"));
            AddSlot(eq, index, EquipSlot.Weapon, h.WeaponId);
            AddSlot(eq, index, EquipSlot.Armor, h.ArmorId);
            AddSlot(eq, index, EquipSlot.Accessory, h.AccessoryId);
            if (S.Battle != null) eq.Add(UIx.Text("Gear can't be changed mid-battle.", "tiny", "muted"));
            _sv.Add(eq);

            // Skills
            var sk = UIx.Div("panel");
            sk.Add(UIx.Text("Skills", "h2"));
            foreach (var skill in h.Skills())
            {
                var row = UIx.Div("row");
                var n = UIx.Text(skill.Name, "h3"); n.style.width = 300;
                row.Add(n);
                row.Add(UIx.Text($"{skill.MpCost} MP · {skill.Description}", "small", "muted", "wrap-text"));
                sk.Add(row);
            }
            _sv.Add(sk);

            // Skill tree
            var tree = UIx.Div("panel");
            var th = UIx.Div("row");
            var tt = UIx.Text("Skill tree", "h2"); tt.style.flexGrow = 1;
            th.Add(tt);
            th.Add(UIx.Text($"{h.SkillPoints} SP", "h3", h.SkillPoints > 0 ? "good" : "muted"));
            tree.Add(th);
            foreach (var node in h.Def.Tree)
            {
                bool learned = h.UnlockedNodes.Contains(node.Id);
                bool can = h.CanUnlock(node.Id, out var reason);
                var card = UIx.Div("card");
                if (learned) card.AddToClassList("done");
                else if (!can) card.AddToClassList("locked");
                var row = UIx.Div("row");
                var col = UIx.Div("col"); col.style.flexGrow = 1;
                var req = node.Requires != null ? " · needs " + h.Def.Tree.First(n => n.Id == node.Requires).Name : "";
                col.Add(UIx.Text(node.Name + (node.UnlocksSkill != null ? " (new skill)" : ""), "h3"));
                col.Add(UIx.Text($"{node.Description} · Lv {node.MinLevel} · {node.Cost} SP{req}", "tiny", "muted", "wrap-text"));
                row.Add(col);
                if (learned) row.Add(UIx.Text("Learned", "small", "good"));
                else
                {
                    var id = node.Id;
                    var b = UIx.Btn("Learn", () => { if (S.LearnNode(index, id)) UI.Toast($"{h.Name} learned {node.Name}!"); }, "small");
                    b.SetEnabled(can);
                    if (!can) b.tooltip = reason;
                    row.Add(b);
                }
                card.Add(row);
                tree.Add(card);
            }
            _sv.Add(tree);

            // Items (out of battle)
            var items = UIx.Div("panel");
            items.Add(UIx.Text("Use item", "h2"));
            int shown = 0;
            foreach (var kv in S.Inventory.Items.OrderBy(k => k.Key))
            {
                var item = GameData.ItemOrNull(kv.Key);
                if (item == null || !item.IsConsumable || item.Use == ConsumableEffect.Cleanse) continue;
                var id = item.Id;
                var b = UIx.Btn($"{item.Name} ×{kv.Value}", () => { if (!S.UseItemOutOfBattle(id, index)) UI.Toast("It would have no effect."); }, "small");
                b.SetEnabled(S.Battle == null);
                items.Add(b);
                shown++;
            }
            if (shown == 0) items.Add(UIx.Text("No usable items. Buy some in the shop.", "small", "muted"));
            _sv.Add(items);
        }

        void AddSlot(VisualElement parent, int heroIndex, EquipSlot slot, string itemId)
        {
            var item = GameData.ItemOrNull(itemId);
            var row = UIx.Div("card", "row");
            var col = UIx.Div("col"); col.style.flexGrow = 1;
            col.Add(UIx.Text(slot.ToString().ToUpperInvariant(), "tiny", "muted"));
            col.Add(UIx.Text(item?.Name ?? "— empty —", "h3", item != null ? "rarity-" + item.Rarity : "muted"));
            if (item != null) col.Add(UIx.Text(StatLine(item.Bonus), "tiny", "muted"));
            row.Add(col);
            var b = UIx.Btn("Change", () => ChooseGear(heroIndex, slot), "small");
            b.SetEnabled(S.Battle == null);
            row.Add(b);
            parent.Add(row);
        }

        void ChooseGear(int heroIndex, EquipSlot slot)
        {
            var hero = S.Party[heroIndex];
            var current = GameData.ItemOrNull(slot == EquipSlot.Weapon ? hero.WeaponId : slot == EquipSlot.Armor ? hero.ArmorId : hero.AccessoryId);
            var list = UIx.Div("col");
            VisualElement modal = null;
            foreach (var kv in S.Inventory.Items)
            {
                var item = GameData.ItemOrNull(kv.Key);
                if (item == null || item.Slot != slot || !item.CanEquip(hero.Class)) continue;
                var card = UIx.Div("card", "row");
                var col = UIx.Div("col"); col.style.flexGrow = 1;
                col.Add(UIx.Text($"{item.Name} ×{kv.Value}", "h3", "rarity-" + item.Rarity));
                col.Add(UIx.Text(DiffLine(item.Bonus, current?.Bonus ?? default), "small", "wrap-text"));
                card.Add(col);
                var id = item.Id;
                card.Add(UIx.Btn("Equip", () => { if (S.Equip(heroIndex, id)) { UI.Toast($"{hero.Name} equipped {item.Name}"); modal?.RemoveFromHierarchy(); } }, "small", "primary"));
                list.Add(card);
            }
            if (list.childCount == 0) list.Add(UIx.Text($"No {slot.ToString().ToLowerInvariant()} in your bags that a {hero.Def.Name} can use. Visit the shop!", "small", "muted", "wrap-text"));
            modal = UI.Modal($"{hero.Name}: {slot}", list);
        }

        public static string StatLine(Stats s)
        {
            var parts = new System.Collections.Generic.List<string>();
            foreach (StatType t in System.Enum.GetValues(typeof(StatType)))
            {
                int v = s.Get(t);
                if (v != 0) parts.Add($"{Short(t)} {(v > 0 ? "+" : "")}{v}");
            }
            return parts.Count == 0 ? "—" : string.Join("  ", parts);
        }

        static string DiffLine(Stats next, Stats cur)
        {
            var parts = new System.Collections.Generic.List<string>();
            foreach (StatType t in System.Enum.GetValues(typeof(StatType)))
            {
                int d = next.Get(t) - cur.Get(t);
                if (d != 0) parts.Add($"{Short(t)} {(d > 0 ? "+" : "-")}{System.Math.Abs(d)}");
            }
            return parts.Count == 0 ? "No change" : string.Join("  ", parts);
        }

        static string Short(StatType t)
        {
            switch (t)
            {
                case StatType.MaxHp: return "HP";
                case StatType.MaxMp: return "MP";
                default: return t.ToString().ToUpperInvariant();
            }
        }
    }
}
