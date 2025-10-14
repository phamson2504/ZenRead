package com.zenread.book.presentation.book.adapter

import androidx.recyclerview.widget.RecyclerView
import com.zenread.book.R
import com.zenread.book.databinding.BookItemBinding
import com.zenread.book.domain.model.Book
import com.zenread.book.presentation.book.adapter.listeners.RecyclerBookListener

class BookViewHolder(
    private val bookItemBinding: BookItemBinding
) : RecyclerView.ViewHolder(bookItemBinding.root) {

    fun bind(book: Book, recyclerBookListener: RecyclerBookListener) {
        bookItemBinding.tvTitleBookItem.text = book.title
        bookItemBinding.ivBookItemImg.setImageResource(R.drawable.ic_pdf)

        // --- CLICK LISTENER ---
        bookItemBinding.rlBookItem.setOnClickListener {
            recyclerBookListener.onBookSelected(book)
        }
    }
}