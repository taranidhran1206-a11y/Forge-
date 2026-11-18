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
            com.forge.app.util.WidgetHelper.pushAll(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context))
        } catch (_: Exception) {
        }
        WidgetSyncOnUpdate.refresh(this, context)
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        manager.updateAppWidget(widgetId, buildViews(context))
    }

    /** Builds exactly what the launcher draws. Public so a device test can inflate it. */
    fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_rank)
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)

        val rankName = prefs.getString("rank_name", "IRON") ?: "IRON"
        val xp = prefs.getInt("total_xp", 0)
        val progress = prefs.getFloat("rank_progress", 0f)
        val rankColor = prefs.getString("rank_color", "#78909C") ?: "#78909C"

        val rank = com.forge.app.data.model.RankSystem.getRank(xp)
        val color = try { Color.parseColor(rankColor) } catch (_: Exception) { Color.WHITE }
        views.setTextViewText(R.id.tv_widget_level, "LEVEL ${rank.ordinal + 1}")
        views.setInt(R.id.iv_widget_hex, "setColorFilter", color)
        val next = com.forge.app.data.model.RankSystem.getNextRank(rank)
        views.setTextViewText(
            R.id.tv_widget_next,
            if (next != null) {
                val name = next.title.lowercase().replaceFirstChar { it.uppercase() }
                "${String.format("%,d", (next.minXp - xp).coerceAtLeast(0))} to $name"
            } else "Top rank"
        )

        // Set rank name with color
        views.setTextViewText(R.id.tv_widget_rank_name, rankName)
        try {
            views.setTextColor(R.id.tv_widget_rank_name, Color.parseColor(rankColor))
        } catch (_: Exception) {
            views.setTextColor(R.id.tv_widget_rank_name, Color.WHITE)
        }

        // Format XP nicely
        val xpText = String.format("%,d XP", xp)
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

        return views
    }
}
