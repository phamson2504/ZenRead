package com.zenread.book.presentation.pdf.popup.bookmark

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zenread.book.databinding.ItemBookmarkBinding
import com.zenread.book.domain.model.BookmarkItem

class BookmarkAdapter(
    private val bookmarkList: List<BookmarkItem>,
    private val onItemClick: (bookmarkItem: BookmarkItem) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemBookmarkBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = bookmarkList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bookmark = bookmarkList[position]
        holder.binding.tvTitle.text = bookmark.title
        (bookmark.page + 1).toString().also { holder.binding.pageNum.text = it }

        holder.binding.tvTitle.setOnClickListener {
            if (position != RecyclerView.NO_POSITION) {
                onItemClick(bookmark)
            }
        }
    }
}