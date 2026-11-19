using System.Collections.Generic;
using StepQuest.Core.Party;
using UnityEngine;

namespace StepQuest.Unity.Art
{
    /// <summary>
    /// Procedural placeholder pixel art, so the game is fully playable before real art is added.
    /// Any PNG at Resources/Art/&lt;key&gt;.png (e.g. "hero_warrior", "enemy_slime", "prop_tree_forest")
    /// replaces the generated sprite: drop CC0 packs in there and they are picked up automatically.
    /// </summary>
    public static class PixelArt
    {
        static readonly Dictionary<string, Texture2D> Cache = new Dictionary<string, Texture2D>();

        public static Color32 Hex(string hex)
        {
            hex = hex.TrimStart('#');
            byte P(int i) => (byte)System.Convert.ToInt32(hex.Substring(i, 2), 16);
            return new Color32(P(0), P(2), P(4), hex.Length >= 8 ? P(6) : (byte)255);
        }

        /// <summary>Marks a colour as emissive: the shading pass leaves it untouched.</summary>
        static Color32 Glow(Color32 c) { c.a = 254; return c; }

        public static Texture2D Get(string key, System.Func<PixelCanvas> draw)
        {
            if (Cache.TryGetValue(key, out var t) && t != null) return t;
            var overrideTex = Resources.Load<Texture2D>("Art/" + key);
            if (overrideTex != null)
            {
                overrideTex.filterMode = FilterMode.Point;
                return Cache[key] = overrideTex;
            }
            var canvas = draw();
            return Cache[key] = canvas.ToTexture(key);
        }

        // ───────────────────────────── Heroes ─────────────────────────────

        public static Texture2D Hero(HeroClass c) => Get("hero_" + c.ToString().ToLowerInvariant(), () => DrawHero(c));

        static readonly Color32 Skin = Hex("#f1c27d"), SkinShade = Hex("#c98d5a"), Eye = Hex("#2a1a1a");

