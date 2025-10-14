package com.zenread.book.core.model

sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null,
    val errorCode: Int? = null
) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(errorCode: Int, message: String? = null) : Resource<T>(message = message, errorCode = errorCode)
    class Loading<T> : Resource<T>()
}