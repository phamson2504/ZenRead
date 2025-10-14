package com.zenread.book.presentation.book.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zenread.book.core.base.BaseViewModel
import com.zenread.book.core.model.FileFormat
import com.zenread.book.core.model.Resource
import com.zenread.book.core.utils.SingleEvent
import com.zenread.book.data.error.ERROR_LOAD_FILE
import com.zenread.book.data.error.NUM_FAIL_ERROR
import com.zenread.book.domain.model.Book
import com.zenread.book.domain.model.BookResult
import com.zenread.book.domain.usecase.GetBooksUseCase
import com.zenread.book.domain.usecase.ImportBooksFromFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookListViewModel @Inject constructor(
    private val importBooksFromFilesUseCase: ImportBooksFromFilesUseCase,
    private val getBooksUseCase: GetBooksUseCase,
) : BaseViewModel() {
    private val _books = MutableLiveData<Resource<List<Book>>>()
    val books: LiveData<Resource<List<Book>>> get() = _books

    private val _openBook = MutableLiveData<SingleEvent<Book>>()
    val openBook: LiveData<SingleEvent<Book>> get() = _openBook

    private val showToastPrivate = MutableLiveData<SingleEvent<Any>>()
    val showToast: LiveData<SingleEvent<Any>> get() = showToastPrivate

    fun loadBooks() {
        viewModelScope.launch {
            _books.value = Resource.Loading()
            try {
                val importResults = importBooksFromFilesUseCase.invoke()
                val errors = importResults.filterIsInstance<BookResult.Error>()

                val books = getBooksUseCase.invoke()
                _books.value = Resource.Success(books)
                
                //error
                if (errors.isNotEmpty())
                    showToastMessage(NUM_FAIL_ERROR, errors.size)
            } catch (e: Exception) {
                _books.value = Resource.Error(ERROR_LOAD_FILE)
            }
        }
    }

    fun openBook(book: Book) {
        if (book.typeFile == FileFormat.PDF.toString()) {
            _openBook.value = SingleEvent(book)
        }
    }

    fun showToastMessage(errorCode: Int, vararg args: Any) {
        val error = errorManager.getError(errorCode,  *args)
        showToastPrivate.value = SingleEvent(error.description)
    }
}