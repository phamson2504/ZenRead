package com.zenread.book.presentation.pdf.popup

interface OnTextActionListener {
    fun onClickColorHighlight(color: Int)
    fun onDeleteHighlight()

    fun onCopyText()

    fun onContentNote()

    fun onTranslate(isTranslate: Boolean)

    fun onMoreExtension(): String?
}