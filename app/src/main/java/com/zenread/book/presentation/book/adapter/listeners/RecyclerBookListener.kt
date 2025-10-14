package com.zenread.book.presentation.book.adapter.listeners

import com.zenread.book.domain.model.Book

interface RecyclerBookListener {
    fun onBookSelected(book : Book)
}