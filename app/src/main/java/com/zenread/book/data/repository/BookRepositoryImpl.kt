package com.zenread.book.data.repository

import android.app.Application
import com.zenread.book.data.local.dao.BookDao
import com.zenread.book.data.mapper.BookMapper
import com.zenread.book.domain.model.Book
import com.zenread.book.domain.repository.BookRepository
import javax.inject.Inject

class BookRepositoryImpl@Inject constructor(
    private val bookDao: BookDao
): BookRepository {
    override suspend fun getBookByTile(title: String): List<Book> {
       val books =  bookDao.getBookByTile(title)
        return books.map { bookEntity ->
            BookMapper.toBook(bookEntity)
        }
    }

    override suspend fun insertBook(book: Book) {
        val bookEntity = BookMapper.toBookEntity(book)
        bookDao.insert(bookEntity)
    }

    override suspend fun insertBooks(books: List<Book>) {
        val bookList = books.map { BookMapper.toBookEntity(it) }
        bookDao.insertAll(bookList)
    }
}