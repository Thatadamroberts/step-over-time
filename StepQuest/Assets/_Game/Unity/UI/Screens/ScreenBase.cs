using StepQuest.Core.Session;
using StepQuest.Unity.Boot;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI.Screens
{
    public abstract class ScreenBase
    {
        public VisualElement Root { get; private set; }
        protected GameRoot Game { get; private set; }
        protected UIRoot UI { get; private set; }
        protected GameSession S => Game.Session;
        public virtual bool ShowNav => true;

        public void Attach(GameRoot game, UIRoot ui)
        {
            Game = game;
            UI = ui;
            Root = new VisualElement();
            Root.style.flexGrow = 1;
            Build(Root);
        }

        protected abstract void Build(VisualElement root);
        public virtual void Refresh() { }
        public virtual void OnShow() { }
        public virtual void OnHide() { }

        /// <summary>A vertical scroll area that fills the screen.</summary>
        protected static ScrollView Scroll(VisualElement parent)
        {
            var sv = new ScrollView(ScrollViewMode.Vertical);
            sv.AddToClassList("scroll");
            sv.style.flexGrow = 1;
            parent.Add(sv);
            return sv;
        }
    }
}
