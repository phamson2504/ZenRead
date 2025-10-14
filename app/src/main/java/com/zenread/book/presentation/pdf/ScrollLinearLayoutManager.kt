package com.zenread.book.presentation.pdf

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager

class ScrollLinearLayoutManager(
    context: Context,
    orientation: Int,
    reverseLayout: Boolean
) : LinearLayoutManager(context, orientation, reverseLayout) {

    var scrollEnabled = true

    override fun canScrollVertically(): Boolean {
        return scrollEnabled && super.canScrollVertically()
    }

    override fun canScrollHorizontally(): Boolean {
        return scrollEnabled && super.canScrollHorizontally()
    }
}