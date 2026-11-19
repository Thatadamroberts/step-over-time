using StepQuest.Core.Data;
using StepQuest.Core.Steps;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI.Screens
{
    /// <summary>Town hub: the party idles in the 3D region while the lower half shows walking progress and actions.</summary>
    public sealed class HomeScreen : ScreenBase
    {
        Label _region, _regionDesc, _actionsBig, _toNext, _todayLabel, _distance, _streakLine;
        VisualElement _nextBar, _todayBar, _buttons, _warning, _debug;

        protected override void Build(VisualElement root)
        {
            root.Add(UIx.Div("spacer")); // the 3D diorama shows through the top half

            var banner = UIx.Div("banner");
            _region = UIx.Text("", "h2");
            _regionDesc = UIx.Text("", "small", "muted", "wrap-text");
            banner.Add(_region);
            banner.Add(_regionDesc);
            root.Add(banner);

            _warning = UIx.Div("panel", "glow");
            root.Add(_warning);

            var actions = UIx.Div("panel");
            var row = UIx.Div("row");
            var left = UIx.Div("col");
            left.style.flexGrow = 1;
            left.Add(UIx.Text("ACTIONS READY", "tiny", "muted"));
            _actionsBig = UIx.Text("0", "big-number");
            left.Add(_actionsBig);
            row.Add(left);
            var right = UIx.Div("col");
            right.style.flexGrow = 2;
            _toNext = UIx.Text("", "small");
            right.Add(_toNext);
            _nextBar = UIx.Bar(0, null, "thick");
            right.Add(_nextBar);
            right.Add(UIx.Text($"Every {StepLedger.StepsPerAction:#,0} steps = 1 action in battle", "tiny", "muted"));
            row.Add(right);
            actions.Add(row);
            root.Add(actions);

            var today = UIx.Div("panel", "inset");
            _todayLabel = UIx.Text("", "small");
            today.Add(_todayLabel);
            _todayBar = UIx.Bar(0, "good");
            today.Add(_todayBar);
            var r2 = UIx.Div("row");
            _distance = UIx.Text("", "tiny", "muted");
            _distance.style.flexGrow = 1;
            _streakLine = UIx.Text("", "tiny", "gold");
            r2.Add(_distance); r2.Add(_streakLine);
            today.Add(r2);
            root.Add(today);

            _buttons = UIx.Div("row", "wrap");
            root.Add(_buttons);

            _debug = UIx.Div("row");
            root.Add(_debug);
        }

        public override void OnShow()
        {
            Game.Stage.BuildRegion(S.CurrentRegion);
            if (S.Battle == null) Game.Stage.ShowParty(S.Party);
            else Game.Stage.ShowBattle(S.Battle);
        }

        public override void Refresh()
        {
            var region = S.CurrentRegion;
            _region.text = region.Name;
            _regionDesc.text = region.Description + $"  ·  Recommended Lv {region.RecommendedLevel}";

            var l = S.Ledger;
            _actionsBig.text = l.BankedActions.ToString();
            _toNext.text = $"{UIx.N(l.StepsToNextAction)} steps to your next action";
            UIx.SetBar(_nextBar, l.StepRemainder / (float)StepLedger.StepsPerAction);

            int goal = S.Data.Settings.DailyStepGoal;
            _todayLabel.text = $"Today: {UIx.N(S.TodaySteps)} / {UIx.N(goal)} steps";
            UIx.SetBar(_todayBar, S.TodaySteps / (float)goal);
            _distance.text = $"Today {UIx.Km(DistanceCalc.Kilometers(S.TodaySteps, S.StrideMeters))}  ·  Lifetime {UIx.Km(S.TotalKm)}";
            var streak = S.Streak;
            _streakLine.text = streak.Current > 0 ? $"{streak.Current}-day streak · coins ×{S.CoinMultiplier:0.00}" : "Hit your goal to start a streak";

            _warning.Clear();
            _warning.style.display = DisplayStyle.None;
            if (!Game.StepPermissionGranted)
            {
                _warning.style.display = DisplayStyle.Flex;
                _warning.Add(UIx.Text("Step tracking is off", "h3", "bad"));
                _warning.Add(UIx.Text("Allow Physical Activity access so your walking can power the party.", "small", "wrap-text"));
                _warning.Add(UIx.Btn("Allow step tracking", () => Game.Steps.RequestPermissions(_ => UI.MarkDirty()), "primary"));
            }
            else if (Game.Steps.IsNative && !Game.Steps.HasSensor)
            {
                _warning.style.display = DisplayStyle.Flex;
                _warning.Add(UIx.Text("No step sensor found", "h3", "bad"));
                _warning.Add(UIx.Text("This device has no hardware step counter, so steps can't be tracked.", "small", "wrap-text"));
            }

            _buttons.Clear();
            if (S.Battle != null)
            {
                _buttons.Add(UIx.Btn("Continue battle", () => UI.Show(ScreenId.Battle), "primary", "wide"));
            }
            else
            {
                var fight = UIx.Btn("Seek a battle", StartBattle(false), "primary", "wide");
                fight.SetEnabled(S.CanStartBattle(false) == null);
                _buttons.Add(fight);
                if (!S.Data.BossesDefeated.Contains(region.BossId))
                {
                    var boss = GameData.Enemy(region.BossId);
                    _buttons.Add(UIx.Btn($"Boss: {boss.Name}", () =>
                        UI.Confirm("Boss battle", $"{boss.Name} awaits. Bosses take many actions to defeat — you can leave mid-fight and return after more walking.\n\nRecommended level {region.RecommendedLevel + 3}. You can't flee.", "Fight!", () => StartBattle(true)()), "danger", "wide"));
                }
            }
            if (S.Battle == null && NeedsRest())
                _buttons.Add(UIx.Btn($"Rest at inn ({S.InnCost}c)", () => { if (!S.RestAtInn()) UI.Toast("Not enough coins"); else { UI.Toast("The party is fully rested."); Game.Stage.ShowParty(S.Party); } }, "wide"));

            _debug.Clear();
            if (S.Data.Settings.DebugTools)
            {
                _debug.Add(UIx.Btn("+1k steps", () => S.AddDebugSteps(1000), "small", "ghost"));
                _debug.Add(UIx.Btn("+10k steps", () => S.AddDebugSteps(10000), "small", "ghost"));
            }
        }

        bool NeedsRest()
        {
            foreach (var h in S.Party) if (h.Hp < h.TotalStats().MaxHp || h.Mp < h.TotalStats().MaxMp) return true;
            return false;
        }

        System.Action StartBattle(bool boss) => () =>
        {
            var err = S.CanStartBattle(boss);
            if (err != null) { UI.Toast(err); return; }
            if (S.Ledger.BankedActions == 0) UI.Toast("No actions yet", "You can look, but each command costs 1,000 steps.");
            S.StartBattle(boss);
            UI.Show(ScreenId.Battle);
        };
    }
}
