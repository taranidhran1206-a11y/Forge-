package com.forge.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.util.WidgetHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Called from each widget's onUpdate (when it is placed, and every 30 minutes):
 * rewrite the widget data from the database, so a widget never stays stale just
 * because the app has not been opened.
 */
object WidgetSyncOnUpdate {

    @Volatile
    private var lastRun = 0L

    fun refresh(receiver: BroadcastReceiver, context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRun < 2000) return // three providers update together; one refresh is enough
        lastRun = now
        val app = context.applicationContext
        val pending = receiver.goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetHelper.updateAllWidgets(app, ForgeRepository(ForgeDatabase.getDatabase(app)))
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