        public static PixelCanvas DrawHero(HeroClass cls)
        {
            var p = new PixelCanvas(32, 40);
            Color32 primary, secondary, hair, boots = Hex("#4a3426");
            bool robe = false;
            switch (cls)
            {
                case HeroClass.Warrior: primary = Hex("#8a9bb0"); secondary = Hex("#b33a3a"); hair = Hex("#6b3e1f"); break;
                case HeroClass.Rogue: primary = Hex("#4f5d3a"); secondary = Hex("#2e2a33"); hair = Hex("#1f1a18"); break;
                case HeroClass.Mage: primary = Hex("#3757a8"); secondary = Hex("#d9b343"); hair = Hex("#c9c9d6"); robe = true; break;
                case HeroClass.Druid: primary = Hex("#5b7a3a"); secondary = Hex("#7a5232"); hair = Hex("#a5552a"); robe = true; break;
                default: primary = Hex("#efe8d8"); secondary = Hex("#d8a93a"); hair = Hex("#e8cf8a"); robe = true; break;
            }

            // Back layer: cape / cloak
            if (cls == HeroClass.Warrior) p.Rect(9, 19, 14, 17, Hex("#7d2020"));
            if (cls == HeroClass.Druid) p.Tri(new Vector2(8, 18), new Vector2(24, 18), new Vector2(16, 38), Hex("#3f5a2a"));

            // Legs / robe
            if (robe)
            {
                p.Tri(new Vector2(10, 22), new Vector2(22, 22), new Vector2(25, 38), primary);
                p.Tri(new Vector2(10, 22), new Vector2(7, 38), new Vector2(25, 38), primary);
                p.Rect(15, 24, 2, 14, secondary); // trim
                p.Rect(9, 37, 6, 2, boots); p.Rect(17, 37, 6, 2, boots);
            }
            else
            {
                p.Rect(11, 29, 4, 8, cls == HeroClass.Rogue ? Hex("#3b3029") : Hex("#5a5f6b"));
                p.Rect(17, 29, 4, 8, cls == HeroClass.Rogue ? Hex("#3b3029") : Hex("#5a5f6b"));
                p.Rect(10, 36, 5, 3, boots); p.Rect(17, 36, 5, 3, boots);
            }

            // Torso
            p.Rect(10, 19, 12, 11, primary);
            p.Rect(10, 27, 12, 2, Hex("#5a3b22")); // belt
            p.Rect(15, 27, 2, 2, Hex("#e0c050"));   // buckle
            if (cls == HeroClass.Warrior) { p.Rect(9, 19, 4, 3, Hex("#aab8c8")); p.Rect(19, 19, 4, 3, Hex("#aab8c8")); } // pauldrons
            if (cls == HeroClass.Priest) { p.Rect(14, 20, 4, 7, secondary); p.Rect(12, 22, 8, 2, secondary); }      // holy symbol
            if (cls == HeroClass.Mage) p.Rect(10, 19, 12, 2, secondary);

            // Arms
            var sleeve = robe ? primary : (cls == HeroClass.Warrior ? Hex("#6f7f94") : Hex("#3d4a2c"));
            p.Rect(7, 20, 3, 8, sleeve); p.Rect(22, 20, 3, 8, sleeve);
            p.Rect(7, 28, 3, 2, Skin); p.Rect(22, 28, 3, 2, Skin);

            // Head
            p.Circle(16, 13, 6, Skin);
            p.Rect(12, 17, 8, 1, SkinShade);
            p.Set(13, 13, Eye); p.Set(14, 13, Eye); p.Set(18, 13, Eye); p.Set(19, 13, Eye);
            p.Ellipse(16, 9, 6.5f, 4f, hair);

            switch (cls)
            {
                case HeroClass.Warrior:
                    p.Ellipse(16, 8, 7, 4.5f, Hex("#9fb0c4")); p.Rect(9, 9, 14, 2, Hex("#7d8da2")); p.Rect(15, 9, 2, 6, Hex("#7d8da2"));
                    p.Rect(13, 3, 6, 2, Hex("#c43c3c")); // plume
                    // Sword (right hand, held up) and shield (left)
                    p.Line(26, 5, 26, 27, Hex("#dfe8f0"), 2); p.Rect(24, 26, 6, 2, Hex("#d6a63a")); p.Rect(26, 28, 2, 3, Hex("#5a3b22"));
                    p.Ellipse(6, 25, 4.5f, 6f, Hex("#7d2020")); p.Ellipse(6, 25, 2.5f, 4f, Hex("#d6a63a"));
                    break;
                case HeroClass.Rogue:
                    p.Ellipse(16, 10, 8, 7.5f, secondary); p.Ellipse(16, 13.5f, 5, 4.5f, Skin);
                    p.Set(13, 13, Eye); p.Set(14, 13, Eye); p.Set(18, 13, Eye); p.Set(19, 13, Eye);
                    p.Rect(11, 15, 10, 3, Hex("#6b2b2b")); // scarf mask
                    p.Line(25, 27, 29, 21, Hex("#d0d8e0"), 1); p.Line(26, 27, 30, 21, Hex("#d0d8e0"), 1);
                    p.Line(7, 27, 3, 21, Hex("#d0d8e0"), 1); p.Line(6, 27, 2, 21, Hex("#d0d8e0"), 1);
                    break;
                case HeroClass.Mage:
                    p.Rect(7, 8, 18, 2, primary); // brim
                    p.Tri(new Vector2(10, 8), new Vector2(22, 8), new Vector2(19, -1), primary);
                    p.Rect(10, 7, 12, 1, secondary);
                    p.Line(27, 6, 27, 38, Hex("#6b4423"), 2);
                    p.Circle(27.5f, 4, 2.6f, Glow(Hex("#7fe3ff"))); p.Set(27, 3, Glow(Hex("#ffffff")));
                    break;
                case HeroClass.Druid:
                    var antler = Hex("#d8c8a0");
                    p.Line(11, 7, 7, 1, antler); p.Line(9, 4, 6, 4, antler); p.Line(21, 7, 25, 1, antler); p.Line(23, 4, 26, 4, antler);
                    p.Rect(10, 7, 12, 2, Hex("#4f8a3a")); p.Set(11, 6, Hex("#79b84a")); p.Set(16, 6, Hex("#79b84a")); p.Set(20, 6, Hex("#79b84a"));
                    p.Line(27, 8, 26, 38, Hex("#5a3b22"), 2); p.Line(27, 8, 30, 4, Hex("#5a3b22")); p.Line(26, 9, 23, 5, Hex("#5a3b22"));
                    p.Circle(29, 4, 1.6f, Hex("#79b84a")); p.Circle(23, 5, 1.6f, Hex("#79b84a"));
                    break;
                default: // Priest
                    p.Ellipse(16, 10, 7.5f, 7f, primary); p.Ellipse(16, 13.5f, 5, 4.5f, Skin);
                    p.Set(13, 13, Eye); p.Set(14, 13, Eye); p.Set(18, 13, Eye); p.Set(19, 13, Eye);
                    p.Ellipse(16, 2.5f, 6, 1.6f, Glow(Hex("#ffe58a"))); p.Ellipse(16, 2.5f, 4, 0.7f, new Color32(0, 0, 0, 0));
                    p.Line(26, 14, 26, 30, Hex("#8a6a3a"), 2); p.Circle(26.5f, 12, 3, Hex("#d8a93a")); p.Set(26, 11, Glow(Hex("#fff3c0")));
                    break;
            }
            p.Finish();
            p.MirrorX(); // heroes stand on the right, facing left
            return p;
        }

        // ───────────────────────────── Enemies ─────────────────────────────

        enum Shape { Slime, Beast, Insect, Plant, Undead, Spirit, Golem, Serpent, Crab }

        struct Look { public Shape Shape; public Color32 A, B, C; public Look(Shape s, string a, string b, string c) { Shape = s; A = Hex(a); B = Hex(b); C = Hex(c); } }

