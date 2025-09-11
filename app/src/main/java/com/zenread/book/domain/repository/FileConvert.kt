package com.zenread.book.domain.repository

import com.zenread.book.data.file.CachedFileWrapper
import com.zenread.book.domain.model.Book

interface FileConvert {
    suspend fun convertFileToBook(cachedFileWrapper: CachedFileWrapper): Book?
}