package com.zenread.book.data.repository

import com.zenread.book.data.local.dao.BookmarkDao
import com.zenread.book.data.mapper.BookmarkMapper
import com.zenread.book.domain.model.Bookmark
import com.zenread.book.domain.repository.BookmarkRepository
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {
    override suspend fun insert(bookmark: Bookmark) {
        val bookmarkEntity = BookmarkMapper.toBookmarkEntity(bookmark)
        bookmarkDao.insert(bookmarkEntity)
    }

    override suspend fun getBookmarksByBook(bookId: Int): List<Bookmark> {
        val bookmarkEntities = bookmarkDao.getBookmarksByBook(bookId)
        return bookmarkEntities.map { BookmarkMapper.toBookmark(it) }
    }

    override suspend fun deleteByBookAndPage(bookId: Int, pageIndex: Int) {
        bookmarkDao.deleteByBookAndPage(bookId, pageIndex)
    }
}