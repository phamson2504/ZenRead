package com.zenread.book.domain.model

import android.graphics.RectF

data class WordInfo(
    val word: String,
    val rect: RectF,
    val pageIndex: Int,
    var lineYKey: Int = -1,
    var columnIndex: Int? = null,
    var columnGroupId: Float? = null
)