        static readonly Dictionary<string, Look> Looks = new Dictionary<string, Look>
        {
            { "slime", new Look(Shape.Slime, "#5fc3e4", "#b9f1ff", "#1d3a5a") },
            { "rat", new Look(Shape.Beast, "#8a6f5a", "#d9a6a0", "#2a1a14") },
            { "wasp", new Look(Shape.Insect, "#e8c13a", "#2b2320", "#d8f0ff") },
            { "boss_boar", new Look(Shape.Beast, "#6b4a3a", "#e8e0c8", "#3a2a24") },
            { "wolf", new Look(Shape.Beast, "#5a6170", "#c8ccd8", "#e84a3a") },
            { "sprite", new Look(Shape.Spirit, "#7ad05a", "#e8ff9a", "#2f5a2a") },
            { "mushroom", new Look(Shape.Plant, "#c4423a", "#f3e6cf", "#6a4a3a") },
            { "boss_treant", new Look(Shape.Plant, "#6b4a2a", "#4f8a3a", "#ffd86b") },
            { "scorpion", new Look(Shape.Insect, "#cfe6ea", "#7aa6b8", "#3a5a6a") },
            { "mummy", new Look(Shape.Undead, "#d8ccb0", "#8a7a5a", "#ff5a3a") },
            { "djinn", new Look(Shape.Spirit, "#5a9ad8", "#d8f0ff", "#ffd86b") },
            { "boss_wyrm", new Look(Shape.Serpent, "#c8913a", "#f0d8a0", "#7a3a1a") },
            { "crab", new Look(Shape.Crab, "#d8563a", "#f3b08a", "#5a1a1a") },
            { "siren", new Look(Shape.Undead, "#4a8a9a", "#a8e0d8", "#d8f0ff") },
            { "pirate", new Look(Shape.Undead, "#7ac8b0", "#3a3a5a", "#ffd86b") },
            { "boss_kraken", new Look(Shape.Serpent, "#7a3a8a", "#d88ad0", "#ffd86b") },
            { "yeti", new Look(Shape.Beast, "#e8f0f8", "#9ab0c8", "#3a5a8a") },
            { "harpy", new Look(Shape.Insect, "#8a7ad8", "#e8d8ff", "#ffd86b") },
            { "golem", new Look(Shape.Golem, "#8a8a9a", "#5ad8ff", "#3a3a4a") },
            { "boss_dragon", new Look(Shape.Serpent, "#9ad0ea", "#f0faff", "#3a6a9a") },
            { "gargoyle", new Look(Shape.Golem, "#6a6a7a", "#ff6a3a", "#2a2a3a") },
            { "wraith", new Look(Shape.Spirit, "#3a2a5a", "#c8a8ff", "#ff5ad8") },
            { "sentinel", new Look(Shape.Golem, "#b89a5a", "#7affd8", "#5a4a2a") },
            { "boss_tyrant", new Look(Shape.Serpent, "#2a1a3a", "#ffd86b", "#ff3a6a") },
        };

        public static Texture2D Enemy(string id) => Get("enemy_" + id, () => DrawEnemy(id));

