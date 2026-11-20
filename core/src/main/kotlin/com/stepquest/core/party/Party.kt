package com.stepquest.core.party

import com.stepquest.core.data.GameData
import com.stepquest.core.economy.EquipSlot
import com.stepquest.core.economy.ItemDef
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class StatType { MaxHp, MaxMp, Atk, Def, Mag, Res, Spd, Crit }
enum class HeroClass { Warrior, Rogue, Mage, Druid, Priest }
enum class Element { Physical, Fire, Ice, Lightning, Nature, Holy, Dark }
enum class TargetKind { SingleEnemy, AllEnemies, SingleAlly, AllAllies, Self, DeadAlly }
enum class SkillEffect { Damage, Heal, Revive, BuffDef, BuffAtk, Taunt, Poison, StealCoins, Regen }

@Serializable
data class Stats(
    val maxHp: Int = 0, val maxMp: Int = 0, val atk: Int = 0, val def: Int = 0,
    val mag: Int = 0, val res: Int = 0, val spd: Int = 0, val crit: Int = 0,
) {
    operator fun plus(o: Stats) = Stats(maxHp + o.maxHp, maxMp + o.maxMp, atk + o.atk, def + o.def, mag + o.mag, res + o.res, spd + o.spd, crit + o.crit)
    operator fun times(n: Int) = Stats(maxHp * n, maxMp * n, atk * n, def * n, mag * n, res * n, spd * n, crit * n)
    operator fun get(t: StatType) = when (t) {
        StatType.MaxHp -> maxHp; StatType.MaxMp -> maxMp; StatType.Atk -> atk; StatType.Def -> def
        StatType.Mag -> mag; StatType.Res -> res; StatType.Spd -> spd; StatType.Crit -> crit
    }

    companion object {
        fun of(t: StatType, v: Int) = when (t) {
            StatType.MaxHp -> Stats(maxHp = v); StatType.MaxMp -> Stats(maxMp = v); StatType.Atk -> Stats(atk = v)
            StatType.Def -> Stats(def = v); StatType.Mag -> Stats(mag = v); StatType.Res -> Stats(res = v)
            StatType.Spd -> Stats(spd = v); StatType.Crit -> Stats(crit = v)
        }
    }
}

class SkillDef(
    val id: String, val name: String, val description: String, val mpCost: Int,
    val target: TargetKind, val effect: SkillEffect, val element: Element,
    /** Multiplier on the scaling stat ×100 (150 = 1.5×). */
    val power: Int, val magical: Boolean, val shieldHits: Int = 1, val turns: Int = 0,
)

class SkillTreeNode(
    val id: String, val name: String, val description: String, val requires: String?,
    val minLevel: Int, val unlocksSkill: String? = null, val bonus: Stats = Stats(), val cost: Int = 1,
)

class ClassDef(
    val heroClass: HeroClass, val name: String, val blurb: String,
    val base: Stats, val growth: Stats,
    val startingSkills: List<String>, val tree: List<SkillTreeNode>,
    val weaponElement: Element = Element.Physical,
)

object Leveling {
    const val MAX_LEVEL = 50

    /** EXP from [level] to level+1; tuned so levels keep pace with distance-gated regions (see BalanceTest). */
    fun expToNext(level: Int): Int = if (level >= MAX_LEVEL) Int.MAX_VALUE else (6 * level.toDouble().pow(2.2) + 12).roundToInt()

    /** One skill point per level, plus a bonus every 5 levels. */
    fun skillPointsForLevel(newLevel: Int) = if (newLevel % 5 == 0) 2 else 1
}

@Serializable
class Hero(
    var name: String,
    var heroClass: HeroClass,
    var level: Int = 1,
    var exp: Int = 0,
    var hp: Int = 0,
    var mp: Int = 0,
    var skillPoints: Int = 0,
    var unlockedNodes: MutableList<String> = mutableListOf(),
    var weaponId: String? = null,
    var armorId: String? = null,
    var accessoryId: String? = null,
) {
    val def: ClassDef get() = GameData.classDef(heroClass)
    val isAlive get() = hp > 0

    fun baseStats() = def.base + def.growth * (level - 1)

    fun totalStats(): Stats {
        var s = baseStats()
        for (id in unlockedNodes) def.tree.find { it.id == id }?.let { s += it.bonus }
        for (id in listOf(weaponId, armorId, accessoryId)) GameData.itemOrNull(id)?.let { s += it.bonus }
        return s
    }

    fun skills(): List<SkillDef> =
        def.startingSkills.map { GameData.skill(it) } +
            unlockedNodes.mapNotNull { id -> def.tree.find { it.id == id }?.unlocksSkill?.let { GameData.skill(it) } }

    /** Adds EXP; returns levels gained. Level-ups restore the HP/MP gained. */
    fun gainExp(amount: Int): Int {
        if (amount <= 0 || level >= Leveling.MAX_LEVEL) return 0
        exp += amount
        var gained = 0
        while (level < Leveling.MAX_LEVEL && exp >= Leveling.expToNext(level)) {
            val before = totalStats()
            exp -= Leveling.expToNext(level)
            level++
            gained++
            skillPoints += Leveling.skillPointsForLevel(level)
            val after = totalStats()
            hp += after.maxHp - before.maxHp
            mp += after.maxMp - before.maxMp
        }
        if (level >= Leveling.MAX_LEVEL) exp = 0
        return gained
    }

    /** Null if allowed, otherwise the reason. */
    fun unlockBlocker(nodeId: String): String? {
        val node = def.tree.find { it.id == nodeId } ?: return "Unknown node"
        return when {
            nodeId in unlockedNodes -> "Already learned"
            node.requires != null && node.requires !in unlockedNodes -> "Requires an earlier node"
            level < node.minLevel -> "Requires level ${node.minLevel}"
            skillPoints < node.cost -> "Not enough skill points"
            else -> null
        }
    }

    fun unlock(nodeId: String): Boolean {
        if (unlockBlocker(nodeId) != null) return false
        val node = def.tree.first { it.id == nodeId }
        val before = totalStats()
        skillPoints -= node.cost
        unlockedNodes += nodeId
        val after = totalStats()
        hp += max(0, after.maxHp - before.maxHp)
        mp += max(0, after.maxMp - before.maxMp)
        return true
    }

    fun equip(item: ItemDef) {
        when (item.slot) {
            EquipSlot.Weapon -> weaponId = item.id
            EquipSlot.Armor -> armorId = item.id
            EquipSlot.Accessory -> accessoryId = item.id
            EquipSlot.None -> return
        }
        clampVitals()
    }

    fun clampVitals() {
        val s = totalStats()
        hp = min(hp, s.maxHp); mp = min(mp, s.maxMp)
    }

    fun fullRestore() {
        val s = totalStats()
        hp = s.maxHp; mp = s.maxMp
    }

    companion object {
        fun create(name: String, cls: HeroClass, weapon: String?, armor: String?) =
            Hero(name, cls, weaponId = weapon, armorId = armor).also { it.fullRestore() }
    }
}
