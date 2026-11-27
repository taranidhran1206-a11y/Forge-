package com.forge.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forge.app.alarm.AlarmStore
import com.forge.app.gym.GymDatabase
import com.forge.app.gym.RoutineItem
import com.forge.app.gym.Routine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Gym logs made with Forge 2.1 must come through the 2.2 upgrade untouched. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun gymLogFromVersionOneSurvives() = runBlocking {
        val name = "forge_gym_migration_test"
        context.deleteDatabase(name)
        // The 2.1 schema, exactly as Room made it
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `workouts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `date` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `gym_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, `exercise` TEXT NOT NULL, `bodyPart` TEXT NOT NULL, `weightKg` REAL NOT NULL, `reps` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
            db.execSQL("INSERT INTO workouts (id, name, date, startedAt, endedAt) VALUES (1, 'Monday workout', '2026-11-24', 1000, 5000)")
            db.execSQL("INSERT INTO gym_sets (workoutId, exercise, bodyPart, weightKg, reps, timestamp) VALUES (1, 'Barbell Bench Press', 'Chest', 60.0, 8, 2000)")
            db.execSQL("INSERT INTO gym_sets (workoutId, exercise, bodyPart, weightKg, reps, timestamp) VALUES (1, 'Barbell Bench Press', 'Chest', 62.5, 6, 3000)")
            db.version = 1
        }

        val gym = Room.databaseBuilder(context, GymDatabase::class.java, name)
            .addMigrations(GymDatabase.MIGRATION_1_2)
            .build()
        try {
            val dao = gym.gymDao()
            val w = dao.workout(1)
            assertNotNull(w)
            assertEquals(null, w!!.routineId)
            val sets = dao.setsForNow(1)
            assertEquals(2, sets.size)
            assertTrue(sets.all { it.done == 1 && it.setType == "normal" })
            assertEquals(62.5, dao.bestWeightBefore("Barbell Bench Press", -1), 0.001)
            assertEquals(2, dao.historyOf("Barbell Bench Press").size)
            // New tables work
            val r = dao.insertRoutine(Routine(name = "Push"))
            dao.insertRoutineItem(RoutineItem(routineId = r, exercise = "Barbell Bench Press"))
            assertEquals(1, dao.routineItems(r).size)
        } finally {
            gym.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun alarmSchedulesWithTheSystem() {
        val a = AlarmStore.Alarm(9001, 6, 30, emptySet(), true, "math", 3, "test")
        AlarmStore.save(context, a)
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            assertNotNull("setAlarmClock should show as the next alarm", am.nextAlarmClock)
            assertEquals(AlarmStore.nextTrigger(a), am.nextAlarmClock.triggerTime)
        } finally {
            AlarmStore.delete(context, a.id)
        }
    }
}
