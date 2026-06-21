package com.zenread.book.presentation.pdf.model

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.LruCache
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.zenread.book.core.system.DeviceProfileManager
import com.zenread.book.core.utils.Constants.POINTER_BORDER_SPACING
import com.zenread.book.data.parser.pdf.PdfTextParser
import com.zenread.book.domain.model.TocItem
import com.zenread.book.domain.model.TranslationResult
import com.zenread.book.domain.model.WordInfo
import com.zenread.book.domain.repository.DiskCacheManager
import com.zenread.book.domain.repository.PdfRendererManager
import com.zenread.book.domain.service.HighlightService
import com.zenread.book.presentation.pdf.MarksState
import com.zenread.book.presentation.pdf.PageMark
import com.zenread.book.presentation.pdf.PointerIndex
import com.zenread.book.presentation.pdf.SentenceInfo
import com.zenread.book.presentation.pdf.gesture.HighlightPointerView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.getOrPut
import kotlin.collections.toMutableList
import kotlin.math.abs
import kotlin.math.sqrt
import android.content.res.Configuration
import androidx.core.graphics.toColorInt
import com.zenread.book.domain.model.Bookmark
import com.zenread.book.domain.model.BookmarkItem
import com.zenread.book.domain.repository.PageCountManager
import com.zenread.book.domain.service.BookMarkService
import com.zenread.book.presentation.pdf.ReadingPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.collections.get
import kotlin.collections.mapNotNull