        public static PixelCanvas DrawEnemy(string id)
        {
            if (!Looks.TryGetValue(id, out var l)) l = new Look(Shape.Slime, "#aaaaaa", "#ffffff", "#333333");
            bool boss = id.StartsWith("boss_");
            int s = boss ? 64 : 32;
            var p = new PixelCanvas(s, s);
            float k = s / 32f; // scale factor for coordinates
            Color32 eye = Glow(boss ? Hex("#ffef6a") : Hex("#ffffff"));

            switch (l.Shape)
            {
                case Shape.Slime:
                    p.Ellipse(16 * k, 22 * k, 12 * k, 9 * k, l.A);
                    p.Rect((int)(4 * k), (int)(22 * k), (int)(24 * k), (int)(8 * k), l.A);
                    p.Ellipse(11 * k, 17 * k, 3 * k, 2 * k, l.B);
                    p.Rect((int)(11 * k), (int)(21 * k), (int)(2 * k), (int)(3 * k), l.C); p.Rect((int)(19 * k), (int)(21 * k), (int)(2 * k), (int)(3 * k), l.C);
                    break;
                case Shape.Beast:
                    p.Ellipse(15 * k, 19 * k, 11 * k, 7 * k, l.A);                     // body
                    p.Ellipse(25 * k, 15 * k, 6 * k, 5 * k, l.A);                      // head
                    p.Tri(new Vector2(29 * k, 14 * k), new Vector2(32 * k, 17 * k), new Vector2(28 * k, 18 * k), l.A); // snout
                    p.Tri(new Vector2(22 * k, 11 * k), new Vector2(24 * k, 5 * k), new Vector2(26 * k, 11 * k), l.A); // ear
                    for (int i = 0; i < 4; i++) p.Rect((int)((7 + i * 5) * k), (int)(23 * k), (int)(3 * k), (int)(7 * k), l.C);
                    p.Ellipse(15 * k, 22 * k, 8 * k, 3 * k, l.B);                       // belly
                    p.Line((int)(4 * k), (int)(17 * k), 0, (int)(12 * k), l.A, (int)Mathf.Max(1, 2 * k)); // tail
                    p.Rect((int)(26 * k), (int)(13 * k), (int)(2 * k), (int)(2 * k), eye);
                    if (boss) { p.Tri(new Vector2(28 * k, 18 * k), new Vector2(31 * k, 22 * k), new Vector2(27 * k, 21 * k), l.B); p.Tri(new Vector2(25 * k, 18 * k), new Vector2(27 * k, 23 * k), new Vector2(24 * k, 21 * k), l.B); }
                    break;
                case Shape.Insect:
                    p.Ellipse(10 * k, 19 * k, 7 * k, 5 * k, l.A);
                    for (int i = 0; i < 3; i++) p.Rect((int)((5 + i * 4) * k), (int)(15 * k), (int)(2 * k), (int)(9 * k), l.B); // stripes
                    p.Ellipse(19 * k, 17 * k, 4 * k, 4 * k, l.A);
                    p.Ellipse(25 * k, 15 * k, 4 * k, 3.5f * k, l.A);
                    p.Tri(new Vector2(19 * k, 14 * k), new Vector2(8 * k, 4 * k), new Vector2(13 * k, 13 * k), WithAlpha(l.C, 150));
                    p.Tri(new Vector2(20 * k, 14 * k), new Vector2(14 * k, 2 * k), new Vector2(18 * k, 13 * k), WithAlpha(l.C, 170));
                    for (int i = 0; i < 3; i++) p.Line((int)((17 + i * 3) * k), (int)(20 * k), (int)((15 + i * 3) * k), (int)(28 * k), l.B);
                    p.Rect((int)(26 * k), (int)(14 * k), (int)(2 * k), (int)(2 * k), Glow(Hex("#ff3a3a")));
                    p.Line((int)(4 * k), (int)(20 * k), (int)(1 * k), (int)(23 * k), l.B); // stinger
                    break;
                case Shape.Plant:
                    if (boss)
                    {
                        p.Rect((int)(12 * k), (int)(10 * k), (int)(9 * k), (int)(20 * k), l.A);           // trunk
                        p.Ellipse(16 * k, 9 * k, 14 * k, 8 * k, l.B);                                         // canopy
                        p.Line((int)(12 * k), (int)(18 * k), (int)(3 * k), (int)(12 * k), l.A, 3); p.Line((int)(21 * k), (int)(18 * k), (int)(30 * k), (int)(12 * k), l.A, 3);
                        p.Rect((int)(13 * k), (int)(16 * k), (int)(2 * k), (int)(2 * k), Glow(l.C)); p.Rect((int)(18 * k), (int)(16 * k), (int)(2 * k), (int)(2 * k), Glow(l.C));
                        p.Rect((int)(14 * k), (int)(21 * k), (int)(5 * k), (int)(1 * k), Hex("#2a1a10"));
                        for (int i = 0; i < 4; i++) p.Line((int)((10 + i * 4) * k), (int)(30 * k), (int)((8 + i * 5) * k), (int)(32 * k) - 1, l.A, 2);
                    }
                    else
                    {
                        p.Rect((int)(12 * k), (int)(16 * k), (int)(8 * k), (int)(12 * k), l.B);
                        p.Ellipse(16 * k, 14 * k, 12 * k, 8 * k, l.A);
                        p.Rect((int)(4 * k), (int)(14 * k), (int)(24 * k), (int)(3 * k), l.A);
                        p.Circle(10 * k, 10 * k, 2 * k, l.B); p.Circle(20 * k, 9 * k, 2.5f * k, l.B); p.Circle(16 * k, 15 * k, 1.5f * k, l.B);
                        p.Rect((int)(13 * k), (int)(21 * k), (int)(2 * k), (int)(2 * k), l.C); p.Rect((int)(17 * k), (int)(21 * k), (int)(2 * k), (int)(2 * k), l.C);
                        p.Rect((int)(10 * k), (int)(28 * k), (int)(12 * k), (int)(2 * k), l.C);
                    }
                    break;
                case Shape.Undead:
                    p.Tri(new Vector2(8 * k, 30 * k), new Vector2(24 * k, 30 * k), new Vector2(16 * k, 12 * k), l.A);
                    p.Rect((int)(10 * k), (int)(14 * k), (int)(12 * k), (int)(14 * k), l.A);
                    p.Circle(16 * k, 10 * k, 5 * k, l.A);
                    for (int i = 0; i < 5; i++) p.Line((int)(10 * k), (int)((15 + i * 3) * k), (int)(22 * k), (int)((14 + i * 3) * k), l.B);
                    p.Rect((int)(6 * k), (int)(16 * k), (int)(4 * k), (int)(3 * k), l.A); p.Rect((int)(22 * k), (int)(16 * k), (int)(5 * k), (int)(3 * k), l.A);
                    p.Rect((int)(13 * k), (int)(9 * k), (int)(2 * k), (int)(2 * k), Glow(l.C)); p.Rect((int)(17 * k), (int)(9 * k), (int)(2 * k), (int)(2 * k), Glow(l.C));
                    break;
                case Shape.Spirit:
                    p.Circle(16 * k, 12 * k, 8 * k, WithAlpha(l.A, 230));
                    p.Tri(new Vector2(8 * k, 13 * k), new Vector2(24 * k, 13 * k), new Vector2(13 * k, 31 * k), WithAlpha(l.A, 230));
                    p.Ellipse(16 * k, 11 * k, 4 * k, 3 * k, l.B);
                    p.Rect((int)(13 * k), (int)(10 * k), (int)(2 * k), (int)(3 * k), Glow(l.C)); p.Rect((int)(18 * k), (int)(10 * k), (int)(2 * k), (int)(3 * k), Glow(l.C));
                    p.Line((int)(8 * k), (int)(16 * k), (int)(2 * k), (int)(10 * k), l.A, 2); p.Line((int)(24 * k), (int)(16 * k), (int)(30 * k), (int)(10 * k), l.A, 2);
                    for (int i = 0; i < 4; i++) p.Set((int)((5 + i * 7) * k), (int)((4 + (i % 2) * 20) * k), Glow(l.B));
                    break;
                case Shape.Golem:
                    p.Rect((int)(8 * k), (int)(10 * k), (int)(16 * k), (int)(14 * k), l.A);
                    p.Rect((int)(11 * k), (int)(4 * k), (int)(10 * k), (int)(7 * k), l.A);
                    p.Rect((int)(3 * k), (int)(11 * k), (int)(5 * k), (int)(13 * k), l.C); p.Rect((int)(24 * k), (int)(11 * k), (int)(5 * k), (int)(13 * k), l.C);
                    p.Rect((int)(9 * k), (int)(24 * k), (int)(5 * k), (int)(7 * k), l.C); p.Rect((int)(18 * k), (int)(24 * k), (int)(5 * k), (int)(7 * k), l.C);
                    p.Rect((int)(13 * k), (int)(6 * k), (int)(2 * k), (int)(2 * k), Glow(l.B)); p.Rect((int)(17 * k), (int)(6 * k), (int)(2 * k), (int)(2 * k), Glow(l.B));
                    p.Circle(16 * k, 16 * k, 2.5f * k, Glow(l.B));
                    p.Line((int)(10 * k), (int)(20 * k), (int)(14 * k), (int)(22 * k), l.C); p.Line((int)(20 * k), (int)(12 * k), (int)(22 * k), (int)(18 * k), l.C);
                    break;
                case Shape.Crab:
                    p.Ellipse(16 * k, 20 * k, 11 * k, 6 * k, l.A);
                    p.Circle(5 * k, 12 * k, 4 * k, l.A); p.Circle(27 * k, 12 * k, 4 * k, l.A);
                    p.Tri(new Vector2(3 * k, 8 * k), new Vector2(7 * k, 8 * k), new Vector2(5 * k, 12 * k), new Color32(0, 0, 0, 0));
                    p.Tri(new Vector2(25 * k, 8 * k), new Vector2(29 * k, 8 * k), new Vector2(27 * k, 12 * k), new Color32(0, 0, 0, 0));
                    for (int i = 0; i < 3; i++) { p.Line((int)((9 + i * 3) * k), (int)(24 * k), (int)((6 + i * 3) * k), (int)(30 * k), l.A); p.Line((int)((23 - i * 3) * k), (int)(24 * k), (int)((26 - i * 3) * k), (int)(30 * k), l.A); }
                    p.Line((int)(13 * k), (int)(15 * k), (int)(12 * k), (int)(11 * k), l.C); p.Line((int)(19 * k), (int)(15 * k), (int)(20 * k), (int)(11 * k), l.C);
                    p.Rect((int)(11 * k), (int)(10 * k), (int)(2 * k), (int)(2 * k), Glow(Hex("#ffffff"))); p.Rect((int)(19 * k), (int)(10 * k), (int)(2 * k), (int)(2 * k), Glow(Hex("#ffffff")));
                    p.Ellipse(16 * k, 18 * k, 7 * k, 2.5f * k, l.B);
                    break;
                case Shape.Serpent:
                    // Coiled body
                    p.Ellipse(14 * k, 25 * k, 12 * k, 5 * k, l.A);
                    p.Ellipse(12 * k, 22 * k, 9 * k, 4 * k, l.B);
                    p.Line((int)(18 * k), (int)(24 * k), (int)(22 * k), (int)(10 * k), l.A, (int)(4 * k));
                    p.Ellipse(25 * k, 9 * k, 6 * k, 4.5f * k, l.A);                        // head
                    p.Tri(new Vector2(29 * k, 7 * k), new Vector2(32 * k, 10 * k), new Vector2(28 * k, 12 * k), l.A);
                    p.Tri(new Vector2(21 * k, 6 * k), new Vector2(18 * k, 0), new Vector2(24 * k, 5 * k), l.C); // horn
                    p.Tri(new Vector2(24 * k, 5 * k), new Vector2(24 * k, 0), new Vector2(27 * k, 5 * k), l.C);
                    // Bat wing: three membrane panels hanging from a bony arm.
                    var membrane = WithAlpha(Color32.Lerp(l.A, l.C, 0.45f), 235);
                    p.Tri(new Vector2(18 * k, 14 * k), new Vector2(4 * k, 3 * k), new Vector2(5 * k, 15 * k), membrane);
                    p.Tri(new Vector2(18 * k, 14 * k), new Vector2(4 * k, 3 * k), new Vector2(11 * k, 17 * k), membrane);
                    p.Tri(new Vector2(18 * k, 14 * k), new Vector2(10 * k, 1 * k), new Vector2(15 * k, 17 * k), membrane);
                    p.Line((int)(18 * k), (int)(14 * k), (int)(10 * k), (int)(1 * k), l.C, 2);
                    p.Line((int)(10 * k), (int)(1 * k), (int)(4 * k), (int)(3 * k), l.C, 2);
                    p.Line((int)(10 * k), (int)(1 * k), (int)(11 * k), (int)(16 * k), l.C, 1);
                    p.Line((int)(4 * k), (int)(3 * k), (int)(5 * k), (int)(15 * k), l.C, 1);
                    p.Line((int)(18 * k), (int)(24 * k), (int)(22 * k), (int)(10 * k), l.A, (int)(4 * k)); // neck over the wing
                    p.Ellipse(25 * k, 9 * k, 6 * k, 4.5f * k, l.A);
                    p.Rect((int)(26 * k), (int)(8 * k), (int)(2 * k), (int)(2 * k), eye);
                    for (int i = 0; i < 5; i++) p.Set((int)((6 + i * 4) * k), (int)(27 * k), l.C);
                    for (int i = 0; i < 3; i++) p.Tri(new Vector2((8 + i * 6) * k, 29 * k), new Vector2((10 + i * 6) * k, 32 * k - 1), new Vector2((12 + i * 6) * k, 29 * k), l.C); // claws
                    break;
            }
            switch (id)
            {
                case "pirate": // tricorn hat and cutlass
                    p.Rect((int)(9 * k), (int)(4 * k), (int)(14 * k), (int)(3 * k), Hex("#2a2a3a"));
                    p.Tri(new Vector2(11 * k, 4 * k), new Vector2(21 * k, 4 * k), new Vector2(16 * k, 0), Hex("#2a2a3a"));
                    p.Line((int)(26 * k), (int)(18 * k), (int)(30 * k), (int)(9 * k), Hex("#d0d8e0"), 2);
                    break;
                case "siren": // flowing hair
                    p.Ellipse(16 * k, 9 * k, 7 * k, 5 * k, Hex("#3a6a7a"));
                    p.Line((int)(10 * k), (int)(9 * k), (int)(6 * k), (int)(22 * k), Hex("#3a6a7a"), 2);
                    p.Line((int)(22 * k), (int)(9 * k), (int)(26 * k), (int)(22 * k), Hex("#3a6a7a"), 2);
                    p.Rect((int)(13 * k), (int)(10 * k), (int)(2 * k), (int)(2 * k), Glow(l.C)); p.Rect((int)(17 * k), (int)(10 * k), (int)(2 * k), (int)(2 * k), Glow(l.C));
                    break;
                case "gargoyle": // stone wings and horns
                    p.Tri(new Vector2(8 * k, 12 * k), new Vector2(0, 2 * k), new Vector2(2 * k, 20 * k), l.C);
                    p.Tri(new Vector2(24 * k, 12 * k), new Vector2(32 * k - 1, 2 * k), new Vector2(30 * k, 20 * k), l.C);
                    p.Tri(new Vector2(11 * k, 5 * k), new Vector2(9 * k, 0), new Vector2(13 * k, 4 * k), l.C);
                    p.Tri(new Vector2(21 * k, 5 * k), new Vector2(23 * k, 0), new Vector2(19 * k, 4 * k), l.C);
                    break;
                case "sentinel": // clockwork gear on the chest
                    p.Circle(16 * k, 16 * k, 4 * k, Hex("#8a6a2a")); p.Circle(16 * k, 16 * k, 2 * k, Glow(l.B));
                    break;
            }
            if (boss)
            {
                // Bosses get a gold crown accent.
                p.Rect((int)(23 * k), (int)(2 * k), (int)(4 * k), (int)(1 * k), Glow(Hex("#ffd86b")));
            }
            p.Finish();
            return p;
        }

