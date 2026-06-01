package com.zenread.book.presentation.pdf.popup.action

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import com.zenread.book.R
import java.net.URLEncoder
import kotlin.math.max

class PopupTextActionExtension(
    private val context: Context,
    private val textSelection: String?,
    private val parentX: Int,
    private val parentY: Int,
    private val parentWidth: Int,
    private val parentHeight: Int
) {

    fun show(anchorView: View) {
        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.layout_text_actions_extension, null)
//        val customize = popupView.findViewById<LinearLayout>(R.id.popup_customize)
        val search =  popupView.findViewById<LinearLayout>(R.id.popup_search)

        val popup = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        popup.setBackgroundDrawable(Color.WHITE.toDrawable())

        search.setOnClickListener {
            textSelection?.let { textToShare -> searchTextInBrowser(context,textToShare) }
        }

        val extWidth = popupView.measuredWidth
        val extHeight = popupView.measuredHeight
        val screenHeight = context.resources.displayMetrics.heightPixels
        val margin = 8.dp

        val spaceBelow = screenHeight - (parentY + parentHeight)
        val spaceAbove = parentY

        val popupTop = when {
            spaceBelow >= extHeight + margin -> parentY + parentHeight - margin
            spaceAbove >= extHeight + margin -> parentY - extHeight + margin
            else -> max(margin, screenHeight - extHeight + margin)
        }

        val popupLeft = (parentX + parentWidth - extWidth - margin)
            .coerceIn(margin, context.resources.displayMetrics.widthPixels - extWidth - margin)

        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, popupLeft, popupTop)
    }

    private fun searchTextInBrowser(context: Context, textToSearch: String) {
        try {
            val encodedQuery = URLEncoder.encode(textToSearch, "UTF-8")
            val searchUrl = "https://www.google.com/search?q=$encodedQuery"
            val intent = Intent(Intent.ACTION_VIEW, searchUrl.toUri())
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở trình duyệt để tìm kiếm.", Toast.LENGTH_SHORT).show()
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}