package com.stepquest.app.art

import android.graphics.Bitmap
import com.stepquest.core.party.Element
import com.stepquest.core.party.HeroClass

/** Procedural pixel art: heroes, monsters, props and icons, drawn in code with an outline + shading pass. */
object PixelArt {
    private val cache = HashMap<String, Bitmap>()
    private fun get(key: String, draw: () -> PixelCanvas): Bitmap = cache.getOrPut(key) { draw().toBitmap() }

    private val SKIN = hex("#f1c27d"); private val SKIN_SHADE = hex("#c98d5a"); private val EYE = hex("#2a1a1a")

    fun hero(c: HeroClass) = get("hero_$c") { drawHero(c) }
    fun enemy(id: String) = get("enemy_$id") { drawEnemy(id) }
    fun prop(kind: String, palette: String) = get("prop_${kind}_$palette") { drawProp(kind, palette) }
    fun icon(kind: String) = get("icon_$kind") { drawIcon(kind) }

    enum class Shape { Slime, Beast, Insect, Plant, Undead, Spirit, Golem, Serpent, Crab }
    class Look(val shape: Shape, a: String, b: String, c: String) { val a = hex(a); val b = hex(b); val c = hex(c) }

    private val LOOKS = mapOf(
        "slime" to Look(Shape.Slime, "#5fc3e4", "#b9f1ff", "#1d3a5a"),
        "rat" to Look(Shape.Beast, "#8a6f5a", "#d9a6a0", "#2a1a14"),
        "wasp" to Look(Shape.Insect, "#e8c13a", "#2b2320", "#d8f0ff"),
        "boss_boar" to Look(Shape.Beast, "#6b4a3a", "#e8e0c8", "#3a2a24"),
        "wolf" to Look(Shape.Beast, "#5a6170", "#c8ccd8", "#e84a3a"),
        "sprite" to Look(Shape.Spirit, "#7ad05a", "#e8ff9a", "#2f5a2a"),
        "mushroom" to Look(Shape.Plant, "#c4423a", "#f3e6cf", "#6a4a3a"),
        "boss_treant" to Look(Shape.Plant, "#6b4a2a", "#4f8a3a", "#ffd86b"),
        "scorpion" to Look(Shape.Insect, "#cfe6ea", "#7aa6b8", "#3a5a6a"),
        "mummy" to Look(Shape.Undead, "#d8ccb0", "#8a7a5a", "#ff5a3a"),
        "djinn" to Look(Shape.Spirit, "#5a9ad8", "#d8f0ff", "#ffd86b"),
        "boss_wyrm" to Look(Shape.Serpent, "#c8913a", "#f0d8a0", "#7a3a1a"),
        "crab" to Look(Shape.Crab, "#d8563a", "#f3b08a", "#5a1a1a"),
        "siren" to Look(Shape.Undead, "#4a8a9a", "#a8e0d8", "#d8f0ff"),
        "pirate" to Look(Shape.Undead, "#7ac8b0", "#3a3a5a", "#ffd86b"),
        "boss_kraken" to Look(Shape.Serpent, "#7a3a8a", "#d88ad0", "#ffd86b"),
        "yeti" to Look(Shape.Beast, "#e8f0f8", "#9ab0c8", "#3a5a8a"),
        "harpy" to Look(Shape.Insect, "#8a7ad8", "#e8d8ff", "#ffd86b"),
        "golem" to Look(Shape.Golem, "#8a8a9a", "#5ad8ff", "#3a3a4a"),
        "boss_dragon" to Look(Shape.Serpent, "#9ad0ea", "#f0faff", "#3a6a9a"),
        "gargoyle" to Look(Shape.Golem, "#6a6a7a", "#ff6a3a", "#2a2a3a"),
        "wraith" to Look(Shape.Spirit, "#3a2a5a", "#c8a8ff", "#ff5ad8"),
        "sentinel" to Look(Shape.Golem, "#b89a5a", "#7affd8", "#5a4a2a"),
        "boss_tyrant" to Look(Shape.Serpent, "#2a1a3a", "#ffd86b", "#ff3a6a"),
    )

