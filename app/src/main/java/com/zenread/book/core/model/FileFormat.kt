package com.zenread.book.core.model

enum class FileFormat(val extension: String) {
    PDF("pdf"),
    EPUB("epub");

    override fun toString(): String = extension
}