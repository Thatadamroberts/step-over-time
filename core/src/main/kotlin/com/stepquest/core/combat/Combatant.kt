package com.stepquest.core.combat

import com.stepquest.core.Rng
import com.stepquest.core.party.Element
import com.stepquest.core.party.Stats
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

enum class StatusKind { Defending, AtkUp, DefUp, Taunt, Poison, Regen }

@Serializable
class Status(val kind: StatusKind, var turns: Int, var potency: Int = 0)

@Serializable
class Combatant(
    val id: Int,
    val name: String,
    val isHero: Boolean,
    val heroIndex: Int = -1,
    val enemyId: String? = null,
    val stats: Stats,
    var hp: Int,
    var mp: Int,
    var shield: Int = 0,
    val maxShield: Int = 0,
    /** Broken: skips its next turn and takes double damage. */
    var broken: Boolean = false,
    var turnsTaken: Int = 0,
    val statuses: MutableList<Status> = mutableListOf(),
) {
    val isAlive get() = hp > 0
    fun has(k: StatusKind) = statuses.any { it.kind == k }
    fun get(k: StatusKind) = statuses.firstOrNull { it.kind == k }
    fun apply(k: StatusKind, turns: Int, potency: Int = 0) {
        val e = get(k)
        if (e != null) { e.turns = max(e.turns, turns); e.potency = max(e.potency, potency) }
        else statuses += Status(k, turns, potency)
    }
}

enum class BattleEventKind { Damage, Heal, Miss, Break, Recover, Revive, Status, Defeated, Info, Coins }

@Serializable
class BattleEvent(
    val kind: BattleEventKind,
    val sourceId: Int = -1,
    val targetId: Int = -1,
    val amount: Int = 0,
    val crit: Boolean = false,
    val weak: Boolean = false,
    val text: String = "",
)

object Damage {
    const val WEAKNESS = 1.3
    const val BROKEN = 2.0
    const val CRIT = 1.5
    const val DEFEND = 0.5
    const val BUFF_PERCENT = 30

    class Result(val amount: Int, val crit: Boolean, val weak: Boolean)

    fun compute(a: Combatant, t: Combatant, power: Int, magical: Boolean, element: Element, rng: Rng, weak: Set<Element>? = null): Result {
        var atk = if (magical) a.stats.mag else a.stats.atk
        var def = if (magical) t.stats.res else t.stats.def
        if (!magical && a.has(StatusKind.AtkUp)) atk = atk * (100 + BUFF_PERCENT) / 100
        if (t.has(StatusKind.DefUp)) def = def * (100 + BUFF_PERCENT) / 100

        var raw = atk * power / 100.0 - def * 0.5
        raw = max(raw, atk * power / 100.0 * 0.1)
        raw = max(1.0, raw)
        raw *= 0.9 + rng.nextDouble() * 0.2

        var crit = false
        var isWeak = false
        if (!magical && rng.nextDouble() * 100 < a.stats.crit) { raw *= CRIT; crit = true }
        if (weak != null && element in weak) { raw *= WEAKNESS; isWeak = true }
        if (t.broken) raw *= BROKEN
        if (t.has(StatusKind.Defending)) raw *= DEFEND
        return Result(max(1, raw.roundToInt()), crit, isWeak)
    }

    fun heal(caster: Combatant, power: Int, rng: Rng): Int =
        max(1, (caster.stats.mag * power / 100.0 * (0.95 + rng.nextDouble() * 0.1)).roundToInt())
}
