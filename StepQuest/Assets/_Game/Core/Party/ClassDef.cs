using System.Collections.Generic;

namespace StepQuest.Core.Party
{
    public enum HeroClass { Warrior, Rogue, Mage, Druid, Priest }

    public enum Element { Physical, Fire, Ice, Lightning, Nature, Holy, Dark }

    public enum TargetKind { SingleEnemy, AllEnemies, SingleAlly, AllAllies, Self, DeadAlly }

    public enum SkillEffect { Damage, Heal, Revive, BuffDef, BuffAtk, Taunt, Poison, StealCoins, Regen }

    public sealed class SkillDef
    {
        public string Id;
        public string Name;
        public string Description;
        public int MpCost;
        public TargetKind Target;
        public SkillEffect Effect;
        public Element Element;
        /// <summary>Multiplier applied to the scaling stat (×100, so 150 = 1.5×).</summary>
        public int Power;
        public bool Magical;
        /// <summary>Extra shield damage when hitting a weakness.</summary>
        public int ShieldHits = 1;
        /// <summary>Duration in enemy turns, for buffs/DoTs.</summary>
        public int Turns;
    }

    public sealed class ClassDef
    {
        public HeroClass Class;
        public string Name;
        public string Blurb;
        public Stats Base;
        /// <summary>Gained every level.</summary>
        public Stats Growth;
        public Element WeaponElement = Element.Physical;
        public List<string> StartingSkills = new List<string>();
        public List<SkillTreeNode> Tree = new List<SkillTreeNode>();
    }

    public sealed class SkillTreeNode
    {
        public string Id;
        public string Name;
        public string Description;
        /// <summary>Node that must be unlocked first (null = root).</summary>
        public string Requires;
        public int Cost = 1;
        public int MinLevel = 1;
        public string UnlocksSkill;
        public Stats Bonus;
    }
}
