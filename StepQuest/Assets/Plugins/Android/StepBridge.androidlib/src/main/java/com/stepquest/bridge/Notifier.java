package com.stepquest.bridge;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;

/** "You've earned N new actions" notifications, rate-limited and silent at night. */
final class Notifier {
    private static final String CHANNEL = "stepquest_actions";
    private static final int ID = 4201;
    private static final long MIN_INTERVAL_MS = 2L * 60 * 60 * 1000;
    private static final int QUIET_FROM = 22, QUIET_TO = 8;

    private Notifier() {}

    static void maybeNotify(Context c) {
        Snapshot s = Snapshot.load(c);
        if (!s.notifications || s.lastSyncMs <= 0) return;
        if (Build.VERSION.SDK_INT >= 33 &&
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        long now = System.currentTimeMillis();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= QUIET_FROM || hour < QUIET_TO) return;

        SharedPreferences p = StepStore.prefs(c);
        long lastNotified = p.getLong("notify_last", 0);
        int lastCount = p.getInt("notify_count", 0);
        if (now - lastNotified < MIN_INTERVAL_MS) return;

        int actions = s.estimatedActions(c, now);
        int fresh = actions - s.banked;
        // Only notify about newly earned actions, and not again for the same total.
        if (fresh <= 0 || actions <= lastCount) return;

        ensureChannel(c);
        String title = fresh == 1 ? "⚔ 1 new action earned" : "⚔ " + fresh + " new actions earned";
        String body = "Your party has " + actions + " action" + (actions == 1 ? "" : "s") + " ready. Return to battle!";

        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        PendingIntent pi = launch == null ? null :
                PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        android.app.Notification.Builder b = new android.app.Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true);
        if (pi != null) b.setContentIntent(pi);

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.notify(ID, b.build());
        p.edit().putLong("notify_last", now).putInt("notify_count", actions).apply();
    }

    /** The game calls this when opened, so the next notification is about steps after this point. */
    static void reset(Context c, int bankedNow) {
        StepStore.prefs(c).edit().putInt("notify_count", bankedNow).apply();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ID);
    }

    private static void ensureChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Actions earned", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Tells you when walking has earned new combat actions.");
        nm.createNotificationChannel(ch);
    }
}
