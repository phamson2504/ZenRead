package com.zenread.book.data.error

class AppError(val code: Int, val description: String) {
    constructor(exception: Exception) : this(
        code = DEFAULT_ERROR,
        description = exception.message ?: ""
    )
}

const val DEFAULT_ERROR = -3
const val ERROR_LOAD_FILE = 301

const val NUM_FAIL_ERROR = 302