package com.stepquest.bridge;

import android.content.Context;

/**
 * Static API called from Unity (AndroidJavaClass "com.stepquest.bridge.StepBridge").
 * All times are epoch milliseconds.
 */
public final class StepBridge {
    private static Context app;

    private StepBridge() {}

    public static void init(Context context) {
        app = context.getApplicationContext();
        StepWorker.schedule(app);
    }

    public static boolean hasSensor() {
        return app != null && StepSampler.hasSensor(app);
    }

    /** Continuous sampling while the game is in the foreground. */
    public static void startLive() {
        if (app != null) StepSampler.startLive(app);
    }

    public static void stopLive() {
        if (app != null) StepSampler.stopLive(app);
    }

    /** Takes a fresh reading on a background thread (e.g. on resume). */
    public static void sampleAsync() {
        if (app == null) return;
        new Thread(() -> StepSampler.sampleOnce(app, 5_000), "stepquest-sample-once").start();
    }

    /** Steps walked between two instants, or -1 if the step history isn't available. */
    public static long readSteps(long fromMs, long toMs) {
        if (app == null) return -1;
        return StepStore.stepsBetween(app, fromMs, toMs);
    }

    public static long totalSteps() {
        return app == null ? 0 : StepStore.total(app);
    }

    /** The game's current state, for the widget and notifications while the game is closed. */
    public static void pushSnapshot(long lastSyncMs, int banked, int remainder, int todaySteps, long todayStartMs, int streak, boolean notifications) {
        if (app == null) return;
        Snapshot s = new Snapshot();
        s.lastSyncMs = lastSyncMs;
        s.banked = banked;
        s.remainder = remainder;
        s.todaySteps = todaySteps;
        s.todayStartMs = todayStartMs;
        s.streak = streak;
        s.notifications = notifications;
        s.save(app);
        StepWidgetProvider.refreshAll(app);
    }

    /** Clears the "new actions" notification once the player is back in the game. */
    public static void onGameOpened(int banked) {
        if (app != null) Notifier.reset(app, banked);
    }
}
