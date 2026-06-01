package com.zenread.book.domain.repository

import com.zenread.book.domain.model.Bookmark

interface BookmarkRepository {
    suspend fun insert(bookmark: Bookmark)
    suspend fun getBookmarksByBook(bookId: Int): List<Bookmark>
    suspend fun deleteByBookAndPage(bookId: Int, pageIndex: Int)
}