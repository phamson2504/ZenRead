package com.zenread.book.domain.repository

interface ErrorMapperSource {
    fun getErrorString(codeId: Int, vararg args: Any): String
    val errorsMap: Map<Int, Int>
}