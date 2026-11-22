package com.forge.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.focus.DistractionService
import com.forge.app.util.WidgetHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Refreshes widgets at midnight, after a reboot or app update, and when the
 * clock or time zone changes. Also restarts the distraction watch after a reboot.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MIDNIGHT = "com.forge.app.widget.MIDNIGHT_REFRESH"
        const val ACTION_PLAN_TICK = "com.forge.app.widget.PLAN_TICK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action == ACTION_PLAN_TICK) {
            com.forge.app.util.PlanCard.update(app)
            return
        }
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> DistractionService.start(app)
        }
        val pending = goAsync()
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
