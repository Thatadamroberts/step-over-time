using System;
using System.Collections.Generic;
using System.Linq;
using StepQuest.Core.Data;
using StepQuest.Core.Economy;
using StepQuest.Core.Party;
using StepQuest.Core.World;

namespace StepQuest.Core.Combat
{
    public enum CommandKind { Attack, Skill, Defend, Item, Flee }

    public enum BattleOutcome { Ongoing, Victory, Defeat, Fled }

    [Serializable]
    public class BattleCommand
    {
        public CommandKind Kind;
        public string SkillId;
        public string ItemId;
        public int TargetId = -1;

        public static BattleCommand Attack(int target) => new BattleCommand { Kind = CommandKind.Attack, TargetId = target };
        public static BattleCommand Skill(string id, int target = -1) => new BattleCommand { Kind = CommandKind.Skill, SkillId = id, TargetId = target };
        public static BattleCommand Defend() => new BattleCommand { Kind = CommandKind.Defend };
        public static BattleCommand Item(string id, int target) => new BattleCommand { Kind = CommandKind.Item, ItemId = id, TargetId = target };
        public static BattleCommand Flee() => new BattleCommand { Kind = CommandKind.Flee };
    }

    /// <summary>
    /// Turn-based battle. Turn order is by SPD each round. Enemy turns resolve automatically;
    /// the battle then waits on a hero turn for a command. Every hero command costs one banked
    /// action (charged by <see cref="Session.GameSession"/>), so a battle can sit paused indefinitely
    /// and resume after the player walks more.
    /// </summary>
    [Serializable]
    public class Battle
    {
        public string RegionId;
        public bool IsBoss;
        public int Round;
        public BattleOutcome Outcome = BattleOutcome.Ongoing;
        public List<Combatant> Units = new List<Combatant>();
        public List<int> TurnQueue = new List<int>();
        public Rng Rng;
        public int StolenCoins;
        public List<string> Drops = new List<string>();
        public int RewardExp;
        public int RewardCoins;
        public int EnemiesDefeated;

        public const int MaxLog = 40;
        public List<BattleEvent> Log = new List<BattleEvent>();

        public static Battle Create(IList<Hero> party, IEnumerable<string> enemyIds, Rng rng, string regionId = null)
        {
            var b = new Battle { Rng = rng, RegionId = regionId };
            int id = 0;
            for (int i = 0; i < party.Count; i++)
            {
                var h = party[i];
                b.Units.Add(new Combatant
                {
                    Id = id++, Name = h.Name, IsHero = true, HeroIndex = i,
                    Stats = h.TotalStats(), Hp = h.Hp, Mp = h.Mp
                });
                b.SessionHeroClasses.Add(h.Class);
            }
            var counts = new Dictionary<string, int>();
            var ids = enemyIds.ToList();
            foreach (var eid in ids)
            {
                var def = GameData.Enemy(eid);
                counts.TryGetValue(eid, out var n);
                counts[eid] = n + 1;
                bool dup = ids.Count(x => x == eid) > 1;
                b.Units.Add(new Combatant
                {
                    Id = id++, Name = dup ? $"{def.Name} {(char)('A' + n)}" : def.Name, IsHero = false, EnemyId = eid,
                    Stats = def.Stats, Hp = def.Stats.MaxHp, Mp = def.Stats.MaxMp,
                    Shield = def.Shield, MaxShield = def.Shield
                });
                if (def.IsBoss) b.IsBoss = true;
            }
            b.Round = 0;
            b.CheckOutcome(new List<BattleEvent>());
            b.AdvanceToHeroTurn();
            return b;
        }

        public IEnumerable<Combatant> Heroes => Units.Where(u => u.IsHero);
        public IEnumerable<Combatant> Enemies => Units.Where(u => !u.IsHero);
        public Combatant Unit(int id) => Units.FirstOrDefault(u => u.Id == id);

        public Combatant CurrentActor => TurnQueue.Count > 0 ? Unit(TurnQueue[0]) : null;
        public bool AwaitingHeroCommand => Outcome == BattleOutcome.Ongoing && CurrentActor != null && CurrentActor.IsHero;

