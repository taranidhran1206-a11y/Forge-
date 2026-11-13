package com.forge.app.data.dao

import androidx.room.*
import com.forge.app.data.model.DailyStats

@Dao
interface DailyStatsDao {

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getStats(date: String): DailyStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: DailyStats)

    @Query("SELECT * FROM daily_stats WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getStatsForWeek(startDate: String, endDate: String): List<DailyStats>

    @Query("SELECT date FROM daily_stats WHERE streakCount > 0 ORDER BY date DESC")
    suspend fun getStreakDays(): List<String>
}