        static Color32 WithAlpha(Color32 c, byte a) { c.a = a; return c; }

        // ───────────────────────────── Icons ─────────────────────────────

        /// <summary>16×16 pixel icons for the UI (used instead of font glyphs, which many fonts lack).</summary>
        public static Texture2D Icon(string kind) => Get("icon_" + kind, () => DrawIcon(kind));

        public static PixelCanvas DrawIcon(string kind)
        {
            var p = new PixelCanvas(16, 16);
            Color32 gold = Hex("#e8c35a"), steel = Hex("#d6dee8"), brown = Hex("#7a5232"), red = Hex("#d8463a"), white = Hex("#f3e9d2");
            switch (kind)
            {
                case "sword": case "phys":
                    p.Line(3, 12, 12, 3, steel, 2); p.Line(2, 9, 6, 13, gold, 1); p.Line(3, 13, 1, 15, brown, 2); break;
                case "map":
                    p.Rect(2, 3, 12, 10, Hex("#d8c08a")); p.Line(5, 3, 5, 12, Hex("#a88a5a")); p.Line(10, 3, 10, 12, Hex("#a88a5a"));
                    p.Line(3, 10, 7, 6, red); p.Line(7, 6, 12, 8, red); p.Set(12, 8, Glow(red)); break;
                case "helm":
                    p.Ellipse(8, 8, 6, 6, steel); p.Rect(2, 9, 12, 6, steel); p.Rect(4, 8, 8, 2, Hex("#2a2a3a")); p.Rect(7, 8, 2, 6, Hex("#9aa6b4")); p.Rect(6, 0, 4, 3, red); break;
                case "bag":
                    p.Ellipse(8, 10, 6, 5, brown); p.Rect(5, 3, 6, 3, brown); p.Rect(5, 5, 6, 1, gold); p.Circle(8, 10, 2, Glow(gold)); break;
                case "scroll":
                    p.Rect(3, 3, 10, 10, Hex("#efe0b8")); p.Rect(2, 2, 12, 2, Hex("#c8a868")); p.Rect(2, 12, 12, 2, Hex("#c8a868"));
                    for (int i = 0; i < 3; i++) p.Line(5, 6 + i * 2, 11, 6 + i * 2, Hex("#8a6a3a")); break;
                case "trophy": case "star":
                    p.Tri(new Vector2(8, 0), new Vector2(11, 6), new Vector2(5, 6), gold);
                    p.Tri(new Vector2(0, 6), new Vector2(16, 6), new Vector2(8, 11), gold);
                    p.Tri(new Vector2(8, 9), new Vector2(3, 15), new Vector2(5, 7), gold);
                    p.Tri(new Vector2(8, 9), new Vector2(13, 15), new Vector2(11, 7), gold); break;
                case "star_empty":
                    p.Tri(new Vector2(8, 0), new Vector2(11, 6), new Vector2(5, 6), Hex("#5a5448"));
                    p.Tri(new Vector2(0, 6), new Vector2(16, 6), new Vector2(8, 11), Hex("#5a5448"));
                    p.Tri(new Vector2(8, 9), new Vector2(3, 15), new Vector2(5, 7), Hex("#5a5448"));
                    p.Tri(new Vector2(8, 9), new Vector2(13, 15), new Vector2(11, 7), Hex("#5a5448")); break;
                case "gear":
                    p.Circle(8, 8, 6, steel); for (int i = 0; i < 4; i++) { p.Rect(7, 0, 2, 16, steel); p.Rect(0, 7, 16, 2, steel); }
                    p.Line(2, 2, 13, 13, steel, 2); p.Line(13, 2, 2, 13, steel, 2); p.Circle(8, 8, 2.2f, Hex("#2a2a3a")); break;
                case "flame": case "fire":
                    p.Ellipse(8, 10, 5, 5, Glow(Hex("#ff7a2a"))); p.Tri(new Vector2(3, 10), new Vector2(13, 10), new Vector2(8, 0), Glow(Hex("#ff7a2a")));
                    p.Ellipse(8, 11, 3, 3, Glow(Hex("#ffd86b"))); p.Tri(new Vector2(5, 11), new Vector2(11, 11), new Vector2(8, 5), Glow(Hex("#ffd86b"))); break;
                case "ice":
                    p.Line(8, 1, 8, 15, Hex("#9ae6ff"), 2); p.Line(2, 4, 14, 12, Hex("#9ae6ff"), 2); p.Line(2, 12, 14, 4, Hex("#9ae6ff"), 2); p.Circle(8.5f, 8.5f, 2, Glow(white)); break;
                case "bolt":
                    p.Tri(new Vector2(10, 0), new Vector2(3, 9), new Vector2(9, 9), Glow(Hex("#ffe85a")));
                    p.Tri(new Vector2(7, 7), new Vector2(13, 7), new Vector2(5, 16), Glow(Hex("#ffe85a"))); break;
                case "leaf":
                    p.Ellipse(9, 7, 5, 6, Hex("#6ad04a")); p.Line(3, 14, 11, 4, Hex("#2f6a2a")); break;
                case "holy":
                    p.Rect(6, 1, 4, 14, Glow(Hex("#fff3c0"))); p.Rect(2, 5, 12, 4, Glow(Hex("#fff3c0"))); break;
                case "dark":
                    p.Circle(8, 8, 6, Hex("#a86ae8")); p.Circle(11, 6, 5, new Color32(0, 0, 0, 0)); break;
                case "skull":
                    p.Circle(8, 7, 6, white); p.Rect(5, 11, 6, 4, white); p.Rect(4, 6, 3, 3, Hex("#1a1020")); p.Rect(9, 6, 3, 3, Hex("#1a1020"));
                    p.Set(7, 13, Hex("#1a1020")); p.Set(9, 13, Hex("#1a1020")); break;
                case "lock":
                    p.Rect(3, 7, 10, 8, gold); p.Ellipse(8, 6, 4, 4, Hex("#9aa6b4")); p.Ellipse(8, 6, 2, 2.4f, new Color32(0, 0, 0, 0)); p.Rect(7, 9, 2, 3, Hex("#3a2a1a")); break;
                case "check":
                    p.Line(2, 8, 6, 12, Hex("#7be08a"), 2); p.Line(6, 12, 13, 3, Hex("#7be08a"), 2); break;
                case "coin":
                    p.Circle(8, 8, 6.5f, gold); p.Circle(8, 8, 4, Hex("#f6dc8a")); p.Rect(7, 5, 2, 6, Hex("#b08a2a")); break;
                case "boot":
                    p.Rect(5, 1, 6, 10, brown); p.Rect(5, 10, 10, 4, brown); p.Rect(4, 14, 12, 2, Hex("#3a2a1a")); p.Rect(5, 3, 6, 1, gold); break;
                case "home":
                    p.Tri(new Vector2(0, 8), new Vector2(16, 8), new Vector2(8, 1), red); p.Rect(3, 8, 10, 7, Hex("#d8c08a")); p.Rect(7, 10, 3, 5, brown); break;
                case "heart":
                    p.Circle(5, 6, 3.5f, red); p.Circle(11, 6, 3.5f, red); p.Tri(new Vector2(1.5f, 7), new Vector2(14.5f, 7), new Vector2(8, 15), red); break;
                default:
                    p.Circle(8, 8, 5, gold); break;
            }
            p.Finish();
            return p;
        }

