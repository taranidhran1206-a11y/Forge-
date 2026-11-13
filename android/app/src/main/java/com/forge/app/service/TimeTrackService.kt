package com.forge.app.service

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
import androidx.core.app.NotificationCompat
import com.forge.app.MainActivity
import com.forge.app.R
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimeTrackService : Service() {

    companion object {
        const val CHANNEL_ID = "forge_time_tracking"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.forge.app.STOP_TRACKING"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_TRACK_ID = "track_id"

        fun start(context: Context, category: String, trackId: Long) {
            val intent = Intent(context, TimeTrackService::class.java).apply {
                putExtra(EXTRA_CATEGORY, category)
                putExtra(EXTRA_TRACK_ID, trackId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimeTrackService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var category = ""
    private var trackId = 0L
    private lateinit var repository: ForgeRepository

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 60_000) // update every minute
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val db = ForgeDatabase.getDatabase(applicationContext)
        repository = ForgeRepository(db)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            stopSelf()
            return START_NOT_STICKY
        }

        category = intent?.getStringExtra(EXTRA_CATEGORY) ?: "other"
        trackId = intent?.getLongExtra(EXTRA_TRACK_ID, 0L) ?: 0L
        startTime = System.currentTimeMillis()

        val notification = buildNotification(0)
        startForeground(NOTIFICATION_ID, notification)
        handler.post(updateRunnable)

        // Write to SharedPreferences for widget
        val prefs = getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_tracking", true)
            .putString("tracking_category", category)
            .putLong("tracking_start", startTime)
            .apply()

        return START_STICKY
    }

    private fun stopTracking() {
        handler.removeCallbacks(updateRunnable)

        val endTime = System.currentTimeMillis()
        val durationMinutes = ((endTime - startTime) / 60000).toInt()

        serviceScope.launch {
            repository.stopTracking(trackId, endTime, durationMinutes)
        }

        val prefs = getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_tracking", false)
            .remove("tracking_category")
            .remove("tracking_start")
            .apply()
    }

    private fun updateNotification() {
        val elapsed = System.currentTimeMillis() - startTime
        val minutes = (elapsed / 60000).toInt()
        val notification = buildNotification(minutes)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(minutes: Int): Notification {
        val hours = minutes / 60
        val mins = minutes % 60
        val timeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        val categoryDisplay = formatCategory(category)

        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TimeTrackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Forge — $categoryDisplay")
            .setContentText("Tracking: $timeText")
            .setSmallIcon(R.drawable.ic_timer)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_stop, "Stop", stopPending)
            .setColor(0xFFE53935.toInt())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Time Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active time tracking session"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun formatCategory(cat: String): String {
        return when (cat) {
            "deep_work" -> "Deep Work"
            "admin" -> "Admin"
            "social_media" -> "Social Media"
            "youtube" -> "YouTube"
            "exercise" -> "Exercise"
            "rest" -> "Rest"
            "learning" -> "Learning"
            "other" -> "Other"
            else -> cat
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }
}
