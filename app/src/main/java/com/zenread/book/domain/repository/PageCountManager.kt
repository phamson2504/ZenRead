package com.zenread.book.domain.repository

import com.zenread.book.presentation.pdf.ReadingPosition

interface PageCountManager {
    fun savePageCount(bookId: Int, readingPosition: ReadingPosition)
    fun loadPageCount(bookId: Int): ReadingPosition?
}