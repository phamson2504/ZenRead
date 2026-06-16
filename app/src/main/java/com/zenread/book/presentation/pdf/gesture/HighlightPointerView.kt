package com.zenread.book.presentation.pdf.gesture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.zenread.book.R

class HighlightPointerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var startPointer: PointF? = null
    var endPointer: PointF? = null

    private var startPointerBitmap: Bitmap? = null
    private var endPointerBitmap: Bitmap? = null

    private var startPointerRect: RectF? = null
    private var endPointerRect: RectF? = null

    private var sizePointer: Int = 60

    fun setStartPointerPosition(startPointer: PointF) {
        this.startPointer = startPointer
        invalidate()
    }

    fun setEndPointerPosition(endPointer: PointF) {
        this.endPointer = endPointer
        invalidate()
    }

    fun getSizePointer(): Int {
        return sizePointer
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

    fun removeStartPointer() {
        startPointer = null
        invalidate()
    }

    fun removeEndPointer() {
        endPointer = null
        invalidate()
    }

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

    private var startInverted = false
    private var endInverted = false

    fun setStartInverted(inverted: Boolean) {
        if (startInverted != inverted) {
            startInverted = inverted
            invalidate()
        }
    }

    fun setEndInverted(inverted: Boolean) {
        if (endInverted != inverted) {
            endInverted = inverted
            invalidate()
        }
    }

    fun resetPointer() {
        startInverted = false
        endInverted = false
    }

    override @SuppressLint("DrawAllocation")
    fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        startPointer?.let { sp ->
            val bmp = if (startInverted) getEndPointerBitmap() else getStartPointerBitmap()
            val w = bmp.width
            val h = bmp.height

            val (left, top) = if (startInverted) {
                Pair(sp.x, sp.y)
            } else {
                Pair(sp.x - w, sp.y)
            }
            startPointerRect = RectF(left, top, left + w, top + h)
            canvas.drawBitmap(bmp, left, top, null)
        }

        // --- Vẽ end pointer ---
        endPointer?.let { ep ->
            val bmp = if (endInverted) getStartPointerBitmap() else getEndPointerBitmap()
            val w = bmp.width
            val h = bmp.height

            val (left, top) = if (endInverted) {
                Pair(ep.x - w, ep.y)
            } else {
                Pair(ep.x, ep.y)
            }

            endPointerRect = RectF(left, top, left + w, top + h)
            canvas.drawBitmap(bmp, left, top, null)
        }

    }


    private fun getStartPointerBitmap(): Bitmap {
        if (startPointerBitmap == null) {
            startPointerBitmap =
                getBitmapFromDrawable(context, R.drawable.ic_start_pointer, sizePointer)
        }
        return startPointerBitmap!!
    }

    private fun getEndPointerBitmap(): Bitmap {
        if (endPointerBitmap == null) {
            endPointerBitmap =
                getBitmapFromDrawable(context, R.drawable.ic_end_pointer, sizePointer)
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
}