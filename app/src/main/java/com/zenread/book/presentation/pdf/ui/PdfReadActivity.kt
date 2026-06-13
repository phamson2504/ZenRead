package com.zenread.book.presentation.pdf.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Scroller
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.zenread.book.R
import com.zenread.book.core.base.BaseActivity
import com.zenread.book.core.utils.measureItemAt
import com.zenread.book.databinding.ActivityPdfReadBinding
import com.zenread.book.databinding.DialogAddNoteBinding
import com.zenread.book.domain.model.Book
import com.zenread.book.domain.model.BookmarkItem
import com.zenread.book.domain.model.SearchTextResult
import com.zenread.book.domain.model.TocItem
import com.zenread.book.domain.model.WordInfo
import com.zenread.book.presentation.pdf.MarksState
import com.zenread.book.presentation.pdf.PageMark
import com.zenread.book.presentation.pdf.PointerIndex
import com.zenread.book.presentation.pdf.ReadingPosition
import com.zenread.book.presentation.pdf.ScrollLinearLayoutManager
import com.zenread.book.presentation.pdf.adapter.PdfReadAdapter
import com.zenread.book.presentation.pdf.gesture.PdfTouchHandler
import com.zenread.book.presentation.pdf.model.PdfReadViewModel
import com.zenread.book.presentation.pdf.popup.action.OnTextActionListener
import com.zenread.book.presentation.pdf.popup.action.PopupTextAction
import com.zenread.book.presentation.pdf.popup.reader.OnReaderMenuListener
import com.zenread.book.presentation.pdf.popup.reader.PopupReaderMenu
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.Int
import kotlin.math.abs

