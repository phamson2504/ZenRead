package com.zenread.book.presentation.pdf.popup.reader

import android.graphics.RectF
import com.zenread.book.domain.model.BookmarkItem
import com.zenread.book.domain.model.SearchTextResult
import com.zenread.book.domain.model.TocItem
import com.zenread.book.domain.model.WordInfo

interface OnReaderMenuListener {
    fun onClickVolumeUp()

    fun getCatalogue(): List<TocItem>

    fun getListHighlight(): List<BookmarkItem>

    fun search(query: String)

    fun moveToSearchedPosition(searchTextResult: SearchTextResult)

    fun setChapterFromTocItem()

    fun moveToPageClicked(pageIndex: Int)

    fun moveToHighlight(pageIndex: Int, firstRect: RectF)
}