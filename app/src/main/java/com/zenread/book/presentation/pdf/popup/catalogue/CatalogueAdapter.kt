package com.zenread.book.presentation.pdf.popup.catalogue

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zenread.book.R
import com.zenread.book.databinding.ItemTocBinding
import com.zenread.book.domain.model.TocItem

data class DisplayTocItem(
    val tocItem: TocItem,
    var level: Int = 0,
    var isExpanded: Boolean = false
)

class CatalogueAdapter (private val tocList: List<TocItem>, private val onItemClick: (page: Int) -> Unit) :
    RecyclerView.Adapter<CatalogueAdapter.ViewHolder>() {

    private val displayList = mutableListOf<DisplayTocItem>()

    init {
        tocList.forEach {
            displayList.add(DisplayTocItem(it, level = it.level))
        }
    }
    inner class ViewHolder(val binding: ItemTocBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTocBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = displayList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val displayItem = displayList[position]
        val toc = displayItem.tocItem

        // Set text
        holder.binding.tvTitle.text = toc.title
        holder.binding.pageNum.text = toc.page.toString()

        // Padding theo level
        val paddingStart = 16 + displayItem.level * 40
        holder.binding.tvTitle.setPadding(paddingStart, 0, 0, 0)

        // Mũi tên
        if (toc.children.isNotEmpty()) {
            holder.binding.ivArrow.visibility = View.VISIBLE
            holder.binding.ivArrow.setImageResource(
                if (displayItem.isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right
            )
        } else {
            holder.binding.ivArrow.visibility =
                if (tocList.hasAnyChildren()) View.INVISIBLE else View.GONE
        }

        // Click vào mũi tên → expand/collapse
        holder.binding.ivArrow.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val currentItem = displayList[pos]
                if (currentItem.isExpanded) collapseItem(pos)
                else expandItem(pos)
            }
        }

        // Click vào text → di chuyển đến trang
        holder.binding.tvTitle.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val currentItem = displayList[pos]
                // gọi callback trên Activity/Fragment để di chuyển PDF
                onItemClick(currentItem.tocItem.page)
            }
        }
    }

    fun List<TocItem>.hasAnyChildren(): Boolean =
        any { it.children.isNotEmpty() || it.children.hasAnyChildren() }

    private fun expandItem(position: Int) {
        val displayItem = displayList[position]
        displayItem.isExpanded = true
        val children = displayItem.tocItem.children.map {
            DisplayTocItem(it, level = displayItem.level + 1)
        }
        displayList.addAll(position + 1, children)
        notifyItemRangeInserted(position + 1, children.size)
        notifyItemChanged(position)
    }

    private fun collapseItem(position: Int) {
        val displayItem = displayList[position]
        displayItem.isExpanded = false
        val removedCount = removeChildren(position)
        notifyItemRangeRemoved(position + 1, removedCount)
        notifyItemChanged(position)
    }

    private fun removeChildren(position: Int): Int {
        var count = 0
        val parentLevel = displayList[position].level
        var nextPos = position + 1

        while (nextPos < displayList.size && displayList[nextPos].level > parentLevel) {
            count++
            nextPos++
        }

        // Xóa tất cả children
        for (i in 0 until count) {
            displayList.removeAt(position + 1)
        }
        return count
    }
}