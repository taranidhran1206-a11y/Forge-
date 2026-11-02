package com.forge.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class ForgeWidgetProvider extends AppWidgetProvider {

    private static final String PREFS_NAME = "forge_widget_prefs";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        int xp = prefs.getInt("xp", 0);
        int level = prefs.getInt("level", 1);
        int streak = prefs.getInt("streak", 0);
        int focusMin = prefs.getInt("focusMin", 0);
        int laborsDone = prefs.getInt("laborsDone", 0);
        int laborsTotal = prefs.getInt("laborsTotal", 0);
        String rank = prefs.getString("rank", "IRON");
        String laborList = prefs.getString("laborList", "No labors today");

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_forge);

        views.setTextViewText(R.id.widget_xp, xp + " XP");
        views.setTextViewText(R.id.widget_level, "LVL " + String.format("%02d", level));
        views.setTextViewText(R.id.widget_rank, rank);
        views.setTextViewText(R.id.widget_focus, String.valueOf(focusMin));
        views.setTextViewText(R.id.widget_labors, laborsDone + "/" + laborsTotal);
        views.setTextViewText(R.id.widget_streak, String.valueOf(streak));
        views.setTextViewText(R.id.widget_labor_list, laborList);

        // Tap widget -> open app
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
