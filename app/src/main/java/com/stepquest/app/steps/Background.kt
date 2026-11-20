package com.stepquest.app.steps

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.stepquest.app.R
import java.text.NumberFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Game state pushed by the app so the widget and notifications work while it's closed. */
data class Snapshot(
    val lastSyncMs: Long = 0, val banked: Int = 0, val remainder: Int = 0,
    val todaySteps: Int = 0, val todayStartMs: Long = 0, val streak: Int = 0, val notifications: Boolean = true,
    /** Steps needed today before actions unlock (80% of goal), and actions already waiting on it. */
    val threshold: Int = 0, val locked: Int = 0,
) {
    fun save(c: Context) = StepStore.prefs(c).edit()
        .putLong("snap_sync", lastSyncMs).putInt("snap_banked", banked).putInt("snap_remainder", remainder)
        .putInt("snap_today", todaySteps).putLong("snap_today_start", todayStartMs).putInt("snap_streak", streak)
        .putBoolean("snap_notify", notifications).putInt("snap_threshold", threshold).putInt("snap_locked", locked).apply()

    fun pending(c: Context, now: Long) = if (lastSyncMs <= 0) 0 else StepStore.stepsBetween(c, lastSyncMs, now)
    private fun newActions(c: Context, now: Long) = ((remainder + pending(c, now)) / 1000).toInt()
    private fun lockedToday(now: Long) = if (todayStartMs == startOfDay(now)) locked else 0
    fun unlocked(c: Context, now: Long) = threshold <= 0 || today(c, now) >= threshold
    /** Usable actions: earned ones only count once today has reached 80% of the goal. */
    fun actions(c: Context, now: Long) = if (unlocked(c, now)) banked + lockedToday(now) + newActions(c, now) else banked
    fun lockedNow(c: Context, now: Long) = if (unlocked(c, now)) 0 else lockedToday(now) + newActions(c, now)
    fun toUnlock(c: Context, now: Long) = maxOf(0, threshold - today(c, now))
    fun toNext(c: Context, now: Long) = (1000 - (remainder + pending(c, now)) % 1000).toInt()
    fun today(c: Context, now: Long): Int {
        val start = startOfDay(now)
        val base = if (todayStartMs == start) todaySteps.toLong() else 0L
        return (base + StepStore.stepsBetween(c, maxOf(lastSyncMs, start), now)).toInt()
    }

    companion object {
        fun load(c: Context) = StepStore.prefs(c).let { p ->
            Snapshot(p.getLong("snap_sync", 0), p.getInt("snap_banked", 0), p.getInt("snap_remainder", 0),
                p.getInt("snap_today", 0), p.getLong("snap_today_start", 0), p.getInt("snap_streak", 0), p.getBoolean("snap_notify", true),
                p.getInt("snap_threshold", 0), p.getInt("snap_locked", 0))
        }
        fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
            timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

/** Periodic background job: sample the counter, refresh the widget, maybe notify. */
class StepWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        StepSampler.sampleOnce(applicationContext, 10_000)
        StepWidgetProvider.refreshAll(applicationContext)
        Notifier.maybeNotify(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) = WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            "stepquest-sampler", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<StepWorker>(15, TimeUnit.MINUTES).build())
        fun runOnce(c: Context) = WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<StepWorker>().build())
    }
}

/** "You've earned N new actions", rate-limited and silent at night. */
object Notifier {
    private const val CHANNEL = "stepquest_actions"
    private const val ID = 4201

    fun maybeNotify(c: Context) {
        val s = Snapshot.load(c)
        if (!s.notifications || s.lastSyncMs <= 0) return
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 8) return
        val p = StepStore.prefs(c)
        if (now - p.getLong("notify_last", 0) < 2 * 3_600_000L) return
        val actions = s.actions(c, now)
        val fresh = actions - s.banked
        if (fresh <= 0 || actions <= p.getInt("notify_count", 0)) return

        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Actions earned", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when walking has earned new combat actions."
            })
        val launch = c.packageManager.getLaunchIntentForPackage(c.packageName)
        val pi = launch?.let { PendingIntent.getActivity(c, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(if (fresh == 1) "⚔ 1 new action earned" else "⚔ $fresh new actions earned")
            .setContentText("Your party has $actions action${if (actions == 1) "" else "s"} ready. Return to battle!")
            .setAutoCancel(true)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()
        nm.notify(ID, n)
        p.edit().putLong("notify_last", now).putInt("notify_count", actions).apply()
    }

    fun onGameOpened(c: Context, banked: Int) {
        StepStore.prefs(c).edit().putInt("notify_count", banked).apply()
        c.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }
}

/** Home-screen widget: today's steps, ready actions and progress to the next one. */
class StepWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { mgr.updateAppWidget(it, build(c)) } }

    companion object {
        fun refreshAll(c: Context) {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, StepWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val v = build(c)
            ids.forEach { mgr.updateAppWidget(it, v) }
        }

        private fun build(c: Context): RemoteViews {
            val now = System.currentTimeMillis()
            val s = Snapshot.load(c)
            val toNext = s.toNext(c, now)
            val nf = NumberFormat.getIntegerInstance()
            return RemoteViews(c.packageName, R.layout.sq_widget).apply {
                setTextViewText(R.id.sq_widget_steps, nf.format(s.today(c, now)))
                setTextViewText(R.id.sq_widget_actions, s.actions(c, now).toString())
                val lockedNow = s.lockedNow(c, now)
                setTextViewText(R.id.sq_widget_next,
                    if (!s.unlocked(c, now)) "${nf.format(s.toUnlock(c, now))} more steps to unlock" + (if (lockedNow > 0) " $lockedNow" else "")
                    else nf.format(toNext) + " steps to next action")
                setProgressBar(R.id.sq_widget_progress, 1000, 1000 - toNext, false)
                setTextViewText(R.id.sq_widget_streak, if (s.streak > 0) "🔥 ${s.streak}" else "")
                c.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
                    setOnClickPendingIntent(R.id.sq_widget_root, PendingIntent.getActivity(c, 1, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
            }
        }
    }
}

/** The counter restarts at 0 on boot: re-baseline and resume background sampling. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        StepStore.onReboot(c)
        StepWorker.schedule(c)
        StepWorker.runOnce(c)
    }
}