@HiltViewModel
class PdfReadViewModel @Inject constructor(
    application: Application,
    private val pdfRendererManager: PdfRendererManager,
    private val diskCacheManager: DiskCacheManager,
    private val pdfTextParser: PdfTextParser,
    private val highlightService: HighlightService,
    private val pageCountManager: PageCountManager,
    private val bookMarkService: BookMarkService
) : ViewModel() {

    private val deviceProfile = DeviceProfileManager(application)
    private val _pageBitmaps = MutableSharedFlow<Pair<Int, Bitmap>>(extraBufferCapacity = 64)
    val pageBitmaps: SharedFlow<Pair<Int, Bitmap>> = _pageBitmaps

    var lastOrientation = Configuration.ORIENTATION_UNDEFINED

    private val renderSemaphore = Semaphore(deviceProfile.renderSemaphore)
    private val renderJobs = LinkedHashMap<Int, Job>()
    val preloadDistance = deviceProfile.preloadDistance

    private val pageSelect = mutableMapOf<Int, List<WordInfo>>()

    val pageWords = mutableMapOf<Int, List<WordInfo>>()

    private val _selectedHighlight = MutableLiveData<MutableMap<Int, PageMark>>(mutableMapOf())
    val selectedHighlight: LiveData<MutableMap<Int, PageMark>> = _selectedHighlight

    private val _voicedHighlight = MutableLiveData<MutableMap<Int, PageMark>>(mutableMapOf())
    val voicedHighlight: LiveData<MutableMap<Int, PageMark>> = _voicedHighlight

    private val _scrollToPageEvent = MutableLiveData<Int>()
    val scrollToPageEvent: LiveData<Int> = _scrollToPageEvent

    private val _confirmedHighlight =
        MutableLiveData<MutableMap<Int, MutableList<PageMark>>>(mutableMapOf())
    val confirmedHighlight: LiveData<MutableMap<Int, MutableList<PageMark>>> = _confirmedHighlight

    private val _bookmarks = MutableLiveData<List<Bookmark>>(emptyList())
    val bookmarks: LiveData<List<Bookmark>> = _bookmarks

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(application) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ENGLISH
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        voiceStartIndex++
                        if (voiceStartIndex > sentences[currentVoicePage]!!.last().sentenceIndex) {
                            voiceStartIndex = 0
                            addSentencesNextPage()
                            currentVoicePage++
                            addVoicedHighlight(currentVoicePage, voiceStartIndex, sentences)

                            speakSentence(sentences[currentVoicePage]!![voiceStartIndex].textSentence)
                        } else {
                            if (voiceStartIndex == sentences[currentVoicePage]!!.last().sentenceIndex) {
                                val endMarks = setOf(".", "!", "?", ":", ";", "…", "...", "—", "”")
                                val lastWord =
                                    sentences[currentVoicePage]!!.last().words.last().word
                                if (!endMarks.any { mark -> lastWord.endsWith(mark) }) {
                                    addSentencesNextPage()

                                    val sentencesText =
                                        sentences[currentVoicePage]!!.last().textSentence + sentences[currentVoicePage + 1]!!.first().textSentence

                                    addVoicedHighlight(
                                        currentVoicePage,
                                        sentences[currentVoicePage]!!.last().sentenceIndex,
                                        sentences,
                                        true,
                                    )

                                    speakSentence(sentencesText)
                                    voiceStartIndex = 0
                                    currentVoicePage++
                                } else {
                                    addVoicedHighlight(
                                        currentVoicePage,
                                        sentences[currentVoicePage]!!.last().sentenceIndex,
                                        sentences
                                    )

                                    speakSentence(sentences[voiceStartIndex]!!.last().textSentence)
                                }
                            } else {
                                addVoicedHighlight(currentVoicePage, voiceStartIndex, sentences)
                                speakSentence(sentences[currentVoicePage]!![voiceStartIndex].textSentence)
                            }
                        }

                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                    }
                })
            }
        }
    }

    private var sentences: MutableMap<Int, MutableList<SentenceInfo>> = mutableMapOf()
    private var voiceStartIndex = -1
    private var currentVoicePage = -1

    private val cache = object : LruCache<Int, Bitmap>(deviceProfile.cacheSize) {
        override fun sizeOf(key: Int, value: Bitmap) = 1
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) oldValue.recycle()
        }
    }

    var pageSizes: List<Size> = emptyList()
        private set
    var pageCount: Int = 0
        private set

    var bookId: Int = 0


    fun searchKeywordRealtime(keyword: String): Flow<Map<Int, Map<String, List<WordInfo>>>> =
        flow {
            val pdDocument = pdfRendererManager.getPdDocument() ?: return@flow
            val lowerKeyword = keyword.lowercase().trim()
            if (lowerKeyword.isEmpty()) return@flow


            for (pageIndex in 0 until pdDocument.numberOfPages) {
                if (!currentCoroutineContext().isActive) return@flow

                val wordsInPage = pdfTextParser.getWordsInPage(pdDocument, pageIndex)
                val avgWidth = wordsInPage.map { it.rect.width() }.average().toFloat()
                val avgHeight = wordsInPage.map { it.rect.height() }.average().toFloat()
                val chars = pdfTextParser.groupWords(
                    wordsInPage.map { it.copy() },
                    avgWidth * 3f,
                    avgHeight / 2
                )
                if (chars.isEmpty()) continue

                val lowerFullText = chars.joinToString("") { it.word }.lowercase()
                var searchIndex = 0
                val emittedRanges = mutableListOf<IntRange>()
                val pageMap = mutableMapOf<String, List<WordInfo>>()

                while (true) {
                    val foundIndex = lowerFullText.indexOf(lowerKeyword, searchIndex)
                    if (foundIndex == -1) break
                    val endIndex = foundIndex + lowerKeyword.length

                    val tentativeStart = (foundIndex - 80).coerceAtLeast(searchIndex)
                    var tentativeEnd = (endIndex + 80).coerceAtMost(chars.size)

                    var nextSearchPos = foundIndex
                    while (true) {
                        val foundNext = lowerFullText.indexOf(lowerKeyword, nextSearchPos)
                        if (foundNext == -1 || foundNext > tentativeEnd) break

                        val nextEnd = foundNext + lowerKeyword.length
                        if (nextEnd > tentativeEnd) tentativeEnd = nextEnd

                        nextSearchPos = foundNext + 1
                    }

                    val currentRange = tentativeStart until tentativeEnd
                    val overlap =
                        emittedRanges.any { it.first < currentRange.last && currentRange.first < it.last }
                    if (!overlap) {
                        val context =
                            chars.subList(tentativeStart, tentativeEnd).joinToString("") { it.word }

                        val matchedWordInfos = mutableListOf<WordInfo>()
                        var keywordPos = 0
                        val tempMatch = mutableListOf<WordInfo>()
                        for (i in tentativeStart until tentativeEnd) {
                            val w = chars[i]
                            if (w.word.lowercase() == lowerKeyword[keywordPos].toString()) {
                                tempMatch.add(w)
                                keywordPos++
                                if (keywordPos >= lowerKeyword.length) {
                                    matchedWordInfos.addAll(tempMatch)
                                    tempMatch.clear()
                                    keywordPos = 0
                                }
                            } else {
                                tempMatch.clear()
                                keywordPos = 0
                            }
                        }

                        if (matchedWordInfos.isNotEmpty()) {
                            pageMap[context] = matchedWordInfos
                            emittedRanges.add(currentRange)
                        }
                    }
                    searchIndex = tentativeEnd
                }

                if (pageMap.isNotEmpty()) {
                    emit(mapOf(pageIndex + 1 to pageMap))
                }
            }
        }.flowOn(Dispatchers.Default)

    fun initialDiskCacheManager(bookId: Int, bookTitle: String) {
        val nameDir = bookTitle.take(3) + "_" + bookId
        diskCacheManager.setBookId(nameDir)
    }

    fun addVoicedHighlight(
        currentVoicePage: Int,
        voiceStartIndex: Int,
        sentences: Map<Int, List<SentenceInfo>>,
        nextVoicePage: Boolean? = false,
    ) {
        val currentSentence = sentences[currentVoicePage]?.getOrNull(voiceStartIndex) ?: return
        val currentMap: MutableMap<Int, PageMark> = mutableMapOf()
        val words = currentSentence.words

        val pageMark = PageMark(
            marksState = MarksState.VOICED,
            screenMarks = words.map { it.rect },
        )
        currentMap[currentVoicePage] = pageMark

        if (nextVoicePage == true) {
            val nextSentence = sentences[currentVoicePage + 1]?.getOrNull(0) ?: return
            val nextWords = nextSentence.words
            val nextPageMark = PageMark(
                marksState = MarksState.VOICED,
                screenMarks = nextWords.map { it.rect },
            )
            currentMap[currentVoicePage + 1] = nextPageMark
        }
        _voicedHighlight.postValue(currentMap)
    }


    fun loadPdf(uri: Uri, screenWidth: Int, onReady: () -> Unit) {
        try {
            pdfRendererManager.openDocument(uri)
            pageCount = pdfRendererManager.getPageCount()
            if (pageCount == 0) throw IllegalStateException("PDF has no pages or cannot be opened")

            pageSizes = (0 until pageCount).map { index ->
                pdfRendererManager.getScaledPageSize(index, screenWidth)
            }
            onReady()
        } catch (e: Exception) {
            Log.e("PDF", "Error opening PDF: ${e.message}")
        }
    }

    fun updatePageSize(screenWidth: Int, onReady: () -> Unit) {
        pageSizes = (0 until pageCount).map { index ->
            pdfRendererManager.getScaledPageSize(index, screenWidth)
        }
        onReady()
    }


    fun coordinatesPdfTouch(
        itemWidth: Int,
        itemHeight: Int,
        wordInfo: List<WordInfo>,
        pointX: Float,
        pointY: Float,
        index: Int
    ): List<WordInfo> {
        pageSelect.clear()
        val pdfSize = pdfRendererManager.getPdPageSize(index)
        val scaleX = pdfSize.first / itemWidth
        val scaleY = pdfSize.second / itemHeight
        val pdfX = pointX * scaleX
        val pdfY = pointY * scaleY

        return pdfTextParser.getWordClusterTouch(wordInfo, touchPointExtract(PointF(pdfX, pdfY)))
    }

    private var loadWordsJob: Job? = null
    fun loadVisiblePagesWords(visiblePages: List<Int>) {
        loadWordsJob?.cancel()

        loadWordsJob = viewModelScope.launch {
            delay(300)
            try {
                val keysToRemove = pageWords.keys.filter { it !in visiblePages }
                keysToRemove.forEach { pageWords.remove(it) }

                coroutineScope {
                    val deferred = visiblePages.map { pageIndex ->
                        async(Dispatchers.IO) {
                            pageWords[pageIndex]?.let {
                                return@async pageIndex to it
                            }
                            val wordsInPage = pdfTextParser.getWordsInPage(
                                pdfRendererManager.getPdDocument()!!,
                                pageIndex
                            )
                            val avgWidth = wordsInPage.map { it.rect.width() }.average().toFloat()
                            val avgHeight = wordsInPage.map { it.rect.height() }.average().toFloat()

                            val sortedWords = pdfTextParser.groupWords(
                                wordsInPage.map { it.copy() },
                                avgWidth * 3f,
                                avgHeight / 2
                            )
                            pageIndex to sortedWords
                        }
                    }
                    val results = deferred.awaitAll()
                    results.forEach { (pageIndex, words) ->
                        pageWords[pageIndex] = words
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun getWordInPage(index: Int): List<WordInfo> {
        return pdfTextParser.getWordsInPage(pdfRendererManager.getPdDocument()!!, index)
    }

    fun textParserPointer(
        startPointer: PointerIndex,
        endPointer: PointerIndex,
        startWidth: Int,
        startHeight: Int,
        endWidth: Int,
        endHeight: Int,
    ): List<WordInfo> {
        val pageSelections = mutableListOf<WordInfo>()

        val (pointerAtLowerPage, pointerAtHighPage) =
            if (startPointer.pageIndex <= endPointer.pageIndex)
                startPointer to endPointer
            else
                endPointer to startPointer

        val pageRange = pointerAtLowerPage.pageIndex..pointerAtHighPage.pageIndex

        for (pageIndex in pageRange) {
            if (!pageSelect.containsKey(pageIndex)) {
                val wordInfo = pageWords[pageIndex] ?: run {
                    val wordsInPage = getWordInPage(pageIndex)

                    val avgWidth = wordsInPage.map { it.rect.width() }.average().toFloat()
                    val avgHeight = wordsInPage.map { it.rect.height() }.average().toFloat()

                    pdfTextParser.groupWords(
                        wordsInPage.map { it.copy() },
                        avgWidth * 3f,
                        avgHeight / 2
                    ).also {
                        pageWords[pageIndex] = it
                    }
                }

                pageSelect[pageIndex] = wordInfo
            }
        }

        val keysToRemove = pageSelect.keys.filter { it !in pageRange }
        for (key in keysToRemove) {
            pageSelect.remove(key)
        }

        for (pageIndex in pointerAtLowerPage.pageIndex..pointerAtHighPage.pageIndex) {
            val wordsInPage = pageSelect[pageIndex] ?: continue
            val pdfSize = pdfRendererManager.getPdPageSize(pageIndex)
            val pdfStartPointer = if (pointerAtLowerPage.pageIndex == pageIndex) {
                if (startPointer.pageIndex == pageIndex) {
                    itemToPdfPointer(
                        pointerAtLowerPage.pointF,
                        startWidth,
                        startHeight,
                        pdfSize.first,
                        pdfSize.second
                    )
                } else {
                    itemToPdfPointer(
                        pointerAtLowerPage.pointF,
                        startWidth,
                        endHeight,
                        pdfSize.first,
                        pdfSize.second
                    )
                }
            } else {
                PointF(0f, 0f)
            }
            val pdfEndPointer = if (pointerAtHighPage.pageIndex == pageIndex) {
                if (endPointer.pageIndex == pageIndex) {
                    itemToPdfPointer(
                        pointerAtHighPage.pointF,
                        endWidth,
                        endHeight,
                        pdfSize.first,
                        pdfSize.second
                    )
                } else {
                    itemToPdfPointer(
                        pointerAtHighPage.pointF,
                        startWidth,
                        startHeight,
                        pdfSize.first,
                        pdfSize.second
                    )
                }

            } else {
                PointF(pdfSize.first, pdfSize.second)

            }
            val result =
                pdfTextParser.textParserPointer(wordsInPage, pdfStartPointer, pdfEndPointer)

            pageSelections.addAll(result)
        }
        return pageSelections
    }

    fun copyTextToClipboard(context: Context) {
        val text = textSelected()
        clearSelectedHighlight()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied Text", Toast.LENGTH_SHORT).show()
    }

    fun itemToPdfPointer(
        screenPointer: PointF,
        screenWidth: Int,
        screenHeight: Int,
        pdfWidth: Float,
        pdfHeight: Float
    ): PointF {
        val scaleX = pdfWidth / screenWidth
        val scaleY = pdfHeight / screenHeight
        return PointF(screenPointer.x * scaleX, screenPointer.y * scaleY)
    }

    @SuppressLint("UseKtx")
    fun captureItems(recyclerView: RecyclerView): Pair<Bitmap, Int> {
        val bitmap = createBitmap(recyclerView.width, recyclerView.height)
        val canvas = Canvas(bitmap)
        recyclerView.draw(canvas)
        return Pair(bitmap, 0)
    }

    var rectFirst: RectF? = null
    fun moveScreenToVoicedHighlight(
        recyclerView: RecyclerView,
        containerView: View,
        statusBarHeight: Int,
        rectHighlightOnScreen: MutableMap<Int, PageMark>
    ) {
        val voicedHighlight = _voicedHighlight.value ?: return
        val voicedPageIndex = voicedHighlight.keys.first()

        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val firstVisiblePage = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePage = layoutManager.findLastVisibleItemPosition()

        val containerLoc = IntArray(2)
        containerView.getLocationOnScreen(containerLoc)

        val screenHeight = containerView.height / containerView.scaleY
        val screenWidth = containerView.width / containerView.scaleX
        var currentHeight = 0F
        var lastVisiblePageReal = 0
        var firstVisiblePageReal = 0
        var visibleBottomInPage = 0F
        var visibleRightInPage = 0F
        var visibleLeftInPage = 0F
        var visibleTopInPage = 0F
        var isFirstPage = true
        for (i in firstVisiblePage..lastVisiblePage) {
            val pageView = layoutManager.findViewByPosition(i) ?: continue

            val pageLoc = IntArray(2)
            pageView.getLocationOnScreen(pageLoc)
            val pageLeft = -pageLoc[0]
            val pageTop = -pageLoc[1]

            if (isFirstPage) {
                isFirstPage = false
                firstVisiblePageReal = i
                visibleLeftInPage = (pageLeft / containerView.scaleX).coerceAtLeast(0F)
                visibleTopInPage =
                    ((pageTop + statusBarHeight) / containerView.scaleY).coerceAtLeast(0F)
                visibleRightInPage = screenWidth + visibleLeftInPage
                if (visibleTopInPage > pageView.height) {
                    isFirstPage = true
                }
            }

            val currentVisibleTopInPage =
                ((pageTop + statusBarHeight) / containerView.scaleY).coerceAtLeast(0F)
            val isLastPage =
                currentHeight + (pageView.height - currentVisibleTopInPage) > screenHeight

            if (isLastPage && visibleBottomInPage == 0F) {
                lastVisiblePageReal = i
                visibleBottomInPage = screenHeight - currentHeight
            } else {
                currentHeight += (pageView.height - currentVisibleTopInPage)
            }
        }
        val sentenceFirst = rectHighlightOnScreen.values.firstOrNull() ?: return
        rectFirst = sentenceFirst.screenMarks.firstOrNull() ?: return
        if (!rectFirst!!.isOnArea(
                voicedPageIndex,
                firstVisiblePageReal,
                lastVisiblePageReal,
                visibleLeftInPage,
                visibleTopInPage,
                visibleRightInPage,
                visibleBottomInPage
            )
        ) {
            setScrollToPageOnPos(voicedPageIndex, rectFirst)
        }

    }


    fun setScrollToPageOnPos(pageIndex: Int, firstRectF: RectF? = null) {
        rectFirst = firstRectF
        _scrollToPageEvent.value = pageIndex
    }

    fun moveScreenToVoiced(
        recyclerView: RecyclerView,
        containerView: View,
        statusBarHeight: Int
    ) {
        val scrollToPageIndex = _scrollToPageEvent.value ?: return
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val pageView = layoutManager.findViewByPosition(scrollToPageIndex)
        if (pageView == null) {
            layoutManager.scrollToPosition(scrollToPageIndex)

            recyclerView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View?,
                    left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    val targetView = layoutManager.findViewByPosition(scrollToPageIndex)
                    if (targetView != null) {
                        recyclerView.removeOnLayoutChangeListener(this)
                        moveScreenToVoiced(recyclerView, containerView, statusBarHeight)
                    }
                }
            })
            return
        }
        val pageLoc = IntArray(2)
        pageView.getLocationOnScreen(pageLoc)
        if (pageLoc[1] != 0) {
            if (rectFirst != null) {
                val marginOffset = 25
                val containerH = containerView.height
                val scaledH = containerH * containerView.scaleY
                val maxY = if (scaledH > containerH) (scaledH - containerH) / 2 else 0f
                var offsetY =
                    rectFirst!!.top + (pageLoc[1] - statusBarHeight) / containerView.scaleY

                if (offsetY < 0) {
                    val remainUp = recyclerView.scrollUp(-offsetY.toInt())
                    if (remainUp > 0)
                        containerView.translationY += remainUp * containerView.scaleY
                } else {

                    val moveDownOffset =
                        -containerView.translationY + offsetY * containerView.scaleY
                    if (moveDownOffset > maxY) {

                        if (maxY != 0f) {
                            containerView.translationY = -maxY
                            offsetY -= maxY / containerView.scaleY
                        }
                    } else {
                        containerView.translationY = -moveDownOffset
                        offsetY = 0f
                    }
                    recyclerView.scrollDown(offsetY.toInt())


                }

                val parentView = containerView.parent as View
                val scaledWidth = containerView.width * containerView.scaleX
                val screenWidth = parentView.width

                val deltaTranslationX = (screenWidth - scaledWidth) / 2f
                val targetX =
                    -deltaTranslationX - rectFirst!!.left * containerView.scaleX + marginOffset

                val minTranslationX = screenWidth - scaledWidth - deltaTranslationX
                val maxTranslationX = -deltaTranslationX

                val clampedX = targetX.coerceIn(minTranslationX, maxTranslationX)

                if (rectFirst!! != RectF()) containerView.translationX = clampedX
            }
        }
    }

    fun RecyclerView.scrollDown(dy: Int): Int {
        if (dy <= 0) return 0

        val info = getScrollableInfo()
        val consumed = minOf(dy, info.remainingDown)

        if (consumed > 0) {
            scrollBy(0, consumed)
        }

        return dy - consumed
    }

    fun RecyclerView.scrollUp(dy: Int): Int {
        if (dy <= 0) return 0

        val info = getScrollableInfo()
        val consumed = minOf(dy, info.remainingUp)

        if (consumed > 0) {
            scrollBy(0, -consumed)
        }

        return dy - consumed
    }

    data class ScrollResult(
        val totalScrollable: Int,
        val remainingDown: Int,
        val remainingUp: Int
    )

    fun RecyclerView.getScrollableInfo(): ScrollResult {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        val offset = computeVerticalScrollOffset()

        val totalScrollable = (range - extent)
        val remainingDown = (totalScrollable - offset)

        return ScrollResult(
            totalScrollable = totalScrollable,
            remainingDown = remainingDown,
            remainingUp = offset
        )
    }

    fun RectF.isOnArea(
        pageIndex: Int,
        firstVisiblePage: Int,
        lastVisiblePage: Int,
        visibleLeft: Float,
        visibleTop: Float,
        visibleRight: Float,
        visibleBottom: Float
    ): Boolean {
        if (pageIndex !in firstVisiblePage..lastVisiblePage) return false

        val horizontallyVisible = right >= visibleLeft && left <= visibleRight
        val verticallyVisible =
            (bottom >= visibleTop && pageIndex == firstVisiblePage) || (top <= visibleBottom && pageIndex == lastVisiblePage) || (pageIndex > firstVisiblePage && pageIndex < lastVisiblePage)
        return horizontallyVisible && verticallyVisible
    }

    fun getLocationScreen(recyclerView: RecyclerView, containerView: View, statusBarHeight: Int) {
        val location = IntArray(2)
        containerView.getLocationOnScreen(location)
        val xScreen = location[0] / containerView.scaleX
        val yScreen = (location[1] - statusBarHeight) / containerView.scaleY
        val child = recyclerView.findChildViewUnder(-xScreen, -yScreen)

        if (child != null) {
            val pageIndex = recyclerView.getChildAdapterPosition(child)

            val wordsInPage = getWordInPage(pageIndex)
            val avgWidth = wordsInPage.map { it.rect.width() }.average().toFloat()
            val avgHeight = wordsInPage.map { it.rect.height() }.average().toFloat()
            val sortedWords = pdfTextParser.groupWords(
                wordsInPage.map { it.copy() },
                avgWidth * 3f,
                avgHeight / 2
            )

            val allGroups = mutableListOf<Pair<Int, MutableList<WordInfo>>>()
            var currentGroup = mutableListOf<WordInfo>()
            var currentGroupId: Float? = null
            var groupCount = 0

            for (word in sortedWords) {
                if (word.columnGroupId != currentGroupId) {
                    if (currentGroup.isNotEmpty()) {
                        if (currentGroupId != null) {
                            currentGroup =
                                currentGroup.sortedWith(compareBy<WordInfo> { it.columnIndex }.thenBy { it.lineYKey })
                                    .toMutableList()
                        }
                        allGroups.add(groupCount to currentGroup)
                    }

                    currentGroupId = word.columnGroupId
                    groupCount++
                    currentGroup = mutableListOf(word)
                } else {
                    currentGroup.add(word)
                }
            }
            if (currentGroup.isNotEmpty()) {
                allGroups.add(groupCount to currentGroup)
            }

            val allWords = allGroups.flatMap { it.second }

            val splitIntoSentences = splitIntoSentences(allWords)

            currentVoicePage = pageIndex
            if (sentences[currentVoicePage] == null) {
                sentences[currentVoicePage] = mutableListOf()
            }
            sentences[currentVoicePage]!!.addAll(splitIntoSentences)

            val location = IntArray(2)
            containerView.getLocationOnScreen(location)
            val xScreen = location[0] / containerView.scaleX
            val yScreen = child.y + (location[1] - statusBarHeight) / containerView.scaleY

            val pdfSize = pdfRendererManager.getPdPageSize(pageIndex)

            val pointInPdf = itemToPdfPointer(
                PointF(-xScreen, -yScreen),
                child.width,
                child.height,
                pdfSize.first,
                pdfSize.second
            )

            var firstWord =
                sentences[currentVoicePage]?.map { it.words }?.flatten()
                    ?.firstOrNull { it.rect.top > pointInPdf.y }


            while (firstWord == null) {
                addSentencesNextPage()
                currentVoicePage++
                firstWord =
                    sentences[currentVoicePage]?.map { it.words }?.flatten()
                        ?.firstOrNull { it.rect.top > 0 }
            }

            sentences[currentVoicePage]?.forEach { (sentenceIndex, words) ->
                if (words.contains(firstWord)) {
                    if (words.first() != firstWord) {
                        if (sentences[currentVoicePage]?.last()?.sentenceIndex == sentenceIndex) {
                            currentVoicePage++
                            voiceStartIndex = 0
                        } else {
                            voiceStartIndex = sentenceIndex + 1
                        }
                        return@forEach
                    }
                    voiceStartIndex = sentenceIndex
                }
            }

            if (voiceStartIndex == sentences[currentVoicePage]!!.last().sentenceIndex) {
                val endMarks = setOf(".", "!", "?", ":", ";", "…", "...", "—", "”")
                val lastWord =
                    sentences[currentVoicePage]!!.last().words.last().word
                if (!endMarks.any { mark -> lastWord.endsWith(mark) }) {
                    addSentencesNextPage()

                    val sentencesText =
                        sentences[currentVoicePage]!!.last().textSentence + sentences[currentVoicePage + 1]!!.first().textSentence

                    addVoicedHighlight(
                        currentVoicePage,
                        sentences[currentVoicePage]!!.last().sentenceIndex,
                        sentences,
                        true,
                    )

                    speakSentence(sentencesText)
                    voiceStartIndex = 0
                    currentVoicePage++
                } else {
                    addVoicedHighlight(
                        currentVoicePage,
                        sentences[currentVoicePage]!!.last().sentenceIndex,
                        sentences
                    )

                    speakSentence(sentences[voiceStartIndex]!!.last().textSentence)
                }
            } else {
                addVoicedHighlight(currentVoicePage, voiceStartIndex, sentences)
                speakSentence(sentences[currentVoicePage]!![voiceStartIndex].textSentence)
            }
        }
    }

    private fun addSentencesNextPage() {
        val currentPage = currentVoicePage + 1
        val words = getWordInPage(currentPage)
        val splitSentences = splitIntoSentences(words)
        if (sentences[currentPage] == null) {
            sentences[currentPage] = mutableListOf()
        }
        sentences[currentPage]!!.addAll(splitSentences)
    }

    private fun speakSentence(sentence: String) {
        val utteranceId = "sentence_${System.currentTimeMillis()}"
        tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }


    fun splitIntoSentences(words: List<WordInfo>): List<SentenceInfo> {
        val endMarks = setOf(".", "!", "?", ":", ";", "…", "...", "—", "”")
        val sentences = mutableListOf<SentenceInfo>()
        val currentSentence = mutableListOf<WordInfo>()
        var sentenceIndex = 0

        for (word in words) {
            currentSentence.add(word)

            if (word.word in endMarks || endMarks.any { word.word.endsWith(it) }) {
                sentences.add(SentenceInfo(sentenceIndex, currentSentence.toList()))
                currentSentence.clear()
                sentenceIndex++
            }
        }

        if (currentSentence.isNotEmpty()) {
            sentences.add(SentenceInfo(sentenceIndex, currentSentence.toList()))
        }

        return sentences
    }

    fun updateStartPointerMove(
        wordsInPointer: List<WordInfo>,
        startPointer: PointerIndex,
        endPointer: PointerIndex
    ): PointerIndex {
        val words = _selectedHighlight.value ?: return startPointer

        val firstRect = words.entries.firstOrNull()?.value?.screenMarks?.first()
        val lastRect = words.entries.lastOrNull()?.value?.screenMarks?.last()

        return if (startPointer.pageIndex > wordsInPointer.last().pageIndex) {
            PointerIndex(
                wordsInPointer.last().pageIndex,
                PointF(
                    lastRect!!.right,
                    lastRect.bottom
                )
            )
        } else if (startPointer.pageIndex < wordsInPointer.first().pageIndex) {
            PointerIndex(
                wordsInPointer.first().pageIndex,
                PointF(
                    firstRect!!.left,
                    firstRect.bottom
                )
            )
        } else if (startPointer.pageIndex > endPointer.pageIndex) {
            PointerIndex(
                startPointer.pageIndex,
                PointF(
                    lastRect!!.right,
                    lastRect.bottom
                )
            )
        } else if (startPointer.pageIndex == endPointer.pageIndex) {
            if (wordsInPointer.map { it.lineYKey }
                    .distinct().size == 1 && startPointer.pointF.x > endPointer.pointF.x) {
                PointerIndex(
                    startPointer.pageIndex,
                    PointF(
                        lastRect!!.right,
                        lastRect.bottom
                    )
                )
            } else if (wordsInPointer.first().columnGroupId != null && wordsInPointer.first().columnGroupId == wordsInPointer.last().columnGroupId
                && startPointer.pointF.x > endPointer.pointF.x
            ) {
                PointerIndex(
                    startPointer.pageIndex,
                    PointF(
                        lastRect!!.right,
                        lastRect.bottom
                    )
                )
            } else if (startPointer.pointF.y > endPointer.pointF.y) {
                PointerIndex(
                    startPointer.pageIndex,
                    PointF(
                        lastRect!!.right,
                        lastRect.bottom
                    )
                )
            } else {
                PointerIndex(
                    startPointer.pageIndex,
                    PointF(
                        firstRect!!.left,
                        firstRect.bottom
                    )
                )
            }
        } else {
            PointerIndex(
                startPointer.pageIndex,
                PointF(
                    firstRect!!.left,
                    firstRect.bottom
                )
            )
        }
    }

    fun updateEndPointerMove(
        wordsInPointer: List<WordInfo>,
        startPointer: PointerIndex,
        endPointer: PointerIndex
    ): PointerIndex {
        val words = _selectedHighlight.value ?: return endPointer

        val firstRect = words.entries.firstOrNull()?.value?.screenMarks?.first()
        val lastRect = words.entries.lastOrNull()?.value?.screenMarks?.last()

        return if (endPointer.pageIndex > wordsInPointer.last().pageIndex) {
            PointerIndex(
                wordsInPointer.last().pageIndex,
                PointF(
                    lastRect!!.right,
                    lastRect.bottom
                )
            )
        } else if (endPointer.pageIndex < wordsInPointer.first().pageIndex) {
            PointerIndex(
                wordsInPointer.first().pageIndex,
                PointF(
                    firstRect!!.left,
                    firstRect.bottom
                )
            )
        } else if (endPointer.pageIndex < startPointer.pageIndex) {
            PointerIndex(
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
                    PointerIndex(
                        endPointer.pageIndex,
                        PointF(
                            firstRect.left,
                            firstRect.bottom
                        )
                    )
                } else {
                    PointerIndex(
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
                    PointerIndex(
                        endPointer.pageIndex,
                        PointF(
                            firstRect!!.left,
                            firstRect.bottom
                        )
                    )
                } else {
                    PointerIndex(
                        endPointer.pageIndex,
                        PointF(
                            lastRect!!.right,
                            lastRect.bottom
                        )
                    )
                }

            } else if (startPointer.pointF.y > endPointer.pointF.y) {
                PointerIndex(
                    endPointer.pageIndex,
                    PointF(
                        firstRect!!.left,
                        firstRect.bottom
                    )
                )
            } else {
                PointerIndex(
                    endPointer.pageIndex,
                    PointF(
                        lastRect!!.right,
                        lastRect.bottom
                    )
                )
            }
        } else {
            PointerIndex(
                endPointer.pageIndex,
                PointF(
                    lastRect!!.right,
                    lastRect.bottom
                )
            )
        }
    }

    fun noteSaved(noteContent: String) {
        val selectedHighlights = _selectedHighlight.value ?: return
        selectedHighlights.forEach { (pageIndex, mark) ->
            val confirmId: Long = System.currentTimeMillis()
            val confirmMark = PageMark(
                confirmId = mark.confirmId.takeIf { it != -1L } ?: confirmId,
                marksState = MarksState.CONFIRM,
                screenMarks = mark.screenMarks,
                pdfMarks = mark.pdfMarks,
                color = mark.color ?: "#4A90E2".toColorInt(),
                text = mark.text,
                contentNote = noteContent,
                isFirstPageMark = mark.isFirstPageMark
            )
            selectedHighlights[pageIndex] = confirmMark
        }
        _selectedHighlight.value = selectedHighlights
        updateConfirmHighlight()
    }

    fun textSelected(): String? {
        val textList = _selectedHighlight.value?.flatMap { (_, mark) -> mark.text ?: emptyList() }
        if (!textList.isNullOrEmpty()) {
            val text = textList.joinToString("").replace(Regex("\\s+"), " ").trim()
            return text
        }
        return null
    }


    fun getTranslationTargets(
        context: Context,
        isTranslate: Boolean
    ): TranslationResult? {
        val text = textSelected() ?: return null
        if (text.isBlank()) return null

        val translateIntent = Intent().apply {
            action = Intent.ACTION_PROCESS_TEXT
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }

        val pm = context.packageManager
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

        return TranslationResult(translateApps, browserUrl, text)
    }


    fun mergeSelectedAreas(selectedList: List<RectF>): RectF? {
        if (selectedList.isEmpty()) return null

        val result = RectF(selectedList[0])
        for (i in 1 until selectedList.size) {
            result.union(selectedList[i])
        }
        return result
    }

    fun pointerShape(
        wordsInPointer: List<WordInfo>,
        startPointerIndex: PointerIndex,
        endPointerIndex: PointerIndex,
        highlightPointerView: HighlightPointerView,
        pdfRecyclerView: RecyclerView
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
            highlightPointerView.setStartInverted(startPointerIndex.pointF.x < POINTER_BORDER_SPACING)
        }
        if (endAtBorder) {
            highlightPointerView.setEndInverted(endPointerIndex.pointF.x > (screenWidth - POINTER_BORDER_SPACING))
        }

        if (firstWord.pageIndex == lastWord.pageIndex) {
            val startViewHolder =
                pdfRecyclerView.findViewHolderForAdapterPosition(startPointerIndex.pageIndex)
            val startItemView = startViewHolder?.itemView

            val startWidth = startItemView!!.width
            val startHeight = startItemView.height

            val endViewHolder =
                pdfRecyclerView.findViewHolderForAdapterPosition(endPointerIndex.pageIndex)
            val endItemView = endViewHolder?.itemView

            val endWidth = endItemView!!.width
            val endHeight = endItemView.height


            val startPointer = itemToPdfPointerIndex(
                startPointerIndex.pointF,
                startWidth.toFloat(),
                startHeight.toFloat(),
                startPointerIndex.pageIndex
            )

            val endPointer = itemToPdfPointerIndex(
                endPointerIndex.pointF,
                endWidth.toFloat(),
                endHeight.toFloat(),
                endPointerIndex.pageIndex
            )

            fun RectF.centerPoint(): PointF {
                return PointF(centerX(), centerY())
            }

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
                    highlightPointerView.setStartInverted(false)
                } else {
                    highlightPointerView.setStartInverted(true)
                }
            }

            if (!endAtBorder) {
                if (endIsLast) {
                    highlightPointerView.setEndInverted(false)
                } else {
                    highlightPointerView.setEndInverted(true)
                }
            }
        } else {
            if (!startAtBorder) {
                if (startPointerIndex.pageIndex == firstWord.pageIndex) {
                    highlightPointerView.setStartInverted(false)
                } else {
                    highlightPointerView.setStartInverted(true)
                }
            }

            if (!endAtBorder) {
                if (endPointerIndex.pageIndex == lastWord.pageIndex) {
                    highlightPointerView.setEndInverted(false)
                } else {
                    highlightPointerView.setEndInverted(true)
                }
            }
        }
    }


    fun addBookmark(note: String, pageIndex: Int) {
        viewModelScope.launch {
            val newBookmark = Bookmark(bookId = bookId, pageNumber = pageIndex, note = note)
            bookMarkService.insert(note, bookId, pageIndex)
            _bookmarks.value = _bookmarks.value.orEmpty() + newBookmark
        }
    }

    fun removeBookmark(pageIndex: Int){
        viewModelScope.launch {
            bookMarkService.deleteByBookAndPage(bookId, pageIndex)
            _bookmarks.value = _bookmarks.value?.filterNot { it.pageNumber == pageIndex }
        }
    }

    fun getBookmark() {
        viewModelScope.launch {
            val result = bookMarkService.getBookmarksByBook(bookId)
            _bookmarks.value = result
        }
    }

    fun getBookmarkForToc(): List<BookmarkItem> {
        return _bookmarks.value?.map { bookmark ->
            BookmarkItem(
                title = bookmark.note ?: "Bookmark",
                page = bookmark.pageNumber ?: 0,
                firstRect = RectF()
            )
        } ?: emptyList()
    }

    fun showBookMarkInMainScreen(
        recyclerView: RecyclerView,
        highlightPointerView: HighlightPointerView
    ) {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstPage = lm.findFirstVisibleItemPosition()

        val bookmarkedPages = _bookmarks.value
            ?.mapNotNull { it.pageNumber }
            ?.toSet()
            .orEmpty()

        highlightPointerView.updateShowBookmark(firstPage in bookmarkedPages)
    }

    fun updateSelectedHighlights(
        pageIndex: Int,
        marks: List<RectF>,
        pdfMarks: List<RectF>,
        text: List<String>,
        confirmId: Long? = -1,
        contentNote: String? = null,
    ) {
        val currentMap = _selectedHighlight.value ?: mutableMapOf()
        val newMark = PageMark(
            marksState = MarksState.LONG_PRESSED,
            screenMarks = marks,
            pdfMarks = pdfMarks,
            confirmId = confirmId,
            text = text,
            contentNote = contentNote,
            isFirstPageMark = currentMap.keys.isEmpty() || currentMap.keys.firstOrNull() == pageIndex
        )

        currentMap[pageIndex] = newMark
        _selectedHighlight.value = currentMap.toSortedMap().toMutableMap()
    }

    fun updateConfirmHighlight() {
        val currentSelected = _selectedHighlight.value ?: return
        val confirmed = _confirmedHighlight.value ?: mutableMapOf()
        val confirmId = System.currentTimeMillis()

        currentSelected.forEach { (pageIndex, mark) ->
            val confirmedList = confirmed.getOrPut(pageIndex) { mutableListOf() }
            val existingMark = confirmedList.find { it.confirmId == mark.confirmId }

            if (existingMark != null) {
                existingMark.color = mark.color
                existingMark.contentNote = mark.contentNote
                viewModelScope.launch {
                    highlightService.updateByConfirmId(
                        existingMark.confirmId!!,
                        existingMark.contentNote,
                        existingMark.color
                    )
                }
            } else {
                mark.confirmId = confirmId
                confirmedList.add(mark)
                viewModelScope.launch {
                    highlightService.insert(mark, bookId, pageIndex)
                }
            }
        }
        _confirmedHighlight.value = LinkedHashMap(confirmed.toSortedMap())
        clearSelectedHighlight()
    }

    fun loadConfirmedHighlight() {
        viewModelScope.launch {
            val highlights = highlightService.getHighlightsByBookPageMap(bookId)

            highlights.forEach { (pageIndex, pageMarks) ->
                pageMarks.forEach { pageMark ->
                    pageMark.screenMarks = pdfToItemRect(
                        pageMark.screenMarks,
                        pageSizes[pageIndex].width,
                        pageSizes[pageIndex].height,
                        pageIndex
                    )
                }
            }
            pageSizes
            _confirmedHighlight.value = highlights
        }
    }

    fun updateColorSelection(color: Int) {
        _selectedHighlight.value?.forEach { (_, mark) ->
            mark.color = color
        }
    }

    fun clearSelectedHighlight() {
        _selectedHighlight.value = mutableMapOf()
    }


    fun deleteSelectedHighlight() {
        val confirmId = _selectedHighlight.value?.entries?.firstOrNull()?.value?.confirmId
        if (confirmId != null && confirmId != -1L) {
            val current = _confirmedHighlight.value ?: return

            val updated = current.toMutableMap().apply {
                forEach { (_, list) ->
                    list.removeIf { it.confirmId == confirmId }
                }
            }

            viewModelScope.launch {
                highlightService.deleteByConfirmId(confirmId)
            }

            _confirmedHighlight.value = updated
        }
    }

    fun isTapInConfirmed(pageIndex: Int, x: Float, y: Float): Boolean {
        val confirmed = _confirmedHighlight.value ?: return false
        val sortedHighlights =
            confirmed[pageIndex]?.sortedByDescending { it.confirmId }?.toMutableList()
        val tappedHighlight = sortedHighlights?.find { mark ->
            mark.screenMarks.any { rect -> rect.contains(x, y) }
        }

        var isTapConfirmed = false

        tappedHighlight?.confirmId?.let { confirmId ->
            confirmed.forEach { (pageIndex, marksList) ->
                val mark = marksList.find { it.confirmId == confirmId }
                if (mark != null) {
                    _selectedHighlight.value =
                        _selectedHighlight.value?.toMutableMap()?.apply { this[pageIndex] = mark }
                    isTapConfirmed = true
                }
            }
        }
        return isTapConfirmed
    }

    fun pdfToItemRect(
        pdfRect: List<RectF>,
        screenWidth: Int,
        screenHeight: Int,
        pageIndex: Int,
    ): List<RectF> {
        val pdfSize = pdfRendererManager.getPdPageSize(pageIndex)
        val scaleX = screenWidth / pdfSize.first
        val scaleY = screenHeight / pdfSize.second

        return pdfRect.map { rect ->
            val left = rect.left * scaleX
            val right = rect.right * scaleX

            val top = rect.top * scaleY
            val bottom = rect.bottom * scaleY

            RectF(left, top, right, bottom)
        }
    }

    fun itemToPdfPointerIndex(
        itemPointer: PointF,
        screenWidth: Float,
        screenHeight: Float,
        pageIndex: Int,
    ): PointF {
        val pdfSize = pdfRendererManager.getPdPageSize(pageIndex)
        val itemWidth = pdfSize.first
        val itemHeight = pdfSize.second
        val scaleX = itemWidth / screenWidth
        val scaleY = itemHeight / screenHeight
        return PointF(itemPointer.x * scaleX, itemPointer.y * scaleY)
    }


    fun touchPointExtract(point: PointF, radius: Float = 2f): RectF {
        return RectF(
            point.x - radius,
            point.y - radius,
            point.x + radius,
            point.y + radius
        )
    }

    fun preload(center: Int, windowSize: Int = preloadDistance) {
        val start = (center - windowSize).coerceAtLeast(0)
        val end = (center + windowSize).coerceAtMost(pageCount - 1)

        val targetPages = (start..end).toSet()

        renderJobs.keys
            .filter { it !in targetPages }
            .forEach { page ->

                renderJobs[page]?.cancel()
                renderJobs.remove(page)
            }

        val orderedPages =
            targetPages.sortedBy { abs(it - center) }

        orderedPages.forEach { page ->

            if (cache.get(page) == null) {
                renderPageAsync(page)
            }
        }
    }

    fun getCatalogue(): List<TocItem> {
        val pdDocument = pdfRendererManager.getPdDocument() ?: return emptyList()
        val outline = pdDocument.documentCatalog.documentOutline ?: return emptyList()
        val first = outline.firstChild ?: return emptyList()

        return getCatalogueFrom(first, 0)
    }

    fun getCatalogueFrom(item: PDOutlineItem?, level: Int = 0): List<TocItem> {
        val pdDocument = pdfRendererManager.getPdDocument() ?: return emptyList()
        val tocList = mutableListOf<TocItem>()

        var current = item
        val getPageNumberFromDestination: (PDPageDestination) -> Int? = { dest ->
            if (dest.pageNumber != -1) {
                dest.pageNumber + 1
            } else {
                dest.page?.let { pdDocument.pages.indexOf(it) + 1 }
            }
        }

        while (current != null) {

            val title = current.title
            var pageNumber = -1

            val destination = current.destination
                ?: (current.action as? PDActionGoTo)?.destination

            when (destination) {
                is PDPageDestination -> {
                    pageNumber = getPageNumberFromDestination(destination) ?: -1
                }

                is PDNamedDestination -> {
                    Log.d("TOC", "Named Destination: ${destination.namedDestination}")
                }
            }

            val children = if (current.hasChildren()) {
                getCatalogueFrom(current.firstChild, level + 1)
            } else emptyList()

            tocList.add(
                TocItem(
                    title = title,
                    page = pageNumber,
                    children = children,
                    isExpanded = false,
                    level = level
                )
            )

            current = current.nextSibling
        }

        return tocList
    }

    fun getCurrentPosition(recyclerView: RecyclerView): ReadingPosition {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val pageIndex = layoutManager.findFirstVisibleItemPosition()
        val pageView = layoutManager.findViewByPosition(pageIndex)

        val pageHeight = pageView?.height ?: 1
        val scrolledInPage = -(pageView?.top ?: 0)
        val pagePercentage = scrolledInPage.toFloat() / pageHeight.toFloat()

        return ReadingPosition(pageIndex, pagePercentage.coerceIn(0f, 1f))
    }

    fun saveCurrentPage(readingPosition: ReadingPosition) {
        pageCountManager.savePageCount(bookId, readingPosition)
    }

    fun loadPageCount(): ReadingPosition? {
        return pageCountManager.loadPageCount(bookId)
    }

    fun getTitle(): String {
        val pdDocument = pdfRendererManager.getPdDocument() ?: return ""
        var title = pdDocument.documentInformation.title
        if (title.isNullOrBlank()) {
            title = ""
        }
        return title
    }

    //    fun renderPageAsync(index: Int) {
