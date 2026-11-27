package com.forge.app.gym

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * The gym log lives in its own small database, so it never touches labors, XP or reflections.
 * Version 2 adds routines, body weight, planned sets (done = 0) and set types.
 */

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val routineId: Long? = null
)

@Entity(tableName = "gym_sets")
data class GymSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exercise: String,
    val bodyPart: String = "",
    val weightKg: Double,
    val reps: Int,
    val timestamp: Long = System.currentTimeMillis(),
    /** normal, warmup, drop or failure */
    @ColumnInfo(defaultValue = "normal") val setType: String = "normal",
    @ColumnInfo(defaultValue = "0") val durationSec: Int = 0,
    /** 0 while it's only planned in a running workout; 1 once you've done it */
    @ColumnInfo(defaultValue = "1") val done: Int = 1,
    /** keeps exercises in the order you added them */
    @ColumnInfo(defaultValue = "0") val position: Int = 0
)

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "") val notes: String = "",
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "routine_items")
data class RoutineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exercise: String,
    @ColumnInfo(defaultValue = "") val bodyPart: String = "",
    @ColumnInfo(defaultValue = "3") val sets: Int = 3,
    @ColumnInfo(defaultValue = "8") val repsLow: Int = 8,
    @ColumnInfo(defaultValue = "12") val repsHigh: Int = 12,
    @ColumnInfo(defaultValue = "90") val restSec: Int = 90,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0
)

@Entity(tableName = "body_weights")
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val kg: Double,
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

/** One done set with when its workout happened, for history and recovery. */
data class SetWithDate(
    val exercise: String,
    val bodyPart: String,
    val weightKg: Double,
    val reps: Int,
    val setType: String,
    val workoutId: Long,
    val startedAt: Long
)

@Dao
interface GymDao {
    // ── workouts ──
    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun activeWorkout(): Flow<Workout?>

    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeWorkoutNow(): Workout?

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workout(id: Long): Workout?

    // ── sets ──
    @Insert
    suspend fun insertSet(set: GymSet): Long

    @Update
    suspend fun updateSet(set: GymSet)

    @Query("DELETE FROM gym_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM gym_sets WHERE workoutId = :workoutId")
    suspend fun deleteSetsFor(workoutId: Long)

    @Query("DELETE FROM gym_sets WHERE workoutId = :workoutId AND done = 0")
    suspend fun deletePlannedSets(workoutId: Long)

    @Query("DELETE FROM gym_sets WHERE workoutId = :workoutId AND exercise = :exercise")
    suspend fun deleteExerciseFrom(workoutId: Long, exercise: String)

    @Query("UPDATE gym_sets SET exercise = :to, bodyPart = :bodyPart WHERE workoutId = :workoutId AND exercise = :from")
    suspend fun renameExercise(workoutId: Long, from: String, to: String, bodyPart: String)

    @Query("SELECT * FROM gym_sets WHERE workoutId = :workoutId ORDER BY position ASC, id ASC")
    fun setsFor(workoutId: Long): Flow<List<GymSet>>

    @Query("SELECT * FROM gym_sets WHERE workoutId = :workoutId ORDER BY position ASC, id ASC")
    suspend fun setsForNow(workoutId: Long): List<GymSet>

    @Query("SELECT COALESCE(MAX(position), -1) FROM gym_sets WHERE workoutId = :workoutId")
    suspend fun maxPosition(workoutId: Long): Int

    @Query(
        """SELECT w.id, w.name, w.date, w.startedAt, w.endedAt,
           COUNT(s.id) AS sets, COALESCE(SUM(s.weightKg * s.reps), 0) AS volume
           FROM workouts w LEFT JOIN gym_sets s ON s.workoutId = w.id AND s.done = 1
           WHERE w.endedAt IS NOT NULL
           GROUP BY w.id ORDER BY w.startedAt DESC LIMIT :limit"""
    )
    fun recentWorkouts(limit: Int): Flow<List<WorkoutSummary>>

    @Query("SELECT COALESCE(MAX(weightKg), 0) FROM gym_sets WHERE exercise = :exercise AND workoutId != :excludeWorkout AND done = 1")
    suspend fun bestWeightBefore(exercise: String, excludeWorkout: Long): Double

