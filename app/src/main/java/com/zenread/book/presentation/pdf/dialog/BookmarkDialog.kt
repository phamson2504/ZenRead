package com.zenread.book.presentation.pdf.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zenread.book.databinding.DialogBookmarkBinding

class BookmarkDialog: DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogBookmarkBinding.inflate(layoutInflater)
        val pageIndex = arguments?.getInt(ARG_PAGE) ?: 0

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()

        binding.edtBookmarkName.setText("Bookmark ${pageIndex + 1}")

        binding.btnSave.setOnClickListener {
            val name = binding.edtBookmarkName.text.toString().trim()
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_NAME to name, RESULT_PAGE to pageIndex)
            )
            dialog.dismiss()
        }

        binding.btnCancel.setOnClickListener {
            setFragmentResult(
                CANCEL_REQUEST_KEY,
                bundleOf(RESULT_PAGE to pageIndex)
            )
            dialog.dismiss()
        }

        return dialog
    }

    companion object {
        const val CANCEL_REQUEST_KEY = "bookmark_cancel_request"
        const val REQUEST_KEY = "bookmark_name_request"
        const val RESULT_NAME = "name"
        const val RESULT_PAGE = "page"
        private const val ARG_PAGE = "page"

        fun newInstance(pageIndex: Int) = BookmarkDialog().apply {
            arguments = bundleOf(ARG_PAGE to pageIndex)
        }
    }
}