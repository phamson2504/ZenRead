package com.zenread.book.domain.model


data class CachedFileBuilder(
    val name: String? = null,
    val path: String? = null,
    val size: Long? = null,
    val lastModified: Long? = null,
    val isDirectory: Boolean? = null,
)