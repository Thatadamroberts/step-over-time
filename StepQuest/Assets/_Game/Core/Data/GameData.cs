using System;
using System.Collections.Generic;
using StepQuest.Core.Economy;
using StepQuest.Core.Party;
using StepQuest.Core.World;

namespace StepQuest.Core.Data
{
    /// <summary>
    /// Static game content. Kept in code so it is type-checked and unit-testable;
    /// sprite keys map to assets under Assets/_Game/Art via the Unity SpriteLibrary.
    /// </summary>
    public static partial class GameData
    {
        public static readonly Dictionary<HeroClass, ClassDef> Classes = new Dictionary<HeroClass, ClassDef>();
        public static readonly Dictionary<string, SkillDef> Skills = new Dictionary<string, SkillDef>();
        public static readonly Dictionary<string, ItemDef> Items = new Dictionary<string, ItemDef>();
        public static readonly Dictionary<string, EnemyDef> Enemies = new Dictionary<string, EnemyDef>();
        public static readonly List<RegionDef> Regions = new List<RegionDef>();

        static GameData()
        {
            BuildSkills();
            BuildClasses();
            BuildItems();
            BuildEnemies();
            BuildRegions();
        }

        public static ClassDef Class(HeroClass c) => Classes[c];
        public static SkillDef Skill(string id) => Skills[id];
        public static SkillDef SkillOrNull(string id) => id != null && Skills.TryGetValue(id, out var s) ? s : null;
        public static ItemDef Item(string id) => Items[id];
        public static ItemDef ItemOrNull(string id) => id != null && Items.TryGetValue(id, out var i) ? i : null;
        public static EnemyDef Enemy(string id) => Enemies[id];
        public static RegionDef Region(string id) => Regions.Find(r => r.Id == id);

        public static List<Hero> StartingParty() => new List<Hero>
        {
            new Hero("Brannoc", HeroClass.Warrior) { WeaponId = "rusty_sword", ArmorId = "leather_vest" },
            new Hero("Vess", HeroClass.Rogue) { WeaponId = "worn_dagger", ArmorId = "leather_vest" },
            new Hero("Ilsabet", HeroClass.Mage) { WeaponId = "oak_wand", ArmorId = "cloth_robe" },
            new Hero("Thornwick", HeroClass.Druid) { WeaponId = "ash_staff", ArmorId = "cloth_robe" },
            new Hero("Sister Maren", HeroClass.Priest) { WeaponId = "wooden_mace", ArmorId = "cloth_robe" },
        };

        static void S(string id, string name, string desc, int mp, TargetKind t, SkillEffect fx, Element e, int power, bool magical, int shieldHits = 1, int turns = 0)
        {
            Skills[id] = new SkillDef { Id = id, Name = name, Description = desc, MpCost = mp, Target = t, Effect = fx, Element = e, Power = power, Magical = magical, ShieldHits = shieldHits, Turns = turns };
        }

