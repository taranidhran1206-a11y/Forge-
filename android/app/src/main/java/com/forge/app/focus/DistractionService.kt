package com.forge.app.focus

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.forge.app.BuildConfig
import com.forge.app.MainActivity
import com.forge.app.R
import org.json.JSONArray

/**
 * All-day distraction watch, in the spirit of Rize. Every few seconds it asks
 * Android which app is in front (needs Usage access). Time in distracting apps
 * is counted per app, and after a stretch in one it posts a nudge.
 *
 * During a focus session FocusService already nudges for leaving Forge, so this
 * service only counts time then and does not nudge.
 */
class DistractionService : Service() {

    companion object {
        const val CHANNEL_WATCH = "forge_distraction_watch"
        const val CHANNEL_NUDGE = "forge_distraction_nudge"
        const val NOTIF_WATCH = 2100
        const val NOTIF_NUDGE = 2101

        private const val ACTION_SNOOZE = "com.forge.app.distraction.SNOOZE"
        private const val ACTION_STOP = "com.forge.app.distraction.STOP"

        private const val POLL_MS = 3000L
        private const val FLUSH_EVERY_POLLS = 5

        fun start(context: Context) {
            if (!Distraction.isEnabled(context) || !Distraction.hasUsageAccess(context)) return
            val intent = Intent(context, DistractionService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (_: Exception) {
                // Background start refused; it starts next time the app opens
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DistractionService::class.java))
        }
    }

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var usage: UsageStatsManager
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private lateinit var notifications: NotificationManager

    private var currentPkg: String? = null
    private var lastQueryAt = 0L
    private var streakPkg: String? = null
    private var streakSeconds = 0
    private var sinceNudgeSeconds = Int.MAX_VALUE / 2
    private var nudgesInStreak = 0
    private var polls = 0
    private val pending = mutableMapOf<String, Int>()
    private var pendingNudges = 0

    private val loop = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (e: Exception) {
                android.util.Log.w("ForgeDistraction", "tick failed", e)
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
        thread = HandlerThread("forge-distraction").apply { start() }
        handler = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIF_WATCH, buildWatchNotification())
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                Distraction.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                Distraction.snooze(this, 15)
                notifications.cancel(NOTIF_NUDGE)
            }
        }
        if (!Distraction.isEnabled(this) || !Distraction.hasUsageAccess(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(loop)
        handler.post(loop)
        return START_STICKY
    }

    private fun foregroundPackage(): String? {
        val now = System.currentTimeMillis()
        val begin = if (lastQueryAt == 0L) now - 6 * 60 * 60 * 1000L else lastQueryAt - 1000L
        val events = usage.queryEvents(begin, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                1 -> currentPkg = event.packageName // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                2 -> if (event.packageName == currentPkg) currentPkg = null // ACTIVITY_PAUSED
            }
        }
        lastQueryAt = now
        return currentPkg
    }

    private fun tick() {
        val seconds = (POLL_MS / 1000).toInt()
        val screenInUse = power.isInteractive && !keyguard.isKeyguardLocked
        val pkg = foregroundPackage()
        val distracting = screenInUse && pkg != null && Distraction.isDistracting(this, pkg)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("ForgeDistraction", "front=$pkg inUse=$screenInUse distracting=$distracting streak=$streakSeconds")
        }

        Distraction.currentAppLabel = if (screenInUse && pkg != null && pkg != packageName) {
            Distraction.label(this, pkg)
        } else null

        if (distracting) {
            pending[pkg!!] = (pending[pkg] ?: 0) + seconds
            if (pkg != streakPkg) {
                streakPkg = pkg
                streakSeconds = 0
                nudgesInStreak = 0
                sinceNudgeSeconds = Int.MAX_VALUE / 2
            }
            streakSeconds += seconds
            sinceNudgeSeconds += seconds
            maybeNudge(pkg)
        } else {
            if (streakPkg != null) notifications.cancel(NOTIF_NUDGE)
            streakPkg = null
            streakSeconds = 0
        }

        polls++
        if (polls % FLUSH_EVERY_POLLS == 0) flush()
    }

    private fun thresholdSeconds(): Int = Distraction.thresholdMinutes(this) * 60

    private fun maybeNudge(pkg: String) {
        val focus = FocusState.live.value
        if (focus != null && focus.active) return // FocusService handles nudges in a session
        if (System.currentTimeMillis() < Distraction.snoozedUntil(this)) return
        val threshold = thresholdSeconds()
        if (streakSeconds < threshold || sinceNudgeSeconds < threshold) return

        sinceNudgeSeconds = 0
        nudgesInStreak++
        pendingNudges++
        val label = Distraction.label(this, pkg)
        val todaySeconds = (Distraction.today(this).secondsByApp[pkg] ?: 0) + (pending[pkg] ?: 0)
        val next = nextOpenLabor()
        val title = when (nudgesInStreak) {
            1 -> "${Distraction.minutesText(streakSeconds)} on $label"
            2 -> "Still on $label. ${Distraction.minutesText(streakSeconds)} now."
            else -> "$label has had ${Distraction.minutesText(streakSeconds)}. Close it."
        }
        val text = buildString {
            append("${Distraction.minutesText(todaySeconds)} on $label today.")
            if (next != null) append(" Next up: $next.")
        }

        val nudge = NotificationCompat.Builder(this, CHANNEL_NUDGE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFE53935.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVibrate(longArrayOf(0, 180, 120, 180))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Back to Forge", openAppIntent())
            .addAction(0, "Snooze 15 min", serviceIntent(ACTION_SNOOZE, 31))
            .build()
        try {
            notifications.notify(NOTIF_NUDGE, nudge)
        } catch (_: Exception) {
        }
    }

    private fun nextOpenLabor(): String? = try {
        val prefs = getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("labors_json", "[]"))
        var name: String? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!o.optBoolean("done", false)) {
                name = o.optString("name"); break
            }
        }
        name?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun flush() {
        Distraction.add(this, pending.toMap(), pendingNudges)
        pending.clear()
        pendingNudges = 0
        try {
            notifications.notify(NOTIF_WATCH, buildWatchNotification())
        } catch (_: Exception) {
        }
        Distraction.live.postValue(System.currentTimeMillis())
    }

    private fun buildWatchNotification(): Notification {
        val today = Distraction.today(this)
        val top = today.secondsByApp.maxByOrNull { it.value }
        val text = if (today.totalSeconds == 0) "No distracting apps yet today"
        else "${Distraction.minutesText(today.totalSeconds)} distracted today" +
            (top?.let { " · most on ${Distraction.label(this, it.key)}" } ?: "")
        return NotificationCompat.Builder(this, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle("Time tracker is on")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent())
            .addAction(0, "Turn off", serviceIntent(ACTION_STOP, 32))
            .build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(this, 30, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, DistractionService::class.java).apply { this.action = action }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val watch = NotificationChannel(CHANNEL_WATCH, "Time tracker", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Quiet notice that Forge is watching for distracting apps"
            setShowBadge(false)
        }
        val nudge = NotificationChannel(CHANNEL_NUDGE, "Distraction nudges", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Nudges when you spend too long in a distracting app"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 120, 180)
        }
        notifications.createNotificationChannel(watch)
        notifications.createNotificationChannel(nudge)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        try {
            flush()
        } catch (_: Exception) {
        }
        Distraction.currentAppLabel = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        thread.quitSafely()
        super.onDestroy()
    }
}
