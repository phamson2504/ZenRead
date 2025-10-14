package com.zenread.book.domain.repository

import android.graphics.Bitmap

interface DiskCacheManager {
    fun saveBitmap(pageIndex: Int, bitmap: Bitmap)
    fun loadBitmap(pageIndex: Int): Bitmap?
    fun clear()
    fun setBookId(bookId: String)
}
