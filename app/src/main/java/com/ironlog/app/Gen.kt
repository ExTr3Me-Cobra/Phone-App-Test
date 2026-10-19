package com.ironlog.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Makes AI pictures for a list of exercises in the background (keeps going with the app closed),
 * with a progress notification and a Stop button. The list is saved, so it carries on after a
 * restart, and pictures already made in this run are skipped.
 */
object Gen {
    data class State(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val failed: Int = 0,
        val current: String = "",
        val label: String = "",
        val message: String? = null,
    )

    internal val _state = MutableStateFlow(State())
    val state: StateFlow<State> get() = _state

    private const val PREFS = "gen_queue"

    fun start(c: Context, ids: List<String>, replace: Boolean, label: String) {
        if (ids.isEmpty()) return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ids", ids.joinToString("\n"))
            .putBoolean("replace", replace)
            .putLong("since", System.currentTimeMillis())
            .putString("label", label)
            .apply()
        _state.value = State(running = true, total = ids.size, label = label)
        c.startForegroundService(Intent(c, GenService::class.java))
    }

    fun stop(c: Context) {
        c.startService(Intent(c, GenService::class.java).setAction(GenService.STOP))
    }

    internal class Queue(val ids: List<String>, val replace: Boolean, val since: Long, val label: String)

    internal fun queue(c: Context): Queue? {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = p.getString("ids", null)?.split("\n")?.filter { it.isNotBlank() } ?: return null
        if (ids.isEmpty()) return null
        return Queue(ids, p.getBoolean("replace", false), p.getLong("since", 0), p.getString("label", "") ?: "")
    }

    internal fun clear(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()

    /** Already has a picture this run doesn't need to make. */
    internal fun skip(id: String, q: Queue): Boolean {
        val f = Store.imageFile(id)
        if (!f.exists()) return false
        return !q.replace || f.lastModified() >= q.since
    }
}

class GenService : Service() {
    companion object {
        const val STOP = "stop"
        private const val CHANNEL = "ai_pictures"
        private const val ID = 31
        private const val WORKERS = 2
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    @Volatile private var stopping = false
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.load(applicationContext)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Making AI pictures", NotificationManager.IMPORTANCE_LOW),
        )
        runCatching { startForeground(ID, note(Gen.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
        if (intent?.action == STOP) {
            stopping = true
            Gen.clear(this)
            Gen._state.value = Gen.state.value.copy(running = false, message = "Stopped. ${Gen.state.value.done} pictures made.")
            job?.cancel()
            finish()
            return START_NOT_STICKY
        }
        if (job == null) job = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val q = Gen.queue(this) ?: return finish()
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ironlog:pictures")
            .apply { acquire(12 * 3600 * 1000L) }
        val todo = q.ids.filter { !Gen.skip(it, q) }
        val already = q.ids.size - todo.size
        Gen._state.value = Gen.State(running = true, done = already, total = q.ids.size, label = q.label)
        update()
        val lock = Mutex()
        var next = 0
        var fatal: String? = null
        val workers = (1..WORKERS).map {
            scope.launch {
                while (!stopping && fatal == null) {
                    val id = lock.withLock { todo.getOrNull(next++) } ?: break
                    val e = Store.exercise(id) ?: continue
                    Gen._state.value = Gen.state.value.copy(current = e.name)
                    update()
                    var tries = 0
                    while (true) {
                        val r = Pictures.generate(e, Store.profile.value)
                        if (r.ok) {
                            Gen._state.value = Gen.state.value.copy(done = Gen.state.value.done + 1)
                            break
                        }
                        if (r.fatal) { fatal = r.message; break }
                        if ((r.rateLimited || r.code == 0 || r.code >= 500) && tries < 6) {
                            tries++
                            Gen._state.value = Gen.state.value.copy(message = "Waiting a moment (OpenAI busy or rate limit)…")
                            delay(20_000L * tries)
                            continue
                        }
                        Gen._state.value = Gen.state.value.copy(failed = Gen.state.value.failed + 1, message = "Skipped ${e.name}: ${r.message}")
                        break
                    }
                    update()
                }
            }
        }
        workers.joinAll()
        val s = Gen.state.value
        Gen._state.value = s.copy(
            running = false, current = "",
            message = fatal?.let { "Stopped: $it" }
                ?: "Finished: ${s.done} of ${s.total} pictures" + if (s.failed > 0) " (${s.failed} couldn't be made – run it again to retry them)." else ".",
        )
        if (fatal == null) Gen.clear(this)
        finish()
    }

    private fun update() {
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, note(Gen.state.value)) }
    }

    private fun note(s: Gen.State): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, GenService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Making AI pictures ${s.done}/${s.total}")
            .setContentText(s.current.ifBlank { s.label })
            .setProgress(s.total.coerceAtLeast(1), s.done, s.total == 0)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun finish() {
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}

