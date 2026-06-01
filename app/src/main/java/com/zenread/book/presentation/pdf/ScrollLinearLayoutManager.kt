package com.zenread.book.presentation.pdf

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class ScrollLinearLayoutManager(
    context: Context,
    orientation: Int,
    reverseLayout: Boolean
) : LinearLayoutManager(context, orientation, reverseLayout) {

    var scrollEnabled = true
    var snapToPage = false

    override fun canScrollVertically(): Boolean {
        return scrollEnabled && super.canScrollVertically()
    }

    override fun canScrollHorizontally(): Boolean {
        return scrollEnabled && super.canScrollHorizontally()
    }

}

class CustomSpeedScroller(
    context: Context,
) : LinearSmoothScroller(context) {

    private val MILLISECONDS_PER_INCH = 0.001f

    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
        return MILLISECONDS_PER_INCH / displayMetrics.densityDpi
    }
}
