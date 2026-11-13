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
        prefs.edit().putInt("streak_count", streak).apply()
    }
}
