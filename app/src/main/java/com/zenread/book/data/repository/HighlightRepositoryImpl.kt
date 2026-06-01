package com.zenread.book.data.repository

import android.util.Log
import com.zenread.book.data.dot.HighlightEntity
import com.zenread.book.data.local.dao.HighlightDao
import com.zenread.book.data.mapper.HighlightMapper
import com.zenread.book.domain.model.Highlight
import com.zenread.book.domain.repository.HighlightRepository
import javax.inject.Inject

class HighlightRepositoryImpl @Inject constructor(
    val highlightDao: HighlightDao
) : HighlightRepository {

    override suspend fun insert(highlight: Highlight): Long {
        return try {
            val highlightEntity = HighlightMapper.toHighlightEntity(highlight)
            highlightDao.insert(highlightEntity)
        } catch (e: Exception) {
            Log.e("HighlightRepository", "Insert highlight failed", e)
            -1L
        }
    }

    override suspend fun getHighlightsByBook(bookId: Int): List<Highlight> {
        val highlightsDb = highlightDao.getHighlightsByBook(bookId)
        return highlightsDb.map { entity ->
            HighlightMapper.toHighlight(entity)
        }
    }

    override suspend fun deleteByConfirmId(confirmId: Long) {
        highlightDao.deleteByConfirmId(confirmId)
    }

    override suspend fun updateByConfirmId(
        confirmId: Long,
        contentNote: String?,
        color: Int?
    ) {
        highlightDao.updateByConfirmId(confirmId, contentNote, color)
    }
}