        public static string ElementIcon(Element e)
        {
            switch (e)
            {
                case Element.Fire: return "fire";
                case Element.Ice: return "ice";
                case Element.Lightning: return "bolt";
                case Element.Nature: return "leaf";
                case Element.Holy: return "holy";
                case Element.Dark: return "dark";
                default: return "phys";
            }
        }

        // ───────────────────────────── Props & UI ─────────────────────────────

        public static Texture2D Prop(string kind, string palette) => Get($"prop_{kind}_{palette}", () => DrawProp(kind, palette));

        public static PixelCanvas DrawProp(string kind, string palette)
        {
            var pal = RegionPalette.Of(palette);
            PixelCanvas p;
            switch (kind)
            {
                case "tree":
                    p = new PixelCanvas(32, 48);
                    p.Rect(13, 28, 6, 20, pal.Trunk);
                    p.Circle(16, 18, 12, pal.Foliage); p.Circle(9, 24, 7, pal.Foliage); p.Circle(23, 24, 7, pal.Foliage);
                    p.Circle(12, 13, 4, pal.FoliageLight); p.Circle(20, 20, 3, pal.FoliageLight);
                    break;
                case "rock":
                    p = new PixelCanvas(24, 16);
                    p.Ellipse(12, 11, 11, 6, pal.Rock); p.Ellipse(9, 8, 5, 3, Lerp(pal.Rock, Color.white, 0.25f));
                    break;
                case "grass":
                    p = new PixelCanvas(12, 10);
                    for (int i = 0; i < 6; i++) p.Line(1 + i * 2, 9, 1 + i * 2 + (i % 2 == 0 ? -1 : 1), 2 + (i % 3), pal.FoliageLight);
                    break;
                case "pillar":
                    p = new PixelCanvas(16, 48);
                    p.Rect(3, 4, 10, 42, pal.Rock); p.Rect(1, 2, 14, 3, Lerp(pal.Rock, Color.white, 0.2f)); p.Rect(1, 44, 14, 4, Lerp(pal.Rock, Color.black, 0.2f));
                    p.Line(6, 10, 8, 20, Lerp(pal.Rock, Color.black, 0.3f));
                    break;
                case "cactus":
                    p = new PixelCanvas(20, 32);
                    p.Rect(8, 4, 5, 28, pal.Foliage); p.Rect(2, 12, 4, 8, pal.Foliage); p.Rect(2, 18, 7, 3, pal.Foliage); p.Rect(15, 8, 4, 10, pal.Foliage); p.Rect(12, 16, 6, 3, pal.Foliage);
                    break;
                case "crystal":
                    p = new PixelCanvas(16, 28);
                    p.Tri(new Vector2(8, 0), new Vector2(15, 20), new Vector2(1, 20), Glow(pal.Accent)); p.Rect(2, 20, 12, 8, pal.Rock);
                    break;
                default:
                    p = new PixelCanvas(8, 8); p.Circle(4, 4, 3, pal.Accent); break;
            }
            p.Finish();
            return p;
        }

        static Color32 Lerp(Color32 a, Color b, float t) => Color32.Lerp(a, b, t);

        /// <summary>Soft round particle for dust/fireflies/sparks.</summary>
        public static Texture2D Dot()
        {
            return Get("fx_dot", () =>
            {
                var p = new PixelCanvas(8, 8);
                p.Circle(4, 4, 3.5f, new Color32(255, 255, 255, 120));
                p.Circle(4, 4, 2.2f, new Color32(255, 255, 255, 255));
                return p;
            });
        }
    }
}
