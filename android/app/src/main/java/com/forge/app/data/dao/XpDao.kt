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

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events")
    fun getTotalXp(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE date = :date")
    fun getXpForDate(date: String): Flow<Int>

    @Insert
    suspend fun insert(event: XpEvent)

    @Query("SELECT * FROM xp_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int): Flow<List<XpEvent>>

    @Query("SELECT source, SUM(amount) AS total FROM xp_events GROUP BY source ORDER BY total DESC")
    fun getXpBySource(): Flow<List<XpBySource>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events")
    fun getTotalXpSync(): Int
}
