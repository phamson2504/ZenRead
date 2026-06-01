package com.zenread.book.domain.model

data class Bookmark (
    val id: Long = 0,
    val bookId: Int,
    val pageNumber: Int?,
    val note: String? = null
)