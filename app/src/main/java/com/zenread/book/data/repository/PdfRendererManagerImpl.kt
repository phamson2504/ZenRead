package com.zenread.book.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Size
import com.shockwave.pdfium.PdfiumCore
import com.zenread.book.domain.repository.PdfRendererManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.core.graphics.createBitmap
import com.shockwave.pdfium.PdfDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.FileInputStream
import java.io.IOException

class PdfRendererManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PdfRendererManager {

    private var pdfiumCore = PdfiumCore(context)
    private var pdfDocument: PdfDocument? = null
    private var pdDocument: PDDocument? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    override fun openDocument(uri: Uri) {
        try {
            fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            fileDescriptor?.let { fd ->
                pdfDocument = pdfiumCore.newDocument(fd)

                // PDFBox
                FileInputStream(fd.fileDescriptor).use { inputStream ->
                    pdDocument = PDDocument.load(inputStream)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun getPdDocument(): PDDocument? {
        return pdDocument
    }

    override fun getPageCount(): Int {
        return pdfDocument?.let { pdfiumCore.getPageCount(it) } ?: 0
    }

    override fun  getPageSize(pageIndex: Int): Size {
        val doc = pdfDocument ?: throw IllegalStateException("PDF not opened")
        pdfiumCore.openPage(doc, pageIndex)
        val width = pdfiumCore.getPageWidth(pdfDocument, pageIndex)
        val height = pdfiumCore.getPageHeight(pdfDocument, pageIndex)
        return Size(width, height)
    }

    override fun getPdPageSize(pageIndex: Int): Pair<Float, Float> {
        val doc = pdDocument ?: throw IllegalStateException("PDF not opened")
        val page: PDPage = doc.getPage(pageIndex)
        val width = page.mediaBox.width
        val height = page.mediaBox.height
        return Pair(width, height)
    }

    override fun renderPage(pageIndex: Int, target: Size): Bitmap {
        pdfDocument ?: throw IllegalStateException("PDF not opened")
        pdfiumCore.openPage(pdfDocument, pageIndex)
        val bitmap = createBitmap(target.width, target.height)
        pdfiumCore.renderPageBitmap(
            pdfDocument,
            bitmap,
            pageIndex,
            0,
            0,
            target.width,
            target.height
        )
        return bitmap
    }

    override fun getScaledPageSize(pageIndex: Int, targetWith: Int): Size {
        val originalSize = getPageSize(pageIndex)
        val scale = targetWith.toFloat() / originalSize.width
        val scaledHeight = (originalSize.height * scale).toInt()
        return Size(targetWith, scaledHeight)
    }

    override fun close() {
        pdfDocument?.let {
            pdfiumCore.closeDocument(it)
            pdfDocument = null
        }
        fileDescriptor?.close()
        fileDescriptor = null
    }
}