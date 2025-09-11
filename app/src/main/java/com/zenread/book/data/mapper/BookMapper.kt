package com.zenread.book.data.mapper

import com.zenread.book.data.dot.BookEntity
import com.zenread.book.domain.model.Book

object BookMapper {
    fun toBookEntity(book: Book): BookEntity{
        return BookEntity(
            id = book.id,
            title = book.title,
            filePath = book.filePath,
            description = book.description,
            author = book.author,
            typeFile = book.typeFile
        )
    }
    fun toBook(book: BookEntity): Book{
        return Book(
            id = book.id,
            title = book.title,
            filePath = book.filePath,
            description = book.description,
            author = book.author,
            typeFile = book.typeFile
        )
    }
}