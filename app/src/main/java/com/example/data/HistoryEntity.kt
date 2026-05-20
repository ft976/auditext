package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_items")
data class HistoryEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    val voice: String,
    val emotion: String,
    val language: String,
    val provider: String,
    val timestamp: Long
)
