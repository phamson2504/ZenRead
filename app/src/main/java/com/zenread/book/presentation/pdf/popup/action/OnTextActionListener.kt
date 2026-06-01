package com.zenread.book.presentation.pdf.popup.action

interface OnTextActionListener {
    fun onClickColorHighlight(color: Int)
    fun onDeleteHighlight()

    fun onCopyText()

    fun onContentNote()

    fun onTranslate(isTranslate: Boolean)

    fun onMoreExtension(): String?
}