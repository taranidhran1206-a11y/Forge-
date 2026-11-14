package com.forge.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class ForgeStreakWidgetProvider extends AppWidgetProvider {

    private static final String PREFS_NAME = "forge_widget_prefs";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int streak = prefs.getInt("streak", 0);
        int todayDone = prefs.getInt("todayDone", 0);
        int todayTotal = prefs.getInt("todayTotal", 0);

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_streak);

            // Streak count
            views.setTextViewText(R.id.streak_count, String.valueOf(streak));

            // Today progress
            views.setTextViewText(R.id.streak_today, todayDone + "/" + todayTotal + " today");

            // Open app on tap
            Intent intent = new Intent(context, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 2, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_streak_root, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
