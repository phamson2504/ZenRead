package com.zenread.book.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class Book(
    val id: Int = 0,
    val title: String,
    val author: String? = null,
    val uri: String,
    val filePath: String,
    val description: String? = null,
    val categoryId: Int? = 0,
    val typeFile: String,
    val coverImagePath: String? = null,
    val lastOpened: Long? = null
) : Parcelable
