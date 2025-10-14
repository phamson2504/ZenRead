package com.zenread.book.data.parser.pdf

import android.graphics.PointF
import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.zenread.book.domain.model.WordInfo

interface PdfTextParser {
    fun getWordClusterTouch(wordList: List<WordInfo>, pdfTouchRect: RectF): List<WordInfo>
    fun getWordsInPage(pdd: PDDocument, pageIndex: Int): List<WordInfo>
    fun groupWords(
        allWords: List<WordInfo>,
        horizontalGapThreshold: Float,
        avgWordHeight: Float,
        minConsecutiveLines: Int = 3,
    ): List<WordInfo>
    fun textParserPointer(
        words: List<WordInfo>,
        startPointer: PointF,
        endPointer: PointF
    ): List<WordInfo>
}