package com.forge.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plan_entries")
data class PlanEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val input: String,
    val output: String,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String
)
