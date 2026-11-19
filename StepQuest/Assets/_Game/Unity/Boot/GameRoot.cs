using System;
using System.Collections;
using StepQuest.Core.Save;
using StepQuest.Core.Session;
using StepQuest.Core.Steps;
using StepQuest.Unity.Stage;
using StepQuest.Unity.Steps;
using StepQuest.Unity.UI;
using UnityEngine;

namespace StepQuest.Unity.Boot
{
    /// <summary>
    /// Boots the game in any scene: loads the save, creates the session, the 3D stage and the UI,
    /// keeps steps in sync and saves on every change and when the app is backgrounded.
    /// </summary>
    public sealed class GameRoot : MonoBehaviour
    {
        public static GameRoot Instance { get; private set; }

        public GameSession Session { get; private set; }
        public BattleStage Stage { get; private set; }
        public UIRoot UI { get; private set; }
        public AndroidStepBridge Steps { get; private set; }
        public bool StepPermissionGranted { get; private set; } = true;

        SaveService _saves;
        FileSaveStorage _storage;
        IClock _clock;
        bool _saveQueued;
        float _nextSync;
        const float SyncInterval = 4f;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        static void Boot()
        {
            if (Instance != null) return;
            var go = new GameObject("GameRoot");
            DontDestroyOnLoad(go);
            go.AddComponent<GameRoot>();
        }

        void Awake()
        {
            Instance = this;
            Application.targetFrameRate = 60;
            Screen.sleepTimeout = SleepTimeout.SystemSetting;
            Screen.orientation = ScreenOrientation.Portrait;

            _clock = new StepQuest.Core.Steps.SystemClock();
            _storage = new FileSaveStorage();
            _saves = new SaveService(new NewtonsoftSaveSerializer(), _storage);
            SaveData data;
            try { data = _saves.LoadOrCreate(_clock.UtcNow); }
            catch (Exception e)
            {
                Debug.LogError("Save could not be loaded; starting fresh. " + e);
                data = SaveData.NewGame(_clock.UtcNow);
            }
#if UNITY_EDITOR
            data.Settings.DebugTools = true;
#endif
            Session = new GameSession(data, _clock);
            Session.Changed += () => _saveQueued = true;

            Steps = new AndroidStepBridge();
            Stage = BattleStage.Create();
            Stage.transform.SetParent(transform, false);
            Stage.BuildRegion(Session.CurrentRegion);
            if (Session.Battle != null) Stage.ShowBattle(Session.Battle); else Stage.ShowParty(Session.Party);
            UI = UIRoot.Create(this);
            UI.transform.SetParent(transform, false);
        }

        void Start()
        {
            Steps.RequestPermissions(granted =>
            {
                StepPermissionGranted = granted;
                if (granted) { Steps.StartLive(); Steps.SampleNow(); }
                UI.MarkDirty();
            });
            StartCoroutine(SyncSoon());
        }

        void Update()
        {
            if (Time.unscaledTime >= _nextSync)
            {
                _nextSync = Time.unscaledTime + SyncInterval;
                SyncSteps();
            }
        }

        void LateUpdate()
        {
            if (_saveQueued) { _saveQueued = false; SaveNow(); }
        }

        public void SyncSteps()
        {
            if (!Steps.IsNative) return;
            try { Session.SyncSteps(Steps); }
            catch (Exception e) { Debug.LogWarning("Step sync failed: " + e.Message); }
        }

        IEnumerator SyncSoon()
        {
            // Give the native sampler a moment to record a fresh reading.
            yield return new WaitForSecondsRealtime(1.5f);
            SyncSteps();
            Steps.OnGameOpened(Session.Ledger.BankedActions);
        }

        public void SaveNow()
        {
            try { _saves.Save(Session.Data); }
            catch (Exception e) { Debug.LogError("Save failed: " + e); }
            PushSnapshot();
        }

        void PushSnapshot()
        {
            var l = Session.Ledger;
            var lastSync = new DateTime(Math.Max(l.LastSyncUtcTicks, DateTime.UnixEpoch.Ticks), DateTimeKind.Utc);
            Steps.PushSnapshot(lastSync, l.BankedActions, l.StepRemainder, Session.TodaySteps, _clock.LocalNow, Session.Streak.Current, Session.Data.Settings.NotificationsEnabled);
        }

        void OnApplicationPause(bool paused)
        {
            if (Session == null) return;
            if (paused)
            {
                SaveNow();
                Steps.StopLive();
            }
            else
            {
                if (StepPermissionGranted) { Steps.StartLive(); Steps.SampleNow(); }
                StartCoroutine(SyncSoon());
            }
        }

        void OnApplicationQuit()
        {
            if (Session != null) SaveNow();
        }

        /// <summary>Wipes progress (Settings → Reset). Reloads into a brand new game.</summary>
        public void ResetGame()
        {
            _storage.Delete();
            var data = SaveData.NewGame(_clock.UtcNow);
            data.Settings.DebugTools = Session.Data.Settings.DebugTools;
            Destroy(UI.gameObject);
            Session = new GameSession(data, _clock);
            Session.Changed += () => _saveQueued = true;
            Stage.BuildRegion(Session.CurrentRegion);
            Stage.ShowParty(Session.Party);
            UI = UIRoot.Create(this);
            UI.transform.SetParent(transform, false);
            SaveNow();
        }
    }
}
