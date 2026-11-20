package com.stepquest.core.combat

import com.stepquest.core.Rng
import com.stepquest.core.data.GameData
import com.stepquest.core.economy.ConsumableEffect
import com.stepquest.core.economy.Inventory
import com.stepquest.core.economy.ItemDef
import com.stepquest.core.party.Element
import com.stepquest.core.party.Hero
import com.stepquest.core.party.HeroClass
import com.stepquest.core.party.SkillDef
import com.stepquest.core.party.SkillEffect
import com.stepquest.core.party.TargetKind
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

enum class CommandKind { Attack, Skill, Defend, Item, Flee }
enum class BattleOutcome { Ongoing, Victory, Defeat, Fled }

@Serializable
data class BattleCommand(
    val kind: CommandKind,
    val skillId: String? = null,
    val itemId: String? = null,
    val targetId: Int = -1,
) {
    companion object {
        fun attack(target: Int) = BattleCommand(CommandKind.Attack, targetId = target)
        fun skill(id: String, target: Int = -1) = BattleCommand(CommandKind.Skill, skillId = id, targetId = target)
        fun defend() = BattleCommand(CommandKind.Defend)
        fun item(id: String, target: Int) = BattleCommand(CommandKind.Item, itemId = id, targetId = target)
        fun flee() = BattleCommand(CommandKind.Flee)
    }
}

/**
 * Turn-based battle. Turn order is by SPD each round; enemy turns resolve automatically and the
 * battle then waits on a hero turn. Each hero command costs one banked action (charged by
 * GameSession), so a battle can sit paused indefinitely and resume after more walking.
 */
