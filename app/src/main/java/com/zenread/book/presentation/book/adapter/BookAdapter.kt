package com.zenread.book.presentation.book.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zenread.book.databinding.BookItemBinding
import com.zenread.book.domain.model.Book
import com.zenread.book.presentation.book.adapter.listeners.RecyclerBookListener
import com.zenread.book.presentation.book.adapter.BookViewHolder

class BookAdapter(
    private val books: List<Book>,
    private val listener: RecyclerBookListener
) : RecyclerView.Adapter<BookViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val itemBinding =
            BookItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(itemBinding)
    }

    override fun getItemCount(): Int = books.size

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(books[position], listener)
    }
}