//
//        // If there are too many jobs → cancel the oldest job
//        if (renderJobs.size >= MAX_RENDER_JOBS) {
//            val oldestKey = renderJobs.entries.first().key
//            renderJobs[oldestKey]?.cancel()
//            renderJobs.remove(oldestKey)
//        }
//        // If there is already a job for this index → cancel it
//        renderJobs[index]?.cancel()
//
//        val job = viewModelScope.launch(Dispatchers.IO) {
//            renderSemaphore.withPermit {
//                try {
//                    val cached = diskCacheManager.loadBitmap(index)
//                    Log.v("Page preload renderPageAsync", " $index")
//                    if (cached != null && !cached.isRecycled) {
//                        cache.put(index, cached)
//                        _pageBitmaps.tryEmit(index to cached)
//                        return@launch
//                    }
//                    val bmp = pdfRendererManager.renderPage(index, pageSizes[index])
//                    cache.put(index, bmp)
//                    _pageBitmaps.tryEmit(index to bmp)
//                } catch (e: Exception) {
//                    Log.e("PdfDebug", "Error rendering page $index: ${e.message}", e)
//                }
//            }
//        }
//
//        renderJobs[index] = job
//    }
    fun areVisiblePagesReady(recyclerView: RecyclerView): Boolean {
        val lm = recyclerView.layoutManager as LinearLayoutManager

        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()

        return (first..last).all { page ->
            isPageRendered(page)
        }
    }

    fun isPageRendered(index: Int): Boolean {
        return cache.get(index) != null
    }

    fun renderPageAsync(index: Int) {

        if (cache.get(index) != null) return

        if (renderJobs[index]?.isActive == true) return

        val job = viewModelScope.launch(Dispatchers.IO) {

            try {
                renderSemaphore.withPermit {

                    val cached = diskCacheManager.loadBitmap(index)

                    if (cached != null && !cached.isRecycled) {

                        ensureActive()

                        cache.put(index, cached)
                        _pageBitmaps.tryEmit(index to cached)

                        return@withPermit
                    }

                    val bmp = pdfRendererManager.renderPage(
                        index,
                        pageSizes[index]
                    )

                    ensureActive()

                    cache.put(index, bmp)
                    _pageBitmaps.tryEmit(index to bmp)
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.e(
                    "PdfDebug",
                    "Error rendering page $index",
                    e
                )
            }
        }

        renderJobs[index] = job

        job.invokeOnCompletion {
            renderJobs.remove(index)
        }
    }


    suspend fun storeBitmapInFile() = coroutineScope {
        diskCacheManager.clear()
        for ((index, bitmap) in cache.snapshot()) {
            launch(Dispatchers.IO) {
                try {
                    if (bitmap != null && !bitmap.isRecycled) {
                        val existing = diskCacheManager.loadBitmap(index)
                        if (existing == null || existing.isRecycled) {
                            diskCacheManager.saveBitmap(index, bitmap)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PdfDebug", "Error saving bitmap $index: ${e.message}", e)
                }
            }
        }
    }

    fun clearRenderJobs() {
        renderJobs.values.forEach { it.cancel() }
        renderJobs.clear()
        cache.evictAll()
    }

    override fun onCleared() {
        super.onCleared()
        runBlocking {
            storeBitmapInFile()
        }
        cache.evictAll()
        pdfRendererManager.close()

        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}