package com.zenread.book.data.convert.pdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.zenread.book.data.file.CachedFileWrapper
import com.zenread.book.domain.model.Book
import com.zenread.book.domain.repository.FileConvert
import javax.inject.Inject

class PdfFileConvert @Inject constructor(
    private val application: Application,
) : FileConvert {
    override suspend fun convertFileToBook(cachedFileWrapper: CachedFileWrapper): Book? {
        return try {
            PDFBoxResourceLoader.init(application)
            val inputStream = cachedFileWrapper.openInputStream()
            val document = PDDocument.load(inputStream)
            val pdfInfo = document.documentInformation

            val title = pdfInfo.title?: cachedFileWrapper.name.substringBeforeLast('.')
            val author = pdfInfo.author ?: "Unknown"
            val typeFile = cachedFileWrapper.name.substringAfterLast(".", "").trim()
            val description = pdfInfo.subject ?: ""

            document.close()

            Book(
                title = title,
                author = author,
                description = description,
                filePath = cachedFileWrapper.path,
                uri = cachedFileWrapper.uri.toString(),
                typeFile = typeFile,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}