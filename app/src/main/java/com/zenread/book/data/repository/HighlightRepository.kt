package com.zenread.book.data.repository

import com.zenread.book.presentation.pdf.PageMark

class HighlightRepository {
    private val selectedHighlights = mutableMapOf<Int, PageMark>()

    fun addMark(index: Int, mark: PageMark) {
        selectedHighlights[index] = mark
    }
}