    fun elementIcon(e: Element) = when (e) {
        Element.Fire -> "fire"; Element.Ice -> "ice"; Element.Lightning -> "bolt"; Element.Nature -> "leaf"
        Element.Holy -> "holy"; Element.Dark -> "dark"; Element.Physical -> "phys"
    }

    fun drawHero(cls: HeroClass): PixelCanvas {

            val p = PixelCanvas(32, 40);
            var primary: Int; var secondary: Int; var hair: Int; val boots = hex("#4a3426");
            var robe = false;
            when (cls) {
                HeroClass.Warrior -> {
primary = hex("#8a9bb0"); secondary = hex("#b33a3a"); hair = hex("#6b3e1f"); }
                HeroClass.Rogue -> {
primary = hex("#4f5d3a"); secondary = hex("#2e2a33"); hair = hex("#1f1a18"); }
                HeroClass.Mage -> {
primary = hex("#3757a8"); secondary = hex("#d9b343"); hair = hex("#c9c9d6"); robe = true; }
                HeroClass.Druid -> {
primary = hex("#5b7a3a"); secondary = hex("#7a5232"); hair = hex("#a5552a"); robe = true; }
                else -> { primary = hex("#efe8d8"); secondary = hex("#d8a93a"); hair = hex("#e8cf8a"); robe = true; }
            }

            
            if (cls == HeroClass.Warrior) p.rect(9, 19, 14, 17, hex("#7d2020"));
            if (cls == HeroClass.Druid) p.tri(V(8, 18), V(24, 18), V(16, 38), hex("#3f5a2a"));

            
            if (robe)
            {
                p.tri(V(10, 22), V(22, 22), V(25, 38), primary);
                p.tri(V(10, 22), V(7, 38), V(25, 38), primary);
                p.rect(15, 24, 2, 14, secondary); 
                p.rect(9, 37, 6, 2, boots); p.rect(17, 37, 6, 2, boots);
            }
            else
            {
                p.rect(11, 29, 4, 8, if (cls == HeroClass.Rogue) hex("#3b3029") else hex("#5a5f6b"));
                p.rect(17, 29, 4, 8, if (cls == HeroClass.Rogue) hex("#3b3029") else hex("#5a5f6b"));
                p.rect(10, 36, 5, 3, boots); p.rect(17, 36, 5, 3, boots);
            }

            
            p.rect(10, 19, 12, 11, primary);
            p.rect(10, 27, 12, 2, hex("#5a3b22")); 
            p.rect(15, 27, 2, 2, hex("#e0c050"));   
            if (cls == HeroClass.Warrior) { p.rect(9, 19, 4, 3, hex("#aab8c8")); p.rect(19, 19, 4, 3, hex("#aab8c8")); } 
            if (cls == HeroClass.Priest) { p.rect(14, 20, 4, 7, secondary); p.rect(12, 22, 8, 2, secondary); }      
            if (cls == HeroClass.Mage) p.rect(10, 19, 12, 2, secondary);

            
            val sleeve = if (robe) primary else (if (cls == HeroClass.Warrior) hex("#6f7f94") else hex("#3d4a2c"));
            p.rect(7, 20, 3, 8, sleeve); p.rect(22, 20, 3, 8, sleeve);
            p.rect(7, 28, 3, 2, SKIN); p.rect(22, 28, 3, 2, SKIN);

            
            p.circle(16, 13, 6, SKIN);
            p.rect(12, 17, 8, 1, SKIN_SHADE);
            p.set(13, 13, EYE); p.set(14, 13, EYE); p.set(18, 13, EYE); p.set(19, 13, EYE);
            p.ellipse(16, 9, 6.5f, 4f, hair);

            when (cls) {
                HeroClass.Warrior -> {
p.ellipse(16, 8, 7, 4.5f, hex("#9fb0c4")); p.rect(9, 9, 14, 2, hex("#7d8da2")); p.rect(15, 9, 2, 6, hex("#7d8da2"));
                    p.rect(13, 3, 6, 2, hex("#c43c3c")); 
                    
                    p.line(26, 5, 26, 27, hex("#dfe8f0"), 2); p.rect(24, 26, 6, 2, hex("#d6a63a")); p.rect(26, 28, 2, 3, hex("#5a3b22"));
                    p.ellipse(6, 25, 4.5f, 6f, hex("#7d2020")); p.ellipse(6, 25, 2.5f, 4f, hex("#d6a63a"));
                    }
                HeroClass.Rogue -> {
p.ellipse(16, 10, 8, 7.5f, secondary); p.ellipse(16, 13.5f, 5, 4.5f, SKIN);
                    p.set(13, 13, EYE); p.set(14, 13, EYE); p.set(18, 13, EYE); p.set(19, 13, EYE);
                    p.rect(11, 15, 10, 3, hex("#6b2b2b")); 
                    p.line(25, 27, 29, 21, hex("#d0d8e0"), 1); p.line(26, 27, 30, 21, hex("#d0d8e0"), 1);
                    p.line(7, 27, 3, 21, hex("#d0d8e0"), 1); p.line(6, 27, 2, 21, hex("#d0d8e0"), 1);
                    }
                HeroClass.Mage -> {
p.rect(7, 8, 18, 2, primary); 
                    p.tri(V(10, 8), V(22, 8), V(19, -1), primary);
                    p.rect(10, 7, 12, 1, secondary);
                    p.line(27, 6, 27, 38, hex("#6b4423"), 2);
                    p.circle(27.5f, 4, 2.6f, glow(hex("#7fe3ff"))); p.set(27, 3, glow(hex("#ffffff")));
                    }
                HeroClass.Druid -> {
val antler = hex("#d8c8a0");
                    p.line(11, 7, 7, 1, antler); p.line(9, 4, 6, 4, antler); p.line(21, 7, 25, 1, antler); p.line(23, 4, 26, 4, antler);
                    p.rect(10, 7, 12, 2, hex("#4f8a3a")); p.set(11, 6, hex("#79b84a")); p.set(16, 6, hex("#79b84a")); p.set(20, 6, hex("#79b84a"));
                    p.line(27, 8, 26, 38, hex("#5a3b22"), 2); p.line(27, 8, 30, 4, hex("#5a3b22")); p.line(26, 9, 23, 5, hex("#5a3b22"));
                    p.circle(29, 4, 1.6f, hex("#79b84a")); p.circle(23, 5, 1.6f, hex("#79b84a"));
                    }
                else -> { 
                    p.ellipse(16, 10, 7.5f, 7f, primary); p.ellipse(16, 13.5f, 5, 4.5f, SKIN);
                    p.set(13, 13, EYE); p.set(14, 13, EYE); p.set(18, 13, EYE); p.set(19, 13, EYE);
                    p.ellipse(16, 2.5f, 6, 1.6f, glow(hex("#ffe58a"))); p.ellipse(16, 2.5f, 4, 0.7f, CLEAR);
                    p.line(26, 14, 26, 30, hex("#8a6a3a"), 2); p.circle(26.5f, 12, 3, hex("#d8a93a")); p.set(26, 11, glow(hex("#fff3c0")));
                    }
            }
            p.finish();
            p.mirrorX(); 
            return p
        

    }