        /// <summary>Upcoming actors after the current one, for the turn-order strip.</summary>
        public IEnumerable<Combatant> UpcomingTurns() => TurnQueue.Skip(1).Select(Unit).Where(u => u != null && u.IsAlive);

        public bool IsWeak(Combatant enemy, Element e)
        {
            if (enemy.IsHero) return false;
            return Array.IndexOf(GameData.Enemy(enemy.EnemyId).Weaknesses, e) >= 0;
        }

        /// <summary>Whether a weakness has been revealed by hitting it (for the UI).</summary>
        public List<string> RevealedWeaknesses = new List<string>();

        public bool IsRevealed(Combatant enemy, Element e) => RevealedWeaknesses.Contains(enemy.EnemyId + ":" + e);

        public string Validate(BattleCommand cmd, Inventory inv)
        {
            if (!AwaitingHeroCommand) return "Not a hero's turn";
            var actor = CurrentActor;
            switch (cmd.Kind)
            {
                case CommandKind.Attack:
                    var t = Unit(cmd.TargetId);
                    if (t == null || t.IsHero || !t.IsAlive) return "Choose an enemy";
                    return null;
                case CommandKind.Skill:
                    var hero = actor;
                    var skill = GameData.SkillOrNull(cmd.SkillId);
                    if (skill == null) return "Unknown skill";
                    if (hero.Mp < skill.MpCost) return "Not enough MP";
                    return ValidateTarget(skill.Target, cmd.TargetId);
                case CommandKind.Item:
                    var item = GameData.ItemOrNull(cmd.ItemId);
                    if (item == null || !item.IsConsumable) return "Not usable";
                    if (inv == null || inv.Count(item.Id) <= 0) return "None left";
                    return ValidateTarget(item.Use == ConsumableEffect.Revive ? TargetKind.DeadAlly : TargetKind.SingleAlly, cmd.TargetId);
                case CommandKind.Flee:
                    return IsBoss ? "Can't flee from a boss!" : null;
                default:
                    return null;
            }
        }

        private string ValidateTarget(TargetKind kind, int targetId)
        {
            var t = Unit(targetId);
            switch (kind)
            {
                case TargetKind.SingleEnemy: return t != null && !t.IsHero && t.IsAlive ? null : "Choose an enemy";
                case TargetKind.SingleAlly: return t != null && t.IsHero && t.IsAlive ? null : "Choose an ally";
                case TargetKind.DeadAlly: return t != null && t.IsHero && !t.IsAlive ? null : "Choose a fallen ally";
                default: return null;
            }
        }

        /// <summary>Executes the current hero's command, then runs enemy turns until the next hero turn.</summary>
        public List<BattleEvent> Execute(BattleCommand cmd, Inventory inv)
        {
            var events = new List<BattleEvent>();
            var err = Validate(cmd, inv);
            if (err != null) throw new InvalidOperationException(err);

            var actor = CurrentActor;
            switch (cmd.Kind)
            {
                case CommandKind.Attack:
                    var weaponElement = GameData.Class(HeroOf(actor)).WeaponElement;
                    Hit(actor, Unit(cmd.TargetId), 100, false, weaponElement, 1, events);
                    break;
                case CommandKind.Skill:
                    UseSkill(actor, GameData.Skill(cmd.SkillId), cmd.TargetId, events);
                    break;
                case CommandKind.Defend:
                    actor.Apply(StatusKind.Defending, 1);
                    events.Add(new BattleEvent { Kind = BattleEventKind.Status, SourceId = actor.Id, TargetId = actor.Id, Text = $"{actor.Name} braces for impact." });
                    break;
                case CommandKind.Item:
                    UseItem(actor, GameData.Item(cmd.ItemId), Unit(cmd.TargetId), inv, events);
                    break;
                case CommandKind.Flee:
                    TryFlee(events);
                    break;
            }

            EndTurn(actor, events);
            CheckOutcome(events);
            RunUntilHeroTurn(events);
            Append(events);
            return events;
        }

