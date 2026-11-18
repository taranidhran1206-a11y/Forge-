package com.forge.app.focus

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.forge.app.MainActivity
import com.forge.app.R
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.DailyStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs a focus session in the foreground so it survives leaving the app, and
 * nudges with a notification when the phone is being used for something else.
 *
 * "Away" means: a session is running, the screen is on and unlocked, and no
 * Forge screen is visible. A locked or dark screen does not count.
 */
class FocusService : Service() {

    companion object {
        const val CHANNEL_SESSION = "forge_focus_session"
        const val CHANNEL_NUDGE = "forge_focus_nudge"
        private const val NOTIF_SESSION = 2001
        private const val NOTIF_NUDGE = 2002
        private const val NOTIF_DONE = 2003

        const val ACTION_START = "com.forge.app.focus.START"
        const val ACTION_PAUSE = "com.forge.app.focus.PAUSE"
        const val ACTION_RESUME = "com.forge.app.focus.RESUME"
        const val ACTION_STOP = "com.forge.app.focus.STOP"
        const val ACTION_ADD_FIVE = "com.forge.app.focus.ADD_FIVE"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_LABEL = "label"

        /** Seconds away before it counts as a drift. */
        private const val DRIFT_SECONDS = 5
        /** Seconds away before the first nudge. */
        private const val GRACE_SECONDS = 15
        /** Seconds between repeat nudges while still away. */
        private const val NUDGE_GAP_SECONDS = 60

        private val NUDGE_LINES = listOf(
            "Back to the forge.",
            "This is the drift. Close it and come back.",
            "The session is still running. So should you.",
            "Hold the line.",
            "Whatever that is, it can wait."
        )

        fun start(context: Context, minutes: Int, label: String) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MINUTES, minutes)
                putExtra(EXTRA_LABEL, label)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun send(context: Context, action: String) {
            val active = FocusState.live.value?.active ?: false
            if (!active) return
            try {
                context.startService(Intent(context, FocusService::class.java).apply { this.action = action })
            } catch (_: Exception) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ForgeRepository
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var active = false
    private var running = false
    private var label = ""
    private var totalSeconds = 0
    private var remainingMs = 0L
    private var endAtElapsed = 0L
    private var elapsedSeconds = 0
    private var awayStreak = 0
    private var sinceLastNudge = NUDGE_GAP_SECONDS
    private var nudgeIndex = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            if (running) {
                val left = endAtElapsed - SystemClock.elapsedRealtime()
                if (left <= 0L) {
                    complete()
                    return
                }
                remainingMs = left
                elapsedSeconds++
                trackAway()
            }
            publish()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = ForgeRepository(ForgeDatabase.getDatabase(applicationContext))
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(
                (intent?.getIntExtra(EXTRA_MINUTES, 25) ?: 25).coerceIn(1, 240),
                intent?.getStringExtra(EXTRA_LABEL) ?: ""
            )
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_ADD_FIVE -> addFive()
            ACTION_STOP -> stopEarly()
            else -> if (!active) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startSession(minutes: Int, sessionLabel: String) {
        label = sessionLabel
        totalSeconds = minutes * 60
        remainingMs = totalSeconds * 1000L
        endAtElapsed = SystemClock.elapsedRealtime() + remainingMs
        elapsedSeconds = 0
        awayStreak = 0
        sinceLastNudge = NUDGE_GAP_SECONDS
        active = true
        running = true

        try {
            startForeground(NOTIF_SESSION, buildSessionNotification())
        } catch (_: Exception) {
            // Foreground start can be refused by the system; the timer still runs while the app is open.
        }
        acquireWakeLock()
        NudgeStore.add(this, logLine = "Session started · $minutes min")
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000L)
        publish()
    }

