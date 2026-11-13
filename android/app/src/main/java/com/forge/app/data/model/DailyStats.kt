package com.forge.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey
    val date: String,
    val laborsCompleted: Int = 0,
    val laborsTotal: Int = 0,
    val focusMinutes: Int = 0,
    val reflectionDone: Boolean = false,
    val auditDone: Boolean = false,
    val briefDone: Boolean = false,
    val streakCount: Int = 0,
    val moodScore: Int = -1,
    val totalXp: Int = 0
)
