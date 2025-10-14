package com.zenread.book.domain.usecase

import com.zenread.book.data.error.AppError
import com.zenread.book.data.error.mapper.ErrorMapper
import javax.inject.Inject

class ErrorManager @Inject constructor(private val errorMapper: ErrorMapper) {
    fun getError(errorCode: Int, vararg args: Any): AppError {
        val description = errorMapper.getErrorMessage(errorCode, *args)
        return AppError(code = errorCode, description = description)
    }
}