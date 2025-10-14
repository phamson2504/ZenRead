package com.zenread.book.domain.model

sealed class BookResult {
    data class Success(val book: Book) : BookResult()
    data class Error(val name: String, val reason: String) : BookResult()
}