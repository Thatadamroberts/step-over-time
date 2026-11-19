package com.stepquest.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The hardware counter restarts at 0 on boot; tell the store, then resume background sampling. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        StepStore.onReboot(c);
        StepWorker.schedule(c);
        StepWorker.runOnce(c);
    }
}
