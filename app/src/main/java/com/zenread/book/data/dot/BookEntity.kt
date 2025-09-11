package com.zenread.book.data.dot

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val author: String? = null,
    val filePath: String,
    val description: String? = null,
    val categoryId: Int? = 0,
    val typeFile: String,
    val lastOpened: Long? = null
)
