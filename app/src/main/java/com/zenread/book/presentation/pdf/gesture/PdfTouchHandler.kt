package com.zenread.book.presentation.pdf.gesture

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PdfTouchHandler(
    private val context: Context,
    private val container: View,
    private val ivTemp: ImageView,
    private val pdfRecyclerView: RecyclerView,
) {
    var zoomMode = false

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        ivTemp.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            true
        }
    }
    
    fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                return true
            }

            MotionEvent.ACTION_UP -> {
                return true
            }
        }
        return false
    }

    private val scaleDetector by lazy {
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        })
    }
}
