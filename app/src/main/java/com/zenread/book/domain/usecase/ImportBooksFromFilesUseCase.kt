package com.zenread.book.domain.usecase

import com.zenread.book.domain.model.Book
import com.zenread.book.domain.service.FileToBookService
import javax.inject.Inject

class ImportBooksFromFilesUseCase @Inject constructor(
    private val fileToBookService: FileToBookService
) {
    suspend operator fun invoke(): List<Book> {
        return fileToBookService.importFilesAsBooks()
    }
}