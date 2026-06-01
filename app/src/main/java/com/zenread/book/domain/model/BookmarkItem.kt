package com.zenread.book.domain.model

import android.graphics.RectF

data class BookmarkItem(
    val title: String,
    val page: Int,
    val firstRect: RectF
)