package com.zenread.book.domain.service

import com.zenread.book.domain.model.Highlight
import com.zenread.book.domain.repository.HighlightRepository
import com.zenread.book.presentation.pdf.MarksState
import com.zenread.book.presentation.pdf.PageMark
import javax.inject.Inject
import kotlin.Int

class HighlightService @Inject constructor(
    private val highlightRepository: HighlightRepository
) {
    suspend fun insert(mark: PageMark, bookId: Int, pageNumber: Int): Long {
        val highlight = Highlight(
            bookId = bookId,
            pageNumber = pageNumber,
            rectList = mark.pdfMarks!!,
            texts = mark.text!!,
            color = mark.color!!,
            confirmId = mark.confirmId,
            isFirstPageMark = mark.isFirstPageMark,
            contentNote = mark.contentNote,
        )
        return highlightRepository.insert(highlight)
    }

    suspend fun getHighlightsByBookPageMap(bookId: Int): MutableMap<Int, MutableList<PageMark>> {
        val highlightsMap = mutableMapOf<Int, MutableList<PageMark>>()
        val highlights = highlightRepository.getHighlightsByBook(bookId)

        highlights.forEach { highlight ->
            val pageIndex = highlight.pageNumber

            val pageMark = PageMark(
                screenMarks = highlight.rectList,
                text = highlight.texts,
                marksState = MarksState.CONFIRM,
                color = highlight.color,
                confirmId = highlight.confirmId,
                isFirstPageMark = highlight.isFirstPageMark,
                contentNote = highlight.contentNote
            )

            // Thêm vào map theo pageIndex
            val pageList = highlightsMap.getOrPut(pageIndex) { mutableListOf() }
            pageList.add(pageMark)
        }

        return highlightsMap.toSortedMap()
    }

    suspend fun deleteByConfirmId(confirmId: Long) {
        highlightRepository.deleteByConfirmId(confirmId)
    }

    suspend fun updateByConfirmId(
        confirmId: Long,
        contentNote: String?,
        color: Int?
    ) {
        highlightRepository.updateByConfirmId(confirmId, contentNote, color)
    }
}
