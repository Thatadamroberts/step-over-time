package com.stepquest.core.world

import com.stepquest.core.Rng
import com.stepquest.core.data.GameData
import com.stepquest.core.economy.LootEntry
import com.stepquest.core.party.Element
import com.stepquest.core.party.Stats

class EnemyDef(
    val id: String, val name: String, val stats: Stats, val shield: Int,
    val exp: Int, val coins: Int, val weaknesses: List<Element>,
    var isBoss: Boolean = false,
    /** All-party attack every N turns (0 = never). */
    var aoeEvery: Int = 0,
    var attackElement: Element = Element.Physical,
    val loot: MutableList<LootEntry> = mutableListOf(),
)

class RegionDef(
    val index: Int, val id: String, val name: String, val description: String,
    /** Total distance walked (km) required to enter. */
    val unlockKm: Double,
    val encounters: List<String>, val bossId: String,
    val recommendedLevel: Int, val palette: String,
)

object WorldMap {
    fun isUnlocked(r: RegionDef, totalKm: Double, bosses: Collection<String>): Boolean {
        if (r.index == 0) return true
        val prev = GameData.regions[r.index - 1]
        return totalKm >= r.unlockKm && prev.bossId in bosses
    }

    fun highestUnlocked(totalKm: Double, bosses: Collection<String>) =
        GameData.regions.filter { isUnlocked(it, totalKm, bosses) }.maxOf { it.index }

    fun lockReason(r: RegionDef, totalKm: Double, bosses: Collection<String>): String? {
        if (isUnlocked(r, totalKm, bosses)) return null
        val prev = GameData.regions[r.index - 1]
        val parts = mutableListOf<String>()
        if (prev.bossId !in bosses) parts += "Defeat ${GameData.enemy(prev.bossId).name}"
        if (totalKm < r.unlockKm) parts += "Walk %.1f km more".format(r.unlockKm - totalKm)
        return parts.joinToString(" · ")
    }

    fun rollEncounter(r: RegionDef, rng: Rng): List<String> =
        List(rng.range(1, 4)) { r.encounters[rng.range(0, r.encounters.size)] }
}
