package com.forge.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.Labor
import com.forge.app.data.model.RankSystem
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XpAndWidgetTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val database get() = ForgeDatabase.getDatabase(context)
    private fun repository() = ForgeRepository(database)

    /** Today's labor XP must always equal the ticked rewards, capped. */
    private suspend fun assertLaborXpMatchesTicks() {
        val repo = repository()
        val today = repo.getTodayDate()
        val expected = repo.getLaborsForDateSync(today).filter { it.done }.sumOf { it.xpReward }
            .coerceAtMost(ForgeRepository.DAILY_LABOR_XP_CAP)
        assertEquals(expected, database.xpDao().getSourceTotalForDate(today, "labor"))
    }

    @Test
    fun retickingCannotFarmXp() = runBlocking {
        val repo = repository()
        val today = repo.getTodayDate()
        val id = repo.insertLabor(Labor(name = "Farm test", xpReward = 20, date = today))
        val labor = repo.getLaborsForDateSync(today).first { it.id == id }
        repeat(3) {
            repo.completeLabor(labor)
            repo.uncompleteLabor(labor)
        }
        assertLaborXpMatchesTicks()
        repo.deleteLabor(labor)
    }

    @Test
    fun dailyLaborXpIsCapped() = runBlocking {
        val repo = repository()
        val today = repo.getTodayDate()
        val ids = (1..12).map { repo.insertLabor(Labor(name = "Cap test $it", xpReward = 25, date = today)) }
        repo.getLaborsForDateSync(today).filter { it.id in ids }.forEach { repo.completeLabor(it) }
        val labor = database.xpDao().getSourceTotalForDate(today, "labor")
        assertTrue("labor XP $labor over the cap", labor <= ForgeRepository.DAILY_LABOR_XP_CAP)
        assertLaborXpMatchesTicks()
        repo.getLaborsForDateSync(today).filter { it.id in ids }.forEach {
            repo.uncompleteLabor(it)
            repo.deleteLabor(it)
        }
        assertLaborXpMatchesTicks()
    }

    @Test
    fun onlyLaborsAndReflectionsCount() = runBlocking {
        val repo = repository()
        val before = repo.getTotalXpSync()
        repo.addXp(500, "focus", "Old focus XP")
        repo.addXp(500, "time_track", "Old tracked XP")
        assertEquals(before, repo.getTotalXpSync())
    }

    @Test
    fun ranksArePacedForMonths() {
        // About 150 XP on a full day
        assertEquals(RankSystem.Rank.IRON, RankSystem.getRank(150))
        assertEquals(RankSystem.Rank.GOLD, RankSystem.getRank(150 * 14))
        assertEquals(RankSystem.Rank.LEGEND, RankSystem.getRank(150 * 180))
        assertTrue(RankSystem.getRank(150 * 120) != RankSystem.Rank.LEGEND)
    }

    /** No screen is open in this test: the app-wide observer alone must update the widget data. */
    @Test
    fun widgetDataFollowsDatabaseWithoutAnyScreen() = runBlocking {
        val repo = repository()
        val today = repo.getTodayDate()
        val id = repo.insertLabor(Labor(name = "Widget sync test", xpReward = 5, date = today))
        val labor = repo.getLaborsForDateSync(today).first { it.id == id }
        repo.completeLabor(labor)

        var seenDone = false
        for (i in 0 until 20) {
            Thread.sleep(250)
            val json = context.getSharedPreferences("forge_widget_prefs", Context.MODE_PRIVATE)
                .getString("labors_json", "[]")
            val arr = JSONArray(json)
            for (j in 0 until arr.length()) {
                val o = arr.getJSONObject(j)
                if (o.optLong("id") == id && o.optBoolean("done")) seenDone = true
            }
            if (seenDone) break
        }
        repo.uncompleteLabor(labor)
        repo.deleteLabor(labor)
        assertTrue("widget data did not pick up the tick", seenDone)
    }
}
