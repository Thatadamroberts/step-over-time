using System;
using System.Collections.Generic;
using StepQuest.Core.Party;

namespace StepQuest.Core.Combat
{
    public enum StatusKind { Defending, AtkUp, DefUp, Taunt, Poison, Regen }

    [Serializable]
    public class Status
    {
        public StatusKind Kind;
        public int Turns;
        public int Potency;
    }

    [Serializable]
    public class Combatant
    {
        public int Id;
        public string Name;
        public bool IsHero;
        public int HeroIndex = -1;
        public string EnemyId;
        public Stats Stats;
        public int Hp;
        public int Mp;
        public int Shield;
        public int MaxShield;
        /// <summary>When broken, the combatant skips its next turn and takes double damage.</summary>
        public bool Broken;
        public int TurnsTaken;
        public List<Status> Statuses = new List<Status>();

        public bool IsAlive => Hp > 0;

        public bool Has(StatusKind k) => Statuses.Exists(s => s.Kind == k);

        public Status Get(StatusKind k) => Statuses.Find(s => s.Kind == k);

        public void Apply(StatusKind k, int turns, int potency = 0)
        {
            var existing = Get(k);
            if (existing != null) { existing.Turns = Math.Max(existing.Turns, turns); existing.Potency = Math.Max(existing.Potency, potency); }
            else Statuses.Add(new Status { Kind = k, Turns = turns, Potency = potency });
        }
    }

    public enum BattleEventKind { Damage, Heal, Miss, Break, Recover, Revive, Status, Defeated, Info, Coins }

    [Serializable]
    public class BattleEvent
    {
        public BattleEventKind Kind;
        public int SourceId = -1;
        public int TargetId = -1;
        public int Amount;
        public bool Crit;
        public bool Weak;
        public string Text;
    }
}
