package com.stepquest.bridge;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the hardware TYPE_STEP_COUNTER (cumulative since boot, reset on reboot) into a
 * monotonic, reboot-safe step history: a list of (time, cumulative steps) samples.
 * The game asks for "steps between A and B" and gets an interpolated answer, so steps
 * walked while the app was closed are credited to the right day.
 */
final class StepStore {
    private static final String PREFS = "stepquest_bridge";
    private static final String KEY_LAST_RAW = "last_raw";
    private static final String KEY_TOTAL = "total";
    private static final String FILE = "step_samples.csv";
    private static final long RETENTION_MS = 40L * 24 * 60 * 60 * 1000;
    /** Write a sample even without new steps this often, so interpolation knows when you were idle. */
    private static final long IDLE_SAMPLE_MS = 30L * 60 * 1000;
    private static final long ASSUMED_STEPS_PER_MINUTE = 110;

    private static final Object LOCK = new Object();
    private static List<long[]> cache;

    private StepStore() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Records a raw counter reading. Returns the new cumulative total. */
    static long record(Context c, long raw, long nowMs) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(c);
            long lastRaw = p.getLong(KEY_LAST_RAW, -1);
            long total = p.getLong(KEY_TOTAL, 0);
            long delta;
            if (lastRaw < 0) delta = 0;            // first ever reading is the baseline
            else if (raw < lastRaw) delta = raw;   // device rebooted: counter restarted at 0
            else delta = raw - lastRaw;
            total += delta;
            p.edit().putLong(KEY_LAST_RAW, raw).putLong(KEY_TOTAL, total).apply();

            List<long[]> samples = load(c);
            long[] last = samples.isEmpty() ? null : samples.get(samples.size() - 1);
            if (last == null) {
                // Anchor so steps before the first reading aren't invented.
                samples.add(new long[]{nowMs, total});
                save(c, samples);
            } else if (delta > 0 || nowMs - last[0] >= IDLE_SAMPLE_MS) {
                long walkMs = delta * 60_000L / ASSUMED_STEPS_PER_MINUTE;
                if (delta > 0 && nowMs - last[0] > walkMs) {
                    // Long unsampled gap (app closed, Doze): assume the steps were walked at a normal pace
                    // just before this reading, rather than spread across the whole gap.
                    // Keeps daily buckets honest and stays under the game's steps-per-minute sanity cap.
                    samples.add(new long[]{nowMs - walkMs, last[1]});
                }
                samples.add(new long[]{nowMs, total});
                prune(samples, nowMs);
                save(c, samples);
            }
            return total;
        }
    }

    /** Called on boot: the counter will restart, so the next reading counts from zero. */
    static void onReboot(Context c) {
        synchronized (LOCK) {
            prefs(c).edit().putLong(KEY_LAST_RAW, 0).apply();
        }
    }

    static long total(Context c) {
        return prefs(c).getLong(KEY_TOTAL, 0);
    }

    /** Steps walked between two instants (epoch millis), by linear interpolation of samples. */
    static long stepsBetween(Context c, long fromMs, long toMs) {
        if (toMs <= fromMs) return 0;
        synchronized (LOCK) {
            List<long[]> s = load(c);
            if (s.isEmpty()) return 0;
            return Math.max(0, cumulativeAt(s, toMs) - cumulativeAt(s, fromMs));
        }
    }

    static long cumulativeAt(List<long[]> s, long t) {
        if (t <= s.get(0)[0]) return s.get(0)[1];
        long[] last = s.get(s.size() - 1);
        if (t >= last[0]) return last[1];
        int lo = 0, hi = s.size() - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (s.get(mid)[0] <= t) lo = mid; else hi = mid;
        }
        long[] a = s.get(lo), b = s.get(hi);
        if (b[0] == a[0]) return b[1];
        double f = (double) (t - a[0]) / (double) (b[0] - a[0]);
        return a[1] + Math.round((b[1] - a[1]) * f);
    }

    private static void prune(List<long[]> s, long nowMs) {
        long cutoff = nowMs - RETENTION_MS;
        int drop = 0;
        while (drop < s.size() - 1 && s.get(drop + 1)[0] < cutoff) drop++;
        if (drop > 0) s.subList(0, drop).clear();
    }

    private static List<long[]> load(Context c) {
        if (cache != null) return cache;
        List<long[]> out = new ArrayList<>();
        File f = new File(c.getFilesDir(), FILE);
        if (f.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int comma = line.indexOf(',');
                    if (comma <= 0) continue;
                    try {
                        out.add(new long[]{Long.parseLong(line.substring(0, comma)), Long.parseLong(line.substring(comma + 1).trim())});
                    } catch (NumberFormatException ignored) { }
                }
            } catch (IOException ignored) { }
        }
        cache = out;
        return out;
    }

    private static void save(Context c, List<long[]> s) {
        File tmp = new File(c.getFilesDir(), FILE + ".tmp");
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder(s.size() * 24);
            for (long[] e : s) sb.append(e[0]).append(',').append(e[1]).append('\n');
            w.write(sb.toString());
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(new File(c.getFilesDir(), FILE));
    }
}
