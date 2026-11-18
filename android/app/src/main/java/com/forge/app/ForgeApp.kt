package com.forge.app

import android.app.Application
import androidx.room.InvalidationTracker
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.util.WidgetHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Whenever labors, XP or daily stats change anywhere in the app, the widgets are
 * rewritten from the database shortly after. No screen has to remember to do it.
 */
class ForgeApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingRefresh: Job? = null

    override fun onCreate() {
        super.onCreate()
        val database = ForgeDatabase.getDatabase(this)
        val repository = ForgeRepository(database)

        database.invalidationTracker.addObserver(object :
            InvalidationTracker.Observer(arrayOf("labors", "xp_events", "daily_stats")) {
            override fun onInvalidated(tables: Set<String>) {
                pendingRefresh?.cancel()
                pendingRefresh = scope.launch {
                    delay(400) // one refresh for a burst of writes
                    try {
                        WidgetHelper.updateAllWidgets(this@ForgeApp, repository)
                    } catch (_: Exception) {
                    }
                }
            }
        })

        scope.launch {
            try {
                WidgetHelper.updateAllWidgets(this@ForgeApp, repository)
            } catch (_: Exception) {
            }
        }
    }
}