    private fun pauseSession() {
        if (!active || !running) return
        running = false
        remainingMs = (endAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        flushAway()
        releaseWakeLock()
        updateSessionNotification()
        publish()
    }

    private fun resumeSession() {
        if (!active || running) return
        running = true
        endAtElapsed = SystemClock.elapsedRealtime() + remainingMs
        acquireWakeLock()
        updateSessionNotification()
        publish()
    }

    private fun addFive() {
        if (!active) return
        totalSeconds += 300
        remainingMs += 300_000L
        if (running) endAtElapsed += 300_000L
        updateSessionNotification()
        publish()
    }

    private fun stopEarly() {
        if (!active) {
            stopSelf()
            return
        }
        flushAway()
        NudgeStore.add(
            this,
            sessions = if (elapsedSeconds >= 60) 1 else 0,
            focusSeconds = elapsedSeconds,
            logLine = "Session ended early after ${NudgeStore.formatDuration(elapsedSeconds)}"
        )
        finish()
    }

    private fun complete() {
        flushAway()
        val minutes = totalSeconds / 60
        NudgeStore.add(
            this,
            sessions = 1,
            focusSeconds = elapsedSeconds,
            logLine = "Session complete · $minutes min"
        )
        scope.launch {
            try {
                val today = repository.getTodayDate()
                val stats = repository.getStats(today) ?: DailyStats(date = today)
                repository.upsertStats(stats.copy(focusMinutes = stats.focusMinutes + minutes))
                FocusState.completedXp.postValue(minutes)
            } catch (_: Exception) {
            }
        }
        val day = NudgeStore.today(this)
        val done = NotificationCompat.Builder(this, CHANNEL_NUDGE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Focus session complete")
            .setContentText("$minutes min done · ${day.nudges} nudges today · ${scoreText(day)} focused")
            .setColor(0xFF4CAF50.toInt())
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notifySafely(NOTIF_DONE, done)
        finish()
    }

    private fun finish() {
        active = false
        running = false
        handler.removeCallbacks(tick)
        releaseWakeLock()
        notificationManager.cancel(NOTIF_NUDGE)
        val idleSeconds = (totalSeconds.coerceAtLeast(60) / 300).coerceAtLeast(1) * 300
        FocusState.live.postValue(
            FocusState.Snapshot(totalSeconds = idleSeconds, remainingSeconds = idleSeconds)
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Away detection and nudges ---

    private fun isAway(): Boolean {
        return !FocusState.appInForeground &&
            powerManager.isInteractive &&
            !keyguardManager.isKeyguardLocked
    }

    private fun trackAway() {
        if (isAway()) {
            awayStreak++
            sinceLastNudge++
            if (awayStreak == DRIFT_SECONDS) {
                NudgeStore.add(this, drifts = 1)
            }
            if (awayStreak >= GRACE_SECONDS && sinceLastNudge >= NUDGE_GAP_SECONDS) {
                sendNudge()
            }
        } else {
            flushAway()
        }
    }

    private fun flushAway() {
        if (awayStreak >= DRIFT_SECONDS) {
            NudgeStore.add(
                this,
                awaySeconds = awayStreak,
                logLine = "Back after ${NudgeStore.formatDuration(awayStreak)} away"
            )
            notificationManager.cancel(NOTIF_NUDGE)
        }
        awayStreak = 0
        sinceLastNudge = NUDGE_GAP_SECONDS
    }

    private fun sendNudge() {
        sinceLastNudge = 0
        val minutesLeft = ((remainingMs + 59_999L) / 60_000L).toInt()
        val line = NUDGE_LINES[nudgeIndex % NUDGE_LINES.size]
        nudgeIndex++
        NudgeStore.add(this, nudges = 1, logLine = "Nudge sent" + (Distraction.currentAppLabel?.let { " · on $it" } ?: "") + " · $minutesLeft min left")

        val nudge = NotificationCompat.Builder(this, CHANNEL_NUDGE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(line)
            .setContentText(
                (Distraction.currentAppLabel?.let { "On $it. " } ?: "") +
                    "$minutesLeft min left in your focus session. Away for ${NudgeStore.formatDuration(awayStreak)}."
            )
            .setColor(0xFFE53935.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(longArrayOf(0, 180, 120, 180))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Back to focus", openAppIntent())
            .addAction(0, "End session", serviceIntent(ACTION_STOP, 14))
            .build()
        notifySafely(NOTIF_NUDGE, nudge)
    }

    // --- Notifications ---

    private fun buildSessionNotification(): Notification {
        val title = if (label.isNotBlank()) "Focus · $label" else "Focus session"
        val builder = NotificationCompat.Builder(this, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFE53935.toInt())
            .setContentIntent(openAppIntent())

        if (running) {
            builder.setContentText("Shield wall is up. Leaving Forge gets you a nudge.")
                .setWhen(System.currentTimeMillis() + remainingMs)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(0, "Pause", serviceIntent(ACTION_PAUSE, 11))
        } else {
            val mins = (remainingMs / 60_000L).toInt()
            val secs = ((remainingMs / 1000L) % 60).toInt()
            builder.setContentText(String.format("Paused · %02d:%02d left", mins, secs))
                .setShowWhen(false)
                .addAction(0, "Resume", serviceIntent(ACTION_RESUME, 12))
        }
        builder.addAction(0, "End", serviceIntent(ACTION_STOP, 13))
        return builder.build()
    }

    private fun updateSessionNotification() {
        notifySafely(NOTIF_SESSION, buildSessionNotification())
    }

    private fun notifySafely(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (_: Exception) {
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 10, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, FocusService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val session = NotificationChannel(
                CHANNEL_SESSION, "Focus session", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the running focus timer"
                setShowBadge(false)
            }
            val nudge = NotificationChannel(
                CHANNEL_NUDGE, "Focus nudges", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Nudges you back when you leave Forge during a focus session"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 120, 180)
            }
            notificationManager.createNotificationChannel(session)
            notificationManager.createNotificationChannel(nudge)
        }
    }

    // --- Helpers ---

    private fun publish() {
        FocusState.live.postValue(
            FocusState.Snapshot(
                active = active,
                running = running,
                label = label,
                totalSeconds = totalSeconds,
                remainingSeconds = ((remainingMs + 999L) / 1000L).toInt(),
                elapsedSeconds = elapsedSeconds,
                currentAwaySeconds = if (awayStreak >= DRIFT_SECONDS) awayStreak else 0
            )
        )
    }

    private fun scoreText(day: NudgeStore.Day): String {
        if (day.focusSeconds <= 0) return "100%"
        val pct = 100 - (day.awaySeconds * 100 / day.focusSeconds)
        return "${pct.coerceIn(0, 100)}%"
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "forge:focus")
            }
            wakeLock?.let { if (!it.isHeld) it.acquire(remainingMs + 60_000L) }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        releaseWakeLock()
        super.onDestroy()
    }
}
