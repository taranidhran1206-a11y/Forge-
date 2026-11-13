package com.forge.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "xp_events")
data class XpEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Int,
    val source: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String
)
