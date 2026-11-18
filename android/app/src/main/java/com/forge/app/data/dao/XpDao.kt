package com.forge.app.data.dao

import androidx.room.*
import com.forge.app.data.model.XpEvent
import kotlinx.coroutines.flow.Flow

data class XpBySource(
    val source: String,
    val total: Int
)

@Dao
interface XpDao {

    // Only labors and reflections count towards XP and rank. Older events from other
    // sources stay in the table but are ignored.
    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE source IN ('labor', 'reflection')")
    fun getTotalXp(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE date = :date AND source IN ('labor', 'reflection')")
    fun getXpForDate(date: String): Flow<Int>

    @Insert
    suspend fun insert(event: XpEvent)

    @Query("SELECT * FROM xp_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int): Flow<List<XpEvent>>

    @Query("SELECT source, SUM(amount) AS total FROM xp_events WHERE source IN ('labor', 'reflection') GROUP BY source HAVING total > 0 ORDER BY total DESC")
    fun getXpBySource(): Flow<List<XpBySource>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE source IN ('labor', 'reflection')")
    suspend fun getTotalXpSync(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE date = :date AND source = :source")
    suspend fun getSourceTotalForDate(date: String, source: String): Int
}
