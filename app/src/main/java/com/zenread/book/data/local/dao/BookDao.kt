package com.zenread.book.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zenread.book.data.dot.BookEntity

@Dao
interface BookDao {

    @Query(
        """
        SELECT * FROM books
        WHERE LOWER(title) LIKE '%' || LOWER(:title) || '%'
    """
    )
    suspend fun getBookByTile(title: String): List<BookEntity>

    @Query("SELECT * FROM books")
    suspend fun getBooks(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookEntity: BookEntity)

    @Delete
    suspend fun delete(bookEntity: BookEntity)

    @Query("SELECT * FROM books WHERE filePath = :path LIMIT 1")
    suspend fun getByFilePath(path: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookEntities: List<BookEntity>)

    @Delete
    suspend fun deleteAll(bookEntities: List<BookEntity>)
}