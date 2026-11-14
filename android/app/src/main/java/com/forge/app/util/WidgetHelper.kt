package com.forge.app.util

import android.content.Context
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.Labor
import com.forge.app.data.model.RankSystem
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import com.forge.app.ForgeStreakWidgetProvider
import com.forge.app.widget.RankWidget
import com.forge.app.widget.TodayPlanWidget
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object WidgetHelper {

    suspend fun updateAllWidgets(context: Context, repository: ForgeRepository) {
        withContext(Dispatchers.IO) {
            val today = repository.getTodayDate()

            // Update labors widget data
            val laborsDao = repository.getLaborsForDateSync(today)
            writeLaborsJson(context, laborsDao)

            // Update rank widget data
            val totalXp = repository.getTotalXpSync()
            val rank = RankSystem.getRank(totalXp)
            val progress = RankSystem.getProgress(totalXp)
            writeRankData(context, rank.title, totalXp, progress, rank.colorHex)

            // Update streak widget data
            val streak = repository.calculateStreak()
            writeStreakData(context, streak)

            // Update weekly streak grid data
            writeWeeklyStreakData(context, repository)
        }

        // Trigger widget updates on main thread
        TodayPlanWidget.triggerUpdate(context)
        RankWidget.triggerUpdate(context)
        triggerStreakWidgetUpdate(context)
    }

    fun writeLaborsJson(context: Context, labors: List<Labor>) {
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        val widgetLabors = labors.map {
            mapOf("name" to "${it.emoji} ${it.name}", "done" to it.done)
        }
        prefs.edit().putString("labors_json", Gson().toJson(widgetLabors)).apply()
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

    private fun triggerStreakWidgetUpdate(context: Context) {
        val intent = Intent(context, ForgeStreakWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ForgeStreakWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }

    fun writeStreakData(context: Context, streak: Int) {
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("streak_count", streak)
            .putInt("streak", streak)
            .apply()
    }

    /**
     * Compute the weekly streak grid data:
     * For each day Mon-Sun of the current week, check if the user:
     * - Did focus (focusMinutes > 0 in DailyStats)
     * - Completed labors (laborsCompleted > 0 in DailyStats)
     * - Wrote a journal/reflection
     * - Used Rize time tracking (timeTracked > 0)
     */
    private suspend fun writeWeeklyStreakData(context: Context, repository: ForgeRepository) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        // Go to Monday of this week
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val diff = if (dayOfWeek == Calendar.SUNDAY) -6 else (Calendar.MONDAY - dayOfWeek)
        cal.add(Calendar.DAY_OF_YEAR, diff)
        val mondayDate = cal.time

        val focusDays = JSONArray()
        val laborsDays = JSONArray()
        val journalDays = JSONArray()
        val rizeDays = JSONArray()

        val dateRangeFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val startStr = dateRangeFormat.format(cal.time)

        for (i in 0..6) {
            val dateStr = sdf.format(cal.time)
            val stats = repository.getStats(dateStr)
            val reflection = repository.getReflectionForDate(dateStr)
            val timeMinutes = repository.getTotalMinutesForDate(dateStr)

            focusDays.put(stats != null && stats.focusMinutes > 0)
            laborsDays.put(stats != null && stats.laborsCompleted > 0)
            journalDays.put(reflection != null)
            rizeDays.put(timeMinutes > 0)

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Date range for header e.g. "Nov 11-17"
        cal.add(Calendar.DAY_OF_YEAR, -1) // back to Sunday
        val endStr = dateRangeFormat.format(cal.time)
        val dateRange = "$startStr–$endStr"

        val weeklyJson = JSONObject().apply {
            put("focus", focusDays)
            put("labors", laborsDays)
            put("journal", journalDays)
            put("rize", rizeDays)
        }

        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("weeklyStreakJson", weeklyJson.toString())
            .putString("streakDateRange", dateRange)
            .apply()
    }
}
