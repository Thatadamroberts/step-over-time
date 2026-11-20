package com.stepquest.app.steps

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Turns the hardware TYPE_STEP_COUNTER (cumulative since boot, reset on reboot) into a monotonic,
 * reboot-safe history of (time, cumulative steps) samples. The game asks "steps between A and B"
 * and gets an interpolated answer, so steps walked while the app was closed land on the right day.
 */
object StepStore {
    private const val PREFS = "stepquest_bridge"
    private const val FILE = "step_samples.csv"
    private const val RETENTION_MS = 40L * 24 * 60 * 60 * 1000
    private const val IDLE_SAMPLE_MS = 30L * 60 * 1000
    private const val ASSUMED_STEPS_PER_MINUTE = 110L
    private val lock = Any()
    private var cache: MutableList<LongArray>? = null

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(c: Context, raw: Long, now: Long): Long = synchronized(lock) {
        val p = prefs(c)
        val lastRaw = p.getLong("last_raw", -1)
        var total = p.getLong("total", 0)
        val delta = when {
            lastRaw < 0 -> 0L           // first reading is the baseline
            raw < lastRaw -> raw        // device rebooted: counter restarted
            else -> raw - lastRaw
        }
        total += delta
        p.edit().putLong("last_raw", raw).putLong("total", total).apply()

        val s = load(c)
        val last = s.lastOrNull()
        if (last == null) {
            s += longArrayOf(now, total); save(c, s)
        } else if (delta > 0 || now - last[0] >= IDLE_SAMPLE_MS) {
            val walkMs = delta * 60_000L / ASSUMED_STEPS_PER_MINUTE
            // Long unsampled gap (app closed, Doze): assume the steps were walked at a normal pace just
            // before this reading rather than spread across the gap. Keeps daily buckets honest.
            if (delta > 0 && now - last[0] > walkMs) s += longArrayOf(now - walkMs, last[1])
            s += longArrayOf(now, total)
            val cutoff = now - RETENTION_MS
            while (s.size > 2 && s[1][0] < cutoff) s.removeAt(0)
            save(c, s)
        }
        total
    }

    fun onReboot(c: Context) = synchronized(lock) { prefs(c).edit().putLong("last_raw", 0).apply() }

    fun total(c: Context) = prefs(c).getLong("total", 0)

    fun stepsBetween(c: Context, from: Long, to: Long): Long {
        if (to <= from) return 0
        synchronized(lock) {
            val s = load(c)
            if (s.isEmpty()) return 0
            return maxOf(0, cumulativeAt(s, to) - cumulativeAt(s, from))
        }
    }

    private fun cumulativeAt(s: List<LongArray>, t: Long): Long {
        if (t <= s.first()[0]) return s.first()[1]
        if (t >= s.last()[0]) return s.last()[1]
        var lo = 0; var hi = s.size - 1
        while (hi - lo > 1) { val mid = (lo + hi) ushr 1; if (s[mid][0] <= t) lo = mid else hi = mid }
        val a = s[lo]; val b = s[hi]
        if (b[0] == a[0]) return b[1]
        return a[1] + Math.round((b[1] - a[1]) * ((t - a[0]).toDouble() / (b[0] - a[0])))
    }

    private fun load(c: Context): MutableList<LongArray> {
        cache?.let { return it }
        val f = File(c.filesDir, FILE)
        val out = mutableListOf<LongArray>()
        if (f.exists()) f.forEachLine { line ->
            val i = line.indexOf(',')
            if (i > 0) runCatching { out += longArrayOf(line.substring(0, i).toLong(), line.substring(i + 1).trim().toLong()) }
        }
        cache = out
        return out
    }

    private fun save(c: Context, s: List<LongArray>) {
        val tmp = File(c.filesDir, "$FILE.tmp")
        runCatching {
            tmp.writeText(buildString { s.forEach { append(it[0]).append(',').append(it[1]).append('\n') } })
            tmp.renameTo(File(c.filesDir, FILE))
        }
    }
}
