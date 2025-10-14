package com.zenread.book.presentation.filemanager.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import com.zenread.book.core.base.BaseActivity
import com.zenread.book.databinding.ActivityFileManagerBinding
import com.zenread.book.presentation.MainActivity
import com.zenread.book.presentation.book.ui.BookFragment
import com.zenread.book.presentation.filemanager.model.FileManagerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FileManagerActivity : BaseActivity<ActivityFileManagerBinding>() {
    private val fileManagerViewModel: FileManagerViewModel by viewModels()

    override fun inflateBinding(layoutInflater: LayoutInflater) =
        ActivityFileManagerBinding.inflate(layoutInflater)

    override fun setupView(savedInstanceState: Bundle?) {
        binding.btnSelectFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            folderPickerLauncher.launch(intent)
        }
        binding.icBackBtn.setOnClickListener {
            finish()
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                fileManagerViewModel.grantUriPermission(it)
            }
        }
    }
}