        private HeroClass HeroOf(Combatant c) => SessionHeroClasses != null && c.HeroIndex >= 0 && c.HeroIndex < SessionHeroClasses.Count
            ? SessionHeroClasses[c.HeroIndex] : HeroClass.Warrior;

        /// <summary>Hero classes by party index (stored so the battle is self-contained when saved).</summary>
        public List<HeroClass> SessionHeroClasses = new List<HeroClass>();

        /// <summary>Runs enemy turns (and status ticks) until it's a living hero's turn or the battle ends.</summary>
        public List<BattleEvent> AdvanceToHeroTurn()
        {
            var events = new List<BattleEvent>();
            RunUntilHeroTurn(events);
            Append(events);
            return events;
        }

        private void RunUntilHeroTurn(List<BattleEvent> events)
        {
            int guard = 0;
            while (Outcome == BattleOutcome.Ongoing && guard++ < 500)
            {
                if (TurnQueue.Count == 0) NewRound();
                var actor = CurrentActor;
                if (actor == null || !actor.IsAlive) { TurnQueue.RemoveAt(0); continue; }

                if (!StartTurn(actor, events)) // skipped (broken) or died to poison
                {
                    TurnQueue.RemoveAt(0);
                    CheckOutcome(events);
                    continue;
                }

                if (actor.IsHero) break;

                EnemyAct(actor, events);
                EndTurn(actor, events);
                CheckOutcome(events);
            }
        }

        private void NewRound()
        {
            Round++;
            TurnQueue = Units.Where(u => u.IsAlive)
                .OrderByDescending(u => u.Stats.Spd)
                .ThenBy(u => u.IsHero ? 0 : 1)
                .ThenBy(u => u.Id)
                .Select(u => u.Id).ToList();
        }

        /// <summary>Returns false if the actor loses this turn.</summary>
        private bool StartTurn(Combatant actor, List<BattleEvent> events)
        {
            actor.Statuses.RemoveAll(s => s.Kind == StatusKind.Defending);

            var poison = actor.Get(StatusKind.Poison);
            if (poison != null)
            {
                int dmg = Math.Max(1, poison.Potency);
                actor.Hp = Math.Max(0, actor.Hp - dmg);
                events.Add(new BattleEvent { Kind = BattleEventKind.Damage, TargetId = actor.Id, Amount = dmg, Text = $"{actor.Name} takes {dmg} poison damage." });
                if (!actor.IsAlive) { OnDefeated(actor, events); return false; }
            }
            var regen = actor.Get(StatusKind.Regen);
            if (regen != null)
            {
                int amt = HealUnit(actor, regen.Potency);
                if (amt > 0) events.Add(new BattleEvent { Kind = BattleEventKind.Heal, TargetId = actor.Id, Amount = amt, Text = $"{actor.Name} regenerates {amt} HP." });
            }

            if (actor.Broken)
            {
                actor.Broken = false;
                actor.Shield = actor.MaxShield;
                events.Add(new BattleEvent { Kind = BattleEventKind.Recover, TargetId = actor.Id, Text = $"{actor.Name} recovers from the break!" });
                TickDurations(actor);
                return false;
            }
            return true;
        }

        private void EndTurn(Combatant actor, List<BattleEvent> events)
        {
            actor.TurnsTaken++;
            TickDurations(actor);
            if (TurnQueue.Count > 0 && TurnQueue[0] == actor.Id) TurnQueue.RemoveAt(0);
        }

        private static void TickDurations(Combatant actor)
        {
            foreach (var s in actor.Statuses)
                if (s.Kind != StatusKind.Defending) s.Turns--;
            actor.Statuses.RemoveAll(s => s.Kind != StatusKind.Defending && s.Turns <= 0);
        }

