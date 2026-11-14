package com.forge.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class ForgeMoodWidgetProvider extends AppWidgetProvider {

    private static final String PREFS_NAME = "forge_widget_prefs";

    private static final String[] MOOD_LABELS = {"Rough", "Meh", "Okay", "Good", "Great"};

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int moodIndex = prefs.getInt("moodIndex", 3); // default to "Good"

        if (moodIndex < 0) moodIndex = 0;
        if (moodIndex > 4) moodIndex = 4;

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_mood);

            views.setTextViewText(R.id.mood_current_label, MOOD_LABELS[moodIndex]);

            Intent intent = new Intent(context, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 3, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_mood_root, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
