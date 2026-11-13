package com.forge.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labors")
data class Labor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val emoji: String = "⚔️",
    val description: String = "",
    val time: String = "09:00",
    val timeOfDay: String = "MORNING",
    val xpReward: Int = 10,
    val done: Boolean = false,
    val date: String,
    val sortOrder: Int = 0
)
