package com.zenread.book.domain.model

import android.graphics.RectF
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class Highlight(
    val id: Long = 0,
    val bookId: Int,
    val pageNumber: Int,
    val rectList: List<RectF>,
    val texts: List<String>,
    val color: Int,
    var confirmId: Long? = -1,
    var isFirstPageMark: Boolean = false,
    val contentNote: String? = null,
): Parcelable