package com.zenread.book.presentation.filemanager.model

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.zenread.book.domain.usecase.GrantPersistableUriUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val grantPersistableUriUseCase: GrantPersistableUriUseCase
) : ViewModel() {
    fun grantUriPermission(uri: Uri) {
        grantPersistableUriUseCase.invoke(uri)
    }
}