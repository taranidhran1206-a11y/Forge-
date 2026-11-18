package com.forge.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class ForgeStreakWidgetProvider extends AppWidgetProvider {

    public static void triggerUpdate(Context context) {
        com.forge.app.util.WidgetHelper.INSTANCE.pushAll(context);
    }

    /** Builds exactly what the launcher draws. Public so a device test can inflate it. */
    public static RemoteViews buildViews(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int streak = prefs.getInt("streak", 0);
        int todayDone = prefs.getInt("todayDone", 0);
        int todayTotal = prefs.getInt("todayTotal", 0);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (!today.equals(prefs.getString("labors_date", today))) {
            todayDone = 0; // new day, nothing checked yet
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_streak);
        views.setTextViewText(R.id.streak_count, String.valueOf(streak));
        views.setTextViewText(R.id.streak_today, todayDone + "/" + todayTotal + " today");

        Intent intent = new Intent(context, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_streak_root, pendingIntent);
        return views;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        try {
            appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context));
        } catch (Exception ignored) {
        }
        com.forge.app.widget.WidgetSyncOnUpdate.INSTANCE.refresh(this, context);
    }
}
