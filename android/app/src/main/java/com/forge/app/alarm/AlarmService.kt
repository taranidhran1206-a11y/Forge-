package com.forge.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.forge.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plays the alarm, loud and looping, until the challenge is done. Gives up after 15 minutes. */
class AlarmService : Service() {

    companion object {
        const val CHANNEL = "forge_alarm"
        const val NOTIFICATION_ID = 7410
        private const val GIVE_UP_MS = 15 * 60_000L
        @Volatile var ringingId: Int = -1
            private set

        fun stop(c: Context) {
            c.stopService(Intent(c, AlarmService::class.java))
        }

        fun ensureChannel(c: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) != null) return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Wake-up alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarms you switch off with push-ups, squats or sums"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(AlarmStore.EXTRA_ID, -1) ?: -1
        ensureChannel(this)
        val alarm = AlarmStore.get(this, id)
        val challenge = Intent(this, AlarmActivity::class.java)
            .putExtra(AlarmStore.EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val full = PendingIntent.getActivity(this, 7411, challenge, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(alarm?.label?.ifBlank { null } ?: "Wake up · $time")
            .setContentText("Do ${alarm?.challengeText ?: "the challenge"} to switch it off")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .addAction(0, "Start", full)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        ringingId = id
        startSound()
        // On some phones the full-screen intent only shows a heads-up while the screen is on; try to open it too.
        try { startActivity(challenge) } catch (_: Exception) {}
        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, GIVE_UP_MS)
        return START_NOT_STICKY
    }

    private fun startSound() {
        if (player != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            player = null
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        try {
            val pattern = longArrayOf(0, 700, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            } else {
                @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        ringingId = -1
        super.onDestroy()
    }
}
