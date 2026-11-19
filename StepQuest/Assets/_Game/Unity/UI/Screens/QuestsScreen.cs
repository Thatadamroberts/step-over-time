using System.Linq;
using StepQuest.Core.Data;
using StepQuest.Core.Meta;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI.Screens
{
    public sealed class QuestsScreen : ScreenBase
    {
        ScrollView _sv;
        protected override void Build(VisualElement root) => _sv = Scroll(root);

        public override void Refresh()
        {
            _sv.Clear();
            _sv.Add(UIx.Text("Quests", "title"));

            var streak = S.Streak;
            var sp = UIx.Div("panel", "glow");
            var row = UIx.Div("row");
            row.Add(UIx.Icon("flame", 112));
            var col = UIx.Div("col"); col.style.flexGrow = 1; col.style.marginLeft = 16;
            col.Add(UIx.Text(streak.Current > 0 ? $"{streak.Current}-day streak" : "No streak yet", "h2"));
            col.Add(UIx.Text(streak.TodayDone
                ? "Today's goal reached — see you tomorrow!"
                : $"Walk {UIx.N(System.Math.Max(0, S.Data.Settings.DailyStepGoal - S.TodaySteps))} more steps today to keep it going.", "small", "wrap-text"));
            col.Add(UIx.Text($"Battle coins ×{S.CoinMultiplier:0.00} (max ×{Streaks.CoinMultiplier(Streaks.MaxBonusDays):0.0}) · One missed day a week is forgiven{(streak.ShieldsUsed > 0 ? $" ({streak.ShieldsUsed} used)" : "")}", "tiny", "muted", "wrap-text"));
            row.Add(col);
            sp.Add(row);
            _sv.Add(sp);

            Section("Daily", S.ActiveQuests.Where(q => q.Period == QuestPeriod.Daily));
            Section("Weekly", S.ActiveQuests.Where(q => q.Period == QuestPeriod.Weekly));
        }

        void Section(string title, System.Collections.Generic.IEnumerable<QuestDef> quests)
        {
            _sv.Add(UIx.Text(title, "h2"));
            foreach (var q in quests)
            {
                int progress = S.QuestProgress(q);
                bool claimed = S.IsQuestClaimed(q);
                bool done = progress >= q.Target;
                var card = UIx.Div("card");
                if (claimed) card.AddToClassList("done");
                var top = UIx.Div("row");
                var t = UIx.Text(q.Text, "h3", "wrap-text"); t.style.flexGrow = 1; t.style.flexShrink = 1;
                top.Add(t);
                if (claimed) { top.Add(UIx.Icon("check", 36)); top.Add(UIx.Text(" Claimed", "small", "good")); }
                else
                {
                    var id = q.Id;
                    var b = UIx.Btn("Claim", () => { if (S.ClaimQuest(id)) UI.Toast("Quest complete!", Reward(q)); }, "small", "primary");
                    b.SetEnabled(done);
                    top.Add(b);
                }
                card.Add(top);
                card.Add(UIx.Bar(progress / (float)q.Target, done ? "good" : null));
                var bottom = UIx.Div("row");
                var p = UIx.Text($"{UIx.N(System.Math.Min(progress, q.Target))} / {UIx.N(q.Target)}", "tiny", "muted"); p.style.flexGrow = 1;
                bottom.Add(p);
                bottom.Add(UIx.Text(Reward(q), "tiny", "gold"));
                card.Add(bottom);
                _sv.Add(card);
            }
        }

        static string Reward(QuestDef q)
        {
            var item = GameData.ItemOrNull(q.RewardItem);
            return $"+{q.RewardCoins} coins" + (item != null ? $" + {item.Name}" : "");
        }
    }
}
