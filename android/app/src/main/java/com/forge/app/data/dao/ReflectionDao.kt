package com.forge.app.data.dao

import androidx.room.*
import com.forge.app.data.model.Reflection
import kotlinx.coroutines.flow.Flow

@Dao
interface ReflectionDao {

    @Query("SELECT * FROM reflections ORDER BY timestamp DESC")
    fun getReflections(): Flow<List<Reflection>>

    @Query("SELECT * FROM reflections WHERE date = :date LIMIT 1")
    suspend fun getReflectionForDate(date: String): Reflection?

    @Insert
    suspend fun insert(reflection: Reflection): Long

    @Query("SELECT COUNT(*) FROM reflections")
    fun getCount(): Flow<Int>
}
