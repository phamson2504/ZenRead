package com.zenread.book.domain.model

data class Book(
    val id: Int = 0,
    val title: String,
    val author: String? = null,
    val filePath: String,
    val description: String? = null,
    val categoryId: Int? = 0,
    val typeFile: String,
    val coverImagePath: String? = null,
    val lastOpened: Long? = null
)
