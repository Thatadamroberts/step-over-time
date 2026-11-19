using System;
using System.Collections.Generic;
using Newtonsoft.Json;
using StepQuest.Core.Save;
using StepQuest.Core.Steps;

namespace StepQuest.Tests
{
    /// <summary>Clock with a fixed UTC offset so midnight-splitting is deterministic.</summary>
    public sealed class FakeClock : IClock
    {
        public DateTime UtcNow { get; set; }
        public TimeSpan Offset { get; set; }
        public FakeClock(DateTime utc, double offsetHours = 0) { UtcNow = DateTime.SpecifyKind(utc, DateTimeKind.Utc); Offset = TimeSpan.FromHours(offsetHours); }
        public DateTime LocalNow => DateTime.SpecifyKind(UtcNow + Offset, DateTimeKind.Unspecified);
        public DateTime ToLocal(DateTime utc) => DateTime.SpecifyKind(utc + Offset, DateTimeKind.Unspecified);
        public DateTime ToUtc(DateTime local) => DateTime.SpecifyKind(local - Offset, DateTimeKind.Utc);
        public void Advance(TimeSpan t) => UtcNow += t;
    }

    /// <summary>Step source backed by a list of (utc instant, steps) samples.</summary>
    public sealed class FakeStepSource : IStepSource
    {
        public readonly List<(DateTime at, long steps)> Samples = new List<(DateTime, long)>();
        public bool Available = true;
        public int Calls;
        public void Add(DateTime utc, long steps) => Samples.Add((utc, steps));
        public long ReadSteps(DateTime fromUtc, DateTime toUtc)
        {
            Calls++;
            if (!Available) return -1;
            long sum = 0;
            foreach (var (at, steps) in Samples) if (at >= fromUtc && at < toUtc) sum += steps;
            return sum;
        }
    }

    public sealed class NewtonsoftSerializer : ISaveSerializer
    {
        static readonly JsonSerializerSettings Settings = new JsonSerializerSettings
        {
            TypeNameHandling = TypeNameHandling.None,
            NullValueHandling = NullValueHandling.Include,
            ObjectCreationHandling = ObjectCreationHandling.Replace,
        };
        public string Serialize(SaveData data) => JsonConvert.SerializeObject(data, Formatting.Indented, Settings);
        public SaveData Deserialize(string json) => JsonConvert.DeserializeObject<SaveData>(json, Settings);
        public int PeekVersion(string json) => Newtonsoft.Json.Linq.JObject.Parse(json).Value<int?>("Version") ?? 0;
    }

    public sealed class MemoryStorage : ISaveStorage
    {
        public string Contents;
        public string Read() => Contents;
        public void Write(string c) => Contents = c;
    }
}
