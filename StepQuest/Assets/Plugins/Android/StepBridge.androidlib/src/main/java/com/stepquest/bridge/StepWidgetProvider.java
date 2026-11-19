package com.stepquest.bridge;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.text.NumberFormat;

/** Home-screen widget: today's steps, ready actions and progress to the next one. */
public class StepWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(c));
    }

    static void refreshAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, StepWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews v = build(c);
        for (int id : ids) mgr.updateAppWidget(id, v);
    }

    private static RemoteViews build(Context c) {
        long now = System.currentTimeMillis();
        Snapshot s = Snapshot.load(c);
        int today = s.estimatedTodaySteps(c, now);
        int actions = s.estimatedActions(c, now);
        int toNext = s.estimatedToNext(c, now);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.sq_widget);
        v.setTextViewText(R.id.sq_widget_steps, NumberFormat.getIntegerInstance().format(today));
        v.setTextViewText(R.id.sq_widget_actions, String.valueOf(actions));
        v.setTextViewText(R.id.sq_widget_next, NumberFormat.getIntegerInstance().format(toNext) + " steps to next action");
        v.setProgressBar(R.id.sq_widget_progress, Snapshot.STEPS_PER_ACTION, Snapshot.STEPS_PER_ACTION - toNext, false);
        v.setTextViewText(R.id.sq_widget_streak, s.streak > 0 ? "🔥 " + s.streak : "");

        Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (launch != null) {
            v.setOnClickPendingIntent(R.id.sq_widget_root,
                    PendingIntent.getActivity(c, 1, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        return v;
    }
}
