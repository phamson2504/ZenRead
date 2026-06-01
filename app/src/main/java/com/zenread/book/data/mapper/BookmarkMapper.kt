package com.zenread.book.data.mapper

import com.zenread.book.data.dot.BookmarkEntity
import com.zenread.book.domain.model.Bookmark

object BookmarkMapper {
    fun toBookmarkEntity(bookmark: Bookmark): BookmarkEntity {
        return BookmarkEntity(
            id = bookmark.id,
            bookId = bookmark.bookId,
            pageNumber = bookmark.pageNumber,
            note = bookmark.note
        )
    }

    fun toBookmark(bookmarkEntity: BookmarkEntity): Bookmark {
        return Bookmark(
            id = bookmarkEntity.id,
            bookId = bookmarkEntity.bookId,
            pageNumber = bookmarkEntity.pageNumber,
            note = bookmarkEntity.note
        )
    }
}