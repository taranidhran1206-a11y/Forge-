package com.forge.app.ui.gym

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.forge.app.R
import com.forge.app.SplashActivity

/** Rest finished while Forge was in the background: a nudge to do the next set. */
class RestOverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (GymFragment.restUntil(context) == 0L) return
        GymFragment.setRestUntil(context, 0)
        val open = PendingIntent.getActivity(
            context, 7302, Intent(context, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, "forge_notifications")
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Rest's over")
            .setContentText("Time for your next set.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(7303, n)
        } catch (_: SecurityException) {
        }
    }
}
