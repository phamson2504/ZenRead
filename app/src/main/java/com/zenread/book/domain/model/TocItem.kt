package com.zenread.book.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TocItem(
    val title: String,
    val page: Int,
    val children: List<TocItem> = emptyList(),
    var isExpanded: Boolean = false,
    val level: Int = 0
): Parcelable