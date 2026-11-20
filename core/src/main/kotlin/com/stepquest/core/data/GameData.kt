package com.stepquest.core.data

import com.stepquest.core.economy.ConsumableEffect
import com.stepquest.core.economy.EquipSlot
import com.stepquest.core.economy.ItemDef
import com.stepquest.core.economy.LootEntry
import com.stepquest.core.economy.Rarity
import com.stepquest.core.party.ClassDef
import com.stepquest.core.party.Element
import com.stepquest.core.party.Hero
import com.stepquest.core.party.HeroClass
import com.stepquest.core.party.SkillDef
import com.stepquest.core.party.SkillEffect
import com.stepquest.core.party.SkillTreeNode
import com.stepquest.core.party.StatType
import com.stepquest.core.party.Stats
import com.stepquest.core.party.TargetKind
import com.stepquest.core.world.EnemyDef
import com.stepquest.core.world.RegionDef
import kotlin.math.max

/** Static game content, kept in code so it is type-checked and unit-tested. */
object GameData {
    val classes = LinkedHashMap<HeroClass, ClassDef>()
    val skills = LinkedHashMap<String, SkillDef>()
    val items = LinkedHashMap<String, ItemDef>()
    val enemies = LinkedHashMap<String, EnemyDef>()
    val regions = mutableListOf<RegionDef>()

    /** EXP is scaled up because each command costs 1,000 real steps; see BalanceTest. */
    private const val EXP_SCALE = 2
    /** Global enemy ATK/MAG multiplier (percent): the difficulty knob. */
    var enemyPowerPercent = 130
    /** Applied on top for bosses, which must threaten a full party. */
    var bossPowerPercent = 230

    /**
     * HP pacing per enemy group (percent of listed HP). Targets at the recommended level:
     * ~3–5 commands for a normal fight, ~12 (first boss) rising to ~45 (final boss).
     */
    private val hpPacing = mapOf(
        "meadow" to 65, "forest" to 50, "desert" to 45, "coast" to 45, "peaks" to 32, "ruins" to 26,
        "boss_boar" to 90, "boss_treant" to 63, "boss_wyrm" to 50, "boss_kraken" to 45, "boss_dragon" to 47, "boss_tyrant" to 18,
    )
    private var pacingGroup = ""

    private val BLADES = arrayOf(HeroClass.Warrior, HeroClass.Rogue)
    private val CASTERS = arrayOf(HeroClass.Mage, HeroClass.Druid, HeroClass.Priest)

    init {
        buildSkills(); buildClasses(); buildItems(); buildEnemies(); buildRegions()
    }

    fun classDef(c: HeroClass) = classes.getValue(c)
    fun skill(id: String) = skills.getValue(id)
    fun skillOrNull(id: String?) = id?.let { skills[it] }
    fun item(id: String) = items.getValue(id)
    fun itemOrNull(id: String?) = id?.let { items[it] }
    fun enemy(id: String) = enemies.getValue(id)
    fun region(id: String?) = regions.firstOrNull { it.id == id }

    fun startingParty() = mutableListOf(
        Hero.create("Brannoc", HeroClass.Warrior, "rusty_sword", "leather_vest"),
        Hero.create("Vess", HeroClass.Rogue, "worn_dagger", "leather_vest"),
        Hero.create("Ilsabet", HeroClass.Mage, "oak_wand", "cloth_robe"),
        Hero.create("Thornwick", HeroClass.Druid, "ash_staff", "cloth_robe"),
        Hero.create("Sister Maren", HeroClass.Priest, "wooden_mace", "cloth_robe"),
    )

    private fun S(id: String, name: String, desc: String, mp: Int, t: TargetKind, fx: SkillEffect, e: Element, power: Int, magical: Boolean, shieldHits: Int = 1, turns: Int = 0) {
        skills[id] = SkillDef(id, name, desc, mp, t, fx, e, power, magical, shieldHits, turns)
    }

    private fun N(id: String, name: String, desc: String, requires: String?, minLevel: Int, skill: String? = null, bonus: Stats = Stats(), cost: Int = 1) =
        SkillTreeNode(id, name, desc, requires, minLevel, skill, bonus, cost)

    private fun I(id: String, name: String, slot: EquipSlot, r: Rarity, price: Int, tier: Int, bonus: Stats, desc: String, vararg classes: HeroClass) {
        items[id] = ItemDef(id, name, desc, slot, r, price, tier, bonus, classes.toList())
    }

