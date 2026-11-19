package com.stepquest.bridge;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Periodic background job: sample the step counter, update the widget, maybe notify. */
public class StepWorker extends Worker {
    static final String UNIQUE = "stepquest-sampler";

    public StepWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        StepSampler.sampleOnce(c, 10_000);
        StepWidgetProvider.refreshAll(c);
        Notifier.maybeNotify(c);
        return Result.success();
    }

    static void schedule(Context c) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(StepWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    static void runOnce(Context c) {
        WorkManager.getInstance(c).enqueue(new OneTimeWorkRequest.Builder(StepWorker.class).build());
    }
}
