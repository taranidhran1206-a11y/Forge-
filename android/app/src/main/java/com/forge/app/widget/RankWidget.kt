package com.forge.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.forge.app.SplashActivity
import com.forge.app.R

class RankWidget : AppWidgetProvider() {

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, RankWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RankWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }

        private fun getRankEmoji(rank: String): String {
            return when (rank.uppercase()) {
                "IRON" -> "⚔️"
                "BRONZE" -> "🥉"
                "SILVER" -> "🥈"
                "GOLD" -> "🥇"
                "PLATINUM" -> "💎"
                "DIAMOND" -> "✨"
                "TITAN" -> "🔥"
                "LEGEND" -> "👑"
                else -> "⚔️"
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_rank)
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)

        val rankName = prefs.getString("rank_name", "IRON") ?: "IRON"
        val xp = prefs.getInt("total_xp", 0)
        val progress = prefs.getFloat("rank_progress", 0f)
        val rankColor = prefs.getString("rank_color", "#78909C") ?: "#78909C"

        // Set rank emoji
        views.setTextViewText(R.id.tv_widget_rank_emoji, getRankEmoji(rankName))

        // Set rank name with color
        views.setTextViewText(R.id.tv_widget_rank_name, rankName)
        try {
            views.setTextColor(R.id.tv_widget_rank_name, Color.parseColor(rankColor))
        } catch (_: Exception) {
            views.setTextColor(R.id.tv_widget_rank_name, Color.WHITE)
        }

        // Format XP nicely
        val xpText = if (xp >= 1000) {
            String.format("%.1fk XP", xp / 1000.0)
        } else {
            "$xp XP"
        }
        views.setTextViewText(R.id.tv_widget_xp, xpText)
        views.setProgressBar(R.id.pb_widget_rank, 100, (progress * 100).toInt(), false)

        // Open app on tap — use root layout for better hit target
        val intent = Intent(context, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_rank_root, pendingIntent)

        manager.updateAppWidget(widgetId, views)
    }
}
