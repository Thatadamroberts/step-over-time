using System;
using System.Collections;
using System.Collections.Generic;
using StepQuest.Core.Meta;
using StepQuest.Unity.Boot;
using StepQuest.Unity.UI.Screens;
using UnityEngine;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI
{
    public enum ScreenId { Home, Map, Battle, Party, Shop, Quests, Achievements, Settings }

    /// <summary>Owns the UI Toolkit document: top bar, screen host, nav bar, toasts and modals.</summary>
    public sealed class UIRoot : MonoBehaviour
    {
        public VisualElement Root { get; private set; }
        public VisualElement Overlay { get; private set; }
        GameRoot _game;
        VisualElement _safe, _content, _topbar, _navbar, _toasts;
        Label _steps, _actions, _coins, _streak;
        readonly Dictionary<ScreenId, ScreenBase> _screens = new Dictionary<ScreenId, ScreenBase>();
        readonly Dictionary<ScreenId, Button> _navButtons = new Dictionary<ScreenId, Button>();
        Label _questBadge;
        public ScreenId Current { get; private set; } = ScreenId.Home;
        bool _dirty;
        Rect _lastSafe;

        public static UIRoot Create(GameRoot game)
        {
            var go = new GameObject("UI");
            var ui = go.AddComponent<UIRoot>();
            ui._game = game;
            ui.Build();
            return ui;
        }

        void Build()
        {
            var doc = gameObject.AddComponent<UIDocument>();
            var settings = Resources.Load<PanelSettings>("UI/PanelSettings");
            if (settings == null)
            {
                settings = ScriptableObject.CreateInstance<PanelSettings>();
                settings.scaleMode = PanelScaleMode.ScaleWithScreenSize;
                settings.referenceResolution = new Vector2Int(1080, 1920);
                settings.screenMatchMode = PanelScreenMatchMode.MatchWidthOrHeight;
                settings.match = 0f;
                settings.themeStyleSheet = Resources.Load<ThemeStyleSheet>("UI/StepQuestTheme");
            }
            doc.panelSettings = settings;

            Root = doc.rootVisualElement;
            Root.AddToClassList("root");
            var sheet = Resources.Load<StyleSheet>("UI/StepQuest");
            if (sheet != null) Root.styleSheets.Add(sheet);

            _safe = UIx.Div("safe");
            Root.Add(_safe);

            _topbar = UIx.Div("topbar");
            _steps = AddChip(_topbar, "TODAY", "");
            _actions = AddChip(_topbar, "ACTIONS", "actions");
            _coins = AddChip(_topbar, "COINS", "");
            _streak = AddChip(_topbar, "STREAK", "");
            var gear = UIx.Btn("", () => Show(ScreenId.Settings), "icon-btn", "ghost");
            gear.Add(UIx.Icon("gear", 48));
            gear.style.alignItems = Align.Center; gear.style.justifyContent = Justify.Center;
            _topbar.Add(gear);
            _safe.Add(_topbar);

            _content = UIx.Div("content");
            _safe.Add(_content);

            _navbar = UIx.Div("navbar");
            AddNav(ScreenId.Home, "home", "Town");
            AddNav(ScreenId.Map, "map", "Map");
            AddNav(ScreenId.Party, "helm", "Party");
            AddNav(ScreenId.Shop, "bag", "Shop");
            AddNav(ScreenId.Quests, "scroll", "Quests");
            AddNav(ScreenId.Achievements, "trophy", "Trophies");
            _safe.Add(_navbar);

            Overlay = UIx.Div("overlay");
            Overlay.pickingMode = PickingMode.Ignore;
            _toasts = UIx.Div("toast-stack");
            _toasts.pickingMode = PickingMode.Ignore;
            Overlay.Add(_toasts);
            Root.Add(Overlay);

            _screens[ScreenId.Home] = new HomeScreen();
            _screens[ScreenId.Map] = new MapScreen();
            _screens[ScreenId.Battle] = new BattleScreen();
            _screens[ScreenId.Party] = new PartyScreen();
            _screens[ScreenId.Shop] = new ShopScreen();
            _screens[ScreenId.Quests] = new QuestsScreen();
            _screens[ScreenId.Achievements] = new AchievementsScreen();
            _screens[ScreenId.Settings] = new SettingsScreen();
            foreach (var s in _screens.Values) s.Attach(_game, this);

            _game.Session.Changed += () => _dirty = true;
            _game.Session.AchievementsUnlocked += list =>
            {
                foreach (var a in list) Toast("Trophy unlocked: " + a.Name, $"{a.Description}  +{a.RewardCoins} coins");
            };
            _game.Session.ActionsEarned += n => Toast(n == 1 ? "New action earned!" : $"{n} new actions earned!", "Your walking powers your party.");

            Show(_game.Session.Battle != null ? ScreenId.Battle : ScreenId.Home);
        }

        Label AddChip(VisualElement parent, string label, string extra)
        {
            var chip = UIx.Div("stat-chip");
            if (!string.IsNullOrEmpty(extra)) chip.AddToClassList(extra);
            var value = UIx.Text("0", "value");
            chip.Add(value);
            chip.Add(UIx.Text(label, "label"));
            parent.Add(chip);
            return value;
        }

        void AddNav(ScreenId id, string icon, string label)
        {
            var b = new Button(() => Show(id));
            b.AddToClassList("nav-btn");
            var glyph = UIx.Icon(icon, 56).With("glyph");
            glyph.style.alignSelf = Align.Center;
            b.Add(glyph);
            b.Add(UIx.Text(label, "label"));
            if (id == ScreenId.Quests)
            {
                _questBadge = UIx.Text("!", "badge");
                _questBadge.pickingMode = PickingMode.Ignore;
                b.Add(_questBadge);
            }
            _navbar.Add(b);
            _navButtons[id] = b;
        }

        public void Show(ScreenId id)
        {
            if (_screens.TryGetValue(Current, out var old)) old.OnHide();
            Current = id;
            _content.Clear();
            var screen = _screens[id];
            _content.Add(screen.Root);
            screen.OnShow();
            screen.Refresh();
            _navbar.style.display = screen.ShowNav ? DisplayStyle.Flex : DisplayStyle.None;
            foreach (var kv in _navButtons) kv.Value.EnableInClassList("active", kv.Key == id);
            RefreshTopBar();
        }

        public T Screen<T>(ScreenId id) where T : ScreenBase => _screens[id] as T;

        void LateUpdate()
        {
            ApplySafeArea();
            if (!_dirty) return;
            _dirty = false;
            RefreshTopBar();
            _screens[Current].Refresh();
        }

        void ApplySafeArea()
        {
            var sa = UnityEngine.Screen.safeArea;
            if (sa == _lastSafe || Root.panel == null) return;
            _lastSafe = sa;
            float scale = Root.layout.width > 0 ? Root.layout.width / UnityEngine.Screen.width : 1f;
            _safe.style.paddingTop = (UnityEngine.Screen.height - sa.yMax) * scale;
            _safe.style.paddingBottom = sa.yMin * scale;
            _safe.style.paddingLeft = sa.xMin * scale;
            _safe.style.paddingRight = (UnityEngine.Screen.width - sa.xMax) * scale;
        }

        public void RefreshTopBar()
        {
            var s = _game.Session;
            _steps.text = UIx.N(s.TodaySteps);
            _actions.text = s.Ledger.BankedActions.ToString();
            _coins.text = UIx.N(s.Inventory.Coins);
            var streak = s.Streak;
            _streak.text = streak.Current > 0 ? streak.Current.ToString() : "-";
            bool claimable = false;
            foreach (var q in s.ActiveQuests) if (!s.IsQuestClaimed(q) && s.QuestProgress(q) >= q.Target) claimable = true;
            _questBadge.style.display = claimable ? DisplayStyle.Flex : DisplayStyle.None;
        }

        public void MarkDirty() => _dirty = true;

        // ───────────────────────────── Toasts & modals ─────────────────────────────

        public void Toast(string title, string body = null, float seconds = 3.2f)
        {
            var t = UIx.Div("toast");
            t.Add(UIx.Text(title, "h3", "gold"));
            if (!string.IsNullOrEmpty(body)) t.Add(UIx.Text(body, "small", "wrap-text"));
            t.pickingMode = PickingMode.Ignore;
            _toasts.Add(t);
            StartCoroutine(Expire(t, seconds));
        }

        IEnumerator Expire(VisualElement t, float seconds)
        {
            yield return new WaitForSeconds(seconds);
            t.AddToClassList("out");
            yield return new WaitForSeconds(0.4f);
            t.RemoveFromHierarchy();
        }

        public VisualElement Modal(string title, VisualElement body, params (string label, Action action, string cls)[] buttons)
        {
            var back = UIx.Div("modal-back");
            var modal = UIx.Div("modal");
            modal.Add(UIx.Text(title, "title"));
            if (body != null)
            {
                var scroll = new ScrollView(ScrollViewMode.Vertical);
                scroll.Add(body);
                modal.Add(scroll);
            }
            var row = UIx.Div("row");
            foreach (var (label, action, cls) in buttons)
            {
                var b = UIx.Btn(label, () => { back.RemoveFromHierarchy(); action?.Invoke(); }, "wide");
                if (!string.IsNullOrEmpty(cls)) b.AddToClassList(cls);
                row.Add(b);
            }
            if (buttons.Length == 0) row.Add(UIx.Btn("Close", () => back.RemoveFromHierarchy(), "wide"));
            modal.Add(row);
            back.Add(modal);
            Root.Add(back);
            return back;
        }

        public void Confirm(string title, string text, string ok, Action onOk, string okClass = "primary")
            => Modal(title, UIx.Text(text, "wrap-text"), ("Cancel", null, "ghost"), (ok, onOk, okClass));
    }
}