@Serializable
class Battle(
    val regionId: String?,
    val rng: Rng,
    val units: MutableList<Combatant> = mutableListOf(),
    val heroClasses: MutableList<HeroClass> = mutableListOf(),
    var isBoss: Boolean = false,
    var round: Int = 0,
    var outcome: BattleOutcome = BattleOutcome.Ongoing,
    var turnQueue: MutableList<Int> = mutableListOf(),
    var stolenCoins: Int = 0,
    val drops: MutableList<String> = mutableListOf(),
    var rewardExp: Int = 0,
    var rewardCoins: Int = 0,
    val revealed: MutableList<String> = mutableListOf(),
    val log: MutableList<BattleEvent> = mutableListOf(),
) {
    val heroes get() = units.filter { it.isHero }
    val enemies get() = units.filter { !it.isHero }
    fun unit(id: Int) = units.firstOrNull { it.id == id }
    val currentActor: Combatant? get() = turnQueue.firstOrNull()?.let { unit(it) }
    val awaitingHeroCommand get() = outcome == BattleOutcome.Ongoing && currentActor?.isHero == true
    fun upcomingTurns() = turnQueue.drop(1).mapNotNull { unit(it) }.filter { it.isAlive }

    fun isWeak(e: Combatant, el: Element) = !e.isHero && el in GameData.enemy(e.enemyId!!).weaknesses
    fun isRevealed(e: Combatant, el: Element) = "${e.enemyId}:$el" in revealed

    fun validate(cmd: BattleCommand, inv: Inventory?): String? {
        if (!awaitingHeroCommand) return "Not a hero's turn"
        val actor = currentActor!!
        return when (cmd.kind) {
            CommandKind.Attack -> unit(cmd.targetId).let { if (it == null || it.isHero || !it.isAlive) "Choose an enemy" else null }
            CommandKind.Skill -> {
                val s = GameData.skillOrNull(cmd.skillId) ?: return "Unknown skill"
                if (actor.mp < s.mpCost) "Not enough MP" else validateTarget(s.target, cmd.targetId)
            }
            CommandKind.Item -> {
                val item = GameData.itemOrNull(cmd.itemId)
                if (item == null || !item.isConsumable) "Not usable"
                else if (inv == null || inv.count(item.id) <= 0) "None left"
                else validateTarget(if (item.use == ConsumableEffect.Revive) TargetKind.DeadAlly else TargetKind.SingleAlly, cmd.targetId)
            }
            CommandKind.Flee -> if (isBoss) "Can't flee from a boss!" else null
            CommandKind.Defend -> null
        }
    }

    private fun validateTarget(kind: TargetKind, id: Int): String? {
        val t = unit(id)
        return when (kind) {
            TargetKind.SingleEnemy -> if (t != null && !t.isHero && t.isAlive) null else "Choose an enemy"
            TargetKind.SingleAlly -> if (t != null && t.isHero && t.isAlive) null else "Choose an ally"
            TargetKind.DeadAlly -> if (t != null && t.isHero && !t.isAlive) null else "Choose a fallen ally"
            else -> null
        }
    }

    /** Executes the current hero's command, then runs enemy turns until the next hero turn. */
    fun execute(cmd: BattleCommand, inv: Inventory): List<BattleEvent> {
        validate(cmd, inv)?.let { throw IllegalStateException(it) }
        val ev = mutableListOf<BattleEvent>()
        val actor = currentActor!!
        when (cmd.kind) {
            CommandKind.Attack -> hit(actor, unit(cmd.targetId), 100, false, GameData.classDef(heroClasses[actor.heroIndex]).weaponElement, 1, ev)
            CommandKind.Skill -> useSkill(actor, GameData.skill(cmd.skillId!!), cmd.targetId, ev)
            CommandKind.Defend -> {
                actor.apply(StatusKind.Defending, 1)
                ev += BattleEvent(BattleEventKind.Status, actor.id, actor.id, text = "${actor.name} braces for impact.")
            }
            CommandKind.Item -> useItem(actor, GameData.item(cmd.itemId!!), unit(cmd.targetId)!!, inv, ev)
            CommandKind.Flee -> tryFlee(ev)
        }
        endTurn(actor)
        checkOutcome(ev)
        runUntilHeroTurn(ev)
        append(ev)
        return ev
    }

    fun advanceToHeroTurn(): List<BattleEvent> {
        val ev = mutableListOf<BattleEvent>()
        runUntilHeroTurn(ev)
        append(ev)
        return ev
    }

    private fun runUntilHeroTurn(ev: MutableList<BattleEvent>) {
        var guard = 0
        while (outcome == BattleOutcome.Ongoing && guard++ < 500) {
            if (turnQueue.isEmpty()) newRound()
            val actor = currentActor
            if (actor == null || !actor.isAlive) { turnQueue.removeAt(0); continue }
            if (!startTurn(actor, ev)) {
                turnQueue.removeAt(0)
                checkOutcome(ev)
                continue
            }
            if (actor.isHero) break
            enemyAct(actor, ev)
            endTurn(actor)
            checkOutcome(ev)
        }
    }

    private fun newRound() {
        round++
        turnQueue = units.filter { it.isAlive }
            .sortedWith(compareByDescending<Combatant> { it.stats.spd }.thenBy { if (it.isHero) 0 else 1 }.thenBy { it.id })
            .map { it.id }.toMutableList()
    }

    /** Returns false if the actor loses this turn. */
    private fun startTurn(a: Combatant, ev: MutableList<BattleEvent>): Boolean {
        a.statuses.removeAll { it.kind == StatusKind.Defending }
        a.get(StatusKind.Poison)?.let { p ->
            val dmg = max(1, p.potency)
            a.hp = max(0, a.hp - dmg)
            ev += BattleEvent(BattleEventKind.Damage, targetId = a.id, amount = dmg, text = "${a.name} takes $dmg poison damage.")
            if (!a.isAlive) { onDefeated(a, ev); return false }
        }
        a.get(StatusKind.Regen)?.let { r ->
            val amt = healUnit(a, r.potency)
            if (amt > 0) ev += BattleEvent(BattleEventKind.Heal, targetId = a.id, amount = amt, text = "${a.name} regenerates $amt HP.")
        }
        if (a.broken) {
            a.broken = false
            a.shield = a.maxShield
            ev += BattleEvent(BattleEventKind.Recover, targetId = a.id, text = "${a.name} recovers from the break!")
            tickDurations(a)
            return false
        }
        return true
    }

    private fun endTurn(a: Combatant) {
        a.turnsTaken++
        tickDurations(a)
        if (turnQueue.firstOrNull() == a.id) turnQueue.removeAt(0)
    }

    private fun tickDurations(a: Combatant) {
        a.statuses.forEach { if (it.kind != StatusKind.Defending) it.turns-- }
        a.statuses.removeAll { it.kind != StatusKind.Defending && it.turns <= 0 }
    }

    private fun useSkill(a: Combatant, s: SkillDef, targetId: Int, ev: MutableList<BattleEvent>) {
        a.mp -= s.mpCost
        ev += BattleEvent(BattleEventKind.Info, a.id, text = "${a.name} uses ${s.name}!")
        for (t in resolveTargets(a, s.target, targetId)) {
            when (s.effect) {
                SkillEffect.Damage -> hit(a, t, s.power, s.magical, s.element, s.shieldHits, ev)
                SkillEffect.StealCoins -> {
                    hit(a, t, s.power, false, s.element, s.shieldHits, ev)
                    val coins = 3 + rng.range(0, 3 + GameData.enemy(t.enemyId!!).coins / 2)
                    stolenCoins += coins
                    ev += BattleEvent(BattleEventKind.Coins, a.id, t.id, coins, text = "Stole $coins coins!")
                }
                SkillEffect.Poison -> {
                    hit(a, t, s.power, s.magical, s.element, s.shieldHits, ev)
                    if (t.isAlive) {
                        t.apply(StatusKind.Poison, s.turns, max(1, a.stats.mag / 3))
                        ev += BattleEvent(BattleEventKind.Status, targetId = t.id, text = "${t.name} is poisoned.")
                    }
                }
                SkillEffect.Heal -> {
                    val amt = healUnit(t, Damage.heal(a, s.power, rng))
                    ev += BattleEvent(BattleEventKind.Heal, a.id, t.id, amt, text = "${t.name} recovers $amt HP.")
                }
                SkillEffect.Regen -> {
                    t.apply(StatusKind.Regen, s.turns, max(1, Damage.heal(a, s.power, rng)))
                    ev += BattleEvent(BattleEventKind.Status, targetId = t.id, text = "${t.name} is wrapped in renewing vines.")
                }
                SkillEffect.Revive -> if (!t.isAlive) {
                    t.hp = max(1, t.stats.maxHp * s.power / 100)
                    t.statuses.clear()
                    ev += BattleEvent(BattleEventKind.Revive, a.id, t.id, t.hp, text = "${t.name} is revived!")
                    if (t.id !in turnQueue) turnQueue += t.id
                }
                SkillEffect.BuffDef -> { t.apply(StatusKind.DefUp, s.turns); ev += BattleEvent(BattleEventKind.Status, targetId = t.id, text = "${t.name}'s defense rises.") }
                SkillEffect.BuffAtk -> { t.apply(StatusKind.AtkUp, s.turns); ev += BattleEvent(BattleEventKind.Status, targetId = t.id, text = "${t.name}'s attack rises.") }
                SkillEffect.Taunt -> {
                    t.apply(StatusKind.Taunt, s.turns); t.apply(StatusKind.DefUp, s.turns)
                    ev += BattleEvent(BattleEventKind.Status, targetId = t.id, text = "${t.name} draws the enemy's attention!")
                }
            }
            if (outcome != BattleOutcome.Ongoing) break
        }
    }

    private fun resolveTargets(a: Combatant, kind: TargetKind, id: Int): List<Combatant> = when (kind) {
        TargetKind.AllEnemies -> (if (a.isHero) enemies else heroes).filter { it.isAlive }
        TargetKind.AllAllies -> (if (a.isHero) heroes else enemies).filter { it.isAlive }
        TargetKind.Self -> listOf(a)
        else -> listOfNotNull(unit(id))
    }

    private fun useItem(a: Combatant, item: ItemDef, t: Combatant, inv: Inventory, ev: MutableList<BattleEvent>) {
        inv.remove(item.id)
        when (item.use) {
            ConsumableEffect.HealHp -> {
                val hp = healUnit(t, item.useAmount)
                ev += BattleEvent(BattleEventKind.Heal, a.id, t.id, hp, text = "${item.name}: ${t.name} recovers $hp HP.")
            }
            ConsumableEffect.HealMp -> {
                val before = t.mp
                t.mp = min(t.stats.maxMp, t.mp + item.useAmount)
                ev += BattleEvent(BattleEventKind.Heal, a.id, t.id, t.mp - before, text = "${item.name}: ${t.name} recovers ${t.mp - before} MP.")
            }
            ConsumableEffect.Revive -> {
                t.hp = max(1, t.stats.maxHp * item.useAmount / 100)
                t.statuses.clear()
                if (t.id !in turnQueue) turnQueue += t.id
                ev += BattleEvent(BattleEventKind.Revive, a.id, t.id, t.hp, text = "${item.name}: ${t.name} is revived!")
            }
            ConsumableEffect.Cleanse -> {
                t.statuses.removeAll { it.kind == StatusKind.Poison }
                ev += BattleEvent(BattleEventKind.Status, a.id, t.id, text = "${item.name}: ${t.name} is cleansed.")
            }
            ConsumableEffect.None -> {}
        }
    }

    private fun tryFlee(ev: MutableList<BattleEvent>) {
        val hs = heroes.filter { it.isAlive }.map { it.stats.spd }.ifEmpty { listOf(0) }.average()
        val es = enemies.filter { it.isAlive }.map { it.stats.spd }.ifEmpty { listOf(0) }.average()
        val p = (0.5 + (hs - es) * 0.03).coerceIn(0.2, 0.9)
        if (rng.chance(p)) { outcome = BattleOutcome.Fled; ev += BattleEvent(BattleEventKind.Info, text = "The party escapes!") }
        else ev += BattleEvent(BattleEventKind.Miss, text = "Couldn't get away!")
    }

    private fun enemyAct(e: Combatant, ev: MutableList<BattleEvent>) {
        val def = GameData.enemy(e.enemyId!!)
        val living = heroes.filter { it.isAlive }
        if (living.isEmpty()) return
        if (def.aoeEvery > 0 && (e.turnsTaken + 1) % def.aoeEvery == 0) {
            ev += BattleEvent(BattleEventKind.Info, e.id, text = "${e.name} unleashes a devastating attack!")
            living.forEach { hit(e, it, 80, true, def.attackElement, 0, ev) }
            return
        }
        val taunter = living.firstOrNull { it.has(StatusKind.Taunt) }
        val target = when {
            taunter != null && rng.chance(0.8) -> taunter
            rng.chance(0.3) -> living.minBy { it.hp.toDouble() / it.stats.maxHp }
            else -> living[rng.range(0, living.size)]
        }
        hit(e, target, 100, def.attackElement != Element.Physical, def.attackElement, 0, ev)
    }

    private fun hit(a: Combatant, t: Combatant?, power: Int, magical: Boolean, el: Element, shieldHits: Int, ev: MutableList<BattleEvent>) {
        if (t == null || !t.isAlive) return
        val weak = if (t.isHero) null else GameData.enemy(t.enemyId!!).weaknesses.toSet()
        val r = Damage.compute(a, t, power, magical, el, rng, weak)
        t.hp = max(0, t.hp - r.amount)
        ev += BattleEvent(BattleEventKind.Damage, a.id, t.id, r.amount, r.crit, r.weak,
            "${t.name} takes ${r.amount}${if (r.crit) " (critical!)" else ""}${if (r.weak) " — weak!" else ""}.")
        if (r.weak && !t.isHero) {
            val key = "${t.enemyId}:$el"
            if (key !in revealed) revealed += key
            if (!t.broken && t.isAlive && shieldHits > 0) {
                t.shield = max(0, t.shield - shieldHits)
                if (t.shield == 0) {
                    t.broken = true
                    ev += BattleEvent(BattleEventKind.Break, targetId = t.id, text = "BREAK! ${t.name} is staggered!")
                }
            }
        }
        if (!t.isAlive) onDefeated(t, ev)
        checkOutcome(ev)
    }

    private fun onDefeated(c: Combatant, ev: MutableList<BattleEvent>) {
        c.statuses.clear()
        c.broken = false
        val current = currentActor?.id
        turnQueue.removeAll { it == c.id && it != current }
        ev += BattleEvent(BattleEventKind.Defeated, targetId = c.id, text = if (c.isHero) "${c.name} falls!" else "${c.name} is defeated.")
    }

    private fun healUnit(c: Combatant, amount: Int): Int {
        if (!c.isAlive) return 0
        val before = c.hp
        c.hp = min(c.stats.maxHp, c.hp + amount)
        return c.hp - before
    }

    private fun checkOutcome(ev: MutableList<BattleEvent>) {
        if (outcome != BattleOutcome.Ongoing) return
        if (enemies.none { it.isAlive }) {
            outcome = BattleOutcome.Victory
            for (e in enemies) {
                val d = GameData.enemy(e.enemyId!!)
                rewardExp += d.exp
                rewardCoins += d.coins
                d.loot.forEach { if (rng.chance(it.chance)) drops += it.itemId }
            }
            rewardCoins += stolenCoins
            ev += BattleEvent(BattleEventKind.Info, text = "Victory!")
        } else if (heroes.none { it.isAlive }) {
            outcome = BattleOutcome.Defeat
            ev += BattleEvent(BattleEventKind.Info, text = "The party has fallen...")
        }
    }

    private fun append(ev: List<BattleEvent>) {
        log += ev
        if (log.size > MAX_LOG) repeat(log.size - MAX_LOG) { log.removeAt(0) }
    }

    /** Copies HP/MP back onto the persistent heroes. */
    fun writeBack(party: List<Hero>) {
        for (u in heroes) party.getOrNull(u.heroIndex)?.let { it.hp = u.hp; it.mp = u.mp; it.clampVitals() }
    }

    companion object {
        const val MAX_LOG = 40

        fun create(party: List<Hero>, enemyIds: List<String>, rng: Rng, regionId: String?): Battle {
            val b = Battle(regionId, rng)
            var id = 0
            party.forEachIndexed { i, h ->
                b.units += Combatant(id++, h.name, true, heroIndex = i, stats = h.totalStats(), hp = h.hp, mp = h.mp)
                b.heroClasses += h.heroClass
            }
            val seen = mutableMapOf<String, Int>()
            for (eid in enemyIds) {
                val d = GameData.enemy(eid)
                val n = seen.getOrDefault(eid, 0); seen[eid] = n + 1
                val dup = enemyIds.count { it == eid } > 1
                b.units += Combatant(id++, if (dup) "${d.name} ${'A' + n}" else d.name, false, enemyId = eid,
                    stats = d.stats, hp = d.stats.maxHp, mp = d.stats.maxMp, shield = d.shield, maxShield = d.shield)
                if (d.isBoss) b.isBoss = true
            }
            b.advanceToHeroTurn()
            return b
        }
    }
}
