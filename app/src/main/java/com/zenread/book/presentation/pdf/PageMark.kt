package com.zenread.book.presentation.pdf

import android.graphics.RectF

data class PageMark(val marksState: MarksState, val marks: List<RectF>)