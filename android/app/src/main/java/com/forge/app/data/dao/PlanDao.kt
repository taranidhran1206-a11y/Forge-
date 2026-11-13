package com.forge.app.data.dao

import androidx.room.*
import com.forge.app.data.model.PlanEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    @Query("SELECT * FROM plan_entries ORDER BY timestamp DESC")
    fun getEntries(): Flow<List<PlanEntry>>

    @Insert
    suspend fun insert(entry: PlanEntry)

    @Query("SELECT * FROM plan_entries WHERE date = :date ORDER BY timestamp ASC")
    fun getEntriesForDate(date: String): Flow<List<PlanEntry>>
}
