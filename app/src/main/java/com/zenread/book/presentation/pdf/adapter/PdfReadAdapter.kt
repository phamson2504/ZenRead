package com.zenread.book.presentation.pdf.adapter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.zenread.book.databinding.ItemPdfPageBinding
import com.zenread.book.presentation.pdf.PageMark

class PdfReadAdapter(
    private val confirmedHighlight: Map<Int, MutableList<PageMark>>,
    private val selectedMarksOfPage: Map<Int, PageMark>,
    private var pageSizes: List<Size>
) : RecyclerView.Adapter<PdfReadAdapter.PageViewHolder>() {

    val idRemoveMap = mutableMapOf<Int, Long?>()
    val bitmaps = mutableMapOf<Int, Bitmap>()


    fun updateSelectedMarks(pageIndex: Int) {
        notifyItemChanged(pageIndex, "selectedMarks")
    }

    fun updateConfirmedMarks(pageIndex: Int) {
        notifyItemChanged(pageIndex, "confirmedMarks")
    }

    fun removeConfirmedMarks(pageIndex: Int, confirmId: Long?) {
        idRemoveMap[pageIndex] = confirmId
    }


    inner class PageViewHolder(val binding: ItemPdfPageBinding) :
        RecyclerView.ViewHolder(binding.root)


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPdfPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val bitmap = bitmaps[position]
        val imageView = holder.binding.pageImageView
        val overlay = holder.binding.marksOverlay

        if (bitmap != null) {
            // Có bitmap -> hiển thị trang PDF
            imageView.apply {
                layoutParams.width = pageSizes[position].width
                layoutParams.height = pageSizes[position].height
                requestLayout()

                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.TRANSPARENT)
            }
        } else {
            // Chưa render -> placeholder
            imageView.apply {
                layoutParams.width = pageSizes[position].width
                layoutParams.height = pageSizes[position].height
                requestLayout()

                setImageDrawable(null)
                scaleType = ImageView.ScaleType.CENTER
                setBackgroundColor(Color.WHITE) // nền trắng
            }
        }

        overlay.layoutParams.width = pageSizes[position].width
        overlay.layoutParams.height = pageSizes[position].height
        overlay.requestLayout()

        val marks = selectedMarksOfPage[position]?.marks
        if (marks.isNullOrEmpty()) {
            overlay.clearSelectedMarks()
        } else {
            overlay.setSelectedMarks(marks)
        }

        val confirmedHighlight = confirmedHighlight[position]
        if (confirmedHighlight != null) {
            confirmedHighlight.forEach { mark ->
                {
                    mark.confirmId?.let {
                        overlay.setConfirmMarks(
                            it,
                            mark.marks,
                            mark.color,
                            mark.contentNote,
                            mark.isFirstPageMark
                        )
                    }
                }
            }

        } else {
            overlay.clearConfirmMarks()
        }
    }

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            val overlay = holder.binding.marksOverlay

            for (payload in payloads) {
                when (payload) {
                    "bitmap" -> {
                        val bmp = bitmaps[position]
                        if (bmp != null) {
                            holder.binding.pageImageView.apply {
                                setImageBitmap(bmp)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setBackgroundColor(Color.TRANSPARENT)
                            }
                        }
                    }

                    "selectedMarks" -> {
                        val marks = selectedMarksOfPage[position]?.marks ?: emptyList()
                        overlay.setSelectedMarks(marks)
                    }

                    "confirmedMarks" -> {
                        if (idRemoveMap[position] != null) {
                            idRemoveMap[position]?.let { overlay.removeHighlight(it) }
                            idRemoveMap.remove(position)
                        }

                        val confirmedHighlight = confirmedHighlight[position]
                        confirmedHighlight?.forEach { mark ->
                            mark.confirmId?.let {
                                overlay.setConfirmMarks(
                                    it,
                                    mark.marks,
                                    mark.color,
                                    mark.contentNote,
                                    mark.isFirstPageMark
                                )
                            }
                        }
                    }
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = pageSizes.size

    @SuppressLint("NotifyDataSetChanged")
    fun updatePageSizes(newSizes: List<Size>) {
        pageSizes = newSizes
        bitmaps.clear()
        notifyDataSetChanged()
    }

    fun updateBitmap(index: Int, bitmap: Bitmap) {
        bitmaps[index] = bitmap
        notifyItemChanged(index, "bitmap")
    }
}
