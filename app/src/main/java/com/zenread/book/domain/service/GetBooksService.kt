package com.zenread.book.domain.service

import com.zenread.book.domain.model.Book
import com.zenread.book.domain.repository.BookRepository
import javax.inject.Inject

class GetBooksService @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend fun getBooks(): List<Book> {
        return bookRepository.getBooks()
    }
}