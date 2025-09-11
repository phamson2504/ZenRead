package com.zenread.book.domain.repository

import com.zenread.book.data.file.CachedFileWrapper

interface FileManagerRepository {
    suspend fun getListFiles(query: String = ""): List<CachedFileWrapper>
    suspend fun getAllFile(): List<CachedFileWrapper>
}