package com.zenread.book.domain.service

import com.zenread.book.domain.model.Book
import com.zenread.book.domain.repository.BookRepository
import javax.inject.Inject

class GetBooksService @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend fun findBooksByTitle(title: String): List<Book> {
        return bookRepository.getBookByTile(title)
    }
}