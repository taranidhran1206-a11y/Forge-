package com.forge.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_tracks")
data class TimeTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMinutes: Int = 0,
    val date: String
)
