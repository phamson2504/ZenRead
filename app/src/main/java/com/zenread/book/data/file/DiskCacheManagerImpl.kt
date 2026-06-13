package com.zenread.book.data.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.zenread.book.domain.repository.DiskCacheManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class DiskCacheManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DiskCacheManager {

    private var bookId: String = "default"

    override fun setBookId(bookId: String) {
        this.bookId = bookId
    }

    private val cacheDir: File
        get() = File(context.cacheDir, "pdf_cache/$bookId").apply { mkdirs() }

    private fun getCacheFile(pageIndex: Int) = File(cacheDir, "page_$pageIndex.png")

    override fun saveBitmap(pageIndex: Int, bitmap: Bitmap) {
        val file = getCacheFile(pageIndex)
        if (!file.exists()) {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }


    override fun loadBitmap(pageIndex: Int): Bitmap? {
        val file = getCacheFile(pageIndex)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    override fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
