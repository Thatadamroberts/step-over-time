using System;
using System.IO;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using StepQuest.Core.Save;
using UnityEngine;

namespace StepQuest.Unity.Boot
{
    public sealed class NewtonsoftSaveSerializer : ISaveSerializer
    {
        static readonly JsonSerializerSettings Settings = new JsonSerializerSettings
        {
            TypeNameHandling = TypeNameHandling.None,
            ObjectCreationHandling = ObjectCreationHandling.Replace,
            NullValueHandling = NullValueHandling.Include,
            Formatting = Formatting.None,
        };

        public string Serialize(SaveData data) => JsonConvert.SerializeObject(data, Settings);
        public SaveData Deserialize(string json) => JsonConvert.DeserializeObject<SaveData>(json, Settings);
        public int PeekVersion(string json) => JObject.Parse(json).Value<int?>("Version") ?? 0;
    }

    /// <summary>Atomic file save in persistentDataPath, with one rolling backup.</summary>
    public sealed class FileSaveStorage : ISaveStorage
    {
        readonly string _path;
        public FileSaveStorage(string fileName = "save.json") { _path = Path.Combine(Application.persistentDataPath, fileName); }

        public string Read()
        {
            if (File.Exists(_path)) return File.ReadAllText(_path);
            var bak = _path + ".bak";
            return File.Exists(bak) ? File.ReadAllText(bak) : null;
        }

        public void Write(string contents)
        {
            var tmp = _path + ".tmp";
            File.WriteAllText(tmp, contents);
            if (File.Exists(_path)) File.Copy(_path, _path + ".bak", true);
            if (File.Exists(_path)) File.Delete(_path);
            File.Move(tmp, _path);
        }

        public void Delete()
        {
            foreach (var p in new[] { _path, _path + ".bak", _path + ".tmp" })
                if (File.Exists(p)) File.Delete(p);
        }
    }
}
