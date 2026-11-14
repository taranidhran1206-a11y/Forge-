package com.forge.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

public class ForgeStreakWidgetProvider extends AppWidgetProvider {

    private static final String PREFS_NAME = "forge_widget_prefs";

    // Row 1: Focus — 7 day cells
    private static final int[] R1_IDS = {
        R.id.streak_r1_d1, R.id.streak_r1_d2, R.id.streak_r1_d3,
        R.id.streak_r1_d4, R.id.streak_r1_d5, R.id.streak_r1_d6, R.id.streak_r1_d7
    };
    // Row 2: Labors
    private static final int[] R2_IDS = {
        R.id.streak_r2_d1, R.id.streak_r2_d2, R.id.streak_r2_d3,
        R.id.streak_r2_d4, R.id.streak_r2_d5, R.id.streak_r2_d6, R.id.streak_r2_d7
    };
    // Row 3: Journal
    private static final int[] R3_IDS = {
        R.id.streak_r3_d1, R.id.streak_r3_d2, R.id.streak_r3_d3,
        R.id.streak_r3_d4, R.id.streak_r3_d5, R.id.streak_r3_d6, R.id.streak_r3_d7
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int streak = prefs.getInt("streak", 0);
        String dateRange = prefs.getString("streakDateRange", "");
        String weeklyJson = prefs.getString("weeklyStreakJson", "{}");

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_streak);

            views.setTextViewText(R.id.streak_count, String.valueOf(streak));
            views.setTextViewText(R.id.streak_date_range, dateRange);

            try {
                JSONObject weekly = new JSONObject(weeklyJson);
                JSONArray focusDays = weekly.optJSONArray("focus");
                JSONArray laborsDays = weekly.optJSONArray("labors");
                JSONArray journalDays = weekly.optJSONArray("journal");

                int[][] rowIds = { R1_IDS, R2_IDS, R3_IDS };
                JSONArray[] arrays = { focusDays, laborsDays, journalDays };

                for (int r = 0; r < 3; r++) {
                    JSONArray days = arrays[r];
                    for (int d = 0; d < 7; d++) {
                        boolean done = false;
                        if (days != null && d < days.length()) {
                            done = days.optBoolean(d, false);
                        }
                        if (done) {
                            views.setTextViewText(rowIds[r][d], "✓");
                            views.setInt(rowIds[r][d], "setTextColor", 0xFF4CAF50);
                        } else {
                            views.setTextViewText(rowIds[r][d], "○");
                            views.setInt(rowIds[r][d], "setTextColor", 0x60FFFFFF);
                        }
                    }
                }
            } catch (Exception e) {
                // Leave default empty circles
            }

            Intent intent = new Intent(context, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 2, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_streak_root, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