        private void UseSkill(Combatant actor, SkillDef skill, int targetId, List<BattleEvent> events)
        {
            actor.Mp -= skill.MpCost;
            events.Add(new BattleEvent { Kind = BattleEventKind.Info, SourceId = actor.Id, Text = $"{actor.Name} uses {skill.Name}!" });

            foreach (var target in ResolveTargets(actor, skill.Target, targetId))
            {
                switch (skill.Effect)
                {
                    case SkillEffect.Damage:
                        Hit(actor, target, skill.Power, skill.Magical, skill.Element, skill.ShieldHits, events);
                        break;
                    case SkillEffect.StealCoins:
                        Hit(actor, target, skill.Power, false, skill.Element, skill.ShieldHits, events);
                        int coins = 3 + Rng.Range(0, 3 + GameData.Enemy(target.EnemyId).Coins / 2);
                        StolenCoins += coins;
                        events.Add(new BattleEvent { Kind = BattleEventKind.Coins, SourceId = actor.Id, TargetId = target.Id, Amount = coins, Text = $"Stole {coins} coins!" });
                        break;
                    case SkillEffect.Poison:
                        Hit(actor, target, skill.Power, skill.Magical, skill.Element, skill.ShieldHits, events);
                        if (target.IsAlive)
                        {
                            target.Apply(StatusKind.Poison, skill.Turns, Math.Max(1, actor.Stats.Mag / 3));
                            events.Add(new BattleEvent { Kind = BattleEventKind.Status, TargetId = target.Id, Text = $"{target.Name} is poisoned." });
                        }
                        break;
                    case SkillEffect.Heal:
                        int amt = HealUnit(target, Damage.Heal(actor, skill.Power, Rng));
                        events.Add(new BattleEvent { Kind = BattleEventKind.Heal, SourceId = actor.Id, TargetId = target.Id, Amount = amt, Text = $"{target.Name} recovers {amt} HP." });
                        break;
                    case SkillEffect.Regen:
                        target.Apply(StatusKind.Regen, skill.Turns, Math.Max(1, Damage.Heal(actor, skill.Power, Rng)));
                        events.Add(new BattleEvent { Kind = BattleEventKind.Status, TargetId = target.Id, Text = $"{target.Name} is wrapped in renewing vines." });
                        break;
                    case SkillEffect.Revive:
                        if (!target.IsAlive)
                        {
                            target.Hp = Math.Max(1, target.Stats.MaxHp * skill.Power / 100);
                            target.Statuses.Clear();
                            events.Add(new BattleEvent { Kind = BattleEventKind.Revive, SourceId = actor.Id, TargetId = target.Id, Amount = target.Hp, Text = $"{target.Name} is revived!" });
                            if (!TurnQueue.Contains(target.Id)) TurnQueue.Add(target.Id);
                        }
                        break;
                    case SkillEffect.BuffDef:
                        target.Apply(StatusKind.DefUp, skill.Turns);
                        events.Add(new BattleEvent { Kind = BattleEventKind.Status, TargetId = target.Id, Text = $"{target.Name}'s defense rises." });
                        break;
                    case SkillEffect.BuffAtk:
                        target.Apply(StatusKind.AtkUp, skill.Turns);
                        events.Add(new BattleEvent { Kind = BattleEventKind.Status, TargetId = target.Id, Text = $"{target.Name}'s attack rises." });
                        break;
                    case SkillEffect.Taunt:
                        target.Apply(StatusKind.Taunt, skill.Turns);
                        target.Apply(StatusKind.DefUp, skill.Turns);
                        events.Add(new BattleEvent { Kind = BattleEventKind.Status, TargetId = target.Id, Text = $"{target.Name} draws the enemy's attention!" });
                        break;
                }
                if (Outcome != BattleOutcome.Ongoing) break;
            }
        }

        private IEnumerable<Combatant> ResolveTargets(Combatant actor, TargetKind kind, int targetId)
        {
            switch (kind)
            {
                case TargetKind.AllEnemies: return (actor.IsHero ? Enemies : Heroes).Where(u => u.IsAlive).ToList();
                case TargetKind.AllAllies: return (actor.IsHero ? Heroes : Enemies).Where(u => u.IsAlive).ToList();
                case TargetKind.Self: return new[] { actor };
                default: var t = Unit(targetId); return t == null ? Enumerable.Empty<Combatant>() : new[] { t };
            }
        }

