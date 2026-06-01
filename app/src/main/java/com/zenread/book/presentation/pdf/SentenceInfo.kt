package com.zenread.book.presentation.pdf

import com.zenread.book.domain.model.WordInfo

data class SentenceInfo(
    val sentenceIndex: Int,
    val words: List<WordInfo>
){
    val textSentence: String
        get() = words
            .joinToString("") { it.word }
            .replace(Regex("\\s+"), " ")
            .trim()
}
