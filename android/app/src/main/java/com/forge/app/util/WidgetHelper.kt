package com.forge.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.forge.app.ForgeStreakWidgetProvider
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.Labor
import com.forge.app.data.model.RankSystem
import com.forge.app.widget.RankWidget
import com.forge.app.widget.TodayPlanWidget
import com.forge.app.widget.WidgetRefreshReceiver
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Keeps the home screen widgets in step with the database. Widgets are redrawn
 * by calling AppWidgetManager directly (never by broadcasting to ourselves), so
 * a refresh can be started from a widget's own onUpdate without looping.
 */
object WidgetHelper {

    /** Rewrite every widget from the database and redraw them. Safe from any thread. */
    suspend fun updateAllWidgets(context: Context, repository: ForgeRepository) {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            rollDayIfNeeded(app, repository)
            val today = repository.getTodayDate()

            val labors = repository.getLaborsForDateSync(today)
            writeLaborsJson(app, labors)

            val totalXp = repository.getTotalXpSync()
            val rank = RankSystem.getRank(totalXp)
            writeRankData(app, rank.title, totalXp, RankSystem.getProgress(totalXp), rank.colorHex)

            writeStreakData(app, repository.calculateStreak(), labors.count { it.done }, labors.size)
        }
        pushAll(app)
        scheduleMidnightRefresh(app)
    }

    /**
     * Once per day, carry the last day's labors into today, so a widget looked at
     * after midnight shows today's list before the app has been opened.
     */
    private suspend fun rollDayIfNeeded(context: Context, repository: ForgeRepository) {
        val prefs = context.getSharedPreferences("forge_prefs", Context.MODE_PRIVATE)
        // First run is left to MainActivity, which also seeds the starter labors
        if (!prefs.getBoolean("labors_seeded", false)) return
        val today = repository.getTodayDate()
        if (prefs.getString("labors_rolled_day", "") == today) return
        try {
            repository.ensureTodayLabors()
            prefs.edit().putString("labors_rolled_day", today).apply()
        } catch (_: Exception) {
        }
    }

    /** Redraw every placed widget from what is stored in preferences. */
    fun pushAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        fun ids(cls: Class<*>) = manager.getAppWidgetIds(ComponentName(app, cls))
        try {
            ids(TodayPlanWidget::class.java).takeIf { it.isNotEmpty() }?.let {
                manager.updateAppWidget(it, TodayPlanWidget().buildViews(app))
            }
            ids(RankWidget::class.java).takeIf { it.isNotEmpty() }?.let {
                manager.updateAppWidget(it, RankWidget().buildViews(app))
            }
            ids(ForgeStreakWidgetProvider::class.java).takeIf { it.isNotEmpty() }?.let {
                manager.updateAppWidget(it, ForgeStreakWidgetProvider.buildViews(app))
            }
        } catch (_: Exception) {
        }
    }

    /** A refresh just after midnight, so the date, ticks and streak turn over on time. */
    fun scheduleMidnightRefresh(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(app, WidgetRefreshReceiver::class.java).setAction(WidgetRefreshReceiver.ACTION_MIDNIGHT)
        val pending = PendingIntent.getBroadcast(
            app, 40, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        } catch (_: Exception) {
        }
    }

    fun writeLaborsJson(context: Context, labors: List<Labor>) {
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        val widgetLabors = labors.map {
            mapOf("id" to it.id, "name" to it.name, "done" to it.done)
        }
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        prefs.edit()
            .putString("labors_json", Gson().toJson(widgetLabors))
            .putString("labors_date", today)
            .apply()
    }

    fun writeRankData(context: Context, rank: String, xp: Int, progress: Float, colorHex: String) {
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("rank_name", rank)
            .putInt("total_xp", xp)
            .putFloat("rank_progress", progress)
            .putString("rank_color", colorHex)
            .apply()
    }

    fun writeStreakData(context: Context, streak: Int, todayDone: Int = 0, todayTotal: Int = 0) {
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("streak_count", streak)
            .putInt("streak", streak)
            .putInt("todayDone", todayDone)
            .putInt("todayTotal", todayTotal)
            .apply()
    }
}
