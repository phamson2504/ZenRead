package com.zenread.book.presentation.pdf.gesture

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.OverScroller
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.zenread.book.core.utils.Constants.POINTER_BORDER_SPACING
import com.zenread.book.presentation.pdf.ScrollLinearLayoutManager

class PdfTouchHandler(
    private val context: Context,
    private val container: View,
    private val ivTemp: ImageView,
    private val overlay: View,
    private val highlightPointerView: HighlightPointerView,
    private val pdfRecyclerView: RecyclerView,
    private val layoutManager: ScrollLinearLayoutManager
) {
    private var listener: OnPdfTouchListener? = null

    interface OnPdfTouchListener {
        fun onLongPress(x: Float, y: Float)
        fun captureItemsWithOffset(): Pair<Bitmap, Int>

        fun marksInItemOverlay(
            isStartPointer: Boolean,
            x: Float,
            y: Float,
            startPointerIndex: PointF,
            endPointerIndex: PointF
        )

        fun showPopupAfterDrag()

        fun tapIsConfirmHighlight(x: Float, y: Float): Boolean
    }


    fun setOnPdfTouchListener(listener: OnPdfTouchListener) {
        this.listener = listener
    }

    private var isTapConfirmHighlight: Boolean? = false

    private var zoomMode = false
    private val scroller = OverScroller(context)

    var currentScale = 1f
    private val minScale = 1f
    private val maxScale = 3f
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private var prevX = 0f
    private var prevY = 0f
    private var prevTime = 0L

    private var posX = 0f
    private var posY = 0f

    private var lastX = 0f
    private var lastY = 0f
    private var lastTime = 0L

    private var lastScrollerY = 0f

    //screen stabilizes after moving
    private var ignoreNextMove = false

    private var startPointer: PointF? = null
    private var endPointer: PointF? = null

    var dragOffset: PointF? = null
    var activePointerType: String? = null

    fun updatePointer(start: PointF?, end: PointF?) {
        startPointer = start
        endPointer = end

        if (startPointer == null && endPointer == null) {
            highlightPointerView.removeStartPointer()
            highlightPointerView.removeEndPointer()
            return
        }

        highlightPointerView.setStartPointerPosition(startPointer!!)
        highlightPointerView.setEndPointerPosition(endPointer!!)
    }

    private val gestureDetectorOverlay =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (startPointer != null && endPointer != null) {
                    highlightPointerView.removeStartPointer()
                    highlightPointerView.removeEndPointer()
                    highlightPointerView.setStartInverted(false)
                    highlightPointerView.setEndInverted(false)
                }
                listener?.onLongPress(e.x, e.y)
            }
        })

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        overlay.setOnTouchListener { _, event ->
            gestureDetectorOverlay.onTouchEvent(event)
            handleTouch(event)
        }

        ivTemp.setOnTouchListener { _, event ->
            scaleDetectorItem.onTouchEvent(event)
            true
        }
        pdfRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (endPointer != null && startPointer != null) {
                    val start = PointF(startPointer!!.x, startPointer!!.y + -dy)
                    val end = PointF(endPointer!!.x, endPointer!!.y + -dy)
                    updatePointer(start, end)
                }
            }
        })
    }

    fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!zoomMode) {
                    // Stop fling if it is running
                    if (!scroller.isFinished) scroller.abortAnimation()

                    lastX = event.rawX
                    lastY = event.rawY
                    lastTime = event.eventTime

                    prevX = lastX
                    prevY = lastY
                    prevTime = lastTime

                    posX = container.translationX
                    posY = container.translationY

                    isTapConfirmHighlight = listener?.tapIsConfirmHighlight(event.x, event.y)

                    val result = isTouchOnPointerAt(event.x, event.y)
                    if (result != null) {
                        when (result.first) {
                            "start" -> {
                                val offset = result.second
                                highlightPointerView.setStartPointerPosition(
                                    PointF(
                                        event.x - offset.x,
                                        event.y - offset.y
                                    )
                                )
                                dragOffset = offset
                                activePointerType = "start"
                            }

                            "end" -> {
                                val offset = result.second
                                highlightPointerView.setEndPointerPosition(
                                    PointF(
                                        event.x - offset.x,
                                        event.y - offset.y
                                    )
                                )
                                dragOffset = offset
                                activePointerType = "end"
                            }
                        }
                    }
                }

                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!zoomMode && event.pointerCount >= 2) {
                    val (bmp, offset) = listener!!.captureItemsWithOffset()
                    ivTemp.apply {
                        scaleType = ImageView.ScaleType.MATRIX
                        setImageBitmap(bmp)
                        val matrix = Matrix()
                        matrix.postTranslate(0f, -offset.toFloat())
                        imageMatrix = matrix
                    }
                    ivTemp.isVisible = true
                    ivTemp.translationX = container.translationX
                    ivTemp.translationY = container.translationY
                    zoomMode = true
                }
                ivTemp.dispatchTouchEvent(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (ignoreNextMove) {
                    ignoreNextMove = false
                    return true
                } else if (zoomMode && event.pointerCount >= 2) {
                    // send to ivTemp to handle pinch zoom
                    ivTemp.dispatchTouchEvent(event)
                } else if (!zoomMode) {
                    if (dragOffset != null && activePointerType != null && startPointer!=null) {

                        val newX = event.x - dragOffset!!.x
                        val newY = event.y - dragOffset!!.y
                        when (activePointerType) {
                            "start" -> {
                                highlightPointerView.setStartPointerPosition(
                                    PointF(
                                        newX,
                                        newY
                                    )
                                )
                                listener?.marksInItemOverlay(
                                    true,
                                    newX,
                                    newY,
                                    startPointer!!,
                                    endPointer!!
                                )
                            }

                            "end" -> {
                                highlightPointerView.setEndPointerPosition(
                                    PointF(
                                        newX,
                                        newY
                                    )
                                )
                                listener?.marksInItemOverlay(
                                    false,
                                    newX,
                                    newY,
                                    startPointer!!,
                                    endPointer!!
                                )
                            }
                        }
                    } else {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY

                        val containerW = container.width
                        val containerH = container.height

                        val scaledW = containerW * currentScale
                        val scaledH = containerH * currentScale

                        val minX = if (scaledW > containerW) -(scaledW - containerW) / 2 else 0f
                        val maxX = if (scaledW > containerW) (scaledW - containerW) / 2 else 0f

                        val minY = if (scaledH > containerH) -(scaledH - containerH) / 2 else 0f
                        val maxY = if (scaledH > containerH) (scaledH - containerH) / 2 else 0f

                        // cộng dồn vào pos
                        posX += dx
                        posY += dy

                        // clamp within limits
                        posX = posX.coerceIn(minX, maxX)

                        // apply translation
                        container.translationX = posX

                        if (posY in minY..maxY) {
                            container.translationY = posY
                        } else {
                            pdfRecyclerView.scrollBy(0, -dy.toInt())
                        }

                        // save the position to calculate velocity later (if flinging)
                        prevX = lastX
                        prevY = lastY
                        prevTime = lastTime

                        lastX = event.rawX
                        lastY = event.rawY
                        lastTime = event.eventTime
                    }
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                if (zoomMode) {
                    ivTemp.dispatchTouchEvent(event)
                    if (event.pointerCount <= 1) {
                        zoomMode = false
                        ivTemp.isVisible = false
                    }
                } else {
                    val dt = (lastTime - prevTime).coerceAtLeast(1)
                    var vx = ((lastX - prevX) / dt) * 1000
                    var vy = ((lastY - prevY) / dt) * 1000

                    val boost = 2f
                    vx *= boost
                    vy *= boost

                    fling(vx.toInt(), vy.toInt())
                }
                if (dragOffset != null) {
                    activePointerType = null
                    dragOffset = null
                    if (startPointer != null && endPointer != null) {
                        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
                        if (startPointer!!.x < POINTER_BORDER_SPACING) {
                            highlightPointerView.setStartInverted(true)
                        } else if (startPointer!!.x > (screenWidth - POINTER_BORDER_SPACING)) {
                            highlightPointerView.setStartInverted(false)
                        }
                        if (endPointer!!.x < POINTER_BORDER_SPACING) {
                            highlightPointerView.setEndInverted(false)
                        } else if (endPointer!!.x > (screenWidth - POINTER_BORDER_SPACING)) {
                            highlightPointerView.setEndInverted(true)
                        }
                    }
                }
            }
        }

        return false
    }

    private fun fling(vx: Int, vy: Int) {
        val scaleFactor = 1f + (currentScale - 1f) * 1.5f
        val vxAdj = (vx / scaleFactor).toInt()
        val vyAdj = (vy / scaleFactor).toInt()

        val containerW = container.width
        val scaledW = containerW * currentScale

        val minX = if (scaledW > containerW) -(scaledW - containerW) / 2 else 0f
        val maxX = if (scaledW > containerW) (scaledW - containerW) / 2 else 0f

        scroller.fling(
            posX.toInt(),
            posY.toInt(),
            vxAdj, vyAdj,
            minX.toInt(), maxX.toInt(),
            Int.MIN_VALUE, Int.MAX_VALUE
        )

        lastScrollerY = scroller.startY.toFloat()
        container.postOnAnimation(flingRunnable)
    }

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                val newX = scroller.currX.toFloat()
                val newY = scroller.currY.toFloat()

                val containerW = container.width
                val containerH = container.height

                val scaledW = containerW * currentScale
                val scaledH = containerH * currentScale

                val minX = if (scaledW > containerW) -(scaledW - containerW) / 2 else 0f
                val maxX = if (scaledW > containerW) (scaledW - containerW) / 2 else 0f

                val minY = if (scaledH > containerH) -(scaledH - containerH) / 2 else 0f
                val maxY = if (scaledH > containerH) (scaledH - containerH) / 2 else 0f

                // clamp X
                posX = newX.coerceIn(minX, maxX)
                container.translationX = posX

                // clamp Y
                val deltaY = newY - lastScrollerY
                lastScrollerY = newY

                if (deltaY != 0f) {
                    if (newY in minY..maxY) {
                        container.translationY = newY
                    } else {
                        if (newY > maxY) {
                            container.translationY = maxY
                        } else {
                            container.translationY = minY
                        }
                        pdfRecyclerView.scrollBy(0, -deltaY.toInt())
                    }

                }
                container.postOnAnimation(this)
            } else {
                if (((startPointer != null && endPointer != null) || isTapConfirmHighlight == true) && scroller.isFinished)
                    listener?.showPopupAfterDrag()
            }
        }
    }

    private val scaleDetectorItem by lazy {
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    // Lưu lại vị trí focus ban đầu khi bắt đầu cử chỉ
                    lastFocusX = detector.focusX
                    lastFocusY = detector.focusY
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    var newScale = currentScale * scaleFactor


                    // Giữ scale trong khoảng cho phép
                    newScale = newScale.coerceIn(minScale, maxScale)

                    currentScale = newScale

                    val deltaX = detector.focusX - lastFocusX
                    val deltaY = detector.focusY - lastFocusY

                    ivTemp.scaleX = currentScale
                    ivTemp.scaleY = currentScale

                    ivTemp.translationX += deltaX
                    ivTemp.translationY += deltaY

                    val imageWidth = ivTemp.width * ivTemp.scaleX
                    val imageHeight = ivTemp.height * ivTemp.scaleY


                    val viewWidth = ivTemp.width.toFloat()
                    val viewHeight = ivTemp.height.toFloat()

                    val maxTransX = maxOf(0f, (imageWidth - viewWidth) / 2)
                    val maxTransY = maxOf(0f, (imageHeight - viewHeight) / 2)

                    ivTemp.translationX = ivTemp.translationX.coerceIn(-maxTransX, maxTransX)
                    ivTemp.translationY = ivTemp.translationY.coerceIn(-maxTransY, maxTransY)

                    lastFocusX = detector.focusX
                    lastFocusY = detector.focusY

                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    layoutManager.scrollEnabled = false
                    container.scaleX = ivTemp.scaleX
                    container.scaleY = ivTemp.scaleY

                    container.translationX = ivTemp.translationX
                    container.translationY = ivTemp.translationY

                    highlightPointerView.reSizePointer((60 / (currentScale)).toInt())

                    container.postDelayed({
                        layoutManager.scrollEnabled = true
                    }, 200)
                }
            })
    }

    fun isTouchOnPointerAt(
        x: Float,
        y: Float
    ): Pair<String, PointF>? {
        val startOffset = highlightPointerView.isTouchOnStartPointer(x, y)
        val endOffset = highlightPointerView.isTouchOnEndPointer(x, y)

        return when {
            startOffset != null -> {
                "start" to startOffset
            }

            endOffset != null -> {
                "end" to endOffset
            }

            else -> null
        }
    }