    private fun C(id: String, name: String, price: Int, tier: Int, use: ConsumableEffect, amount: Int, desc: String) {
        items[id] = ItemDef(id, name, desc, EquipSlot.None, Rarity.Common, price, tier, use = use, useAmount = amount)
    }

    private fun E(id: String, name: String, stats: Stats, shield: Int, exp: Int, coins: Int, vararg weak: Element): EnemyDef {
        val pct = hpPacing[id] ?: hpPacing[pacingGroup]
        val power = enemyPowerPercent * (if (id.startsWith("boss_")) bossPowerPercent else 100) / 100
        val s = stats.copy(
            maxHp = if (pct != null) max(1, stats.maxHp * pct / 100) else stats.maxHp,
            atk = stats.atk * power / 100,
            mag = stats.mag * power / 100,
        )
        val e = EnemyDef(id, name, s, shield, exp * EXP_SCALE, coins, weak.toList())
        enemies[id] = e
        return e
    }

    private fun R(id: String, name: String, km: Double, lvl: Int, palette: String, boss: String, desc: String, vararg enc: String) {
        regions += RegionDef(regions.size, id, name, desc, km, enc.toList(), boss, lvl, palette)
    }

    private fun buildSkills() {
            // Warrior
            S("provoke", "Provoke", "Draw enemy attacks and raise defense for 3 turns.", 4, TargetKind.Self, SkillEffect.Taunt, Element.Physical, 0, false, 0, 3)
            S("cleave", "Cleave", "Strike all enemies.", 8, TargetKind.AllEnemies, SkillEffect.Damage, Element.Physical, 80, false)
            S("war_cry", "War Cry", "Raise the party's attack for 3 turns.", 10, TargetKind.AllAllies, SkillEffect.BuffAtk, Element.Physical, 0, false, 0, 3)
            S("earthsplitter", "Earthsplitter", "A crushing blow that shatters shields.", 16, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Physical, 260, false, 3)
            // Rogue
            S("backstab", "Backstab", "A precise strike from the shadows.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Physical, 160, false)
            S("pilfer", "Pilfer", "Strike and steal coins.", 3, TargetKind.SingleEnemy, SkillEffect.StealCoins, Element.Physical, 70, false)
            S("venom_blade", "Venom Blade", "Poison an enemy for 4 turns.", 6, TargetKind.SingleEnemy, SkillEffect.Poison, Element.Dark, 60, false, 1, 4)
            S("flurry", "Flurry", "Rapid cuts that hit for 2 shield points.", 12, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Physical, 190, false, 2)
            // Mage
            S("fire", "Flame", "Fire damage to one enemy.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Fire, 150, true)
            S("ice", "Frost", "Ice damage to one enemy.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Ice, 150, true)
            S("bolt", "Spark", "Lightning damage to one enemy.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Lightning, 150, true)
            S("inferno", "Inferno", "Fire damage to all enemies.", 14, TargetKind.AllEnemies, SkillEffect.Damage, Element.Fire, 120, true)
            S("blizzard", "Blizzard", "Ice damage to all enemies.", 14, TargetKind.AllEnemies, SkillEffect.Damage, Element.Ice, 120, true)
            S("tempest", "Tempest", "Lightning damage to all enemies.", 14, TargetKind.AllEnemies, SkillEffect.Damage, Element.Lightning, 120, true)
            S("meteor", "Starfall", "Overwhelming fire, 3 shield hits.", 28, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Fire, 340, true, 3)
            // Druid
            S("thorns", "Thorns", "Nature damage that poisons for 3 turns.", 4, TargetKind.SingleEnemy, SkillEffect.Poison, Element.Nature, 100, true, 1, 3)
            S("bark_skin", "Bark Skin", "Raise an ally's defense for 3 turns.", 4, TargetKind.SingleAlly, SkillEffect.BuffDef, Element.Nature, 0, true, 0, 3)
            S("rejuvenate", "Rejuvenate", "Heal an ally each turn for 4 turns.", 7, TargetKind.SingleAlly, SkillEffect.Regen, Element.Nature, 50, true, 0, 4)
            S("entangle", "Entangle", "Nature damage to all enemies.", 12, TargetKind.AllEnemies, SkillEffect.Damage, Element.Nature, 110, true)
            S("wild_growth", "Wild Growth", "Regen on the whole party.", 16, TargetKind.AllAllies, SkillEffect.Regen, Element.Nature, 40, true, 0, 4)
            // Priest
            S("heal", "Mend", "Restore an ally's HP.", 4, TargetKind.SingleAlly, SkillEffect.Heal, Element.Holy, 180, true)
            S("raise", "Raise", "Revive a fallen ally with 40% HP.", 12, TargetKind.DeadAlly, SkillEffect.Revive, Element.Holy, 40, true)
            S("smite", "Smite", "Holy damage to one enemy.", 5, TargetKind.SingleEnemy, SkillEffect.Damage, Element.Holy, 140, true)
            S("sanctuary", "Sanctuary", "Heal the whole party.", 14, TargetKind.AllAllies, SkillEffect.Heal, Element.Holy, 110, true)
            S("aegis", "Aegis", "Raise the party's defense for 3 turns.", 12, TargetKind.AllAllies, SkillEffect.BuffDef, Element.Holy, 0, true, 0, 3)
            S("judgement", "Judgement", "Holy light on all enemies.", 20, TargetKind.AllEnemies, SkillEffect.Damage, Element.Holy, 150, true, 2)

    }

    private fun buildClasses() {
        classes[HeroClass.Warrior] = ClassDef(
            HeroClass.Warrior, "Warrior", "A stalwart shield for the party.",
            base = Stats(120, 20, 16, 14, 4, 6, 8, 5), growth = Stats(14, 2, 3, 3, 0, 1, 1, 0),
            startingSkills = listOf("provoke"),
            tree = listOf(
                N("w_tough", "Toughness", "+40 max HP", null, 2, bonus = Stats.of(StatType.MaxHp, 40)),
                N("w_cleave", "Cleave", "Learn Cleave", null, 3, "cleave"),
                N("w_guard", "Iron Guard", "+6 DEF", "w_tough", 6, bonus = Stats.of(StatType.Def, 6)),
                N("w_cry", "War Cry", "Learn War Cry", "w_cleave", 8, "war_cry"),
                N("w_might", "Might", "+8 ATK", "w_cry", 12, bonus = Stats.of(StatType.Atk, 8)),
                N("w_bulwark", "Bulwark", "+100 max HP", "w_guard", 16, bonus = Stats.of(StatType.MaxHp, 100), cost = 2),
                N("w_split", "Earthsplitter", "Learn Earthsplitter", "w_might", 20, "earthsplitter", cost = 2),
                N("w_legend", "Unbreakable", "+12 DEF, +12 RES", "w_bulwark", 30, bonus = Stats(0, 0, 0, 12, 0, 12, 0, 0), cost = 3),
            ),
        )
        classes[HeroClass.Rogue] = ClassDef(
            HeroClass.Rogue, "Rogue", "Quick, sly and deadly.",
            base = Stats(85, 25, 14, 8, 6, 7, 16, 15), growth = Stats(9, 2, 3, 1, 1, 1, 2, 0),
            startingSkills = listOf("backstab", "pilfer"),
            tree = listOf(
                N("r_quick", "Quickstep", "+4 SPD", null, 2, bonus = Stats.of(StatType.Spd, 4)),
                N("r_venom", "Venom Blade", "Learn Venom Blade", null, 3, "venom_blade"),
                N("r_keen", "Keen Eye", "+8% crit", "r_quick", 6, bonus = Stats.of(StatType.Crit, 8)),
                N("r_edge", "Honed Edge", "+6 ATK", "r_venom", 8, bonus = Stats.of(StatType.Atk, 6)),
                N("r_flurry", "Flurry", "Learn Flurry", "r_edge", 12, "flurry"),
                N("r_shadow", "Shadowstep", "+8 SPD", "r_keen", 16, bonus = Stats.of(StatType.Spd, 8), cost = 2),
                N("r_lethal", "Lethality", "+12% crit", "r_shadow", 22, bonus = Stats.of(StatType.Crit, 12), cost = 2),
                N("r_legend", "Night's Kiss", "+14 ATK, +6 SPD", "r_flurry", 30, bonus = Stats(0, 0, 14, 0, 0, 0, 6, 0), cost = 3),
            ),
        )
        classes[HeroClass.Mage] = ClassDef(
            HeroClass.Mage, "Mage", "Master of fire, frost and storm.",
            base = Stats(70, 60, 5, 6, 18, 12, 10, 3), growth = Stats(7, 6, 0, 1, 4, 2, 1, 0),
            startingSkills = listOf("fire", "ice", "bolt"),
            tree = listOf(
                N("m_focus", "Focus", "+20 max MP", null, 2, bonus = Stats.of(StatType.MaxMp, 20)),
                N("m_inferno", "Inferno", "Learn Inferno", null, 4, "inferno"),
                N("m_blizzard", "Blizzard", "Learn Blizzard", "m_inferno", 7, "blizzard"),
                N("m_tempest", "Tempest", "Learn Tempest", "m_blizzard", 10, "tempest"),
                N("m_arcane", "Arcane Mind", "+8 MAG", "m_focus", 8, bonus = Stats.of(StatType.Mag, 8)),
                N("m_ward", "Mana Ward", "+8 RES, +30 HP", "m_arcane", 14, bonus = Stats(30, 0, 0, 0, 0, 8, 0, 0), cost = 2),
                N("m_meteor", "Starfall", "Learn Starfall", "m_tempest", 22, "meteor", cost = 2),
                N("m_legend", "Archmage", "+16 MAG, +40 MP", "m_ward", 30, bonus = Stats(0, 40, 0, 0, 16, 0, 0, 0), cost = 3),
            ),
        )
        classes[HeroClass.Druid] = ClassDef(
            HeroClass.Druid, "Druid", "Speaks for the wild; heals and harries.",
            base = Stats(90, 45, 8, 9, 13, 12, 9, 4), growth = Stats(10, 4, 1, 2, 3, 2, 1, 0),
            startingSkills = listOf("thorns", "bark_skin"),
            tree = listOf(
                N("d_root", "Deep Roots", "+30 max HP", null, 2, bonus = Stats.of(StatType.MaxHp, 30)),
                N("d_rejuv", "Rejuvenate", "Learn Rejuvenate", null, 3, "rejuvenate"),
                N("d_grove", "Grove Wisdom", "+6 MAG", "d_rejuv", 6, bonus = Stats.of(StatType.Mag, 6)),
                N("d_entangle", "Entangle", "Learn Entangle", "d_grove", 9, "entangle"),
                N("d_hide", "Bear Hide", "+6 DEF, +6 RES", "d_root", 10, bonus = Stats(0, 0, 0, 6, 0, 6, 0, 0)),
                N("d_growth", "Wild Growth", "Learn Wild Growth", "d_entangle", 16, "wild_growth", cost = 2),
                N("d_spring", "Wellspring", "+30 max MP", "d_hide", 20, bonus = Stats.of(StatType.MaxMp, 30), cost = 2),
                N("d_legend", "Avatar of the Wild", "+10 MAG, +60 HP", "d_growth", 30, bonus = Stats(60, 0, 0, 0, 10, 0, 0, 0), cost = 3),
            ),
        )
        classes[HeroClass.Priest] = ClassDef(
            HeroClass.Priest, "Priest", "Keeps everyone standing.",
            base = Stats(80, 55, 7, 8, 15, 15, 9, 3), growth = Stats(8, 5, 1, 1, 3, 3, 1, 0),
            startingSkills = listOf("heal", "smite"),
            tree = listOf(
                N("p_devotion", "Devotion", "+20 max MP", null, 2, bonus = Stats.of(StatType.MaxMp, 20)),
                N("p_raise", "Raise", "Learn Raise", null, 4, "raise"),
                N("p_grace", "Grace", "+6 MAG", "p_devotion", 6, bonus = Stats.of(StatType.Mag, 6)),
                N("p_sanct", "Sanctuary", "Learn Sanctuary", "p_raise", 9, "sanctuary"),
                N("p_aegis", "Aegis", "Learn Aegis", "p_sanct", 13, "aegis"),
                N("p_faith", "Steadfast Faith", "+10 RES, +40 HP", "p_grace", 16, bonus = Stats(40, 0, 0, 0, 0, 10, 0, 0), cost = 2),
                N("p_judge", "Judgement", "Learn Judgement", "p_aegis", 22, "judgement", cost = 2),
                N("p_legend", "Saint", "+14 MAG, +40 MP", "p_faith", 30, bonus = Stats(0, 40, 0, 0, 14, 0, 0, 0), cost = 3),
            ),
        )
    }

    private fun buildItems() {

            C("tonic", "Healing Tonic", 20, 0, ConsumableEffect.HealHp, 80, "Restores 80 HP.")
            C("hi_tonic", "Greater Tonic", 70, 2, ConsumableEffect.HealHp, 300, "Restores 300 HP.")
            C("ether", "Ether Drop", 45, 0, ConsumableEffect.HealMp, 30, "Restores 30 MP.")
            C("hi_ether", "Ether Flask", 140, 3, ConsumableEffect.HealMp, 100, "Restores 100 MP.")
            C("ember_feather", "Ember Feather", 120, 1, ConsumableEffect.Revive, 50, "Revives a fallen ally with 50% HP.")
            C("antidote", "Herbal Antidote", 15, 0, ConsumableEffect.Cleanse, 0, "Cures poison.")


            I("rusty_sword", "Rusty Sword", EquipSlot.Weapon, Rarity.Common, 0, -1, Stats.of(StatType.Atk, 3), "Seen better days.", HeroClass.Warrior)
            I("worn_dagger", "Worn Dagger", EquipSlot.Weapon, Rarity.Common, 0, -1, Stats(0, 0, 3, 0, 0, 0, 1, 2), "Still sharp enough.", HeroClass.Rogue)
            I("oak_wand", "Oak Wand", EquipSlot.Weapon, Rarity.Common, 0, -1, Stats.of(StatType.Mag, 3), "Hums faintly.", HeroClass.Mage)
            I("ash_staff", "Ash Staff", EquipSlot.Weapon, Rarity.Common, 0, -1, Stats(0, 0, 2, 0, 2, 0, 0, 0), "Carved from a storm-felled ash.", HeroClass.Druid)
            I("wooden_mace", "Wooden Mace", EquipSlot.Weapon, Rarity.Common, 0, -1, Stats(0, 0, 2, 0, 2, 0, 0, 0), "Blessed, mostly.", HeroClass.Priest)

            I("iron_sword", "Iron Longsword", EquipSlot.Weapon, Rarity.Common, 120, 0, Stats.of(StatType.Atk, 8), "Reliable steel.", HeroClass.Warrior)
            I("steel_dirk", "Steel Dirk", EquipSlot.Weapon, Rarity.Common, 110, 0, Stats(0, 0, 6, 0, 0, 0, 2, 4), "Light and wicked.", HeroClass.Rogue)
            I("apprentice_rod", "Apprentice Rod", EquipSlot.Weapon, Rarity.Common, 110, 0, Stats.of(StatType.Mag, 8), "Standard academy issue.", HeroClass.Mage)
            I("hazel_staff", "Hazel Staff", EquipSlot.Weapon, Rarity.Common, 110, 0, Stats(0, 5, 3, 0, 6, 0, 0, 0), "Green buds still on it.", HeroClass.Druid)
            I("iron_mace", "Iron Mace", EquipSlot.Weapon, Rarity.Common, 110, 0, Stats(0, 5, 4, 0, 5, 0, 0, 0), "For smiting, gently.", HeroClass.Priest)

            I("knight_blade", "Knight's Blade", EquipSlot.Weapon, Rarity.Uncommon, 480, 2, Stats.of(StatType.Atk, 18), "Forged for the royal guard.", HeroClass.Warrior)
            I("shadow_kris", "Shadow Kris", EquipSlot.Weapon, Rarity.Uncommon, 460, 2, Stats(0, 0, 14, 0, 0, 0, 4, 8), "Wavy blade, drinks light.", HeroClass.Rogue)
            I("ember_staff", "Ember Staff", EquipSlot.Weapon, Rarity.Uncommon, 460, 2, Stats(0, 10, 0, 0, 18, 0, 0, 0), "Warm to the touch.", HeroClass.Mage)
            I("moonwood_staff", "Moonwood Staff", EquipSlot.Weapon, Rarity.Uncommon, 460, 2, Stats(0, 10, 4, 0, 14, 4, 0, 0), "Glows at night.", HeroClass.Druid)
            I("silver_scepter", "Silver Scepter", EquipSlot.Weapon, Rarity.Uncommon, 460, 2, Stats(0, 10, 4, 0, 14, 4, 0, 0), "Rings like a bell.", HeroClass.Priest)

            I("dragon_cleaver", "Dragon Cleaver", EquipSlot.Weapon, Rarity.Rare, 1500, 4, Stats(0, 0, 32, 4, 0, 0, 0, 3), "Once split a wyvern's scale.", HeroClass.Warrior)
            I("whisper", "Whisper", EquipSlot.Weapon, Rarity.Rare, 1450, 4, Stats(0, 0, 26, 0, 0, 0, 8, 14), "You never hear it coming.", HeroClass.Rogue)
            I("astral_rod", "Astral Rod", EquipSlot.Weapon, Rarity.Rare, 1450, 4, Stats(0, 25, 0, 0, 32, 6, 0, 0), "Maps the stars.", HeroClass.Mage)
            I("worldroot", "Worldroot Staff", EquipSlot.Weapon, Rarity.Rare, 1450, 4, Stats(40, 20, 6, 0, 26, 6, 0, 0), "Cut from the eldest tree.", HeroClass.Druid)
            I("dawn_scepter", "Dawn Scepter", EquipSlot.Weapon, Rarity.Rare, 1450, 4, Stats(0, 25, 6, 0, 26, 8, 0, 0), "Brings the sunrise.", HeroClass.Priest)


            I("sunforged_blade", "Sunforged Blade", EquipSlot.Weapon, Rarity.Legendary, 5000, -1, Stats(50, 0, 48, 10, 0, 10, 4, 6), "Dropped by the Sky Tyrant.", *BLADES)
            I("starweave_staff", "Starweave Staff", EquipSlot.Weapon, Rarity.Legendary, 5000, -1, Stats(30, 50, 0, 6, 48, 16, 4, 0), "Dropped by the Sky Tyrant.", *CASTERS)


            I("leather_vest", "Leather Vest", EquipSlot.Armor, Rarity.Common, 0, -1, Stats.of(StatType.Def, 2), "Patched at the elbows.")
            I("cloth_robe", "Cloth Robe", EquipSlot.Armor, Rarity.Common, 0, -1, Stats(0, 0, 0, 1, 0, 2, 0, 0), "Comfortable.")
            I("chain_mail", "Chain Mail", EquipSlot.Armor, Rarity.Common, 150, 0, Stats(10, 0, 0, 7, 0, 2, -1, 0), "Clinks as you walk.", HeroClass.Warrior)
            I("studded_leather", "Studded Leather", EquipSlot.Armor, Rarity.Common, 130, 0, Stats(5, 0, 0, 5, 0, 2, 0, 0), "Light protection.", HeroClass.Rogue, HeroClass.Druid)
            I("silk_robe", "Silk Robe", EquipSlot.Armor, Rarity.Common, 130, 0, Stats(0, 10, 0, 3, 0, 6, 0, 0), "Embroidered runes.", HeroClass.Mage, HeroClass.Priest, HeroClass.Druid)
            I("plate_armor", "Plate Armor", EquipSlot.Armor, Rarity.Uncommon, 600, 2, Stats(40, 0, 0, 16, 0, 6, -2, 0), "Heavy but safe.", HeroClass.Warrior)
            I("shadow_garb", "Shadow Garb", EquipSlot.Armor, Rarity.Uncommon, 560, 2, Stats(20, 0, 0, 10, 0, 6, 3, 3), "Blends with dusk.", HeroClass.Rogue)
            I("sage_vestment", "Sage Vestment", EquipSlot.Armor, Rarity.Uncommon, 560, 2, Stats(20, 20, 0, 7, 0, 14, 0, 0), "Woven with silver thread.", *CASTERS)
            I("dragonscale", "Dragonscale Mail", EquipSlot.Armor, Rarity.Rare, 1800, 4, Stats(80, 0, 0, 28, 0, 16, 0, 0), "Shrugs off flame.", *BLADES)
            I("celestial_robe", "Celestial Robe", EquipSlot.Armor, Rarity.Rare, 1800, 4, Stats(50, 40, 0, 14, 0, 28, 0, 0), "Shimmers like the night sky.", *CASTERS)


            I("lucky_coin", "Lucky Coin", EquipSlot.Accessory, Rarity.Common, 90, 0, Stats.of(StatType.Crit, 5), "Always lands heads.")
            I("copper_ring", "Copper Ring", EquipSlot.Accessory, Rarity.Common, 80, 0, Stats.of(StatType.MaxHp, 25), "Turns your finger green.")
            I("walkers_charm", "Walker's Charm", EquipSlot.Accessory, Rarity.Uncommon, 300, 1, Stats(20, 10, 2, 2, 2, 2, 3, 0), "Warm after a long walk.")
            I("sapphire_pendant", "Sapphire Pendant", EquipSlot.Accessory, Rarity.Uncommon, 380, 2, Stats(0, 30, 0, 0, 6, 4, 0, 0), "Cool and calm.")
            I("ruby_band", "Ruby Band", EquipSlot.Accessory, Rarity.Uncommon, 380, 2, Stats(0, 0, 8, 0, 0, 0, 0, 4), "Burns with resolve.")
            I("pilgrims_sigil", "Pilgrim's Sigil", EquipSlot.Accessory, Rarity.Rare, 1200, 4, Stats(60, 30, 6, 6, 6, 6, 4, 4), "Earned on the long road.")
            I("moonstone", "Moonstone", EquipSlot.Accessory, Rarity.Legendary, 4000, -1, Stats(100, 60, 10, 10, 10, 10, 6, 6), "A shard of the Moon. Fitting.")

    }

    private fun buildEnemies() {
            // Region 0 — Greenmoor Meadow
            pacingGroup = "meadow"
            E("slime", "Dew Slime", Stats(40, 0, 10, 4, 4, 2, 6, 0), 2, 8, 6, Element.Fire, Element.Lightning).loot.add(LootEntry("tonic", 0.2))
            E("rat", "Burrow Rat", Stats(32, 0, 12, 3, 0, 2, 11, 5), 1, 7, 5, Element.Physical, Element.Fire)
            E("wasp", "Meadow Wasp", Stats(28, 0, 13, 2, 0, 3, 14, 5), 2, 9, 6, Element.Ice, Element.Nature)
            val b0 = E("boss_boar", "Ironhide Boar", Stats(420, 0, 22, 10, 0, 6, 9, 5), 5, 120, 150, Element.Fire, Element.Holy)
            b0.isBoss = true; b0.aoeEvery = 4; b0.loot.add(LootEntry("walkers_charm", 1.0));

            // Region 1 — Whisperwood
            pacingGroup = "forest"
            E("wolf", "Gloam Wolf", Stats(80, 0, 20, 8, 0, 6, 15, 8), 2, 18, 12, Element.Fire, Element.Physical).loot.add(LootEntry("tonic", 0.25))
            E("sprite", "Thorn Sprite", Stats(60, 20, 8, 6, 18, 12, 13, 0), 2, 20, 14, Element.Fire, Element.Ice).attackElement = Element.Nature
            E("mushroom", "Sporecap", Stats(110, 0, 16, 12, 6, 4, 5, 0), 3, 22, 15, Element.Fire, Element.Lightning).loot.add(LootEntry("antidote", 0.4))
            val b1 = E("boss_treant", "Elderbark Treant", Stats(1100, 0, 34, 20, 20, 14, 7, 0), 7, 360, 400, Element.Fire, Element.Lightning)
            b1.isBoss = true; b1.aoeEvery = 3; b1.attackElement = Element.Nature; b1.loot.add(LootEntry("ember_feather", 1.0));

            // Region 2 — Sunscorch Dunes
            pacingGroup = "desert"
            E("scorpion", "Glass Scorpion", Stats(150, 0, 32, 20, 0, 8, 12, 10), 3, 40, 26, Element.Ice, Element.Lightning).loot.add(LootEntry("antidote", 0.3))
            E("mummy", "Sand-wrapped Dead", Stats(190, 0, 30, 14, 0, 10, 7, 0), 3, 44, 28, Element.Fire, Element.Holy)
            E("djinn", "Dust Djinn", Stats(130, 40, 14, 10, 34, 24, 16, 0), 3, 46, 30, Element.Ice, Element.Physical).attackElement = Element.Lightning
            val b2 = E("boss_wyrm", "Dune Wyrm", Stats(2400, 0, 48, 30, 30, 20, 11, 5), 8, 900, 900, Element.Ice, Element.Lightning)
            b2.isBoss = true; b2.aoeEvery = 3; b2.attackElement = Element.Fire; b2.loot.add(LootEntry("ruby_band", 1.0));

            // Region 3 — Saltspray Coast
            pacingGroup = "coast"
            E("crab", "Reef Crab", Stats(260, 0, 44, 36, 0, 12, 8, 5), 4, 70, 40, Element.Lightning, Element.Physical).loot.add(LootEntry("hi_tonic", 0.15))
            E("siren", "Drowned Siren", Stats(200, 60, 20, 16, 46, 32, 17, 0), 3, 74, 44, Element.Lightning, Element.Dark).attackElement = Element.Ice
            E("pirate", "Ghost Corsair", Stats(240, 0, 50, 22, 0, 18, 15, 12), 3, 78, 50, Element.Holy, Element.Fire).loot.add(LootEntry("lucky_coin", 0.1))
            val b3 = E("boss_kraken", "Abyssal Kraken", Stats(4200, 0, 62, 36, 46, 30, 12, 0), 9, 1800, 1600, Element.Lightning, Element.Fire)
            b3.isBoss = true; b3.aoeEvery = 3; b3.attackElement = Element.Ice; b3.loot.add(LootEntry("sapphire_pendant", 1.0));

            // Region 4 — Frostfang Peaks
            pacingGroup = "peaks"
            E("yeti", "Frost Yeti", Stats(420, 0, 66, 34, 0, 20, 12, 5), 4, 120, 70, Element.Fire, Element.Physical).loot.add(LootEntry("hi_ether", 0.15))
            E("harpy", "Storm Harpy", Stats(300, 40, 54, 22, 40, 26, 22, 10), 3, 118, 66, Element.Ice, Element.Physical).attackElement = Element.Lightning
            E("golem", "Rune Golem", Stats(560, 0, 60, 60, 0, 20, 6, 0), 5, 130, 80, Element.Lightning, Element.Nature)
            val b4 = E("boss_dragon", "Rimeclaw Dragon", Stats(7200, 0, 82, 48, 64, 40, 14, 5), 10, 3200, 2800, Element.Fire, Element.Holy)
            b4.isBoss = true; b4.aoeEvery = 3; b4.attackElement = Element.Ice; b4.loot.add(LootEntry("pilgrims_sigil", 1.0));

            // Region 5 — Skyreach Ruins
            pacingGroup = "ruins"
            E("gargoyle", "Sky Gargoyle", Stats(620, 0, 82, 60, 0, 30, 14, 5), 4, 200, 110, Element.Lightning, Element.Holy).loot.add(LootEntry("hi_tonic", 0.2))
            E("wraith", "Star Wraith", Stats(480, 80, 40, 30, 80, 50, 20, 0), 4, 210, 120, Element.Holy, Element.Fire).attackElement = Element.Dark
            E("sentinel", "Clockwork Sentinel", Stats(700, 0, 86, 70, 0, 40, 10, 0), 5, 220, 130, Element.Lightning, Element.Ice)
            val b5 = E("boss_tyrant", "The Sky Tyrant", Stats(12000, 0, 78, 60, 68, 60, 18, 8), 12, 6000, 6000, Element.Holy, Element.Nature)
            b5.isBoss = true; b5.aoeEvery = 3; b5.attackElement = Element.Dark;
            b5.loot.add(LootEntry("sunforged_blade", 1.0)); b5.loot.add(LootEntry("starweave_staff", 1.0)); b5.loot.add(LootEntry("moonstone", 1.0));

    }

    private fun buildRegions() {
            R("meadow", "Greenmoor Meadow", 0.0, 1, "meadow", "boss_boar", "Rolling hills where every journey begins.", "slime", "rat", "wasp")
            R("forest", "Whisperwood", 15.0, 5, "forest", "boss_treant", "Ancient trees that remember every footstep.", "wolf", "sprite", "mushroom")
            R("desert", "Sunscorch Dunes", 60.0, 10, "desert", "boss_wyrm", "Shifting sand over buried kingdoms.", "scorpion", "mummy", "djinn")
            R("coast", "Saltspray Coast", 150.0, 16, "coast", "boss_kraken", "Wrecks, reefs and restless tides.", "crab", "siren", "pirate")
            R("peaks", "Frostfang Peaks", 300.0, 22, "peaks", "boss_dragon", "Thin air and thinner patience.", "yeti", "harpy", "golem")
            R("ruins", "Skyreach Ruins", 500.0, 28, "ruins", "boss_tyrant", "A broken city drifting above the clouds.", "gargoyle", "wraith", "sentinel")

    }
}
