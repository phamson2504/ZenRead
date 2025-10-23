package com.zenread.book.presentation.pdf

import android.graphics.RectF

data class PageMark(
    var confirmId: Long? = -1,
    val marksState: MarksState,
    val marks: List<RectF>,
    val text: List<String>? = null,
    var contentNote: String? = null,
    var color: Int? = null,
    var isFirstPageMark: Boolean = false
)