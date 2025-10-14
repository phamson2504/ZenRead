package com.zenread.book.domain.usecase

import com.zenread.book.domain.model.Book
import com.zenread.book.domain.repository.BookRepository
import javax.inject.Inject

class GetBooksUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(): List<Book>{
        return bookRepository.getBooks()
    }
}