@AndroidEntryPoint
class PdfReadActivity : BaseActivity<ActivityPdfReadBinding>(),
    PdfTouchHandler.OnPdfTouchListener,
    OnTextActionListener, OnReaderMenuListener {

    private val viewModel: PdfReadViewModel by viewModels()

    private lateinit var layoutManager: ScrollLinearLayoutManager

    private lateinit var adapter: PdfReadAdapter

    private lateinit var pdfTouchHandler: PdfTouchHandler

    private lateinit var popupTextAction: PopupTextAction

    private var startPointerInPage: PointerIndex? = null
    private var endPointerInPage: PointerIndex? = null

    private var selectedHighlights: MutableMap<Int, PageMark> = mutableMapOf()
    private var voicedHighlights: MutableMap<Int, PageMark> = mutableMapOf()
    private var confirmedHighlight: MutableMap<Int, MutableList<PageMark>> = mutableMapOf()
    private var searchHighlight: MutableMap<Int, MutableList<PageMark>> = mutableMapOf()

    private lateinit var popupReaderMenu: PopupReaderMenu

    var currentPage = 0
    private var isScrollToPage = false

    override fun inflateBinding(layoutInflater: LayoutInflater) =
        ActivityPdfReadBinding.inflate(layoutInflater)

    override fun setupView(savedInstanceState: Bundle?) {
        PDFBoxResourceLoader.init(application)

        val book: Book? = intent.getParcelableExtra("book_data")
        val uri = book?.uri?.toUri() ?: return showError("No PDF URI provided")

        viewModel.bookId = book.id

        viewModel.initialDiskCacheManager(book.id, book.title)

        val recyclerView = binding.pdfRecyclerView
        layoutManager = ScrollLinearLayoutManager(this, RecyclerView.VERTICAL, false)

        recyclerView.layoutManager = layoutManager

        binding.container.post {
            val screenWidth = binding.container.width
            viewModel.loadPdf(uri, screenWidth) {
                adapter.updatePageSizes(viewModel.pageSizes)
                viewModel.loadConfirmedHighlight()
                loadVisiblePagesWords()
            }
            moveToPage()
        }

        adapter =
            PdfReadAdapter(
                confirmedHighlight,
                selectedHighlights,
                voicedHighlights,
                searchHighlight,
                emptyList()
            )
        recyclerView.adapter = adapter



        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val first = lm.findFirstVisibleItemPosition()
                val last = lm.findLastVisibleItemPosition()
                if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return
                val center = (first + last) / 2

                if ((currentPage == 0 || abs(center - currentPage) >= 0) && !isScrollToPage) {
                    currentPage = center
                    viewModel.preload(center)
                }
                setChapterFromTocItem()
            }
        })

        val currentOrientation = resources.configuration.orientation
        if (viewModel.lastOrientation != Configuration.ORIENTATION_UNDEFINED &&
            viewModel.lastOrientation != currentOrientation
        ) {
            onOrientationChanged()
        }
        viewModel.lastOrientation = currentOrientation

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

        popupTextAction = PopupTextAction(this, binding.root)
        popupTextAction.setOnTextActionListener(this)


        popupReaderMenu = PopupReaderMenu(this, binding.root as ViewGroup)
        binding.root.addView(popupReaderMenu.view)
        popupReaderMenu.setOnTextActionListener(this)
        popupReaderMenu.setTitle(viewModel.getTitle())
    }

    fun onOrientationChanged() {
        viewModel.clearRenderJobs()
        binding.container.post {
            val screenWidth = binding.container.width
            viewModel.updatePageSize(screenWidth) {
                adapter.updatePageSizes(viewModel.pageSizes)
            }
            viewModel.loadConfirmedHighlight()
        }
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
                viewModel.clearSelectedHighlight()
                for ((page, words) in wordsByPage) {
                    val itemView = binding.pdfRecyclerView.measureItemAt(page)
                    val wordsInPage =
                        viewModel.pdfToItemRect(
                            words.map { it.rect },
                            itemView.width.toInt(),
                            itemView.height.toInt(),
                            page
                        )
                    isFirstTimeTap = true
                    viewModel.updateSelectedHighlights(
                        page,
                        wordsInPage,
                        words.map { it.rect },
                        words.map { it.word })
                }

                if (isStartPointer) {
                    startPointer =
                        viewModel.updateStartPointerMove(wordsInPointer, startPointer, endPointer)
                    val start = pointerInOverlay(startPointer)
                    pdfTouchHandler.updatePointer(start, endPointerIndex)
                } else {
                    endPointer =
                        viewModel.updateEndPointerMove(wordsInPointer, startPointer, endPointer)
                    val end = pointerInOverlay(endPointer)
                    pdfTouchHandler.updatePointer(startPointerIndex, end)

                    startPointerInPage = startPointer
                    endPointerInPage = endPointer
                }
                viewModel.pointerShape(
                    wordsInPointer,
                    startPointer,
                    endPointer,
                    binding.highlightPointerView,
                    binding.pdfRecyclerView
                )
            }
        }
    }

    override fun showPopupAfterDrag() {
        showPopup()
    }

    override fun setupObserver() {
        lifecycleScope.launch {
            viewModel.pageBitmaps.collect { (index, bmp) ->
                adapter.updateBitmap(index, bmp)
                adapter.updateConfirmedMarks(index)
            }
        }
        viewModel.selectedHighlight.observe(this) { highlights ->
            clearSelectedHighlights()
            highlights.forEach { (pageIndex, mark) ->
                selectedHighlights[pageIndex] = mark
                adapter.updateSelectedMarks(pageIndex)
            }
        }
        viewModel.voicedHighlight.observe(this) { highlights ->
            clearVoicedHighlights()
            highlights.forEach { (pageIndex, mark) ->
                val itemView = binding.pdfRecyclerView.measureItemAt(pageIndex)
                val rectInItem = viewModel.pdfToItemRect(
                    mark.screenMarks,
                    itemView.width.toInt(),
                    itemView.height.toInt(),
                    pageIndex
                )

                voicedHighlights[pageIndex] = PageMark(
                    marksState = mark.marksState,
                    screenMarks = rectInItem
                )

                adapter.updateVoiceHighlights(pageIndex)
                viewModel.moveScreenToVoicedHighlight(
                    binding.pdfRecyclerView,
                    binding.container,
                    80,
                    voicedHighlights
                )
            }
        }

        viewModel.confirmedHighlight.observe(this) { highlights ->
            viewModel.clearSelectedHighlight()
            highlights.forEach { (pageIndex, mark) ->
                confirmedHighlight[pageIndex] = mark
                adapter.updateConfirmedMarks(pageIndex)
            }
        }

        viewModel.scrollToPageEvent.observe(this) { pageIndex ->
            if (!pdfTouchHandler.scroller.isFinished) pdfTouchHandler.scroller.abortAnimation()
            binding.pdfRecyclerView.scrollBy(0, 10)
            val layoutManager = binding.pdfRecyclerView.layoutManager
            if (layoutManager != null) {
                isScrollToPage = true
                val lm = binding.pdfRecyclerView.layoutManager as LinearLayoutManager
                lm.scrollToPositionWithOffset(pageIndex, 0)
                binding.pdfRecyclerView.post {
                    onSmoothScrollFinished()
                }
            }
        }
    }

    fun moveToPage() {
        val readingPosition = viewModel.loadPageCount()
        currentPage = readingPosition?.pageIndex ?: 0
        val lm = binding.pdfRecyclerView.layoutManager as LinearLayoutManager
        val pageIndex = readingPosition?.pageIndex
        if (pageIndex != null) {
            val sizePage = viewModel.pageSizes[pageIndex]
            val currentY = (readingPosition.pagePercentage * sizePage.height).toInt()
            lm.scrollToPositionWithOffset(currentPage, -currentY)

        } else {
            lm.scrollToPositionWithOffset(currentPage, 0)
        }

    }

    override fun captureItemsWithOffset(): Pair<Bitmap, Int> {
        return viewModel.captureItems(binding.pdfRecyclerView)
    }

    var isFirstTimeTap = false
    override fun tapIsConfirmHighlight(x: Float, y: Float): Boolean {
        val child = binding.pdfRecyclerView.findChildViewUnder(x, y)
        if (child != null) {
            val pageIndex = binding.pdfRecyclerView.getChildAdapterPosition(child)
            val isTapInConfirm = viewModel.isTapInConfirmed(pageIndex, x, y - child.y)
            if (isTapInConfirm) {
                showPopup()
                val isNote = !selectedHighlights.values.firstOrNull()?.contentNote.isNullOrEmpty()
                if (isNote) {
                    showNoteDialog(false)
                }
                isFirstTimeTap = true
            }
        }
        return selectedHighlights.isNotEmpty()
    }

    override fun clearSelectedHighlight(isClear: Boolean, isInsideCenterSquare: Boolean) {
        if (isClear && !isFirstTimeTap) {
            if (selectedHighlights.isNotEmpty()) {
                viewModel.clearSelectedHighlight()
            } else {
                if (isInsideCenterSquare || popupReaderMenu.isVisible())
                    popupReaderMenu.toggleMenu()
            }
        } else {
            isFirstTimeTap = false
            if (popupReaderMenu.isVisible())
                popupReaderMenu.hideMenu()
        }

    }

    override fun onSmoothScrollFinished() {
        updateHighlightSearchVis()
        viewModel.moveScreenToVoiced(binding.pdfRecyclerView, binding.container, 80)
        binding.pdfRecyclerView.post {
            isScrollToPage = false
            binding.pdfRecyclerView.scrollBy(0, 1)
        }
    }

    override fun saveCurrentPage() {
        val readingPosition = viewModel.getCurrentPosition(binding.pdfRecyclerView)
        viewModel.saveCurrentPage(readingPosition)
    }

    override fun loadVisiblePagesWords() {
        val lm = binding.pdfRecyclerView.layoutManager as LinearLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        val visiblePage = (first..last).toList()
        viewModel.loadVisiblePagesWords(visiblePage)
    }

    fun updateHighlightSearchVis() {
        val first = (layoutManager.findFirstVisibleItemPosition() - 5).coerceAtLeast(0)
        val last =
            (layoutManager.findLastVisibleItemPosition() + 5).coerceAtMost(adapter.itemCount - 1)
        for (i in first..last) {
            adapter.updateSearchMarks(i)
        }
    }

    override fun onLongPress(
        x: Float,
        y: Float,
    ) {
        val child = binding.pdfRecyclerView.findChildViewUnder(x, y)
        if (child != null) {
            val pageIndex = binding.pdfRecyclerView.getChildAdapterPosition(child)

//            val words = viewModel.coordinatesPdfTouch(
//                child.width,
//                child.height,
//                x,
//                y - child.y,
//                pageIndex,
//            )

            val words = viewModel.pageWords[pageIndex]
            if (words != null) {
                val pointWords = viewModel.coordinatesPdfTouch(
                    child.width,
                    child.height,
                    words,
                    x,
                    y - child.y,
                    pageIndex,
                )
                val itemView = binding.pdfRecyclerView.measureItemAt(pageIndex)

                val coordinatesPdfTouch = viewModel.pdfToItemRect(
                    pointWords.map { it.rect },
                    itemView.width.toInt(),
                    itemView.height.toInt(),
                    pageIndex
                )

                if (coordinatesPdfTouch.isNotEmpty()) {
                    clearSelectedHighlights()
                    isFirstTimeTap = true
                    viewModel.clearSelectedHighlight()
                    val sorted = coordinatesPdfTouch.sortedWith(
                        compareBy<RectF> { it.top }
                            .thenBy { it.left }
                    )

                    val startPointer = PointF(sorted.first().left, sorted.first().bottom + child.y)
                    val endPointer = PointF(sorted.last().right, sorted.last().bottom + child.y)

                    if (coordinatesPdfTouch.isNotEmpty()) {
                        viewModel.updateSelectedHighlights(
                            pageIndex,
                            coordinatesPdfTouch,
                            pointWords.map { it.rect },
                            pointWords.map { it.word })
                        pdfTouchHandler.updatePointer(startPointer, endPointer)
                        showPopup()
                    }
                }
            }
        }
    }

    fun clearSelectedHighlights() {
        val copyHighlights = selectedHighlights.toMap()
        selectedHighlights.clear()
        copyHighlights.keys.forEach { pageIndex ->
            adapter.updateSelectedMarks(pageIndex)
        }
        pdfTouchHandler.updatePointer(null, null)
    }

    fun clearVoicedHighlights() {
        val copyHighlights = voicedHighlights.toMap()
        voicedHighlights.clear()
        copyHighlights.keys.forEach { pageIndex ->
            adapter.updateVoiceHighlights(pageIndex)
        }
    }


    fun showPopup() {
        popupTextAction.updateSelectArea(null)

        popupTextAction.setDictionary(false)
        val text = viewModel.textSelected()
        if (!text.isNullOrBlank()) {
            if (text.matches(Regex("^[A-Za-zÀ-ỹ]+$"))) {
                popupTextAction.setDictionary(true)
            }
        }

        val mergedRect = selectedHighlights
            .mapNotNull { (pageIndex, mark) ->
                viewModel.mergeSelectedAreas(mark.screenMarks)
                    ?.toScreenRectForItem(binding.pdfRecyclerView, pageIndex)
            }
            .reduceOrNull { acc, rect ->
                acc.apply {
                    left = minOf(left, rect.left)
                    top = minOf(top, rect.top)
                    right = maxOf(right, rect.right)
                    bottom = maxOf(bottom, rect.bottom)
                }
            }

        if (selectedHighlights.any { it.value.confirmId != -1L }) {
            popupTextAction.setEdit(true)
        } else {
            popupTextAction.setEdit(false)
        }

        mergedRect?.let { popupTextAction.updateSelectArea(it) }

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
        viewModel.updateColorSelection(color)
        viewModel.updateConfirmHighlight()
    }

    override fun onDeleteHighlight() {
        selectedHighlights.forEach { (pageIndex, mark) ->
            adapter.removeConfirmedMarks(pageIndex, mark.confirmId)
        }
        viewModel.deleteSelectedHighlight()
    }

    override fun onCopyText() {
        viewModel.copyTextToClipboard(this)
    }

    override fun onContentNote() {
        showNoteDialog(true)
    }

    override fun onTranslate(isTranslate: Boolean) {
        val result = viewModel.getTranslationTargets(this, isTranslate)

        if (result == null) {
            Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            return
        }

        val pm = packageManager
        val translateApps = result.apps
        val browserUrl = result.browserUrl
        val text = result.text

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

                            val translateIntent = Intent().apply {
                                action = Intent.ACTION_PROCESS_TEXT
                                type = "text/plain"
                                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                            }

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
        return viewModel.textSelected()
    }

    private fun showNoteDialog(isShowKey: Boolean) {
        val binding = DialogAddNoteBinding.inflate(LayoutInflater.from(this))

        if (!selectedHighlights.values.firstOrNull()?.contentNote.isNullOrEmpty()) {
            val note = selectedHighlights.values.firstOrNull()?.contentNote
            binding.editNote.setText(note)
            binding.editNote.setSelection(note?.length ?: 0)
            binding.editNote.apply {
                isVerticalScrollBarEnabled = true
                movementMethod = ScrollingMovementMethod()
                setScroller(Scroller(context))
                isNestedScrollingEnabled = false
                post {
                    scrollTo(0, 0)
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(binding.root)
            .setPositiveButton("Save") { _, _ ->
                val noteContent = binding.editNote.text.toString()
                viewModel.noteSaved(noteContent)
            }
            .setNegativeButton("Cancel") { _, _ ->
            }
            .create()

        dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        if (isShowKey) {
            dialog.setOnShowListener {
                binding.editNote.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.editNote, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    override fun onClickVolumeUp() {
        viewModel.getLocationScreen(binding.pdfRecyclerView, binding.container, statusBarHeight())
    }

    fun statusBarHeight(): Int {
        val rootView = window.decorView
        val insets = ViewCompat.getRootWindowInsets(rootView)
        return insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    }

    override fun getCatalogue(): List<TocItem> {
        return viewModel.getCatalogue()
    }

    override fun getListHighlight(): List<BookmarkItem> {
        return confirmedHighlight.flatMap { (page, highlights) ->
            highlights.mapNotNull { highlight ->
                highlight.text
                    ?.joinToString("")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { BookmarkItem(it, page, highlight.screenMarks.first()) }
            }
        }
    }

    var searchJob: Job? = null
    val tempHighlight = mutableMapOf<Int, MutableList<PageMark>>()
    override fun search(query: String) {
        searchJob?.cancel()
        tempHighlight.clear()
        val itemList = mutableListOf<SearchTextResult>()
        if (query.isBlank()) {
            popupReaderMenu.updateSearchItem(itemList.toList())
            searchHighlight.clear()
            updateHighlightSearchVis()
            return
        }
        var id = 0L
        searchJob =
            viewModel.searchKeywordRealtime(query).onEach { result ->
                if (result.isNotEmpty()) {
                    result.forEach { (page, contextMap) ->
                        contextMap.forEach { (contextText, wordInfos) ->
                            id++
                            val item = SearchTextResult(
                                id,
                                contextText,
                                page,
                                wordInfos
                            )
                            itemList.add(item)


                            val itemView = binding.pdfRecyclerView.measureItemAt(page - 1)
                            val rectInItem = viewModel.pdfToItemRect(
                                wordInfos.map { it.rect },
                                itemView.width.toInt(),
                                itemView.height.toInt(),
                                page - 1
                            )
                            if (tempHighlight[page - 1] == null) {
                                tempHighlight[page - 1] = mutableListOf()
                            }
                            tempHighlight[page - 1]?.add(
                                PageMark(
                                    confirmId = id,
                                    marksState = MarksState.SEARCH,
                                    screenMarks = rectInItem,
                                    color = ContextCompat.getColor(this, R.color.search_color)
                                )
                            )
                        }
                        popupReaderMenu.updateSearchItem(itemList.toList())
                    }
                }
            }.launchIn(lifecycleScope)
    }

    override fun moveToSearchedPosition(searchTextResult: SearchTextResult) {
        searchHighlight.clear()
        tempHighlight.forEach { (page, list) ->
            searchHighlight[page] = list.map { it.copy() }.toMutableList()
        }

        val pageIndex = searchTextResult.page - 1
        val list = searchHighlight[pageIndex] ?: return

        val movedColor = ContextCompat.getColor(this, R.color.moved_search_color)
        val searchColor = ContextCompat.getColor(this, R.color.search_color)

        val result = list.filter { it.confirmId == searchTextResult.id }
        if (result.isEmpty()) return

        result.forEach { it.color = movedColor }

        list.forEach { mark ->
            if (mark.confirmId != searchTextResult.id) mark.color = searchColor
        }

        viewModel.setScrollToPageOnPos(
            pageIndex,
            result.first().screenMarks.first()
        )

    }

    override fun setChapterFromTocItem() {
        val tocList = popupReaderMenu.getTocList()
        if (tocList.isNotEmpty()) {

            val flat = flattenToc(tocList).sortedBy { it.page }

            val tocItem = findCurrentTocItem(flat, currentPage + 1)

            tocItem?.title?.let { popupReaderMenu.setChapter(it) }
        }
    }

    override fun moveToPageClicked(pageIndex: Int) {
        viewModel.setScrollToPageOnPos(
            pageIndex,
            RectF()
        )
    }

    override fun moveToHighlight(pageIndex: Int, firstRect: RectF) {
        viewModel.setScrollToPageOnPos(
            pageIndex,
            firstRect
        )
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    fun flattenToc(tocList: List<TocItem>): List<TocItem> {
        val result = mutableListOf<TocItem>()

        fun dfs(items: List<TocItem>) {
            for (item in items) {
                result.add(item)
                if (item.children.isNotEmpty()) dfs(item.children)
            }
        }

        dfs(tocList)
        return result.sortedBy { it.page }
    }

    fun findCurrentTocItem(flatList: List<TocItem>, currentPage: Int): TocItem? {
        var left = 0
        var right = flatList.size - 1
        var tocItem: TocItem? = null

        while (left <= right) {
            val mid = (left + right) / 2
            val item = flatList[mid]

            if (item.page <= currentPage) {
                tocItem = item
                left = mid + 1
            } else {
                right = mid - 1
            }
        }
        return tocItem ?: flatList.firstOrNull()
    }
}