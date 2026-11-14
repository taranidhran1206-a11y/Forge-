package com.forge.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.forge.app.SplashActivity
import com.forge.app.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodayPlanWidget : AppWidgetProvider() {

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, TodayPlanWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayPlanWidget::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_today_plan)
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)

        // Date
        val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        views.setTextViewText(R.id.tv_widget_date, dateFormat.format(Date()))

        // Open app on tap
        val intent = Intent(context, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_today_root, pendingIntent)

        // Labors
        val laborsJson = prefs.getString("labors_json", "[]") ?: "[]"
        try {
            val type = object : TypeToken<List<WidgetLabor>>() {}.type
            val labors: List<WidgetLabor> = Gson().fromJson(laborsJson, type)

            val laborViews = listOf(
                Triple(R.id.ll_labor_1, R.id.iv_check_1, R.id.tv_labor_name_1),
                Triple(R.id.ll_labor_2, R.id.iv_check_2, R.id.tv_labor_name_2),
                Triple(R.id.ll_labor_3, R.id.iv_check_3, R.id.tv_labor_name_3),
                Triple(R.id.ll_labor_4, R.id.iv_check_4, R.id.tv_labor_name_4),
                Triple(R.id.ll_labor_5, R.id.iv_check_5, R.id.tv_labor_name_5),
                Triple(R.id.ll_labor_6, R.id.iv_check_6, R.id.tv_labor_name_6)
            )

            laborViews.forEachIndexed { index, (rowId, checkId, nameId) ->
                if (index < labors.size) {
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setImageViewResource(
                        checkId,
                        if (labors[index].done) R.drawable.widget_check_done else R.drawable.widget_check_pending
                    )
                    views.setTextViewText(nameId, labors[index].name)
                    // Dim completed items
                    views.setTextColor(nameId, if (labors[index].done) 0xFF666666.toInt() else 0xFFE8E8E8.toInt())
                } else {
                    views.setViewVisibility(rowId, View.GONE)
                }
            }

            // Progress + count badge
            val done = labors.count { it.done }
            val total = labors.size
            val percent = if (total > 0) (done * 100 / total) else 0
            views.setProgressBar(R.id.pb_widget_progress, 100, percent, false)

            // Green count badge "X/Y" format like video
            if (total > 0) {
                views.setTextViewText(R.id.tv_widget_count, "$done/$total")
            } else {
                views.setTextViewText(R.id.tv_widget_count, "")
            }

            // "All done - nice work" message
            if (total > 0 && done == total) {
                views.setViewVisibility(R.id.tv_widget_all_done, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.tv_widget_all_done, View.GONE)
            }
        } catch (_: Exception) {
            // No data available
        }

        manager.updateAppWidget(widgetId, views)
    }

    data class WidgetLabor(val name: String, val done: Boolean)
}
