package com.stepquest.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.stepquest.app.steps.Notifier
import com.stepquest.app.steps.Snapshot
import com.stepquest.app.steps.StepSampler
import com.stepquest.app.steps.StepStore
import com.stepquest.app.steps.StepWidgetProvider
import com.stepquest.app.steps.StepWorker
import com.stepquest.core.save.SaveCodec
import com.stepquest.core.save.SaveData
import com.stepquest.core.session.GameSession
import com.stepquest.core.steps.StepSource
import com.stepquest.core.steps.SystemClock
import java.io.File

data class Toast(val id: Long, val title: String, val body: String?)

/**
 * App-wide game state: owns the session, saves it on every change, keeps steps in sync with the
 * native step history and pushes a snapshot for the widget and notifications.
 */
object Game {
    lateinit var session: GameSession
        private set
    private lateinit var app: Context
    private lateinit var file: File

    /** Bumped on every change; reading it in a composable subscribes it to game updates. */
    var version by mutableIntStateOf(0)
        private set
    val toasts = mutableStateListOf<Toast>()
    private var lastLocked = 0
    private fun n(v: Int) = "%,d".format(v)
    private var toastId = 0L

    val hasSensor get() = StepSampler.hasSensor(app)

    private val source = StepSource { from, to -> StepStore.stepsBetween(app, from, to) }

    fun init(context: Context) {
        if (::session.isInitialized) return
        app = context.applicationContext
        file = File(app.filesDir, "save.json")
        val now = SystemClock.nowMillis()
        val data = runCatching { if (file.exists()) SaveCodec.decode(file.readText(), now) else null }
            .onFailure { Log.e("StepQuest", "Save unreadable, starting fresh", it) }
            .getOrNull() ?: SaveData.newGame(now)
        bind(GameSession(data, SystemClock))
        StepWorker.schedule(app)
    }

    private fun bind(s: GameSession) {
        session = s
        s.onChanged = { save(); version++ }
        s.onActionsEarned = { n -> toast(if (n == 1) "New action earned!" else "$n new actions earned!", "Your walking powers your party.") }
        s.onAchievements = { list -> list.forEach { toast("Trophy: ${it.name}", "${it.description}  +${it.rewardCoins} coins") } }
        s.onActionsForfeited = { n, _ -> toast("$n locked action${if (n == 1) "" else "s"} expired", "Yesterday ended below 80% of your step goal.") }
        lastLocked = s.lockedActions
        val prev = s.onChanged
        s.onChanged = {
            prev?.invoke()
            val locked = s.lockedActions
            if (locked > lastLocked) toast("Action earned, locked", "Walk ${n(s.stepsToUnlock)} more steps today (80% of your goal) to unlock it.")
            lastLocked = locked
        }
    }

    fun toast(title: String, body: String? = null) { toasts += Toast(toastId++, title, body) }

    fun syncSteps() {
        runCatching { session.syncSteps(source) }.onFailure { Log.w("StepQuest", "sync failed", it) }
    }

    fun onResume() {
        StepSampler.startLive(app)
        val main = Handler(Looper.getMainLooper())
        Thread {
            StepSampler.sampleOnce(app, 4000)
            main.post { syncSteps(); Notifier.onGameOpened(app, session.ledger.bankedActions) }
        }.start()
    }

    fun onPause() {
        save()
        StepSampler.stopLive(app)
    }

    fun refresh() { version++ }

    @Synchronized fun save() {
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(SaveCodec.encode(session.data))
            tmp.renameTo(file)
        }.onFailure { Log.e("StepQuest", "save failed", it) }
        pushSnapshot()
    }

    private fun pushSnapshot() {
        val s = session
        val l = s.ledger
        Snapshot(l.lastSyncMillis, l.bankedActions, l.stepRemainder, s.todaySteps,
            Snapshot.startOfDay(System.currentTimeMillis()), s.streak.current, s.data.settings.notificationsEnabled,
            l.unlockThreshold, s.lockedActions).save(app)
        StepWidgetProvider.refreshAll(app)
    }

    fun reset() {
        val debug = session.data.settings.debugTools
        file.delete()
        bind(GameSession(SaveData.newGame(SystemClock.nowMillis()).also { it.settings.debugTools = debug }, SystemClock))
        save()
        version++
    }
}
