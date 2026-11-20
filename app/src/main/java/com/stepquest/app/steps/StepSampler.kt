package com.stepquest.app.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Reads the hardware step counter: once (background) or continuously (app open). */
object StepSampler {
    private var liveThread: HandlerThread? = null
    private var liveListener: SensorEventListener? = null

    private fun sensor(c: Context): Pair<SensorManager, Sensor>? {
        val sm = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return sm to s
    }

    fun hasSensor(c: Context) = sensor(c) != null

    /** Blocks up to [timeoutMs] for one reading and records it. Never call on the main thread. */
    fun sampleOnce(c: Context, timeoutMs: Long): Boolean {
        val (sm, s) = sensor(c) ?: return false
        val latch = CountDownLatch(1)
        var value = -1L
        val t = HandlerThread("stepquest-sample").apply { start() }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { value = e.values[0].toLong(); latch.countDown() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        try {
            if (!sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL, Handler(t.looper))) return false
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: SecurityException) {
            return false
        } finally {
            sm.unregisterListener(l)
            t.quitSafely()
        }
        if (value < 0) return false
        StepStore.record(c, value, System.currentTimeMillis())
        return true
    }

    @Synchronized fun startLive(c: Context) {
        if (liveListener != null) return
        val (sm, s) = sensor(c) ?: return
        val app = c.applicationContext
        val t = HandlerThread("stepquest-live").apply { start() }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { StepStore.record(app, e.values[0].toLong(), System.currentTimeMillis()) }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        try {
            sm.registerListener(l, s, SensorManager.SENSOR_DELAY_UI, Handler(t.looper))
            liveThread = t; liveListener = l
        } catch (_: SecurityException) { t.quitSafely() }
    }

    @Synchronized fun stopLive(c: Context) {
        val sm = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        liveListener?.let { sm?.unregisterListener(it) }
        liveListener = null
        liveThread?.quitSafely(); liveThread = null
    }
}
