using System;
using StepQuest.Core.Steps;
using UnityEngine;
#if UNITY_ANDROID && !UNITY_EDITOR
using UnityEngine.Android;
#endif

namespace StepQuest.Unity.Steps
{
    /// <summary>
    /// Talks to the native StepBridge (Assets/Plugins/Android/StepBridge.androidlib), which keeps a
    /// reboot-safe history of the hardware step counter, even while the game is closed.
    /// In the Editor it reports zero steps; use the debug button to add some.
    /// </summary>
    public sealed class AndroidStepBridge : IStepSource
    {
        public const string ActivityRecognition = "android.permission.ACTIVITY_RECOGNITION";
        public const string PostNotifications = "android.permission.POST_NOTIFICATIONS";

#if UNITY_ANDROID && !UNITY_EDITOR
        readonly AndroidJavaClass _bridge;
#endif
        public bool IsNative { get; }

        public AndroidStepBridge()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                _bridge = new AndroidJavaClass("com.stepquest.bridge.StepBridge");
                using (var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var activity = player.GetStatic<AndroidJavaObject>("currentActivity"))
                    _bridge.CallStatic("init", activity);
                IsNative = true;
            }
            catch (Exception e)
            {
                Debug.LogError("StepBridge init failed: " + e);
                IsNative = false;
            }
#else
            IsNative = false;
#endif
        }

        public bool HasSensor => Get("hasSensor", false);

        public bool HasStepPermission
        {
            get
            {
#if UNITY_ANDROID && !UNITY_EDITOR
                return Permission.HasUserAuthorizedPermission(ActivityRecognition);
#else
                return true;
#endif
            }
        }

        public void RequestPermissions(Action<bool> onStepPermission)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            var cb = new PermissionCallbacks();
            cb.PermissionGranted += p => { if (p == ActivityRecognition) { onStepPermission?.Invoke(true); RequestNotifications(); } };
            cb.PermissionDenied += p => { if (p == ActivityRecognition) { onStepPermission?.Invoke(false); RequestNotifications(); } };
            if (HasStepPermission) { onStepPermission?.Invoke(true); RequestNotifications(); }
            else Permission.RequestUserPermission(ActivityRecognition, cb);
#else
            onStepPermission?.Invoke(true);
#endif
        }

        void RequestNotifications()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (!Permission.HasUserAuthorizedPermission(PostNotifications))
                Permission.RequestUserPermission(PostNotifications);
#endif
        }

        public void StartLive() => Call("startLive");
        public void StopLive() => Call("stopLive");
        public void SampleNow() => Call("sampleAsync");

        public long ReadSteps(DateTime fromUtc, DateTime toUtc)
        {
            if (!IsNative) return 0;
            return Get("readSteps", -1L, ToMillis(fromUtc), ToMillis(toUtc));
        }

        public void PushSnapshot(DateTime lastSyncUtc, int banked, int remainder, int todaySteps, DateTime localToday, int streak, bool notifications)
        {
            long todayStartMs = ToMillis(localToday.Date.ToUniversalTime());
            Call("pushSnapshot", ToMillis(lastSyncUtc), banked, remainder, todaySteps, todayStartMs, streak, notifications);
        }

        public void OnGameOpened(int banked) => Call("onGameOpened", banked);

        static long ToMillis(DateTime utc) => (long)(DateTime.SpecifyKind(utc, DateTimeKind.Utc) - DateTime.UnixEpoch).TotalMilliseconds;

        void Call(string method, params object[] args)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (!IsNative) return;
            try { _bridge.CallStatic(method, args); } catch (Exception e) { Debug.LogWarning($"StepBridge.{method}: {e.Message}"); }
#endif
        }

        T Get<T>(string method, T fallback, params object[] args)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (!IsNative) return fallback;
            try { return _bridge.CallStatic<T>(method, args); } catch (Exception e) { Debug.LogWarning($"StepBridge.{method}: {e.Message}"); }
#endif
            return fallback;
        }
    }
}
