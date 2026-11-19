package com.stepquest.bridge;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Reads the hardware step counter, either once (background) or continuously (app open). */
final class StepSampler {
    private static HandlerThread liveThread;
    private static SensorEventListener liveListener;

    private StepSampler() {}

    static boolean hasSensor(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    /** Blocks (up to timeoutMs) for one reading and records it. Never call on the main thread. */
    static boolean sampleOnce(Context c, long timeoutMs) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return false;
        Sensor sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) return false;

        final CountDownLatch latch = new CountDownLatch(1);
        final long[] value = {-1};
        HandlerThread t = new HandlerThread("stepquest-sample");
        t.start();
        SensorEventListener l = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                value[0] = (long) e.values[0];
                latch.countDown();
            }
            @Override public void onAccuracyChanged(Sensor s, int a) { }
        };
        try {
            if (!sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL, new Handler(t.getLooper()))) return false;
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (SecurityException noPermission) {
            return false;
        } finally {
            sm.unregisterListener(l);
            t.quitSafely();
        }
        if (value[0] < 0) return false;
        StepStore.record(c, value[0], System.currentTimeMillis());
        return true;
    }

    static synchronized void startLive(final Context c) {
        if (liveListener != null) return;
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return;
        Sensor sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) return;
        liveThread = new HandlerThread("stepquest-live");
        liveThread.start();
        liveListener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                StepStore.record(c, (long) e.values[0], System.currentTimeMillis());
            }
            @Override public void onAccuracyChanged(Sensor s, int a) { }
        };
        try {
            sm.registerListener(liveListener, sensor, SensorManager.SENSOR_DELAY_UI, new Handler(liveThread.getLooper()));
        } catch (SecurityException noPermission) {
            stopLive(c);
        }
    }

    static synchronized void stopLive(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null && liveListener != null) sm.unregisterListener(liveListener);
        liveListener = null;
        if (liveThread != null) { liveThread.quitSafely(); liveThread = null; }
    }
}
