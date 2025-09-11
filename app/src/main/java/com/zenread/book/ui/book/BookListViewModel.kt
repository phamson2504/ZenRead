package com.zenread.book.ui.book

import com.zenread.book.domain.usecase.ImportBooksFromFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BookListViewModel @Inject constructor(
    importBooksFromFilesUseCase: ImportBooksFromFilesUseCase
) {

}