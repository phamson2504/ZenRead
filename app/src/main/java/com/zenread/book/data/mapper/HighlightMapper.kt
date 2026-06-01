package com.zenread.book.data.mapper

import android.graphics.RectF
import com.zenread.book.data.dot.HighlightEntity
import com.zenread.book.domain.model.Highlight
import kotlin.Long

object HighlightMapper {
    fun toHighlightEntity(highlight: Highlight): HighlightEntity {
        return HighlightEntity(
            id = highlight.id,
            bookId = highlight.bookId,
            pageNumber = highlight.pageNumber,
            rectList = highlight.rectList,
            texts = highlight.texts,
            color = highlight.color,
            confirmId = highlight.confirmId,
            isFirstPageMark = highlight.isFirstPageMark,
            contentNote = highlight.contentNote
        )
    }

    fun toHighlight(entity: HighlightEntity): Highlight {
        return Highlight(
            id = entity.id,
            bookId = entity.bookId,
            pageNumber = entity.pageNumber,
            rectList = entity.rectList,
            texts = entity.texts,
            color = entity.color,
            confirmId = entity.confirmId,
            isFirstPageMark = entity.isFirstPageMark,
            contentNote = entity.contentNote
        )
    }
}