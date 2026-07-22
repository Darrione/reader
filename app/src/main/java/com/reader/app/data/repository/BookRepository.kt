package com.reader.app.data.repository

import com.reader.app.data.db.BookDao
import com.reader.app.data.db.BookEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
) {
    fun books(): Flow<List<BookEntity>> = bookDao.getAll()

    suspend fun getById(id: Long): BookEntity? = bookDao.getById(id)

    suspend fun insert(book: BookEntity): Long = bookDao.insert(book)

    suspend fun updateCover(id: Long, coverPath: String?) = bookDao.updateCover(id, coverPath)

    suspend fun saveProgression(id: Long, progressionJson: String) =
        bookDao.saveProgression(id, progressionJson)

    suspend fun delete(book: BookEntity) = bookDao.delete(book)
}
