package com.forge.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Rings an alarm, and books every alarm again after a reboot, an update or a clock change. */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RING = "com.forge.app.alarm.RING"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RING -> {
                val id = intent.getIntExtra(AlarmStore.EXTRA_ID, -1)
                if (id < 0) return
                AlarmStore.afterRing(context, id)
                val ring = Intent(context, AlarmService::class.java).putExtra(AlarmStore.EXTRA_ID, id)
                try {
                    ContextCompat.startForegroundService(context, ring)
                } catch (_: Exception) {
                    // Couldn't start the ringer: at least show the challenge.
                    context.startActivity(
                        Intent(context, AlarmActivity::class.java).putExtra(AlarmStore.EXTRA_ID, id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> AlarmStore.rescheduleAll(context)
        }
    }
}
