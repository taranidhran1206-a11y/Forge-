package com.forge.app.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.forge.app.MainActivity
import com.forge.app.R
import com.forge.app.widget.TodayPlanWidget
import com.forge.app.widget.WidgetRefreshReceiver
import java.util.Calendar

/**
 * "Today's plan" on the lock screen: how much of the day has passed and the next
 * labors to do, always in front of you. While a focus session runs, the session
 * notification takes its place. On Android 16+ both ask to be shown as a Live Update
 * (Samsung's Now Bar). Turn it off in Android's notification settings for Forge.
 */
object PlanCard {

    private const val CHANNEL = "forge_plan_card"
    private const val NOTIF_ID = 2200
    /** Android 16's Notification.EXTRA_REQUEST_PROMOTED_ONGOING, by name so we can build on SDK 34. */
    const val EXTRA_PROMOTE = "android.requestPromotedOngoing"

    @Volatile
    var focusActive = false

    fun promoteExtras(): Bundle = Bundle().apply { putBoolean(EXTRA_PROMOTE, true) }

    /** Open labors for today, in order, from the same data the widgets use. */
    fun openLabors(context: Context): List<TodayPlanWidget.WidgetLabor> {
        return TodayPlanWidget.readLabors(context).filter { !it.done }
    }

    fun dayPercent(now: Calendar = Calendar.getInstance()): Int {
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return (minutes * 100 / (24 * 60)).coerceIn(0, 100)
    }

    fun update(context: Context) {
        val app = context.applicationContext
        val nm = NotificationManagerCompat.from(app)
        try {
            ensureChannel(app)
            scheduleTick(app)
            val all = TodayPlanWidget.readLabors(app)
            if (focusActive || all.isEmpty() || !nm.areNotificationsEnabled()) {
                nm.cancel(NOTIF_ID)
                return
            }
            val open = all.filter { !it.done }
            val done = all.size - open.size
            val pct = dayPercent()

            val title = if (open.isEmpty()) "All ${all.size} labors done today" else "Today's plan · $done/${all.size} done"
            val lines = if (open.isEmpty()) "Nothing left. Rest is earned."
                        else open.take(4).joinToString("\n") { "○  ${it.name}" } +
                             (if (open.size > 4) "\n+${open.size - 4} more" else "")

            val builder = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(title)
                .setContentText(if (open.isEmpty()) "Nothing left. Rest is earned." else "Next: ${open.first().name}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
                .setSubText("$pct% of the day has passed")
                .setProgress(100, pct, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openApp(app))
                .addExtras(promoteExtras())

            // Tick the next labor straight from the lock screen, like the widget does
            open.firstOrNull()?.takeIf { it.id > 0L }?.let { next ->
                val tick = Intent(app, TodayPlanWidget::class.java).apply {
                    action = TodayPlanWidget.ACTION_TOGGLE
                    data = android.net.Uri.parse("forge://plancard/${next.id}")
                    putExtra(TodayPlanWidget.EXTRA_LABOR_ID, next.id)
                }
                builder.addAction(0, "✓ ${next.name.take(24)}",
                    PendingIntent.getBroadcast(app, 2201, tick,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            nm.notify(NOTIF_ID, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 2202, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Today's plan (lock screen)", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Keeps today's plan and the day's progress on your lock screen"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    /** Keeps "% of the day" fresh about every 15 minutes while the phone is awake. */
    private fun scheduleTick(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(context, 2203,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(WidgetRefreshReceiver.ACTION_PLAN_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 15 * 60_000L,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi)
    }
}
