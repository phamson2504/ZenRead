package com.zenread.book.presentation.pdf.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.zenread.book.R
import com.zenread.book.core.base.BaseActivity
import com.zenread.book.core.utils.Constants.POINTER_BORDER_SPACING
import com.zenread.book.databinding.ActivityPdfReadBinding
import com.zenread.book.databinding.DialogAddNoteBinding
import com.zenread.book.domain.model.Book
import com.zenread.book.domain.model.WordInfo
import com.zenread.book.presentation.pdf.MarksState
import com.zenread.book.presentation.pdf.PageMark
import com.zenread.book.presentation.pdf.PointerIndex
import com.zenread.book.presentation.pdf.ScrollLinearLayoutManager
import com.zenread.book.presentation.pdf.adapter.PdfReadAdapter
import com.zenread.book.presentation.pdf.gesture.PdfTouchHandler
import com.zenread.book.presentation.pdf.model.PdfReadViewModel
import com.zenread.book.presentation.pdf.popup.OnTextActionListener
import com.zenread.book.presentation.pdf.popup.PopupTextAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

@AndroidEntryPoint
class PdfReadActivity : BaseActivity<ActivityPdfReadBinding>(),
    PdfTouchHandler.OnPdfTouchListener,
    OnTextActionListener {

    private val viewModel: PdfReadViewModel by viewModels()

    private lateinit var layoutManager: ScrollLinearLayoutManager

    private lateinit var adapter: PdfReadAdapter

    private lateinit var pdfTouchHandler: PdfTouchHandler

    private lateinit var popupTextAction: PopupTextAction

    private var startPointerInPage: PointerIndex? = null
    private var endPointerInPage: PointerIndex? = null

    private var selectedHighlights: MutableMap<Int, PageMark> = mutableMapOf()

    private var confirmedHighlight: MutableMap<Int, MutableList<PageMark>> = mutableMapOf()


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

        adapter = PdfReadAdapter(confirmedHighlight, selectedHighlights, emptyList())
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


        pdfTouchHandler = PdfTouchHandler(
            this,
            binding.container,
            binding.ivTemp,
            binding.gestureOverlay,
            binding.highlightPointerView,
            recyclerView,
            layoutManager

        )
        pdfTouchHandler.setOnPdfTouchListener(this)
        pdfTouchHandler.attach()

        //pop text a action
        popupTextAction = PopupTextAction(this, binding.root)
        popupTextAction.setOnTextActionListener(this)
    }

    override fun marksInItemOverlay(
        isStartPointer: Boolean,
        x: Float,
        y: Float,
        startPointerIndex: PointF,
        endPointerIndex: PointF
    ) {
        val child = binding.pdfRecyclerView.findChildViewUnder(x, y)
        if (child != null) {
            val pageIndex = binding.pdfRecyclerView.getChildAdapterPosition(child)
            var startPointer: PointerIndex? =
                pointerInPage(startPointerIndex.x, startPointerIndex.y)
            if (startPointer == null) startPointer = startPointerInPage

            var endPointer: PointerIndex? = pointerInPage(endPointerIndex.x, endPointerIndex.y)
            if (endPointer == null) endPointer = endPointerInPage

            //fix
            val wordsInPointer = if (isStartPointer) {
                startPointer = PointerIndex(pageIndex, PointF(x, y - child.y))
                val endView = binding.pdfRecyclerView.measureItemAt(endPointer!!.pageIndex)
                viewModel.textParserPointer(
                    startPointer,
                    endPointer,
                    child.width,
                    child.height,
                    endView.width.toInt(),
                    endView.height.toInt()
                )
            } else {
                endPointer = PointerIndex(pageIndex, PointF(x, y - child.y))
                val startView = binding.pdfRecyclerView.measureItemAt(startPointer!!.pageIndex)
                viewModel.textParserPointer(
                    startPointer,
                    endPointer,
                    startView.width.toInt(),
                    startView.height.toInt(),
                    child.width,
                    child.height,
                )
            }


            if (wordsInPointer.isNotEmpty()) {

                val wordsByPage: Map<Int, List<WordInfo>> = wordsInPointer.groupBy { it.pageIndex }
                var firstRect: RectF? = null
                var lastRect: RectF? = null

                for ((page, words) in wordsByPage) {
                    val itemView = binding.pdfRecyclerView.measureItemAt(page)
                    val wordsInPage =
                        viewModel.pdfToItemRect(
                            words.map { it.rect },
                            itemView.width.toInt(),
                            itemView.height.toInt(),
                            page
                        )

                    if (firstRect == null && wordsInPage.isNotEmpty()) {
                        firstRect = wordsInPage.first()
                    }

                    if (wordsInPage.isNotEmpty()) {
                        lastRect = wordsInPage.last()
                    }

                    updateSelectedHighlights(page, wordsInPage, words.map { it.word })
                }

                if (isStartPointer) {
                    if (startPointer.pageIndex > wordsInPointer.last().pageIndex) {
                        startPointer = PointerIndex(
                            wordsInPointer.last().pageIndex,
                            PointF(
                                lastRect!!.right,
                                lastRect.bottom
                            )
                        )
                    } else if (startPointer.pageIndex < wordsInPointer.first().pageIndex) {
                        startPointer = PointerIndex(
                            wordsInPointer.first().pageIndex,
                            PointF(
                                firstRect!!.left,
                                firstRect.bottom
                            )
                        )
                    } else if (startPointer.pageIndex > endPointer.pageIndex) {
                        startPointer = PointerIndex(
                            startPointer.pageIndex,
                            PointF(
                                lastRect!!.right,
                                lastRect.bottom
                            )
                        )
                    } else if (startPointer.pageIndex == endPointer.pageIndex) {
                        if (wordsInPointer.map { it.lineYKey }
                                .distinct().size == 1 && startPointer.pointF.x > endPointer.pointF.x) {
                            startPointer = PointerIndex(
                                startPointer.pageIndex,
                                PointF(
                                    lastRect!!.right,
                                    lastRect.bottom
                                )
                            )
                        } else if (wordsInPointer.first().columnGroupId != null && wordsInPointer.first().columnGroupId == wordsInPointer.last().columnGroupId
                            && startPointer.pointF.x > endPointer.pointF.x
                        ) {
                            startPointer = PointerIndex(
                                startPointer.pageIndex,
                                PointF(
                                    lastRect!!.right,
                                    lastRect.bottom
                                )
                            )
                        } else if (startPointer.pointF.y > endPointer.pointF.y) {
                            startPointer = PointerIndex(
                                startPointer.pageIndex,
                                PointF(
                                    lastRect!!.right,
                                    lastRect.bottom
                                )
                            )
                        } else {
                            startPointer = PointerIndex(
                                startPointer.pageIndex,
                                PointF(
                                    firstRect!!.left,
                                    firstRect.bottom
                                )
                            )
                        }
                    } else {
                        startPointer = PointerIndex(
                            startPointer.pageIndex,
                            PointF(
                                firstRect!!.left,
                                firstRect.bottom
                            )
                        )
                    }
                    val start = pointerInOverlay(startPointer)
                    pdfTouchHandler.updatePointer(start, endPointerIndex)
                } else {
                    if (endPointer.pageIndex > wordsInPointer.last().pageIndex) {
                        endPointer = PointerIndex(
                            wordsInPointer.last().pageIndex,
                            PointF(
                                lastRect!!.right,
                                lastRect.bottom
                            )
                        )
                    } else if (endPointer.pageIndex < wordsInPointer.first().pageIndex) {
                        endPointer = PointerIndex(
                            wordsInPointer.first().pageIndex,
                            PointF(
                                firstRect!!.left,
                                firstRect.bottom
                            )
                        )
                    } else if (endPointer.pageIndex < startPointer.pageIndex) {
                        endPointer = PointerIndex(
                            endPointer.pageIndex,
                            PointF(
                                firstRect!!.left,
                                firstRect.bottom
                            )
                        )
                    } else if (endPointer.pageIndex == startPointer.pageIndex) {
                        if (wordsInPointer.map { it.lineYKey }.distinct().size == 1
                            && wordsInPointer.first().columnIndex == wordsInPointer.last().columnIndex
                        ) {
                            if (startPointer.pointF.x > endPointer.pointF.x && endPointer.pointF.y <= firstRect!!.bottom + 2) {
                                endPointer = PointerIndex(
                                    endPointer.pageIndex,
                                    PointF(
                                        firstRect.left,
                                        firstRect.bottom
                                    )
                                )
                            } else {
                                endPointer = PointerIndex(
                                    endPointer.pageIndex,
                                    PointF(
                                        lastRect!!.right,
                                        lastRect.bottom
                                    )
                                )
                            }

                        } else if (wordsInPointer.first().columnGroupId != null
                            && wordsInPointer.first().columnGroupId == wordsInPointer.last().columnGroupId
                        ) {
                            if (startPointer.pointF.x > endPointer.pointF.x) {
                                endPointer = PointerIndex(
                                    endPointer.pageIndex,
                                    PointF(
                                        firstRect!!.left,
                                        firstRect.bottom
                                    )
                                )
                            } else {
                                endPointer = PointerIndex(
                                    endPointer.pageIndex,
                                    PointF(
                                        lastRect!!.right,
                                        lastRect.bottom
                                    )
                                )
                            }

                        } else if (startPointer.pointF.y > endPointer.pointF.y) {
                            endPointer = PointerIndex(
                                endPointer.pageIndex,
                                PointF(
                                    firstRect!!.left,
                                    firstRect.bottom
                                )
                            )
                        } else {
                            endPointer = PointerIndex(
                                endPointer.pageIndex,
                                PointF(
                                    lastRect!!.right,
                                    lastRect.bottom
                                )
                            )
                        }
                    } else {
                        endPointer = PointerIndex(
                            endPointer.pageIndex,
                            PointF(
                                lastRect!!.right,
                                lastRect.bottom
                            )
                        )
                    }
                    val end = pointerInOverlay(endPointer)
                    pdfTouchHandler.updatePointer(startPointerIndex, end)

                    startPointerInPage = startPointer
                    endPointerInPage = endPointer
                }
                pointerShape(wordsInPointer, startPointer, endPointer)
            }
        }
    }

    fun RecyclerView.measureItemAt(position: Int): SizeF {
        val adapter = adapter ?: return SizeF(0f, 0f)
        if (position !in 0 until adapter.itemCount) return SizeF(0f, 0f)

        // Lấy viewType của item đó
        val viewType = adapter.getItemViewType(position)

        // Tạo ViewHolder "ảo" (chưa add vào RecyclerView thật)
        val vh = adapter.createViewHolder(this, viewType)

        // Bind dữ liệu tương ứng vào ViewHolder đó
        adapter.onBindViewHolder(vh, position)

        // Tạo spec để đo kích thước view
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        // Đo layout
        vh.itemView.measure(widthSpec, heightSpec)
        vh.itemView.layout(0, 0, vh.itemView.measuredWidth, vh.itemView.measuredHeight)

        // Trả về kích thước
        return SizeF(
            vh.itemView.measuredWidth.toFloat(),
            vh.itemView.measuredHeight.toFloat()
        )
    }

    override fun showPopupAfterDrag() {
        showPopup()
    }

    override fun setupObserver() {
        lifecycleScope.launch {
            viewModel.pageBitmaps.collect { (index, bmp) ->
                adapter.updateBitmap(index, bmp)
            }
        }
    }

    override fun captureItemsWithOffset(): Pair<Bitmap, Int> {
        val layoutManager = binding.pdfRecyclerView.layoutManager as LinearLayoutManager

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
            return Pair(createBitmap(1, 1), 0)
        }

        // Calculate the total height of the items
        var totalHeight = 0
        for (i in firstVisible..lastVisible) {
            layoutManager.findViewByPosition(i)?.let {
                totalHeight += it.height
            }
        }

        val bitmap = createBitmap(binding.pdfRecyclerView.width, totalHeight)
        val canvas = Canvas(bitmap)

        var offsetY = 0
        var firstItemTopOffset = 0

        for (i in firstVisible..lastVisible) {
            val child = layoutManager.findViewByPosition(i) ?: continue

            if (i == firstVisible) {
                // Save the scroll position in the first item
                firstItemTopOffset = -child.top
            }

            canvas.withTranslation(0f, offsetY.toFloat()) {
                child.draw(canvas)
            }

            offsetY += child.height
        }

        // Return the bitmap + offset needed to align with the current screen
        return Pair(bitmap, firstItemTopOffset)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun tapIsConfirmHighlight(x: Float, y: Float): Boolean {
        val child = binding.pdfRecyclerView.findChildViewUnder(x, y)
        if (child != null) {
            val pageIndex = binding.pdfRecyclerView.getChildAdapterPosition(child)
            val sortedHighlights = confirmedHighlight[pageIndex]
                ?.sortedByDescending { it.confirmId }
                ?.toMutableList()
            if (sortedHighlights != null) {
                val tappedHighlight = sortedHighlights.find { mark ->
                    mark.marks.any { rect -> rect.contains(x, y - child.y) }
                }
                tappedHighlight?.confirmId?.let {
                    findHighlightByConfirmId(it)
                    return true
                }
            }
        }
        return true
    }

    private fun findHighlightByConfirmId(confirmId: Long) {
        clearSelectedHighlights()
        confirmedHighlight.forEach { (pageIndex, marksList) ->
            val mark = marksList.find { it.confirmId == confirmId }
            if (mark != null) {
                selectedHighlights[pageIndex] = mark
            }
        }
        selectedHighlights.forEach { (pageIndex, mark) ->
            mark.text?.let {
                updateSelectedHighlights(
                    pageIndex,
                    mark.marks,
                    it,
                    mark.confirmId,
                    mark.contentNote,
                )
            }
        }
        showPopup()
    }

    override fun onLongPress(
        x: Float,
        y: Float,
    ) {
        val child = binding.pdfRecyclerView.findChildViewUnder(x, y)
        if (child != null) {
            val pageIndex = binding.pdfRecyclerView.getChildAdapterPosition(child)

            val words = viewModel.coordinatesPdfTouch(
                child.width,
                child.height,
                x,
                y - child.y,
                pageIndex,
            )
            val itemView = binding.pdfRecyclerView.measureItemAt(pageIndex)
            val coordinatesPdfTouch = viewModel.pdfToItemRect(
                words.map { it.rect },
                itemView.width.toInt(),
                itemView.height.toInt(),
                pageIndex
            )

            if (coordinatesPdfTouch.isNotEmpty()) {
                clearSelectedHighlights()

                val sorted = coordinatesPdfTouch.sortedWith(
                    compareBy<RectF> { it.top }
                        .thenBy { it.left }
                )

                val startPointer = PointF(sorted.first().left, sorted.first().bottom + child.y)
                val endPointer = PointF(sorted.last().right, sorted.last().bottom + child.y)

                if (coordinatesPdfTouch.isNotEmpty()) {
                    pdfTouchHandler.updatePointer(startPointer, endPointer)
                    updateSelectedHighlights(pageIndex, coordinatesPdfTouch, words.map { it.word })
                    showPopup()
                }
            }

        }
    }

    fun updateSelectedHighlights(
        pageIndex: Int,
        marks: List<RectF>,
        text: List<String>,
        confirmId: Long? = -1,
        contentNote: String? = null,
    ) {
        val newMark =
            PageMark(
                marksState = MarksState.LONG_PRESSED,
                marks = marks,
                confirmId = confirmId,
                text = text,
                contentNote = contentNote,
                isFirstPageMark = selectedHighlights.keys.firstOrNull() == pageIndex
            )
        selectedHighlights[pageIndex] = newMark
        adapter.updateSelectedMarks(pageIndex)
    }

    fun clearSelectedHighlights() {
        val copyHighlights = selectedHighlights.toMap()
        selectedHighlights.clear()

        copyHighlights.keys.forEach { pageIndex ->
            adapter.updateSelectedMarks(pageIndex)
        }

        pdfTouchHandler.updatePointer(null, null)
    }


    fun mergeSelectedAreas(selectedList: List<RectF>): RectF? {
        if (selectedList.isEmpty()) return null

        val result = RectF(selectedList[0])
        for (i in 1 until selectedList.size) {
            result.union(selectedList[i])
        }
        return result
    }

    fun showPopup() {
        popupTextAction.updateSelectArea(null)

        popupTextAction.setDictionary(false)
        val textList = selectedHighlights.flatMap { (_, mark) -> mark.text ?: emptyList() }
        if (textList.isNotEmpty()) {
            val text = textList.joinToString("").replace(Regex("\\s+"), " ").trim()
            if (text.matches(Regex("^[A-Za-zÀ-ỹ]+$"))) {
                popupTextAction.setDictionary(true)
            }
        }

        selectedHighlights.forEach { (pageIndex, mark) ->
            if (mark.confirmId != -1L) popupTextAction.setEdit(true) else popupTextAction.setEdit(
                false
            )
            val mergedRect = mergeSelectedAreas(mark.marks) ?: return@forEach
            val selectArea =
                mergedRect.toScreenRectForItem(binding.pdfRecyclerView, pageIndex) ?: return@forEach
            if (selectArea.bottom > 0 && (selectArea.left > 0 || selectArea.right > 0)) {
                popupTextAction.updateSelectArea(selectArea)
                return@forEach
            }
        }
        popupTextAction.show()
    }

    fun RectF.toScreenRectForItem(recyclerView: RecyclerView, position: Int): RectF? {
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position) ?: return null
        val itemView = viewHolder.itemView

        val location = IntArray(2)
        itemView.getLocationOnScreen(location)

        val currentScale = pdfTouchHandler.currentScale

        return RectF(
            this.left * currentScale + location[0],
            this.top * currentScale + location[1],
            this.right * currentScale + location[0],
            this.bottom * currentScale + location[1] + binding.highlightPointerView.getSizePointer()
        )
    }

    fun pointerShape(
        wordsInPointer: List<WordInfo>,
        startPointerIndex: PointerIndex,
        endPointerIndex: PointerIndex
    ) {
        if (wordsInPointer.isEmpty()) return

        val firstWord = wordsInPointer.first()
        val lastWord = wordsInPointer.last()

        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val startAtBorder =
            startPointerIndex.pointF.x < POINTER_BORDER_SPACING || startPointerIndex.pointF.x > (screenWidth - POINTER_BORDER_SPACING)
        val endAtBorder =
            endPointerIndex.pointF.x < POINTER_BORDER_SPACING || endPointerIndex.pointF.x > (screenWidth - POINTER_BORDER_SPACING)

        if (startAtBorder) {
            binding.highlightPointerView.setStartInverted(startPointerIndex.pointF.x < POINTER_BORDER_SPACING)
        }
        if (endAtBorder) {
            binding.highlightPointerView.setEndInverted(endPointerIndex.pointF.x > (screenWidth - POINTER_BORDER_SPACING))
        }

        if (firstWord.pageIndex == lastWord.pageIndex) {
            val startViewHolder =
                binding.pdfRecyclerView.findViewHolderForAdapterPosition(startPointerIndex.pageIndex)
            val startItemView = startViewHolder?.itemView

            val startWidth = startItemView!!.width
            val startHeight = startItemView.height

            val endViewHolder =
                binding.pdfRecyclerView.findViewHolderForAdapterPosition(endPointerIndex.pageIndex)
            val endItemView = endViewHolder?.itemView

            val endWidth = endItemView!!.width
            val endHeight = endItemView.height


            val startPointer = viewModel.itemToPdfPointerIndex(
                startPointerIndex.pointF,
                startWidth.toFloat(),
                startHeight.toFloat(),
                startPointerIndex.pageIndex
            )

            val endPointer = viewModel.itemToPdfPointerIndex(
                endPointerIndex.pointF,
                endWidth.toFloat(),
                endHeight.toFloat(),
                endPointerIndex.pageIndex
            )

            // Lấy tâm của từ
            fun RectF.centerPoint(): PointF {
                return PointF(centerX(), centerY())
            }

            // Hàm tính khoảng cách 2 điểm
            fun distance(p1: PointF, p2: PointF): Float {
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                return sqrt(dx * dx + dy * dy)
            }

            val firstCenter = firstWord.rect.centerPoint()
            val lastCenter = lastWord.rect.centerPoint()

            val distFirstToStart = distance(firstCenter, startPointer)
            val distLastToStart = distance(lastCenter, startPointer)

            val distFirstToEnd = distance(firstCenter, endPointer)
            val distLastToEnd = distance(lastCenter, endPointer)

            val startIsFirst = distFirstToStart < distLastToStart
            val endIsLast = distFirstToEnd > distLastToEnd

            if (!startAtBorder) {
                if (startIsFirst) {
                    binding.highlightPointerView.setStartInverted(false)
                } else {
                    binding.highlightPointerView.setStartInverted(true)
                }
            }

            if (!endAtBorder) {
                if (endIsLast) {
                    binding.highlightPointerView.setEndInverted(false)
                } else {
                    binding.highlightPointerView.setEndInverted(true)
                }
            }
        } else {
            if (!startAtBorder) {
                if (startPointerIndex.pageIndex == firstWord.pageIndex) {
                    binding.highlightPointerView.setStartInverted(false)
                } else {
                    binding.highlightPointerView.setStartInverted(true)
                }
            }

            if (!endAtBorder) {
                if (endPointerIndex.pageIndex == lastWord.pageIndex) {
                    binding.highlightPointerView.setEndInverted(false)
                } else {
                    binding.highlightPointerView.setEndInverted(true)
                }
            }
        }
    }

    fun pointerInOverlay(pointerIndex: PointerIndex): PointF? {
        val viewHolder =
            binding.pdfRecyclerView.findViewHolderForAdapterPosition(pointerIndex.pageIndex)
        val child = viewHolder?.itemView ?: return null

        return PointF(
            pointerIndex.pointF.x,
            pointerIndex.pointF.y + child.top
        )
    }

    fun pointerInPage(
        x: Float,
        y: Float
    ): PointerIndex? {
        val child = binding.pdfRecyclerView.findChildViewUnder(x, y)
        if (child != null) {
            val pageIndex = binding.pdfRecyclerView.getChildAdapterPosition(child)

            return PointerIndex(
                pageIndex,
                PointF(
                    x,
                    y - child.top
                )
            )
        }
        return null
    }

    override fun onClickColorHighlight(color: Int) {
        val selectedHighlightsCopy = selectedHighlights.toMap()

        selectedHighlights.forEach { (pageIndex, mark) ->
            val confirmId: Long = System.currentTimeMillis()
            val confirmMark = PageMark(
                confirmId = mark.confirmId.takeIf { it != -1L } ?: confirmId,
                marksState = MarksState.CONFIRM,
                marks = mark.marks,
                color = color,
                text = mark.text,
                contentNote = mark.contentNote,
                isFirstPageMark = mark.isFirstPageMark
            )
            insertConfirmHighlight(pageIndex, confirmMark)
        }

        clearSelectedHighlights()

        selectedHighlightsCopy.forEach { (pageIndex, _) ->
            adapter.updateConfirmedMarks(pageIndex)
        }
    }

    override fun onDeleteHighlight() {
        val confirmId = selectedHighlights.values.first().confirmId

        selectedHighlights.forEach { (pageIndex, _) ->
            confirmedHighlight[pageIndex]?.removeIf { it.confirmId == confirmId }
            confirmId?.let { adapter.removeConfirmedMarks(pageIndex, it) }
            adapter.updateConfirmedMarks(pageIndex)

        }

        clearSelectedHighlights()
    }

    override fun onCopyText() {
        val textList = selectedHighlights.flatMap { (_, mark) -> mark.text ?: emptyList() }
        if (textList.isNotEmpty()) {
            val text = textList.joinToString("").replace(Regex("\\s+"), " ").trim()
            viewModel.copyTextToClipboard(this, text)
            clearSelectedHighlights()
        }
    }

    override fun onContentNote() {
        showNoteDialog()
    }

    override fun onTranslate(isTranslate: Boolean) {
        val textList = selectedHighlights.flatMap { (_, mark) -> mark.text ?: emptyList() }
        val text = textList.joinToString("").replace(Regex("\\s+"), " ").trim()

        if (text.isBlank()) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            return
        }

        val translateIntent = Intent().apply {
            action = Intent.ACTION_PROCESS_TEXT
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }

        val pm = packageManager
        val resolveInfos = pm.queryIntentActivities(translateIntent, 0)

        val translateApps = resolveInfos.filter {
            val appName = it.loadLabel(pm).toString().lowercase()
            val pkg = it.activityInfo.packageName.lowercase()
            if (isTranslate) {
                listOf("translate", "translator", "deepl")
                    .any { keyword -> keyword in appName || keyword in pkg }
            } else {
                listOf("dict", "dictionary", "tflat", "laban", "oxford", "cambridge")
                    .any { keyword -> keyword in appName || keyword in pkg }
            }
        }

        val browserUrl = if (isTranslate)
            "https://translate.google.com/?sl=auto&tl=vi&text=${Uri.encode(text)}"
        else
            "https://dictionary.cambridge.org/dictionary/english/${Uri.encode(text)}"

        when {
            translateApps.isNotEmpty() -> {
                // App or browser
                val appNames = translateApps.map { it.loadLabel(pm).toString() }.toMutableList()
                appNames.add(if (isTranslate) "Open in browser (Google Translate)" else "Open in browser (Dictionary site)")

                AlertDialog.Builder(this)
                    .setTitle(if (isTranslate) "Select translation app" else "Select dictionary app")
                    .setItems(appNames.toTypedArray()) { _, which ->
                        if (which == translateApps.size) {
                            val browserIntent = Intent.makeMainSelectorActivity(
                                Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER
                            ).apply {
                                data = browserUrl.toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            try {
                                startActivity(browserIntent)
                            } catch (_: Exception) {
                                Toast.makeText(this, "Browser not found", Toast.LENGTH_SHORT).show()
                            }

                        } else {
                            val chosenApp = translateApps[which]
                            val packageName = chosenApp.activityInfo.packageName
                            val className = chosenApp.activityInfo.name

                            val intent = Intent(translateIntent).apply {
                                setClassName(packageName, className)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            try {
                                startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            else -> {
                // browser
                val browserIntent = Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER
                ).apply {
                    data = browserUrl.toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    startActivity(browserIntent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Browser not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onMoreExtension(): String? {
        val textList = selectedHighlights.flatMap { (_, mark) -> mark.text ?: emptyList() }
        if (textList.isNotEmpty()) {
            val text = textList.joinToString("").replace(Regex("\\s+"), " ").trim()
            return text
        }
        return null
    }

    fun insertConfirmHighlight(pageIndex: Int, mark: PageMark) {
        val confirmedList = confirmedHighlight.getOrPut(pageIndex) { mutableListOf() }
        val existConfirmedMark = confirmedList.any { it.confirmId == mark.confirmId }
        if (existConfirmedMark) {
            val confirmedMark = confirmedList.find { it.confirmId == mark.confirmId }
            confirmedMark?.color = mark.color
            confirmedMark?.contentNote = mark.contentNote
        } else {
            confirmedList.add(mark)
        }
    }

    private fun showNoteDialog() {
        val binding = DialogAddNoteBinding.inflate(LayoutInflater.from(this))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Note")
            .setView(binding.root)
            .setPositiveButton("Save") { _, _ ->
                val noteContent = binding.editNote.text.toString()

                selectedHighlights.values.forEach { mark ->
                    mark.contentNote = noteContent
                }

                val selectedHighlightsCopy = selectedHighlights.toMap()

                selectedHighlights.forEach { (pageIndex, mark) ->
                    val confirmId: Long = System.currentTimeMillis()
                    val confirmMark = PageMark(
                        confirmId = mark.confirmId.takeIf { it != -1L } ?: confirmId,
                        marksState = MarksState.CONFIRM,
                        marks = mark.marks,
                        color = mark.color ?: Color.parseColor("#4A90E2"),
                        text = mark.text,
                        contentNote = noteContent,
                        isFirstPageMark = mark.isFirstPageMark
                    )
                    insertConfirmHighlight(pageIndex, confirmMark)
                }
                clearSelectedHighlights()

                selectedHighlightsCopy.forEach { (pageIndex, _) ->
                    adapter.updateConfirmedMarks(pageIndex)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
            }
            .create()

        dialog.setOnShowListener {
            binding.editNote.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.editNote, InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

}