        static void BuildSkills()
        {
            // Warrior
            S("provoke", "Provoke", "Draw enemy attacks and raise defense for 3 turns.", 4, TargetKind.Self, SkillEffect.Taunt, Element.Physical, 0, false, 0, 3);
            S("cleave", "Cleave", "Strike all enemies.", 8, TargetKind.AllEnemies, SkillEffect.Damage, Element.Physical, 80, false);
            S("war_cry", "War Cry", "Raise the party's attack for 3 turns.", 10, TargetKind.AllAllies, SkillEffect.BuffAtk, Element.Physical, 0, false, 0, 3);
            S("earthsplitter", "Earthsplitter", "A crushing blow that shatters shields.", 16, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Physical, 260, false, 3);
            // Rogue
            S("backstab", "Backstab", "A precise strike from the shadows.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Physical, 160, false);
            S("pilfer", "Pilfer", "Strike and steal coins.", 3, TargetKind.SingleEnemy, SkillEffect.StealCoins, Element.Physical, 70, false);
            S("venom_blade", "Venom Blade", "Poison an enemy for 4 turns.", 6, TargetKind.SingleEnemy, SkillEffect.Poison, Element.Dark, 60, false, 1, 4);
            S("flurry", "Flurry", "Rapid cuts that hit for 2 shield points.", 12, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Physical, 190, false, 2);
            // Mage
            S("fire", "Flame", "Fire damage to one enemy.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Fire, 150, true);
            S("ice", "Frost", "Ice damage to one enemy.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Ice, 150, true);
            S("bolt", "Spark", "Lightning damage to one enemy.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Lightning, 150, true);
            S("inferno", "Inferno", "Fire damage to all enemies.", 14, TargetKind.AllEnemies, SkillEffect.Damage, Element.Fire, 120, true);
            S("blizzard", "Blizzard", "Ice damage to all enemies.", 14, TargetKind.AllEnemies, SkillEffect.Damage, Element.Ice, 120, true);
            S("tempest", "Tempest", "Lightning damage to all enemies.", 14, TargetKind.AllEnemies, SkillEffect.Damage, Element.Lightning, 120, true);
            S("meteor", "Starfall", "Overwhelming fire, 3 shield hits.", 28, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Fire, 340, true, 3);
            // Druid
            S("thorns", "Thorns", "Nature damage that poisons for 3 turns.", 4, TargetKind.SingleEnemy, SkillEffect.Poison, Element.Nature, 100, true, 1, 3);
            S("bark_skin", "Bark Skin", "Raise an ally's defense for 3 turns.", 4, TargetKind.SingleAlly, SkillEffect.BuffDef, Element.Nature, 0, true, 0, 3);
            S("rejuvenate", "Rejuvenate", "Heal an ally each turn for 4 turns.", 7, TargetKind.SingleAlly, SkillEffect.Regen, Element.Nature, 50, true, 0, 4);
            S("entangle", "Entangle", "Nature damage to all enemies.", 12, TargetKind.AllEnemies, SkillEffect.Damage, Element.Nature, 110, true);
            S("wild_growth", "Wild Growth", "Regen on the whole party.", 16, TargetKind.AllAllies, SkillEffect.Regen, Element.Nature, 40, true, 0, 4);
            // Priest
            S("heal", "Mend", "Restore an ally's HP.", 4, TargetKind.SingleAlly, SkillEffect.Heal, Element.Holy, 180, true);
            S("raise", "Raise", "Revive a fallen ally with 40% HP.", 12, TargetKind.DeadAlly, SkillEffect.Revive, Element.Holy, 40, true);
            S("smite", "Smite", "Holy damage to one enemy.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Holy, 140, true);
            S("sanctuary", "Sanctuary", "Heal the whole party.", 14, TargetKind.AllAllies, SkillEffect.Heal, Element.Holy, 110, true);
            S("aegis", "Aegis", "Raise the party's defense for 3 turns.", 12, TargetKind.AllAllies, SkillEffect.BuffDef, Element.Holy, 0, true, 0, 3);
            S("judgement", "Judgement", "Holy light on all enemies.", 20, TargetKind.AllEnemies, SkillEffect.Damage, Element.Holy, 150, true, 2);
        }

        static SkillTreeNode N(string id, string name, string desc, string requires, int minLevel, string skill = null, Stats bonus = default, int cost = 1)
            => new SkillTreeNode { Id = id, Name = name, Description = desc, Requires = requires, MinLevel = minLevel, UnlocksSkill = skill, Bonus = bonus, Cost = cost };

        static void BuildClasses()
        {
            Classes[HeroClass.Warrior] = new ClassDef
            {
                Class = HeroClass.Warrior, Name = "Warrior", Blurb = "A stalwart shield for the party.",
                Base = new Stats(120, 20, 16, 14, 4, 6, 8, 5), Growth = new Stats(14, 2, 3, 3, 0, 1, 1, 0),
                StartingSkills = { "provoke" },
                Tree =
                {
                    N("w_tough", "Toughness", "+40 max HP", null, 2, bonus: Stats.Of(StatType.MaxHp, 40)),
                    N("w_cleave", "Cleave", "Learn Cleave", null, 3, "cleave"),
                    N("w_guard", "Iron Guard", "+6 DEF", "w_tough", 6, bonus: Stats.Of(StatType.Def, 6)),
                    N("w_cry", "War Cry", "Learn War Cry", "w_cleave", 8, "war_cry"),
                    N("w_might", "Might", "+8 ATK", "w_cry", 12, bonus: Stats.Of(StatType.Atk, 8)),
                    N("w_bulwark", "Bulwark", "+100 max HP", "w_guard", 16, bonus: Stats.Of(StatType.MaxHp, 100), cost: 2),
                    N("w_split", "Earthsplitter", "Learn Earthsplitter", "w_might", 20, "earthsplitter", cost: 2),
                    N("w_legend", "Unbreakable", "+12 DEF, +12 RES", "w_bulwark", 30, bonus: new Stats(0, 0, 0, 12, 0, 12, 0, 0), cost: 3),
                }
            };
            Classes[HeroClass.Rogue] = new ClassDef
            {
                Class = HeroClass.Rogue, Name = "Rogue", Blurb = "Quick, sly and deadly.",
                Base = new Stats(85, 25, 14, 8, 6, 7, 16, 15), Growth = new Stats(9, 2, 3, 1, 1, 1, 2, 0),
                StartingSkills = { "backstab", "pilfer" },
                Tree =
                {
                    N("r_quick", "Quickstep", "+4 SPD", null, 2, bonus: Stats.Of(StatType.Spd, 4)),
                    N("r_venom", "Venom Blade", "Learn Venom Blade", null, 3, "venom_blade"),
                    N("r_keen", "Keen Eye", "+8% crit", "r_quick", 6, bonus: Stats.Of(StatType.Crit, 8)),
                    N("r_edge", "Honed Edge", "+6 ATK", "r_venom", 8, bonus: Stats.Of(StatType.Atk, 6)),
                    N("r_flurry", "Flurry", "Learn Flurry", "r_edge", 12, "flurry"),
                    N("r_shadow", "Shadowstep", "+8 SPD", "r_keen", 16, bonus: Stats.Of(StatType.Spd, 8), cost: 2),
                    N("r_lethal", "Lethality", "+12% crit", "r_shadow", 22, bonus: Stats.Of(StatType.Crit, 12), cost: 2),
                    N("r_legend", "Night's Kiss", "+14 ATK, +6 SPD", "r_flurry", 30, bonus: new Stats(0, 0, 14, 0, 0, 0, 6, 0), cost: 3),
                }
            };
            Classes[HeroClass.Mage] = new ClassDef
            {
                Class = HeroClass.Mage, Name = "Mage", Blurb = "Master of fire, frost and storm.",
                Base = new Stats(70, 60, 5, 6, 18, 12, 10, 3), Growth = new Stats(7, 6, 0, 1, 4, 2, 1, 0),
                WeaponElement = Element.Physical,
                StartingSkills = { "fire", "ice", "bolt" },
                Tree =
                {
                    N("m_focus", "Focus", "+20 max MP", null, 2, bonus: Stats.Of(StatType.MaxMp, 20)),
                    N("m_inferno", "Inferno", "Learn Inferno", null, 4, "inferno"),
                    N("m_blizzard", "Blizzard", "Learn Blizzard", "m_inferno", 7, "blizzard"),
                    N("m_tempest", "Tempest", "Learn Tempest", "m_blizzard", 10, "tempest"),
                    N("m_arcane", "Arcane Mind", "+8 MAG", "m_focus", 8, bonus: Stats.Of(StatType.Mag, 8)),
                    N("m_ward", "Mana Ward", "+8 RES, +30 HP", "m_arcane", 14, bonus: new Stats(30, 0, 0, 0, 0, 8, 0, 0), cost: 2),
                    N("m_meteor", "Starfall", "Learn Starfall", "m_tempest", 22, "meteor", cost: 2),
                    N("m_legend", "Archmage", "+16 MAG, +40 MP", "m_ward", 30, bonus: new Stats(0, 40, 0, 0, 16, 0, 0, 0), cost: 3),
                }
            };
            Classes[HeroClass.Druid] = new ClassDef
            {
                Class = HeroClass.Druid, Name = "Druid", Blurb = "Speaks for the wild; heals and harries.",
                Base = new Stats(90, 45, 8, 9, 13, 12, 9, 4), Growth = new Stats(10, 4, 1, 2, 3, 2, 1, 0),
                StartingSkills = { "thorns", "bark_skin" },
                Tree =
                {
                    N("d_root", "Deep Roots", "+30 max HP", null, 2, bonus: Stats.Of(StatType.MaxHp, 30)),
                    N("d_rejuv", "Rejuvenate", "Learn Rejuvenate", null, 3, "rejuvenate"),
                    N("d_grove", "Grove Wisdom", "+6 MAG", "d_rejuv", 6, bonus: Stats.Of(StatType.Mag, 6)),
                    N("d_entangle", "Entangle", "Learn Entangle", "d_grove", 9, "entangle"),
                    N("d_hide", "Bear Hide", "+6 DEF, +6 RES", "d_root", 10, bonus: new Stats(0, 0, 0, 6, 0, 6, 0, 0)),
                    N("d_growth", "Wild Growth", "Learn Wild Growth", "d_entangle", 16, "wild_growth", cost: 2),
                    N("d_spring", "Wellspring", "+30 max MP", "d_hide", 20, bonus: Stats.Of(StatType.MaxMp, 30), cost: 2),
                    N("d_legend", "Avatar of the Wild", "+10 MAG, +60 HP", "d_growth", 30, bonus: new Stats(60, 0, 0, 0, 10, 0, 0, 0), cost: 3),
                }
            };
            Classes[HeroClass.Priest] = new ClassDef
            {
                Class = HeroClass.Priest, Name = "Priest", Blurb = "Keeps everyone standing.",
                Base = new Stats(80, 55, 7, 8, 15, 15, 9, 3), Growth = new Stats(8, 5, 1, 1, 3, 3, 1, 0),
                StartingSkills = { "heal", "smite" },
                Tree =
                {
                    N("p_devotion", "Devotion", "+20 max MP", null, 2, bonus: Stats.Of(StatType.MaxMp, 20)),
                    N("p_raise", "Raise", "Learn Raise", null, 4, "raise"),
                    N("p_grace", "Grace", "+6 MAG", "p_devotion", 6, bonus: Stats.Of(StatType.Mag, 6)),
                    N("p_sanct", "Sanctuary", "Learn Sanctuary", "p_raise", 9, "sanctuary"),
                    N("p_aegis", "Aegis", "Learn Aegis", "p_sanct", 13, "aegis"),
                    N("p_faith", "Steadfast Faith", "+10 RES, +40 HP", "p_grace", 16, bonus: new Stats(40, 0, 0, 0, 0, 10, 0, 0), cost: 2),
                    N("p_judge", "Judgement", "Learn Judgement", "p_aegis", 22, "judgement", cost: 2),
                    N("p_legend", "Saint", "+14 MAG, +40 MP", "p_faith", 30, bonus: new Stats(0, 40, 0, 0, 14, 0, 0, 0), cost: 3),
                }
            };
        }

        static void I(string id, string name, EquipSlot slot, Rarity r, int price, int tier, Stats bonus, string desc, params HeroClass[] classes)
        {
            Items[id] = new ItemDef { Id = id, Name = name, Slot = slot, Rarity = r, Price = price, ShopTier = tier, Bonus = bonus, Description = desc, Classes = classes };
        }

        static void C(string id, string name, int price, int tier, ConsumableEffect use, int amount, string desc)
        {
            Items[id] = new ItemDef { Id = id, Name = name, Slot = EquipSlot.None, Rarity = Rarity.Common, Price = price, ShopTier = tier, Use = use, UseAmount = amount, Description = desc };
        }

        static readonly HeroClass[] Blades = { HeroClass.Warrior, HeroClass.Rogue };
        static readonly HeroClass[] Casters = { HeroClass.Mage, HeroClass.Druid, HeroClass.Priest };

        static void BuildItems()
        {
            // Consumables
            C("tonic", "Healing Tonic", 20, 0, ConsumableEffect.HealHp, 80, "Restores 80 HP.");
            C("hi_tonic", "Greater Tonic", 70, 2, ConsumableEffect.HealHp, 300, "Restores 300 HP.");
            C("ether", "Ether Drop", 45, 0, ConsumableEffect.HealMp, 30, "Restores 30 MP.");
            C("hi_ether", "Ether Flask", 140, 3, ConsumableEffect.HealMp, 100, "Restores 100 MP.");
            C("ember_feather", "Ember Feather", 120, 1, ConsumableEffect.Revive, 50, "Revives a fallen ally with 50% HP.");
            C("antidote", "Herbal Antidote", 15, 0, ConsumableEffect.Cleanse, 0, "Cures poison.");

            // Weapons — tier = region index where the shop starts selling it.
            I("rusty_sword", "Rusty Sword", EquipSlot.Weapon, Rarity.Common, 0, -1, Stats.Of(StatType.Atk, 3), "Seen better days.", HeroClass.Warrior);
            I("worn_dagger", "Worn Dagger", EquipSlot.Weapon, Rarity.Common, 0, -1, new Stats(0, 0, 3, 0, 0, 0, 1, 2), "Still sharp enough.", HeroClass.Rogue);
            I("oak_wand", "Oak Wand", EquipSlot.Weapon, Rarity.Common, 0, -1, Stats.Of(StatType.Mag, 3), "Hums faintly.", HeroClass.Mage);
            I("ash_staff", "Ash Staff", EquipSlot.Weapon, Rarity.Common, 0, -1, new Stats(0, 0, 2, 0, 2, 0, 0, 0), "Carved from a storm-felled ash.", HeroClass.Druid);
            I("wooden_mace", "Wooden Mace", EquipSlot.Weapon, Rarity.Common, 0, -1, new Stats(0, 0, 2, 0, 2, 0, 0, 0), "Blessed, mostly.", HeroClass.Priest);

            I("iron_sword", "Iron Longsword", EquipSlot.Weapon, Rarity.Common, 120, 0, Stats.Of(StatType.Atk, 8), "Reliable steel.", HeroClass.Warrior);
            I("steel_dirk", "Steel Dirk", EquipSlot.Weapon, Rarity.Common, 110, 0, new Stats(0, 0, 6, 0, 0, 0, 2, 4), "Light and wicked.", HeroClass.Rogue);
            I("apprentice_rod", "Apprentice Rod", EquipSlot.Weapon, Rarity.Common, 110, 0, Stats.Of(StatType.Mag, 8), "Standard academy issue.", HeroClass.Mage);
            I("hazel_staff", "Hazel Staff", EquipSlot.Weapon, Rarity.Common, 110, 0, new Stats(0, 5, 3, 0, 6, 0, 0, 0), "Green buds still on it.", HeroClass.Druid);
            I("iron_mace", "Iron Mace", EquipSlot.Weapon, Rarity.Common, 110, 0, new Stats(0, 5, 4, 0, 5, 0, 0, 0), "For smiting, gently.", HeroClass.Priest);

            I("knight_blade", "Knight's Blade", EquipSlot.Weapon, Rarity.Uncommon, 480, 2, Stats.Of(StatType.Atk, 18), "Forged for the royal guard.", HeroClass.Warrior);
            I("shadow_kris", "Shadow Kris", EquipSlot.Weapon, Rarity.Uncommon, 460, 2, new Stats(0, 0, 14, 0, 0, 0, 4, 8), "Wavy blade, drinks light.", HeroClass.Rogue);
            I("ember_staff", "Ember Staff", EquipSlot.Weapon, Rarity.Uncommon, 460, 2, new Stats(0, 10, 0, 0, 18, 0, 0, 0), "Warm to the touch.", HeroClass.Mage);
            I("moonwood_staff", "Moonwood Staff", EquipSlot.Weapon, Rarity.Uncommon, 460, 2, new Stats(0, 10, 4, 0, 14, 4, 0, 0), "Glows at night.", HeroClass.Druid);
            I("silver_scepter", "Silver Scepter", EquipSlot.Weapon, Rarity.Uncommon, 460, 2, new Stats(0, 10, 4, 0, 14, 4, 0, 0), "Rings like a bell.", HeroClass.Priest);

            I("dragon_cleaver", "Dragon Cleaver", EquipSlot.Weapon, Rarity.Rare, 1500, 4, new Stats(0, 0, 32, 4, 0, 0, 0, 3), "Once split a wyvern's scale.", HeroClass.Warrior);
            I("whisper", "Whisper", EquipSlot.Weapon, Rarity.Rare, 1450, 4, new Stats(0, 0, 26, 0, 0, 0, 8, 14), "You never hear it coming.", HeroClass.Rogue);
            I("astral_rod", "Astral Rod", EquipSlot.Weapon, Rarity.Rare, 1450, 4, new Stats(0, 25, 0, 0, 32, 6, 0, 0), "Maps the stars.", HeroClass.Mage);
            I("worldroot", "Worldroot Staff", EquipSlot.Weapon, Rarity.Rare, 1450, 4, new Stats(40, 20, 6, 0, 26, 6, 0, 0), "Cut from the eldest tree.", HeroClass.Druid);
            I("dawn_scepter", "Dawn Scepter", EquipSlot.Weapon, Rarity.Rare, 1450, 4, new Stats(0, 25, 6, 0, 26, 8, 0, 0), "Brings the sunrise.", HeroClass.Priest);

            // Legendary — boss drops only.
            I("sunforged_blade", "Sunforged Blade", EquipSlot.Weapon, Rarity.Legendary, 5000, -1, new Stats(50, 0, 48, 10, 0, 10, 4, 6), "Dropped by the Sky Tyrant.", Blades);
            I("starweave_staff", "Starweave Staff", EquipSlot.Weapon, Rarity.Legendary, 5000, -1, new Stats(30, 50, 0, 6, 48, 16, 4, 0), "Dropped by the Sky Tyrant.", Casters);

            // Armor
            I("leather_vest", "Leather Vest", EquipSlot.Armor, Rarity.Common, 0, -1, Stats.Of(StatType.Def, 2), "Patched at the elbows.");
            I("cloth_robe", "Cloth Robe", EquipSlot.Armor, Rarity.Common, 0, -1, new Stats(0, 0, 0, 1, 0, 2, 0, 0), "Comfortable.");
            I("chain_mail", "Chain Mail", EquipSlot.Armor, Rarity.Common, 150, 0, new Stats(10, 0, 0, 7, 0, 2, -1, 0), "Clinks as you walk.", HeroClass.Warrior);
            I("studded_leather", "Studded Leather", EquipSlot.Armor, Rarity.Common, 130, 0, new Stats(5, 0, 0, 5, 0, 2, 0, 0), "Light protection.", HeroClass.Rogue, HeroClass.Druid);
            I("silk_robe", "Silk Robe", EquipSlot.Armor, Rarity.Common, 130, 0, new Stats(0, 10, 0, 3, 0, 6, 0, 0), "Embroidered runes.", HeroClass.Mage, HeroClass.Priest, HeroClass.Druid);
            I("plate_armor", "Plate Armor", EquipSlot.Armor, Rarity.Uncommon, 600, 2, new Stats(40, 0, 0, 16, 0, 6, -2, 0), "Heavy but safe.", HeroClass.Warrior);
            I("shadow_garb", "Shadow Garb", EquipSlot.Armor, Rarity.Uncommon, 560, 2, new Stats(20, 0, 0, 10, 0, 6, 3, 3), "Blends with dusk.", HeroClass.Rogue);
            I("sage_vestment", "Sage Vestment", EquipSlot.Armor, Rarity.Uncommon, 560, 2, new Stats(20, 20, 0, 7, 0, 14, 0, 0), "Woven with silver thread.", Casters);
            I("dragonscale", "Dragonscale Mail", EquipSlot.Armor, Rarity.Rare, 1800, 4, new Stats(80, 0, 0, 28, 0, 16, 0, 0), "Shrugs off flame.", Blades);
            I("celestial_robe", "Celestial Robe", EquipSlot.Armor, Rarity.Rare, 1800, 4, new Stats(50, 40, 0, 14, 0, 28, 0, 0), "Shimmers like the night sky.", Casters);

            // Accessories
            I("lucky_coin", "Lucky Coin", EquipSlot.Accessory, Rarity.Common, 90, 0, Stats.Of(StatType.Crit, 5), "Always lands heads.");
            I("copper_ring", "Copper Ring", EquipSlot.Accessory, Rarity.Common, 80, 0, Stats.Of(StatType.MaxHp, 25), "Turns your finger green.");
            I("walkers_charm", "Walker's Charm", EquipSlot.Accessory, Rarity.Uncommon, 300, 1, new Stats(20, 10, 2, 2, 2, 2, 3, 0), "Warm after a long walk.");
            I("sapphire_pendant", "Sapphire Pendant", EquipSlot.Accessory, Rarity.Uncommon, 380, 2, new Stats(0, 30, 0, 0, 6, 4, 0, 0), "Cool and calm.");
            I("ruby_band", "Ruby Band", EquipSlot.Accessory, Rarity.Uncommon, 380, 2, new Stats(0, 0, 8, 0, 0, 0, 0, 4), "Burns with resolve.");
            I("pilgrims_sigil", "Pilgrim's Sigil", EquipSlot.Accessory, Rarity.Rare, 1200, 4, new Stats(60, 30, 6, 6, 6, 6, 4, 4), "Earned on the long road.");
            I("moonstone", "Moonstone", EquipSlot.Accessory, Rarity.Legendary, 4000, -1, new Stats(100, 60, 10, 10, 10, 10, 6, 6), "A shard of the Moon. Fitting.");
        }

        static EnemyDef E(string id, string name, Stats stats, int shield, int exp, int coins, params Element[] weak)
        {
            // EXP is scaled up because each command costs 1,000 real steps; see BalanceTests for the pacing targets.
            if (HpPacing.TryGetValue(id, out var pct) || HpPacing.TryGetValue(_pacingGroup ?? "", out pct))
                stats.MaxHp = Math.Max(1, stats.MaxHp * pct / 100);
            int power = EnemyPowerPercent * (id.StartsWith("boss_") ? BossPowerPercent : 100) / 100;
            stats.Atk = stats.Atk * power / 100;
            stats.Mag = stats.Mag * power / 100;
            var e = new EnemyDef { Id = id, Name = name, Sprite = id, Stats = stats, Shield = shield, Exp = exp * ExpScale, Coins = coins, Weaknesses = weak };
            Enemies[id] = e;
            return e;
        }

        const int ExpScale = 2;

        /// <summary>
        /// HP pacing per enemy group (percent of the listed HP). Targets at the region's recommended level:
        /// ~3–4 commands for a normal fight, ~12 (first boss) rising to ~45 (final boss). See BalanceTests.
        /// </summary>
        static readonly Dictionary<string, int> HpPacing = new Dictionary<string, int>
        {
            { "meadow", 65 }, { "forest", 50 }, { "desert", 45 }, { "coast", 45 }, { "peaks", 32 }, { "ruins", 26 },
            { "boss_boar", 90 }, { "boss_treant", 63 }, { "boss_wyrm", 50 }, { "boss_kraken", 45 }, { "boss_dragon", 47 }, { "boss_tyrant", 18 },
        };
        static string _pacingGroup;

        /// <summary>Global enemy ATK/MAG multiplier (percent): the difficulty knob.</summary>
        public static int EnemyPowerPercent = 130;
        /// <summary>Applied on top for bosses, which must threaten a full party.</summary>
        public static int BossPowerPercent = 230;

        static void BuildEnemies()
        {
            // Region 0 — Greenmoor Meadow
            _pacingGroup = "meadow";
            E("slime", "Dew Slime", new Stats(40, 0, 10, 4, 4, 2, 6, 0), 2, 8, 6, Element.Fire, Element.Lightning).Loot.Add(new LootEntry("tonic", 0.2));
            E("rat", "Burrow Rat", new Stats(32, 0, 12, 3, 0, 2, 11, 5), 1, 7, 5, Element.Physical, Element.Fire);
            E("wasp", "Meadow Wasp", new Stats(28, 0, 13, 2, 0, 3, 14, 5), 2, 9, 6, Element.Ice, Element.Nature);
            var b0 = E("boss_boar", "Ironhide Boar", new Stats(420, 0, 22, 10, 0, 6, 9, 5), 5, 120, 150, Element.Fire, Element.Holy);
            b0.IsBoss = true; b0.AoeEvery = 4; b0.Loot.Add(new LootEntry("walkers_charm", 1.0));

            // Region 1 — Whisperwood
            _pacingGroup = "forest";
            E("wolf", "Gloam Wolf", new Stats(80, 0, 20, 8, 0, 6, 15, 8), 2, 18, 12, Element.Fire, Element.Physical).Loot.Add(new LootEntry("tonic", 0.25));
            E("sprite", "Thorn Sprite", new Stats(60, 20, 8, 6, 18, 12, 13, 0), 2, 20, 14, Element.Fire, Element.Ice).AttackElement = Element.Nature;
            E("mushroom", "Sporecap", new Stats(110, 0, 16, 12, 6, 4, 5, 0), 3, 22, 15, Element.Fire, Element.Lightning).Loot.Add(new LootEntry("antidote", 0.4));
            var b1 = E("boss_treant", "Elderbark Treant", new Stats(1100, 0, 34, 20, 20, 14, 7, 0), 7, 360, 400, Element.Fire, Element.Lightning);
            b1.IsBoss = true; b1.AoeEvery = 3; b1.AttackElement = Element.Nature; b1.Loot.Add(new LootEntry("ember_feather", 1.0));

            // Region 2 — Sunscorch Dunes
            _pacingGroup = "desert";
            E("scorpion", "Glass Scorpion", new Stats(150, 0, 32, 20, 0, 8, 12, 10), 3, 40, 26, Element.Ice, Element.Lightning).Loot.Add(new LootEntry("antidote", 0.3));
            E("mummy", "Sand-wrapped Dead", new Stats(190, 0, 30, 14, 0, 10, 7, 0), 3, 44, 28, Element.Fire, Element.Holy);
            E("djinn", "Dust Djinn", new Stats(130, 40, 14, 10, 34, 24, 16, 0), 3, 46, 30, Element.Ice, Element.Physical).AttackElement = Element.Lightning;
            var b2 = E("boss_wyrm", "Dune Wyrm", new Stats(2400, 0, 48, 30, 30, 20, 11, 5), 8, 900, 900, Element.Ice, Element.Lightning);
            b2.IsBoss = true; b2.AoeEvery = 3; b2.AttackElement = Element.Fire; b2.Loot.Add(new LootEntry("ruby_band", 1.0));

            // Region 3 — Saltspray Coast
            _pacingGroup = "coast";
            E("crab", "Reef Crab", new Stats(260, 0, 44, 36, 0, 12, 8, 5), 4, 70, 40, Element.Lightning, Element.Physical).Loot.Add(new LootEntry("hi_tonic", 0.15));
            E("siren", "Drowned Siren", new Stats(200, 60, 20, 16, 46, 32, 17, 0), 3, 74, 44, Element.Lightning, Element.Dark).AttackElement = Element.Ice;
            E("pirate", "Ghost Corsair", new Stats(240, 0, 50, 22, 0, 18, 15, 12), 3, 78, 50, Element.Holy, Element.Fire).Loot.Add(new LootEntry("lucky_coin", 0.1));
            var b3 = E("boss_kraken", "Abyssal Kraken", new Stats(4200, 0, 62, 36, 46, 30, 12, 0), 9, 1800, 1600, Element.Lightning, Element.Fire);
            b3.IsBoss = true; b3.AoeEvery = 3; b3.AttackElement = Element.Ice; b3.Loot.Add(new LootEntry("sapphire_pendant", 1.0));

            // Region 4 — Frostfang Peaks
            _pacingGroup = "peaks";
            E("yeti", "Frost Yeti", new Stats(420, 0, 66, 34, 0, 20, 12, 5), 4, 120, 70, Element.Fire, Element.Physical).Loot.Add(new LootEntry("hi_ether", 0.15));
            E("harpy", "Storm Harpy", new Stats(300, 40, 54, 22, 40, 26, 22, 10), 3, 118, 66, Element.Ice, Element.Physical).AttackElement = Element.Lightning;
            E("golem", "Rune Golem", new Stats(560, 0, 60, 60, 0, 20, 6, 0), 5, 130, 80, Element.Lightning, Element.Nature);
            var b4 = E("boss_dragon", "Rimeclaw Dragon", new Stats(7200, 0, 82, 48, 64, 40, 14, 5), 10, 3200, 2800, Element.Fire, Element.Holy);
            b4.IsBoss = true; b4.AoeEvery = 3; b4.AttackElement = Element.Ice; b4.Loot.Add(new LootEntry("pilgrims_sigil", 1.0));

            // Region 5 — Skyreach Ruins
            _pacingGroup = "ruins";
            E("gargoyle", "Sky Gargoyle", new Stats(620, 0, 82, 60, 0, 30, 14, 5), 4, 200, 110, Element.Lightning, Element.Holy).Loot.Add(new LootEntry("hi_tonic", 0.2));
            E("wraith", "Star Wraith", new Stats(480, 80, 40, 30, 80, 50, 20, 0), 4, 210, 120, Element.Holy, Element.Fire).AttackElement = Element.Dark;
            E("sentinel", "Clockwork Sentinel", new Stats(700, 0, 86, 70, 0, 40, 10, 0), 5, 220, 130, Element.Lightning, Element.Ice);
            var b5 = E("boss_tyrant", "The Sky Tyrant", new Stats(12000, 0, 78, 60, 68, 60, 18, 8), 12, 6000, 6000, Element.Holy, Element.Nature);
            b5.IsBoss = true; b5.AoeEvery = 3; b5.AttackElement = Element.Dark;
            b5.Loot.Add(new LootEntry("sunforged_blade", 1.0)); b5.Loot.Add(new LootEntry("starweave_staff", 1.0)); b5.Loot.Add(new LootEntry("moonstone", 1.0));
        }

        static void R(string id, string name, double km, int lvl, string palette, string boss, string desc, params string[] enc)
        {
            Regions.Add(new RegionDef { Index = Regions.Count, Id = id, Name = name, UnlockKm = km, RecommendedLevel = lvl, Palette = palette, BossId = boss, Description = desc, Encounters = enc });
        }

        static void BuildRegions()
        {
            R("meadow", "Greenmoor Meadow", 0, 1, "meadow", "boss_boar", "Rolling hills where every journey begins.", "slime", "rat", "wasp");
            R("forest", "Whisperwood", 15, 5, "forest", "boss_treant", "Ancient trees that remember every footstep.", "wolf", "sprite", "mushroom");
            R("desert", "Sunscorch Dunes", 60, 10, "desert", "boss_wyrm", "Shifting sand over buried kingdoms.", "scorpion", "mummy", "djinn");
            R("coast", "Saltspray Coast", 150, 16, "coast", "boss_kraken", "Wrecks, reefs and restless tides.", "crab", "siren", "pirate");
            R("peaks", "Frostfang Peaks", 300, 22, "peaks", "boss_dragon", "Thin air and thinner patience.", "yeti", "harpy", "golem");
            R("ruins", "Skyreach Ruins", 500, 28, "ruins", "boss_tyrant", "A broken city drifting above the clouds.", "gargoyle", "wraith", "sentinel");
        }
    }
}
