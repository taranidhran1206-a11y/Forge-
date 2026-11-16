package com.forge.app

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.Labor
import com.forge.app.util.WidgetHelper
import com.forge.app.widget.RankWidget
import com.forge.app.widget.TodayPlanWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a real device or emulator: the things a compile cannot prove. */
@RunWith(AndroidJUnit4::class)
class DeviceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun repository() = ForgeRepository(ForgeDatabase.getDatabase(context))

    /** Inflates the widget the way a launcher does; an unsupported view throws here. */
    private fun inflate(views: RemoteViews) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertNotNull(views.apply(context, FrameLayout(context)))
        }
    }

    private fun seedWidgetLabors(done: Boolean) {
        val labors = listOf(
            Labor(id = 1, name = "Morning run", done = done, date = "x"),
            Labor(id = 2, name = "Deep work block", done = done, date = "x"),
            Labor(id = 3, name = "Gym session", done = done, date = "x")
        )
        WidgetHelper.writeLaborsJson(context, labors)
    }

    @Test
    fun todayPlanWidgetInflatesWithOpenLabors() {
        seedWidgetLabors(done = false)
        inflate(TodayPlanWidget().buildViews(context))
    }

    @Test
    fun todayPlanWidgetInflatesWhenAllDone() {
        seedWidgetLabors(done = true)
        inflate(TodayPlanWidget().buildViews(context))
    }

    @Test
    fun todayPlanWidgetInflatesWhenEmpty() {
        WidgetHelper.writeLaborsJson(context, emptyList())
        inflate(TodayPlanWidget().buildViews(context))
    }

    @Test
    fun streakWidgetInflates() {
        WidgetHelper.writeStreakData(context, 12, 2, 5)
        inflate(ForgeStreakWidgetProvider.buildViews(context))
    }

    @Test
    fun rankWidgetInflates() {
        WidgetHelper.writeRankData(context, "GOLD", 1450, 0.45f, "#FFD54F")
        inflate(RankWidget().buildViews(context))
    }

    @Test
    fun widgetTapTicksAndUnticksALabor() = runBlocking {
        val repo = repository()
        val today = repo.getTodayDate()
        val id = repo.insertLabor(Labor(name = "Widget test labor", xpReward = 10, date = today))
        val xpBefore = repo.getTotalXpSync()

        assertTrue(repo.toggleLaborFromWidget(id))
        assertTrue(repo.getLaborsForDateSync(today).first { it.id == id }.done)
        assertEquals(xpBefore + 10, repo.getTotalXpSync())

        assertTrue(repo.toggleLaborFromWidget(id))
        assertTrue(!repo.getLaborsForDateSync(today).first { it.id == id }.done)
    }

    @Test
    fun yesterdaysLaborsCarryIntoToday() = runBlocking {
        val repo = repository()
        val today = repo.getTodayDate()
        repo.getLaborsForDateSync(today).forEach { repo.deleteLabor(it) }
        repo.insertLabor(Labor(name = "Carry me", done = true, date = "2000-01-01"))

        assertTrue(repo.ensureTodayLabors())
        val carried = repo.getLaborsForDateSync(today)
        assertTrue(carried.any { it.name == "Carry me" && !it.done })
    }
}
