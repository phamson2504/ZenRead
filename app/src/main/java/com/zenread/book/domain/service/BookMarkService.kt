package com.zenread.book.domain.service

import com.zenread.book.domain.model.Bookmark
import com.zenread.book.domain.repository.BookmarkRepository
import com.zenread.book.presentation.pdf.MarksState
import com.zenread.book.presentation.pdf.PageMark
import javax.inject.Inject

class BookMarkService @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {
    suspend fun insert(note: String, bookId: Int, pageNumber: Int) {
        val bookMark = Bookmark(
            bookId = bookId,
            pageNumber = pageNumber,
            note = note
        )
        bookmarkRepository.insert(bookMark)
    }

    suspend fun getBookmarksByBook(bookId: Int): List<Bookmark> {
        return bookmarkRepository.getBookmarksByBook(bookId)
    }

    suspend fun deleteByBookAndPage(bookId: Int, pageIndex: Int) {
        bookmarkRepository.deleteByBookAndPage(bookId, pageIndex)
    }
}