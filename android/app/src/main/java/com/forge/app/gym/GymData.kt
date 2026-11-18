package com.forge.app.gym

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 * The gym log lives in its own small database, so adding it needs no migration
 * of the main Forge database and cannot touch existing labors, XP or reflections.
 */

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String,
    val startedAt: Long,
    val endedAt: Long? = null
)

@Entity(tableName = "gym_sets")
data class GymSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exercise: String,
    val bodyPart: String = "",
    val weightKg: Double,
    val reps: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class WorkoutSummary(
    val id: Long,
    val name: String,
    val date: String,
    val startedAt: Long,
    val endedAt: Long?,
    val sets: Int,
    val volume: Double
)

data class ExerciseBest(
    val exercise: String,
    val weightKg: Double,
    val reps: Int,
    val timestamp: Long
)

@Dao
interface GymDao {
    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("DELETE FROM gym_sets WHERE workoutId = :workoutId")
    suspend fun deleteSetsFor(workoutId: Long)

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun activeWorkout(): Flow<Workout?>

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeWorkoutNow(): Workout?

    @Insert
    suspend fun insertSet(set: GymSet): Long

    @Query("DELETE FROM gym_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM gym_sets WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    fun setsFor(workoutId: Long): Flow<List<GymSet>>

    @Query("SELECT * FROM gym_sets WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    suspend fun setsForNow(workoutId: Long): List<GymSet>

    @Query(
        """SELECT w.id, w.name, w.date, w.startedAt, w.endedAt,
           COUNT(s.id) AS sets, COALESCE(SUM(s.weightKg * s.reps), 0) AS volume
           FROM workouts w LEFT JOIN gym_sets s ON s.workoutId = w.id
           WHERE w.endedAt IS NOT NULL
           GROUP BY w.id ORDER BY w.startedAt DESC LIMIT :limit"""
    )
    fun recentWorkouts(limit: Int): Flow<List<WorkoutSummary>>

    @Query("SELECT COALESCE(MAX(weightKg), 0) FROM gym_sets WHERE exercise = :exercise AND workoutId != :excludeWorkout")
    suspend fun bestWeightBefore(exercise: String, excludeWorkout: Long): Double

    @Query("SELECT * FROM gym_sets WHERE exercise = :exercise ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastSet(exercise: String): GymSet?

    /** Heaviest set per exercise, newest first. */
    @Query(
        """SELECT s.exercise, s.weightKg, s.reps, s.timestamp FROM gym_sets s
           JOIN workouts w ON w.id = s.workoutId AND w.endedAt IS NOT NULL
           WHERE s.weightKg = (SELECT MAX(weightKg) FROM gym_sets t WHERE t.exercise = s.exercise)
           GROUP BY s.exercise ORDER BY s.timestamp DESC LIMIT :limit"""
    )
    fun bests(limit: Int): Flow<List<ExerciseBest>>
}

@Database(entities = [Workout::class, GymSet::class], version = 1, exportSchema = false)
abstract class GymDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao

    companion object {
        @Volatile
        private var INSTANCE: GymDatabase? = null

        fun get(context: Context): GymDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, GymDatabase::class.java, "forge_gym")
                .build().also { INSTANCE = it }
        }
    }
}
