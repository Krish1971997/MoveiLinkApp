package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sublink: String = "",
    val category: String = "",
    val link: String = "",
    val pageUrl: String = "",
    val importedAt: Long = System.currentTimeMillis()
)
