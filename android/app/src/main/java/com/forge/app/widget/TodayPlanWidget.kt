package com.forge.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import com.forge.app.R
import com.forge.app.SplashActivity
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.util.WidgetHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodayPlanWidget : AppWidgetProvider() {

    companion object {
        private const val MAX_ROWS = 6
        const val ACTION_TOGGLE = "com.forge.app.widget.TOGGLE_LABOR"
        const val EXTRA_LABOR_ID = "labor_id"

        fun triggerUpdate(context: Context) {
            WidgetHelper.pushAll(context)
        }
    }

    data class WidgetLabor(val id: Long = 0, val name: String = "", val done: Boolean = false)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) {
            super.onReceive(context, intent)
            return
        }
        val laborId = intent.getLongExtra(EXTRA_LABOR_ID, -1L)
        if (laborId <= 0L) return

        // Show the tick straight away, then make it real in the database
        flipInPrefs(context, laborId)
        triggerUpdate(context)

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ForgeRepository(ForgeDatabase.getDatabase(appContext))
                repository.toggleLaborFromWidget(laborId)
                // Rewrites labors, streak and rank from the database and redraws every widget
                WidgetHelper.updateAllWidgets(appContext, repository)
            } catch (_: Exception) {
                WidgetHelper.pushAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private fun flipInPrefs(context: Context, laborId: Long) {
        try {
            val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val flipped = readLabors(context).map {
                mapOf("id" to it.id, "name" to it.name, "done" to (if (it.id == laborId) !it.done else it.done))
            }
            prefs.edit()
                .putString("labors_json", Gson().toJson(flipped))
                .putString("labors_date", today)
                .apply()
        } catch (_: Exception) {
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context))
        } catch (_: Exception) {
            // Never let one bad update take the widget down
        }
        WidgetSyncOnUpdate.refresh(this, context)
    }

    private fun readLabors(context: Context): List<WidgetLabor> {
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
        val stored: List<WidgetLabor> = try {
            val type = object : TypeToken<List<WidgetLabor>>() {}.type
            Gson().fromJson<List<WidgetLabor>>(prefs.getString("labors_json", "[]") ?: "[]", type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        // A new day starts unchecked even before the app is opened
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val sameDay = prefs.getString("labors_date", today) == today
        return if (sameDay) stored else stored.map { it.copy(done = false) }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        manager.updateAppWidget(widgetId, buildViews(context))
    }

    /** Builds exactly what the launcher draws. Public so a device test can inflate it. */
    fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today_plan)
        val prefs = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)

        views.setTextViewText(
            R.id.tv_widget_date,
            SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
        )

        val intent = Intent(context, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        views.setOnClickPendingIntent(
            R.id.widget_today_root,
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val labors = readLabors(context)
        val total = labors.size
        val done = labors.count { it.done }
        val allDone = total > 0 && done == total

        views.setTextViewText(R.id.tv_widget_count, if (total > 0) "$done/$total" else "")
        views.setProgressBar(R.id.pb_widget_progress, 100, if (total > 0) done * 100 / total else 0, false)
        views.setViewVisibility(R.id.tv_widget_empty, if (total == 0) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.ll_widget_all_done, if (allDone) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.ll_widget_rows, if (total > 0 && !allDone) View.VISIBLE else View.GONE)

        if (allDone) {
            val streak = prefs.getInt("streak", 0)
            views.setTextViewText(R.id.tv_widget_done_sub, "$done/$total labors · streak $streak")
        }

        val rows = listOf(
            intArrayOf(R.id.ll_labor_1, R.id.iv_check_1, R.id.tv_labor_name_1, R.id.tv_next_1),
            intArrayOf(R.id.ll_labor_2, R.id.iv_check_2, R.id.tv_labor_name_2, R.id.tv_next_2),
            intArrayOf(R.id.ll_labor_3, R.id.iv_check_3, R.id.tv_labor_name_3, R.id.tv_next_3),
            intArrayOf(R.id.ll_labor_4, R.id.iv_check_4, R.id.tv_labor_name_4, R.id.tv_next_4),
            intArrayOf(R.id.ll_labor_5, R.id.iv_check_5, R.id.tv_labor_name_5, R.id.tv_next_5),
            intArrayOf(R.id.ll_labor_6, R.id.iv_check_6, R.id.tv_labor_name_6, R.id.tv_next_6)
        )

        // Keep the plan in order, but slide the window so the next open labor is always visible
        val firstOpen = labors.indexOfFirst { !it.done }
        val start = if (total <= MAX_ROWS) 0 else (firstOpen - 1).coerceIn(0, total - MAX_ROWS)

        rows.forEachIndexed { index, ids ->
            val labor = labors.getOrNull(start + index)
            if (labor == null) {
                views.setViewVisibility(ids[0], View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(ids[0], View.VISIBLE)
            views.setImageViewResource(
                ids[1],
                if (labor.done) R.drawable.widget_check_done else R.drawable.widget_check_pending
            )
            views.setTextViewText(ids[2], labor.name)
            views.setTextColor(ids[2], if (labor.done) 0xFF8A8A8A.toInt() else 0xFFF5F5F5.toInt())
            views.setInt(
                ids[2], "setPaintFlags",
                if (labor.done) Paint.ANTI_ALIAS_FLAG or Paint.STRIKE_THRU_TEXT_FLAG else Paint.ANTI_ALIAS_FLAG
            )
            views.setViewVisibility(ids[3], if (start + index == firstOpen) View.VISIBLE else View.GONE)

            // Tapping a row ticks or unticks that labor without opening the app
            if (labor.id > 0L) {
                val toggle = Intent(context, TodayPlanWidget::class.java).apply {
                    action = ACTION_TOGGLE
                    data = android.net.Uri.parse("forge://labor/${labor.id}")
                    putExtra(EXTRA_LABOR_ID, labor.id)
                }
                views.setOnClickPendingIntent(
                    ids[0],
                    PendingIntent.getBroadcast(
                        context, index + 1, toggle,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }

        return views
    }
}
