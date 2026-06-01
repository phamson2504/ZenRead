package com.zenread.book.domain.model

data class SearchTextResult (val id:Long, val text: String, val page: Int, val listWordInfo: List<WordInfo>)