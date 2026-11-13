package com.forge.app.data.dao

import androidx.room.*
import com.forge.app.data.model.TimeTrack
import kotlinx.coroutines.flow.Flow

data class CategoryTime(
    val category: String,
    val minutes: Int
)

@Dao
interface TimeTrackDao {

    @Query("SELECT * FROM time_tracks WHERE date = :date ORDER BY startTime DESC")
    fun getTracksForDate(date: String): Flow<List<TimeTrack>>

    @Query("SELECT * FROM time_tracks WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveTrack(): TimeTrack?

    @Insert
    suspend fun insert(track: TimeTrack): Long

    @Query("UPDATE time_tracks SET endTime = :endTime, durationMinutes = :durationMinutes WHERE id = :id")
    suspend fun stopTrack(id: Long, endTime: Long, durationMinutes: Int)

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM time_tracks WHERE date = :date")
    suspend fun getTotalMinutesForDate(date: String): Int

    @Query("SELECT category, SUM(durationMinutes) AS minutes FROM time_tracks WHERE date = :date GROUP BY category ORDER BY minutes DESC")
    fun getTotalMinutesByCategory(date: String): Flow<List<CategoryTime>>
}
