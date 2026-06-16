package com.zenread.book.presentation.pdf.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.zenread.book.R
import com.zenread.book.R.id.ic_back_btn
import com.zenread.book.presentation.pdf.MarksState

class MarksOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val highlightPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.selected_color_default)
        alpha = 60
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    data class Highlight(
        val rectList: List<RectF>,
        val marksState: MarksState,
        val id: Long? = null,
        var color: Int? = null,
        var contentNote: Boolean = false,
        var isFirstPageMark: Boolean = false
    )

    private var highlights = mutableListOf<Highlight>()

//    private var startPointer: PointF? = null
//    private var endPointer: PointF? = null

    private var sizePointer: Int = 60

    private var isBookmark = false

//    private var startPointerBitmap: Bitmap? = null
//    private var endPointerBitmap: Bitmap? = null
//
//    private var startPointerRect: RectF? = null
//    private var endPointerRect: RectF? = null

    fun setSelectedMarks(marks: List<RectF>) {
        clearSelectedMarks()
        if (marks.isNotEmpty()) highlights.add(Highlight(marks, MarksState.LONG_PRESSED))
        invalidate()
    }

    fun setIsBookmark() {
        isBookmark = true
    }

    fun setVoicedMarks(marks: List<RectF>) {
        clearVoicedMarks()
        if (marks.isNotEmpty()) highlights.add(Highlight(marks, MarksState.VOICED))
        invalidate()
    }

    fun setConfirmMarks(
        confirmId: Long,
        marks: List<RectF>,
        color: Int?,
        contentNote: String? = null,
        isFirstPageMark: Boolean = false
    ) {
        if (marks.isNotEmpty()) {
            if (!highlights.any { it.id == confirmId }) {
                highlights.add(
                    Highlight(
                        marks,
                        MarksState.CONFIRM,
                        id = confirmId,
                        color = color,
                        isFirstPageMark = isFirstPageMark,
                        contentNote = !contentNote.isNullOrBlank()
                    )
                )
            } else {
                val highlight = highlights.find { it.id == confirmId }
                highlight?.color = color
                highlight?.contentNote = !contentNote.isNullOrBlank()

            }
        }
        invalidate()
    }

    fun setSearchMarks(marks: List<RectF>, color: Int) {
        highlights.add(
            Highlight(
                marks,
                MarksState.SEARCH,
                color = color
            )
        )
        invalidate()
    }

    fun removeHighlight(id: Long) {
        highlights.removeIf { id == it.id }
        invalidate()
    }

    fun clearSelectedMarks() {
        highlights.filter { it.marksState == MarksState.LONG_PRESSED }
            .forEach { highlights.remove(it) }
        invalidate()
    }

    fun clearVoicedMarks() {
        highlights.filter { it.marksState == MarksState.VOICED }
            .forEach { highlights.remove(it) }
        invalidate()
    }

    fun clearConfirmMarks() {
        highlights.filter { it.marksState == MarksState.CONFIRM }.forEach {
            highlights.remove(it)
        }
        invalidate()
    }

    fun clearSearchMarks() {
        highlights.filter { it.marksState == MarksState.SEARCH }.forEach {
            highlights.remove(it)
        }
        invalidate()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // highlight
        for (highlight in highlights) {
            highlightPaint.apply {
                color = highlight.color ?: ContextCompat.getColor(
                    context,
                    if (highlight.marksState == MarksState.VOICED) {
                        R.color.lavender
                    } else {
                        R.color.selected_color_default
                    }
                )
                alpha = if (highlight.marksState == MarksState.LONG_PRESSED) 60 else 80
            }

            for (rect in highlight.rectList) {
                canvas.drawRect(rect, highlightPaint)
            }

            if (highlight.isFirstPageMark && highlight.contentNote) {
                val firstRect = highlight.rectList.first()
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_notes)
                drawable?.let {
                    val iconSize = sizePointer / 3
                    val left = firstRect.left.toInt() - (iconSize / 2)
                    val top = firstRect.top.toInt() - (iconSize / 2)
                    val right = left + iconSize
                    val bottom = top + iconSize

                    it.setBounds(left, top, right, bottom)
                    it.draw(canvas)
                }
            }
        }

//        if (isBookmark) {
           drawBookmark(canvas)

//        startPointer?.let { sp ->
//            val bmp = getStartPointerBitmap()
//            val w = bmp.width
//            val h = bmp.height
//            val left = sp.x - w
//            val top = sp.y
//            startPointerRect = RectF(left, top, left + w, top + h)
//            canvas.drawBitmap(bmp, left, top, null)
//        }
//
//        endPointer?.let { ep ->
//            val bmp = getEndPointerBitmap()
//            val w = bmp.width
//            val h = bmp.height
//            val left = ep.x
//            val top = ep.y
//            endPointerRect = RectF(left, top, left + w, top + h)
//            canvas.drawBitmap(bmp, left, top, null)
//        }
    }
    private val bookmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D8FF1")
        style = Paint.Style.FILL
    }

    private fun drawBookmark(canvas: Canvas) {
        val w = 36.dp()
        val h = 54.dp()

        val left = width - w

        val path = Path().apply {
            moveTo(left.toFloat(), 0f)
            lineTo(width.toFloat(), 0f)
            lineTo(width.toFloat(), h.toFloat())
            lineTo((left + w / 2).toFloat(), (h - 12.dp()).toFloat())
            lineTo(left.toFloat(), h.toFloat())
            close()
        }

        canvas.drawPath(path, bookmarkPaint)
    }
    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
//    private fun getStartPointerBitmap(): Bitmap {
//        if (startPointerBitmap == null) {
//            startPointerBitmap =
//                getBitmapFromDrawable(context, R.drawable.ic_start_pointer, sizePointer)
//        }
//        return startPointerBitmap!!
//    }
//
//    private fun getEndPointerBitmap(): Bitmap {
//        if (endPointerBitmap == null) {
//            endPointerBitmap =
//                getBitmapFromDrawable(context, R.drawable.ic_end_pointer, sizePointer)
//        }
//        return endPointerBitmap!!
//    }

//    @SuppressLint("UseKtx")
//    private fun getBitmapFromDrawable(context: Context, drawableId: Int, size: Int): Bitmap {
//        val drawable = ContextCompat.getDrawable(context, drawableId)!!
//        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
//        val canvas = Canvas(bitmap)
//        drawable.setBounds(0, 0, canvas.width, canvas.height)
//        drawable.draw(canvas)
//        return Bitmap.createScaledBitmap(bitmap, size, size, true)
//    }

//    override fun onDetachedFromWindow() {
//        super.onDetachedFromWindow()
//        startPointerBitmap?.recycle()
//        endPointerBitmap?.recycle()
//        startPointerBitmap = null
//        endPointerBitmap = null
//    }
}
