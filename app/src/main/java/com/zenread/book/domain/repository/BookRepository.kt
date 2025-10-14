package com.zenread.book.domain.repository

import com.zenread.book.domain.model.Book

interface BookRepository {
    suspend fun getBookByTile(title: String): List<Book>
    suspend fun insertBook(book: Book)
    suspend fun insertBooks(books: List<Book>)

    suspend fun getBooks(): List<Book>
}