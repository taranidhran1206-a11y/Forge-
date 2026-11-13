package com.forge.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reflections")
data class Reflection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val aiResponse: String? = null,
    val promptUsed: String,
    val date: String,
    val timestamp: Long = System.currentTimeMillis()
)