    fun drawEnemy(id: String): PixelCanvas {

            val l = LOOKS[id] ?: Look(Shape.Slime, "#aaaaaa", "#ffffff", "#333333")
            val boss = id.startsWith("boss_")
            val s = if (boss) 64 else 32;
            val p = PixelCanvas(s, s);
            val k = s / 32f; 
            val eye = glow(if (boss) hex("#ffef6a") else hex("#ffffff"));

            when (l.shape) {
                Shape.Slime -> {
p.ellipse(16 * k, 22 * k, 12 * k, 9 * k, l.a);
                    p.rect(i(4 * k), i(22 * k), i(24 * k), i(8 * k), l.a);
                    p.ellipse(11 * k, 17 * k, 3 * k, 2 * k, l.b);
                    p.rect(i(11 * k), i(21 * k), i(2 * k), i(3 * k), l.c); p.rect(i(19 * k), i(21 * k), i(2 * k), i(3 * k), l.c);
                    }
                Shape.Beast -> {
p.ellipse(15 * k, 19 * k, 11 * k, 7 * k, l.a);                     
                    p.ellipse(25 * k, 15 * k, 6 * k, 5 * k, l.a);                      
                    p.tri(V(29 * k, 14 * k), V(32 * k, 17 * k), V(28 * k, 18 * k), l.a); 
                    p.tri(V(22 * k, 11 * k), V(24 * k, 5 * k), V(26 * k, 11 * k), l.a); 
                    for (i in 0 until 4) p.rect(i((7 + i * 5) * k), i(23 * k), i(3 * k), i(7 * k), l.c);
                    p.ellipse(15 * k, 22 * k, 8 * k, 3 * k, l.b);                       
                    p.line(i(4 * k), i(17 * k), 0, i(12 * k), l.a, maxOf(1, (2 * k).toInt())); 
                    p.rect(i(26 * k), i(13 * k), i(2 * k), i(2 * k), eye);
                    if (boss) { p.tri(V(28 * k, 18 * k), V(31 * k, 22 * k), V(27 * k, 21 * k), l.b); p.tri(V(25 * k, 18 * k), V(27 * k, 23 * k), V(24 * k, 21 * k), l.b); }
                    }
                Shape.Insect -> {
p.ellipse(10 * k, 19 * k, 7 * k, 5 * k, l.a);
                    for (i in 0 until 3) p.rect(i((5 + i * 4) * k), i(15 * k), i(2 * k), i(9 * k), l.b); 
                    p.ellipse(19 * k, 17 * k, 4 * k, 4 * k, l.a);
                    p.ellipse(25 * k, 15 * k, 4 * k, 3.5f * k, l.a);
                    p.tri(V(19 * k, 14 * k), V(8 * k, 4 * k), V(13 * k, 13 * k), withAlpha(l.c, 150));
                    p.tri(V(20 * k, 14 * k), V(14 * k, 2 * k), V(18 * k, 13 * k), withAlpha(l.c, 170));
                    for (i in 0 until 3) p.line(i((17 + i * 3) * k), i(20 * k), i((15 + i * 3) * k), i(28 * k), l.b);
                    p.rect(i(26 * k), i(14 * k), i(2 * k), i(2 * k), glow(hex("#ff3a3a")));
                    p.line(i(4 * k), i(20 * k), i(1 * k), i(23 * k), l.b); 
                    }
                Shape.Plant -> {
if (boss)
                    {
                        p.rect(i(12 * k), i(10 * k), i(9 * k), i(20 * k), l.a);           
                        p.ellipse(16 * k, 9 * k, 14 * k, 8 * k, l.b);                                         
                        p.line(i(12 * k), i(18 * k), i(3 * k), i(12 * k), l.a, 3); p.line(i(21 * k), i(18 * k), i(30 * k), i(12 * k), l.a, 3);
                        p.rect(i(13 * k), i(16 * k), i(2 * k), i(2 * k), glow(l.c)); p.rect(i(18 * k), i(16 * k), i(2 * k), i(2 * k), glow(l.c));
                        p.rect(i(14 * k), i(21 * k), i(5 * k), i(1 * k), hex("#2a1a10"));
                        for (i in 0 until 4) p.line(i((10 + i * 4) * k), i(30 * k), i((8 + i * 5) * k), i(32 * k) - 1, l.a, 2);
                    }
                    else
                    {
                        p.rect(i(12 * k), i(16 * k), i(8 * k), i(12 * k), l.b);
                        p.ellipse(16 * k, 14 * k, 12 * k, 8 * k, l.a);
                        p.rect(i(4 * k), i(14 * k), i(24 * k), i(3 * k), l.a);
                        p.circle(10 * k, 10 * k, 2 * k, l.b); p.circle(20 * k, 9 * k, 2.5f * k, l.b); p.circle(16 * k, 15 * k, 1.5f * k, l.b);
                        p.rect(i(13 * k), i(21 * k), i(2 * k), i(2 * k), l.c); p.rect(i(17 * k), i(21 * k), i(2 * k), i(2 * k), l.c);
                        p.rect(i(10 * k), i(28 * k), i(12 * k), i(2 * k), l.c);
                    }
                    }
                Shape.Undead -> {
p.tri(V(8 * k, 30 * k), V(24 * k, 30 * k), V(16 * k, 12 * k), l.a);
                    p.rect(i(10 * k), i(14 * k), i(12 * k), i(14 * k), l.a);
                    p.circle(16 * k, 10 * k, 5 * k, l.a);
                    for (i in 0 until 5) p.line(i(10 * k), i((15 + i * 3) * k), i(22 * k), i((14 + i * 3) * k), l.b);
                    p.rect(i(6 * k), i(16 * k), i(4 * k), i(3 * k), l.a); p.rect(i(22 * k), i(16 * k), i(5 * k), i(3 * k), l.a);
                    p.rect(i(13 * k), i(9 * k), i(2 * k), i(2 * k), glow(l.c)); p.rect(i(17 * k), i(9 * k), i(2 * k), i(2 * k), glow(l.c));
                    }
                Shape.Spirit -> {
p.circle(16 * k, 12 * k, 8 * k, withAlpha(l.a, 230));
                    p.tri(V(8 * k, 13 * k), V(24 * k, 13 * k), V(13 * k, 31 * k), withAlpha(l.a, 230));
                    p.ellipse(16 * k, 11 * k, 4 * k, 3 * k, l.b);
                    p.rect(i(13 * k), i(10 * k), i(2 * k), i(3 * k), glow(l.c)); p.rect(i(18 * k), i(10 * k), i(2 * k), i(3 * k), glow(l.c));
                    p.line(i(8 * k), i(16 * k), i(2 * k), i(10 * k), l.a, 2); p.line(i(24 * k), i(16 * k), i(30 * k), i(10 * k), l.a, 2);
                    for (i in 0 until 4) p.set(i((5 + i * 7) * k), i((4 + (i % 2) * 20) * k), glow(l.b));
                    }
                Shape.Golem -> {
p.rect(i(8 * k), i(10 * k), i(16 * k), i(14 * k), l.a);
                    p.rect(i(11 * k), i(4 * k), i(10 * k), i(7 * k), l.a);
                    p.rect(i(3 * k), i(11 * k), i(5 * k), i(13 * k), l.c); p.rect(i(24 * k), i(11 * k), i(5 * k), i(13 * k), l.c);
                    p.rect(i(9 * k), i(24 * k), i(5 * k), i(7 * k), l.c); p.rect(i(18 * k), i(24 * k), i(5 * k), i(7 * k), l.c);
                    p.rect(i(13 * k), i(6 * k), i(2 * k), i(2 * k), glow(l.b)); p.rect(i(17 * k), i(6 * k), i(2 * k), i(2 * k), glow(l.b));
                    p.circle(16 * k, 16 * k, 2.5f * k, glow(l.b));
                    p.line(i(10 * k), i(20 * k), i(14 * k), i(22 * k), l.c); p.line(i(20 * k), i(12 * k), i(22 * k), i(18 * k), l.c);
                    }
                Shape.Crab -> {
p.ellipse(16 * k, 20 * k, 11 * k, 6 * k, l.a);
                    p.circle(5 * k, 12 * k, 4 * k, l.a); p.circle(27 * k, 12 * k, 4 * k, l.a);
                    p.tri(V(3 * k, 8 * k), V(7 * k, 8 * k), V(5 * k, 12 * k), CLEAR);
                    p.tri(V(25 * k, 8 * k), V(29 * k, 8 * k), V(27 * k, 12 * k), CLEAR);
                    for (i in 0 until 3) { p.line(i((9 + i * 3) * k), i(24 * k), i((6 + i * 3) * k), i(30 * k), l.a); p.line(i((23 - i * 3) * k), i(24 * k), i((26 - i * 3) * k), i(30 * k), l.a); }
                    p.line(i(13 * k), i(15 * k), i(12 * k), i(11 * k), l.c); p.line(i(19 * k), i(15 * k), i(20 * k), i(11 * k), l.c);
                    p.rect(i(11 * k), i(10 * k), i(2 * k), i(2 * k), glow(hex("#ffffff"))); p.rect(i(19 * k), i(10 * k), i(2 * k), i(2 * k), glow(hex("#ffffff")));
                    p.ellipse(16 * k, 18 * k, 7 * k, 2.5f * k, l.b);
                    }
                Shape.Serpent -> {
p.ellipse(14 * k, 25 * k, 12 * k, 5 * k, l.a);
                    p.ellipse(12 * k, 22 * k, 9 * k, 4 * k, l.b);
                    p.line(i(18 * k), i(24 * k), i(22 * k), i(10 * k), l.a, i(4 * k));
                    p.ellipse(25 * k, 9 * k, 6 * k, 4.5f * k, l.a);                        
                    p.tri(V(29 * k, 7 * k), V(32 * k, 10 * k), V(28 * k, 12 * k), l.a);
                    p.tri(V(21 * k, 6 * k), V(18 * k, 0), V(24 * k, 5 * k), l.c); 
                    p.tri(V(24 * k, 5 * k), V(24 * k, 0), V(27 * k, 5 * k), l.c);
                    
                    val membrane = withAlpha(lerp(l.a, l.c, 0.45f), 235);
                    p.tri(V(18 * k, 14 * k), V(4 * k, 3 * k), V(5 * k, 15 * k), membrane);
                    p.tri(V(18 * k, 14 * k), V(4 * k, 3 * k), V(11 * k, 17 * k), membrane);
                    p.tri(V(18 * k, 14 * k), V(10 * k, 1 * k), V(15 * k, 17 * k), membrane);
                    p.line(i(18 * k), i(14 * k), i(10 * k), i(1 * k), l.c, 2);
                    p.line(i(10 * k), i(1 * k), i(4 * k), i(3 * k), l.c, 2);
                    p.line(i(10 * k), i(1 * k), i(11 * k), i(16 * k), l.c, 1);
                    p.line(i(4 * k), i(3 * k), i(5 * k), i(15 * k), l.c, 1);
                    p.line(i(18 * k), i(24 * k), i(22 * k), i(10 * k), l.a, i(4 * k)); 
                    p.ellipse(25 * k, 9 * k, 6 * k, 4.5f * k, l.a);
                    p.rect(i(26 * k), i(8 * k), i(2 * k), i(2 * k), eye);
                    for (i in 0 until 5) p.set(i((6 + i * 4) * k), i(27 * k), l.c);
                    for (i in 0 until 3) p.tri(V((8 + i * 6) * k, 29 * k), V((10 + i * 6) * k, 32 * k - 1), V((12 + i * 6) * k, 29 * k), l.c); 
                    }
            }
            when (id) {
                "pirate" -> {
p.rect(i(9 * k), i(4 * k), i(14 * k), i(3 * k), hex("#2a2a3a"));
                    p.tri(V(11 * k, 4 * k), V(21 * k, 4 * k), V(16 * k, 0), hex("#2a2a3a"));
                    p.line(i(26 * k), i(18 * k), i(30 * k), i(9 * k), hex("#d0d8e0"), 2);
                    }
                "siren" -> {
p.ellipse(16 * k, 9 * k, 7 * k, 5 * k, hex("#3a6a7a"));
                    p.line(i(10 * k), i(9 * k), i(6 * k), i(22 * k), hex("#3a6a7a"), 2);
                    p.line(i(22 * k), i(9 * k), i(26 * k), i(22 * k), hex("#3a6a7a"), 2);
                    p.rect(i(13 * k), i(10 * k), i(2 * k), i(2 * k), glow(l.c)); p.rect(i(17 * k), i(10 * k), i(2 * k), i(2 * k), glow(l.c));
                    }
                "gargoyle" -> {
p.tri(V(8 * k, 12 * k), V(0, 2 * k), V(2 * k, 20 * k), l.c);
                    p.tri(V(24 * k, 12 * k), V(32 * k - 1, 2 * k), V(30 * k, 20 * k), l.c);
                    p.tri(V(11 * k, 5 * k), V(9 * k, 0), V(13 * k, 4 * k), l.c);
                    p.tri(V(21 * k, 5 * k), V(23 * k, 0), V(19 * k, 4 * k), l.c);
                    }
                "sentinel" -> {
p.circle(16 * k, 16 * k, 4 * k, hex("#8a6a2a")); p.circle(16 * k, 16 * k, 2 * k, glow(l.b));
                    }
            }
            if (boss)
            {
                
                p.rect(i(23 * k), i(2 * k), i(4 * k), i(1 * k), glow(hex("#ffd86b")));
            }
            p.finish();
            return p
        

    }

