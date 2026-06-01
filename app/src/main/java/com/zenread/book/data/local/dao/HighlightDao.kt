package com.zenread.book.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zenread.book.data.dot.BookEntity
import com.zenread.book.data.dot.HighlightEntity

@Dao
interface HighlightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity): Long

    @Query("SELECT * FROM highlight WHERE bookId = :bookId")
    suspend fun getHighlightsByBook(bookId: Int): List<HighlightEntity>

    @Query("DELETE FROM highlight WHERE confirmId = :confirmId")
    suspend fun deleteByConfirmId(confirmId: Long)

    @Query("""
    UPDATE highlight 
    SET contentNote = :contentNote,
        color = :color
    WHERE confirmId = :confirmId
    """)
    suspend fun updateByConfirmId(
        confirmId: Long,
        contentNote: String?,
        color: Int?
    )
}