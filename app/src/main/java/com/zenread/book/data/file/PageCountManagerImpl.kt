package com.zenread.book.data.file
import android.content.Context
import com.zenread.book.domain.repository.PageCountManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.core.content.edit
import com.zenread.book.presentation.pdf.ReadingPosition

class PageCountManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PageCountManager {

    private val prefs = context.getSharedPreferences("zenread_page_prefs", Context.MODE_PRIVATE)

    override fun savePageCount(bookId: Int, readingPosition: ReadingPosition) {
        if (bookId <= 0) return
        prefs.edit {
            prefs.edit {
                putInt("key_page_$bookId", readingPosition.pageIndex)
                putFloat("key_percentage_$bookId", readingPosition.pagePercentage)
            }
        }
    }

    override fun loadPageCount(bookId: Int): ReadingPosition? {
        if (bookId <= 0) return null

        val pageIndex = prefs.getInt("key_page_$bookId", -1)
        if (pageIndex == -1) return null

        return ReadingPosition(
            pageIndex = pageIndex,
            pagePercentage = prefs.getFloat("key_percentage_$bookId", 0f)
        )
    }
}