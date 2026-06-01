package com.zenread.book.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zenread.book.data.dot.BookmarkEntity

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId")
    suspend fun getBookmarksByBook(bookId: Int): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND pageNumber = :pageIndex")
    suspend fun deleteByBookAndPage(bookId: Int, pageIndex: Int)
}