    fun drawProp(kind: String, palette: String): PixelCanvas {

            val pal = RegionPalette.of(palette)
            val p: PixelCanvas
            when (kind) {
                "tree" -> {
p = PixelCanvas(32, 48);
                    p.rect(13, 28, 6, 20, pal.trunk);
                    p.circle(16, 18, 12, pal.foliage); p.circle(9, 24, 7, pal.foliage); p.circle(23, 24, 7, pal.foliage);
                    p.circle(12, 13, 4, pal.foliageLight); p.circle(20, 20, 3, pal.foliageLight);
                    }
                "rock" -> {
p = PixelCanvas(24, 16);
                    p.ellipse(12, 11, 11, 6, pal.rock); p.ellipse(9, 8, 5, 3, lerp(pal.rock, WHITE, 0.25f));
                    }
                "grass" -> {
p = PixelCanvas(12, 10);
                    for (i in 0 until 6) p.line(1 + i * 2, 9, 1 + i * 2 + (if (i % 2 == 0) -1 else 1), 2 + (i % 3), pal.foliageLight);
                    }
                "pillar" -> {
p = PixelCanvas(16, 48);
                    p.rect(3, 4, 10, 42, pal.rock); p.rect(1, 2, 14, 3, lerp(pal.rock, WHITE, 0.2f)); p.rect(1, 44, 14, 4, lerp(pal.rock, BLACK, 0.2f));
                    p.line(6, 10, 8, 20, lerp(pal.rock, BLACK, 0.3f));
                    }
                "cactus" -> {
p = PixelCanvas(20, 32);
                    p.rect(8, 4, 5, 28, pal.foliage); p.rect(2, 12, 4, 8, pal.foliage); p.rect(2, 18, 7, 3, pal.foliage); p.rect(15, 8, 4, 10, pal.foliage); p.rect(12, 16, 6, 3, pal.foliage);
                    }
                "crystal" -> {
p = PixelCanvas(16, 28);
                    p.tri(V(8, 0), V(15, 20), V(1, 20), glow(pal.accent)); p.rect(2, 20, 12, 8, pal.rock);
                    }
                else -> {
                    p = PixelCanvas(8, 8); p.circle(4, 4, 3, pal.accent); }
            }
            p.finish();
            return p
        

    }