//    fun captureItemsWithOffset(recyclerView: RecyclerView): Pair<Bitmap, Int> {
//        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
//
//        val firstVisible = layoutManager.findFirstVisibleItemPosition()
//        val lastVisible = layoutManager.findLastVisibleItemPosition()
//
//        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
//            return Pair(createBitmap(1, 1), 0)
//        }
//
//        // Calculate the total height of the items
//        var totalHeight = 0
//        for (i in firstVisible..lastVisible) {
//            layoutManager.findViewByPosition(i)?.let {
//                totalHeight += it.height
//            }
//        }
//
//        val bitmap = createBitmap(recyclerView.width, totalHeight)
//        val canvas = Canvas(bitmap)
//
//        var offsetY = 0
//        var firstItemTopOffset = 0
//
//        for (i in firstVisible..lastVisible) {
//            val child = layoutManager.findViewByPosition(i) ?: continue
//
//            if (i == firstVisible) {
//                // Save the scroll position in the first item
//                firstItemTopOffset = -child.top
//            }
//
//            canvas.withTranslation(0f, offsetY.toFloat()) {
//                child.draw(canvas)
//            }
//
//            offsetY += child.height
//        }
//
//        // Return the bitmap + offset needed to align with the current screen
//        return Pair(bitmap, firstItemTopOffset)
//    }
}
