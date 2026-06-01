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
    }

    suspend fun getHighlightsByBook(bookId: Int): Map<Int, PageMark> {
        val bookmarkList = bookmarkRepository.getBookmarksByBook(bookId)

        return bookmarkList.associate { bookmark ->
            bookmark.pageNumber!! to PageMark(
                screenMarks = emptyList(),
                marksState = MarksState.MARK_BOOK,
                contentNote = bookmark.note,
            )
        }
    }

    suspend fun deleteByBookAndPage(bookId: Int, pageIndex: Int) {
        bookmarkRepository.deleteByBookAndPage(bookId, pageIndex)
    }
}