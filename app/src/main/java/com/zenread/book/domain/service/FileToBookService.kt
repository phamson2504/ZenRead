package com.zenread.book.domain.service

import com.zenread.book.domain.model.Book
import com.zenread.book.domain.repository.BookRepository
import com.zenread.book.domain.repository.FileConvert
import com.zenread.book.domain.repository.FileManagerRepository
import java.io.File
import javax.inject.Inject

class FileToBookService @Inject constructor(
    private val bookRepository: BookRepository,
    private val fileConvert: FileConvert,
    private val fileManagerRepository: FileManagerRepository
) {
    suspend fun importFilesAsBooks(): List<Book> {
        val cachedFileWrappers = fileManagerRepository.getListFiles()
        val newBooks = mutableListOf<Book>()
        for (cfw in cachedFileWrappers) {
            val book = fileConvert.convertFileToBook(cfw)
            book?.let { newBooks.add(it) }
        }
        bookRepository.insertBooks(newBooks)
        return newBooks
    }

}