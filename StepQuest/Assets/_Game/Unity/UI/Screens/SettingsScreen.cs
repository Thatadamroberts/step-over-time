using StepQuest.Core.Steps;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI.Screens
{
    public sealed class SettingsScreen : ScreenBase
    {
        ScrollView _sv;
        protected override void Build(VisualElement root) => _sv = Scroll(root);

        bool _built;

        public override void OnShow() => _built = false;

        // Built once per visit: rebuilding on every session change would interrupt slider drags.
        public override void Refresh()
        {
            if (_built) return;
            Rebuild();
        }

        void Rebuild()
        {
            _built = true;
            _sv.Clear();
            var set = S.Data.Settings;
            var back = S.Battle != null ? ScreenId.Battle : ScreenId.Home;
            _sv.Add(UIx.Btn("< Back", () => UI.Show(back), "small", "ghost"));
            _sv.Add(UIx.Text("Settings", "title"));

            var walk = UIx.Div("panel");
            walk.Add(UIx.Text("Walking", "h2"));
            var heightLabel = UIx.Text("", "small");
            void UpdateHeight() => heightLabel.text = set.HeightCm > 0
                ? $"Height {set.HeightCm} cm = stride {set.StrideMeters:0.00} m"
                : $"Height not set: average stride {set.StrideMeters:0.00} m";
            UpdateHeight();
            walk.Add(heightLabel);
            var height = new SliderInt(120, 220) { value = set.HeightCm > 0 ? set.HeightCm : 170 };
            height.AddToClassList("slider-row");
            height.RegisterValueChangedCallback(e => { S.SetHeight(e.newValue); UpdateHeight(); });
            walk.Add(height);
            walk.Add(UIx.Text("Stride is used for distance trophies (like the Moon). Steps → actions is always 1,000:1.", "tiny", "muted", "wrap-text"));

            var goalLabel = UIx.Text($"Daily goal: {UIx.N(set.DailyStepGoal)} steps", "small");
            walk.Add(goalLabel);
            var goal = new SliderInt(2, 30) { value = set.DailyStepGoal / 1000 };
            goal.AddToClassList("slider-row");
            goal.RegisterValueChangedCallback(e => { S.SetStepGoal(e.newValue * 1000); goalLabel.text = $"Daily goal: {UIx.N(set.DailyStepGoal)} steps"; });
            walk.Add(goal);
            walk.Add(UIx.Text("Your streak counts days you reach this goal.", "tiny", "muted"));
            _sv.Add(walk);

            var notif = UIx.Div("panel");
            notif.Add(UIx.Text("Notifications & tracking", "h2"));
            var t = new Toggle("Tell me when I've earned new actions") { value = set.NotificationsEnabled };
            t.RegisterValueChangedCallback(e => { set.NotificationsEnabled = e.newValue; Game.SaveNow(); });
            notif.Add(t);
            notif.Add(UIx.Text(Game.Steps.IsNative
                ? (Game.StepPermissionGranted ? "Step tracking is on (it keeps counting while the app is closed)." : "Step tracking permission was denied.")
                : "Step tracking is only available on an Android device. Use the debug buttons in the editor.", "small", "wrap-text"));
            if (!Game.StepPermissionGranted)
                notif.Add(UIx.Btn("Allow step tracking", () => Game.Steps.RequestPermissions(_ => Rebuild()), "primary"));
            notif.Add(UIx.Text("Tip: add the StepQuest widget to your home screen to see steps and ready actions at a glance.", "tiny", "muted", "wrap-text"));
            _sv.Add(notif);

            var dev = UIx.Div("panel", "inset");
            dev.Add(UIx.Text("Developer", "h2"));
            var dbg = new Toggle("Show debug step buttons") { value = set.DebugTools };
            dbg.RegisterValueChangedCallback(e => { set.DebugTools = e.newValue; Game.SaveNow(); Rebuild(); });
            dev.Add(dbg);
            if (set.DebugTools)
            {
                var row = UIx.Div("row", "wrap");
                row.Add(UIx.Btn("+1,000", () => S.AddDebugSteps(1000), "small"));
                row.Add(UIx.Btn("+10,000", () => S.AddDebugSteps(10000), "small"));
                row.Add(UIx.Btn("+100,000", () => S.AddDebugSteps(100000), "small"));
                dev.Add(row);
            }
            _sv.Add(dev);

            var danger = UIx.Div("panel");
            danger.Add(UIx.Text("Start over", "h2"));
            danger.Add(UIx.Text("Deletes your party, gear and trophies. Steps already walked are not re-counted.", "small", "muted", "wrap-text"));
            danger.Add(UIx.Btn("Reset game", () => UI.Confirm("Reset everything?", "This can't be undone.", "Reset", () => Game.ResetGame(), "danger"), "danger"));
            _sv.Add(danger);

            var about = UIx.Div("panel", "inset");
            about.Add(UIx.Text("About", "h2"));
            about.Add(UIx.Text($"StepQuest · every {StepLedger.StepsPerAction:#,0} steps is one action in battle.\nArt: procedurally generated placeholders; drop CC0 art into Resources/Art to replace them (see ART.md).", "tiny", "muted", "wrap-text"));
            _sv.Add(about);
        }
    }
}
