package com.stepquest.core.economy

import com.stepquest.core.data.GameData
import com.stepquest.core.party.Hero
import com.stepquest.core.party.HeroClass
import com.stepquest.core.party.Stats
import kotlinx.serialization.Serializable

enum class EquipSlot { None, Weapon, Armor, Accessory }
enum class Rarity { Common, Uncommon, Rare, Epic, Legendary }
enum class ConsumableEffect { None, HealHp, HealMp, Revive, Cleanse }

class ItemDef(
    val id: String, val name: String, val description: String, val slot: EquipSlot,
    val rarity: Rarity, val price: Int,
    /** Region index at which the shop sells it (-1 = never). */
    val shopTier: Int,
    val bonus: Stats = Stats(),
    val classes: List<HeroClass> = emptyList(),
    val use: ConsumableEffect = ConsumableEffect.None,
    val useAmount: Int = 0,
) {
    val isConsumable get() = slot == EquipSlot.None && use != ConsumableEffect.None
    fun canEquip(c: HeroClass) = slot != EquipSlot.None && (classes.isEmpty() || c in classes)
}

@Serializable
class Inventory(
    var coins: Int = 0,
    var items: MutableMap<String, Int> = mutableMapOf(),
) {
    fun count(id: String) = items[id] ?: 0
    fun add(id: String, n: Int = 1) { if (n > 0) items[id] = count(id) + n }
    fun remove(id: String, n: Int = 1): Boolean {
        val have = count(id)
        if (have < n) return false
        if (have == n) items.remove(id) else items[id] = have - n
        return true
    }
    fun spend(c: Int): Boolean {
        if (c < 0 || coins < c) return false
        coins -= c
        return true
    }
}

object Shop {
    const val INN_COST_PER_LEVEL = 2

    fun stock(highestRegion: Int) = GameData.items.values.filter { it.shopTier in 0..highestRegion }

    fun buy(inv: Inventory, item: ItemDef, qty: Int = 1): Boolean {
        if (qty <= 0 || item.shopTier < 0 || !inv.spend(item.price * qty)) return false
        inv.add(item.id, qty)
        return true
    }

    fun sellPrice(item: ItemDef) = item.price / 2

    fun sell(inv: Inventory, item: ItemDef): Boolean {
        if (!inv.remove(item.id)) return false
        inv.coins += sellPrice(item)
        return true
    }

    fun innCost(party: List<Hero>) = party.sumOf { it.level * INN_COST_PER_LEVEL }

    fun rest(inv: Inventory, party: List<Hero>): Boolean {
        if (!inv.spend(innCost(party))) return false
        party.forEach { it.fullRestore() }
        return true
    }
}

class LootEntry(val itemId: String, val chance: Double)
