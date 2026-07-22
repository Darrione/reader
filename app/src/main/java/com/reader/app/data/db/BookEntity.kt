package com.reader.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String?,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "cover_path")
    val coverPath: String? = null,
    @ColumnInfo(name = "progression_json")
    val progressionJson: String? = null,
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
)
