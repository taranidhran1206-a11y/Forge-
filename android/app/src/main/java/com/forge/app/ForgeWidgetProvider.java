package com.forge.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class ForgeWidgetProvider extends AppWidgetProvider {

    private static final String PREFS_NAME = "forge_widget_prefs";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_forge);

            String rank = prefs.getString("rank", "IRON");
            int xp = prefs.getInt("xp", 0);
            int level = prefs.getInt("level", 1);
            int streak = prefs.getInt("streak", 0);
            int laborsDone = prefs.getInt("laborsDone", 0);
            int laborsTotal = prefs.getInt("laborsTotal", 0);
            String focus = prefs.getString("focus", "0m");

            views.setTextViewText(R.id.widget_rank, rank);
            views.setTextViewText(R.id.widget_xp, xp + " XP");
            views.setTextViewText(R.id.widget_level, "Lv " + level);
            views.setTextViewText(R.id.widget_focus, focus);
            views.setTextViewText(R.id.widget_labors, laborsDone + "/" + laborsTotal);
            views.setTextViewText(R.id.widget_streak, streak + "d");

            Intent intent = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    public static void triggerUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);

        // Stats widget
        int[] statsIds = manager.getAppWidgetIds(new ComponentName(context, ForgeWidgetProvider.class));
        if (statsIds.length > 0) {
            Intent intent = new Intent(context, ForgeWidgetProvider.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, statsIds);
            context.sendBroadcast(intent);
        }

        // Labors widget
        int[] laborIds = manager.getAppWidgetIds(new ComponentName(context, ForgeLaborsWidgetProvider.class));
        if (laborIds.length > 0) {
            Intent intent2 = new Intent(context, ForgeLaborsWidgetProvider.class);
            intent2.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent2.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, laborIds);
            context.sendBroadcast(intent2);
        }

        // Streak widget
        int[] streakIds = manager.getAppWidgetIds(new ComponentName(context, ForgeStreakWidgetProvider.class));
        if (streakIds.length > 0) {
            Intent intent3 = new Intent(context, ForgeStreakWidgetProvider.class);
            intent3.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent3.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, streakIds);
            context.sendBroadcast(intent3);
        }

        // Mood widget
        int[] moodIds = manager.getAppWidgetIds(new ComponentName(context, ForgeMoodWidgetProvider.class));
        if (moodIds.length > 0) {
            Intent intent4 = new Intent(context, ForgeMoodWidgetProvider.class);
            intent4.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent4.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, moodIds);
            context.sendBroadcast(intent4);
        }
    }
}