    fun drawIcon(kind: String): PixelCanvas {

            val p = PixelCanvas(16, 16);
            val gold = hex("#e8c35a"); val steel = hex("#d6dee8"); val brown = hex("#7a5232"); val red = hex("#d8463a"); val white = hex("#f3e9d2")
            when (kind) {
                "sword", "phys" -> {
p.line(3, 12, 12, 3, steel, 2); p.line(2, 9, 6, 13, gold, 1); p.line(3, 13, 1, 15, brown, 2); }
                "map" -> {
p.rect(2, 3, 12, 10, hex("#d8c08a")); p.line(5, 3, 5, 12, hex("#a88a5a")); p.line(10, 3, 10, 12, hex("#a88a5a"));
                    p.line(3, 10, 7, 6, red); p.line(7, 6, 12, 8, red); p.set(12, 8, glow(red)); }
                "helm" -> {
p.ellipse(8, 8, 6, 6, steel); p.rect(2, 9, 12, 6, steel); p.rect(4, 8, 8, 2, hex("#2a2a3a")); p.rect(7, 8, 2, 6, hex("#9aa6b4")); p.rect(6, 0, 4, 3, red); }
                "bag" -> {
p.ellipse(8, 10, 6, 5, brown); p.rect(5, 3, 6, 3, brown); p.rect(5, 5, 6, 1, gold); p.circle(8, 10, 2, glow(gold)); }
                "scroll" -> {
p.rect(3, 3, 10, 10, hex("#efe0b8")); p.rect(2, 2, 12, 2, hex("#c8a868")); p.rect(2, 12, 12, 2, hex("#c8a868"));
                    for (i in 0 until 3) p.line(5, 6 + i * 2, 11, 6 + i * 2, hex("#8a6a3a")); }
                "trophy", "star" -> {
p.tri(V(8, 0), V(11, 6), V(5, 6), gold);
                    p.tri(V(0, 6), V(16, 6), V(8, 11), gold);
                    p.tri(V(8, 9), V(3, 15), V(5, 7), gold);
                    p.tri(V(8, 9), V(13, 15), V(11, 7), gold); }
                "star_empty" -> {
p.tri(V(8, 0), V(11, 6), V(5, 6), hex("#5a5448"));
                    p.tri(V(0, 6), V(16, 6), V(8, 11), hex("#5a5448"));
                    p.tri(V(8, 9), V(3, 15), V(5, 7), hex("#5a5448"));
                    p.tri(V(8, 9), V(13, 15), V(11, 7), hex("#5a5448")); }
                "gear" -> {
p.circle(8, 8, 6, steel); for (i in 0 until 4) { p.rect(7, 0, 2, 16, steel); p.rect(0, 7, 16, 2, steel); }
                    p.line(2, 2, 13, 13, steel, 2); p.line(13, 2, 2, 13, steel, 2); p.circle(8, 8, 2.2f, hex("#2a2a3a")); }
                "flame", "fire" -> {
p.ellipse(8, 10, 5, 5, glow(hex("#ff7a2a"))); p.tri(V(3, 10), V(13, 10), V(8, 0), glow(hex("#ff7a2a")));
                    p.ellipse(8, 11, 3, 3, glow(hex("#ffd86b"))); p.tri(V(5, 11), V(11, 11), V(8, 5), glow(hex("#ffd86b"))); }
                "ice" -> {
p.line(8, 1, 8, 15, hex("#9ae6ff"), 2); p.line(2, 4, 14, 12, hex("#9ae6ff"), 2); p.line(2, 12, 14, 4, hex("#9ae6ff"), 2); p.circle(8.5f, 8.5f, 2, glow(white)); }
                "bolt" -> {
p.tri(V(10, 0), V(3, 9), V(9, 9), glow(hex("#ffe85a")));
                    p.tri(V(7, 7), V(13, 7), V(5, 16), glow(hex("#ffe85a"))); }
                "leaf" -> {
p.ellipse(9, 7, 5, 6, hex("#6ad04a")); p.line(3, 14, 11, 4, hex("#2f6a2a")); }
                "holy" -> {
p.rect(6, 1, 4, 14, glow(hex("#fff3c0"))); p.rect(2, 5, 12, 4, glow(hex("#fff3c0"))); }
                "dark" -> {
p.circle(8, 8, 6, hex("#a86ae8")); p.circle(11, 6, 5, CLEAR); }
                "skull" -> {
p.circle(8, 7, 6, white); p.rect(5, 11, 6, 4, white); p.rect(4, 6, 3, 3, hex("#1a1020")); p.rect(9, 6, 3, 3, hex("#1a1020"));
                    p.set(7, 13, hex("#1a1020")); p.set(9, 13, hex("#1a1020")); }
                "lock" -> {
p.rect(3, 7, 10, 8, gold); p.ellipse(8, 6, 4, 4, hex("#9aa6b4")); p.ellipse(8, 6, 2, 2.4f, CLEAR); p.rect(7, 9, 2, 3, hex("#3a2a1a")); }
                "check" -> {
p.line(2, 8, 6, 12, hex("#7be08a"), 2); p.line(6, 12, 13, 3, hex("#7be08a"), 2); }
                "coin" -> {
p.circle(8, 8, 6.5f, gold); p.circle(8, 8, 4, hex("#f6dc8a")); p.rect(7, 5, 2, 6, hex("#b08a2a")); }
                "boot" -> {
p.rect(5, 1, 6, 10, brown); p.rect(5, 10, 10, 4, brown); p.rect(4, 14, 12, 2, hex("#3a2a1a")); p.rect(5, 3, 6, 1, gold); }
                "home" -> {
p.tri(V(0, 8), V(16, 8), V(8, 1), red); p.rect(3, 8, 10, 7, hex("#d8c08a")); p.rect(7, 10, 3, 5, brown); }
                "heart" -> {
p.circle(5, 6, 3.5f, red); p.circle(11, 6, 3.5f, red); p.tri(V(1.5f, 7), V(14.5f, 7), V(8, 15), red); }
                else -> {
                    p.circle(8, 8, 5, gold); }
            }
            p.finish();
            return p
        
    }

    fun dot(): Bitmap = get("fx_dot") {
        PixelCanvas(8, 8).apply { circle(4, 4, 3.5f, argb(120, 255, 255, 255)); circle(4, 4, 2.2f, WHITE) }
    }
}
