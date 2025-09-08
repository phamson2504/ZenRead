package com.zenread.book.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zenread.book.data.dot.Book

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    suspend fun getAll(): List<Book>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Query("SELECT * FROM books WHERE filePath = :path LIMIT 1")
    suspend fun getByFilePath(path: String): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<Book>)

    @Delete
    suspend fun deleteAll(books: List<Book>)
}