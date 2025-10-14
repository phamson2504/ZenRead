package com.zenread.book.core.base

import androidx.lifecycle.ViewModel
import com.zenread.book.domain.usecase.ErrorManager
import javax.inject.Inject

abstract class BaseViewModel : ViewModel() {
    @Inject
    lateinit var errorManager: ErrorManager
}