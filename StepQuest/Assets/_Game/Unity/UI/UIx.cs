using System;
using UnityEngine;
using UnityEngine.UIElements;

namespace StepQuest.Unity.UI
{
    /// <summary>Terse helpers for building UI Toolkit trees in code.</summary>
    public static class UIx
    {
        public static VisualElement Div(params string[] classes)
        {
            var v = new VisualElement();
            foreach (var c in classes) v.AddToClassList(c);
            return v;
        }

        public static Label Text(string text, params string[] classes)
        {
            var l = new Label(text);
            foreach (var c in classes) l.AddToClassList(c);
            return l;
        }

        public static Button Btn(string text, Action onClick, params string[] classes)
        {
            var b = new Button(onClick) { text = text };
            b.AddToClassList("btn");
            foreach (var c in classes) b.AddToClassList(c);
            return b;
        }

        public static VisualElement Bar(float fraction, string fillClass = null, string barClass = null)
        {
            var bar = Div("bar");
            if (barClass != null) bar.AddToClassList(barClass);
            var fill = Div("bar-fill");
            if (fillClass != null) foreach (var c in fillClass.Split(' ')) fill.AddToClassList(c);
            fill.style.width = Length.Percent(Mathf.Clamp01(fraction) * 100f);
            bar.Add(fill);
            return bar;
        }

        public static void SetBar(VisualElement bar, float fraction)
        {
            if (bar != null && bar.childCount > 0) bar[0].style.width = Length.Percent(Mathf.Clamp01(fraction) * 100f);
        }

        public static VisualElement Image(Texture2D tex, params string[] classes)
        {
            var v = Div(classes);
            v.style.backgroundImage = new StyleBackground(tex);
            return v;
        }

        /// <summary>A pixel icon from <see cref="Art.PixelArt.Icon"/>, at a multiple of its 16px size.</summary>
        public static VisualElement Icon(string kind, float size = 48f)
        {
            var v = new VisualElement();
            v.style.backgroundImage = new StyleBackground(Art.PixelArt.Icon(kind));
            v.style.width = size;
            v.style.height = size;
            v.style.flexShrink = 0;
            v.pickingMode = PickingMode.Ignore;
            return v;
        }

        public static VisualElement Row(params VisualElement[] children)
        {
            var r = Div("row");
            foreach (var c in children) r.Add(c);
            return r;
        }

        public static T With<T>(this T e, params string[] classes) where T : VisualElement
        {
            foreach (var c in classes) e.AddToClassList(c);
            return e;
        }

        public static string N(long n) => n.ToString("#,0");
        public static string Km(double km) => km < 10 ? km.ToString("0.00") + " km" : km < 1000 ? km.ToString("0.0") + " km" : km.ToString("#,0") + " km";
    }
}
