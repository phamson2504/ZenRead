package com.zenread.book.presentation.pdf.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.zenread.book.R

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
        val id: Long? = null,
        var color: Int? = null
    )

    private var highlights = mutableListOf<Highlight>()

    private var startPointer: PointF? = null
    private var endPointer: PointF? = null

    private var sizePointer: Int = 60

    private var startPointerBitmap: Bitmap? = null
    private var endPointerBitmap: Bitmap? = null

    private var startPointerRect: RectF? = null
    private var endPointerRect: RectF? = null

    // ===== MARKS =====
    fun setMarks(marks: List<RectF>) {
        highlights.clear()
        if (marks.isNotEmpty()) highlights.add(Highlight(marks))
        invalidate()
    }

    // ===== POINTERS =====
    fun setStartPointerPosition(startPointer: PointF) {
        this.startPointer = startPointer
        invalidate()
    }

    fun setEndPointerPosition(endPointer: PointF) {
        this.endPointer = endPointer
        invalidate()
    }

    fun removeStartPointer() {
        startPointer = null
        startPointerRect = null
        invalidate()
    }

    fun removeEndPointer() {
        endPointer = null
        endPointerRect = null
        invalidate()
    }

    /**
     * Resize pointer icon theo pixel (ví dụ khi zoom)
     */
    fun reSizePointer(size: Int) {
        if (sizePointer == size) return // tránh re-create không cần thiết
        sizePointer = size

        // Giải phóng bitmap cũ để tránh leak
        startPointerBitmap?.recycle()
        endPointerBitmap?.recycle()

        // Reset bitmap để tạo lại ở lần vẽ tiếp theo
        startPointerBitmap = null
        endPointerBitmap = null

        invalidate()
    }

    fun clearMarks() {
        highlights.clear()
        invalidate()
    }

    // ===== TOUCH DETECTION =====
    fun isTouchOnStartPointer(x: Float, y: Float): PointF? {
        return if (startPointerRect?.contains(x, y) == true) {
            PointF(x - (startPointer?.x ?: 0f), y - (startPointer?.y ?: 0f))
        } else null
    }

    fun isTouchOnEndPointer(x: Float, y: Float): PointF? {
        return if (endPointerRect?.contains(x, y) == true) {
            PointF(x - (endPointer?.x ?: 0f), y - (endPointer?.y ?: 0f))
        } else null
    }

    // ===== DRAW =====
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Vẽ vùng highlight
        for (highlight in highlights) {
            for (rect in highlight.rectList) {
                canvas.drawRect(rect, highlightPaint)
            }
        }

        // Vẽ start pointer
        startPointer?.let { sp ->
            val bmp = getStartPointerBitmap()
            val w = bmp.width
            val h = bmp.height
            val left = sp.x - w
            val top = sp.y
            startPointerRect = RectF(left, top, left + w, top + h)
            canvas.drawBitmap(bmp, left, top, null)
        }

        // Vẽ end pointer
        endPointer?.let { ep ->
            val bmp = getEndPointerBitmap()
            val w = bmp.width
            val h = bmp.height
            val left = ep.x
            val top = ep.y
            endPointerRect = RectF(left, top, left + w, top + h)
            canvas.drawBitmap(bmp, left, top, null)
        }
    }

    // ===== LAZY LOAD BITMAP =====
    private fun getStartPointerBitmap(): Bitmap {
        if (startPointerBitmap == null) {
            startPointerBitmap = getBitmapFromDrawable(context, R.drawable.ic_start_pointer, sizePointer)
        }
        return startPointerBitmap!!
    }

    private fun getEndPointerBitmap(): Bitmap {
        if (endPointerBitmap == null) {
            endPointerBitmap = getBitmapFromDrawable(context, R.drawable.ic_end_pointer, sizePointer)
        }
        return endPointerBitmap!!
    }

    @SuppressLint("UseKtx")
    private fun getBitmapFromDrawable(context: Context, drawableId: Int, size: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)!!
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return Bitmap.createScaledBitmap(bitmap, size, size, true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Giải phóng bitmap khi view bị destroy
        startPointerBitmap?.recycle()
        endPointerBitmap?.recycle()
        startPointerBitmap = null
        endPointerBitmap = null
    }
}
