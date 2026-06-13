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
    private val voicedHighlight: Map<Int, PageMark>,
    private val searchHighlight: Map<Int, MutableList<PageMark>>,
    private var pageSizes: List<Size>
) : RecyclerView.Adapter<PdfReadAdapter.PageViewHolder>() {

    val idRemoveMap = mutableMapOf<Int, Long?>()
    val bitmaps = mutableMapOf<Int, Bitmap>()

    fun updateVoiceHighlights(pageIndex: Int) {
        notifyItemChanged(pageIndex, "voicedMarks")
    }

    fun updateSelectedMarks(pageIndex: Int) {
        notifyItemChanged(pageIndex, "selectedMarks")
    }

    fun updateConfirmedMarks(pageIndex: Int) {
        notifyItemChanged(pageIndex, "confirmedMarks")
    }

    fun updateSearchMarks(pageIndex: Int) {
        notifyItemChanged(pageIndex, "searchMarks")
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

        if (bitmap != null && !bitmap.isRecycled) {
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
            bitmaps.remove(position)
            imageView.setImageDrawable(null)
        }

        overlay.layoutParams.width = pageSizes[position].width
        overlay.layoutParams.height = pageSizes[position].height
        overlay.requestLayout()

        val marks = selectedMarksOfPage[position]?.screenMarks
        overlay.clearSelectedMarks()
        marks?.let { overlay.setSelectedMarks(it) }


        val confirmedHighlight = confirmedHighlight[position]
        overlay.clearConfirmMarks()
        confirmedHighlight?.forEach { mark ->
            mark.confirmId?.let {
                overlay.setConfirmMarks(
                    it,
                    mark.screenMarks,
                    mark.color,
                    mark.contentNote,
                    mark.isFirstPageMark
                )
            }
        }

        val searchHighlight = searchHighlight[position]
        overlay.clearSearchMarks()
        searchHighlight?.forEach { mark ->
            mark.color?.let { overlay.setSearchMarks(mark.screenMarks,  it ) }
        }


        val voicedMarks = voicedHighlight[position]?.screenMarks
        overlay.clearVoicedMarks()
        voicedMarks?.let { overlay.setVoicedMarks(it) }


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
                        }else {
                            holder.binding.pageImageView.setImageDrawable(null)
                        }
                    }

                    "selectedMarks" -> {
                        val marks = selectedMarksOfPage[position]?.screenMarks ?: emptyList()
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
                                    mark.screenMarks,
                                    mark.color,
                                    mark.contentNote,
                                    mark.isFirstPageMark
                                )
                            }
                        }
                    }

                    "searchMarks" -> {
                        val searchHighlight = searchHighlight[position]
                        overlay.clearSearchMarks()
                        searchHighlight?.forEach { mark ->
                            mark.color?.let { overlay.setSearchMarks(mark.screenMarks, it) }
                        }
                    }

                    "voicedMarks" -> {
                        val voicedMarks = voicedHighlight[position]?.screenMarks ?: emptyList()
                        overlay.setVoicedMarks(voicedMarks)
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


    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        val img = holder.binding.pageImageView
        img.setImageDrawable(null)
    }
}
