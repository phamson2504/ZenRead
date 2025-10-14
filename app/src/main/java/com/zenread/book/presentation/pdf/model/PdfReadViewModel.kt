package com.zenread.book.presentation.pdf.model

import android.app.Application
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenread.book.core.system.DeviceProfileManager
import com.zenread.book.data.parser.pdf.PdfTextParser
import com.zenread.book.domain.model.WordInfo
import com.zenread.book.domain.repository.DiskCacheManager
import com.zenread.book.domain.repository.PdfRendererManager
import com.zenread.book.presentation.pdf.PointerIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class PdfReadViewModel @Inject constructor(
    application: Application,
    private val pdfRendererManager: PdfRendererManager,
    private val diskCacheManager: DiskCacheManager,
    private val pdfTextParser: PdfTextParser
) : ViewModel() {

    private val deviceProfile = DeviceProfileManager(application)
    private val _pageBitmaps = MutableSharedFlow<Pair<Int, Bitmap>>(extraBufferCapacity = 64)
    val pageBitmaps: SharedFlow<Pair<Int, Bitmap>> = _pageBitmaps

    private val renderSemaphore = Semaphore(deviceProfile.renderSemaphore)
    private val renderJobs = LinkedHashMap<Int, Job>()
    private val MAX_RENDER_JOBS = 5
    val preloadDistance = deviceProfile.preloadDistance

    private val pageSelect = mutableMapOf<Int, List<WordInfo>>()

    // 🚀 Cache RAM dùng LruCache
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

    fun initialDiskCacheManager(bookId: Int, bookTitle: String) {
        val nameDir = bookTitle.take(3) + "_" + bookId
        diskCacheManager.setBookId(nameDir)
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

    fun coordinatesPdfTouch(
        itemWidth: Int,
        itemHeight: Int,
        pointX: Float,
        pointY: Float,
        index: Int
    ): List<RectF> {
        val pdfSize = pdfRendererManager.getPdPageSize(index)
        val scaleX = pdfSize.first / itemWidth
        val scaleY = pdfSize.second / itemHeight
        val pdfX = pointX * scaleX
        val pdfY = pointY * scaleY

        val words = pdfTextParser.getWordsInPage(pdfRendererManager.getPdDocument()!!, index)
            .sortedWith(compareBy<WordInfo> { it.rect.top }.thenBy { it.rect.left })
        val result = pdfTextParser.getWordClusterTouch(words, touchPointExtract(PointF(pdfX, pdfY)))

        return pdfToItemOverlayRect(
            result.map { it.rect },
            pdfSize.first,
            pdfSize.second,
            itemWidth,
            itemHeight
        )
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
                val wordInfo = getWordInPage(pageIndex)
                pageSelect[pageIndex] = wordInfo
            }
        }

        val keysToRemove = pageSelect.keys.filter { it !in pageRange }
        for (key in keysToRemove) {
            pageSelect.remove(key)
        }

        for (pageIndex in pointerAtLowerPage.pageIndex..pointerAtHighPage.pageIndex) {
            val wordsInPage = pageSelect[pageIndex] ?: continue

            val avgWidth = wordsInPage.map { it.rect.width() }.average().toFloat()
            val avgHeight = wordsInPage.map { it.rect.height() }.average().toFloat()
            val sortedWords = pdfTextParser.groupWords(
                wordsInPage.map { it.copy() },
                avgWidth * 3f,
                avgHeight / 2
            )
            val pdfSize = pdfRendererManager.getPdPageSize(pageIndex)
            val pdfStartPointer = if (pointerAtLowerPage.pageIndex == pageIndex) {
                if (startPointer.pageIndex == pageIndex){
                    itemToPdfPointer(
                        pointerAtLowerPage.pointF,
                        startWidth,
                        startHeight,
                        pdfSize.first,
                        pdfSize.second
                    )
                }else{
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
                if (endPointer.pageIndex == pageIndex){
                    itemToPdfPointer(
                        pointerAtHighPage.pointF,
                        endWidth,
                        endHeight,
                        pdfSize.first,
                        pdfSize.second
                    )
                }else{
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
                pdfTextParser.textParserPointer(sortedWords, pdfStartPointer, pdfEndPointer)
            pageSelections.addAll(result)
        }
        return pageSelections
    }


    fun pdfToItemOverlayRect(
        pdfRects: List<RectF>,
        pdfPageWidth: Float,
        pdfPageHeight: Float,
        overlayWidth: Int,
        overlayHeight: Int
    ): List<RectF> {
        val scaleX = overlayWidth / pdfPageWidth
        val scaleY = overlayHeight / pdfPageHeight

        return pdfRects.map { rect ->
            val left = rect.left * scaleX
            val right = rect.right * scaleX

            val top = rect.top * scaleY
            val bottom = rect.bottom * scaleY

            RectF(left, top, right, bottom)
        }
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

    fun pdfToItemRect(
        pdfRects: List<RectF>,
        screenWidth: Int,
        screenHeight: Int,
        pageIndex: Int,
    ): List<RectF> {
        val pdfSize = pdfRendererManager.getPdPageSize(pageIndex)
        val scaleX = screenWidth / pdfSize.first
        val scaleY = screenHeight / pdfSize.second

        return pdfRects.map { rect ->
            val left = rect.left * scaleX
            val right = rect.right * scaleX

            val top = rect.top * scaleY
            val bottom = rect.bottom * scaleY

            RectF(left, top, right, bottom)
        }
    }

    fun itemPointerToScreen(
        itemPointer: PointF,
        screenWidth: Float,
        screenHeight: Float,
        itemWidth: Int,
        itemHeight: Int,
    ): PointF {
        val scaleX = screenWidth / itemWidth
        val scaleY = screenHeight / itemHeight
        return PointF(itemPointer.x * scaleX, itemPointer.y * scaleY)
    }

    fun touchPointExtract(point: PointF, radius: Float = 10f): RectF {
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

        val indices = (start..end).sortedBy { abs(it - center) }

        for (i in indices) {
            if ((cache.get(i) == null)) {
                renderPageAsync(i)
            }
        }
    }


    fun renderPageAsync(index: Int) {
        // If there are too many jobs → cancel the oldest job
        if (renderJobs.size >= MAX_RENDER_JOBS) {
            val oldestKey = renderJobs.entries.first().key
            renderJobs[oldestKey]?.cancel()
            renderJobs.remove(oldestKey)
        }

        // If there is already a job for this index → cancel it
        renderJobs[index]?.cancel()

        val job = viewModelScope.launch(Dispatchers.IO) {
            renderSemaphore.withPermit {
                try {
                    val cached = diskCacheManager.loadBitmap(index)
                    if (cached != null && !cached.isRecycled) {
                        cache.put(index, cached)
                        _pageBitmaps.tryEmit(index to cached)
                        return@launch
                    }

                    val bmp = pdfRendererManager.renderPage(index, pageSizes[index])
                    cache.put(index, bmp)
                    _pageBitmaps.emit(index to bmp)
                } catch (e: Exception) {
                    Log.e("PdfDebug", "Error rendering page $index: ${e.message}", e)
                }
            }
        }

        renderJobs[index] = job
    }


    suspend fun storeBitmapInFile() = coroutineScope {
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

    override fun onCleared() {
        super.onCleared()
        runBlocking {
            storeBitmapInFile()
        }
        cache.evictAll()
        pdfRendererManager.close()
    }
}