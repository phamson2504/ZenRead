package com.zenread.book.presentation.pdf.popup

import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.zenread.book.R
import androidx.core.graphics.drawable.toDrawable

class PopupTextAction(
    private val context: Context,
    private val parent: View,
    private var selectArea: RectF? = null,
) {
    private var isEdit = false
    private var listener : OnTextActionListener? = null
    private var isDictionary = false

    fun setEdit(isEdit: Boolean) {
        this.isEdit = isEdit
    }

    fun setDictionary(isDictionary: Boolean) {
        this.isDictionary = isDictionary
    }

    fun setOnTextActionListener(listener: OnTextActionListener) {
        this.listener = listener
    }


    private var popup: PopupWindow? = null

    fun show() {
        dismiss()

        val selectArea = selectArea ?: return
        val inflater = LayoutInflater.from(context)
        val content = inflater.inflate(R.layout.layout_pdf_text_actions, null)
        popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(Color.WHITE.toDrawable())
            isOutsideTouchable = true
            isTouchable = true
            isFocusable = false
            elevation = 2f.dp
        }

        val colorPalette = content.findViewById<LinearLayout>(R.id.colorPalette)
        val layoutDelete = content.findViewById<LinearLayout>(R.id.layoutDelete)
        val btnCopy = content.findViewById<LinearLayout>(R.id.layoutCopy)
        val btnNote = content.findViewById<LinearLayout>(R.id.layoutNote)
        val btnTranslate = content.findViewById<LinearLayout>(R.id.layoutTranslate)
        val btnMore = content.findViewById<LinearLayout>(R.id.layoutMore)
        val btnDictionary = content.findViewById<LinearLayout>(R.id.layoutDictionary)



        colorPalette.removeAllViews()

        btnDictionary.isEnabled = isDictionary
        btnDictionary.alpha = if (isDictionary) 1f else 0.4f

        if (isEdit) {
            layoutDelete.visibility = View.VISIBLE
            btnDictionary.visibility = View.GONE
        } else {
            layoutDelete.visibility = View.GONE
            btnDictionary.visibility = View.VISIBLE
        }

        val highlightColors = listOf(
            Color.parseColor("#4A90E2"),
            Color.parseColor("#F5A623"),
            Color.parseColor("#BD10E0"),
            Color.parseColor("#7ED321"),
            Color.parseColor("#F8E71C")
        )

        highlightColors.forEach { color ->
            val colorView = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(50.dp, 20.dp).apply {
                    marginEnd = 6.dp
                    marginStart = 6.dp
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f.dp
                    setColor(color)
                    setStroke(2.dp, Color.DKGRAY)
                }

                setOnClickListener {
                    listener?.onClickColorHighlight(color)
                    dismiss()
                }
            }
            colorPalette.addView(colorView)
        }

        layoutDelete.setOnClickListener {
            listener?.onDeleteHighlight()
            dismiss()
        }
        btnCopy.setOnClickListener {
            listener?.onCopyText()
            dismiss()
        }
        btnNote.setOnClickListener {
            listener?.onContentNote()
            dismiss()
        }

        btnTranslate.setOnClickListener {
            listener?.onTranslate(true)
            dismiss()
        }

        btnDictionary.setOnClickListener {
            listener?.onTranslate(false)
            dismiss()
        }


        btnMore.setOnClickListener {
            val actionLocation = IntArray(2)
            popup?.contentView?.getLocationOnScreen(actionLocation)
            val actionX = actionLocation[0]
            val actionY = actionLocation[1]
            val actionHeight = popup!!.contentView.height
            val actionWidth = popup!!.contentView.width

            val selectedText = listener?.onMoreExtension()
            val extensionPopup = PopupTextActionExtension(context, selectedText, actionX, actionY, actionWidth, actionHeight)
            extensionPopup.show(parent)
        }

        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupW = content.measuredWidth
        val popupH = content.measuredHeight

        val displaySize = getDisplaySize()
        val screenW = displaySize.x
        val screenH = displaySize.y

        var x = (selectArea.centerX() - popupW / 2).toInt()
        if (x < 8.dp) x = 8.dp
        if (x + popupW > screenW - 8.dp) x = screenW - popupW - 8.dp

        // Thử đặt phía trên rect
        val offsetY = 8.dp
        var y = (selectArea.top - popupH - offsetY).toInt()

        // Nếu không đủ chỗ phía trên thì đặt phía dưới
        if (y < 0) {
            y = (selectArea.bottom + offsetY).toInt()
            if (y + popupH > screenH) {
                // Nếu dưới cũng không đủ (rất hiếm), ép lên giữa màn hình
                y = (screenH / 2) - (popupH / 2)
            }
        }

        popup?.showAtLocation(parent, Gravity.NO_GRAVITY, x, y)
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    fun updateSelectArea(newArea: RectF?) {
        selectArea = newArea
    }

    private fun getDisplaySize(): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val out = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getSize(out)
        return out
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    private val Float.dp: Float
        get() = this * context.resources.displayMetrics.density
}