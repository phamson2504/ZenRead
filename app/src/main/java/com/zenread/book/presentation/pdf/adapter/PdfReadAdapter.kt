package com.zenread.book.presentation.pdf.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.zenread.book.databinding.ItemPdfPageBinding
import com.zenread.book.presentation.pdf.MarksState
import com.zenread.book.presentation.pdf.PageMark
import com.zenread.book.presentation.pdf.PointerIndex
import java.lang.ref.WeakReference

class PdfReadAdapter(
    private var pageSizes: List<Size>
) : RecyclerView.Adapter<PdfReadAdapter.PageViewHolder>() {

    val bitmaps = mutableMapOf<Int, Bitmap>()

    val pageMarks: MutableMap<Int, PageMark> = mutableMapOf()
    var startPointer: PointerIndex? = null
    var endPointer: PointerIndex? = null

    fun updatePointer(
        startPointerIndex: PointerIndex,
        endPointerIndex: PointerIndex
    ) {
        startPointer = startPointerIndex
        endPointer = endPointerIndex
        notifyItemChanged(startPointer!!.pageIndex, "startPointer")
        notifyItemChanged(endPointer!!.pageIndex, "endPointer")
    }

    fun hideStartPointer(pageIndex: Int) {
        val startHolder = getHolderAt(pageIndex)
        startHolder?.binding?.marksOverlay?.removeStartPointer()
    }

    fun hideEndPointer(pageIndex: Int) {
        val endHolder = getHolderAt(pageIndex)
        endHolder?.binding?.marksOverlay?.removeEndPointer()
    }

    fun visibleStartPointer() {
        val startHolder = getHolderAt(startPointer!!.pageIndex)
        startHolder?.binding?.marksOverlay?.setStartPointerPosition(startPointer!!.pointF)
    }

    fun visibleEndPointer() {
        val endHolder = getHolderAt(endPointer!!.pageIndex)
        endHolder?.binding?.marksOverlay?.setEndPointerPosition(endPointer!!.pointF)
    }

    fun reSizePointer(startPointerIndex: Int, endPointerIndex: Int, size: Int) {
        if (startPointerIndex == endPointerIndex) {
            val startHolder = getHolderAt(startPointerIndex)
            startHolder?.binding?.marksOverlay?.reSizePointer(size)
        } else {
            val startHolder = getHolderAt(startPointerIndex)
            startHolder?.binding?.marksOverlay?.reSizePointer(size)
            val endHolder = getHolderAt(endPointerIndex)
            endHolder?.binding?.marksOverlay?.reSizePointer(size)
        }

    }

    fun updateMarks(pageIndex: Int, marks: List<RectF>) {
        if (marks.isEmpty()) {
            pageMarks.remove(pageIndex)
        }else{
            pageMarks[pageIndex] = PageMark(MarksState.LONG_PRESSED, marks)
        }
        notifyItemChanged(pageIndex, "marks")
    }


    inner class PageViewHolder(val binding: ItemPdfPageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun isTouchOnStartPointer(x: Float, y: Float): PointF? {
            return binding.marksOverlay.isTouchOnStartPointer(x, y)
        }

        fun isTouchOnEndPointer(x: Float, y: Float): PointF? {
            return binding.marksOverlay.isTouchOnEndPointer(x, y)
        }

        fun removeStartPointer() {
            binding.marksOverlay.removeStartPointer()
        }

        fun removeEndPointer() {
            binding.marksOverlay.removeEndPointer()
        }
    }

    fun isTouchOnPointerAt(
        recyclerView: RecyclerView,
        pageIndex: Int,
        x: Float,
        y: Float
    ): Pair<String, PointF>? {
        val holder = recyclerView.findViewHolderForAdapterPosition(pageIndex) as? PageViewHolder
            ?: return null
        val startOffset = holder.isTouchOnStartPointer(x, y)
        val endOffset = holder.isTouchOnEndPointer(x, y)

        return when {
            startOffset != null -> {
                holder.removeStartPointer()
                startPointer = null
                "start" to startOffset
            }

            endOffset != null -> {
                holder.removeEndPointer()
                endPointer = null
                "end" to endOffset
            }

            else -> null
        }
    }


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

        val marks = pageMarks[position]?.marks
        if (marks.isNullOrEmpty()) {
            overlay.clearMarks()
        } else {
            overlay.setMarks(marks)
        }

        if (startPointer?.pageIndex == position) {
            overlay.setStartPointerPosition(startPointer!!.pointF)
        } else {
            overlay.removeStartPointer()
        }

        // --- 5️⃣ Hiển thị hoặc reset endPointer ---
        if (endPointer?.pageIndex == position) {
            overlay.setEndPointerPosition(endPointer!!.pointF)
        } else {
            overlay.removeEndPointer()
        }
    }

    private var recyclerViewRef: WeakReference<RecyclerView>? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerViewRef = WeakReference(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerViewRef = null
    }

    private fun getHolderAt(pageIndex: Int): PageViewHolder? {
        val recyclerView = recyclerViewRef?.get() ?: return null
        return recyclerView.findViewHolderForAdapterPosition(pageIndex) as? PageViewHolder
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

                    "marks" -> {
                        val marks = pageMarks[position]?.marks ?: emptyList()
                        overlay.setMarks(marks)
                    }

                    "startPointer" -> {
                        if (startPointer?.pageIndex == position) {
                            startPointer?.let { overlay.setStartPointerPosition(it.pointF) }
                        }
                        if (startPointer == null) {
                            overlay.removeStartPointer()
                        }
                    }

                    "endPointer" -> {
                        if (endPointer?.pageIndex == position) {
                            endPointer?.let { overlay.setEndPointerPosition(it.pointF) }
                        }
                        if (endPointer == null) {
                            overlay.removeEndPointer()
                        }
                    }
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }


    override fun getItemCount(): Int = pageSizes.size

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
