package com.zenread.book.presentation.pdf.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import android.widget.OverScroller
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.zenread.book.core.base.BaseActivity
import com.zenread.book.databinding.ActivityPdfReadBinding
import com.zenread.book.domain.model.Book
import com.zenread.book.domain.model.WordInfo
import com.zenread.book.presentation.pdf.PointerIndex
import com.zenread.book.presentation.pdf.ScrollLinearLayoutManager
import com.zenread.book.presentation.pdf.adapter.PdfReadAdapter
import com.zenread.book.presentation.pdf.model.PdfReadViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class PdfReadActivity : BaseActivity<ActivityPdfReadBinding>() {

    private val viewModel: PdfReadViewModel by viewModels()

    private lateinit var layoutManager: ScrollLinearLayoutManager

    private lateinit var adapter: PdfReadAdapter

    private var startPointer: PointerIndex? = null

    private var endPointer: PointerIndex? = null

    var zoomMode = false

    override fun inflateBinding(layoutInflater: LayoutInflater) =
        ActivityPdfReadBinding.inflate(layoutInflater)

    override fun setupView(savedInstanceState: Bundle?) {
        PDFBoxResourceLoader.init(application)

        val book: Book? = intent.getParcelableExtra("book_data")
        val uri = book?.uri?.toUri() ?: return showError("No PDF URI provided")

        viewModel.initialDiskCacheManager(book.id, book.title)

        val recyclerView = binding.pdfRecyclerView
        layoutManager = ScrollLinearLayoutManager(this, RecyclerView.VERTICAL, false)

        recyclerView.layoutManager = layoutManager

        val screenWidth = Resources.getSystem().displayMetrics.widthPixels

        adapter = PdfReadAdapter(emptyList())
        recyclerView.adapter = adapter

        // Load PDF
        viewModel.loadPdf(uri, screenWidth) {
            adapter.updatePageSizes(viewModel.pageSizes)
        }

        var currentPage = 0
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val first = lm.findFirstVisibleItemPosition()
                val last = lm.findLastVisibleItemPosition()
                if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return

                val center = (first + last) / 2
                if (currentPage == 0 || abs(center - currentPage) >= 2) {
                    currentPage = center
                    viewModel.preload(center, windowSize = 10)

                }
            }
        })
        binding.ivTemp.isVisible = false
        scroller = OverScroller(this)
        var ignoreNextMove = false
        var dragOffset: PointF? = null
        var activePointerType: String? = null
        val gestureDetector =
            GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    val child = binding.pdfRecyclerView.findChildViewUnder(e.x, e.y)
                    if (child != null) {
                        val pageIndex = binding.pdfRecyclerView.getChildAdapterPosition(child)

                        val coordinatesPdfTouch = viewModel.coordinatesPdfTouch(
                            child.width,
                            child.height,
                            e.x,
                            e.y - child.y,
                            pageIndex,
                        )
                        if (coordinatesPdfTouch.isNotEmpty()) {
                            val sorted = coordinatesPdfTouch.sortedWith(
                                compareBy<RectF> { it.top }
                                    .thenBy { it.left }
                            )

                            if (startPointer != null && endPointer != null) {
                                adapter.hideStartPointer(startPointer!!.pageIndex)
                                adapter.hideEndPointer(endPointer!!.pageIndex)
                            }

                            val wordInAdapter = adapter.pageMarks
                            wordInAdapter.keys.forEach {
                                adapter.updateMarks(it, emptyList())
                            }

                            startPointer = PointerIndex(
                                pageIndex,
                                PointF(sorted.first().left, sorted.first().bottom)
                            )
                            endPointer = PointerIndex(
                                pageIndex,
                                PointF(sorted.last().right, sorted.last().bottom)
                            )

                            if (coordinatesPdfTouch.isNotEmpty()) {
                                adapter.updatePointer(startPointer!!, endPointer!!)
                                adapter.updateMarks(pageIndex, coordinatesPdfTouch)
                            }
                        }

                    }
                }
            })

        binding.gestureOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!zoomMode) {
                        // Dừng fling nếu đang chạy
                        if (!scroller.isFinished) scroller.abortAnimation()

                        lastX = event.rawX
                        lastY = event.rawY
                        lastTime = event.eventTime

                        prevX = lastX
                        prevY = lastY
                        prevTime = lastTime

                        posX = binding.container.translationX
                        posY = binding.container.translationY

                        val child = binding.pdfRecyclerView.findChildViewUnder(event.x, event.y)
                        if (child != null) {
                            val pageIndex = binding.pdfRecyclerView.getChildAdapterPosition(child)

                            // 2. Tính toạ độ local trong page (tương đối với view con)
                            val childLocation = IntArray(2)
                            child.getLocationOnScreen(childLocation)

                            val result = adapter.isTouchOnPointerAt(
                                binding.pdfRecyclerView,
                                pageIndex,
                                event.x,
                                event.y - child.top
                            )
                            when (result?.first) {
                                "start" -> {
                                    val offset = result.second
                                    binding.highlightPointerView.setStartPointerPosition(
                                        PointF(
                                            event.x - offset.x,
                                            event.y - offset.y
                                        )
                                    )
                                    dragOffset = offset
                                    activePointerType = "start"
                                }

                                "end" -> {
                                    Log.d(
                                        "PdfDebug",
                                        "Touch on END pointer at page $pageIndex at  ${result.second}"
                                    )
                                    val offset = result.second
                                    binding.highlightPointerView.setEndPointerPosition(
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

                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (!zoomMode && event.pointerCount >= 2) {
                        adapter.hideStartPointer(startPointer!!.pageIndex)
                        adapter.hideEndPointer(endPointer!!.pageIndex)
                        recyclerView.post {
                            val (bmp, offset) = captureItemsWithOffset(binding.pdfRecyclerView)
                            binding.ivTemp.apply {
                                scaleType = ImageView.ScaleType.MATRIX
                                setImageBitmap(bmp)
                                val matrix = Matrix()
                                matrix.postTranslate(0f, -offset.toFloat())
                                imageMatrix = matrix
                            }
                            binding.ivTemp.isVisible = true
                            binding.ivTemp.translationX = binding.container.translationX
                            binding.ivTemp.translationY = binding.container.translationY
                            zoomMode = true
                        }

                    }
                    binding.ivTemp.dispatchTouchEvent(event)
                }

                MotionEvent.ACTION_MOVE -> {
                    if (ignoreNextMove) {
                        ignoreNextMove = false
                        return@setOnTouchListener true
                    }
                    if (zoomMode && event.pointerCount >= 2) {
                        // gửi cho ivTemp xử lý pinch zoom
                        binding.ivTemp.dispatchTouchEvent(event)
                    } else if (!zoomMode) {
                        if (dragOffset != null && activePointerType != null) {

                            val newX = event.x - dragOffset!!.x
                            val newY = event.y - dragOffset!!.y
                            when (activePointerType) {
                                "start" -> {
                                    binding.highlightPointerView.setStartPointerPosition(
                                        PointF(
                                            newX,
                                            newY
                                        )
                                    )
                                    marksInItemOverlay(true, newX, newY)

                                }

                                "end" -> {
                                    binding.highlightPointerView.setEndPointerPosition(
                                        PointF(
                                            newX,
                                            newY
                                        )
                                    )
                                    marksInItemOverlay(false, newX, newY)
                                }
                            }
                        } else {
                            val dx = event.rawX - lastX
                            val dy = event.rawY - lastY

                            val containerW = binding.container.width
                            val containerH = binding.container.height

                            val scaledW = containerW * currentScale
                            val scaledH = containerH * currentScale

                            val minX = if (scaledW > containerW) -(scaledW - containerW) / 2 else 0f
                            val maxX = if (scaledW > containerW) (scaledW - containerW) / 2 else 0f

                            val minY = if (scaledH > containerH) -(scaledH - containerH) / 2 else 0f
                            val maxY = if (scaledH > containerH) (scaledH - containerH) / 2 else 0f

                            // cộng dồn vào pos
                            posX += dx
                            posY += dy

                            // clamp trong giới hạn
                            posX = posX.coerceIn(minX, maxX)

                            // apply translation
                            binding.container.translationX = posX
                            if (posY in minY..maxY) {
                                binding.container.translationY = posY
                            } else {
                                binding.pdfRecyclerView.scrollBy(0, -dy.toInt())
                            }


                            // lưu lại vị trí để tính velocity sau này (nếu fling)
                            prevX = lastX
                            prevY = lastY
                            prevTime = lastTime

                            lastX = event.rawX
                            lastY = event.rawY
                            lastTime = event.eventTime
                        }

                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (zoomMode) {
                        binding.ivTemp.dispatchTouchEvent(event)
                        if (event.pointerCount <= 1) {
                            zoomMode = false
                            binding.ivTemp.isVisible = false
                        }
                        if (startPointer != null && endPointer != null) {
                            adapter.visibleEndPointer()
                            adapter.visibleStartPointer()
                        }
                    } else {
                        val dt = (lastTime - prevTime).coerceAtLeast(1)
                        var vx = ((lastX - prevX) / dt) * 800
                        var vy = ((lastY - prevY) / dt) * 800

                        val boost = 2f
                        vx *= boost
                        vy *= boost

                        fling(vx.toInt(), vy.toInt())
                    }
                    if (dragOffset != null) {
                        activePointerType = null
                        dragOffset = null

                        adapter.updatePointer(startPointer!!, endPointer!!)

                        binding.highlightPointerView.removeStartPointer()
                        binding.highlightPointerView.removeEndPointer()
                    }


                    // đánh dấu để bỏ qua MOVE kế tiếp
                    ignoreNextMove = true
                }
            }
            true
        }

        binding.ivTemp.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            true
        }
    }

    private var lastScrollerY = 0f
    private fun fling(vx: Int, vy: Int) {
        val containerW = binding.container.width
        val scaledW = containerW * currentScale

        val minX = if (scaledW > containerW) -(scaledW - containerW) / 2 else 0f
        val maxX = if (scaledW > containerW) (scaledW - containerW) / 2 else 0f

        scroller.fling(
            posX.toInt(),
            posY.toInt(),
            vx, vy,
            minX.toInt(), maxX.toInt(),
            Int.MIN_VALUE, Int.MAX_VALUE
        )

        lastScrollerY = scroller.startY.toFloat()
        binding.container.postOnAnimation(flingRunnable)
    }

    private fun marksInItemOverlay(isStartPointer: Boolean, x: Float, y: Float) {
        val child = binding.pdfRecyclerView.findChildViewUnder(x, y)
        if (child != null) {
            val pageIndex = binding.pdfRecyclerView.getChildAdapterPosition(child)
            //fix
            val wordsInPointer = if (isStartPointer) {
                startPointer = PointerIndex(pageIndex, PointF(x, y - child.y))
                val endViewHolder =
                    binding.pdfRecyclerView.findViewHolderForAdapterPosition(endPointer!!.pageIndex)
                val endView = endViewHolder?.itemView
                viewModel.textParserPointer(
                    startPointer!!,
                    endPointer!!,
                    child.width,
                    child.height,
                    endView!!.width,
                    endView.height
                )
            } else {
                endPointer = PointerIndex(pageIndex, PointF(x, y - child.y))
                val startViewHolder =
                    binding.pdfRecyclerView.findViewHolderForAdapterPosition(startPointer!!.pageIndex)
                val startView = startViewHolder?.itemView
                viewModel.textParserPointer(
                    startPointer!!,
                    endPointer!!,
                    startView!!.width,
                    startView.height,
                    child.width,
                    child.height,
                )
            }


            if (wordsInPointer.isNotEmpty()) {

                val wordsByPage: Map<Int, List<WordInfo>> = wordsInPointer.groupBy { it.pageIndex }
                val wordInAdapter = adapter.pageMarks

                wordInAdapter.keys.filterNot { wordsByPage.containsKey(it) }.forEach {
                    adapter.updateMarks(it, emptyList())
                }

                var firstRect: RectF? = null
                var lastRect: RectF? = null

                for ((page, words) in wordsByPage) {
                    val viewHolder = binding.pdfRecyclerView.findViewHolderForAdapterPosition(page)
                    val itemView = viewHolder?.itemView
                    val wordsInPage =
                        viewModel.pdfToItemRect(
                            words.map { it.rect },
                            itemView!!.width,
                            itemView.height,
                            page
                        )

                    if (firstRect == null && wordsInPage.isNotEmpty()) {
                        firstRect = wordsInPage.first()
                    }

                    if (wordsInPage.isNotEmpty()) {
                        lastRect = wordsInPage.last()
                    }

                    adapter.updateMarks(page, wordsInPage)
                }

                if (isStartPointer) {
                    if (startPointer!!.pageIndex > wordsInPointer.last().pageIndex) {
                        startPointer = PointerIndex(
                            wordsInPointer.last().pageIndex,
                            PointF(
                                lastRect!!.right,
                                lastRect.bottom
                            )
                        )
                    } else if (startPointer!!.pageIndex < wordsInPointer.first().pageIndex) {
                        startPointer = PointerIndex(
                            wordsInPointer.first().pageIndex,
                            PointF(
                                firstRect!!.left,
                                firstRect.bottom
                            )
                        )
                    } else if (startPointer!!.pageIndex > endPointer!!.pageIndex) {
                        startPointer = PointerIndex(
                            startPointer!!.pageIndex,
                            PointF(
                                lastRect!!.right,
                                lastRect.bottom
                            )
                        )
                    } else if (startPointer!!.pageIndex == endPointer!!.pageIndex) {
                        if (wordsInPointer.map { it.lineYKey }
                                .distinct().size == 1 && startPointer!!.pointF.x > endPointer!!.pointF.x) {
                            startPointer = PointerIndex(
                                startPointer!!.pageIndex,
                                PointF(
                                    lastRect!!.right,
                                    lastRect.bottom
                                )
                            )
                        } else if (wordsInPointer.first().columnGroupId != null && wordsInPointer.first().columnGroupId == wordsInPointer.last().columnGroupId
                            && startPointer!!.pointF.x > endPointer!!.pointF.x
                        ) {
                            startPointer = PointerIndex(
                                startPointer!!.pageIndex,
                                PointF(
                                    lastRect!!.right,
                                    lastRect.bottom
                                )
                            )
                        } else if (startPointer!!.pointF.y > endPointer!!.pointF.y) {
                            startPointer = PointerIndex(
                                startPointer!!.pageIndex,
                                PointF(
                                    lastRect!!.right,
                                    lastRect.bottom
                                )
                            )
                        } else {
                            startPointer = PointerIndex(
                                startPointer!!.pageIndex,
                                PointF(
                                    firstRect!!.left,
                                    firstRect.bottom
                                )
                            )
                        }
                    } else {
                        startPointer = PointerIndex(
                            startPointer!!.pageIndex,
                            PointF(
                                firstRect!!.left,
                                firstRect.bottom
                            )
                        )
                    }
                } else {
                    if (endPointer!!.pageIndex > wordsInPointer.last().pageIndex) {
                        endPointer = PointerIndex(
                            wordsInPointer.last().pageIndex,
                            PointF(
                                lastRect!!.right,
                                lastRect.bottom
                            )
                        )
                    } else if (endPointer!!.pageIndex < wordsInPointer.first().pageIndex) {
                        endPointer = PointerIndex(
                            wordsInPointer.first().pageIndex,
                            PointF(
                                firstRect!!.left,
                                firstRect.bottom
                            )
                        )
                    } else if (endPointer!!.pageIndex < startPointer!!.pageIndex) {
                        endPointer = PointerIndex(
                            endPointer!!.pageIndex,
                            PointF(
                                firstRect!!.left,
                                firstRect.bottom
                            )
                        )
                    } else if (endPointer!!.pageIndex == startPointer!!.pageIndex) {
                        if (wordsInPointer.map { it.lineYKey }.distinct().size == 1
                            && wordsInPointer.first().columnIndex == wordsInPointer.last().columnIndex
                        ) {
                            if (startPointer!!.pointF.x > endPointer!!.pointF.x) {
                                endPointer = PointerIndex(
                                    endPointer!!.pageIndex,
                                    PointF(
                                        firstRect!!.left,
                                        firstRect.bottom
                                    )
                                )
                            } else {
                                endPointer = PointerIndex(
                                    endPointer!!.pageIndex,
                                    PointF(
                                        lastRect!!.right,
                                        lastRect.bottom
                                    )
                                )
                            }

                        } else if (wordsInPointer.first().columnGroupId != null
                            && wordsInPointer.first().columnGroupId == wordsInPointer.last().columnGroupId
                        ) {
                            if (startPointer!!.pointF.x > endPointer!!.pointF.x){
                                endPointer = PointerIndex(
                                    endPointer!!.pageIndex,
                                    PointF(
                                        firstRect!!.left,
                                        firstRect.bottom
                                    )
                                )
                            }else{
                                endPointer = PointerIndex(
                                    endPointer!!.pageIndex,
                                    PointF(
                                        lastRect!!.right,
                                        lastRect.bottom
                                    )
                                )
                            }

                        } else if (startPointer!!.pointF.y > endPointer!!.pointF.y) {
                            endPointer = PointerIndex(
                                endPointer!!.pageIndex,
                                PointF(
                                    firstRect!!.left,
                                    firstRect.bottom
                                )
                            )
                        } else {
                            endPointer = PointerIndex(
                                endPointer!!.pageIndex,
                                PointF(
                                    lastRect!!.right,
                                    lastRect.bottom
                                )
                            )
                        }
                    } else {
                        endPointer = PointerIndex(
                            endPointer!!.pageIndex,
                            PointF(
                                lastRect!!.right,
                                lastRect.bottom
                            )
                        )
                    }
                }
                Log.v("endPointer", wordsInPointer.last().toString())
                Log.v("startPointer", wordsInPointer.first().toString())
            }
        }
    }

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                val newX = scroller.currX.toFloat()
                val newY = scroller.currY.toFloat()

                val containerW = binding.container.width
                val containerH = binding.container.height

                val scaledW = containerW * currentScale
                val scaledH = containerH * currentScale

                val minX = if (scaledW > containerW) -(scaledW - containerW) / 2 else 0f
                val maxX = if (scaledW > containerW) (scaledW - containerW) / 2 else 0f

                val minY = if (scaledH > containerH) -(scaledH - containerH) / 2 else 0f
                val maxY = if (scaledH > containerH) (scaledH - containerH) / 2 else 0f

                // clamp X
                posX = newX.coerceIn(minX, maxX)
                binding.container.translationX = posX

                // clamp Y
                val deltaY = newY - lastScrollerY
                lastScrollerY = newY

                if (deltaY != 0f) {
                    if (newY in minY..maxY) {
                        binding.container.translationY = newY
                    } else {
                        if (newY > maxY) {
                            binding.container.translationY = maxY
                        } else {
                            binding.container.translationY = minY
                        }
                        binding.pdfRecyclerView.scrollBy(0, -deltaY.toInt())
                    }

                }
                binding.container.postOnAnimation(this)
            }
        }
    }


    private lateinit var scroller: OverScroller

    private var posX = 0f
    private var posY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTime = 0L

    private var prevX = 0f
    private var prevY = 0f
    private var prevTime = 0L

    private val minScale = 1f
    private val maxScale = 3f
    private var currentScale = 1f

    private var lastFocusX = 0f
    private var lastFocusY = 0f


    private val scaleDetector by lazy {
        ScaleGestureDetector(
            this,
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

                    binding.ivTemp.scaleX = currentScale
                    binding.ivTemp.scaleY = currentScale

                    binding.ivTemp.translationX += deltaX
                    binding.ivTemp.translationY += deltaY

                    val imageWidth = binding.ivTemp.width * binding.ivTemp.scaleX
                    val imageHeight = binding.ivTemp.height * binding.ivTemp.scaleY


                    val viewWidth = binding.ivTemp.width.toFloat()
                    val viewHeight = binding.ivTemp.height.toFloat()

                    val maxTransX = maxOf(0f, (imageWidth - viewWidth) / 2)
                    val maxTransY = maxOf(0f, (imageHeight - viewHeight) / 2)

                    binding.ivTemp.translationX =
                        binding.ivTemp.translationX.coerceIn(-maxTransX, maxTransX)
                    binding.ivTemp.translationY =
                        binding.ivTemp.translationY.coerceIn(-maxTransY, maxTransY)

                    lastFocusX = detector.focusX
                    lastFocusY = detector.focusY

                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    layoutManager.scrollEnabled = false
                    binding.container.scaleX = binding.ivTemp.scaleX
                    binding.container.scaleY = binding.ivTemp.scaleY

                    binding.container.translationX = binding.ivTemp.translationX
                    binding.container.translationY = binding.ivTemp.translationY

                    adapter.reSizePointer(
                        startPointer!!.pageIndex,
                        endPointer!!.pageIndex,
                        (60 / (currentScale)).toInt()
                    )
                    
                    binding.highlightPointerView.reSizePointer((60 / (currentScale)).toInt())

                    binding.container.postDelayed({
                        layoutManager.scrollEnabled = true
                    }, 200)
                }

            })
    }

    fun captureItemsWithOffset(recyclerView: RecyclerView): Pair<Bitmap, Int> {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
            return Pair(createBitmap(1, 1), 0)
        }

        // Tính tổng chiều cao các item
        var totalHeight = 0
        for (i in firstVisible..lastVisible) {
            layoutManager.findViewByPosition(i)?.let {
                totalHeight += it.height
            }
        }

        val bitmap = createBitmap(recyclerView.width, totalHeight)
        val canvas = Canvas(bitmap)

        var offsetY = 0
        var firstItemTopOffset = 0

        for (i in firstVisible..lastVisible) {
            val child = layoutManager.findViewByPosition(i) ?: continue

            if (i == firstVisible) {
                // Lưu lại khoảng đã scroll trong item đầu tiên
                firstItemTopOffset = -child.top
            }

            canvas.withTranslation(0f, offsetY.toFloat()) {
                child.draw(canvas)
            }

            offsetY += child.height
        }

        // Trả về bitmap + offset cần dịch chuyển để khớp màn hình hiện tại
        return Pair(bitmap, firstItemTopOffset)
    }


    override fun setupObserver() {
        lifecycleScope.launch {
            viewModel.pageBitmaps.collect { (index, bmp) ->
                adapter.updateBitmap(index, bmp)
            }
        }
    }


    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}