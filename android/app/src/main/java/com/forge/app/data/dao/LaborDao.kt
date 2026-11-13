package com.forge.app.data.dao

import androidx.room.*
import com.forge.app.data.model.Labor
import kotlinx.coroutines.flow.Flow

@Dao
interface LaborDao {

    @Query("SELECT * FROM labors WHERE date = :date ORDER BY sortOrder ASC, time ASC")
    fun getLaborsForDate(date: String): Flow<List<Labor>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(labor: Labor): Long

    @Update
    suspend fun update(labor: Labor)

    @Delete
    suspend fun delete(labor: Labor)

    @Query("UPDATE labors SET done = :done WHERE id = :id")
    suspend fun toggleDone(id: Long, done: Boolean)

    @Query("SELECT COUNT(*) FROM labors WHERE date = :date AND done = 1")
    suspend fun getCompletedCount(date: String): Int

    @Query("SELECT COUNT(*) FROM labors WHERE date = :date")
    suspend fun getTotalCount(date: String): Int

    @Query("SELECT * FROM labors WHERE date = :date ORDER BY sortOrder ASC, time ASC")
    fun getLaborsForDateSync(date: String): List<Labor>
}
