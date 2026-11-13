package com.forge.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.forge.app.data.dao.*
import com.forge.app.data.model.*

@Database(
    entities = [
        Labor::class,
        XpEvent::class,
        Reflection::class,
        TimeTrack::class,
        CoachMessage::class,
        PlanEntry::class,
        DailyStats::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ForgeDatabase : RoomDatabase() {

    abstract fun laborDao(): LaborDao
    abstract fun xpDao(): XpDao
    abstract fun reflectionDao(): ReflectionDao
    abstract fun timeTrackDao(): TimeTrackDao
    abstract fun coachDao(): CoachDao
    abstract fun planDao(): PlanDao
    abstract fun dailyStatsDao(): DailyStatsDao

    companion object {
        @Volatile
        private var INSTANCE: ForgeDatabase? = null

        fun getDatabase(context: Context): ForgeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ForgeDatabase::class.java,
                    "forge_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
