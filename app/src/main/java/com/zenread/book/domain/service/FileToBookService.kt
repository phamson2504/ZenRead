package com.zenread.book.domain.service

import com.zenread.book.domain.model.Book
import com.zenread.book.domain.model.BookResult
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
    suspend fun importFilesAsBooks(): List<BookResult> {
        val cachedFileWrappers = fileManagerRepository.getListFiles()
        val results = mutableListOf<BookResult>()
        for (cfw in cachedFileWrappers) {
            val book = fileConvert.convertFileToBook(cfw)
            if (book!=null){
                results.add(BookResult.Success(book))
            }else{
                results.add(
                    BookResult.Error(
                        name = cfw.name,
                        reason = "Failed to import file to book"
                    )
                )
            }
        }
        val newBooks = results.filterIsInstance<BookResult.Success>().map { it.book }
        if (newBooks.isNotEmpty())
            bookRepository.insertBooks(newBooks)
        return results
    }

}