        private void UseItem(Combatant actor, ItemDef item, Combatant target, Inventory inv, List<BattleEvent> events)
        {
            inv.Remove(item.Id);
            switch (item.Use)
            {
                case ConsumableEffect.HealHp:
                    int hp = HealUnit(target, item.UseAmount);
                    events.Add(new BattleEvent { Kind = BattleEventKind.Heal, SourceId = actor.Id, TargetId = target.Id, Amount = hp, Text = $"{item.Name}: {target.Name} recovers {hp} HP." });
                    break;
                case ConsumableEffect.HealMp:
                    int before = target.Mp;
                    target.Mp = Math.Min(target.Stats.MaxMp, target.Mp + item.UseAmount);
                    events.Add(new BattleEvent { Kind = BattleEventKind.Heal, SourceId = actor.Id, TargetId = target.Id, Amount = target.Mp - before, Text = $"{item.Name}: {target.Name} recovers {target.Mp - before} MP." });
                    break;
                case ConsumableEffect.Revive:
                    target.Hp = Math.Max(1, target.Stats.MaxHp * item.UseAmount / 100);
                    target.Statuses.Clear();
                    if (!TurnQueue.Contains(target.Id)) TurnQueue.Add(target.Id);
                    events.Add(new BattleEvent { Kind = BattleEventKind.Revive, SourceId = actor.Id, TargetId = target.Id, Amount = target.Hp, Text = $"{item.Name}: {target.Name} is revived!" });
                    break;
                case ConsumableEffect.Cleanse:
                    target.Statuses.RemoveAll(s => s.Kind == StatusKind.Poison);
                    events.Add(new BattleEvent { Kind = BattleEventKind.Status, SourceId = actor.Id, TargetId = target.Id, Text = $"{item.Name}: {target.Name} is cleansed." });
                    break;
            }
        }

        private void TryFlee(List<BattleEvent> events)
        {
            double heroSpd = Heroes.Where(h => h.IsAlive).Select(h => h.Stats.Spd).DefaultIfEmpty(0).Average();
            double enemySpd = Enemies.Where(e => e.IsAlive).Select(e => e.Stats.Spd).DefaultIfEmpty(0).Average();
            double p = Math.Max(0.2, Math.Min(0.9, 0.5 + (heroSpd - enemySpd) * 0.03));
            if (Rng.Chance(p))
            {
                Outcome = BattleOutcome.Fled;
                events.Add(new BattleEvent { Kind = BattleEventKind.Info, Text = "The party escapes!" });
            }
            else events.Add(new BattleEvent { Kind = BattleEventKind.Miss, Text = "Couldn't get away!" });
        }

        private void EnemyAct(Combatant enemy, List<BattleEvent> events)
        {
            var def = GameData.Enemy(enemy.EnemyId);
            var living = Heroes.Where(h => h.IsAlive).ToList();
            if (living.Count == 0) return;

            if (def.AoeEvery > 0 && (enemy.TurnsTaken + 1) % def.AoeEvery == 0)
            {
                events.Add(new BattleEvent { Kind = BattleEventKind.Info, SourceId = enemy.Id, Text = $"{enemy.Name} unleashes a devastating attack!" });
                foreach (var h in living) Hit(enemy, h, 80, true, def.AttackElement, 0, events);
                return;
            }

            var taunter = living.FirstOrDefault(h => h.Has(StatusKind.Taunt));
            Combatant target;
            if (taunter != null && Rng.Chance(0.8)) target = taunter;
            else if (Rng.Chance(0.3)) target = living.OrderBy(h => (double)h.Hp / h.Stats.MaxHp).First(); // pick on the weak
            else target = living[Rng.Range(0, living.Count)];

            bool magical = def.AttackElement != Element.Physical;
            Hit(enemy, target, 100, magical, def.AttackElement, 0, events);
        }

