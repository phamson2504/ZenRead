package com.zenread.book.data.dot

import android.graphics.RectF
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlight",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Int,
    val pageNumber: Int,
    val rectList: List<RectF>,
    val texts: List<String>,
    val color: Int,
    var confirmId: Long? = -1,
    val contentNote: String? = null,
)