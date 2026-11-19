package com.stepquest.bridge;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** Game state the Unity side pushes down, so the widget and notifications can show it while the game is closed. */
final class Snapshot {
    static final int STEPS_PER_ACTION = 1000;

    long lastSyncMs;
    int banked;
    int remainder;
    int todaySteps;
    long todayStartMs;
    int streak;
    boolean notifications;

    static Snapshot load(Context c) {
        SharedPreferences p = StepStore.prefs(c);
        Snapshot s = new Snapshot();
        s.lastSyncMs = p.getLong("snap_sync", 0);
        s.banked = p.getInt("snap_banked", 0);
        s.remainder = p.getInt("snap_remainder", 0);
        s.todaySteps = p.getInt("snap_today", 0);
        s.todayStartMs = p.getLong("snap_today_start", 0);
        s.streak = p.getInt("snap_streak", 0);
        s.notifications = p.getBoolean("snap_notify", true);
        return s;
    }

    void save(Context c) {
        StepStore.prefs(c).edit()
                .putLong("snap_sync", lastSyncMs)
                .putInt("snap_banked", banked)
                .putInt("snap_remainder", remainder)
                .putInt("snap_today", todaySteps)
                .putLong("snap_today_start", todayStartMs)
                .putInt("snap_streak", streak)
                .putBoolean("snap_notify", notifications)
                .apply();
    }

    /** Steps since the game last synced, which the game hasn't converted yet. */
    long pendingSteps(Context c, long nowMs) {
        if (lastSyncMs <= 0) return 0;
        return StepStore.stepsBetween(c, lastSyncMs, nowMs);
    }

    int estimatedActions(Context c, long nowMs) {
        return banked + (int) ((remainder + pendingSteps(c, nowMs)) / STEPS_PER_ACTION);
    }

    int estimatedToNext(Context c, long nowMs) {
        long pool = remainder + pendingSteps(c, nowMs);
        return (int) (STEPS_PER_ACTION - (pool % STEPS_PER_ACTION));
    }

    int estimatedTodaySteps(Context c, long nowMs) {
        long startOfToday = startOfDay(nowMs);
        long base = todayStartMs == startOfToday ? todaySteps : 0;
        long since = Math.max(lastSyncMs, startOfToday);
        return (int) (base + StepStore.stepsBetween(c, since, nowMs));
    }

    static long startOfDay(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
