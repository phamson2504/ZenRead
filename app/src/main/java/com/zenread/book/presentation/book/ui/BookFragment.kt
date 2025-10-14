package com.zenread.book.presentation.book.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.zenread.book.R
import com.zenread.book.core.base.BaseFragment
import com.zenread.book.core.model.Resource
import com.zenread.book.core.utils.SingleEvent
import com.zenread.book.core.utils.observe
import com.zenread.book.core.utils.observeEvent
import com.zenread.book.core.utils.setupSnackbar
import com.zenread.book.databinding.FragmentBookBinding
import com.zenread.book.domain.model.Book
import com.zenread.book.presentation.MainActivity
import com.zenread.book.presentation.book.adapter.BookAdapter
import com.zenread.book.presentation.book.adapter.listeners.RecyclerBookListener
import com.zenread.book.presentation.book.model.BookListViewModel
import com.zenread.book.presentation.filemanager.ui.FileManagerActivity
import com.zenread.book.presentation.pdf.ui.PdfReadActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BookFragment : BaseFragment<FragmentBookBinding>() {

    private val bookListViewModel: BookListViewModel by viewModels()
    private lateinit var bookAdapter: BookAdapter

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentBookBinding.inflate(inflater, container, false)

    override fun setupView(savedInstanceState: Bundle?) {
        (activity as? MainActivity)?.setToolbarTitle(getString(R.string.recent_books))

        val layoutManager = LinearLayoutManager(requireContext())
        binding.rlvBookFragment.layoutManager = layoutManager

        checkPermission()
    }

    private fun handleBooksList(resource: Resource<List<Book>>) {
        when (resource) {
            is Resource.Loading -> {
                showLoadingView()
            }

            is Resource.Success -> {
                resource.data?.let { bindingListData(it) }
            }

            is Resource.Error -> {
                showDataView(false)
            }
        }
    }

    private fun showLoadingView() {
        binding.progressBar.isVisible = true
        binding.tvNoData.isVisible = false
        binding.rlvBookFragment.isGone
    }

    private fun showDataView(show: Boolean) {
        binding.tvNoData.visibility = if (show) GONE else VISIBLE
        binding.rlvBookFragment.visibility = if (show) VISIBLE else GONE
        binding.progressBar.isVisible = false
    }

    private fun bindingListData(books: List<Book>) {
        if (books.isNotEmpty()) {
            bookAdapter = BookAdapter(books, object : RecyclerBookListener {
                override fun onBookSelected(book: Book) {
                    bookListViewModel.openBook(book)
                }
            })

            binding.rlvBookFragment.adapter = bookAdapter
            showDataView(true)
        } else {
            showDataView(false)
        }
    }

    private fun navigateToBookScreen(navigateEvent: SingleEvent<Book>) {
        navigateEvent.getContentIfNotHandled()?.let { book ->
            val intent = Intent(requireContext(), PdfReadActivity::class.java)
            intent.putExtra("book_data", book)
            startActivity(intent)
        }
    }

    private fun observeSnackBarMessages(event: LiveData<SingleEvent<Any>>) {
        binding.root.setupSnackbar(this, event, Snackbar.LENGTH_LONG)
    }

    private fun checkPermission() {
        val hasPersistedUriPermission =
            requireActivity().application.contentResolver.persistedUriPermissions.isEmpty()
        if (hasPersistedUriPermission) {
            val intent = Intent(requireContext(), FileManagerActivity::class.java)
            startActivity(intent)
        }
    }

    override fun setupObserver() {
        observe(bookListViewModel.books, ::handleBooksList)
        observeEvent(bookListViewModel.openBook, ::navigateToBookScreen)
        observeSnackBarMessages(bookListViewModel.showToast)
    }

    override fun onResume() {
        super.onResume()
        bookListViewModel.loadBooks()
    }

}