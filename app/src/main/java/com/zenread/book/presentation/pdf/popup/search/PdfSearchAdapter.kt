package com.zenread.book.presentation.pdf.popup.search

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zenread.book.databinding.ItemSearchDropdownBinding
import com.zenread.book.domain.model.SearchTextResult

class PdfSearchAdapter(
    private val onItemClick: (SearchTextResult) -> Unit
): ListAdapter<SearchTextResult, PdfSearchAdapter.SearchViewHolder>(DiffCallback())
{
    private var currentKeyword: String = ""

    fun setCurrentKeyword(keyword: String) {
        currentKeyword = keyword
    }

    class SearchViewHolder(val binding: ItemSearchDropdownBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: SearchTextResult,keyword:String, onItemClick: (SearchTextResult) -> Unit) {
            binding.tvWord.text = highlightKeyword(result.text,keyword, Color.GREEN)
            binding.tvPageNum.text = result.page.toString()
            binding.root.setOnClickListener { onItemClick(result) }
        }
        fun highlightKeyword(text: String, keyword: String, color: Int): SpannableStringBuilder {
            val spannable = SpannableStringBuilder(text)
            val lowerText = text.lowercase()
            val lowerKeyword = keyword.lowercase()

            var startIndex = lowerText.indexOf(lowerKeyword)
            while (startIndex >= 0) {
                val endIndex = startIndex + keyword.length
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    startIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = lowerText.indexOf(lowerKeyword, endIndex)
            }
            return spannable
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemSearchDropdownBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(getItem(position),currentKeyword, onItemClick )
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchTextResult>() {
        override fun areItemsTheSame(oldItem: SearchTextResult, newItem: SearchTextResult) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SearchTextResult, newItem: SearchTextResult) =
            oldItem == newItem
    }

}