    @Query("SELECT * FROM gym_sets WHERE exercise = :exercise AND done = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastSet(exercise: String): GymSet?

    /** The sets from the most recent finished workout that had this exercise. */
    @Query(
        """SELECT * FROM gym_sets WHERE exercise = :exercise AND done = 1 AND workoutId = (
             SELECT s.workoutId FROM gym_sets s JOIN workouts w ON w.id = s.workoutId
             WHERE s.exercise = :exercise AND s.done = 1 AND w.endedAt IS NOT NULL AND w.id != :excludeWorkout
             ORDER BY w.startedAt DESC LIMIT 1)
           ORDER BY position ASC, id ASC"""
    )
    suspend fun lastSession(exercise: String, excludeWorkout: Long): List<GymSet>

    /** Heaviest set per exercise, newest first. */
    @Query(
        """SELECT s.exercise, s.weightKg, s.reps, s.timestamp FROM gym_sets s
           JOIN workouts w ON w.id = s.workoutId AND w.endedAt IS NOT NULL
           WHERE s.done = 1 AND s.weightKg = (SELECT MAX(weightKg) FROM gym_sets t WHERE t.exercise = s.exercise AND t.done = 1)
           GROUP BY s.exercise ORDER BY s.timestamp DESC LIMIT :limit"""
    )
    fun bests(limit: Int): Flow<List<ExerciseBest>>

    @Query(
        """SELECT s.exercise, s.bodyPart, s.weightKg, s.reps, s.setType, s.workoutId, w.startedAt
           FROM gym_sets s JOIN workouts w ON w.id = s.workoutId
           WHERE s.done = 1 AND w.endedAt IS NOT NULL AND w.startedAt >= :since
           ORDER BY w.startedAt DESC"""
    )
    fun setsSince(since: Long): Flow<List<SetWithDate>>

    @Query(
        """SELECT s.exercise, s.bodyPart, s.weightKg, s.reps, s.setType, s.workoutId, w.startedAt
           FROM gym_sets s JOIN workouts w ON w.id = s.workoutId
           WHERE s.done = 1 AND w.endedAt IS NOT NULL AND s.exercise = :exercise
           ORDER BY w.startedAt DESC, s.position ASC, s.id ASC LIMIT 300"""
    )
    suspend fun historyOf(exercise: String): List<SetWithDate>

    @Query(
        """SELECT s.exercise, s.bodyPart, s.weightKg, s.reps, s.setType, s.workoutId, w.startedAt
           FROM gym_sets s JOIN workouts w ON w.id = s.workoutId
           WHERE s.done = 1 AND w.endedAt IS NOT NULL AND w.startedAt >= :since
           ORDER BY w.startedAt DESC"""
    )
    suspend fun setsSinceNow(since: Long): List<SetWithDate>

    // ── routines ──
    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("SELECT * FROM routines ORDER BY sortOrder ASC, id ASC")
    fun routines(): Flow<List<Routine>>

    @Query("SELECT * FROM routines ORDER BY sortOrder ASC, id ASC")
    suspend fun routinesNow(): List<Routine>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun routine(id: Long): Routine?

    @Insert
    suspend fun insertRoutineItem(item: RoutineItem): Long

    @Query("DELETE FROM routine_items WHERE routineId = :routineId")
    suspend fun deleteRoutineItems(routineId: Long)

    @Query("SELECT * FROM routine_items WHERE routineId = :routineId ORDER BY sortOrder ASC, id ASC")
    suspend fun routineItems(routineId: Long): List<RoutineItem>

    @Query("SELECT * FROM routine_items ORDER BY routineId ASC, sortOrder ASC, id ASC")
    fun allRoutineItems(): Flow<List<RoutineItem>>

    @Query("SELECT MAX(w.startedAt) FROM workouts w WHERE w.routineId = :routineId AND w.endedAt IS NOT NULL")
    suspend fun lastUsed(routineId: Long): Long?

    // ── body weight ──
    @Insert
    suspend fun insertWeight(w: BodyWeight): Long

    @Query("DELETE FROM body_weights WHERE date = :date")
    suspend fun deleteWeightOn(date: String)

    @Query("DELETE FROM body_weights WHERE id = :id")
    suspend fun deleteWeight(id: Long)

    @Query("SELECT * FROM body_weights ORDER BY date DESC, timestamp DESC LIMIT :limit")
    fun weights(limit: Int): Flow<List<BodyWeight>>

    @Query("SELECT * FROM body_weights ORDER BY date DESC, timestamp DESC LIMIT 1")
    suspend fun latestWeight(): BodyWeight?
}

@Database(
    entities = [Workout::class, GymSet::class, Routine::class, RoutineItem::class, BodyWeight::class],
    version = 2,
    exportSchema = false
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao

    companion object {
        @Volatile
        private var INSTANCE: GymDatabase? = null

        /** v1 → v2: new columns default to what old rows already were (done sets, normal type). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN routineId INTEGER")
                db.execSQL("ALTER TABLE gym_sets ADD COLUMN setType TEXT NOT NULL DEFAULT 'normal'")
                db.execSQL("ALTER TABLE gym_sets ADD COLUMN durationSec INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gym_sets ADD COLUMN done INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE gym_sets ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `notes` TEXT NOT NULL DEFAULT '', `sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routine_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`routineId` INTEGER NOT NULL, `exercise` TEXT NOT NULL, `bodyPart` TEXT NOT NULL DEFAULT '', " +
                        "`sets` INTEGER NOT NULL DEFAULT 3, `repsLow` INTEGER NOT NULL DEFAULT 8, " +
                        "`repsHigh` INTEGER NOT NULL DEFAULT 12, `restSec` INTEGER NOT NULL DEFAULT 90, " +
                        "`sortOrder` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_weights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, `kg` REAL NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): GymDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, GymDatabase::class.java, "forge_gym")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }
    }
}
