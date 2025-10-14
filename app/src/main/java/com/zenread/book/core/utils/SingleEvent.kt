package com.zenread.book.core.utils

open class SingleEvent<out T>(private val content: T) {

    private var hasBeenHandled = false

    /**
     * Returns content if not handled yet, otherwise null.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Used to peek at content without marking it as handled.
     */
    fun peekContent(): T = content
}