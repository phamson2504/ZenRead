package com.zenread.book.domain.usecase

import com.zenread.book.domain.model.BookResult
import com.zenread.book.domain.service.FileToBookService
import javax.inject.Inject

class ImportBooksFromFilesUseCase @Inject constructor(
    private val fileToBookService: FileToBookService
) {
    suspend operator fun invoke(): List<BookResult> {
        return fileToBookService.importFilesAsBooks()
    }
}