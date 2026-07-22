package com.reader.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY added_at DESC")
    fun getAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET cover_path = :coverPath WHERE id = :id")
    suspend fun updateCover(id: Long, coverPath: String?)

    @Query("UPDATE books SET progression_json = :progressionJson WHERE id = :id")
    suspend fun saveProgression(id: Long, progressionJson: String)

    @Delete
    suspend fun delete(book: BookEntity)
}
