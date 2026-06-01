package com.zenread.book.core.utils

import android.util.SizeF
import android.view.View
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.measureItemAt(position: Int): SizeF {
    val adapter = adapter ?: return SizeF(0f, 0f)
    if (position !in 0 until adapter.itemCount) return SizeF(0f, 0f)

    val viewType = adapter.getItemViewType(position)
    val vh = adapter.createViewHolder(this, viewType)
    adapter.onBindViewHolder(vh, position)

    val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

    vh.itemView.measure(widthSpec, heightSpec)
    vh.itemView.layout(0, 0, vh.itemView.measuredWidth, vh.itemView.measuredHeight)

    return SizeF(
        vh.itemView.measuredWidth.toFloat(),
        vh.itemView.measuredHeight.toFloat()
    )
}

