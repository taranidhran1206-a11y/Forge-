package com.forge.app.data.dao

import androidx.room.*
import com.forge.app.data.model.CoachMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachDao {

    @Query("SELECT * FROM coach_messages ORDER BY timestamp ASC")
    fun getMessages(): Flow<List<CoachMessage>>

    @Insert
    suspend fun insert(message: CoachMessage)

    @Query("DELETE FROM coach_messages")
    suspend fun clearAll()
}
