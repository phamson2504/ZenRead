package com.zenread.book.domain.repository

import androidx.room.Query
import com.zenread.book.domain.model.Highlight

interface HighlightRepository {
    suspend fun insert(highlight: Highlight): Long
    suspend fun getHighlightsByBook(bookId: Int): List<Highlight>
    suspend fun deleteByConfirmId(confirmId: Long)
    suspend fun updateByConfirmId(confirmId: Long, contentNote: String?, color: Int?)
}