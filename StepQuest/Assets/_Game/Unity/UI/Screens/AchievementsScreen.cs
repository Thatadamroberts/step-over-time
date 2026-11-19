using System.Linq;
using StepQuest.Core.Meta;
using StepQuest.Core.Steps;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI.Screens
{
    /// <summary>Walking milestones and real-world comparisons, led by the long road to the Moon.</summary>
    public sealed class AchievementsScreen : ScreenBase
    {
        ScrollView _sv;
        AchievementCategory? _filter;

        protected override void Build(VisualElement root) => _sv = Scroll(root);

        public override void Refresh()
        {
            _sv.Clear();
            _sv.Add(UIx.Text("Trophies", "title"));

            // Moon hero card
            double km = S.TotalKm;
            double moon = km / Achievements.MoonKm;
            var hero = UIx.Div("panel", "glow", "row");
            var moonIcon = UIx.Div("moon");
            var fill = UIx.Div("moon-fill");
            fill.style.height = Length.Percent((float)System.Math.Min(100, System.Math.Max(2, moon * 100)));
            moonIcon.Add(fill);
            hero.Add(moonIcon);
            var col = UIx.Div("col"); col.style.flexGrow = 1; col.style.flexShrink = 1;
            col.Add(UIx.Text("Journey to the Moon", "h2"));
            col.Add(UIx.Text(Achievements.Percent(moon), "big-number"));
            col.Add(UIx.Text($"{UIx.Km(km)} of 384,400 km", "small", "muted"));
            hero.Add(col);
            _sv.Add(hero);

            // Lifetime stats
            var stats = UIx.Div("panel", "inset");
            var grid = UIx.Div("stat-grid");
            void Cell(string n, string v) { var c = UIx.Div("col"); c.style.width = Length.Percent(50); c.style.marginBottom = 10; c.Add(UIx.Text(n, "tiny", "muted")); c.Add(UIx.Text(v, "h3")); grid.Add(c); }
            Cell("Total steps", UIx.N(S.Ledger.TotalSteps));
            Cell("Distance", UIx.Km(km));
            Cell("Best day", UIx.N((long)S.Metric(Metric.BestDaySteps)) + " steps");
            Cell("Longest streak", $"{(int)S.Metric(Metric.LongestStreak)} days");
            Cell("Climbed (as stairs)", $"{DistanceCalc.ClimbMeters(S.Ledger.TotalSteps):#,0} m");
            Cell("Actions earned", UIx.N(S.Ledger.TotalActionsEarned));
            Cell("Battles won", UIx.N(S.Data.Counters.BattlesWon));
            Cell("Unlocked", $"{S.Data.Achievements.Count} / {Achievements.All.Count}");
            stats.Add(grid);
            _sv.Add(stats);

            // Comparisons
            var comp = UIx.Div("panel");
            comp.Add(UIx.Text("Did you know?", "h2"));
            foreach (var line in S.Comparisons()) comp.Add(UIx.Text("• " + line, "small", "wrap-text"));
            _sv.Add(comp);

            // Filter tabs
            var tabs = UIx.Div("row", "wrap");
            AddTab(tabs, "All", null);
            foreach (AchievementCategory c in System.Enum.GetValues(typeof(AchievementCategory))) AddTab(tabs, c.ToString(), c);
            _sv.Add(tabs);

            // Unlocked first, then closest to completion.
            foreach (var a in Achievements.All.Where(a => _filter == null || a.Category == _filter)
                         .OrderBy(a => S.IsUnlocked(a) ? 0 : 1).ThenByDescending(a => S.AchievementProgress(a)))
                _sv.Add(Row(a));
        }

        void AddTab(VisualElement parent, string label, AchievementCategory? cat)
        {
            var b = UIx.Btn(label, () => { _filter = cat; Refresh(); }, "small");
            if (_filter == cat) b.AddToClassList("selected");
            parent.Add(b);
        }

        VisualElement Row(AchievementDef a)
        {
            bool done = S.IsUnlocked(a);
            double p = S.AchievementProgress(a);
            var card = UIx.Div("card");
            if (done) card.AddToClassList("done");
            var top = UIx.Div("row");
            top.Add(UIx.Icon(done ? "star" : "star_empty", 56));
            var col = UIx.Div("col"); col.style.flexGrow = 1; col.style.flexShrink = 1; col.style.marginLeft = 12;
            col.Add(UIx.Text(a.Name, "h3"));
            col.Add(UIx.Text(a.Description, "tiny", "muted", "wrap-text"));
            top.Add(col);
            top.Add(UIx.Text($"+{UIx.N(a.RewardCoins)}c", "tiny", "gold"));
            card.Add(top);
            if (!done)
            {
                card.Add(UIx.Bar((float)p, null, "thin"));
                card.Add(UIx.Text(Progress(a, p), "tiny", "muted"));
            }
            return card;
        }

        string Progress(AchievementDef a, double p)
        {
            if (a.ShowPercent) return Achievements.Percent(p) + " complete";
            double v = S.Metric(a.Metric);
            switch (a.Metric)
            {
                case Metric.TotalKm: return $"{UIx.Km(v)} / {UIx.Km(a.Threshold)}";
                case Metric.ClimbMeters: return $"{v:#,0} / {a.Threshold:#,0} m";
                default: return $"{v:#,0} / {a.Threshold:#,0}";
            }
        }
    }
}
