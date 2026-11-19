using StepQuest.Core.Data;
using StepQuest.Core.World;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI.Screens
{
    /// <summary>The journey: regions unlock by distance walked and by defeating the previous boss.</summary>
    public sealed class MapScreen : ScreenBase
    {
        VisualElement _list;
        Label _summary;
        VisualElement _journeyBar;

        protected override void Build(VisualElement root)
        {
            var sv = Scroll(root);
            var head = UIx.Div("panel");
            head.Add(UIx.Text("The Long Road", "title"));
            _summary = UIx.Text("", "small", "wrap-text");
            head.Add(_summary);
            _journeyBar = UIx.Bar(0, null, "thick");
            head.Add(_journeyBar);
            sv.Add(head);
            _list = UIx.Div("col");
            sv.Add(_list);
        }

        public override void Refresh()
        {
            double km = S.TotalKm;
            RegionDef next = null;
            foreach (var r in GameData.Regions) if (!S.IsRegionUnlocked(r)) { next = r; break; }
            var last = GameData.Regions[GameData.Regions.Count - 1];
            _summary.text = next == null
                ? $"You've walked {UIx.Km(km)}. Every region is open to you."
                : $"You've walked {UIx.Km(km)}. Next: {next.Name} — {WorldMap.LockReason(next, km, S.Data.BossesDefeated)}.";
            UIx.SetBar(_journeyBar, (float)(km / last.UnlockKm));

            _list.Clear();
            foreach (var r in GameData.Regions)
            {
                bool unlocked = S.IsRegionUnlocked(r);
                bool here = S.CurrentRegion.Id == r.Id;
                bool bossDown = S.Data.BossesDefeated.Contains(r.BossId);
                var card = UIx.Div("card");
                if (!unlocked) card.AddToClassList("locked");
                if (here) card.AddToClassList("done");

                var top = UIx.Div("row");
                var name = UIx.Text($"{r.Index + 1}. {r.Name}", "h3");
                name.style.flexGrow = 1;
                top.Add(name);
                top.Add(UIx.Text($"Lv {r.RecommendedLevel}+", "small", "muted"));
                card.Add(top);
                card.Add(UIx.Text(r.Description, "small", "muted", "wrap-text"));

                var boss = GameData.Enemy(r.BossId);
                var bottom = UIx.Div("row");
                var status = UIx.Text(bossDown ? $"Defeated {boss.Name}" : $"Boss: {boss.Name}", "tiny", bossDown ? "good" : "muted");
                status.style.flexGrow = 1;
                bottom.Add(status);
                if (!unlocked)
                    {
                    bottom.Add(UIx.Icon("lock", 32));
                    bottom.Add(UIx.Text(" " + WorldMap.LockReason(r, S.TotalKm, S.Data.BossesDefeated), "tiny", "muted"));
                }
                else if (here)
                    bottom.Add(UIx.Text("You are here", "tiny", "gold"));
                else
                {
                    var id = r.Id;
                    var travel = UIx.Btn("Travel", () =>
                    {
                        if (S.TravelTo(id)) { UI.Toast("Arrived at " + GameData.Region(id).Name); UI.Show(ScreenId.Home); }
                        else UI.Toast("Finish your battle first.");
                    }, "small");
                    bottom.Add(travel);
                }
                card.Add(bottom);
                _list.Add(card);
            }
        }
    }
}