        private void Hit(Combatant attacker, Combatant target, int power, bool magical, Element element, int shieldHits, List<BattleEvent> events)
        {
            if (target == null || !target.IsAlive) return;
            bool[] weak = null;
            if (!target.IsHero)
            {
                weak = new bool[Enum.GetValues(typeof(Element)).Length];
                foreach (var e in GameData.Enemy(target.EnemyId).Weaknesses) weak[(int)e] = true;
            }
            var r = Damage.Compute(attacker, target, power, magical, element, Rng, weak);
            target.Hp = Math.Max(0, target.Hp - r.Amount);
            events.Add(new BattleEvent
            {
                Kind = BattleEventKind.Damage, SourceId = attacker.Id, TargetId = target.Id, Amount = r.Amount, Crit = r.Crit, Weak = r.Weak,
                Text = $"{target.Name} takes {r.Amount}{(r.Crit ? " (critical!)" : "")}{(r.Weak ? " — weak!" : "")}."
            });

            if (r.Weak && !target.IsHero)
            {
                var key = target.EnemyId + ":" + element;
                if (!RevealedWeaknesses.Contains(key)) RevealedWeaknesses.Add(key);
                if (!target.Broken && target.IsAlive && shieldHits > 0)
                {
                    target.Shield = Math.Max(0, target.Shield - shieldHits);
                    if (target.Shield == 0)
                    {
                        target.Broken = true;
                        events.Add(new BattleEvent { Kind = BattleEventKind.Break, TargetId = target.Id, Text = $"BREAK! {target.Name} is staggered!" });
                        // A broken enemy loses its next turn: if it's still queued this round, that's the one it loses.
                    }
                }
            }

            if (!target.IsAlive) OnDefeated(target, events);
            CheckOutcome(events);
        }

        private void OnDefeated(Combatant c, List<BattleEvent> events)
        {
            c.Statuses.Clear();
            c.Broken = false;
            TurnQueue.RemoveAll(id => id == c.Id && (CurrentActor == null || CurrentActor.Id != c.Id));
            events.Add(new BattleEvent { Kind = BattleEventKind.Defeated, TargetId = c.Id, Text = c.IsHero ? $"{c.Name} falls!" : $"{c.Name} is defeated." });
            if (!c.IsHero) EnemiesDefeated++;
        }

        private int HealUnit(Combatant c, int amount)
        {
            if (!c.IsAlive) return 0;
            int before = c.Hp;
            c.Hp = Math.Min(c.Stats.MaxHp, c.Hp + amount);
            return c.Hp - before;
        }

        private void CheckOutcome(List<BattleEvent> events)
        {
            if (Outcome != BattleOutcome.Ongoing) return;
            if (Enemies.All(e => !e.IsAlive))
            {
                Outcome = BattleOutcome.Victory;
                foreach (var e in Enemies)
                {
                    var def = GameData.Enemy(e.EnemyId);
                    RewardExp += def.Exp;
                    RewardCoins += def.Coins;
                    foreach (var loot in def.Loot)
                        if (Rng.Chance(loot.Chance)) Drops.Add(loot.ItemId);
                }
                RewardCoins += StolenCoins;
                events.Add(new BattleEvent { Kind = BattleEventKind.Info, Text = "Victory!" });
            }
            else if (Heroes.All(h => !h.IsAlive))
            {
                Outcome = BattleOutcome.Defeat;
                events.Add(new BattleEvent { Kind = BattleEventKind.Info, Text = "The party has fallen..." });
            }
        }

        private void Append(List<BattleEvent> events)
        {
            Log.AddRange(events);
            if (Log.Count > MaxLog) Log.RemoveRange(0, Log.Count - MaxLog);
        }

        /// <summary>Copies HP/MP back onto the persistent heroes.</summary>
        public void WriteBack(IList<Hero> party)
        {
            foreach (var u in Heroes)
            {
                if (u.HeroIndex < 0 || u.HeroIndex >= party.Count) continue;
                party[u.HeroIndex].Hp = u.Hp;
                party[u.HeroIndex].Mp = u.Mp;
                party[u.HeroIndex].ClampVitals();
            }
        }
    }
}
