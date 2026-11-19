using System.Collections;
using System.Collections.Generic;
using System.Linq;
using StepQuest.Core.Combat;
using StepQuest.Core.Data;
using StepQuest.Core.Economy;
using StepQuest.Core.Party;
using StepQuest.Core.Session;
using StepQuest.Core.Steps;
using StepQuest.Unity.Art;
using UnityEngine;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI.Screens
{
    public sealed class BattleScreen : ScreenBase
    {
        enum Mode { Root, Skills, Items, Target }

        public override bool ShowNav => false;

        VisualElement _tags, _floaters, _turnStrip, _party, _commands, _outOfActions;
        Label _title, _actions, _hint, _log;
        VisualElement _walkBar;
        Mode _mode = Mode.Root;
        BattleCommand _pending;
        TargetKind _pendingTarget;
        bool _busy;
        readonly Dictionary<int, VisualElement> _tagByUnit = new Dictionary<int, VisualElement>();
        IVisualElementScheduledItem _tagTicker;
        BattleSummary _shownSummary;

        protected override void Build(VisualElement root)
        {
            var top = UIx.Div("battle-top");
            _title = UIx.Text("", "h3", "gold");
            var actionsBox = UIx.Div("col");
            actionsBox.style.alignItems = Align.FlexEnd;
            _actions = UIx.Text("", "h2");
            _hint = UIx.Text("", "tiny", "muted");
            actionsBox.Add(_actions); actionsBox.Add(_hint);
            top.Add(_title); top.Add(actionsBox);
            root.Add(top);

            // Tap the 3D scene to pick a target.
            var sceneArea = UIx.Div();
            sceneArea.style.flexGrow = 1;
            sceneArea.RegisterCallback<PointerDownEvent>(OnSceneTap);
            root.Add(sceneArea);

            _turnStrip = UIx.Div("turn-strip");
            root.Add(_turnStrip);

            var panel = UIx.Div("panel");
            panel.style.marginBottom = 8;
            _party = UIx.Div("col");
            panel.Add(_party);
            _log = UIx.Text("", "log-line", "wrap-text");
            panel.Add(_log);
            root.Add(panel);

            _commands = UIx.Div("row", "wrap");
            root.Add(_commands);

            _outOfActions = UIx.Div("panel", "glow");
            _outOfActions.Add(UIx.Text("Out of actions", "h2"));
            _outOfActions.Add(UIx.Text("Your party holds its ground. Every 1,000 steps you walk gives them another action — the battle will wait for you.", "small", "wrap-text"));
            _walkBar = UIx.Bar(0, null, "thick");
            _outOfActions.Add(_walkBar);
            _outOfActions.Add(UIx.Btn("Return to town", () => UI.Show(ScreenId.Home), "wide"));
            root.Add(_outOfActions);

            // Overlays positioned over the 3D sprites.
            _tags = UIx.Div("overlay"); _tags.pickingMode = PickingMode.Ignore;
            _floaters = UIx.Div("overlay"); _floaters.pickingMode = PickingMode.Ignore;
            root.Add(_tags);
            root.Add(_floaters);
        }

        public override void OnShow()
        {
            _mode = Mode.Root;
            _pending = null;
            if (S.Battle != null) Game.Stage.ShowBattle(S.Battle);
            BuildTags();
            _tagTicker = Root.schedule.Execute(PositionTags).Every(16);
        }

        public override void OnHide()
        {
            _tagTicker?.Pause();
            Game.Stage.Highlight(-1);
        }

        public override void Refresh()
        {
            if (_busy) return;
            var b = S.Battle;
            if (b == null)
            {
                if (S.LastSummary != null) { if (_shownSummary != S.LastSummary) ShowResults(S.LastSummary); }
                else UI.Show(ScreenId.Home);
                return;
            }

            var region = GameData.Region(b.RegionId);
            _title.text = b.IsBoss ? "BOSS BATTLE" : region?.Name ?? "Battle";
            int actions = S.Ledger.BankedActions;
            _actions.text = actions == 1 ? "1 action" : $"{actions} actions";
            _hint.text = $"{UIx.N(S.Ledger.StepsToNextAction)} steps to next";

            RefreshTurnStrip(b);
            RefreshParty(b);
            RefreshTags(b);
            _log.text = string.Join("\n", b.Log.Skip(System.Math.Max(0, b.Log.Count - 2)).Select(e => e.Text));

            bool out_ = actions <= 0;
            _outOfActions.style.display = out_ ? DisplayStyle.Flex : DisplayStyle.None;
            _commands.style.display = out_ ? DisplayStyle.None : DisplayStyle.Flex;
            UIx.SetBar(_walkBar, S.Ledger.StepRemainder / (float)StepLedger.StepsPerAction);
            if (!out_) RefreshCommands(b);
            Game.Stage.Highlight(b.CurrentActor?.Id ?? -1);
        }

        // ───────────────────────────── Panels ─────────────────────────────

        void RefreshTurnStrip(Battle b)
        {
            _turnStrip.Clear();
            _turnStrip.Add(UIx.Text("TURN ", "tiny", "muted"));
            var order = new List<Combatant>();
            if (b.CurrentActor != null) order.Add(b.CurrentActor);
            order.AddRange(b.UpcomingTurns().Take(7));
            for (int i = 0; i < order.Count; i++)
            {
                var u = order[i];
                var tex = u.IsHero ? PixelArt.Hero(b.SessionHeroClasses[u.HeroIndex]) : PixelArt.Enemy(u.EnemyId);
                var chip = UIx.Image(tex, "turn-chip", u.IsHero ? "hero" : "enemy");
                if (i == 0) chip.AddToClassList("current");
                _turnStrip.Add(chip);
            }
        }

        void RefreshParty(Battle b)
        {
            _party.Clear();
            foreach (var u in b.Heroes)
            {
                var row = UIx.Div("hero-row");
                if (b.CurrentActor == u) row.AddToClassList("active");
                if (!u.IsAlive) row.AddToClassList("fallen");
                var name = UIx.Text((b.CurrentActor == u ? "> " : "") + u.Name, "name");
                if (u.Broken) name.text += " (broken)";
                row.Add(name);
                var bars = UIx.Div("bars");
                float hp = u.Hp / (float)u.Stats.MaxHp;
                bars.Add(UIx.Bar(hp, hp < 0.3f ? "hp low" : "hp"));
                var mp = UIx.Bar(u.Stats.MaxMp == 0 ? 0 : u.Mp / (float)u.Stats.MaxMp, "mp", "thin");
                mp.style.marginTop = 4;
                bars.Add(mp);
                row.Add(bars);
                row.Add(UIx.Text($"{u.Hp}/{u.Stats.MaxHp}\n{u.Mp} MP", "nums"));
                _party.Add(row);
            }
        }

        void RefreshCommands(Battle b)
        {
            _commands.Clear();
            var actor = b.CurrentActor;
            if (actor == null || !actor.IsHero) return;
            var hero = S.Party[actor.HeroIndex];

            switch (_mode)
            {
                case Mode.Root:
                    _commands.Add(UIx.Btn("Attack", () => BeginTarget(BattleCommand.Attack(-1), TargetKind.SingleEnemy), "cmd", "primary"));
                    _commands.Add(UIx.Btn("Skills", () => { _mode = Mode.Skills; Refresh(); }, "cmd"));
                    _commands.Add(UIx.Btn("Defend", () => Execute(BattleCommand.Defend()), "cmd"));
                    _commands.Add(UIx.Btn("Items", () => { _mode = Mode.Items; Refresh(); }, "cmd"));
                    var flee = UIx.Btn("Flee", () => Execute(BattleCommand.Flee()), "cmd", "ghost");
                    flee.SetEnabled(!b.IsBoss);
                    _commands.Add(flee);
                    break;

                case Mode.Skills:
                    foreach (var sk in hero.Skills())
                    {
                        var skill = sk;
                        var btn = UIx.Btn($"{skill.Name}  {skill.MpCost} MP", () => ChooseSkill(skill), "target");
                        btn.tooltip = skill.Description;
                        btn.SetEnabled(actor.Mp >= skill.MpCost);
                        _commands.Add(btn);
                    }
                    _commands.Add(UIx.Btn("< Back", Back, "target", "ghost"));
                    break;

                case Mode.Items:
                    foreach (var kv in S.Inventory.Items.OrderBy(k => k.Key))
                    {
                        var item = GameData.ItemOrNull(kv.Key);
                        if (item == null || !item.IsConsumable) continue;
                        var id = item.Id;
                        _commands.Add(UIx.Btn($"{item.Name} ×{kv.Value}", () =>
                            BeginTarget(BattleCommand.Item(id, -1), item.Use == ConsumableEffect.Revive ? TargetKind.DeadAlly : TargetKind.SingleAlly), "target"));
                    }
                    if (_commands.childCount == 0) _commands.Add(UIx.Text("No usable items.", "small", "muted"));
                    _commands.Add(UIx.Btn("< Back", Back, "target", "ghost"));
                    break;

                case Mode.Target:
                    foreach (var t in ValidTargets(b, _pendingTarget))
                    {
                        var target = t;
                        string label = target.Name + (target.IsHero ? $"  {target.Hp}/{target.Stats.MaxHp}" : "");
                        _commands.Add(UIx.Btn(label, () => ChooseTarget(target.Id), "target"));
                    }
                    _commands.Add(UIx.Btn("< Back", Back, "target", "ghost"));
                    break;
            }
        }

        static IEnumerable<Combatant> ValidTargets(Battle b, TargetKind k)
        {
            switch (k)
            {
                case TargetKind.SingleEnemy: return b.Enemies.Where(e => e.IsAlive);
                case TargetKind.SingleAlly: return b.Heroes.Where(h => h.IsAlive);
                case TargetKind.DeadAlly: return b.Heroes.Where(h => !h.IsAlive);
                default: return Enumerable.Empty<Combatant>();
            }
        }

        void Back() { _mode = _mode == Mode.Target && _pending?.Kind == CommandKind.Skill ? Mode.Skills : _mode == Mode.Target && _pending?.Kind == CommandKind.Item ? Mode.Items : Mode.Root; _pending = null; Refresh(); }

        void ChooseSkill(SkillDef skill)
        {
            var cmd = BattleCommand.Skill(skill.Id);
            if (skill.Target == TargetKind.SingleEnemy || skill.Target == TargetKind.SingleAlly || skill.Target == TargetKind.DeadAlly)
                BeginTarget(cmd, skill.Target);
            else Execute(cmd);
        }

        void BeginTarget(BattleCommand cmd, TargetKind kind)
        {
            var b = S.Battle;
            var targets = ValidTargets(b, kind).ToList();
            if (targets.Count == 0) { UI.Toast("No valid targets"); return; }
            _pending = cmd;
            _pendingTarget = kind;
            if (targets.Count == 1 && kind == TargetKind.SingleEnemy) { ChooseTarget(targets[0].Id); return; }
            _mode = Mode.Target;
            Refresh();
        }

        void ChooseTarget(int id)
        {
            if (_pending == null) return;
            _pending.TargetId = id;
            var cmd = _pending;
            _pending = null;
            Execute(cmd);
        }

        void OnSceneTap(PointerDownEvent evt)
        {
            if (_busy || S.Battle == null) return;
            var panelPos = (Vector2)evt.position;
            float scale = Screen.width / Mathf.Max(1f, Root.panel.visualTree.layout.width);
            var screen = new Vector2(panelPos.x * scale, Screen.height - panelPos.y * scale);
            int id = Game.Stage.UnitAtScreen(screen);
            if (id < 0) return;
            var unit = S.Battle.Unit(id);
            if (unit == null) return;

            if (_mode == Mode.Target && ValidTargets(S.Battle, _pendingTarget).Any(u => u.Id == id)) { ChooseTarget(id); return; }
            if (_mode == Mode.Root && !unit.IsHero && unit.IsAlive && S.Ledger.BankedActions > 0) Execute(BattleCommand.Attack(id));
        }

        // ───────────────────────────── Execution ─────────────────────────────

        void Execute(BattleCommand cmd)
        {
            if (_busy) return;
            var err = S.ValidateCommand(cmd);
            if (err != null) { UI.Toast(err); return; }
            var b = S.Battle;
            var element = cmd.Kind == CommandKind.Skill ? GameData.Skill(cmd.SkillId).Element : GameData.Class(b.SessionHeroClasses[b.CurrentActor.HeroIndex]).WeaponElement;
            List<BattleEvent> events;
            try { events = S.Command(cmd); }
            catch (System.InvalidOperationException e) { UI.Toast(e.Message); return; }
            _mode = Mode.Root;
            _busy = true;
            _commands.SetEnabled(false);
            Game.StartCoroutine(PlayThenRefresh(b, events, element));
        }

        IEnumerator PlayThenRefresh(Battle b, List<BattleEvent> events, Element element)
        {
            yield return Game.Stage.Play(b, events, ShowEvent, element);
            _busy = false;
            _commands.SetEnabled(true);
            UI.RefreshTopBar();
            Refresh();
        }

        void ShowEvent(BattleEvent e)
        {
            if (!string.IsNullOrEmpty(e.Text)) _log.text = e.Text;
            if (e.TargetId < 0) return;
            switch (e.Kind)
            {
                case BattleEventKind.Damage: Float(e.TargetId, e.Amount.ToString(), e.Crit ? "crit" : e.Weak ? "weak" : null); break;
                case BattleEventKind.Heal: Float(e.TargetId, "+" + e.Amount, "heal"); break;
                case BattleEventKind.Revive: Float(e.TargetId, "Revived!", "heal"); break;
                case BattleEventKind.Break: Float(e.TargetId, "BREAK!", "break"); break;
                case BattleEventKind.Coins: Float(e.TargetId, "+" + e.Amount + "c", "crit"); break;
                case BattleEventKind.Recover: Float(e.TargetId, "Recovered", null); break;
            }
            if (S.Battle != null) RefreshTags(S.Battle);
        }

        void Float(int unitId, string text, string cls)
        {
            var screen = Game.Stage.ScreenPointOf(unitId, false);
            if (screen.z < 0 || Root.panel == null) return;
            var pos = RuntimePanelUtils.ScreenToPanel(Root.panel, new Vector2(screen.x, Screen.height - screen.y));
            var local = _floaters.WorldToLocal(pos);
            var l = UIx.Text(text, "floater");
            if (cls != null) l.AddToClassList(cls);
            l.style.left = local.x + Random.Range(-20f, 20f);
            l.style.top = local.y;
            _floaters.Add(l);
            l.schedule.Execute(() => l.AddToClassList("rise")).StartingIn(30);
            l.schedule.Execute(() => l.RemoveFromHierarchy()).StartingIn(1100);
        }

        // ───────────────────────────── Enemy tags ─────────────────────────────

        void BuildTags()
        {
            _tags.Clear();
            _tagByUnit.Clear();
            var b = S.Battle;
            if (b == null) return;
            foreach (var e in b.Enemies)
            {
                var tag = UIx.Div("enemy-tag");
                tag.pickingMode = PickingMode.Ignore;
                _tags.Add(tag);
                _tagByUnit[e.Id] = tag;
            }
            RefreshTags(b);
        }

        void RefreshTags(Battle b)
        {
            foreach (var e in b.Enemies)
            {
                if (!_tagByUnit.TryGetValue(e.Id, out var tag)) continue;
                tag.Clear();
                tag.style.display = e.IsAlive ? DisplayStyle.Flex : DisplayStyle.None;
                if (!e.IsAlive) continue;
                tag.Add(UIx.Text(e.Name, "name"));
                tag.Add(UIx.Bar(e.Hp / (float)e.Stats.MaxHp, "hp", "thin"));
                if (e.Broken) tag.Add(UIx.Text("BROKEN", "broken-label"));
                else
                {
                    var pips = UIx.Div("shield-row");
                    for (int i = 0; i < e.MaxShield; i++) pips.Add(UIx.Div("shield-pip", i < e.Shield ? "full" : "empty"));
                    tag.Add(pips);
                }
                // Weaknesses: "?" until discovered by hitting them, as in classic break systems.
                var weak = UIx.Div("weak-row");
                foreach (var w in GameData.Enemy(e.EnemyId).Weaknesses)
                    weak.Add(b.IsRevealed(e, w) ? UIx.Icon(PixelArt.ElementIcon(w), 30) : UIx.Text("?", "weak-icon"));
                tag.Add(weak);
            }
        }

        void PositionTags()
        {
            if (Root.panel == null) return;
            foreach (var kv in _tagByUnit)
            {
                var screen = Game.Stage.ScreenPointOf(kv.Key, true);
                if (screen.z < 0) continue;
                var pos = RuntimePanelUtils.ScreenToPanel(Root.panel, new Vector2(screen.x, Screen.height - screen.y));
                var local = _tags.WorldToLocal(pos);
                kv.Value.style.left = local.x;
                kv.Value.style.top = local.y;
            }
        }

        // ───────────────────────────── Results ─────────────────────────────

        void ShowResults(BattleSummary s)
        {
            _shownSummary = s;
            var body = UIx.Div("col");
            string title;
            switch (s.Outcome)
            {
                case BattleOutcome.Victory:
                    title = s.BossDefeated ? "Boss defeated!" : "Victory!";
                    body.Add(UIx.Text($"+{UIx.N(s.Exp)} EXP", "h3"));
                    body.Add(UIx.Text($"+{UIx.N(s.Coins)} coins" + (s.CoinMultiplier > 1.001 ? $"  (streak ×{s.CoinMultiplier:0.00})" : ""), "h3", "gold"));
                    foreach (var d in s.Drops)
                    {
                        var item = GameData.ItemOrNull(d);
                        if (item != null) body.Add(UIx.Text("Found: " + item.Name, "small", "rarity-" + item.Rarity));
                    }
                    foreach (var kv in s.LevelsGained) body.Add(UIx.Text($"{kv.Key} reached level {S.Party.First(h => h.Name == kv.Key).Level}!", "small", "good"));
                    if (!string.IsNullOrEmpty(s.RegionUnlockedHint)) body.Add(UIx.Text(s.RegionUnlockedHint, "small", "gold", "wrap-text"));
                    break;
                case BattleOutcome.Defeat:
                    title = "Defeated...";
                    body.Add(UIx.Text($"Your party was carried back to town. You dropped {UIx.N(s.CoinsLost)} coins.", "small", "wrap-text"));
                    body.Add(UIx.Text("Tip: rest at the inn, upgrade gear, or level up on easier foes.", "small", "muted", "wrap-text"));
                    break;
                default:
                    title = "Escaped";
                    body.Add(UIx.Text("You live to walk another day.", "small"));
                    break;
            }
            var summary = s;
            UI.Modal(title, body, ("Continue", () =>
            {
                if (S.LastSummary == summary) S.DismissSummary();
                UI.Show(ScreenId.Home);
            }, "primary"));
        }
    }
}
