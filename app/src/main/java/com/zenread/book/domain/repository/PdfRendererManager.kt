package com.zenread.book.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import com.tom_roush.pdfbox.pdmodel.PDDocument

interface PdfRendererManager {
    fun openDocument(uri: Uri)
    fun getPageCount(): Int
    fun getPageSize(pageIndex: Int): Size
    fun renderPage(pageIndex: Int, target: Size): Bitmap
    fun getScaledPageSize(pageIndex: Int, targetWith: Int): Size
    fun getPdDocument(): PDDocument?
    fun getPdPageSize(pageIndex: Int): Pair<Float, Float>
    fun close()
}