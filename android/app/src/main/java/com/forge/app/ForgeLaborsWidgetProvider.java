package com.forge.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;

public class ForgeLaborsWidgetProvider extends AppWidgetProvider {

    private static final String PREFS_NAME = "forge_widget_prefs";

    private static final int[] ROW_IDS = {
        R.id.labor_row_1, R.id.labor_row_2, R.id.labor_row_3,
        R.id.labor_row_4, R.id.labor_row_5, R.id.labor_row_6
    };
    private static final int[] TEXT_IDS = {
        R.id.labor_text_1, R.id.labor_text_2, R.id.labor_text_3,
        R.id.labor_text_4, R.id.labor_text_5, R.id.labor_text_6
    };
    private static final int[] CHECK_IDS = {
        R.id.labor_check_1, R.id.labor_check_2, R.id.labor_check_3,
        R.id.labor_check_4, R.id.labor_check_5, R.id.labor_check_6
    };
    private static final int[] DOT_IDS = {
        R.id.labor_dot_1, R.id.labor_dot_2, R.id.labor_dot_3,
        R.id.labor_dot_4, R.id.labor_dot_5, R.id.labor_dot_6
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String laborsJson = prefs.getString("laborsJson", "[]");

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_labors);

            try {
                JSONArray labors = new JSONArray(laborsJson);
                int openCount = 0;
                int count = Math.min(labors.length(), 6);

                for (int i = 0; i < 6; i++) {
                    if (i < count) {
                        JSONObject labor = labors.getJSONObject(i);
                        String name = labor.optString("name", "");
                        String emoji = labor.optString("emoji", "");
                        boolean done = labor.optBoolean("done", false);

                        views.setViewVisibility(ROW_IDS[i], View.VISIBLE);
                        views.setTextViewText(TEXT_IDS[i], emoji + " " + name);

                        if (done) {
                            views.setTextViewText(CHECK_IDS[i], "✓");
                            views.setInt(CHECK_IDS[i], "setTextColor", 0xFF22c55e);
                            views.setInt(DOT_IDS[i], "setBackgroundResource", R.drawable.labor_done_dot);
                            views.setInt(TEXT_IDS[i], "setTextColor", 0xFF6b7280);
                        } else {
                            views.setTextViewText(CHECK_IDS[i], "○");
                            views.setInt(CHECK_IDS[i], "setTextColor", 0xFF6b7280);
                            views.setInt(DOT_IDS[i], "setBackgroundResource", R.drawable.labor_pending_dot);
                            views.setInt(TEXT_IDS[i], "setTextColor", 0xFFd1d5db);
                            openCount++;
                        }
                    } else {
                        views.setViewVisibility(ROW_IDS[i], View.GONE);
                    }
                }

                views.setTextViewText(R.id.labors_open_count, openCount + " open");
            } catch (Exception e) {
                for (int rowId : ROW_IDS) {
                    views.setViewVisibility(rowId, View.GONE);
                }
                views.setTextViewText(R.id.labors_open_count, "0 open");
            }

            Intent intent = new Intent(context, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_labors_root, pendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
