package com.stepquest.core.save

import com.stepquest.core.combat.Battle
import com.stepquest.core.data.GameData
import com.stepquest.core.economy.Inventory
import com.stepquest.core.meta.QuestState
import com.stepquest.core.meta.Streaks
import com.stepquest.core.party.Hero
import com.stepquest.core.steps.DistanceCalc
import com.stepquest.core.steps.StepLedger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class Settings(
    var heightCm: Int = 0,
    var strideMeters: Double = DistanceCalc.DEFAULT_STRIDE_METERS,
    var dailyStepGoal: Int = Streaks.DEFAULT_GOAL,
    var notificationsEnabled: Boolean = true,
    var debugTools: Boolean = false,
)

@Serializable
class Counters(
    var battlesWon: Int = 0,
    var battlesLost: Int = 0,
    var enemiesDefeated: Int = 0,
    var bossesDefeated: Int = 0,
    var breaksLanded: Int = 0,
    var skillsUsed: Int = 0,
    var coinsEarned: Long = 0,
    var longestStreak: Int = 0,
)

@Serializable
class SaveData(
    var version: Int = CURRENT_VERSION,
    var createdMillis: Long = 0,
    var ledger: StepLedger = StepLedger(),
    var party: MutableList<Hero> = mutableListOf(),
    var inventory: Inventory = Inventory(),
    var activeBattle: Battle? = null,
    var currentRegionId: String = "meadow",
    var bossesDefeated: MutableList<String> = mutableListOf(),
    var counters: Counters = Counters(),
    var achievements: MutableMap<String, Long> = mutableMapOf(),
    var quests: QuestState = QuestState(),
    var settings: Settings = Settings(),
    var rngState: Long = 0,
) {
    companion object {
        const val CURRENT_VERSION = 1

        fun newGame(nowMillis: Long) = SaveData(
            createdMillis = nowMillis,
            party = GameData.startingParty(),
            rngState = nowMillis or 1L,
        ).also {
            it.ledger.lastSyncMillis = nowMillis
            it.inventory.coins = 100
            it.inventory.add("tonic", 3)
            it.inventory.add("ether", 1)
        }
    }
}

object SaveCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun encode(d: SaveData): String = json.encodeToString(SaveData.serializer(), d)

    /** Decodes, migrates and repairs a save; throws if it's from a newer app version. */
    fun decode(text: String, nowMillis: Long): SaveData {
        val version = runCatching { json.parseToJsonElement(text).jsonObject["version"]?.jsonPrimitive?.int ?: 0 }.getOrDefault(0)
        if (version > SaveData.CURRENT_VERSION) throw IllegalStateException("Save is from a newer version ($version).")
        val d = json.decodeFromString(SaveData.serializer(), text)
        d.version = SaveData.CURRENT_VERSION
        if (d.party.isEmpty()) d.party = GameData.startingParty()
        if (GameData.region(d.currentRegionId) == null) d.currentRegionId = "meadow"
        if (d.ledger.lastSyncMillis == 0L) d.ledger.lastSyncMillis = nowMillis
        return d
    }
}
